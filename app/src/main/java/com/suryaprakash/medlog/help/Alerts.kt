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
 * Tells helpers. Every alert goes by SMS (works without internet) and, when their phone is nearby,
 * straight to their MedLog Helper app over Bluetooth / Wi-Fi Direct.
 */
object Alerts {
    enum class Type { SOS, DANGER, AMBER, MISSED_DOSE, CHECKIN, FALL, MESSAGE, LOW_BATTERY, REFILL }

    /** [phones]: when given, only helpers whose number (digits only) is in the set are texted (B59). */
    fun send(ctx: Context, type: Type, text: String, sosOnly: Boolean = false, alsoNearby: Boolean = true, phones: Set<String>? = null) {
        val app = ctx.medlog
        app.scope.launch {
            val helpers = app.db.helpers().all().filter { if (sosOnly) it.sos else it.alerts || it.sos }
                .filter { phones == null || CarePlan.digits(it.phone) in phones }
            var sent = 0
            for (h in helpers) if (Calls.sms(ctx, h.phone, text)) sent++
            if (alsoNearby) Nearby.broadcast(ctx, type.name, text)
            app.repo.addEvent(Kind.MESSAGE, "Told helpers: $text", org.json.JSONObject().put("type", type.name).put("sms", sent).toString())
        }
    }

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
            val repo = ctx.medlog.repo
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
            if (phones.isNotEmpty()) send(ctx, Type.AMBER, Wording.seeDoctorToday(n, problem, t.reasons.firstOrNull().orEmpty()), alsoNearby = false, phones = phones)
        }
    }
}
