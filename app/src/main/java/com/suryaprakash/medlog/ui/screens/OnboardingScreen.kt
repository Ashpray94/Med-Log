package com.suryaprakash.medlog.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.Sos
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Icon
import com.suryaprakash.medlog.ui.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.suryaprakash.medlog.data.Helper
import com.suryaprakash.medlog.data.Profile
import com.suryaprakash.medlog.medlog
import com.suryaprakash.medlog.meds.Scheduler
import com.suryaprakash.medlog.pictogram.SpriteIcon
import com.suryaprakash.medlog.ui.BigButton
import com.suryaprakash.medlog.ui.Body
import com.suryaprakash.medlog.ui.BigField
import com.suryaprakash.medlog.ui.LocalPalette
import com.suryaprakash.medlog.ui.LocalScale
import com.suryaprakash.medlog.ui.LocalSettings
import com.suryaprakash.medlog.ui.Nav
import com.suryaprakash.medlog.ui.Perms
import com.suryaprakash.medlog.ui.ReadAloud
import com.suryaprakash.medlog.ui.Route
import com.suryaprakash.medlog.ui.Segmented
import com.suryaprakash.medlog.ui.Toggle
import com.suryaprakash.medlog.ui.Tone
import com.suryaprakash.medlog.ui.rememberContactPicker
import com.suryaprakash.medlog.ui.rememberPermissionAsker
import com.suryaprakash.medlog.ui.steady
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * First-time setup, from the welcome page to the home screen.
 *
 * One question per page, one main button, a progress bar, and Back on every page. Each answer is saved the moment
 * it is given, so leaving to pair a helper's phone (or closing the app) loses nothing. A family member often does
 * this with the person; every page is written so either of them can answer it.
 */
private object Onboard {
    var step by mutableIntStateOf(0)
    var editing by mutableStateOf<Long?>(null)      // helper being changed, null = new
    var justAdded by mutableStateOf<Long?>(null)    // helper to offer "let them know" for
}

private const val WELCOME = 0
private const val WHO = 1
private const val EASY = 2
private const val LANGS = 3
private const val NAME = 4
private const val AGE = 5
private const val ILLNESS = 6
private const val ALLERGY = 7
private const val THINNER = 8
private const val DOCTOR = 9
private const val HELPERS = 10
private const val HELPER_FORM = 11
private const val PERMISSIONS = 12
private const val WIDGET = 13
private const val DONE = 14

/** Steps that show in the progress bar (the welcome, who-is-it-for and finish pages don't). */
private val COUNTED = listOf(EASY, LANGS, NAME, AGE, ILLNESS, ALLERGY, THINNER, DOCTOR, HELPERS, PERMISSIONS, WIDGET)

@Composable
fun OnboardingScreen(nav: Nav) {
    val ctx = LocalContext.current
    val app = ctx.medlog
    val s = LocalSettings.current
    val scope = rememberCoroutineScope()
    var profile by remember { mutableStateOf<Profile?>(null) }
    LaunchedEffect(Unit) { if (profile == null) profile = app.repo.profile() }
    val pr = profile ?: Profile()
    fun save(p: Profile) { profile = p; scope.launch { app.db.profile().put(p) } }
    fun go(step: Int) { Onboard.step = step }
    var setupError by remember { mutableStateOf<String?>(null) }
    var confirmSkip by remember { mutableStateOf(false) }
    val loadSetup = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri: android.net.Uri? ->
        if (uri != null) scope.launch {
            runCatching { com.suryaprakash.medlog.data.SetupFile.import(ctx, uri) }
                .onSuccess { Onboard.step = WELCOME; app.speaker.say("Setup loaded. Welcome back."); nav.home(Route.Home) }
                .onFailure { setupError = "That file isn't a MedLog setup file." }
        }
    }
    val first = pr.name.substringBefore(" ").ifBlank { "you" }
    // the phone's own Back button steps back through setup instead of closing the app
    val prev = mapOf(WHO to WELCOME, EASY to WHO, LANGS to EASY, NAME to LANGS, AGE to NAME, ILLNESS to AGE, ALLERGY to ILLNESS, THINNER to ALLERGY,
        DOCTOR to THINNER, HELPERS to DOCTOR, HELPER_FORM to HELPERS, PERMISSIONS to HELPERS, WIDGET to PERMISSIONS, DONE to WIDGET)
    androidx.activity.compose.BackHandler(enabled = Onboard.step != WELCOME) { prev[Onboard.step]?.let { go(it) } }

    when (Onboard.step) {
        // ───────────── welcome ─────────────
        WELCOME -> Step(null, "Welcome to MedLog", "MedLog helps you keep track of how you feel, take your medicines on time, and get help from family. Everything stays on this phone.",
            primary = "Let's begin", onPrimary = { go(WHO) }) {
            Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(112.dp).clip(CircleShape).background(LocalPalette.current.brand), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Favorite, null, tint = Color(0xFFFF8A80), modifier = Modifier.size(56.dp))
                }
            }
            Group {
                Point(Icons.Rounded.Mic, "Say how you feel", "Talk, or tap a picture")
                Divider()
                Point(Icons.Rounded.Medication, "Never miss a medicine", "Reminders that ring, even on silent")
                Divider()
                Point(Icons.Rounded.Sos, "Help in one tap", "Your family is called and told where you are")
                Divider()
                Point(Icons.Rounded.Description, "One clear page for your doctor", "What happened, when, and how bad")
            }
            Note(Icons.Rounded.Lock, "Your notes stay on this phone.")
            // set up before? load it and skip every question
            BigButton("I have a setup file", tone = Tone.QUIET, onClick = { setupError = null; loadSetup.launch(arrayOf("application/json", "application/octet-stream", "*/*")) })
            setupError?.let { Body(it, bold = true) }
            if (!confirmSkip) BigButton("Skip setup", tone = Tone.QUIET, onClick = { confirmSkip = true })
            else com.suryaprakash.medlog.ui.Card {
                Body("Skip the questions? You can fill in your details, helpers and languages later in Settings.", bold = true)
                com.suryaprakash.medlog.ui.YesNo(yes = "Skip", no = "Answer them", onYes = {
                    app.settings.update { it.copy(role = "self", onboarded = true) }; Onboard.step = WELCOME; nav.home(Route.Home)
                }, onNo = { confirmSkip = false })
            }
        }

        // ───────────── who is it for ─────────────
        WHO -> Step(null, "Who will use MedLog on this phone?", "Who will use MedLog on this phone? Tap Me if it is for your own health. Tap I'm a helper if you look after someone else who has MedLog.",
            onBack = { go(WELCOME) }) {
            BigChoice("Me", "I want to keep track of my own health", selected = false) { go(EASY) }
            BigChoice("I'm a helper", "I look after someone who uses MedLog on their phone", selected = false) {
                app.settings.update { it.copy(role = "helper", onboarded = true) }
                Onboard.step = WELCOME
                nav.home(Route.HelperHome); nav.go(Route.Pair)
            }
            Note(null, "Setting this up for a parent or someone you care for? Choose Me, answer together, and hand them the phone at the end.")
        }

        // ───────────── easier to see and hear ─────────────
        EASY -> Step(EASY, "How should MedLog look?", "Choose how MedLog should look. Easy has bigger text and buttons, and reads each page aloud.", onBack = { go(WHO) }) {
            SizeChoice(big = true, selected = s.bigMode) {
                app.settings.update { it.copy(easyMode = true, bigMode = true, autoRead = true) }; go(LANGS)
            }
            SizeChoice(big = false, selected = !s.bigMode) {
                app.settings.update { it.copy(easyMode = true, bigMode = false, autoRead = false) }; go(LANGS)
            }
            Note(null, "You can change this any time in Settings.")
        }

        // ───────────── languages ─────────────
        LANGS -> Step(LANGS, "Which languages do you speak?", "Tap every language you speak. You can talk to MedLog in any of them.",
            onBack = { go(EASY) }, primary = "Next", onPrimary = { go(NAME) }) {
            com.suryaprakash.medlog.clinical.Lang.ALL.chunked(2).forEach { row ->
                Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { l ->
                        val on = l.tag in s.languages
                        Tile(on, Modifier.weight(1f).fillMaxHeight(), l.name + if (on) ", selected" else "", {
                            app.settings.update { st -> val list = if (l.tag in st.languages) st.languages - l.tag else st.languages + l.tag; st.copy(languages = list.ifEmpty { listOf("en-IN") }) }
                        }) {
                            Text(l.name, fontSize = LocalScale.current.body, fontWeight = FontWeight.Bold, color = if (on) LocalPalette.current.onBrand else LocalPalette.current.ink, maxLines = 1)
                            Text(l.native, fontSize = LocalScale.current.small, color = if (on) LocalPalette.current.onBrand else LocalPalette.current.inkSoft, maxLines = 1)
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            Note(null, "The first one you chose is your main language.")
        }

        // ───────────── name ─────────────
        NAME -> Step(NAME, "What is your name?", "What is your name? It goes on your doctor page, and your family sees it in help messages.",
            onBack = { go(LANGS) }, primary = "Next", primaryEnabled = pr.name.isNotBlank(), onPrimary = { go(AGE) }, secondary = "Skip", onSecondary = { go(AGE) }) {
            BigField("Your name", pr.name, { save(pr.copy(name = it)) })
            Note(null, "It goes on your doctor page, and your family sees it in help messages.")
        }

        // ───────────── age ─────────────
        AGE -> {
            var year by remember { mutableStateOf(pr.dob.take(4)) }
            val ok = year.toIntOrNull()?.let { it in 1900..java.time.LocalDate.now().year } == true
            Step(AGE, "When were you born?", "Which year were you born? And are you a woman or a man? This helps the doctor.",
                onBack = { go(NAME) }, primary = "Next", primaryEnabled = ok || year.isBlank(),
                onPrimary = { if (ok && pr.dob.take(4) != year) save(pr.copy(dob = "$year-07-01")); go(ILLNESS) }) {
                BigField("Year you were born", year, { year = it.filter(Char::isDigit).take(4) }, keyboard = KeyboardType.Number, hint = "For example 1952")
                Label("You are")
                val o = listOf("F" to "A woman", "M" to "A man", "" to "Rather not say")
                Segmented(o.map { it.second }, o.indexOfFirst { it.first == pr.sex }) { save(pr.copy(sex = o[it].first)) }
            }
        }

        // ───────────── long-term illnesses ─────────────
        ILLNESS -> {
            val common = listOf("Diabetes", "High BP", "Heart problem", "Asthma or COPD", "Thyroid", "Joint pain", "Kidney problem", "Had a stroke")
            val chosen = pr.conditions.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            var other by remember { mutableStateOf(chosen.filter { it !in common }.joinToString(", ")) }
            fun set(list: List<String>, extra: String) = save(pr.copy(conditions = (list.filter { it in common } + extra.split(",").map { it.trim() }.filter { it.isNotEmpty() }).distinct().joinToString(", ")))
            Step(ILLNESS, "Any long-term illness?", "Do you have any long-term illness? Tap all that apply. If none, tap None.",
                onBack = { go(AGE) }, primary = if (chosen.isEmpty()) "None" else "Next", onPrimary = { go(ALLERGY) }) {
                common.chunked(2).forEach { row ->
                    Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { c ->
                            val on = c in chosen
                            Tile(on, Modifier.weight(1f).fillMaxHeight(), c + if (on) ", selected" else "", { set(if (on) chosen - c else chosen + c, other) }) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(c, fontSize = LocalScale.current.body, fontWeight = FontWeight.SemiBold, color = if (on) LocalPalette.current.onBrand else LocalPalette.current.ink, modifier = Modifier.weight(1f))
                                    if (on) Icon(Icons.Rounded.Check, null, tint = LocalPalette.current.onBrand, modifier = Modifier.size(22.dp))
                                }
                            }
                        }
                    }
                }
                BigField("Something else", other, { other = it; set(chosen, it) }, hint = "For example: glaucoma")
            }
        }

        // ───────────── allergies ─────────────
        ALLERGY -> {
            var has by remember { mutableStateOf(pr.allergies.isNotBlank()) }
            Step(ALLERGY, "Are you allergic to any medicine?", "Are you allergic to any medicine or food? This keeps you safe if a doctor gives you something new.",
                onBack = { go(ILLNESS) }, primary = if (has) "Next" else null, primaryEnabled = pr.allergies.isNotBlank(), onPrimary = { go(THINNER) }) {
                BigChoice("No", "Not that I know of", selected = false) { has = false; save(pr.copy(allergies = "")); go(THINNER) }
                BigChoice("Yes", "I'll write what it is", selected = has) { has = true }
                if (has) BigField("What are you allergic to?", pr.allergies, { save(pr.copy(allergies = it)) }, hint = "For example: penicillin (rash)")
            }
        }

        // ───────────── blood thinner ─────────────
        THINNER -> Step(THINNER, "Do you take a blood thinner?", "Do you take a blood thinner, like Ecosprin, Warfarin or Clopidogrel? It matters after a fall or a cut.",
            onBack = { go(ALLERGY) }) {
            Note(null, "For example Ecosprin, Warfarin, Clopidogrel, Apixaban. It matters after a fall or a cut.")
            BigChoice("Yes", null, selected = pr.onBloodThinner) { save(pr.copy(onBloodThinner = true)); go(DOCTOR) }
            BigChoice("No", null, selected = false) { save(pr.copy(onBloodThinner = false)); go(DOCTOR) }
            BigChoice("I'm not sure", "You can check your medicines later", selected = false) { save(pr.copy(onBloodThinner = false)); go(DOCTOR) }
        }

        // ───────────── doctor ─────────────
        DOCTOR -> Step(DOCTOR, "Who is your doctor?", "Who is your doctor? MedLog can call them with one tap. You can skip this.",
            onBack = { go(THINNER) }, primary = "Next", onPrimary = { go(HELPERS) }, secondary = "Skip", onSecondary = { go(HELPERS) }) {
            BigField("Doctor's name", pr.doctorName, { save(pr.copy(doctorName = it)) }, hint = "For example: Dr. Kumar")
            BigField("Doctor's phone number", pr.doctorPhone, { save(pr.copy(doctorPhone = it)) }, keyboard = KeyboardType.Phone)
        }

        // ───────────── helpers ─────────────
        HELPERS -> HelpersStep(nav, first) { go(it) }
        HELPER_FORM -> HelperForm { go(HELPERS) }

        // ───────────── permissions ─────────────
        PERMISSIONS -> PermissionsStep(onBack = { go(HELPERS) }) { go(WIDGET) }

        // ───────────── widget ─────────────
        WIDGET -> Step(WIDGET, "Put MedLog on your home screen", "Put MedLog on your home screen. Then you can tell how you feel, or call for help, without opening the app.",
            onBack = { go(PERMISSIONS) }, primary = "Add to home screen", onPrimary = { pinWidget(ctx); scope.launch { delay(600); go(DONE) } },
            secondary = "Not now", onSecondary = { go(DONE) }) {
            WidgetPreview()
            Note(null, "Tap a picture to note it straight away, or the red button for help.")
        }

        // ───────────── finish ─────────────
        else -> DoneStep(nav, pr)
    }
}

// ───────────────────────── helpers step ─────────────────────────

@Composable
private fun HelpersStep(nav: Nav, first: String, go: (Int) -> Unit) {
    val ctx = LocalContext.current
    val app = ctx.medlog
    val p = LocalPalette.current
    val sc = LocalScale.current
    val helpers by app.db.helpers().flow().collectAsState(emptyList())
    var askSkip by remember { mutableStateOf(false) }
    val s = LocalSettings.current
    Step(HELPERS, "Who should we call if you need help?",
        "Add family or friends. If you need help, MedLog texts them where you are and calls them one by one.",
        onBack = { go(DOCTOR) }, primary = if (helpers.isEmpty()) "Add a helper" else "Next",
        onPrimary = { if (helpers.isEmpty()) { Onboard.editing = null; go(HELPER_FORM) } else go(PERMISSIONS) },
        secondary = if (helpers.isEmpty()) "Skip for now" else null, onSecondary = { askSkip = true }) {
        if (helpers.isEmpty()) {
            Label("What a helper gets")
            Group {
                Point(Icons.Rounded.Sms, "A text with where you are", "The moment you tap SOS")
                Divider()
                Point(Icons.Rounded.Call, "A call, one person at a time", "Until someone answers. Then ${s.emergencyNumber}.")
                Divider()
                Point(Icons.Rounded.Medication, "A note if you miss a medicine", "Only if you want this")
            }
            Note(Icons.Rounded.Lock, "They don't see your notes unless you share your doctor page.")
        } else {
            Group {
                helpers.forEachIndexed { i, h ->
                    if (i > 0) Divider(76.dp)
                    HelperRow(h, i) { Onboard.editing = h.id; go(HELPER_FORM) }
                }
            }
            // just added: let them know, and pair their phone if they want
            helpers.firstOrNull { it.id == Onboard.justAdded }?.let { h ->
                val name = h.name.substringBefore(" ")
                Group {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Let $name know", fontSize = sc.headline, fontWeight = FontWeight.Bold, color = p.ink)
                        Text("So $name isn't surprised by a call from MedLog.", fontSize = sc.small, color = p.inkSoft)
                        BigButton("Send $name a text", tone = Tone.QUIET, icon = Icons.Rounded.Sms, onClick = {
                            val body = "Hi $name, I've added you as my helper in MedLog. If I need help, you'll get a text with where I am and a call. – $first"
                            runCatching { ctx.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${h.phone}")).putExtra("sms_body", body)) }
                        })
                        BigButton("Pair $name's phone", tone = Tone.SECONDARY, icon = Icons.Rounded.Bluetooth, sub = "Optional: their phone rings when you ask for help at home", onClick = { nav.go(Route.Pair) })
                    }
                }
            }
            if (helpers.size < 5) BigButton("Add another helper", tone = Tone.QUIET, icon = Icons.Rounded.PersonAdd, onClick = { Onboard.editing = null; go(HELPER_FORM) })
            Note(null, "In an emergency they are called in this order. If nobody answers, MedLog calls ${s.emergencyNumber}.")
        }
    }
    if (askSkip) Ask(
        "Continue without a helper?",
        "Without a helper, MedLog can only call ${s.emergencyNumber} in an emergency. You can add one later in Settings.",
        yes = "Add a helper", no = "Continue anyway",
        onYes = { askSkip = false; Onboard.editing = null; go(HELPER_FORM) }, onNo = { askSkip = false; go(PERMISSIONS) },
    )
}

@Composable
private fun HelperRow(h: Helper, i: Int, onClick: () -> Unit) {
    val p = LocalPalette.current
    val sc = LocalScale.current
    val tints = listOf(p.tintBlue, p.tintGreen, p.tintOrange, p.tintPurple, p.tintPink)
    val order = listOf("1st", "2nd", "3rd", "4th", "5th")[i.coerceAtMost(4)]
    Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).steady("${h.name}. Called $order. Tap to change.", onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(tints[i % tints.size].copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
            Text(h.name.take(1).uppercase(), fontSize = sc.headline, fontWeight = FontWeight.Bold, color = tints[i % tints.size])
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(h.name, fontSize = sc.body, fontWeight = FontWeight.Bold, color = p.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(listOf(h.relation.ifBlank { null }, h.phone).filterNotNull().joinToString(" · "), fontSize = sc.small, color = p.inkSoft)
            Text(buildList { add("Called $order"); if (h.alerts) add("medicine alerts") }.joinToString(" · "), fontSize = sc.small, color = p.brand, fontWeight = FontWeight.SemiBold)
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = p.inkSoft.copy(alpha = 0.5f), modifier = Modifier.size(24.dp))
    }
}

/** Add or change one helper: contacts first, then who they are and what they receive. */
@Composable
private fun HelperForm(back: () -> Unit) {
    val ctx = LocalContext.current
    val app = ctx.medlog
    val p = LocalPalette.current
    val scope = rememberCoroutineScope()
    val id = Onboard.editing
    var h by remember(id) { mutableStateOf(Helper(name = "", phone = "")) }
    LaunchedEffect(id) { if (id != null) app.db.helpers().all().firstOrNull { it.id == id }?.let { h = it } }
    val pick = rememberContactPicker { n, ph -> h = h.copy(name = n, phone = ph) }
    val relations = listOf("Son", "Daughter", "Husband or wife", "Friend or neighbour", "Carer or nurse", "Other")
    val ok = h.name.isNotBlank() && h.phone.count(Char::isDigit) >= 6
    var confirmRemove by remember { mutableStateOf(false) }
    Step(HELPERS, if (id == null) "Add a helper" else "Change ${h.name.ifBlank { "helper" }}", "Choose someone from your contacts, or type their name and number.",
        onBack = back, primary = "Save", primaryEnabled = ok, onPrimary = {
            scope.launch {
                if (id == null) {
                    val newId = app.db.helpers().insert(h.copy(name = h.name.trim(), phone = h.phone.trim(), sortOrder = app.db.helpers().all().size))
                    Onboard.justAdded = newId
                } else app.db.helpers().update(h.copy(name = h.name.trim(), phone = h.phone.trim()))
                back()
            }
        }, secondary = if (id != null) "Remove this helper" else "Cancel", onSecondary = { if (id != null) confirmRemove = true else back() }) {
        BigButton("Choose from contacts", tone = Tone.QUIET, icon = Icons.Rounded.Contacts, onClick = pick)
        Label("Or type it")
        BigField("Name", h.name, { h = h.copy(name = it) })
        BigField("Phone number", h.phone, { h = h.copy(phone = it) }, keyboard = KeyboardType.Phone)
        Label("Who are they to you?")
        relations.chunked(2).forEach { row ->
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { r ->
                    val on = h.relation == r || (r == "Other" && h.relation.isNotBlank() && h.relation !in relations)
                    Tile(on, Modifier.weight(1f).fillMaxHeight(), r + if (on) ", selected" else "", { h = h.copy(relation = if (h.relation == r) "" else r) }) {
                        Text(r, fontSize = LocalScale.current.body, fontWeight = FontWeight.SemiBold, color = if (on) p.onBrand else p.ink)
                    }
                }
            }
        }
        Label("What should they get?")
        Toggle("Calls and texts if I need help", h.sos, "With where you are") { h = h.copy(sos = it) }
        Toggle("A text if I miss a medicine", h.alerts) { h = h.copy(alerts = it) }
    }
    if (confirmRemove) Ask("Remove ${h.name}?", "${h.name} won't be called or texted if you need help.", yes = "Remove", no = "Keep",
        onYes = { confirmRemove = false; scope.launch { id?.let { app.db.helpers().delete(it) }; if (Onboard.justAdded == id) Onboard.justAdded = null; back() } },
        onNo = { confirmRemove = false })
}

// ───────────────────────── permissions step ─────────────────────────

@Composable
private fun PermissionsStep(onBack: () -> Unit, next: () -> Unit) {
    val ctx = LocalContext.current
    val p = LocalPalette.current
    var tick by remember { mutableIntStateOf(0) }
    // re-check when the person comes back from a system settings page
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) tick++ }
        owner.lifecycle.addObserver(obs); onDispose { owner.lifecycle.removeObserver(obs) }
    }
    val ask = rememberPermissionAsker { tick++ }
    @Suppress("UNUSED_EXPRESSION") tick
    val runtime = listOf(
        Triple(Icons.Rounded.Mic, "Hear you", "So you can talk instead of typing") to Perms.MIC,
        Triple(Icons.Rounded.Notifications, "Remind you", "Medicine times and check-ins") to Perms.NOTIFY,
        Triple(Icons.Rounded.Sms, "Text your helpers", "Only when you ask for help") to Perms.SMS,
        Triple(Icons.Rounded.Call, "Call your helpers", "Only when you ask for help") to Perms.CALL,
        Triple(Icons.Rounded.LocationOn, "Say where you are", "Only in a help message, only to your helpers") to Perms.LOCATION,
    )
    val missing = runtime.filter { !Perms.has(ctx, *it.second) }
    val special = buildList {
        if (!Perms.exactAlarmsOk(ctx)) add(Triple(Icons.Rounded.Alarm, "Ring at the exact time", "Opens a setting: turn on MedLog") to { Perms.openExactAlarms(ctx) })
        if (!Perms.fullScreenOk(ctx)) add(Triple(Icons.Rounded.Notifications, "Show reminders on the lock screen", "Opens a setting: turn on MedLog") to { Perms.openFullScreen(ctx) })
        if (!Perms.batteryOk(ctx)) add(Triple(Icons.Rounded.BatteryChargingFull, "Keep working when the phone sleeps", "Tap Allow on the next message") to { Perms.openBattery(ctx) })
    }
    Step(PERMISSIONS, "Let MedLog look after you", "MedLog needs your OK for a few things. Tap Allow, then Allow on each message the phone shows.",
        onBack = onBack, primary = if (missing.isNotEmpty()) "Allow" else "Next",
        onPrimary = { if (missing.isNotEmpty()) ask(missing.flatMap { it.second.toList() }.toTypedArray()) else next() },
        secondary = if (missing.isNotEmpty()) "Not now" else null, onSecondary = next) {
        Group {
            runtime.forEachIndexed { i, (t, perms) ->
                if (i > 0) Divider(72.dp)
                PermRow(t.first, t.second, t.third, Perms.has(ctx, *perms))
            }
        }
        if (special.isNotEmpty() && missing.isEmpty()) {
            Label(if (special.size == 1) "One more, so reminders always ring" else "${listOf("", "One", "Two", "Three")[special.size]} more, so reminders always ring")
            Group {
                special.forEachIndexed { i, (t, open) ->
                    if (i > 0) Divider(72.dp)
                    PermRow(t.first, t.second, t.third, false, action = "Turn on", onAction = open)
                }
            }
        }
        Note(Icons.Rounded.Lock, "Nothing is sent anywhere unless you ask for help or share your doctor page.")
    }
    @Suppress("unused") val keep = p
}

@Composable
private fun PermRow(icon: ImageVector, title: String, why: String, ok: Boolean, action: String? = null, onAction: () -> Unit = {}) {
    val p = LocalPalette.current
    val sc = LocalScale.current
    Row(Modifier.fillMaxWidth().heightIn(min = 68.dp).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(if (ok) p.okSoft else p.brandSoft), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = if (ok) p.ok else p.brand, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = sc.body, fontWeight = FontWeight.SemiBold, color = p.ink)
            Text(why, fontSize = sc.small, color = p.inkSoft)
        }
        Spacer(Modifier.width(10.dp))
        when {
            ok -> Icon(Icons.Rounded.CheckCircle, "Allowed", tint = p.ok, modifier = Modifier.size(28.dp))
            action != null -> Text(action, fontSize = sc.body, fontWeight = FontWeight.Bold, color = p.brand,
                modifier = Modifier.clip(RoundedCornerShape(12.dp)).steady(title, onClick = onAction).padding(horizontal = 10.dp, vertical = 12.dp))
        }
    }
}

// ───────────────────────── finish ─────────────────────────

@Composable
private fun DoneStep(nav: Nav, pr: Profile) {
    val ctx = LocalContext.current
    val app = ctx.medlog
    val p = LocalPalette.current
    val sc = LocalScale.current
    val s = LocalSettings.current
    val scope = rememberCoroutineScope()
    val helpers by app.db.helpers().flow().collectAsState(emptyList())
    val name = pr.name.substringBefore(" ")
    val langNames = s.languages.mapNotNull { t -> com.suryaprakash.medlog.clinical.Lang.ALL.firstOrNull { it.tag == t }?.name }
    Step(null, if (name.isBlank()) "You're all set" else "You're all set, $name", "You're all set. MedLog is not a doctor. In an emergency, call ${s.emergencyNumber}.",
        onBack = { Onboard.step = WIDGET }, primary = "Start using MedLog", onPrimary = {
            scope.launch {
                app.settings.update { it.copy(onboarded = true, role = "self") }
                Scheduler.reschedule(ctx)
                Onboard.step = WELCOME; Onboard.justAdded = null
                nav.home(Route.Home)
            }
        }) {
        Group {
            DoneRow("Languages", langNames.joinToString(", ").ifBlank { "English" }, true)
            Divider()
            DoneRow("Helpers", if (helpers.isEmpty()) "None yet. Add one in Settings." else helpers.joinToString(", ") { it.name.substringBefore(" ") }, helpers.isNotEmpty())
            Divider()
            DoneRow("Doctor", pr.doctorName.ifBlank { "Not added" }, pr.doctorName.isNotBlank())
            Divider()
            DoneRow("Reminders", if (Perms.has(ctx, *Perms.NOTIFY)) "On" else "Off. Turn on in Settings.", Perms.has(ctx, *Perms.NOTIFY))
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(p.amberSoft).padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("MedLog is not a doctor", fontSize = sc.body, fontWeight = FontWeight.Bold, color = p.ink)
            Text("It helps you remember and share how you feel. In an emergency, call ${s.emergencyNumber}, or tap the red SOS button at the bottom of every page.", fontSize = sc.body, color = p.ink)
        }
        if (!app.catalogue.reviewed) Note(null, "The warning signs in this version have not yet been checked by a doctor. Always follow your doctor's advice.")
    }
}

@Composable
private fun DoneRow(label: String, value: String, ok: Boolean) {
    val p = LocalPalette.current
    val sc = LocalScale.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(if (ok) Icons.Rounded.CheckCircle else Icons.Rounded.Check, null, tint = if (ok) p.ok else p.inkSoft.copy(alpha = 0.4f), modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = sc.small, color = p.inkSoft)
            Text(value, fontSize = sc.body, color = p.ink)
        }
    }
}

// ───────────────────────── the page frame ─────────────────────────

/**
 * One onboarding page: Back and Read aloud on top, progress, the question, the content,
 * and the main button fixed at the bottom where the thumb is.
 */
@Composable
private fun Step(
    step: Int?, title: String, speak: String,
    onBack: (() -> Unit)? = null,
    primary: String? = null, primaryEnabled: Boolean = true, onPrimary: () -> Unit = {},
    secondary: String? = null, onSecondary: () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val ctx = LocalContext.current
    val app = ctx.medlog
    val p = LocalPalette.current
    val sc = LocalScale.current
    val s = LocalSettings.current
    val speaking by app.speaker.speaking.collectAsState()
    val full = "$title. $speak"
    LaunchedEffect(full) { ReadAloud.text = full }
    LaunchedEffect(title) { if (s.autoRead) { delay(350); app.speaker.say(full) } }
    Column(Modifier.fillMaxSize().background(p.paper).statusBarsPadding().navigationBarsPadding().imePadding()) {
        // top: back, progress, read aloud
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = sc.margin, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) Box(Modifier.size(52.dp).clip(CircleShape).steady("Back", onClick = onBack), contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, tint = p.brand, modifier = Modifier.size(28.dp))
            } else Spacer(Modifier.size(52.dp))
            Box(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                if (step != null) {
                    val i = COUNTED.indexOf(step) + 1
                    Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(p.fill)) {
                        Box(Modifier.fillMaxWidth(i / COUNTED.size.toFloat()).height(6.dp).clip(RoundedCornerShape(3.dp)).background(p.brand))
                    }
                }
            }
            Box(Modifier.size(52.dp).clip(CircleShape).background(if (speaking) p.brand else p.card)
                .steady(if (speaking) "Stop reading" else "Read aloud") { if (speaking) app.speaker.stop() else app.speaker.say(full) },
                contentAlignment = Alignment.Center) {
                Icon(if (speaking) Icons.Rounded.Stop else Icons.AutoMirrored.Rounded.VolumeUp, null, tint = if (speaking) Color.White else p.brand, modifier = Modifier.size(26.dp))
            }
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = sc.margin),
            verticalArrangement = Arrangement.spacedBy(sc.gap),
        ) {
            Spacer(Modifier.height(8.dp))
            if (step != null) Text("Step ${COUNTED.indexOf(step) + 1} of ${COUNTED.size}", fontSize = sc.small, color = p.inkSoft, fontWeight = FontWeight.SemiBold)
            Text(title, fontSize = sc.title, fontWeight = FontWeight.Bold, color = p.ink, lineHeight = sc.title * 1.15f, modifier = Modifier.semantics { heading() })
            content()
            Spacer(Modifier.height(16.dp))
        }
        if (primary != null || secondary != null) Column(Modifier.fillMaxWidth().background(p.paper)) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(p.line))
            Column(Modifier.padding(horizontal = sc.margin, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (primary != null) BigButton(primary, enabled = primaryEnabled, onClick = onPrimary)
                if (secondary != null) Text(secondary, fontSize = sc.body, fontWeight = FontWeight.SemiBold, color = if (secondary.startsWith("Remove")) p.red else p.brand, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp)).steady(secondary, onClick = onSecondary).padding(vertical = 12.dp))
            }
        }
    }
}

// ───────────────────────── small parts ─────────────────────────

@Composable
private fun Group(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(LocalPalette.current.card), content = content)
}

@Composable
private fun Divider(inset: androidx.compose.ui.unit.Dp = 16.dp) {
    Box(Modifier.padding(start = inset).fillMaxWidth().height(1.dp).background(LocalPalette.current.line))
}

@Composable
private fun Label(text: String) {
    Text(text, fontSize = LocalScale.current.body, fontWeight = FontWeight.Bold, color = LocalPalette.current.ink, modifier = Modifier.padding(top = 4.dp))
}

/** A quiet line of explanation, optionally with an icon. */
@Composable
private fun Note(icon: ImageVector?, text: String) {
    val p = LocalPalette.current
    Row(verticalAlignment = Alignment.Top) {
        icon?.let { Icon(it, null, tint = p.inkSoft, modifier = Modifier.padding(top = 2.dp).size(20.dp)); Spacer(Modifier.width(10.dp)) }
        Text(text, fontSize = LocalScale.current.small, color = p.inkSoft)
    }
}

@Composable
private fun Point(icon: ImageVector, title: String, sub: String) {
    val p = LocalPalette.current
    val sc = LocalScale.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(p.brandSoft), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = p.brand, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = sc.body, fontWeight = FontWeight.Bold, color = p.ink)
            Text(sub, fontSize = sc.small, color = p.inkSoft)
        }
    }
}

/** A selectable tile in a grid of equal tiles. */
@Composable
private fun Tile(on: Boolean, modifier: Modifier, label: String, onClick: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val p = LocalPalette.current
    Column(
        modifier.heightIn(min = LocalScale.current.target).clip(RoundedCornerShape(16.dp)).background(if (on) p.brand else p.card)
            .then(if (on) Modifier else Modifier.border(1.dp, p.line, RoundedCornerShape(16.dp)))
            .steady(label, onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.Center, content = content,
    )
}

/** A full-width answer: a word, an optional line under it, a check when chosen. Tapping answers and moves on. */
@Composable
private fun BigChoice(title: String, sub: String?, selected: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    val sc = LocalScale.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = sc.target + 12.dp).clip(RoundedCornerShape(18.dp)).background(if (selected) p.brandSoft else p.card)
            .border(if (selected) 2.dp else 1.dp, if (selected) p.brand else p.line, RoundedCornerShape(18.dp))
            .steady(title + (sub?.let { ". $it" } ?: ""), onClick = onClick).padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = sc.headline, fontWeight = FontWeight.Bold, color = p.ink)
            if (sub != null) Text(sub, fontSize = sc.small, color = p.inkSoft)
        }
        Icon(if (selected) Icons.Rounded.CheckCircle else Icons.AutoMirrored.Rounded.KeyboardArrowRight, null,
            tint = if (selected) p.brand else p.inkSoft.copy(alpha = 0.5f), modifier = Modifier.size(28.dp))
    }
}

/** Easy or standard, shown as what it will look like. */
@Composable
private fun SizeChoice(big: Boolean, selected: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    val sc = LocalScale.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(if (selected) p.brandSoft else p.card)
            .border(if (selected) 2.dp else 1.dp, if (selected) p.brand else p.line, RoundedCornerShape(18.dp))
            .steady(if (big) "Easy: big text, reads aloud" else "Standard size", onClick = onClick).padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(72.dp).clip(RoundedCornerShape(16.dp)).background(p.paper), contentAlignment = Alignment.Center) {
            Text("Aa", fontSize = if (big) sc.huge else sc.headline, fontWeight = FontWeight.Bold, color = p.ink)
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(if (big) "Easy" else "Standard", fontSize = sc.headline, fontWeight = FontWeight.Bold, color = p.ink)
            Text(if (big) "Big text and buttons. Reads each page aloud." else "Normal text size. Read aloud when you tap it.", fontSize = sc.small, color = p.inkSoft)
        }
    }
}

/** What the widget looks like, so "add to home screen" means something. */
@Composable
private fun WidgetPreview() {
    val p = LocalPalette.current
    val sc = LocalScale.current
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color(0xFF3B4A5A)).padding(20.dp)) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color(0xFFF4F2EE)).padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("headache" to "Headache", "knee_pain" to "Knee pain", "cough" to "Cough").forEach { (id, l) ->
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(Color.White).padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        SpriteIcon(id, 44.dp)
                        Text(l, fontSize = sc.small, fontWeight = FontWeight.Bold, color = p.ink, maxLines = 1)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(14.dp)).background(p.brand), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Icon(Icons.Rounded.Mic, null, tint = Color.White, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(6.dp))
                    Text("How are you feeling?", fontSize = sc.small, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
                }
                Box(Modifier.width(64.dp).height(44.dp).clip(RoundedCornerShape(14.dp)).background(p.red), contentAlignment = Alignment.Center) {
                    Text("SOS", fontSize = sc.small, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
    @Suppress("unused") val keep = Icons.Rounded.Widgets
}

/** A calm yes/no question in a window. */
@Composable
private fun Ask(title: String, body: String, yes: String, no: String, onYes: () -> Unit, onNo: () -> Unit) {
    val p = LocalPalette.current
    val sc = LocalScale.current
    Dialog(onDismissRequest = onNo) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(p.card).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, fontSize = sc.headline, fontWeight = FontWeight.Bold, color = p.ink)
            Text(body, fontSize = sc.body, color = p.ink)
            BigButton(yes, onClick = onYes)
            BigButton(no, tone = Tone.SECONDARY, onClick = onNo)
        }
    }
}
