package com.suryaprakash.medlog.help

import android.content.Context
import com.suryaprakash.medlog.clinical.Triage
import com.suryaprakash.medlog.data.Kind
import com.suryaprakash.medlog.medlog
import kotlinx.coroutines.launch

/**
 * Tells helpers. Every alert goes by SMS (works without internet) and, when their phone is nearby,
 * straight to their MedLog Helper app over Bluetooth / Wi-Fi Direct.
 */
object Alerts {
    enum class Type { SOS, DANGER, AMBER, MISSED_DOSE, CHECKIN, FALL, MESSAGE, LOW_BATTERY, REFILL }

    fun send(ctx: Context, type: Type, text: String, sosOnly: Boolean = false, alsoNearby: Boolean = true) {
        val app = ctx.medlog
        app.scope.launch {
            val helpers = app.db.helpers().all().filter { if (sosOnly) it.sos else it.alerts || it.sos }
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
            send(ctx, Type.DANGER, Wording.seeDoctorNow(n, problem))
        }
    }

    fun amberToHelpers(ctx: Context, problem: String, t: Triage) {
        ctx.medlog.scope.launch {
            val n = name(ctx)
            send(ctx, Type.AMBER, Wording.seeDoctorToday(n, problem), alsoNearby = false)
        }
    }
}
