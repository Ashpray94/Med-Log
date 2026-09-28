package com.suryaprakash.medlog.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Snooze
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.suryaprakash.medlog.care.HelperCare
import com.suryaprakash.medlog.care.HelperDose
import com.suryaprakash.medlog.data.Dose
import com.suryaprakash.medlog.data.DoseStatus
import com.suryaprakash.medlog.data.Medicine
import com.suryaprakash.medlog.medlog
import com.suryaprakash.medlog.ui.BigButton
import com.suryaprakash.medlog.ui.BigField
import com.suryaprakash.medlog.ui.Hint
import com.suryaprakash.medlog.ui.Nav
import com.suryaprakash.medlog.ui.Screen
import com.suryaprakash.medlog.ui.Tone
import kotlinx.coroutines.launch

/**
 * "More" on a helper's reminder: everything a helper can do about one dose, on one page. Given (now or earlier);
 * snooze; "I'll give it in …" (the other helpers are told); ask another helper to give it; or not given, and why.
 */
@Composable
fun DoseChoicesScreen(nav: Nav, pairId: String, uid: String) {
    val ctx = LocalContext.current
    val app = ctx.medlog
    val scope = rememberCoroutineScope()
    var found by remember { mutableStateOf<Pair<Dose, Medicine>?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var said by remember { mutableStateOf<String?>(null) }
    var earlier by remember { mutableStateOf(false) }
    var minutes by remember { mutableStateOf("") }
    LaunchedEffect(uid, said) { found = HelperDose.find(ctx, pairId, uid); loaded = true }
    val person = remember { com.suryaprakash.medlog.data.People.byPairId(ctx, pairId) }
    val who = person?.name?.ifBlank { null } ?: "them"
    fun close() { if (!nav.back()) nav.home(com.suryaprakash.medlog.ui.Route.HelperHome) }
    fun act(words: String, block: suspend () -> Unit) = scope.launch { block(); said = words }
    if (!loaded) { com.suryaprakash.medlog.ui.Loading(); return }
    val (d, m) = found ?: run {
        Screen("Not found", "This reminder is no longer here.", onHome = null, onBack = ::close) { Hint("It may have been changed on $who's phone.") }
        return
    }
    val feed = m.form == "feed"
    val time = DoseActivityTime(d.scheduledAt)
    val thing = if (feed) "the $time feed" else "${m.name} ($time)"
    if (earlier) { com.suryaprakash.medlog.ui.WhenSheet(d.scheduledAt, onDone = { t -> earlier = false; act("Noted as given") { HelperDose.give(ctx, pairId, uid, t) } }, onDismiss = { earlier = false }); }
    Screen(if (feed) "$who's feed" else "$who's ${m.name}", "Due at $time. What would you like to do?", onHome = null, onBack = ::close,
        subtitle = "Due at $time · ${m.amount}${if (!feed && m.strength.isNotBlank()) " · ${m.strength}" else ""}") {
        // already answered, here or on another phone
        val answered = said ?: when (d.status) {
            DoseStatus.TAKEN -> if (feed) "Given" else "Taken"
            DoseStatus.SKIPPED -> com.suryaprakash.medlog.data.reasonWords(d.reason) ?: "Not given"
            else -> null
        }
        if (answered != null) {
            com.suryaprakash.medlog.ui.TopBanner(answered, tone = LocalPaletteOk())
            BigButton("Done", onClick = ::close)
            return@Screen
        }
        BigButton("Given", tone = Tone.PRIMARY, icon = Icons.Rounded.Check, onClick = { act(if (feed) "Given" else "Noted as given") { HelperDose.give(ctx, pairId, uid) } })
        BigButton("Given earlier", tone = Tone.SECONDARY, onClick = { earlier = true })

        com.suryaprakash.medlog.ui.SectionHeader("Not just now", "You'll be reminded again", null)
        BigButton("Snooze ${HelperCare.SNOOZE_MIN} min", tone = Tone.SECONDARY, icon = Icons.Rounded.Snooze,
            onClick = { act("Reminding you in ${HelperCare.SNOOZE_MIN} min") { HelperDose.later(ctx, pairId, uid, HelperCare.SNOOZE_MIN) } })
        com.suryaprakash.medlog.ui.Body("I'll give it in …", bold = true)
        com.suryaprakash.medlog.ui.FlowRowOf {
            listOf(10, 15, 20, 30, 45, 60).forEach { n -> com.suryaprakash.medlog.ui.Chip("$n min", minutes == "$n") { minutes = "$n" } }
        }
        BigField("Minutes", minutes, { minutes = it.filter(Char::isDigit).take(3) }, keyboard = KeyboardType.Number, hint = "Or type how many minutes")
        val mins = minutes.toIntOrNull()?.takeIf { it in 1..720 }
        BigButton(if (mins != null) "I'll give it in $mins min" else "Choose the minutes", tone = Tone.SECONDARY, icon = Icons.Rounded.Schedule, enabled = mins != null,
            onClick = { mins?.let { n -> act("You'll be reminded in $n min. The other helpers know.") { HelperDose.later(ctx, pairId, uid, n, tell = true) } } })

        // the other helpers, from the person's phone
        val others = remember(pairId) { otherHelpers(ctx, pairId) }
        if (others.isNotEmpty()) {
            com.suryaprakash.medlog.ui.SectionHeader("Ask someone else", "Their phone rings", null)
            others.forEach { (name, pid) ->
                BigButton("Ask $name to give it", tone = Tone.SECONDARY, icon = Icons.Rounded.Group, onClick = { act("Asked $name to give $thing") { HelperDose.askOther(ctx, pairId, uid, pid) } })
            }
        }

        com.suryaprakash.medlog.ui.SectionHeader("Not given", "Say why, for the doctor", null)
        val reasons = (if (feed) listOf(com.suryaprakash.medlog.data.FOOD_INSTEAD_WORDS) else emptyList()) +
            listOf("Refused", "Asleep", "Vomited", "Feeling sick", "Ran out", "Doctor said stop", "Other reason")
        reasons.forEach { r ->
            BigButton(r, tone = Tone.SECONDARY, height = 52.dp, onClick = {
                val stored = if (r == com.suryaprakash.medlog.data.FOOD_INSTEAD_WORDS) com.suryaprakash.medlog.data.FOOD_INSTEAD else if (r == "Other reason") "Not given" else r
                act(com.suryaprakash.medlog.data.reasonWords(stored) ?: r) { HelperDose.notGiven(ctx, pairId, uid, stored) }
            })
        }
        @Suppress("UNUSED_EXPRESSION") app
    }
}

/** Another helper of the same person, as the person's phone knows them. */
data class OtherHelper(val name: String, val pairId: String, val phone: String, val relation: String)

/** The other helpers of the person with [pairId] (from the person's phone), leaving out this phone's own entry. */
fun otherHelpersFull(ctx: android.content.Context, pairId: String): List<OtherHelper> {
    val app = ctx.medlog
    val me = app.settings.getString("my_name")
    return runCatching { org.json.JSONArray(app.settings.getString("helpers_of_$pairId") ?: "[]") }.getOrDefault(org.json.JSONArray()).let { arr ->
        (0 until arr.length()).map { arr.getJSONObject(it) }
            .filter { it.optString("name").isNotBlank() && it.optString("pairId") != pairId && !it.optString("name").equals(me ?: "", true) }
            .map { OtherHelper(it.optString("name"), it.optString("pairId"), it.optString("phone"), it.optString("relation")) }
    }
}

/** The other helpers who have the app (a pairing), as name and pairing id: the ones whose phone can be rung. */
fun otherHelpers(ctx: android.content.Context, pairId: String): List<Pair<String, String>> =
    otherHelpersFull(ctx, pairId).filter { it.pairId.isNotBlank() }.map { it.name to it.pairId }

private fun DoseActivityTime(at: Long) = com.suryaprakash.medlog.meds.DoseActivity.time(at)

@Composable
private fun LocalPaletteOk() = com.suryaprakash.medlog.ui.LocalPalette.current.ok

@Suppress("unused") private val keepRow: @Composable () -> Unit = { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {} }
@Suppress("unused") private val keepMod = Modifier
