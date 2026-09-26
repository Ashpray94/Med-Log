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

/**
 * Home-screen widget (plan section 6). Shows the problems the person has been logging most (with
 * "3rd time today"), a microphone, the next medicine with "I took it", and Help.
 * Tapping a problem opens MedLog straight to listening for that problem.
 */
class MedLogWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    data class Tile(val id: String, val label: String, val badge: String?, val picture: android.graphics.Bitmap)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.medlog
        val s = app.settings.value
        val recent = if (s.onboarded && s.role == "self") runCatching { app.repo.recentProblems(6) }.getOrDefault(emptyList()) else emptyList()
        val tiles = recent.mapNotNull { r ->
            val p = app.catalogue.problem(r.problemId) ?: return@mapNotNull null
            Tile(p.id, p.label, when { r.todayCount >= 2 -> ordinal(r.todayCount) + " today"; r.todayCount == 1 -> "Today"; else -> null },
                com.suryaprakash.medlog.pictogram.Sprites.bitmap(context, p.id, 240) ?: return@mapNotNull null)
        }
        val next = runCatching { Scheduler.nextDose(context) }.getOrNull()
        val nextText = next?.let { (d, m) -> "${DoseActivity.time(d.scheduledAt)} · ${m.name}" }
        val nextDue = next?.let { it.first.scheduledAt <= System.currentTimeMillis() + 15 * 60_000 } == true
        provideContent { Content(context, tiles, nextText, if (nextDue) next?.first?.id else null) }
    }

    @Composable
    private fun Content(ctx: Context, tiles: List<Tile>, nextText: String?, dueDose: Long?) {
        val size = LocalSize.current
        val ink = ColorProvider(Color(0xFF1C1C1E))
        val white = ColorProvider(Color.White)
        val wide = size.width >= MEDIUM.width
        val tall = size.height >= LARGE.height
        // Square tiles only. Work out the biggest square that fits, for the most tiles we have (up to 6),
        // after the action row (52) and, on tall widgets, the medicine row (54).
        val gap = 8f
        val availW = size.width.value - 16f
        val availH = size.height.value - 16f - 60f - (if (tall && nextText != null) 54f else 0f)
        var cols = 0; var rows = 0; var side = 0f
        if (tiles.isNotEmpty() && availH >= 64f) {
            for (r in 1..2) for (c in 2..4) {
                if (r * c > 6 || (r - 1) * c >= tiles.size) continue
                val sq = minOf((availW - gap * (c - 1)) / c, (availH - gap * (r - 1)) / r, 132f)
                if (sq < 64f) continue
                val n = minOf(tiles.size, r * c)
                val better = n > minOf(tiles.size, rows * cols) || (n == minOf(tiles.size, rows * cols) && sq > side)
                if (better) { cols = c; rows = r; side = sq }
            }
        }
        val show = tiles.take(rows * cols)
        val icon = (side * 0.56f).coerceIn(32f, 76f).dp
        Column(GlanceModifier.fillMaxSize().background(ImageProvider(R.drawable.widget_bg)).padding(8.dp)) {
            // recent problems: a grid of equal squares, centred
            show.chunked(cols.coerceAtLeast(1)).forEachIndexed { ri, row ->
                if (ri > 0) Spacer(GlanceModifier.height(gap.dp))
                Row(GlanceModifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    row.forEachIndexed { i, t ->
                        if (i > 0) Spacer(GlanceModifier.width(gap.dp))
                        Column(
                            GlanceModifier.size(side.dp).background(ImageProvider(R.drawable.widget_tile)).padding(4.dp)
                                .clickable(actionStartActivity(link(ctx, "tell?problem=${t.id}"))),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Image(ImageProvider(t.picture), t.label, GlanceModifier.size(icon))
                            Text(com.suryaprakash.medlog.ui.tr(t.label), style = TextStyle(color = ink, fontSize = if (side >= 96f) 13.sp else 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center), maxLines = 1)
                        }
                    }
                    // keep a short last row aligned to the grid, not re-centred
                    repeat(cols - row.size) { Spacer(GlanceModifier.width(gap.dp)); Spacer(GlanceModifier.size(side.dp)) }
                }
            }
            if (show.isNotEmpty()) Spacer(GlanceModifier.defaultWeight())
            // next medicine (large only)
            if (tall && nextText != null) {
                Row(GlanceModifier.fillMaxWidth().height(48.dp).background(ImageProvider(R.drawable.widget_soft)).padding(horizontal = 12.dp).clickable(actionStartActivity(link(ctx, "meds"))),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(com.suryaprakash.medlog.ui.tr(nextText), GlanceModifier.defaultWeight(), style = TextStyle(color = ink, fontSize = 15.sp, fontWeight = FontWeight.Medium), maxLines = 1)
                    if (dueDose != null) Box(GlanceModifier.height(34.dp).background(ImageProvider(R.drawable.widget_ok)).padding(horizontal = 12.dp)
                        .clickable(actionRunCallback<TakeDose>(actionParametersOf(DOSE to dueDose))), contentAlignment = Alignment.Center) {
                        Text(com.suryaprakash.medlog.ui.tr("I took it"), style = TextStyle(color = white, fontSize = 14.sp, fontWeight = FontWeight.Bold))
                    }
                }
                Spacer(GlanceModifier.height(6.dp))
            }
            // the two actions, always the same
            Row(GlanceModifier.fillMaxWidth().then(if (show.isEmpty()) GlanceModifier.defaultWeight() else GlanceModifier.height(52.dp))) {
                Row(GlanceModifier.defaultWeight().fillMaxHeight().background(ImageProvider(R.drawable.widget_brand)).padding(horizontal = 10.dp)
                    .clickable(actionStartActivity(link(ctx, "tell"))), verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(ImageProvider(R.drawable.ic_w_mic), "Tell how I feel", GlanceModifier.size(if (show.isEmpty()) 34.dp else 24.dp))
                    Spacer(GlanceModifier.width(6.dp))
                    Text(com.suryaprakash.medlog.ui.tr(if (wide) "How are you?" else "Tell"), style = TextStyle(color = white, fontSize = if (wide) 15.sp else 17.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                }
                Spacer(GlanceModifier.width(6.dp))
                // family: the Ask family page, one tap away
                Column(GlanceModifier.width(if (wide) 88.dp else 58.dp).fillMaxHeight().background(ImageProvider(R.drawable.widget_brand))
                    .clickable(actionStartActivity(link(ctx, "help"))), horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically) {
                    Image(ImageProvider(R.drawable.ic_w_family), "Ask family", GlanceModifier.size(22.dp))
                    if (wide) Text(com.suryaprakash.medlog.ui.tr("Family"), style = TextStyle(color = white, fontSize = 13.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                }
                Spacer(GlanceModifier.width(6.dp))
                Box(GlanceModifier.width(if (wide) 88.dp else 64.dp).fillMaxHeight().background(ImageProvider(R.drawable.widget_red))
                    .clickable(actionStartActivity(link(ctx, "emergency"))), contentAlignment = Alignment.Center) {
                    Text(com.suryaprakash.medlog.ui.tr("SOS"), style = TextStyle(color = white, fontSize = 17.sp, fontWeight = FontWeight.Bold))
                }
            }
        }
    }

    companion object {
        val SMALL = DpSize(110.dp, 110.dp)
        val MEDIUM = DpSize(250.dp, 110.dp)
        val LARGE = DpSize(250.dp, 250.dp)
        val DOSE = ActionParameters.Key<Long>("dose")
        fun link(ctx: Context, path: String) = Intent(ctx, MainActivity::class.java).setAction(Intent.ACTION_VIEW).setData(Uri.parse("medlog://$path")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        fun ordinal(n: Int) = "$n" + when { n % 100 in 11..13 -> "th"; n % 10 == 1 -> "st"; n % 10 == 2 -> "nd"; n % 10 == 3 -> "rd"; else -> "th" }
    }
}

/**
 * The family widget: the person's own first three messages (one tap sends, and MedLog opens to show who got
 * it and who answered), a call to the first helper, and SOS.
 */
class FamilyWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.medlog
        val s = app.settings.value
        val self = s.onboarded && s.role == "self"
        val msgs = if (self) s.messages.take(3) else emptyList()
        val helper = if (self) runCatching { app.db.helpers().all().firstOrNull() }.getOrNull() else null
        provideContent { Content(context, msgs, helper?.name) }
    }

    @Composable
    private fun Content(ctx: Context, msgs: List<String>, helper: String?) {
        val ink = ColorProvider(Color(0xFF1C1C1E))
        val white = ColorProvider(Color.White)
        val size = LocalSize.current
        val rowH = ((size.height.value - 16f - 6f * (msgs.size + 1)) / (msgs.size + 1).coerceAtLeast(2)).coerceIn(40f, 64f).dp
        Column(GlanceModifier.fillMaxSize().background(ImageProvider(R.drawable.widget_bg)).padding(8.dp)) {
            if (msgs.isEmpty()) {
                Box(GlanceModifier.fillMaxWidth().defaultWeight().background(ImageProvider(R.drawable.widget_soft)).padding(10.dp)
                    .clickable(actionStartActivity(MedLogWidget.link(ctx, "messages"))), contentAlignment = Alignment.Center) {
                    Text(com.suryaprakash.medlog.ui.tr("Tap to choose your messages"), style = TextStyle(color = ink, fontSize = 15.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center))
                }
            }
            msgs.forEachIndexed { i, m ->
                Row(GlanceModifier.fillMaxWidth().height(rowH).background(ImageProvider(R.drawable.widget_tile)).padding(horizontal = 12.dp)
                    .clickable(actionStartActivity(MedLogWidget.link(ctx, "help?send=${Uri.encode(m.substringBefore('|'))}"))), verticalAlignment = Alignment.CenterVertically) {
                    Text(com.suryaprakash.medlog.ui.tr(m.substringAfter('|')), GlanceModifier.defaultWeight(), style = TextStyle(color = ink, fontSize = 16.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                    Text(com.suryaprakash.medlog.ui.tr("Send"), style = TextStyle(color = ColorProvider(Color(0xFF0B6E66)), fontSize = 14.sp, fontWeight = FontWeight.Bold))
                }
                Spacer(GlanceModifier.height(6.dp))
            }
            if (msgs.isNotEmpty()) Spacer(GlanceModifier.defaultWeight())
            Row(GlanceModifier.fillMaxWidth().height(rowH)) {
                Row(GlanceModifier.defaultWeight().fillMaxHeight().background(ImageProvider(R.drawable.widget_brand)).padding(horizontal = 10.dp)
                    .clickable(actionStartActivity(MedLogWidget.link(ctx, if (helper != null) "call" else "help"))), verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(ImageProvider(R.drawable.ic_w_call), "Call", GlanceModifier.size(22.dp))
                    Spacer(GlanceModifier.width(6.dp))
                    Text(com.suryaprakash.medlog.ui.tr(helper?.let { "Call $it" } ?: "Ask family"), style = TextStyle(color = white, fontSize = 15.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                }
                Spacer(GlanceModifier.width(6.dp))
                Box(GlanceModifier.width(72.dp).fillMaxHeight().background(ImageProvider(R.drawable.widget_red))
                    .clickable(actionStartActivity(MedLogWidget.link(ctx, "emergency"))), contentAlignment = Alignment.Center) {
                    Text(com.suryaprakash.medlog.ui.tr("SOS"), style = TextStyle(color = white, fontSize = 17.sp, fontWeight = FontWeight.Bold))
                }
            }
        }
    }
}

class FamilyWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = FamilyWidget()
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
