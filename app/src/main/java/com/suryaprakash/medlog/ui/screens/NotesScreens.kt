package com.suryaprakash.medlog.ui.screens

import android.media.MediaPlayer
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.LocalDrink
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Sos
import androidx.compose.material.icons.rounded.StickyNote2
import androidx.compose.material3.Icon
import com.suryaprakash.medlog.ui.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.suryaprakash.medlog.MedLogApp
import com.suryaprakash.medlog.data.DAY
import com.suryaprakash.medlog.data.DocLine
import com.suryaprakash.medlog.data.DoseStatus
import com.suryaprakash.medlog.data.Kind
import com.suryaprakash.medlog.data.Note
import com.suryaprakash.medlog.medlog
import com.suryaprakash.medlog.nlu.Fact
import com.suryaprakash.medlog.nlu.factsFromJson
import com.suryaprakash.medlog.pictogram.SpriteIcon
import com.suryaprakash.medlog.ui.BigButton
import com.suryaprakash.medlog.ui.BigField
import com.suryaprakash.medlog.ui.Body
import com.suryaprakash.medlog.ui.Card
import com.suryaprakash.medlog.ui.Hint
import com.suryaprakash.medlog.ui.IconTile
import com.suryaprakash.medlog.ui.LevelMark
import com.suryaprakash.medlog.ui.LocalPalette
import com.suryaprakash.medlog.ui.LocalScale
import com.suryaprakash.medlog.ui.Nav
import com.suryaprakash.medlog.ui.Route
import com.suryaprakash.medlog.ui.RowActions
import com.suryaprakash.medlog.ui.Screen
import com.suryaprakash.medlog.ui.Segmented
import com.suryaprakash.medlog.ui.Title
import com.suryaprakash.medlog.ui.Tone
import com.suryaprakash.medlog.ui.UndoHost
import com.suryaprakash.medlog.ui.Announce
import com.suryaprakash.medlog.ui.steady
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.Locale

fun dayLabel(t: Long): String {
    val d = Instant.ofEpochMilli(t).atZone(ZoneId.systemDefault()).toLocalDate()
    return dateLabel(d)
}

/** "Today", or the full date: "Thursday, 24 September" (with the year if it is not this year). Never "yesterday". */
fun dateLabel(d: LocalDate): String = if (d == LocalDate.now()) "Today" else fullDate(d)

/** "Thursday, 24 September", always, with the year when it is not this year. */
fun fullDate(d: LocalDate): String {
    val pattern = if (d.year == LocalDate.now().year) "EEEE, d MMMM" else "EEEE, d MMMM yyyy"
    return SimpleDateFormat(pattern, Locale.ENGLISH).format(Date.from(d.atStartOfDay(ZoneId.systemDefault()).toInstant()))
}

/** Lower-case the first word only when it is an ordinary word ("Earlier…"), never a day or month name. */
fun softStart(w: String): String = if (w.startsWith("Earlier") || w.startsWith("A ")) w.replaceFirstChar(Char::lowercase) else w

/** "10:06 PM". */
fun timeLabel(t: Long): String = SimpleDateFormat("h:mm a", Locale.ENGLISH).format(Date(t))

fun localDate(t: Long): LocalDate = Instant.ofEpochMilli(t).atZone(ZoneId.systemDefault()).toLocalDate()

/**
 * The "when did it start?" answer, turned from words that go stale ("just now", "yesterday") into a real time or date,
 * counted from when the note was made.
 */
fun startedWords(value: String, at: Long, alwaysDate: Boolean = false): String {
    val d = localDate(at)
    val today = d == LocalDate.now() && !alwaysDate
    val onDay = if (today) "today" else "on ${fullDate(d)}"
    return when (value.trim().lowercase()) {
        "just now" -> if (today) timeLabel(at) else "${timeLabel(at)}, ${fullDate(d)}"
        "earlier today", "today" -> "Earlier $onDay, before ${timeLabel(at)}"
        "yesterday" -> fullDate(d.minusDays(1))
        "a few days ago", "a few days" -> "A few days before ${fullDate(d)}"
        "a week or more" -> "A week or more before ${fullDate(d)}"
        else -> value.replaceFirstChar(Char::uppercase)
    }
}

/** The few words that matter for one symptom note: "Bad · left knee · in the bone or joint". */
fun shortDetail(app: MedLogApp, facts: Map<String, Fact>, at: Long? = null): String {
    val parts = ArrayList<String>()
    (facts["count"]?.value as? Number)?.let { parts += "${it.toInt()} times" }
    (facts["severity"]?.value as? Number)?.let { parts += app.describe.severityWord(it.toInt()) }
    facts["site"]?.value?.toString()?.let { parts += it.lowercase() }
    facts["depth"]?.value?.toString()?.let { parts += it }
    (facts["character"]?.value as? List<*>)?.takeIf { it.isNotEmpty() }?.let { parts += it.joinToString("/") }
    facts["started"]?.value?.toString()?.takeIf { it.lowercase() != "just now" }?.let { if (parts.size < 3) parts += "started " + (if (at != null) startedWords(it, at) else it).let(::softStart) }
    if (facts["better"]?.value == true) parts += "better now"
    return parts.take(4).joinToString(" · ").replaceFirstChar(Char::uppercase)
}

// ───────────────────────── History ─────────────────────────

/** History (plan 5, screen 8): large day-by-day navigation, or everything about one problem. */
@Composable
fun NotesScreen(nav: Nav) {
    val ctx = LocalContext.current
    val app = ctx.medlog
    val p = LocalPalette.current
    val sc = LocalScale.current
    var byProblem by remember { mutableStateOf(false) }
    var day by remember { mutableStateOf(LocalDate.now()) }
    val zone = ZoneId.systemDefault()
    val from = day.atStartOfDay(zone).toInstant().toEpochMilli()
    val notes by app.db.notes().betweenFlow(from, from + DAY).collectAsState(emptyList())
    val doses by app.db.doses().betweenFlow(from, from + DAY).collectAsState(emptyList())
    val since = remember { System.currentTimeMillis() - 90 * DAY }
    val allSymptoms by app.db.notes().symptomsSinceFlow(since).collectAsState(emptyList())
    // which days in the strip have something
    val daysWith = remember(allSymptoms) { allSymptoms.map { Instant.ofEpochMilli(it.occurredAt).atZone(zone).toLocalDate() }.toSet() }

    val symptomNotes = notes.filter { it.kind == Kind.SYMPTOM }.sortedBy { it.occurredAt }
    val speak = if (byProblem) "Your problems from the last 3 months. Tap one to see every time you noted it."
        else "${dateLabel(day)}. " + if (symptomNotes.isEmpty()) "Nothing noted." else symptomNotes.joinToString(". ") { app.catalogue.problem(it.problemId)?.label ?: it.text }

    Screen("History", speak, onHome = { nav.home() }, trailing = {
        Box(Modifier.size(52.dp).clip(CircleShape).background(p.card).steady("Find a note") { nav.go(Route.Search) }, contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Search, null, tint = p.inkSoft, modifier = Modifier.size(26.dp))
        }
    }) {
        Segmented(listOf("By day", "By problem"), if (byProblem) 1 else 0) { byProblem = it == 1 }
        if (!byProblem) {
            DayNavigator(day, onPrev = { day = day.minusDays(1) }, onNext = { if (day.isBefore(LocalDate.now())) day = day.plusDays(1) })
            WeekStrip(day, daysWith) { day = it }
            DaySummary(app, notes, doses.count { it.status == DoseStatus.TAKEN }, doses.size, onNote = { nav.go(Route.NoteDetail(it)) })
        } else {
            val grouped = allSymptoms.filter { it.problemId != null }.groupBy { it.problemId!! }.entries.sortedByDescending { e -> e.value.maxOf { it.occurredAt } }
            if (grouped.isEmpty()) Empty("Nothing noted in the last 3 months.")
            grouped.forEach { (pid, list) ->
                val label = app.catalogue.problem(pid)?.label ?: pid
                val times = list.sumOf { it.count ?: 1 }
                HistoryRow(
                    icon = { SpriteIcon(pid, 48.dp) },
                    title = label,
                    sub = "$times ${if (times == 1) "time" else "times"}. Last noted ${dayLabel(list.maxOf { it.occurredAt }).replaceFirstChar(Char::lowercase).let { if (it == "today") it else fullDate(localDate(list.maxOf { n -> n.occurredAt })) }}",
                    level = list.maxByOrNull { levelRank(it.triage) }?.triage ?: "GREEN",
                ) { nav.go(Route.ProblemHistory(pid)) }
            }
        }
    }
}

private fun levelRank(l: String) = when (l) { "RED" -> 2; "AMBER" -> 1; else -> 0 }

@Composable
private fun DayNavigator(day: LocalDate, onPrev: () -> Unit, onNext: () -> Unit) {
    val p = LocalPalette.current
    val sc = LocalScale.current
    val canNext = day.isBefore(LocalDate.now())
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        ArrowButton(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Go to ${fullDate(day.minusDays(1))}", true, onPrev)
        val at = Date.from(day.atStartOfDay(ZoneId.systemDefault()).toInstant())
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (day == LocalDate.now()) "Today" else SimpleDateFormat("d MMMM", Locale.ENGLISH).format(at), fontSize = sc.headline, fontWeight = FontWeight.Bold, color = p.ink, textAlign = TextAlign.Center)
            Text(SimpleDateFormat(if (day == LocalDate.now()) "EEEE, d MMMM yyyy" else "EEEE, yyyy", Locale.ENGLISH).format(at), fontSize = sc.small, color = p.inkSoft, textAlign = TextAlign.Center)
        }
        ArrowButton(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Go to ${fullDate(day.plusDays(1))}", canNext, onNext)
    }
}

@Composable
private fun ArrowButton(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    val sc = LocalScale.current
    Box(Modifier.size(sc.target).clip(CircleShape).background(if (enabled) p.card else Color.Transparent).steady(label, enabled, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = if (enabled) p.brand else p.line, modifier = Modifier.size(36.dp))
    }
}

@Composable
private fun WeekStrip(day: LocalDate, daysWith: Set<LocalDate>, onPick: (LocalDate) -> Unit) {
    val p = LocalPalette.current
    val sc = LocalScale.current
    val today = LocalDate.now()
    val end = if (day.plusDays(3).isAfter(today)) today else day.plusDays(3)
    val days = (6 downTo 0).map { end.minusDays(it.toLong()) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        days.forEach { d ->
            val on = d == day
            Column(
                Modifier.weight(1f).height(if (sc.big) 64.dp else 56.dp).clip(RoundedCornerShape(14.dp)).background(if (on) p.brand else p.card).steady(dateLabel(d)) { onPick(d) },
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
            ) {
                Text("${d.dayOfMonth}", fontSize = sc.body, fontWeight = FontWeight.Bold, color = if (on) Color.White else p.ink)
                Box(Modifier.padding(top = 3.dp).size(6.dp).clip(CircleShape).background(if (d in daysWith) (if (on) Color.White else p.amber) else Color.Transparent))
            }
        }
    }
}

/** One day: what was felt, then a single line each for medicines, water and food. */
@Composable
private fun DaySummary(app: MedLogApp, notes: List<Note>, taken: Int, due: Int, onNote: (Long) -> Unit) {
    val p = LocalPalette.current
    val symptoms = notes.filter { it.kind == Kind.SYMPTOM }.sortedBy { it.occurredAt }
    val water = notes.filter { it.kind == Kind.WATER }.sumOf { it.count ?: 1 }
    val food = notes.filter { it.kind == Kind.FOOD }
    val readings = notes.filter { it.kind == Kind.READING }
    val other = notes.filter { it.kind in setOf(Kind.SOS, Kind.VISIT, Kind.MED_TAKEN, Kind.QUESTION, Kind.IMPORTED) }
    if (symptoms.isEmpty() && water == 0 && food.isEmpty() && due == 0 && readings.isEmpty() && other.isEmpty()) { Empty("Nothing noted on this day."); return }

    if (symptoms.isNotEmpty()) {
        Title("How I felt")
        symptoms.forEach { n ->
            val facts = factsFromJson(n.details)
            HistoryRow(
                icon = { SpriteIcon(n.problemId, 48.dp) },
                title = app.catalogue.problem(n.problemId)?.label ?: n.text,
                sub = listOf(timeLabel(n.occurredAt), shortDetail(app, facts, n.occurredAt)).filter { it.isNotBlank() }.joinToString(" · "),
                level = n.triage,
            ) { onNote(n.id) }
        }
    }
    val daily = ArrayList<@Composable () -> Unit>()
    if (due > 0) daily += { HistoryRow({ IconTile(Icons.Rounded.Medication, p.tintOrange, 48.dp) }, "Medicines", "$taken of $due taken", level = if (taken < due) "AMBER" else "GREEN", showChevron = false) {} }
    if (water > 0) daily += { HistoryRow({ IconTile(Icons.Rounded.LocalDrink, p.tintBlue, 48.dp) }, "Water", "$water ${if (water == 1) "glass" else "glasses"}", showChevron = false) {} }
    if (food.isNotEmpty()) daily += { HistoryRow({ IconTile(Icons.Rounded.Restaurant, p.tintGreen, 48.dp) }, "Food", food.joinToString(", ") { it.transcript ?: "a meal" }, showChevron = false) {} }
    readings.forEach { r -> daily += { HistoryRow({ IconTile(Icons.Rounded.MonitorHeart, p.tintPink, 48.dp) }, r.text, timeLabel(r.occurredAt), showChevron = false) {} } }
    other.forEach { n -> daily += { HistoryRow({ IconTile(if (n.kind == Kind.SOS) Icons.Rounded.Sos else Icons.Rounded.StickyNote2, if (n.kind == Kind.SOS) p.red else p.tintTeal, 48.dp) }, n.text, timeLabel(n.occurredAt)) { onNote(n.id) } } }
    if (daily.isNotEmpty()) { Title("Medicines, food and more"); daily.forEach { it() } }
}

@Composable
private fun HistoryRow(icon: @Composable () -> Unit, title: String, sub: String, time: String? = null, level: String = "GREEN", showChevron: Boolean = true, onClick: () -> Unit) {
    val p = LocalPalette.current
    val sc = LocalScale.current
    Card(padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 12.dp), onClick = if (showChevron) onClick else null, label = "$title. $sub") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            icon()
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(title, fontSize = sc.body * 1.05f, fontWeight = FontWeight.SemiBold, color = p.ink, modifier = Modifier.weight(1f))
                    if (level != "GREEN") { Spacer(Modifier.width(8.dp)); LevelMark(level, withWord = false) }
                }
                val line = listOfNotNull(time, sub.ifBlank { null }).joinToString(" · ")
                if (line.isNotBlank()) Text(line, fontSize = sc.small, color = p.inkSoft)
            }
            if (showChevron) Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = p.inkSoft.copy(alpha = 0.5f), modifier = Modifier.size(26.dp))
        }
    }
}

@Composable
private fun Empty(text: String) {
    val p = LocalPalette.current
    Box(Modifier.fillMaxWidth().heightIn(min = 140.dp), contentAlignment = Alignment.Center) { Text(text, color = p.inkSoft, fontSize = LocalScale.current.body, textAlign = TextAlign.Center) }
}

/** Every time one problem was noted: a 14-day chart, then a timeline grouped by date. */
@Composable
fun ProblemHistoryScreen(nav: Nav, problemId: String) {
    val ctx = LocalContext.current
    val app = ctx.medlog
    val p = LocalPalette.current
    val sc = LocalScale.current
    val since = remember { System.currentTimeMillis() - 90 * DAY }
    val all by app.db.notes().symptomsSinceFlow(since).collectAsState(emptyList())
    val list = all.filter { it.problemId == problemId }.sortedByDescending { it.occurredAt }
    val label = app.catalogue.problem(problemId)?.label ?: problemId
    val days = (13 downTo 0).map { LocalDate.now().minusDays(it.toLong()) }
    val counts = days.map { d -> list.filter { localDate(it.occurredAt) == d }.sumOf { it.count ?: 1 } }
    val total = list.sumOf { it.count ?: 1 }
    Screen(label, "$label: noted $total times in the last 3 months.", onHome = { nav.home() }, onBack = { nav.back() }) {
        Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SpriteIcon(problemId, 56.dp); Spacer(Modifier.width(14.dp))
                Column {
                    Text("$total ${if (total == 1) "time" else "times"}", fontSize = sc.headline, fontWeight = FontWeight.Bold, color = p.ink)
                    Text("in the last 3 months", fontSize = sc.small, color = p.inkSoft)
                }
            }
            val max = (counts.maxOrNull() ?: 0).coerceAtLeast(1)
            Canvas(Modifier.fillMaxWidth().height(72.dp)) {
                val w = size.width / days.size
                counts.forEachIndexed { i, c ->
                    val h = if (c == 0) 4f else (size.height - 4f) * c / max
                    drawRoundRect(if (c == 0) p.line else p.amber, Offset(i * w + w * 0.18f, size.height - h), Size(w * 0.64f, h), CornerRadius(6f))
                }
            }
            Row(Modifier.fillMaxWidth()) {
                Text(SimpleDateFormat("d MMMM", Locale.ENGLISH).format(Date.from(days.first().atStartOfDay(ZoneId.systemDefault()).toInstant())), fontSize = sc.small, color = p.inkSoft, modifier = Modifier.weight(1f))
                Text("Today", fontSize = sc.small, color = p.inkSoft)
            }
        }
        BigButton("Tell how it is now", tone = Tone.PRIMARY, onClick = { nav.go(Route.Tell(problemId)) })
        if (list.isEmpty()) return@Screen
        Title("Timeline")
        Card(padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
            val byDay = list.groupBy { localDate(it.occurredAt) }.toList()
            byDay.forEachIndexed { di, (d, notes) ->
                TimelineDate(dateLabel(d), first = di == 0)
                notes.forEachIndexed { ni, n ->
                    TimelineEntry(
                        time = timeLabel(n.occurredAt),
                        lines = detailLines(app, factsFromJson(n.details), n.occurredAt),
                        level = n.triage,
                        last = di == byDay.lastIndex && ni == notes.lastIndex,
                    ) { nav.go(Route.NoteDetail(n.id)) }
                }
            }
        }
    }
}

/** The main facts of one note as short, plain lines: "How bad: very bad, 8 out of 10". */
fun detailLines(app: MedLogApp, facts: Map<String, Fact>, at: Long): List<String> {
    val out = ArrayList<String>()
    (facts["count"]?.value as? Number)?.let { out += "${it.toInt()} ${if (it.toInt() == 1) "time" else "times"}" }
    (facts["severity"]?.value as? Number)?.let { out += "How bad: ${app.describe.severityWord(it.toInt()).lowercase()}, ${it.toInt()} out of 10" }
    listOfNotNull(facts["site"]?.value?.toString(), facts["depth"]?.value?.toString()).takeIf { it.isNotEmpty() }?.let { out += "Where: " + it.joinToString(", ").replaceFirstChar(Char::lowercase) }
    (facts["character"]?.value as? List<*>)?.takeIf { it.isNotEmpty() }?.let { out += "Feels: " + it.joinToString(", ").lowercase() }
    facts["started"]?.value?.toString()?.takeIf { it.lowercase() != "just now" }?.let { out += "Started: " + startedWords(it, at) }
    if (facts["better"]?.value == true) out += "Getting better"
    return out.ifEmpty { listOf("No details") }
}

/** A date on the timeline: a bigger dot and the full date. */
@Composable
private fun TimelineDate(text: String, first: Boolean) {
    val p = LocalPalette.current
    val sc = LocalScale.current
    Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min)) {
        Box(Modifier.width(28.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
            if (!first) Box(Modifier.align(Alignment.TopCenter).width(2.dp).fillMaxHeight(0.5f).background(p.line))
            Box(Modifier.align(Alignment.BottomCenter).width(2.dp).fillMaxHeight(0.5f).background(p.line))
            Box(Modifier.size(14.dp).clip(CircleShape).background(p.brand))
        }
        Spacer(Modifier.width(12.dp))
        Text(text, fontSize = sc.body, fontWeight = FontWeight.Bold, color = p.ink, modifier = Modifier.padding(vertical = 12.dp))
    }
}

/** One note on the timeline: the time, then its details, one per line. */
@Composable
private fun TimelineEntry(time: String, lines: List<String>, level: String, last: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    val sc = LocalScale.current
    Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min)) {
        Box(Modifier.width(28.dp).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.width(2.dp).then(if (last) Modifier.height(24.dp) else Modifier.fillMaxHeight()).background(p.line))
            Box(Modifier.padding(top = 18.dp).size(8.dp).clip(CircleShape).background(p.inkSoft.copy(alpha = 0.5f)))
        }
        Spacer(Modifier.width(12.dp))
        Column(
            Modifier.weight(1f).padding(vertical = 6.dp).clip(RoundedCornerShape(14.dp)).background(p.paper)
                .steady("$time. ${lines.joinToString(". ")}", onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(time, fontSize = sc.body, fontWeight = FontWeight.SemiBold, color = p.ink, modifier = Modifier.weight(1f))
                if (level != "GREEN") LevelMark(level, withWord = false)
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = p.inkSoft.copy(alpha = 0.5f), modifier = Modifier.size(24.dp))
            }
            lines.forEach { Text(it, fontSize = sc.small, color = p.inkSoft) }
        }
    }
}

// ───────────────────────── one note ─────────────────────────

@Composable
fun NoteRow(n: Note, onClick: () -> Unit) {
    val ctx = LocalContext.current
    val app = ctx.medlog
    val problem = app.catalogue.problem(n.problemId)
    HistoryRow(
        icon = { if (problem != null) SpriteIcon(problem.id, 48.dp) else IconTile(Icons.Rounded.StickyNote2, LocalPalette.current.tintTeal, 48.dp) },
        title = problem?.label ?: n.text, sub = if (problem != null) shortDetail(app, factsFromJson(n.details), n.occurredAt) else "",
        time = "${dayLabel(n.occurredAt)} at ${timeLabel(n.occurredAt)}", level = n.triage, onClick = onClick,
    )
}

@Composable
fun NoteDetailScreen(nav: Nav, id: Long) {
    val ctx = LocalContext.current
    val app = ctx.medlog
    val p = LocalPalette.current
    val sc = LocalScale.current
    val scope = rememberCoroutineScope()
    val note by app.db.notes().flow(id).collectAsState(null)
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    DisposableEffect(Unit) { onDispose { player?.release() } }
    val n = note ?: return
    val problem = app.catalogue.problem(n.problemId)
    val facts = factsFromJson(if (n.kind == Kind.SYMPTOM) n.details else null)
    val rows = if (n.kind == Kind.SYMPTOM) summaryRows(app, facts, n.occurredAt) else emptyList()
    Screen(problem?.label ?: n.text.substringBefore(":"), n.text, onHome = { nav.home() }, onBack = { nav.back() }, subtitle = "${dayLabel(n.occurredAt)} at ${timeLabel(n.occurredAt)}") {
        Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (problem != null) { SpriteIcon(problem.id, 64.dp); Spacer(Modifier.width(14.dp)) }
                Column(Modifier.weight(1f)) {
                    Text(problem?.label ?: n.text, fontSize = sc.headline, fontWeight = FontWeight.Bold, color = p.ink)
                    if (n.triage != "GREEN") LevelMark(n.triage)
                }
            }
            if (rows.isEmpty() && n.kind != Kind.SYMPTOM) Body(n.text)
            rows.forEach { (k, v) ->
                Box(Modifier.fillMaxWidth().height(1.dp).background(p.line))
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                    Text(k, fontSize = sc.small, color = p.inkSoft, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(118.dp).alignByBaseline())
                    Text(v, fontSize = sc.body, color = p.ink, modifier = Modifier.weight(1f).alignByBaseline())
                }
            }
        }
        n.transcript?.takeIf { it.isNotBlank() }?.let { Card { Hint("In your words"); Body("“$it”") } }
        n.audioPath?.let { path ->
            if (java.io.File(path).exists()) BigButton("Play my voice", tone = Tone.QUIET, icon = Icons.Rounded.PlayArrow, onClick = {
                player?.release(); player = MediaPlayer().apply { setDataSource(path); prepare(); start() }
            })
        }
        if (problem != null) BigButton("Add more about this", tone = Tone.QUIET, onClick = { nav.go(Route.Tell(noteId = n.id)) })
        RowActions(what = problem?.label ?: n.text.substringBefore(":"),
            onEdit = { nav.go(Route.Tell(noteId = n.id)) },
            onDelete = {
                scope.launch {
                    app.repo.remove(listOf(n.id)); app.refreshWidgets()
                    UndoHost.show("Note removed.") { scope.launch { app.repo.restore(listOf(n.id)); app.refreshWidgets() } }
                    Announce.done(ctx, null, "removed note", "delete", n.id)
                    nav.back()
                }
            }
        )
    }
}

@Composable
fun RemovedScreen(nav: Nav) {
    val ctx = LocalContext.current
    val app = ctx.medlog
    val scope = rememberCoroutineScope()
    val removed by app.db.notes().removedFlow().collectAsState(emptyList())
    Screen("Removed notes", "Removed notes are kept for 30 days. Tap Bring back to restore one.", onHome = { nav.home() }, onBack = { nav.back() }) {
        if (removed.isEmpty()) Empty("Nothing removed.")
        removed.forEach { n ->
            Card {
                Body("${dayLabel(n.occurredAt)} · ${n.text}")
                BigButton("Bring back", tone = Tone.QUIET, icon = Icons.Rounded.Restore, onClick = { scope.launch { app.repo.restore(listOf(n.id)); app.refreshWidgets() } })
            }
        }
    }
}

/** Search: returns only what is stored (notes and imported report lines), never generated text. */
@Composable
fun SearchScreen(nav: Nav) {
    val ctx = LocalContext.current
    val app = ctx.medlog
    val scope = rememberCoroutineScope()
    var q by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf<List<Note>>(emptyList()) }
    var docs by remember { mutableStateOf<List<DocLine>>(emptyList()) }
    Screen("Find a note", "Type a word, like dizzy or BP.", onHome = { nav.home() }, onBack = { nav.back() }) {
        BigField("Word to find", q, { v -> q = v; scope.launch { val (a, b) = app.repo.search(v); notes = a; docs = b } })
        if (q.length >= 2 && notes.isEmpty() && docs.isEmpty()) Empty("Nothing found for “$q”.")
        notes.forEach { n -> NoteRow(n) { nav.go(Route.NoteDetail(n.id)) } }
        if (docs.isNotEmpty()) { Title("From your old reports"); docs.forEach { d -> Card { Hint(d.source); Body(d.content) } } }
        BigButton("Removed notes", tone = Tone.SECONDARY, icon = Icons.Rounded.Restore, onClick = { nav.go(Route.Removed) })
    }
}
