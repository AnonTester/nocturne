package app.nocturne.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepTimerTest {
    private val t0 = 1_000_000L

    @Test fun startSetsTargetAndRemaining() {
        val s = SleepTimer.start(t0, 30, true)
        assertTrue(s.isActive)
        assertEquals(t0 + 30 * 60_000L, s.targetMillis)
        assertEquals(1800L, s.remainingSeconds)
    }

    @Test fun tickCountsDownRoundingUp() {
        val s = SleepTimer.start(t0, 1, true)
        val (next, action) = SleepTimer.tick(s, t0 + 500)
        assertEquals(60L, next.remainingSeconds)
        assertEquals(SleepAction.NONE, action)
        assertEquals(59L, SleepTimer.tick(s, t0 + 1_500).first.remainingSeconds)
    }

    @Test fun expiryWithFinishTrackWaitsForSongEnd() {
        val s = SleepTimer.start(t0, 1, true)
        val (waiting, action) = SleepTimer.tick(s, t0 + 60_000)
        assertEquals(SleepAction.NONE, action)
        assertTrue(waiting.isActive && waiting.waitingForTrackToEnd)
        val (done, fade) = SleepTimer.onTrackEnded(waiting)
        assertEquals(SleepAction.FADE_OUT_NOW, fade)
        assertFalse(done.isActive)
    }

    @Test fun expiryWithoutFinishTrackFadesImmediately() {
        val s = SleepTimer.start(t0, 1, false)
        val (done, action) = SleepTimer.tick(s, t0 + 61_000)
        assertEquals(SleepAction.FADE_OUT_NOW, action)
        assertFalse(done.isActive)
    }

    @Test fun trackEndWhileTimerStillRunningDoesNothing() {
        val s = SleepTimer.start(t0, 10, true)
        val (same, action) = SleepTimer.onTrackEnded(s)
        assertEquals(SleepAction.NONE, action)
        assertEquals(s, same)
    }

    @Test fun extendPushesTargetAndClearsWaiting() {
        val waiting = SleepTimer.tick(SleepTimer.start(t0, 1, true), t0 + 61_000).first
        val extended = SleepTimer.extend(waiting, 15)
        assertFalse(extended.waitingForTrackToEnd)
        assertEquals(t0 + 60_000 + 15 * 60_000L, extended.targetMillis)
    }

    @Test fun extendOnInactiveTimerIsNoOp() {
        val idle = SleepTimerState()
        assertEquals(idle, SleepTimer.extend(idle, 15))
    }

    @Test fun cancelResets() = assertFalse(SleepTimer.cancel().isActive)
}
