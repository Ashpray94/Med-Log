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

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.medlog
        val s = app.settings.value
        val self = s.onboarded && s.role == "self"
        val profile = runCatching { app.ownRepo.profile() }.getOrNull()
        val plan = com.suryaprakash.medlog.data.CarePlan.parse(profile?.plan)
        val ids = if (!self) emptyList() else runCatching {
            val history = app.ownDb.notes().symptomsSince(System.currentTimeMillis() - 180 * com.suryaprakash.medlog.data.DAY)
                .mapNotNull { n -> n.problemId?.let { com.suryaprakash.medlog.clinical.Suggest.Logged(it, n.occurredAt) } }
            val r = com.suryaprakash.medlog.clinical.Suggest.rank(history, profile?.let { app.ownRepo.ageYears(it.dob) }, profile?.conditions.orEmpty().split(",").map { it.trim() },
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
        provideContent { Content(context, self, status(context), tiles, msgs, nextText, if (nextDue) next?.first?.id else null) }
    }

    /** The latest thing done from the widget, and what came of it. Fresh for 30 minutes. */
    private fun status(ctx: Context): Pair<String, Boolean>? {
        val st = com.suryaprakash.medlog.ui.screens.HelpMessages.status.value
        if (st != null && System.currentTimeMillis() - st.at < 30 * 60_000L) {
            val ack = com.suryaprakash.medlog.help.Nearby.acks.value.lastOrNull()
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
        if (System.currentTimeMillis() - at < 30 * 60_000L) return (s.getString("widget_noted") ?: return null) to true
        return null
    }

    @Composable
    private fun Content(ctx: Context, self: Boolean, status: Pair<String, Boolean>?, tiles: List<Tile>, msgs: List<String>, nextText: String?, dueDose: Long?) {
        val size = LocalSize.current
        val ink = ColorProvider(Color(0xFF18181B))
        val soft = ColorProvider(Color(0xFF52525B))
        val accent = ColorProvider(Color(0xFF0B6E66))
        val white = ColorProvider(Color.White)
        val w = size.width.value - 16f
        var h = size.height.value - 16f
        // what fits, most important first: SOS row, status, symptom row, messages, medicine
        val sosH = 52f; h -= sosH
        val showStatus = status != null && h >= 30f; if (showStatus) h -= 32f
        val cols = if (w >= 300f) 4 else if (w >= 200f) 3 else 2
        val side = minOf((w - 8f * (cols - 1)) / cols, 110f)
        val showTiles = self && tiles.isNotEmpty() && h >= side + 8f; if (showTiles) h -= side + 8f
        val rowH = 46f
        val nMsgs = if (!self) 0 else minOf(msgs.size, ((h + 6f) / (rowH + 6f)).toInt().coerceAtLeast(0)); h -= nMsgs * (rowH + 6f)
        val showMed = nextText != null && h >= 50f
        Column(GlanceModifier.fillMaxSize().background(ImageProvider(R.drawable.widget_bg)).padding(8.dp)) {
            if (!self) {
                Box(GlanceModifier.fillMaxWidth().defaultWeight().clickable(actionStartActivity(link(ctx, ""))), contentAlignment = Alignment.Center) {
                    Text(com.suryaprakash.medlog.ui.tr("Open MedLog to finish setting up"), style = TextStyle(color = ink, fontSize = 15.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center))
                }
            }
            if (showStatus && status != null) {
                Box(GlanceModifier.fillMaxWidth().height(28.dp).background(ImageProvider(if (status.second) R.drawable.widget_done else R.drawable.widget_tile)).padding(horizontal = 10.dp)
                    .clickable(actionStartActivity(link(ctx, "help"))), contentAlignment = Alignment.CenterStart) {
                    Text(com.suryaprakash.medlog.ui.tr(status.first), style = TextStyle(color = ink, fontSize = 13.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                }
                Spacer(GlanceModifier.height(4.dp))
            }
            if (showTiles) {
                Row(GlanceModifier.fillMaxWidth()) {
                    tiles.take(cols).forEachIndexed { i, t ->
                        if (i > 0) Spacer(GlanceModifier.width(8.dp))
                        // one tap saves it; an emergency of theirs opens MedLog so helpers are called
                        val act = if (t.emergency) actionStartActivity(link(ctx, "tell?problem=${t.id}")) else actionRunCallback<NoteProblem>(actionParametersOf(PROBLEM to t.id))
                        Column(GlanceModifier.size(side.dp).background(ImageProvider(R.drawable.widget_tile)).padding(4.dp).clickable(act),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically) {
                            Image(ImageProvider(t.picture), t.label, GlanceModifier.size((side * 0.5f).coerceIn(28f, 64f).dp))
                            Text(com.suryaprakash.medlog.ui.tr(t.label), style = TextStyle(color = ink, fontSize = if (side >= 90f) 14.sp else 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center), maxLines = 1)
                        }
                    }
                }
                Spacer(GlanceModifier.height(8.dp))
            }
            msgs.take(nMsgs).forEach { m ->
                Row(GlanceModifier.fillMaxWidth().height(rowH.dp).background(ImageProvider(R.drawable.widget_soft)).padding(horizontal = 12.dp)
                    .clickable(actionRunCallback<SendMessage>(actionParametersOf(TEXT to m))), verticalAlignment = Alignment.CenterVertically) {
                    Text(com.suryaprakash.medlog.ui.tr(m), GlanceModifier.defaultWeight(), style = TextStyle(color = ink, fontSize = 15.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                    Text(com.suryaprakash.medlog.ui.tr("Send"), style = TextStyle(color = accent, fontSize = 14.sp, fontWeight = FontWeight.Bold))
                }
                Spacer(GlanceModifier.height(6.dp))
            }
            if (showMed && nextText != null) {
                Row(GlanceModifier.fillMaxWidth().height(44.dp).padding(horizontal = 6.dp).clickable(actionStartActivity(link(ctx, "meds"))), verticalAlignment = Alignment.CenterVertically) {
                    Text(com.suryaprakash.medlog.ui.tr(nextText), GlanceModifier.defaultWeight(), style = TextStyle(color = soft, fontSize = 14.sp, fontWeight = FontWeight.Medium), maxLines = 1)
                    if (dueDose != null) Box(GlanceModifier.height(36.dp).background(ImageProvider(R.drawable.widget_ok)).padding(horizontal = 12.dp)
                        .clickable(actionRunCallback<TakeDose>(actionParametersOf(DOSE to dueDose))), contentAlignment = Alignment.Center) {
                        Text(com.suryaprakash.medlog.ui.tr("I took it"), style = TextStyle(color = white, fontSize = 14.sp, fontWeight = FontWeight.Bold))
                    }
                }
            }
            Spacer(GlanceModifier.defaultWeight())
            // always: more symptoms, and SOS
            Row(GlanceModifier.fillMaxWidth().height(sosH.dp)) {
                Box(GlanceModifier.defaultWeight().fillMaxHeight().background(ImageProvider(R.drawable.widget_soft)).clickable(actionStartActivity(link(ctx, "tell"))),
                    contentAlignment = Alignment.Center) {
                    Text(com.suryaprakash.medlog.ui.tr(if (w >= 200f) "Something else…" else "More"), style = TextStyle(color = ink, fontSize = 15.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                }
                Spacer(GlanceModifier.width(6.dp))
                Box(GlanceModifier.width(if (w >= 200f) 96.dp else 64.dp).fillMaxHeight().background(ImageProvider(R.drawable.widget_red))
                    .clickable(actionStartActivity(link(ctx, "emergency"))), contentAlignment = Alignment.Center) {
                    Text(com.suryaprakash.medlog.ui.tr("SOS"), style = TextStyle(color = white, fontSize = 17.sp, fontWeight = FontWeight.Bold))
                }
            }
        }
    }

    companion object {
        val DOSE = ActionParameters.Key<Long>("dose")
        val PROBLEM = ActionParameters.Key<String>("problem")
        val TEXT = ActionParameters.Key<String>("text")
        fun link(ctx: Context, path: String) = Intent(ctx, MainActivity::class.java).setAction(Intent.ACTION_VIEW).setData(Uri.parse("medlog://$path")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP).putExtra(com.suryaprakash.medlog.integration.TrustedLinks.EXTRA, com.suryaprakash.medlog.integration.TrustedLinks.secret(ctx))
        fun ordinal(n: Int) = "$n" + when { n % 100 in 11..13 -> "th"; n % 10 == 1 -> "st"; n % 10 == 2 -> "nd"; n % 10 == 3 -> "rd"; else -> "th" }
    }
}

/** A symptom tile: saved at once, with the time. Details can be added in MedLog later. */
class NoteProblem : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val pid = parameters[MedLogWidget.PROBLEM] ?: return
        val app = context.medlog
        val label = app.catalogue.problem(pid)?.label ?: return
        val now = System.currentTimeMillis()
        app.ownRepo.saveTold(listOf(com.suryaprakash.medlog.nlu.Mention(pid)), null, now, com.suryaprakash.medlog.clinical.Triage.OK, emptyList(), emptyList(), null)
        val today = app.ownRepo.recentProblems(12).firstOrNull { it.problemId == pid }?.todayCount ?: 1
        val time = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault()).format(java.util.Date(now))
        app.settings.putString("widget_noted", "✓ ${com.suryaprakash.medlog.ui.tr(label)} " + (if (today > 1) "(${MedLogWidget.ordinal(today)} today) " else "") + time)
        app.settings.putLong("widget_noted_at", now)
        com.suryaprakash.medlog.ui.savedFeedback(context)
        MedLogWidget().updateAll(context)
    }
}

/** A message tile: sent to family at once. The widget follows the answer for a few minutes. */
class SendMessage : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val text = parameters[MedLogWidget.TEXT] ?: return
        val app = context.medlog
        com.suryaprakash.medlog.ui.screens.HelpMessages.send(context, text)
        com.suryaprakash.medlog.ui.savedFeedback(context)
        MedLogWidget().updateAll(context)
        app.scope.launch {
            var last: Any? = null
            val end = System.currentTimeMillis() + 4 * 60_000L
            while (System.currentTimeMillis() < end) {
                val now = com.suryaprakash.medlog.ui.screens.HelpMessages.status.value to com.suryaprakash.medlog.help.Nearby.acks.value.size
                if (now != last) { last = now; MedLogWidget().updateAll(context) }
                if (now.second > 0) break
                kotlinx.coroutines.delay(1500)
            }
        }
    }
}

class TakeDose : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        parameters[MedLogWidget.DOSE]?.let { Scheduler.take(context, it) }
        MedLogWidget().updateAll(context)
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
        val n = NotificationCompat.Builder(ctx, MedLogApp.CH_QUICK).setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(com.suryaprakash.medlog.ui.tr("MedLog")).setContentText(com.suryaprakash.medlog.ui.tr("Tap to tell how you feel"))
            .setContentIntent(pi("tell", 91)).setOngoing(true).setShowWhen(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC).setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, "Tell how I feel", pi("tell", 92)).addAction(0, "Medicines", pi("meds", 93)).addAction(0, "Help", pi("help", 94))
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(ID, n) }
    }
}

@Suppress("unused") private val keep = Repo.DEFAULT_PROBLEMS
