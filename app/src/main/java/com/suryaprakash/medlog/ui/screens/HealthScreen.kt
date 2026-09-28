package com.suryaprakash.medlog.ui.screens
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.horizontalScroll

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.Bloodtype
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.LocalDrink
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.MonitorWeight
import androidx.compose.material.icons.rounded.Sick
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.suryaprakash.medlog.data.DAY
import com.suryaprakash.medlog.data.planned
import com.suryaprakash.medlog.data.DoseStatus
import com.suryaprakash.medlog.data.Kind
import com.suryaprakash.medlog.medlog
import com.suryaprakash.medlog.nlu.fmt1
import com.suryaprakash.medlog.ui.Group
import com.suryaprakash.medlog.ui.GroupLine
import com.suryaprakash.medlog.ui.LocalPalette
import com.suryaprakash.medlog.ui.LocalScale
import com.suryaprakash.medlog.ui.Nav
import com.suryaprakash.medlog.ui.OptionIcon
import com.suryaprakash.medlog.ui.Palette
import com.suryaprakash.medlog.ui.Screen
import com.suryaprakash.medlog.ui.SectionHeader
import com.suryaprakash.medlog.ui.Segmented
import com.suryaprakash.medlog.ui.Text
import com.suryaprakash.medlog.ui.steady
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import com.suryaprakash.medlog.ui.lift
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.Locale

/** One thing MedLog tracks over time: a line of readings, or a bar for each day, week or month. */
private data class Metric(
    val key: String, val name: String, val unit: String, val icon: ImageVector, val tint: (Palette) -> Color,
    val bars: Boolean = false, val normal: Pair<Double, Double>? = null,
)

private val METRICS = listOf(
    Metric("weight", "Weight", "kg", Icons.Rounded.MonitorWeight, { it.tintPurple }),
    Metric("bp", "Blood pressure", "mmHg", Icons.Rounded.MonitorHeart, { it.tintPink }, normal = 90.0 to 140.0),
    Metric("sugar", "Sugar", "mg/dL", Icons.Rounded.Bloodtype, { it.tintOrange }, normal = 70.0 to 180.0),
    Metric("pulse", "Pulse", "beats a minute", Icons.Rounded.Favorite, { it.red }, normal = 50.0 to 100.0),
    Metric("spo2", "Oxygen", "%", Icons.Rounded.Air, { it.tintBlue }, normal = 94.0 to 100.0),
    Metric("temp", "Temperature", "°F", Icons.Rounded.Thermostat, { it.tintOrange }, normal = 96.0 to 99.5),
    Metric("water", "Water", "glasses", Icons.Rounded.LocalDrink, { it.tintBlue }, bars = true),
    Metric("meds", "Medicines taken", "%", Icons.Rounded.Medication, { it.tintTeal }, bars = true),
    Metric("symptoms", "Problems noted", "times", Icons.Rounded.Sick, { it.amber }, bars = true),
)

/** A reading at a moment: one value, or two for blood pressure. */
private data class Point(val at: Long, val v: Double, val v2: Double? = null)

/** A bar: the period it covers and its value (null when there's nothing that period). */
private data class Bar(val from: Long, val v: Double?)

private val PERIODS = listOf(7 to "W", 30 to "M", 182 to "6M", 365 to "Y")
private val SPAN_WORDS = mapOf(7 to "week", 30 to "month", 182 to "6 months", 365 to "year")

/** Everything My health and each measure's page show, read once: readings by type, and a value a day for the rest. */
private class HealthData(val points: Map<String, List<Point>>, val daily: Map<String, Map<LocalDate, Double>>)

private suspend fun loadHealth(ctx: android.content.Context): HealthData {
    val app = ctx.medlog
    val zone = ZoneId.systemDefault()
    val now = System.currentTimeMillis()
    val since = now - 365 * DAY
    val points = app.viewDb.notes().kindSince(Kind.READING, since).mapNotNull { n ->
        runCatching { JSONObject(n.details) }.getOrNull()?.let { o -> o.getString("type") to Point(n.occurredAt, o.getDouble("v1"), o.optDouble("v2").takeIf { !it.isNaN() }) }
    }.groupBy({ it.first }, { it.second }).mapValues { e -> e.value.sortedBy { it.at } }
    fun day(t: Long) = Instant.ofEpochMilli(t).atZone(zone).toLocalDate()
    val water = app.viewDb.notes().kindSince(Kind.WATER, since).groupBy { day(it.occurredAt) }.mapValues { e -> e.value.sumOf { it.count ?: 1 }.toDouble() }
    // problems: counted the one way used everywhere (a running "times today" is not added up twice)
    val symptoms = com.suryaprakash.medlog.data.Occurrences.perDayOf(app.viewDb.notes().symptomsSince(since), zone).mapValues { it.value.toDouble() }
    // medicines only: feeds are counted on their own, so a missed feed never lowers "medicines taken"
    val feedIds = app.viewDb.medicines().all().filter { it.form == "feed" }.map { it.id }.toSet()
    val meds = app.viewDb.doses().between(since, now).planned().filter { it.scheduledAt <= now && it.medicineId !in feedIds }.groupBy { day(it.scheduledAt) }
        .mapValues { e -> 100.0 * e.value.count { it.status == DoseStatus.TAKEN } / e.value.size }
    return HealthData(points, mapOf("water" to water, "symptoms" to symptoms, "meds" to meds))
}

/**
 * My health: the nutrition verdict, then every measure as a row with its latest value and date. Each row opens that
 * measure's own page, with its chart and every entry. Measures with nothing yet are one line that leads to adding them.
 */
@Composable
fun ReportsScreen(nav: Nav) {
    val ctx = LocalContext.current
    val p = LocalPalette.current
    var data by remember { mutableStateOf<HealthData?>(null) }
    var nutrition by remember { mutableStateOf<com.suryaprakash.medlog.nutrition.Nutrition.Report?>(null) }
    LaunchedEffect(Unit) { data = loadHealth(ctx); nutrition = com.suryaprakash.medlog.nutrition.Nutrition.build(ctx, 14) }
    Screen("My health", "Your numbers over time. Tap a measure to see its chart and every entry.", onHome = { nav.home() }, onBack = { nav.back() },
        subtitle = "Your numbers over time") {
        val d = data ?: run { com.suryaprakash.medlog.ui.Loading(); return@Screen }
        fun has(m: Metric) = if (m.bars) d.daily[m.key].orEmpty().isNotEmpty() else d.points[m.key].orEmpty().isNotEmpty()
        val (withData, without) = METRICS.partition { has(it) }
        if (withData.isNotEmpty()) {
            SectionHeader("Measures", "Tap one to see its chart", null)
            Group {
                withData.forEachIndexed { i, m ->
                    if (i > 0) GroupLine()
                    MetricRow(m, d.points[m.key].orEmpty(), d.daily[m.key].orEmpty()) { nav.go(com.suryaprakash.medlog.ui.Route.Measure(m.key)) }
                }
            }
        }
        // ── nutrition: the verdict and three numbers; the card opens the full report ──
        nutrition?.takeIf { it.loggedDays > 0 || it.weights.isNotEmpty() || it.feeds.isNotEmpty() }?.let { r ->
            SectionHeader("Nutrition", "Last 2 weeks", "Report") { nav.go(com.suryaprakash.medlog.ui.Route.Nutrition) }
            NutritionSummary(r)
        }
        if (without.isNotEmpty()) Group {
            com.suryaprakash.medlog.ui.NavRow("Not recorded yet", sub = without.joinToString(", ") { it.name }) { nav.go(com.suryaprakash.medlog.ui.Route.Readings) }
        }
        @Suppress("UNUSED_EXPRESSION") p
    }
}

/**
 * One measure on its own page: the latest value and the range, a chart for the chosen span (week, month, six months or
 * year), and every entry, newest first. A chart needs two points; with one, the reading is shown on its own.
 */
@Composable
fun MeasureScreen(nav: Nav, key: String) {
    val ctx = LocalContext.current
    val p = LocalPalette.current
    val sc = LocalScale.current
    val metric = METRICS.firstOrNull { it.key == key } ?: METRICS.first()
    var days by remember { mutableIntStateOf(30) }
    var data by remember { mutableStateOf<HealthData?>(null) }
    LaunchedEffect(Unit) { data = loadHealth(ctx) }
    val zone = ZoneId.systemDefault()
    val now = System.currentTimeMillis()
    val from = now - days * DAY
    Screen(metric.name, "${metric.name} over time, and every entry.", onHome = { nav.home() }, onBack = { nav.back() }) {
        val d = data ?: run { com.suryaprakash.medlog.ui.Loading(); return@Screen }
        Segmented(PERIODS.map { it.second }, PERIODS.indexOfFirst { it.first == days }) { days = PERIODS[it].first }
        val tint = metric.tint(p)
        val inSpan = d.points[metric.key].orEmpty().filter { it.at >= from }
        val bs = if (metric.bars) bars(d.daily[metric.key].orEmpty(), metric.key, days, zone) else emptyList()
        val (headline, caption) = summaryOf(metric, inSpan, bs, days)
        val csh = RoundedCornerShape(sc.radius)
        Column(Modifier.fillMaxWidth().lift(csh).clip(csh).background(p.card).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (headline == "No data") { Text(caption, fontSize = sc.body, color = p.inkSoft); return@Column }
            Text(caption, fontSize = sc.small, color = p.inkSoft)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(headline, fontSize = if (headline == "No data") sc.headline else sc.title * 1.2f, fontWeight = FontWeight.Bold, color = if (headline == "No data") p.inkSoft else p.ink)
                Spacer(Modifier.width(6.dp))
                if (headline != "No data") Text(metric.unit, fontSize = sc.body, color = p.inkSoft, modifier = Modifier.padding(bottom = 6.dp))
            }
            val enough = if (metric.bars) bs.count { it.v != null } >= 2 else inSpan.size >= 2
            when {
                !enough -> Text("The chart appears once there are two or more entries in this time.", fontSize = sc.small, color = p.inkSoft)
                // the chart starts where the entries do, so a few readings aren't squeezed against one edge
                metric.bars -> BarChart(bs.dropWhile { it.v == null }.let { if (it.size >= 7) it else bs.takeLast(7) }, tint, metric.unit, days)
                else -> {
                    val first = inSpan.minOf { it.at }
                    val start = maxOf(from, first - maxOf((now - first) / 10, DAY / 2))
                    LineChart(inSpan, start, now, tint, metric.normal, if (now - start <= 8 * DAY) 7 else days)
                }
            }
        }
        // ── every entry, newest first ──
        val entries: List<Pair<Long, String>> = if (metric.bars)
            d.daily[metric.key].orEmpty().entries.sortedByDescending { it.key }.take(60).map { (day, v) ->
                day.atStartOfDay(zone).toInstant().toEpochMilli() to when (metric.key) { "meds" -> "${v.toInt()}% taken"; "water" -> "${v.toInt()} glass${if (v.toInt() == 1) "" else "es"}"; else -> "${v.toInt()} noted" }
            }
        else d.points[metric.key].orEmpty().sortedByDescending { it.at }.take(60).map { pt ->
            pt.at to ((if (pt.v2 != null) "${pt.v.toInt()}/${pt.v2.toInt()}" else fmt1(pt.v)) + " " + metric.unit)
        }
        if (entries.isNotEmpty()) {
            SectionHeader("Every entry", "${entries.size} shown, newest first", null)
            com.suryaprakash.medlog.ui.Timeline(entries.map { (at, words) ->
                com.suryaprakash.medlog.ui.TimelineItem(if (metric.bars) SimpleDateFormat("EEE d MMM", com.suryaprakash.medlog.speech.I18n.locale).format(Date(at))
                    else "${dayLabel(at)} ${timeLabel(at)}".trim(), words)
            })
        }
        com.suryaprakash.medlog.ui.BigButton(when (metric.key) { "water" -> "Add water"; "meds" -> "Open Medicines"; "symptoms" -> "Open History"; else -> "Add a reading" }, tone = com.suryaprakash.medlog.ui.Tone.SECONDARY, height = 52.dp,
            onClick = { nav.go(if (metric.key == "water") com.suryaprakash.medlog.ui.Route.Food else if (metric.key == "meds") com.suryaprakash.medlog.ui.Route.Meds
                else if (metric.key == "symptoms") com.suryaprakash.medlog.ui.Route.Notes else com.suryaprakash.medlog.ui.Route.Readings) })
    }
}

/** The chosen span, cut into bars: a day each for a week or a month, a week each for six months, a month each for a year. */
private fun bars(d: Map<LocalDate, Double>, key: String, days: Int, zone: ZoneId): List<Bar> {
    val today = LocalDate.now(zone)
    val sum = key != "meds"
    fun agg(vals: List<Double>) = if (vals.isEmpty()) null else if (sum) vals.sum() / (if (days > 30) vals.size.coerceAtLeast(1) else 1) else vals.average()
    return when {
        days <= 30 -> (days - 1 downTo 0).map { k -> today.minusDays(k.toLong()).let { dd -> Bar(dd.atStartOfDay(zone).toInstant().toEpochMilli(), d[dd]) } }
        days <= 182 -> (25 downTo 0).map { k ->
            val start = today.minusWeeks(k.toLong()).minusDays(6)
            Bar(start.atStartOfDay(zone).toInstant().toEpochMilli(), agg((0..6).mapNotNull { d[start.plusDays(it.toLong())] }))
        }
        else -> (11 downTo 0).map { k ->
            val m = today.withDayOfMonth(1).minusMonths(k.toLong())
            Bar(m.atStartOfDay(zone).toInstant().toEpochMilli(), agg((0 until m.lengthOfMonth()).mapNotNull { d[m.plusDays(it.toLong())] }))
        }
    }
}

/** The big number and the line under it, for the chosen measure and span. */
private fun summaryOf(m: Metric, pts: List<Point>, bars: List<Bar>, days: Int): Pair<String, String> {
    val span = SPAN_WORDS[days] ?: "period"
    if (m.bars) {
        val vals = bars.mapNotNull { it.v }
        if (vals.isEmpty()) return "No data" to "Nothing in the last $span"
        return when (m.key) {
            "meds" -> "${vals.average().toInt()}" to "Average, last $span"
            "water" -> fmt1(vals.average()) to "Average a day, last $span"
            else -> "${vals.sum().toInt()}" to "In total, last $span"
        }
    }
    if (pts.isEmpty()) return "No data" to "Nothing in the last $span"
    val last = pts.last()
    val latest = if (last.v2 != null) "${last.v.toInt()}/${last.v2.toInt()}" else fmt1(last.v)
    val lo = pts.minOf { it.v }; val hi = pts.maxOf { it.v }
    return latest to "Latest, ${SimpleDateFormat("d MMMM", com.suryaprakash.medlog.speech.I18n.locale).format(Date(last.at))} · range ${fmt1(lo)} to ${fmt1(hi)}"
}

/** One measure in the list: icon, name, the latest value and when; it opens the measure's own page. */
@Composable
private fun MetricRow(m: Metric, pts: List<Point>, daily: Map<LocalDate, Double>, onClick: () -> Unit) {
    val p = LocalPalette.current
    val sc = LocalScale.current
    val tint = m.tint(p)
    val (latest, whenText) = when {
        m.bars && daily.isNotEmpty() -> daily.maxBy { it.key }.let { (d, v) ->
            (when (m.key) { "water" -> "${v.toInt()} glass${if (v.toInt() == 1) "" else "es"}"; "symptoms" -> "${v.toInt()} noted"; else -> "${v.toInt()}% taken" }) to
                (if (d == LocalDate.now()) "Today" else d.format(java.time.format.DateTimeFormatter.ofPattern("d MMM", com.suryaprakash.medlog.speech.I18n.locale)))
        }
        pts.isNotEmpty() -> pts.last().let { (if (it.v2 != null) "${it.v.toInt()}/${it.v2.toInt()}" else fmt1(it.v)) + " " + m.unit.substringBefore(" ") to "${dayLabel(it.at)} ${timeLabel(it.at)}".trim() }
        else -> "No data" to ""
    }
    Row(Modifier.fillMaxWidth().steady("${m.name}, $latest, $whenText. Opens its chart.", onClick = onClick)
        .padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        OptionIcon(m.icon, tint, 44.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(m.name, fontSize = sc.body, fontWeight = FontWeight.SemiBold, color = p.ink)
            Text(latest, fontSize = sc.small, color = p.ink, maxLines = 1)
        }
        Text(whenText, fontSize = sc.small, color = p.inkSoft, maxLines = 1)
        Spacer(Modifier.width(6.dp))
        androidx.compose.material3.Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = p.inkSoft)
    }
}

private fun dateLabel(t: Long, days: Int) = SimpleDateFormat(if (days > 182) "MMM" else "d MMM", Locale.getDefault()).format(Date(t))

/**
 * A chart area that always fits the card, whatever the span (a year is twelve month bars, never a sideways scroll
 * that hides part of it); the value labels sit on the right. [content] draws the chart and its dates at the given width.
 */
@Composable
private fun ScrollingChart(minWidth: Dp, hi: String, mid: String, lo: String, content: @Composable (Dp) -> Unit) {
    val p = LocalPalette.current
    val sc = LocalScale.current
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
        val avail = maxWidth - 44.dp
        @Suppress("UNUSED_VARIABLE") val unused = minWidth
        Row {
            Box(Modifier.width(avail)) { content(avail) }
            Column(Modifier.width(44.dp).height(200.dp).padding(start = 8.dp), verticalArrangement = Arrangement.SpaceBetween) {
                listOf(hi, mid, lo).forEach { Text(it, fontSize = sc.small * 0.8f, color = p.inkSoft) }
            }
        }
    }
}

/** Dates under a chart, spread evenly, about one every 90 dp. */
@Composable
private fun DateRow(from: Long, to: Long, width: Dp, days: Int) {
    val p = LocalPalette.current
    val sc = LocalScale.current
    val n = (width / 90.dp).toInt().coerceIn(2, 40)
    Row(Modifier.width(width), horizontalArrangement = Arrangement.SpaceBetween) {
        (0 until n).forEach { i -> Text(dateLabel(from + (to - from) * i / (n - 1), days), fontSize = sc.small * 0.8f, color = p.inkSoft, maxLines = 1) }
    }
}

/** Readings over time: dots joined by a line, placed by their real time, with the normal range shaded. Blood pressure shows both numbers. */
@Composable
private fun LineChart(pts: List<Point>, from: Long, to: Long, tint: Color, normal: Pair<Double, Double>?, days: Int) {
    val p = LocalPalette.current
    val values = pts.flatMap { listOfNotNull(it.v, it.v2) }
    val lo0 = (values + listOfNotNull(normal?.first)).minOrNull() ?: 0.0
    val hi0 = (values + listOfNotNull(normal?.second)).maxOrNull() ?: 1.0
    val pad = ((hi0 - lo0) * 0.1).coerceAtLeast(1.0)
    val lo = lo0 - pad; val hi = hi0 + pad
    // room for each day: a week fits; longer spans scroll
    val perDay = when { days <= 7 -> 0.dp; days <= 30 -> 14.dp; days <= 182 -> 5.dp; else -> 3.dp }
    ScrollingChart(perDay * days, axis(hi), axis((hi + lo) / 2), axis(lo)) { w ->
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Canvas(Modifier.width(w).height(200.dp).semantics { contentDescription = "Chart of ${pts.size} readings" }) {
                fun x(t: Long) = ((t - from).toFloat() / (to - from)) * size.width
                fun y(v: Double) = (size.height - (v - lo) / (hi - lo) * size.height).toFloat()
                repeat(4) { k -> val yy = size.height * k / 3f; drawLine(p.line, Offset(0f, yy), Offset(size.width, yy), 1.dp.toPx()) }
                normal?.let { (a, b) -> drawRect(p.okSoft, Offset(0f, y(b)), Size(size.width, y(a) - y(b))) }
                fun series(vals: List<Pair<Long, Double>>, c: Color) {
                    val path = Path()
                    vals.forEachIndexed { i, (t, v) -> if (i == 0) path.moveTo(x(t), y(v)) else path.lineTo(x(t), y(v)) }
                    if (vals.size > 1) drawPath(path, c, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
                    vals.forEach { (t, v) -> drawCircle(Color.White, 6.dp.toPx(), Offset(x(t), y(v))); drawCircle(c, 4.dp.toPx(), Offset(x(t), y(v))) }
                }
                series(pts.map { it.at to it.v }, tint)
                if (pts.any { it.v2 != null }) series(pts.mapNotNull { pt -> pt.v2?.let { pt.at to it } }, tint.copy(alpha = 0.5f))
            }
            DateRow(from, to, w, days)
        }
    }
}

/** A bar for each day, week or month, never thinner than a finger's width; empty periods leave a gap. */
@Composable
private fun BarChart(bars: List<Bar>, tint: Color, unit: String, days: Int) {
    val p = LocalPalette.current
    // counts: a top that halves into a whole number (8 and 4, not 7 and 3.5)
    val hi = (bars.mapNotNull { it.v }.maxOrNull() ?: 1.0).let { if (unit == "%") it.coerceAtLeast(100.0) else (kotlin.math.ceil(it / 2) * 2).coerceAtLeast(2.0) }
    if (bars.isEmpty()) return
    ScrollingChart(28.dp * bars.size, axis(hi), axis(hi / 2), "0") { w ->
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Canvas(Modifier.width(w).height(200.dp).semantics { contentDescription = "Bar chart" }) {
                repeat(4) { k -> val yy = size.height * k / 3f; drawLine(p.line, Offset(0f, yy), Offset(size.width, yy), 1.dp.toPx()) }
                val bw = size.width / bars.size
                bars.forEachIndexed { i, b ->
                    val v = b.v ?: return@forEachIndexed
                    val h = (size.height * v / hi).toFloat().coerceAtLeast(3.dp.toPx())
                    drawRoundRect(tint, Offset(i * bw + bw * 0.18f, size.height - h), Size(bw * 0.64f, h), CornerRadius(bw * 0.2f))
                }
            }
            DateRow(bars.first().from, bars.last().from, w, days)
        }
    }
}

/** An axis label: whole numbers from 10 up (175, not 175.8); one decimal below (like a temperature step). */
private fun axis(v: Double) = if (kotlin.math.abs(v) >= 10) "${kotlin.math.round(v).toInt()}" else fmt1(v)
