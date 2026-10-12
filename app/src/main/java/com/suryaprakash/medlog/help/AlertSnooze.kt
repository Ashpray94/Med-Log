package com.suryaprakash.medlog.help

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.suryaprakash.medlog.medlog
import kotlinx.coroutines.launch

/**
 * "In 5 min" on a helper alert: the alarm goes quiet now and comes back in five minutes with the same alert and the
 * same three answers. (A helper's medicine reminder has its own snooze in `HelperDose.later`.)
 */
object AlertSnooze {
    const val MINUTES = 5

    fun schedule(ctx: Context, inboxId: Long, from: String, text: String, kind: String, mid: String, pairId: String?) {
        val at = System.currentTimeMillis() + MINUTES * 60_000L
        runCatching {
            ctx.getSystemService(AlarmManager::class.java).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(ctx, inboxId, from, text, kind, mid, pairId))
        }
    }

    private fun pending(ctx: Context, inboxId: Long, from: String, text: String, kind: String, mid: String, pairId: String?) =
        PendingIntent.getBroadcast(ctx, 96_000 + (inboxId % 1000).toInt(),
            Intent(ctx, AlertSnoozeReceiver::class.java).putExtra("id", inboxId).putExtra("from", from).putExtra("text", text)
                .putExtra("kind", kind).putExtra("mid", mid).putExtra("pairId", pairId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
}

/** The five minutes are up: ring the alert again. */
class AlertSnoozeReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val id = intent.getLongExtra("id", 0)
        val pending = goAsync()
        ctx.medlog.scope.launch {
            try {
                Nearby.ring(ctx, id, intent.getStringExtra("mid").orEmpty(), intent.getStringExtra("pairId").orEmpty(),
                    intent.getStringExtra("from").orEmpty(), intent.getStringExtra("text").orEmpty(), intent.getStringExtra("kind").orEmpty())
            } finally { pending.finish() }
        }
    }
}
