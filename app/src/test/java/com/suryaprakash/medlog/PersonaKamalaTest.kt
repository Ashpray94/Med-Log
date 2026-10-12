package com.suryaprakash.medlog

import com.suryaprakash.medlog.clinical.Band
import com.suryaprakash.medlog.clinical.DangerRules
import com.suryaprakash.medlog.clinical.Interview
import com.suryaprakash.medlog.clinical.Interview.Heard
import com.suryaprakash.medlog.clinical.Level
import com.suryaprakash.medlog.clinical.Limits
import com.suryaprakash.medlog.clinical.PersonContext
import com.suryaprakash.medlog.clinical.RecentNote
import com.suryaprakash.medlog.clinical.Told
import com.suryaprakash.medlog.clinical.Triage
import com.suryaprakash.medlog.data.CarePlan
import com.suryaprakash.medlog.nlu.Fact
import com.suryaprakash.medlog.nlu.Reading
import com.suryaprakash.medlog.nlu.Source
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Persona 1: Kamala, 67, oesophageal cancer, on chemotherapy, not on a blood thinner.
 * Scenario numbers (k1..k9) follow docs/WORK_PLAN.md "Persona tests"; k10..k13 are extra scenarios.
 * A test marked @Ignore("FAILS: ...") is a real finding: the expectation is kept, only the build is kept green.
 */
class PersonaKamalaTest {
    private val now = 1_800_000_000_000L
    private val hour = 3600_000L

    private val conditions = "Cancer"                                   // oesophageal cancer, picked from CarePlan.CONDITIONS
    private val treatments = listOf("Chemotherapy")
    private val cancerCare = DangerRules.cancerCareOf(conditions, treatments)

    private fun kamala(limits: Limits = Limits()) = PersonContext(67, false, conditions, limits, cancerCare)
    private fun facts(vararg f: Pair<String, Any>) = f.associate { it.first to Fact(it.second, Source.ASKED) }
    private fun eval(problem: String?, f: Map<String, Fact> = emptyMap(), readings: List<Reading> = emptyList(),
                     recent: List<RecentNote> = emptyList(), who: PersonContext = kamala()): Triage =
        DangerRules.evaluate(problem, f, readings, recent, who, now)

    private fun ask(problemId: String, known: Map<String, Fact> = emptyMap()) = Interview.core(CAT, CAT.problem(problemId)!!, known)

    @Test fun kamalaIsInCancerCare() {
        assertTrue(cancerCare)
        assertFalse(DangerRules.cancerCareOf("Cancer", emptyList()))
        assertFalse(DangerRules.cancerCareOf("Diabetes", treatments))
    }

    // ── K1 ──
    @Test fun k1_vomitingThreeIsGreenFiveIsAmberAndNoteIsNotDoubleCounted() {
        assertEquals(Level.GREEN, eval("vomiting", facts("count" to 3)).level)
        assertEquals(Level.GREEN, eval("vomiting", facts("count" to 4)).level)          // "more than 4" means 5 and up
        val five = eval("vomiting", facts("count" to 5))
        assertEquals(Level.AMBER, five.level)
        assertTrue(five.reasons.toString(), five.reasons.any { it.contains("5 times") && it.contains("call your doctor") })
        // the "5 or more" tap stores 5 (B74); a typed 6 is still AMBER
        assertEquals(Level.AMBER, eval("vomiting", facts("count" to 6)).level)
        // B06: "how many times today" is a running total (data/Occurrences): an earlier note of 1 an hour ago and this
        // note of 3 mean 3 today, never 4 and never 7
        val earlier = listOf(RecentNote("vomiting", now - hour, emptyMap(), 1))
        assertEquals(Level.GREEN, eval("vomiting", facts("count" to 3), recent = earlier).level)
        // four separate vomits earlier (a note each, with their answers) plus this one = 5 -> AMBER
        val four = (1..4).map { RecentNote("vomiting", now - it * hour, facts("keepWater" to true), null) }
        assertEquals(Level.AMBER, eval("vomiting", facts("count" to 1), recent = four).level)
        // notes older than 24 hours do not count
        assertEquals(Level.GREEN, eval("vomiting", facts("count" to 3), recent = listOf(RecentNote("vomiting", now - 30 * hour, emptyMap(), 5))).level)
    }

    // ── K2 ──
    @Test fun k2_vomitingBloodIsRedAndNoWaterNoUrineIsRed() {
        assertEquals(Level.RED, eval("vomiting", facts("blood" to true)).level)
        assertEquals(Level.RED, eval("vomit_blood").level)
        assertEquals(Level.RED, eval("vomiting", facts("coffeeGround" to true)).level)
        val dry = eval("vomiting", facts("keepWater" to false, "urineToday" to false, "count" to 2))
        assertEquals(Level.RED, dry.level)
        assertTrue(dry.reasons.toString(), dry.reasons.any { it.contains("dehydration") })
        // only one of the two is AMBER, not RED
        assertEquals(Level.AMBER, eval("vomiting", facts("keepWater" to false, "count" to 2)).level)
    }

    // ── K3 ──
    @Test fun k3_chillsAfterVomitingHundredPointTwoIsRedUntilHelperRaisesTheLimit() {
        val chills = facts("temperature" to 100.2)
        val plain = eval("chills", chills)
        assertEquals(Level.RED, plain.level)
        assertTrue(plain.reasons.toString(), plain.reasons.any { it.contains("cancer treatment") })
        // the helper's doctor says 102 for her
        val own = kamala(Limits(mapOf("temp" to Band(amberHigh = 102.0, redHigh = 102.0)), doctorConfirmed = true))
        assertEquals(Level.GREEN, eval("chills", chills, who = own).level)
        assertEquals(Level.GREEN, eval("vomiting", facts("count" to 2, "temperature" to 100.2), who = own).level)
        val high = eval("chills", facts("temperature" to 102.4), who = own)
        assertEquals(Level.RED, high.level)
        assertTrue(high.reasons.toString(), high.reasons.any { it.contains("above the limit set for you") })
        // 104 is red for everyone, whatever the helper set
        assertEquals(Level.RED, eval("chills", facts("temperature" to 104.0), who = kamala(Limits(mapOf("temp" to Band(redHigh = 110.0))))).level)
    }

    @Test fun k3_celsiusTypedIsTurnedIntoFahrenheit() {
        assertEquals(102.0, DangerRules.toFahrenheit(38.9), 0.0)
        assertEquals(100.4, DangerRules.toFahrenheit(38.0), 0.0)
        assertEquals(100.2, DangerRules.toFahrenheit(100.2), 0.0)                   // already Fahrenheit: unchanged
        val tempAsk = ask("chills").first { it.id == "q_temp" }
        assertEquals(102.0, (Interview.understand(tempAsk, "38.9") as Heard.Value).value as Double, 0.0)
        // a typed number that is neither is not accepted
        assertEquals(Heard.Unclear, Interview.understand(tempAsk, "60"))
    }

    @Test fun k3_helperSetsOnlyTheAmberFeverLine_cancerRedLineStillApplies() {
        // Documents current behaviour: a helper who sets only "amber above 102" leaves the cancer red line (100.0) in force,
        // so 100.2 is still RED. The Personal limits page has to make the helper set both lines (see report).
        val own = kamala(Limits(mapOf("temp" to Band(amberHigh = 102.0))))
        assertEquals(Level.RED, eval("chills", facts("temperature" to 100.2), who = own).level)
    }

    // ── K4 ──
    @Test fun k4_severeCoughOneWeekIsNotRed_coughBloodProblemIsRed_threeWeeksIsAmber() {
        val severe = eval("cough", facts("severity" to 8, "weeks" to 1, "blood" to false))
        assertEquals(Level.GREEN, severe.level)
        assertEquals(Level.RED, eval("cough_blood").level)
        assertEquals(Level.AMBER, eval("cough", facts("weeks" to 3, "severity" to 8)).level)
    }

    @Test fun k4_coughWithBloodAnsweredYesIsRed() {
        assertEquals(Level.RED, eval("cough", facts("blood" to true, "severity" to 8)).level)
    }

    // ── K5 ──
    @Test fun k5_mildConfusionDuringFeverIsRedConfusion() {
        val t = eval("fever", facts("confusion" to true, "temperature" to 100.6))
        assertEquals(Level.RED, t.level)
        assertTrue(t.reasons.toString(), t.reasons.any { it.contains("confusion", ignoreCase = true) })
        // confusion alone (no temperature) is RED as well
        val c = eval("fever", facts("confusion" to true))
        assertEquals(Level.RED, c.level)
        assertTrue(c.reasons.toString(), "Confusion" in c.reasons)
        // "no confusion" at 100.6 is RED for cancer care anyway, but the reason is not confusion
        assertTrue(eval("fever", facts("confusion" to false, "temperature" to 100.6)).reasons.none { it.contains("onfusion") })
    }

    @Test fun k5_helpersAreToldOnceNotTwice() {
        // TellScreen texts through Alerts.tellOnce, which asks Repo.markTold -> Told.shouldTell: RED is sent once
        var facts: Map<String, Fact> = emptyMap()
        assertTrue(Told.shouldTell(facts, Level.RED))
        facts = Told.mark(facts, Level.RED)
        assertFalse("second Save after 'This is wrong - change it'", Told.shouldTell(facts, Level.RED))
        assertFalse(Told.shouldTell(facts, Level.AMBER))
    }

    // ── K6 ──
    @Test fun k6_hardToBreatheIsNotAnAutomaticEmergencyUnlessBreathingRiskChosen() {
        val kamalaRisks = listOf("falls", "alone")
        assertFalse("breathless" in CarePlan.emergenciesFor(kamalaRisks, listOf("Cancer")))
        assertFalse("breathless" in CarePlan.emergenciesFor(emptyList(), listOf("Cancer", "Asthma or COPD")))
        assertTrue("breathless" in CarePlan.emergenciesFor(listOf("breathing"), listOf("Cancer")))
        assertEquals(listOf("fall", "fainted"), CarePlan.emergenciesFor(kamalaRisks, listOf("Cancer")))
    }

    // ── K7 ──
    @Test fun k7_spo2At67() {
        val none = eval(null, readings = listOf(Reading("spo2", 88.0)))
        assertEquals(Level.AMBER, none.level)
        assertEquals("spo2", none.needsLimit)
        assertTrue(none.reasons.toString(), none.reasons.any { it.contains("please call your doctor") })
        // 92 at 67 with no limit: no alarm, still the quiet 'no limit' line
        val ok = eval(null, readings = listOf(Reading("spo2", 92.0)))
        assertEquals(Level.GREEN, ok.level); assertEquals("spo2", ok.needsLimit)
        // 89 at 67 is AMBER not RED (a younger person would be RED at 89)
        assertEquals(Level.AMBER, eval(null, readings = listOf(Reading("spo2", 89.0))).level)
    }

    @Test fun k7_spo2HelpersRedLineAt88IsRedAt88() {
        val own = kamala(Limits(mapOf("spo2" to Band(redLow = 88.0))))
        val t = eval(null, readings = listOf(Reading("spo2", 88.0)), who = own)
        assertEquals(Level.RED, t.level)
        assertNull(t.needsLimit)
    }

    @Test fun k7_spo2HelpersRedLineAt88_87IsRedAndNoNeedsLimitLine() {
        val own = kamala(Limits(mapOf("spo2" to Band(redLow = 88.0))))
        val t = eval(null, readings = listOf(Reading("spo2", 87.0)), who = own)
        assertEquals(Level.RED, t.level)
        assertNull(t.needsLimit)
        assertEquals(Level.AMBER, eval(null, readings = listOf(Reading("spo2", 89.0)), who = own).level)   // above the red line, under the 90 amber line
    }

    // ── K8 ──
    @Test fun k8_bp168over96() {
        val r = listOf(Reading("bp", 168.0, 96.0))
        val none = eval(null, readings = r)
        assertEquals(Level.GREEN, none.level)
        assertEquals("bp", none.needsLimit)
        val own = kamala(Limits(mapOf("bpSys" to Band(amberHigh = 160.0, redHigh = 180.0), "bpDia" to Band(amberHigh = 100.0, redHigh = 110.0))))
        val t = eval(null, readings = r, who = own)
        assertEquals(Level.AMBER, t.level)
        assertNull(t.needsLimit)
        assertEquals(Level.RED, eval(null, readings = listOf(Reading("bp", 182.0, 96.0)), who = own).level)
        assertEquals(Level.RED, eval(null, readings = listOf(Reading("bp", 150.0, 112.0)), who = own).level)
        assertEquals(Level.GREEN, eval(null, readings = listOf(Reading("bp", 150.0, 90.0)), who = own).level)
    }

    // ── K10: shivering after vomiting ──
    @Test fun k10_shiveringAfterVomitingAsksForTemperatureInTheCoreQuestions() {
        val core = ask("chills")
        println("KAMALA chills core: " + core.joinToString(" | ") { "${it.id}: ${it.text}" })
        assertEquals(listOf("when", "q_temp", "strength"), core.map { it.id })
        val temp = core.first { it.id == "q_temp" }
        assertEquals(Interview.Kind.TEMP, temp.kind)
        assertTrue(temp.danger)
        assertTrue(core.size <= 6)
        // no body map for a whole-body sensation
        assertTrue(core.none { it.id == "where" })
        // if she already said the temperature, it is not asked twice
        assertTrue(ask("chills", facts("temperature" to 100.2)).none { it.id == "q_temp" })
    }

    @Test fun k10_vomitingThenShiveringWithTemperatureIsRed() {
        assertEquals(Level.GREEN, eval("vomiting", facts("count" to 3)).level)
        assertEquals(Level.RED, eval("chills", facts("temperature" to 100.2, "severity" to 4)).level)
        // no temperature: not GREEN any more (B56)
        assertEquals(Level.AMBER, eval("chills", facts("severity" to 4)).level)
    }

    @Test fun k10_shiveringOnChemoWithNoTemperatureIsNotGreen() {
        val t = eval("chills", facts("severity" to 4))
        assertTrue(t.level != Level.GREEN)
        assertEquals(listOf("Check your temperature. If you can't, call your cancer doctor today."), t.reasons)
        assertEquals(Level.AMBER, eval("fever", emptyMap()).level)
        // not on cancer treatment: unchanged
        assertEquals(Level.GREEN, eval("chills", facts("severity" to 4), who = PersonContext(67, false, "Diabetes", Limits(), false)).level)
    }

    // ── K11: her vomiting interview ──
    @Test fun k11_vomitingInterviewListAndCount() {
        val core = ask("vomiting")
        println("KAMALA vomiting core: " + core.joinToString(" | ") { "${it.id}: ${it.text}" })
        assertEquals(listOf("when", "q_blood_vomit", "q_keepwater", "q_urine", "count", "severity"), core.map { it.id })
        assertTrue("more than 6 core questions: $core", core.size <= 6)
        assertEquals("Was there any blood?", core[1].text)
        assertEquals("How many times today?", core[4].text)
        assertEquals("How bad is it?", core[5].text)
    }

    @Test fun k11_vomitingCoreAsksWaterAndUrine() {
        val ids = ask("vomiting").map { it.id }
        assertTrue(ids.toString(), "q_keepwater" in ids && "q_urine" in ids)
        assertTrue(ids.size <= 6)
    }

    // ── K12: her cough interview ──
    @Test fun k12_coughInterviewListAndCount() {
        val core = ask("cough")
        println("KAMALA cough core: " + core.joinToString(" | ") { "${it.id}: ${it.text}" })
        // 2.13 also asks how often it comes, and since when a doctor has known
        assertEquals(listOf("when", "q_blood_cough", "severity", "often", "diagnosed"), core.map { it.id })
        assertTrue("more than 6 core questions: $core", core.size <= 6)
        assertEquals("Any blood when you cough?", core[1].text)
    }

    @Test fun k12_coughCoreAsksHowManyWeeks() {
        // only when "When did it start?" was "a week or more" (B54); nothing is derived otherwise
        assertTrue(ask("cough", facts("started" to "A week or more")).any { it.id == "q_weeks" })
        assertTrue(ask("cough", facts("started" to "a week or more")).any { it.id == "q_weeks" })
        assertTrue(ask("cough", facts("started" to "Yesterday")).none { it.id == "q_weeks" })
        assertTrue(ask("cough").none { it.id == "q_weeks" })
        assertTrue(ask("cough", facts("started" to "A week or more", "weeks" to 4)).none { it.id == "q_weeks" })
        // 2.13 stores the answer as days
        assertTrue(ask("cough", facts("started" to "A week or more", "days" to 28)).none { it.id == "q_weeks" })
        assertTrue(ask("cough", facts("started" to "A week or more")).size <= 6)
        val cough = CAT.problem("cough")!!
        assertEquals("For how many days?", Interview.weeksAsk(CAT, cough, facts("started" to "A week or more"))?.text)
        assertNull(Interview.weeksAsk(CAT, CAT.problem("vomiting")!!, facts("started" to "A week or more")))
    }

    // ── K13: the tiles she can tap match the rules ──
    @Test fun k13_countTapsMatchTheRules() {
        // the tiles are Once, 2, 3, 4 and '5 or more' (stored as 5, B74)
        val values = Interview.COUNT.choices.map { it.value.toInt() }
        assertEquals(listOf(1, 2, 3, 4, 5), values)
        assertEquals("5 or more", Interview.COUNT.choices.last().label)
        assertEquals(Level.GREEN, eval("vomiting", facts("count" to values[3])).level)   // '4 times'
        assertEquals(Level.AMBER, eval("vomiting", facts("count" to values[4])).level)   // '5 or more'
    }

    @Test fun k13_severityAloneNeverMakesCoughVomitingOrChillsRed() {
        for (p in listOf("cough", "vomiting", "chills")) assertTrue(p, eval(p, facts("severity" to 10)).level != Level.RED)
    }
}
