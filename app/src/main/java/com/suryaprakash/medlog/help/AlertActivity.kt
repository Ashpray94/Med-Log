package com.suryaprakash.medlog.help

import android.app.KeyguardManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import com.suryaprakash.medlog.ui.steady
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import com.suryaprakash.medlog.ui.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.suryaprakash.medlog.MainActivity
import com.suryaprakash.medlog.data.Kind
import com.suryaprakash.medlog.medlog
import com.suryaprakash.medlog.ui.BigButton
import com.suryaprakash.medlog.ui.Body
import com.suryaprakash.medlog.ui.Card
import com.suryaprakash.medlog.ui.Hint
import com.suryaprakash.medlog.ui.LocalPalette
import com.suryaprakash.medlog.ui.LocalScale
import com.suryaprakash.medlog.ui.MedTheme
import com.suryaprakash.medlog.ui.Screen
import com.suryaprakash.medlog.ui.Tone
import com.suryaprakash.medlog.ui.savedFeedback
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Full-screen alerts that must show over the lock screen:
 * SOS progress, "Did you fall?", the morning check-in, and (on a helper's phone) an incoming alert.
 */
class AlertActivity : ComponentActivity() {
    override fun onDestroy() { if (isFinishing) AlertSound.stop(); super.onDestroy() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) { setShowWhenLocked(true); setTurnScreenOn(true) }
        else @Suppress("DEPRECATION") window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        render()
    }

    override fun onNewIntent(intent: android.content.Intent) { super.onNewIntent(intent); setIntent(intent); render() }

    private fun render() {
        val mode = intent.getStringExtra(MODE) ?: SOS
        setContent {
            val s by medlog.settings.flow.collectAsState()
            MedTheme(s) {
                when (mode) {
                    SOS -> SosPanel(onClose = { finish() })
                    FALL -> FallPanel(onClose = { finish() })
                    CHECKIN -> CheckInPanel(onClose = { finish() })
                    ASKED -> AskedPanel(intent.getLongExtra("helperId", 0), intent.getStringExtra("from") ?: "", intent.getStringExtra("re") ?: "", onClose = { finish() })
                    HELPER -> HelperAlertPanel(intent.getStringExtra("from") ?: "", intent.getStringExtra("text") ?: "", intent.getStringExtra("kind") ?: "", intent.getLongExtra("id", 0), onClose = { finish() })
                }
            }
        }
    }

    private fun openApp(route: String) {
        startActivity(android.content.Intent(this, MainActivity::class.java).setData(android.net.Uri.parse("medlog://$route")).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        if (Build.VERSION.SDK_INT >= 26) getSystemService(KeyguardManager::class.java)?.requestDismissKeyguard(this, null)
        finish()
    }

    @Composable
    private fun SosPanel(onClose: () -> Unit) {
        val p = LocalPalette.current
        val sc = LocalScale.current
        val phase by Sos.phase.collectAsState()
        val sent by Sos.smsSentTo.collectAsState()
        val log by Sos.log.collectAsState()
        val (title, say) = when (val ph = phase) {
            is Sos.Phase.Countdown -> "Calling for help in ${ph.seconds}" to "Calling for help in ${ph.seconds} seconds. Tap Cancel to stop."
            Sos.Phase.Messaging -> "Getting help" to "Messaging your helpers."
            is Sos.Phase.WhatsApp -> (if (ph.started) "WhatsApp call started" else "Starting WhatsApp call") to "Starting a WhatsApp call with your family."
            is Sos.Phase.Calling -> "Calling ${ph.name}" to "Calling ${ph.name}."
            is Sos.Phase.Answered -> "Did ${ph.name} answer?" to "Did ${ph.name} answer? Is help coming? If you don't tap, I will call the next person."
            is Sos.Phase.EmergencyCountdown -> "Calling ${ph.number} in ${ph.seconds}" to "Calling ${ph.number} in ${ph.seconds} seconds."
            is Sos.Phase.EmergencyCalling -> "Calling ${ph.number}" to "Calling ${ph.number}."
            Sos.Phase.HelpComing -> "Help is coming" to "Help is coming. Stay where you are."
            Sos.Phase.Cancelled, Sos.Phase.Idle -> "SOS finished" to "SOS finished."
        }
        // which step of the three SOS is on: 1 message family, 2 call family, 3 call the ambulance
        val step = when (phase) {
            is Sos.Phase.Countdown -> 0
            Sos.Phase.Messaging -> 1
            is Sos.Phase.WhatsApp, is Sos.Phase.Calling, is Sos.Phase.Answered -> 2
            is Sos.Phase.EmergencyCountdown, is Sos.Phase.EmergencyCalling -> 3
            else -> 4
        }
        val calm = phase is Sos.Phase.HelpComing || phase is Sos.Phase.Cancelled || phase is Sos.Phase.Idle
        Screen(title, say, onHome = null) {
            // status: one big, clear sentence
            androidx.compose.foundation.layout.Column(
                Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(sc.radius + 6.dp)).background(if (calm) p.okSoft else p.red).padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
            ) {
                val fg = if (calm) p.ok else Color.White
                when (val ph = phase) {
                    is Sos.Phase.Countdown -> Text("${ph.seconds}", fontSize = sc.huge * 2.2f, fontWeight = FontWeight.Bold, color = fg)
                    is Sos.Phase.EmergencyCountdown -> Text("${ph.seconds}", fontSize = sc.huge * 2f, fontWeight = FontWeight.Bold, color = fg)
                    is Sos.Phase.Answered -> Text("${ph.secondsLeft}", fontSize = sc.huge * 1.4f, fontWeight = FontWeight.Bold, color = fg)
                    else -> androidx.compose.material3.Icon(if (calm) Icons.Rounded.Check else Icons.Rounded.Call, null, tint = fg, modifier = Modifier.size(56.dp))
                }
                Text(title, fontSize = sc.headline * 1.15f, fontWeight = FontWeight.Bold, color = fg, textAlign = TextAlign.Center)
                if (phase is Sos.Phase.Countdown) Text("Then your family is messaged and called", fontSize = sc.small, color = fg.copy(alpha = 0.9f), textAlign = TextAlign.Center)
            }
            // the one thing to do now
            when (val ph = phase) {
                is Sos.Phase.Countdown -> {
                    BigButton("Cancel – I'm OK", tone = Tone.SECONDARY, icon = Icons.Rounded.Close, height = sc.target * 1.5f, onClick = { Sos.cancel(this@AlertActivity) })
                    BigButton("Don't wait – start now", tone = Tone.DANGER, onClick = { Sos.answer.value = "go" })
                }
                is Sos.Phase.Answered -> {
                    BigButton("Yes, help is coming", tone = Tone.OK, icon = Icons.Rounded.Check, height = sc.target * 1.5f, onClick = { Sos.helpComing() })
                    BigButton("No, call the next person", tone = Tone.DANGER, icon = Icons.Rounded.Call, onClick = { Sos.next() })
                }
                is Sos.Phase.WhatsApp -> {
                    if (ph.started) BigButton("Help is coming", tone = Tone.OK, icon = Icons.Rounded.Check, onClick = { Sos.helpComing() })
                    BigButton("Call helpers one by one", tone = Tone.DANGER, icon = Icons.Rounded.Call, onClick = { Sos.next() })
                }
                is Sos.Phase.EmergencyCountdown -> {
                    BigButton("Call ${ph.number} now", tone = Tone.DANGER, icon = Icons.Rounded.Call, height = sc.target * 1.5f, onClick = { Sos.answer.value = "go" })
                    BigButton("Help is already coming", tone = Tone.OK, onClick = { Sos.helpComing() })
                }
                Sos.Phase.HelpComing, Sos.Phase.Cancelled, Sos.Phase.Idle -> BigButton("Close", tone = Tone.OK, height = sc.target * 1.3f, onClick = onClose)
                else -> BigButton("Help is coming – stop calling", tone = Tone.OK, icon = Icons.Rounded.Check, height = sc.target * 1.3f, onClick = { Sos.helpComing() })
            }
            // if there's time: what happened, in pictures. Helpers get it straight away. Nothing waits for it.
            if (!calm || phase is Sos.Phase.HelpComing) {
                var picked by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(Sos.kind.value) }
                com.suryaprakash.medlog.ui.SectionHeader("What happened?", if (picked == null) "If you can, tap one. Help is coming anyway." else "Sent to your helpers", null)
                val kinds = Sos.KINDS
                com.suryaprakash.medlog.ui.TileGrid(kinds, 4, aspect = 0.66f) { (id, label), mod ->
                    com.suryaprakash.medlog.ui.PicTile(label, mod, picture = 44.dp, selected = picked == id, onClick = {
                        picked = id
                        com.suryaprakash.medlog.help.Nearby.broadcast(this@AlertActivity, "SOS", "What happened: $label")
                        medlog.scope.launch { medlog.repo.addEvent(com.suryaprakash.medlog.data.Kind.SOS, "SOS: $label") }
                    }) {
                        com.suryaprakash.medlog.pictogram.SpriteIcon(id, 44.dp)
                    }
                }
            }
            // the three steps, so it is clear what has happened and what comes next. Only steps that really ran get a tick.
            var reached by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(0) }
            if (step in 1..3 && step > reached) reached = step
            if (!(calm && reached == 0)) Card {
                val steps = listOf(
                    "Message family" to (if (sent.isNotEmpty()) "Sent to ${sent.joinToString(", ")}" else "Your location by text"),
                    "Call family, one by one" to ((phase as? Sos.Phase.Calling)?.let { "Calling ${it.name} now" } ?: "On speaker"),
                    "Call ${com.suryaprakash.medlog.ui.LocalSettings.current.emergencyNumber}" to "If nobody answers",
                )
                steps.forEachIndexed { i, (name, sub) ->
                    val n = i + 1
                    val done = n < reached && (step > n) || (phase is Sos.Phase.HelpComing && n == reached)
                    val now = step == n
                    androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.foundation.layout.Box(
                            Modifier.size(34.dp).clip(androidx.compose.foundation.shape.CircleShape).background(when { now -> p.red; done -> p.ok; else -> p.fill }),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (done) androidx.compose.material3.Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(20.dp))
                            else Text("$n", color = if (now) Color.White else p.inkSoft, fontWeight = FontWeight.Bold, fontSize = sc.small)
                        }
                        androidx.compose.foundation.layout.Spacer(Modifier.width(14.dp))
                        androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                            Text(name, fontSize = sc.body, fontWeight = if (now) FontWeight.Bold else FontWeight.SemiBold, color = if (now || done) p.ink else p.inkSoft)
                            Text(sub, fontSize = sc.small, color = p.inkSoft)
                        }
                    }
                }
            }
            if (!calm && phase !is Sos.Phase.Countdown) BigButton("Cancel SOS", tone = Tone.QUIET, onClick = { Sos.cancel(this@AlertActivity) })
            if (log.isNotEmpty()) Card { Hint("What happened"); log.takeLast(6).forEach { Hint(it) } }
        }
    }

    /** "Did you fall?" 60 seconds, then SOS (plan 13.4). */
    @Composable
    private fun FallPanel(onClose: () -> Unit) {
        val p = LocalPalette.current
        val sc = LocalScale.current
        var left by remember { mutableIntStateOf(60) }
        var done by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            medlog.speaker.say("Did you fall? Are you OK? Tap I'm OK. If you don't, I will call for help in one minute.")
            while (left > 0 && !done) { delay(1000); left-- ; if (left % 15 == 0 && left > 0) medlog.speaker.say("Are you OK? Calling for help in $left seconds.") }
            if (!done) { done = true; medlog.repo.addEvent(Kind.FALL_ALERT, "Possible fall: no answer, SOS started"); Sos.start(this@AlertActivity, "Possible fall, no answer", countdown = false); onClose() }
        }
        Screen("Did you fall?", "Did you fall? Are you OK?", onHome = null, background = p.redSoft) {
            Text("$left", Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontSize = sc.huge * 2f, fontWeight = FontWeight.Bold, color = p.red)
            BigButton("I'm OK", tone = Tone.OK, icon = Icons.Rounded.Check, height = sc.target * 2f, onClick = {
                done = true; medlog.scope.launch { medlog.repo.addEvent(Kind.FALL_ALERT, "Possible fall: said I'm OK") }; medlog.speaker.say("Good. I'm glad you're OK."); onClose()
            })
            BigButton("I fell – I need help", tone = Tone.DANGER, height = sc.target * 1.5f, onClick = { done = true; Sos.start(this@AlertActivity, "I fell", countdown = false); onClose() })
            BigButton("I fell but I'm OK – note it", tone = Tone.SECONDARY, onClick = { done = true; openApp("tell?problem=fall") })
        }
    }

    /** Good-morning check-in (plan 13.4). */
    @Composable
    private fun CheckInPanel(onClose: () -> Unit) {
        val sc = LocalScale.current
        val name = remember { mutableStateOf("") }
        LaunchedEffect(Unit) { name.value = medlog.repo.profile().name }
        val hello = "Good morning${if (name.value.isNotBlank()) ", ${name.value}" else ""}! How are you today?"
        Screen("How are you today?", hello, onHome = null) {
            Body(hello, bold = true)
            fun answer(word: String, route: String?) {
                medlog.scope.launch { medlog.repo.addEvent(Kind.CHECKIN, "Check-in: $word") }
                com.suryaprakash.medlog.care.CheckIn.answered(this@AlertActivity)
                savedFeedback(this@AlertActivity)
                if (route != null) openApp(route) else { medlog.speaker.say("Thank you. Have a good day."); onClose() }
            }
            BigButton("😊  Good", tone = Tone.OK, height = sc.target * 1.5f, onClick = { answer("good", null) })
            BigButton("😐  OK", tone = Tone.QUIET, height = sc.target * 1.5f, onClick = { answer("ok", null) })
            BigButton("😟  Not well", tone = Tone.AMBER, height = sc.target * 1.5f, onClick = { answer("not well", "tell") })
            BigButton("Tell how I feel", tone = Tone.SECONDARY, onClick = { answer("told", "tell") })
        }
    }

    /**
     * On a helper's phone: someone needs you. Built like Meeting Timer's alert: a dark card at the bottom with a
     * status dot and "Answer within", the message as the title, who and when, a big 0:30 countdown with a bar,
     * one big "I'm coming", and smaller replies under it. The card turns red in the last ten seconds and stays
     * red while the alarm rings. Answering anything stops it and tells the other helpers.
     */
    @Composable
    private fun HelperAlertPanel(from: String, text: String, kind: String, id: Long, onClose: () -> Unit) {
        val sc = LocalScale.current
        val urgent = kind in setOf("SOS", "DANGER", "FALL")
        val deadline by AlertSound.deadline.collectAsState()
        val screaming by AlertSound.screaming.collectAsState()
        var now by remember { mutableStateOf(System.currentTimeMillis()) }
        LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(200) } }
        val left = deadline?.let { ((it - now + 999) / 1000).toInt().coerceAtLeast(0) } ?: 0
        val red = urgent || screaming || (deadline != null && left <= 10)
        val card = if (red) Color(0xFFB3261E) else Color(0xFF1F2023)
        val at = remember { java.text.SimpleDateFormat("h:mm a", com.suryaprakash.medlog.speech.I18n.locale).format(java.util.Date()) }
        fun reply(r: String) {
            Loud.done(this@AlertActivity, Loud.alertId(id))
            val mid = intent.getStringExtra("mid").orEmpty(); val pairId = intent.getStringExtra("pairId")?.ifEmpty { null }
            medlog.scope.launch { if (id > 0) medlog.db.inbox().ack(id); if (mid.isNotEmpty()) Nearby.reply(this@AlertActivity, r, re = mid, pairId = pairId) else Nearby.reply(this@AlertActivity, r, pairId = pairId) }
            onClose()
        }
        androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().background(Color(0xCC000000)).padding(12.dp), contentAlignment = Alignment.BottomCenter) {
            androidx.compose.foundation.layout.Column(
                Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(28.dp)).background(card).padding(22.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(14.dp),
            ) {
                // status line
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.foundation.layout.Box(Modifier.size(12.dp).clip(androidx.compose.foundation.shape.CircleShape).background(if (red) Color.White else Color(0xFF4ADE80)))
                    Spacer(Modifier.width(10.dp))
                    Text(when { screaming -> "Nobody has answered"; deadline == null -> "Message"; else -> "Answer within" }, color = Color.White.copy(alpha = 0.85f), fontSize = sc.body, fontWeight = FontWeight.SemiBold)
                }
                // the message, then who and when
                Text(text, color = Color.White, fontSize = sc.question, fontWeight = FontWeight.Bold, lineHeight = sc.question * 1.15f)
                Text("$from · $at", color = Color.White.copy(alpha = 0.75f), fontSize = sc.body)
                // flip-clock countdown and bar
                if (deadline != null || screaming) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                        listOf("0", ":", "%02d".format(left).take(1), "%02d".format(left).drop(1)).forEach { d ->
                            if (d == ":") Text(":", color = Color.White, fontSize = sc.huge * 1.2f, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp))
                            else androidx.compose.foundation.layout.Box(
                                Modifier.padding(horizontal = 3.dp).size(width = 64.dp, height = 84.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(d, color = Color.White, fontSize = sc.huge * 1.2f, fontWeight = FontWeight.Bold)
                                androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().height(1.dp).background(Color.Black.copy(alpha = 0.35f)))
                            }
                        }
                    }
                    androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().height(6.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = 0.2f))) {
                        androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth(left / AlertSound.WINDOW.toFloat()).height(6.dp).background(Color.White))
                    }
                }
                // one big answer, smaller ones under it
                AlertButton("I'm coming", Color.White, card, sc.target * 1.3f, Modifier.fillMaxWidth()) { reply("coming") }
                Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) {
                    AlertButton("In 5 min", Color.White.copy(alpha = 0.16f), Color.White, sc.target, Modifier.weight(1f)) { reply("5min") }
                    AlertButton("I'll call", Color.White.copy(alpha = 0.16f), Color.White, sc.target, Modifier.weight(1f)) { reply("call") }
                    AlertButton("Can't now", Color.White.copy(alpha = 0.16f), Color.White, sc.target, Modifier.weight(1f)) { reply("cant") }
                }
                Text("Open the app", color = Color.White.copy(alpha = 0.85f), fontSize = sc.body, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp)).steady("Open the app") { Loud.silence(); openApp("helper") }.padding(vertical = 12.dp))
            }
        }
    }

    @Composable
    private fun AlertButton(label: String, bg: Color, fg: Color, height: androidx.compose.ui.unit.Dp, modifier: Modifier, onClick: () -> Unit) {
        androidx.compose.foundation.layout.Box(
            modifier.height(height).fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(18.dp)).background(bg).steady(label, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) { Text(label, color = fg, fontSize = LocalScale.current.button, fontWeight = FontWeight.Bold, maxLines = 1) }
    }

    /** On the person's phone: a helper asked "How are you?". One tap answers them. */
    @Composable
    private fun AskedPanel(helperId: Long, from: String, re: String, onClose: () -> Unit) {
        val sc = LocalScale.current
        LaunchedEffect(Unit) { medlog.speaker.say("$from asks: How are you? Tap your answer.") }
        Screen("$from asks: How are you?", "Tap your answer. $from will see it.", onHome = null) {
            fun answer(key: String) {
                Nearby.answer(this@AlertActivity, helperId, re, key)
                savedFeedback(this@AlertActivity)
                when (key) {
                    "notwell" -> { medlog.speaker.say("I'm sorry. I told $from. You can tell me how you feel."); openApp("tell") }
                    "call" -> { medlog.speaker.say("I asked $from to call you."); onClose() }
                    else -> { medlog.speaker.say("Thank you. I told $from."); onClose() }
                }
            }
            BigButton("😊  I'm good", tone = Tone.OK, height = sc.target * 1.5f, onClick = { answer("good") })
            BigButton("😐  I'm OK", tone = Tone.QUIET, height = sc.target * 1.5f, onClick = { answer("ok") })
            BigButton("😟  Not so well", tone = Tone.AMBER, height = sc.target * 1.5f, onClick = { answer("notwell") })
            BigButton("Please call me", tone = Tone.SECONDARY, icon = Icons.Rounded.Call, onClick = { answer("call") })
        }
    }

    companion object {
        const val MODE = "mode"
        const val ASKED = "asked"
        const val SOS = "sos"
        const val FALL = "fall"
        const val CHECKIN = "checkin"
        const val HELPER = "helper"
    }
}
