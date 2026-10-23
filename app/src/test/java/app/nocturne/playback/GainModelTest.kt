package app.nocturne.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class GainModelTest {
    @Test fun trackGainPlusOffset() {
        assertEquals(-4f, GainModel.levelDb(-6f, -6f, 2f), 0.001f)
    }

    @Test fun unknownTracksUseTheFallback() {
        assertEquals(-5f, GainModel.levelDb(null, -7f, 2f), 0.001f)
    }

    @Test fun levelIsLimited() {
        assertEquals(24f, GainModel.levelDb(40f, 0f, 0f), 0.001f)
        assertEquals(-24f, GainModel.levelDb(-40f, 0f, 0f), 0.001f)
    }

    @Test fun volumeIsGainTimesFactorButNeverAboveOne() {
        assertEquals(0.05f, GainModel.volume(0.1f, -6.0206f), 0.0005f)
        assertEquals(1f, GainModel.volume(0.9f, 12f), 0.0001f)
        assertEquals(0.3f, GainModel.volume(0.3f, null), 0.0001f)
        assertEquals(0f, GainModel.volume(0f, 12f), 0.0001f)
    }

    @Test fun medianIsOnlyTrustedOnceEnoughTracksAreKnown() {
        assertEquals(GainModel.DEFAULT_FALLBACK_DB, GainModel.fallbackDb(-9f, 5), 0.001f)
        assertEquals(GainModel.DEFAULT_FALLBACK_DB, GainModel.fallbackDb(null, 500), 0.001f)
        assertEquals(-9f, GainModel.fallbackDb(-9f, GainModel.MIN_KNOWN_FOR_MEDIAN), 0.001f)
    }
}
