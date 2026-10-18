package app.nocturne.library

import app.nocturne.data.SmbConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNotNull
import org.junit.Test

/**
 * Talks to a real SMB2/3 server. Skipped unless NOCTURNE_SMB_TEST=host:port is set, with a share called
 * "music" (user test / password secret) holding the sample library from CLAUDE.md.
 */
class SmbIntegrationTest {
    private val target: String? = System.getenv("NOCTURNE_SMB_TEST")

    private fun cfg(share: String = "music", user: String = "test", host: String = target ?: "") =
        SmbConfig(host = host, share = share, username = user, domain = "WORKGROUP")

    @Test fun listsTheShareAndReportsSampleEntries() {
        assumeNotNull(target)
        val r = SmbShareProvider.test(cfg(), "secret")
        assertTrue(r.message, r.success)
        assertTrue(r.sample.contains("Loose"))
    }

    @Test fun wrongPasswordGivesLoginMessage() {
        assumeNotNull(target)
        val r = SmbShareProvider.test(cfg(), "nope")
        assertFalse(r.success)
        assertTrue(r.message, r.message.startsWith("Login failed"))
    }

    @Test fun wrongShareGivesNotFoundMessage() {
        assumeNotNull(target)
        val r = SmbShareProvider.test(cfg(share = "doesnotexist"), "secret")
        assertFalse(r.success)
        assertTrue(r.message, r.message.startsWith("Share or folder not found"))
    }

    @Test fun missingSubfolderGivesNotFoundMessage() {
        assumeNotNull(target)
        val r = SmbShareProvider.test(cfg().copy(subfolder = "no/such/dir"), "secret")
        assertFalse(r.success)
        assertTrue(r.message, r.message.startsWith("Share or folder not found"))
    }

    @Test fun unknownHostIsNamed() {
        assumeNotNull(target)
        val r = SmbShareProvider.test(cfg(host = "no-such-host.invalid"), "secret")
        assertFalse(r.success)
        assertTrue(r.message, r.message.startsWith("Unknown host"))
    }

    @Test fun closedPortIsReportedAsUnreachable() {
        assumeNotNull(target)
        val r = SmbShareProvider.test(cfg(host = "127.0.0.1:1"), "secret")
        assertFalse(r.success)
        assertTrue(r.message, r.message.startsWith("Cannot reach"))
    }

    @Test fun providerReconnectsAfterClose() {
        assumeNotNull(target)
        val p = SmbShareProvider({ cfg() }, { "secret" })
        assertTrue(p.share().isConnected)
        p.close()
        assertTrue(p.share().isConnected)
        assertEquals("music", p.share().smbPath.shareName)
        p.close()
    }
}
