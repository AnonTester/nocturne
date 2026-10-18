package app.nocturne.library

import app.nocturne.data.SmbConfig
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import android.util.Log
import java.util.concurrent.TimeUnit
import com.hierynomus.smbj.SmbConfig as SmbjConfig

/**
 * Keeps one authenticated SMB2/3 connection to the configured share and reconnects on demand.
 * Shared by the scanner, the ExoPlayer data source and the metadata reader.
 */
class SmbShareProvider(
    private val config: () -> SmbConfig,
    private val password: () -> String,
) {
    private var client: SMBClient? = null
    private var connection: Connection? = null
    private var session: Session? = null
    private var share: DiskShare? = null
    private var connectedKey: String? = null

    @Synchronized
    fun share(): DiskShare {
        val cfg = config()
        require(cfg.isConfigured) { "No Samba share configured" }
        val key = "${cfg.host}|${cfg.share}|${cfg.username}|${cfg.domain}|${password().hashCode()}"
        val current = share
        if (current != null && current.isConnected && key == connectedKey) return current
        closeQuietly()
        return open(cfg, password()).also { connectedKey = key }
    }

    @Synchronized
    fun close() = closeQuietly()

    private fun open(cfg: SmbConfig, pass: String): DiskShare {
        val c = SMBClient(clientConfig())
        client = c
        try {
            val (h, port) = parseHost(cfg.host)
            val t0 = System.nanoTime()
            val conn = c.connect(h, port)
            connection = conn
            val t1 = System.nanoTime()
            val sess = conn.authenticate(authContext(cfg, pass))
            session = sess
            val t2 = System.nanoTime()
            val s = sess.connectShare(cfg.share.trim().trim('/', '\\')) as? DiskShare
                ?: error("'${cfg.share}' is not a file share")
            share = s
            Log.d(TAG, "connected: negotiate=${(t1 - t0) / 1_000_000}ms auth=${(t2 - t1) / 1_000_000}ms " +
                "tree=${(System.nanoTime() - t2) / 1_000_000}ms dialect=${conn.negotiatedProtocol.dialect} " +
                "maxRead=${conn.negotiatedProtocol.maxReadSize}")
            return s
        } catch (e: Exception) {
            closeQuietly()
            throw e
        }
    }

    private fun closeQuietly() {
        runCatching { share?.close() }
        runCatching { session?.close() }
        runCatching { connection?.close(true) }
        runCatching { client?.close() }
        share = null; session = null; connection = null; client = null; connectedKey = null
    }

    companion object {
        private const val TAG = "NocturneSMB"
        const val DEFAULT_PORT = 445

        /** "nas.local" or "nas.local:4455" (IPv6 literals are left alone). */
        fun parseHost(raw: String): Pair<String, Int> {
            val t = raw.trim()
            if (t.count { it == ':' } == 1) {
                val port = t.substringAfter(':').toIntOrNull()
                if (port != null && port in 1..65535) return t.substringBefore(':') to port
            }
            return t to DEFAULT_PORT
        }

        fun clientConfig(): SmbjConfig = SmbjConfig.builder()
            .withTimeout(15, TimeUnit.SECONDS)
            .withSoTimeout(30, TimeUnit.SECONDS)
            .withMultiProtocolNegotiate(true)
            .build()

        fun authContext(cfg: SmbConfig, pass: String): AuthenticationContext =
            if (cfg.username.isBlank()) AuthenticationContext.guest()
            else AuthenticationContext(cfg.username.trim(), pass.toCharArray(), cfg.domain.trim().ifEmpty { "WORKGROUP" })

        /** One-shot connection used by "Test connection", independent of the shared one. */
        fun test(cfg: SmbConfig, pass: String): TestResult {
            val c = SMBClient(clientConfig())
            return try {
                val (h, port) = parseHost(cfg.host)
                c.connect(h, port).use { conn ->
                    conn.authenticate(authContext(cfg, pass)).use { sess ->
                        val s = sess.connectShare(cfg.share.trim().trim('/', '\\'))
                        s.use {
                            val disk = it as? DiskShare ?: return TestResult(false, "'${cfg.share}' is not a file share")
                            val start = cfg.subfolder.trim('/', '\\').replace('/', '\\')
                            val names = disk.list(start).map { f -> f.fileName }.filter { n -> n != "." && n != ".." }
                            TestResult(true, "Connected to \\\\${cfg.host}\\${cfg.share} (${names.size} entries)", names.take(8))
                        }
                    }
                }
            } catch (e: Exception) {
                TestResult(false, describe(e, cfg))
            } finally {
                runCatching { c.close() }
            }
        }

        fun describe(e: Throwable, cfg: SmbConfig): String {
            val root = generateSequence(e) { it.cause }.last()
            val text = (e.message ?: "") + " " + (root.message ?: "")
            return when {
                "LOGON_FAILURE" in text || "STATUS_ACCESS_DENIED" in text || "Authentication failed" in text ->
                    "Login failed: wrong username, password or domain."
                "BAD_NETWORK_NAME" in text || "OBJECT_NAME_NOT_FOUND" in text || "OBJECT_PATH_NOT_FOUND" in text ||
                    "NO_SUCH_FILE" in text || "NOT_A_DIRECTORY" in text ->
                    "Share or folder not found on ${cfg.host}. Check the share name and subfolder."
                root is java.net.UnknownHostException -> "Unknown host '${cfg.host}'."
                root is java.net.ConnectException || root is java.net.SocketTimeoutException ||
                    root is java.util.concurrent.TimeoutException ->
                    "Cannot reach ${cfg.host}. Check the address and that this device is on the same network."
                "Unsupported" in text && "dialect" in text.lowercase() ->
                    "The server only offers SMB1, which is not supported. Enable SMB2/3 on the server."
                else -> "Connection failed: ${root.message ?: e.javaClass.simpleName}"
            }
        }
    }
}

data class TestResult(val success: Boolean, val message: String, val sample: List<String> = emptyList())
