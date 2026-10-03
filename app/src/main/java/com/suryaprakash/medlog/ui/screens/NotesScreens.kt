package com.suryaprakash.medlog.ui.screens
import com.suryaprakash.medlog.data.planned
import androidx.compose.material.icons.rounded.Wc
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.border
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.ExpandLess

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
import com.suryaprakash.medlog.ui.Screen
import com.suryaprakash.medlog.ui.Segmented
import com.suryaprakash.medlog.ui.Title
import com.suryaprakash.medlog.ui.Tone
import com.suryaprakash.medlog.ui.UndoHost
import com.suryaprakash.medlog.ui.steady
import com.suryaprakash.medlog.ui.lift
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
    return SimpleDateFormat(pattern, com.suryaprakash.medlog.speech.I18n.locale).format(Date.from(d.atStartOfDay(ZoneId.systemDefault()).toInstant()))
}

/** Lower-case the first word only when it is an ordinary word ("Earlier…"), never a day or month name. */
fun softStart(w: String): String = if (w.startsWith("Earlier") || w.startsWith("A ")) w.replaceFirstChar(Char::lowercase) else w

/** "10:06 PM". */
fun timeLabel(t: Long): String = SimpleDateFormat("h:mm a", com.suryaprakash.medlog.speech.I18n.locale).format(Date(t))

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
    val notes by app.viewDb.notes().betweenFlow(from, from + DAY).collectAsState(emptyList())
    val allDoses by app.viewDb.doses().betweenFlow(from, from + DAY).collectAsState(emptyList())
    val medForms by app.viewDb.medicines().activeFlow().collectAsState(emptyList())
    // feeds are food, not medicine: counted in their own group
    val feedIds = medForms.filter { it.form == "feed" }.map { it.id }.toSet()
    val doses = allDoses.planned().filter { it.medicineId !in feedIds }
    val feedDoses = allDoses.filter { it.medicineId in feedIds }
    // "Watch" only for something overdue, never for doses still to come later today
    fun late(list: List<com.suryaprakash.medlog.data.Dose>) = list.any { it.status == DoseStatus.MISSED || (it.status != DoseStatus.TAKEN && it.status != DoseStatus.SKIPPED && it.scheduledAt < System.currentTimeMillis() - 30 * 60_000) }
    val since = remember { System.currentTimeMillis() - 90 * DAY }
    val allSymptoms by app.viewDb.notes().symptomsSinceFlow(since).collectAsState(emptyList())
    // which days in the strip have something
    val daysWith = remember(allSymptoms) { allSymptoms.map { Instant.ofEpochMilli(it.occurredAt).atZone(zone).toLocalDate() }.toSet() }

    val symptomNotes = notes.filter { it.kind == Kind.SYMPTOM }.sortedBy { it.occurredAt }
    val speak = if (byProblem) "Your problems from the last 3 months. Tap one to see every time you noted it."
        else "${dateLabel(day)}. " + if (symptomNotes.isEmpty()) "Nothing noted." else symptomNotes.joinToString(". ") { app.catalogue.problem(it.problemId)?.label ?: it.text }

    Screen("History", speak, onHome = { nav.home() }, onBack = { nav.back() }, trailing = {
        Box(Modifier.size(52.dp).clip(CircleShape).background(p.card).steady("Find a note") { nav.go(Route.Search) }, contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Search, null, tint = p.inkSoft, modifier = Modifier.size(26.dp))
        }
    }) {
        Segmented(listOf("By day", "By problem"), if (byProblem) 1 else 0) { byProblem = it == 1 }
        if (!byProblem) {
            DayNavigator(day, onPrev = { day = day.minusDays(1) }, onNext = { if (day.isBefore(LocalDate.now())) day = day.plusDays(1) })
            WeekStrip(day, daysWith) { day = it }
            DaySummary(app, notes, doses.count { it.status == DoseStatus.TAKEN }, doses.size, feedDoses.planned().count { it.status == DoseStatus.TAKEN }, feedDoses.planned().size, onNote = { nav.go(Route.NoteDetail(it)) },
                medsLate = late(doses), feedsLate = late(feedDoses), medDoses = doses.mapNotNull { d -> medForms.firstOrNull { it.id == d.medicineId }?.let { it.name to d } },
                onGo = { nav.go(it) })
        } else {
            val grouped = allSymptoms.filter { it.problemId != null }.groupBy { it.problemId!! }.filterValues { com.suryaprakash.medlog.data.Occurrences.total(it) > 0 }.entries.sortedByDescending { e -> e.value.maxOf { it.occurredAt } }
            if (grouped.isEmpty()) Empty("Nothing noted in the last 3 months.")
            grouped.forEach { (pid, list) ->
                val label = app.catalogue.problem(pid)?.label ?: pid
                val times = com.suryaprakash.medlog.data.Occurrences.total(list)
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
            Text(if (day == LocalDate.now()) "Today" else SimpleDateFormat("d MMMM", com.suryaprakash.medlog.speech.I18n.locale).format(at), fontSize = sc.headline, fontWeight = FontWeight.Bold, color = p.ink, textAlign = TextAlign.Center)
            Text(SimpleDateFormat(if (day == LocalDate.now()) "EEEE, d MMMM yyyy" else "EEEE, yyyy", com.suryaprakash.medlog.speech.I18n.locale).format(at), fontSize = sc.small, color = p.inkSoft, textAlign = TextAlign.Center)
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
private fun DaySummary(app: MedLogApp, notes: List<Note>, taken: Int, due: Int, fed: Int, feeds: Int, onNote: (Long) -> Unit, medsLate: Boolean = false, feedsLate: Boolean = false,
                       medDoses: List<Pair<String, com.suryaprakash.medlog.data.Dose>> = emptyList(), onGo: (Route) -> Unit = {}) {
    val p = LocalPalette.current
    val sc = com.suryaprakash.medlog.ui.LocalScale.current
    val symptoms = notes.filter { it.kind == Kind.SYMPTOM }.sortedBy { it.occurredAt }
    val water = notes.filter { it.kind == Kind.WATER }
    val food = notes.filter { it.kind == Kind.FOOD }.sortedBy { it.occurredAt }
    val readings = notes.filter { it.kind == Kind.READING }.sortedBy { it.occurredAt }
    val output = notes.filter { it.kind == Kind.OUTPUT }.sortedBy { it.occurredAt }
    val other = notes.filter { it.kind in setOf(Kind.SOS, Kind.VISIT, Kind.MED_TAKEN, Kind.QUESTION, Kind.IMPORTED) }
    if (symptoms.isEmpty() && water.isEmpty() && food.isEmpty() && due == 0 && feeds == 0 && readings.isEmpty() && other.isEmpty() && output.isEmpty()) { Empty("Nothing noted on this day."); return }
    // one group per kind of thing; open a group to see each entry
    var open by remember { mutableStateOf<String?>(null) }
    fun toggle(k: String) { open = if (open == k) null else k }
    var doseSheet by remember { mutableStateOf<Pair<String, com.suryaprakash.medlog.data.Dose>?>(null) }
    doseSheet?.let { (name, d) -> DoseAfterSheet(name, d, onDismiss = { doseSheet = null }) }
    fun times(list: List<Note>) = list.joinToString(", ") { timeLabel(it.occurredAt) }

    // every time in the same column, as wide as the widest time ("12:59 PM") in this text size, so the times and
    // the names line up down the list and on their first line
    val timeStyle = androidx.compose.ui.text.TextStyle(fontSize = sc.body, fontWeight = FontWeight.SemiBold)
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val timeWidth = remember(sc.body, com.suryaprakash.medlog.speech.I18n.lang) {
        val widest = listOf(0L, 12 * 3600_000L + 59 * 60_000L, 22 * 3600_000L + 59 * 60_000L).maxOf { t ->
            measurer.measure(timeLabel(java.time.LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() + t), timeStyle).size.width
        }
        with(density) { widest.toDp() } + 16.dp
    }

    // each entry in a group is a step on a timeline: the dot, the time on top, what it was, what was noted
    @Suppress("UNUSED_VARIABLE") val unusedTime = timeStyle to timeWidth
    @Composable
    fun Entry(n: Note, title: String, sub: String, level: String = "GREEN", last: Boolean = false) {
        com.suryaprakash.medlog.ui.TimelineRow(com.suryaprakash.medlog.ui.TimelineItem(timeLabel(n.occurredAt), title, sub.ifBlank { null },
            mark = when (level) { "RED" -> p.red; "AMBER" -> p.amber; else -> null }, onClick = { onNote(n.id) }), last,
            trailing = if (level != "GREEN") { { Box(Modifier.padding(top = 4.dp)) { LevelMark(level, withWord = false) } } } else null)
    }

    if (symptoms.isNotEmpty()) {
        com.suryaprakash.medlog.ui.SectionHeader("How I felt", "${symptoms.size} noted", null)
        symptoms.groupBy { it.problemId ?: it.text }.forEach { (pid, list) ->
            val label = app.catalogue.problem(pid)?.label ?: list.first().text
            val worst = list.maxByOrNull { levelRank(it.triage) }?.triage ?: "GREEN"
            HistoryGroup(label, "${com.suryaprakash.medlog.data.ReportIntegrity.countWords(list)} · ${times(list)}", { SpriteIcon(pid, 44.dp) }, worst, open == "s$pid", { toggle("s$pid") }) {
                list.forEachIndexed { i, n -> val lastE = i == list.lastIndex; Entry(n, label, shortDetail(app, factsFromJson(n.details), n.occurredAt), n.triage, last = lastE) }
            }
        }
    }

    com.suryaprakash.medlog.ui.SectionHeader("Medicines, food and more", "Tap a group to see each one", null)
    // every group can take you to its own page: Feeds straight away, the others from a line at the foot of the group
    if (feeds > 0) HistoryGroup("Feeds", "$fed of $feeds given", { IconTile(Icons.Rounded.LocalDrink, p.tintPurple, 44.dp) }, if (feedsLate) "AMBER" else "GREEN", false, { onGo(Route.Food) }, expandable = false) {}
    if (due > 0) HistoryGroup("Medicines", "$taken of $due taken", { IconTile(Icons.Rounded.Medication, p.tintOrange, 44.dp) }, if (medsLate) "AMBER" else "GREEN", open == "m", { toggle("m") }) {
        // one line per dose, in time order: when, which medicine, and what happened. Tap one to note it afterwards.
        medDoses.sortedBy { it.second.scheduledAt }.forEach { (name, d) ->
            val status = when (d.status) {
                DoseStatus.TAKEN -> "Taken" + (d.actedAt?.let { " at ${timeLabel(it)}" } ?: "")
                DoseStatus.MISSED -> "Missed"
                DoseStatus.SKIPPED -> if (d.reason == com.suryaprakash.medlog.data.FOOD_INSTEAD) com.suryaprakash.medlog.data.FOOD_INSTEAD_WORDS else "Skipped"
                else -> if (d.scheduledAt > System.currentTimeMillis()) "Later today" else "Not taken yet"
            }
            com.suryaprakash.medlog.ui.TimelineRow(com.suryaprakash.medlog.ui.TimelineItem(timeLabel(d.scheduledAt), name, status,
                mark = when (d.status) { DoseStatus.TAKEN -> p.ok; DoseStatus.MISSED -> p.red; else -> null }, onClick = { doseSheet = name to d }),
                last = d == medDoses.maxByOrNull { it.second.scheduledAt }?.second)
        }
        GoLine("Open Medicines") { onGo(Route.Meds) }
    }
    if (water.isNotEmpty()) {
        val glasses = water.sumOf { it.count ?: 1 }
        HistoryGroup("Water", "$glasses glass${if (glasses == 1) "" else "es"} · ${times(water)}", { IconTile(Icons.Rounded.LocalDrink, p.tintBlue, 44.dp) }, "GREEN", open == "w", { toggle("w") }) {
            water.forEachIndexed { i, n -> val lastE = i == water.lastIndex; Entry(n, "${n.count ?: 1} glass", "", last = lastE) }
            GoLine("Open Food & water") { onGo(Route.Food) }
        }
    }
    if (food.isNotEmpty()) {
        val kcal = food.sumOf { runCatching { org.json.JSONObject(it.details ?: "").optInt("kcal", 0) }.getOrDefault(0) }
        HistoryGroup("Food", "${food.size} meal${if (food.size == 1) "" else "s"}${if (kcal > 0) " · $kcal kcal" else ""}", { IconTile(Icons.Rounded.Restaurant, p.tintGreen, 44.dp) },
            "GREEN", open == "f", { toggle("f") }) {
            food.forEach { n ->
                // the main dish as the name; its amount, what came with it and the calories under it
                val o = runCatching { org.json.JSONObject(n.details ?: "") }.getOrNull()
                val items = o?.optJSONArray("items")?.let { a -> (0 until a.length()).map { a.getJSONObject(it) } }.orEmpty()
                val main = items.firstOrNull { x -> com.suryaprakash.medlog.nutrition.Foods.all.firstOrNull { it.name == x.optString("name") }?.side != true } ?: items.firstOrNull()
                val sides = items.filter { it !== main }.joinToString(", ") { it.optString("name") }
                val k = o?.optInt("kcal", -1) ?: -1
                if (main == null) Entry(n, n.transcript?.ifBlank { null } ?: "Photo of a meal", if (k >= 0) "$k kcal" else "", last = n == food.last())
                else Entry(n, main.optString("name").replaceFirstChar(Char::uppercase),
                    listOfNotNull(main.optString("amount").ifBlank { null }?.takeIf { a -> a.any(Char::isDigit) && listOf("katori", "plate", "cup").none { a.contains(it) } },
                        sides.ifBlank { null }?.let { "with $it" }, if (k >= 0) "$k kcal" else null).joinToString(" · "), last = n == food.last())
            }
            GoLine("Open Food & water") { onGo(Route.Food) }
        }
    }
    if (output.isNotEmpty()) {
        val flagged = output.any { it.triage != "GREEN" }
        HistoryGroup("Toilet and tummy", "${output.size} · ${times(output)}", { IconTile(Icons.Rounded.Wc, p.tintTeal, 44.dp) }, if (flagged) "AMBER" else "GREEN",
            open == "t", { toggle("t") }) {
            output.forEachIndexed { i, n -> val lastE = i == output.lastIndex; Entry(n, n.text.substringBefore(":"), n.text.substringAfter(": ", ""), n.triage, last = lastE) }
            GoLine("Open Toilet and tummy") { onGo(Route.Output()) }
        }
    }
    readings.groupBy { r -> runCatching { org.json.JSONObject(r.details).optString("type") }.getOrDefault("") }.forEach { (type, list) ->
        val name = mapOf("bp" to "Blood pressure", "sugar" to "Sugar", "spo2" to "Oxygen", "temp" to "Temperature", "pulse" to "Pulse", "weight" to "Weight")[type] ?: "Readings"
        HistoryGroup(name, "${list.size} reading${if (list.size == 1) "" else "s"} · last ${list.last().text?.substringAfter(" ")}", { IconTile(Icons.Rounded.MonitorHeart, p.tintPink, 44.dp) },
            "GREEN", open == "r$type", { toggle("r$type") }) {
            list.forEachIndexed { i, n -> val lastE = i == list.lastIndex; Entry(n, n.text ?: "", "", last = lastE) }
            GoLine("Open BP & sugar") { onGo(Route.Readings) }
        }
    }
    other.groupBy { it.kind }.forEach { (kind, list) ->
        val name = when (kind) { Kind.SOS -> "SOS"; Kind.VISIT -> "Doctor visits"; Kind.MED_TAKEN -> "Medicines when needed"; Kind.QUESTION -> "Questions for the doctor"; else -> "From old reports" }
        HistoryGroup(name, "${list.size} · ${times(list)}", { IconTile(if (kind == Kind.SOS) Icons.Rounded.Sos else Icons.Rounded.StickyNote2, if (kind == Kind.SOS) p.red else p.tintTeal, 44.dp) },
            if (kind == Kind.SOS) "RED" else "GREEN", open == "o$kind", { toggle("o$kind") }) {
            list.forEachIndexed { i, n -> val lastE = i == list.lastIndex; Entry(n, n.text ?: "", "", last = lastE) }
        }
    }
}

/** The last line of an opened group: to that kind's own page. */
@Composable
private fun GoLine(text: String, onClick: () -> Unit) {
    val p = LocalPalette.current
    val sc = LocalScale.current
    Row(Modifier.fillMaxWidth().padding(top = 6.dp).heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp)).background(p.fill).steady(text, onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(text, fontSize = sc.body, fontWeight = FontWeight.SemiBold, color = p.ink, modifier = Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = p.inkSoft, modifier = Modifier.size(22.dp))
    }
}

/** A group in the day: an icon, a name and a one-line count; opens to show each entry. Groups with nothing to open don't open. */
@Composable
private fun HistoryGroup(title: String, sub: String, icon: @Composable () -> Unit, level: String, open: Boolean, onToggle: () -> Unit, expandable: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    val p = LocalPalette.current
    val sc = com.suryaprakash.medlog.ui.LocalScale.current
    val sh = RoundedCornerShape(sc.radius)
    Column(Modifier.fillMaxWidth().lift(sh).clip(sh).background(p.card)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).steady("$title. $sub", onClick = onToggle).padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            icon()
            Spacer(Modifier.width(14.dp))
            // the "Watch" mark sits under the name, so it never squeezes it
            // the "Watch" mark sits at the right of the name, so the card keeps its height; the name wraps if it must
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, fontSize = sc.body * 1.05f, fontWeight = FontWeight.SemiBold, color = p.ink, modifier = Modifier.weight(1f, fill = false))
                    if (level != "GREEN") { Spacer(Modifier.width(8.dp)); LevelMark(level, withWord = false) }
                }
                Text(sub, fontSize = sc.small, color = p.inkSoft)
            }
            val turn by androidx.compose.animation.core.animateFloatAsState(if (open) 180f else 0f, androidx.compose.animation.core.tween(260), label = "chevron")
            if (expandable) Icon(Icons.Rounded.ExpandMore, null, tint = p.inkSoft, modifier = Modifier.size(26.dp).graphicsLayer { rotationZ = turn })
            else Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = p.inkSoft, modifier = Modifier.size(26.dp))
        }
        androidx.compose.animation.AnimatedVisibility(open && expandable,
            enter = androidx.compose.animation.expandVertically(androidx.compose.animation.core.tween(260), expandFrom = Alignment.Top) + androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(200, delayMillis = 60)),
            exit = androidx.compose.animation.shrinkVertically(androidx.compose.animation.core.tween(220), shrinkTowards = Alignment.Top) + androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(120))) {
            Column(Modifier.fillMaxWidth().padding(start = 14.dp, end = 10.dp, bottom = 12.dp), content = content)
        }
    }
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
fun ProblemHistoryScreen(nav: Nav, problemId: String, rangeDays: Int = 90) {
    val ctx = LocalContext.current
    val app = ctx.medlog
    val p = LocalPalette.current
    val sc = LocalScale.current
    val zone = ZoneId.systemDefault()
    val since = remember(rangeDays) { LocalDate.now().plusDays(1).minusDays(rangeDays.toLong()).atStartOfDay(zone).toInstant().toEpochMilli() }
    val all by app.viewDb.notes().symptomsSinceFlow(since).collectAsState(emptyList())
    val list = com.suryaprakash.medlog.data.ReportIntegrity.notes(all, since, System.currentTimeMillis() + 1, System.currentTimeMillis()).filter { it.problemId == problemId }.sortedByDescending { it.occurredAt }
    val label = app.catalogue.problem(problemId)?.label ?: problemId
    val days = (rangeDays - 1 downTo 0).map { LocalDate.now().minusDays(it.toLong()) }
    val perDay = com.suryaprakash.medlog.data.ReportIntegrity.reports(list).groupingBy { localDate(it.occurredAt) }.eachCount()
    val counts = days.map { d -> perDay[d] ?: 0 }
    val total = com.suryaprakash.medlog.data.Occurrences.total(list)
    Screen(label, "$label: ${com.suryaprakash.medlog.doctor.notedWords(com.suryaprakash.medlog.data.ReportIntegrity.reports(list).size)} in the selected period.", onHome = { nav.home() }, onBack = { nav.back() }) {
        Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SpriteIcon(problemId, 56.dp); Spacer(Modifier.width(14.dp))
                Column {
                    Text(com.suryaprakash.medlog.doctor.notedWords(com.suryaprakash.medlog.data.ReportIntegrity.reports(list).size), fontSize = sc.headline, fontWeight = FontWeight.Bold, color = p.ink)
                    Text("${dateLabel(days.first())} to ${dateLabel(days.last())}", fontSize = sc.small, color = p.inkSoft)
                }
            }
            Text("Times noted each day", fontSize = sc.small, color = p.inkSoft)
            val max = (counts.maxOrNull() ?: 0).coerceAtLeast(1)
            Canvas(Modifier.fillMaxWidth().height(72.dp)) {
                val w = size.width / days.size
                counts.forEachIndexed { i, c ->
                    val h = if (c == 0) 4f else (size.height - 4f) * c / max
                    drawRoundRect(if (c == 0) p.line else p.amber, Offset(i * w + w * 0.18f, size.height - h), Size(w * 0.64f, h), CornerRadius(6f))
                }
            }
            Row(Modifier.fillMaxWidth()) {
                Text(SimpleDateFormat("d MMMM", com.suryaprakash.medlog.speech.I18n.locale).format(Date.from(days.first().atStartOfDay(ZoneId.systemDefault()).toInstant())), fontSize = sc.small, color = p.inkSoft, modifier = Modifier.weight(1f))
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
                    val f = factsFromJson(n.details)
                    f["pin"]?.value?.toString()?.takeIf { it.isNotBlank() }?.let { pin ->
                        RecordedBodyMap(listOf(1 to pin), mapOf(1 to "$label - ${timeLabel(n.occurredAt)}"), compact = true)
                    }
                    f["note"]?.value?.toString()?.takeIf { it.isNotBlank() }?.let { remark -> Text("Remark: $remark", fontSize = sc.small, color = p.ink, modifier = Modifier.padding(start = 28.dp, bottom = 12.dp)) }
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
    val note by app.viewDb.notes().flow(id).collectAsState(null)
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
        facts["pin"]?.value?.toString()?.takeIf { it.isNotBlank() }?.let { pin ->
            Card { RecordedBodyMap(listOf(1 to pin), mapOf(1 to (problem?.label ?: "Location")), compact = true) }
        }
        n.transcript?.takeIf { it.isNotBlank() }?.let { Card { Hint("In your words"); Body("“$it”") } }
        n.audioPath?.let { path ->
            if (java.io.File(path).exists()) BigButton("Play my voice", tone = Tone.QUIET, icon = Icons.Rounded.PlayArrow, onClick = {
                player?.release(); player = MediaPlayer().apply { setDataSource(path); prepare(); start() }
            })
        }
        if (problem != null) BigButton("Add more about this", tone = Tone.QUIET, onClick = { nav.go(Route.Tell(noteId = n.id)) })
        BigButton("Remove this note", tone = Tone.SECONDARY, icon = Icons.Rounded.Delete, onClick = {
            scope.launch {
                app.viewRepo.remove(listOf(n.id)); app.refreshWidgets()
                UndoHost.show("Note removed.") { scope.launch { app.viewRepo.restore(listOf(n.id)); app.refreshWidgets() } }
                nav.back()
            }
        })
    }
}

@Composable
fun RemovedScreen(nav: Nav) {
    val ctx = LocalContext.current
    val app = ctx.medlog
    val scope = rememberCoroutineScope()
    val removed by app.viewDb.notes().removedFlow().collectAsState(emptyList())
    Screen("Removed notes", "Removed notes are kept for 30 days. Tap Bring back to restore one.", onHome = { nav.home() }, onBack = { nav.back() }) {
        if (removed.isEmpty()) Empty("Nothing removed.")
        removed.forEach { n ->
            Card {
                Body("${dayLabel(n.occurredAt)} · ${n.text}")
                BigButton("Bring back", tone = Tone.QUIET, icon = Icons.Rounded.Restore, onClick = { scope.launch { app.viewRepo.restore(listOf(n.id)); app.refreshWidgets() } })
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
        BigField("Word to find", q, { v -> q = v; scope.launch { val (a, b) = app.viewRepo.search(v); notes = a; docs = b } })
        if (q.length >= 2 && notes.isEmpty() && docs.isEmpty()) Empty("Nothing found for “$q”.")
        notes.forEach { n -> NoteRow(n) { nav.go(Route.NoteDetail(n.id)) } }
        if (docs.isNotEmpty()) { Title("From your old reports"); docs.forEach { d -> Card { Hint(d.source); Body(d.content) } } }
        BigButton("Removed notes", tone = Tone.SECONDARY, icon = Icons.Rounded.Restore, onClick = { nav.go(Route.Removed) })
    }
}

/**
 * One dose from any day, noted afterwards: taken on time, taken at another time, not taken, or skipped.
 * So a day's medicines can be put right at night or the next day, by the person or a helper.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun DoseAfterSheet(name: String, d: com.suryaprakash.medlog.data.Dose, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val p = LocalPalette.current
    val sc = com.suryaprakash.medlog.ui.LocalScale.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var other by remember { mutableStateOf(false) }
    fun done(block: suspend () -> Unit) { scope.launch { block(); com.suryaprakash.medlog.ui.savedFeedback(ctx) }; onDismiss() }
    if (other) { com.suryaprakash.medlog.ui.WhenSheet(d.actedAt ?: d.scheduledAt, onDone = { t -> done { com.suryaprakash.medlog.data.Doses.take(ctx, d.id, t ?: System.currentTimeMillis()) } }, onDismiss = onDismiss); return }
    val t = com.suryaprakash.medlog.ui.screens.chipTime(d.scheduledAt)
    com.suryaprakash.medlog.ui.AppSheet(onDismissRequest = onDismiss, containerColor = p.paper) {
        Column(Modifier.fillMaxWidth().padding(horizontal = sc.margin).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            com.suryaprakash.medlog.ui.SectionHeader(name, "The $t dose, ${dayLabel(d.scheduledAt).lowercase()}", null)
            BigButton("Taken on time · $t", tone = com.suryaprakash.medlog.ui.Tone.PRIMARY, onClick = { done { com.suryaprakash.medlog.data.Doses.take(ctx, d.id, d.scheduledAt) } })
            BigButton("Taken at another time", tone = com.suryaprakash.medlog.ui.Tone.TINT, onClick = { other = true })
            if (d.status == DoseStatus.TAKEN) BigButton("Not taken", tone = com.suryaprakash.medlog.ui.Tone.SECONDARY, onClick = { done { com.suryaprakash.medlog.data.Doses.untake(ctx, d.id) } })
            if (d.status != DoseStatus.SKIPPED) BigButton("Skipped on purpose", tone = com.suryaprakash.medlog.ui.Tone.SECONDARY, onClick = { done { com.suryaprakash.medlog.data.Doses.skip(ctx, d.id, "Skipped") } })
        }
    }
}
