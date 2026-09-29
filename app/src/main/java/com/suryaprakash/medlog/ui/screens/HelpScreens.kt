package com.suryaprakash.medlog.ui.screens

import com.suryaprakash.medlog.data.planned
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.History
import com.suryaprakash.medlog.ui.cardTitle
import android.content.Context
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.Reply
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
import androidx.compose.material.icons.rounded.MoreHoriz
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
import com.suryaprakash.medlog.ui.lift
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
    var justAllowed by remember { mutableStateOf(false) }
    val smsOk = com.suryaprakash.medlog.ui.rememberAllowed(*Perms.SMS) || justAllowed
    val ask = rememberPermissionAsker { justAllowed = Perms.has(ctx, *Perms.SMS) }

    val speak = "Tap a message to send it to your family. For an emergency, tap the red SOS button at the bottom. " + (if (helpers.isEmpty()) "You have no helpers yet. Add one first." else "")
    Screen("Family", speak, onHome = { nav.home() }, subtitle = "Tell your family what you need", eyebrow = "") {
        if (helpers.isEmpty()) Card(border = p.amber) {
            Body("Add at least one helper, so we know who to ask.", bold = true)
            BigButton("Add a helper", onClick = { nav.go(Route.HelperEdit(null)) })
        }
        if (!smsOk) Card(border = p.amber) { Body("Allow text messages, so your messages always get through."); BigButton("Allow", tone = Tone.SECONDARY, onClick = { ask(Perms.SMS + Perms.CALL) }) }

        // ── what happened to the last message, person by person ──
        status?.let { st -> MessageStatus(st, acks, reached, helpers.firstOrNull()) }

        // ── the main thing: a message in one tap ──
        val msgs = s.messages
        if (msgs.isEmpty()) {
            val hsh = androidx.compose.foundation.shape.RoundedCornerShape(sc.radius + 4.dp)
            Row(Modifier.fillMaxWidth().heightIn(min = 140.dp).clip(hsh).background(p.brandSoft).steady("Choose your messages") { nav.go(Route.Messages) }
                .padding(horizontal = 24.dp, vertical = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Choose your messages", color = p.ink, fontSize = sc.title, fontWeight = FontWeight.Bold, lineHeight = sc.title * 1.2f)
                    Spacer(Modifier.height(8.dp))
                    Text("Water, bathroom, please come: sent with one tap", color = p.inkSoft, fontSize = sc.body)
                }
                Spacer(Modifier.width(16.dp))
                Box(Modifier.size(sc.target + 8.dp).clip(CircleShape).background(p.brand), contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = Color.White, modifier = Modifier.size(28.dp))
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
        st.stage == "sending" -> Triple(Icons.Rounded.Schedule, p.inkSoft, "Sending…")
        st.stage == "sent" -> Triple(Icons.Rounded.Schedule, p.inkSoft, "Waiting for an answer")
        st.stage == "noanswer" -> Triple(Icons.Rounded.Warning, p.amber, "Nobody has answered yet")
        else -> Triple(Icons.Rounded.Warning, p.red, "Couldn't send it")
    }
    val people = acks.map { it.name to Nearby.replyWords(it.reply) } +
        reached.filter { n -> acks.none { it.name == n } }.map { it to "Got it on their phone" } +
        st.texted.filter { n -> acks.none { it.name == n } && n !in reached }.map { it to "Text message sent" }
    val sh = androidx.compose.foundation.shape.RoundedCornerShape(sc.radius)
    val edge = when { answered != null -> p.ok; st.stage == "noanswer" -> p.amber; st.stage == "failed" -> p.red; else -> p.line }
    Column(Modifier.fillMaxWidth().clip(sh).background(p.card).then(if (edge != p.line) Modifier.border(2.dp, edge, sh) else Modifier).padding(18.dp),
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
        Toggle("Tell them when the app says 'call the doctor today'", tellAmber, "Off: they are told only about the most serious notes") { tellAmber = it }
        Toggle("Let them see my notes", h.canSeeNotes, "Only used when you share your doctor page with them") { h = h.copy(canSeeNotes = it) }
        BigButton("Done", tone = Tone.PRIMARY, enabled = h.name.isNotBlank() && h.phone.count(Char::isDigit) >= 6, onClick = {
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
    var myName by remember { mutableStateOf(app.settings.getString("my_name").orEmpty()) }
    var chosen by remember { mutableStateOf<Pair<String, String>?>(null) }
    var phone by remember { mutableStateOf("") }
    var started by remember { mutableStateOf(false) }
    DisposableEffect(Unit) { onDispose { if (st.done == null) Nearby.cancelPairing(ctx) } }
    // a phone can connect both ways: to its own helper's phone, or to the phone of someone it helps
    var helperSide by remember { mutableStateOf(s.role == "helper" || PairMode.helping) }
    DisposableEffect(Unit) { onDispose { PairMode.helping = false } }
    Screen("Connect phones", if (helperSide) "Hold this phone next to the other person's phone." else "Ask your helper to open the app on their phone and choose I'm a helper.", onHome = { nav.home(if (helperSide) Route.HelperHome else Route.Home) }, onBack = { nav.back() }) {
        if (!allowed) { Body("Bluetooth and nearby devices are needed to connect."); BigButton("Allow", onClick = { ask(Perms.NEARBY + Perms.NOTIFY) }); return@Screen }
        st.done?.let { name ->
            Card(border = p.ok) { Body(if (helperSide) "Paired with $name. You'll be alerted when $name needs you." else "$name's phone is paired.", bold = true) }
            BigButton("Done", tone = Tone.PRIMARY, onClick = { if (!s.onboarded) nav.back() else nav.home(if (s.role == "helper") Route.HelperHome else Route.Home) })
            return@Screen
        }
        st.error?.let { Card(border = p.amber) { Body(it) } }
        st.digits?.let { d ->
            Card {
                Hint("Check both phones show the same number:")
                Text(d, Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontSize = sc.huge * 1.4f, fontWeight = FontWeight.Bold, color = p.ink)
            }
            // nothing is accepted until "They match" is tapped; a different number means the wrong phone
            if (!st.confirmed) {
                BigButton("They match", tone = Tone.PRIMARY, onClick = { Nearby.confirmDigits(ctx) })
                BigButton("The numbers don't match – stop", tone = Tone.SECONDARY, onClick = { Nearby.cancelPairing(ctx); started = false })
                return@Screen
            }
            if (st.incoming == null) {
                Body("Waiting for the other phone… This takes a few seconds.")
                BigButton("Stop", tone = Tone.SECONDARY, onClick = { Nearby.cancelPairing(ctx); started = false })
                return@Screen
            }
        }
        if (s.role != "helper" && !started) com.suryaprakash.medlog.ui.Segmented(listOf("My helper's phone", "Someone I help"), if (helperSide) 1 else 0) { helperSide = it == 1 }
        st.connecting?.let { Body("Connecting to $it… Keep both phones close together.", bold = true) }
        if (helperSide) {
            BigField("Your name", myName, { myName = it }, hint = "The other phone will see this")
            if (!started) BigButton("Look for phones nearby", enabled = myName.isNotBlank(), icon = Icons.Rounded.Bluetooth, onClick = { started = true; Nearby.pairAsHelper(ctx, myName.trim()) })
            else if (st.connecting == null) {
                // every phone nearby that's looking for a helper; pick the right one (the other phone can also pick this one)
                com.suryaprakash.medlog.ui.SectionHeader("Phones nearby", if (st.found.isEmpty()) "Looking…" else "Tap the one to connect", null)
                if (st.found.isEmpty()) Hint("On their phone: Family → Connect phones → My helper's phone → Look for my helper's phone. This phone shows as \"$myName\" there.")
                st.found.forEach { (eid, name) -> BigButton("$name's phone", tone = Tone.SECONDARY, icon = Icons.Rounded.Bluetooth, onClick = { Nearby.requestPerson(ctx, eid, myName.trim()) }) }
            }
        } else {
            val helpers by app.db.helpers().flow().collectAsState(emptyList())
            if (!started) BigButton("Look for my helper's phone", icon = Icons.Rounded.Bluetooth, onClick = { started = true; Nearby.findHelpers(ctx) })
            // a helper's phone picked this one: say which helper it is, then connect
            val incoming = st.incoming
            if (incoming != null) {
                WhichHelper(incoming.second, helpers, onConnect = { name, number, id -> Nearby.acceptIncoming(ctx, name, number, id) }, onStop = { Nearby.cancelPairing(ctx); started = false })
            } else if (started && chosen == null && st.connecting == null) {
                com.suryaprakash.medlog.ui.SectionHeader("Helper phones nearby", if (st.found.isEmpty()) "Looking…" else "Tap the one to connect", null)
                if (st.found.isEmpty()) Hint("On your helper's phone: open the app → I'm a helper → Connect → Look for phones nearby.")
                st.found.forEach { (eid, name) -> BigButton("$name's phone", tone = Tone.SECONDARY, icon = Icons.Rounded.Bluetooth, onClick = { chosen = eid to name; phone = "" }) }
            }
            chosen?.takeIf { st.connecting == null }?.let { (eid, name) ->
                WhichHelper(name, helpers, onConnect = { n, number, id -> Nearby.pairWith(ctx, eid, n, number, id) }, onStop = { chosen = null })
            }
        }
    }
}

/** "Which of your helpers is Ravi?": link to someone already in the list (never asking for their number again), or add them. */
@Composable
private fun WhichHelper(name: String, helpers: List<com.suryaprakash.medlog.data.Helper>, onConnect: (String, String, Long?) -> Unit, onStop: () -> Unit) {
    val match = helpers.firstOrNull { it.name.trim().equals(name.trim(), true) } ?: helpers.firstOrNull { it.name.substringBefore(" ").equals(name.substringBefore(" "), true) }
    var pick by remember(name) { mutableStateOf(match?.id) }
    var newOne by remember(name) { mutableStateOf(helpers.isEmpty()) }
    var phone by remember(name) { mutableStateOf("") }
    com.suryaprakash.medlog.ui.Question("Which of your helpers is $name?")
    helpers.forEach { h -> com.suryaprakash.medlog.ui.Choice(h.name, pick == h.id && !newOne, sub = h.relation.ifBlank { null }) { pick = h.id; newOne = false } }
    com.suryaprakash.medlog.ui.Choice("Someone not in my list", newOne) { newOne = true; pick = null }
    if (newOne) BigField("$name's phone number", phone, { phone = it }, keyboard = KeyboardType.Phone, hint = "For a text message if the internet can't reach")
    val h = helpers.firstOrNull { it.id == pick }
    BigButton("Connect $name's phone", enabled = (h != null && !newOne) || phone.count(Char::isDigit) >= 6,
        onClick = { if (h != null && !newOne) onConnect(h.name, h.phone, h.id) else onConnect(name, phone, null) })
    BigButton("Not this phone", tone = Tone.SECONDARY, onClick = onStop)
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
    val scope = rememberCoroutineScope()
    val fromPerson = inbox.filter { it.kind != com.suryaprakash.medlog.help.FamilyChat.KIND && (people.size < 2 || it.fromName == person?.name) }
    val heard by com.suryaprakash.medlog.data.Sync.lastHeard.collectAsState()
    val updated = person?.let { heard[it.pairId] ?: com.suryaprakash.medlog.data.Sync.heardAt(ctx, it.pairId) } ?: 0L
    Screen(when { !paired -> "Helping someone"; people.size == 1 -> who; else -> "People you help" },
        if (paired) "The latest from $who, and your reply." else "Connect to the phone of the person you help.", onHome = null,
        subtitle = if (!paired) null else person?.name?.let { "Their records and messages" },
        // only when something is wrong: nothing heard for two hours, or nothing yet
        banner = if (!paired || (updated > 0 && System.currentTimeMillis() - updated <= 2 * 3600_000L)) null else { {
            com.suryaprakash.medlog.ui.TopBanner(if (updated > 0) "Not updated since ${com.suryaprakash.medlog.ui.whenWords(updated).removePrefix("Today, ").lowercase()}. Their phone may be off or offline." else "Waiting for their first update",
                tone = p.amber)
        } },
        eyebrow = "You're helping",
        side = { PersonaSwitch(nav) }) {
        // ── the latest from them, before anything else: the one thing a helper must see ──
        if (paired) LatestMessage(fromPerson.firstOrNull(), who, person, scope)
        // a new version, found when the app came to the front
        val update by com.suryaprakash.medlog.Updater.state.collectAsState()
        if (update is com.suryaprakash.medlog.Updater.State.Available || update is com.suryaprakash.medlog.Updater.State.Downloading || update is com.suryaprakash.medlog.Updater.State.Installing) UpdateCard()
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
            HeroTell(title = "How are they feeling?", onChoose = { view(Route.Tell()) }, onSpeak = { view(Route.Tell(speak = true)) })
            val mdb = com.suryaprakash.medlog.data.Mirror.db(ctx, pp.pairId)
            val (start, end) = remember { com.suryaprakash.medlog.meds.Scheduler.today() }
            val doses by mdb.doses().betweenFlow(start, end).collectAsState(emptyList())
            val meds by mdb.medicines().activeFlow().collectAsState(emptyList())
            val byId = meds.associateBy { it.id }
            val today = doses.filter { it.medicineId in byId }
            // medicines and feeds in their own sections, each one card at a time with its own "See all"
            listOf(false, true).forEach { feedPart ->
                val part = today.filter { (byId[it.medicineId]?.form == "feed") == feedPart }
                if (part.isEmpty()) return@forEach
                // for feeds, food given instead counts as done
                val taken = part.planned().count { it.status == com.suryaprakash.medlog.data.DoseStatus.TAKEN || (feedPart && it.reason == com.suryaprakash.medlog.data.FOOD_INSTEAD && it.status == com.suryaprakash.medlog.data.DoseStatus.SKIPPED) }
                val days = part.groupBy { it.medicineId }.map { (id, g) -> byId[id]!! to g }
                com.suryaprakash.medlog.ui.SectionHeader(if (feedPart) "Their feeds today" else "Their medicines today",
                    "$taken of ${part.planned().size} ${if (feedPart) "done" else "taken"}", "See all ${days.size}") { view(Route.TodayMeds(feeds = feedPart)) }
                TodayMedsPreview(days) { (m, g), mod ->
                    DayCard(m, g, onOpen = { view(Route.Meds) },
                        onTaken = { d -> scope.launch { com.suryaprakash.medlog.data.Viewing.pairId.value = pp.pairId; com.suryaprakash.medlog.data.Doses.take(ctx, d.id); com.suryaprakash.medlog.data.Viewing.pairId.value = null } },
                        onUndo = { d -> scope.launch { com.suryaprakash.medlog.data.Viewing.pairId.value = pp.pairId; com.suryaprakash.medlog.data.Doses.untake(ctx, d.id); com.suryaprakash.medlog.data.Viewing.pairId.value = null } },
                        onNotGiven = { d -> scope.launch { com.suryaprakash.medlog.data.Viewing.pairId.value = pp.pairId; com.suryaprakash.medlog.data.Doses.skip(ctx, d.id, "Not given"); com.suryaprakash.medlog.data.Viewing.pairId.value = null } },
                        onTakenAt = { d, at -> scope.launch { com.suryaprakash.medlog.data.Viewing.pairId.value = pp.pairId; com.suryaprakash.medlog.data.Doses.take(ctx, d.id, at); com.suryaprakash.medlog.data.Viewing.pairId.value = null } },
                        who = pp.name.substringBefore(' ').ifBlank { "They" }, modifier = mod,
                        onAteInstead = { d -> scope.launch { com.suryaprakash.medlog.data.Viewing.pairId.value = pp.pairId; com.suryaprakash.medlog.data.Doses.skip(ctx, d.id, com.suryaprakash.medlog.data.FOOD_INSTEAD); view(Route.FoodPick()) } })
                }
            }
            com.suryaprakash.medlog.ui.SectionHeader("Their records", "See and add, the same as on their phone", null)
            val tiles: List<Triple<String, Pair<androidx.compose.ui.graphics.vector.ImageVector, androidx.compose.ui.graphics.Color>, Route>> = listOf(
                Triple("History", Icons.Rounded.History to p.tintTeal, Route.Notes), Triple("Medicines", Icons.Rounded.Medication to p.tintOrange, Route.Meds),
                Triple("Food & water", Icons.Rounded.Restaurant to p.tintGreen, Route.Food), Triple("Toilet and tummy", Icons.Rounded.Wc to p.tintTeal, Route.Output()),
                Triple("BP & sugar", Icons.Rounded.MonitorHeart to p.tintPink, Route.Readings), Triple("Their health", Icons.Rounded.Insights to p.tintPurple, Route.Reports),
            )
            com.suryaprakash.medlog.ui.TileGrid(tiles, 3, aspect = 0.82f) { (label, look, route), mod ->
                com.suryaprakash.medlog.ui.PicTile(label, mod, picture = 48.dp, onClick = { view(route) }) {
                    com.suryaprakash.medlog.ui.IconTile(look.first, look.second, 48.dp)
                }
            }
        }
        person?.let { pp -> BigButton("Their doctor page", tone = Tone.SECONDARY, icon = Icons.Rounded.LocalHospital, onClick = {
            com.suryaprakash.medlog.data.Viewing.pairId.value = pp.pairId; nav.go(Route.Doctor) }) }
        // ── check on them, before they have to ask ──
        BigButton("Ask $who how they are", tone = Tone.SECONDARY, icon = Icons.Rounded.ChatBubble,
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
        // ── who else helps: one tap to call or message them ──
        person?.let { pp ->
            val others = remember(pp.pairId) { otherHelpersFull(ctx, pp.pairId) }
            if (others.isNotEmpty()) com.suryaprakash.medlog.ui.Group {
                com.suryaprakash.medlog.ui.NavRow("Other helpers", sub = others.joinToString(", ") { it.name } + " · call or message") { nav.go(Route.HelperChat) }
            }
        }
        // ── earlier ──
        if (fromPerson.size > 1) run {
            com.suryaprakash.medlog.ui.Section("Earlier")
            // what happened before, as a timeline: when on top, the words under it at full width
            com.suryaprakash.medlog.ui.Timeline(fromPerson.drop(1).take(6).map { m ->
                com.suryaprakash.medlog.ui.TimelineItem("${dayLabel(m.at)} ${timeLabel(m.at)}".trim(), shown(m.text),
                    sub = if (m.acked) "You answered" else null, mark = if (m.kind in setOf("SOS", "DANGER", "FALL")) p.red else null)
            })
        }
        com.suryaprakash.medlog.ui.Group {
            com.suryaprakash.medlog.ui.ValueRow("Report a problem with the app", null, sub = "Send a picture and your words") { com.suryaprakash.medlog.feedback.Capture.openFeedback(ctx, nav) }
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
    // put away by the helper: gone from the top until something new comes
    var hidden by remember(latest.id) { mutableStateOf(app.settings.getString("hidden_msg") == latest.id.toString()) }
    if (hidden) return
    val p = LocalPalette.current
    val sc = LocalScale.current
    val urgent = latest.kind in setOf("SOS", "DANGER", "FALL")
    val open = !latest.acked
    val sh = androidx.compose.foundation.shape.RoundedCornerShape(sc.radius)
    fun reply(r: String) { com.suryaprakash.medlog.help.Loud.done(ctx, com.suryaprakash.medlog.help.Loud.alertId(latest.id)); scope.launch { app.db.inbox().ack(latest.id); Nearby.reply(ctx, r, pairId = person?.pairId) } }
    // put away without telling anyone
    fun dismiss() {
        com.suryaprakash.medlog.help.Loud.done(ctx, com.suryaprakash.medlog.help.Loud.alertId(latest.id))
        app.settings.putString("my_last_reply", "dismiss|${System.currentTimeMillis()}")
        scope.launch { app.db.inbox().ack(latest.id) }
    }
    // a person's own words need a person; the app's own notices (a missed dose, no check-in) need a look
    val personal = latest.kind in setOf("MESSAGE", "SOS", "DANGER", "FALL")
    val title = when (latest.kind) {
        "SOS" -> "SOS from $who"
        "DANGER" -> "$who may need urgent help"
        "FALL" -> "$who may have fallen"
        "AMBER" -> "$who noted something to watch"
        "MISSED_DOSE" -> if ("feed" in latest.text) "A feed isn't marked as given" else "A medicine isn't marked as taken"
        "CHECKIN" -> "No answer to the check-in"
        "LOW_BATTERY" -> "$who's phone battery is low"
        "REFILL" -> "A medicine is running low"
        else -> "Message from $who"
    }
    var more by remember { mutableStateOf(false) }
    // one white card: a round icon with a real title and when; the message; the answers, or what was answered
    Column(Modifier.fillMaxWidth().lift(sh).clip(sh).background(p.card)) {
        androidx.compose.runtime.CompositionLocalProvider(com.suryaprakash.medlog.ui.LocalOnCard provides true) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(48.dp).clip(androidx.compose.foundation.shape.CircleShape).background(if (urgent) p.redSoft else p.brandSoft), contentAlignment = Alignment.Center) {
                        Icon(when { urgent -> Icons.Rounded.Sos; latest.kind == "MISSED_DOSE" || latest.kind == "REFILL" -> Icons.Rounded.Medication; else -> Icons.Rounded.ChatBubble },
                            null, tint = if (urgent) p.red else p.brand, modifier = Modifier.size(24.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(title, fontSize = sc.headline, fontWeight = FontWeight.Bold, color = if (open && urgent) p.red else p.ink, lineHeight = sc.headline * 1.2f)
                        Text("${dayLabel(latest.at)} ${timeLabel(latest.at)}".trim(), fontSize = sc.small, color = p.inkSoft)
                    }
                }
                // a person's message is the point, so it's large; a notice from the app is read as a sentence
                Text(shown(latest.text), fontSize = if (latest.kind == "MESSAGE") sc.headline else sc.body, fontWeight = if (latest.kind == "MESSAGE") FontWeight.SemiBold else FontWeight.Normal,
                    color = p.ink, lineHeight = (if (latest.kind == "MESSAGE") sc.headline else sc.body) * 1.35f)
                if (open) {
                    if (personal) {
                        BigButton("I'm coming", tone = if (urgent) Tone.DANGER else Tone.PRIMARY, icon = Icons.Rounded.DirectionsWalk, onClick = { reply("coming") })
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            BigButton("I'll call", Modifier.weight(1f), Tone.SECONDARY, icon = Icons.Rounded.Call, onClick = { reply("call") })
                            BigButton("More", Modifier.weight(1f), Tone.SECONDARY, icon = Icons.Rounded.MoreHoriz, onClick = { more = true })
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            BigButton("I'll call", Modifier.weight(1f), Tone.PRIMARY, icon = Icons.Rounded.Call, onClick = { reply("call") })
                            BigButton("Already handled", Modifier.weight(1f), Tone.SECONDARY, onClick = { reply("got") })
                        }
                    }
                    // every alert can be put away without answering
                    BigButton("Dismiss", tone = Tone.SECONDARY, icon = Icons.Rounded.Close, height = 52.dp, onClick = { dismiss() })
                }
            }
        }
        if (!open) {
            // what was answered, in plain words; green only for "I'm coming"
            val said = app.settings.getString("my_last_reply")?.split("|")?.takeIf { it.size == 2 && (it[1].toLongOrNull() ?: 0) >= latest.at }?.get(0)
            val coming = said == "coming"
            Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(1.dp).background(p.line))
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (coming || said == "got") Icons.Rounded.CheckCircle else Icons.AutoMirrored.Rounded.Reply, null, tint = if (coming) p.ok else p.inkSoft, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(when (said) { null -> "Answered"; "got" -> "Marked as handled"; "dismiss" -> "Dismissed"; else -> "You answered: ${Nearby.replyWords(said)}" },
                    fontSize = sc.small, fontWeight = FontWeight.Bold, color = if (coming) p.ok else p.ink, modifier = Modifier.weight(1f))
                // answered: take the card off the top
                Text("Dismiss", fontSize = sc.small, fontWeight = FontWeight.SemiBold, color = p.inkSoft,
                    modifier = Modifier.heightIn(min = 48.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                        .steady("Dismiss") { app.settings.putString("hidden_msg", latest.id.toString()); hidden = true }.padding(horizontal = 12.dp, vertical = 14.dp))
            }
        }
    }
    // the rarer answers, in a sheet
    if (more) {
        val others = remember(person?.pairId) {
            runCatching { org.json.JSONArray(app.settings.getString("helpers_of_${person?.pairId}") ?: "[]") }.getOrDefault(org.json.JSONArray()).let { arr ->
                (0 until arr.length()).map { arr.getJSONObject(it) }.filter { it.optString("pairId").isNotBlank() && it.optString("name") != app.settings.getString("my_name") }
                    .map { it.optString("name") to it.optString("pairId") }
            }
        }
        com.suryaprakash.medlog.ui.AppSheet(onDismissRequest = { more = false }) {
            Column(Modifier.fillMaxWidth().padding(horizontal = sc.margin).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                com.suryaprakash.medlog.ui.SectionHeader("Other answers", shown(latest.text).take(60), null)
                BigButton("In 5 minutes", tone = Tone.SECONDARY, onClick = { more = false; reply("5min") })
                others.forEach { (n, pid) ->
                    BigButton("Ask $n to go", tone = Tone.SECONDARY, onClick = { more = false; person?.let { Nearby.askOther(ctx, it.pairId, pid, latest.text) }; reply("call") })
                }
                BigButton("Already handled", tone = Tone.SECONDARY, onClick = { more = false; reply("got") })
            }
        }
    }
}

/** Messages written by the app for texts (they start with its name, so the text app shows who sent them); on screen, just the words. */
private fun shown(t: String) = t.removePrefix("MedLog: ")

/** Settings on a helper's phone, from the bottom bar. */
@Composable
fun HelperSettingsScreen(nav: Nav) = HelperSettings(nav, onBack = null)

/** The helper's settings: the link, pairing, and whose phone this is. */
@Composable
private fun HelperSettings(nav: Nav, onBack: (() -> Unit)?) {
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
        SharingSettings()
        com.suryaprakash.medlog.ui.Section("Problems with the app")
        com.suryaprakash.medlog.ui.Group {
            com.suryaprakash.medlog.ui.ValueRow("Report a problem", null, sub = "Send a picture and your words") { com.suryaprakash.medlog.feedback.Capture.openFeedback(ctx, nav) }
            com.suryaprakash.medlog.ui.GroupLine()
            com.suryaprakash.medlog.ui.ValueRow("My reports", null) { nav.go(Route.MyReports) }
        }
        Toggle("Shake to report a problem", s.shakeOn, "Shake the phone twice to report what you see.") { on -> ctx.medlog.settings.update { it.copy(shakeToReport = on) } }
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

/**
 * The helpers' own page (bottom bar): who else helps, each with Call and Message; then messages to all the helpers.
 * The person doesn't see any of it.
 */
@Composable
fun HelperChatScreen(nav: Nav) {
    val ctx = LocalContext.current
    val app = ctx.medlog
    val inbox by app.db.inbox().flow().collectAsState(emptyList())
    val people = remember { com.suryaprakash.medlog.data.People.all(ctx) }
    val who = people.firstOrNull()?.name?.ifBlank { null } ?: "them"
    Screen("Helpers", "The other helpers, and messages between you. $who doesn't see them.", onHome = null, subtitle = "Only helpers see this") {
        FamilyChatSection(who, people, inbox.filter { it.kind == com.suryaprakash.medlog.help.FamilyChat.KIND })
    }
}

/** Who else helps, one card each (call or message them); then quick messages to all of them; then what was said. */
@Composable
private fun FamilyChatSection(who: String, people: List<com.suryaprakash.medlog.data.CaredFor>, chat: List<com.suryaprakash.medlog.data.InboxItem>) {
    val ctx = LocalContext.current
    val app = ctx.medlog
    val p = LocalPalette.current
    val sc = LocalScale.current
    var name by remember { mutableStateOf(com.suryaprakash.medlog.help.FamilyChat.myName(ctx)) }
    val ready = com.suryaprakash.medlog.help.FamilyChat.key(ctx) != null
    var writingTo by remember { mutableStateOf<Pair<com.suryaprakash.medlog.data.CaredFor, OtherHelper?>?>(null) }
    if (com.suryaprakash.medlog.help.FamilyChat.myName(ctx).isBlank()) {
        com.suryaprakash.medlog.ui.Question("What's your name?", "The other helpers will see it")
        BigField("Your name", name, { name = it })
        BigButton("Done", tone = Tone.SECONDARY, enabled = name.isNotBlank(), onClick = { app.settings.putString("my_name", name.trim()) })
    }
    // ── who else helps ──
    people.forEach { person ->
        val others = remember(person.pairId) { otherHelpersFull(ctx, person.pairId) }
        com.suryaprakash.medlog.ui.SectionHeader(if (people.size > 1) "Also helping ${person.name}" else "Other helpers",
            if (others.isEmpty()) "None yet" else "${others.size} other${if (others.size == 1) "" else "s"}", null)
        if (others.isEmpty()) Hint("Other helpers show here once ${person.name.ifBlank { "their" }}'s phone shares them. It does this on its next update.")
        others.forEach { o ->
            val sh = androidx.compose.foundation.shape.RoundedCornerShape(sc.radius)
            Column(Modifier.fillMaxWidth().lift(sh).clip(sh).background(p.card).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(48.dp).clip(CircleShape).background(p.brandSoft), contentAlignment = Alignment.Center) {
                        Text(o.name.take(1).uppercase(), fontSize = sc.headline, fontWeight = FontWeight.Bold, color = p.brand)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(o.name, fontSize = sc.headline, fontWeight = FontWeight.Bold, color = p.ink)
                        Text(listOf(o.relation, if (o.pairId.isBlank()) "Doesn't have the app yet" else "").filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "Helps ${person.name.ifBlank { "them" }}" },
                            fontSize = sc.small, color = p.inkSoft)
                    }
                }
                androidx.compose.runtime.CompositionLocalProvider(com.suryaprakash.medlog.ui.LocalOnCard provides true) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (o.phone.isNotBlank()) BigButton("Call", Modifier.weight(1f), Tone.SECONDARY, icon = Icons.Rounded.Call, height = 52.dp,
                            onClick = { com.suryaprakash.medlog.help.Calls.call(ctx, o.phone) })
                        if (o.pairId.isNotBlank() && ready) BigButton("Message", Modifier.weight(1f), Tone.SECONDARY, icon = Icons.Rounded.ChatBubble, height = 52.dp,
                            onClick = { writingTo = person to o })
                    }
                }
            }
        }
    }
    // ── to all the helpers ──
    com.suryaprakash.medlog.ui.SectionHeader("Message all helpers", "$who doesn't see these", null)
    if (!ready) { Hint("Starts once $who's phone has the new version and is online. Only helpers see this."); return }
    com.suryaprakash.medlog.ui.FlowRowOf {
        listOf("I'm going there now", "Can someone check on $who?", "I'll call $who", "I can't go today").forEach { q ->
            com.suryaprakash.medlog.ui.Chip(q, false) { com.suryaprakash.medlog.help.FamilyChat.send(ctx, q) }
        }
    }
    BigButton("Write a message", tone = Tone.SECONDARY, icon = Icons.Rounded.Send, height = 52.dp, onClick = { people.firstOrNull()?.let { writingTo = it to null } })
    val recent = chat.sortedByDescending { it.at }.take(20)
    if (recent.isNotEmpty()) {
        com.suryaprakash.medlog.ui.Section("Messages")
        com.suryaprakash.medlog.ui.Timeline(recent.map { m ->
            com.suryaprakash.medlog.ui.TimelineItem("${dayLabel(m.at)} ${timeLabel(m.at)}".trim(), m.text, sub = m.fromName.ifBlank { null })
        })
    }
    writingTo?.let { (person, o) -> WriteToHelper(person, o, who) { writingTo = null } }
}

/** A message to one helper ([to]), or to all of them: a few ready lines, or your own words. */
@Composable
private fun WriteToHelper(person: com.suryaprakash.medlog.data.CaredFor, to: OtherHelper?, who: String, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val sc = LocalScale.current
    var text by remember { mutableStateOf("") }
    fun send(t: String) { com.suryaprakash.medlog.help.FamilyChat.send(ctx, t, person, to = to?.pairId, toName = to?.name); onDone() }
    com.suryaprakash.medlog.ui.AppSheet(onDismissRequest = onDone) {
        Column(Modifier.fillMaxWidth().padding(horizontal = sc.margin).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            com.suryaprakash.medlog.ui.SectionHeader(if (to != null) "Message ${to.name}" else "Message all helpers",
                if (to != null) "Only ${to.name} sees this" else "$who doesn't see this", null)
            com.suryaprakash.medlog.ui.FlowRowOf {
                (if (to != null) listOf("Can you go to $who now?", "Can you give the medicine?", "I'm on my way", "Call me when you can")
                 else listOf("I'm going there now", "Can someone check on $who?")).forEach { q -> com.suryaprakash.medlog.ui.Chip(q, false) { send(q) } }
            }
            BigField("Your message", text, { text = it }, lines = 3)
            BigButton("Send", icon = Icons.Rounded.Send, enabled = text.isNotBlank(), onClick = { send(text.trim()) })
        }
    }
}

/**
 * Me | Helping, beside the name on both home pages: two icons, switches at once (no question), and the whole
 * app changes colour with it, teal for my health and blue for helping, so the mode is never mistaken.
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
        Body("Allow this phone to ring when you are needed, even when it is asleep.", bold = true)
        BigButton("Allow", tone = Tone.SECONDARY, onClick = {
            if (need.isNotEmpty()) ask(need.flatMap { it.perms.toList() }.toTypedArray()) else Perms.openBattery(ctx)
        })
    }
}

@Suppress("unused") private val keepAlerts = Alerts.Type.MESSAGE

/** Set before opening the pairing screen to connect to someone this phone helps (rather than to its own helper). */
object PairMode { var helping = false }
