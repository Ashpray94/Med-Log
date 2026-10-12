package com.suryaprakash.medlog.meds

import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Snooze
import com.suryaprakash.medlog.ui.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.suryaprakash.medlog.data.Dose
import com.suryaprakash.medlog.data.DoseStatus
import com.suryaprakash.medlog.data.Medicine
import com.suryaprakash.medlog.medlog
import com.suryaprakash.medlog.pictogram.Picture
import com.suryaprakash.medlog.ui.BigButton
import com.suryaprakash.medlog.ui.Body
import com.suryaprakash.medlog.ui.Card
import com.suryaprakash.medlog.ui.Hint
import com.suryaprakash.medlog.ui.LocalPalette
import com.suryaprakash.medlog.ui.LocalScale
import com.suryaprakash.medlog.ui.MedTheme
import com.suryaprakash.medlog.ui.Screen
import com.suryaprakash.medlog.ui.Tone
import com.suryaprakash.medlog.ui.YesNo
import com.suryaprakash.medlog.ui.savedFeedback
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The big medicine card (plan 12.2): pill photo, name, dose, food, spoken. I took it / Later / Skip. */
class DoseActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) { setShowWhenLocked(true); setTurnScreenOn(true) }
        else @Suppress("DEPRECATION") window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        AlarmTone.start(this, intent.getBooleanExtra("louder", false))
        setContent {
            val s by medlog.settings.flow.collectAsState()
            MedTheme(s) { DoseScreen { finish() } }
        }
    }

    override fun onDestroy() { AlarmTone.stop(); medlog.speaker.stop(); super.onDestroy() }

    @Composable
    private fun DoseScreen(onClose: () -> Unit) {
        val p = LocalPalette.current
        val sc = LocalScale.current
        val scope = rememberCoroutineScope()
        var due by remember { mutableStateOf<List<Pair<Dose, Medicine>>>(emptyList()) }
        var loaded by remember { mutableStateOf(false) }
        var confirmDouble by remember { mutableStateOf<Dose?>(null) }
        var skipping by remember { mutableStateOf<Dose?>(null) }
        var version by remember { mutableStateOf(0) }

        LaunchedEffect(version) {
            val now = System.currentTimeMillis()
            val meds = medlog.db.medicines().all().associateBy { it.id }
            due = medlog.db.doses().between(now - 3 * 3600_000L, now + 60_000)
                .filter { (it.status == DoseStatus.DUE || it.status == DoseStatus.SNOOZED) && (it.snoozeUntil == null || it.snoozeUntil <= now + 60_000) }
                .mapNotNull { d -> meds[d.medicineId]?.let { d to it } }.filter { it.second.form != "feed" }
            loaded = true
            if (due.isEmpty() && version > 0) { AlarmTone.stop(); onClose() }
        }
        if (!loaded) return
        if (due.isEmpty()) {
            Screen("No medicine due", "Nothing is due right now.", onHome = null) { BigButton("Close", onClick = onClose) }
            return
        }
        val feeding = due.all { it.second.form == "feed" }
        val say = if (feeding) "Time to give the feed. " + due.joinToString(". ") { (_, m) -> "${m.name}, ${m.amount}" } else "Time for your medicine. " + due.joinToString(". ") { (_, m) -> describe(m) }
        LaunchedEffect(say) { AlarmTone.stop(); medlog.speaker.say(say) }

        confirmDouble?.let { d ->
            val m = due.firstOrNull { it.first.id == d.id }?.second
            Screen("Already taken", "You already took this. Take again?", onHome = null) {
                Body("You already took ${m?.name ?: "this"} at ${d.actedAt?.let { time(it) } ?: "earlier"}.", bold = true)
                Body("Take it again?")
                YesNo(yes = "Yes, again", no = "No", onYes = { scope.launch { Scheduler.take(this@DoseActivity, d.id, force = true); confirmDouble = null; version++ } }, onNo = { confirmDouble = null })
            }
            return
        }
        skipping?.let { d ->
            Screen("Why skip?", "Why are you skipping it? Tap one.", onHome = null) {
                listOf("Feeling sick", "Ran out", "Doctor said stop", "Other").forEach { r ->
                    BigButton(r, tone = Tone.SECONDARY, onClick = { scope.launch { Scheduler.skip(this@DoseActivity, d.id, r); skipping = null; version++ } })
                }
                BigButton("Back", tone = Tone.QUIET, onClick = { skipping = null })
            }
            return
        }

        Screen(if (feeding) "Feed time" else if (due.size == 1) "Medicine time" else "Medicine time (${due.size})", say, onHome = null) {
            due.forEach { (d, m) ->
                Card(border = if (m.critical) p.red else p.brand) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val photo = remember(m.photoPath) { m.photoPath?.let { runCatching { BitmapFactory.decodeFile(it) }.getOrNull() } }
                        if (photo != null) Image(photo.asImageBitmap(), "Photo of ${m.name}", Modifier.size(sc.target * 1.8f).clip(RoundedCornerShape(16.dp)), contentScale = ContentScale.Crop)
                        else Picture("mouth", "pill", sc.target * 1.6f)
                        Spacer(Modifier.width(16.dp))
                        androidx.compose.foundation.layout.Column {
                            Text(m.name, fontSize = sc.title, fontWeight = FontWeight.Bold, color = p.ink)
                            Body("${m.amount} ${m.form}${if (m.strength.isNotBlank()) " · ${m.strength}" else ""}")
                            Hint(foodWords(m.food) + " · due ${time(d.scheduledAt)}")
                            if (m.critical) Text("Important medicine", color = p.red, fontSize = sc.small, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (m.form == "feed") Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        BigButton("Given", Modifier.weight(1f), Tone.PRIMARY, icon = Icons.Rounded.Check, height = sc.target * 1.3f, onClick = { scope.launch { Scheduler.take(this@DoseActivity, d.id); savedFeedback(this@DoseActivity); version++ } })
                        BigButton("Not given", Modifier.weight(1f), Tone.SECONDARY, height = sc.target * 1.3f, onClick = { scope.launch { Scheduler.skip(this@DoseActivity, d.id, "Not given"); version++ } })
                    } else BigButton("I took it", tone = Tone.PRIMARY, icon = Icons.Rounded.Check, height = sc.target * 1.3f, onClick = {
                        scope.launch {
                            val r = Scheduler.take(this@DoseActivity, d.id)
                            if (r == Scheduler.Taken.ALREADY) confirmDouble = medlog.db.doses().get(d.id) else { savedFeedback(this@DoseActivity); medlog.speaker.say("Well done."); version++ }
                        }
                    })
                    if (m.form != "feed") Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        BigButton("In ${medlog.settings.value.snoozeMinutes} min", Modifier.weight(1f), Tone.QUIET, icon = Icons.Rounded.Snooze, onClick = { scope.launch { Scheduler.snooze(this@DoseActivity, d.id); version++ } })
                        BigButton("Skip", Modifier.weight(1f), Tone.SECONDARY, onClick = { skipping = d })
                    }
                }
            }
            if (due.size > 1) BigButton("I took them all", tone = Tone.PRIMARY, onClick = {
                scope.launch { due.forEach { Scheduler.take(this@DoseActivity, it.first.id) }; savedFeedback(this@DoseActivity); version++ }
            })
        }
    }

    companion object {
        fun time(t: Long): String = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(t))
        fun foodWords(f: String) = when (f) { "before" -> "Before food"; "after" -> "After food"; "with" -> "With food"; else -> "Any time" }
        fun describe(m: Medicine) = "${m.name}${if (m.strength.isNotBlank()) ", ${m.strength}" else ""}. ${m.amount} ${m.form}. ${foodWords(m.food)}."
    }
}
