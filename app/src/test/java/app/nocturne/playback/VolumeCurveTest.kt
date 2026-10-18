package app.nocturne.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class VolumeCurveTest {
    @Test fun fullSliderIsUnityGain() = assertEquals(1f, VolumeCurve.gain(100f, false, false), 1e-6f)

    @Test fun curveIsPerceptual() = assertEquals(0.2176f, VolumeCurve.gain(50f, false, false), 1e-3f)

    @Test fun whisperCapsAtTwentyPercent() = assertEquals(0.2f, VolumeCurve.gain(100f, true, false), 1e-6f)

    @Test fun mutedIsSilent() = assertEquals(0f, VolumeCurve.gain(80f, false, true), 0f)

    @Test fun sliderIsClamped() {
        assertEquals(1f, VolumeCurve.gain(250f, false, false), 1e-6f)
        assertEquals(0f, VolumeCurve.gain(-5f, false, false), 1e-6f)
    }

    @Test fun decibelReadoutMatchesWebUi() {
        assertEquals(0, VolumeCurve.decibels(100f, false))
        assertEquals(-14, VolumeCurve.decibels(100f, true))
        assertEquals(-26, VolumeCurve.decibels(25f, false))
    }

    @Test fun stepRoundsToTenthsAndClamps() {
        assertEquals(25.5f, VolumeCurve.step(25f, 0.5f), 1e-6f)
        assertEquals(0f, VolumeCurve.step(0.5f, -1f), 1e-6f)
        assertEquals(100f, VolumeCurve.step(99.5f, 1f), 1e-6f)
    }
}
