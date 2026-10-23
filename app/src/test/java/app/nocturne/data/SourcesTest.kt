package app.nocturne.data

import app.nocturne.library.SmbUri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SourcesTest {
    private val extra = SmbSource("smb-1", "Comedy", SmbConfig("nas.local", "fun", "Stand-up", "u", "WORKGROUP", 15), includeInMain = false, lastScan = 42)

    @Test fun sourcesSurviveJsonRoundTrip() {
        val back = decodeSources(encodeSources(listOf(extra, extra.copy(id = "smb-2", name = "Review", includeInMain = true))))
        assertEquals(2, back.size)
        assertEquals(extra, back[0])
        assertTrue(back[1].includeInMain)
    }

    @Test fun missingOrBrokenJsonGivesNoSources() {
        assertEquals(emptyList<SmbSource>(), decodeSources(null))
        assertEquals(emptyList<SmbSource>(), decodeSources(""))
        assertEquals(emptyList<SmbSource>(), decodeSources("not json"))
    }

    @Test fun mainShareIsAlwaysFirstAndMerged() {
        val s = AppSettings(smb = SmbConfig("h", "s"), smbExtra = listOf(extra))
        assertEquals(listOf(Source.SAMBA, "smb-1"), s.smbSources.map { it.id })
        assertTrue(s.smbSources.first().includeInMain)
    }

    @Test fun mergedSourcesFollowTheFlags() {
        val s = AppSettings(smbExtra = listOf(extra, extra.copy(id = "smb-2", includeInMain = true)))
        assertEquals(listOf(Source.SAMBA, "smb-2", Source.LOCAL), s.mergedSources)
        assertEquals(listOf(Source.SAMBA, "smb-2"), s.copy(deviceInMain = false).mergedSources)
    }

    @Test fun sourceNamesFallBackToTheId() {
        val s = AppSettings(smbExtra = listOf(extra))
        assertEquals("Comedy", s.sourceName("smb-1"))
        assertEquals("This device", s.sourceName(Source.LOCAL))
        assertEquals("gone", s.sourceName("gone"))
    }

    @Test fun mainShareTrackIdsKeepTheirOriginalForm() {
        assertEquals("smb:Album/a.mp3", SmbUri.trackId(Source.SAMBA, "Album/a.mp3"))
        assertEquals("smb:smb-1:Album/a.mp3", SmbUri.trackId("smb-1", "Album/a.mp3"))
        assertNotEquals(SmbUri.trackId(Source.SAMBA, "x"), SmbUri.trackId("smb-1", "x"))
    }

    @Test fun newShareIdsAreUniqueAndNotReserved() {
        val a = Source.newShareId(); val b = Source.newShareId()
        assertNotEquals(a, b)
        assertTrue(Source.isSmb(a) && a != Source.SAMBA)
    }
}
