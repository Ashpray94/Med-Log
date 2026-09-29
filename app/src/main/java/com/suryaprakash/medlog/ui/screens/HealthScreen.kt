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
import com.suryaprakash.medlog.data.DoseStatus
import com.suryaprakash.medlog.data.Kind
import com.suryaprakash.medlog.data.occurrences
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

/**
 * My health, like the Health app: one big chart for the chosen measure, with the time span switchable,
 * and every measure below as a row with its latest value and a small trend line. Tap a row to chart it.
 */
@Composable
fun ReportsScreen(nav: Nav) {
    val ctx = LocalContext.current
    val app = ctx.medlog
    val p = LocalPalette.current
    val sc = LocalScale.current
    var days by remember { mutableIntStateOf(30) }
    var chosen by remember { mutableStateOf<String?>(null) }
    var points by remember { mutableStateOf<Map<String, List<Point>>>(emptyMap()) }
    var daily by remember { mutableStateOf<Map<String, Map<LocalDate, Double>>>(emptyMap()) }
    var nutrition by remember { mutableStateOf<com.suryaprakash.medlog.nutrition.Nutrition.Report?>(null) }
    LaunchedEffect(Unit) { nutrition = com.suryaprakash.medlog.nutrition.Nutrition.build(ctx, 14) }
    val zone = ZoneId.systemDefault()
    val now = System.currentTimeMillis()
    val from = now - days * DAY

    LaunchedEffect(days) {
        val since = now - 365 * DAY
        points = app.db.notes().kindSince(Kind.READING, since).mapNotNull { n ->
            runCatching { JSONObject(n.details) }.getOrNull()?.let { o -> o.getString("type") to Point(n.occurredAt, o.getDouble("v1"), o.optDouble("v2").takeIf { !it.isNaN() }) }
        }.groupBy({ it.first }, { it.second }).mapValues { e -> e.value.sortedBy { it.at } }
        fun day(t: Long) = Instant.ofEpochMilli(t).atZone(zone).toLocalDate()
        val water = app.db.notes().kindSince(Kind.WATER, since).groupBy { day(it.occurredAt) }.mapValues { e -> e.value.sumOf { it.count ?: 1 }.toDouble() }
        // the total number of times per day (Σ count), not the number of notes; "Yes, better" taps add nothing (B19)
        val symptoms = app.db.notes().symptomsSince(since).occurrences().groupBy { day(it.occurredAt) }.mapValues { e -> e.value.sumOf { it.count ?: 1 }.toDouble() }
        val meds = app.db.doses().between(since, now).filter { it.scheduledAt <= now }.groupBy { day(it.scheduledAt) }
            .mapValues { e -> 100.0 * e.value.count { it.status == DoseStatus.TAKEN } / e.value.size }
        daily = mapOf("water" to water, "symptoms" to symptoms, "meds" to meds)
        // open on the first measure that has something to show
        if (chosen == null) chosen = METRICS.firstOrNull { m -> if (m.bars) daily[m.key].orEmpty().isNotEmpty() else points[m.key].orEmpty().isNotEmpty() }?.key ?: "weight"
    }

    // the chosen span, cut into bars: a day each for a week or a month, a week each for six months, a month each for a year
    fun bars(key: String): List<Bar> {
        val d = daily[key] ?: return emptyList()
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

    fun has(m: Metric) = if (m.bars) daily[m.key].orEmpty().isNotEmpty() else points[m.key].orEmpty().isNotEmpty()
    val metric = METRICS.first { it.key == (chosen ?: "weight") }

    Screen("My health", "Your numbers over time. Choose week, month, six months or year. Tap a measure to see its chart.", onHome = { nav.home() }, onBack = { nav.back() },
        subtitle = "Your numbers over time") {
        Segmented(PERIODS.map { it.second }, PERIODS.indexOfFirst { it.first == days }) { days = PERIODS[it].first }

        // ── the big chart ──
        val tint = metric.tint(p)
        val inSpan = points[metric.key].orEmpty().filter { it.at >= from }
        val bs = if (metric.bars) bars(metric.key) else emptyList()
        val (headline, caption) = summaryOf(metric, inSpan, bs, days)
        val csh = RoundedCornerShape(sc.radius)
        Column(Modifier.fillMaxWidth().clip(csh).background(p.card).border(1.dp, p.line, csh).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OptionIcon(metric.icon, tint, 44.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(metric.name, fontSize = sc.body, fontWeight = FontWeight.SemiBold, color = p.ink)
                    Text(caption, fontSize = sc.small * 0.88f, color = p.inkSoft)
                }
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(headline, fontSize = if (headline == "No data") sc.headline else sc.title * 1.2f, fontWeight = FontWeight.Bold, color = if (headline == "No data") p.inkSoft else p.ink)
                Spacer(Modifier.width(6.dp))
                if (headline != "No data") Text(metric.unit, fontSize = sc.body, color = p.inkSoft, modifier = Modifier.padding(bottom = 6.dp))
            }
            if (headline == "No data") Text(if (metric.bars) "Nothing noted in this time." else "No ${metric.name.lowercase()} readings in this time. Add one from BP & sugar.", fontSize = sc.body, color = p.inkSoft)
            else if (metric.bars) BarChart(bs, tint, metric.unit, days) else LineChart(inSpan, from, now, tint, metric.normal, days)
        }

        // ── nutrition: the verdict and three numbers, the full report one tap away ──
        nutrition?.takeIf { it.loggedDays > 0 || it.weights.isNotEmpty() || it.feeds.isNotEmpty() }?.let { r ->
            SectionHeader("Nutrition", "Last 2 weeks", "Report") { nav.go(com.suryaprakash.medlog.ui.Route.Nutrition) }
            NutritionSummary(r)
        }

        // ── every measure ──
        val (withData, without) = METRICS.partition { has(it) }
        if (withData.isNotEmpty()) {
            SectionHeader("All measures", "Tap one to see its chart", null)
            Group {
                withData.forEachIndexed { i, m ->
                    if (i > 0) GroupLine()
                    MetricRow(m, points[m.key].orEmpty(), daily[m.key].orEmpty(), m.key == chosen) { chosen = m.key }
                }
            }
        }
        if (without.isNotEmpty()) {
            SectionHeader("No data yet", "Add readings from BP & sugar, or Food & water", null)
            Group {
                without.forEachIndexed { i, m ->
                    if (i > 0) GroupLine()
                    MetricRow(m, emptyList(), emptyMap(), m.key == chosen) { chosen = m.key }
                }
            }
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
    return latest to "Latest, ${SimpleDateFormat("d MMMM", Locale.getDefault()).format(Date(last.at))} · range ${fmt1(lo)} to ${fmt1(hi)}"
}

/** One measure in the list: icon, name, latest value, and a small trend line. */
@Composable
private fun MetricRow(m: Metric, pts: List<Point>, daily: Map<LocalDate, Double>, selected: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    val sc = LocalScale.current
    val tint = m.tint(p)
    val latest = when {
        m.bars && daily.isNotEmpty() -> daily.maxBy { it.key }.let { (d, v) -> "${if (m.key == "water" || m.key == "symptoms") v.toInt() else "${v.toInt()}%"}" + if (m.key == "water") " glasses" else if (m.key == "symptoms") " noted" else "" }
        pts.isNotEmpty() -> pts.last().let { if (it.v2 != null) "${it.v.toInt()}/${it.v2.toInt()}" else fmt1(it.v) } + " " + m.unit.substringBefore(" ")
        else -> "No data"
    }
    Row(Modifier.fillMaxWidth().background(if (selected) p.brandSoft.copy(alpha = 0.6f) else Color.Transparent).steady("${m.name}, $latest", onClick = onClick)
        .padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        OptionIcon(m.icon, tint, 44.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(m.name, fontSize = sc.body, fontWeight = FontWeight.SemiBold, color = p.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(latest, fontSize = sc.small, color = if (latest == "No data") p.inkSoft else p.ink, maxLines = 1)
        }
        val series = if (m.bars) daily.entries.sortedBy { it.key }.takeLast(14).map { it.value } else pts.takeLast(14).map { it.v }
        if (series.size >= 2) Canvas(Modifier.width(72.dp).height(32.dp)) {
            val lo = series.min(); val hi = series.max().let { if (it == lo) lo + 1 else it }
            val path = Path()
            series.forEachIndexed { i, v ->
                val o = Offset(i * size.width / (series.size - 1), (size.height - (v - lo) / (hi - lo) * size.height).toFloat())
                if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
            }
            drawPath(path, tint, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
        }
    }
}

private fun dateLabel(t: Long, days: Int) = SimpleDateFormat(if (days > 182) "MMM" else "d MMM", Locale.getDefault()).format(Date(t))

/**
 * A chart area that fits the card when it can, and scrolls sideways when it can't: it opens on the newest end,
 * and the value labels stay put on the right. [content] draws the chart and its dates at the given width.
 */
@Composable
private fun ScrollingChart(minWidth: Dp, hi: String, mid: String, lo: String, content: @Composable (Dp) -> Unit) {
    val p = LocalPalette.current
    val sc = LocalScale.current
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
        val avail = maxWidth - 44.dp
        val w = if (minWidth > avail) minWidth else avail
        val state = androidx.compose.foundation.rememberScrollState()
        LaunchedEffect(w) { state.scrollTo(state.maxValue) }
        Row {
            Box(Modifier.width(avail).horizontalScroll(state)) { Box(Modifier.width(w)) { content(w) } }
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
    ScrollingChart(perDay * days, fmt1(hi), fmt1((hi + lo) / 2), fmt1(lo)) { w ->
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
    val hi = (bars.mapNotNull { it.v }.maxOrNull() ?: 1.0).coerceAtLeast(if (unit == "%") 100.0 else 1.0)
    if (bars.isEmpty()) return
    ScrollingChart(28.dp * bars.size, fmt1(hi), fmt1(hi / 2), "0") { w ->
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
