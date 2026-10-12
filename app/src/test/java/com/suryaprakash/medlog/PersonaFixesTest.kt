package com.suryaprakash.medlog

import com.suryaprakash.medlog.clinical.Band
import com.suryaprakash.medlog.clinical.DangerRules
import com.suryaprakash.medlog.clinical.Describe
import com.suryaprakash.medlog.clinical.Interview
import com.suryaprakash.medlog.clinical.Level
import com.suryaprakash.medlog.clinical.Limits
import com.suryaprakash.medlog.clinical.LimitsForm
import com.suryaprakash.medlog.clinical.Line
import com.suryaprakash.medlog.clinical.PersonContext
import com.suryaprakash.medlog.clinical.RecentNote
import com.suryaprakash.medlog.clinical.Told
import com.suryaprakash.medlog.clinical.Triage
import com.suryaprakash.medlog.data.CarePlan
import com.suryaprakash.medlog.help.Wording
import com.suryaprakash.medlog.nlu.Fact
import com.suryaprakash.medlog.nlu.Reading
import com.suryaprakash.medlog.nlu.Source
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fixes from the persona test report (B52 to B74) that are pure logic. */
class PersonaFixesTest {
    private val now = 1_800_000_000_000L
    private val hour = 3600_000L
    private val young = PersonContext(40, false)
    private val kamala = PersonContext(67, false, "Cancer", Limits(), true)

    private fun facts(vararg f: Pair<String, Any>) = f.associate { it.first to Fact(it.second, Source.ASKED) }
    private fun eval(problem: String?, f: Map<String, Fact> = emptyMap(), readings: List<Reading> = emptyList(),
                     recent: List<RecentNote> = emptyList(), who: PersonContext = young) = DangerRules.evaluate(problem, f, readings, recent, who, now)

    // ── B52 ──
    @Test fun b52_coughWithBloodIsRedWithItsOwnReason() {
        val t = eval("cough", facts("blood" to true))
        assertEquals(Level.RED, t.level)
        assertTrue("Coughing blood" in t.reasons)
        assertEquals(Level.GREEN, eval("cough", facts("blood" to false)).level)
    }

    // ── B53 ──
    @Test fun b53_vomitingAndLooseMotionsCoreAskWaterAndUrine() {
        for (p in listOf("vomiting", "loose_motions")) {
            val ids = Interview.core(CAT, CAT.problem(p)!!, emptyMap()).map { it.id }
            assertTrue("$p $ids", "q_keepwater" in ids && "q_urine" in ids)
            assertTrue("$p $ids", ids.size <= 6)
        }
        // the blood question stays
        assertTrue("q_blood_stool" in Interview.core(CAT, CAT.problem("loose_motions")!!, emptyMap()).map { it.id })
        // and the answers are used: no water and no urine at 65 or more is RED
        assertEquals(Level.RED, eval("loose_motions", facts("keepWater" to false, "urineToday" to false), who = kamala).level)
    }

    // ── B54 ──
    @Test fun b54_weeksQuestionOnlyAfterAWeekOrMore() {
        val cough = CAT.problem("cough")!!
        assertNull(Interview.weeksAsk(CAT, cough, facts("started" to "Earlier today")))
        assertEquals("q_weeks", Interview.weeksAsk(CAT, cough, facts("started" to "A week or more"))?.id)
        assertTrue(Interview.startedWeekOrMore(facts("started" to "A week or more")))
        assertFalse(Interview.startedWeekOrMore(facts("started" to "A few days ago")))
        // the answer then drives the 3-weeks rule
        assertEquals(Level.AMBER, eval("cough", facts("started" to "A week or more", "weeks" to 3)).level)
    }

    // ── B56 ──
    @Test fun b56_cancerCareFeverOrChillsWithoutTemperatureIsAmber() {
        val t = eval("fever", who = kamala)
        assertEquals(Level.AMBER, t.level)
        assertEquals("Check your temperature. If you can't, call your cancer doctor today.", t.reasons.single())
        // a temperature (typed, or a reading from the day) is judged as before
        assertEquals(Level.GREEN, eval("chills", facts("temperature" to 98.6), who = kamala).level)
        assertEquals(Level.RED, eval("chills", who = kamala, readings = listOf(Reading("temp", 100.4))).level)
        // not for other people or other problems
        assertEquals(Level.GREEN, eval("chills", who = young).level)
        assertEquals(Level.GREEN, eval("headache", facts("severity" to 3), who = kamala).level)
    }

    // ── B57 / B59: the told marker ──
    @Test fun b57_toldOncePerLevelAndOnlyWhenTheLevelRises() {
        var f: Map<String, Fact> = facts("count" to 2)
        assertNull(Told.level(f))
        assertTrue(Told.shouldTell(f, Level.AMBER))
        assertFalse(Told.shouldTell(f, Level.GREEN))
        f = Told.mark(f, Level.AMBER)
        assertEquals(Level.AMBER, Told.level(f))
        assertFalse(Told.shouldTell(f, Level.AMBER))          // Save again: no second text
        assertTrue(Told.shouldTell(f, Level.RED))             // it got worse: one more
        f = Told.mark(f, Level.RED)
        assertFalse(Told.shouldTell(f, Level.RED))
        assertFalse(Told.shouldTell(f, Level.AMBER))          // never told a lower level after a higher one
        // the marker is hidden from the summary and the notes line
        assertNull(Describe(CAT).fact(Told.KEY, f.getValue(Told.KEY)))
        assertFalse(Describe(CAT).line("vomiting", f).contains("RED"))
    }

    // ── B59: helpers who also hear about AMBER ──
    @Test fun b59_amberHelpersAreKeptInThePlanByPhoneDigits() {
        var plan = CarePlan()
        assertTrue(plan.amberHelpers.isEmpty())
        plan = plan.withAmberHelper("+91 98765-43210", true).withAmberHelper("+91 98765-43210", true)
        assertEquals(listOf("919876543210"), plan.amberHelpers)
        assertTrue(plan.tellsAmber("+91 98765 43210"))
        assertFalse(plan.tellsAmber("99999 99999"))
        val back = CarePlan.parse(plan.toJson())
        assertEquals(listOf("919876543210"), back.amberHelpers)
        assertEquals(emptyList<String>(), back.withAmberHelper("+91 98765-43210", false).amberHelpers)
        // an older plan without the field reads as nobody opted in
        assertTrue(CarePlan.parse("""{"doctors":[],"symptoms":[]}""").amberHelpers.isEmpty())
    }

    // ── B58 ──
    @Test fun b58_cancerDoctorIsPreferredOnCancerCare() {
        val plan = CarePlan(doctors = listOf(
            CarePlan.Doctor("Dr. Family", "Family doctor", "111"), CarePlan.Doctor("Dr. Stomach", "Stomach", "222"),
            CarePlan.Doctor("Dr. Onco", "Cancer", "333"),
        ))
        assertEquals("Dr. Stomach", plan.doctorFor("Gastroenterology")?.name)
        assertEquals("Dr. Onco", plan.doctorFor("Gastroenterology", cancerCare = true)?.name)
        assertEquals("Dr. Onco", plan.doctorFor(null, true)?.name)
        // no cancer doctor listed: the old choice
        assertEquals("Dr. Family", CarePlan(doctors = plan.doctors.take(2)).doctorFor("Dermatology", true)?.name)
        // a cancer doctor with a phone beats one without
        val two = CarePlan(doctors = listOf(CarePlan.Doctor("No phone", "Cancer", ""), CarePlan.Doctor("Has phone", "Cancer", "555")))
        assertEquals("Has phone", two.doctorFor(null, true)?.name)
    }

    // ── B60 ──
    @Test fun b60_smsCarriesTheFirstReasonAndStaysCalm() {
        val sms = Wording.seeDoctorNow("Kamala", "Shivering", "Fever during cancer treatment (100.2 °F)")
        assertEquals("MedLog: Kamala noted \"Shivering\" (Fever during cancer treatment (100.2 °F)). MedLog suggested getting medical help straight away. Please call or go to them.", sms)
        assertTrue(Wording.seeDoctorToday("Kamala", "Vomiting", "Vomiting 5 times in 24 hours").contains("(Vomiting 5 times in 24 hours)"))
        // no reason: as before
        assertEquals("MedLog: Kamala noted \"Fever\". MedLog suggested getting medical help straight away. Please call or go to them.", Wording.seeDoctorNow("Kamala", "Fever"))
        // every reason the rules can give reads calm once it is put in a message
        val reasons = listOf("Severe chest pain", "Sudden, worst-ever headache", "Swelling of lips, face or tongue (possible severe allergy)",
            "Very high fever (104 °F)", "Coughing blood", "Fainted / passed out", "Choking", "Very low sugar (50)", "Cannot swallow water")
        for (r in reasons) for (m in listOf(Wording.seeDoctorNow("Kamala", "X", r), Wording.seeDoctorToday("Kamala", "X", r)))
            assertEquals(m, emptyList<String>(), Wording.check(m))
    }

    // ── B62 ──
    @Test fun b62_anAbandonedNoteIsNotOneVomit() {
        val opened = RecentNote("vomiting", now - hour, emptyMap(), null)
        val started = RecentNote("vomiting", now - hour, facts("started" to "Just now"), null)
        // three real vomits before (each note has its answers and is one vomit), plus this one = 4 (not more than 4)
        val real = (2..4).map { RecentNote("vomiting", now - it * hour, facts("keepWater" to true), null) }
        val answered = RecentNote("vomiting", now - hour, facts("keepWater" to true), null)   // other answers: still counts as 1
        assertEquals("the two abandoned notes add nothing", Level.GREEN, eval("vomiting", facts("count" to 1), recent = real + opened + started, who = kamala).level)
        assertEquals("a note with other answers adds 1: 4 + 1", Level.AMBER, eval("vomiting", facts("count" to 1), recent = real + answered, who = kamala).level)
        // "3 times today" said once is a running total (data/Occurrences): 3 + this one is 3, not 4
        val total3 = RecentNote("vomiting", now - 2 * hour, emptyMap(), 3)
        assertEquals(Level.GREEN, eval("vomiting", facts("count" to 1), recent = listOf(total3, opened, started), who = kamala).level)
        // the note being evaluated still counts as one, with or without answers
        assertEquals(Level.GREEN, eval("vomiting", emptyMap(), recent = real, who = kamala).level)          // 3 + 1 = 4
        assertEquals(Level.AMBER, eval("vomiting", emptyMap(), recent = real + answered, who = kamala).level)   // 4 + 1
        assertTrue(DangerRules.isEmptyNote(null, emptyMap()))
        assertTrue(DangerRules.isEmptyNote(null, facts("started" to "today")))
        assertTrue(DangerRules.isEmptyNote(null, mapOf("_toldLevel" to Fact("RED", Source.INFERRED))))
        assertFalse(DangerRules.isEmptyNote(2, emptyMap()))
        assertFalse(DangerRules.isEmptyNote(null, facts("pin" to "front:belly")))
    }

    // ── B55 ──
    @Test fun b55_lowLinesAreAtOrBelow() {
        fun spo2(v: Double, own: Limits = Limits(), who: PersonContext = young) = eval(null, readings = listOf(Reading("spo2", v)), who = who.copy(limits = own))
        // the helper's lines include the number itself
        val own = Limits(mapOf("spo2" to Band(amberLow = 92.0, redLow = 88.0)))
        assertEquals(Level.RED, spo2(88.0, own).level)
        assertEquals(Level.AMBER, spo2(89.0, own).level)
        assertEquals(Level.AMBER, spo2(92.0, own).level)
        assertEquals(Level.GREEN, spo2(93.0, own).level)
        // the general numbers keep today's meaning: red below 90, amber below 94
        assertEquals(Level.RED, spo2(89.0).level)
        assertEquals(Level.AMBER, spo2(90.0).level)
        assertEquals(Level.AMBER, spo2(93.0).level)
        assertEquals(Level.GREEN, spo2(94.0).level)
        assertEquals(Level.RED, spo2(89.5).level)   // 89.5 is below 90
        // sugar: red below 54, amber below 70
        fun sugar(v: Double, own: Limits = Limits()) = eval(null, readings = listOf(Reading("sugar", v)), who = young.copy(limits = own))
        assertEquals(Level.RED, sugar(53.0).level)
        assertEquals(Level.AMBER, sugar(54.0).level)
        assertEquals(Level.AMBER, sugar(69.0).level)
        assertEquals(Level.GREEN, sugar(70.0).level)
        assertEquals(Level.RED, sugar(60.0, Limits(mapOf("sugar" to Band(redLow = 60.0)))).level)
        assertEquals(Level.AMBER, sugar(61.0, Limits(mapOf("sugar" to Band(redLow = 60.0)))).level)   // not red; the general amber line still applies
        assertEquals(Level.AMBER, sugar(80.0, Limits(mapOf("sugar" to Band(amberLow = 80.0)))).level)
        // pulse: amber below 40; the helper's low red line includes itself
        fun pulse(v: Double, own: Limits = Limits()) = eval(null, readings = listOf(Reading("pulse", v)), who = young.copy(limits = own))
        assertEquals(Level.GREEN, pulse(40.0).level)
        assertEquals(Level.AMBER, pulse(39.0).level)
        assertEquals(Level.RED, pulse(45.0, Limits(mapOf("pulse" to Band(redLow = 45.0)))).level)
        // over 55: red only from the helper's line, and that line includes itself too
        assertEquals(Level.GREEN, spo2(90.0, who = kamala).level)   // general amber is still "below 90"
        assertEquals(Level.AMBER, spo2(89.0, who = kamala).level)
        assertEquals(Level.AMBER, spo2(89.9, who = kamala).level)
    }

    @Test fun b55_limitsPageSaysAtOrBelowForLowLines() {
        for (k in listOf("spo2", "sugar", "pulse", "bpSys", "bpDia")) {
            val sp = LimitsForm.spec(k)
            assertEquals("Red at or below", sp.label(Line.RED_LOW))
            assertEquals("at or below", sp.lowWord)
        }
        // the general numbers shown as hints are the last number that still counts
        assertEquals("general: 69", LimitsForm.general("sugar", Line.AMBER_LOW, 40, false))
        assertEquals("general: 53", LimitsForm.general("sugar", Line.RED_LOW, 40, false))
        assertEquals("general: 93", LimitsForm.general("spo2", Line.AMBER_LOW, 40, false))
        assertEquals("general: 89", LimitsForm.general("spo2", Line.RED_LOW, 40, false))
        assertEquals("general: 39", LimitsForm.general("pulse", Line.AMBER_LOW, 40, false))
    }

    // ── B71 ──
    @Test fun b71_feverIsOneNumberSavedAsBothLines() {
        val sp = LimitsForm.spec("temp")
        assertTrue(sp.oneNumber)
        assertEquals("The doctor's fever limit for this person (°F or °C)", sp.fieldLabel(Line.AMBER_HIGH))
        val typed = LimitsForm.typed(sp, emptyMap(), Line.AMBER_HIGH, "102")
        assertEquals("102", typed[Line.RED_HIGH]); assertEquals("102", typed[Line.AMBER_HIGH])
        val limits = LimitsForm.toLimits(mapOf("temp" to typed), false, "helper", 1L)
        assertEquals(Band(amberHigh = 102.0, redHigh = 102.0), limits.band("temp"))
        // Celsius is turned into Fahrenheit for both
        val c = LimitsForm.toLimits(mapOf("temp" to LimitsForm.typed(sp, emptyMap(), Line.AMBER_HIGH, "38.9")), false, "helper", 1L)
        assertEquals(Band(amberHigh = 102.0, redHigh = 102.0), c.band("temp"))
        // still capped at 104
        assertNull(LimitsForm.validate(sp, LimitsForm.typed(sp, emptyMap(), Line.AMBER_HIGH, "104")))
        assertTrue(LimitsForm.validate(sp, LimitsForm.typed(sp, emptyMap(), Line.AMBER_HIGH, "105"))!!.contains("104"))
        // the page opens with the one number (older saved amber/red pairs show the amber one)
        assertEquals(mapOf(Line.AMBER_HIGH to "102", Line.RED_HIGH to "102"), LimitsForm.initialTexts(sp, Band(amberHigh = 102.0, redHigh = 102.0)))
        assertEquals(mapOf(Line.AMBER_HIGH to "101.5", Line.RED_HIGH to "101.5"), LimitsForm.initialTexts(sp, Band(amberHigh = 101.5, redHigh = 103.0)))
        assertEquals(mapOf(Line.AMBER_HIGH to "", Line.RED_HIGH to ""), LimitsForm.initialTexts(sp, null))
        // other measures keep separate lines
        val bp = LimitsForm.spec("bpSys")
        assertEquals("120", LimitsForm.typed(bp, emptyMap(), Line.AMBER_HIGH, "120")[Line.AMBER_HIGH])
        assertNull(LimitsForm.typed(bp, emptyMap(), Line.AMBER_HIGH, "120")[Line.RED_HIGH])
        // the rule it replaces is shown
        assertTrue(LimitsForm.tempRule(67, true).contains("red at 100 °F (cancer treatment)"))
        assertTrue(LimitsForm.tempRule(40, false).contains("amber at 102 °F, red at 104 °F"))
        // and the rules honour it: the doctor's 102 for Kamala replaces her cancer red line of 100
        val own = kamala.copy(limits = limits)
        assertEquals(Level.GREEN, eval("chills", facts("temperature" to 100.2), who = own).level)
        assertEquals(Level.RED, eval("chills", facts("temperature" to 102.0), who = own).level)
    }

    // ── B72 ──
    @Test fun b72_feverWithConfusionDoesNotAlsoListPlainConfusion() {
        val t = eval("fever", facts("confusion" to true, "temperature" to 100.6))
        assertEquals(Level.RED, t.level)
        assertTrue("Fever with confusion" in t.reasons)
        assertFalse("Confusion" in t.reasons)
        // confusion without a fever keeps its own line
        assertTrue("Confusion" in eval("fever", facts("confusion" to true)).reasons)
        assertTrue("Confusion" in eval("fever", facts("confusion" to true, "temperature" to 99.0)).reasons)
    }

    // ── B74 ──
    @Test fun b74_fiveOrMoreIsStoredAsFiveAndShownAsFiveOrMore() {
        val d = Describe(CAT)
        assertEquals("Vomited 5 or more times", d.line("vomiting", facts("count" to 5)))
        assertEquals("Vomited 4 times", d.line("vomiting", facts("count" to 4)))
        assertEquals("Vomited 6 times", d.line("vomiting", facts("count" to 6)))
        assertEquals("5 or more", d.countWords(5, Source.ASKED))
        assertEquals("5", d.countWords(5, Source.SAID))          // said as "five" in a sentence: exactly five
        assertEquals(5, Interview.COUNT.choices.last().value.toInt())
        assertEquals(Level.AMBER, eval("vomiting", facts("count" to 5), who = kamala).level)   // in cancer care the rule is "more than 4"
    }
}
