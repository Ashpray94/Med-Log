package com.suryaprakash.medlog

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.suryaprakash.medlog.data.*
import com.suryaprakash.medlog.doctor.DoctorNoteBuilder
import com.suryaprakash.medlog.meds.Scheduler
import com.suryaprakash.medlog.nlu.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReportIntegrityHotfixTest {
    private val app = ApplicationProvider.getApplicationContext<Context>() as MedLogApp
    private val now = LocalDate.of(2026, 10, 3).atTime(8, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private fun note(id: Long, at: Long, pid: String = "vomiting", count: Int? = 1, better: Boolean = false): Note {
        val f = mutableMapOf("severity" to Fact(2, Source.ASKED))
        if (better) f["better"] = Fact(true, Source.TAPPED)
        return Note(id = id, kind = Kind.SYMPTOM, problemId = pid, occurredAt = at, count = count, details = factsToJson(f))
    }
    private fun report(ns: List<Note>, ms: List<Medicine> = emptyList(), ds: List<Dose> = emptyList()) =
        DoctorNoteBuilder(app.catalogue, app.describe).build(Profile(), now - 14 * DAY, now + DAY, ns, ms, ds, now = now)

    @Test fun oldFrequencyAlertNeverBecomesCurrent24HourCount() {
        val old = note(1, now - 5 * DAY).copy(triage = "AMBER", triageReasons = "Vomiting 6 times in 24 hours")
        val r = report(listOf(old, note(2, now - 10 * HOUR), note(3, now - 2 * DAY, better = true)))
        assertEquals(1, r.symptoms.single().reports24h)
        assertEquals(2, r.symptoms.single().reportCount)
        assertTrue(r.concerns.single().contains("Historical alert recorded"))
        assertFalse(r.concerns.joinToString().contains("6 times in 24 hours"))
        assertNull(r.symptoms.single().trend) // old improvement predates recurrence
    }
    @Test fun emptyAndBetterCheckinsAreNeverEpisodes() {
        val ns = listOf(note(1, now - HOUR, count = null).copy(details = "{}"), note(2, now - HOUR * 2, better = true), note(3, now - HOUR * 3, count = 0))
        val r = report(ns).symptoms.single()
        assertEquals(1, r.reportCount) // bare record is transparently a report, not an episode claim
        assertEquals(1, r.total)
        assertTrue(r.whenText.contains("symptom report"))
        assertFalse(r.whenText.contains(" times"))
    }
    @Test fun duplicateIdentityUsesNewestIncludingDeletionBeforeFiltering() {
        val a = note(1, now - HOUR).copy(uid = "one", updatedAt = 1)
        val b = a.copy(id = 2, updatedAt = 2, deletedAt = now)
        assertTrue(ReportIntegrity.notes(listOf(a, b), now - DAY, now + DAY, now).isEmpty())
        val c = a.copy(id = 0, uid = "")
        assertEquals(2, ReportIntegrity.notes(listOf(c, c), now - DAY, now + DAY, now).size)
    }
    @Test fun periodFutureAndRemovedRecordsDoNotPolluteAnyReportSection() {
        val ns = listOf(note(1, now - HOUR), note(2, now + HOUR), note(3, now - 20 * DAY), note(4, now - HOUR).copy(deletedAt = now),
            Note(id = 5, kind = Kind.QUESTION, occurredAt = now + HOUR, transcript = "Future question"))
        val r = report(ns)
        assertEquals(1, r.symptoms.single().reportCount)
        assertTrue(r.questions.isEmpty())
    }
    @Test fun quoteIsDatedAndLatestRatherThanLongestOrMergedTranscript() {
        fun q(n: Note, text: String) = n.copy(details = factsToJson(mapOf("note" to Fact(text, Source.ASKED))))
        val r = report(listOf(q(note(1, now - 4 * DAY), "Older context"), q(note(2, now - HOUR), "New context"))).symptoms.single()
        assertEquals("New context", r.quote)
        assertNotNull(r.quoteDate)
        assertTrue(r.notes.contains("Additional note"))
    }
    @Test fun stoppedDuplicateListEntryDoesNotClaimActiveTreatmentStopped() {
        val active = Medicine(id = 1, name = "Sompraz IT", startDate = now - 5 * DAY, times = "08:00")
        val old = active.copy(id = 2, active = false, changedAt = now - 5 * DAY, changeNote = "stopped")
        val r = report(emptyList(), listOf(active, old))
        assertTrue(r.medicines.first().change.contains("Currently active"))
        assertTrue(r.medicines.last().change.contains("matching medicine is currently active"))
        assertFalse(r.medicines.last().change.contains("stopped"))
        assertFalse(ReportIntegrity.continued(old.copy(strength = "different"), listOf(active)))
    }
    @Test fun sameDayMovedReminderIsExcludedButConfirmedAndEarlierDosesSurvive() {
        val day = now - 4 * DAY
        val m = Medicine(id = 1, name = "Test medicine", times = "14:00", startDate = now - 7 * DAY, changedAt = day + 4 * HOUR, changeNote = "times changed")
        // now is 08:00, so old 12:30 is +4.5h, new 14:00 is +6h
        val old = Dose(id = 1, medicineId = 1, scheduledAt = day + 270 * 60_000L, status = DoseStatus.MISSED)
        val taken = old.copy(id = 2, scheduledAt = day + 6 * HOUR, status = DoseStatus.TAKEN, actedAt = day + 6 * HOUR)
        val earlier = old.copy(id = 3, scheduledAt = old.scheduledAt - DAY)
        assertTrue(MedicineSchedule.obsolete(m, old))
        assertFalse(MedicineSchedule.obsolete(m, old.copy(status = DoseStatus.TAKEN, actedAt = old.scheduledAt)))
        assertFalse(MedicineSchedule.obsolete(m, earlier))
        val r = report(emptyList(), listOf(m), listOf(old, taken, earlier))
        assertEquals(2, r.medicines.single().due)
        assertEquals(1, r.medicines.single().done)
    }
    @Test fun cancelledStoppedAndFutureSlotsNeverPenalizeAdherence() {
        val m = Medicine(id = 1, name = "Test medicine")
        val taken = Dose(id = 1, medicineId = 1, scheduledAt = now - HOUR, status = DoseStatus.TAKEN, actedAt = now - HOUR, reason = "old skip reason")
        val ds = listOf(taken, taken.copy(id = 2, scheduledAt = now - 2 * HOUR, status = DoseStatus.CANCELLED),
            taken.copy(id = 3, scheduledAt = now - 3 * HOUR, status = DoseStatus.SKIPPED, reason = "Stopped"), taken.copy(id = 4, scheduledAt = now + HOUR))
        val med = report(emptyList(), listOf(m), ds).medicines.single()
        assertEquals("1/1", med.taken)
        assertFalse(med.change.contains("skipped:"))
    }
    @Test fun firstLoggingTimeIsNeverMedicineInducedSymptomOnset() {
        val m = Medicine(id = 1, name = "Test medicine", startDate = now - 5 * DAY, changedAt = now - 5 * DAY, changeNote = "started")
        assertTrue(report(listOf(note(1, now - 5 * DAY + HOUR, "stomach_pain")), listOf(m)).links.isEmpty())
    }
    @Test fun repeatedScheduleEditsAreDetectedEvenWithSameChangeLabel() {
        val old = Medicine(id = 1, name = "Test", times = "12:30", changeNote = "times changed")
        assertTrue(MedicineSchedule.changed(old, old.copy(times = "14:00")))
        assertTrue(MedicineSchedule.changed(old, old.copy(days = "1,3,5")))
        assertFalse(MedicineSchedule.changed(old, old.copy(pillsLeft = 10.0)))
    }
    @Test fun concurrentTakingOnlyConsumesOnePillAndClearsOldSkipReason() = runBlocking {
        val id = app.db.medicines().insert(Medicine(name = "Test", asNeeded = true, pillsLeft = 10.0))
        val did = app.db.doses().insert(Dose(medicineId = id, scheduledAt = System.currentTimeMillis(), status = DoseStatus.SKIPPED, reason = "Other"))
        coroutineScope { List(4) { async(Dispatchers.Default) { Scheduler.take(app, did) } }.awaitAll() }
        assertEquals(9.0, app.db.medicines().get(id)!!.pillsLeft!!, 0.0)
        assertNull(app.db.doses().get(did)!!.reason)
    }
    @Test fun scheduleCancellationCannotOverwriteConcurrentConfirmedTake() = runBlocking {
        val id = app.db.medicines().insert(Medicine(name = "Test", asNeeded = true))
        val did = app.db.doses().insert(Dose(medicineId = id, scheduledAt = System.currentTimeMillis()))
        val before = app.db.doses().get(did)!!
        app.db.doses().update(before.copy(status = DoseStatus.TAKEN, actedAt = System.currentTimeMillis()))
        assertEquals(0, app.db.doses().cancelUnconfirmed(did, before.updatedAt))
        assertEquals(DoseStatus.TAKEN, app.db.doses().get(did)!!.status)
    }
    @Test fun reinstatedScheduleCanRestoreUnconfirmedCancelledSlot() = runBlocking {
        val mid = app.db.medicines().insert(Medicine(name = "Test", asNeeded = true))
        val at = System.currentTimeMillis()
        val did = app.db.doses().insert(Dose(medicineId = mid, scheduledAt = at, status = DoseStatus.CANCELLED, reason = MedicineSchedule.REPLACED))
        assertEquals(1, app.db.doses().restoreScheduled(mid, at))
        assertEquals(DoseStatus.DUE, app.db.doses().get(did)!!.status)
        assertNull(app.db.doses().get(did)!!.reason)
    }
    @Test fun emptySyncIdentityNeverMergesUnrelatedPatientRecords() = runBlocking {
        val db = MedDb.open(app, "sync-fixture")
        val n = org.json.JSONObject().put("uid", "").put("u", 123L).put("kind", Kind.SYMPTOM).put("problemId", "cough").put("at", now).put("created", now)
        Sync.apply(db, org.json.JSONObject().put("notes", org.json.JSONArray().put(n)), hub = false)
        assertTrue(db.notes().between(0, Long.MAX_VALUE).isEmpty())
        db.close()
    }
    @Test fun nutritionExcludesFutureAndCancelledRecordsAndQualifiesIncompleteIntake() = runBlocking {
        val at = System.currentTimeMillis()
        app.db.notes().insert(Note(kind = Kind.FOOD, occurredAt = at - HOUR, details = "{\"kcal\":200,\"protein\":5}"))
        app.db.notes().insert(Note(kind = Kind.FOOD, occurredAt = at + DAY, details = "{\"kcal\":9000,\"protein\":500}"))
        app.db.notes().insert(Note(kind = Kind.READING, occurredAt = at - HOUR, details = "{\"type\":\"weight\",\"v1\":50}"))
        val mid = app.db.medicines().insert(Medicine(name = "Test feed", form = "feed", amount = "100 ml"))
        app.db.doses().insert(Dose(medicineId = mid, scheduledAt = at - 4 * HOUR, status = DoseStatus.CANCELLED, reason = MedicineSchedule.REPLACED))
        val r = com.suryaprakash.medlog.nutrition.Nutrition.build(app, 14, at)
        assertEquals(200.0, r.avgKcal, 0.01)
        assertTrue(r.missed.isEmpty())
        assertTrue(r.findings.any { "cannot establish total intake" in it.text })
        assertFalse(r.findings.any { "Eating far too little" in it.text })
    }

}
