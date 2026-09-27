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
        val all = doses.mapNotNull { d -> app.db.medicines().get(d.medicineId)?.let { d to it } }
        all.filter { it.second.form == "feed" }.takeIf { it.isNotEmpty() }?.let { showFeeds(ctx, it) }
        val meds = all.filter { it.second.form != "feed" }
        if (meds.isEmpty()) return
        @Suppress("NAME_SHADOWING") val doses = meds.map { it.first }
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
            // loud, never pinned: a swipe (even of the pop-up) silences it and leaves a quiet reminder until taken
            .setAutoCancel(false)
            .setDeleteIntent(com.suryaprakash.medlog.help.SilenceReceiver.intent(ctx, BASE_ID, com.suryaprakash.medlog.ui.tr(title), "Not taken yet. Tap to open.", full))
            .addAction(0, if (meds.size == 1) "I took it" else "I took them", action("take"))
            .addAction(0, "In ${app.settings.value.snoozeMinutes} min", action("snooze"))
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(BASE_ID, n) }
        // Also start the screen directly: on older phones and when the phone is unlocked
        runCatching { ctx.startActivity(open) }
    }

    /** Feed time: a normal reminder (no alarm), answered right from the notification. */
    private fun showFeeds(ctx: Context, feeds: List<Pair<com.suryaprakash.medlog.data.Dose, com.suryaprakash.medlog.data.Medicine>>) {
        val d = feeds.first().first
        fun action(kind: String) = PendingIntent.getBroadcast(ctx, (d.id * 10 + kind.length + 3).toInt(),
            Intent(ctx, DoseActionReceiver::class.java).putExtra("dose", d.id).putExtra("all", feeds.map { it.first.id }.toLongArray()).putExtra("kind", kind).putExtra("nid", FEED_ID),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val open = PendingIntent.getActivity(ctx, 71, Intent(ctx, com.suryaprakash.medlog.MainActivity::class.java).setData(android.net.Uri.parse("medlog://food")), PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(ctx, MedLogApp.CH_CARE).setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(com.suryaprakash.medlog.ui.tr("Time to give the feed"))
            .setContentText(feeds.joinToString(", ") { (_, m) -> "${m.name}, ${m.amount}" })
            .setOnlyAlertOnce(true).setContentIntent(open).setAutoCancel(true)
            .addAction(0, "Given", action("take")).addAction(0, "Not given", action("skip"))
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(FEED_ID, n) }
    }
    private const val FEED_ID = 7100

    fun cancel(ctx: Context, @Suppress("UNUSED_PARAMETER") doseId: Long) {
        CoroutineScope(Dispatchers.IO).launch {
            val app = ctx.medlog
            val (s, e) = Scheduler.today()
            val stillDue = app.db.doses().between(s - 3 * 3600_000L, System.currentTimeMillis() + 60_000)
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
                    "skip" -> Scheduler.skip(ctx, id, "Not given")
                }
                AlarmTone.stop()
                NotificationManagerCompat.from(ctx).cancel(intent.getIntExtra("nid", 7000))
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
