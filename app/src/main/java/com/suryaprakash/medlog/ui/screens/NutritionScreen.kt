package com.suryaprakash.medlog.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.suryaprakash.medlog.nutrition.Nutrition
import com.suryaprakash.medlog.ui.BigButton
import com.suryaprakash.medlog.ui.Group
import com.suryaprakash.medlog.ui.GroupLine
import com.suryaprakash.medlog.ui.Hint
import com.suryaprakash.medlog.ui.LocalPalette
import com.suryaprakash.medlog.ui.LocalScale
import com.suryaprakash.medlog.ui.Nav
import com.suryaprakash.medlog.ui.Palette
import com.suryaprakash.medlog.ui.Screen
import com.suryaprakash.medlog.ui.SectionHeader
import com.suryaprakash.medlog.ui.Segmented
import com.suryaprakash.medlog.ui.Text
import com.suryaprakash.medlog.ui.ValueRow
import com.suryaprakash.medlog.medlog
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val SPANS = listOf(7 to "1W", 14 to "2W", 30 to "1M")

fun levelColor(p: Palette, level: String) = when (level) { "RED" -> p.red; "AMBER" -> p.amber; else -> p.ok }

/**
 * Nutrition, for the doctor: the one-line verdict and three numbers first, then the charts, then the detail
 * day by day, the missed feeds, what changed and what else was noticed.
 */
@Composable
fun NutritionScreen(nav: Nav) {
    val ctx = LocalContext.current
    val p = LocalPalette.current
    val sc = LocalScale.current
    var days by remember { mutableIntStateOf(14) }
    var r by remember { mutableStateOf<Nutrition.Report?>(null) }
    var targets by remember { mutableStateOf(false) }
    var version by remember { mutableIntStateOf(0) }
    LaunchedEffect(days, version) { r = Nutrition.build(ctx, days) }
    val rep = r
    Screen("Nutrition", rep?.headline?.text ?: "Preparing.", onHome = { nav.home() }, onBack = { nav.back() }, subtitle = "What went in, and weight") {
        Segmented(SPANS.map { it.second }, SPANS.indexOfFirst { it.first == days }) { days = SPANS[it].first }
        if (rep == null) return@Screen
        NutritionSummary(rep)

        // the charts
        SectionHeader("Calories each day", rep.kcalTarget?.let { "Dashed line: target ${it.roundToInt()} kcal" } ?: "No target yet: add a weight", null)
        IntakeChart(rep)
        if (rep.weights.size >= 2) {
            SectionHeader("Weight", "${"%.1f".format(rep.weights.first().second)} → ${"%.1f".format(rep.weights.last().second)} kg", null)
            WeightChart(rep.weights)
        }

        // the detail
        SectionHeader("Day by day", "${rep.loggedDays} of ${rep.days.size} days logged", null)
        // the days with something noted, one line each; a run of empty days is one small pill ("13 days not noted")
        val fmt = DateTimeFormatter.ofPattern("EEE, d MMM", com.suryaprakash.medlog.speech.I18n.locale)
        val short = DateTimeFormatter.ofPattern("d MMM", com.suryaprakash.medlog.speech.I18n.locale)
        val runs = mutableListOf<List<com.suryaprakash.medlog.nutrition.Nutrition.Day>>()
        rep.days.reversed().forEach { d -> if (runs.isNotEmpty() && !d.logged && !runs.last().first().logged) runs[runs.lastIndex] = runs.last() + d else runs += listOf(d) }
        Group {
            runs.forEachIndexed { i, run ->
                if (i > 0) GroupLine()
                val d = run.first()
                if (d.logged) ValueRow(d.date.format(fmt), "${d.kcal.roundToInt()} kcal", sub = "${d.protein.roundToInt()} g protein · ${d.water} glasses of water",
                    valueColor = rep.kcalTarget?.let { t -> if (d.kcal < t * 0.6) p.red else if (d.kcal < t * 0.85) p.amber else null })
                else Box(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp)) {
                    val words = if (run.size == 1) "Nothing noted · ${d.date.format(short)}" else "${run.size} days not noted · ${run.last().date.format(short)} – ${d.date.format(short)}"
                    Text(words, fontSize = sc.small, fontWeight = FontWeight.SemiBold, color = p.inkSoft,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(p.fill).padding(horizontal = 12.dp, vertical = 6.dp))
                }
            }
        }
        if (rep.missed.isNotEmpty()) {
            SectionHeader("Missed feeds", "${rep.missed.size} in this time", null)
            com.suryaprakash.medlog.ui.Timeline(rep.missed.sortedByDescending { it.at }.take(20).map { m ->
                com.suryaprakash.medlog.ui.TimelineItem("${dayLabel(m.at)} ${timeLabel(m.at)}".trim(), "${m.feed} · ${m.ml.roundToInt()} ml", mark = p.red)
            })
        }
        if (rep.feedLines.isNotEmpty()) {
            SectionHeader("Feeds", "Given now", null)
            Group {
                rep.feedLines.forEachIndexed { i, f ->
                    if (i > 0) GroupLine()
                    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp)) {
                        Text(f.name, fontSize = sc.body, fontWeight = FontWeight.SemiBold, color = p.ink)
                        Text(listOfNotNull(f.detail, f.since).joinToString(" · "), fontSize = sc.small, color = p.inkSoft)
                    }
                }
            }
        }
        if (rep.observed.isNotEmpty()) {
            SectionHeader("Also noticed", "Problems that affect eating", null)
            Group {
                rep.observed.forEachIndexed { i, t ->
                    if (i > 0) GroupLine()
                    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp)) {
                        Text(t.substringBefore(": "), fontSize = sc.body, fontWeight = FontWeight.SemiBold, color = p.ink)
                        if (": " in t) Text(t.substringAfter(": ").replaceFirstChar(Char::uppercase), fontSize = sc.small, color = p.inkSoft)
                    }
                }
            }
        }
        SectionHeader("Targets", if (rep.targetsFromDoctor) "Set by the doctor" else "About 30 kcal and 1 g protein per kg · doctor to confirm", "Change") { targets = true }
        Group {
            ValueRow("Calories a day", rep.kcalTarget?.let { "${it.roundToInt()} kcal" } ?: "Needs a weight")
            GroupLine()
            ValueRow("Protein a day", rep.proteinTarget?.let { "${it.roundToInt()} g" } ?: "Needs a weight")
        }
        Hint("Food values are estimates for home cooking. Feed values come from what was entered for each feed.")
    }
    if (targets) TargetsSheet(rep?.kcalTarget?.takeIf { rep.targetsFromDoctor }, rep?.proteinTarget?.takeIf { rep.targetsFromDoctor }, onDone = { k, pr ->
        ctx.medlog.settings.putString("kcal_target", k?.toString()); ctx.medlog.settings.putString("protein_target", pr?.toString()); targets = false; version++
    }, onDismiss = { targets = false })
}

/** The verdict, then three numbers side by side. Used here and on the doctor page. */
@Composable
fun NutritionSummary(rep: Nutrition.Report, onOpen: (() -> Unit)? = null) {
    val p = LocalPalette.current
    val sc = LocalScale.current
    val edge = levelColor(p, rep.headline.level)
    val sh = RoundedCornerShape(sc.radius)
    Column(Modifier.fillMaxWidth().clip(sh).background(p.card).drawBehind { drawRect(edge, size = androidx.compose.ui.geometry.Size(5.dp.toPx(), size.height)) }
        .then(if (onOpen != null) Modifier.padding(0.dp) else Modifier).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(rep.headline.text, fontSize = sc.headline, fontWeight = FontWeight.Bold, color = p.ink, lineHeight = sc.headline * 1.25f)
        rep.findings.drop(1).take(4).forEach { f ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(levelColor(p, f.level)))
                Spacer(Modifier.width(10.dp))
                Text(f.text, fontSize = sc.small, color = p.ink)
            }
        }
    }
    Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Stat("Calories", rep.kcalPct?.let { "$it%" } ?: "${rep.avgKcal.roundToInt()}", rep.kcalTarget?.let { "${rep.avgKcal.roundToInt()} of ${it.roundToInt()} a day" } ?: "kcal a day",
            rep.kcalPct?.let { if (it < 60) p.red else if (it < 85) p.amber else p.ok }, Modifier.weight(1f).fillMaxHeight())
        Stat("Protein", rep.proteinPct?.let { "$it%" } ?: "${rep.avgProtein.roundToInt()} g", rep.proteinTarget?.let { "${rep.avgProtein.roundToInt()} of ${it.roundToInt()} g a day" } ?: "a day",
            rep.proteinPct?.let { if (it < 60) p.red else if (it < 80) p.amber else p.ok }, Modifier.weight(1f).fillMaxHeight())
        Stat("Weight", rep.weightChange?.let { (if (it > 0) "+" else "") + "%.1f".format(it) } ?: "–", if (rep.weightChange != null) "kg in ${rep.weightDays} days" else "Not enough",
            rep.weightChange?.let { c -> val pct = 100 * c / rep.weights.first().second; if (pct <= -5) p.red else if (pct <= -2) p.amber else p.ok }, Modifier.weight(1f).fillMaxHeight())
    }
    if (onOpen != null) BigButton("Open nutrition report", tone = com.suryaprakash.medlog.ui.Tone.TINT, height = 52.dp, onClick = onOpen)
}

@Composable
private fun Stat(label: String, value: String, sub: String, color: Color?, modifier: Modifier) {
    val p = LocalPalette.current
    val sc = LocalScale.current
    val sh = RoundedCornerShape(sc.radius)
    Column(modifier.clip(sh).background(p.card).padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, fontSize = sc.small * 0.88f, color = p.inkSoft)
        Text(value, fontSize = sc.headline, fontWeight = FontWeight.Bold, color = color ?: p.ink, maxLines = 1)
        Text(sub, fontSize = sc.small * 0.8f, color = p.inkSoft, lineHeight = sc.small)
    }
}

/** A bar per day, coloured by how close it came to the target, with the target as a dashed line. */
@Composable
private fun IntakeChart(rep: Nutrition.Report) {
    val p = LocalPalette.current
    val t = rep.kcalTarget
    val hi = maxOf(rep.days.maxOfOrNull { it.kcal } ?: 0.0, t ?: 0.0, 500.0) * 1.1
    val sh = RoundedCornerShape(LocalScale.current.radius)
    Box(Modifier.fillMaxWidth().clip(sh).background(p.card).padding(16.dp)) {
        Canvas(Modifier.fillMaxWidth().height(160.dp).semantics { contentDescription = "Calories each day" }) {
            val w = size.width / rep.days.size
            rep.days.forEachIndexed { i, d ->
                if (!d.logged) { drawRoundRect(p.line, Offset(i * w + w * 0.2f, size.height - 4.dp.toPx()), Size(w * 0.6f, 4.dp.toPx()), CornerRadius(4f)); return@forEachIndexed }
                val h = (size.height * d.kcal / hi).toFloat()
                val c = t?.let { if (d.kcal < it * 0.6) p.red else if (d.kcal < it * 0.85) p.amber else p.ok } ?: p.tintBlue
                drawRoundRect(c, Offset(i * w + w * 0.2f, size.height - h), Size(w * 0.6f, h), CornerRadius(w * 0.18f))
            }
            t?.let { tt ->
                val y = (size.height - size.height * tt / hi).toFloat()
                drawLine(p.ink.copy(alpha = 0.6f), Offset(0f, y), Offset(size.width, y), 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f)))
            }
        }
    }
}

@Composable
private fun WeightChart(weights: List<Pair<Long, Double>>) {
    val p = LocalPalette.current
    val lo = weights.minOf { it.second } - 1; val hi = weights.maxOf { it.second } + 1
    val from = weights.first().first; val to = weights.last().first.coerceAtLeast(from + 1)
    val sh = RoundedCornerShape(LocalScale.current.radius)
    Box(Modifier.fillMaxWidth().clip(sh).background(p.card).padding(16.dp)) {
        Canvas(Modifier.fillMaxWidth().height(120.dp).semantics { contentDescription = "Weight over time" }) {
            fun o(t: Long, v: Double) = Offset(((t - from).toFloat() / (to - from)) * size.width, (size.height - (v - lo) / (hi - lo) * size.height).toFloat())
            for (i in 1 until weights.size) drawLine(p.tintPurple, o(weights[i - 1].first, weights[i - 1].second), o(weights[i].first, weights[i].second), 3.dp.toPx())
            weights.forEach { (t, v) -> drawCircle(Color.White, 6.dp.toPx(), o(t, v)); drawCircle(p.tintPurple, 4.dp.toPx(), o(t, v)) }
        }
    }
}

/** The doctor's own targets; blank goes back to the one worked out from weight. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun TargetsSheet(kcal: Double?, protein: Double?, onDone: (Double?, Double?) -> Unit, onDismiss: () -> Unit) {
    val p = LocalPalette.current
    val sc = LocalScale.current
    var k by remember { mutableStateOf(kcal?.roundToInt()?.toString() ?: "") }
    var pr by remember { mutableStateOf(protein?.roundToInt()?.toString() ?: "") }
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss, containerColor = p.paper,
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = sc.margin).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Daily targets", fontSize = sc.headline, fontWeight = FontWeight.Bold, color = p.ink)
            Hint("As advised by the doctor or dietitian. Leave empty to work them out from weight.")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                com.suryaprakash.medlog.ui.BigField("Calories (kcal)", k, { k = it.filter(Char::isDigit).take(4) }, Modifier.weight(1f), keyboard = androidx.compose.ui.text.input.KeyboardType.Number)
                com.suryaprakash.medlog.ui.BigField("Protein (g)", pr, { pr = it.filter(Char::isDigit).take(3) }, Modifier.weight(1f), keyboard = androidx.compose.ui.text.input.KeyboardType.Number)
            }
            BigButton("Done", onClick = { onDone(k.toDoubleOrNull(), pr.toDoubleOrNull()) })
        }
    }
}
