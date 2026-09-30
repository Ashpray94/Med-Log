package com.suryaprakash.medlog.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import com.suryaprakash.medlog.help.Relay

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, manifest = Config.NONE, sdk = [28])
class PairedSyncTest {
    @Test
    fun `inbound topic uses the reverse of the peer outgoing direction`() {
        assertEquals(Relay.UP, Sync.inboundDir(Relay.DOWN))
        assertEquals(Relay.DOWN, Sync.inboundDir(Relay.UP))
    }

    @Test
    fun `changed since includes all rows tied at the limit boundary`() = kotlinx.coroutines.runBlocking {
        val db = MedDb.open(RuntimeEnvironment.getApplication(), "same-timestamp-sync-test.db")
        try {
            repeat(401) { index ->
                db.notes().insert(Note(uid = "same-time-$index", updatedAt = 77, kind = Kind.WATER, occurredAt = index.toLong(), text = "Water $index"))
            }
            val rows = db.notes().changedSince(since = 0, limit = 400)
            assertEquals(401, rows.size)
            assertEquals(401, rows.map { it.uid }.distinct().size)
            assertTrue(rows.all { it.updatedAt == 77L })
        } finally {
            db.close()
        }
    }

    @Test
    fun `partial packet failure rolls back earlier row changes`() = kotlinx.coroutines.runBlocking {
        val db = MedDb.open(RuntimeEnvironment.getApplication(), "transaction-sync-test.db")
        try {
            val malformedNote = JSONObject().put("_type", "note").put("kind", Kind.SYMPTOM).put("u", 50)
            val failure = runCatching {
                Sync.apply(db, batch(medicine("transaction-med", 50, "Must roll back"), malformedNote), hub = false)
            }.exceptionOrNull()
            assertTrue(failure != null)
            assertNull(db.medicines().byUid("transaction-med"))
        } finally {
            db.close()
        }
    }

    @Test
    fun `stable ids pair records across devices edits repeat packets tombstones and restore`() = kotlinx.coroutines.runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val source = MedDb.open(context, "source-sync-test.db")
        val receiver = MedDb.open(context, "receiver-sync-test.db")
        try {
            val sourceMedId = source.medicines().insert(Medicine(uid = "shared-med", updatedAt = 100, name = "Medicine A", strength = "10 mg"))
            val sourceNoteId = source.notes().insert(Note(uid = "shared-note", updatedAt = 100, kind = Kind.SYMPTOM, occurredAt = 80,
                createdAt = 70, text = "Cough", problemId = "cough", groupId = null))
            source.doses().insert(Dose(uid = "shared-dose", updatedAt = 100, medicineId = sourceMedId, scheduledAt = 200,
                status = DoseStatus.TAKEN, actedAt = 210, reason = "taken"))

            // Force different local IDs before applying the source records.
            receiver.medicines().insert(Medicine(name = "Unrelated local medicine"))
            receiver.notes().insert(Note(kind = Kind.WATER, occurredAt = 1, text = "Local water"))
            val initial = batch(
                medicine(uid = "shared-med", updatedAt = 100, name = "Medicine A"),
                note(uid = "shared-note", updatedAt = 100, text = "Cough"),
                dose(uid = "shared-dose", updatedAt = 100, medicineUid = "shared-med"),
            )
            Sync.apply(receiver, initial, hub = false)

            val receiverMed = receiver.medicines().byUid("shared-med")!!
            assertNotEquals(sourceMedId, receiverMed.id)
            val receiverDose = receiver.doses().byUid("shared-dose")!!
            assertEquals(receiverMed.id, receiverDose.medicineId)
            assertNotEquals(sourceNoteId, receiver.notes().byUid("shared-note")!!.id)

            Sync.apply(receiver, initial, hub = false)
            assertEquals(1, receiver.medicines().all().count { it.uid == "shared-med" })
            assertEquals(1, receiver.notes().everything().count { it.uid == "shared-note" })
            assertEquals(1, receiver.doses().everything().count { it.uid == "shared-dose" })

            // A helper edit returns to the source without changing its local primary key.
            Sync.apply(source, batch(medicine(uid = "shared-med", updatedAt = 200, name = "Medicine B")), hub = true)
            assertEquals("Medicine B", source.medicines().byUid("shared-med")!!.name)
            assertEquals(sourceMedId, source.medicines().byUid("shared-med")!!.id)

            // A later source edit reaches the receiver; older packets cannot roll it back.
            Sync.apply(receiver, batch(medicine(uid = "shared-med", updatedAt = 300, name = "Medicine C")), hub = false)
            Sync.apply(receiver, batch(medicine(uid = "shared-med", updatedAt = 250, name = "Stale value")), hub = false)
            assertEquals("Medicine C", receiver.medicines().byUid("shared-med")!!.name)

            Sync.apply(receiver, batch(note(uid = "shared-note", updatedAt = 400, text = "Cough", deletedAt = 400)), hub = false)
            assertEquals(400L, receiver.notes().byUid("shared-note")!!.deletedAt)
            Sync.apply(receiver, batch(note(uid = "shared-note", updatedAt = 500, text = "Cough", deletedAt = 0)), hub = false)
            assertNull(receiver.notes().byUid("shared-note")!!.deletedAt)
            Sync.apply(receiver, batch(note(uid = "shared-note", updatedAt = 450, text = "Old deleted copy", deletedAt = 450)), hub = false)
            assertNull(receiver.notes().byUid("shared-note")!!.deletedAt)
            assertTrue(receiver.doses().byUid("shared-dose")!!.medicineId == receiverMed.id)
        } finally {
            source.close()
            receiver.close()
        }
    }

    private fun batch(vararg entries: JSONObject): JSONObject = JSONObject()
        .put("medicines", JSONArray().apply { entries.filter { it.optString("_type") == "medicine" }.forEach(::put) })
        .put("notes", JSONArray().apply { entries.filter { it.optString("_type") == "note" }.forEach(::put) })
        .put("doses", JSONArray().apply { entries.filter { it.optString("_type") == "dose" }.forEach(::put) })

    private fun medicine(uid: String, updatedAt: Long, name: String) = JSONObject()
        .put("_type", "medicine").put("uid", uid).put("u", updatedAt).put("name", name).put("strength", "10 mg")
        .put("form", "tablet").put("amount", "1").put("food", "any").put("times", "08:00").put("days", "")
        .put("start", 10).put("end", 0).put("critical", false).put("asNeeded", false).put("gap", 4)
        .put("purpose", "").put("left", -1).put("active", true).put("thinner", false).put("changed", 10)
        .put("note", "").put("shape", "").put("color", "")

    private fun note(uid: String, updatedAt: Long, text: String, deletedAt: Long = 0) = JSONObject()
        .put("_type", "note").put("uid", uid).put("u", updatedAt).put("kind", Kind.SYMPTOM).put("problemId", "cough")
        .put("at", 80).put("created", 70).put("transcript", "").put("details", "{}").put("severity", -1).put("count", -1)
        .put("triage", "GREEN").put("reasons", "").put("group", -1).put("deleted", deletedAt).put("text", text)

    private fun dose(uid: String, updatedAt: Long, medicineUid: String) = JSONObject()
        .put("_type", "dose").put("uid", uid).put("u", updatedAt).put("med", medicineUid).put("at", 200)
        .put("status", DoseStatus.TAKEN).put("acted", 210).put("reason", "taken")
}
