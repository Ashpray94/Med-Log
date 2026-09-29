package com.suryaprakash.medlog.help

import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.DisposableEffect
import com.suryaprakash.medlog.ui.tr
import org.json.JSONArray
import org.json.JSONObject
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
                    BigButton("Yes, help is coming", tone = Tone.PRIMARY, icon = Icons.Rounded.Check, height = sc.target * 1.5f, onClick = { Sos.helpComing() })
                    BigButton("No, call the next person", tone = Tone.DANGER, icon = Icons.Rounded.Call, onClick = { Sos.next() })
                }
                is Sos.Phase.WhatsApp -> {
                    if (ph.started) BigButton("Help is coming", tone = Tone.PRIMARY, icon = Icons.Rounded.Check, onClick = { Sos.helpComing() })
                    BigButton("Call helpers one by one", tone = Tone.DANGER, icon = Icons.Rounded.Call, onClick = { Sos.next() })
                }
                is Sos.Phase.EmergencyCountdown -> {
                    BigButton("Call ${ph.number} now", tone = Tone.DANGER, icon = Icons.Rounded.Call, height = sc.target * 1.5f, onClick = { Sos.answer.value = "go" })
                    BigButton("Help is already coming", tone = Tone.PRIMARY, onClick = { Sos.helpComing() })
                }
                Sos.Phase.HelpComing, Sos.Phase.Cancelled, Sos.Phase.Idle -> BigButton("Close", tone = Tone.PRIMARY, height = sc.target * 1.3f, onClick = onClose)
                else -> BigButton("Help is coming – stop calling", tone = Tone.PRIMARY, icon = Icons.Rounded.Check, height = sc.target * 1.3f, onClick = { Sos.helpComing() })
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
            BigButton("I'm OK", tone = Tone.PRIMARY, icon = Icons.Rounded.Check, height = sc.target * 2f, onClick = {
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
            BigButton("😊  Good", tone = Tone.PRIMARY, height = sc.target * 1.5f, onClick = { answer("good", null) })
            BigButton("😐  OK", tone = Tone.QUIET, height = sc.target * 1.5f, onClick = { answer("ok", null) })
            BigButton("😟  Not well", tone = Tone.AMBER, height = sc.target * 1.5f, onClick = { answer("not well", "tell") })
            BigButton("Tell how I feel", tone = Tone.SECONDARY, onClick = { answer("told", "tell") })
        }
    }

    /**
     * On a helper's phone: someone needs you. This is Meeting Timer's reminder, unchanged in look (its own page, in
     * assets/alert/): a dark card at the bottom with a status dot, the alert's title, who and when, a flip-clock
     * countdown, one big answer and two smaller ones. The three answers are [AlertReplies] 1, 2 and 3. The card turns
     * red in the last ten seconds (and all the time for SOS, danger and a fall) and stays red while the alarm rings.
     * The sound and buzzing are [AlertSound], not the page. Answering anything stops it and tells the other helpers.
     */
    @SuppressLint("SetJavaScriptEnabled")
    @Composable
    private fun HelperAlertPanel(from: String, text: String, kind: String, id: Long, onClose: () -> Unit) {
        val urgent = AlertReplies.urgent(kind)
        val replies = remember(kind) { AlertReplies.forKind(kind) }
        val deadline by AlertSound.deadline.collectAsState()
        val screaming by AlertSound.screaming.collectAsState()
        val at = remember { java.text.SimpleDateFormat("h:mm a", com.suryaprakash.medlog.speech.I18n.locale).format(java.util.Date()) }
        val dm = resources.displayMetrics
        val cardWidth = minOf((dm.widthPixels / dm.density).toInt() - 24, 420)
        var ready by remember { mutableStateOf(false) }
        var answered by remember { mutableStateOf(false) }
        fun reply(i: Int) {
            if (answered) return
            answered = true
            val mid = intent.getStringExtra("mid").orEmpty(); val pairId = intent.getStringExtra("pairId")?.ifEmpty { null }
            val app = applicationContext
            medlog.scope.launch {
                val say = AlertAnswer.perform(app, replies[i].code, kind, text, from, id, mid, pairId)
                say?.let { s -> android.os.Handler(android.os.Looper.getMainLooper()).post { android.widget.Toast.makeText(app, tr(s), android.widget.Toast.LENGTH_LONG).show() } }
            }
            onClose()
        }
        // what the page shows: the alert where Meeting Timer had a meeting; the countdown runs to the app's own deadline
        fun alertJson(): JSONObject {
            val start = deadline ?: System.currentTimeMillis()
            return JSONObject().put("key", "alert").put("title", tr(AlertReplies.title(kind, from, text))).put("start", start).put("end", start + 3_600_000L)
                .put("who", from).put("at", at).put("text", tr(text)).put("link", JSONObject.NULL).put("attendees", JSONArray())
                .put("clock", deadline != null || screaming)
                .put("replies", JSONArray(replies.map { JSONObject().put("code", it.code).put("words", tr(it.words)) }))
                .put("words", JSONObject()
                    .put("status", JSONObject().put("normal", tr("Answer within")).put("warn", tr("Answer now")).put("late", tr("Nobody has answered")))
                    .put("min", tr("Minutes")).put("sec", tr("Seconds")).put("minLate", tr("Minutes late")).put("secLate", tr("Seconds late")))
        }
        fun js(name: String, payload: String) = "window.__hostEmit && window.__hostEmit('$name', ${payload.replace("\u2028", "\\u2028").replace("\u2029", "\\u2029")})"
        val web = remember {
            WebView(this@AlertActivity).apply {
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                isVerticalScrollBarEnabled = false; isHorizontalScrollBarEnabled = false; overScrollMode = android.view.View.OVER_SCROLL_NEVER
                settings.javaScriptEnabled = true
                settings.allowFileAccess = false; settings.allowContentAccess = false
                addJavascriptInterface(AlertBridge { i -> runOnUiThread { reply(i) } }, "AndroidHost")
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String) {
                        val m = alertJson()
                        evaluateJavascript(js("init", JSONObject().put("meeting", m).put("platform", "android").put("pad", 12).put("systemGlass", false)
                            .put("cardWidth", cardWidth).put("miniSize", 34).put("lead", 0).put("warn", if (urgent) 3_600_000L else 10_000L)
                            .put("sound", false).put("snoozeMinutes", AlertSnooze.MINUTES).toString()), null)
                        evaluateJavascript(js("meeting", m.toString()), null)
                        postDelayed({ evaluateJavascript(js("enter", "null"), null) }, 60)
                        ready = true
                    }
                }
                loadUrl("file:///android_asset/alert/widget.html")
            }
        }
        DisposableEffect(Unit) { onDispose { web.destroy() } }
        // the deadline arrives (or the alarm turns continuous) after the page is up: show it
        LaunchedEffect(ready, deadline, screaming) { if (ready) web.evaluateJavascript(js("meeting", alertJson().toString()), null) }
        androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().background(Color(0xCC000000))) {
            androidx.compose.ui.viewinterop.AndroidView(factory = { web }, modifier = Modifier.fillMaxSize().navigationBarsPadding())
            Text("Open the app", color = Color.White.copy(alpha = 0.85f), fontSize = LocalScale.current.body, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 8.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                    .steady("Open the app") { Loud.silence(); openApp("helper") }.padding(horizontal = 20.dp, vertical = 12.dp))
        }
    }

    /** The page's answers: `action('join')` is answer 1, `'snooze'` answer 2, `'dismiss'` answer 3. */
    class AlertBridge(val onAnswer: (Int) -> Unit) {
        @JavascriptInterface
        fun action(type: String, minutes: Int) = onAnswer(when (type) { "join" -> 0; "snooze" -> 1; else -> 2 })
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
            BigButton("😊  I'm good", tone = Tone.PRIMARY, height = sc.target * 1.5f, onClick = { answer("good") })
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
