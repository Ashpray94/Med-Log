package com.suryaprakash.medlog.help

import android.content.Context
import com.suryaprakash.medlog.clinical.Triage
import com.suryaprakash.medlog.data.Kind
import com.suryaprakash.medlog.medlog
import com.suryaprakash.medlog.notify.NotifySpec
import kotlinx.coroutines.launch

/**
 * Tells helpers. Every alert goes by SMS (works without internet) and, when their phone is nearby,
 * straight to their MedLog Helper app over Bluetooth / Wi-Fi Direct.
 */
object Alerts {
    enum class Type { SOS, DANGER, AMBER, MISSED_DOSE, CHECKIN, FALL, MESSAGE, LOW_BATTERY, REFILL }

    /** The NotifySpec row that describes what helpers receive for each alert type (tier, responses, escalation). */
    fun spec(type: Type): NotifySpec.Row = NotifySpec[when (type) {
        Type.SOS, Type.DANGER, Type.FALL -> NotifySpec.Type.HELPER_ALERT
        Type.AMBER, Type.CHECKIN -> NotifySpec.Type.HELPER_NOTE
        Type.MISSED_DOSE -> NotifySpec.Type.HELPER_MISSED_DOSE
        Type.REFILL -> NotifySpec.Type.HELPER_REFILL
        Type.LOW_BATTERY -> NotifySpec.Type.HELPER_LOW_BATTERY
        Type.MESSAGE -> NotifySpec.Type.MESSAGE
    }]

    /**
     * A synced entry arrived on THIS phone. [by] is who added it, [fromHelper] true when a helper added it to the
     * patient's log (the patient's phone shows it), false when the patient did (helpers' phones show it).
     * Called by the sync agent. Quiet hours hold it: the entry is in the log, only the buzz waits.
     */
    fun entryAdded(ctx: Context, by: String, fromHelper: Boolean, what: String) {
        val type = if (fromHelper) NotifySpec.Type.ENTRY_BY_HELPER else NotifySpec.Type.ENTRY_BY_PATIENT
        val row = NotifySpec[type]
        if (NotifySpec.isQuiet(java.time.LocalTime.now().hour) && row.quiet == NotifySpec.Quiet.HOLD) return
        val short = what.split(" ").filter { it.isNotBlank() }.take(8).joinToString(" ")
        com.suryaprakash.medlog.care.Care.notify(ctx, row.notifId + (what.hashCode() and 0xff), Wording.entryAddedTitle(by), short, "medlog://home")
    }

    /** Records an answer to a helper-side alert and tells whoever the table says should hear about it. */
    fun answered(type: NotifySpec.Type, respId: String, escalated: Boolean = false) =
        NotifySpec.responded(type, respId, NotifySpec.Who.HELPERS, escalated)

    fun send(ctx: Context, type: Type, text: String, sosOnly: Boolean = false, alsoNearby: Boolean = true) {
        val app = ctx.medlog
        app.scope.launch {
            val helpers = app.db.helpers().all().filter { if (sosOnly) it.sos else it.alerts || it.sos }
            var sent = 0
            for (h in helpers) if (Calls.sms(ctx, h.phone, text)) sent++
            if (alsoNearby) Nearby.broadcast(ctx, type.name, text)
            app.repo.addEvent(Kind.MESSAGE, "Told helpers: $text", org.json.JSONObject().put("type", type.name).put("tier", spec(type).tier.name).put("sms", sent).toString())
        }
    }

    private suspend fun name(ctx: Context) = ctx.medlog.repo.profile().name

    fun dangerToHelpers(ctx: Context, problem: String, t: Triage) {
        ctx.medlog.scope.launch {
            val n = name(ctx)
            send(ctx, Type.DANGER, Wording.seeDoctorNow(n, problem))
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
            send(ctx, Type.AMBER, Wording.seeDoctorToday(n, problem), alsoNearby = false)
        }
    }
}
