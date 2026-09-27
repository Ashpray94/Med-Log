package com.suryaprakash.medlog.ui.screens

import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.History
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
            com.suryaprakash.medlog.ui.TileGrid(msgs, 2, aspect = 1.3f) { m, mod ->
                val key = m.substringBefore('|'); val text = m.substringAfter('|')
                com.suryaprakash.medlog.ui.PicTile(text, mod, picture = 56.dp, speak = "Send: $text", onClick = { HelpMessages.send(ctx, text); app.speaker.say("Sending: $text") }) {
                    com.suryaprakash.medlog.ui.OptionIcon(HelpMessages.icon(key), HelpMessages.tint(key, p), 56.dp)
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
                Text(" · $what", fontSize = sc.body, color = p.inkSoft)
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
    val pick = rememberContactPicker { n, ph -> h = h.copy(name = n, phone = ph) }
    LaunchedEffect(id) { if (id != null) app.db.helpers().all().firstOrNull { it.id == id }?.let { h = it } }
    Screen(if (id == null) "Add a helper" else "Change helper", "Their name and phone number.", onHome = { nav.home() }, onBack = { nav.back() }) {
        BigButton("Choose from contacts", tone = Tone.QUIET, icon = Icons.Rounded.Contacts, onClick = pick)
        BigField("Name", h.name, { h = h.copy(name = it) })
        BigField("Phone number", h.phone, { h = h.copy(phone = it) }, keyboard = KeyboardType.Phone)
        BigField("Relation (optional)", h.relation, { h = h.copy(relation = it) })
        Toggle("Call and message in an SOS", h.sos) { h = h.copy(sos = it) }
        Toggle("Tell them about missed medicines", h.alerts) { h = h.copy(alerts = it) }
        Toggle("Let them see my notes", h.canSeeNotes, "Only used when you share your doctor page with them") { h = h.copy(canSeeNotes = it) }
        BigButton("Done", tone = Tone.OK, enabled = h.name.isNotBlank() && h.phone.count(Char::isDigit) >= 6, onClick = {
            scope.launch { if (id == null) app.db.helpers().insert(h.copy(sortOrder = app.db.helpers().all().size)) else app.db.helpers().update(h); app.refreshWidgets(); nav.back() }
        })
        if (id != null) {
            if (!confirmDelete) BigButton("Remove this helper", tone = Tone.SECONDARY, icon = Icons.Rounded.Delete, onClick = { confirmDelete = true })
            else Card(border = p.red) {
                Body("Remove ${h.name}? They won't be called in an SOS.", bold = true)
                YesNo(yes = "Remove", no = "Keep", onYes = { scope.launch { app.db.helpers().delete(id); nav.back() } }, onNo = { confirmDelete = false })
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
            BigButton("They match", tone = Tone.OK, onClick = { Nearby.confirmDigits(ctx) })
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
    // back on the helper's own page: screens show this phone's records again
    LaunchedEffect(Unit) { com.suryaprakash.medlog.data.Viewing.pairId.value = null; Nearby.startListening(ctx) }
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
    val heard by com.suryaprakash.medlog.data.Sync.lastHeard.collectAsState()
    val updated = person?.let { heard[it.pairId] ?: com.suryaprakash.medlog.data.Sync.heardAt(ctx, it.pairId) } ?: 0L
    Screen(when { !paired -> "MedLog Helper"; people.size == 1 -> who; else -> "People you help" },
        if (paired) "The latest from $who, and your reply." else "Connect to the phone of the person you help.", onHome = null,
        subtitle = if (!paired) null else if (updated > 0) "Updated ${com.suryaprakash.medlog.ui.whenWords(updated).removePrefix("Today, ").lowercase().let { if (it.first().isDigit()) "at $it" else it }}" else "Waiting for their first update",
        eyebrow = "You're helping",
        trailing = { com.suryaprakash.medlog.ui.RoundIcon(Icons.Rounded.Settings, "Settings") { settings = true } },
        side = { PersonaSwitch(nav) }) {
        // ── the latest from them, before anything else: the one thing a helper must see ──
        if (paired) LatestMessage(fromPerson.firstOrNull(), who, person, scope)
        PermissionListCompact()
        if (!paired) {
            com.suryaprakash.medlog.ui.Question("Connect to their phone", "Hold both phones close. It takes a minute, once.")
            BigButton("Connect", icon = Icons.Rounded.Bluetooth, onClick = { nav.go(Route.Pair) })
            return@Screen
        }
        if (people.size > 1) com.suryaprakash.medlog.ui.Segmented(people.map { it.name.ifBlank { "Person" } }, chosen) { chosen = it }
        // ── their health, from their phone: the same pages they see ──
        person?.let { pp ->
            fun view(r: Route) { com.suryaprakash.medlog.data.Viewing.pairId.value = pp.pairId; nav.go(r) }
            HeroTell(title = "How are they feeling?") { view(Route.Tell()) }
            BigButton("Speak it all for them", tone = Tone.TINT, icon = Icons.Rounded.Mic, height = 56.dp, onClick = { view(Route.SpeakAll()) })
            val mdb = com.suryaprakash.medlog.data.Mirror.db(ctx, pp.pairId)
            val (start, end) = remember { com.suryaprakash.medlog.meds.Scheduler.today() }
            val doses by mdb.doses().betweenFlow(start, end).collectAsState(emptyList())
            val meds by mdb.medicines().activeFlow().collectAsState(emptyList())
            val byId = meds.associateBy { it.id }
            val today = doses.filter { it.medicineId in byId }
            if (today.isNotEmpty()) {
                val taken = today.count { it.status == com.suryaprakash.medlog.data.DoseStatus.TAKEN }
                com.suryaprakash.medlog.ui.SectionHeader("Their medicines today", "$taken of ${today.size} taken or given", null)
                today.groupBy { it.medicineId }.forEach { (id, g) ->
                    DayCard(byId[id]!!, g, onOpen = { view(Route.Meds) },
                        onTaken = { d -> scope.launch { com.suryaprakash.medlog.data.Viewing.pairId.value = pp.pairId; com.suryaprakash.medlog.data.Doses.take(ctx, d.id); com.suryaprakash.medlog.data.Viewing.pairId.value = null } },
                        onUndo = { d -> scope.launch { com.suryaprakash.medlog.data.Viewing.pairId.value = pp.pairId; com.suryaprakash.medlog.data.Doses.untake(ctx, d.id); com.suryaprakash.medlog.data.Viewing.pairId.value = null } },
                        onNotGiven = { d -> scope.launch { com.suryaprakash.medlog.data.Viewing.pairId.value = pp.pairId; com.suryaprakash.medlog.data.Doses.skip(ctx, d.id, "Not given"); com.suryaprakash.medlog.data.Viewing.pairId.value = null } },
                        onTakenAt = { d, at -> scope.launch { com.suryaprakash.medlog.data.Viewing.pairId.value = pp.pairId; com.suryaprakash.medlog.data.Doses.take(ctx, d.id, at); com.suryaprakash.medlog.data.Viewing.pairId.value = null } },
                        who = pp.name.substringBefore(' ').ifBlank { "They" })
                }
            }
            com.suryaprakash.medlog.ui.SectionHeader("Their records", "See and add, the same as on their phone", null)
            val tiles: List<Triple<String, Pair<androidx.compose.ui.graphics.vector.ImageVector, androidx.compose.ui.graphics.Color>, Route>> = listOf(
                Triple("History", Icons.Rounded.History to p.tintTeal, Route.Notes), Triple("Medicines", Icons.Rounded.Medication to p.tintOrange, Route.Meds),
                Triple("Food & water", Icons.Rounded.Restaurant to p.tintGreen, Route.Food), Triple("Toilet & vomit", Icons.Rounded.Wc to p.tintTeal, Route.Output()),
                Triple("BP & sugar", Icons.Rounded.MonitorHeart to p.tintPink, Route.Readings), Triple("Their health", Icons.Rounded.Insights to p.tintPurple, Route.Reports),
            )
            com.suryaprakash.medlog.ui.TileGrid(tiles, 3, aspect = 1f) { (label, look, route), mod ->
                com.suryaprakash.medlog.ui.PicTile(label, mod, picture = 48.dp, onClick = { view(route) }) {
                    com.suryaprakash.medlog.ui.IconTile(look.first, look.second, 48.dp)
                }
            }
        }
        person?.let { pp -> BigButton("Their doctor page", tone = Tone.OUTLINE, icon = Icons.Rounded.LocalHospital, onClick = {
            com.suryaprakash.medlog.data.Viewing.pairId.value = pp.pairId; nav.go(Route.Doctor) }) }
        // ── check on them, before they have to ask ──
        BigButton("Ask $who how they are", tone = Tone.OUTLINE, icon = Icons.Rounded.ChatBubble,
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
        Text("Help one more person", fontSize = sc.body, fontWeight = FontWeight.SemiBold, color = p.brand, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                .steady("Help one more person") { PairMode.helping = true; nav.go(Route.Pair) }.padding(vertical = 12.dp))
    }
}

/**
 * The latest from the person, first on the helper's page. Unanswered, it is a solid card (red for SOS, danger or a
 * fall; teal otherwise) with the answers right on it; answered, it becomes a quiet line.
 */
@Composable
private fun LatestMessage(latest: com.suryaprakash.medlog.data.InboxItem?, who: String, person: com.suryaprakash.medlog.data.CaredFor?, scope: kotlinx.coroutines.CoroutineScope) {
    latest ?: return
    val ctx = LocalContext.current
    val app = ctx.medlog
    val p = LocalPalette.current
    val sc = LocalScale.current
    val urgent = latest.kind in setOf("SOS", "DANGER", "FALL")
    val open = !latest.acked
    val sh = androidx.compose.foundation.shape.RoundedCornerShape(sc.radius + 4.dp)
    if (!open) {
        com.suryaprakash.medlog.ui.Group { com.suryaprakash.medlog.ui.ValueRow("Last message: ${latest.text}", "${dayLabel(latest.at)} ${timeLabel(latest.at)}".trim(), sub = "✓ You answered") }
        return
    }
    val bg = if (urgent) p.red else p.brand
    fun reply(r: String) { com.suryaprakash.medlog.help.Loud.done(ctx, com.suryaprakash.medlog.help.Loud.alertId(latest.id)); scope.launch { app.db.inbox().ack(latest.id); Nearby.reply(ctx, r, pairId = person?.pairId) } }
    Column(Modifier.fillMaxWidth().clip(sh).background(bg).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                Icon(if (urgent) Icons.Rounded.Sos else Icons.Rounded.ChatBubble, null, tint = bg, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("$who needs you", fontSize = sc.body, fontWeight = FontWeight.Bold, color = Color.White)
                Text("${dayLabel(latest.at)} ${timeLabel(latest.at)}".trim(), fontSize = sc.small, color = Color.White)
            }
        }
        Text(latest.text, fontSize = sc.question, fontWeight = FontWeight.Bold, color = Color.White, lineHeight = sc.question * 1.15f)
        // white buttons on the colour: the main answer filled, the others outlined
        Row(Modifier.fillMaxWidth().heightIn(min = sc.target).clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp)).background(Color.White)
            .steady("I'm coming") { reply("coming") }.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Icon(Icons.Rounded.DirectionsWalk, null, tint = bg, modifier = Modifier.size(26.dp)); Spacer(Modifier.width(12.dp))
            Text("I'm coming", fontSize = sc.button, fontWeight = FontWeight.Bold, color = bg)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf("In 5 min" to "5min", "I'll call" to "call").forEach { (label, key) ->
                Box(Modifier.weight(1f).heightIn(min = sc.target).clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp)).border(2.dp, Color.White, androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                    .steady(label) { reply(key) }.padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
                    Text(label, fontSize = sc.button, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center)
                }
            }
        }
        // someone else is nearer: pass it on
        val others = remember(person?.pairId) {
            runCatching { org.json.JSONArray(app.settings.getString("helpers_of_${person?.pairId}") ?: "[]") }.getOrDefault(org.json.JSONArray()).let { arr ->
                (0 until arr.length()).map { arr.getJSONObject(it) }.filter { it.optString("pairId").isNotBlank() && it.optString("name") != app.settings.getString("my_name") }
                    .map { it.optString("name") to it.optString("pairId") }
            }
        }
        var passing by remember { mutableStateOf(false) }
        if (others.isNotEmpty()) Text("Ask someone else to go", fontSize = sc.body, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp)).steady("Ask someone else to go") { passing = true }.padding(vertical = 12.dp))
        if (passing) androidx.compose.ui.window.Dialog(onDismissRequest = { passing = false }) {
            com.suryaprakash.medlog.ui.Card(color = p.paper) {
                com.suryaprakash.medlog.ui.Title("Who should go?")
                others.forEach { (n, pid) ->
                    BigButton("Ask $n", tone = Tone.TINT, onClick = {
                        passing = false
                        person?.let { Nearby.askOther(ctx, it.pairId, pid, latest.text) }
                        reply("call")
                    })
                }
                BigButton("Cancel", tone = Tone.SECONDARY, onClick = { passing = false })
            }
        }
    }
}

/** The helper's settings: the link, pairing, and whose phone this is. */
@Composable
private fun HelperSettings(nav: Nav, onBack: () -> Unit) {
    val s = LocalSettings.current
    val link by Relay.link.collectAsState()
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
        SharingSettings()
    }
}

/** Sharing records over the internet: on or off, which relay, update now, and when each phone last came in. */
@Composable
fun SharingSettings() {
    val ctx = LocalContext.current
    val app = ctx.medlog
    val s = LocalSettings.current
    val scope = rememberCoroutineScope()
    val heard by com.suryaprakash.medlog.data.Sync.lastHeard.collectAsState()
    var peers by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    LaunchedEffect(Unit) { peers = com.suryaprakash.medlog.data.Sync.peers(ctx).map { it.id to it.name } }
    var busy by remember { mutableStateOf(false) }
    com.suryaprakash.medlog.ui.SectionHeader("Sharing records", if (s.internetLink) "Over the internet, encrypted" else "Nearby only", null)
    com.suryaprakash.medlog.ui.Group {
        com.suryaprakash.medlog.ui.SwitchRow("Share over the internet", s.internetLink, "Works from anywhere, not just the same Wi-Fi") { on ->
            app.settings.update { it.copy(internetLink = on) }; Nearby.startListening(ctx)
        }
        peers.forEach { (id, name) ->
            com.suryaprakash.medlog.ui.GroupLine()
            val t = heard[id] ?: com.suryaprakash.medlog.data.Sync.heardAt(ctx, id)
            com.suryaprakash.medlog.ui.ValueRow(name.ifBlank { "Paired phone" }, if (t > 0) com.suryaprakash.medlog.ui.whenWords(t) else "Not yet", sub = "Last update from them")
        }
    }
    BigButton(if (busy) "Updating…" else "Update now", tone = Tone.TINT, height = 52.dp, enabled = !busy && s.internetLink, onClick = {
        busy = true
        scope.launch { com.suryaprakash.medlog.data.Sync.push(ctx); peers.forEach { com.suryaprakash.medlog.data.Sync.askSince(ctx, it.first) }; busy = false }
    })
}

/** The helpers' own page (bottom bar): quick messages between helpers; the person doesn't see them. */
@Composable
fun HelperChatScreen(nav: Nav) {
    val ctx = LocalContext.current
    val app = ctx.medlog
    val inbox by app.db.inbox().flow().collectAsState(emptyList())
    val who = remember { com.suryaprakash.medlog.data.People.all(ctx).firstOrNull()?.name?.ifBlank { null } ?: "them" }
    Screen("Helpers", "Messages between you and the other helpers. $who doesn't see them.", onHome = null, subtitle = "Only helpers see this") {
        FamilyChatSection(who, inbox.filter { it.kind == com.suryaprakash.medlog.help.FamilyChat.KIND })
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
        BigButton("Done", tone = Tone.SECONDARY, enabled = name.isNotBlank(), onClick = { app.settings.putString("my_name", name.trim()) })
    }
    com.suryaprakash.medlog.ui.FlowRowOf {
        listOf("I'm going there now", "Can someone check on $who?", "I'll call $who", "I can't go today").forEach { q ->
            com.suryaprakash.medlog.ui.Chip(q, false) { com.suryaprakash.medlog.help.FamilyChat.send(ctx, q) }
        }
    }
    val recent = chat.sortedByDescending { it.at }.take(20)
    if (recent.isNotEmpty()) com.suryaprakash.medlog.ui.Group {
        recent.forEachIndexed { i, m ->
            if (i > 0) com.suryaprakash.medlog.ui.GroupLine()
            com.suryaprakash.medlog.ui.ValueRow(m.text, timeLabel(m.at), sub = m.fromName)
        }
    }
}

/**
 * Me | Helping, beside the name on both home pages: two icons, switches at once (no question), and the whole
 * app changes colour with it, teal for my health and indigo for helping, so the mode is never mistaken.
 */
@Composable
fun PersonaSwitch(nav: Nav) {
    val ctx = LocalContext.current
    val app = ctx.medlog
    val s = LocalSettings.current
    val scope = rememberCoroutineScope()
    com.suryaprakash.medlog.ui.ModeSwitch(helping = s.role == "helper") { helping ->
        Nearby.stopListening(ctx)
        if (helping) { app.settings.update { it.copy(role = "helper", onboarded = true) }; Nearby.startListening(ctx); nav.home(Route.HelperHome) }
        else scope.launch {
            val set = app.repo.profile().name.isNotBlank()
            app.settings.update { it.copy(role = "self", onboarded = set) }
            nav.home(if (set) Route.Home else Route.Onboarding)
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
    // Android pauses the internet for sleeping phones unless the app is let off battery saving
    var batteryOk by remember { mutableStateOf(Perms.batteryOk(ctx)) }
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) { batteryOk = Perms.batteryOk(ctx); onPauseOrDispose {} }
    val needBattery = !batteryOk && ctx.medlog.settings.value.internetLink
    // one card, one button: the permissions first, then running in the background
    if (need.isNotEmpty() || needBattery) Card(border = LocalPalette.current.amber) {
        Body("Allow MedLog to ring this phone when you are needed, even when it is asleep.", bold = true)
        BigButton("Allow", tone = Tone.PRIMARY, onClick = {
            if (need.isNotEmpty()) ask(need.flatMap { it.perms.toList() }.toTypedArray()) else Perms.openBattery(ctx)
        })
    }
}

@Suppress("unused") private val keepAlerts = Alerts.Type.MESSAGE

/** Set before opening the pairing screen to connect to someone this phone helps (rather than to its own helper). */
object PairMode { var helping = false }
