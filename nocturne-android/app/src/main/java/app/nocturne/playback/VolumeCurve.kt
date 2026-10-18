package app.nocturne.playback

import kotlin.math.log10
import kotlin.math.pow

/** Bedside volume model: perceptual gain curve with an optional whisper ceiling. */
object VolumeCurve {
    const val EXPONENT = 2.2
    const val WHISPER_CEILING = 0.20f

    fun gain(slider: Float, whisper: Boolean, muted: Boolean): Float {
        if (muted) return 0f
        val normalized = slider.coerceIn(0f, 100f) / 100f
        var g = normalized.toDouble().pow(EXPONENT).toFloat()
        if (whisper) g *= WHISPER_CEILING
        return g.coerceIn(0f, 1f)
    }

    /** dB readout shown under the slider (same maths as the web UI). */
    fun decibels(slider: Float, whisper: Boolean): Int {
        val normalized = maxOf(0.001f, slider / 100f)
        val effective = normalized.toDouble().pow(EXPONENT) * if (whisper) WHISPER_CEILING.toDouble() else 1.0
        return Math.round(20 * log10(effective)).toInt()
    }

    fun step(current: Float, delta: Float): Float =
        (Math.round((current + delta) * 10f) / 10f).coerceIn(0f, 100f)
}
