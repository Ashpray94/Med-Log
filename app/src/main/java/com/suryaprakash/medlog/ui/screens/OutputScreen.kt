package com.suryaprakash.medlog.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.suryaprakash.medlog.data.Kind
import com.suryaprakash.medlog.data.Note
import com.suryaprakash.medlog.medlog
import com.suryaprakash.medlog.ui.BigButton
import com.suryaprakash.medlog.ui.LocalPalette
import com.suryaprakash.medlog.ui.LocalScale
import com.suryaprakash.medlog.ui.Nav
import com.suryaprakash.medlog.ui.Route
import com.suryaprakash.medlog.ui.Screen
import com.suryaprakash.medlog.ui.SectionHeader
import com.suryaprakash.medlog.ui.Segmented
import com.suryaprakash.medlog.ui.Text
import com.suryaprakash.medlog.ui.TileGrid
import com.suryaprakash.medlog.ui.Tone
import com.suryaprakash.medlog.ui.WhenRow
import com.suryaprakash.medlog.ui.savedFeedback
import com.suryaprakash.medlog.ui.steady
import kotlinx.coroutines.launch
import androidx.compose.runtime.LaunchedEffect
import org.json.JSONObject

/**
 * Toilet and vomit, logged apart from how one feels: what it looked like, in pictures, in a few taps.
 * Stool uses the 7 types doctors use (the Bristol scale); urine and vomit, their colour. Signs of bleeding are
 * marked red and offer to tell MedLog about it, so the usual warnings and helper alerts follow.
 */
object Output {
    val STOOL = listOf(
        "Hard lumps", "Lumpy", "Cracked", "Smooth, soft", "Soft blobs", "Mushy", "Watery",
    )
    val STOOL_COLOURS = listOf("Brown" to 0xFF7A4B25, "Yellow" to 0xFFD9B23A, "Green" to 0xFF5E7D2B, "Pale" to 0xFFD9CFB8, "Black" to 0xFF1E1A18, "Red" to 0xFFB3261E)
    val URINE_COLOURS = listOf("Clear" to 0xFFF4F2DA, "Pale yellow" to 0xFFF5E27A, "Yellow" to 0xFFEAC22B, "Dark yellow" to 0xFFC98E14, "Brown" to 0xFF7A4B1E,
        "Red or pink" to 0xFFC8404A, "Cloudy" to 0xFFD8D4C4)
    val VOMIT_COLOURS = listOf("Food" to 0xFFC9A66B, "Clear" to 0xFFE6EEF0, "Yellow or green" to 0xFF9DAF3A, "Red" to 0xFFB3261E, "Dark, like coffee" to 0xFF3E2A1C)
    val AMOUNTS = listOf("A little", "Normal", "A lot")

    /** A warning sign, and the problem to tell MedLog about. */
    fun danger(type: String, colour: String?, blood: Boolean): Pair<String, String>? = when {
        type == "stool" && (colour == "Black") -> "Black stool can mean bleeding inside." to "black_stool"
        type == "stool" && (colour == "Red" || blood) -> "Blood in stool needs a doctor." to "blood_stool"
        type == "urine" && (colour == "Red or pink" || blood) -> "Blood in urine needs a doctor." to "blood_urine"
        type == "vomit" && (colour == "Red" || colour == "Dark, like coffee" || blood) -> "Blood in vomit is urgent." to "vomit_blood"
        else -> null
    }

    fun words(o: JSONObject): String = when (o.optString("type")) {
        "stool" -> "Stool: " + listOfNotNull(o.optInt("form", 0).takeIf { it > 0 }?.let { "type $it, ${STOOL[it - 1].lowercase()}" }, o.optString("colour").ifBlank { null }?.lowercase(),
            "blood".takeIf { o.optBoolean("blood") }, "pain".takeIf { o.optBoolean("pain") }).joinToString(", ")
        "urine" -> "Urine: " + listOfNotNull(o.optString("colour").ifBlank { null }?.lowercase(), o.optString("amount").ifBlank { null }?.lowercase(),
            "burning".takeIf { o.optBoolean("burning") }, "blood".takeIf { o.optBoolean("blood") }).joinToString(", ")
        else -> "Vomit: " + listOfNotNull(o.optString("colour").ifBlank { null }?.lowercase(), o.optString("amount").ifBlank { null }?.lowercase(),
            "blood".takeIf { o.optBoolean("blood") }).joinToString(", ")
    }
}

@Composable
fun OutputScreen(nav: Nav, start: Int = 0) {
    val ctx = LocalContext.current
    val app = ctx.medlog
    val p = LocalPalette.current
    val sc = LocalScale.current
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(start) }
    var form by remember { mutableStateOf(0) }
    var colour by remember { mutableStateOf<String?>(null) }
    var amount by remember { mutableStateOf<String?>(null) }
    var blood by remember { mutableStateOf(false) }
    var pain by remember { mutableStateOf(false) }
    var at by remember { mutableStateOf<Long?>(null) }
    val type = listOf("stool", "urine", "vomit")[tab]
    fun reset() { form = 0; colour = null; amount = null; blood = false; pain = false }
    val ready = when (type) { "stool" -> form > 0; else -> colour != null }
    val warn = Output.danger(type, colour, blood)
    // kept as it's tapped: closing MedLog loses nothing, and one reminder comes 30 minutes later
    LaunchedEffect(Unit) {
        com.suryaprakash.medlog.care.Drafts.get(ctx, "output")?.let { d -> runCatching {
            val o = JSONObject(d)
            tab = o.optInt("tab", start); form = o.optInt("form"); colour = o.optString("colour").ifBlank { null }
            amount = o.optString("amount").ifBlank { null }; blood = o.optBoolean("blood"); pain = o.optBoolean("pain")
            if (o.has("at")) at = o.getLong("at")
        } }
    }
    LaunchedEffect(tab, form, colour, amount, blood, pain, at) {
        if (form == 0 && colour == null && amount == null && !blood && !pain) { com.suryaprakash.medlog.care.Drafts.clear(ctx, "output"); return@LaunchedEffect }
        com.suryaprakash.medlog.care.Drafts.save(ctx, "output", "toilet note", "medlog://open?name=toilet",
            JSONObject().put("tab", tab).put("form", form).put("colour", colour ?: "").put("amount", amount ?: "").put("blood", blood).put("pain", pain).apply { at?.let { put("at", it) } }.toString())
        kotlinx.coroutines.delay(2000)
        com.suryaprakash.medlog.meds.Scheduler.reschedule(ctx)
    }

    Screen("Toilet & vomit", "Choose stool, urine or vomit, then tap what it looked like.", onHome = { nav.home() }, onBack = { nav.back() },
        subtitle = "What it looked like", actions = {
            BigButton("Done", enabled = ready, onClick = {
                val o = JSONObject().put("type", type).put("colour", colour ?: "").put("amount", amount ?: "").put("blood", blood)
                if (type == "stool") o.put("form", form).put("pain", pain)
                if (type == "urine") o.put("burning", pain)
                scope.launch {
                    app.viewDb.notes().insert(Note(kind = Kind.OUTPUT, occurredAt = at ?: System.currentTimeMillis(), details = o.toString(), text = Output.words(o),
                        triage = if (warn != null) "AMBER" else "GREEN"))
                    com.suryaprakash.medlog.care.Drafts.clear(ctx, "output")
                    savedFeedback(ctx); app.speaker.say("Saved.")
                    if (warn != null) nav.replace(Route.Tell(warn.second)) else nav.back()
                }
            })
        }) {
        Segmented(listOf("Stool", "Urine", "Vomit"), tab) { tab = it; reset() }
        WhenRow(at) { at = it }
        when (type) {
            "stool" -> {
                SectionHeader("What did it look like?", "Tap the closest picture", null)
                TileGrid((1..7).toList(), 2, aspect = 1.25f) { n, mod ->
                    val on = form == n
                    val sh = RoundedCornerShape(sc.radius)
                    Column(mod.clip(sh).background(if (on) p.brandSoft else p.card).border(if (on) 3.dp else 1.5.dp, if (on) p.brand else p.outline, sh)
                        .steady("Type $n, ${Output.STOOL[n - 1]}" + if (on) ", chosen" else "") { form = n }.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        StoolPicture(n, Modifier.fillMaxWidth().height(48.dp))
                        Spacer(Modifier.height(8.dp))
                        Text(Output.STOOL[n - 1], fontSize = sc.body, fontWeight = FontWeight.SemiBold, color = p.ink, textAlign = TextAlign.Center)
                        Text("Type $n", fontSize = sc.small, color = p.inkSoft)
                    }
                }
                Swatches("Colour", Output.STOOL_COLOURS, colour) { colour = it }
                YesNoRow("Blood seen?", blood) { blood = it }
                YesNoRow("Painful?", pain) { pain = it }
            }
            "urine" -> {
                Swatches("Colour", Output.URINE_COLOURS, colour) { colour = it }
                Amounts(amount) { amount = it }
                YesNoRow("Burning or pain?", pain) { pain = it }
                YesNoRow("Blood seen?", blood) { blood = it }
            }
            else -> {
                Swatches("What did it look like?", Output.VOMIT_COLOURS, colour) { colour = it }
                Amounts(amount) { amount = it }
                YesNoRow("Blood seen?", blood) { blood = it }
            }
        }
        warn?.let { (words, _) ->
            val sh = RoundedCornerShape(sc.radius)
            Row(Modifier.fillMaxWidth().clip(sh).background(p.card).border(2.dp, p.red, sh).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Warning, null, tint = p.red, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(12.dp))
                Text("$words Saving it asks a few quick questions.", fontSize = sc.body, fontWeight = FontWeight.SemiBold, color = p.red)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** Colour choices as big round swatches with their names. */
@Composable
private fun Swatches(title: String, colours: List<Pair<String, Long>>, chosen: String?, onPick: (String) -> Unit) {
    val p = LocalPalette.current
    val sc = LocalScale.current
    SectionHeader(title, "Tap the closest", null)
    TileGrid(colours, 3, aspect = 1f) { (name, c), mod ->
        val on = chosen == name
        com.suryaprakash.medlog.ui.PicTile(name, mod, picture = 44.dp, selected = on, onClick = { onPick(name) }) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(Color(c)).border(1.5.dp, p.inkSoft.copy(alpha = 0.5f), CircleShape))
        }
    }
}

@Composable
private fun Amounts(chosen: String?, onPick: (String) -> Unit) {
    SectionHeader("How much?", "Your best guess", null)
    Segmented(Output.AMOUNTS, Output.AMOUNTS.indexOf(chosen)) { onPick(Output.AMOUNTS[it]) }
}

/** A yes/no that reads as a statement: the answer in words, on a switch. */
@Composable
private fun YesNoRow(title: String, on: Boolean, onChange: (Boolean) -> Unit) {
    com.suryaprakash.medlog.ui.Group { com.suryaprakash.medlog.ui.SwitchRow(title, on, if (on) "Yes" else "No") { onChange(it) } }
}

/** The 7 stool types, drawn simply in brown: separate lumps, lumpy, cracked, smooth, soft blobs, mushy, watery. */
@Composable
private fun StoolPicture(n: Int, modifier: Modifier) {
    val brown = Color(0xFF7A4B25)
    Canvas(modifier) {
        val w = size.width; val h = size.height; val cy = h / 2
        when (n) {
            1 -> for (i in 0..4) drawCircle(brown, h * 0.16f, Offset(w * (0.22f + i * 0.14f), cy + (if (i % 2 == 0) -h * 0.08f else h * 0.1f)))
            2 -> { for (i in 0..5) drawCircle(brown, h * 0.24f, Offset(w * (0.25f + i * 0.1f), cy + if (i % 2 == 0) -h * 0.04f else h * 0.04f)) }
            3 -> { drawRoundRect(brown, Offset(w * 0.18f, cy - h * 0.22f), Size(w * 0.64f, h * 0.44f), CornerRadius(h * 0.22f))
                for (i in 1..4) drawLine(Color(0xFFD9C3A8), Offset(w * (0.18f + i * 0.13f), cy - h * 0.22f), Offset(w * (0.2f + i * 0.13f), cy - h * 0.02f), 3f) }
            4 -> drawRoundRect(brown, Offset(w * 0.15f, cy - h * 0.18f), Size(w * 0.7f, h * 0.36f), CornerRadius(h * 0.18f))
            5 -> for (i in 0..2) drawOval(brown, Offset(w * (0.2f + i * 0.21f), cy - h * 0.2f), Size(w * 0.17f, h * 0.4f))
            6 -> for (i in 0..6) drawCircle(brown.copy(alpha = 0.85f), h * (0.14f + (i % 3) * 0.04f), Offset(w * (0.22f + i * 0.09f), cy + (if (i % 2 == 0) -h * 0.1f else h * 0.08f)))
            else -> { drawOval(brown.copy(alpha = 0.7f), Offset(w * 0.2f, cy - h * 0.1f), Size(w * 0.6f, h * 0.32f))
                drawOval(brown.copy(alpha = 0.5f), Offset(w * 0.3f, cy - h * 0.34f), Size(w * 0.08f, h * 0.16f)); drawOval(brown.copy(alpha = 0.5f), Offset(w * 0.62f, cy - h * 0.3f), Size(w * 0.07f, h * 0.14f)) }
        }
    }
}

@Suppress("unused") private val keepStroke = Stroke(1f)
