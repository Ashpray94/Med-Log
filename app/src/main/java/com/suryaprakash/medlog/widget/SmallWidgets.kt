package com.suryaprakash.medlog.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.suryaprakash.medlog.R
import com.suryaprakash.medlog.medlog
import com.suryaprakash.medlog.ui.tr

private val INK = ColorProvider(Color(0xFF18181B))
private val SOFT = ColorProvider(Color(0xFF52525B))
private val WHITE = ColorProvider(Color.White)

/**
 * The widget as one question (Yes / Cancel) before anything is saved or sent; or, once a problem is noted,
 * "Add details / Done" (Done sets a reminder in 30 minutes). Big targets, nothing else to hit by mistake.
 * [src] is the widget it belongs to, so a question on one widget never shows on another.
 */
@Composable
internal fun WidgetQuestion(ctx: Context, src: String, ask: String?, noted: String?) {
    val (title, sub, yes, no) = if (ask != null) {
        val (kind, _, label) = ask.split("|", limit = 3).let { Triple(it[0], it.getOrElse(1) { "" }, it.getOrElse(2) { "" }) }
        when (kind) {
            "message" -> listOf("Send \"$label\"?", "To your family", "Yes, send", "Cancel")
            "water" -> listOf("Add $label?", "Saved with the time", "Yes, add it", "Cancel")
            "dose" -> listOf("Took $label?", "Marks it taken now", "Yes, taken", "Cancel")
            else -> listOf("Note $label?", "Saved with the time", "Yes, note it", "Cancel")
        }
    } else {
        val parts = noted!!.split("|", limit = 3)
        listOf("✓ ${parts.getOrElse(1) { "" }} noted", parts.getOrElse(2) { "" }, "Add details", "Done")
    }
    val p = actionParametersOf(MedLogWidget.SRC to src)
    Column(GlanceModifier.fillMaxSize().background(ImageProvider(R.drawable.widget_bg)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(tr(title), style = TextStyle(color = INK, fontSize = 18.sp, fontWeight = FontWeight.Bold), maxLines = 3)
        Text(tr(sub), style = TextStyle(color = SOFT, fontSize = 14.sp), maxLines = 2)
        Spacer(GlanceModifier.height(10.dp))
        Row(GlanceModifier.fillMaxWidth().height(52.dp)) {
            val noAct = if (ask != null) actionRunCallback<AnswerAsk>(actionParametersOf(MedLogWidget.YES to false, MedLogWidget.SRC to src)) else actionRunCallback<LaterDetails>(p)
            Box(GlanceModifier.defaultWeight().fillMaxHeight().background(ImageProvider(R.drawable.widget_soft)).clickable(noAct), contentAlignment = Alignment.Center) {
                Text(tr(no), style = TextStyle(color = INK, fontSize = 16.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center), maxLines = 2)
            }
            Spacer(GlanceModifier.width(8.dp))
            val yesAct = if (ask != null) actionRunCallback<AnswerAsk>(actionParametersOf(MedLogWidget.YES to true, MedLogWidget.SRC to src))
                else actionStartActivity(MedLogWidget.link(ctx, "tell?note=${noted!!.substringBefore("|")}&problem=${noted.split("|").getOrElse(3) { "" }}"))
            Box(GlanceModifier.defaultWeight().fillMaxHeight().background(ImageProvider(R.drawable.widget_brand)).clickable(yesAct), contentAlignment = Alignment.Center) {
                Text(tr(yes), style = TextStyle(color = WHITE, fontSize = 16.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center), maxLines = 2)
            }
        }
    }
}

/** A question or a just-noted entry waiting on widget [src], while still fresh. */
private fun pending(ctx: Context, src: String): Pair<String?, String?> {
    val st = ctx.medlog.settings
    val now = System.currentTimeMillis()
    return st.getString("${src}_ask")?.takeIf { now - st.getLong("${src}_ask_at") < 60_000L } to
        st.getString("${src}_done")?.takeIf { now - st.getLong("${src}_done_at") < 10 * 60_000L }
}

/**
 * "How I feel": the person's usual problems as pictures, one tap each (asked first, then Add details / Done),
 * and "More" for the full list. Their own emergencies open MedLog straight away, so helpers are called at once.
 */
class FeelWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact
    private data class Face(val self: Boolean, val tiles: List<MedLogWidget.Tile>, val ask: String?, val noted: String?)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val first = load(context)
        provideContent {
            val tick by MedLogWidget.tick.collectAsState()
            var f by remember { mutableStateOf(first) }
            LaunchedEffect(tick) { if (tick > 0) f = load(context) }
            if (f.ask != null || f.noted != null) WidgetQuestion(context, SRC, f.ask, f.noted) else Content(context, f)
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
                plan.symptoms, java.time.LocalTime.now().hour, known = { app.catalogue.problem(it) != null }, limit = 6)
            (r.yours + r.suggested).distinct().take(5)
        }.getOrDefault(emptyList())
        val tiles = ids.mapNotNull { pid ->
            val p = app.catalogue.problem(pid) ?: return@mapNotNull null
            MedLogWidget.Tile(p.id, p.label, com.suryaprakash.medlog.pictogram.Sprites.bitmap(context, p.id, 200) ?: return@mapNotNull null, pid in plan.emergencies)
        }
        val (ask, noted) = pending(context, SRC)
        return Face(self, tiles, ask, noted)
    }

    @Composable
    private fun Content(ctx: Context, f: Face) {
        val size = LocalSize.current
        val w = size.width.value - 16f
        val h = size.height.value - 16f
        // two rows when there's room, else one; the last place is always "More"
        val cols = if (w >= 240f) 3 else 2
        val rows = if (h >= 190f) 2 else 1
        val side = minOf((w - 8f * (cols - 1)) / cols, (h - 8f * (rows - 1)) / rows)
        val shown = f.tiles.take(cols * rows - 1)
        Column(GlanceModifier.fillMaxSize().background(ImageProvider(R.drawable.widget_bg)).padding(8.dp)) {
            if (!f.self) {
                Box(GlanceModifier.fillMaxSize().clickable(actionStartActivity(MedLogWidget.link(ctx, ""))), contentAlignment = Alignment.Center) {
                    Text(tr("Open the app to finish setting up"), style = TextStyle(color = INK, fontSize = 15.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center))
                }
                return@Column
            }
            val cells: List<MedLogWidget.Tile?> = shown + listOf(null)
            cells.chunked(cols).take(rows).forEachIndexed { r, row ->
                if (r > 0) Spacer(GlanceModifier.height(8.dp))
                Row(GlanceModifier.fillMaxWidth().height(side.dp)) {
                    row.forEachIndexed { i, t ->
                        if (i > 0) Spacer(GlanceModifier.width(8.dp))
                        val act = when {
                            t == null -> actionStartActivity(MedLogWidget.link(ctx, "tell"))
                            t.emergency -> actionStartActivity(MedLogWidget.link(ctx, "tell?problem=${t.id}"))
                            else -> actionRunCallback<AskFirst>(actionParametersOf(MedLogWidget.ASK to "problem|${t.id}|${t.label}", MedLogWidget.SRC to SRC))
                        }
                        Column(GlanceModifier.defaultWeight().fillMaxHeight().background(ImageProvider(if (t == null) R.drawable.widget_soft else R.drawable.widget_tile)).padding(4.dp).clickable(act),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically) {
                            if (t != null) Image(ImageProvider(t.picture), t.label, GlanceModifier.size((side * 0.45f).coerceIn(28f, 56f).dp))
                            Text(tr(t?.label ?: "More"), style = TextStyle(color = INK, fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center), maxLines = 2)
                        }
                    }
                    // an unfilled place stays empty, so every tile keeps the same size
                    repeat(cols - row.size) { Spacer(GlanceModifier.width(8.dp)); Spacer(GlanceModifier.defaultWeight()) }
                }
            }
        }
    }

    companion object { const val SRC = "feel" }
}

/**
 * "Toilet and tummy": three big buttons. Each opens that page in MedLog, where the pictures of what it looked like
 * make the note precise (the colour, blood, how much); nothing is guessed from the widget.
 */
class OutWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val self = context.medlog.settings.value.let { it.onboarded && it.role == "self" }
        // the same pictures as in MedLog, so each button is recognised without reading
        val pics = listOf("loose_motions", "frequent_urine", "vomiting").map { com.suryaprakash.medlog.pictogram.Sprites.bitmap(context, it, 96) }
        provideContent { Content(context, self, pics) }
    }

    @Composable
    private fun Content(ctx: Context, self: Boolean, pics: List<android.graphics.Bitmap?>) {
        val size = LocalSize.current
        // side by side when wide, stacked when tall and narrow
        val across = size.width.value >= size.height.value * 1.3f
        val kinds = listOf(Triple("Stool", R.drawable.ic_w_toilet, 0), Triple("Urine", R.drawable.ic_w_water, 1), Triple("Vomit", R.drawable.ic_w_toilet, 2))
        Column(GlanceModifier.fillMaxSize().background(ImageProvider(R.drawable.widget_bg)).padding(8.dp)) {
            if (!self) {
                Box(GlanceModifier.fillMaxSize().clickable(actionStartActivity(MedLogWidget.link(ctx, ""))), contentAlignment = Alignment.Center) {
                    Text(tr("Open the app to finish setting up"), style = TextStyle(color = INK, fontSize = 15.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center))
                }
                return@Column
            }
            @Composable fun Button(modifier: GlanceModifier, label: String, icon: Int, tab: Int) {
                Row(modifier.background(ImageProvider(R.drawable.widget_tile)).padding(horizontal = 8.dp).clickable(actionStartActivity(MedLogWidget.link(ctx, "output?tab=$tab"))),
                    verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.CenterHorizontally) {
                    val pic = pics.getOrNull(tab)
                    if (pic != null) Image(ImageProvider(pic), null, GlanceModifier.size(32.dp)) else Image(ImageProvider(icon), null, GlanceModifier.size(24.dp))
                    Spacer(GlanceModifier.width(8.dp))
                    Text(tr(label), style = TextStyle(color = INK, fontSize = 16.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                }
            }
            if (across) Row(GlanceModifier.fillMaxSize()) {
                kinds.forEachIndexed { i, (label, icon, tab) -> if (i > 0) Spacer(GlanceModifier.width(8.dp)); Button(GlanceModifier.defaultWeight().fillMaxHeight(), label, icon, tab) }
            } else kinds.forEachIndexed { i, (label, icon, tab) ->
                if (i > 0) Spacer(GlanceModifier.height(8.dp))
                Button(GlanceModifier.fillMaxWidth().defaultWeight(), label, icon, tab)
            }
        }
    }
}

class FeelWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = FeelWidget() }
class OutWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = OutWidget() }
