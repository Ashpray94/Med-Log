package com.suryaprakash.medlog.help

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.suryaprakash.medlog.MedLogApp
import com.suryaprakash.medlog.R
import com.suryaprakash.medlog.meds.AlarmTone

/**
 * Swiping an alarm away silences it at once, even with MedLog closed. What it was about stays in view as a quiet
 * notification ("Not answered yet"), so nothing is lost; that one can be swiped for good.
 */
class SilenceReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        AlertSound.stop()
        AlarmTone.stop()
        val id = intent.getIntExtra(ID, 0)
        val title = intent.getStringExtra(TITLE) ?: return
        val open = intent.getParcelableExtra<PendingIntent>(OPEN)
        val n = NotificationCompat.Builder(ctx, MedLogApp.CH_CARE).setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(title).setContentText(intent.getStringExtra(TEXT) ?: "Not answered yet. Tap to answer.")
            .setSilent(true).setOnlyAlertOnce(true).setAutoCancel(true)
            .apply { open?.let { setContentIntent(it) } }
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(id, n) }
    }

    companion object {
        const val ID = "id"; const val TITLE = "title"; const val TEXT = "text"; const val OPEN = "open"

        /** The delete intent for a loud notification: silence, then leave a quiet reminder with the same tap action. */
        fun intent(ctx: Context, id: Int, title: String, text: String, open: PendingIntent?): PendingIntent =
            PendingIntent.getBroadcast(ctx, 90_000 + id, Intent(ctx, SilenceReceiver::class.java)
                .putExtra(ID, id).putExtra(TITLE, title).putExtra(TEXT, text).putExtra(OPEN, open),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }
}
