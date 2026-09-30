package com.suryaprakash.medlog

import com.suryaprakash.medlog.help.PianoTone
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class PianoToneTest {
    @Test
    fun testRenderLength() {
        val note = PianoTone.render()
        val expected = (4.0 * PianoTone.RATE).toInt()
        assertEquals("Note should be 4 seconds long", expected, note.size)
    }

    @Test
    fun testRenderAmplitude() {
        val note = PianoTone.render()
        var maxAbs = 0.0
        for (sample in note) {
            maxAbs = maxOf(maxAbs, abs(sample.toDouble()))
        }

        // Max should be <= Short.MAX_VALUE
        assertTrue("Max amplitude should be <= Short.MAX_VALUE", maxAbs <= Short.MAX_VALUE)

        // Max should be >= 0.9 * Short.MAX_VALUE (normalized to this)
        val threshold = 0.9 * Short.MAX_VALUE
        assertTrue("Max amplitude should be >= 0.9 * Short.MAX_VALUE (was $maxAbs)", maxAbs >= threshold)
    }

    @Test
    fun testFundamentalFrequency() {
        val note = PianoTone.render()

        // Extract samples from 0.1s to 0.6s for stable oscillation
        val startSample = (0.1 * PianoTone.RATE).toInt()
        val endSample = (0.6 * PianoTone.RATE).toInt()

        // Use Goertzel algorithm to measure energy at specific frequencies
        val energyAtF0 = goertzelEnergy(note, PianoTone.F0, startSample, endSample)
        val energyAt440 = goertzelEnergy(note, 440.0, startSample, endSample)
        val energyAt2400 = goertzelEnergy(note, 2400.0, startSample, endSample)

        // F0 should be > 10x energy at 440 Hz
        assertTrue("Energy at 261.63 Hz should be > 10x energy at 440 Hz (F0=$energyAtF0, 440=$energyAt440)",
            energyAtF0 > energyAt440 * 10)

        // F0 should be > 10x energy at 2400 Hz
        assertTrue("Energy at 261.63 Hz should be > 10x energy at 2400 Hz (F0=$energyAtF0, 2400=$energyAt2400)",
            energyAtF0 > energyAt2400 * 10)
    }

    @Test
    fun testSustainedDecay() {
        val note = PianoTone.render()

        // RMS of early part (0.1-0.6s) for comparison
        val earlyStart = (0.1 * PianoTone.RATE).toInt()
        val earlyEnd = (0.6 * PianoTone.RATE).toInt()
        val earlyRMS = calculateRMS(note, earlyStart, earlyEnd)

        // RMS of late part before fade (3.0-3.5s)
        val lateStart = (3.0 * PianoTone.RATE).toInt()
        val lateEnd = (3.5 * PianoTone.RATE).toInt()
        val lateRMS = calculateRMS(note, lateStart, lateEnd)

        // Late RMS should be > 5% of early RMS (sustained)
        val threshold = 0.05 * earlyRMS
        assertTrue("Late RMS ($lateRMS) should be > 5% of early RMS ($earlyRMS, threshold=$threshold)",
            lateRMS > threshold)
    }

    private fun goertzelEnergy(samples: ShortArray, targetFreq: Double, startIdx: Int, endIdx: Int): Double {
        val numSamples = endIdx - startIdx
        val k = targetFreq * numSamples / PianoTone.RATE
        val w = 2 * PI * k / numSamples
        val coeff = 2 * cos(w)

        var s0 = 0.0
        var s1 = 0.0
        var s2 = 0.0

        for (i in startIdx until endIdx) {
            val sample = samples[i].toDouble()
            s0 = sample + coeff * s1 - s2
            s2 = s1
            s1 = s0
        }

        val real = s1 - s2 * cos(w)
        val imag = s2 * sin(w)

        return real * real + imag * imag
    }

    private fun calculateRMS(samples: ShortArray, startIdx: Int, endIdx: Int): Double {
        var sum = 0.0
        for (i in startIdx until endIdx) {
            val sample = samples[i].toDouble()
            sum += sample * sample
        }
        val count = endIdx - startIdx
        return sqrt(sum / count)
    }
}
