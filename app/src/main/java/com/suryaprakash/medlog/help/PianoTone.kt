package com.suryaprakash.medlog.help

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * Synthesizes a sustained middle-C (261.63 Hz) concert grand piano note using additive synthesis.
 * This is a pure Kotlin implementation with no Android dependencies.
 */
object PianoTone {
    const val RATE = 44_100
    const val F0 = 261.63

    private const val DURATION = 4.0
    private const val B = 0.0004  // String inharmonicity
    private const val ATTACK_MS = 3
    private const val HAMMER_NOISE_MS = 15
    private const val FADEOUT_MS = 50
    private const val LIMITER_GAIN = 1.6
    private const val LIMITER_OUTPUT_PEAK = 0.95

    private val amplitudes = doubleArrayOf(
        1.0, 0.8, 0.6, 0.5, 0.35, 0.3, 0.2, 0.15, 0.12, 0.1, 0.08, 0.06, 0.05, 0.04
    )

    val note: ShortArray by lazy { render() }

    fun render(seconds: Double = DURATION): ShortArray {
        val numSamples = (seconds * RATE).toInt()
        val output = DoubleArray(numSamples)

        // Synthesize 14 partials
        for (n in 1..14) {
            val partial = synthesizePartial(n, seconds)
            for (i in 0 until numSamples) {
                if (i < partial.size) {
                    output[i] += partial[i]
                }
            }
        }

        // Add hammer noise (deterministic pseudo-random with fixed seed)
        val noise = synthesizeHammerNoise(numSamples)
        for (i in 0 until numSamples) {
            output[i] += noise[i]
        }

        // Apply soft limiter and normalize
        val limited = DoubleArray(numSamples)
        var maxVal = 0.0
        for (i in 0 until numSamples) {
            limited[i] = tanh(LIMITER_GAIN * output[i])
            maxVal = maxOf(maxVal, abs(limited[i]))
        }

        // Normalize to peak = 0.95 * Short.MAX_VALUE
        val scale = if (maxVal > 0) (LIMITER_OUTPUT_PEAK * Short.MAX_VALUE / maxVal) else 1.0

        // Apply fade-out at the end
        val fadeoutSamples = (FADEOUT_MS * RATE / 1000)
        for (i in 0 until numSamples) {
            val fadeMultiplier = if (i >= numSamples - fadeoutSamples) {
                (numSamples - i).toDouble() / fadeoutSamples
            } else {
                1.0
            }
            limited[i] *= fadeMultiplier * scale
        }

        // Convert to short array
        val result = ShortArray(numSamples)
        for (i in 0 until numSamples) {
            result[i] = limited[i].toInt().toShort()
        }

        return result
    }

    private fun synthesizePartial(n: Int, seconds: Double): DoubleArray {
        val numSamples = (seconds * RATE).toInt()
        val result = DoubleArray(numSamples)

        // Frequency with inharmonicity
        val freq = n * F0 * sqrt(1.0 + B * n * n)

        // Amplitude
        val amp = (1.0 / n.toDouble().pow(0.9)) * amplitudes[n - 1]

        // Decay time constant
        val tau = 3.2 / (1.0 + 0.55 * (n - 1))

        // Attack time in samples
        val attackSamples = (ATTACK_MS * RATE / 1000)

        // Synthesize two detuned strings
        val detune1 = freq * (1.0 - 0.0006)
        val detune2 = freq * (1.0 + 0.0006)

        for (i in 0 until numSamples) {
            val t = i.toDouble() / RATE

            // Attack envelope
            val attackEnv = if (i < attackSamples) {
                i.toDouble() / attackSamples
            } else {
                1.0
            }

            // Decay envelope
            val decayEnv = exp(-t / tau)

            // Combine two detuned strings for natural beating
            val phase1 = 2 * PI * detune1 * t
            val phase2 = 2 * PI * detune2 * t
            val wave = (sin(phase1) + sin(phase2)) / 2.0

            result[i] = wave * amp * attackEnv * decayEnv
        }

        return result
    }

    private fun synthesizeHammerNoise(numSamples: Int): DoubleArray {
        val result = DoubleArray(numSamples)
        val noiseSamples = (HAMMER_NOISE_MS * RATE / 1000)

        // Deterministic pseudo-random with fixed seed for reproducibility
        var seed = 0x12345678L

        // 1-pole lowpass filter state
        var filterState = 0.0
        val filterAlpha = 0.1

        for (i in 0 until minOf(noiseSamples, numSamples)) {
            // Linear congruential generator
            seed = (seed * 1103515245L + 12345L) and 0x7fffffffL
            val noiseVal = (seed.toDouble() / 0x7fffffffL) * 2.0 - 1.0

            // Apply 1-pole lowpass filter
            filterState = filterState + filterAlpha * (noiseVal - filterState)

            // Fade out hammer noise
            val fade = (noiseSamples - i).toDouble() / noiseSamples

            result[i] = filterState * 0.05 * fade
        }

        return result
    }
}
