package app.nocturne.library

import java.nio.ByteBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackRamCacheTest {
    private fun file(bytes: Int) = ByteBuffer.allocateDirect(bytes).also { b -> repeat(bytes) { b.put(it, (it % 251).toByte()) } }

    @Test fun keepsThreeFilesAndDropsTheLeastRecentlyUsed() {
        val c = TrackRamCache(maxFileBytes = 1000, budgetBytes = 10_000, maxEntries = 3)
        for (k in listOf("a", "b", "c")) assertTrue(c.put(k, file(100)))
        c.get("a") // a is now newer than b
        assertTrue(c.put("d", file(100)))
        assertTrue(c.contains("a")); assertFalse(c.contains("b")); assertTrue(c.contains("c")); assertTrue(c.contains("d"))
    }

    @Test fun theKeptEntryIsNeverTheVictim() {
        val c = TrackRamCache(maxFileBytes = 1000, budgetBytes = 10_000, maxEntries = 2)
        c.put("playing", file(100)); c.put("x", file(100))
        c.put("y", file(100), keep = "playing")
        assertTrue(c.contains("playing")); assertFalse(c.contains("x")); assertTrue(c.contains("y"))
    }

    @Test fun budgetEvictsOldFiles() {
        val c = TrackRamCache(maxFileBytes = 600, budgetBytes = 1000, maxEntries = 3)
        c.put("a", file(500)); c.put("b", file(500)); c.put("c", file(500))
        assertEquals(1000L, c.bytes())
        assertFalse(c.contains("a"))
    }

    @Test fun tooBigFilesAreRefused() {
        val c = TrackRamCache(maxFileBytes = 100, budgetBytes = 10_000, maxEntries = 3)
        assertFalse(c.fits(101)); assertTrue(c.fits(100)); assertFalse(c.fits(0))
        assertFalse(c.put("big", file(101)))
    }

    @Test fun disablingClearsAndBlocks() {
        val c = TrackRamCache()
        c.put("a", file(10))
        c.enabled = false
        assertNull(c.get("a")); assertFalse(c.put("b", file(10)))
        c.enabled = true
        assertFalse(c.contains("a"))
    }

    @Test fun clearExceptKeepsOne() {
        val c = TrackRamCache()
        c.put("a", file(10)); c.put("b", file(10))
        c.clearExcept("b")
        assertFalse(c.contains("a")); assertTrue(c.contains("b"))
    }

    @Test fun getGivesIndependentPositions() {
        val c = TrackRamCache()
        c.put("a", file(10))
        val x = c.get("a")!!; x.position(5)
        assertEquals(0, c.get("a")!!.position())
        assertNotNull(x)
    }
}
