package com.suryaprakash.medlog.ui.screens

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Mic
import com.suryaprakash.medlog.ui.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.suryaprakash.medlog.clinical.DangerRules
import com.suryaprakash.medlog.clinical.Level
import com.suryaprakash.medlog.clinical.Triage
import com.suryaprakash.medlog.data.Kind
import com.suryaprakash.medlog.help.Alerts
import com.suryaprakash.medlog.medlog
import com.suryaprakash.medlog.nlu.Reading
import com.suryaprakash.medlog.ui.BigButton
import com.suryaprakash.medlog.ui.BigField
import com.suryaprakash.medlog.ui.Body
import com.suryaprakash.medlog.ui.Card
import com.suryaprakash.medlog.ui.Chip
import com.suryaprakash.medlog.ui.FlowRowOf
import com.suryaprakash.medlog.ui.Hint
import com.suryaprakash.medlog.ui.LocalPalette
import com.suryaprakash.medlog.ui.LocalScale
import com.suryaprakash.medlog.ui.LocalSettings
import com.suryaprakash.medlog.ui.Nav
import com.suryaprakash.medlog.ui.Perms
import com.suryaprakash.medlog.ui.Screen
import com.suryaprakash.medlog.ui.Title
import com.suryaprakash.medlog.ui.Tone
import com.suryaprakash.medlog.ui.UndoHost
import com.suryaprakash.medlog.ui.savedFeedback
import kotlinx.coroutines.launch
import java.io.File

/** Food & water (plan 12.3): one big "+ glass" button, food by voice or photo. */
@Composable
fun FoodScreen(nav: Nav) {
    val ctx = LocalContext.current
    val app = ctx.medlog
    val p = LocalPalette.current
    val sc = LocalScale.current
    val s = LocalSettings.current
    val scope = rememberCoroutineScope()
    var water by remember { mutableStateOf(0) }
    var food by remember { mutableStateOf("") }
    var photo by remember { mutableStateOf<File?>(null) }
    var pending by remember { mutableStateOf<File?>(null) }
    var listening by remember { mutableStateOf(false) }
    val (start, _) = remember { com.suryaprakash.medlog.meds.Scheduler.today() }
    val todayFood by app.db.notes().kindSinceFlow(Kind.FOOD, start).collectAsState(emptyList())
    LaunchedEffect(Unit) { water = app.repo.waterToday() }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) photo = pending }

    Screen("Food & water", "Tap plus one glass each time you drink water. You have had $water of ${s.waterGoal} glasses today.", onHome = { nav.home() }, onBack = { nav.back() }) {
        Card {
            Text("💧 $water of ${s.waterGoal} glasses today", fontSize = sc.title, fontWeight = FontWeight.Bold, color = p.ink)
            BigButton("+ 1 glass of water", icon = Icons.Rounded.Add, height = sc.target * 1.5f, onClick = {
                scope.launch {
                    val id = app.repo.addWater(1); water = app.repo.waterToday(); savedFeedback(ctx)
                    UndoHost.show("Added a glass.") { scope.launch { app.repo.remove(listOf(id)); water = app.repo.waterToday() } }
                }
            })
        }
        Title("What did you eat?")
        BigField("Food", food, { food = it }, hint = "For example: two idlis and coffee")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val speak = com.suryaprakash.medlog.ui.rememberDictation("What did you eat?") { food = it }
            if (speak != null) BigButton("Speak", Modifier.weight(1f), Tone.QUIET, icon = Icons.Rounded.Mic, onClick = speak)
            BigButton("Photo", Modifier.weight(1f), Tone.QUIET, icon = Icons.Rounded.CameraAlt, onClick = {
                val f = File(File(ctx.filesDir, "photos").apply { mkdirs() }, "food_${System.currentTimeMillis()}.jpg"); pending = f
                camera.launch(FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f))
            })
        }
        photo?.let { f -> remember(f) { BitmapFactory.decodeFile(f.absolutePath, BitmapFactory.Options().apply { inSampleSize = 4 }) }?.let { Image(it.asImageBitmap(), "Food photo", Modifier.fillMaxWidth().height(sc.target * 3).clip(RoundedCornerShape(18.dp)), contentScale = ContentScale.Crop) } }
        BigButton("Save", tone = Tone.OK, enabled = food.isNotBlank() || photo != null, onClick = {
            scope.launch { app.repo.addFood(food.trim(), photo?.absolutePath); food = ""; photo = null; savedFeedback(ctx); app.speaker.say("Saved.") }
        })
        if (todayFood.isNotEmpty()) { Hint("Today"); todayFood.forEach { n -> Body("${timeLabel(n.occurredAt)} · ${n.transcript ?: "photo"}") } }
    }
}

/** BP, sugar, oxygen, temperature, weight, pulse: said or typed, checked against danger signs. */
@Composable
fun ReadingsScreen(nav: Nav) {
    val ctx = LocalContext.current
    val app = ctx.medlog
    val p = LocalPalette.current
    val sc = LocalScale.current
    val scope = rememberCoroutineScope()
    var type by remember { mutableStateOf<String?>(null) }
    var v1 by remember { mutableStateOf("") }
    var v2 by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<Triage?>(null) }
    var listening by remember { mutableStateOf(false) }
    val recent by app.db.notes().kindSinceFlow(Kind.READING, System.currentTimeMillis() - 14 * 24 * 3600_000L).collectAsState(emptyList())

    result?.let { t ->
        if (t.level == Level.RED) { DangerScreen(nav, t) { result = null }; return }
    }

    fun save(r: Reading) {
        scope.launch {
            app.repo.addReading(r, null)
            val problem = when (r.type) { "bp" -> if (r.v1 < 100) "low_bp" else "high_bp"; "sugar" -> if (r.v1 < 100) "low_sugar" else "high_sugar"; "spo2" -> "low_oxygen"; "temp" -> "fever"; else -> null }
            val t = DangerRules.evaluate(problem?.takeIf { r.type != "temp" || r.v1 >= 100.4 }, emptyMap(), listOf(r), emptyList(), app.repo.person())
            savedFeedback(ctx)
            if (t.level == Level.RED) Alerts.dangerToHelpers(ctx, r.label(), t)
            result = t
            app.speaker.say("Saved. ${r.label()}. " + if (t.level == Level.GREEN) "" else t.say + " " + t.reasons.joinToString(". "))
            v1 = ""; v2 = ""; type = null
        }
    }

    Screen("BP, sugar & more", "Choose what you measured, then type the number, or speak it.", onHome = { nav.home() }, onBack = { if (type != null) type = null else nav.back() }) {
        result?.takeIf { it.level == Level.AMBER }?.let { t -> Card(border = p.amber) { Text("▲ " + t.say, color = p.amber, fontWeight = FontWeight.Bold, fontSize = sc.body); t.reasons.forEach { Body("• $it") }; t.firstAid?.let { Body(it, bold = true) } }; DoctorCallButton() }
        val speak = com.suryaprakash.medlog.ui.rememberDictation("For example: BP 140 by 90") { t ->
            val rs = app.parser.readings(com.suryaprakash.medlog.nlu.Normalize.text(t))
            if (rs.isEmpty()) app.speaker.say("I didn't catch a number. Please try again or type it.") else rs.forEach { save(it) }
        }
        if (speak != null) BigButton("Speak the reading", tone = Tone.QUIET, icon = Icons.Rounded.Mic, sub = "For example: BP 140 by 90", onClick = speak)
        val types = listOf("bp" to "Blood pressure", "sugar" to "Sugar", "spo2" to "Oxygen", "temp" to "Temperature", "pulse" to "Pulse", "weight" to "Weight")
        FlowRowOf { types.forEach { (k, l) -> Chip(l, type == k) { type = k; result = null } } }
        when (type) {
            "bp" -> {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BigField("Top number", v1, { v1 = it.filter(Char::isDigit).take(3) }, Modifier.weight(1f), keyboard = androidx.compose.ui.text.input.KeyboardType.Number)
                    BigField("Bottom number", v2, { v2 = it.filter(Char::isDigit).take(3) }, Modifier.weight(1f), keyboard = androidx.compose.ui.text.input.KeyboardType.Number)
                }
                val s = v1.toDoubleOrNull(); val d = v2.toDoubleOrNull()
                val ok = s != null && d != null && s in 60.0..260.0 && d in 30.0..160.0 && s > d
                if (v1.isNotEmpty() && v2.isNotEmpty() && !ok) Hint("Those numbers look wrong. Please check.")
                BigButton("Save", tone = Tone.OK, enabled = ok, onClick = { save(Reading("bp", s!!, d, "mmHg")) })
            }
            null -> {}
            else -> {
                val (unit, range) = when (type) { "sugar" -> "mg/dL" to 20.0..600.0; "spo2" -> "%" to 50.0..100.0; "temp" -> "°F" to 93.0..110.0; "pulse" -> "per minute" to 30.0..220.0; else -> "kg" to 20.0..250.0 }
                NumberPad(unit, allowDecimal = type == "temp" || type == "weight", range = range) { v -> save(Reading(type!!, v, unit = unit)) }
            }
        }
        if (recent.isNotEmpty()) { Title("Last 2 weeks"); recent.take(12).forEach { n -> Body("${dayLabel(n.occurredAt)} ${timeLabel(n.occurredAt)} · ${n.text}") } }
    }
}
