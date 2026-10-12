package com.suryaprakash.medlog

import com.suryaprakash.medlog.clinical.DangerRules
import com.suryaprakash.medlog.clinical.Level
import com.suryaprakash.medlog.clinical.PersonContext
import com.suryaprakash.medlog.clinical.RecentNote
import com.suryaprakash.medlog.nlu.Parser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Danger-sign test set. Every red case must come out RED (recall first).
 * These are written cases, not real patients.
 */
class TriageTest {
    private val p = Parser(CAT)
    private val elder = PersonContext(ageYears = 78, onBloodThinner = false)
    private val now = LocalDateTime.of(2026, 9, 25, 16, 0)

    private fun level(text: String, focus: String? = null, person: PersonContext = elder, recent: List<RecentNote> = emptyList()): Level {
        val r = p.parse(text, focus, emptyList(), now, ZoneId.of("Asia/Kolkata"))
        val m = r.main!!
        return DangerRules.evaluate(m.problemId, m.facts, r.readings, recent, person).level
    }

    @Test fun mustBeRed() {
        val red = listOf(
            "chest pain spreading to my jaw" to null,
            "chest pain and sweating" to null,
            "tight chest and I can't breathe" to null,
            "her face is drooping on one side" to null,
            "his speech is slurred and arm is weak" to null,
            "I vomited blood" to null,
            "vomit looks like coffee grounds" to "vomiting",
            "black sticky stool" to null,
            "I fainted in the bathroom" to null,
            "worst headache ever, it came suddenly" to null,
            "lips swelling after the tablet" to "allergic_reaction",
            "I fell and couldn't get up" to "fall",
            "sugar is 45" to "low_sugar",
            "fever 104 degrees" to "fever",
            "fever and confused" to "fever",
            "bleeding won't stop" to "bleeding",
            "hard to breathe even at rest" to "breathless",
            "he had a fit" to null,
            "choking on food" to null,
        )
        for ((text, focus) in red) assertEquals(text, Level.RED, level(text, focus))
    }

    @Test fun fallWithHeadInjuryOnThinnerIsRed() {
        val onThinner = elder.copy(onBloodThinner = true)
        assertEquals(Level.RED, level("I fell and hit my head", "fall", onThinner))
        assertEquals(Level.AMBER, level("I fell and hit my head", "fall", elder))
    }

    @Test fun mustBeAmber() {
        assertEquals(Level.AMBER, level("vomiting, can't keep water down", "vomiting"))
        assertEquals(Level.AMBER, level("cough for 4 weeks", "cough"))
        // over 55 a BP number alone is not judged until the helper sets limits (W2), so this is a younger person
        assertEquals(Level.AMBER, level("bp is 190 by 100", "high_bp", person = elder.copy(ageYears = 45)))
        assertEquals(Level.AMBER, level("mild chest pain", "chest_pain"))
        assertEquals(Level.AMBER, level("fever 100.8", "fever"))
        assertEquals(Level.AMBER, level("only one leg is swollen and painful", "swollen_ankles"))
    }

    @Test fun lowOxygenIsRedForAYoungerPerson() {
        // changed with W2: for over 55 the same number is AMBER until the helper sets a red line (see ClinicalRulesTest)
        assertEquals(Level.RED, level("oxygen is 86", "low_oxygen", person = elder.copy(ageYears = 45)))
        assertEquals(Level.AMBER, level("oxygen is 86", "low_oxygen"))
    }

    @Test fun manyVomitsInADayIsAmber() {
        val t = System.currentTimeMillis()
        val recent = (1..5).map { RecentNote("vomiting", t - it * 3600_000L, emptyMap(), 1) }
        assertEquals(Level.AMBER, level("vomited again", "vomiting", recent = recent))
    }

    @Test fun ordinaryThingsStayGreen() {
        assertEquals(Level.GREEN, level("runny nose since morning"))
        assertEquals(Level.GREEN, level("a little knee pain after walking"))
        assertEquals(Level.GREEN, level("burping after lunch"))
    }

    @Test fun selfHarmIsCalmNotAlarm() {
        val r = p.parse("I want to die", null, emptyList(), now, ZoneId.of("Asia/Kolkata"))
        val t = DangerRules.evaluate(r.main!!.problemId, r.main!!.facts, r.readings, emptyList(), elder)
        assertTrue(t.mentalHealth)
    }
}
