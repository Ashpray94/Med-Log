package com.suryaprakash.medlog.ui.screens

import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.StickyNote2
import androidx.compose.material3.Icon
import com.suryaprakash.medlog.ui.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.suryaprakash.medlog.doctor.DoctorNote
import com.suryaprakash.medlog.doctor.Pdf
import com.suryaprakash.medlog.medlog
import com.suryaprakash.medlog.pictogram.BodyArt
import com.suryaprakash.medlog.pictogram.SpriteIcon
import com.suryaprakash.medlog.pictogram.WHOLE
import com.suryaprakash.medlog.ui.BigButton
import com.suryaprakash.medlog.ui.BigField
import com.suryaprakash.medlog.ui.LocalPalette
import com.suryaprakash.medlog.ui.LocalScale
import com.suryaprakash.medlog.ui.Nav
import com.suryaprakash.medlog.ui.Palette
import com.suryaprakash.medlog.ui.Perms
import com.suryaprakash.medlog.ui.Route
import com.suryaprakash.medlog.ui.Screen
import com.suryaprakash.medlog.ui.Tone
import com.suryaprakash.medlog.ui.steady
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val PERIODS = listOf(7 to "Last week", 14 to "Last 2 weeks", 30 to "Last month", 90 to "Last 3 months")

/**
 * The doctor page. One job: show a doctor, in under five minutes, what has been happening.
 * One main action (Share), one quiet setting (how far back), then the content in clearly separated sections.
 * The patient's own extras (adding a question, visits) wait lower down, out of the way.
 */
@Composable
fun DoctorScreen(nav: Nav) {
    val ctx = LocalContext.current
    val p = LocalPalette.current
    val scope = rememberCoroutineScope()
    var days by remember { mutableStateOf(14) }
    var note by remember { mutableStateOf<DoctorNote?>(null) }
    var busy by remember { mutableStateOf(false) }
    var asking by remember { mutableStateOf(false) }
    var nut by remember { mutableStateOf<com.suryaprakash.medlog.nutrition.Nutrition.Report?>(null) }
    val selectedPatient by com.suryaprakash.medlog.data.Viewing.pairId.collectAsState()
    LaunchedEffect(days, selectedPatient) {
        note = null; nut = null
        val db = ctx.medlog.viewDb
        callbackFlow {
            val observer = object : androidx.room.InvalidationTracker.Observer("notes", "medicines", "doses", "profile") {
                override fun onInvalidated(tables: Set<String>) { trySend(Unit) }
            }
            db.invalidationTracker.addObserver(observer)
            trySend(Unit)
            awaitClose { db.invalidationTracker.removeObserver(observer) }
        }.conflate().collect {
            note = withContext(Dispatchers.IO) { buildNote(ctx, days) }
            nut = withContext(Dispatchers.IO) { com.suryaprakash.medlog.nutrition.Nutrition.build(ctx, days) }
        }
    }
    val nutShown = nut?.takeIf { it.loggedDays > 0 || it.weights.isNotEmpty() || it.feeds.isNotEmpty() }
    val n = note
    val speak = if (n == null) "Preparing." else "Your summary for the doctor. Most important: " + n.concerns.joinToString(". ").ifBlank { "nothing worrying" } + ". Tap Share to send it, or Print."
    // the PDF waits for everything: the nutrition page takes a few seconds longer than the rest, and a quick tap on
    // Share used to make a PDF without it (only the first page)
    fun pdf(then: (java.io.File) -> Unit) { scope.launch {
        busy = true
        try {
        val f = withContext(Dispatchers.IO) {
            val r = com.suryaprakash.medlog.nutrition.Nutrition.build(ctx, days).also { nut = it }
            Pdf.write(ctx, buildNote(ctx, days), r.takeIf { it.loggedDays > 0 || it.weights.isNotEmpty() || it.feeds.isNotEmpty() })
        }
        then(f)
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
            android.widget.Toast.makeText(ctx, "Could not prepare the report. Please try again.", android.widget.Toast.LENGTH_LONG).show()
        } finally { busy = false }
    } }

    var doctors by remember { mutableStateOf<List<com.suryaprakash.medlog.data.CarePlan.Doctor>>(emptyList()) }
    LaunchedEffect(Unit) { doctors = com.suryaprakash.medlog.data.CarePlan.parse(ctx.medlog.viewRepo.profile().plan).doctors }
    // the page's job is to be shown or sent: those two actions stay pinned at the bottom, side by side
    Screen("For the doctor", speak, onHome = { nav.home() }, onBack = { nav.back() }, actions = {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BigButton("Print", Modifier.weight(1f), Tone.SECONDARY, icon = Icons.Rounded.Print, enabled = n != null && !busy, onClick = { pdf { Pdf.print(ctx, it) } })
            BigButton("Share", Modifier.weight(1f), Tone.PRIMARY, icon = Icons.Rounded.Share, enabled = n != null && !busy, onClick = { pdf { Pdf.share(ctx, it) } })
        }
    }) {
        // how far back: four choices, one tap, no window
        com.suryaprakash.medlog.ui.Segmented(listOf("1W", "2W", "1M", "3M"), PERIODS.indexOfFirst { it.first == days }) { days = PERIODS[it].first }
        n?.period?.let { com.suryaprakash.medlog.ui.Hint(it) }
        if (n == null) { com.suryaprakash.medlog.ui.Loading(); return@Screen }

        Stats(n)

        if (n.concerns.isNotEmpty()) {
            Section("Symptoms at a glance")
            // each opens its own history: a symptom's every entry, a medicine's page
            Group { n.concerns.forEachIndexed { i, c ->
                if (i > 0) Line()
                val pid = n.symptoms.firstOrNull { it.name == c.substringBefore(":") }?.problemId
                ConcernRow(c, n.concernLevels.getOrElse(i) { "GREEN" }) { if (pid != null) nav.go(Route.ProblemHistory(pid)) else nav.go(Route.Meds) }
            } }
        }

        if (n.pins.isNotEmpty()) {
            Section("Where on the body")
            Group { BodyPins(n) }
        }

        Section("Symptoms")
        if (n.symptoms.isEmpty()) Group { Plain("No symptoms noted in this time.") }
        n.symptoms.forEach { r -> SymptomCard(r, n.days) { nav.go(Route.ProblemHistory(r.problemId, days)) } }

        if (n.medicines.isNotEmpty()) {
            Section("Medicines")
            // how well doses were taken belongs here, with the medicines, in words: how many, of how many, over how long
            run {
                val due = n.medicines.filter { !it.asNeeded }.sumOf { it.due }
                val done = n.medicines.filter { !it.asNeeded }.sumOf { it.done }
                if (due > 0) Text("$done of $due doses taken in ${n.days} days (${done * 100 / due}%)", fontSize = LocalScale.current.body,
                    color = if (done * 100 / due < 80) LocalPalette.current.amber else LocalPalette.current.inkSoft, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 4.dp))
            }
            Group { n.medicines.forEachIndexed { i, m -> if (i > 0) Line(); MedRow(m) { nav.go(Route.Meds) } } }
        }

        if (n.tiles.isNotEmpty()) {
            Section("Readings")
            n.tiles.chunked(2).forEach { row ->
                Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { r -> ReadingCard(r, Modifier.weight(1f).fillMaxHeight()) {
                        val key = mapOf("BP" to "bp", "Blood sugar" to "sugar", "SpO₂" to "spo2", "Temperature" to "temp", "Pulse" to "pulse", "Weight" to "weight")[r.name]
                        nav.go(if (key != null) Route.Measure(key) else Route.Readings)
                    } }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }

        nutShown?.let { r ->
            Section("Nutrition")
            Group { NavRow(Icons.Rounded.Restaurant, r.headline.text) { nav.go(Route.Nutrition) } }
        }

        if (n.links.isNotEmpty()) {
            Section("Patterns noticed")
            Group { n.links.forEachIndexed { i, l -> if (i > 0) Line(); Plain(l) } }
        }

        // the patient's own part, kept apart from the medical summary
        Spacer(Modifier.height(20.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(p.line))
        Section("My questions for the doctor")
        Group {
            if (n.questions.isEmpty()) Plain("No questions yet.", soft = true)
            n.questions.forEachIndexed { i, q -> if (i > 0) Line(); Plain(q) }
        }
        BigButton("Add a question", tone = Tone.QUIET, icon = Icons.Rounded.Add, onClick = { asking = true })

        if (doctors.isNotEmpty()) {
            Section("My doctors")
            Group {
                doctors.forEachIndexed { i, d ->
                    if (i > 0) Line()
                    com.suryaprakash.medlog.ui.ValueRow(d.name, if (d.phone.isNotBlank()) "Call" else null, sub = listOf(d.speciality, d.hospital).filter { it.isNotBlank() }.joinToString(" · "),
                        onClick = if (d.phone.isNotBlank()) ({ com.suryaprakash.medlog.help.Calls.call(ctx, d.phone) }) else null)
                }
            }
        }

        Section("Visits")
        Group {
            NavRow(Icons.Rounded.StickyNote2, "Write what the doctor said") { nav.go(Route.Visit) }
            Line(60.dp)
            NavRow(Icons.Rounded.CalendarMonth, "Appointments") { nav.go(Route.Appointments) }
        }
        Spacer(Modifier.height(8.dp))
    }

    if (asking) AddQuestionDialog(onDismiss = { asking = false }) { q ->
        asking = false
        scope.launch { ctx.medlog.viewRepo.addQuestion(q); note = buildNote(ctx, days) }
    }
}

// ───────────────────────── building blocks ─────────────────────────

/** A grey group of rows. */
@Composable
private fun Group(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(LocalPalette.current.card), content = content)
}

/** A section heading, with room above it so sections read as separate. */
@Composable
private fun Section(text: String) {
    Text(text, fontSize = LocalScale.current.headline, fontWeight = FontWeight.Bold, color = LocalPalette.current.ink,
        modifier = Modifier.padding(top = 18.dp, start = 4.dp).semantics { heading() })
}

@Composable
private fun Line(inset: Dp = 16.dp) {
    Box(Modifier.padding(start = inset).fillMaxWidth().height(1.dp).background(LocalPalette.current.line))
}

@Composable
private fun Plain(text: String, soft: Boolean = false) {
    Text(text, fontSize = LocalScale.current.body, color = if (soft) LocalPalette.current.inkSoft else LocalPalette.current.ink,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp))
}

/** Label on top, answer below: reads in one glance and never squeezes long answers into a narrow column. */
@Composable
private fun Fact(label: String, value: String, color: Color? = null) {
    val p = LocalPalette.current
    val sc = LocalScale.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, fontSize = sc.small, color = p.inkSoft)
        Text(value, fontSize = sc.body, color = color ?: p.ink, fontWeight = if (color != null) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
private fun Tag(text: String, fg: Color, bg: Color) {
    Text(text, fontSize = LocalScale.current.small, fontWeight = FontWeight.Bold, color = fg, maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(bg).padding(horizontal = 10.dp, vertical = 4.dp))
}

private fun levelColor(level: String, p: Palette) = when (level) { "RED" -> p.red; "AMBER" -> p.amber; else -> p.inkSoft }
private fun levelWord(level: String) = when (level) { "RED" -> "Urgent"; "AMBER" -> "Watch"; else -> null }

// ───────────────────────── top of the page ─────────────────────────

/** Three numbers as three cards, side by side. */
@Composable
private fun Stats(n: DoctorNote) {
    val p = LocalPalette.current
    val urgent = n.symptoms.count { it.urgent == "RED" }
    val watch = n.symptoms.count { it.urgent == "AMBER" }
    val due = n.medicines.filter { !it.asNeeded }.sumOf { it.due }
    val done = n.medicines.filter { !it.asNeeded }.sumOf { it.done }
    val pct = if (due == 0) null else done * 100 / due
    Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StatCard("${n.symptoms.size}", "Symptoms", p.ink, p.card)
        StatCard("${n.symptoms.sumOf { it.reportCount }}", "Notes", if (urgent > 0) p.red else if (watch > 0) p.amber else p.ink, p.card)
        // doses taken are told with the medicines below, where they make sense
        @Suppress("UNUSED_VARIABLE") val unused = pct
    }
}

@Composable
private fun RowScope.StatCard(value: String, label: String, fg: Color, bg: Color) {
    val sc = LocalScale.current
    Column(
        Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(18.dp)).background(bg).padding(vertical = 16.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Text(value, fontSize = sc.title, fontWeight = FontWeight.Bold, color = fg, maxLines = 1)
        Text(label, fontSize = sc.small, color = LocalPalette.current.inkSoft, textAlign = TextAlign.Center)
    }
}

@Composable
private fun ConcernRow(text: String, level: String, onClick: () -> Unit) {
    val p = LocalPalette.current
    val sc = LocalScale.current
    val title = text.substringBefore(":")
    val rest = text.substringAfter(":", "").trim()
    val date = Regex("""\(([^()]*)\)$""").find(rest)?.groupValues?.get(1)
    val what = rest.removeSuffix(date?.let { "($it)" } ?: "").trim()
    // the level is said by its tag beside the name; no bar down the side
    Row(Modifier.fillMaxWidth().steady("$title. Opens its history.", onClick = onClick).padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(horizontal = 14.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(title, fontSize = sc.body, fontWeight = FontWeight.Bold, color = p.ink, modifier = Modifier.weight(1f))
                levelWord(level)?.let { Spacer(Modifier.width(8.dp)); Tag(it, Color.White, levelColor(level, p)) }
            }
            if (what.isNotEmpty()) Text(what.replaceFirstChar(Char::uppercase), fontSize = sc.body, color = p.ink)
            date?.let { Text(it, fontSize = sc.small, color = p.inkSoft) }
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = p.inkSoft, modifier = Modifier.padding(end = 12.dp).size(24.dp))
    }
}

// ───────────────────────── body ─────────────────────────

/** Front and/or back with numbered pins. Pain "all over" tints the whole figure. */
@Composable
private fun BodyPins(n: DoctorNote) = RecordedBodyMap(n.pins, n.symptoms.associate { it.n to it.name })

/** Dots stay at the recorded position. Offset numbered labels connect with lines, avoiding overlap. */
@Composable
fun RecordedBodyMap(pins: List<Pair<Int, String>>, labels: Map<Int, String>, compact: Boolean = false) {
    val ctx = LocalContext.current
    val p = LocalPalette.current
    val sc = LocalScale.current
    val marks = remember(pins) { com.suryaprakash.medlog.doctor.BodyMarkers.layout(pins) }
    val views = listOf(false, true).filter { back -> marks.any { it.back == back } }
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            for (back in views) {
                val art by produceState<ImageBitmap?>(null, back) { value = withContext(Dispatchers.IO) { BodyArt.bitmap(ctx, back, WHOLE, 500)?.asImageBitmap() } }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Canvas(Modifier.height(if (compact) 190.dp else 280.dp).aspectRatio(100f / 170f)) {
                        val u = size.width / 100f
                        art?.let { drawImage(it, dstSize = IntSize(size.width.toInt(), size.height.toInt())) }
                        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = android.graphics.Color.WHITE; textSize = 10f * u; textAlign = android.graphics.Paint.Align.CENTER; isFakeBoldText = true
                        }
                        for (mark in marks.filter { it.back == back }) {
                            val anchor = Offset(mark.x * u, mark.y * u)
                            val label = Offset(mark.labelX * u, mark.labelY * u)
                            if (mark.all) art?.let { drawImage(it, dstSize = IntSize(size.width.toInt(), size.height.toInt()), alpha = 0.25f,
                                colorFilter = ColorFilter.tint(p.brand, BlendMode.SrcIn)) }
                            drawLine(p.inkSoft, anchor, label, 1.2f * u)
                            drawCircle(p.brand, 2.5f * u, anchor)
                            drawCircle(Color.White, 10f * u, label)
                            drawCircle(p.brand, 8.5f * u, label)
                            drawContext.canvas.nativeCanvas.drawText("${mark.number}", label.x, label.y + 3.5f * u, paint)
                        }
                    }
                    Text(if (back) "Back" else "Front", fontSize = sc.small, color = p.inkSoft)
                }
            }
        }
        marks.map { it.number }.distinct().forEach { number ->
            Text("$number. ${labels[number].orEmpty()}" + if (marks.any { it.number == number && it.all }) " - all over" else "",
                fontSize = sc.small, color = p.ink, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp))
        }
    }
}

// ───────────────────────── symptoms ─────────────────────────

/** One symptom: its name, two numbers as cards, when it happened, then plain facts. */
@Composable
private fun SymptomCard(r: DoctorNote.Row, days: Int, onClick: () -> Unit) {
    val p = LocalPalette.current
    val sc = LocalScale.current
    val tone = levelColor(r.urgent, p)
    Group {
        Row(Modifier.fillMaxWidth().steady("${r.name}. Opens every time it was noted.", onClick = onClick).padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${r.n}", fontSize = sc.small, fontWeight = FontWeight.Bold, color = Color.White,
                modifier = Modifier.size(28.dp).clip(CircleShape).background(tone).wrapContentSize(Alignment.Center))
            Spacer(Modifier.width(12.dp))
            SpriteIcon(r.problemId, 40.dp)
            Spacer(Modifier.width(10.dp))
            Text(r.name, fontSize = sc.headline, fontWeight = FontWeight.Bold, color = p.ink, modifier = Modifier.weight(1f))
            levelWord(r.urgent)?.let { Tag(it, Color.White, tone) }
        }
        // the two numbers a doctor asks first
        Row(Modifier.padding(horizontal = 16.dp).height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MiniCard("Times noted", "${r.reportCount}", if (r.reportCount > 1) "in ${r.daysWith} day${if (r.daysWith == 1) "" else "s"}" else null)
            r.sevHigh?.let { MiniCard("Highest severity", "$it of 10", sevWord(it), sevColor(it, p)) } ?: Spacer(Modifier.weight(1f))
        }
        if (r.daily.isNotEmpty()) DayStrip(r.daily, tone, days)
        Spacer(Modifier.height(4.dp))
        r.lastNoted?.let { Line(); Fact("Last noted", it) }
        r.began?.let { Line(); Fact("Started", it) }
        r.trend?.let { t ->
            Line()
            when (t) {
                "increasing" -> Fact("Over time", "Getting worse", p.red)
                "decreasing" -> Fact("Over time", "Happening less often", p.ok)
                else -> Fact("Over time", "Getting better", p.ok)
            }
        }
        if (r.places.isNotEmpty()) { Line(); Fact("Where", r.places.joinToString(", ")) }
        if (r.feels.isNotEmpty()) { Line(); Fact("What it feels like", r.feels.joinToString(", ")) }

        r.quote?.let { Line(); Fact("Additional note" + (r.quoteDate?.let { date -> " · $date" } ?: ""), "“$it”") }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun RowScope.MiniCard(label: String, value: String, sub: String?, color: Color? = null) {
    val p = LocalPalette.current
    val sc = LocalScale.current
    Column(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(14.dp)).background(p.paper).padding(horizontal = 14.dp, vertical = 12.dp)) {
        Text(label, fontSize = sc.small, color = p.inkSoft)
        Text(value, fontSize = sc.headline, fontWeight = FontWeight.Bold, color = color ?: p.ink)
        if (sub != null) Text(sub, fontSize = sc.small, color = p.inkSoft)
    }
}

private fun sevWord(v: Int) = when { v >= 9 -> "Very severe"; v >= 7 -> "Severe"; v >= 4 -> "Moderate"; v >= 1 -> "Mild"; else -> "None" }
private fun sevColor(v: Int, p: Palette) = when { v >= 7 -> p.red; v >= 4 -> Color(0xFFC2410C); else -> p.ok }

/** When it happened: one bar per day. */
@Composable
private fun DayStrip(daily: List<Int>, tone: Color, days: Int) {
    val p = LocalPalette.current
    val sc = LocalScale.current
    val max = (daily.maxOrNull() ?: 1).coerceAtLeast(1)
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Times noted each day", fontSize = sc.small, color = p.inkSoft)
        Canvas(Modifier.fillMaxWidth().height(32.dp)) {
            val slot = size.width / daily.size
            val bw = (slot * 0.64f).coerceAtLeast(1.5f)
            daily.forEachIndexed { i, v ->
                val x = i * slot + (slot - bw) / 2
                val bh = if (v == 0) 2.dp.toPx() else size.height * (0.3f + 0.7f * v / max)
                drawRoundRect(if (v == 0) Color(0x1F000000) else tone, Offset(x, size.height - bh), Size(bw, bh), CornerRadius(minOf(bw / 2, 3.dp.toPx())))
            }
        }
        Row {
            Text("${days - 1} days ago", fontSize = sc.small, color = p.inkSoft, modifier = Modifier.weight(1f))
            Text("Today", fontSize = sc.small, color = p.inkSoft)
        }
    }
}

// ───────────────────────── medicines & readings ─────────────────────────

@Composable
private fun MedRow(m: DoctorNote.Med, onClick: () -> Unit) {
    val p = LocalPalette.current
    val sc = LocalScale.current
    val low = m.due > 0 && m.done * 100 / m.due < 80
    Column(Modifier.fillMaxWidth().steady("${m.name}. Opens Medicines.", onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(m.name, fontSize = sc.body, fontWeight = FontWeight.SemiBold, color = p.ink)
                Text(plainDose(m.dose), fontSize = sc.small, color = p.inkSoft)
            }
            Spacer(Modifier.width(12.dp))
            Text(if (m.asNeeded || m.due == 0) m.taken.replaceFirstChar(Char::uppercase) else "${m.done} of ${m.due} taken", fontSize = sc.body, fontWeight = FontWeight.SemiBold, color = if (low) p.amber else p.ink)
        }
        if (!m.asNeeded && m.due > 0) {
            val f = (m.done.toFloat() / m.due).coerceIn(0f, 1f)
            Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(p.fill)) {
                if (f > 0f) Box(Modifier.fillMaxWidth(f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(if (low) p.amber else p.ok))
            }
        }
        if (m.change.isNotBlank()) Text(m.change.replaceFirstChar(Char::uppercase), fontSize = sc.small, color = p.inkSoft)
    }
}

/** "500 mg BD" becomes "500 mg, twice a day": plain for the patient, still exact for the doctor. */
private fun plainDose(d: String) = d.replace(" OD", ", once a day").replace(" BD", ", twice a day").replace(" TDS", ", 3 times a day")
    .replace(" QID", ", 4 times a day").replace("as needed", "when needed").trim().trimStart(',').trim()

@Composable
private fun ReadingCard(r: DoctorNote.Reading, modifier: Modifier, onClick: () -> Unit) {
    val p = LocalPalette.current
    val sc = LocalScale.current
    Column(modifier.clip(RoundedCornerShape(18.dp)).background(p.card).steady("${r.name} ${r.latest}. Opens its chart.", onClick = onClick).padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(r.name, fontSize = sc.small, fontWeight = FontWeight.SemiBold, color = if (r.off) p.red else p.inkSoft)
        Text(r.latest, fontSize = sc.title, fontWeight = FontWeight.Bold, color = if (r.off) p.red else p.ink, maxLines = 1)
        Text(r.unit, fontSize = sc.small, color = p.inkSoft)
        Spacer(Modifier.height(6.dp))
        Text(if (r.off) "Outside the usual range" else "Latest, ${r.date}", fontSize = sc.small, color = if (r.off) p.red else p.inkSoft)
        r.range?.let { Text("Lowest to highest: $it", fontSize = sc.small, color = p.inkSoft) }
    }
}

// ───────────────────────── rows and windows ─────────────────────────

@Composable
private fun NavRow(icon: ImageVector, title: String, onClick: () -> Unit) {
    val p = LocalPalette.current
    val sc = LocalScale.current
    Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).steady(title, onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = p.ink, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(12.dp))
        Text(title, fontSize = sc.body, color = p.ink, modifier = Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = p.inkSoft.copy(alpha = 0.5f), modifier = Modifier.size(24.dp))
    }
}

/** Adding a question gets its own quiet window: type it, or say it. */
@Composable
private fun AddQuestionDialog(onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    val ctx = LocalContext.current
    val app = ctx.medlog
    val p = LocalPalette.current
    val sc = LocalScale.current
    var q by remember { mutableStateOf("") }
    val speak = com.suryaprakash.medlog.ui.rememberDictation("Your question") { q = it }
    Dialog(onDismissRequest = { onDismiss() }) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(p.paper).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Your question", fontSize = sc.headline, fontWeight = FontWeight.Bold, color = p.ink)
            Text("Something you want to ask the doctor.", fontSize = sc.small, color = p.inkSoft)
            BigField("Type it here", q, { q = it })
            if (speak != null) BigButton("Speak instead", tone = Tone.QUIET, icon = Icons.Rounded.Mic, onClick = speak)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BigButton("Cancel", Modifier.weight(1f), Tone.SECONDARY, onClick = { onDismiss() })
                BigButton("Add", Modifier.weight(1f), enabled = q.isNotBlank(), onClick = { onAdd(q.trim()) })
            }
        }
    }
}
