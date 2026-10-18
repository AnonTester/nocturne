package app.nocturne.playback

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShuffleEngineTest {
    private val ids = (1..100).map { "t$it" }

    @Test fun neverRepeatsWithinExclusionWindow() {
        val engine = ShuffleEngine(Random(42))
        var current: String? = null
        val played = ArrayList<String>()
        repeat(60) {
            val next = engine.next(ids, current)!!
            current = next
            played.add(next)
        }
        // window = min(60% of 100, 60) = 60 -> the first 60 picks are all distinct
        assertEquals(60, played.toSet().size)
    }

    @Test fun neverReturnsCurrentWhenAlternativesExist() {
        val engine = ShuffleEngine(Random(1))
        val small = listOf("a", "b")
        var current: String? = "a"
        repeat(50) {
            val n = engine.next(small, current)!!
            assertNotEquals(current, n)
            current = n
        }
    }

    @Test fun singleTrackLibraryReturnsIt() {
        assertEquals("only", ShuffleEngine().next(listOf("only"), "only"))
    }

    @Test fun emptyLibraryReturnsNull() {
        assertNull(ShuffleEngine().next(emptyList(), null))
    }

    @Test fun previousWalksHistoryBackThenForwardAgain() {
        val engine = ShuffleEngine(Random(7))
        val a = engine.next(ids, null)!!
        val b = engine.next(ids, a)!!
        val c = engine.next(ids, b)!!
        val avail = ids.toSet()
        assertEquals(b, engine.previous(avail))
        assertEquals(a, engine.previous(avail))
        assertNull(engine.previous(avail))
        assertEquals(b, engine.next(ids, a))
        assertEquals(c, engine.next(ids, b))
    }

    @Test fun playingNewTrackDropsForwardHistory() {
        val engine = ShuffleEngine(Random(3))
        engine.recordPlay("a"); engine.recordPlay("b"); engine.recordPlay("c")
        engine.previous(setOf("a", "b", "c"))
        engine.recordPlay("x")
        assertFalse(engine.hasPrevious().not())
        assertEquals("b", engine.previous(setOf("a", "b", "c", "x")))
    }

    @Test fun historyIsCapped() {
        val engine = ShuffleEngine()
        repeat(400) { engine.recordPlay("t$it") }
        var steps = 0
        val avail = (0 until 400).map { "t$it" }.toSet()
        while (engine.previous(avail) != null) steps++
        assertEquals(ShuffleEngine.MAX_HISTORY - 1, steps)
    }

    @Test fun previousSkipsIdsNoLongerInLibrary() {
        val engine = ShuffleEngine()
        engine.recordPlay("a"); engine.recordPlay("gone"); engine.recordPlay("c")
        assertEquals("a", engine.previous(setOf("a", "c")))
    }

    @Test fun resetClearsHistory() {
        val engine = ShuffleEngine()
        engine.recordPlay("a"); engine.recordPlay("b")
        engine.reset()
        assertFalse(engine.hasPrevious())
        assertTrue(engine.previous(setOf("a", "b")) == null)
    }
}
