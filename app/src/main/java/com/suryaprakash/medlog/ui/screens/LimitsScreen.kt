package com.suryaprakash.medlog.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.wrapContentHeight
import com.suryaprakash.medlog.clinical.Line
import com.suryaprakash.medlog.clinical.LimitsForm
import com.suryaprakash.medlog.clinical.MeasureSpec
import com.suryaprakash.medlog.data.Kind
import com.suryaprakash.medlog.data.carePlan
import com.suryaprakash.medlog.data.saveCarePlan
import com.suryaprakash.medlog.medlog
import com.suryaprakash.medlog.ui.BigButton
import com.suryaprakash.medlog.ui.BigField
import com.suryaprakash.medlog.ui.Body
import com.suryaprakash.medlog.ui.Card
import com.suryaprakash.medlog.ui.Hint
import com.suryaprakash.medlog.ui.LocalPalette
import com.suryaprakash.medlog.ui.LocalScale
import com.suryaprakash.medlog.ui.LocalSettings
import com.suryaprakash.medlog.ui.Nav
import com.suryaprakash.medlog.ui.Screen
import com.suryaprakash.medlog.ui.SwitchRow
import com.suryaprakash.medlog.ui.Text
import com.suryaprakash.medlog.ui.Tone
import com.suryaprakash.medlog.ui.savedFeedback
import com.suryaprakash.medlog.ui.steady
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch

/**
 * "Personal limits": the numbers a helper sets, with the doctor's agreement, for one person. The app uses them
 * instead of the general numbers; an empty box keeps the general number. All the maths and checks live in
 * [LimitsForm]; this page only shows them. Saved through Repo.saveCarePlan (so it is backed up and synced).
 */
@Composable
fun LimitsScreen(nav: Nav) {
    val ctx = LocalContext.current
    val app = ctx.medlog
    val s = LocalSettings.current
    val p = LocalPalette.current
    val scope = rememberCoroutineScope()
    var loaded by remember { mutableStateOf(false) }
    var texts by remember { mutableStateOf<Map<String, Map<Line, String>>>(emptyMap()) }
    var confirmed by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf<Pair<String, String>?>(null) }   // measure key → message under its card
    var age by remember { mutableStateOf<Int?>(null) }
    var cancerCare by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val plan = app.repo.carePlan()
        val pr = app.repo.profile()
        age = app.repo.ageYears(pr.dob)
        cancerCare = com.suryaprakash.medlog.clinical.DangerRules.cancerCareOf(pr.conditions, plan.treatments)
        texts = LimitsForm.SPECS.associate { sp -> sp.key to sp.lines.associateWith { LimitsForm.text(plan.limits.band(sp.key), it) } }
        confirmed = plan.limits.doctorConfirmed
        loaded = true
    }

    fun setText(key: String, line: Line, v: String) {
        texts = texts + (key to (texts[key].orEmpty() + (line to v.filter { it.isDigit() || it == '.' || it == ',' }.take(6))))
        error = null; note = null
    }

    fun suggest(sp: MeasureSpec) {
        scope.launch {
            val type = sp.readingType ?: return@launch
            // the last 14 readings of that type, newest first
            val values = app.db.notes().kindSince(Kind.READING, 0).mapNotNull { n ->
                runCatching { org.json.JSONObject(n.details) }.getOrNull()?.takeIf { it.optString("type") == type }
                    ?.let { o -> if (sp.second) o.optDouble("v2").takeIf { !it.isNaN() } else o.optDouble("v1").takeIf { !it.isNaN() } }
            }.take(14)
            val b = LimitsForm.suggest(sp.key, values)
            if (b == null) { note = sp.key to "Not enough readings yet. MedLog needs at least ${LimitsForm.MIN_READINGS}."; return@launch }
            texts = texts + (sp.key to sp.lines.associateWith { LimitsForm.text(b, it) })
            error = null
            note = sp.key to "Suggested from the last ${values.size} readings. Check the numbers with the doctor, then tap Save."
        }
    }

    fun save() {
        val problem = LimitsForm.validateAll(texts)
        if (problem != null) { error = problem; return }
        scope.launch {
            val limits = LimitsForm.toLimits(texts, confirmed, if (s.role == "helper") "helper" else "self", System.currentTimeMillis())
            app.repo.saveCarePlan { it.copy(limits = limits) }
            savedFeedback(ctx)
            nav.back()
        }
    }

    Screen("Personal limits", "Set by a helper, with the doctor's agreement. The app uses these instead of the general numbers.",
        onHome = { nav.home() }, onBack = { nav.back() },
        actions = {
            error?.let { Text(it, color = p.red, fontWeight = FontWeight.SemiBold, fontSize = LocalScale.current.body) }
            BigButton("Save limits", enabled = loaded, onClick = { save() })
        }) {
        Hint("Set by a helper, with the doctor's agreement. The app uses these instead of the general numbers. Leave a box empty to use the general number.")
        if (!loaded) return@Screen
        LimitsForm.SPECS.forEach { sp ->
            Card {
                Body(sp.title, bold = true)
                Hint("in ${sp.unit}" + if (sp.key == "temp") ". You can type °C (34 to 43): MedLog changes it to °F." else "")
                sp.lines.forEach { l ->
                    BigField("${sp.label(l)} (${sp.unit})", texts[sp.key]?.get(l).orEmpty(), { setText(sp.key, l, it) },
                        keyboard = KeyboardType.Decimal, hint = LimitsForm.general(sp.key, l, age, cancerCare))
                }
                if (sp.readingType != null) BigButton("Suggest from readings", tone = Tone.OUTLINE, onClick = { suggest(sp) })
                note?.takeIf { it.first == sp.key }?.let { Hint(it.second) }
            }
        }
        com.suryaprakash.medlog.ui.Group {
            SwitchRow("The doctor said these are OK", confirmed) { confirmed = it }
        }
    }
}

/**
 * The quiet grey line under a reading when the helper hasn't set limits yet ([needsLimit] is Triage.needsLimit),
 * with a small "Set limits" text button. Not a warning, and never sent to helpers.
 */
@Composable
fun NeedsLimitLine(nav: Nav, needsLimit: String?) {
    val line = LimitsForm.needsLimitLine(needsLimit) ?: return
    val p = LocalPalette.current
    val sc = LocalScale.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text(line, color = p.inkSoft, fontSize = sc.small, modifier = Modifier.weight(1f))
        Text("Set limits", color = p.brand, fontSize = sc.small, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.heightIn(min = 48.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                .steady("Set limits") { nav.go(com.suryaprakash.medlog.ui.Route.Limits) }
                .padding(horizontal = 10.dp).wrapContentHeight(androidx.compose.ui.Alignment.CenterVertically))
    }
}
