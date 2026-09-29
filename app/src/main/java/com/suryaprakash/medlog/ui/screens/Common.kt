package com.suryaprakash.medlog.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Sos
import com.suryaprakash.medlog.ui.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.suryaprakash.medlog.clinical.Triage
import com.suryaprakash.medlog.data.Helper
import com.suryaprakash.medlog.data.Profile
import com.suryaprakash.medlog.help.Calls
import com.suryaprakash.medlog.help.Sos
import com.suryaprakash.medlog.medlog
import com.suryaprakash.medlog.pictogram.ProblemPicture
import com.suryaprakash.medlog.ui.BigButton
import com.suryaprakash.medlog.ui.Body
import com.suryaprakash.medlog.ui.Card
import com.suryaprakash.medlog.ui.Hint
import com.suryaprakash.medlog.ui.LocalPalette
import com.suryaprakash.medlog.ui.LocalScale
import com.suryaprakash.medlog.ui.LocalSettings
import com.suryaprakash.medlog.ui.Nav
import com.suryaprakash.medlog.ui.Route
import com.suryaprakash.medlog.ui.Screen
import com.suryaprakash.medlog.ui.Title
import com.suryaprakash.medlog.ui.Tone
import com.suryaprakash.medlog.ui.steady
import androidx.compose.foundation.layout.size

/** Tele-MANAS: India's free 24-hour mental health line. */
const val MENTAL_HEALTH_LINE = "14416"

/**
 * The danger screen (plan 10). Help first: call buttons on top, reasons below. Spoken slowly.
 * Helpers have already been messaged by the time this shows.
 */
@Composable
fun DangerScreen(nav: Nav, t: Triage, onChange: () -> Unit) {
    val ctx = LocalContext.current
    val app = ctx.medlog
    val p = LocalPalette.current
    val sc = LocalScale.current
    val s = LocalSettings.current
    var helpers by remember { mutableStateOf<List<Helper>>(emptyList()) }
    var cancerDoctor by remember { mutableStateOf<com.suryaprakash.medlog.data.CarePlan.Doctor?>(null) }
    LaunchedEffect(Unit) {
        helpers = app.db.helpers().all().filter { it.sos }
        cancerDoctor = app.repo.cancerDoctor()   // B58: on cancer treatment, the cancer team comes before the ambulance
    }
    if (t.mentalHealth) {
        Screen("You are not alone", "Thank you for telling me. You matter. Please talk to someone now. You can call the free helpline, $MENTAL_HEALTH_LINE, any time, day or night.", onHome = { nav.home() }) {
            Card() {
                Body("Thank you for telling me. You matter.", bold = true)
                Body("Talking to someone helps. You can call the free helpline any time, day or night.")
            }
            BigButton("Call helpline $MENTAL_HEALTH_LINE", icon = Icons.Rounded.Call, height = sc.target * 1.4f, onClick = { Calls.call(ctx, MENTAL_HEALTH_LINE) })
            helpers.firstOrNull()?.let { h -> BigButton("Call ${h.name}", tone = Tone.SECONDARY, icon = Icons.Rounded.Call, onClick = { Calls.call(ctx, h.phone) }) }
            BigButton("I'm safe for now", tone = Tone.QUIET, onClick = { nav.home() })
        }
        return
    }
    val say = (t.firstAid?.let { "$it " } ?: "") + t.say + " " + t.reasons.joinToString(". ") + ". Call ${s.emergencyNumber} now." +
        if (helpers.isNotEmpty()) " Your helpers have been sent a message." else ""
    Screen("Get help now", say, onHome = { nav.home() }) {
        Text(t.say, color = p.red, fontSize = sc.headline, fontWeight = FontWeight.Bold, lineHeight = sc.headline * 1.25f)
        cancerDoctor?.let { d ->
            Body("Call your cancer team now", bold = true)
            BigButton("Call ${d.name}, your cancer doctor", icon = Icons.Rounded.Call, height = sc.target * 1.4f, onClick = { Calls.call(ctx, d.phone) })
        }
        CallAmbulanceCard(s.emergencyNumber)
        t.firstAid?.let {
            Card(color = p.card, border = p.red) {
                Text("WHILE YOU WAIT", fontSize = sc.small, fontWeight = FontWeight.Bold, color = p.red)
                Body(it, bold = true)
            }
        }
        AlertFamilyCard(helpers.size) { Sos.start(ctx, "Danger sign: " + t.reasons.firstOrNull().orEmpty(), countdown = false) }
        if (helpers.isNotEmpty()) HelperCalls(helpers)
        SectionLabel("Why")
        Card(color = p.card) {
            t.reasons.forEach { r ->
                Row(verticalAlignment = Alignment.Top) {
                    androidx.compose.foundation.layout.Box(Modifier.padding(top = 9.dp).size(8.dp).clip(androidx.compose.foundation.shape.CircleShape).background(p.red))
                    Spacer(Modifier.width(12.dp))
                    Body(r)
                }
            }
            if (helpers.isNotEmpty()) Hint("Your helpers have been sent a message.")
        }
        BigButton("This is wrong – change it", tone = Tone.QUIET, onClick = onChange)
    }
}

/** One tap to call the right doctor: the one whose speciality fits [dept], else the family doctor. */
@Composable
fun DoctorCallButton(dept: String? = null) {
    val ctx = LocalContext.current
    var profile by remember { mutableStateOf<Profile?>(null) }
    LaunchedEffect(Unit) { profile = ctx.medlog.repo.profile() }
    val pr = profile ?: return
    val plan = com.suryaprakash.medlog.data.CarePlan.parse(pr.plan)
    val d = plan.doctorFor(dept, com.suryaprakash.medlog.clinical.DangerRules.cancerCareOf(pr.conditions, plan.treatments))
    val (name, phone) = if (d != null && d.phone.isNotBlank()) d.name to d.phone else pr.doctorName.ifBlank { "my doctor" } to pr.doctorPhone
    if (phone.isNotBlank()) BigButton("Call $name", icon = Icons.Rounded.Call, sub = d?.speciality, onClick = { Calls.call(ctx, phone) })
    else Hint("Add your doctors in Settings to call them with one tap.")
}

/**
 * "How are you feeling?": tap first. Search is always at the top (type, or tap Speak for the phone's own speech
 * typing). Below it, the person's own problems (only what they really logged), then suggestions that fit them
 * (setup, conditions, age, time of day), then everything by body area.
 */
@Composable
fun PickProblem(nav: Nav, onPicked: (String) -> Unit, onBack: () -> Unit, title: String = "How are you feeling?") {
    val ctx = LocalContext.current
    val app = ctx.medlog
    val cat = app.catalogue
    var query by remember { mutableStateOf("") }
    var yours by remember { mutableStateOf<List<String>>(emptyList()) }
    var suggested by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(Unit) {
        val profile = app.repo.profile()
        val plan = com.suryaprakash.medlog.data.CarePlan.parse(profile.plan)
        val history = app.db.notes().symptomsSince(System.currentTimeMillis() - 180 * com.suryaprakash.medlog.data.DAY)
            .mapNotNull { n -> n.problemId?.let { com.suryaprakash.medlog.clinical.Suggest.Logged(it, n.occurredAt) } }
        val r = com.suryaprakash.medlog.clinical.Suggest.rank(history, app.repo.ageYears(profile.dob),
            profile.conditions.split(",").map { it.trim() }.filter { it.isNotEmpty() }, plan.symptoms, java.time.LocalTime.now().hour,
            known = { cat.problem(it) != null })
        yours = r.yours; suggested = r.suggested
    }
    com.suryaprakash.medlog.pictogram.Sprites.init(ctx)
    Screen(title, "Tap the one that matches, or search.", onHome = { nav.home() }, onBack = onBack) {
        com.suryaprakash.medlog.ui.SearchBox(query, { query = it }, "Search problems")
        if (query.isNotBlank()) {
            val found = remember(query) { searchProblems(app, query) }
            if (found.isEmpty()) Hint("Nothing found for \"$query\". Try a simpler word, like pain, fever or cough.")
            else ProblemGrid(found, onPicked)
            return@Screen
        }
        if (yours.isNotEmpty()) {
            com.suryaprakash.medlog.ui.Title("You told me before")
            ProblemGrid(yours, onPicked)
        }
        if (suggested.isNotEmpty()) {
            com.suryaprakash.medlog.ui.Title(if (yours.isEmpty()) "Common for you" else "Others you may have")
            ProblemGrid(suggested, onPicked)
        }
        com.suryaprakash.medlog.pictogram.Sprites.SECTIONS.forEach { (title, ids) ->
            com.suryaprakash.medlog.ui.Title(title)
            ProblemGrid(ids.filter { cat.problem(it) != null }, onPicked)
        }
    }
}

/** Problems whose name or everyday words match what was typed or spoken (in English or the person's language). */
fun searchProblems(app: com.suryaprakash.medlog.MedLogApp, q: String): List<String> {
    val words = com.suryaprakash.medlog.nlu.Normalize.text(q)
    if (words.isBlank()) return emptyList()
    // what the sentence parser understands first ("my head hurts since morning"), then plain name matches
    val parsed = runCatching { app.parser.parse(q).let { listOfNotNull(it.main?.problemId) + it.others.map { m -> m.problemId } } }.getOrDefault(emptyList())
    val low = q.trim().lowercase()
    val byName = app.catalogue.problems.filter { p ->
        p.label.lowercase().contains(words) || p.synonyms.any { it.lowercase().contains(words) || words.contains(it.lowercase()) } ||
            com.suryaprakash.medlog.ui.tr(p.label).lowercase().contains(low)
    }.map { it.id }
    return (parsed + byName).distinct().take(12)
}

@Composable
fun ProblemGrid(ids: List<String>, onPick: (String) -> Unit) {
    val ctx = LocalContext.current
    val cat = ctx.medlog.catalogue
    val p = LocalPalette.current
    val sc = LocalScale.current
    com.suryaprakash.medlog.ui.TileGrid(ids, if (sc.big) 2 else 3, aspect = 0.9f) { id, m ->
        val label = cat.problem(id)?.label ?: id
        com.suryaprakash.medlog.ui.Tile(label, m, onClick = { onPick(id) }) {
            com.suryaprakash.medlog.pictogram.SpriteIcon(id, if (sc.big) sc.target * 1.6f else sc.target * 1.45f)
            Spacer(Modifier.size(6.dp))
            Text(label, fontSize = sc.small, fontWeight = FontWeight.SemiBold, color = p.ink, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                maxLines = 2, minLines = 2, lineHeight = sc.small * 1.15f, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
    }
}
