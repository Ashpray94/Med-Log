package com.suryaprakash.medlog

import com.suryaprakash.medlog.clinical.Catalogue
import com.suryaprakash.medlog.clinical.Describe
import com.suryaprakash.medlog.clinical.FollowUps
import com.suryaprakash.medlog.nlu.Normalize
import com.suryaprakash.medlog.nlu.Parser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId

val CAT: Catalogue by lazy { Catalogue.parse(File("src/main/assets/clinical/catalogue.json").readText()) }

class ParserTest {
    private val p = Parser(CAT)
    private val d = Describe(CAT)
    private val now = LocalDateTime.of(2026, 9, 25, 16, 0)
    private fun parse(s: String, focus: String? = null, meds: List<String> = emptyList()) = p.parse(s, focus, meds, now, ZoneId.of("Asia/Kolkata"))

    @Test fun catalogueIsConsistent() {
        assertTrue(CAT.problems.size >= 140)
        for (pr in CAT.problems) {
            pr.fields.forEach { assertNotNull("${pr.id} field $it", CAT.field(it)) }
            pr.followUps.forEach { assertNotNull("${pr.id} question $it", CAT.questions[it]) }
            assertTrue("${pr.id} group", CAT.groups.any { it.id == pr.group })
        }
        val all = CAT.problems.flatMap { pr -> pr.synonyms.map { Normalize.text(it) to pr.id } }
        val dup = all.groupBy { it.first }.filter { g -> g.value.map { it.second }.distinct().size > 1 }
        assertTrue("same words for two problems: ${dup.keys}", dup.isEmpty())
    }

    @Test fun numberWords() {
        assertEquals("150 by 90", Normalize.text("one fifty by ninety"))
        assertEquals("2 times", Normalize.text("twice"))
        assertEquals("99.5", Normalize.text("ninety nine point five"))
        assertEquals("102", Normalize.text("one hundred and two"))
        assertEquals("cant keep water", Normalize.text("Can't keep water"))
        assertEquals("21", Normalize.text("twenty one"))
    }

    @Test fun tappedVomitingWithDetails() {
        val r = parse("Twice after lunch, it was yellowish, my stomach is also paining", focus = "vomiting")
        val m = r.main!!
        assertEquals("vomiting", m.problemId)
        assertEquals(2, m.facts["count"]!!.value)
        assertEquals("yellow", m.facts["colour"]!!.value)
        assertTrue((m.facts["context"]!!.value as String).contains("after lunch"))
        assertTrue(r.others.any { it.problemId == "stomach_pain" })
    }

    @Test fun negationIsNeverMissed() {
        val r = parse("I vomited 3 times but there was no blood", focus = "vomiting")
        assertEquals(false, r.main!!.facts["blood"]!!.value)
        val r2 = parse("vomiting with blood", focus = "vomiting")
        assertEquals(true, r2.main!!.facts["blood"]!!.value)
        val r3 = parse("no fever but headache since morning")
        assertEquals("headache", r3.main!!.problemId)
        assertTrue(r3.negatives.any { it.problemId == "fever" })
        val r4 = parse("I didn't hit my head", focus = "fall")
        assertEquals(false, r4.main!!.facts["hitHead"]!!.value)
        val r5 = parse("i fell and hit my head", focus = "fall")
        assertEquals(true, r5.main!!.facts["hitHead"]!!.value)
        val r6 = parse("not dizzy, only tired")
        assertEquals("tired", r6.main!!.problemId)
        assertTrue(r6.negatives.any { it.problemId == "dizzy" })
    }

    @Test fun negationDoesNotCrossAnd() {
        val r = parse("I didn't sleep and I vomited twice")
        assertFalse(r.mentions.first { it.problemId == "vomiting" }.negated)
    }

    @Test fun cantBreatheIsASymptomNotANegation() {
        val r = parse("I can't breathe properly")
        assertEquals("breathless", r.main!!.problemId)
        assertFalse(r.main!!.negated)
    }

    @Test fun chestPainRedFlagsCaptured() {
        val r = parse("chest pain going to my left arm and I am sweating")
        val m = r.main!!
        assertEquals("chest_pain", m.problemId)
        assertEquals(true, m.facts["armJaw"]!!.value)
        assertEquals(true, m.facts["sweating"]!!.value)
    }

    @Test fun bodyPartPlusPainWords() {
        assertEquals("knee_pain", parse("my knee is really paining").main!!.problemId)
        assertEquals("stomach_pain", parse("vayiru vali after food").main!!.problemId)
        assertEquals("back_pain", parse("lower back pain since 2 days").main!!.problemId)
    }

    @Test fun speechSlipsAreMatchedButFlagged() {
        val r = parse("vomitting since morning")
        assertEquals("vomiting", r.main!!.problemId)
    }

    @Test fun readings() {
        val r = parse("my bp is one fifty by ninety and sugar is 250")
        assertTrue(r.readings.any { it.type == "bp" && it.v1 == 150.0 && it.v2 == 90.0 })
        assertTrue(r.readings.any { it.type == "sugar" && it.v1 == 250.0 })
        assertEquals(101.0, p.temperature("fever is 101 degrees")!!, 0.01)
        assertEquals(100.4, p.temperature("temperature 38 degrees celsius")!!, 0.01)
        assertNull(p.temperature("i vomited 390 times"))
    }

    @Test fun severity() {
        assertEquals(8, parse("headache 8 out of 10").main!!.facts["severity"]!!.value)
        assertEquals(9, parse("unbearable headache").main!!.facts["severity"]!!.value)
    }

    @Test fun whenItHappened() {
        val r = parse("I fainted an hour ago")
        val expected = now.minusHours(1).atZone(ZoneId.of("Asia/Kolkata")).toInstant().toEpochMilli()
        assertEquals(expected, r.occurredAt)
        assertNotNull(parse("fell last night").occurredAt)
    }

    @Test fun medicinesTaken() {
        val r = parse("headache, I took a dolo 650", meds = listOf("Metformin 500"))
        assertTrue(r.medicinesTaken.contains("Dolo"))
        val r2 = parse("i took my metformin and then vomited", meds = listOf("Metformin 500"))
        assertTrue(r2.medicinesTaken.contains("Metformin 500"))
    }

    @Test fun readBackIsPlain() {
        val r = parse("Twice after lunch, yellow, no blood", focus = "vomiting")
        val s = d.readBack(r)
        assertTrue(s, s.startsWith("Vomited 2 times"))
        assertTrue(s, s.contains("no blood"))
    }

    @Test fun followUpsAskDangerFirstAndSkipKnown() {
        val r = parse("vomited twice", focus = "vomiting")
        val qs = FollowUps.pick(CAT, "vomiting", r.main!!.facts)
        assertEquals("blood", qs.first().field)
        assertTrue(qs.size <= 2)
        val r2 = parse("vomited twice no blood", focus = "vomiting")
        assertTrue(FollowUps.pick(CAT, "vomiting", r2.main!!.facts).none { it.field == "blood" })
    }

    @Test fun selfHarmLanguageIsRecognised() {
        assertEquals("self_harm", parse("sometimes I feel I want to die").main!!.problemId)
    }

    @Test fun tappedProblemThatStoppedIsKeptAsBetter() {
        val r = parse("the vomiting has stopped now", focus = "vomiting")
        assertEquals("vomiting", r.main!!.problemId)
        assertEquals(true, r.main!!.facts["better"]!!.value)
    }
}
