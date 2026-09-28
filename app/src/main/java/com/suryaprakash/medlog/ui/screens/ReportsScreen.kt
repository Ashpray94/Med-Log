package com.suryaprakash.medlog.ui.screens

import com.suryaprakash.medlog.data.planned
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
    val notes = app.viewDb.notes().between(start - days * DAY, end)
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
    val meds = app.viewDb.medicines().all().associateBy { it.id }
    val doses = app.viewDb.doses().between(start, minOf(end, System.currentTimeMillis())).planned()
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
