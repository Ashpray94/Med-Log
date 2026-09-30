package com.suryaprakash.medlog.data

import android.app.Application
import android.content.Context
import android.util.Base64
import com.suryaprakash.medlog.help.Relay
import com.suryaprakash.medlog.help.FamilyChat
import com.suryaprakash.medlog.medlog
import com.suryaprakash.medlog.ui.Announce
import com.suryaprakash.medlog.ui.Event
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.LinkedBlockingQueue

/** Sync entries between phones using Last-Writer-Wins (LWW) and uid-based deduplication. */
object Sync {
    private const val DIR = "family"

    private var app: Application? = null
    private lateinit var scope: CoroutineScope
    @Volatile private var applyingRemote = false
    private val queue = LinkedBlockingQueue<JSONObject>(100)

    fun init(application: Application) {
        app = application
        val ctx = application.applicationContext
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        Announce.onLocal = { e -> publish(ctx, e) }
        scope.launch {
            while (isActive) {
                delay(30_000L)
                retryQueue(ctx)
            }
        }
    }

    /** Publish a local entry to other phones. Called from Repo after mutations. */
    fun local(type: String, op: String, localId: Long) {
        val a = app ?: return
        val ctx = a.applicationContext
        scope.launch {
            val db = ctx.medlog.db
            val actor = FamilyChat.myName(ctx).ifBlank { "You" }
            val (kind, what) = when (type) {
                "note" -> {
                    val note = db.notes().get(localId) ?: return@launch
                    when (op) {
                        "insert" -> Kind.SYMPTOM to "added a note"
                        "update" -> Kind.SYMPTOM to "updated a note"
                        "delete" -> Kind.SYMPTOM to "removed a note"
                        else -> return@launch
                    }
                }
                "medicine" -> {
                    val med = db.medicines().get(localId) ?: return@launch
                    when (op) {
                        "insert" -> med.name to "added medicine"
                        "update" -> med.name to "updated medicine"
                        "delete" -> med.name to "stopped medicine"
                        else -> return@launch
                    }
                }
                "dose" -> {
                    val dose = db.doses().get(localId) ?: return@launch
                    when (op) {
                        "insert" -> "dose" to "scheduled dose"
                        "update" -> "dose" to "updated dose"
                        "delete" -> "dose" to "deleted dose"
                        else -> return@launch
                    }
                }
                "appointment" -> {
                    val appt = db.appointments().get(localId) ?: return@launch
                    when (op) {
                        "insert" -> "appointment" to "added appointment"
                        "update" -> "appointment" to "updated appointment"
                        "delete" -> "appointment" to "removed appointment"
                        else -> return@launch
                    }
                }
                else -> return@launch
            }
            val event = Event(op, actor, what, localId, System.currentTimeMillis())
            publishToRelay(ctx, type, localId, op, event)
        }
    }

    /** Published a local event to other phones via Announce.onLocal callback. */
    private fun publish(ctx: Context, e: Event) {
        if (applyingRemote) return
        // Note: The actual publish to relay is done via local() from Repo
    }

    private suspend fun publishToRelay(ctx: Context, type: String, localId: Long, op: String, event: Event) {
        val db = ctx.medlog.db
        val now = System.currentTimeMillis()

        val json = when (type) {
            "note" -> db.notes().get(localId)?.let { toJsonNote(it) } ?: return
            "medicine" -> db.medicines().get(localId)?.let { toJsonMedicine(it) } ?: return
            "dose" -> db.doses().get(localId)?.let { toJsonDose(it) } ?: return
            "appointment" -> db.appointments().get(localId)?.let { toJsonAppointment(it) } ?: return
            else -> return
        }

        val uid = db.sync().byLocal(type, localId)?.uid ?: UUID.randomUUID().toString()
        val body = JSONObject().apply {
            put("sync", 1)
            put("uid", uid)
            put("kind", event.kind)
            put("actor", event.actor)
            put("what", event.what)
            put("at", now)
            put("op", op)
            put("type", type)
            put("payload", json)
        }
        db.sync().put(SyncMeta(uid, type, localId, now, op == "delete"))

        val keyStr = FamilyChat.familyKey(ctx)
        val key = Base64.decode(keyStr, Base64.NO_WRAP)
        if (!Relay.post(ctx, key, DIR, body)) {
            if (queue.size >= 100) queue.poll()
            queue.offer(body)
        }
    }

    /** Receive a sync message from another phone. */
    fun receive(ctx: Context, o: JSONObject) {
        if (applyingRemote) return
        val uid = o.optString("uid").takeIf { it.isNotBlank() } ?: return
        val type = o.optString("type").takeIf { it.isNotBlank() } ?: return
        val op = o.optString("op").takeIf { it.isNotBlank() } ?: return
        val incomingAt = o.optLong("at")
        val payload = o.optJSONObject("payload") ?: JSONObject()

        val app = ctx.medlog
        app.scope.launch {
            val db = app.db
            val existing = db.sync().get(uid)
            if (!shouldApply(existing?.updatedAt, incomingAt, existing?.deleted ?: false)) return@launch

            applyingRemote = true
            try {
                val localId = when (type) {
                    "note" -> applyNote(ctx, uid, op, payload, existing?.localId)
                    "medicine" -> applyMedicine(ctx, uid, op, payload, existing?.localId)
                    "dose" -> applyDose(ctx, uid, op, payload, existing?.localId)
                    "appointment" -> applyAppointment(ctx, uid, op, payload, existing?.localId)
                    else -> return@launch
                }
                db.sync().put(SyncMeta(uid, type, localId, incomingAt, op == "delete"))
                Announce.remote(ctx, Event(o.optString("kind"), o.optString("actor"), o.optString("what"), localId, incomingAt))
            } finally {
                applyingRemote = false
            }
        }
    }

    private suspend fun applyNote(ctx: Context, uid: String, op: String, payload: JSONObject, localId: Long?): Long {
        val db = ctx.medlog.db
        val note = fromJsonNote(payload)
        return when (op) {
            "delete" -> {
                if (localId != null) {
                    db.notes().remove(localId, payload.optLong("at", System.currentTimeMillis()))
                    localId
                } else 0
            }
            else -> {
                if (localId != null) {
                    val existing = db.notes().get(localId)
                    if (existing != null) {
                        db.notes().update(existing.copy(
                            kind = note.kind,
                            problemId = note.problemId,
                            occurredAt = note.occurredAt,
                            transcript = note.transcript,
                            details = note.details,
                            severity = note.severity,
                            count = note.count,
                            triage = note.triage,
                            triageReasons = note.triageReasons,
                            text = note.text
                        ))
                    }
                    localId
                } else {
                    db.notes().insert(note)
                }
            }
        }
    }

    private suspend fun applyMedicine(ctx: Context, uid: String, op: String, payload: JSONObject, localId: Long?): Long {
        val db = ctx.medlog.db
        val med = fromJsonMedicine(payload)
        return when (op) {
            "delete" -> {
                if (localId != null) {
                    val existing = db.medicines().get(localId)
                    if (existing != null) {
                        db.medicines().update(existing.copy(active = false, changedAt = System.currentTimeMillis()))
                    }
                    localId
                } else 0
            }
            else -> {
                if (localId != null) {
                    val existing = db.medicines().get(localId)
                    if (existing != null) {
                        db.medicines().update(med.copy(id = localId))
                    }
                    localId
                } else {
                    db.medicines().insert(med)
                }
            }
        }
    }

    private suspend fun applyDose(ctx: Context, uid: String, op: String, payload: JSONObject, localId: Long?): Long {
        val db = ctx.medlog.db
        val dose = fromJsonDose(payload)
        return when (op) {
            "delete" -> {
                if (localId != null) {
                    val d = db.doses().get(localId) ?: return 0
                    db.doses().update(d.copy(status = DoseStatus.SKIPPED))
                    localId
                } else 0
            }
            else -> {
                if (localId != null) {
                    db.doses().update(dose.copy(id = localId))
                    localId
                } else {
                    db.doses().insert(dose)
                }
            }
        }
    }

    private suspend fun applyAppointment(ctx: Context, uid: String, op: String, payload: JSONObject, localId: Long?): Long {
        val db = ctx.medlog.db
        val appt = fromJsonAppointment(payload)
        return when (op) {
            "delete" -> {
                if (localId != null) {
                    db.appointments().delete(localId)
                    localId
                } else 0
            }
            else -> {
                if (localId != null) {
                    db.appointments().update(appt.copy(id = localId))
                    localId
                } else {
                    db.appointments().insert(appt)
                }
            }
        }
    }

    private suspend fun retryQueue(ctx: Context) {
        val keyStr = FamilyChat.familyKey(ctx)
        val key = Base64.decode(keyStr, Base64.NO_WRAP)
        val batch = mutableListOf<JSONObject>()
        queue.drainTo(batch)
        for (body in batch) {
            if (!Relay.post(ctx, key, DIR, body)) {
                if (queue.size >= 100) queue.poll()
                queue.offer(body)
            }
        }
    }

    // ──── JSON serialization ────

    private fun toJsonNote(n: Note): JSONObject = JSONObject().apply {
        put("kind", n.kind)
        put("problemId", n.problemId)
        put("occurredAt", n.occurredAt)
        put("createdAt", n.createdAt)
        put("transcript", n.transcript)
        put("details", n.details)
        put("severity", n.severity)
        put("count", n.count)
        put("triage", n.triage)
        put("triageReasons", n.triageReasons)
        put("text", n.text)
    }

    private fun fromJsonNote(o: JSONObject): Note = Note(
        kind = o.getString("kind"),
        problemId = o.optString("problemId", "").takeIf { it.isNotBlank() },
        occurredAt = o.getLong("occurredAt"),
        createdAt = o.optLong("createdAt", System.currentTimeMillis()),
        transcript = o.optString("transcript", "").takeIf { it.isNotBlank() },
        details = o.optString("details", "{}"),
        severity = if (o.has("severity")) o.optInt("severity").takeIf { it != 0 } else null,
        count = if (o.has("count")) o.optInt("count").takeIf { it != 0 } else null,
        triage = o.optString("triage", "GREEN"),
        triageReasons = o.optString("triageReasons", ""),
        text = o.getString("text")
    )

    private fun toJsonMedicine(m: Medicine): JSONObject = JSONObject().apply {
        put("name", m.name)
        put("strength", m.strength)
        put("form", m.form)
        put("amount", m.amount)
        put("food", m.food)
        put("times", m.times)
        put("days", m.days)
        put("startDate", m.startDate)
        if (m.endDate != null) put("endDate", m.endDate)
        put("critical", m.critical)
        put("asNeeded", m.asNeeded)
        put("minGapHours", m.minGapHours)
        put("purpose", m.purpose)
        if (m.pillsLeft != null) put("pillsLeft", m.pillsLeft)
        put("active", m.active)
        put("bloodThinner", m.bloodThinner)
        put("changedAt", m.changedAt)
        put("changeNote", m.changeNote)
        put("shape", m.shape)
        put("color", m.color)
    }

    private fun fromJsonMedicine(o: JSONObject): Medicine = Medicine(
        name = o.getString("name"),
        strength = o.optString("strength", ""),
        form = o.optString("form", "tablet"),
        amount = o.optString("amount", "1"),
        food = o.optString("food", "any"),
        times = o.optString("times", ""),
        days = o.optString("days", ""),
        startDate = o.optLong("startDate", System.currentTimeMillis()),
        endDate = if (o.has("endDate")) o.optLong("endDate").takeIf { it > 0 } else null,
        critical = o.optBoolean("critical", false),
        asNeeded = o.optBoolean("asNeeded", false),
        minGapHours = o.optInt("minGapHours", 4),
        purpose = o.optString("purpose", ""),
        pillsLeft = if (o.has("pillsLeft")) o.optDouble("pillsLeft").takeIf { it > 0 } else null,
        active = o.optBoolean("active", true),
        bloodThinner = o.optBoolean("bloodThinner", false),
        changedAt = o.optLong("changedAt", System.currentTimeMillis()),
        changeNote = o.optString("changeNote", "")
    )

    private fun toJsonDose(d: Dose): JSONObject = JSONObject().apply {
        put("medicineId", d.medicineId)
        put("scheduledAt", d.scheduledAt)
        put("status", d.status)
        if (d.actedAt != null) put("actedAt", d.actedAt)
        if (d.reason != null) put("reason", d.reason)
        if (d.snoozeUntil != null) put("snoozeUntil", d.snoozeUntil)
        put("reminded", d.reminded)
        put("helperAlerted", d.helperAlerted)
        if (d.shownBy != null) put("shownBy", d.shownBy)
    }

    private fun fromJsonDose(o: JSONObject): Dose = Dose(
        medicineId = o.getLong("medicineId"),
        scheduledAt = o.getLong("scheduledAt"),
        status = o.optString("status", DoseStatus.DUE),
        actedAt = if (o.has("actedAt")) o.optLong("actedAt").takeIf { it > 0 } else null,
        reason = o.optString("reason", "").takeIf { it.isNotBlank() },
        snoozeUntil = if (o.has("snoozeUntil")) o.optLong("snoozeUntil").takeIf { it > 0 } else null,
        reminded = o.optInt("reminded", 0),
        helperAlerted = o.optBoolean("helperAlerted", false),
        shownBy = o.optString("shownBy", "").takeIf { it.isNotBlank() }
    )

    private fun toJsonAppointment(a: Appointment): JSONObject = JSONObject().apply {
        put("at", a.at)
        put("doctor", a.doctor)
        put("place", a.place)
        put("purpose", a.purpose)
        if (a.calendarEventId != null) put("calendarEventId", a.calendarEventId)
        put("done", a.done)
    }

    private fun fromJsonAppointment(o: JSONObject): Appointment = Appointment(
        at = o.getLong("at"),
        doctor = o.optString("doctor", ""),
        place = o.optString("place", ""),
        purpose = o.optString("purpose", ""),
        calendarEventId = if (o.has("calendarEventId")) o.optLong("calendarEventId").takeIf { it > 0 } else null,
        done = o.optBoolean("done", false)
    )

    // ──── LWW deduplication ────

    /** Decide whether to apply an incoming event based on Last-Writer-Wins. */
    fun shouldApply(existingAt: Long?, incomingAt: Long, existingDeleted: Boolean): Boolean {
        // Unknown → apply
        if (existingAt == null) return true
        // Newer → apply
        if (incomingAt > existingAt) return true
        // Equal and tombstone → apply (tombstone overwrites edit)
        if (incomingAt == existingAt && existingDeleted) return true
        // Older or equal with no tombstone → skip
        return false
    }

    /** Handle relay onNote hook (for duplicate checking with family messages). Returns true if consumed. */
    fun onNote(ctx: Context, topic: String, o: JSONObject): Boolean {
        if (o.optInt("sync") != 1) return false
        receive(ctx, o)
        return true
    }
}
