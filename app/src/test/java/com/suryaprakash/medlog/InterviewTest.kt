package com.suryaprakash.medlog

import com.suryaprakash.medlog.clinical.DangerRules
import com.suryaprakash.medlog.clinical.Interview
import com.suryaprakash.medlog.clinical.Interview.Heard
import com.suryaprakash.medlog.clinical.Level
import com.suryaprakash.medlog.clinical.PersonContext
import com.suryaprakash.medlog.nlu.Fact
import com.suryaprakash.medlog.nlu.Parser
import com.suryaprakash.medlog.nlu.Source
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InterviewTest {
    private fun v(a: Interview.Ask, s: String) = (Interview.understand(a, s) as? Heard.Value)?.value

    @Test fun yesNoInIndianLanguages() {
        for (w in listOf("yes", "haan", "ha ji", "aamaa", "avunu", "houdu")) assertEquals(w, true, v(Interview.MORE, w))
        for (w in listOf("no", "nahi", "illai", "illa", "ledu", "nope")) assertEquals(w, false, v(Interview.MORE, w))
        assertEquals(Heard.Skip, Interview.understand(Interview.MORE, "pata nahi"))
        assertEquals(Heard.Done, Interview.understand(Interview.PAIN, "bas"))
    }

    @Test fun whenItStarted() {
        assertEquals("yesterday", v(Interview.WHEN, "kal raat"))
        assertEquals("yesterday", v(Interview.WHEN, "nethu"))
        assertEquals("today", v(Interview.WHEN, "aaj subah"))
        assertEquals("a few days", v(Interview.WHEN, "rendu moonu naal"))
    }

    @Test fun painScaleTakesNumbersAndWords() {
        assertEquals(7, v(Interview.PAIN, "seven"))
        assertEquals(7, v(Interview.PAIN, "saat"))
        assertEquals(8, v(Interview.PAIN, "romba"))
        assertEquals(0, v(Interview.PAIN, "no pain"))
        assertEquals(10, v(Interview.PAIN, "unbearable"))
    }

    @Test fun questionsFitTheProblem() {
        val burn = Interview.core(CAT, CAT.problem("burn")!!, emptyMap()).map { it.id }
        assertTrue(burn.toString(), burn.containsAll(listOf("burnLook", "burnSize", "pain")))
        val head = Interview.core(CAT, CAT.problem("headache")!!, emptyMap()).map { it.id }
        assertEquals("when", head.first())
        assertTrue(head.toString(), "pain" in head && "q_worst" in head)
        assertTrue(Interview.core(CAT, CAT.problem("itching")!!, emptyMap()).any { it.id == "itch" })
        assertTrue(Interview.core(CAT, CAT.problem("vomiting")!!, emptyMap()).none { it.id == "where" })
        assertTrue(Interview.core(CAT, CAT.problem("knee_pain")!!, emptyMap()).any { it.id == "where" })
        // never more than a handful before asking whether to go on
        for (p in CAT.problems) assertTrue(p.id, Interview.core(CAT, p, emptyMap()).size <= 6)
        // known facts are not asked again
        assertTrue(Interview.core(CAT, CAT.problem("headache")!!, mapOf("started" to Fact("Yesterday", Source.ASKED))).none { it.id == "when" })
    }

    @Test fun burnDangerSigns() {
        val old = PersonContext(78, false)
        fun level(vararg f: Pair<String, Any>) = DangerRules.evaluate("burn", f.associate { it.first to Fact(it.second, Source.ASKED) }, emptyList(), emptyList(), old).level
        assertEquals(Level.RED, level("burnDepth" to "white, brown or black"))
        assertEquals(Level.RED, level("burnSize" to "bigger than a palm"))
        assertEquals(Level.AMBER, level("burnDepth" to "blisters"))
        assertEquals(Level.AMBER, level("burnDepth" to "red, no blisters", "site" to "Right hand"))
        assertEquals(Level.GREEN, level("burnDepth" to "red, no blisters", "burnSize" to "smaller than a coin"))
    }

    @Test fun symptomWordsInIndianLanguages() {
        val p = Parser(CAT)
        assertEquals("headache", p.parse("sir dard ho raha hai").main!!.problemId)
        assertEquals("vomiting", p.parse("do baar ulti hui").main!!.problemId)
        assertEquals("vomiting", p.parse("vanti vandhuchu").main!!.problemId)
        assertEquals("fever", p.parse("bukhar hai").main!!.problemId)
        assertEquals("stomach_pain", p.parse("vayiru vali").main!!.problemId)
        assertEquals("dizzy", p.parse("chakkar aa raha hai").main!!.problemId)
    }

    @Test fun coughFieldsGated() {
        val cough = CAT.problem("cough")!!
        // with empty facts, dryWet should apply but phlegm/phlegmColour/shade should not
        val ext0 = Interview.extended(CAT, cough, emptyMap())
        val dryWetAsk = ext0.firstOrNull { it.field == "dryWet" }
        assertTrue("dryWet should be in extended asks", dryWetAsk != null)
        assertTrue("dryWet should apply with empty facts", Interview.applies(dryWetAsk!!, emptyMap()))

        val phlegmAsk = ext0.firstOrNull { it.field == "phlegm" }
        assertTrue("phlegm should be in extended asks", phlegmAsk != null)
        assertTrue("phlegm should NOT apply with empty facts", !Interview.applies(phlegmAsk!!, emptyMap()))

        // with dryWet=wet, phlegm should apply
        val facts1 = mapOf("dryWet" to Fact("wet", Source.ASKED))
        assertTrue("phlegm should apply with dryWet=wet", Interview.applies(phlegmAsk, facts1))

        // with phlegm=true, phlegmColour and shade should apply
        val facts2 = mapOf("phlegm" to Fact(true, Source.ASKED))
        val phlegmColourAsk = ext0.firstOrNull { it.field == "phlegmColour" }
        val shadeAsk = ext0.firstOrNull { it.field == "shade" }
        assertTrue("phlegmColour should be in extended asks", phlegmColourAsk != null)
        assertTrue("shade should be in extended asks", shadeAsk != null)
        assertTrue("phlegmColour should apply with phlegm=true", Interview.applies(phlegmColourAsk!!, facts2))
        assertTrue("shade should apply with phlegm=true", Interview.applies(shadeAsk!!, facts2))
    }

    @Test fun coughNoCoffeeGround() {
        val cough = CAT.problem("cough")!!
        val all = Interview.core(CAT, cough, emptyMap()) + Interview.extended(CAT, cough, emptyMap())
        assertTrue("cough should not have coffeeGround field", all.none { it.field == "coffeeGround" })
    }

    @Test fun ageGating() {
        val headache = CAT.problem("headache")!!
        // With age 50, should not exclude any fields by age
        val ext50 = Interview.extended(CAT, headache, emptyMap(), age = 50)
        assertTrue("extended questions for age 50 should not be empty", ext50.isNotEmpty())

        // With age null, should keep all fields
        val extNull = Interview.extended(CAT, headache, emptyMap(), age = null)
        assertTrue("extended questions for age null should not be empty", extNull.isNotEmpty())
    }

    @Test fun redFlagTest() {
        val redFlagList = listOf(
            com.suryaprakash.medlog.clinical.RedFlag(
                id = "test_rf",
                problems = listOf("headache"),
                all = listOf(
                    com.suryaprakash.medlog.clinical.Cond(field = "worstEver", isValue = true)
                ),
                level = "RED",
                reason = "Test red flag",
                say = "This is a test"
            )
        )
        val facts = mapOf("worstEver" to Fact(true, Source.ASKED))
        val result = DangerRules.evaluate("headache", facts, emptyList(), emptyList(), PersonContext(78, false), redFlags = redFlagList)
        assertEquals(Level.RED, result.level)
        assertTrue(result.reasons.contains("Test red flag"))
    }

    @Test fun allAsksHaveHelp() {
        for (p in CAT.problems) {
            val all = Interview.core(CAT, p, emptyMap()) + Interview.extended(CAT, p, emptyMap())
            for (a in all) {
                assertTrue("Ask ${a.id} for problem ${p.id} should have non-empty help", a.help.isNotEmpty())
                assertTrue("Ask ${a.id} should have what part", a.what.isNotEmpty())
                assertTrue("Ask ${a.id} should have why part", a.why.isNotEmpty())
            }
        }
    }
}
