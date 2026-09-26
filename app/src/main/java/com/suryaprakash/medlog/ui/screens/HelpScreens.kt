package com.suryaprakash.medlog.ui.screens

import android.content.Context
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
    Screen("Ask family", speak, onHome = { nav.home() }, onBack = { nav.back() }) {
        if (helpers.isEmpty()) Card(border = p.amber) {
            Body("Add at least one helper so MedLog knows who to ask.", bold = true)
            BigButton("Add a helper", onClick = { nav.go(Route.HelperEdit(null)) })
        }
        if (!smsOk) Card(border = p.amber) { Body("Allow MedLog to send text messages, so your messages always get through."); BigButton("Allow", tone = Tone.QUIET, onClick = { ask(Perms.SMS + Perms.CALL) }) }

        // ── what happened to the last message, person by person ──
        status?.let { st ->
            val edge = when (st.stage) { "answered" -> p.ok; "noanswer" -> p.amber; "failed" -> p.red; else -> null }
            Card(border = edge) {
                Hint("You sent at ${timeLabel(st.at)}")
                Body("\"${st.text}\"", bold = true)
                acks.forEach { a -> Body("✓ ${a.name}: ${Nearby.replyWords(a.reply)}", bold = true) }
                reached.filter { n -> acks.none { it.name == n } }.forEach { n -> Body("✓ $n's phone got it") }
                st.texted.filter { n -> acks.none { it.name == n } && n !in reached }.forEach { n -> Body("✓ Text message sent to $n") }
                when (st.stage) {
                    "sending" -> Hint("Sending…")
                    "sent" -> if (acks.isEmpty()) Hint("Waiting for an answer…")
                    "noanswer" -> Body("Nobody has answered yet.", bold = true)
                    "failed" -> Body("The message could not be sent. Please call someone.", bold = true)
                }
                if (st.stage == "noanswer" || st.stage == "failed") helpers.firstOrNull()?.let { h -> BigButton("Call ${h.name}", icon = Icons.Rounded.Call, onClick = { Calls.call(ctx, h.phone) }) }
            }
        }

        // ── the person's own messages ──
        val msgs = s.messages
        if (msgs.isEmpty()) Card {
            Body("Choose the messages you want here. Pick only the ones you'll use.", bold = true)
            BigButton("Choose my messages", icon = Icons.Rounded.Add, onClick = { nav.go(Route.Messages) })
        } else {
            Title("Send a message")
            com.suryaprakash.medlog.ui.TileGrid(msgs, 2, aspect = 1.45f) { m, mod ->
                val key = m.substringBefore('|'); val text = m.substringAfter('|')
                val tint = p.ink
                com.suryaprakash.medlog.ui.Tile("Send: $text", mod, onClick = { HelpMessages.send(ctx, text); app.speaker.say("Sending: $text") }) {
                    com.suryaprakash.medlog.ui.IconTile(HelpMessages.icon(key), tint, 44.dp)
                    androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 10.dp))
                    Text(text, fontSize = sc.body, fontWeight = FontWeight.SemiBold, color = p.ink, textAlign = TextAlign.Center, maxLines = 2)
                }
            }
            BigButton("Change my messages", tone = Tone.QUIET, onClick = { nav.go(Route.Messages) })
        }

        Title("Something else")
        com.suryaprakash.medlog.ui.SearchBox(custom, { custom = it }, "Type, or tap Speak")
        if (custom.isNotBlank()) Toggle("Keep it for next time", saveCustom) { saveCustom = it }
        BigButton("Send", icon = Icons.Rounded.Send, enabled = custom.isNotBlank(), onClick = {
            val t = custom.trim()
            if (saveCustom && s.messages.none { it.substringAfter('|').equals(t, true) }) app.settings.update { it.copy(messages = it.messages + "custom_${System.currentTimeMillis()}|$t") }
            HelpMessages.send(ctx, t); custom = ""
        })
        Title("Call someone")
        helpers.forEach { h -> BigButton("Call ${h.name}", tone = Tone.SECONDARY, icon = Icons.Rounded.Call, sub = h.relation.ifBlank { null }, onClick = { Calls.call(ctx, h.phone) }) }
        BigButton("Call ${s.emergencyNumber} (ambulance)", tone = Tone.DANGER, icon = Icons.Rounded.Call, onClick = { Calls.call(ctx, s.emergencyNumber) })
        BigButton("My helpers", tone = Tone.QUIET, onClick = { nav.go(Route.Helpers) })
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
        BigButton("Save", tone = Tone.OK, enabled = h.name.isNotBlank() && h.phone.count(Char::isDigit) >= 6, onClick = {
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
    val helperSide = s.role == "helper"
    Screen("Connect phones", if (helperSide) "Hold this phone next to the other person's phone." else "Ask your helper to open MedLog on their phone and choose I'm a helper.", onHome = { nav.home(if (helperSide) Route.HelperHome else Route.Home) }, onBack = { nav.back() }) {
        if (!allowed) { Body("MedLog needs Bluetooth and nearby devices to pair."); BigButton("Allow", onClick = { ask(Perms.NEARBY + Perms.NOTIFY) }); return@Screen }
        st.done?.let { name ->
            Card(border = p.ok) { Body(if (helperSide) "Paired with $name. You'll be alerted when $name needs you." else "$name's phone is paired.", bold = true) }
            BigButton("Done", tone = Tone.OK, onClick = { nav.home(if (helperSide) Route.HelperHome else Route.Home) })
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
    val paired = app.settings.getString("pair_id") != null
    val asking by Nearby.asking.collectAsState()
    val who = s.pairedWith.ifBlank { "them" }
    var settings by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    if (settings) { androidx.activity.compose.BackHandler { settings = false }; HelperSettings(nav, onBack = { settings = false }); return }
    val fromPerson = inbox.filter { it.kind != com.suryaprakash.medlog.help.FamilyChat.KIND }
    Screen(if (paired) s.pairedWith.ifBlank { "MedLog Helper" } else "MedLog Helper", if (paired) "The latest from $who, and your reply." else "Connect to the phone of the person you help.", onHome = null,
        trailing = { com.suryaprakash.medlog.ui.RoundIcon(Icons.Rounded.Settings, "Settings") { settings = true } }) {
        PermissionListCompact()
        if (!paired) {
            com.suryaprakash.medlog.ui.Question("Connect to their phone", "Hold both phones close. It takes a minute, once.")
            BigButton("Connect", icon = Icons.Rounded.Bluetooth, onClick = { nav.go(Route.Pair) })
            return@Screen
        }
        // ── the latest from them: the most important thing on this screen ──
        val latest = fromPerson.firstOrNull()
        if (latest == null) com.suryaprakash.medlog.ui.Card { Hint("No messages from $who yet. You'll hear an alarm when one comes.") }
        else {
            val urgent = latest.kind in setOf("SOS", "DANGER", "FALL")
            com.suryaprakash.medlog.ui.Card(border = if (!latest.acked) (if (urgent) p.red else p.brand) else null) {
                Hint("${dayLabel(latest.at)} ${timeLabel(latest.at)}")
                Text(latest.text, fontSize = sc.question, fontWeight = FontWeight.Bold, color = p.ink, lineHeight = sc.question * 1.15f)
                if (!latest.acked) {
                    fun reply(r: String) { com.suryaprakash.medlog.help.AlertSound.stop(); scope.launch { app.db.inbox().ack(latest.id); Nearby.reply(ctx, r) } }
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
            enabled = s.internetLink, onClick = { Nearby.ask(ctx) })
        asking?.let { a ->
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
        if (fromPerson.size > 1) {
            com.suryaprakash.medlog.ui.Section("Earlier")
            com.suryaprakash.medlog.ui.Group {
                fromPerson.drop(1).take(6).forEachIndexed { i, m ->
                    if (i > 0) com.suryaprakash.medlog.ui.GroupLine()
                    com.suryaprakash.medlog.ui.ValueRow(m.text, "${dayLabel(m.at)} ${timeLabel(m.at)}".trim(), valueColor = if (m.kind in setOf("SOS", "DANGER", "FALL")) p.red else null)
                }
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
        RoleSwitch(nav)
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
            YesNo(yes = "Yes, switch", no = "No", onYes = {
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
