package com.suryaprakash.medlog.care

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.suryaprakash.medlog.MainActivity
import com.suryaprakash.medlog.MedLogApp
import com.suryaprakash.medlog.R
import com.suryaprakash.medlog.data.DAY
import com.suryaprakash.medlog.data.HOUR
import com.suryaprakash.medlog.help.AlertActivity
import com.suryaprakash.medlog.help.Alerts
import com.suryaprakash.medlog.medlog
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.launch

/**
 * Looking out for the person (plan 13.4): the morning check-in, the "hasn't checked in" alert,
 * doctor-visit reminders, refills and the Sunday summary. Shares the medicine alarm (one alarm for everything).
 */
object Care {
    private fun zone() = ZoneId.systemDefault()
    private fun at(date: LocalDate, t: LocalTime) = date.atTime(t).atZone(zone()).toInstant().toEpochMilli()
    private fun today() = LocalDate.now(zone())

    private fun checkInAt(ctx: Context): Long? {
        val s = ctx.medlog.settings.value
        if (!s.checkInEnabled || s.role != "self") return null
        val t = runCatching { LocalTime.parse(s.checkInTime) }.getOrDefault(LocalTime.of(10, 0))
        return at(today(), t)
    }

    private fun weeklyAt(): Long {
        var d = today()
        while (d.dayOfWeek != DayOfWeek.SUNDAY) d = d.plusDays(1)
        return at(d, LocalTime.of(18, 0))
    }

    suspend fun nextWake(ctx: Context, now: Long): Long? {
        val app = ctx.medlog
        val st = app.settings
        val list = ArrayList<Long>()
        checkInAt(ctx)?.let { t ->
            val doneToday = st.getString("checkin_day") == today().toString()
            if (!doneToday) {
                if (st.getString("checkin_shown") != today().toString()) list += t else list += t + 2 * HOUR
            }
            list += at(today().plusDays(1), LocalTime.parse(app.settings.value.checkInTime))
        }
        if (app.settings.value.weeklySummary && app.settings.value.role == "self") list += weeklyAt()
        for (a in app.db.appointments().upcoming(now)) {
            val eveBefore = at(java.time.Instant.ofEpochMilli(a.at).atZone(zone()).toLocalDate().minusDays(1), LocalTime.of(19, 0))
            list += eveBefore; list += a.at - 2 * HOUR
        }
        list += at(today().plusDays(1), LocalTime.of(3, 0))    // daily housekeeping
        FollowUp.pending(ctx).forEach { (_, t) -> list += t }
        return list.filter { it > now + 1000 && st.getLong("fired_$it") == 0L }.minOrNull()
    }

    suspend fun tick(ctx: Context, now: Long) {
        val app = ctx.medlog
        val st = app.settings
        fun due(t: Long) = now >= t && now - t < 6 * HOUR && st.getLong("fired_$t") == 0L
        fun mark(t: Long) = st.putLong("fired_$t", now)

        checkInAt(ctx)?.let { t ->
            val doneToday = st.getString("checkin_day") == today().toString()
            if (!doneToday && due(t) && st.getString("checkin_shown") != today().toString()) {
                mark(t); st.putString("checkin_shown", today().toString())
                ctx.startActivity(Intent(ctx, AlertActivity::class.java).putExtra(AlertActivity.MODE, AlertActivity.CHECKIN).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                notify(ctx, 8001, "How are you today?", "Tap to answer", "medlog://checkin")
            }
            val late = t + 2 * HOUR
            if (!doneToday && due(late)) {
                mark(late)
                val name = app.repo.profile().name.ifBlank { "Your family member" }
                Alerts.send(ctx, Alerts.Type.CHECKIN, com.suryaprakash.medlog.help.Wording.noCheckIn(name))
            }
        }
        val w = weeklyAt()
        if (app.settings.value.weeklySummary && due(w)) {
            mark(w)
            notify(ctx, 8002, "Your week", "Tap to hear how your week went", "medlog://reports?speak=1")
        }
        for ((noteId, t) in FollowUp.pending(ctx)) if (now >= t) {
            FollowUp.remove(ctx, noteId)
            val n = app.db.notes().get(noteId) ?: continue
            if (n.deletedAt != null) continue
            val label = app.catalogue.problem(n.problemId)?.label?.lowercase() ?: "how you feel"
            notify(ctx, 8400 + (noteId % 500).toInt(), "Can you tell me a bit more?", "A few more details about your $label help your doctor. Tap when you're ready.", "medlog://tell?note=$noteId")
        }
        for (a in app.db.appointments().upcoming(now - 3 * HOUR)) {
            val eve = at(java.time.Instant.ofEpochMilli(a.at).atZone(zone()).toLocalDate().minusDays(1), LocalTime.of(19, 0))
            if (due(eve)) { mark(eve); notify(ctx, 8100 + a.id.toInt(), "Doctor visit tomorrow", "Your doctor page is ready. Take your medicines with you.", "medlog://doctor") }
            val soon = a.at - 2 * HOUR
            if (due(soon)) { mark(soon); notify(ctx, 8200 + a.id.toInt(), "Doctor visit in 2 hours", a.doctor.ifBlank { "Your appointment" } + if (a.place.isNotBlank()) " at ${a.place}" else "", "medlog://doctor") }
        }
    }

    fun checkedIn(ctx: Context) = ctx.medlog.settings.putString("checkin_day", today().toString())

    fun refill(ctx: Context, name: String, daysLeft: Int) {
        val text = if (daysLeft <= 0) "$name has run out." else "About $daysLeft days of $name left."
        notify(ctx, 8300 + name.hashCode() % 100, "Time to buy more medicine", text, "medlog://meds")
        if (daysLeft <= 2) {
            val app = ctx.medlog
            app.scope.launchIo { Alerts.send(ctx, Alerts.Type.REFILL, com.suryaprakash.medlog.help.Wording.refill(app.repo.profile().name, text), alsoNearby = false) }
        }
    }

    fun notify(ctx: Context, id: Int, title: String, text: String, link: String) {
        val pi = PendingIntent.getActivity(ctx, id, Intent(ctx, MainActivity::class.java).setData(android.net.Uri.parse(link)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(ctx, MedLogApp.CH_CARE).setSmallIcon(R.drawable.ic_stat).setContentTitle(com.suryaprakash.medlog.ui.tr(title)).setContentText(com.suryaprakash.medlog.ui.tr(text))
            .setStyle(NotificationCompat.BigTextStyle().bigText(text)).setContentIntent(pi).setAutoCancel(true).setPriority(NotificationCompat.PRIORITY_HIGH).build()
        runCatching { NotificationManagerCompat.from(ctx).notify(id, n) }
    }

    @Suppress("unused") private val keepDay = DAY
}

/** "Tell me more later": one gentle reminder, 30 minutes after a short answer (plan: don't pester). */
object FollowUp {
    const val DELAY = 30 * 60_000L
    fun pending(ctx: Context): List<Pair<Long, Long>> = ctx.medlog.settings.getString("followups").orEmpty().split(";").mapNotNull {
        val p = it.split(":"); if (p.size == 2) (p[0].toLongOrNull() ?: return@mapNotNull null) to (p[1].toLongOrNull() ?: return@mapNotNull null) else null
    }
    fun schedule(ctx: Context, noteId: Long) {
        val list = pending(ctx).filter { it.first != noteId } + (noteId to System.currentTimeMillis() + DELAY)
        ctx.medlog.settings.putString("followups", list.joinToString(";") { "${it.first}:${it.second}" })
    }
    fun remove(ctx: Context, noteId: Long) {
        ctx.medlog.settings.putString("followups", pending(ctx).filter { it.first != noteId }.joinToString(";") { "${it.first}:${it.second}" })
    }
}

object CheckIn {
    fun answered(ctx: Context) = Care.checkedIn(ctx)
}

private fun kotlinx.coroutines.CoroutineScope.launchIo(block: suspend () -> Unit) { launch { block() } }
