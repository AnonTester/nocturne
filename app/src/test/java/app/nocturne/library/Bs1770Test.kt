package app.nocturne.library

import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Bs1770Test {
    /** Interleaved 16-bit sine of [seconds] at [dbfs] peak (997 Hz is the EBU test frequency). */
    private fun sine(fs: Int, ch: Int, seconds: Double, dbfs: Double, hz: Double = 997.0): ShortArray {
        val frames = (fs * seconds).toInt()
        val amp = 10.0.pow(dbfs / 20.0) * 32767.0
        return ShortArray(frames * ch) { i -> (amp * sin(2 * PI * hz * (i / ch) / fs)).toInt().toShort() }
    }

    private fun measure(fs: Int, ch: Int, pcm: ShortArray): Double? =
        Bs1770(fs, ch).also { it.feed(pcm, pcm.size / ch) }.loudness()

    @Test fun stereoSineAtMinus23DbfsIsMinus23Lufs() {
        assertEquals(-23.0, measure(48000, 2, sine(48000, 2, 10.0, -23.0))!!, 0.1)
    }

    @Test fun sameResultAt44100Hz() {
        assertEquals(-23.0, measure(44100, 2, sine(44100, 2, 10.0, -23.0))!!, 0.1)
    }

    @Test fun monoIsTreatedAsDualMono() {
        assertEquals(-23.0, measure(48000, 1, sine(48000, 1, 10.0, -23.0))!!, 0.1)
    }

    @Test fun tenDbLouderReadsTenLufsHigher() {
        val quiet = measure(48000, 2, sine(48000, 2, 10.0, -30.0))!!
        val loud = measure(48000, 2, sine(48000, 2, 10.0, -20.0))!!
        assertEquals(10.0, loud - quiet, 0.05)
    }

    @Test fun silenceHasNoLoudness() {
        assertNull(measure(48000, 2, ShortArray(48000 * 2 * 5)))
    }

    @Test fun relativeGateIgnoresVeryQuietParts() {
        val a = Bs1770(48000, 2)
        val loud = sine(48000, 2, 10.0, -20.0)
        val quiet = sine(48000, 2, 10.0, -50.0)
        a.feed(loud, loud.size / 2); a.newSegment(); a.feed(quiet, quiet.size / 2)
        assertEquals(measure(48000, 2, loud)!!, a.loudness()!!, 0.1)
    }

    @Test fun skippedWarmupDoesNotCount() {
        val a = Bs1770(48000, 2)
        val pcm = sine(48000, 2, 10.0, -23.0)
        a.skip(48000 * 5)
        a.feed(pcm, pcm.size / 2)
        // only the last 5 s were counted but the level is the same
        assertEquals(-23.0, a.loudness()!!, 0.1)
    }

    @Test fun peakIsTheLargestSample() {
        val a = Bs1770(48000, 2)
        val pcm = sine(48000, 2, 1.0, -6.0)
        a.feed(pcm, pcm.size / 2)
        assertEquals(10.0.pow(-6.0 / 20.0).toFloat(), a.peak, 0.01f)
    }

    @Test fun gainBringsATrackToTheReferenceLevel() {
        assertEquals(-4.0f, Bs1770.gainFor(-14.0), 0.001f)
        assertEquals(3.0f, Bs1770.gainFor(-21.0), 0.001f)
        assertTrue(Bs1770.gainFor(-18.0) == 0f)
    }
}
