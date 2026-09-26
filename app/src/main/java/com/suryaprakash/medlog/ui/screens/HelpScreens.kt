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
    var recording by remember { mutableStateOf<MediaRecorder?>(null) }
    var voiceFile by remember { mutableStateOf<File?>(null) }
    var smsOk by remember { mutableStateOf(Perms.has(ctx, *Perms.SMS)) }
    val ask = rememberPermissionAsker { smsOk = Perms.has(ctx, *Perms.SMS) }
    DisposableEffect(Unit) { onDispose { runCatching { recording?.release() } } }

    fun stopRecording() {
        runCatching { recording?.stop() }; runCatching { recording?.release() }; recording = null
    }

    val speak = "Tap a message to send it to your family. For an emergency, tap the red SOS button at the bottom. " + (if (helpers.isEmpty()) "You have no helpers yet. Add one first." else "")
    Screen("Ask family", speak, onHome = { nav.home() }, onBack = { nav.back() }) {
        if (helpers.isEmpty()) Card(color = p.amberSoft, border = p.amber) {
            Body("Add at least one helper so MedLog knows who to ask.", bold = true)
            BigButton("Add a helper", onClick = { nav.go(Route.HelperEdit(null)) })
        }
        if (!smsOk) Card(color = p.amberSoft) { Body("Allow MedLog to send text messages, so your messages always get through."); BigButton("Allow", tone = Tone.QUIET, onClick = { ask(Perms.SMS + Perms.CALL) }) }

        // ── what happened to the last message, person by person ──
        status?.let { st ->
            val color = when (st.stage) { "answered" -> p.okSoft; "noanswer" -> p.amberSoft; "failed" -> p.redSoft; else -> p.brandSoft }
            Card(color = color) {
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
        if (msgs.isEmpty()) Card(color = p.brandSoft) {
            Body("Choose the messages you want here. Pick only the ones you'll use.", bold = true)
            BigButton("Choose my messages", icon = Icons.Rounded.Add, onClick = { nav.go(Route.Messages) })
        } else {
            Title("Send a message")
            com.suryaprakash.medlog.ui.TileGrid(msgs, 2, aspect = 1.45f) { m, mod ->
                val key = m.substringBefore('|'); val text = m.substringAfter('|')
                val tint = when (key) { "unwell" -> p.red; "water", "please_come", "walk" -> p.tintBlue; "bathroom", "bed" -> p.tintPurple; "medicine" -> p.tintOrange; "hungry" -> p.tintGreen; else -> p.tintTeal }
                com.suryaprakash.medlog.ui.Tile("Send: $text", mod, onClick = { HelpMessages.send(ctx, text); app.speaker.say("Sending: $text") }) {
                    com.suryaprakash.medlog.ui.IconTile(HelpMessages.icon(key), tint, 44.dp)
                    androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 10.dp))
                    Text(text, fontSize = sc.body, fontWeight = FontWeight.SemiBold, color = p.ink, textAlign = TextAlign.Center, maxLines = 2)
                }
            }
            BigButton("Change my messages", tone = Tone.QUIET, onClick = { nav.go(Route.Messages) })
        }

        Title("Something else")
        BigField("Type a message", custom, { custom = it })
        if (custom.isNotBlank()) Toggle("Keep it for next time", saveCustom) { saveCustom = it }
        BigButton("Send", icon = Icons.Rounded.Send, enabled = custom.isNotBlank(), onClick = {
            val t = custom.trim()
            if (saveCustom && s.messages.none { it.substringAfter('|').equals(t, true) }) app.settings.update { it.copy(messages = it.messages + "custom_${System.currentTimeMillis()}|$t") }
            HelpMessages.send(ctx, t); custom = ""
        })
        if (recording == null) BigButton("Record a voice message", tone = Tone.QUIET, icon = Icons.Rounded.Mic, enabled = Perms.has(ctx, *Perms.MIC), onClick = {
            val f = File(File(ctx.filesDir, "audio").apply { mkdirs() }, "voice_${System.currentTimeMillis()}.amr")
            voiceFile = f
            recording = (if (Build.VERSION.SDK_INT >= 31) MediaRecorder(ctx) else @Suppress("DEPRECATION") MediaRecorder()).apply {
                setAudioSource(MediaRecorder.AudioSource.MIC); setOutputFormat(MediaRecorder.OutputFormat.AMR_NB); setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                setMaxDuration(10_000); setOutputFile(f.absolutePath); prepare(); start()
                setOnInfoListener { _, what, _ -> if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) scope.launch { stopRecording() } }
            }
        }) else BigButton("Stop recording", tone = Tone.DANGER, icon = Icons.Rounded.Stop, onClick = { stopRecording() })
        voiceFile?.takeIf { recording == null && it.exists() && it.length() > 0 }?.let { f ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BigButton("Play", Modifier.weight(1f), Tone.SECONDARY, icon = Icons.Rounded.PlayArrow, onClick = { runCatching { MediaPlayer().apply { setDataSource(f.absolutePath); prepare(); start() } } })
                BigButton("Send", Modifier.weight(1f), icon = Icons.Rounded.Send, onClick = { HelpMessages.send(ctx, "Voice message", f); voiceFile = null })
            }
        }
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
            else Card(color = p.redSoft) {
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
    Screen("Pair phones", if (helperSide) "Hold this phone next to the other person's phone." else "Ask your helper to open MedLog on their phone and choose I'm a helper.", onHome = { nav.home(if (helperSide) Route.HelperHome else Route.Home) }, onBack = { nav.back() }) {
        if (!allowed) { Body("MedLog needs Bluetooth and nearby devices to pair."); BigButton("Allow", onClick = { ask(Perms.NEARBY + Perms.NOTIFY) }); return@Screen }
        st.done?.let { name ->
            Card(color = p.okSoft) { Body(if (helperSide) "Paired with $name. You'll be alerted when $name needs you." else "$name's phone is paired.", bold = true) }
            BigButton("Done", tone = Tone.OK, onClick = { nav.home(if (helperSide) Route.HelperHome else Route.Home) })
            return@Screen
        }
        st.error?.let { Card(color = p.amberSoft) { Body(it) } }
        st.digits?.let { d ->
            Card(color = p.brandSoft) {
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
                BigField("$name's phone number", phone, { phone = it }, keyboard = KeyboardType.Phone, hint = "Used for SMS if Bluetooth can't reach")
                BigButton("Pair with $name", enabled = phone.count(Char::isDigit) >= 6, onClick = { Nearby.pairWith(ctx, eid, name, phone) })
            }
        }
    }
}

/** The helper's phone (plan 5, screen 19): who they look after, alerts received, and a quick call. */
@Composable
fun HelperHomeScreen(nav: Nav) {
    val ctx = LocalContext.current
    val app = ctx.medlog
    val p = LocalPalette.current
    val s = LocalSettings.current
    val inbox by app.db.inbox().flow().collectAsState(emptyList())
    LaunchedEffect(Unit) { Nearby.startListening(ctx) }
    val paired = app.settings.getString("pair_id") != null
    val asking by Nearby.asking.collectAsState()
    val who = s.pairedWith.ifBlank { "them" }
    Screen(if (paired) "Helping ${s.pairedWith}" else "MedLog Helper", if (paired) "You'll be alerted here when ${s.pairedWith} needs you. Tap Ask how they are to check on them." else "Pair with the phone of the person you help.", onHome = null) {
        if (!paired) BigButton("Pair with their phone", icon = Icons.Rounded.Bluetooth, onClick = { nav.go(Route.Pair) })
        else {
            // ── check on them, before they have to ask ──
            BigButton("Ask $who: How are you?", icon = Icons.Rounded.ChatBubble, height = LocalScale.current.target * 1.5f, enabled = s.internetLink, onClick = { Nearby.ask(ctx) })
            if (!s.internetLink) Hint("Needs the internet link, which is off on $who's phone.")
            asking?.let { a ->
                Card(color = if (a.answer != null) p.okSoft else if (a.sent == false) p.amberSoft else p.brandSoft) {
                    Hint("You asked at ${timeLabel(a.at)}")
                    Body(when {
                        a.answer != null -> "$who: ${a.answer}"
                        a.sent == false -> "Couldn't send. Check the internet and try again."
                        a.got -> "$who's phone got it. Waiting for an answer…"
                        a.sent == true -> "Sent. Waiting for $who's phone…"
                        else -> "Sending…"
                    }, bold = true)
                    if (a.answer != null) Hint("at ${timeLabel(a.answerAt)}")
                }
            }
            val link by Relay.link.collectAsState()
            val far = when {
                !s.internetLink -> "Far away: text messages only."
                link == Relay.Link.ON -> "Far away: connected through the internet."
                link == Relay.Link.NO_INTERNET -> "No internet right now. MedLog keeps trying, and text messages still come."
                else -> "Connecting through the internet…"
            }
            Card(color = if (link == Relay.Link.NO_INTERNET && s.internetLink) p.amberSoft else p.okSoft) {
                Body("Listening for ${s.pairedWith}.", bold = true)
                Hint("Nearby: by Bluetooth, no internet needed.")
                Hint(far)
            }
        }
        if (paired) FamilyChatSection(who, inbox.filter { it.kind == com.suryaprakash.medlog.help.FamilyChat.KIND })
        PermissionListCompact()
        Title("Messages")
        val fromPerson = inbox.filter { it.kind != com.suryaprakash.medlog.help.FamilyChat.KIND }
        if (fromPerson.isEmpty()) Hint("No messages yet.")
        fromPerson.forEach { i ->
            val worried = i.kind in setOf("SOS", "DANGER", "FALL") && !i.acked
            Card(color = if (worried) p.redSoft else if (i.kind == "ANSWER") p.okSoft else p.card) {
                Hint("${dayLabel(i.at)} ${timeLabel(i.at)}${if (i.acked && i.kind != "ANSWER") " · answered" else ""}")
                Body("${i.fromName}: ${i.text}", bold = true)
                i.audioPath?.let { a -> BigButton("Play voice", tone = Tone.QUIET, icon = Icons.Rounded.PlayArrow, onClick = { runCatching { MediaPlayer().apply { setDataSource(a); prepare(); start() } } }) }
            }
        }
        BigButton("Pair again", tone = Tone.SECONDARY, onClick = { nav.go(Route.Pair) })
        RoleSwitch(nav)
    }
}

/** Quick messages between helpers; the person doesn't see them. */
@Composable
private fun FamilyChatSection(who: String, chat: List<com.suryaprakash.medlog.data.InboxItem>) {
    val ctx = LocalContext.current
    val app = ctx.medlog
    val p = LocalPalette.current
    var name by remember { mutableStateOf(com.suryaprakash.medlog.help.FamilyChat.myName(ctx)) }
    val ready = com.suryaprakash.medlog.help.FamilyChat.key(ctx) != null
    Title("Tell the other helpers")
    Hint("Only helpers see this. $who doesn't.")
    if (!ready) { Hint("It starts once $who's phone has the new MedLog and is online."); return }
    if (com.suryaprakash.medlog.help.FamilyChat.myName(ctx).isBlank()) Card(color = p.brandSoft) {
        BigField("Your name", name, { name = it }, hint = "The other helpers will see this")
        BigButton("Save", tone = Tone.QUIET, enabled = name.isNotBlank(), onClick = { app.settings.putString("my_name", name.trim()) })
    }
    fun send(t: String) { com.suryaprakash.medlog.help.FamilyChat.send(ctx, t); app.speaker.say("Sent: $t") }
    listOf("I'm going there now", "Can someone check on $who?", "I'll call $who", "I can't go today").forEach { q ->
        BigButton(q, tone = Tone.QUIET, onClick = { send(q) })
    }
    // the latest few, newest first
    chat.sortedByDescending { it.at }.take(4).forEach { m ->
        Card(color = if (m.fromName == "You") p.brandSoft else p.card) {
            Hint("${m.fromName} · ${dayLabel(m.at)} ${timeLabel(m.at)}")
            Body(m.text, bold = true)
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
    if (need.isNotEmpty()) Card(color = LocalPalette.current.amberSoft) {
        Body("To hear alerts, MedLog needs: " + need.joinToString(", ") { it.title.lowercase() })
        BigButton("Allow", tone = Tone.QUIET, onClick = { ask(need.flatMap { it.perms.toList() }.toTypedArray()) })
    }
    // Android pauses the internet for sleeping phones unless the app is let off battery saving
    var batteryOk by remember { mutableStateOf(Perms.batteryOk(ctx)) }
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) { batteryOk = Perms.batteryOk(ctx); onPauseOrDispose {} }
    if (!batteryOk && ctx.medlog.settings.value.internetLink) Card(color = LocalPalette.current.amberSoft) {
        Body("Let MedLog run in the background, so alerts from far away arrive even when this phone is asleep.")
        BigButton("Allow", tone = Tone.QUIET, onClick = { Perms.openBattery(ctx) })
    }
}

@Suppress("unused") private val keepAlerts = Alerts.Type.MESSAGE
