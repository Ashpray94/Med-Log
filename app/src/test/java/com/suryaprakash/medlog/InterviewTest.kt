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
        // a cough that keeps coming: how often, and since when a doctor has known
        assertTrue(Interview.core(CAT, CAT.problem("cough")!!, emptyMap()).map { it.id }.containsAll(listOf("often", "diagnosed")))
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
}
