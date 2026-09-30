package com.suryaprakash.medlog

import com.suryaprakash.medlog.clinical.DangerRules
import com.suryaprakash.medlog.clinical.Level
import com.suryaprakash.medlog.clinical.PersonContext
import com.suryaprakash.medlog.nlu.Fact
import com.suryaprakash.medlog.nlu.Reading
import com.suryaprakash.medlog.nlu.Source
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OralSafetyTest {
    private val person = PersonContext(ageYears = 78, onBloodThinner = false)

    private fun evaluate(facts: Map<String, Fact> = emptyMap(), sugar: Double = 45.0) = DangerRules.evaluate(
        problemId = "low_sugar",
        facts = facts,
        readings = listOf(Reading("sugar", sugar)),
        recent = emptyList(),
        person = person,
    )

    private fun yes(key: String) = mapOf(key to Fact(true, Source.ASKED))

    @Test fun unsafeConsciousnessOrSwallowingSuppressesOralSugar() {
        val unsafeFacts = listOf(
            yes("hardToWake"),
            yes("lostResponse"),
            yes("lostConsciousness"),
            yes("cantSwallowWater"),
            mapOf("swallowWater" to Fact(false, Source.ASKED)),
        )

        for (facts in unsafeFacts) {
            val triage = evaluate(facts)
            assertEquals("$facts", Level.RED, triage.level)
            assertNull("$facts", triage.firstAid)
            assertFalse("$facts", triage.say.contains("Take sugar now", ignoreCase = true))
            assertEquals("This could be serious. Get help now.", triage.say)
        }
    }

    @Test fun lowSugarWithNoRecordedUnsafeFactKeepsOralSugarAdvice() {
        val triage = evaluate(mapOf("confusion" to Fact(false, Source.ASKED)))
        assertEquals(Level.RED, triage.level)
        assertEquals(DangerRules.SUGAR_AID, triage.firstAid)
        assertTrue(triage.say.contains("Take sugar now"))
    }

    @Test fun normalSugarRemainsUnchanged() {
        val triage = evaluate(sugar = 100.0)
        assertEquals(Level.GREEN, triage.level)
        assertNull(triage.firstAid)
        assertEquals("Saved.", triage.say)
    }
}
