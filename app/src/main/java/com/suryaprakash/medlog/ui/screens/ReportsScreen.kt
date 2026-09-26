package com.suryaprakash.medlog.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.VolumeUp
import com.suryaprakash.medlog.ui.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.suryaprakash.medlog.data.DAY
import com.suryaprakash.medlog.data.DoseStatus
import com.suryaprakash.medlog.data.Kind
import com.suryaprakash.medlog.data.Note
import com.suryaprakash.medlog.medlog
import com.suryaprakash.medlog.nlu.factsFromJson
import com.suryaprakash.medlog.pictogram.ProblemPicture
import com.suryaprakash.medlog.ui.BigButton
import com.suryaprakash.medlog.ui.Body
import com.suryaprakash.medlog.ui.Card
import com.suryaprakash.medlog.ui.Chip
import com.suryaprakash.medlog.ui.FlowRowOf
import com.suryaprakash.medlog.ui.Hint
import com.suryaprakash.medlog.ui.LocalPalette
import com.suryaprakash.medlog.ui.LocalScale
import com.suryaprakash.medlog.ui.Nav
import com.suryaprakash.medlog.ui.Screen
import com.suryaprakash.medlog.ui.Title
import com.suryaprakash.medlog.ui.Tone
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Plain-language trends (plan 14). Counts and times, never claims. */
data class ProblemStat(val problemId: String, val label: String, val counts: List<Int>, val total: Int, val previous: Int, val caption: String, val timeOfDay: String?, val link: String?)

suspend fun buildStats(ctx: android.content.Context, days: Int): Pair<List<ProblemStat>, List<String>> {
    val app = ctx.medlog
    val zone = ZoneId.systemDefault()
    val end = LocalDate.now().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val start = end - days * DAY
    val notes = app.db.notes().between(start - days * DAY, end)
    val cur = notes.filter { it.occurredAt >= start }
    val prev = notes.filter { it.occurredAt < start }
    val buckets = if (days <= 31) days else 12
    val bucketMs = (end - start) / buckets
    val stats = cur.filter { it.kind == Kind.SYMPTOM && it.problemId != null && factsFromJson(it.details)["better"] == null }.groupBy { it.problemId!! }.map { (pid, list) ->
        val counts = (0 until buckets).map { b -> list.filter { ((it.occurredAt - start) / bucketMs).toInt() == b }.sumOf { it.count ?: 1 } }
        val total = list.sumOf { it.count ?: 1 }
        val before = prev.filter { it.problemId == pid }.sumOf { it.count ?: 1 }
        val caption = when {
            before == 0 -> "New in this period."
            total < before -> "Less often than before ($total, was $before)."
            total > before -> "More often than before ($total, was $before)."
            else -> "About the same as before."
        }
        val hours = list.map { Instant.ofEpochMilli(it.occurredAt).atZone(zone).hour }
        val bucket = hours.groupingBy { h -> when (h) { in 5..11 -> "morning"; in 12..16 -> "afternoon"; in 17..20 -> "evening"; else -> "night" } }.eachCount()
        val top = bucket.maxByOrNull { it.value }
        val tod = if (list.size >= 3 && top != null && top.value * 10 >= list.size * 6) "Mostly in the ${top.key}." else null
        val after = list.count { n -> (factsFromJson(n.details)["context"]?.value as? String)?.contains(Regex("after (breakfast|lunch|dinner|food|eating|meals?)")) == true }
        val link = if (list.size >= 3 && after * 2 >= list.size) "After meals $after of ${list.size} times." else null
        ProblemStat(pid, app.catalogue.problem(pid)?.label ?: pid, counts, total, before, caption, tod, link)
    }.sortedByDescending { it.total }
    // medicines
    val meds = app.db.medicines().all().associateBy { it.id }
    val doses = app.db.doses().between(start, minOf(end, System.currentTimeMillis()))
    val medLines = ArrayList<String>()
    if (doses.isNotEmpty()) {
        val taken = doses.count { it.status == DoseStatus.TAKEN }
        medLines += "You took $taken of ${doses.size} medicines (${taken * 100 / doses.size}%)."
        doses.groupBy { it.medicineId }.forEach { (id, l) -> val t = l.count { it.status == DoseStatus.TAKEN }; if (t < l.size) medLines += "${meds[id]?.name}: $t of ${l.size}." }
        val missedByTime = doses.filter { it.status == DoseStatus.MISSED || it.status == DoseStatus.SKIPPED }
            .groupingBy { d -> when (Instant.ofEpochMilli(d.scheduledAt).atZone(zone).hour) { in 5..11 -> "morning"; in 12..16 -> "afternoon"; in 17..20 -> "evening"; else -> "night" } }.eachCount()
        missedByTime.maxByOrNull { it.value }?.takeIf { it.value >= 2 }?.let { medLines += "Most missed doses are in the ${it.key}." }
    }
    return stats to medLines
}

suspend fun weeklySummaryText(ctx: android.content.Context): String {
    val (stats, meds) = buildStats(ctx, 7)
    val sb = StringBuilder("This week. ")
    if (stats.isEmpty()) sb.append("You didn't note any problems. ")
    stats.take(3).forEach { s -> sb.append("${s.label}: ${s.total} ${if (s.total == 1) "time" else "times"}. ${s.caption} ") }
    meds.firstOrNull()?.let { sb.append(it).append(' ') }
    if (meds.firstOrNull()?.contains("(100%)") == true) sb.append("Well done.")
    return sb.toString().trim()
}

@Composable
fun ReportsScreen(nav: Nav) {
    val ctx = LocalContext.current
    val app = ctx.medlog
    val p = LocalPalette.current
    val sc = LocalScale.current
    var days by remember { mutableStateOf(7) }
    var stats by remember { mutableStateOf<List<ProblemStat>>(emptyList()) }
    var meds by remember { mutableStateOf<List<String>>(emptyList()) }
    var readings by remember { mutableStateOf<Map<String, List<Pair<Long, Pair<Double, Double?>>>>>(emptyMap()) }
    var summary by remember { mutableStateOf("") }
    LaunchedEffect(days) {
        val (s, m) = buildStats(ctx, days); stats = s; meds = m
        readings = app.db.notes().kindSince(Kind.READING, System.currentTimeMillis() - days * DAY).mapNotNull { n: Note ->
            runCatching { JSONObject(n.details) }.getOrNull()?.let { o -> o.getString("type") to (n.occurredAt to (o.getDouble("v1") to o.optDouble("v2").takeIf { !it.isNaN() })) }
        }.groupBy({ it.first }, { it.second }).mapValues { it.value.sortedBy { v -> v.first } }
        summary = if (days == 7) weeklySummaryText(ctx) else stats.take(3).joinToString(" ") { "${it.label}: ${it.total} times. ${it.caption}" }
    }
    Screen("How am I doing", summary.ifBlank { "Your trends." }, onHome = { nav.home() }, onBack = { nav.back() }) {
        val periods = listOf(7 to "Week", 30 to "Month", 90 to "3 months", 365 to "Year")
        com.suryaprakash.medlog.ui.Segmented(periods.map { it.second }, periods.indexOfFirst { it.first == days }) { days = periods[it].first }
        if (summary.isNotBlank()) Card() { Body(summary); BigButton("Read it to me", tone = Tone.QUIET, icon = Icons.Rounded.VolumeUp, onClick = { app.speaker.say(summary) }) }
        if (stats.isEmpty()) Hint("No problems noted in this time.")
        stats.forEach { s ->
            Card {
                androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    ProblemPicture(app.catalogue.problem(s.problemId), sc.target)
                    androidx.compose.foundation.layout.Spacer(Modifier.height(4.dp))
                    Text("  ${s.label}: ${s.total}", fontWeight = FontWeight.Bold, fontSize = sc.body, color = p.ink)
                }
                Bars(s.counts, p.amber, "${s.label}: ${s.caption}")
                Body(s.caption, bold = true)
                s.timeOfDay?.let { Body(it) }
                s.link?.let { Body(it) }
            }
        }
        if (meds.isNotEmpty()) { Title("Medicines"); Card { meds.forEach { Body(it) } } }
        readings.forEach { (type, list) ->
            val name = mapOf("bp" to "Blood pressure", "sugar" to "Sugar", "spo2" to "Oxygen", "temp" to "Temperature", "pulse" to "Pulse", "weight" to "Weight")[type] ?: type
            val range = mapOf("bp" to (90.0 to 140.0), "sugar" to (70.0 to 180.0), "spo2" to (94.0 to 100.0), "temp" to (96.0 to 99.5), "pulse" to (50.0 to 100.0))[type]
            Card {
                Text(name, fontWeight = FontWeight.Bold, fontSize = sc.body, color = p.ink)
                Line(list.map { it.second.first }, list.map { it.second.second }, range, p.brand, "$name, last ${list.last().second.first.toInt()}")
                Hint("Latest: ${list.last().second.let { (a, b) -> if (b != null) "${a.toInt()}/${b.toInt()}" else com.suryaprakash.medlog.nlu.fmt1(a) }} on ${dayLabel(list.last().first)}")
            }
        }
    }
}

@Composable
private fun Bars(values: List<Int>, color: Color, description: String) {
    val p = LocalPalette.current
    val max = (values.maxOrNull() ?: 0).coerceAtLeast(1)
    Canvas(Modifier.fillMaxWidth().height(90.dp).semantics { contentDescription = description }) {
        val w = size.width / values.size
        values.forEachIndexed { i, v ->
            val h = (size.height - 16f) * v / max
            if (v > 0) drawRoundRect(color, Offset(i * w + w * 0.15f, size.height - h), Size(w * 0.7f, h), androidx.compose.ui.geometry.CornerRadius(6f))
        }
        drawLine(p.line, Offset(0f, size.height), Offset(size.width, size.height), 2f)
    }
}

@Composable
private fun Line(v1: List<Double>, v2: List<Double?>, range: Pair<Double, Double>?, color: Color, description: String) {
    val p = LocalPalette.current
    val all = v1 + v2.filterNotNull() + listOfNotNull(range?.first, range?.second)
    val lo = (all.minOrNull() ?: 0.0) * 0.95; val hi = (all.maxOrNull() ?: 1.0) * 1.05
    Canvas(Modifier.fillMaxWidth().height(120.dp).semantics { contentDescription = description }) {
        fun y(v: Double) = (size.height - (v - lo) / (hi - lo) * size.height).toFloat()
        range?.let { (a, b) -> drawRect(Color(0x221E6B2A), Offset(0f, y(b)), Size(size.width, y(a) - y(b))) }
        fun series(vals: List<Double?>, c: Color) {
            val pts = vals.mapIndexedNotNull { i, v -> v?.let { Offset(if (vals.size == 1) size.width / 2 else i * size.width / (vals.size - 1), y(it)) } }
            for (i in 1 until pts.size) drawLine(c, pts[i - 1], pts[i], 4f)
            pts.forEach { drawCircle(c, 7f, it) }
        }
        series(v1, color)
        if (v2.any { it != null }) series(v2, color.copy(alpha = 0.55f))
        drawRect(p.line, style = Stroke(1f))
        drawContext.canvas.nativeCanvas.apply { }
    }
}
