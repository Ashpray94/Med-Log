package com.suryaprakash.medlog.ui.screens

import com.suryaprakash.medlog.ui.cardTitle
import android.content.Context
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.LocalDrink
import androidx.compose.material.icons.rounded.Wc
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.Sick
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Sos
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Stop
import com.suryaprakash.medlog.ui.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.suryaprakash.medlog.data.Helper
import com.suryaprakash.medlog.data.Kind
import com.suryaprakash.medlog.data.saveCarePlan
import com.suryaprakash.medlog.help.Alerts
import com.suryaprakash.medlog.help.Calls
import com.suryaprakash.medlog.help.Nearby
import com.suryaprakash.medlog.help.Relay
import com.suryaprakash.medlog.help.Sos
import com.suryaprakash.medlog.help.Wording
import com.suryaprakash.medlog.medlog
import com.suryaprakash.medlog.ui.BigButton
import com.suryaprakash.medlog.ui.BigField
import com.suryaprakash.medlog.ui.Body
import com.suryaprakash.medlog.ui.Card
import com.suryaprakash.medlog.ui.Hint
import com.suryaprakash.medlog.ui.HoldButton
import com.suryaprakash.medlog.ui.LocalPalette
import com.suryaprakash.medlog.ui.LocalScale
import com.suryaprakash.medlog.ui.LocalSettings
import com.suryaprakash.medlog.ui.Nav
import com.suryaprakash.medlog.ui.Perms
import com.suryaprakash.medlog.ui.Route
import com.suryaprakash.medlog.ui.Screen
import com.suryaprakash.medlog.ui.Title
import com.suryaprakash.medlog.ui.Toggle
import com.suryaprakash.medlog.ui.Tone
import com.suryaprakash.medlog.ui.YesNo
import com.suryaprakash.medlog.ui.rememberContactPicker
import com.suryaprakash.medlog.ui.rememberPermissionAsker
import com.suryaprakash.medlog.ui.steady
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * Sending a help message (plan 13.2): straight to paired phones, nearby and over the internet, all at once.
 * Any helper whose phone hasn't said "got it" within 15 seconds is sent a text message too. After 3 minutes
 * with no answer, a phone call is offered.
 */
/** The 3 seconds between tapping a message and it going out, so a slip of the finger can be taken back. */
object SendCountdown {
    const val MILLIS = 3000L
    /** Whole seconds still to wait, 3 down to 0. */
    fun secondsLeft(startedAt: Long, now: Long): Int = (((startedAt + MILLIS - now).coerceAtLeast(0) + 999) / 1000).toInt()
    fun due(startedAt: Long, now: Long) = now - startedAt >= MILLIS
    fun line(text: String, secondsLeft: Int) = "Sending \"$text\" in $secondsLeft\u2026"
}

/** The one message waiting out its countdown. take() hands it out exactly once, so leaving the page, cancelling and the timer can never send it twice. */
class PendingSend {
    private var text: String? = null
    @Synchronized fun start(t: String) { text = t }
    @Synchronized fun cancel() { text = null }
    @Synchronized fun take(): String? { val t = text; text = null; return t }
}

object HelpMessages {
    data class Status(val text: String, val stage: String, val at: Long = System.currentTimeMillis(), val texted: List<String> = emptyList())
    val status = MutableStateFlow<Status?>(null)

    fun send(ctx: Context, text: String, audio: File? = null) {
        val app = ctx.medlog
        app.scope.launch {
            val helpers = app.db.helpers().all()
            val paired = helpers.filter { it.pairKey != null }
            val me = app.repo.profile().name
            Nearby.acks.value = emptyList()
            Nearby.reached.value = emptySet()
            app.repo.addEvent(Kind.MESSAGE, "Sent: $text")
            val started = System.currentTimeMillis()
            status.value = Status(text, "sending", started)
            if (paired.isNotEmpty() && (Nearby.allowed(ctx) || Relay.enabled(ctx))) {
                Nearby.broadcast(ctx, "MESSAGE", text, audio)
                // most phones answer "got it" in a second or two; stop waiting once every paired phone has
                for (t in 0 until 30) { if (paired.all { it.name in Nearby.reached.value }) break; delay(500) }
            }
            // a text to everyone whose phone didn't get it, including helpers with no paired phone
            val smsText = Wording.message(me, text, audio != null)
            val texted = helpers.filter { (it.alerts || it.sos) && it.name !in Nearby.reached.value }.filter { Calls.sms(ctx, it.phone, smsText) }.map { it.name }
            if (texted.isEmpty() && Nearby.reached.value.isEmpty()) { status.value = Status(text, "failed", started); return@launch }
            status.value = Status(text, "sent", started, texted)
            for (t in 0 until 360) { if (Nearby.acks.value.isNotEmpty()) { status.value = Status(text, "answered", started, texted); return@launch }; delay(500) }
            status.value = Status(text, "noanswer", started, texted)
        }
    }

    /** Each kind of message keeps its own colour, so they're told apart at a glance. */
    fun tint(key: String, p: com.suryaprakash.medlog.ui.Palette) = when (key) {
        "please_come", "walk" -> p.tintPurple
        "water" -> p.tintBlue
        "bathroom" -> p.tintTeal
        "medicine" -> p.tintOrange
        "unwell" -> p.tintPink
        "hungry" -> p.tintGreen
        "call" -> p.tintGreen
        else -> p.tintBlue
    }

    fun icon(key: String) = when (key) {
        "please_come", "walk" -> Icons.Rounded.DirectionsWalk
        "water" -> Icons.Rounded.LocalDrink
        "bathroom" -> Icons.Rounded.Wc
        "medicine" -> Icons.Rounded.Medication
        "unwell" -> Icons.Rounded.Sick
        "hungry" -> Icons.Rounded.Restaurant
        "call" -> Icons.Rounded.Call
        else -> Icons.Rounded.ChatBubble
    }
}

@Composable
fun HelpScreen(nav: Nav) {
    val ctx = LocalContext.current
    val app = ctx.medlog
    val p = LocalPalette.current
    val sc = LocalScale.current
    val s = LocalSettings.current
    val scope = rememberCoroutineScope()
    val helpers by app.db.helpers().flow().collectAsState(emptyList())
    val acks by Nearby.acks.collectAsState()
    val reached by Nearby.reached.collectAsState()
    val status by HelpMessages.status.collectAsState()
    var custom by remember { mutableStateOf("") }
    var saveCustom by remember { mutableStateOf(true) }
    var smsOk by remember { mutableStateOf(Perms.has(ctx, *Perms.SMS)) }
    val ask = rememberPermissionAsker { smsOk = Perms.has(ctx, *Perms.SMS) }
    // a tapped message waits 3 seconds and can be cancelled: (text, time tapped)
    var waiting by remember { mutableStateOf<Pair<String, Long>?>(null) }
    var clock by remember { mutableStateOf(System.currentTimeMillis()) }
    val pending = remember { PendingSend() }
    // leaving the page (tab, Back, background) must not lose the message; only Cancel stops it
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { pending.take()?.let { HelpMessages.send(ctx, it) } } }
    LaunchedEffect(waiting) {
        val w = waiting ?: return@LaunchedEffect
        while (true) {
            clock = System.currentTimeMillis()
            if (SendCountdown.due(w.second, clock)) { pending.take()?.let { HelpMessages.send(ctx, it) }; waiting = null; return@LaunchedEffect }
            delay(200)
        }
    }

    val speak = "Tap a message to send it to your family. For an emergency, tap the red SOS button at the bottom. " + (if (helpers.isEmpty()) "You have no helpers yet. Add one first." else "")
    Screen("Family", speak, onHome = { nav.home() }, subtitle = "Tell your family what you need", eyebrow = "") {
        if (helpers.isEmpty()) Card(border = p.amber) {
            Body("Add at least one helper so MedLog knows who to ask.", bold = true)
            BigButton("Add a helper", onClick = { nav.go(Route.HelperEdit(null)) })
        }
        if (!smsOk) Card(border = p.amber) { Body("Allow MedLog to send text messages, so your messages always get through."); BigButton("Allow", tone = Tone.QUIET, onClick = { ask(Perms.SMS + Perms.CALL) }) }

        // ── what happened to the last message, person by person ──
        status?.let { st -> MessageStatus(st, acks, reached, helpers.firstOrNull()) }

        // ── the main thing: a message in one tap ──
        val msgs = s.messages
        if (msgs.isEmpty()) {
            val hsh = androidx.compose.foundation.shape.RoundedCornerShape(sc.radius + 4.dp)
            Row(Modifier.fillMaxWidth().heightIn(min = 140.dp).clip(hsh).background(p.brand).steady("Choose your messages") { nav.go(Route.Messages) }
                .padding(horizontal = 24.dp, vertical = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Choose your messages", color = Color.White, fontSize = sc.title, fontWeight = FontWeight.Bold, lineHeight = sc.title * 1.2f)
                    Spacer(Modifier.height(8.dp))
                    Text("Water, bathroom, please come: sent with one tap", color = Color.White.copy(alpha = 0.85f), fontSize = sc.body)
                }
                Spacer(Modifier.width(16.dp))
                Box(Modifier.size(sc.target + 8.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = p.brand, modifier = Modifier.size(28.dp))
                }
            }
        } else {
            com.suryaprakash.medlog.ui.SectionHeader("Send a message", "${msgs.size} message${if (msgs.size == 1) "" else "s"} · tap one to send", "Change") { nav.go(Route.Messages) }
            waiting?.let { w ->
                Card(border = p.brand) {
                    Body(SendCountdown.line(w.first, SendCountdown.secondsLeft(w.second, clock).coerceAtLeast(1)), bold = true)
                    BigButton("Cancel", tone = Tone.SECONDARY, onClick = { pending.cancel(); waiting = null; app.speaker.say("Cancelled") })
                }
            }
            com.suryaprakash.medlog.ui.TileGrid(msgs, 2, aspect = 1.3f) { m, mod ->
                val key = m.substringBefore('|'); val text = m.substringAfter('|')
                com.suryaprakash.medlog.ui.Tile("Send: $text", mod, onClick = { pending.take()?.let { HelpMessages.send(ctx, it) }; pending.start(text); waiting = text to System.currentTimeMillis(); clock = System.currentTimeMillis(); app.speaker.say("Sending: $text in 3 seconds. Tap Cancel to stop.") }) {
                    com.suryaprakash.medlog.ui.OptionIcon(HelpMessages.icon(key), HelpMessages.tint(key, p), 56.dp)
                    Spacer(Modifier.height(10.dp))
                    Text(text, fontSize = sc.body, fontWeight = FontWeight.SemiBold, color = p.ink, textAlign = TextAlign.Center, maxLines = 2)
                }
            }
        }

        // ── anything else, in their own words ──
        Title("Something else")
        com.suryaprakash.medlog.ui.SearchBox(custom, { custom = it }, "Type, or tap Speak")
        if (custom.isNotBlank()) {
            Toggle("Keep it for next time", saveCustom) { saveCustom = it }
            BigButton("Send", tone = Tone.TINT, icon = Icons.Rounded.Send, onClick = {
                val t = custom.trim()
                if (saveCustom && s.messages.none { it.substringAfter('|').equals(t, true) }) app.settings.update { it.copy(messages = it.messages + "custom_${System.currentTimeMillis()}|$t") }
                HelpMessages.send(ctx, t); custom = ""
            })
        }

        // ── or call one of them ──
        if (helpers.isNotEmpty()) {
            com.suryaprakash.medlog.ui.SectionHeader("Call your family", "${helpers.size} helper${if (helpers.size == 1) "" else "s"} · tap to call", "Manage") { nav.go(Route.Helpers) }
            com.suryaprakash.medlog.ui.Group {
                helpers.forEachIndexed { i, h ->
                    if (i > 0) com.suryaprakash.medlog.ui.GroupLine()
                    Row(Modifier.fillMaxWidth().steady("Call ${h.name}") { Calls.call(ctx, h.phone) }.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(52.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(15.dp)).background(p.fill), contentAlignment = Alignment.Center) {
                            Text(h.name.trim().take(1).uppercase(), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = p.inkSoft)
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(h.name, fontSize = sc.body, fontWeight = FontWeight.SemiBold, color = p.ink, maxLines = 1)
                            if (h.relation.isNotBlank()) Text(h.relation, fontSize = sc.small, color = p.inkSoft)
                        }
                        Box(Modifier.size(52.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(15.dp)).background(p.brandSoft), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Call, null, tint = p.brand, modifier = Modifier.size(26.dp))
                        }
                    }
                }
            }
        }
    }
}

/**
 * What happened to the last message, in order of importance: where it stands (icon and one line, with the time under it),
 * the message itself, then each person, then a call button if nobody answered.
 */
@Composable
private fun MessageStatus(st: HelpMessages.Status, acks: List<Nearby.Ack>, reached: Set<String>, first: com.suryaprakash.medlog.data.Helper?) {
    val ctx = LocalContext.current
    val p = LocalPalette.current
    val sc = LocalScale.current
    val answered = acks.firstOrNull()
    val (icon, tint, headline) = when {
        answered != null -> Triple(Icons.Rounded.CheckCircle, p.ok, "${answered.name} answered")
        st.stage == "sending" -> Triple(Icons.Rounded.Schedule, p.tintBlue, "Sending…")
        st.stage == "sent" -> Triple(Icons.Rounded.Schedule, p.tintBlue, "Waiting for an answer")
        st.stage == "noanswer" -> Triple(Icons.Rounded.Warning, p.amber, "Nobody has answered yet")
        else -> Triple(Icons.Rounded.Warning, p.red, "Couldn't send it")
    }
    val people = acks.map { it.name to Nearby.replyWords(it.reply) } +
        reached.filter { n -> acks.none { it.name == n } }.map { it to "Got it on their phone" } +
        st.texted.filter { n -> acks.none { it.name == n } && n !in reached }.map { it to "Text message sent" }
    val sh = androidx.compose.foundation.shape.RoundedCornerShape(sc.radius)
    val edge = when { answered != null -> p.ok; st.stage == "noanswer" -> p.amber; st.stage == "failed" -> p.red; else -> p.line }
    Column(Modifier.fillMaxWidth().clip(sh).background(p.card).border(if (edge == p.line) 1.dp else 2.dp, edge, sh).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            com.suryaprakash.medlog.ui.OptionIcon(icon, tint, 48.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(headline, fontSize = sc.cardTitle, fontWeight = FontWeight.Bold, color = p.ink)
                Text("Sent at ${timeLabel(st.at)}", fontSize = sc.small, color = p.inkSoft)
            }
        }
        Text(st.text, fontSize = sc.body, fontWeight = FontWeight.SemiBold, color = p.ink,
            modifier = Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp)).background(p.fill).padding(horizontal = 16.dp, vertical = 12.dp))
        people.forEach { (name, what) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Check, null, tint = p.ok, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(name, fontSize = sc.body, fontWeight = FontWeight.SemiBold, color = p.ink)
                Text(" · $what", fontSize = sc.body, color = p.inkSoft, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
        }
        if ((st.stage == "noanswer" || st.stage == "failed") && answered == null && first != null)
            BigButton("Call ${first.name}", icon = Icons.Rounded.Call, height = 52.dp, onClick = { Calls.call(ctx, first.phone) })
    }
}

/** Choose which help messages to show, in which order, and keep your own (plan 13.2). */
@Composable
fun MessagesScreen(nav: Nav) {
    val ctx = LocalContext.current
    val app = ctx.medlog
    val p = LocalPalette.current
    val s = LocalSettings.current
    var custom by remember { mutableStateOf("") }
    fun set(list: List<String>) { app.settings.update { it.copy(messages = list) }; app.refreshWidgets() }
    Screen("My messages", "Choose the messages you want on the Ask family screen and on the home-screen widget. The first three show on the widget.", onHome = { nav.home() }, onBack = { nav.back() }) {
        Title("Your messages")
        if (s.messages.isEmpty()) Hint("None yet. Add some from the list below.")
        s.messages.forEachIndexed { i, m ->
            val text = m.substringAfter('|')
            Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    com.suryaprakash.medlog.ui.IconTile(HelpMessages.icon(m.substringBefore('|')), p.tintTeal, 40.dp)
                    androidx.compose.foundation.layout.Spacer(Modifier.padding(start = 12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(text, fontSize = LocalScale.current.body, fontWeight = FontWeight.SemiBold, color = p.ink)
                        if (i < 3) Hint("On the widget")
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BigButton("Move up", Modifier.weight(1f), Tone.QUIET, enabled = i > 0, onClick = { set(s.messages.toMutableList().apply { add(i - 1, removeAt(i)) }) })
                    BigButton("Remove", Modifier.weight(1f), Tone.SECONDARY, icon = Icons.Rounded.Delete, onClick = { set(s.messages - m) })
                }
            }
        }
        Title("Add a message")
        com.suryaprakash.medlog.data.Settings.MESSAGE_OPTIONS.filter { o -> s.messages.none { it.substringBefore('|') == o.substringBefore('|') } }.forEach { o ->
            BigButton("+  ${o.substringAfter('|')}", tone = Tone.QUIET, onClick = { set(s.messages + o) })
        }
        Title("Write your own")
        BigField("Your message", custom, { custom = it }, hint = "For example: Please bring the newspaper")
        BigButton("Add my message", icon = Icons.Rounded.Add, enabled = custom.isNotBlank(), onClick = { set(s.messages + "custom_${System.currentTimeMillis()}|${custom.trim()}"); custom = "" })
    }
}

@Composable
fun HelpersScreen(nav: Nav) {
    val ctx = LocalContext.current
    val app = ctx.medlog
    val p = LocalPalette.current
    val helpers by app.db.helpers().flow().collectAsState(emptyList())
    Screen("My helpers", "These people are called and messaged when you need help. Tap one to change it.", onHome = { nav.home() }, onBack = { nav.back() }) {
        if (helpers.isEmpty()) Hint("No helpers yet.")
        helpers.forEachIndexed { i, h ->
            Card(onClick = { nav.go(Route.HelperEdit(h.id)) }, label = "${h.name}. Tap to change.") {
                Text("${i + 1}. ${h.name}", fontWeight = FontWeight.Bold, fontSize = LocalScale.current.body, color = p.ink)
                Hint(listOfNotNull(h.phone, h.relation.ifBlank { null }, if (h.sos) "SOS" else null, if (h.pairId != null) "Phone paired ✓" else null).joinToString(" · "))
            }
        }
        BigButton("Add a helper", icon = Icons.Rounded.Add, onClick = { nav.go(Route.HelperEdit(null)) })
        BigButton("Pair a helper's phone", tone = Tone.QUIET, icon = Icons.Rounded.Bluetooth, sub = "So their phone rings when you tap a help message", onClick = { nav.go(Route.Pair) })
        Hint("In an SOS, helpers are called in this order.")
    }
}

@Composable
fun HelperEditScreen(nav: Nav, id: Long?) {
    val ctx = LocalContext.current
    val app = ctx.medlog
    val p = LocalPalette.current
    val scope = rememberCoroutineScope()
    var h by remember { mutableStateOf(Helper(name = "", phone = "")) }
    var confirmDelete by remember { mutableStateOf(false) }
    // B59: helpers hear about RED notes; only the ones with this switch on also hear "call the doctor today" (kept in the care plan by phone number)
    var tellAmber by remember { mutableStateOf(false) }
    var savedPhone by remember { mutableStateOf("") }
    val pick = rememberContactPicker { n, ph -> h = h.copy(name = n, phone = ph) }
    LaunchedEffect(id) {
        if (id != null) app.db.helpers().all().firstOrNull { it.id == id }?.let { h = it; savedPhone = it.phone }
        tellAmber = com.suryaprakash.medlog.data.CarePlan.parse(app.repo.profile().plan).tellsAmber(savedPhone).takeIf { savedPhone.isNotBlank() } ?: false
    }
    Screen(if (id == null) "Add a helper" else "Change helper", "Their name and phone number.", onHome = { nav.home() }, onBack = { nav.back() }) {
        BigButton("Choose from contacts", tone = Tone.QUIET, icon = Icons.Rounded.Contacts, onClick = pick)
        BigField("Name", h.name, { h = h.copy(name = it) })
        BigField("Phone number", h.phone, { h = h.copy(phone = it) }, keyboard = KeyboardType.Phone)
        BigField("Relation (optional)", h.relation, { h = h.copy(relation = it) })
        Toggle("Call and message in an SOS", h.sos) { h = h.copy(sos = it) }
        Toggle("Tell them about missed medicines", h.alerts) { h = h.copy(alerts = it) }
        Toggle("Tell them when MedLog says 'call the doctor today'", tellAmber, "Off: they are told only about the most serious notes") { tellAmber = it }
        Toggle("Let them see my notes", h.canSeeNotes, "Only used when you share your doctor page with them") { h = h.copy(canSeeNotes = it) }
        BigButton("Save", tone = Tone.OK, enabled = h.name.isNotBlank() && h.phone.count(Char::isDigit) >= 6, onClick = {
            scope.launch {
                if (id == null) app.db.helpers().insert(h.copy(sortOrder = app.db.helpers().all().size)) else app.db.helpers().update(h)
                app.repo.saveCarePlan { pl -> pl.withAmberHelper(savedPhone, false).withAmberHelper(h.phone, tellAmber) }
                app.refreshWidgets(); nav.back()
            }
        })
        if (id != null) {
            if (!confirmDelete) BigButton("Remove this helper", tone = Tone.SECONDARY, icon = Icons.Rounded.Delete, onClick = { confirmDelete = true })
            else Card(border = p.red) {
                Body("Remove ${h.name}? They won't be called in an SOS.", bold = true)
                YesNo(yes = "Remove", no = "Keep", onYes = { scope.launch { app.db.helpers().delete(id); app.repo.saveCarePlan { pl -> pl.withAmberHelper(savedPhone, false) }; nav.back() } }, onNo = { confirmDelete = false })
            }
        }
    }
}

/** Face-to-face pairing: both phones show the same 4 digits (plan 13.1). */
@Composable
fun PairScreen(nav: Nav) {
    val ctx = LocalContext.current
    val app = ctx.medlog
    val p = LocalPalette.current
    val sc = LocalScale.current
    val s = LocalSettings.current
    val st by Nearby.pair.collectAsState()
    var allowed by remember { mutableStateOf(Nearby.allowed(ctx)) }
    val ask = rememberPermissionAsker { allowed = Nearby.allowed(ctx) }
    var myName by remember { mutableStateOf("") }
    var chosen by remember { mutableStateOf<Pair<String, String>?>(null) }
    var phone by remember { mutableStateOf("") }
    var started by remember { mutableStateOf(false) }
    DisposableEffect(Unit) { onDispose { if (st.done == null) Nearby.cancelPairing(ctx) } }
    // a phone can connect both ways: to its own helper's phone, or to the phone of someone it helps
    var helperSide by remember { mutableStateOf(s.role == "helper" || PairMode.helping) }
    DisposableEffect(Unit) { onDispose { PairMode.helping = false } }
    Screen("Connect phones", if (helperSide) "Hold this phone next to the other person's phone." else "Ask your helper to open MedLog on their phone and choose I'm a helper.", onHome = { nav.home(if (helperSide) Route.HelperHome else Route.Home) }, onBack = { nav.back() }) {
        if (!allowed) { Body("MedLog needs Bluetooth and nearby devices to pair."); BigButton("Allow", onClick = { ask(Perms.NEARBY + Perms.NOTIFY) }); return@Screen }
        st.done?.let { name ->
            Card(border = p.ok) { Body(if (helperSide) "Paired with $name. You'll be alerted when $name needs you." else "$name's phone is paired.", bold = true) }
            BigButton("Done", tone = Tone.OK, onClick = { if (!s.onboarded) nav.back() else nav.home(if (s.role == "helper") Route.HelperHome else Route.Home) })
            return@Screen
        }
        st.error?.let { Card(border = p.amber) { Body(it) } }
        st.digits?.let { d ->
            Card {
                Hint("Check both phones show the same number:")
                Text(d, Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontSize = sc.huge * 1.4f, fontWeight = FontWeight.Bold, color = p.ink)
            }
            if (st.confirmed) Body("Waiting for the other phone to confirm…")
            else BigButton("They match", tone = Tone.OK, onClick = { Nearby.confirmDigits(ctx) })
            BigButton("They don't match – stop", tone = Tone.SECONDARY, onClick = { Nearby.cancelPairing(ctx); started = false })
            return@Screen
        }
        if (s.role != "helper" && !started) com.suryaprakash.medlog.ui.Segmented(listOf("My helper's phone", "Someone I help"), if (helperSide) 1 else 0) { helperSide = it == 1 }
        if (helperSide) {
            BigField("Your name", myName, { myName = it }, hint = "The other phone will see this")
            if (!started) BigButton("Start pairing", enabled = myName.isNotBlank(), onClick = { started = true; Nearby.pairAsHelper(ctx, myName.trim()) })
            else Body("Waiting for the other phone… Keep both phones close together.")
        } else {
            if (!started) BigButton("Look for my helper's phone", onClick = { started = true; Nearby.findHelpers(ctx) })
            if (started && st.found.isEmpty()) Body("Looking… On your helper's phone: open MedLog → I'm a helper → Start pairing.")
            if (chosen == null) st.found.forEach { (eid, name) -> BigButton(name, tone = Tone.SECONDARY, onClick = { chosen = eid to name; phone = "" }) }
            chosen?.let { (eid, name) ->
                // the helper is already in the list (with their number): link to them, never ask for the number again
                val helpers by app.db.helpers().flow().collectAsState(emptyList())
                val match = helpers.firstOrNull { it.name.trim().equals(name.trim(), true) } ?: helpers.firstOrNull { it.name.substringBefore(" ").equals(name.substringBefore(" "), true) }
                var pick by remember(eid) { mutableStateOf(match?.id) }
                var newOne by remember(eid) { mutableStateOf(helpers.isEmpty()) }
                com.suryaprakash.medlog.ui.Question("Which of your helpers is $name?")
                helpers.forEach { h -> com.suryaprakash.medlog.ui.Choice(h.name, pick == h.id && !newOne, sub = h.relation.ifBlank { null }) { pick = h.id; newOne = false } }
                com.suryaprakash.medlog.ui.Choice("Someone not in my list", newOne) { newOne = true; pick = null }
                if (newOne) BigField("$name's phone number", phone, { phone = it }, keyboard = KeyboardType.Phone, hint = "For a text message if the internet can't reach")
                val h = helpers.firstOrNull { it.id == pick }
                BigButton("Connect $name's phone", enabled = (h != null && !newOne) || phone.count(Char::isDigit) >= 6,
                    onClick = { if (h != null && !newOne) Nearby.pairWith(ctx, eid, h.name, h.phone) else Nearby.pairWith(ctx, eid, name, phone) })
            }
        }
    }
}

/**
 * The helper's phone. Its one job: answer the person. Their latest message is the biggest thing, with the reply
 * right under it; then "Ask how they are". Other helpers and earlier messages follow, and the link, pairing and
 * whose-phone switch live one tap away in Settings.
 */
@Composable
fun HelperHomeScreen(nav: Nav) {
    val ctx = LocalContext.current
    val app = ctx.medlog
    val p = LocalPalette.current
    val sc = LocalScale.current
    val s = LocalSettings.current
    val inbox by app.db.inbox().flow().collectAsState(emptyList())
    LaunchedEffect(Unit) { Nearby.startListening(ctx) }
    // everyone this phone helps; with more than one, a switch at the top picks whose messages are shown
    val people = remember(s.pairedWith) { com.suryaprakash.medlog.data.People.all(ctx) }
    val paired = people.isNotEmpty()
    var chosen by remember { mutableStateOf(0) }
    val person = people.getOrNull(chosen.coerceAtMost((people.size - 1).coerceAtLeast(0)))
    val asking by Nearby.asking.collectAsState()
    val who = person?.name?.ifBlank { "them" } ?: "them"
    var settings by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    if (settings) { androidx.activity.compose.BackHandler { settings = false }; HelperSettings(nav, onBack = { settings = false }); return }
    val fromPerson = inbox.filter { it.kind != com.suryaprakash.medlog.help.FamilyChat.KIND && (people.size < 2 || it.fromName == person?.name) }
    Screen(when { !paired -> "MedLog Helper"; people.size == 1 -> who; else -> "People you help" },
        if (paired) "The latest from $who, and your reply." else "Connect to the phone of the person you help.", onHome = null,
        trailing = { com.suryaprakash.medlog.ui.RoundIcon(Icons.Rounded.Settings, "Settings") { settings = true } }) {
        if (s.role == "helper") PersonaSwitch(nav)
        PermissionListCompact()
        if (!paired) {
            com.suryaprakash.medlog.ui.Question("Connect to their phone", "Hold both phones close. It takes a minute, once.")
            BigButton("Connect", icon = Icons.Rounded.Bluetooth, onClick = { nav.go(Route.Pair) })
            return@Screen
        }
        if (people.size > 1) com.suryaprakash.medlog.ui.Segmented(people.map { it.name.ifBlank { "Person" } }, chosen) { chosen = it }
        // ── the latest from them: the most important thing on this screen ──
        val latest = fromPerson.firstOrNull()
        if (latest == null) com.suryaprakash.medlog.ui.Card { Hint("No messages from $who yet. You'll hear an alarm when one comes.") }
        else {
            val urgent = latest.kind in setOf("SOS", "DANGER", "FALL")
            com.suryaprakash.medlog.ui.Card(border = if (!latest.acked) (if (urgent) p.red else p.brand) else null) {
                Hint("${dayLabel(latest.at)} ${timeLabel(latest.at)}")
                Text(latest.text, fontSize = sc.question, fontWeight = FontWeight.Bold, color = p.ink, lineHeight = sc.question * 1.15f)
                if (!latest.acked) {
                    fun reply(r: String) { com.suryaprakash.medlog.help.AlertSound.stop(); scope.launch { app.db.inbox().ack(latest.id); Nearby.reply(ctx, r, pairId = person?.pairId) } }
                    BigButton("I'm coming", icon = Icons.Rounded.DirectionsWalk, onClick = { reply("coming") })
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        BigButton("In 5 min", Modifier.weight(1f), Tone.SECONDARY, onClick = { reply("5min") })
                        BigButton("I'll call", Modifier.weight(1f), Tone.SECONDARY, onClick = { reply("call") })
                    }
                } else Body("✓ You answered", color = p.ok, bold = true)
            }
        }
        // ── check on them, before they have to ask ──
        BigButton("Ask $who how they are", tone = if (latest == null || latest.acked) Tone.PRIMARY else Tone.SECONDARY, icon = Icons.Rounded.ChatBubble,
            enabled = s.internetLink, onClick = { Nearby.ask(ctx, person?.pairId) })
        asking?.takeIf { it.pairId.isEmpty() || it.pairId == person?.pairId }?.let { a ->
            com.suryaprakash.medlog.ui.Group {
                com.suryaprakash.medlog.ui.ValueRow("You asked at ${timeLabel(a.at)}", when {
                    a.answer != null -> a.answer
                    a.sent == false -> "Not sent"
                    a.got -> "Waiting…"
                    else -> "Sending…"
                }, valueColor = if (a.answer != null) p.ok else null)
            }
        }
        // ── the other helpers ──
        FamilyChatSection(who, inbox.filter { it.kind == com.suryaprakash.medlog.help.FamilyChat.KIND })
        // ── earlier ──
        if (fromPerson.size > 1) run {
            com.suryaprakash.medlog.ui.Section("Earlier")
            com.suryaprakash.medlog.ui.Group {
                fromPerson.drop(1).take(6).forEachIndexed { i, m ->
                    if (i > 0) com.suryaprakash.medlog.ui.GroupLine()
                    com.suryaprakash.medlog.ui.ValueRow(m.text, "${dayLabel(m.at)} ${timeLabel(m.at)}".trim(), valueColor = if (m.kind in setOf("SOS", "DANGER", "FALL")) p.red else null)
                }
            }
        }
        com.suryaprakash.medlog.ui.Group {
            com.suryaprakash.medlog.ui.ValueRow("Report a problem with MedLog", null, sub = "Send a picture and your words") { com.suryaprakash.medlog.feedback.Capture.openFeedback(ctx, nav) }
        }
        Text("Help one more person", fontSize = sc.body, fontWeight = FontWeight.SemiBold, color = p.brand, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                .steady("Help one more person") { PairMode.helping = true; nav.go(Route.Pair) }.padding(vertical = 12.dp))
    }
}

/** The helper's settings: the link, pairing, and whose phone this is. */
@Composable
private fun HelperSettings(nav: Nav, onBack: () -> Unit) {
    val s = LocalSettings.current
    val link by Relay.link.collectAsState()
    val ctx = LocalContext.current
    Screen("Settings", "How this phone hears ${s.pairedWith.ifBlank { "them" }}.", onHome = null, onBack = onBack) {
        com.suryaprakash.medlog.ui.Section("Connection")
        com.suryaprakash.medlog.ui.Group {
            com.suryaprakash.medlog.ui.ValueRow("Nearby", "Bluetooth", sub = "Works without internet")
            com.suryaprakash.medlog.ui.GroupLine()
            com.suryaprakash.medlog.ui.ValueRow("Far away", when {
                !s.internetLink -> "Text messages only"
                link == Relay.Link.ON -> "Connected"
                link == Relay.Link.NO_INTERNET -> "No internet"
                else -> "Connecting…"
            }, sub = "Through the internet, encrypted")
            com.suryaprakash.medlog.ui.GroupLine()
            com.suryaprakash.medlog.ui.ValueRow("Connect again", null, sub = "If the other phone was reset") { nav.go(Route.Pair) }
        }
        com.suryaprakash.medlog.ui.Section("Problems with MedLog")
        com.suryaprakash.medlog.ui.Group {
            com.suryaprakash.medlog.ui.ValueRow("Report a problem", null, sub = "Send a picture and your words") { com.suryaprakash.medlog.feedback.Capture.openFeedback(ctx, nav) }
            com.suryaprakash.medlog.ui.GroupLine()
            com.suryaprakash.medlog.ui.ValueRow("My reports", null) { nav.go(Route.MyReports) }
        }
        Toggle("Shake to report a problem", s.shakeOn, "Shake the phone twice to report what you see.") { on -> ctx.medlog.settings.update { it.copy(shakeToReport = on) } }
    }
}

/** Quick messages between helpers; the person doesn't see them. */
@Composable
private fun FamilyChatSection(who: String, chat: List<com.suryaprakash.medlog.data.InboxItem>) {
    val ctx = LocalContext.current
    val app = ctx.medlog
    var name by remember { mutableStateOf(com.suryaprakash.medlog.help.FamilyChat.myName(ctx)) }
    val ready = com.suryaprakash.medlog.help.FamilyChat.key(ctx) != null
    com.suryaprakash.medlog.ui.Section("Other helpers")
    if (!ready) { Hint("Starts once $who's phone has the new MedLog and is online. Only helpers see this."); return }
    if (com.suryaprakash.medlog.help.FamilyChat.myName(ctx).isBlank()) {
        BigField("Your name", name, { name = it }, hint = "The other helpers will see this")
        BigButton("Save", tone = Tone.SECONDARY, enabled = name.isNotBlank(), onClick = { app.settings.putString("my_name", name.trim()) })
    }
    com.suryaprakash.medlog.ui.FlowRowOf {
        listOf("I'm going there now", "Can someone check on $who?", "I'll call $who", "I can't go today").forEach { q ->
            com.suryaprakash.medlog.ui.Chip(q, false) { com.suryaprakash.medlog.help.FamilyChat.send(ctx, q) }
        }
    }
    val recent = chat.sortedByDescending { it.at }.take(3)
    if (recent.isNotEmpty()) com.suryaprakash.medlog.ui.Group {
        recent.forEachIndexed { i, m ->
            if (i > 0) com.suryaprakash.medlog.ui.GroupLine()
            com.suryaprakash.medlog.ui.ValueRow(m.text, timeLabel(m.at), sub = m.fromName)
        }
    }
}

/** Me | I help someone, at the top of both home pages. Switching asks first. */
@Composable
fun PersonaSwitch(nav: Nav) {
    val ctx = LocalContext.current
    val app = ctx.medlog
    val s = LocalSettings.current
    val scope = rememberCoroutineScope()
    var confirm by remember { mutableStateOf<String?>(null) }
    com.suryaprakash.medlog.ui.Segmented(listOf("Me", "I help someone"), if (s.role == "helper") 1 else 0) { i ->
        val r = if (i == 0) "self" else "helper"
        if (r != s.role) confirm = r
    }
    confirm?.let { r ->
        androidx.compose.ui.window.Dialog(onDismissRequest = { confirm = null }) {
            com.suryaprakash.medlog.ui.Card(color = LocalPalette.current.paper) {
                com.suryaprakash.medlog.ui.Title(if (r == "helper") "Switch to helping?" else "Switch to my health?")
                Body(if (r == "helper") "This phone will ring when the person you help needs you." else "This phone will keep your own health notes.")
                BigButton(if (r == "helper") "Switch to helping" else "Switch to my health", onClick = {
                    confirm = null
                    Nearby.stopListening(ctx)
                    if (r == "helper") { app.settings.update { it.copy(role = "helper", onboarded = true) }; Nearby.startListening(ctx); nav.home(Route.HelperHome) }
                    else scope.launch {
                        val set = app.repo.profile().name.isNotBlank()
                        app.settings.update { it.copy(role = "self", onboarded = set) }
                        nav.home(if (set) Route.Home else Route.Onboarding)
                    }
                })
                BigButton("Keep it as it is", tone = Tone.SECONDARY, onClick = { confirm = null })
            }
        }
    }
}

/** "Whose phone is this?" The same obvious switch on both kinds of phone. */
@Composable
fun RoleSwitch(nav: Nav) {
    val ctx = LocalContext.current
    val app = ctx.medlog
    val s = LocalSettings.current
    var confirm by remember { mutableStateOf<String?>(null) }
    Card {
        Body("Whose phone is this?", bold = true)
        com.suryaprakash.medlog.ui.Segmented(listOf("Mine", "A helper's"), if (s.role == "helper") 1 else 0) { i ->
            val r = if (i == 0) "self" else "helper"
            confirm = if (r != s.role) r else null
        }
        Hint(if (s.role == "helper") "This phone gets alerts from the person you look after." else "This phone keeps your own health notes and asks your family for help.")
        confirm?.let { r ->
            Body(if (r == "helper") "Make this a helper's phone? It will ring when the person you look after needs you. Notes already on this phone stay saved."
                 else "Make this your own phone? You'll set it up for yourself in a few steps.", bold = true)
            YesNo(yes = "Switch this phone", no = "Keep it as it is", onYes = {
                confirm = null
                Nearby.stopListening(ctx)
                if (r == "helper") { app.settings.update { it.copy(role = "helper", onboarded = true) }; Nearby.startListening(ctx); nav.home(Route.HelperHome) }
                else { app.settings.update { it.copy(role = "self", onboarded = false) }; nav.home(Route.Onboarding) }
            }, onNo = { confirm = null })
        }
    }
}

@Composable
private fun PermissionListCompact() {
    val ctx = LocalContext.current
    val need = Perms.list("helper").filter { !Perms.has(ctx, *it.perms) }
    var tick by remember { mutableStateOf(0) }
    val ask = rememberPermissionAsker { tick++; Nearby.startListening(ctx) }
    if (need.isNotEmpty()) Card(border = LocalPalette.current.amber) {
        Body("To hear alerts, MedLog needs: " + need.joinToString(", ") { it.title.lowercase() })
        BigButton("Allow", tone = Tone.QUIET, onClick = { ask(need.flatMap { it.perms.toList() }.toTypedArray()) })
    }
    // Android pauses the internet for sleeping phones unless the app is let off battery saving
    var batteryOk by remember { mutableStateOf(Perms.batteryOk(ctx)) }
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) { batteryOk = Perms.batteryOk(ctx); onPauseOrDispose {} }
    if (!batteryOk && ctx.medlog.settings.value.internetLink) Card(border = LocalPalette.current.amber) {
        Body("Let MedLog run in the background, so alerts from far away arrive even when this phone is asleep.")
        BigButton("Allow", tone = Tone.QUIET, onClick = { Perms.openBattery(ctx) })
    }
}

@Suppress("unused") private val keepAlerts = Alerts.Type.MESSAGE

/** Set before opening the pairing screen to connect to someone this phone helps (rather than to its own helper). */
object PairMode { var helping = false }
