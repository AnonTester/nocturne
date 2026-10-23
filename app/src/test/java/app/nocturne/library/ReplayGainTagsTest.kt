package app.nocturne.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReplayGainTagsTest {
    @Test fun parsesCommonGainFormats() {
        assertEquals(-6.5f, ReplayGainTags.parseDb("-6.50 dB")!!, 0.001f)
        assertEquals(3.1f, ReplayGainTags.parseDb("+3.1 dB")!!, 0.001f)
        assertEquals(-2.5f, ReplayGainTags.parseDb("−2,5 dB")!!, 0.001f)
        assertEquals(0f, ReplayGainTags.parseDb("0.00")!!, 0.001f)
        assertNull(ReplayGainTags.parseDb("n/a"))
    }

    @Test fun peakMustBePositive() {
        assertEquals(0.988f, ReplayGainTags.parsePeak("0.988")!!, 0.0001f)
        assertNull(ReplayGainTags.parsePeak("0.0"))
        assertNull(ReplayGainTags.parsePeak("-1"))
    }

    @Test fun trackGainWinsOverAlbumGain() {
        val g = ReplayGainTags.fromTags(
            listOf("replaygain_album_gain" to "-9 dB", "REPLAYGAIN_TRACK_GAIN" to "-7.25 dB", "REPLAYGAIN_TRACK_PEAK" to "0.95"),
        )!!
        assertEquals(-7.25f, g.gainDb, 0.001f)
        assertEquals(0.95f, g.peak!!, 0.001f)
    }

    @Test fun albumGainIsUsedWhenThereIsNoTrackGain() {
        assertEquals(-9f, ReplayGainTags.fromTags(listOf("REPLAYGAIN_ALBUM_GAIN" to "-9.00 dB"))!!.gainDb, 0.001f)
    }

    @Test fun opusR128GainIsShiftedToTheReplayGainReference() {
        // Q7.8: -1280 = -5 dB relative to -23 LUFS = 0 dB relative to -18 LUFS
        assertEquals(0f, ReplayGainTags.fromTags(listOf("R128_TRACK_GAIN" to "-1280"))!!.gainDb, 0.001f)
        assertEquals(-3f, ReplayGainTags.fromTags(listOf("R128_TRACK_GAIN" to "-2048"))!!.gainDb, 0.001f)
    }

    @Test fun missingOrBrokenTagsGiveNothing() {
        assertNull(ReplayGainTags.fromTags(emptyList()))
        assertNull(ReplayGainTags.fromTags(listOf("TITLE" to "x", "REPLAYGAIN_TRACK_GAIN" to "loud")))
    }
}
