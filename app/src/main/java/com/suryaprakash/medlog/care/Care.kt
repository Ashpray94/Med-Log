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
        Drafts.due(ctx).forEach { (_, t) -> list += t }
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
        for ((key, t) in Drafts.due(ctx)) if (now >= t) Drafts.remind(ctx, key)
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
            app.scope.launchIo { Alerts.send(ctx, Alerts.Type.REFILL, com.suryaprakash.medlog.help.Wording.refill(app.repo.profile().name, text)) }
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

/**
 * Half-done entries, kept as you go so closing MedLog loses nothing. One gentle reminder 30 minutes after the
 * last change; finishing or discarding the entry clears it.
 */
object Drafts {
    const val DELAY = 30 * 60_000L
    private fun all(ctx: Context) = runCatching { org.json.JSONObject(ctx.medlog.settings.getString("drafts") ?: "{}") }.getOrDefault(org.json.JSONObject())
    fun get(ctx: Context, key: String): String? = all(ctx).optJSONObject(key)?.optString("data")?.ifBlank { null }
    fun save(ctx: Context, key: String, title: String, link: String, data: String) {
        val a = all(ctx)
        a.put(key, org.json.JSONObject().put("title", title).put("link", link).put("data", data).put("at", System.currentTimeMillis()))
        ctx.medlog.settings.putString("drafts", a.toString())
    }
    fun clear(ctx: Context, key: String) {
        val a = all(ctx); a.remove(key); ctx.medlog.settings.putString("drafts", a.toString())
        runCatching { NotificationManagerCompat.from(ctx).cancel(8700 + key.hashCode() % 100) }
    }
    /** (key, when to remind) for drafts not yet reminded about. */
    fun due(ctx: Context): List<Pair<String, Long>> {
        val a = all(ctx)
        return a.keys().asSequence().mapNotNull { k -> a.optJSONObject(k)?.takeIf { !it.optBoolean("reminded") }?.let { k to it.optLong("at") + DELAY } }.toList()
    }
    fun remind(ctx: Context, key: String) {
        val a = all(ctx); val d = a.optJSONObject(key) ?: return
        d.put("reminded", true); ctx.medlog.settings.putString("drafts", a.toString())
        Care.notify(ctx, 8700 + key.hashCode() % 100, "Finish your ${d.optString("title")}?", "It's saved where you left it. Tap to finish.", d.optString("link"))
    }
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


/**
 * A helper's phone reminds the helper too, from its copy of each person's medicines and feeds: a quiet
 * notification when something's due, and, for a medicine still not marked taken 15 minutes later, a loud one that
 * keeps ringing until answered or swiped (a swipe silences it). Feeds only ever get quiet reminders.
 * Each reminder answers itself: Given, Snooze 5 min, or More (ask another helper, give it in a few minutes, not given).
 */
object HelperCare {
    private const val LATE = 15 * 60_000L
    const val SNOOZE_MIN = 5

    private suspend fun due(ctx: Context, now: Long): List<Triple<com.suryaprakash.medlog.data.CaredFor, com.suryaprakash.medlog.data.Dose, com.suryaprakash.medlog.data.Medicine>> {
        val out = ArrayList<Triple<com.suryaprakash.medlog.data.CaredFor, com.suryaprakash.medlog.data.Dose, com.suryaprakash.medlog.data.Medicine>>()
        for (p in com.suryaprakash.medlog.data.People.all(ctx)) {
            val db = com.suryaprakash.medlog.data.Mirror.db(ctx, p.pairId)
            val meds = db.medicines().all().filter { it.active }.associateBy { it.id }
            db.doses().between(now - 6 * HOUR, now + 2 * DAY).filter { it.status == "DUE" || it.status == "SNOOZED" }
                .forEach { d -> meds[d.medicineId]?.let { out += Triple(p, d, it) } }
        }
        return out
    }

    /** Put off by this helper until then (Snooze, or "I'll give it in 20 minutes"); 0 when not. */
    fun snoozedUntil(ctx: Context, uid: String) = ctx.medlog.settings.getLong("hsnz_$uid")

    suspend fun nextWake(ctx: Context, now: Long): Long? {
        val st = ctx.medlog.settings
        return due(ctx, now).flatMap { (_, d, m) -> listOfNotNull(d.scheduledAt, (d.scheduledAt + LATE).takeIf { m.form != "feed" }, snoozedUntil(ctx, d.uid).takeIf { it > 0 }) }
            .filter { it > now + 1000 && st.getLong("hfired_$it") == 0L }.minOrNull()
    }

    fun notificationId(d: com.suryaprakash.medlog.data.Dose) = 8800 + (d.id % 100).toInt()

    suspend fun tick(ctx: Context, now: Long) {
        val st = ctx.medlog.settings
        for ((p, d, m) in due(ctx, now)) {
            val key = "hfired_${d.uid}"
            val until = snoozedUntil(ctx, d.uid)
            if (until > now) continue
            if (until > 0) {
                // the snooze is over: remind again, loud for a medicine, quiet for a feed
                st.putLong("hsnz_${d.uid}", 0)
                show(ctx, p, d, m, loud = m.form != "feed", again = true)
                st.putString(key, "loud")
                continue
            }
            if (now >= d.scheduledAt && now - d.scheduledAt < 2 * HOUR && st.getString(key) == null) {
                st.putString(key, "quiet")
                show(ctx, p, d, m, loud = false)
            }
            if (m.form != "feed" && now >= d.scheduledAt + LATE && now - d.scheduledAt < 3 * HOUR && st.getString(key) != "loud") {
                st.putString(key, "loud")
                show(ctx, p, d, m, loud = true)
            }
        }
    }

    private fun show(ctx: Context, p: com.suryaprakash.medlog.data.CaredFor, d: com.suryaprakash.medlog.data.Dose, m: com.suryaprakash.medlog.data.Medicine, loud: Boolean, again: Boolean = false) {
        val who = p.name.ifBlank { "Your person" }
        val feed = m.form == "feed"
        val time = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault()).format(java.util.Date(d.scheduledAt))
        val id = notificationId(d)
        val title = when {
            loud && !again -> "$who hasn't taken ${m.name}"
            feed -> "$who: time for the feed"
            else -> "$who: time for ${m.name}"
        }
        val text = if (loud && !again) "Due at $time. Please check on them." else "$time · ${if (feed) m.name + ", " else ""}${m.amount}"
        val more = PendingIntent.getActivity(ctx, id, Intent(ctx, MainActivity::class.java)
            .setData(android.net.Uri.parse("medlog://dose?pair=${android.net.Uri.encode(p.pairId)}&uid=${android.net.Uri.encode(d.uid)}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        fun action(kind: String) = PendingIntent.getBroadcast(ctx, id * 10 + kind.length, Intent(ctx, HelperDoseReceiver::class.java)
            .putExtra("kind", kind).putExtra("pair", p.pairId).putExtra("uid", d.uid).putExtra("nid", id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val b = NotificationCompat.Builder(ctx, if (loud) MedLogApp.CH_ALERT else MedLogApp.CH_CARE).setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(com.suryaprakash.medlog.ui.tr(title)).setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(if (loud) NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_HIGH)
            .setCategory(if (loud) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(more).setAutoCancel(true)
        // the same three answers as every helper alert; the notification itself opens the dose page (Given, Given earlier, reasons)
        val replies = com.suryaprakash.medlog.help.AlertReplies.forType(com.suryaprakash.medlog.help.Alerts.Type.MISSED_DOSE)
        listOf("willgive", "skip", "ask").forEachIndexed { i, kind -> b.addAction(0, com.suryaprakash.medlog.ui.tr(replies[i].words), action(kind)) }
        if (loud) b.setDeleteIntent(com.suryaprakash.medlog.help.SilenceReceiver.intent(ctx, id, title, "Due at $time. Tap to open.", more, keep = false))
        runCatching { NotificationManagerCompat.from(ctx).notify(id, b.build()) }
        if (loud) com.suryaprakash.medlog.help.AlertSound.start(ctx, urgent = false)
    }
}

/** What a helper can do about one of the person's doses, from a reminder or from the app: the same rules everywhere. */
object HelperDose {
    suspend fun find(ctx: Context, pairId: String, uid: String): Pair<com.suryaprakash.medlog.data.Dose, com.suryaprakash.medlog.data.Medicine>? {
        val db = com.suryaprakash.medlog.data.Mirror.db(ctx, pairId)
        val d = db.doses().byUid(uid) ?: return null
        val m = db.medicines().get(d.medicineId) ?: return null
        return d to m
    }

    private suspend fun done(ctx: Context, d: com.suryaprakash.medlog.data.Dose) {
        ctx.medlog.settings.putLong("hsnz_${d.uid}", 0)
        NotificationManagerCompat.from(ctx).cancel(HelperCare.notificationId(d))
        com.suryaprakash.medlog.help.AlertSound.stop()
        com.suryaprakash.medlog.meds.Scheduler.reschedule(ctx)
    }

    /** Given, now or at [at]. The change goes back to the person's phone. */
    suspend fun give(ctx: Context, pairId: String, uid: String, at: Long? = null) {
        val db = com.suryaprakash.medlog.data.Mirror.db(ctx, pairId)
        val d = db.doses().byUid(uid) ?: return
        db.doses().update(d.copy(status = com.suryaprakash.medlog.data.DoseStatus.TAKEN, actedAt = at ?: System.currentTimeMillis(), snoozeUntil = null))
        done(ctx, d)
    }

    /** Not given, and why (or food taken in place of a feed). */
    suspend fun notGiven(ctx: Context, pairId: String, uid: String, reason: String) {
        val db = com.suryaprakash.medlog.data.Mirror.db(ctx, pairId)
        val d = db.doses().byUid(uid) ?: return
        db.doses().update(d.copy(status = com.suryaprakash.medlog.data.DoseStatus.SKIPPED, actedAt = System.currentTimeMillis(), reason = reason, snoozeUntil = null))
        done(ctx, d)
    }

    /** "I'll give it": the alarm stops and the other helpers hear it; the dose stays open until it is marked given. */
    suspend fun willGive(ctx: Context, pairId: String, uid: String) {
        val (d, m) = find(ctx, pairId, uid) ?: return
        NotificationManagerCompat.from(ctx).cancel(HelperCare.notificationId(d))
        com.suryaprakash.medlog.help.AlertSound.stop()
        com.suryaprakash.medlog.data.People.byPairId(ctx, pairId)?.let { p ->
            com.suryaprakash.medlog.help.FamilyChat.send(ctx, "I'll give ${p.name.ifBlank { "them" }} ${if (m.form == "feed") "the feed" else m.name}", p)
        }
    }

    /** "Skip this dose" on a helper's alert or reminder: the same as the helper's own "not given" ([notGiven]). */
    suspend fun skip(ctx: Context, pairId: String, uid: String) = notGiven(ctx, pairId, uid, "Not given")

    /**
     * "Skip this dose" on an alert from the person's phone, which names the medicine and time but not the dose:
     * finds that one dose in this phone's copy and skips it. Nothing is changed when it can't be told for sure.
     */
    suspend fun skipFromAlert(ctx: Context, pairId: String, text: String) {
        val db = com.suryaprakash.medlog.data.Mirror.db(ctx, pairId)
        val now = System.currentTimeMillis()
        val meds = db.medicines().all().filter { it.name.isNotBlank() && text.contains("(${it.name})") }.associateBy { it.id }
        val open = db.doses().between(now - 12 * HOUR, now + HOUR).filter { it.medicineId in meds && it.status in setOf("DUE", "SNOOZED", "MISSED") }
        val time = { d: com.suryaprakash.medlog.data.Dose -> java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault()).format(java.util.Date(d.scheduledAt)) }
        val pick = open.filter { text.contains(time(it)) }.ifEmpty { open }.singleOrNull() ?: return
        notGiven(ctx, pairId, pick.uid, "Not given")
    }

    /** Remind this phone again in [minutes]; with [tell], the other helpers hear who is giving it and when. */
    suspend fun later(ctx: Context, pairId: String, uid: String, minutes: Int, tell: Boolean = false) {
        val (d, m) = find(ctx, pairId, uid) ?: return
        ctx.medlog.settings.putLong("hsnz_$uid", System.currentTimeMillis() + minutes * 60_000L)
        NotificationManagerCompat.from(ctx).cancel(HelperCare.notificationId(d))
        com.suryaprakash.medlog.help.AlertSound.stop()
        if (tell) com.suryaprakash.medlog.data.People.byPairId(ctx, pairId)?.let { p ->
            com.suryaprakash.medlog.help.FamilyChat.send(ctx, "I'll give ${p.name.ifBlank { "them" }} ${if (m.form == "feed") "the feed" else m.name} in $minutes min", p)
        }
        com.suryaprakash.medlog.meds.Scheduler.reschedule(ctx)
    }

    /** Ask another helper to give it: their phone rings, through the person's phone. */
    suspend fun askOther(ctx: Context, pairId: String, uid: String, toPairId: String) {
        val (d, m) = find(ctx, pairId, uid) ?: return
        val who = com.suryaprakash.medlog.data.People.byPairId(ctx, pairId)?.name?.ifBlank { null } ?: "them"
        val time = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault()).format(java.util.Date(d.scheduledAt))
        com.suryaprakash.medlog.help.Nearby.askOther(ctx, pairId, toPairId, "Please give $who ${if (m.form == "feed") "the $time feed" else "the $time ${m.name}"}")
        NotificationManagerCompat.from(ctx).cancel(HelperCare.notificationId(d))
        com.suryaprakash.medlog.help.AlertSound.stop()
    }
}

/** Given and Snooze, straight from a helper's reminder. */
class HelperDoseReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val pair = intent.getStringExtra("pair") ?: return
        val uid = intent.getStringExtra("uid") ?: return
        val pending = goAsync()
        ctx.medlog.scope.launch {
            try {
                when (intent.getStringExtra("kind")) {
                    "give" -> HelperDose.give(ctx, pair, uid)
                    "snooze" -> HelperDose.later(ctx, pair, uid, HelperCare.SNOOZE_MIN)
                    "willgive" -> HelperDose.willGive(ctx, pair, uid)
                    "skip" -> HelperDose.skip(ctx, pair, uid)
                    "ask" -> {
                        val other = com.suryaprakash.medlog.ui.screens.otherHelpers(ctx, pair).firstOrNull()
                        if (other != null) HelperDose.askOther(ctx, pair, uid, other.second)
                        else android.os.Handler(android.os.Looper.getMainLooper()).post {
                            android.widget.Toast.makeText(ctx, com.suryaprakash.medlog.ui.tr("No other helper is paired"), android.widget.Toast.LENGTH_LONG).show()
                        }
                    }
                }
                NotificationManagerCompat.from(ctx).cancel(intent.getIntExtra("nid", 0))
            } finally { pending.finish() }
        }
    }
}
