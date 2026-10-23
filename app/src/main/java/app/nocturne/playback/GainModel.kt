package app.nocturne.playback

import kotlin.math.pow

/**
 * Loudness levelling: how much a track is turned up or down so a mixed queue plays at one level.
 * ExoPlayer's volume can only attenuate (max 1.0), so applying a gain can never clip; the only limit is that the
 * final volume cannot exceed 1.0 (a boost is cut off when the slider is near the top).
 */
object GainModel {
    const val MIN_OFFSET_DB = -12f
    const val MAX_OFFSET_DB = 12f
    /** Assumed gain of a typical modern master until enough tracks are known to take the library median. */
    const val DEFAULT_FALLBACK_DB = -6f
    /** The median is only trusted once this many tracks have a known gain. */
    const val MIN_KNOWN_FOR_MEDIAN = 20
    private const val LIMIT_DB = 24f

    /** dB applied on top of the user's volume for a track whose own gain is [gainDb] (null = unknown -> [fallbackDb]). */
    fun levelDb(gainDb: Float?, fallbackDb: Float, offsetDb: Float): Float =
        ((gainDb ?: fallbackDb) + offsetDb).coerceIn(-LIMIT_DB, LIMIT_DB)

    fun linear(db: Float): Float = 10f.pow(db / 20f)

    /** Final ExoPlayer volume: the user's gain times the levelling factor, never above 1. */
    fun volume(userGain: Float, levelDb: Float?): Float =
        (userGain * (levelDb?.let(::linear) ?: 1f)).coerceIn(0f, 1f)

    /** Median to assume for untracked tracks: the library's median once known well enough, else a typical value. */
    fun fallbackDb(median: Float?, known: Int): Float =
        if (median != null && known >= MIN_KNOWN_FOR_MEDIAN) median else DEFAULT_FALLBACK_DB
}
