package com.suryaprakash.medlog.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.LocalDrink
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Sos
import androidx.compose.material.icons.rounded.Wc
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.suryaprakash.medlog.clinical.DangerRules
import com.suryaprakash.medlog.clinical.Level
import com.suryaprakash.medlog.clinical.Triage
import com.suryaprakash.medlog.data.DoseStatus
import com.suryaprakash.medlog.data.Kind
import com.suryaprakash.medlog.data.Note
import com.suryaprakash.medlog.medlog
import com.suryaprakash.medlog.nutrition.Foods
import com.suryaprakash.medlog.pictogram.SpriteIcon
import com.suryaprakash.medlog.ui.BigButton
import com.suryaprakash.medlog.ui.BigField
import com.suryaprakash.medlog.ui.LocalPalette
import com.suryaprakash.medlog.ui.LocalScale
import com.suryaprakash.medlog.ui.Nav
import com.suryaprakash.medlog.ui.OptionIcon
import com.suryaprakash.medlog.ui.Route
import com.suryaprakash.medlog.ui.Screen
import com.suryaprakash.medlog.ui.SectionHeader
import com.suryaprakash.medlog.ui.Text
import com.suryaprakash.medlog.ui.Tone
import com.suryaprakash.medlog.ui.UndoHost
import com.suryaprakash.medlog.ui.cardTitle
import com.suryaprakash.medlog.ui.savedFeedback
import com.suryaprakash.medlog.ui.steady
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * "Tell it all at once": whatever is said is sorted, on the phone, into how one feels, toilet and vomit, food and
 * water, medicines taken and readings, and saved straight away. Anything that sounds like an emergency tells the
 * helpers at once, with no questions first. Each thing saved can then be opened to change it.
 */
object SpeakSort {
    data class Saved(val kind: String, val title: String, val sub: String, val noteId: Long?, val route: Route?)
    data class Result(val saved: List<Saved>, val urgent: String?)

    private val NUM = mapOf("a" to 1, "an" to 1, "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6, "ek" to 1, "do" to 2, "teen" to 3)

    suspend fun sort(ctx: Context, raw: String): Result {
        val app = ctx.medlog
        val repo = app.viewRepo
        val db = app.viewDb
        val text = raw.trim()
        val lower = " " + text.lowercase() + " "
        val meds = db.medicines().all().filter { it.active }
        val parsed = app.parser.parse(text, myMedicines = meds.map { it.name })
        val at = parsed.occurredAt ?: System.currentTimeMillis()
        val out = ArrayList<Saved>()
        var urgent: String? = null
        val plan = com.suryaprakash.medlog.data.CarePlan.parse(repo.profile().plan)

        // ── toilet and vomit ──
        fun near(word: String, what: Regex) = Regex("$word[^.,]{0,25}").findAll(lower).any { what.containsMatchIn(it.value) } ||
            Regex("[^.,]{0,25}$word").findAll(lower).any { what.containsMatchIn(it.value) }
        val blood = Regex("blood|bleed|red")
        val outputs = ArrayList<JSONObject>()
        if (Regex("\\b(stool|stools|poo|potty|motion|motions|bowel|latrine|toilet)\\b").containsMatchIn(lower)) {
            val o = JSONObject().put("type", "stool")
            when {
                Regex("loose|watery|diarr").containsMatchIn(lower) -> o.put("form", 7)
                Regex("hard|constip").containsMatchIn(lower) -> o.put("form", 1)
                else -> o.put("form", 4)
            }
            if (near("(stool|motion|potty|poo)", Regex("black"))) o.put("colour", "Black")
            o.put("blood", near("(stool|motion|potty|poo)", blood))
            outputs += o
        }
        if (Regex("\\b(urine|pee|peed|peeing|urinat\\w*)\\b").containsMatchIn(lower)) {
            val o = JSONObject().put("type", "urine").put("burning", Regex("burn|pain").containsMatchIn(lower))
            if (near("(urine|pee)", Regex("dark|brown"))) o.put("colour", "Dark yellow")
            o.put("blood", near("(urine|pee)", blood))
            outputs += o
        }
        if (Regex("\\b(vomit\\w*|threw up|throwing up|ulti)\\b").containsMatchIn(lower)) {
            outputs += JSONObject().put("type", "vomit").put("blood", near("(vomit\\w*|threw up|ulti)", blood))
        }
        for (o in outputs) {
            val warn = Output.danger(o.optString("type"), o.optString("colour").ifBlank { null }, o.optBoolean("blood"))
            val id = db.notes().insert(Note(kind = Kind.OUTPUT, occurredAt = at, details = o.toString(), text = Output.words(o), transcript = text,
                triage = if (warn != null) "AMBER" else "GREEN"))
            if (warn != null) urgent = urgent ?: warn.first
            out += Saved(Kind.OUTPUT, Output.words(o).substringBefore(":"), Output.words(o).substringAfter(": ", ""), id, Route.Output())
        }

        // ── how one feels (toilet words are logged above, not again as symptoms) ──
        // toilet and vomit problems are logged in their own place above, not again as symptoms
        val toiletIds = mapOf("loose_motions" to "stool", "constipation" to "stool", "blood_stool" to "stool", "black_stool" to "stool",
            "burning_urine" to "urine", "frequent_urine" to "urine", "blood_urine" to "urine", "vomiting" to "vomit", "vomit_blood" to "vomit")
        val loggedTypes = outputs.map { it.optString("type") }.toSet()
        val symptomMentions = parsed.mentions.filter { !it.negated && toiletIds[it.problemId] !in loggedTypes }
        if (symptomMentions.isNotEmpty()) {
            val first = symptomMentions.first()
            val t: Triage = DangerRules.evaluate(first.problemId, first.facts, parsed.readings, repo.recentForRules(), repo.person())
            val ids = repo.saveTold(symptomMentions, text, at, t, emptyList(), emptyList(), null)
            symptomMentions.forEachIndexed { i, m ->
                val label = app.catalogue.problem(m.problemId)?.label ?: m.problemId
                out += Saved(Kind.SYMPTOM, label, "", ids.getOrNull(i), ids.getOrNull(i)?.let { Route.Tell(noteId = it, problemId = m.problemId) })
            }
            val emergency = symptomMentions.firstOrNull { it.problemId in plan.emergencies }
            if (t.level == Level.RED) urgent = urgent ?: (t.reasons.firstOrNull() ?: t.say)
            if (emergency != null) urgent = urgent ?: (app.catalogue.problem(emergency.problemId)?.label ?: "An emergency")
            ids.forEach { com.suryaprakash.medlog.care.FollowUp.schedule(ctx, it) }
        }

        // ── readings ──
        for (r in parsed.readings) {
            val id = repo.addReading(r, text, at)
            out += Saved(Kind.READING, r.label(), "", id, Route.Readings)
            val t = DangerRules.evaluate(null, emptyMap(), listOf(r), emptyList(), repo.person())
            if (t.level == Level.RED) urgent = urgent ?: r.label()
        }

        // ── medicines taken: tick today's dose if there is one, else note it ──
        for (name in parsed.medicinesTaken) {
            val m = meds.firstOrNull { it.name.equals(name, true) }
            val (s0, e0) = com.suryaprakash.medlog.meds.Scheduler.today()
            val dose = m?.let { mm -> db.doses().between(s0, e0).filter { it.medicineId == mm.id && it.status != DoseStatus.TAKEN }.minByOrNull { kotlin.math.abs(it.scheduledAt - at) } }
            if (dose != null) com.suryaprakash.medlog.data.Doses.take(ctx, dose.id) else repo.addMedicineTaken(name, at)
            out += Saved(Kind.MED_TAKEN, "Took $name", "", null, Route.Meds)
        }

        // ── water ──
        Regex("\\b(\\d+|a|an|one|two|three|four|five|six|ek|do|teen)\\s+(glass|glasses|cup|cups|tumbler)s?\\s+(of\\s+)?(water|paani|pani|thanni)").findAll(lower).forEach { m ->
            val n = m.groupValues[1].toIntOrNull() ?: NUM[m.groupValues[1]] ?: 1
            val id = repo.addWater(n, at)
            out += Saved(Kind.WATER, "Water", "$n glass${if (n == 1) "" else "es"}", id, Route.Food)
        }

        // ── food: what follows "ate", "had", "breakfast was" …, matched to known dishes only ──
        val foodBits = Regex("(ate|had|have eaten|eaten|breakfast|lunch|dinner|khaya|khana|saapten|sapten)\\s+([^.;]+)").findAll(lower).map { it.groupValues[2] }.toList()
        val custom = Foods.customFrom(app.settings.getString("custom_foods"))
        val items = foodBits.flatMap { Foods.parse(it, custom) }.filter { it.food != null && !it.estimated }
            .filter { it.food!!.name != "water" }.distinctBy { it.food!!.name }
        if (items.isNotEmpty()) {
            val portions = items.map { Foods.Portion(it.food!!, if (it.food.measure == Foods.Measure.PIECE) it.qty else it.food.start * it.qty) }
            val words = portions.joinToString(", ") { "${it.food.name} ${it.words}" }
            val id = repo.addFood(words, null, at)
            db.notes().get(id)?.let { n -> db.notes().update(n.copy(details = Foods.portionsJson(portions))) }
            out += Saved(Kind.FOOD, portions.first().food.name.replaceFirstChar(Char::uppercase),
                listOfNotNull(portions.first().words, portions.drop(1).joinToString(", ") { it.food.name }.ifBlank { null }?.let { "with $it" }).joinToString(" · "), id, Route.FoodPick(id))
        }

        // ── something urgent: helpers are told now; no questions first ──
        urgent?.let { u ->
            val t = Triage(Level.RED, "Your helpers are being told.", listOf(u))
            com.suryaprakash.medlog.help.Alerts.dangerToHelpers(ctx, u, t)
        }
        app.refreshWidgets()
        return Result(out, urgent)
    }
}

@Composable
fun SpeakAllScreen(nav: Nav, start: String? = null) {
    val ctx = LocalContext.current
    val p = LocalPalette.current
    val sc = LocalScale.current
    val scope = rememberCoroutineScope()
    var typed by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<SpeakSort.Result?>(null) }
    var busy by remember { mutableStateOf(false) }
    fun go(t: String) { if (t.isBlank()) return; busy = true; scope.launch { result = SpeakSort.sort(ctx, t); busy = false; savedFeedback(ctx) } }
    val dictate = com.suryaprakash.medlog.ui.rememberDictation("Say everything: how you feel, what you ate, toilet, medicines") { go(it) }
    LaunchedEffect(start) { if (!start.isNullOrBlank() && result == null) go(start) }
    val r = result
    if (r == null) {
        Screen("Tell it all", "Tap Speak and say everything: how you feel, what you ate, toilet, medicines. MedLog puts each thing in its place.", onHome = { nav.home() }, onBack = { if (!nav.back()) nav.home() },
            subtitle = "Say everything at once") {
            val hsh = RoundedCornerShape(sc.radius + 4.dp)
            Column(Modifier.fillMaxWidth().heightIn(min = 180.dp).clip(hsh).background(p.brand).steady("Speak") { dictate?.invoke() }.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(Icons.Rounded.Mic, null, tint = Color.White, modifier = Modifier.size(56.dp))
                Spacer(Modifier.size(10.dp))
                Text(if (busy) "One moment…" else "Speak", fontSize = sc.title, fontWeight = FontWeight.Bold, color = Color.White)
            }
            SectionHeader("For example", "Say it your way", null)
            Text("\"Fell in the bathroom, knee hurts. Had two idlis and coffee. Loose motion twice. BP 150 by 90.\"", fontSize = sc.body, color = p.inkSoft)
            SectionHeader("Or write it", "Then tap Done", null)
            BigField("Everything, in your words", typed, { typed = it }, lines = 3)
            if (typed.isNotBlank()) BigButton("Done", onClick = { go(typed) })
        }
        return
    }
    Screen("Saved", "Here's what MedLog noted. Tap any to change it.", onHome = { nav.home() }, onBack = { if (!nav.back()) nav.home() },
        subtitle = if (r.saved.isEmpty()) "Nothing was understood" else "${r.saved.size} thing${if (r.saved.size == 1) "" else "s"} noted", actions = {
            BigButton("Done", onClick = { nav.home() })
        }) {
        r.urgent?.let { u ->
            val sh = RoundedCornerShape(sc.radius)
            Column(Modifier.fillMaxWidth().clip(sh).background(p.red).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(u, fontSize = sc.headline, fontWeight = FontWeight.Bold, color = Color.White)
                Text("Your helpers are being told now.", fontSize = sc.body, color = Color.White)
                BigButton("SOS – call for help", tone = Tone.SECONDARY, icon = Icons.Rounded.Sos, onClick = { com.suryaprakash.medlog.help.Sos.start(ctx, "Emergency: $u") })
            }
        }
        if (r.saved.isEmpty()) {
            Text("Try again, naming each thing: \"headache\", \"ate rice and dal\", \"urine burning\".", fontSize = sc.body, color = p.inkSoft)
            BigButton("Speak again", tone = Tone.TINT, icon = Icons.Rounded.Mic, onClick = { result = null })
        }
        val order = listOf(Kind.SYMPTOM, Kind.OUTPUT, Kind.FOOD, Kind.WATER, Kind.MED_TAKEN, Kind.READING)
        r.saved.sortedBy { order.indexOf(it.kind) }.forEach { s ->
            val (icon, tint) = kindLook(s.kind, p)
            val sh = RoundedCornerShape(sc.radius)
            Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).clip(sh).background(p.card).border(1.dp, p.line, sh)
                .steady("${s.title}. Tap to change") { s.route?.let { nav.go(it) } }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (s.kind == Kind.SYMPTOM) (s.route as? Route.Tell)?.problemId?.let { SpriteIcon(it, 48.dp) } ?: OptionIcon(icon, tint, 48.dp) else OptionIcon(icon, tint, 48.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(s.title, fontSize = sc.cardTitle, fontWeight = FontWeight.Bold, color = p.ink)
                    Text(listOf(kindName(s.kind).takeIf { it != s.title }.orEmpty(), s.sub).filter { it.isNotBlank() }.joinToString(" · "), fontSize = sc.small, color = p.inkSoft, maxLines = 2)
                }
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = p.inkSoft, modifier = Modifier.size(24.dp))
            }
        }
        if (r.saved.isNotEmpty()) BigButton("Remove all of these", tone = Tone.OUTLINE, onClick = {
            scope.launch {
                val ids = r.saved.mapNotNull { it.noteId }
                ctx.medlog.viewRepo.remove(ids)
                UndoHost.show("Removed.") { scope.launch { ctx.medlog.viewRepo.restore(ids) } }
                nav.back()
            }
        })
    }
}

private fun kindName(k: String) = when (k) { Kind.SYMPTOM -> "How I feel"; Kind.OUTPUT -> "Toilet & vomit"; Kind.FOOD -> "Food"; Kind.WATER -> "Water"; Kind.MED_TAKEN -> "Medicine"; else -> "Reading" }
private fun kindLook(k: String, p: com.suryaprakash.medlog.ui.Palette): Pair<ImageVector, Color> = when (k) {
    Kind.OUTPUT -> Icons.Rounded.Wc to p.tintTeal; Kind.FOOD -> Icons.Rounded.Restaurant to p.tintGreen; Kind.WATER -> Icons.Rounded.LocalDrink to p.tintBlue
    Kind.MED_TAKEN -> Icons.Rounded.Medication to p.tintOrange; else -> Icons.Rounded.MonitorHeart to p.tintPink
}
