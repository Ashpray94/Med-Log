package com.suryaprakash.medlog.widget

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.suryaprakash.medlog.MainActivity
import com.suryaprakash.medlog.MedLogApp
import com.suryaprakash.medlog.R
import com.suryaprakash.medlog.data.Repo
import com.suryaprakash.medlog.medlog
import com.suryaprakash.medlog.meds.DoseActivity
import com.suryaprakash.medlog.meds.Scheduler
import com.suryaprakash.medlog.pictogram.Pictogram
import kotlinx.coroutines.launch

/**
 * The one home-screen widget. Its job: note how you feel and reach family, without opening MedLog.
 *
 * - A status line on top: what just happened ("✓ Headache noted, 9:41", "Sent · Ravi: I'm coming").
 * - Symptom tiles: the person's own (or, on day one, the ones that fit them). One tap saves it with the time.
 * - The person's messages: one tap sends to family straight away; the status line shows who got it and answers.
 * - SOS, always. On a tall widget, the next medicine with "I took it".
 * Light and plain: white, grey tiles, dark words. Red only for SOS.
 */
class MedLogWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    data class Tile(val id: String, val label: String, val picture: android.graphics.Bitmap, val emergency: Boolean)

    /** Everything the widget shows, read fresh each time it's asked to refresh. */
    private data class Face(val self: Boolean, val status: Pair<String, Boolean>?, val tiles: List<Tile>, val msgs: List<String>, val nextText: String?,
                            val dueDose: Long?, val ask: String?, val noted: String?)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val first = load(context)
        provideContent {
            // a running widget session keeps its composition; re-read on every refresh, or taps like "Yes, add it" look stuck
            val tick by MedLogWidget.tick.collectAsState()
            var f by remember { mutableStateOf(first) }
            LaunchedEffect(tick) { if (tick > 0) f = load(context) }
            Content(context, f.self, f.status, f.tiles, f.msgs, f.nextText, f.dueDose, f.ask, f.noted)
        }
    }

    private suspend fun load(context: Context): Face {
        val app = context.medlog
        val s = app.settings.value
        val self = s.onboarded && s.role == "self"
        val profile = runCatching { app.repo.profile() }.getOrNull()
        val plan = com.suryaprakash.medlog.data.CarePlan.parse(profile?.plan)
        val ids = if (!self) emptyList() else runCatching {
            val history = app.db.notes().symptomsSince(System.currentTimeMillis() - 180 * com.suryaprakash.medlog.data.DAY)
                .mapNotNull { n -> n.problemId?.let { com.suryaprakash.medlog.clinical.Suggest.Logged(it, n.occurredAt) } }
            val r = com.suryaprakash.medlog.clinical.Suggest.rank(history, profile?.let { app.repo.ageYears(it.dob) }, profile?.conditions.orEmpty().split(",").map { it.trim() },
                plan.symptoms, java.time.LocalTime.now().hour, known = { app.catalogue.problem(it) != null }, limit = 4)
            (r.yours + r.suggested).distinct().take(4)
        }.getOrDefault(emptyList())
        val tiles = ids.mapNotNull { pid ->
            val p = app.catalogue.problem(pid) ?: return@mapNotNull null
            Tile(p.id, p.label, com.suryaprakash.medlog.pictogram.Sprites.bitmap(context, p.id, 200) ?: return@mapNotNull null, pid in plan.emergencies)
        }
        val msgs = if (self) s.messages.take(3).map { it.substringAfter('|') } else emptyList()
        val next = runCatching { Scheduler.nextDose(context) }.getOrNull()
        val nextText = next?.let { (d, m) -> "${DoseActivity.time(d.scheduledAt)} · ${m.name}" }
        val nextDue = next?.let { it.first.scheduledAt <= System.currentTimeMillis() + 15 * 60_000 } == true
        val st = app.settings
        val ask = st.getString("widget_ask")?.takeIf { System.currentTimeMillis() - st.getLong("widget_ask_at") < 60_000L }
        val noted = st.getString("widget_done")?.takeIf { System.currentTimeMillis() - st.getLong("widget_done_at") < 10 * 60_000L }
        return Face(self, status(context), tiles, msgs, nextText, if (nextDue) next?.first?.id else null, ask, noted)
    }

    /** The latest thing done from the widget, and what came of it: shown for 10 seconds, then the widget is itself again. */
    private fun status(ctx: Context): Pair<String, Boolean>? {
        val now = System.currentTimeMillis()
        val st = com.suryaprakash.medlog.ui.screens.HelpMessages.status.value
        val ackNow = com.suryaprakash.medlog.help.Nearby.acks.value.lastOrNull()?.takeIf { now - it.at < SHOW_MS }
        if (st != null && (now - st.at < SHOW_MS || st.stage == "sending" && now - st.at < 60_000L || ackNow != null)) {
            val ack = ackNow
            return when {
                ack != null -> "✓ ${ack.name}: ${com.suryaprakash.medlog.help.Nearby.replyWords(ack.reply)}" to true
                st.stage == "sending" -> "Sending: ${st.text}…" to false
                st.stage == "failed" -> "Couldn't send. Please call." to false
                st.stage == "noanswer" -> "No answer yet. Try calling." to false
                else -> "✓ Sent: ${st.text}" to true
            }
        }
        val s = ctx.medlog.settings
        val at = s.getLong("widget_noted_at")
        if (now - at < SHOW_MS) return (s.getString("widget_noted") ?: return null) to true
        return null
    }

    @Composable
    private fun Content(ctx: Context, self: Boolean, status: Pair<String, Boolean>?, tiles: List<Tile>, msgs: List<String>, nextText: String?, dueDose: Long?,
                        ask: String? = null, noted: String? = null) {
        val size = LocalSize.current
        val ink = ColorProvider(Color(0xFF18181B))
        val soft = ColorProvider(Color(0xFF52525B))
        val accent = ColorProvider(if (ctx.medlog.settings.value.role == "helper") com.suryaprakash.medlog.ui.HELPER_BRAND else com.suryaprakash.medlog.ui.MY_BRAND)
        val white = ColorProvider(Color.White)
        if (ask != null || noted != null) { Pending(ctx, ask, noted); return }
        val w = size.width.value - 16f
        var h = size.height.value - 16f
        // top to bottom: what just happened; their usual problems (the last place is "More"); food, water, medicine;
        // their messages; and, always at the bottom where the thumb rests, Speak and SOS
        val bottomH = 56f; h -= bottomH
        val showStatus = status != null && h >= 30f; if (showStatus) h -= 32f
        val cols = if (w >= 240f) 3 else 2
        val kindsH = 52f
        val showKinds = self && h >= kindsH + 8f; if (showKinds) h -= kindsH + 8f
        val side = minOf((w - 8f * (cols - 1)) / cols, 116f, h - 8f)
        val showTiles = self && side >= 72f; if (showTiles) h -= side + 8f
        val rowH = 46f
        val nMsgs = if (!self) 0 else minOf(msgs.size, ((h - 8f + 6f) / (rowH + 6f)).toInt().coerceAtLeast(0))
        Column(GlanceModifier.fillMaxSize().background(ImageProvider(R.drawable.widget_bg)).padding(8.dp)) {
            if (!self) {
                Box(GlanceModifier.fillMaxWidth().defaultWeight().clickable(actionStartActivity(link(ctx, ""))), contentAlignment = Alignment.Center) {
                    Text(com.suryaprakash.medlog.ui.tr("Open the app to finish setting up"), style = TextStyle(color = ink, fontSize = 15.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center))
                }
            }
            if (showStatus && status != null) {
                Box(GlanceModifier.fillMaxWidth().height(28.dp).background(ImageProvider(if (status.second) R.drawable.widget_done else R.drawable.widget_tile)).padding(horizontal = 10.dp)
                    .clickable(actionStartActivity(link(ctx, "help"))), contentAlignment = Alignment.CenterStart) {
                    Text(com.suryaprakash.medlog.ui.tr(status.first), style = TextStyle(color = ink, fontSize = 13.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                }
                Spacer(GlanceModifier.height(4.dp))
            }
            // their usual problems, and "More" in the last place for the full list
            if (showTiles) {
                val cells: List<Tile?> = tiles.take(cols - 1) + listOf(null)
                Row(GlanceModifier.fillMaxWidth()) {
                    cells.forEachIndexed { i, t ->
                        if (i > 0) Spacer(GlanceModifier.width(8.dp))
                        val act = when {
                            t == null -> actionStartActivity(link(ctx, "tell"))
                            t.emergency -> actionStartActivity(link(ctx, "tell?problem=${t.id}"))
                            else -> actionRunCallback<AskFirst>(actionParametersOf(ASK to "problem|${t.id}|${t.label}"))
                        }
                        Column(GlanceModifier.defaultWeight().height(side.dp).background(ImageProvider(if (t == null) R.drawable.widget_soft else R.drawable.widget_tile)).padding(4.dp).clickable(act),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically) {
                            if (t != null) Image(ImageProvider(t.picture), t.label, GlanceModifier.size((side * 0.45f).coerceIn(28f, 56f).dp))
                            else Image(ImageProvider(R.drawable.ic_w_more), null, GlanceModifier.size((side * 0.3f).coerceIn(24f, 40f).dp))
                            Text(com.suryaprakash.medlog.ui.tr(t?.label ?: "More"), style = TextStyle(color = ink, fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center), maxLines = 2)
                        }
                    }
                    repeat(cols - cells.size) { Spacer(GlanceModifier.width(8.dp)); Spacer(GlanceModifier.defaultWeight()) }
                }
                Spacer(GlanceModifier.height(8.dp))
            }
            // food, water and medicine: food and medicine open the app (medicine shows which one is being noted),
            // water asks here first
            if (showKinds) {
                Row(GlanceModifier.fillMaxWidth().height(kindsH.dp)) {
                    KindButton(ctx, R.drawable.ic_w_food, "Food", actionStartActivity(link(ctx, "open?name=foodadd")))
                    Spacer(GlanceModifier.width(8.dp))
                    KindButton(ctx, R.drawable.ic_w_water, "Water", actionRunCallback<AskFirst>(actionParametersOf(ASK to "water|1|a glass of water")))
                    Spacer(GlanceModifier.width(8.dp))
                    KindButton(ctx, R.drawable.ic_w_pill, "Medicine", actionStartActivity(link(ctx, "open?name=took").also { dueDose }))
                }
                Spacer(GlanceModifier.height(8.dp))
            }
            msgs.take(nMsgs).forEach { m ->
                Row(GlanceModifier.fillMaxWidth().height(rowH.dp).background(ImageProvider(R.drawable.widget_soft)).padding(horizontal = 12.dp)
                    .clickable(actionRunCallback<AskFirst>(actionParametersOf(ASK to "message|$m|$m"))), verticalAlignment = Alignment.CenterVertically) {
                    Text(com.suryaprakash.medlog.ui.tr(m), GlanceModifier.defaultWeight(), style = TextStyle(color = ink, fontSize = 15.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                    Text(com.suryaprakash.medlog.ui.tr("Send"), style = TextStyle(color = accent, fontSize = 14.sp, fontWeight = FontWeight.Bold))
                }
                Spacer(GlanceModifier.height(6.dp))
            }
            Spacer(GlanceModifier.defaultWeight())
            // Speak (everything at once) and SOS, always at the bottom
            Row(GlanceModifier.fillMaxWidth().height(bottomH.dp)) {
                if (self) {
                    // messages to family: a square button, left of Speak
                    Box(GlanceModifier.width(bottomH.dp).fillMaxHeight().background(ImageProvider(R.drawable.widget_outline)).clickable(actionStartActivity(link(ctx, "help"))),
                        contentAlignment = Alignment.Center) {
                        Image(ImageProvider(R.drawable.ic_w_chat), com.suryaprakash.medlog.ui.tr("Messages"), GlanceModifier.size(26.dp))
                    }
                    Spacer(GlanceModifier.width(8.dp))
                    Row(GlanceModifier.defaultWeight().fillMaxHeight().background(ImageProvider(R.drawable.widget_brand)).clickable(actionStartActivity(link(ctx, "speak"))),
                        verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.CenterHorizontally) {
                        Image(ImageProvider(R.drawable.ic_w_mic), null, GlanceModifier.size(24.dp))
                        Spacer(GlanceModifier.width(8.dp))
                        Text(com.suryaprakash.medlog.ui.tr("Speak"), style = TextStyle(color = white, fontSize = 17.sp, fontWeight = FontWeight.Bold))
                    }
                    Spacer(GlanceModifier.width(8.dp))
                }
                Box(GlanceModifier.width(if (self) (if (w >= 240f) 110.dp else 84.dp) else w.dp).fillMaxHeight().background(ImageProvider(R.drawable.widget_red))
                    .clickable(actionStartActivity(link(ctx, "emergency"))), contentAlignment = Alignment.Center) {
                    Text(com.suryaprakash.medlog.ui.tr("SOS"), style = TextStyle(color = white, fontSize = 18.sp, fontWeight = FontWeight.Bold))
                }
            }
            @Suppress("UNUSED_VARIABLE") val unused = soft to nextText
        }
    }

    /** One kind of log on the widget: its icon and one word. */
    @Composable
    private fun androidx.glance.layout.RowScope.KindButton(ctx: Context, icon: Int, label: String, action: androidx.glance.action.Action) {
        Row(GlanceModifier.defaultWeight().fillMaxHeight().background(ImageProvider(R.drawable.widget_tile)).padding(horizontal = 6.dp).clickable(action),
            verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.CenterHorizontally) {
            Image(ImageProvider(icon), null, GlanceModifier.size(22.dp))
            Spacer(GlanceModifier.width(6.dp))
            Text(com.suryaprakash.medlog.ui.tr(label), style = TextStyle(color = ColorProvider(Color(0xFF18181B)), fontSize = 14.sp, fontWeight = FontWeight.Bold), maxLines = 1)
        }
        @Suppress("UNUSED_VARIABLE") val c = ctx
    }

    @Composable
    private fun Pending(ctx: Context, ask: String?, noted: String?) = WidgetQuestion(ctx, MAIN, ask, noted)

    companion object {
        val tick = kotlinx.coroutines.flow.MutableStateFlow(0L)
        /** How long a status line stays on the widget. */
        const val SHOW_MS = 10_000L
        /** Redraw now, and again once the status line has had its 10 seconds, so it goes away by itself. */
        fun showStatus(ctx: Context) {
            val app = ctx.medlog
            app.scope.launch { refresh(ctx); kotlinx.coroutines.delay(SHOW_MS + 500); refresh(ctx) }
        }
        /** Redraw the widget with fresh data. */
        suspend fun refresh(ctx: Context) {
            tick.value = tick.value + 1
            MedLogWidget().updateAll(ctx); FeelWidget().updateAll(ctx); OutWidget().updateAll(ctx)
        }
        /** Which widget a question belongs to, so a question on one never shows on another. */
        val SRC = ActionParameters.Key<String>("src")
        const val MAIN = "widget"
        val ASK = ActionParameters.Key<String>("ask")
        val YES = ActionParameters.Key<Boolean>("yes")
        val DOSE = ActionParameters.Key<Long>("dose")
        val PROBLEM = ActionParameters.Key<String>("problem")
        val TEXT = ActionParameters.Key<String>("text")
        fun link(ctx: Context, path: String) = Intent(ctx, MainActivity::class.java).setAction(Intent.ACTION_VIEW).setData(Uri.parse("medlog://$path")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP).putExtra(com.suryaprakash.medlog.integration.TrustedLinks.EXTRA, com.suryaprakash.medlog.integration.TrustedLinks.secret(ctx))
        fun ordinal(n: Int) = "$n" + when { n % 100 in 11..13 -> "th"; n % 10 == 1 -> "st"; n % 10 == 2 -> "nd"; n % 10 == 3 -> "rd"; else -> "th" }
    }
}

/** Any widget tap first becomes a question on the widget; nothing is saved or sent yet. */
class AskFirst : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val s = context.medlog.settings
        val src = parameters[MedLogWidget.SRC] ?: MedLogWidget.MAIN
        var ask = parameters[MedLogWidget.ASK] ?: return
        // the same problem noted in the last 10 minutes: ask whether it happened again, or it's that one
        if (ask.startsWith("problem|")) {
            val pid = ask.split("|")[1]
            context.medlog.repo.db.notes().symptomsSince(System.currentTimeMillis() - 10 * 60_000L)
                .firstOrNull { it.problemId == pid && com.suryaprakash.medlog.data.Occurrences.isOccurrence(it) }?.let { n ->
                    val time = java.text.SimpleDateFormat("h:mm a", com.suryaprakash.medlog.speech.I18n.locale).format(java.util.Date(n.occurredAt))
                    ask = "again|$pid|${ask.split("|", limit = 3).getOrElse(2) { "" }}|${n.id}|$time"
                }
        }
        s.putString("${src}_ask", ask)
        s.putLong("${src}_ask_at", System.currentTimeMillis())
        MedLogWidget.refresh(context)
    }
}

private val askLock = Any()

/** Yes: do what was asked. Cancel: forget it. */
class AnswerAsk : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val s = context.medlog.settings
        val src = parameters[MedLogWidget.SRC] ?: MedLogWidget.MAIN
        // taken once: a second quick tap finds nothing left to do, so nothing is saved twice
        val ask = synchronized(askLock) { s.getString("${src}_ask").also { s.putString("${src}_ask", null) } }
        if (parameters[MedLogWidget.YES] == true && ask != null) {
            val (kind, value) = ask.split("|", limit = 3).let { it[0] to it.getOrElse(1) { "" } }
            when (kind) {
                "problem", "again" -> NoteProblem.note(context, value, src, followUp = true)
                "message" -> SendMessage.send(context, value)
                "dose" -> value.toLongOrNull()?.let { Scheduler.take(context, it) }
                "water" -> {
                    context.medlog.repo.addWater(1); com.suryaprakash.medlog.ui.savedFeedback(context)
                    context.medlog.settings.putString("widget_noted", "✓ ${com.suryaprakash.medlog.ui.tr("A glass of water")} " + java.text.SimpleDateFormat("h:mm a", com.suryaprakash.medlog.speech.I18n.locale).format(java.util.Date()))
                    context.medlog.settings.putLong("widget_noted_at", System.currentTimeMillis())
                    MedLogWidget.showStatus(context)
                }
            }
        }
        MedLogWidget.refresh(context)
    }
}

/** Done for now: a reminder in 30 minutes to add the details. */
class LaterDetails : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val s = context.medlog.settings
        val src = parameters[MedLogWidget.SRC] ?: MedLogWidget.MAIN
        s.getString("${src}_done")?.substringBefore("|")?.toLongOrNull()?.let { com.suryaprakash.medlog.care.FollowUp.schedule(context, it) }
        s.putString("${src}_done", null)
        MedLogWidget.refresh(context)
    }
}

/** A symptom tile: saved at once, with the time. Details can be added in MedLog later. */
class NoteProblem : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        note(context, parameters[MedLogWidget.PROBLEM] ?: return)
    }
    companion object { suspend fun note(context: Context, pid: String, src: String = MedLogWidget.MAIN, followUp: Boolean = false) {
        val app = context.medlog
        val label = app.catalogue.problem(pid)?.label ?: return
        val now = System.currentTimeMillis()
        val ids = app.repo.saveTold(listOf(com.suryaprakash.medlog.nlu.Mention(pid)), null, now, com.suryaprakash.medlog.clinical.Triage.OK, emptyList(), emptyList(), null)
        val today = app.repo.recentProblems(12).firstOrNull { it.problemId == pid }?.todayCount ?: 1
        val time = java.text.SimpleDateFormat("h:mm a", com.suryaprakash.medlog.speech.I18n.locale).format(java.util.Date(now))
        app.settings.putString("widget_noted", "✓ ${com.suryaprakash.medlog.ui.tr(label)} " + (if (today > 1) "(${MedLogWidget.ordinal(today)} today) " else "") + time)
        app.settings.putLong("widget_noted_at", now)
        ids.firstOrNull()?.let { id ->
            if (followUp) { com.suryaprakash.medlog.care.FollowUp.schedule(context, id); return@let }
            app.settings.putString("${src}_done", "$id|${com.suryaprakash.medlog.ui.tr(label)}|$time|$pid")
            app.settings.putLong("${src}_done_at", now)
        }
        com.suryaprakash.medlog.ui.savedFeedback(context)
        MedLogWidget.showStatus(context)
    } }
}

/** A message tile: sent to family at once. The widget follows the answer for a few minutes. */
class SendMessage : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) { send(context, parameters[MedLogWidget.TEXT] ?: return) }
    companion object { suspend fun send(context: Context, text: String) {
        val app = context.medlog
        com.suryaprakash.medlog.ui.screens.HelpMessages.send(context, text)
        com.suryaprakash.medlog.ui.savedFeedback(context)
        MedLogWidget.refresh(context)
        app.scope.launch {
            var last: Any? = null
            val end = System.currentTimeMillis() + 4 * 60_000L
            while (System.currentTimeMillis() < end) {
                val now = com.suryaprakash.medlog.ui.screens.HelpMessages.status.value to com.suryaprakash.medlog.help.Nearby.acks.value.size
                if (now != last) { last = now; MedLogWidget.refresh(context) }
                if (now.second > 0) break
                kotlinx.coroutines.delay(1500)
            }
            // the answer (or the last word on sending) has its 10 seconds, then goes
            kotlinx.coroutines.delay(MedLogWidget.SHOW_MS + 500); MedLogWidget.refresh(context)
        }
    } }
}

class TakeDose : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        parameters[MedLogWidget.DOSE]?.let { Scheduler.take(context, it) }
        MedLogWidget.refresh(context)
    }
}

class MedLogWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MedLogWidget()
}

/** Quick Settings tile: "Tell MedLog" from the pull-down shade (Android 7+). */
@RequiresApi(24)
class TellTileService : TileService() {
    @SuppressLint("StartActivityAndCollapseDeprecated")
    override fun onClick() {
        val i = MedLogWidget.link(this, "tell")
        if (Build.VERSION.SDK_INT >= 34) startActivityAndCollapse(PendingIntent.getActivity(this, 90, i, PendingIntent.FLAG_IMMUTABLE))
        else @Suppress("DEPRECATION") startActivityAndCollapse(i)
    }
}

/** Optional always-there notification with Tell and Help buttons (reachable from the lock screen). */
object QuickNotification {
    private const val ID = 9000
    fun sync(ctx: Context) {
        val s = ctx.medlog.settings.value
        if (!s.persistentNotification || s.role != "self") { NotificationManagerCompat.from(ctx).cancel(ID); return }
        fun pi(path: String, code: Int) = PendingIntent.getActivity(ctx, code, MedLogWidget.link(ctx, path), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        // "Speak" opens over the lock screen, without unlocking: logging only, nothing already recorded is shown
        val speak = PendingIntent.getActivity(ctx, 95, Intent(ctx, com.suryaprakash.medlog.ui.LockLogActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(ctx, MedLogApp.CH_QUICK).setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(com.suryaprakash.medlog.ui.tr("MedLog")).setContentText(com.suryaprakash.medlog.ui.tr("Tap Speak to tell how you are"))
            .setContentIntent(speak).setOngoing(true).setShowWhen(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC).setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, "Speak", speak).addAction(0, "Medicines", pi("meds", 93)).addAction(0, "SOS", pi("emergency", 94))
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(ID, n) }
    }
}

@Suppress("unused") private val keep = Repo.DEFAULT_PROBLEMS
