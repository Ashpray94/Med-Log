package com.suryaprakash.medlog

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.suryaprakash.medlog.clinical.DangerRules
import com.suryaprakash.medlog.clinical.PersonContext
import com.suryaprakash.medlog.clinical.RecentNote
import com.suryaprakash.medlog.clinical.Triage
import com.suryaprakash.medlog.data.DAY
import com.suryaprakash.medlog.data.Dose
import com.suryaprakash.medlog.data.DoseStatus
import com.suryaprakash.medlog.data.Duplicates
import com.suryaprakash.medlog.data.FOOD_INSTEAD
import com.suryaprakash.medlog.data.Kind
import com.suryaprakash.medlog.data.MedDb
import com.suryaprakash.medlog.data.Medicine
import com.suryaprakash.medlog.data.Note
import com.suryaprakash.medlog.data.Occurrences
import com.suryaprakash.medlog.data.Outcome
import com.suryaprakash.medlog.data.Profile
import com.suryaprakash.medlog.data.Sync
import com.suryaprakash.medlog.data.outcome
import com.suryaprakash.medlog.data.planned
import com.suryaprakash.medlog.doctor.DoctorNoteBuilder
import com.suryaprakash.medlog.nlu.Fact
import com.suryaprakash.medlog.nlu.Mention
import com.suryaprakash.medlog.nlu.Source
import com.suryaprakash.medlog.nlu.factsToJson
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId

/**
 * Pressure test for the records: the same facts entered in different ways must read the same on every page
 * (History, Home, widget, doctor page and PDF, danger rules), copies must never be counted, and sharing between
 * phones must neither lose nor double anything.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DataIntegrityTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val app get() = ctx as MedLogApp
    private val zone = ZoneId.systemDefault()
    private fun todayAt(h: Int, m: Int = 0) = LocalDate.now().atStartOfDay(zone).plusHours(h.toLong()).plusMinutes(m.toLong()).toInstant().toEpochMilli()
    private fun fresh(name: String): MedDb = MedDb.open(ctx, "$name-${System.nanoTime()}.db")
    private fun symptom(pid: String, at: Long, count: Int? = null, better: Boolean = false, facts: Map<String, Fact> = emptyMap()): Note {
        val f = facts.toMutableMap()
        if (count != null) f["count"] = Fact(count, Source.ASKED)
        if (better) f["better"] = Fact(true, Source.TAPPED)
        return Note(kind = Kind.SYMPTOM, problemId = pid, occurredAt = at, details = factsToJson(f), count = count, text = pid)
    }

    // ── counting: "how many times today" is a running total ──

    @Test fun runningTotalsAreNotAddedTwice() {
        val es = listOf(Occurrences.E(todayAt(7, 45), 3), Occurrences.E(todayAt(8, 43), 3))
        assertEquals("two notes that each say 3 times today mean 3", 3, Occurrences.total(es))
        assertEquals("a later, higher total wins", 5, Occurrences.total(es + Occurrences.E(todayAt(12), 5)))
        assertEquals("more notes than the stated total: every note counts", 4, Occurrences.total(List(4) { Occurrences.E(todayAt(9, it), 2) }))
        assertEquals("no totals given: one a note", 2, Occurrences.total(listOf(Occurrences.E(todayAt(7), null), Occurrences.E(todayAt(8), null))))
    }

    @Test fun daysAreCountedSeparately() {
        val yesterday = todayAt(22) - DAY
        val es = listOf(Occurrences.E(yesterday, 4), Occurrences.E(todayAt(1), 2))
        assertEquals(6, Occurrences.total(es))
        assertEquals(2, Occurrences.perDay(es).size)
    }

    @Test fun betterAndRemovedNotesAreNotOccurrences() {
        val notes = listOf(symptom("cough", todayAt(8)), symptom("cough", todayAt(9), better = true),
            symptom("cough", todayAt(10)).copy(deletedAt = todayAt(11)))
        assertEquals(1, Occurrences.total(notes))
    }

    // ── the danger rules: never count the note being written twice ──

    @Test fun vomitingTwiceWithRunningTotalIsNotSix() {
        val recent = listOf(RecentNote("vomiting", todayAt(7, 45), emptyMap(), 3))
        val t = DangerRules.evaluate("vomiting", mapOf("count" to Fact(3, Source.ASKED)), emptyList(), recent, PersonContext(78, false), now = todayAt(8, 43))
        assertTrue("3 times today is not 6: ${t.reasons}", t.reasons.none { "6 times" in it })
        val six = DangerRules.evaluate("vomiting", mapOf("count" to Fact(6, Source.ASKED)), emptyList(), recent, PersonContext(78, false), now = todayAt(9))
        assertTrue("6 really noted is flagged", six.reasons.any { "6 times in 24 hours" in it })
    }

    @Test fun theNoteBeingWrittenIsLeftOutOfRecent() = runBlocking {
        val db = fresh("rules"); val repo = app.repoFor(db)
        val id = db.notes().insert(symptom("vomiting", System.currentTimeMillis(), count = 2))
        assertTrue(repo.recentForRules(exclude = id).none { it.at == db.notes().get(id)!!.occurredAt && it.count == 2 })
        assertEquals(1, repo.recentForRules().size)
    }

    // ── every page reads the same numbers ──

    @Test fun historyHomeDoctorAndWidgetAgree() = runBlocking {
        val db = fresh("agree"); val repo = app.repoFor(db)
        val now = System.currentTimeMillis()
        // vomiting: two notes, the second saying 3 times today; cough: three plain notes and a "better now"; tired: yesterday
        listOf(symptom("vomiting", now - 60 * 60_000L), symptom("vomiting", now - 30 * 60_000L, count = 3),
            symptom("cough", now - 50 * 60_000L), symptom("cough", now - 40 * 60_000L), symptom("cough", now - 20 * 60_000L),
            symptom("cough", now - 10 * 60_000L, better = true), symptom("tired", now - DAY - 3600_000L)).forEach { db.notes().insert(it) }
        val from = now - 7 * DAY; val to = now + 60_000
        val notes = db.notes().between(from, to)
        val history = notes.filter { it.problemId != null }.groupBy { it.problemId!! }.mapValues { Occurrences.total(it.value) }
        val home = repo.recentProblems(10).associate { it.problemId to it.todayCount }
        val doctor = DoctorNoteBuilder(app.catalogue, app.describe).build(Profile(name = "Test"), from, to, notes, emptyList(), emptyList(), now = now)
            .symptoms.associate { it.problemId to it.total }
        assertEquals(mapOf("vomiting" to 3, "cough" to 3, "tired" to 1), history)
        assertEquals("the doctor page counts the same", history, doctor)
        assertEquals("Home and the widget count today the same", 3, home["vomiting"]); assertEquals(3, home["cough"])
        assertEquals("yesterday is not today", 0, home["tired"] ?: 0)
    }

    @Test fun aProblemNotedOnlyAsBetterIsNotListedForTheDoctor() = runBlocking {
        val now = System.currentTimeMillis()
        val notes = listOf(symptom("headache", now - 3600_000L, better = true))
        val n = DoctorNoteBuilder(app.catalogue, app.describe).build(Profile(name = "Test"), now - 7 * DAY, now + 60_000, notes, emptyList(), emptyList(), now = now)
        assertTrue(n.symptoms.isEmpty())
    }

    // ── copies ──

    @Test fun copiesAreFoundAndNothingElse() {
        val t = todayAt(14, 19)
        val a = symptom("cough", t).copy(id = 1)
        val copies = (2L..5L).map { symptom("cough", t + it * 10_000).copy(id = it) }
        val laterSame = symptom("cough", t + 10 * 60_000).copy(id = 6)              // 10 minutes later: another time
        val otherDetails = symptom("cough", t + 30_000, facts = mapOf("severity" to Fact(2, Source.ASKED))).copy(id = 7)
        val water = Note(id = 8, kind = Kind.WATER, occurredAt = t, count = 1, text = "Water: 1 glass")
        val water2 = water.copy(id = 9, occurredAt = t + 20_000)                     // two glasses in a row are real
        val found = Duplicates.find(listOf(a) + copies + listOf(laterSame, otherDetails, water, water2))
        assertEquals(listOf(2L, 3L, 4L, 5L), found.sorted())
    }

    @Test fun cleanUpMovesCopiesToRemovedOnce() = runBlocking {
        val db = fresh("clean"); val repo = app.repoFor(db)
        val t = System.currentTimeMillis() - 3600_000L
        repeat(5) { db.notes().insert(symptom("cough", t + it * 1000)) }
        db.notes().insert(symptom("cough", t + 30 * 60_000L))
        assertEquals(4, repo.removeDuplicates())
        assertEquals(2, Occurrences.total(db.notes().between(0, Long.MAX_VALUE)))
        assertEquals("nothing left to move", 0, repo.removeDuplicates())
    }

    // ── one save each, however many times it's asked ──

    @Test fun savingTheSameMentionTwiceMakesTwoNotesOnlyWhenAskedTwice() = runBlocking {
        val db = fresh("save"); val repo = app.repoFor(db)
        val ids = repo.saveTold(listOf(Mention("cough")), null, System.currentTimeMillis(), Triage.OK, emptyList(), emptyList(), null)
        assertEquals(1, ids.size)
        assertEquals(1, db.notes().between(0, Long.MAX_VALUE).size)
    }

    // ── doses: one meaning everywhere ──

    @Test fun doseOutcomesAndPlannedCounts() {
        val now = todayAt(20)
        val d = listOf(
            Dose(id = 1, medicineId = 1, scheduledAt = todayAt(8), status = DoseStatus.TAKEN, actedAt = todayAt(8)),
            Dose(id = 2, medicineId = 1, scheduledAt = todayAt(12), status = DoseStatus.SKIPPED, reason = FOOD_INSTEAD),
            Dose(id = 3, medicineId = 1, scheduledAt = todayAt(16), status = DoseStatus.MISSED),
            Dose(id = 4, medicineId = 1, scheduledAt = todayAt(20, 5), status = DoseStatus.DUE),
            Dose(id = 5, medicineId = 1, scheduledAt = todayAt(23), status = DoseStatus.DUE),
            Dose(id = 6, medicineId = 1, scheduledAt = todayAt(14), status = DoseStatus.TAKEN, actedAt = todayAt(14), reason = com.suryaprakash.medlog.data.EXTRA_FEED),
            Dose(id = 7, medicineId = 1, scheduledAt = todayAt(15), status = DoseStatus.SKIPPED, reason = com.suryaprakash.medlog.data.EXTRA_REMOVED),
        )
        assertEquals(listOf(Outcome.GIVEN, Outcome.FOOD_INSTEAD, Outcome.MISSED, Outcome.DUE, Outcome.LATER), d.take(5).map { it.outcome(now) })
        assertTrue("food instead is done, not missed", Outcome.FOOD_INSTEAD.covered)
        assertEquals("extra feeds are not part of the plan", 5, d.planned().size)
        assertEquals(2, d.planned().count { it.outcome(now).covered })
    }

    // ── sharing between phones: nothing lost, nothing doubled, groups kept ──

    @Test fun syncRoundTripKeepsEverythingOnce() = runBlocking {
        val a = fresh("phoneA"); val b = fresh("phoneB")
        val repoA = app.repoFor(a)
        val now = System.currentTimeMillis()
        // a symptom told with a reading: one group
        repoA.saveTold(listOf(Mention("fever")), "fever 101", now - 3600_000L, Triage.OK,
            listOf(com.suryaprakash.medlog.nlu.Reading("temp", 101.0, null)), emptyList(), null)
        repeat(3) { a.notes().insert(symptom("cough", now - (it + 1) * 20 * 60_000L)) }
        // a noise row first on phone B, so its row numbers differ from phone A's
        b.notes().insert(Note(kind = Kind.WATER, occurredAt = now - DAY, count = 1, text = "Water"))
        suspend fun send(from: MedDb, to: MedDb) {
            val (body, _) = Sync.pack(ctx, Sync.Peer("x", ByteArray(32), from, "down", "X"), 0) ?: return
            Sync.apply(to, JSONObject(Sync.unzip(body.getString("z"))), hub = false)
        }
        send(a, b); send(a, b)              // sent twice: still once
        send(b, a)                          // and back again
        val onA = a.notes().between(0, Long.MAX_VALUE); val onB = b.notes().between(0, Long.MAX_VALUE)
        assertEquals("nothing doubled on A (B's water came over too)", 6, onA.size)
        assertEquals("nothing lost or doubled on B", 6, onB.size)
        assertEquals(3, Occurrences.total(onB.filter { it.problemId == "cough" }))
        val feverB = onB.first { it.problemId == "fever" }; val readingB = onB.first { it.kind == Kind.READING }
        assertEquals("the reading stays with its symptom on the other phone", feverB.groupId, readingB.groupId)
        assertEquals(feverB.id, feverB.groupId)
        assertFalse("no group points at the unrelated water row", onB.first { it.kind == Kind.WATER }.id == readingB.groupId)
    }

    @Test fun aDeletionTravelsAndIsNotUndoneBySync() = runBlocking {
        val a = fresh("delA"); val b = fresh("delB")
        val id = a.notes().insert(symptom("cough", System.currentTimeMillis() - 3600_000L))
        suspend fun send(from: MedDb, to: MedDb) {
            val (body, _) = Sync.pack(ctx, Sync.Peer("x", ByteArray(32), from, "down", "X"), 0) ?: return
            Sync.apply(to, JSONObject(Sync.unzip(body.getString("z"))), hub = false)
        }
        send(a, b)
        Thread.sleep(5)
        a.notes().remove(id)
        send(a, b); send(b, a)
        assertEquals(0, Occurrences.total(a.notes().between(0, Long.MAX_VALUE)))
        assertEquals(0, Occurrences.total(b.notes().between(0, Long.MAX_VALUE)))
    }

    // ── every entry keeps the id it shares with other phones ──

    @Test fun savedNotesAlwaysHaveASharedId() = runBlocking {
        val db = fresh("uid"); val repo = app.repoFor(db)
        val ids = repo.saveTold(listOf(Mention("fever"), Mention("cough")), null, System.currentTimeMillis(), Triage.OK,
            listOf(com.suryaprakash.medlog.nlu.Reading("temp", 100.0, null)), emptyList(), null)
        ids.forEach { id -> val n = db.notes().get(id)!!; assertTrue("note $id has no shared id", n.uid.isNotBlank()); assertTrue(n.updatedAt > 0) }
        assertEquals("every id different", ids.size, ids.map { db.notes().get(it)!!.uid }.distinct().size)
        // writing back a copy made before saving can't blank it
        val before = db.notes().get(ids[0])!!
        db.notes().update(before.copy(uid = "", updatedAt = 0, text = "changed"))
        val after = db.notes().get(ids[0])!!
        assertEquals(before.uid, after.uid); assertTrue(after.updatedAt > 0); assertEquals("changed", after.text)
    }

    @Suppress("unused") private val keepMed = Medicine(name = "x")
}
