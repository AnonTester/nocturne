package app.nocturne.library

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.tan

/**
 * Integrated loudness after ITU-R BS.1770-4 / EBU R128: K-weighting, 400 ms blocks with 75 % overlap, absolute gate at
 * -70 LUFS and relative gate 10 LU below the ungated mean. Pure maths, no Android types, so it is unit-tested.
 *
 * Feed interleaved 16-bit PCM. Several separate stretches of one track can be analysed together: call [newSegment]
 * between them (blocks never straddle two stretches) and [skip] to run decoder warm-up samples through the filters
 * without counting them.
 */
class Bs1770(private val sampleRate: Int, private val channels: Int) {
    private class Biquad(val b0: Double, val b1: Double, val b2: Double, val a1: Double, val a2: Double) {
        private var z1 = 0.0
        private var z2 = 0.0
        fun reset() { z1 = 0.0; z2 = 0.0 }
        fun process(x: Double): Double {
            val y = b0 * x + z1
            z1 = b1 * x - a1 * y + z2
            z2 = b2 * x - a2 * y
            return y
        }
    }

    private val shelf: Array<Biquad> = Array(channels) { shelfFilter(sampleRate) }
    private val highPass: Array<Biquad> = Array(channels) { highPassFilter(sampleRate) }
    private val weights = DoubleArray(channels) { channelWeight(channels, it) }

    private val hopFrames = (sampleRate * 0.1).toInt().coerceAtLeast(1)
    private var hopFill = 0
    private var hopEnergy = 0.0 // sum over channels of weight * sum of squares inside the current hop
    private val recentHops = ArrayDeque<Double>() // the last four completed hops
    private val blockEnergies = ArrayList<Double>() // mean-square energy of every 400 ms block
    private var skipFrames = 0

    /** Largest absolute sample seen so far, as a linear value (1.0 = full scale). */
    var peak = 0f
        private set

    /** Counts the next [frames] as warm-up: they go through the filters but not into the loudness. */
    fun skip(frames: Int) { skipFrames = frames }

    /** Starts a new, unrelated stretch of audio (after a seek): filters and partial blocks are reset. */
    fun newSegment() {
        shelf.forEach { it.reset() }
        highPass.forEach { it.reset() }
        hopFill = 0; hopEnergy = 0.0; recentHops.clear(); skipFrames = 0
    }

    fun feed(pcm: ShortArray, frames: Int) {
        var i = 0
        for (f in 0 until frames) {
            val counted = skipFrames <= 0
            if (!counted) skipFrames--
            var frameEnergy = 0.0
            for (ch in 0 until channels) {
                val s = pcm[i++] / 32768.0
                val a = abs(s).toFloat()
                if (a > peak) peak = a
                val y = highPass[ch].process(shelf[ch].process(s))
                frameEnergy += weights[ch] * y * y
            }
            if (!counted) continue
            hopEnergy += frameEnergy
            if (++hopFill == hopFrames) {
                recentHops.addLast(hopEnergy)
                if (recentHops.size > 4) recentHops.removeFirst()
                if (recentHops.size == 4) blockEnergies.add(recentHops.sum() / (hopFrames * 4.0))
                hopFill = 0; hopEnergy = 0.0
            }
        }
    }

    /** Gated integrated loudness in LUFS, or null when there was no audible block at all. */
    fun loudness(): Double? {
        val absolute = blockEnergies.filter { lufs(it) > ABSOLUTE_GATE }
        if (absolute.isEmpty()) return null
        val relativeThreshold = lufs(absolute.average()) - RELATIVE_GATE
        val kept = absolute.filter { lufs(it) > relativeThreshold }
        return lufs((if (kept.isEmpty()) absolute else kept).average())
    }

    companion object {
        const val ABSOLUTE_GATE = -70.0
        const val RELATIVE_GATE = 10.0
        /** ReplayGain 2.0 reference loudness. */
        const val REFERENCE_LUFS = -18.0

        private fun lufs(energy: Double) = -0.691 + 10 * log10(energy)

        /** BS.1770 stage 1: high shelf (+4 dB above ~1.7 kHz), coefficients derived for any sample rate. */
        private fun shelfFilter(fs: Int): Biquad {
            val f0 = 1681.974450955533
            val g = 3.999843853973347
            val q = 0.7071752369554196
            val k = tan(PI * f0 / fs)
            val vh = 10.0.pow(g / 20.0)
            val vb = vh.pow(0.4996667741545416)
            val a0 = 1.0 + k / q + k * k
            return Biquad(
                (vh + vb * k / q + k * k) / a0, 2.0 * (k * k - vh) / a0, (vh - vb * k / q + k * k) / a0,
                2.0 * (k * k - 1.0) / a0, (1.0 - k / q + k * k) / a0,
            )
        }

        /** BS.1770 stage 2: "RLB" high-pass at ~38 Hz. */
        private fun highPassFilter(fs: Int): Biquad {
            val f0 = 38.13547087602444
            val q = 0.5003270373238773
            val k = tan(PI * f0 / fs)
            val a0 = 1.0 + k / q + k * k
            return Biquad(1.0, -2.0, 1.0, 2.0 * (k * k - 1.0) / a0, (1.0 - k / q + k * k) / a0)
        }

        /** Mono counts as dual mono (it is played on both speakers); 5.1 uses the standard surround weights, LFE is ignored. */
        private fun channelWeight(channels: Int, index: Int): Double = when {
            channels == 1 -> 2.0
            channels >= 6 && index == 3 -> 0.0
            channels >= 6 && (index == 4 || index == 5) -> 1.41
            else -> 1.0
        }

        /** ReplayGain-style gain in dB that brings a track of [lufs] loudness to the reference level. */
        fun gainFor(lufs: Double): Float = (REFERENCE_LUFS - lufs).toFloat()
    }
}
