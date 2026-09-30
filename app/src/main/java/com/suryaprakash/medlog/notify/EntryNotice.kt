package com.suryaprakash.medlog.notify

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.suryaprakash.medlog.MainActivity
import com.suryaprakash.medlog.MedLogApp
import com.suryaprakash.medlog.R
import com.suryaprakash.medlog.ui.tr

object EntryNotice {
    fun entryAdded(ctx: Context, who: String, what: String) {
        val row = NotifySpec[NotifySpec.Type.ENTRY_BY_PATIENT]
        val id = row.notifId + (what.hashCode() and 0xff)
        val text = "$who added $what"
        val pi = PendingIntent.getActivity(ctx, id, Intent(ctx, MainActivity::class.java).setData(android.net.Uri.parse("medlog://open?name=timeline")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val seenAction = PendingIntent.getBroadcast(ctx, (id * 10).toInt(),
            Intent(ctx, com.suryaprakash.medlog.help.AlertActionReceiver::class.java).putExtra("reply", "seen").putExtra("notifId", id).putExtra("inboxId", 0L),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(ctx, MedLogApp.CH_CARE).setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(tr("New entry")).setContentText(tr(text))
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pi).setAutoCancel(true).setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(0, NotifySpec.label(NotifySpec.Type.MESSAGE, "clear"), seenAction)
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(id, n) }
    }
}
