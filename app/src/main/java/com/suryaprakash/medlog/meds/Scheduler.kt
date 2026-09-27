package com.suryaprakash.medlog.meds

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.suryaprakash.medlog.MainActivity
import com.suryaprakash.medlog.care.Care
import com.suryaprakash.medlog.data.DAY
import com.suryaprakash.medlog.data.Dose
import com.suryaprakash.medlog.data.DoseStatus
import com.suryaprakash.medlog.data.HOUR
import com.suryaprakash.medlog.data.Medicine
import com.suryaprakash.medlog.help.Alerts
import com.suryaprakash.medlog.medlog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Medicine reminders (plan 12.2). Like Meeting Timer, nothing polls: one exact alarm is armed for the
 * next moment something is due, and every alarm re-arms the next one.
 *
 * For each dose:  at time → reminder (full screen, alarm sound)
 *                 +snooze, +2×snooze → reminded again, louder
 *                 +30 min (15 for critical medicines) → helpers told
 *                 +3 h → marked missed
 * If Meeting Timer shows the reminder, MedLog waits 2 minutes for it; if it didn't, MedLog shows it itself.
 */
object Scheduler {
    private const val TAG = "MedLogScheduler"
    private val lock = Mutex()
    const val MISS_AFTER = 3 * HOUR
    const val MT_GRACE = 2 * 60_000L

    /** Dose times for one medicine in [from, to). */
    fun times(m: Medicine, from: Long, to: Long, zone: ZoneId = ZoneId.systemDefault()): List<Long> {
        if (m.asNeeded || m.times.isBlank() || !m.active) return emptyList()
        val slots = m.times.split(",").mapNotNull { runCatching { LocalTime.parse(it.trim()) }.getOrNull() }
        val days = m.days.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()
        val out = ArrayList<Long>()
        var d = Instant.ofEpochMilli(from).atZone(zone).toLocalDate().minusDays(1)
        val last = Instant.ofEpochMilli(to).atZone(zone).toLocalDate()
        val start = Instant.ofEpochMilli(m.startDate).atZone(zone).toLocalDate()
        val end = m.endDate?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
        while (!d.isAfter(last)) {
            val ok = !d.isBefore(start) && (end == null || !d.isAfter(end)) && (days.isEmpty() || d.dayOfWeek.value in days)
            if (ok) for (t in slots) {
                val at = d.atTime(t).atZone(zone).toInstant().toEpochMilli()
                if (at in from until to) out += at
            }
            d = d.plusDays(1)
        }
        return out
    }

    suspend fun reschedule(ctx: Context) = lock.withLock {
        val app = ctx.medlog
        val now = System.currentTimeMillis()
        val meds = app.db.medicines().active()
        // make sure dose rows exist for the next 2 days (and the last few hours, after a restart)
        for (m in meds) for (t in times(m, now - 3 * HOUR, now + 2 * DAY)) app.db.doses().insert(Dose(medicineId = m.id, scheduledAt = t))
        arm(ctx, nextWake(ctx, now))
    }

    private suspend fun nextWake(ctx: Context, now: Long): Long {
        val app = ctx.medlog
        val s = app.settings.value
        val medsById = app.db.medicines().all().associateBy { it.id }
        var next = Long.MAX_VALUE
        for (d in app.db.doses().open()) {
            val m = medsById[d.medicineId] ?: continue
            if (!m.active) continue
            for (t in events(d, m, s.snoozeMinutes, s.escalateMinutes, s.escalateCriticalMinutes, s.useMeetingTimer)) if (t > now - 1000) next = minOf(next, t)
        }
        Care.nextWake(ctx, now)?.let { next = minOf(next, it) }
        runCatching { com.suryaprakash.medlog.care.HelperCare.nextWake(ctx, now) }.getOrNull()?.let { next = minOf(next, it) }
        return next
    }

    /** The moments a dose needs attention. */
    fun events(d: Dose, m: Medicine, snooze: Int, escalate: Int, escalateCritical: Int, meetingTimer: Boolean): List<Long> {
        val base = d.snoozeUntil ?: d.scheduledAt
        val list = ArrayList<Long>()
        val showAt = if (meetingTimer && d.shownBy == null && d.snoozeUntil == null) d.scheduledAt + MT_GRACE else base
        if (d.reminded == 0 || (d.snoozeUntil != null && d.reminded < 99)) list += showAt
        if (d.reminded in 1..2) list += base + d.reminded * snooze * 60_000L
        if (!d.helperAlerted) list += d.scheduledAt + (if (m.critical) escalateCritical else escalate) * 60_000L
        list += d.scheduledAt + MISS_AFTER
        return list
    }

    private fun arm(ctx: Context, at: Long) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(ctx, 1, Intent(ctx, AlarmReceiver::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        if (at == Long.MAX_VALUE) { am.cancel(pi); return }
        val show = PendingIntent.getActivity(ctx, 2, Intent(ctx, MainActivity::class.java).setData(android.net.Uri.parse("medlog://meds")), PendingIntent.FLAG_IMMUTABLE)
        val canExact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        runCatching {
            if (canExact) am.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }.onFailure { Log.w(TAG, "arm failed", it); am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi) }
        Log.i(TAG, "next wake ${java.util.Date(at)}")
    }

    /** Called when the alarm fires: act on everything due, then arm the next one. */
    suspend fun tick(ctx: Context) {
        val app = ctx.medlog
        val s = app.settings.value
        val now = System.currentTimeMillis()
        val medsById = app.db.medicines().all().associateBy { it.id }
        val toShow = ArrayList<Dose>()
        var louder = false
        for (d in app.db.doses().open()) {
            val m = medsById[d.medicineId] ?: continue
            var dose = d
            // missed
            if (now >= d.scheduledAt + MISS_AFTER) {
                app.db.doses().update(d.copy(status = DoseStatus.MISSED))
                checkMissedInARow(ctx, m)
                continue
            }
            // escalate to helpers
            val escAt = d.scheduledAt + (if (m.critical) s.escalateCriticalMinutes else s.escalateMinutes) * 60_000L
            if (!d.helperAlerted && now >= escAt) {
                dose = dose.copy(helperAlerted = true)
                val name = app.repo.profile().name.ifBlank { "Your family member" }
                val t = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault()).format(java.util.Date(d.scheduledAt))
                Alerts.send(ctx, Alerts.Type.MISSED_DOSE, com.suryaprakash.medlog.help.Wording.missedDose(name, t, m.name, feed = m.form == "feed"))
            }
            // first reminder (or after snooze), then repeats
            val base = d.snoozeUntil ?: d.scheduledAt
            val firstAt = if (s.useMeetingTimer && d.shownBy == null && d.snoozeUntil == null) d.scheduledAt + MT_GRACE else base
            if (d.reminded == 0 && now >= firstAt || (d.snoozeUntil != null && now >= d.snoozeUntil && d.reminded == 0)) {
                dose = dose.copy(reminded = 1, shownBy = dose.shownBy ?: "medlog"); toShow += dose
            } else if (d.reminded in 1..2 && now >= base + d.reminded * s.snoozeMinutes * 60_000L) {
                dose = dose.copy(reminded = d.reminded + 1); toShow += dose; louder = true
            }
            if (dose != d) app.db.doses().update(dose)
        }
        if (toShow.isNotEmpty()) DoseAlert.show(ctx, toShow, louder)
        Care.tick(ctx, now)
        runCatching { com.suryaprakash.medlog.care.HelperCare.tick(ctx, now) }
        reschedule(ctx)
    }

    /** Two critical doses missed in a row → an amber note for the doctor page. */
    private suspend fun checkMissedInARow(ctx: Context, m: Medicine) {
        if (!m.critical) return
        val last = ctx.medlog.db.doses().lastFor(m.id, 2)
        if (last.size == 2 && last.all { it.status == DoseStatus.MISSED }) {
            ctx.medlog.repo.addEvent(com.suryaprakash.medlog.data.Kind.MESSAGE, "Missed ${m.name} twice in a row")
        }
    }

    // ───────── actions from the reminder ─────────

    enum class Taken { OK, ALREADY }

    /** Marks a dose taken. Returns ALREADY if it was already taken (the double-dose guard asks first). */
    /** [at]: when it was really taken, for noting it afterwards (at night, or for an earlier day); now if null. */
    suspend fun take(ctx: Context, doseId: Long, force: Boolean = false, at: Long? = null): Taken {
        val app = ctx.medlog
        val d = app.db.doses().get(doseId) ?: return Taken.OK
        if (d.status == DoseStatus.TAKEN && !force) {
            // already taken: only the time changes
            if (at != null) { app.db.doses().update(d.copy(actedAt = at)); app.refreshWidgets() }
            return Taken.ALREADY
        }
        app.db.doses().update(d.copy(status = DoseStatus.TAKEN, actedAt = at ?: System.currentTimeMillis(), snoozeUntil = null))
        app.db.medicines().get(d.medicineId)?.let { m -> countDown(ctx, m) }
        if (force && d.status == DoseStatus.TAKEN) {
            val m = app.db.medicines().get(d.medicineId)
            Alerts.send(ctx, Alerts.Type.MESSAGE, com.suryaprakash.medlog.help.Wording.takenTwice(app.repo.profile().name, m?.name ?: "a medicine"))
        }
        DoseAlert.cancel(ctx, doseId)
        app.refreshWidgets()
        reschedule(ctx)
        return Taken.OK
    }

    /** Takes back a "taken" tapped by mistake: the dose is open again and the pill count goes back up. */
    suspend fun untake(ctx: Context, doseId: Long) {
        val app = ctx.medlog
        val d = app.db.doses().get(doseId) ?: return
        if (d.status != DoseStatus.TAKEN) return
        app.db.doses().update(d.copy(status = DoseStatus.DUE, actedAt = null))
        app.db.medicines().get(d.medicineId)?.let { m ->
            m.pillsLeft?.let { left -> app.db.medicines().update(m.copy(pillsLeft = left + (m.amount.replace("½", "0.5").toDoubleOrNull() ?: 1.0))) }
        }
        app.refreshWidgets()
        reschedule(ctx)
    }

    suspend fun snooze(ctx: Context, doseId: Long) {
        val app = ctx.medlog
        val d = app.db.doses().get(doseId) ?: return
        app.db.doses().update(d.copy(status = DoseStatus.SNOOZED, snoozeUntil = System.currentTimeMillis() + app.settings.value.snoozeMinutes * 60_000L, reminded = 0))
        DoseAlert.cancel(ctx, doseId)
        reschedule(ctx)
    }

    suspend fun skip(ctx: Context, doseId: Long, reason: String) {
        val app = ctx.medlog
        val d = app.db.doses().get(doseId) ?: return
        app.db.doses().update(d.copy(status = DoseStatus.SKIPPED, actedAt = System.currentTimeMillis(), reason = reason, snoozeUntil = null))
        DoseAlert.cancel(ctx, doseId)
        app.refreshWidgets()
        reschedule(ctx)
    }

    /** Pill count and refill warning (plan 12.1). */
    private suspend fun countDown(ctx: Context, m: Medicine) {
        val left = m.pillsLeft ?: return
        val per = m.amount.replace("½", "0.5").toDoubleOrNull() ?: 1.0
        val now = (left - per).coerceAtLeast(0.0)
        ctx.medlog.db.medicines().update(m.copy(pillsLeft = now))
        val perDay = per * m.times.split(",").count { it.isNotBlank() }.coerceAtLeast(1)
        val days = (now / perDay).toInt()
        if (days in listOf(5, 2, 0)) Care.refill(ctx, m.name, days)
    }

    /** Next dose (for the home screen and widget). */
    suspend fun nextDose(ctx: Context): Pair<Dose, Medicine>? {
        val app = ctx.medlog
        val now = System.currentTimeMillis()
        val meds = app.db.medicines().all().associateBy { it.id }
        return app.db.doses().between(now - 3 * HOUR, now + 2 * DAY)
            .filter { it.status == DoseStatus.DUE || it.status == DoseStatus.SNOOZED }
            .sortedBy { it.scheduledAt }
            .firstNotNullOfOrNull { d -> meds[d.medicineId]?.let { d to it } }
    }

    fun today(zone: ZoneId = ZoneId.systemDefault()): Pair<Long, Long> {
        val s = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
        return s to s + DAY
    }

    @Suppress("unused") private fun dow(d: LocalDate): DayOfWeek = d.dayOfWeek
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try { Scheduler.tick(ctx) } catch (e: Exception) { Log.e("AlarmReceiver", "tick", e) } finally { pending.finish() }
        }
    }
}
