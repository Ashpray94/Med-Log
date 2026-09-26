package com.suryaprakash.medlog.ui.screens

import android.graphics.BitmapFactory
import com.suryaprakash.medlog.ui.steady
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Check
import com.suryaprakash.medlog.ui.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.suryaprakash.medlog.data.DAY
import com.suryaprakash.medlog.data.Dose
import com.suryaprakash.medlog.data.DoseStatus
import com.suryaprakash.medlog.data.HOUR
import com.suryaprakash.medlog.data.Kind
import com.suryaprakash.medlog.data.Medicine
import com.suryaprakash.medlog.importer.Ocr
import com.suryaprakash.medlog.integration.CalendarSync
import com.suryaprakash.medlog.medlog
import com.suryaprakash.medlog.meds.DoseActivity
import com.suryaprakash.medlog.meds.Scheduler
import com.suryaprakash.medlog.pictogram.Picture
import com.suryaprakash.medlog.ui.BigButton
import com.suryaprakash.medlog.ui.BigField
import com.suryaprakash.medlog.ui.Body
import com.suryaprakash.medlog.ui.Card
import com.suryaprakash.medlog.ui.Chip
import com.suryaprakash.medlog.ui.FlowRowOf
import com.suryaprakash.medlog.ui.Hint
import com.suryaprakash.medlog.ui.LocalPalette
import com.suryaprakash.medlog.ui.LocalScale
import com.suryaprakash.medlog.ui.Nav
import com.suryaprakash.medlog.ui.Route
import com.suryaprakash.medlog.ui.Screen
import com.suryaprakash.medlog.ui.Title
import com.suryaprakash.medlog.ui.Toggle
import com.suryaprakash.medlog.ui.Tone
import com.suryaprakash.medlog.ui.UndoHost
import com.suryaprakash.medlog.ui.YesNo
import com.suryaprakash.medlog.ui.savedFeedback
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File

@Composable
fun MedPhoto(path: String?, sizeMul: Float = 1.2f) {
    val sc = LocalScale.current
    val bmp = remember(path) { path?.let { runCatching { BitmapFactory.decodeFile(it, BitmapFactory.Options().apply { inSampleSize = 4 }) }.getOrNull() } }
    if (bmp != null) Image(bmp.asImageBitmap(), null, Modifier.size(sc.target * sizeMul).clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.Crop)
    else Picture("mouth", "pill", sc.target * sizeMul)
}

private fun statusWord(d: Dose, now: Long) = when (d.status) {
    DoseStatus.TAKEN -> "✓ Taken ${d.actedAt?.let { DoseActivity.time(it) } ?: ""}"
    DoseStatus.SKIPPED -> "Skipped" + (d.reason?.let { ": $it" } ?: "")
    DoseStatus.MISSED -> "Missed"
    DoseStatus.SNOOZED -> "Later: ${d.snoozeUntil?.let { DoseActivity.time(it) } ?: ""}"
    else -> if (d.scheduledAt <= now) "Due now" else "Later today"
}

@Composable
fun MedsScreen(nav: Nav) {
    val ctx = LocalContext.current
    val app = ctx.medlog
    val p = LocalPalette.current
    val sc = LocalScale.current
    val scope = rememberCoroutineScope()
    val (start, end) = remember { Scheduler.today() }
    val doses by app.db.doses().betweenFlow(start, end).collectAsState(emptyList())
    val meds by app.db.medicines().activeFlow().collectAsState(emptyList()).let { st -> androidx.compose.runtime.derivedStateOf { st.value.filter { it.form != "feed" } } }
    var confirmDouble by remember { mutableStateOf<Triple<Medicine, Long?, Long?>?>(null) }
    val byId = meds.associateBy { it.id }
    val now = System.currentTimeMillis()
    LaunchedEffect(Unit) { Scheduler.reschedule(ctx) }

    fun takeAsNeeded(m: Medicine, force: Boolean) {
        scope.launch {
            val last = app.db.notes().kindSince(Kind.MED_TAKEN, now - m.minGapHours * HOUR).firstOrNull { JSONObject(it.details).optString("name") == m.name }
            if (last != null && !force) { confirmDouble = Triple(m, last.occurredAt, null); return@launch }
            val id = app.repo.addMedicineTaken(m.name)
            savedFeedback(ctx); app.speaker.say("Noted. You took ${m.name}.")
            UndoHost.show("Noted: ${m.name}") { scope.launch { app.repo.remove(listOf(id)) } }
            confirmDouble = null
        }
    }

    confirmDouble?.let { (m, at, doseId) ->
        Screen("Already taken", "You already took ${m.name}. Take again?", onHome = { nav.home() }, onBack = { confirmDouble = null }) {
            Body("You already took ${m.name}${at?.let { " at ${DoseActivity.time(it)}" } ?: ""}.", bold = true)
            Body("It is safest to wait ${m.minGapHours} hours between doses. Take again?")
            YesNo(yes = "Yes, again", no = "No, wait", onYes = {
                if (doseId != null) scope.launch { Scheduler.take(ctx, doseId, force = true); confirmDouble = null } else takeAsNeeded(m, true)
            }, onNo = { confirmDouble = null })
        }
        return
    }

    val due = doses.filter { it.status == DoseStatus.DUE || it.status == DoseStatus.SNOOZED }
    val speak = if (doses.isEmpty()) "No medicines today." else "Today: " + doses.joinToString(". ") { d -> "${DoseActivity.time(d.scheduledAt)}, ${byId[d.medicineId]?.name ?: ""}, ${statusWord(d, now)}" }
    Screen("Medicines", speak, onHome = { nav.home() }, onBack = { nav.back() }, subtitle = "What to take, and when") {
        // today, dose by dose, the same cards as Home
        if (doses.isNotEmpty()) {
            val taken = doses.count { it.status == DoseStatus.TAKEN }
            com.suryaprakash.medlog.ui.SectionHeader("Today", if (taken == doses.size) "All ${doses.size} taken" else "$taken of ${doses.size} taken", null)
            doses.forEach { d ->
                val m = byId[d.medicineId] ?: return@forEach
                DoseCard(d, m, onOpen = { nav.go(Route.MedEdit(m.id)) },
                    onTaken = { scope.launch { Scheduler.take(ctx, d.id); savedFeedback(ctx); app.speaker.say("Well done.") } },
                    onUndo = { scope.launch { Scheduler.untake(ctx, d.id) } })
            }
        }
        // taken only when needed
        val asNeeded = meds.filter { it.asNeeded }
        if (asNeeded.isNotEmpty()) {
            com.suryaprakash.medlog.ui.SectionHeader("When needed", "Only when you need one", null)
            com.suryaprakash.medlog.ui.Group {
                asNeeded.forEachIndexed { i, m ->
                    if (i > 0) com.suryaprakash.medlog.ui.GroupLine()
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        MedicinePicture(m, 52.dp); Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(m.name, fontWeight = FontWeight.SemiBold, fontSize = sc.body, color = p.ink)
                            Text(m.purpose.ifBlank { "When needed" }, fontSize = sc.small, color = p.inkSoft)
                        }
                        Text("I took one", fontSize = sc.small, fontWeight = FontWeight.SemiBold, color = p.brand,
                            modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(p.brandSoft).steady("I took ${m.name} now") { takeAsNeeded(m, false) }
                                .padding(horizontal = 14.dp, vertical = 10.dp))
                    }
                }
            }
        }
        // every medicine, to change or add
        com.suryaprakash.medlog.ui.SectionHeader("My medicines",
            if (meds.isEmpty()) "None added yet" else "${meds.size} medicine${if (meds.size == 1) "" else "s"} · tap one to change",
            if (meds.isNotEmpty()) "Add" else null, Icons.Rounded.Add) { nav.go(Route.MedEdit(null)) }
        if (meds.isEmpty()) com.suryaprakash.medlog.ui.DashedAddCard("Add a medicine") { nav.go(Route.MedEdit(null)) }
        meds.forEach { m -> MedicineCard(m) { nav.go(Route.MedEdit(m.id)) } }
    }
}

@Composable
fun MedEditScreen(nav: Nav, id: Long?) {
    MedicineFlow(nav, id)
}

@Suppress("unused")
@Composable
private fun OldMedEditScreen(nav: Nav, id: Long?) {
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
    var ocrLines by remember { mutableStateOf<List<String>>(emptyList()) }
    var reading by remember { mutableStateOf(false) }
    var photoFile by remember { mutableStateOf<File?>(null) }
    var customTime by remember { mutableStateOf("") }

    LaunchedEffect(id) {
        if (id != null) app.db.medicines().get(id)?.let { e ->
            m = e; original = e; times.clear(); times.addAll(e.times.split(",").map { it.trim() }.filter { it.isNotBlank() }); pills = e.pillsLeft?.toInt()?.toString() ?: ""
        }
    }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val f = photoFile
        if (ok && f != null) {
            m = m.copy(photoPath = f.absolutePath)
            reading = true
            scope.launch {
                val found = runCatching { Ocr.medicineGuess(ctx, Uri.fromFile(f)) }.getOrNull()
                reading = false
                if (found != null) {
                    ocrLines = found.candidates
                    if (m.name.isBlank()) m = m.copy(name = found.name ?: m.name, strength = found.strength ?: m.strength)
                    app.speaker.say(if (found.name != null) "Is this ${found.name} ${found.strength ?: ""}?" else "I couldn't read the name. Please type it.")
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

    val presets = listOf("Morning" to "08:00", "Afternoon" to "13:00", "Evening" to "18:00", "Night" to "21:00")
    Screen(if (id == null) "Add a medicine" else "Change medicine", "Take a photo of the strip, or type the name. Then choose when to take it.", onHome = { nav.home() }, onBack = { nav.back() }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MedPhoto(m.photoPath, 1.8f)
            Spacer(Modifier.width(14.dp))
            BigButton(if (m.photoPath == null) "Photo of the strip" else "New photo", Modifier.weight(1f), Tone.QUIET, icon = Icons.Rounded.CameraAlt, onClick = { takePhoto() })
        }
        if (reading) Hint("Reading the strip…")
        if (ocrLines.isNotEmpty()) {
            Hint("Tap the name if you see it:")
            FlowRowOf { ocrLines.take(6).forEach { l -> Chip(l, m.name == l) { m = m.copy(name = l) } } }
        }
        BigField("Medicine name", m.name, { m = m.copy(name = it) })
        BigField("Strength", m.strength, { m = m.copy(strength = it) }, hint = "For example 500 mg")
        Body("What kind?", bold = true)
        FlowRowOf { listOf("tablet", "capsule", "syrup", "drops", "injection", "inhaler", "cream").forEach { f -> Chip(f.replaceFirstChar { it.uppercase() }, m.form == f) { m = m.copy(form = f) } } }
        Body("How many each time?", bold = true)
        FlowRowOf { listOf("½", "1", "2", "3", "5 ml", "10 ml").forEach { a -> Chip(a, m.amount == a) { m = m.copy(amount = a) } } }
        Toggle("Only when needed", m.asNeeded, "For example painkillers or antacids") { m = m.copy(asNeeded = it) }
        if (m.asNeeded) {
            Body("Wait at least", bold = true)
            FlowRowOf { listOf(4, 6, 8, 12).forEach { h -> Chip("$h hours", m.minGapHours == h) { m = m.copy(minGapHours = h) } } }
            BigField("What is it for?", m.purpose, { m = m.copy(purpose = it) })
        } else {
            Body("When?", bold = true)
            FlowRowOf { presets.forEach { (l, t) -> Chip("$l ($t)", t in times) { if (t in times) times.remove(t) else times.add(t) } } }
            Row(verticalAlignment = Alignment.Bottom) {
                BigField("Other time (HH:MM)", customTime, { customTime = it.take(5) }, Modifier.weight(1f), keyboard = KeyboardType.Number)
                Spacer(Modifier.width(10.dp))
                BigButton("Add", Modifier.width(sc.target * 1.6f), Tone.QUIET, enabled = Regex("^([01]?\\d|2[0-3]):[0-5]\\d$").matches(customTime), onClick = {
                    val t = java.time.LocalTime.parse(customTime.padStart(5, '0')).toString(); if (t !in times) times.add(t); customTime = ""
                })
            }
            times.filter { t -> presets.none { it.second == t } }.takeIf { it.isNotEmpty() }?.let { extra -> FlowRowOf { extra.forEach { t -> Chip("$t ✕", true) { times.remove(t) } } } }
            Body("Food", bold = true)
            FlowRowOf { listOf("before" to "Before food", "after" to "After food", "with" to "With food", "any" to "Any time").forEach { (k, l) -> Chip(l, m.food == k) { m = m.copy(food = k) } } }
            BigField("For how many days? (leave empty if always)", daysCount, { daysCount = it.filter(Char::isDigit).take(3) }, keyboard = KeyboardType.Number)
        }
        BigField("How many tablets do you have? (optional)", pills, { pills = it.filter(Char::isDigit).take(4) }, keyboard = KeyboardType.Number, hint = "MedLog tells you before they run out")
        Toggle("Important medicine", m.critical, "Heart, sugar, fits, blood thinner: faster alerts to helpers if missed") { m = m.copy(critical = it) }
        Toggle("This is a blood thinner", m.bloodThinner) { m = m.copy(bloodThinner = it) }
        val valid = m.name.isNotBlank() && (m.asNeeded || times.isNotEmpty())
        if (!valid) Hint(if (m.name.isBlank()) "Please add the name." else "Please choose when to take it.")
        BigButton("Save", tone = Tone.OK, enabled = valid, height = sc.target * 1.3f, onClick = {
            scope.launch {
                val now = System.currentTimeMillis()
                val change = original?.let { o ->
                    when {
                        o.strength != m.strength -> "changed from ${o.strength.ifBlank { "?" }} to ${m.strength}"
                        o.times != times.sorted().joinToString(",") -> "times changed"
                        else -> o.changeNote
                    }
                } ?: "started"
                val saved = m.copy(
                    name = m.name.trim(), strength = m.strength.trim(),
                    times = if (m.asNeeded) "" else times.sorted().joinToString(","),
                    endDate = daysCount.toIntOrNull()?.let { now + it * DAY },
                    pillsLeft = pills.toDoubleOrNull(),
                    changedAt = if (original == null || change != original?.changeNote) now else m.changedAt,
                    changeNote = change,
                )
                val mid = if (id == null) app.db.medicines().insert(saved) else { app.db.medicines().update(saved); id }
                app.db.doses().dropFuture(mid, now)
                Scheduler.reschedule(ctx)
                app.db.medicines().get(mid)?.let { CalendarSync.syncMedicine(ctx, it) }
                savedFeedback(ctx)
                app.refreshWidgets()
                nav.back()
            }
        })
        if (id != null) BigButton("Stop this medicine", tone = Tone.SECONDARY, onClick = {
            scope.launch {
                val stopped = m.copy(active = false, changedAt = System.currentTimeMillis(), changeNote = "stopped")
                app.db.medicines().update(stopped)
                app.db.doses().dropFuture(stopped.id, System.currentTimeMillis())
                CalendarSync.removeMedicine(ctx, stopped)
                Scheduler.reschedule(ctx)
                nav.back()
            }
        })
    }
}

/** "Did I take my medicines?" (plan 4.3 #9): one tap, with times and photos. */
@Composable
fun DidITakeScreen(nav: Nav) {
    val ctx = LocalContext.current
    val app = ctx.medlog
    val p = LocalPalette.current
    val sc = LocalScale.current
    val (start, end) = remember { Scheduler.today() }
    val doses by app.db.doses().betweenFlow(start, end).collectAsState(emptyList())
    val meds by app.db.medicines().activeFlow().collectAsState(emptyList())
    val byId = meds.associateBy { it.id }
    val now = System.currentTimeMillis()
    val taken = doses.filter { it.status == DoseStatus.TAKEN }
    val pending = doses.filter { (it.status == DoseStatus.DUE || it.status == DoseStatus.SNOOZED) && it.scheduledAt <= now }
    val later = doses.filter { it.status == DoseStatus.DUE && it.scheduledAt > now }
    val say = buildString {
        if (taken.isEmpty()) append("You haven't taken any medicine yet today. ")
        else append("Yes. Today you took: " + taken.joinToString(". ") { "${byId[it.medicineId]?.name} at ${DoseActivity.time(it.actedAt ?: it.scheduledAt)}" } + ". ")
        if (pending.isNotEmpty()) append("Still to take now: " + pending.joinToString(", ") { byId[it.medicineId]?.name ?: "" } + ". ")
        if (later.isNotEmpty()) append("Later today: " + later.joinToString(", ") { "${byId[it.medicineId]?.name} at ${DoseActivity.time(it.scheduledAt)}" } + ".")
    }
    Screen("Did I take my medicines?", say, onHome = { nav.home() }, onBack = { nav.back() }) {
        Card(border = if (pending.isEmpty()) p.ok else p.amber) { Text(say, fontSize = sc.body * 1.05f, color = p.ink, fontWeight = FontWeight.Bold) }
        taken.forEach { d -> byId[d.medicineId]?.let { m -> Row(verticalAlignment = Alignment.CenterVertically) { MedPhoto(m.photoPath); Spacer(Modifier.width(12.dp)); Body("✓ ${m.name} · ${DoseActivity.time(d.actedAt ?: d.scheduledAt)}", bold = true) } } }
        if (pending.isNotEmpty()) BigButton("Take them now", tone = Tone.OK, onClick = { nav.replace(Route.Meds) })
        Spacer(Modifier.height(4.dp))
    }
}
