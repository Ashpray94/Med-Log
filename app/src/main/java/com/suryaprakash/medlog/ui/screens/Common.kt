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
import androidx.compose.material.icons.rounded.Mic
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
    LaunchedEffect(Unit) { helpers = app.db.helpers().all().filter { it.sos } }
    if (t.mentalHealth) {
        Screen("You are not alone", "Thank you for telling me. You matter. Please talk to someone now. You can call the free helpline, $MENTAL_HEALTH_LINE, any time, day or night.", onHome = { nav.home() }) {
            Card(color = p.brandSoft) {
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

/** One tap to call the doctor (from the profile), shown with amber advice. */
@Composable
fun DoctorCallButton() {
    val ctx = LocalContext.current
    var profile by remember { mutableStateOf<Profile?>(null) }
    LaunchedEffect(Unit) { profile = ctx.medlog.repo.profile() }
    val pr = profile ?: return
    if (pr.doctorPhone.isNotBlank()) BigButton("Call ${pr.doctorName.ifBlank { "my doctor" }}", icon = Icons.Rounded.Call, onClick = { Calls.call(ctx, pr.doctorPhone) })
    else Hint("Add your doctor's number in Settings to call with one tap.")
}

/**
 * Choosing a problem by picture (plan 5, screen 5). One list, no "where is it?" step:
 * recent problems first, then the 70 common ones under three plain headings, each a big icon and a word.
 * Anything not here can simply be said.
 */
@Composable
fun PickProblem(nav: Nav, onPicked: (String) -> Unit, onBack: () -> Unit, onSay: (() -> Unit)? = null, title: String = "What's wrong?") {
    val ctx = LocalContext.current
    val app = ctx.medlog
    val sc = LocalScale.current
    var recent by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(Unit) { recent = app.repo.recentProblems(6).map { it.problemId }.filter { app.catalogue.problem(it) != null } }
    com.suryaprakash.medlog.pictogram.Sprites.init(ctx)
    Screen(title, "Tap the picture that matches.", onHome = { nav.home() }, onBack = onBack) {
        if (onSay != null) BigButton("Say it instead", tone = Tone.QUIET, icon = androidx.compose.material.icons.Icons.Rounded.Mic, onClick = onSay)
        if (recent.isNotEmpty()) {
            com.suryaprakash.medlog.ui.Title("Recent")
            ProblemGrid(recent, onPicked)
        }
        com.suryaprakash.medlog.pictogram.Sprites.SECTIONS.forEach { (title, ids) ->
            com.suryaprakash.medlog.ui.Title(title)
            ProblemGrid(ids.filter { app.catalogue.problem(it) != null }, onPicked)
        }
        Hint("Not here? Tap Say it instead and tell me in your own words.")
    }
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
