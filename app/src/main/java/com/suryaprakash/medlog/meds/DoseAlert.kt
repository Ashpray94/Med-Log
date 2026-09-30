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
import com.suryaprakash.medlog.notify.NotifySpec
import com.suryaprakash.medlog.notify.NotifySpec.Type
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Shows a medicine reminder: full screen over the lock screen, with alarm sound, plus a notification with buttons. */
object DoseAlert {
    private const val BASE_ID = NotifySpec.DOSE_ID

    suspend fun show(ctx: Context, doses: List<Dose>, louder: Boolean) {
        val app = ctx.medlog
        val meds = doses.mapNotNull { d -> app.db.medicines().get(d.medicineId)?.let { d to it } }
        if (meds.isEmpty()) return
        val title = if (meds.all { it.second.form == "feed" }) "Time to give the feed" else if (meds.size == 1) "Time for ${meds[0].second.name}" else "Time for your medicines"
        val text = meds.joinToString(", ") { (_, m) -> "${m.name} ${m.strength}".trim() }
        val open = Intent(ctx, DoseActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP).putExtra("louder", louder)
        val full = PendingIntent.getActivity(ctx, 70, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val first = meds.first().first
        fun action(kind: String) = PendingIntent.getBroadcast(ctx, (first.id * 10 + kind.length).toInt(),
            Intent(ctx, DoseActionReceiver::class.java).putExtra("dose", first.id).putExtra("all", doses.map { it.id }.toLongArray()).putExtra("kind", kind),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val spec = NotifySpec[Type.DOSE_DUE]
        val n = NotificationCompat.Builder(ctx, spec.chan.id)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(com.suryaprakash.medlog.ui.tr(title)).setContentText(com.suryaprakash.medlog.ui.tr(text))
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_MAX).setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(full, true).setContentIntent(full)
            .setOngoing(true).setAutoCancel(false)
            .apply {
                // Android shows three buttons: the first three responses of the table (the screen has all four)
                for (r in spec.responses.take(3)) {
                    val pi = if (r.asksWhy) skipAsk(ctx, first.id) else action(r.id)
                    addAction(0, NotifySpec.label(Type.DOSE_DUE, r.id, app.settings.value.snoozeMinutes), pi)
                }
            }
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(BASE_ID, n) }
        // Also start the screen directly: on older phones and when the phone is unlocked
        runCatching { ctx.startActivity(open) }
    }

    private fun skipAsk(ctx: Context, doseId: Long) = PendingIntent.getActivity(ctx, (doseId * 10 + 9).toInt(),
        Intent(ctx, DoseActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP).putExtra("skipDose", doseId),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    /** A dose that was not taken within the miss window: quiet notice with Take now / Skip / Tell helper (NotifySpec DOSE_MISSED). */
    fun showMissed(ctx: Context, dose: Dose, m: com.suryaprakash.medlog.data.Medicine) {
        val spec = NotifySpec[Type.DOSE_MISSED]
        val id = NotifySpec.MISSED_ID + (dose.id % 100).toInt()
        fun action(kind: String) = PendingIntent.getBroadcast(ctx, (dose.id * 10 + 100 + kind.length).toInt(),
            Intent(ctx, DoseActionReceiver::class.java).putExtra("dose", dose.id).putExtra("kind", kind).putExtra("missed", true).putExtra("notif", id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val open = skipAsk(ctx, dose.id)
        val n = NotificationCompat.Builder(ctx, spec.chan.id).setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(com.suryaprakash.medlog.ui.tr(spec.title)).setContentText(com.suryaprakash.medlog.ui.tr(spec.body.replace("{medicine}", m.name)))
            .setPriority(NotificationCompat.PRIORITY_HIGH).setCategory(NotificationCompat.CATEGORY_REMINDER).setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true).setContentIntent(open)
            .apply {
                for (r in spec.responses.take(3)) addAction(0, r.label, if (r.asksWhy) skipAsk(ctx, dose.id) else action(r.id))
            }.build()
        runCatching { NotificationManagerCompat.from(ctx).notify(id, n) }
    }

    /** Silences the reminder screen and sound without answering ("Not now"). */
    fun dismiss(ctx: Context) { NotificationManagerCompat.from(ctx).cancel(BASE_ID); AlarmTone.stop() }

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
        val missed = intent.getBooleanExtra("missed", false)
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                for (id in all) when (kind) {
                    "take" -> Scheduler.take(ctx, id)
                    "snooze" -> Scheduler.snooze(ctx, id)
                    "later" -> Scheduler.notNow(ctx, id)
                    "tell" -> tellHelper(ctx, id)
                }
                AlarmTone.stop()
                if (missed) NotificationManagerCompat.from(ctx).cancel(intent.getIntExtra("notif", NotifySpec.MISSED_ID))
                else if (kind != "later") NotificationManagerCompat.from(ctx).cancel(NotifySpec.DOSE_ID)
            } finally { pending.finish() }
        }
    }
}

private suspend fun tellHelper(ctx: Context, doseId: Long) {
    val app = ctx.medlog
    val d = app.db.doses().get(doseId) ?: return
    val m = app.db.medicines().get(d.medicineId) ?: return
    val t = DoseActivity.time(d.scheduledAt)
    com.suryaprakash.medlog.help.Alerts.send(ctx, com.suryaprakash.medlog.help.Alerts.Type.MISSED_DOSE, com.suryaprakash.medlog.help.Wording.missedDose(app.repo.profile().name, t, m.name))
    NotifySpec.responded(Type.DOSE_MISSED, "tell", NotifySpec.Who.PATIENT, escalated = true)
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
