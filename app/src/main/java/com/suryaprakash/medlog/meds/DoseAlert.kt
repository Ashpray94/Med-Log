package com.suryaprakash.medlog.meds

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.suryaprakash.medlog.MedLogApp
import com.suryaprakash.medlog.R
import com.suryaprakash.medlog.data.Dose
import com.suryaprakash.medlog.medlog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Shows a medicine reminder: full screen over the lock screen, with alarm sound, plus a notification with buttons. */
object DoseAlert {
    private const val BASE_ID = 7000

    suspend fun show(ctx: Context, doses: List<Dose>, louder: Boolean) {
        val app = ctx.medlog
        val meds = doses.mapNotNull { d -> app.ownDb.medicines().get(d.medicineId)?.let { d to it } }
        if (meds.isEmpty()) return
        val title = if (meds.all { it.second.form == "feed" }) "Time to give the feed" else if (meds.size == 1) "Time for ${meds[0].second.name}" else "Time for your medicines"
        val text = meds.joinToString(", ") { (_, m) -> "${m.name} ${m.strength}".trim() }
        val open = Intent(ctx, DoseActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP).putExtra("louder", louder)
        val full = PendingIntent.getActivity(ctx, 70, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val first = meds.first().first
        fun action(kind: String) = PendingIntent.getBroadcast(ctx, (first.id * 10 + kind.length).toInt(),
            Intent(ctx, DoseActionReceiver::class.java).putExtra("dose", first.id).putExtra("all", doses.map { it.id }.toLongArray()).putExtra("kind", kind),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(ctx, MedLogApp.CH_DOSE)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(com.suryaprakash.medlog.ui.tr(title)).setContentText(com.suryaprakash.medlog.ui.tr(text))
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_MAX).setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(full, true).setContentIntent(full)
            .setOngoing(true).setAutoCancel(false)
            .addAction(0, if (meds.size == 1) "I took it" else "I took them", action("take"))
            .addAction(0, "In ${app.settings.value.snoozeMinutes} min", action("snooze"))
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(BASE_ID, n) }
        // Also start the screen directly: on older phones and when the phone is unlocked
        runCatching { ctx.startActivity(open) }
    }

    fun cancel(ctx: Context, @Suppress("UNUSED_PARAMETER") doseId: Long) {
        CoroutineScope(Dispatchers.IO).launch {
            val app = ctx.medlog
            val (s, e) = Scheduler.today()
            val stillDue = app.ownDb.doses().between(s - 3 * 3600_000L, System.currentTimeMillis() + 60_000)
                .any { (it.status == "DUE" || it.status == "SNOOZED") && it.reminded > 0 && it.snoozeUntil == null }
            if (!stillDue) { NotificationManagerCompat.from(ctx).cancel(BASE_ID); AlarmTone.stop() }
        }
    }
}

class DoseActionReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val all = intent.getLongArrayExtra("all") ?: longArrayOf(intent.getLongExtra("dose", 0))
        val kind = intent.getStringExtra("kind")
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                for (id in all) when (kind) {
                    "take" -> Scheduler.take(ctx, id)
                    "snooze" -> Scheduler.snooze(ctx, id)
                }
                AlarmTone.stop()
                NotificationManagerCompat.from(ctx).cancel(7000)
            } finally { pending.finish() }
        }
    }
}

/** Alarm-channel sound for the reminder: rings on silent. Stops after a minute or on any answer. */
object AlarmTone {
    private var player: android.media.MediaPlayer? = null
    private val h = android.os.Handler(android.os.Looper.getMainLooper())
    fun start(ctx: Context, louder: Boolean) {
        stop()
        runCatching {
            val am = ctx.getSystemService(android.media.AudioManager::class.java)
            if (louder) am.setStreamVolume(android.media.AudioManager.STREAM_ALARM, (am.getStreamMaxVolume(android.media.AudioManager.STREAM_ALARM) * 0.9).toInt().coerceAtLeast(am.getStreamVolume(android.media.AudioManager.STREAM_ALARM)), 0)
            player = android.media.MediaPlayer().apply {
                setAudioAttributes(android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_ALARM).build())
                setDataSource(ctx, android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM))
                isLooping = true; prepare(); start()
            }
            h.postDelayed({ stop() }, 60_000)
        }
    }
    fun stop() { h.removeCallbacksAndMessages(null); runCatching { player?.stop(); player?.release() }; player = null }
}
