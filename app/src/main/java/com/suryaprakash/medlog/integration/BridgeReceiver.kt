package com.suryaprakash.medlog.integration

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.suryaprakash.medlog.data.DoseStatus
import com.suryaprakash.medlog.medlog
import com.suryaprakash.medlog.meds.Scheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/**
 * Messages from Meeting Timer's medicine card (signature-protected: only apps signed with the same key).
 *   action SHOWN : Meeting Timer is showing the reminder for med=<id> at time=<HH:mm> (MedLog then doesn't)
 *   action DOSE  : the person tapped taken / snooze / skip there
 */
class BridgeReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val med = intent.getLongExtra("med", -1)
        val time = intent.getStringExtra("time") ?: return
        val status = intent.getStringExtra("status")
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = ctx.medlog
                val t = runCatching { LocalTime.parse(time) }.getOrNull() ?: return@launch
                val zone = ZoneId.systemDefault()
                val (s, e) = Scheduler.today()
                val dose = app.ownDb.doses().between(s - 3 * 3600_000L, e).filter { it.medicineId == med }
                    .minByOrNull { kotlin.math.abs(Instant.ofEpochMilli(it.scheduledAt).atZone(zone).toLocalTime().toSecondOfDay() - t.toSecondOfDay()) } ?: return@launch
                when (intent.action) {
                    ACTION_SHOWN -> if (dose.shownBy == null) app.ownDb.doses().update(dose.copy(shownBy = "meetingtimer", reminded = 1))
                    ACTION_DOSE -> when (status) {
                        "taken" -> Scheduler.take(ctx, dose.id)
                        "snooze" -> Scheduler.snooze(ctx, dose.id)
                        "skip" -> Scheduler.skip(ctx, dose.id, intent.getStringExtra("reason") ?: "Skipped in Meeting Timer")
                    }
                }
                if (dose.status == DoseStatus.DUE) Scheduler.reschedule(ctx)
            } finally { pending.finish() }
        }
    }

    companion object {
        const val ACTION_DOSE = "com.suryaprakash.medlog.action.DOSE"
        const val ACTION_SHOWN = "com.suryaprakash.medlog.action.SHOWN"
    }
}
