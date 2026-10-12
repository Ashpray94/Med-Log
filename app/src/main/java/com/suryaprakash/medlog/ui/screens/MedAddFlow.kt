package com.suryaprakash.medlog.ui.screens

import com.suryaprakash.medlog.ui.cardTitle
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.RamenDining
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.NoFood
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.LocalDrink
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Sanitizer
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material.icons.rounded.Vaccines
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.suryaprakash.medlog.data.DAY
import com.suryaprakash.medlog.data.Medicine
import com.suryaprakash.medlog.integration.CalendarSync
import com.suryaprakash.medlog.medlog
import com.suryaprakash.medlog.importer.Ocr
import com.suryaprakash.medlog.meds.Scheduler
import com.suryaprakash.medlog.ui.BigButton
import com.suryaprakash.medlog.ui.BigField
import com.suryaprakash.medlog.ui.BigOption
import com.suryaprakash.medlog.ui.ChoiceCards
import com.suryaprakash.medlog.ui.ChoiceGrid
import com.suryaprakash.medlog.ui.FlowScreen
import com.suryaprakash.medlog.ui.Group
import com.suryaprakash.medlog.ui.GroupLine
import com.suryaprakash.medlog.ui.Hint
import com.suryaprakash.medlog.ui.LocalPalette
import com.suryaprakash.medlog.ui.LocalScale
import com.suryaprakash.medlog.ui.Nav
import com.suryaprakash.medlog.ui.NumberWheel
import com.suryaprakash.medlog.ui.OptionIcon
import com.suryaprakash.medlog.ui.Section
import com.suryaprakash.medlog.ui.Segmented
import com.suryaprakash.medlog.ui.Toggle
import com.suryaprakash.medlog.ui.Tone
import com.suryaprakash.medlog.ui.ValueRow
import com.suryaprakash.medlog.ui.savedFeedback
import com.suryaprakash.medlog.ui.steady
import kotlinx.coroutines.launch
import java.io.File

/** The steps of adding a medicine, one decision each. */
private enum class M { NAME, KIND, STRENGTH, OFTEN, DAYS, TIMES, GAP, FOOD, LOOK, REVIEW }

private val FORMS = listOf("tablet" to "Tablet", "capsule" to "Capsule", "syrup" to "Syrup", "drops" to "Drops",
    "injection" to "Injection", "inhaler" to "Inhaler", "cream" to "Cream")
private val SHAPES = listOf("round" to "Round", "oval" to "Oval", "capsule" to "Capsule", "oblong" to "Long", "square" to "Square", "diamond" to "Diamond")
private val COLORS = listOf("white" to 0xFFFFFFFF, "yellow" to 0xFFF6D860, "orange" to 0xFFF59E4C, "pink" to 0xFFF4A7C0, "red" to 0xFFE35D5D,
    "blue" to 0xFF6AA6E8, "green" to 0xFF6CC28A, "brown" to 0xFFA47551, "purple" to 0xFFA88BE0, "grey" to 0xFFB8BCC2)
private val WEEK = listOf(1 to "Monday", 2 to "Tuesday", 3 to "Wednesday", 4 to "Thursday", 5 to "Friday", 6 to "Saturday", 7 to "Sunday")

/**
 * Adding (or changing) a medicine, as a short guided flow: name, kind, strength, how often, times and amount,
 * food, what it looks like, then a review page to check and save. Changing an existing one opens on the review.
 */
@Composable
fun MedicineFlow(nav: Nav, id: Long?, inSheet: Boolean = false) = androidx.compose.runtime.CompositionLocalProvider(com.suryaprakash.medlog.ui.LocalInSheet provides inSheet) { MedicineFlowPages(nav, id) }

@Composable
private fun MedicineFlowPages(nav: Nav, id: Long?) {
    val ctx = LocalContext.current
    val app = ctx.medlog
    val p = LocalPalette.current
    val sc = LocalScale.current
    val scope = rememberCoroutineScope()
    var m by remember { mutableStateOf(Medicine(name = "")) }
    var original by remember { mutableStateOf<Medicine?>(null) }
    val times = remember { mutableStateListOf<String>() }
    var daysCount by remember { mutableStateOf("") }
    var pills by remember { mutableStateOf("") }
    var step by remember { mutableStateOf(if (id == null) M.NAME else M.REVIEW) }
    var backTo by remember { mutableStateOf<M?>(null) }      // set when a review row opened a step
    var ocrLines by remember { mutableStateOf<List<String>>(emptyList()) }
    var reading by remember { mutableStateOf(false) }
    var photoFile by remember { mutableStateOf<File?>(null) }
    var someDays by remember { mutableStateOf(false) }

    LaunchedEffect(id) {
        if (id != null) app.viewDb.medicines().get(id)?.let { e ->
            m = e; original = e; times.clear(); times.addAll(e.times.split(",").map { it.trim() }.filter { it.isNotBlank() })
            pills = e.pillsLeft?.toInt()?.toString() ?: ""; someDays = e.days.isNotBlank()
        }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val f = photoFile
        if (ok && f != null) {
            m = m.copy(photoPath = f.absolutePath); reading = true
            scope.launch {
                val found = runCatching { Ocr.medicineGuess(ctx, Uri.fromFile(f)) }.getOrNull()
                reading = false
                if (found != null) {
                    ocrLines = found.candidates
                    if (m.name.isBlank()) m = m.copy(name = found.name ?: m.name, strength = found.strength ?: m.strength)
                }
            }
        }
    }
    fun takePhoto() {
        val dir = File(ctx.filesDir, "photos").apply { mkdirs() }
        val f = File(dir, "med_${System.currentTimeMillis()}.jpg")
        photoFile = f
        camera.launch(FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f))
    }

    // the order of pages for this medicine (some only appear when they apply)
    fun pages(): List<M> = buildList {
        add(M.NAME); add(M.KIND); add(M.STRENGTH); add(M.OFTEN)
        if (!m.asNeeded && someDays) add(M.DAYS)
        if (m.asNeeded) add(M.GAP) else { add(M.TIMES); add(M.FOOD) }
        if (m.form == "tablet" || m.form == "capsule") add(M.LOOK)
        add(M.REVIEW)
    }
    fun next() { val ps = pages(); step = backTo?.let { backTo = null; M.REVIEW } ?: ps.getOrElse(ps.indexOf(step) + 1) { M.REVIEW } }
    fun back() {
        if (backTo != null) { backTo = null; step = M.REVIEW; return }
        val ps = pages(); val i = ps.indexOf(step)
        if (i <= 0 || (id != null && step == M.REVIEW)) nav.back() else step = ps[i - 1]
    }
    fun open(s: M) { backTo = M.REVIEW; step = s }
    androidx.activity.compose.BackHandler { back() }
    val ps = pages()
    val n = ps.indexOf(step) + 1
    val task = if (id == null) "Add a medicine" else "Change medicine"
    val close: () -> Unit = { nav.back() }
    val primaryNext = if (backTo != null) "Done" else "Next"
    val unit = when (m.form) { "syrup", "drops" -> "ml"; "cream" -> "use"; "inhaler" -> "puff"; "injection" -> "dose"; else -> m.form }

    when (step) {
        // ───────────── name ─────────────
        M.NAME -> FlowScreen(task, "What's the medicine called?", step = n, steps = ps.size, onBack = if (backTo != null || id != null) ({ back() }) else null, onClose = close,
            primary = primaryNext, primaryEnabled = m.name.isNotBlank(), onPrimary = { next() }) {
            BigField("Medicine name", m.name, { m = m.copy(name = it) })
            BigButton(if (m.photoPath == null) "Take a photo of the strip" else "Take another photo", tone = Tone.SECONDARY, icon = Icons.Rounded.CameraAlt, onClick = { takePhoto() })
            Box(Modifier.fillMaxWidth().heightIn(min = 28.dp)) {
                when {
                    reading -> Hint("Reading the strip…")
                    ocrLines.isNotEmpty() -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Hint("Tap the name if you see it:")
                        ChoiceGrid(ocrLines.take(6), { it == m.name }) { m = m.copy(name = it) }
                    }
                }
            }
        }

        // ───────────── kind ─────────────
        M.KIND -> FlowScreen(task, "What kind is it?", step = n, steps = ps.size, onBack = { back() }, onClose = close,
            primary = primaryNext, onPrimary = { next() }) {
            ChoiceCards(FORMS.map { (k, l) ->
                BigOption(l, null, m.form == k, { FormPicture(k, 56.dp) }) { m = m.copy(form = k) }
            })
        }

        // ───────────── strength ─────────────
        M.STRENGTH -> {
            val units = listOf("mg", "mcg", "g", "ml", "%")
            var number by remember { mutableStateOf(m.strength.takeWhile { it.isDigit() || it == '.' }) }
            var u by remember { mutableStateOf(units.firstOrNull { m.strength.endsWith(it) } ?: "mg") }
            fun keep() { m = m.copy(strength = if (number.isBlank()) "" else "$number $u") }
            FlowScreen(task, "How strong is it?", hint = "It's printed on the strip, next to the name.", step = n, steps = ps.size, onBack = { back() }, onClose = close,
                primary = primaryNext, onPrimary = { keep(); next() }) {
                BigField("Strength", number, { number = it.filter { c -> c.isDigit() || c == '.' }.take(6); keep() }, keyboard = KeyboardType.Decimal)
                Segmented(units, units.indexOf(u)) { u = units[it]; keep() }
                BigButton("I don't know", tone = Tone.SECONDARY, onClick = { number = ""; m = m.copy(strength = ""); next() })
            }
        }

        // ───────────── how often ─────────────
        M.OFTEN -> FlowScreen(task, "How often do you take it?", step = n, steps = ps.size, onBack = { back() }, onClose = close,
            primary = primaryNext, onPrimary = { next() }) {
            val every = !m.asNeeded && !someDays
            ChoiceCards(listOf(
                BigOption("Every day", "At the same times each day", every, { OptionIcon(Icons.Rounded.Repeat, p.tintBlue, 56.dp) }) { m = m.copy(asNeeded = false, days = ""); someDays = false },
                BigOption("Some days of the week", "For example Monday and Thursday", !m.asNeeded && someDays, { OptionIcon(Icons.Rounded.CalendarMonth, p.tintPurple, 56.dp) }) { m = m.copy(asNeeded = false); someDays = true },
                BigOption("Only when needed", "For example a painkiller", m.asNeeded, { OptionIcon(Icons.Rounded.Today, p.tintOrange, 56.dp) }) { m = m.copy(asNeeded = true, days = ""); someDays = false },
            ))
        }

        // ───────────── which days ─────────────
        M.DAYS -> {
            val chosen = m.days.split(",").mapNotNull { it.trim().toIntOrNull() }
            FlowScreen(task, "Which days?", step = n, steps = ps.size, onBack = { back() }, onClose = close,
                primary = primaryNext, primaryEnabled = chosen.isNotEmpty(), onPrimary = { next() }) {
                ChoiceGrid(WEEK.map { it.second }, { name -> WEEK.first { it.second == name }.first in chosen }) { name ->
                    val d = WEEK.first { it.second == name }.first
                    val now = m.days.split(",").mapNotNull { it.trim().toIntOrNull() }
                    m = m.copy(days = (if (d in now) now - d else now + d).sorted().joinToString(","))
                }
            }
        }

        // ───────────── times and amount ─────────────
        M.TIMES -> {
            var editing by remember { mutableStateOf<Int?>(null) }        // index in times, -1 = new
            FlowScreen(task, "When do you take it?", step = n, steps = ps.size, onBack = { back() }, onClose = close,
                primary = primaryNext, primaryEnabled = times.isNotEmpty(), onPrimary = { next() }) {
                AmountLine(m.amount, m.form, unit) { m = m.copy(amount = it) }
                Spacer(Modifier.height(8.dp))
                times.sorted().forEach { t ->
                    TimeCard(t, amountWords(m.amount, unit), onOpen = { editing = times.indexOf(t) }, onRemove = { times.remove(t) })
                }
                AddTimeCard { editing = -1 }
            }
            editing?.let { i ->
                val start = if (i >= 0) times.getOrNull(i) else listOf("08:00", "13:00", "18:00", "21:00").firstOrNull { it !in times } ?: "09:00"
                TimeSheet(start ?: "08:00", onDone = { t -> if (i >= 0 && i < times.size) times[i] = t else if (t !in times) times.add(t); editing = null }, onDismiss = { editing = null })
            }
        }

        // ───────────── only when needed: the gap ─────────────
        M.GAP -> FlowScreen(task, "How long between doses, at least?", hint = "You'll be warned if you try to take it sooner.", step = n, steps = ps.size,
            onBack = { back() }, onClose = close, primary = primaryNext, onPrimary = { next() }) {
            AmountLine(m.amount, m.form, unit) { m = m.copy(amount = it) }
            Section("Wait at least")
            ChoiceGrid(listOf("4 hours", "6 hours", "8 hours", "12 hours"), { it == "${m.minGapHours} hours" }) { m = m.copy(minGapHours = it.substringBefore(" ").toInt()) }
        }

        // ───────────── food ─────────────
        M.FOOD -> FlowScreen(task, "Before or after food?", step = n, steps = ps.size, onBack = { back() }, onClose = close,
            primary = primaryNext, onPrimary = { next() }) {
            ChoiceCards(listOf("before" to "Before food", "after" to "After food", "with" to "With food", "any" to "Any time").map { (k, l) ->
                BigOption(l, null, m.food == k, { FoodPicture(k) }) { m = m.copy(food = k) }
            })
        }

        // ───────────── what it looks like ─────────────
        M.LOOK -> FlowScreen(task, "What does it look like?", hint = "So you can tell it apart from your other medicines.", step = n, steps = ps.size,
            onBack = { back() }, onClose = close, primary = primaryNext, onPrimary = { next() }) {
            Box(Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(24.dp)).background(p.card), contentAlignment = Alignment.Center) {
                PillPicture(m.shape.ifBlank { if (m.form == "capsule") "capsule" else "round" }, m.color.ifBlank { "white" }, 84.dp)
            }
            Section("Shape")
            SHAPES.chunked(3).forEach { row ->
                Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { (k, l) ->
                        val on = (m.shape.ifBlank { if (m.form == "capsule") "capsule" else "round" }) == k
                        LookTile(l, on, Modifier.weight(1f)) { m = m.copy(shape = k) }
                    }
                }
            }
            Section("Colour")
            COLORS.chunked(5).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { (k, c) ->
                        val on = m.color.ifBlank { "white" } == k
                        Box(Modifier.weight(1f).height(56.dp).clip(RoundedCornerShape(16.dp)).background(Color(c))
                            .then(if (on) Modifier.border(3.dp, p.brand, RoundedCornerShape(16.dp)) else Modifier)
                            .steady(k.replaceFirstChar(Char::uppercase) + if (on) ", chosen" else "") { m = m.copy(color = k) })
                    }
                }
            }
        }

        // ───────────── review and save ─────────────
        M.REVIEW -> {
            val valid = m.name.isNotBlank() && (m.asNeeded || times.isNotEmpty())
            FlowScreen(task, "Check and save", step = ps.size, steps = ps.size, onBack = { back() }, onClose = close,
                primary = "Done", primaryEnabled = valid, onPrimary = {
                    scope.launch {
                        val now = System.currentTimeMillis()
                        val change = original?.let { o ->
                            when {
                                o.strength != m.strength -> "changed from ${o.strength.ifBlank { "?" }} to ${m.strength}"
                                o.times != times.sorted().joinToString(",") -> "times changed"
                                else -> o.changeNote
                            }
                        } ?: "started"
                        val prepared = m.copy(
                            name = m.name.trim(), strength = m.strength.trim(),
                            times = if (m.asNeeded) "" else times.sorted().joinToString(","),
                            days = if (m.asNeeded || !someDays) "" else m.days,
                            endDate = daysCount.toIntOrNull()?.let { now + it * DAY } ?: m.endDate,
                            pillsLeft = pills.toDoubleOrNull(),
                            changedAt = m.changedAt,
                            changeNote = change,
                        )
                        val mid = com.suryaprakash.medlog.data.MedicineRecords.save(ctx, original, prepared)
                        savedFeedback(ctx); app.refreshWidgets(); nav.back()
                    }
                }) {
                // the medicine at a glance
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(p.card).padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(72.dp).clip(RoundedCornerShape(20.dp)).background(p.fill), contentAlignment = Alignment.Center) {
                        if (m.form == "tablet" || m.form == "capsule") PillPicture(m.shape.ifBlank { if (m.form == "capsule") "capsule" else "round" }, m.color.ifBlank { "white" }, 44.dp)
                        else FormPicture(m.form, 44.dp)
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(m.name.ifBlank { "No name yet" }, fontSize = sc.headline, fontWeight = FontWeight.Bold, color = p.ink)
                        Text(listOf(m.strength, FORMS.firstOrNull { it.first == m.form }?.second.orEmpty()).filter { it.isNotBlank() }.joinToString(" · "), fontSize = sc.body, color = p.inkSoft)
                    }
                }
                Group {
                    ValueRow("Name", m.name.ifBlank { null }, onClick = { open(M.NAME) }); GroupLine()
                    ValueRow("Kind", FORMS.firstOrNull { it.first == m.form }?.second, onClick = { open(M.KIND) }); GroupLine()
                    ValueRow("Strength", m.strength.ifBlank { null }, onClick = { open(M.STRENGTH) }); GroupLine()
                    ValueRow("How often", when { m.asNeeded -> "Only when needed"; someDays -> m.days.split(",").mapNotNull { d -> WEEK.firstOrNull { it.first == d.trim().toIntOrNull() }?.second }.joinToString(", "); else -> "Every day" },
                        onClick = { open(M.OFTEN) }); GroupLine()
                    if (m.asNeeded) { ValueRow("Wait at least", "${m.minGapHours} hours", onClick = { open(M.GAP) }) }
                    else {
                        ValueRow("Times", times.sorted().joinToString(", ") { timeWords(it) }.ifBlank { null }, sub = "${m.amount} each time", onClick = { open(M.TIMES) }); GroupLine()
                        ValueRow("Food", mapOf("before" to "Before food", "after" to "After food", "with" to "With food", "any" to "Any time")[m.food], onClick = { open(M.FOOD) })
                    }
                    if (m.form == "tablet" || m.form == "capsule") { GroupLine(); ValueRow("Looks like", listOf(m.color.ifBlank { "white" }, SHAPES.firstOrNull { it.first == m.shape }?.second ?: "").filter { it.isNotBlank() }.joinToString(" ").lowercase().replaceFirstChar(Char::uppercase), onClick = { open(M.LOOK) }) }
                }
                // extras as short rows; each opens a small panel with one box, so the page stays easy to read
                var editing by remember { mutableStateOf<String?>(null) }
                Section("Details")
                Group {
                    ValueRow("Start date", java.text.SimpleDateFormat("d MMM yyyy").format(java.util.Date(m.startDate)), onClick = {
                        val day = java.time.Instant.ofEpochMilli(m.startDate).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                        android.app.DatePickerDialog(ctx, { _, year, month, date -> m = m.copy(startDate = java.time.LocalDate.of(year, month + 1, date).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()) }, day.year, day.monthValue - 1, day.dayOfMonth).show()
                    }); GroupLine()
                    ValueRow("What it's for", m.purpose.ifBlank { null }, onClick = { editing = "purpose" }); GroupLine()
                    ValueRow("Tablets left", pills.ifBlank { null }, onClick = { editing = "pills" }); GroupLine()
                    ValueRow("How long", daysCount.takeIf { it.isNotBlank() }?.let { "$it days" } ?: m.endDate?.let { "Until " + java.text.SimpleDateFormat("d MMM yyyy").format(java.util.Date(it)) } ?: "Always", onClick = { editing = "days" })
                }
                Section("Alerts")
                Group {
                    com.suryaprakash.medlog.ui.SwitchRow("Important medicine", m.critical, "Your helpers are told if you miss it") { m = m.copy(critical = it) }
                    GroupLine()
                    com.suryaprakash.medlog.ui.SwitchRow("It's a blood thinner", m.bloodThinner, "Matters after a fall or a cut") { m = m.copy(bloodThinner = it) }
                }
                editing?.let { what ->
                    when (what) {
                        "pills" -> CounterSheet("How many do you have?", "Tablets left", pills.toIntOrNull() ?: 0, zero = "None", unitWord = "", quick = listOf(10, 30),
                            onDone = { v -> pills = if (v == 0) "" else v.toString(); editing = null }, onDismiss = { editing = null })
                        "days" -> CounterSheet("For how many days?", "Days", daysCount.toIntOrNull() ?: 0, zero = "Always", unitWord = "days", quick = listOf(7, 30),
                            onDone = { v -> daysCount = if (v == 0) "" else v.toString(); if (v == 0) m = m.copy(endDate = null); editing = null }, onDismiss = { editing = null })
                        else -> PurposeSheet(m.purpose, onDone = { v -> m = m.copy(purpose = v); editing = null }, onDismiss = { editing = null })
                    }
                }
                if (id != null) {
                    com.suryaprakash.medlog.ui.SwitchRow("Currently taking", m.active, "Turn on to restart this medicine") { m = m.copy(active = it) }
                    var removing by remember { mutableStateOf(false) }
                    BigButton("Remove from my medicines", tone = Tone.OUTLINE, onClick = { removing = true })
                    if (removing) com.suryaprakash.medlog.ui.AppSheet(onDismissRequest = { removing = false }, containerColor = p.paper) {
                        Column(Modifier.fillMaxWidth().padding(sc.margin), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text("Remove ${m.name}?", fontSize = sc.title, fontWeight = FontWeight.Bold)
                            Hint("Reminders will stop. Past doses stay in your history.")
                            BigButton("Remove medicine", tone = Tone.OUTLINE, onClick = { scope.launch {
                                com.suryaprakash.medlog.data.MedicineRecords.remove(ctx, id); nav.back()
                            } })
                            BigButton("Keep medicine", tone = Tone.QUIET, onClick = { removing = false })
                        }
                    }
                }
            }
        }
    }
}

// ───────────────────────── small parts ─────────────────────────

/** "08:00" → "8:00 AM". */
private fun timeWords(t: String): String = runCatching {
    val lt = java.time.LocalTime.parse(t.padStart(5, '0'))
    lt.format(java.time.format.DateTimeFormatter.ofPattern("h:mm a", com.suryaprakash.medlog.speech.I18n.locale))
}.getOrDefault(t)

private fun amountSteps(form: String) = if (form == "syrup" || form == "drops") listOf("1 ml", "2.5 ml", "5 ml", "7.5 ml", "10 ml", "15 ml", "20 ml") else listOf("½", "1", "1½", "2", "3", "4")

internal fun amountWords(amount: String, unit: String) =
    if (amount.endsWith("ml")) amount else "$amount $unit" + if (amount != "1" && amount != "½" && unit != "ml") "s" else ""

/** How much each time: a heading line with the counter beside it (halves for tablets, millilitres for liquids). */
@Composable
private fun AmountLine(value: String, form: String, unit: String, onChange: (String) -> Unit) {
    val steps = amountSteps(form)
    val i = steps.indexOf(value).let { if (it < 0) steps.indexOf(if (form == "syrup" || form == "drops") "5 ml" else "1") else it }
    LaunchedEffect(value) { if (value !in steps) onChange(steps[i]) }
    val words = amountWords(steps[i], unit).removePrefix(steps[i]).trim()
    com.suryaprakash.medlog.ui.CounterLine("How much each time", if (words.isEmpty()) "At every time below" else "$words at every time below".replaceFirstChar(Char::uppercase), steps[i],
        i > 0, i < steps.size - 1, { onChange(steps[i - 1]) }, { onChange(steps[i + 1]) })
}

/** One time to take it, as a big card: the time, a sun or moon for the part of the day, and how much. */
@Composable
private fun TimeCard(t: String, amount: String, onOpen: () -> Unit, onRemove: () -> Unit) {
    val p = LocalPalette.current
    val sc = LocalScale.current
    val hour = t.substringBefore(":").toIntOrNull() ?: 8
    val (icon, tint, part) = com.suryaprakash.medlog.ui.dayPart(hour)
    val sh = RoundedCornerShape(24.dp)
    Box(Modifier.fillMaxWidth().clip(sh).background(p.card)
        .steady("$part, ${timeWords(t)}, $amount. Tap to change", onClick = onOpen), contentAlignment = Alignment.CenterStart) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            OptionIcon(icon, tint, 64.dp)
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                Text(timeWords(t), fontSize = sc.title, fontWeight = FontWeight.Bold, color = p.ink)
                Text("$part · $amount", fontSize = sc.body, color = p.inkSoft)
            }
        }
        Box(Modifier.align(Alignment.TopEnd).padding(8.dp).size(48.dp).clip(CircleShape).steady("Remove ${timeWords(t)}", onClick = onRemove),
            contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Close, null, tint = p.inkSoft, modifier = Modifier.size(22.dp)) }
    }
}

/** "Add a time": the same size as a time card, with a dashed outline. */
@Composable
private fun AddTimeCard(onClick: () -> Unit) {
    val p = LocalPalette.current
    val sc = LocalScale.current
    val sh = RoundedCornerShape(24.dp)
    Row(Modifier.fillMaxWidth().heightIn(min = if (sc.big) 96.dp else 88.dp).clip(sh)
        .drawBehind {
            drawRoundRect(p.brand.copy(alpha = 0.7f), cornerRadius = CornerRadius(24.dp.toPx()),
                style = Stroke(width = 2.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(14f, 10f))))
        }
        .steady("Add a time", onClick = onClick), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        Icon(Icons.Rounded.Add, null, tint = p.brand, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(8.dp))
        Text("Add a time", fontSize = sc.button, fontWeight = FontWeight.SemiBold, color = p.brand)
    }
}

/** A count in a small panel: the standard counter row, quick "+10 / +30" buttons, and Done. 0 shows as [zero]. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun CounterSheet(title: String, label: String, start: Int, zero: String, unitWord: String, quick: List<Int>, onDone: (Int) -> Unit, onDismiss: () -> Unit) {
    val p = LocalPalette.current
    val sc = LocalScale.current
    var v by remember { mutableStateOf(start) }
    com.suryaprakash.medlog.ui.AppSheet(onDismissRequest = onDismiss, containerColor = p.paper) {
        Column(Modifier.fillMaxWidth().padding(horizontal = sc.margin).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(title, fontSize = sc.headline, fontWeight = FontWeight.Bold, color = p.ink)
            Group {
                com.suryaprakash.medlog.ui.StepperRow(label, if (v == 0) zero else "$v $unitWord".trim(), v > 0, v < 999, { v = (v - 1).coerceAtLeast(0) }, { v = (v + 1).coerceAtMost(999) })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                quick.forEach { q -> BigButton("+$q", Modifier.weight(1f), Tone.SECONDARY, onClick = { v = (v + q).coerceAtMost(999) }) }
            }
            BigButton("Done", onClick = { onDone(v) })
        }
    }
}

/** The closest picture for each long-term illness. */
private val ILLNESS_PICTURE = mapOf(
    "Diabetes" to "high_sugar", "High BP" to "high_bp", "Heart disease" to "palpitations", "Asthma or COPD" to "breathless",
    "Kidney disease" to "kidney_pain", "Arthritis" to "knee_pain", "Thyroid" to "sore_throat", "Stroke before" to "one_side_weak",
    "Parkinson's" to "tremor", "Memory loss" to "memory", "Cancer" to "weight_loss", "Depression or anxiety" to "low_mood",
)

/** What the medicine is for, in pictures: their own illnesses first, then every problem, with search. Pick any. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun PurposeSheet(start: String, onDone: (String) -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val p = LocalPalette.current
    val sc = LocalScale.current
    val cat = ctx.medlog.catalogue
    com.suryaprakash.medlog.pictogram.Sprites.init(ctx)
    val chosen = remember { mutableStateListOf<String>().apply { addAll(start.split(",").map { it.trim() }.filter { it.isNotEmpty() }) } }
    var query by remember { mutableStateOf("") }
    var mine by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(Unit) { mine = ctx.medlog.viewRepo.profile().conditions.split(",").map { it.trim() }.filter { it.isNotEmpty() } }
    // (label, picture) for every choice
    val illnesses = remember(mine) { (mine + com.suryaprakash.medlog.data.CarePlan.CONDITIONS).distinct().map { it to (ILLNESS_PICTURE[it] ?: "confusion") } }
    val problems = remember { com.suryaprakash.medlog.pictogram.Sprites.SECTIONS.flatMap { it.second }.mapNotNull { id -> cat.problem(id)?.let { it.label to id } } }
    fun toggle(x: String) { if (x in chosen) chosen.remove(x) else chosen.add(x) }
    com.suryaprakash.medlog.ui.AppSheet(onDismissRequest = onDismiss, containerColor = p.paper, scroll = false) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.92f).padding(horizontal = sc.margin).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("What is it for?", fontSize = sc.headline, fontWeight = FontWeight.Bold, color = p.ink)
            com.suryaprakash.medlog.ui.SearchBox(query, { query = it }, "Search problems")
            Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (query.isBlank()) {
                    Section("Your illnesses")
                    PictureGrid(illnesses.filter { it.first in mine }.ifEmpty { illnesses }, chosen) { toggle(it) }
                    if (mine.isNotEmpty()) { Section("Other illnesses"); PictureGrid(illnesses.filter { it.first !in mine }, chosen) { toggle(it) } }
                    Section("All problems")
                    PictureGrid(problems, chosen) { toggle(it) }
                } else PictureGrid((illnesses + problems).filter { it.first.contains(query.trim(), ignoreCase = true) }.take(30), chosen) { toggle(it) }
                Spacer(Modifier.height(8.dp))
            }
            BigButton(if (chosen.isEmpty()) "Done" else "Done, ${chosen.size} chosen", onClick = { onDone(chosen.joinToString(", ")) })
        }
    }
}

/** Two columns of picture tiles, chosen by their label. */
@Composable
private fun PictureGrid(items: List<Pair<String, String>>, chosen: List<String>, toggle: (String) -> Unit) {
    val sc = LocalScale.current
    com.suryaprakash.medlog.ui.TileGrid(items, 2, aspect = 1.0f) { (label, pic), mod ->
        com.suryaprakash.medlog.ui.PicTile(label, mod, picture = 84.dp, selected = label in chosen, onClick = { toggle(label) }) {
            com.suryaprakash.medlog.pictogram.SpriteIcon(pic, 84.dp)
        }
    }
}

/** Picking a time on rolling wheels: hour, minutes, morning or afternoon. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun TimeSheet(start: String, onDone: (String) -> Unit, onDismiss: () -> Unit) {
    val p = LocalPalette.current
    val sc = LocalScale.current
    val lt = runCatching { java.time.LocalTime.parse(start.padStart(5, '0')) }.getOrDefault(java.time.LocalTime.of(8, 0))
    var h by remember { mutableStateOf(((lt.hour + 11) % 12) + 1) }
    var mi by remember { mutableStateOf(lt.minute - lt.minute % 5) }
    var pm by remember { mutableStateOf(if (lt.hour >= 12) 1 else 0) }
    fun result(): String { val hour24 = (h % 12) + if (pm == 1) 12 else 0; return "%02d:%02d".format(hour24, mi) }
    com.suryaprakash.medlog.ui.AppSheet(onDismissRequest = onDismiss, containerColor = p.card) {
        Column(Modifier.fillMaxWidth().padding(horizontal = sc.margin).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Time", fontSize = sc.headline, fontWeight = FontWeight.Bold, color = p.ink)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberWheel((1..12).toList(), h, { h = it }, Modifier.weight(1f))
                NumberWheel((0..55 step 5).toList(), mi, { mi = it }, Modifier.weight(1f), label = { "%02d".format(it) })
                NumberWheel(listOf(0, 1), pm, { pm = it }, Modifier.weight(1f), label = { if (it == 0) "AM" else "PM" })
            }
            BigButton("Done", onClick = { onDone(result()) })
        }
    }
}

/** A tile for a shape choice. */
@Composable
private fun LookTile(label: String, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val p = LocalPalette.current
    val sc = LocalScale.current
    Box(modifier.fillMaxHeight().heightIn(min = sc.target + 12.dp).clip(RoundedCornerShape(18.dp))
        .background(if (on) p.brandSoft else p.card).then(if (on) Modifier.border(3.dp, p.brand, RoundedCornerShape(18.dp)) else Modifier)
        .steady(label + if (on) ", chosen" else "", onClick = onClick).padding(10.dp), contentAlignment = Alignment.Center) {
        Text(label, fontSize = sc.body, fontWeight = FontWeight.SemiBold, color = p.ink)
    }
}


/** A drawn pill: its shape and colour, with a soft outline and a score line. */
@Composable
fun PillPicture(shape: String, color: String, size: Dp) {
    val fill = Color(COLORS.firstOrNull { it.first == color }?.second ?: 0xFFFFFFFF)
    val edge = Color(0xFF8A8F98)
    Canvas(Modifier.size(size)) {
        val w = this.size.width; val hgt = this.size.height
        val stroke = Stroke(width = w * 0.035f)
        when (shape) {
            "oval" -> { drawOval(fill, Offset(w * 0.08f, hgt * 0.22f), Size(w * 0.84f, hgt * 0.56f)); drawOval(edge, Offset(w * 0.08f, hgt * 0.22f), Size(w * 0.84f, hgt * 0.56f), style = stroke) }
            "capsule" -> {
                val r = CornerRadius(hgt * 0.2f)
                drawRoundRect(fill, Offset(w * 0.1f, hgt * 0.3f), Size(w * 0.8f, hgt * 0.4f), r)
                drawRoundRect(Color(0xFFE35D5D).copy(alpha = if (color == "red") 0.4f else 0.85f), Offset(w * 0.1f, hgt * 0.3f), Size(w * 0.4f, hgt * 0.4f), r)
                drawRoundRect(edge, Offset(w * 0.1f, hgt * 0.3f), Size(w * 0.8f, hgt * 0.4f), r, style = stroke)
            }
            "oblong" -> { val r = CornerRadius(hgt * 0.14f); drawRoundRect(fill, Offset(w * 0.06f, hgt * 0.33f), Size(w * 0.88f, hgt * 0.34f), r); drawRoundRect(edge, Offset(w * 0.06f, hgt * 0.33f), Size(w * 0.88f, hgt * 0.34f), r, style = stroke) }
            "square" -> { val r = CornerRadius(w * 0.14f); drawRoundRect(fill, Offset(w * 0.18f, hgt * 0.18f), Size(w * 0.64f, hgt * 0.64f), r); drawRoundRect(edge, Offset(w * 0.18f, hgt * 0.18f), Size(w * 0.64f, hgt * 0.64f), r, style = stroke) }
            "diamond" -> {
                val path = Path().apply { moveTo(w * 0.5f, hgt * 0.1f); lineTo(w * 0.9f, hgt * 0.5f); lineTo(w * 0.5f, hgt * 0.9f); lineTo(w * 0.1f, hgt * 0.5f); close() }
                drawPath(path, fill); drawPath(path, edge, style = stroke)
            }
            else -> {
                drawCircle(fill, w * 0.38f); drawCircle(edge, w * 0.38f, style = stroke)
                drawLine(edge.copy(alpha = 0.5f), Offset(w * 0.5f, hgt * 0.2f), Offset(w * 0.5f, hgt * 0.8f), strokeWidth = w * 0.03f)
            }
        }
    }
}

/** The picture for a kind of medicine, drawn to look like the real thing, on a soft square in its own colour. */
@Composable
private fun FormPicture(form: String, size: Dp) {
    val p = LocalPalette.current
    val tint = when (form) { "tablet" -> p.tintBlue; "capsule" -> p.tintOrange; "syrup" -> p.tintPurple; "drops" -> p.tintTeal; "injection" -> p.tintPink; "inhaler" -> p.tintBlue; else -> p.tintGreen }
    Box(Modifier.size(size).clip(RoundedCornerShape(size * 0.28f)).background(tint.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
        when (form) {
            "tablet" -> PillPicture("round", "white", size * 0.55f)
            "capsule" -> PillPicture("capsule", "white", size * 0.62f)
            else -> Canvas(Modifier.size(size * 0.6f)) { drawForm(form, tint) }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawForm(form: String, tint: Color) {
    val w = size.width; val h = size.height
    val line = Color(0xFF3F3A35)
    val st = Stroke(width = w * 0.05f)
    when (form) {
        // a bottle: cap, neck, body with a label
        "syrup" -> {
            drawRoundRect(line, Offset(w * 0.36f, 0f), Size(w * 0.28f, h * 0.14f), CornerRadius(w * 0.04f))
            drawRect(tint.copy(alpha = 0.55f), Offset(w * 0.4f, h * 0.14f), Size(w * 0.2f, h * 0.1f))
            drawRoundRect(tint, Offset(w * 0.2f, h * 0.24f), Size(w * 0.6f, h * 0.76f), CornerRadius(w * 0.14f))
            drawRoundRect(Color.White, Offset(w * 0.28f, h * 0.46f), Size(w * 0.44f, h * 0.3f), CornerRadius(w * 0.05f))
            drawLine(tint, Offset(w * 0.36f, h * 0.56f), Offset(w * 0.64f, h * 0.56f), strokeWidth = w * 0.04f)
            drawLine(tint, Offset(w * 0.36f, h * 0.66f), Offset(w * 0.56f, h * 0.66f), strokeWidth = w * 0.04f)
        }
        // a dropper bottle and a drop
        "drops" -> {
            drawRoundRect(line, Offset(w * 0.22f, 0f), Size(w * 0.2f, h * 0.24f), CornerRadius(w * 0.08f))
            drawRect(tint.copy(alpha = 0.55f), Offset(w * 0.26f, h * 0.24f), Size(w * 0.12f, h * 0.1f))
            drawRoundRect(tint, Offset(w * 0.08f, h * 0.34f), Size(w * 0.48f, h * 0.66f), CornerRadius(w * 0.12f))
            val d = Path().apply { moveTo(w * 0.8f, h * 0.36f); cubicTo(w * 0.9f, h * 0.52f, w * 0.96f, h * 0.6f, w * 0.96f, h * 0.68f)
                cubicTo(w * 0.96f, h * 0.8f, w * 0.64f, h * 0.8f, w * 0.64f, h * 0.68f); cubicTo(w * 0.64f, h * 0.6f, w * 0.7f, h * 0.52f, w * 0.8f, h * 0.36f); close() }
            drawPath(d, tint)
        }
        // a syringe: barrel with marks, plunger, needle
        "injection" -> {
            rotate(-40f) {
                drawRoundRect(Color.White, Offset(w * 0.3f, h * 0.36f), Size(w * 0.44f, h * 0.24f), CornerRadius(w * 0.04f))
                drawRect(tint, Offset(w * 0.42f, h * 0.38f), Size(w * 0.3f, h * 0.2f))
                drawRoundRect(line, Offset(w * 0.3f, h * 0.36f), Size(w * 0.44f, h * 0.24f), CornerRadius(w * 0.04f), style = st)
                drawLine(line, Offset(w * 0.1f, h * 0.48f), Offset(w * 0.3f, h * 0.48f), strokeWidth = w * 0.06f)
                drawLine(line, Offset(w * 0.08f, h * 0.36f), Offset(w * 0.08f, h * 0.6f), strokeWidth = w * 0.06f)
                drawLine(line, Offset(w * 0.74f, h * 0.48f), Offset(w * 0.98f, h * 0.48f), strokeWidth = w * 0.025f)
            }
        }
        // an inhaler: the L-shaped puffer with its canister on top
        "inhaler" -> {
            drawRoundRect(line, Offset(w * 0.3f, 0f), Size(w * 0.3f, h * 0.42f), CornerRadius(w * 0.08f))
            val body = Path().apply {
                moveTo(w * 0.22f, h * 0.36f); lineTo(w * 0.68f, h * 0.36f); lineTo(w * 0.68f, h * 0.7f); lineTo(w * 0.96f, h * 0.7f)
                lineTo(w * 0.96f, h * 0.98f); lineTo(w * 0.3f, h * 0.98f); quadraticTo(w * 0.22f, h * 0.98f, w * 0.22f, h * 0.88f); close()
            }
            drawPath(body, tint)
            drawRect(Color.White.copy(alpha = 0.6f), Offset(w * 0.9f, h * 0.74f), Size(w * 0.04f, h * 0.2f))
        }
        // a tube of cream: cap, body pressed flat at the end
        else -> {
            rotate(-30f) {
                drawRoundRect(line, Offset(w * 0.04f, h * 0.4f), Size(w * 0.18f, h * 0.2f), CornerRadius(w * 0.04f))
                val tube = Path().apply {
                    moveTo(w * 0.22f, h * 0.36f); lineTo(w * 0.86f, h * 0.28f); lineTo(w * 0.96f, h * 0.28f); lineTo(w * 0.96f, h * 0.72f)
                    lineTo(w * 0.86f, h * 0.72f); lineTo(w * 0.22f, h * 0.64f); close()
                }
                drawPath(tube, tint)
                drawLine(Color.White.copy(alpha = 0.7f), Offset(w * 0.9f, h * 0.3f), Offset(w * 0.9f, h * 0.7f), strokeWidth = w * 0.03f)
                drawRoundRect(Color.White, Offset(w * 0.38f, h * 0.42f), Size(w * 0.34f, h * 0.16f), CornerRadius(w * 0.04f))
            }
        }
    }
}

/** A simple picture for "before / after / with food / any time". */
@Composable
private fun FoodPicture(kind: String) {
    val p = LocalPalette.current
    val (icon, tint) = when (kind) {
        "before" -> Icons.Rounded.NoFood to p.tintOrange
        "after" -> Icons.Rounded.Restaurant to p.tintGreen
        "with" -> Icons.Rounded.RamenDining to p.tintBlue
        else -> Icons.Rounded.Schedule to p.tintPurple
    }
    OptionIcon(icon, tint, 56.dp)
}

/** One value in a small panel from the bottom: a title, one box, Done. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun FieldSheet(title: String, label: String, start: String, number: Boolean, onDone: (String) -> Unit, onDismiss: () -> Unit) {
    val p = LocalPalette.current
    val sc = LocalScale.current
    var v by remember { mutableStateOf(start) }
    com.suryaprakash.medlog.ui.AppSheet(onDismissRequest = onDismiss, containerColor = p.card) {
        Column(Modifier.fillMaxWidth().padding(horizontal = sc.margin).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(title, fontSize = sc.headline, fontWeight = FontWeight.Bold, color = p.ink)
            BigField(label, v, { v = it }, keyboard = if (number) KeyboardType.Number else KeyboardType.Text)
            BigButton("Done", onClick = { onDone(v.trim()) })
        }
    }
}

/** A medicine at a glance, to confirm it: its picture, name and strength, what it's for, and when. Tap to change. */
/** What a medicine looks like: its pill shape and colour, or its kind (bottle, inhaler …), on a grey square. */
@Composable
fun MedicinePicture(m: Medicine, size: Dp) {
    val p = LocalPalette.current
    Box(Modifier.size(size).clip(RoundedCornerShape(size * 0.28f)).background(p.fill), contentAlignment = Alignment.Center) {
        if (m.form == "tablet" || m.form == "capsule") PillPicture(m.shape.ifBlank { if (m.form == "capsule") "capsule" else "round" }, m.color.ifBlank { "white" }, size * 0.78f)
        else FormPicture(if (m.form == "feed") "syrup" else m.form, size * 0.88f)
    }
}

/** How much one dose is, in words: "1 tablet", "2 puffs", "10 ml". */
fun doseWords(m: Medicine): String {
    // an amount already in words ("2 puffs", "18 units") is used as it is
    if (m.form == "feed" || m.amount.any { it.isLetter() }) return m.amount
    val unit = when (m.form) { "syrup", "drops" -> "ml"; "cream" -> "use"; "inhaler" -> "puff"; "injection" -> "dose"; else -> m.form }
    return amountWords(m.amount, unit)
}

@Composable
fun MedicineCard(m: Medicine, onClick: () -> Unit) {
    val p = LocalPalette.current
    val sc = LocalScale.current
    val sh = RoundedCornerShape(22.dp)
    val times = m.times.split(",").map { it.trim() }.filter { it.isNotBlank() }.sorted()
    val unit = when (m.form) { "syrup", "drops" -> "ml"; "cream" -> "use"; "inhaler" -> "puff"; "injection" -> "dose"; else -> m.form }
    val whenWords = if (m.asNeeded || times.isEmpty()) "When needed" else
        (if (times.size == 1) timeWords(times[0]) else times.dropLast(1).joinToString(", ") { timeWords(it) } + " and " + timeWords(times.last())) +
            " · " + doseWords(m)
    Row(Modifier.fillMaxWidth().clip(sh).background(p.card)
        .steady("${m.name} ${m.strength}. $whenWords. Tap to change", onClick = onClick).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        MedicinePicture(m, 64.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(listOf(m.name, m.strength.takeIf { m.form != "feed" }.orEmpty()).filter { it.isNotBlank() }.joinToString(" "), fontSize = sc.cardTitle, fontWeight = FontWeight.Bold, color = p.ink)
            if (m.purpose.isNotBlank()) Text("For ${m.purpose}", fontSize = sc.body, color = p.ink)
            else Text("Add what it's for", fontSize = sc.body, color = p.brand)
            Text(whenWords, fontSize = sc.small, color = p.inkSoft)
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = p.inkSoft.copy(alpha = 0.6f), modifier = Modifier.size(26.dp))
    }
}
