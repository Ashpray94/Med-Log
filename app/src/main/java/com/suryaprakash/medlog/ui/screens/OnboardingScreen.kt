package com.suryaprakash.medlog.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.Sos
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.Icon
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.suryaprakash.medlog.clinical.Suggest
import com.suryaprakash.medlog.data.CarePlan
import com.suryaprakash.medlog.data.Helper
import com.suryaprakash.medlog.data.Profile
import com.suryaprakash.medlog.medlog
import com.suryaprakash.medlog.meds.Scheduler
import com.suryaprakash.medlog.ui.BigButton
import com.suryaprakash.medlog.ui.BigField
import com.suryaprakash.medlog.ui.Body
import com.suryaprakash.medlog.ui.Chip
import com.suryaprakash.medlog.ui.Choice
import com.suryaprakash.medlog.ui.FlowRowOf
import com.suryaprakash.medlog.ui.FlowScreen
import com.suryaprakash.medlog.ui.Group
import com.suryaprakash.medlog.ui.GroupLine
import com.suryaprakash.medlog.ui.Hint
import com.suryaprakash.medlog.ui.LocalPalette
import com.suryaprakash.medlog.ui.LocalSettings
import com.suryaprakash.medlog.ui.Nav
import com.suryaprakash.medlog.ui.Perms
import com.suryaprakash.medlog.ui.Point
import com.suryaprakash.medlog.ui.Route
import com.suryaprakash.medlog.ui.SearchBox
import com.suryaprakash.medlog.ui.Section
import com.suryaprakash.medlog.ui.Tone
import com.suryaprakash.medlog.ui.ValueRow
import com.suryaprakash.medlog.ui.YesNo
import com.suryaprakash.medlog.ui.rememberContactPicker
import com.suryaprakash.medlog.ui.rememberPermissionAsker
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * First-time setup. One question per page (docs/DESIGN.md): the task name small at the top, the question in the
 * largest words, answers as full-width choices with a clear mark, one main button pinned at the bottom.
 *
 * It is detailed on purpose, so the app fits the person the moment setup ends: their doctors by speciality,
 * conditions, what they feel these days (offered first when they tell how they feel), medicines, treatments,
 * risks, their own emergencies (helpers are called straight away), and a daily check-in.
 * Every answer is saved as it is given; leaving to add a medicine or pair a phone loses nothing.
 */
private object Onboard {
    var step by mutableIntStateOf(0)
    var editingHelper by mutableStateOf<Long?>(null)
    var editingDoctor by mutableStateOf<Int?>(null)      // index in the plan, -1 = new
}

private enum class S { WELCOME, WHO, SIZE, READ, LANGS, NAME, BORN, SEX, CONDITIONS, SYMPTOMS, MEDS, TREATMENTS, ALLERGY, RISKS,
    DOCTORS, DOCTOR_FORM, HELPERS, HELPER_FORM, EMERGENCIES, CHECKIN, PERMISSIONS, WIDGET, DONE }

/** Pages that count in the progress bar. */
private val COUNTED = listOf(S.SIZE, S.READ, S.LANGS, S.NAME, S.BORN, S.SEX, S.CONDITIONS, S.SYMPTOMS, S.MEDS, S.TREATMENTS, S.ALLERGY, S.RISKS,
    S.DOCTORS, S.HELPERS, S.EMERGENCIES, S.CHECKIN, S.PERMISSIONS, S.WIDGET)

@Composable
fun OnboardingScreen(nav: Nav) {
    val ctx = LocalContext.current
    val app = ctx.medlog
    val s = LocalSettings.current
    val scope = rememberCoroutineScope()
    var profile by remember { mutableStateOf<Profile?>(null) }
    LaunchedEffect(Unit) { if (profile == null) profile = app.repo.profile() }
    val pr = profile ?: Profile()
    val plan = CarePlan.parse(pr.plan)
    fun save(p: Profile) { profile = p; scope.launch { app.db.profile().put(p) } }
    fun savePlan(f: (CarePlan) -> CarePlan) = save(pr.copy(plan = f(plan).toJson()))
    val step = S.entries[Onboard.step.coerceIn(0, S.entries.size - 1)]
    fun go(to: S) { Onboard.step = to.ordinal }
    fun next() = go(S.entries[(step.ordinal + 1).coerceAtMost(S.entries.size - 1)].let { if (it == S.DOCTOR_FORM) S.HELPERS else if (it == S.HELPER_FORM) S.EMERGENCIES else it })
    fun back() = go(when (step) {
        S.DOCTOR_FORM -> S.DOCTORS; S.HELPERS -> S.DOCTORS; S.HELPER_FORM -> S.HELPERS; S.EMERGENCIES -> S.HELPERS
        else -> S.entries[(step.ordinal - 1).coerceAtLeast(0)]
    })
    androidx.activity.compose.BackHandler(enabled = step != S.WELCOME) { back() }
    val n = COUNTED.indexOf(step).takeIf { it >= 0 }?.plus(1)
    val total = COUNTED.size
    val task = "Setting up"
    // someone may be setting this up for a parent: the words work for either of them
    val first = pr.name.substringBefore(" ")

    when (step) {
        // ───────────── welcome ─────────────
        S.WELCOME -> {
            var setupError by remember { mutableStateOf<String?>(null) }
            var confirmSkip by remember { mutableStateOf(false) }
            val loadSetup = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri: Uri? ->
                if (uri != null) scope.launch {
                    runCatching { com.suryaprakash.medlog.data.SetupFile.import(ctx, uri) }
                        .onSuccess { Onboard.step = 0; nav.home(Route.Home) }
                        .onFailure { setupError = "That file isn't a MedLog setup file." }
                }
            }
            FlowScreen("MedLog", "Welcome", hint = "A few questions, so MedLog fits you from the first day. About five minutes. Everything stays on this phone.",
                primary = "Start", onPrimary = { go(S.WHO) }, secondary = "I have a setup file",
                onSecondary = { setupError = null; loadSetup.launch(arrayOf("application/json", "application/octet-stream", "*/*")) }) {
                Point(Icons.Rounded.TouchApp, "Tell how you feel", "Tap what you feel. MedLog asks a few short questions.")
                Point(Icons.Rounded.Medication, "Never miss a medicine", "Reminders that ring, even on silent.")
                Point(Icons.Rounded.Sos, "Help in one tap", "Your family is called and told where you are.")
                Point(Icons.Rounded.Description, "One page for your doctor", "What happened, when, and how bad.")
                setupError?.let { Body(it, bold = true) }
                Spacer(Modifier.height(8.dp))
                if (!confirmSkip) Hint("Just looking? You can skip setup and fill it in later.", modifier = Modifier)
                if (!confirmSkip) BigButton("Skip setup", tone = Tone.SECONDARY, onClick = { confirmSkip = true })
                else com.suryaprakash.medlog.ui.Card {
                    Body("Skip the questions? You can fill them in later in Settings.", bold = true)
                    YesNo(yes = "Skip", no = "Answer them", onYes = { app.settings.update { it.copy(role = "self", onboarded = true) }; Onboard.step = 0; nav.home(Route.Home) }, onNo = { confirmSkip = false })
                }
            }
        }

        // ───────────── who ─────────────
        S.WHO -> {
            var pick by remember { mutableStateOf(if (s.role == "helper") 1 else 0) }
            FlowScreen(task, "Who will use MedLog on this phone?", onBack = { back() }, primary = "Next", onPrimary = {
                if (pick == 1) { app.settings.update { it.copy(role = "helper", onboarded = true) }; Onboard.step = 0; nav.home(Route.HelperHome); nav.go(Route.Pair) }
                else { app.settings.update { it.copy(role = "self") }; go(S.SIZE) }
            }) {
                Choice("Me", pick == 0, sub = "For my own health, or I'm setting it up for the person who will use it") { pick = 0 }
                Choice("I'm a helper", pick == 1, sub = "I look after someone who has MedLog on their phone") { pick = 1 }
            }
        }

        // ───────────── text size ─────────────
        S.SIZE -> FlowScreen(task, "How big should the words be?", hint = "Tap one to see it. You can change it later.", step = n, steps = total, onBack = { back() },
            primary = "Next", onPrimary = { next() }) {
            Choice("Regular", !s.bigMode, sub = "Like most apps") { app.settings.update { it.copy(bigMode = false) } }
            Choice("Large", s.bigMode, sub = "Bigger words and buttons") { app.settings.update { it.copy(bigMode = true) } }
        }

        // ───────────── read aloud ─────────────
        S.READ -> {
            val now = when { !s.readAloud -> 2; s.autoRead -> 0; else -> 1 }
            FlowScreen(task, "Should MedLog read pages out loud?", step = n, steps = total, onBack = { back() }, primary = "Next", onPrimary = { next() }) {
                Choice("Yes, read every page", now == 0, sub = "Good if reading is hard") { app.settings.update { it.copy(autoRead = true, readAloud = true) }; app.speaker.say("I will read each page to you.") }
                Choice("Only when I tap Read aloud", now == 1, sub = "A Read aloud button is always there") { app.settings.update { it.copy(autoRead = false, readAloud = true) } }
                Choice("No, never", now == 2, sub = "No button, no voice") { app.speaker.stop(); app.settings.update { it.copy(autoRead = false, readAloud = false) } }
            }
        }

        // ───────────── languages ─────────────
        S.LANGS -> FlowScreen(task, "Which languages do you read?", hint = "Tap all you read. The first one is used for the app.", step = n, steps = total,
            onBack = { back() }, primary = "Next", onPrimary = { next() }) {
            com.suryaprakash.medlog.clinical.Lang.ALL.forEach { l ->
                val on = l.tag in s.languages
                val main = s.languages.firstOrNull() == l.tag
                Choice(l.name, on, multi = true, sub = l.native + if (main && s.languages.size > 1) " · used for the app" else "") {
                    app.settings.update { st -> val list = if (on) st.languages - l.tag else st.languages + l.tag; st.copy(languages = list.ifEmpty { listOf("en-IN") }) }
                }
            }
        }

        // ───────────── name ─────────────
        S.NAME -> FlowScreen(task, "What is your name?", hint = "Setting this up for someone else? Write their name.", step = n, steps = total, onBack = { back() },
            primary = "Next", primaryEnabled = pr.name.isNotBlank(), onPrimary = { next() }, secondary = "Skip", onSecondary = { next() }) {
            BigField("Name", pr.name, { save(pr.copy(name = it)) })
            Hint("It goes on the doctor page and in messages to family.")
        }

        // ───────────── born ─────────────
        S.BORN -> {
            var year by remember { mutableStateOf(pr.dob.take(4)) }
            val ok = year.toIntOrNull()?.let { it in 1900..java.time.LocalDate.now().year } == true
            FlowScreen(task, "Which year were you born?", hint = "MedLog suggests the problems common at your age.", step = n, steps = total, onBack = { back() },
                primary = "Next", primaryEnabled = ok, onPrimary = { if (pr.dob.take(4) != year) save(pr.copy(dob = "$year-07-01")); next() },
                secondary = "Skip", onSecondary = { next() }) {
                BigField("Year", year, { year = it.filter(Char::isDigit).take(4) }, keyboard = KeyboardType.Number, hint = "For example 1952")
                year.toIntOrNull()?.takeIf { ok }?.let { Body("That's about ${java.time.LocalDate.now().year - it} years old.") }
            }
        }

        // ───────────── sex ─────────────
        S.SEX -> FlowScreen(task, "Are you a woman or a man?", hint = "Doctors ask this. It also changes a few questions.", step = n, steps = total,
            onBack = { back() }, primary = "Next", onPrimary = { next() }) {
            listOf("F" to "Woman", "M" to "Man", "" to "I'd rather not say").forEach { (k, l) -> Choice(l, pr.sex == k) { save(pr.copy(sex = k)) } }
        }

        // ───────────── conditions ─────────────
        S.CONDITIONS -> {
            val chosen = pr.conditions.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            var other by remember { mutableStateOf(chosen.filter { it !in CarePlan.CONDITIONS }.joinToString(", ")) }
            fun set(list: List<String>, extra: String) = save(pr.copy(conditions = (list.filter { it in CarePlan.CONDITIONS } + extra.split(",").map { it.trim() }.filter { it.isNotEmpty() }).distinct().joinToString(", ")))
            FlowScreen(task, "Do you have any long-term illness?", hint = "Tap all that apply.", step = n, steps = total, onBack = { back() },
                primary = if (chosen.isEmpty()) "None of these" else "Next", onPrimary = { next() }) {
                CarePlan.CONDITIONS.forEach { c -> Choice(c, c in chosen, multi = true) { set(if (c in chosen) chosen - c else chosen + c, other) } }
                BigField("Something else", other, { other = it; set(chosen, it) }, hint = "For example: glaucoma")
            }
        }

        // ───────────── symptoms these days ─────────────
        S.SYMPTOMS -> {
            val cat = app.catalogue
            var query by remember { mutableStateOf("") }
            val offered = remember(pr.conditions, pr.dob) {
                Suggest.rank(emptyList(), app.repo.ageYears(pr.dob), pr.conditions.split(",").map { it.trim() }, emptyList(), 12, known = { cat.problem(it) != null }, limit = 12).suggested
            }
            val chosen = plan.symptoms
            fun toggle(id: String) = savePlan { it.copy(symptoms = if (id in it.symptoms) it.symptoms - id else it.symptoms + id) }
            com.suryaprakash.medlog.pictogram.Sprites.init(ctx)
            FlowScreen(task, "What do you feel these days?", hint = "Tap what you have often. These come first when you tell how you feel.", step = n, steps = total,
                onBack = { back() }, primary = if (chosen.isEmpty()) "Nothing these days" else "Next", onPrimary = { next() }) {
                SearchBox(query, { query = it }, "Search, for example: knee pain")
                val list = (if (query.isBlank()) (chosen + offered).distinct() else (searchProblems(app, query) + chosen).distinct())
                com.suryaprakash.medlog.ui.TileGrid(list, if (LocalSettingsBig()) 2 else 3, aspect = 0.9f) { id, m ->
                    val label = cat.problem(id)?.label ?: id
                    com.suryaprakash.medlog.ui.Tile(label, m, selected = id in chosen, onClick = { toggle(id) }) {
                        com.suryaprakash.medlog.pictogram.SpriteIcon(id, 56.dp)
                        Spacer(Modifier.size(6.dp))
                        com.suryaprakash.medlog.ui.Text(label, fontSize = com.suryaprakash.medlog.ui.LocalScale.current.small, maxLines = 2, minLines = 2,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                    }
                }
            }
        }

        // ───────────── medicines ─────────────
        S.MEDS -> {
            val meds by app.db.medicines().activeFlow().collectAsState(emptyList())
            FlowScreen(task, "Which medicines do you take?", hint = "Add each one once. MedLog reminds you on time.", step = n, steps = total, onBack = { back() },
                primary = if (meds.isEmpty()) "I don't take any" else "Next", onPrimary = { next() }) {
                if (meds.isNotEmpty()) Group {
                    meds.forEachIndexed { i, m ->
                        if (i > 0) GroupLine()
                        ValueRow(m.name, m.times.split(",").filter { it.isNotBlank() }.joinToString(", ").ifBlank { "When needed" }, sub = m.strength.ifBlank { null }) { nav.go(Route.MedEdit(m.id)) }
                    }
                }
                BigButton(if (meds.isEmpty()) "Add a medicine" else "Add another", tone = Tone.SECONDARY, icon = Icons.Rounded.Medication, onClick = { nav.go(Route.MedEdit(null)) })
            }
        }

        // ───────────── treatments ─────────────
        S.TREATMENTS -> FlowScreen(task, "Any other treatment?", hint = "Tap all that apply.", step = n, steps = total, onBack = { back() },
            primary = if (plan.treatments.isEmpty()) "None of these" else "Next", onPrimary = { next() }) {
            CarePlan.TREATMENTS.forEach { t ->
                Choice(t, t in plan.treatments, multi = true, sub = if (t == "Blood thinner") "Ecosprin, Warfarin, Clopidogrel… matters after a fall or cut" else null) {
                    val on = t in plan.treatments
                    val p2 = if (t == "Blood thinner") pr.copy(onBloodThinner = !on) else pr
                    save(p2.copy(plan = plan.copy(treatments = if (on) plan.treatments - t else plan.treatments + t).toJson()))
                }
            }
        }

        // ───────────── allergy ─────────────
        S.ALLERGY -> {
            var has by remember { mutableStateOf(if (pr.allergies.isNotBlank()) true else null) }
            FlowScreen(task, "Are you allergic to any medicine or food?", step = n, steps = total, onBack = { back() },
                primary = "Next", primaryEnabled = has == false || (has == true && pr.allergies.isNotBlank()), onPrimary = { next() }) {
                Choice("No, not that I know of", has == false) { has = false; save(pr.copy(allergies = "")) }
                Choice("Yes", has == true) { has = true }
                if (has == true) BigField("What are you allergic to?", pr.allergies, { save(pr.copy(allergies = it)) }, hint = "For example: penicillin (rash)")
            }
        }

        // ───────────── risks ─────────────
        S.RISKS -> FlowScreen(task, "Do any of these apply to you?", hint = "It helps MedLog know when to call your family.", step = n, steps = total, onBack = { back() },
            primary = if (plan.risks.isEmpty()) "None of these" else "Next", onPrimary = { next() }) {
            CarePlan.RISKS.forEach { (k, l) -> Choice(l, k in plan.risks, multi = true) { savePlan { it.copy(risks = if (k in it.risks) it.risks - k else it.risks + k) } } }
        }

        // ───────────── doctors ─────────────
        S.DOCTORS -> FlowScreen(task, "Who are your doctors?", hint = "Add each doctor with what they treat. MedLog then offers the right one to call.", step = n, steps = total,
            onBack = { back() }, primary = if (plan.doctors.isEmpty()) "Add a doctor" else "Next",
            onPrimary = { if (plan.doctors.isEmpty()) { Onboard.editingDoctor = -1; go(S.DOCTOR_FORM) } else go(S.HELPERS) },
            secondary = if (plan.doctors.isEmpty()) "Skip for now" else null, onSecondary = { go(S.HELPERS) }) {
            if (plan.doctors.isNotEmpty()) {
                Group {
                    plan.doctors.forEachIndexed { i, d ->
                        if (i > 0) GroupLine()
                        ValueRow(d.name, d.speciality, sub = d.phone.ifBlank { null }) { Onboard.editingDoctor = i; go(S.DOCTOR_FORM) }
                    }
                }
                BigButton("Add another doctor", tone = Tone.SECONDARY, icon = Icons.Rounded.PersonAdd, onClick = { Onboard.editingDoctor = -1; go(S.DOCTOR_FORM) })
            }
        }
        S.DOCTOR_FORM -> DoctorForm(plan, onSave = { list -> savePlan { it.copy(doctors = list) }; go(S.DOCTORS) }, onBack = { go(S.DOCTORS) })

        // ───────────── helpers ─────────────
        S.HELPERS -> HelpersStep(nav, n, total, first, onBack = { go(S.DOCTORS) }, onEdit = { Onboard.editingHelper = it; go(S.HELPER_FORM) }) {
            // the first time here, suggest emergencies from what they said
            if (plan.emergencies.isEmpty()) savePlan { it.copy(emergencies = CarePlan.emergenciesFor(it.risks, pr.conditions.split(",").map { c -> c.trim() })) }
            go(S.EMERGENCIES)
        }
        S.HELPER_FORM -> HelperForm { go(S.HELPERS) }

        // ───────────── emergencies ─────────────
        S.EMERGENCIES -> FlowScreen(task, "Which of these are emergencies for you?",
            hint = "If you tell MedLog one of these, your helpers are called straight away. If nobody picks up, their phones ring loudly until someone answers.",
            step = n, steps = total, onBack = { back() }, primary = "Next", onPrimary = { next() }) {
            CarePlan.EMERGENCIES.forEach { id ->
                val label = app.catalogue.problem(id)?.label ?: return@forEach
                Choice(label, id in plan.emergencies, multi = true) { savePlan { it.copy(emergencies = if (id in it.emergencies) it.emergencies - id else it.emergencies + id) } }
            }
            Hint("In any emergency you can also press the red SOS button at the bottom of every page.")
        }

        // ───────────── check-in ─────────────
        S.CHECKIN -> {
            val opts = listOf("08:00" to "Morning, 8 am", "10:00" to "Mid-morning, 10 am", "13:00" to "Afternoon, 1 pm", "18:00" to "Evening, 6 pm")
            FlowScreen(task, "When should MedLog ask how you are?", hint = "Once a day. If you don't answer within 2 hours, your helpers get a message.", step = n, steps = total,
                onBack = { back() }, primary = "Next", onPrimary = { scope.launch { Scheduler.reschedule(ctx) }; next() }) {
                opts.forEach { (t, l) -> Choice(l, s.checkInEnabled && s.checkInTime == t) { app.settings.update { it.copy(checkInEnabled = true, checkInTime = t) } } }
                Choice("Don't ask me every day", !s.checkInEnabled) { app.settings.update { it.copy(checkInEnabled = false) } }
            }
        }

        // ───────────── permissions ─────────────
        S.PERMISSIONS -> PermissionsStep(n, total, onBack = { back() }) { next() }

        // ───────────── widget ─────────────
        S.WIDGET -> FlowScreen(task, "Put MedLog on your home screen", hint = "Note how you feel, or send your family a message, without opening the app.", step = n, steps = total,
            onBack = { back() }, primary = "Add to home screen", onPrimary = { pinWidget(ctx); scope.launch { delay(600); next() } },
            secondary = "Not now", onSecondary = { next() }) {
            Point(Icons.Rounded.TouchApp, "One tap notes it", "Tap headache: it's saved, with the time.")
            Point(Icons.Rounded.Sms, "One tap tells family", "Your own messages, sent at once.")
            Point(Icons.Rounded.Sos, "SOS always there", "The red button calls for help.")
        }

        // ───────────── finish ─────────────
        S.DONE -> DoneStep(nav, pr, plan)
    }
}

@Composable
private fun LocalSettingsBig() = LocalSettings.current.bigMode

// ───────────────────────── doctors ─────────────────────────

@Composable
private fun DoctorForm(plan: CarePlan, onSave: (List<CarePlan.Doctor>) -> Unit, onBack: () -> Unit) {
    val i = Onboard.editingDoctor ?: -1
    val old = plan.doctors.getOrNull(i)
    var name by remember(i) { mutableStateOf(old?.name ?: "") }
    var phone by remember(i) { mutableStateOf(old?.phone ?: "") }
    var spec by remember(i) { mutableStateOf(old?.speciality ?: "Family doctor") }
    val pick = rememberContactPicker { n, ph -> if (name.isBlank()) name = n; phone = ph }
    FlowScreen("Doctor", if (old == null) "Add a doctor" else "Change ${old.name}", onBack = onBack,
        primary = "Save", primaryEnabled = name.isNotBlank(), onPrimary = {
            val d = CarePlan.Doctor(name.trim(), spec, phone.trim())
            onSave(if (old == null) plan.doctors + d else plan.doctors.mapIndexed { k, x -> if (k == i) d else x })
        }, secondary = if (old != null) "Remove this doctor" else null, onSecondary = { onSave(plan.doctors.filterIndexed { k, _ -> k != i }) }) {
        BigField("Doctor's name", name, { name = it }, hint = "For example: Dr. Rao")
        Section("What do they treat?")
        FlowRowOf { CarePlan.SPECIALITIES.forEach { sp -> Chip(sp, spec == sp) { spec = sp } } }
        Section("Phone number")
        BigButton("Choose from contacts", tone = Tone.SECONDARY, icon = Icons.Rounded.Contacts, onClick = pick)
        BigField("Or type it", phone, { phone = it }, keyboard = KeyboardType.Phone)
    }
}

// ───────────────────────── helpers ─────────────────────────

@Composable
private fun HelpersStep(nav: Nav, n: Int?, total: Int, first: String, onBack: () -> Unit, onEdit: (Long?) -> Unit, next: () -> Unit) {
    val ctx = LocalContext.current
    val app = ctx.medlog
    val s = LocalSettings.current
    val helpers by app.db.helpers().flow().collectAsState(emptyList())
    var askSkip by remember { mutableStateOf(false) }
    FlowScreen("Setting up", "Who should be called if you need help?", hint = "Family, friends or neighbours. They are called in this order.", step = n, steps = total,
        onBack = onBack, primary = if (helpers.isEmpty()) "Add a helper" else "Next", onPrimary = { if (helpers.isEmpty()) onEdit(null) else next() },
        secondary = if (helpers.isEmpty()) "Skip for now" else null, onSecondary = { askSkip = true }) {
        if (helpers.isEmpty()) {
            Point(Icons.Rounded.Sms, "A text with where you are", "The moment you ask for help.")
            Point(Icons.Rounded.Call, "A call, one person at a time", "Until someone answers. Then ${s.emergencyNumber}.")
            Point(Icons.Rounded.Lock, "Your notes stay private", "They see them only if you share.")
        } else {
            Group {
                helpers.forEachIndexed { i, h ->
                    if (i > 0) GroupLine()
                    ValueRow(h.name, listOf("1st", "2nd", "3rd", "4th", "5th")[i.coerceAtMost(4)], sub = listOf(h.relation.ifBlank { null }, h.phone).filterNotNull().joinToString(" · ")) { onEdit(h.id) }
                }
            }
            if (helpers.size < 5) BigButton("Add another helper", tone = Tone.SECONDARY, icon = Icons.Rounded.PersonAdd, onClick = { onEdit(null) })
            val h = helpers.last()
            val name = h.name.substringBefore(" ")
            Section("Let $name know")
            BigButton("Send $name a text", tone = Tone.QUIET, icon = Icons.Rounded.Sms, onClick = {
                val body = "Hi $name, I've added you as my helper in MedLog. If I need help, you'll get a text and a call." + if (first.isNotBlank()) " – $first" else ""
                runCatching { ctx.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${h.phone}")).putExtra("sms_body", body)) }
            })
            BigButton("Connect ${name}'s phone", tone = Tone.QUIET, sub = "Their phone then rings when you need them", onClick = { nav.go(Route.Pair) })
        }
    }
    if (askSkip) ConfirmDialog("Continue without a helper?", "Without a helper, MedLog can only call ${s.emergencyNumber} in an emergency.",
        yes = "Add a helper", no = "Continue anyway", onYes = { askSkip = false; onEdit(null) }, onNo = { askSkip = false; next() })
}

@Composable
private fun HelperForm(back: () -> Unit) {
    val ctx = LocalContext.current
    val app = ctx.medlog
    val scope = rememberCoroutineScope()
    val id = Onboard.editingHelper
    var h by remember(id) { mutableStateOf(Helper(name = "", phone = "")) }
    LaunchedEffect(id) { if (id != null) app.db.helpers().all().firstOrNull { it.id == id }?.let { h = it } }
    val pick = rememberContactPicker { n, ph -> h = h.copy(name = n, phone = ph) }
    val relations = listOf("Son", "Daughter", "Husband or wife", "Friend or neighbour", "Carer or nurse", "Other")
    val ok = h.name.isNotBlank() && h.phone.count(Char::isDigit) >= 6
    var confirmRemove by remember { mutableStateOf(false) }
    FlowScreen("Helper", if (id == null) "Add a helper" else "Change ${h.name}", onBack = back,
        primary = "Save", primaryEnabled = ok, onPrimary = {
            scope.launch {
                if (id == null) app.db.helpers().insert(h.copy(name = h.name.trim(), phone = h.phone.trim(), sortOrder = app.db.helpers().all().size))
                else app.db.helpers().update(h.copy(name = h.name.trim(), phone = h.phone.trim()))
                back()
            }
        }, secondary = if (id != null) "Remove this helper" else null, onSecondary = { confirmRemove = true }) {
        BigButton("Choose from contacts", tone = if (h.name.isBlank()) Tone.PRIMARY else Tone.SECONDARY, icon = Icons.Rounded.Contacts, onClick = pick)
        BigField("Name", h.name, { h = h.copy(name = it) })
        BigField("Phone number", h.phone, { h = h.copy(phone = it) }, keyboard = KeyboardType.Phone)
        Section("Who are they to you?")
        FlowRowOf { relations.forEach { r -> Chip(r, h.relation == r) { h = h.copy(relation = if (h.relation == r) "" else r) } } }
        Section("What should they get?")
        com.suryaprakash.medlog.ui.Toggle("Calls and texts if I need help", h.sos, "With where you are") { h = h.copy(sos = it) }
        com.suryaprakash.medlog.ui.Toggle("A text if I miss a medicine", h.alerts) { h = h.copy(alerts = it) }
    }
    if (confirmRemove) ConfirmDialog("Remove ${h.name}?", "${h.name} won't be called or texted if you need help.", yes = "Keep", no = "Remove",
        onYes = { confirmRemove = false }, onNo = { confirmRemove = false; scope.launch { id?.let { app.db.helpers().delete(it) }; back() } })
}

// ───────────────────────── permissions ─────────────────────────

@Composable
private fun PermissionsStep(n: Int?, total: Int, onBack: () -> Unit, next: () -> Unit) {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) tick++ }
        owner.lifecycle.addObserver(obs); onDispose { owner.lifecycle.removeObserver(obs) }
    }
    val ask = rememberPermissionAsker { tick++ }
    @Suppress("UNUSED_EXPRESSION") tick
    val runtime = listOf(
        Triple("Remind you", "Medicine times and check-ins", Perms.NOTIFY),
        Triple("Text your helpers", "Only when you ask for help", Perms.SMS),
        Triple("Call your helpers", "Only when you ask for help", Perms.CALL),
        Triple("Say where you are", "Only in a help message, only to your helpers", Perms.LOCATION),
    )
    val missing = runtime.filter { !Perms.has(ctx, *it.third) }
    val special = buildList {
        if (!Perms.exactAlarmsOk(ctx)) add(Triple("Ring at the exact time", Icons.Rounded.Alarm) { Perms.openExactAlarms(ctx) })
        if (!Perms.fullScreenOk(ctx)) add(Triple("Show alarms on the lock screen", Icons.Rounded.Notifications) { Perms.openFullScreen(ctx) })
        if (!Perms.batteryOk(ctx)) add(Triple("Keep working when the phone sleeps", Icons.Rounded.BatteryChargingFull) { Perms.openBattery(ctx) })
    }
    FlowScreen("Setting up", "Allow MedLog to look after you", hint = "Tap Allow, then Allow on each message the phone shows.", step = n, steps = total, onBack = onBack,
        primary = if (missing.isNotEmpty()) "Allow" else "Next", onPrimary = { if (missing.isNotEmpty()) ask(missing.flatMap { it.third.toList() }.toTypedArray()) else next() },
        secondary = if (missing.isNotEmpty()) "Not now" else null, onSecondary = next) {
        Group {
            runtime.forEachIndexed { i, (t, why, perms) ->
                if (i > 0) GroupLine()
                ValueRow(t, if (Perms.has(ctx, *perms)) "Allowed" else "Not yet", sub = why, valueColor = if (Perms.has(ctx, *perms)) LocalPalette.current.ok else null)
            }
        }
        if (special.isNotEmpty() && missing.isEmpty()) {
            Section("So alarms always ring")
            Group {
                special.forEachIndexed { i, (t, _, open) -> if (i > 0) GroupLine(); ValueRow(t, "Turn on", onClick = open) }
            }
        }
        Hint("Nothing is sent anywhere unless you ask for help or share your doctor page.")
    }
}

// ───────────────────────── finish ─────────────────────────

@Composable
private fun DoneStep(nav: Nav, pr: Profile, plan: CarePlan) {
    val ctx = LocalContext.current
    val app = ctx.medlog
    val s = LocalSettings.current
    val scope = rememberCoroutineScope()
    val helpers by app.db.helpers().flow().collectAsState(emptyList())
    val meds by app.db.medicines().activeFlow().collectAsState(emptyList())
    val name = pr.name.substringBefore(" ")
    FlowScreen("Setting up", if (name.isBlank()) "You're all set" else "You're all set, $name", hint = "Here's what MedLog knows. You can change any of it in Settings.",
        onBack = { Onboard.step = S.WIDGET.ordinal }, primary = "Start using MedLog", onPrimary = {
            scope.launch {
                app.settings.update { it.copy(onboarded = true, role = "self") }
                Scheduler.reschedule(ctx)
                Onboard.step = 0
                nav.home(Route.Home)
            }
        }) {
        Group {
            ValueRow("Doctors", plan.doctors.size.takeIf { it > 0 }?.toString() ?: "None")
            GroupLine()
            ValueRow("Helpers", helpers.size.takeIf { it > 0 }?.toString() ?: "None")
            GroupLine()
            ValueRow("Medicines", meds.size.takeIf { it > 0 }?.toString() ?: "None")
            GroupLine()
            ValueRow("Illnesses", pr.conditions.ifBlank { "None" })
            GroupLine()
            ValueRow("Emergencies", plan.emergencies.size.takeIf { it > 0 }?.toString() ?: "None")
            GroupLine()
            ValueRow("Daily check-in", if (s.checkInEnabled) s.checkInTime else "Off")
        }
        com.suryaprakash.medlog.ui.Card(border = LocalPalette.current.amber) {
            Body("MedLog is not a doctor", bold = true)
            Body("In an emergency, call ${s.emergencyNumber}, or press the red SOS button at the bottom of every page.")
        }
        if (!app.catalogue.reviewed) Hint("The warning signs in this version have not yet been checked by a doctor. Always follow your doctor's advice.")
    }
}

/** A calm two-choice question in a window. */
@Composable
private fun ConfirmDialog(title: String, body: String, yes: String, no: String, onYes: () -> Unit, onNo: () -> Unit) {
    val p = LocalPalette.current
    androidx.compose.ui.window.Dialog(onDismissRequest = onNo) {
        com.suryaprakash.medlog.ui.Card(color = p.paper) {
            com.suryaprakash.medlog.ui.Title(title)
            Body(body)
            BigButton(yes, onClick = onYes)
            BigButton(no, tone = Tone.SECONDARY, onClick = onNo)
        }
    }
}
