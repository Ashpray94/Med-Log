package com.suryaprakash.medlog.help

import android.content.Context
import com.suryaprakash.medlog.clinical.Level
import com.suryaprakash.medlog.clinical.Triage
import com.suryaprakash.medlog.data.CarePlan
import com.suryaprakash.medlog.data.carePlan
import com.suryaprakash.medlog.data.Kind
import com.suryaprakash.medlog.medlog
import kotlinx.coroutines.launch

/**
 * Tells helpers through their helper app only (internet relay and Bluetooth). SMS costs money, so it is never used here:
 * only an SOS sends SMS, and only as the last resort when no helper answers in the app (see [Sos]).
 */
object Alerts {
    enum class Type { SOS, DANGER, AMBER, MISSED_DOSE, CHECKIN, FALL, MESSAGE, LOW_BATTERY, REFILL }

    /** [phones]: when given, only helpers whose number (digits only) is in the set are told (B59). Never SMS. */
    fun send(ctx: Context, type: Type, text: String, phones: Set<String>? = null) {
        val app = ctx.medlog
        app.scope.launch {
            val helpers = app.db.helpers().all().filter { it.alerts || it.sos }
                .filter { phones == null || CarePlan.digits(it.phone) in phones }
            val ids = recipients(helpers)
            if (ids.isNotEmpty()) Nearby.broadcast(ctx, type.name, text, only = { it.id in ids })
            app.repo.addEvent(Kind.MESSAGE, (if (ids.isEmpty()) "Not sent (no helper phone is paired): " else "Told helpers' phones: ") + text,
                org.json.JSONObject().put("type", type.name).put("phones", ids.size).toString())
        }
    }

    /** The helpers an app alert can reach: those whose phone is paired. Nobody else is told (no SMS outside an SOS). */
    fun recipients(helpers: List<com.suryaprakash.medlog.data.Helper>): Set<Long> =
        helpers.filter { it.pairId != null && it.pairKey != null }.map { it.id }.toSet()

    private suspend fun name(ctx: Context) = ctx.medlog.repo.profile().name

    fun dangerToHelpers(ctx: Context, problem: String, t: Triage) {
        ctx.medlog.scope.launch {
            val n = name(ctx)
            send(ctx, Type.DANGER, Wording.seeDoctorNow(n, problem, t.reasons.firstOrNull().orEmpty()))
        }
    }

    /**
     * Tells the helpers about note [noteId] once per level (B57, B59): RED texts every helper, AMBER only the helpers the
     * person's plan lists in [CarePlan.amberHelpers]. Does nothing when this level (or a higher one) was already told.
     */
    fun tellOnce(ctx: Context, noteId: Long, problem: String, t: Triage) {
        if (t.level == Level.GREEN) return
        ctx.medlog.scope.launch {
            val repo = ctx.medlog.viewRepo
            if (t.level == Level.AMBER && repo.carePlan().amberHelpers.isEmpty()) return@launch   // nobody asked: leave the note untold
            if (!repo.markTold(noteId, t.level)) return@launch
            if (t.level == Level.RED) dangerToHelpers(ctx, problem, t) else amberToHelpers(ctx, problem, t)
        }
    }

    /**
     * One of the person's own emergencies (chosen in setup) was logged: no questions first. The SOS run calls
     * their helpers in turn and alerts every paired helper phone, which rings until someone answers.
     */
    fun emergency(ctx: Context, problem: String) = Sos.start(ctx, "Emergency: $problem", countdown = false)

    fun amberToHelpers(ctx: Context, problem: String, t: Triage) {
        ctx.medlog.scope.launch {
            val n = name(ctx)
            val phones = ctx.medlog.repo.carePlan().amberHelpers.toSet()
            if (phones.isNotEmpty()) send(ctx, Type.AMBER, Wording.seeDoctorToday(n, problem, t.reasons.firstOrNull().orEmpty()), phones = phones)
        }
    }
}
