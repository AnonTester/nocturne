package app.nocturne.library

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.SystemClock
import app.nocturne.data.Source
import app.nocturne.data.Track
import java.nio.ByteOrder

/**
 * Measures the loudness of a track that has no ReplayGain tag, from short stretches in the middle of the song
 * (most songs start soft and build up, so the start would read too quiet). Decodes with the platform codecs.
 */
class LoudnessMeter(private val context: Context, private val smb: SmbProviders) {
    class Result(val lufs: Double, val gainDb: Float, val peak: Float)

    /** Output format of the decoder; it is announced once and stays valid across seeks, so it outlives one window. */
    private class Output { var channels = 0; var sampleRate = 0 }

    /** Blocking; call from a background thread. Throws when the file cannot be decoded or is silent. */
    fun measure(track: Track): Result {
        val extractor = MediaExtractor()
        var source: SmbMediaDataSource? = null
        var codec: MediaCodec? = null
        try {
            val uri = Uri.parse(track.uri)
            if (Source.isSmb(track.source)) {
                source = SmbMediaDataSource(smb.get(track.source), SmbUri.sharePath(uri))
                extractor.setDataSource(source)
            } else {
                extractor.setDataSource(context, uri, null)
            }
            val index = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: error("no audio track")
            extractor.selectTrack(index)
            val format = extractor.getTrackFormat(index)
            val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION)
            else track.durationMs * 1000
            check(durationUs > 0) { "unknown duration" }

            codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
            codec.configure(format, null, null, 0)
            codec.start()

            var analyzer: Bs1770? = null
            val output = Output()
            for ((startUs, lengthUs) in windows(durationUs)) {
                extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
                codec.flush()
                analyzer?.newSegment()
                analyzer = decodeWindow(extractor, codec, output, analyzer, startUs, startUs + lengthUs)
            }
            val a = analyzer ?: error("nothing decoded")
            val lufs = a.loudness() ?: error("silent")
            return Result(lufs, Bs1770.gainFor(lufs).coerceIn(-MAX_GAIN_DB, MAX_GAIN_DB), a.peak)
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
            runCatching { source?.close() }
        }
    }

    private fun decodeWindow(extractor: MediaExtractor, codec: MediaCodec, out: Output, existing: Bs1770?, startUs: Long, endUs: Long): Bs1770? {
        var analyzer = existing
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        val deadline = SystemClock.elapsedRealtime() + WINDOW_TIMEOUT_MS
        var pcm = ShortArray(0)
        val countFromUs = startUs + WARMUP_US
        while (true) {
            check(SystemClock.elapsedRealtime() < deadline) { "decode timeout" }
            if (!inputDone) {
                val i = codec.dequeueInputBuffer(10_000)
                if (i >= 0) {
                    val buf = codec.getInputBuffer(i)!!
                    val n = extractor.readSampleData(buf, 0)
                    if (n < 0) {
                        codec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        codec.queueInputBuffer(i, 0, n, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }
            val o = codec.dequeueOutputBuffer(info, 10_000)
            when {
                o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    val f = codec.outputFormat
                    out.channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    out.sampleRate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    if (analyzer == null) analyzer = Bs1770(out.sampleRate, out.channels)
                }
                o >= 0 -> {
                    val buffer = codec.getOutputBuffer(o)
                    val eos = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    val channels = out.channels
                    val sampleRate = out.sampleRate
                    if (buffer != null && info.size > 0 && channels > 0 && analyzer != null) {
                        val a = analyzer
                        val frames = info.size / 2 / channels
                        if (pcm.size < frames * channels) pcm = ShortArray(frames * channels)
                        buffer.position(info.offset); buffer.limit(info.offset + info.size)
                        buffer.order(ByteOrder.nativeOrder()).asShortBuffer().get(pcm, 0, frames * channels)
                        // Frames decoded before the window (the seek lands on an earlier sync point) and the warm-up only settle the filters.
                        val toSkip = ((countFromUs - info.presentationTimeUs) * sampleRate / 1_000_000L).toInt().coerceIn(0, frames)
                        val toEnd = ((endUs - info.presentationTimeUs) * sampleRate / 1_000_000L).toInt().coerceIn(0, frames)
                        if (toSkip > 0) a.skip(toSkip)
                        a.feed(pcm, toEnd)
                    }
                    codec.releaseOutputBuffer(o, false)
                    if (eos || info.presentationTimeUs >= endUs) return analyzer
                }
            }
        }
    }

    companion object {
        const val WINDOW_US = 12_000_000L
        const val WARMUP_US = 300_000L
        const val WINDOW_TIMEOUT_MS = 40_000L
        const val MAX_GAIN_DB = 24f

        /**
         * Where to listen: two stretches around 30 % and 62 % of the song; a short song gets one stretch from its middle,
         * a very short one is measured whole. Returns (start, length) pairs in microseconds.
         */
        fun windows(durationUs: Long): List<Pair<Long, Long>> = when {
            durationUs < 8_000_000L -> listOf(0L to durationUs)
            durationUs < 40_000_000L -> listOf((durationUs * 0.2).toLong() to (durationUs * 0.6).toLong())
            else -> listOf((durationUs * 0.30).toLong() to WINDOW_US, (durationUs * 0.62).toLong() to WINDOW_US)
        }
    }
}
