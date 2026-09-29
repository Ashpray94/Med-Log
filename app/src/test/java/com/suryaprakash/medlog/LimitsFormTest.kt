package com.suryaprakash.medlog

import com.suryaprakash.medlog.clinical.Band
import com.suryaprakash.medlog.clinical.DangerRules
import com.suryaprakash.medlog.clinical.Level
import com.suryaprakash.medlog.clinical.Limits
import com.suryaprakash.medlog.clinical.LimitsForm
import com.suryaprakash.medlog.clinical.Line
import com.suryaprakash.medlog.clinical.PersonContext
import com.suryaprakash.medlog.data.CarePlan
import com.suryaprakash.medlog.nlu.Reading
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LimitsFormTest {
    private fun t(vararg p: Pair<Line, String>) = mapOf(*p)
    private fun err(key: String, texts: Map<Line, String>) = LimitsForm.validate(LimitsForm.spec(key), texts)

    @Test fun suggestUsesMedianPlusMargins() {
        val b = LimitsForm.suggest("bpSys", listOf(90.0, 100.0, 100.0, 500.0, 110.0))!!   // median 100, the odd one out does not matter
        assertEquals(110.0, b.amberHigh!!, 0.01); assertEquals(120.0, b.redHigh!!, 0.01)
        assertEquals(95.0, b.amberLow!!, 0.01); assertEquals(90.0, b.redLow!!, 0.01)
    }

    @Test fun suggestNeedsEnoughReadingsAndOnlyFillsThatMeasuresLines() {
        assertNull(LimitsForm.suggest("bpSys", listOf(120.0, 130.0)))
        val s = LimitsForm.suggest("spo2", listOf(99.0, 99.0, 98.0, 100.0))!!   // median 99, capped at 100 (would not exceed anyway)
        assertNull(s.amberHigh); assertNull(s.redHigh)
        assertEquals(94.0, s.amberLow!!, 0.01); assertEquals(89.0, s.redLow!!, 0.01)
        val temp = LimitsForm.suggest("temp", listOf(98.4, 98.6, 98.6))!!
        assertNull(temp.amberLow); assertEquals(100.6, temp.amberHigh!!, 0.01); assertEquals(102.1, temp.redHigh!!, 0.01)
        assertEquals(104.0, LimitsForm.suggest("temp", listOf(101.0, 101.0, 101.0))!!.redHigh!!, 0.01)   // never above 104
    }

    @Test fun validateChecksOrderRangeAndNumbers() {
        assertNull(err("bpSys", t(Line.AMBER_HIGH to "150", Line.RED_HIGH to "170", Line.AMBER_LOW to "95", Line.RED_LOW to "85")))
        assertNotNull(err("bpSys", t(Line.AMBER_HIGH to "150", Line.RED_HIGH to "140")))          // red not beyond amber
        assertNotNull(err("spo2", t(Line.AMBER_LOW to "88", Line.RED_LOW to "92")))
        assertNotNull(err("bpSys", t(Line.AMBER_LOW to "160", Line.AMBER_HIGH to "150")))         // low above high
        assertNotNull(err("bpSys", t(Line.AMBER_HIGH to "900")))                                  // out of range
        assertNotNull(err("sugar", t(Line.AMBER_LOW to "abc")))
        assertNull(err("sugar", t(Line.AMBER_LOW to "", Line.RED_LOW to "")))                     // all empty = nothing to check
        assertNotNull(err("temp", t(Line.RED_HIGH to "106")))                                     // 104 is always red
    }

    @Test fun temperatureAcceptsCelsius() {
        assertEquals(102.2, LimitsForm.parse("temp", "39")!!, 0.01)
        assertEquals(101.0, LimitsForm.parse("temp", "101")!!, 0.01)
        assertNull(err("temp", t(Line.AMBER_HIGH to "38.5", Line.RED_HIGH to "39.5")))            // 101.3 and 103.1
        assertNotNull(err("temp", t(Line.AMBER_HIGH to "39.5", Line.RED_HIGH to "38.5")))
    }

    @Test fun toLimitsKeepsOnlyWhatWasTyped() {
        val l = LimitsForm.toLimits(mapOf("spo2" to t(Line.AMBER_LOW to "92", Line.RED_LOW to ""), "sugar" to t(Line.AMBER_LOW to "")), true, "helper", 5L)
        assertEquals(setOf("spo2"), l.bands.keys); assertEquals(92.0, l.band("spo2")!!.amberLow!!, 0.0)
        assertNull(l.band("spo2")!!.redLow)
        assertTrue(l.doctorConfirmed); assertEquals("helper", l.setBy); assertEquals(5L, l.setAt)
        // it survives the care plan's JSON
        assertEquals(92.0, CarePlan.parse(CarePlan(limits = l).toJson()).limits.band("spo2")!!.amberLow!!, 0.0)
    }

    @Test fun textsAndSummaries() {
        assertEquals("92", LimitsForm.text(Band(amberLow = 92.0), Line.AMBER_LOW))
        assertEquals("100.4", LimitsForm.text(Band(amberHigh = 100.4), Line.AMBER_HIGH))
        assertEquals("", LimitsForm.text(null, Line.RED_LOW))
        assertEquals("Not set", LimitsForm.summary(Limits()))
        assertEquals("Set", LimitsForm.summary(Limits(mapOf("spo2" to Band(amberLow = 92.0)))))
        assertEquals("Set · doctor agreed", LimitsForm.summary(Limits(mapOf("spo2" to Band(amberLow = 92.0)), doctorConfirmed = true)))
        assertEquals("Not set", LimitsForm.summary(Limits(mapOf("spo2" to Band()), doctorConfirmed = true)))
        assertEquals("general: 180", LimitsForm.general("bpSys", Line.AMBER_HIGH, 40, false))
        assertEquals("general: none", LimitsForm.general("bpSys", Line.AMBER_HIGH, 70, false))
        assertEquals("general: 89", LimitsForm.general("spo2", Line.AMBER_LOW, 70, false))
        assertEquals("general: 4", LimitsForm.general("vomit", Line.AMBER_HIGH, 50, true))
    }

    @Test fun needsLimitLines() {
        assertEquals("Your helper hasn't set blood pressure limits yet.", LimitsForm.needsLimitLine("bp"))
        assertEquals("Your helper hasn't set oxygen limits yet.", LimitsForm.needsLimitLine("spo2"))
        assertNull(LimitsForm.needsLimitLine(null))
    }

    @Test fun savedLimitsChangeTheRulesAndClearTheGreyLine() {
        val r = listOf(Reading("spo2", 91.0, unit = "%"))
        val before = DangerRules.evaluate(null, emptyMap(), r, emptyList(), PersonContext(70, false))
        assertEquals("spo2", before.needsLimit)
        val l = LimitsForm.toLimits(mapOf("spo2" to t(Line.AMBER_LOW to "90", Line.RED_LOW to "85")), true, "helper", 1L)
        val after = DangerRules.evaluate(null, emptyMap(), r, emptyList(), PersonContext(70, false, limits = l))
        assertNull(after.needsLimit); assertEquals(Level.GREEN, after.level)
    }
}
