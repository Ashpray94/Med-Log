package com.suryaprakash.medlog

import com.suryaprakash.medlog.clinical.Band
import com.suryaprakash.medlog.clinical.DangerRules
import com.suryaprakash.medlog.clinical.Interview
import com.suryaprakash.medlog.clinical.Interview.Heard
import com.suryaprakash.medlog.clinical.Level
import com.suryaprakash.medlog.clinical.Limits
import com.suryaprakash.medlog.clinical.PersonContext
import com.suryaprakash.medlog.clinical.RecentNote
import com.suryaprakash.medlog.clinical.Triage
import com.suryaprakash.medlog.data.CarePlan
import com.suryaprakash.medlog.data.Kind
import com.suryaprakash.medlog.data.Note
import com.suryaprakash.medlog.data.isBetterNote
import com.suryaprakash.medlog.data.occurrences
import com.suryaprakash.medlog.data.rulesNotes
import com.suryaprakash.medlog.nlu.Fact
import com.suryaprakash.medlog.nlu.Reading
import com.suryaprakash.medlog.nlu.Source
import com.suryaprakash.medlog.nlu.factsToJson
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** W2: limits, cancer-care numbers, the over-55 rules, the count fixes (B06, B07, B19) and the question changes (B49–B51). */
class ClinicalRulesTest {
    private val now = 1_800_000_000_000L
    private val hour = 3600_000L

    private fun facts(vararg f: Pair<String, Any>) = f.associate { it.first to Fact(it.second, Source.ASKED) }
    private fun person(age: Int? = 67, cancer: Boolean = false, limits: Limits = Limits(), thinner: Boolean = false) =
        PersonContext(age, thinner, if (cancer) "Cancer" else "", limits, cancer)

    private fun eval(problem: String?, f: Map<String, Fact> = emptyMap(), readings: List<Reading> = emptyList(), recent: List<RecentNote> = emptyList(), who: PersonContext = person()): Triage =
        DangerRules.evaluate(problem, f, readings, recent, who, now)

    private fun band(vararg p: Pair<String, Band>, confirmed: Boolean = true) = Limits(mapOf(*p), confirmed, "helper", now)

    // ───────── rules version ─────────

    @Test fun versionIsBumped() = assertEquals("rules-0.2.0-unreviewed", DangerRules.VERSION)

    // ───────── cancer care ─────────

    @Test fun cancerCareIsCancerPlusChemoOrRadiotherapy() {
        assertTrue(DangerRules.cancerCareOf("Diabetes, Cancer", listOf("Chemotherapy")))
        assertTrue(DangerRules.cancerCareOf("cancer", listOf("Insulin", "Radiotherapy")))
        assertFalse(DangerRules.cancerCareOf("Cancer", listOf("Insulin")))
        assertFalse(DangerRules.cancerCareOf("Diabetes", listOf("Chemotherapy")))
        assertTrue("Radiotherapy" in CarePlan.TREATMENTS)
    }

    @Test fun cancerVomitingMoreThanFourIsAmber() {
        val cancer = person(cancer = true)
        assertEquals(Level.GREEN, eval("vomiting", facts("count" to 4), who = cancer).level)
        val five = eval("vomiting", facts("count" to 5), who = cancer)
        assertEquals(Level.AMBER, five.level)
        assertTrue(five.reasons.toString(), five.reasons.any { it.contains("5 times") && it.contains("cancer") })
        // without cancer care the general line (6) stays
        assertEquals(Level.GREEN, eval("vomiting", facts("count" to 5)).level)
        assertEquals(Level.AMBER, eval("vomiting", facts("count" to 6)).level)
    }

    @Test fun cancerLooseMotionsMoreThanFourIsAmber() {
        val cancer = person(cancer = true)
        assertEquals(Level.GREEN, eval("loose_motions", facts("count" to 4), who = cancer).level)
        assertEquals(Level.AMBER, eval("loose_motions", facts("count" to 5), who = cancer).level)
        assertEquals(Level.GREEN, eval("loose_motions", facts("count" to 5)).level)
    }

    @Test fun cancerVomitingAddsEarlierNotesOfTheDay() {
        val recent = (1..3).map { RecentNote("vomiting", now - it * hour, emptyMap(), 1) }
        assertEquals(Level.AMBER, eval("vomiting", facts("count" to 2), recent = recent, who = person(cancer = true)).level)   // 3 + 2 = 5
        assertEquals(Level.GREEN, eval("vomiting", facts("count" to 1), recent = recent, who = person(cancer = true)).level)   // 3 + 1 = 4
    }

    @Test fun cancerFeverFromHundredIsRed() {
        val t = eval("fever", facts("temperature" to 100.2), who = person(cancer = true))
        assertEquals(Level.RED, t.level)
        assertTrue(t.reasons.toString(), t.reasons.any { it.startsWith("Fever during cancer treatment") })
        assertEquals(Level.RED, eval(null, readings = listOf(Reading("temp", 100.0, unit = "°F")), who = person(cancer = true)).level)
        assertEquals(Level.GREEN, eval("fever", facts("temperature" to 99.8), who = person(cancer = true)).level)
        // the same 100.2 without cancer care is GREEN (below 100.4)
        assertEquals(Level.GREEN, eval("fever", facts("temperature" to 100.2)).level)
    }

    @Test fun helperFeverLineOverridesTheCancerDefault() {
        val lim = band("temp" to Band(amberHigh = 101.0, redHigh = 102.0))
        val who = person(cancer = true, limits = lim)
        assertEquals(Level.GREEN, eval("fever", facts("temperature" to 100.2), who = who).level)
        assertEquals(Level.AMBER, eval("fever", facts("temperature" to 101.4), who = who).level)
        assertEquals(Level.RED, eval("fever", facts("temperature" to 102.4), who = who).level)
        assertEquals(Level.RED, eval("fever", facts("temperature" to 102.0), who = who).level)
        // an unset amber line keeps the general one: 101 in an older person is AMBER even with only a red line set
        assertEquals(Level.AMBER, eval("fever", facts("temperature" to 101.0), who = person(limits = band("temp" to Band(redHigh = 102.0)))).level)
        assertEquals(Level.GREEN, eval("fever", facts("temperature" to 101.0), who = person(limits = band("temp" to Band(amberHigh = 101.5, redHigh = 102.0)))).level)
        // the low line and fever with confusion stay
        assertEquals(Level.RED, eval("fever", facts("temperature" to 94.5), who = who).level)
        assertEquals(Level.RED, eval("fever", facts("temperature" to 100.6, "confusion" to true), who = who).level)
    }

    @Test fun cancerConstipationMoreThanTwoDaysIsAmber() {
        val cancer = person(cancer = true)
        assertEquals(Level.GREEN, eval("constipation", facts("daysNoMotion" to 2), who = cancer).level)
        assertEquals(Level.AMBER, eval("constipation", facts("daysNoMotion" to 3), who = cancer).level)
        assertEquals(Level.GREEN, eval("constipation", facts("daysNoMotion" to 5)).level)
        // the helper's band decides
        val lim = person(limits = band("constipationDays" to Band(amberHigh = 4.0, redHigh = 7.0)))
        assertEquals(Level.GREEN, eval("constipation", facts("daysNoMotion" to 4), who = lim).level)
        assertEquals(Level.AMBER, eval("constipation", facts("daysNoMotion" to 5), who = lim).level)
        assertEquals(Level.RED, eval("constipation", facts("daysNoMotion" to 8), who = lim).level)
        assertEquals(Level.GREEN, eval("constipation", facts("daysNoMotion" to 3), who = person(cancer = true, limits = lim.limits)).level)
    }

    @Test fun helperVomitAndLooseBandsDecide() {
        val lim = band("vomit" to Band(amberHigh = 6.0, redHigh = 10.0), "loose" to Band(amberHigh = 3.0))
        val who = person(cancer = true, limits = lim)
        assertEquals(Level.GREEN, eval("vomiting", facts("count" to 6), who = who).level)      // beats the cancer "more than 4"
        assertEquals(Level.AMBER, eval("vomiting", facts("count" to 7), who = who).level)
        assertEquals(Level.RED, eval("vomiting", facts("count" to 11), who = who).level)
        assertEquals(Level.AMBER, eval("loose_motions", facts("count" to 4), who = person(age = 40, limits = lim)).level)
    }

    // ───────── SpO2 ─────────

    @Test fun spo2OverFiftyFiveWithoutLimitIsAmberAndAsksForALimit() {
        val t = eval(null, readings = listOf(Reading("spo2", 88.0, unit = "%")), who = person(67))
        assertEquals(Level.AMBER, t.level)
        assertEquals("spo2", t.needsLimit)
        // 90–93 is not judged for over 55 without a limit
        val ok = eval(null, readings = listOf(Reading("spo2", 91.0, unit = "%")), who = person(67))
        assertEquals(Level.GREEN, ok.level); assertEquals("spo2", ok.needsLimit)
    }

    @Test fun spo2YoungerOrUnknownAgeKeepsTheGeneralNumbers() {
        for (age in listOf(40, 55, null)) {
            val red = eval(null, readings = listOf(Reading("spo2", 88.0, unit = "%")), who = person(age))
            assertEquals("age $age", Level.RED, red.level); assertNull(red.needsLimit)
            assertEquals(Level.AMBER, eval(null, readings = listOf(Reading("spo2", 92.0, unit = "%")), who = person(age)).level)
        }
    }

    @Test fun spo2HelperLinesDecide() {
        val who = person(67, limits = band("spo2" to Band(amberLow = 92.0, redLow = 88.0)))
        val at88 = eval(null, readings = listOf(Reading("spo2", 88.0, unit = "%")), who = who)
        assertEquals(Level.RED, at88.level)                           // low lines are "at or below" (B55): 88 is on the red line
        assertNull(at88.needsLimit)
        assertEquals(Level.RED, eval(null, readings = listOf(Reading("spo2", 87.0, unit = "%")), who = who).level)
        assertEquals(Level.AMBER, eval(null, readings = listOf(Reading("spo2", 91.0, unit = "%")), who = who).level)
        assertEquals(Level.AMBER, eval(null, readings = listOf(Reading("spo2", 92.0, unit = "%")), who = who).level)   // on the amber line
        assertEquals(Level.GREEN, eval(null, readings = listOf(Reading("spo2", 93.0, unit = "%")), who = who).level)
        // a helper's line is used for younger people too
        // (under 55, a helper who sets only the red line keeps the general amber line at 94)
        assertEquals(Level.AMBER, eval(null, readings = listOf(Reading("spo2", 89.0, unit = "%")), who = person(40, limits = band("spo2" to Band(redLow = 85.0)))).level)
        assertEquals(Level.RED, eval(null, readings = listOf(Reading("spo2", 84.0, unit = "%")), who = person(40, limits = band("spo2" to Band(redLow = 85.0)))).level)
    }

    // ───────── BP ─────────

    @Test fun bpOverFiftyFiveWithoutLimitIsQuietAndAsksForALimit() {
        val t = eval(null, readings = listOf(Reading("bp", 168.0, 96.0, "mmHg")), who = person(67))
        assertEquals(Level.GREEN, t.level)
        assertEquals("bp", t.needsLimit)
        // even very low and very high numbers alone raise nothing (never assume)
        assertEquals(Level.GREEN, eval(null, readings = listOf(Reading("bp", 85.0, 55.0, "mmHg")), who = person(67)).level)
        assertEquals(Level.GREEN, eval(null, readings = listOf(Reading("bp", 190.0, 100.0, "mmHg")), who = person(67)).level)
    }

    @Test fun bpWithSymptomsIsStillRedForEveryone() {
        val t = eval("headache", readings = listOf(Reading("bp", 185.0, 95.0, "mmHg")), who = person(67))
        assertEquals(Level.RED, t.level)
        assertEquals(Level.RED, eval("chest_pain", facts("severity" to 3), readings = listOf(Reading("bp", 150.0, 121.0, "mmHg")), who = person(67)).level)
    }

    @Test fun bpYoungerKeepsTodaysRules() {
        val t = eval(null, readings = listOf(Reading("bp", 190.0, 100.0, "mmHg")), who = person(45))
        assertEquals(Level.AMBER, t.level); assertNull(t.needsLimit)
        assertEquals(Level.AMBER, eval(null, readings = listOf(Reading("bp", 85.0, 55.0, "mmHg")), who = person(null)).level)
    }

    @Test fun bpHelperLinesDecide() {
        val lim = band("bpSys" to Band(amberLow = 100.0, redLow = 85.0, amberHigh = 160.0, redHigh = 180.0), "bpDia" to Band(amberHigh = 100.0, redHigh = 110.0))
        val who = person(67, limits = lim)
        fun bp(s: Double, d: Double) = eval(null, readings = listOf(Reading("bp", s, d, "mmHg")), who = who)
        assertEquals(Level.AMBER, bp(168.0, 96.0).level)              // top reaches 160
        assertNull(bp(168.0, 96.0).needsLimit)
        assertEquals(Level.GREEN, bp(140.0, 90.0).level)
        assertEquals(Level.AMBER, bp(140.0, 100.0).level)             // bottom reaches 100
        assertEquals(Level.RED, bp(185.0, 95.0).level)
        assertEquals(Level.RED, bp(150.0, 111.0).level)
        assertEquals(Level.AMBER, bp(99.0, 60.0).level)
        assertEquals(Level.RED, bp(85.0, 55.0).level)
        assertEquals(Level.RED, eval("headache", readings = listOf(Reading("bp", 185.0, 95.0, "mmHg")), who = who).level)
    }

    @Test fun sugarAndPulseBandsDecide() {
        val who = person(67, limits = band("sugar" to Band(amberLow = 80.0, redLow = 60.0, amberHigh = 250.0, redHigh = 350.0), "pulse" to Band(amberLow = 50.0, amberHigh = 110.0, redHigh = 140.0)))
        fun sugar(v: Double) = eval(null, readings = listOf(Reading("sugar", v, unit = "mg/dL")), who = who)
        assertEquals(Level.GREEN, sugar(120.0).level)
        assertEquals(Level.AMBER, sugar(75.0).level)
        assertEquals(Level.RED, sugar(55.0).level)
        assertEquals(Level.AMBER, sugar(260.0).level)
        assertEquals(Level.RED, sugar(360.0).level)
        assertNull(sugar(120.0).needsLimit)
        // a line the helper left unset keeps the general number: 310 is still AMBER
        assertEquals(Level.AMBER, eval(null, readings = listOf(Reading("sugar", 310.0, unit = "mg/dL")), who = person(67, limits = band("sugar" to Band(redLow = 60.0)))).level)
        fun pulse(v: Double) = eval(null, readings = listOf(Reading("pulse", v, unit = "per minute")), who = who)
        assertEquals(Level.GREEN, pulse(80.0).level)
        assertEquals(Level.AMBER, pulse(115.0).level)
        assertEquals(Level.RED, pulse(145.0).level)
        assertEquals(Level.AMBER, pulse(45.0).level)
        // without bands: today's numbers
        assertEquals(Level.AMBER, eval(null, readings = listOf(Reading("pulse", 135.0, unit = "per minute"))).level)
        assertEquals(Level.RED, eval(null, readings = listOf(Reading("sugar", 45.0, unit = "mg/dL"))).level)
    }

    // ───────── B06 / B07: the note being evaluated is counted once ─────────

    private fun symptom(id: Long, problem: String, hoursAgo: Long, count: Int? = null, better: Boolean = false, betterList: List<String>? = null): Note {
        val f = HashMap<String, Fact>()
        if (better) f["better"] = Fact(true, Source.TAPPED)
        if (betterList != null) f["better"] = Fact(betterList, Source.ASKED)
        return Note(id = id, kind = Kind.SYMPTOM, problemId = problem, occurredAt = now - hoursAgo * hour, details = factsToJson(f), count = count)
    }

    @Test fun b06OneNoteOfThreeIsNotSix() {
        // the note is already saved (id 9, count 3) when the rules run
        val saved = listOf(symptom(9, "vomiting", 0, count = 3))
        val t = eval("vomiting", facts("count" to 3), recent = rulesNotes(saved, excludeId = 9))
        assertEquals(t.reasons.toString(), Level.GREEN, t.level)
        // without leaving it out, the old bug: 3 + 3 = 6 → AMBER
        assertEquals(Level.AMBER, eval("vomiting", facts("count" to 3), recent = rulesNotes(saved, excludeId = null)).level)
    }

    @Test fun b06SixRealVomitsAreAmber() {
        val saved = (1L..5L).map { symptom(it, "vomiting", it, count = 1) } + symptom(9, "vomiting", 0, count = 1)
        val t = eval("vomiting", facts("count" to 1), recent = rulesNotes(saved, excludeId = 9))
        assertEquals(Level.AMBER, t.level)
        assertTrue(t.reasons.toString(), t.reasons.any { it.contains("6 times") })
        val five = (1L..4L).map { symptom(it, "vomiting", it, count = 1) } + symptom(9, "vomiting", 0, count = 1)
        assertEquals(Level.GREEN, eval("vomiting", facts("count" to 1), recent = rulesNotes(five, excludeId = 9)).level)
    }

    @Test fun b07DizzyTwoIsGreenThreeIsAmber() {
        val two = listOf(symptom(1, "dizzy", 20), symptom(2, "dizzy", 0))
        assertEquals(Level.GREEN, eval("dizzy", recent = rulesNotes(two, excludeId = 2)).level)
        val three = listOf(symptom(1, "dizzy", 30), symptom(2, "dizzy", 20), symptom(3, "dizzy", 0))
        val t = eval("dizzy", recent = rulesNotes(three, excludeId = 3))
        assertEquals(Level.AMBER, t.level)
        assertTrue(t.reasons.toString(), t.reasons.any { it.contains("3 times in 2 days") })
    }

    @Test fun betterTapsNeverCountForTheRules() {
        val saved = listOf(symptom(1, "dizzy", 30), symptom(2, "dizzy", 25, better = true), symptom(3, "dizzy", 0))
        assertEquals(Level.GREEN, eval("dizzy", recent = rulesNotes(saved, excludeId = 3)).level)
        assertEquals(1, rulesNotes(saved, excludeId = 3).size)   // only the first dizzy note is left
    }

    // ───────── B19: "better" notes ─────────

    @Test fun b19BetterNoteIsOnlyTheBooleanFact() {
        assertTrue(symptom(1, "headache", 0, count = 0, better = true).isBetterNote())
        assertTrue(symptom(1, "headache", 0, count = null, better = true).isBetterNote())           // old notes had no count
        // "What makes it better?" holds a list: that is a real headache note
        assertFalse(symptom(2, "headache", 0, count = null, betterList = listOf("rest", "medicine")).isBetterNote())
        assertFalse(symptom(3, "headache", 0).isBetterNote())
        // a reading or other kinds are never better-notes
        assertFalse(Note(kind = Kind.WATER, occurredAt = now, details = "{\"better\":{\"v\":true}}").isBetterNote())
        // broken details do not crash
        assertFalse(Note(kind = Kind.SYMPTOM, occurredAt = now, details = "not json").isBetterNote())
    }

    @Test fun b19HeadacheOnceThenYesBetterCountsOnce() {
        val notes = listOf(symptom(1, "headache", 2), symptom(2, "headache", 0, count = 0, better = true))
        assertEquals(1, notes.occurrences().sumOf { it.count ?: 1 })
        // old better-notes (count null) do not count either
        val old = listOf(symptom(1, "headache", 2), symptom(2, "headache", 0, count = null, better = true))
        assertEquals(1, old.occurrences().sumOf { it.count ?: 1 })
        assertEquals(1, old.occurrences().size)
    }

    @Test fun b19ListValuedBetterNoteStaysCounted() {
        val notes = listOf(symptom(1, "headache", 2, betterList = listOf("rest")), symptom(2, "headache", 0, count = 0, better = true))
        assertEquals(listOf(1L), notes.occurrences().map { it.id })
    }

    // ───────── CarePlan: limits, emergencies ─────────

    @Test fun carePlanRoundTripKeepsLimits() {
        val lim = Limits(mapOf("spo2" to Band(amberLow = 92.0, redLow = 88.0), "bpSys" to Band(amberHigh = 160.0, redHigh = 180.0), "temp" to Band(redHigh = 102.0)), true, "Ravi", 12345L)
        val plan = CarePlan(treatments = listOf("Chemotherapy"), emergencies = listOf("fall"), emergenciesAsked = true, limits = lim)
        val back = CarePlan.parse(plan.toJson())
        assertEquals(lim, back.limits)
        assertTrue(back.emergenciesAsked)
        assertEquals(listOf("fall"), back.emergencies)
        assertEquals(Band(amberLow = 92.0, redLow = 88.0), back.limits.band("spo2"))
        assertEquals(JSONObject(plan.toJson()).getJSONObject("limits").getJSONObject("bands").getJSONObject("temp").getDouble("redHigh"), 102.0, 0.0)
        assertNull(back.limits.band("sugar"))
    }

    @Test fun carePlanParsesTolerantly() {
        assertEquals(Limits(), CarePlan.parse("{\"doctors\":[]}").limits)                       // missing
        assertEquals(Limits(), CarePlan.parse("{\"limits\":\"junk\"}").limits)                  // wrong type
        assertEquals(Limits(), CarePlan.parse("{\"limits\":{\"bands\":5}}").limits)
        assertFalse(CarePlan.parse("{}").emergenciesAsked)
        val partial = CarePlan.parse("{\"limits\":{\"bands\":{\"spo2\":{\"redLow\":88}}}}").limits
        assertEquals(88.0, partial.band("spo2")!!.redLow!!, 0.0)
        assertNull(partial.band("spo2")!!.amberLow)
        assertFalse(partial.doctorConfirmed)
    }

    @Test fun b02EmergenciesOnlyFromChosenRisks() {
        val asthma = CarePlan.emergenciesFor(emptyList(), listOf("Asthma or COPD"))
        assertFalse(asthma.toString(), "breathless" in asthma)
        assertTrue(CarePlan.emergenciesFor(emptyList(), emptyList()).isEmpty())
        assertTrue("breathless" in CarePlan.emergenciesFor(listOf("breathing"), emptyList()))
        assertTrue("chest_pain" in CarePlan.emergenciesFor(listOf("heart"), emptyList()))
        assertFalse("chest_pain" in CarePlan.emergenciesFor(listOf("breathing"), emptyList()))
        val falls = CarePlan.emergenciesFor(listOf("falls"), emptyList())
        assertTrue("fall" in falls && "fainted" in falls)
        assertTrue("fall" in CarePlan.emergenciesFor(listOf("alone"), emptyList()))
        assertTrue("low_sugar" in CarePlan.emergenciesFor(emptyList(), listOf("Diabetes")))
    }

    @Test fun b02CountdownIsTenSeconds() = assertEquals(10, com.suryaprakash.medlog.ui.screens.EMERGENCY_COUNTDOWN)

    // ───────── B22: °C ─────────

    @Test fun b22CelsiusIsConverted() {
        assertEquals(101.3, DangerRules.toFahrenheit(38.5), 0.0001)
        assertEquals(102.0, DangerRules.toFahrenheit(38.9), 0.0001)
        assertEquals(98.6, DangerRules.toFahrenheit(37.0), 0.0001)
        assertEquals(93.2, DangerRules.toFahrenheit(34.0), 0.0001)
        assertEquals(109.4, DangerRules.toFahrenheit(43.0), 0.0001)
        // Fahrenheit and out-of-range values are left alone
        assertEquals(101.3, DangerRules.toFahrenheit(101.3), 0.0)
        assertEquals(33.9, DangerRules.toFahrenheit(33.9), 0.0)
        assertEquals(43.5, DangerRules.toFahrenheit(43.5), 0.0)
        // the spoken path uses the same helper
        assertEquals(101.3, (Interview.understand(Interview.Ask("t", "temperature", "?", Interview.Kind.TEMP), "38.5") as Heard.Value).value)
        assertEquals(101.3, (Interview.understand(Interview.Ask("t", "temperature", "?", Interview.Kind.TEMP), "101.3") as Heard.Value).value)
        assertEquals(Heard.Unclear, Interview.understand(Interview.Ask("t", "temperature", "?", Interview.Kind.TEMP), "60"))
    }

    // ───────── B49, B50, B51: the questions ─────────

    private fun coreIds(problem: String, known: Map<String, Fact> = emptyMap()) = Interview.core(CAT, CAT.problem(problem)!!, known).map { it.id }

    @Test fun b49FeverAsksTemperatureAndConfusionBeforeTellMore() {
        val ids = coreIds("fever")
        assertTrue(ids.toString(), "q_temp" in ids && "q_confused" in ids)
        assertEquals("when", ids.first())
        val chills = coreIds("chills")
        assertTrue(chills.toString(), "q_temp" in chills)
        // and not asked twice
        val ext = Interview.extended(CAT, CAT.problem("fever")!!, emptyMap()).map { it.id }
        assertTrue(ext.toString(), ext.none { it in setOf("q_temp", "q_confused", "q_stiffneck") })
        // known facts are skipped
        assertTrue("q_temp" !in coreIds("fever", mapOf("temperature" to Fact(101.0, Source.SAID))))
    }

    @Test fun b49FeverWithConfusionAndTemperatureIsRed() {
        assertEquals(Level.RED, eval("fever", facts("temperature" to 101.0, "confusion" to true), who = person(45)).level)
    }

    @Test fun b51StiffNeckIsAskedForFeverCoreAndHeadacheExtended() {
        assertTrue(coreIds("fever").toString(), "q_stiffneck" in coreIds("fever"))
        assertEquals("Is your neck stiff, or does it hurt to bend your head down?", CAT.questions["q_stiffneck"]!!.ask)
        val headacheCore = coreIds("headache")
        assertTrue(headacheCore.toString(), "q_stiffneck" !in headacheCore)
        val headacheExt = Interview.extended(CAT, CAT.problem("headache")!!, emptyMap()).map { it.id }
        assertTrue(headacheExt.toString(), "q_stiffneck" in headacheExt)
        assertEquals(1, headacheExt.count { it == "q_stiffneck" || it == "f_stiffNeck" })
    }

    @Test fun b51FeverWithStiffNeckIsRed() {
        val t = eval("fever", facts("temperature" to 101.0, "stiffNeck" to true), who = person(45))
        assertEquals(Level.RED, t.level)
        assertTrue(t.reasons.toString(), "Fever with stiff neck" in t.reasons)
        assertEquals(Level.GREEN, eval("fever", facts("temperature" to 101.0, "stiffNeck" to false), who = person(45)).level)
    }

    @Test fun b50FallAsksCouldYouGetUpInCore() {
        val ids = coreIds("fall")
        assertTrue(ids.toString(), "q_getup" in ids && "q_hithead" in ids)
        // not yet asked how long on the floor
        assertTrue(ids.none { it == Interview.FLOOR.id })
        // after "no", the floor question follows
        val after = coreIds("fall", mapOf("couldGetUp" to Fact(false, Source.ASKED)))
        assertTrue(after.toString(), Interview.FLOOR.id in after)
        assertTrue(coreIds("fall", mapOf("couldGetUp" to Fact(true, Source.ASKED))).none { it == Interview.FLOOR.id })
        assertTrue(after.size <= 6)
        assertEquals("How long were you on the floor?", Interview.FLOOR.text)
        assertEquals(listOf("5", "30", "90"), Interview.FLOOR.choices.map { it.value })
        assertEquals("90", (Interview.understand(Interview.FLOOR, "more than an hour") as Heard.Value).value)
        assertEquals("5", (Interview.understand(Interview.FLOOR, "under 10 minutes") as Heard.Value).value)
    }

    @Test fun b50FallRules() {
        assertEquals(Level.RED, eval("fall", facts("couldGetUp" to false), who = person(45)).level)
        assertEquals(Level.RED, eval("fall", facts("couldGetUp" to true, "timeOnFloor" to 90), who = person(45)).level)
        assertEquals(Level.RED, eval("fall", facts("couldGetUp" to true, "timeOnFloor" to "90"), who = person(45)).level)
        assertEquals(Level.AMBER, eval("fall", facts("couldGetUp" to true, "timeOnFloor" to 30), who = person(45)).level)
    }

    @Test fun constipationAsksHowManyDays() {
        assertTrue(coreIds("constipation").toString(), "q_nomotion" in coreIds("constipation"))
        assertEquals("daysNoMotion", CAT.questions["q_nomotion"]!!.field)
    }

    // ───────── an unset line keeps the general number (per-line fallback) ─────────

    @Test fun effectiveTakesEachLineFromTheHelperElseTheDefault() {
        val e = com.suryaprakash.medlog.clinical.effective(Band(amberHigh = 250.0), Band(amberLow = 70.0, redLow = 54.0, amberHigh = 300.0, redHigh = 400.0))
        assertEquals(Band(amberLow = 70.0, redLow = 54.0, amberHigh = 250.0, redHigh = 400.0), e)
        assertEquals(Band(redLow = 1.0), com.suryaprakash.medlog.clinical.effective(null, Band(redLow = 1.0)))
    }

    @Test fun sugarBandWithOnlyHighLinesStillCatchesLowSugar() {
        val who = person(67, limits = band("sugar" to Band(amberHigh = 250.0, redHigh = 350.0)))
        val low = eval(null, readings = listOf(Reading("sugar", 45.0, unit = "mg/dL")), who = who)
        assertEquals(Level.RED, low.level)
        assertEquals(DangerRules.SUGAR_AID, low.firstAid)
        assertEquals(Level.AMBER, eval(null, readings = listOf(Reading("sugar", 65.0, unit = "mg/dL")), who = who).level)
        assertEquals(Level.RED, eval("dizzy", facts("sweating" to true), readings = listOf(Reading("sugar", 65.0, unit = "mg/dL")), who = who).level)
        assertEquals(Level.AMBER, eval(null, readings = listOf(Reading("sugar", 260.0, unit = "mg/dL")), who = who).level)
        // and a band with only low lines keeps 400 as RED, 301 as AMBER
        val lows = person(67, limits = band("sugar" to Band(amberLow = 80.0)))
        assertEquals(Level.RED, eval(null, readings = listOf(Reading("sugar", 400.0, unit = "mg/dL")), who = lows).level)
        assertEquals(Level.AMBER, eval(null, readings = listOf(Reading("sugar", 301.0, unit = "mg/dL")), who = lows).level)
        assertEquals(Level.GREEN, eval(null, readings = listOf(Reading("sugar", 300.0, unit = "mg/dL")), who = lows).level)
    }

    @Test fun pulseBandWithOnlyHighLinesStillCatchesSlowPulse() {
        val who = person(67, limits = band("pulse" to Band(amberHigh = 110.0, redHigh = 150.0)))
        assertEquals(Level.AMBER, eval(null, readings = listOf(Reading("pulse", 35.0, unit = "per minute")), who = who).level)
        assertEquals(Level.GREEN, eval(null, readings = listOf(Reading("pulse", 100.0, unit = "per minute")), who = who).level)
        assertEquals(Level.AMBER, eval(null, readings = listOf(Reading("pulse", 120.0, unit = "per minute")), who = who).level)
        // the general 130 no longer applies once the helper's own high line is set higher
        val high = person(67, limits = band("pulse" to Band(amberHigh = 140.0)))
        assertEquals(Level.GREEN, eval(null, readings = listOf(Reading("pulse", 135.0, unit = "per minute")), who = high).level)
    }

    @Test fun temperatureFloorsAndCeilingsNoHelperCanRemove() {
        val who = person(45, limits = band("temp" to Band(amberHigh = 101.0)))
        assertEquals(Level.RED, eval("fever", facts("temperature" to 105.0), who = who).level)     // 104 default red still there
        assertEquals(Level.RED, eval("fever", facts("temperature" to 94.5), who = who).level)
        assertEquals(Level.AMBER, eval("fever", facts("temperature" to 101.5), who = who).level)
        // even a helper red line above 104 cannot lift the ceiling
        val high = person(45, limits = band("temp" to Band(redHigh = 106.0)))
        assertEquals(Level.RED, eval("fever", facts("temperature" to 104.5), who = high).level)
        assertEquals(Level.RED, eval("fever", facts("temperature" to 94.0), who = high).level)
        // fever with confusion stays RED whatever the band
        assertEquals(Level.RED, eval("fever", facts("temperature" to 100.5, "confusion" to true), who = person(45, limits = band("temp" to Band(amberHigh = 103.0, redHigh = 104.0)))).level)
        // the same limits from a reading, not a spoken fact
        assertEquals(Level.RED, eval(null, readings = listOf(Reading("temp", 105.0, unit = "°F")), who = who).level)
    }

    @Test fun spo2OverFiftyFiveBandWithOnlyAmberLow() {
        val who = person(67, limits = band("spo2" to Band(amberLow = 92.0)))
        val t = eval(null, readings = listOf(Reading("spo2", 91.0, unit = "%")), who = who)
        assertEquals(Level.AMBER, t.level)
        assertEquals("spo2", t.needsLimit)                    // no red line from the helper yet
        assertEquals(Level.AMBER, eval(null, readings = listOf(Reading("spo2", 92.0, unit = "%")), who = who).level)   // "at or below" 92
        assertEquals(Level.GREEN, eval(null, readings = listOf(Reading("spo2", 93.0, unit = "%")), who = who).level)
        // very low is still only AMBER for over 55 until the helper sets a red line
        assertEquals(Level.AMBER, eval(null, readings = listOf(Reading("spo2", 80.0, unit = "%")), who = who).level)
    }

    @Test fun bpOverFiftyFiveBandWithOnlySysRedHigh() {
        val who = person(67, limits = band("bpSys" to Band(redHigh = 180.0)))
        assertEquals(Level.GREEN, eval(null, readings = listOf(Reading("bp", 170.0, 95.0, "mmHg")), who = who).level)
        assertEquals(Level.RED, eval(null, readings = listOf(Reading("bp", 182.0, 90.0, "mmHg")), who = who).level)
        // unset lines mean no alarm for over 55, even very low
        assertEquals(Level.GREEN, eval(null, readings = listOf(Reading("bp", 80.0, 50.0, "mmHg")), who = who).level)
    }

    @Test fun bpYoungerBandWithOnlySysAmberHighKeepsTheOtherGeneralLines() {
        val who = person(45, limits = band("bpSys" to Band(amberHigh = 150.0)))
        assertEquals(Level.AMBER, eval(null, readings = listOf(Reading("bp", 152.0, 80.0, "mmHg")), who = who).level)
        assertEquals(Level.AMBER, eval(null, readings = listOf(Reading("bp", 185.0, 85.0, "mmHg")), who = who).level)   // >= 180 default, and 150 line
        assertEquals(Level.AMBER, eval(null, readings = listOf(Reading("bp", 120.0, 112.0, "mmHg")), who = who).level)  // dia 110 default
        assertEquals(Level.AMBER, eval(null, readings = listOf(Reading("bp", 85.0, 55.0, "mmHg")), who = who).level)    // sys < 90 default
        assertEquals(Level.GREEN, eval(null, readings = listOf(Reading("bp", 120.0, 80.0, "mmHg")), who = who).level)
        // helper lines above the general ones lift them: sys 185 with amberHigh 190 is fine
        val lifted = person(45, limits = band("bpSys" to Band(amberHigh = 190.0)))
        assertEquals(Level.GREEN, eval(null, readings = listOf(Reading("bp", 185.0, 85.0, "mmHg")), who = lifted).level)
    }

    @Test fun vomitBandWithOnlyRedKeepsTheGeneralAmberLine() {
        val who = person(45, limits = band("vomit" to Band(redHigh = 10.0)))
        assertEquals(Level.AMBER, eval("vomiting", facts("count" to 6), who = who).level)
        assertEquals(Level.RED, eval("vomiting", facts("count" to 11), who = who).level)
        assertEquals(Level.AMBER, eval("vomiting", facts("count" to 5), who = person(cancer = true, limits = band("vomit" to Band(redHigh = 10.0)))).level)
    }

    @Test fun coreStaysShort() {
        for (p in CAT.problems) {
            assertTrue(p.id, Interview.core(CAT, p, emptyMap()).size <= 6)
            assertTrue(p.id, Interview.core(CAT, p, mapOf("couldGetUp" to Fact(false, Source.ASKED))).size <= 6)
        }
    }
}
