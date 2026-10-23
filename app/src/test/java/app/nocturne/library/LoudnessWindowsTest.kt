package app.nocturne.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LoudnessWindowsTest {
    private val sec = 1_000_000L

    @Test fun aNormalSongIsSampledTwiceInTheMiddle() {
        val w = LoudnessMeter.windows(200 * sec)
        assertEquals(2, w.size)
        assertEquals(60 * sec, w[0].first)
        assertEquals(124 * sec, w[1].first)
        assertTrue(w.all { it.second == LoudnessMeter.WINDOW_US })
    }

    @Test fun windowsStayInsideTheSong() {
        for (d in listOf(40, 41, 90, 600)) {
            val dur = d * sec
            assertTrue(LoudnessMeter.windows(dur).all { (s, l) -> s >= 0 && s + l <= dur })
        }
    }

    @Test fun aShortSongGetsOneMiddleStretch() {
        val w = LoudnessMeter.windows(20 * sec)
        assertEquals(1, w.size)
        assertEquals(4 * sec, w[0].first)
        assertEquals(12 * sec, w[0].second)
    }

    @Test fun aVeryShortSoundIsMeasuredWhole() {
        assertEquals(listOf(0L to 5 * sec), LoudnessMeter.windows(5 * sec))
    }
}
