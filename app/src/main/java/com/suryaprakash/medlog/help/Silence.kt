package com.suryaprakash.medlog.help

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.suryaprakash.medlog.MedLogApp
import com.suryaprakash.medlog.R
import com.suryaprakash.medlog.medlog
import com.suryaprakash.medlog.meds.AlarmTone
import kotlinx.coroutines.launch

/**
 * The two kinds of reminder, so each one behaves the same everywhere:
 *
 * - **Loud** (medicines, a message or SOS from the person, a medicine the person hasn't taken): rings on the alarm
 *   sound until answered. It is never "ongoing", so swiping it away works everywhere, even the pop-up at the top of
 *   the screen, and a swipe always silences it. What it was about then stays in view as a quiet, pinned reminder
 *   until it is answered, so it can't be lost.
 * - **Quiet** (food and feeds, water, check-in, finishing an entry): an ordinary notification, no alarm.
 *
 * Answering anywhere (the notification's button, the alert screen, the app, or another helper answering) clears
 * the notification and stops the sound, through [done].
 */
object Loud {
    /** Stop the sound and take the reminder [id] (loud or its quiet follow-up) away. */
    fun done(ctx: Context, id: Int) {
        AlertSound.stop()
        AlarmTone.stop()
        runCatching { NotificationManagerCompat.from(ctx).cancel(id) }
    }

    /** Just the sound; the reminder stays. */
    fun silence() { AlertSound.stop(); AlarmTone.stop() }

    /** The notification id of a helper alert for inbox item [inboxId]. */
    fun alertId(inboxId: Long) = 5000 + (inboxId % 1000).toInt()

    /** Remember which inbox item a message id rang as, so another helper's answer can quiet it here. */
    fun rang(ctx: Context, mid: String, inboxId: Long) { if (mid.isNotEmpty()) ctx.medlog.settings.putLong("rang_$mid", inboxId) }

    /** Another helper answered message [mid]: it's handled, so this phone goes quiet too. */
    fun answeredElsewhere(ctx: Context, mid: String) {
        val id = ctx.medlog.settings.getLong("rang_$mid").takeIf { it > 0 } ?: return
        ctx.medlog.scope.launch { ctx.medlog.db.inbox().ack(id) }
        done(ctx, alertId(id))
    }
}

/**
 * Swiping a loud reminder away silences it at once, even with MedLog closed. What it was about stays in view as a
 * quiet, pinned reminder ("Not answered yet"), so nothing is lost; answering takes it away.
 */
class SilenceReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Loud.silence()
        val id = intent.getIntExtra(ID, 0)
        val title = intent.getStringExtra(TITLE) ?: return
        val open = intent.getParcelableExtra<PendingIntent>(OPEN)
        val keep = intent.getBooleanExtra(KEEP, true)
        val n = NotificationCompat.Builder(ctx, MedLogApp.CH_CARE).setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(title).setContentText(intent.getStringExtra(TEXT) ?: "Not answered yet. Tap to answer.")
            .setSilent(true).setOnlyAlertOnce(true)
            .setOngoing(keep).setAutoCancel(!keep)
            .apply { open?.let { setContentIntent(it) } }
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(id, n) }
    }

    companion object {
        const val ID = "id"; const val TITLE = "title"; const val TEXT = "text"; const val OPEN = "open"; const val KEEP = "keep"

        /**
         * The delete intent for a loud notification: silence, then leave a quiet reminder with the same tap action.
         * [keep]: the quiet reminder stays until answered (it can't be swiped); false for ones nothing else clears.
         */
        fun intent(ctx: Context, id: Int, title: String, text: String, open: PendingIntent?, keep: Boolean = true): PendingIntent =
            PendingIntent.getBroadcast(ctx, 90_000 + id, Intent(ctx, SilenceReceiver::class.java)
                .putExtra(ID, id).putExtra(TITLE, title).putExtra(TEXT, text).putExtra(OPEN, open).putExtra(KEEP, keep),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }
}

/** A helper answers straight from the notification ("I'm coming"), without opening anything. */
class AlertReplyReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val inboxId = intent.getLongExtra("id", 0)
        val reply = intent.getStringExtra("reply") ?: return
        val pairId = intent.getStringExtra("pairId")
        val mid = intent.getStringExtra("mid").orEmpty()
        val pending = goAsync()
        ctx.medlog.scope.launch {
            try {
                val say = AlertAnswer.perform(ctx, reply, intent.getStringExtra("kind").orEmpty(), intent.getStringExtra("text").orEmpty(),
                    intent.getStringExtra("from").orEmpty(), inboxId, mid, pairId)
                say?.let { s -> android.os.Handler(android.os.Looper.getMainLooper()).post { android.widget.Toast.makeText(ctx, com.suryaprakash.medlog.ui.tr(s), android.widget.Toast.LENGTH_LONG).show() } }
            } finally { pending.finish() }
        }
    }

    companion object {
        fun intent(ctx: Context, inboxId: Long, reply: String, mid: String, pairId: String?, slot: Int = 0, kind: String = "", text: String = "", from: String = ""): PendingIntent =
            PendingIntent.getBroadcast(ctx, 95_000 + (inboxId % 1000).toInt() * 4 + slot,
                Intent(ctx, AlertReplyReceiver::class.java).putExtra("id", inboxId).putExtra("reply", reply).putExtra("mid", mid).putExtra("pairId", pairId)
                    .putExtra("kind", kind).putExtra("text", text).putExtra("from", from),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }
}
