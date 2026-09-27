package com.suryaprakash.medlog.ui.screens

import com.suryaprakash.medlog.ui.cardTitle
import androidx.compose.foundation.border
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Box
import com.suryaprakash.medlog.ui.steady
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.PhonelinkRing
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Male
import androidx.compose.material.icons.rounded.Female
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Person
import androidx.compose.ui.draw.shadow
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.Hearing
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.MotionPhotosOff
import androidx.compose.material.icons.rounded.BackHand
import androidx.compose.material.icons.rounded.PanTool
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.FormatBold
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
    var alsoHelps by mutableStateOf(false)               // keeps their own health and helps someone too
    var orderAsked by mutableStateOf(false)
}

private enum class S { WELCOME, WHO, ORDER, SIZE, READ, LANGS, NAME, BORN, SEX, CONDITIONS, SYMPTOMS, MEDS, TREATMENTS, ALLERGY, RISKS,
    DOCTORS, DOCTOR_FORM, HELPERS, HELPER_FORM, EMERGENCIES, CHECKIN, PERMISSIONS, WIDGET, DONE }

/** Pages that count in the progress bar. */
private val COUNTED = listOf(S.WHO, S.SIZE, S.LANGS, S.NAME, S.BORN, S.SEX, S.CONDITIONS, S.SYMPTOMS, S.MEDS, S.TREATMENTS, S.ALLERGY, S.RISKS,
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
    // read the latest answers at the moment of the tap, never a copy from when the page was drawn
    // (quick taps in a row used to overwrite each other)
    fun update(f: (Profile) -> Profile) = save(f(profile ?: Profile()))
    fun savePlan(f: (CarePlan) -> CarePlan) { val cur = profile ?: Profile(); save(cur.copy(plan = f(CarePlan.parse(cur.plan)).toJson())) }
    val step = S.entries[Onboard.step.coerceIn(0, S.entries.size - 1)]
    // the page is remembered, so reopening the app (or an update) comes back to the same question
    LaunchedEffect(Unit) { if (Onboard.step == 0) app.settings.getString("onboard_step")?.toIntOrNull()?.let { Onboard.step = it } }
    LaunchedEffect(Onboard.step) { app.settings.putString("onboard_step", Onboard.step.toString()) }
    fun go(to: S) { Onboard.step = to.ordinal }
    fun next() = go(S.entries[(step.ordinal + 1).coerceAtMost(S.entries.size - 1)].let { when (it) { S.DOCTOR_FORM -> S.HELPERS; S.HELPER_FORM -> S.EMERGENCIES; S.READ -> S.LANGS; else -> it } })
    fun back() = go(when (step) {
        S.SIZE -> if (Onboard.alsoHelps || s.role == "self" && Onboard.orderAsked) S.ORDER else S.WHO
        S.LANGS -> S.SIZE
        S.DOCTOR_FORM -> S.DOCTORS; S.HELPERS -> S.DOCTORS; S.HELPER_FORM -> S.HELPERS; S.EMERGENCIES -> S.HELPERS
        else -> S.entries[(step.ordinal - 1).coerceAtLeast(0)]
    })
    androidx.activity.compose.BackHandler(enabled = step != S.WELCOME) { back() }
    val n = COUNTED.indexOf(step).takeIf { it >= 0 }?.plus(1)
    val total = COUNTED.size
    val task = "Setting up"
    // someone may be setting this up for a parent: the words work for either of them
    val first = pr.name.substringBefore(" ")

    // Skip: this section, or setup altogether (a panel from the bottom); no Skip on the welcome/choice pages
    var skipping by remember { mutableStateOf(false) }
    val section = SECTIONS.firstOrNull { step in it.second }
    fun finishNow() { app.settings.update { it.copy(role = if (it.role == "helper") "helper" else "self", onboarded = true) }; Onboard.step = 0; nav.home(Route.Home) }
    if (skipping && section != null) SkipSheet(section.first, onSection = {
        skipping = false
        val last = section.second.maxOf { it.ordinal }
        go(S.entries[(last + 1).coerceAtMost(S.DONE.ordinal)].let { if (it == S.READ) S.LANGS else it })
    }, onAll = { skipping = false; finishNow() }, onDismiss = { skipping = false })
    androidx.compose.runtime.CompositionLocalProvider(com.suryaprakash.medlog.ui.LocalFlowSkip provides if (section != null) ({ skipping = true }) else null) {
    when (step) {
        // ───────────── welcome ─────────────
        S.WELCOME -> {
            var setupError by remember { mutableStateOf<String?>(null) }
            var confirmSkip by remember { mutableStateOf(false) }
            val loadSetup = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri: Uri? ->
                if (uri != null) scope.launch {
                    runCatching { com.suryaprakash.medlog.data.SetupFile.import(ctx, uri) }
                        .onSuccess { Onboard.step = 0; nav.home(Route.Home) }
                        .onFailure { setupError = "That file isn't a setup file." }
                }
            }
            FlowScreen("MedLog", "Welcome", hint = "Everything stays on this phone.",
                primary = "Start", onPrimary = { go(S.WHO) }, secondary = "Skip", onSecondary = { confirmSkip = true }) {
                WelcomeCarousel()
                // room kept for a message, so nothing moves when one appears
                Box(Modifier.fillMaxWidth().heightIn(min = 28.dp)) { setupError?.let { Body(it, bold = true) } }
            }
            if (confirmSkip) OptionsSheet("Skip setup?", "You can answer the questions later in Settings.", listOf(
                "Skip setup completely" to { confirmSkip = false; finishNow() },
                "Use a setup file" to { confirmSkip = false; setupError = null; loadSetup.launch(arrayOf("application/json", "application/octet-stream", "*/*")) },
            ), onDismiss = { confirmSkip = false })
        }
        // ───────────── who ─────────────
        S.WHO -> {
            // one person can be both: keep their own health, and help someone else (more people can be added later)
            var own by remember { mutableStateOf(s.role != "helper") }
            var helps by remember { mutableStateOf(Onboard.alsoHelps || s.role == "helper") }
            FlowScreen(task, "How will you use this?", hint = "Tap one or both.", step = n, steps = total, onBack = { back() }, primary = "Next", primaryEnabled = own || helps, onPrimary = {
                Onboard.alsoHelps = helps
                when {
                    !own -> { app.settings.update { it.copy(role = "helper", onboarded = true) }; Onboard.step = 0; PairMode.helping = true; nav.home(Route.HelperHome); nav.go(Route.Pair) }
                    helps -> { app.settings.update { it.copy(role = "self") }; go(S.ORDER) }
                    else -> { app.settings.update { it.copy(role = "self") }; go(S.SIZE) }
                }
            }) {
                val p = com.suryaprakash.medlog.ui.LocalPalette.current
                com.suryaprakash.medlog.ui.ChoicePair(
                    com.suryaprakash.medlog.ui.BigOption("My health", "Track how I feel and my medicines", own, { com.suryaprakash.medlog.ui.OptionIcon(Icons.Rounded.Person, p.tintBlue, 60.dp) }) { own = !own },
                    com.suryaprakash.medlog.ui.BigOption("I help someone", "Get their alerts and messages", helps, { com.suryaprakash.medlog.ui.OptionIcon(Icons.Rounded.Groups, p.tintGreen, 60.dp) }) { helps = !helps },
                    vertical = true,
                )
                Hint("Setting this up for a parent? Choose My health.")
            }
        }

        // ───────────── both: whose setup first ─────────────
        S.ORDER -> {
            val p = com.suryaprakash.medlog.ui.LocalPalette.current
            FlowScreen(task, "Whose setup first?", hint = "You'll do the other one right after.", step = COUNTED.indexOf(S.WHO) + 1, steps = total, onBack = { go(S.WHO) }) {
                com.suryaprakash.medlog.ui.ChoicePair(
                    com.suryaprakash.medlog.ui.BigOption("Mine first", "My health, then connect to them", false, { com.suryaprakash.medlog.ui.OptionIcon(Icons.Rounded.Person, p.tintBlue, 60.dp) }) {
                        Onboard.alsoHelps = true; go(S.SIZE)
                    },
                    com.suryaprakash.medlog.ui.BigOption("Theirs first", "Connect to their phone, then my health", false, { com.suryaprakash.medlog.ui.OptionIcon(Icons.Rounded.Groups, p.tintGreen, 60.dp) }) {
                        Onboard.alsoHelps = false; go(S.SIZE); PairMode.helping = true; nav.go(Route.Pair)
                    },
                    vertical = true,
                )
            }
        }

        S.SIZE -> FlowScreen(task, "Make it easy for you", hint = "Pick a word size, and anything that's harder for you.", step = n, steps = total, onBack = { back() },
            primary = "Next", onPrimary = { next() }) {
            // one group per kind of difficulty, as an accordion: all closed at first, one open at a time
            val p = com.suryaprakash.medlog.ui.LocalPalette.current
            var open by remember { mutableStateOf<String?>(null) }
            fun toggleGroup(g: String) { open = if (open == g) null else g }
            AccessRow("Seeing", Icons.Rounded.Visibility, open == "Seeing", { toggleGroup("Seeing") }, listOf(
                AccessCard("Regular words", "Like most apps", !s.bigMode, { SizeSample(big = false, box = 56.dp) }) { app.settings.update { it.copy(bigMode = false) } },
                AccessCard("Large words", "Bigger words and buttons", s.bigMode, { SizeSample(big = true, box = 56.dp) }) { app.settings.update { it.copy(bigMode = true) } },
                AccessCard("Words look faint", "Bold, darker words", s.boldText, { com.suryaprakash.medlog.ui.OptionIcon(Icons.Rounded.FormatBold, p.tintBlue, 56.dp) }) { app.settings.update { it.copy(boldText = !it.boldText) } },
                AccessCard("Hard to see edges", "Strong outlines, black on white", s.highContrast, { com.suryaprakash.medlog.ui.OptionIcon(Icons.Rounded.Contrast, p.ink, 56.dp) }) { app.settings.update { it.copy(highContrast = !it.highContrast) } },
            ))
            AccessRow("Reading", Icons.Rounded.MenuBook, open == "Reading", { toggleGroup("Reading") }, listOf(
                AccessCard("Reading long text", "Each page is read aloud", s.readAloud && s.autoRead, { com.suryaprakash.medlog.ui.OptionIcon(Icons.Rounded.RecordVoiceOver, p.tintTeal, 56.dp) }) {
                    app.settings.update { it.copy(readAloud = true, autoRead = !(it.readAloud && it.autoRead)) } },
                AccessCard("Not sure what a button does", "Tap once to hear it, again to press", s.touchToHear, { com.suryaprakash.medlog.ui.OptionIcon(Icons.Rounded.TouchApp, p.tintPurple, 56.dp) }) {
                    app.settings.update { it.copy(touchToHear = !it.touchToHear) } },
            ))
            AccessRow("Hearing", Icons.Rounded.Hearing, open == "Hearing", { toggleGroup("Hearing") }, listOf(
                AccessCard("Missing alarms", "Flash and vibrate with every alert", s.flashAlerts, { com.suryaprakash.medlog.ui.OptionIcon(Icons.Rounded.NotificationsActive, p.tintOrange, 56.dp) }) {
                    app.settings.update { it.copy(flashAlerts = !it.flashAlerts) } },
                AccessCard("Voice too fast", "A slower reading voice", s.speechRate < 0.95f, { com.suryaprakash.medlog.ui.OptionIcon(Icons.Rounded.Speed, p.tintPink, 56.dp) }) {
                    app.settings.update { it.copy(speechRate = if (it.speechRate < 0.95f) 1.0f else 0.8f) } },
            ))
            AccessRow("Hands", Icons.Rounded.PanTool, open == "Hands", { toggleGroup("Hands") }, listOf(
                AccessCard("Shaky hands", "Accidental double taps are ignored", s.steadyTouch, { com.suryaprakash.medlog.ui.OptionIcon(Icons.Rounded.PanTool, p.tintGreen, 56.dp) }) {
                    app.settings.update { it.copy(steadyTouch = !it.steadyTouch) } },
                AccessCard("I use my left hand", "Main buttons on the left", s.leftHand, { com.suryaprakash.medlog.ui.OptionIcon(Icons.Rounded.BackHand, p.tintBlue, 56.dp) }) {
                    app.settings.update { it.copy(leftHand = !it.leftHand) } },
            ))
            AccessRow("Movement", Icons.Rounded.MotionPhotosOff, open == "Movement", { toggleGroup("Movement") }, listOf(
                AccessCard("Moving screens bother me", "No slides or animations", s.lessMotion, { com.suryaprakash.medlog.ui.OptionIcon(Icons.Rounded.MotionPhotosOff, p.tintPurple, 56.dp) }) {
                    app.settings.update { it.copy(lessMotion = !it.lessMotion) } },
            ))
        }

        // ───────────── read aloud ─────────────
        S.READ -> {
            val now = when { !s.readAloud -> 2; s.autoRead -> 0; else -> 1 }
            FlowScreen(task, "Should pages be read out loud?", step = n, steps = total, onBack = { back() }, primary = "Next", onPrimary = { next() }) {
                val p = com.suryaprakash.medlog.ui.LocalPalette.current
                com.suryaprakash.medlog.ui.ChoiceCards(listOf(
                    com.suryaprakash.medlog.ui.BigOption("Read every page", "Good if reading is hard", now == 0, { com.suryaprakash.medlog.ui.OptionIcon(Icons.Rounded.RecordVoiceOver, p.tintBlue, 64.dp) }) {
                        app.settings.update { it.copy(autoRead = true, readAloud = true) }; app.speaker.say("I will read each page to you.") },
                    com.suryaprakash.medlog.ui.BigOption("Only when I tap Read", "A Read button is always there", now == 1, { com.suryaprakash.medlog.ui.OptionIcon(Icons.Rounded.TouchApp, p.tintTeal, 64.dp) }) {
                        app.settings.update { it.copy(autoRead = false, readAloud = true) } },
                    com.suryaprakash.medlog.ui.BigOption("Never", "No button, no voice", now == 2, { com.suryaprakash.medlog.ui.OptionIcon(Icons.Rounded.VolumeOff, p.inkSoft, 64.dp) }) {
                        app.speaker.stop(); app.settings.update { it.copy(autoRead = false, readAloud = false) } },
                ))
            }
        }

        // ───────────── languages ─────────────
        S.LANGS -> FlowScreen(task, "Which languages do you read?", hint = "Tap all you read. The first one is used for the app.", step = n, steps = total,
            onBack = { back() }, primary = "Next", onPrimary = { next() }) {
            // two columns of equal tiles: the name, and the language in its own script
            com.suryaprakash.medlog.clinical.Lang.ALL.chunked(2).forEach { row ->
                Row(Modifier.height(androidx.compose.foundation.layout.IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { l ->
                        val on = l.tag in s.languages
                        val p = com.suryaprakash.medlog.ui.LocalPalette.current
                        val sc = com.suryaprakash.medlog.ui.LocalScale.current
                        val sh = androidx.compose.foundation.shape.RoundedCornerShape(18.dp)
                        Column(
                            Modifier.weight(1f).fillMaxHeight().heightIn(min = sc.target + 16.dp).clip(sh)
                                .background(if (on) p.brandSoft else p.card)
                                .then(if (on) Modifier.border(3.dp, p.brand, sh) else Modifier)
                                .steady(l.name + if (on) ", chosen" else ", not chosen") {
                                    app.settings.update { st -> val list = if (on) st.languages - l.tag else st.languages + l.tag; st.copy(languages = list.ifEmpty { listOf("en-IN") }) }
                                }.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(l.name, fontSize = sc.body, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = p.ink, maxLines = 1)
                            Text(l.native, fontSize = sc.small, color = p.inkSoft, maxLines = 1)
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }

        // ───────────── name ─────────────
        S.NAME -> FlowScreen(task, "What is your name?", hint = "Setting this up for someone else? Write their name.", step = n, steps = total, onBack = { back() },
            primary = "Next", primaryEnabled = pr.name.isNotBlank(), onPrimary = { next() }) {
            BigField("Your name", pr.name, { update { cur -> cur.copy(name = it) } })
            Hint("It goes on the doctor page and in messages to family.")
        }

        // ───────────── born ─────────────
        S.BORN -> {
            var year by remember { mutableStateOf(pr.dob.take(4)) }
            val ok = year.toIntOrNull()?.let { it in 1900..java.time.LocalDate.now().year } == true
            FlowScreen(task, "Which year were you born?", hint = "This shows the problems common at your age first.", step = n, steps = total, onBack = { back() },
                primary = "Next", primaryEnabled = ok, onPrimary = { if (pr.dob.take(4) != year) update { cur -> cur.copy(dob = "$year-07-01") }; next() },
                ) {
                var picking by remember { mutableStateOf(false) }
                BigField("Year", year, {}, hint = "Tap to choose", onTap = { picking = true })
                if (picking) YearSheet(year.toIntOrNull(), onDone = { year = it.toString(); picking = false }, onDismiss = { picking = false })
                Box(Modifier.fillMaxWidth().heightIn(min = 28.dp)) { year.toIntOrNull()?.takeIf { ok }?.let { Body("That's about ${java.time.LocalDate.now().year - it} years old.") } }
            }
        }

        // ───────────── sex ─────────────
        S.SEX -> FlowScreen(task, "Are you a woman or a man?", hint = "Doctors ask this. It also changes a few questions.", step = n, steps = total,
            onBack = { back() }, primary = "Next", onPrimary = { next() }) {
            val p = com.suryaprakash.medlog.ui.LocalPalette.current
            listOf(Triple("F", "Woman", Icons.Rounded.Female), Triple("M", "Man", Icons.Rounded.Male), Triple("", "I'd rather not say", Icons.Rounded.Person))
                .forEach { (k, l, ic) -> Choice(l, pr.sex == k, icon = ic, tint = if (k == "") p.inkSoft else p.tintPurple) { update { cur -> cur.copy(sex = k) } } }
        }

        // ───────────── conditions ─────────────
        S.CONDITIONS -> {
            val chosen = pr.conditions.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            var other by remember { mutableStateOf(chosen.filter { it !in CarePlan.CONDITIONS }.joinToString(", ")) }
            fun set(list: List<String>, extra: String) = update { cur -> cur.copy(conditions = (list.filter { it in CarePlan.CONDITIONS } + extra.split(",").map { it.trim() }.filter { it.isNotEmpty() }).distinct().joinToString(", ")) }
            fun toggleCondition(c: String) = update { cur ->
                val now = cur.conditions.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                cur.copy(conditions = (if (c in now) now - c else now + c).distinct().joinToString(", "))
            }
            FlowScreen(task, "Do you have any long-term illness?", hint = "Tap all that apply.", step = n, steps = total, onBack = { back() },
                primary = if (chosen.isEmpty()) "None of these" else "Next", onPrimary = { next() }) {
                com.suryaprakash.medlog.ui.ChoiceGrid(CarePlan.CONDITIONS, { it in chosen }) { c -> toggleCondition(c) }
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
                SearchBox(query, { query = it }, "Search problems")
                val conds = pr.conditions.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                if (query.isNotBlank()) {
                    SymptomGrid((searchProblems(app, query) + chosen).distinct(), chosen) { toggle(it) }
                } else {
                    // first: what usually comes with their illnesses (or their age); then everything else
                    val related = offered.filter { cat.problem(it) != null }
                    val all = com.suryaprakash.medlog.pictogram.Sprites.SECTIONS.flatMap { it.second }.filter { it !in related }
                    val mine = chosen.filter { it !in related && it !in all }
                    if (related.isNotEmpty()) {
                        com.suryaprakash.medlog.ui.Section(when {
                            conds.isEmpty() -> "Common at your age"
                            conds.size == 1 -> "Common with ${conds[0]}"
                            else -> "Common with ${conds.dropLast(1).joinToString(", ")} and ${conds.last()}"
                        })
                        SymptomGrid(related, chosen) { toggle(it) }
                    }
                    com.suryaprakash.medlog.ui.Section("All problems")
                    SymptomGrid(mine + all, chosen) { toggle(it) }
                }
            }
        }

        // ───────────── medicines ─────────────
        S.MEDS -> {
            val meds by app.db.medicines().activeFlow().collectAsState(emptyList())
            FlowScreen(task, "Which medicines do you take?", hint = "Add each one once. You'll be reminded on time.", step = n, steps = total, onBack = { back() },
                primary = if (meds.isEmpty()) "I don't take any" else "Next", onPrimary = { next() }) {
                meds.forEach { m -> MedicineCard(m) { nav.go(Route.MedEdit(m.id)) } }
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
            var writing by remember { mutableStateOf(false) }
            if (writing) {
                FlowScreen(task, "What are you allergic to?", hint = "For example: penicillin, causes a rash", step = n, steps = total, onBack = { writing = false },
                    primary = "Next", primaryEnabled = pr.allergies.isNotBlank(), onPrimary = { writing = false; next() }) {
                    BigField("Allergic to", pr.allergies, { update { cur -> cur.copy(allergies = it) } })
                }
                return@CompositionLocalProvider
            }
            FlowScreen(task, "Any allergies to medicine or food?", step = n, steps = total, onBack = { back() },
                primary = "Next", primaryEnabled = has == false || (has == true && pr.allergies.isNotBlank()), onPrimary = { next() }) {
                val p = com.suryaprakash.medlog.ui.LocalPalette.current
                com.suryaprakash.medlog.ui.AnswerCards(listOf(
                    com.suryaprakash.medlog.ui.Answer("I have no allergies", "Not that I know of", Icons.Rounded.CheckCircle, p.ok, has == false) {
                        has = false; update { cur -> cur.copy(allergies = "") }; next() },
                    com.suryaprakash.medlog.ui.Answer("I'm allergic to something", "You'll tell us what next", Icons.Rounded.Warning, p.amber, has == true) {
                        has = true; writing = true },
                ))
                Box(Modifier.fillMaxWidth().heightIn(min = 28.dp)) { if (has == true && pr.allergies.isNotBlank()) Body("Allergic to: ${pr.allergies}") }
            }
        }

        // ───────────── risks ─────────────
        S.RISKS -> FlowScreen(task, "Do any of these apply to you?", hint = "It helps us know when to call your family.", step = n, steps = total, onBack = { back() },
            primary = if (plan.risks.isEmpty()) "None of these" else "Next", onPrimary = { next() }) {
            CarePlan.RISKS.forEach { (k, l) -> Choice(l, k in plan.risks, multi = true) { savePlan { it.copy(risks = if (k in it.risks) it.risks - k else it.risks + k) } } }
        }

        // ───────────── doctors ─────────────
        S.DOCTORS -> FlowScreen(task, "Who are your doctors?", hint = "Add each doctor with what they treat, so the right one is offered to call.", step = n, steps = total,
            onBack = { back() }, primary = if (plan.doctors.isEmpty()) "Add a doctor" else "Next",
            onPrimary = { if (plan.doctors.isEmpty()) { Onboard.editingDoctor = -1; go(S.DOCTOR_FORM) } else go(S.HELPERS) },
            secondary = if (plan.doctors.isEmpty()) "Skip for now" else null, onSecondary = { go(S.HELPERS) }) {
            if (plan.doctors.isNotEmpty()) {
                Group {
                    plan.doctors.forEachIndexed { i, d ->
                        if (i > 0) GroupLine()
                        ValueRow(d.name, d.speciality, sub = listOf(d.hospital, d.phone).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { null }) { Onboard.editingDoctor = i; go(S.DOCTOR_FORM) }
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
            hint = "Your helpers are called straight away.",
            step = n, steps = total, onBack = { back() }, primary = "Next", onPrimary = { next() }) {
            SymptomGrid(CarePlan.EMERGENCIES.filter { app.catalogue.problem(it) != null }, plan.emergencies.toList()) { id ->
                savePlan { it.copy(emergencies = if (id in it.emergencies) it.emergencies - id else it.emergencies + id) }
            }
        }

        // ───────────── check-in ─────────────
        S.CHECKIN -> {
            val opts = listOf("08:00" to ("Morning" to "8 am"), "10:00" to ("Mid-morning" to "10 am"), "13:00" to ("Afternoon" to "1 pm"), "18:00" to ("Evening" to "6 pm"))
            FlowScreen(task, "When should we ask how you are?", hint = "Once a day. If you don't answer within 2 hours, your helpers get a message.", step = n, steps = total,
                onBack = { back() }, primary = "Next", onPrimary = { scope.launch { Scheduler.reschedule(ctx) }; next() }) {
                opts.forEach { (t, l) ->
                    val part = com.suryaprakash.medlog.ui.dayPart(t.substringBefore(":").toInt())
                    Choice(l.first, s.checkInEnabled && s.checkInTime == t, sub = l.second, icon = part.icon, tint = part.tint) {
                        app.settings.update { it.copy(checkInEnabled = true, checkInTime = t) } }
                }
                Choice("Don't ask me every day", !s.checkInEnabled, icon = Icons.Rounded.NotificationsOff, tint = com.suryaprakash.medlog.ui.LocalPalette.current.inkSoft) {
                    app.settings.update { it.copy(checkInEnabled = false) } }
            }
        }

        // ───────────── permissions ─────────────
        S.PERMISSIONS -> PermissionsStep(n, total, onBack = { back() }) { next() }

        // ───────────── widget ─────────────
        S.WIDGET -> FlowScreen(task, "Put it on your home screen", hint = "One tap notes how you feel. No need to open the app.", step = n, steps = total,
            onBack = { back() }, primary = "Add to home screen",
            onPrimary = { pinWidget(ctx); scope.launch { delay(600); next() } },
            secondary = "Not now", onSecondary = { next() }) {
            WidgetPreview { pinWidget(ctx); scope.launch { delay(600); next() } }
        }

        // ───────────── finish ─────────────
        S.DONE -> DoneStep(nav, pr, plan)
    }
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
    var hospital by remember(i) { mutableStateOf(old?.hospital ?: "") }
    val pick = rememberContactPicker { n, ph -> if (name.isBlank()) name = n; phone = ph }
    FlowScreen("Doctor", if (old == null) "Add a doctor" else "Change ${old.name}", onBack = onBack,
        primary = "Done", primaryEnabled = name.isNotBlank(), onPrimary = {
            val d = CarePlan.Doctor(name.trim(), spec, phone.trim(), hospital.trim())
            onSave(if (old == null) plan.doctors + d else plan.doctors.mapIndexed { k, x -> if (k == i) d else x })
        }, secondary = if (old != null) "Remove this doctor" else null, onSecondary = { onSave(plan.doctors.filterIndexed { k, _ -> k != i }) }) {
        BigField("Doctor's name", name, { name = it }, hint = "For example: Dr. Rao")
        BigField("Hospital or clinic", hospital, { hospital = it }, hint = "For example: City Hospital")
        Section("What do they treat?")
        FlowRowOf { CarePlan.SPECIALITIES.forEach { sp -> Chip(sp, spec == sp) { spec = sp } } }
        Section("Phone number")
        BigButton("Choose from contacts", tone = Tone.SECONDARY, icon = Icons.Rounded.Contacts, onClick = pick)
        BigField("Mobile number", phone, { phone = it }, keyboard = KeyboardType.Phone)
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
    FlowScreen("Setting up", "Who should be called if you need help?", hint = if (helpers.isEmpty()) "Family, friends or neighbours. Your notes stay private." else "They are called in this order.", step = n, steps = total,
        onBack = onBack, primary = if (helpers.isEmpty()) "Add a helper" else "Next", onPrimary = { if (helpers.isEmpty()) onEdit(null) else next() },
        secondary = if (helpers.isEmpty()) "Skip for now" else null, onSecondary = { askSkip = true }) {
        if (helpers.isEmpty()) {
            HelpSteps(s.emergencyNumber)
        } else {
            helpers.forEachIndexed { i, h -> HelperCard(nav, h, i, helpers, first, onEdit) }
            if (helpers.size < 5) BigButton("Add another helper", tone = Tone.OUTLINE, icon = Icons.Rounded.PersonAdd, onClick = { onEdit(null) })
        }
    }
    if (askSkip) ConfirmDialog("Continue without a helper?", "Without a helper, only ${s.emergencyNumber} can be called in an emergency.",
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
        primary = "Done", primaryEnabled = ok, onPrimary = {
            scope.launch {
                if (id == null) app.db.helpers().insert(h.copy(name = h.name.trim(), phone = h.phone.trim(), sortOrder = app.db.helpers().all().size))
                else app.db.helpers().update(h.copy(name = h.name.trim(), phone = h.phone.trim()))
                back()
            }
        }, secondary = if (id != null) "Remove this helper" else null, onSecondary = { confirmRemove = true }) {
        BigButton("Choose from contacts", tone = if (h.name.isBlank()) Tone.PRIMARY else Tone.SECONDARY, icon = Icons.Rounded.Contacts, onClick = pick)
        BigField("Name", h.name, { h = h.copy(name = it) })
        BigField("Phone number", h.phone, { h = h.copy(phone = it) }, keyboard = KeyboardType.Phone)
        RelationField(h.relation, relations) { h = h.copy(relation = it) }
        Section("What should they get?")
        com.suryaprakash.medlog.ui.Toggle("Calls and texts if I need help", h.sos, "With where you are") { h = h.copy(sos = it) }
        com.suryaprakash.medlog.ui.Toggle("A text if I miss a medicine", h.alerts) { h = h.copy(alerts = it) }
    }
    if (confirmRemove) ConfirmDialog("Remove ${h.name}?", "${h.name} won't be called or texted if you need help.", yes = "Keep", no = "Remove",
        onYes = { confirmRemove = false }, onNo = { confirmRemove = false; scope.launch { id?.let { app.db.helpers().delete(it) }; back() } })
}

/** What happens when you ask for help, as three pictures in order. */
@Composable
private fun HelpSteps(emergency: String) {
    val p = com.suryaprakash.medlog.ui.LocalPalette.current
    val sc = com.suryaprakash.medlog.ui.LocalScale.current
    val steps = listOf(
        Triple(Icons.Rounded.Sms, p.tintBlue, "A text with where you are" to "The moment you ask for help"),
        Triple(Icons.Rounded.Call, p.tintGreen, "A call, one person at a time" to "Until someone answers"),
        Triple(Icons.Rounded.LocalHospital, p.red, "Then $emergency" to "If nobody answers"),
    )
    steps.forEachIndexed { i, (icon, tint, words) ->
        if (i > 0) Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.ArrowDownward, null, tint = p.inkSoft.copy(alpha = 0.5f), modifier = Modifier.size(24.dp))
        }
        val sh = androidx.compose.foundation.shape.RoundedCornerShape(sc.radius)
        Row(Modifier.fillMaxWidth().clip(sh).background(p.card).padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically) {
            com.suryaprakash.medlog.ui.OptionIcon(icon, tint, 64.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(words.first, fontSize = sc.body, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, color = p.ink)
                Text(words.second, fontSize = sc.small, color = p.inkSoft)
            }
        }
    }
}

/** One helper as a person: their picture, name and place in the order, one main action and a ⋯ for the rest. */
@Composable
private fun HelperCard(nav: Nav, h: Helper, i: Int, all: List<Helper>, first: String, onEdit: (Long?) -> Unit) {
    val ctx = LocalContext.current
    val app = ctx.medlog
    val p = com.suryaprakash.medlog.ui.LocalPalette.current
    val sc = com.suryaprakash.medlog.ui.LocalScale.current
    val scope = rememberCoroutineScope()
    val name = h.name.substringBefore(" ")
    val connected = h.pairId != null
    var more by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }
    fun text() {
        val body = "Hi $name, I've added you as my helper in MedLog. If I need help, you'll get a text and a call." + if (first.isNotBlank()) " – $first" else ""
        runCatching { ctx.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${h.phone}")).putExtra("sms_body", body)) }
    }
    val bold = androidx.compose.ui.text.font.FontWeight.Bold
    val sh = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)
    val order = listOf("first", "second", "third", "fourth", "fifth")[i.coerceAtMost(4)]
    val before = all.getOrNull(i - 1)?.name?.substringBefore(" ")
    Column(Modifier.fillMaxWidth().clip(sh).background(p.card)) {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(76.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(21.dp)).background(p.fill), contentAlignment = Alignment.Center) {
                    Text(h.name.trim().take(1).uppercase().ifBlank { "?" }, fontSize = 34.sp, fontWeight = bold, color = p.inkSoft)
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(h.name, fontSize = sc.cardTitle, fontWeight = bold, color = p.ink)
                    Text(listOfNotNull(h.relation.ifBlank { null }, h.phone).joinToString(" · "), fontSize = sc.body, color = p.inkSoft, maxLines = 1)
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                if (connected) BigButton("Send a text", Modifier.weight(1f), Tone.TINT, icon = Icons.Rounded.Sms, height = 52.dp, onClick = { text() })
                else BigButton("Connect phone", Modifier.weight(1f), Tone.TINT, icon = Icons.Rounded.PhonelinkRing, height = 52.dp, onClick = { nav.go(Route.Pair) })
                val msh = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
                Box(Modifier.size(52.dp).clip(msh).background(p.fill).steady("More for $name", onClick = { more = true }), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.MoreHoriz, null, tint = p.ink, modifier = Modifier.size(28.dp))
                }
            }
        }
        // where they are in the calling order, as a strip along the bottom
        Box(Modifier.fillMaxWidth().height(1.dp).background(p.line))
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Call, null, tint = p.inkSoft, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(if (before == null) "They will be called first for help" else "They will be called next, after $before",
                fontSize = sc.small, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, color = p.inkSoft)
        }
    }
    if (more) OptionsSheet(h.name, listOfNotNull(h.relation.ifBlank { null }, h.phone).joinToString(" · "), buildList {
        if (!connected) add("Send $name a text" to { more = false; text() })
        if (i > 0) add("Call $name earlier" to {
            more = false
            scope.launch {
                val list = all.toMutableList().apply { add(i - 1, removeAt(i)) }
                list.forEachIndexed { k, x -> app.db.helpers().update(x.copy(sortOrder = k)) }
            }
        })
        add("Change $name's details" to { more = false; onEdit(h.id) })
        add("Remove $name" to { more = false; confirmRemove = true })
    }) { more = false }
    if (confirmRemove) ConfirmDialog("Remove ${h.name}?", "${h.name} won't be called or texted if you need help.", yes = "Keep $name", no = "Remove $name",
        onYes = { confirmRemove = false }, onNo = { confirmRemove = false; scope.launch { app.db.helpers().delete(h.id) } })
}

/** "Relationship" as a standard dropdown box. */
@Composable
private fun RelationField(value: String, options: List<String>, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val p = com.suryaprakash.medlog.ui.LocalPalette.current
    val sc = com.suryaprakash.medlog.ui.LocalScale.current
    Box {
        BigField("Relationship", value, {}, onTap = { open = true })
        androidx.compose.material3.DropdownMenu(open, { open = false }, containerColor = p.card, modifier = Modifier.fillMaxWidth(0.88f)) {
            options.forEach { r ->
                androidx.compose.material3.DropdownMenuItem(text = { Text(r, fontSize = sc.body, color = p.ink) },
                    onClick = { onPick(r); open = false }, modifier = Modifier.heightIn(min = 56.dp))
            }
        }
    }
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
    val pal = LocalPalette.current
    val runtime = listOf(
        Triple("Reminders", Icons.Rounded.Notifications to pal.tintOrange, Perms.NOTIFY),
        Triple("Texts", Icons.Rounded.Sms to pal.tintBlue, Perms.SMS),
        Triple("Calls", Icons.Rounded.Call to pal.tintGreen, Perms.CALL),
        Triple("Location", Icons.Rounded.LocationOn to pal.tintPink, Perms.LOCATION),
    )
    val missing = runtime.filter { !Perms.has(ctx, *it.third) }
    val special = buildList {
        if (!Perms.exactAlarmsOk(ctx)) add(Triple("Ring at the exact time", Icons.Rounded.Alarm) { Perms.openExactAlarms(ctx) })
        if (!Perms.fullScreenOk(ctx)) add(Triple("Show alarms on the lock screen", Icons.Rounded.Notifications) { Perms.openFullScreen(ctx) })
        if (!Perms.batteryOk(ctx)) add(Triple("Keep working when the phone sleeps", Icons.Rounded.BatteryChargingFull) { Perms.openBattery(ctx) })
    }
    FlowScreen("Setting up", "Allow this phone to look after you", hint = "Turn each one on, then tap Allow on the phone's message.", step = n, steps = total, onBack = onBack,
        primary = if (missing.size > 1) "Allow all" else if (missing.isNotEmpty()) "Allow" else "Next", onPrimary = { if (missing.isNotEmpty()) ask(missing.flatMap { it.third.toList() }.toTypedArray()) else next() },
        secondary = if (missing.isNotEmpty()) "Not now" else null, onSecondary = next) {
        Group {
            runtime.forEachIndexed { i, (t, look, perms) ->
                if (i > 0) GroupLine()
                val on = Perms.has(ctx, *perms)
                PermRow(t, look.first, look.second, on) {
                    if (!on) ask(perms)
                    else runCatching { ctx.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))) }
                }
            }
        }
        if (special.isNotEmpty() && missing.isEmpty()) {
            Section("So alarms always ring")
            Group {
                special.forEachIndexed { i, (t, _, open) -> if (i > 0) GroupLine(); ValueRow(t, "Turn on", onClick = open) }
            }
        }
        Hint("Texts, calls and location are used only when you ask for help.")
    }
}

/** One permission as a settings row: its icon, one word, and a switch that shows whether it's allowed. */
@Composable
private fun PermRow(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, tint: androidx.compose.ui.graphics.Color, allowed: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    val sc = com.suryaprakash.medlog.ui.LocalScale.current
    Row(Modifier.fillMaxWidth().heightIn(min = sc.target + 16.dp).steady("$label, ${if (allowed) "allowed" else "not allowed"}", onClick = onClick)
        .padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        com.suryaprakash.medlog.ui.OptionIcon(icon, tint, 48.dp)
        Spacer(Modifier.width(16.dp))
        Text(label, fontSize = sc.body, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, color = p.ink, modifier = Modifier.weight(1f))
        com.suryaprakash.medlog.ui.AppSwitch(allowed)
    }
}

/** What the home-screen widget looks like, on a phone's home screen: problems to tap, a message, and SOS. */
@Composable
private fun WidgetPreview(onAdd: () -> Unit) {
    val ctx = LocalContext.current
    val p = LocalPalette.current
    val sc = com.suryaprakash.medlog.ui.LocalScale.current
    val cat = ctx.medlog.catalogue
    com.suryaprakash.medlog.pictogram.Sprites.init(ctx)
    val bold = androidx.compose.ui.text.font.FontWeight.Bold
    val wall = androidx.compose.ui.graphics.Brush.verticalGradient(listOf(androidx.compose.ui.graphics.Color(0xFFDCE6EE), androidx.compose.ui.graphics.Color(0xFFE9E4DA)))
    // the home screen
    Box(Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(28.dp)).background(wall)
        .steady("The home-screen widget. Tap to add it", onClick = onAdd).padding(18.dp)) {
        // the widget
        val wsh = androidx.compose.foundation.shape.RoundedCornerShape(22.dp)
        Column(Modifier.fillMaxWidth().clip(wsh).background(p.card).padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("headache", "dizzy", "tired").forEach { id ->
                    Column(Modifier.weight(1f).clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp)).background(p.fill).padding(vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        com.suryaprakash.medlog.pictogram.SpriteIcon(id, 56.dp)
                        Spacer(Modifier.height(4.dp))
                        Text(cat.problem(id)?.label ?: id, fontSize = sc.small, fontWeight = bold, color = p.ink, maxLines = 1)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp)).background(p.fill).padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text("I'm fine today", fontSize = sc.small, fontWeight = bold, color = p.ink, modifier = Modifier.weight(1f))
                Text("Send", fontSize = sc.small, fontWeight = bold, color = p.brand)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1.6f).height(48.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp)).background(p.fill), contentAlignment = Alignment.Center) {
                    Text("Something else", fontSize = sc.small, fontWeight = bold, color = p.ink, maxLines = 1)
                }
                Box(Modifier.weight(1f).height(48.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp)).background(p.red), contentAlignment = Alignment.Center) {
                    Text("SOS", fontSize = sc.body, fontWeight = bold, color = androidx.compose.ui.graphics.Color.White)
                }
            }
        }
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
    FlowScreen("Setting up", if (name.isBlank()) "You're all set" else "You're all set, $name", hint = "Change anything later in Settings.",
        onBack = { Onboard.step = S.WIDGET.ordinal }, primary = "Start", onPrimary = {
            scope.launch {
                app.settings.update { it.copy(onboarded = true, role = "self") }
                Scheduler.reschedule(ctx)
                Onboard.step = 0
                nav.home(Route.Home)
                // they also help someone: connect to that person's phone next
                if (Onboard.alsoHelps) { Onboard.alsoHelps = false; PairMode.helping = true; nav.go(Route.Pair) }
            }
        }) {
        val p = LocalPalette.current
        fun count(n: Int) = if (n > 0) "$n" else "None"
        val illnesses = pr.conditions.split(",").map { it.trim() }.filter { it.isNotEmpty() }.size
        val checkIn = if (s.checkInEnabled) s.checkInTime.substringBefore(":").toIntOrNull()?.let { h -> "${if (h % 12 == 0) 12 else h % 12} ${if (h < 12) "am" else "pm"}" } ?: s.checkInTime else "Off"
        val facts = listOf(
            Triple("Doctors", count(plan.doctors.size), Icons.Rounded.LocalHospital to p.tintBlue),
            Triple("Helpers", count(helpers.size), Icons.Rounded.Groups to p.tintPurple),
            Triple("Medicines", count(meds.size), Icons.Rounded.Medication to p.tintTeal),
            Triple("Illnesses", count(illnesses), Icons.Rounded.MonitorHeart to p.tintPink),
            Triple("Emergencies", count(plan.emergencies.size), Icons.Rounded.Sos to p.red),
            Triple("Check-in", checkIn, Icons.Rounded.Alarm to p.tintOrange),
        )
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            facts.chunked(2).forEach { row ->
                Row(Modifier.height(androidx.compose.foundation.layout.IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { (label, value, look) -> FactTile(label, value, look.first, look.second, Modifier.weight(1f).fillMaxHeight()) }
                }
            }
        }
        val wsh = androidx.compose.foundation.shape.RoundedCornerShape(com.suryaprakash.medlog.ui.LocalScale.current.radius)
        Row(Modifier.fillMaxWidth().clip(wsh).background(p.card).border(1.5.dp, p.amber, wsh).padding(horizontal = 14.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            com.suryaprakash.medlog.ui.OptionIcon(Icons.Rounded.Warning, p.amber, 40.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Body("This is not a doctor", bold = true)
                Hint(if (app.catalogue.reviewed) "In an emergency, press SOS." else "In an emergency, press SOS. Follow your doctor's advice.")
            }
        }
    }
}

/** One thing MedLog knows, as a tile: an icon, the value large, and one word. */
@Composable
private fun FactTile(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, tint: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    val p = LocalPalette.current
    val sc = com.suryaprakash.medlog.ui.LocalScale.current
    val sh = androidx.compose.foundation.shape.RoundedCornerShape(sc.radius)
    Row(modifier.clip(sh).background(p.card).padding(horizontal = 14.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        com.suryaprakash.medlog.ui.OptionIcon(icon, tint, 40.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(value, fontSize = sc.headline, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = if (value == "None" || value == "Off") p.inkSoft else p.ink, maxLines = 1)
            Text(label, fontSize = sc.small, color = p.inkSoft, maxLines = 1)
        }
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

/**
 * What MedLog does, one thing at a time: a card that moves on by itself every few seconds (or with a swipe),
 * every card the same size, with dots showing where you are.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun WelcomeCarousel() {
    val p = com.suryaprakash.medlog.ui.LocalPalette.current
    val sc = com.suryaprakash.medlog.ui.LocalScale.current
    val still = com.suryaprakash.medlog.ui.LocalSettings.current.lessMotion
    data class Slide(val icon: androidx.compose.ui.graphics.vector.ImageVector, val tint: androidx.compose.ui.graphics.Color, val title: String, val sub: String)
    val slides = listOf(
        Slide(Icons.Rounded.TouchApp, p.tintBlue, "Tell how you feel", "Tap a picture. A few short questions."),
        Slide(Icons.Rounded.Medication, p.tintOrange, "Medicines on time", "Reminders that ring, even on silent."),
        Slide(Icons.Rounded.Sos, p.red, "Help in one tap", "Your family is called and told where you are."),
        Slide(Icons.Rounded.Description, p.tintPurple, "One page for the doctor", "What happened, when, and how bad."),
    )
    val pager = androidx.compose.foundation.pager.rememberPagerState { slides.size }
    LaunchedEffect(still) {
        if (still) return@LaunchedEffect
        while (true) { kotlinx.coroutines.delay(3500); pager.animateScrollToPage((pager.currentPage + 1) % slides.size) }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // portrait cards with the next one peeking in, so it's clear they can be swiped by thumb too
        androidx.compose.foundation.pager.HorizontalPager(pager, Modifier.fillMaxWidth(), pageSpacing = 14.dp,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 40.dp)) { i ->
            val sl = slides[i]
            val sh = androidx.compose.foundation.shape.RoundedCornerShape(32.dp)
            Column(
                Modifier.fillMaxWidth().height(if (sc.big) 420.dp else 380.dp).clip(sh).background(sl.tint.copy(alpha = 0.10f)).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
            ) {
                com.suryaprakash.medlog.ui.IconTile(sl.icon, sl.tint, 104.dp)
                Spacer(Modifier.height(28.dp))
                Text(sl.title, fontSize = sc.title, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = p.ink, textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 2, minLines = 2)
                Spacer(Modifier.height(8.dp))
                Text(sl.sub, fontSize = sc.body, color = p.inkSoft, textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 2, minLines = 2)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(slides.size) { i ->
                Box(Modifier.height(8.dp).width(if (i == pager.currentPage) 24.dp else 8.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                    .background(if (i == pager.currentPage) p.brand else p.line))
            }
        }
    }
}

/** The same square for both sizes, so the difference in the letters' size is what you see. */
@Composable
private fun SizeSample(big: Boolean, box: androidx.compose.ui.unit.Dp = 84.dp) {
    val p = com.suryaprakash.medlog.ui.LocalPalette.current
    val onAccent = com.suryaprakash.medlog.ui.LocalOnAccent.current
    Box(Modifier.size(box).clip(androidx.compose.foundation.shape.RoundedCornerShape(box * 0.24f)).background(if (onAccent) androidx.compose.ui.graphics.Color.White else p.brand.copy(alpha = 0.10f)), contentAlignment = Alignment.Center) {
        // the sample keeps to half its box (the icon-box rule), Regular smaller in the same proportion
        Text("Aa", fontSize = (box.value * if (big) 0.42f else 0.26f).sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = p.ink)
    }
}

/** The year of birth, chosen on a rolling wheel in a panel from the bottom. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun YearSheet(current: Int?, onDone: (Int) -> Unit, onDismiss: () -> Unit) {
    val p = com.suryaprakash.medlog.ui.LocalPalette.current
    val sc = com.suryaprakash.medlog.ui.LocalScale.current
    val now = java.time.LocalDate.now().year
    val years = remember { (1920..now).toList() }
    var pick by remember { mutableStateOf(current ?: 1955) }
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss, containerColor = p.card,
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = sc.margin).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Year you were born", fontSize = sc.headline, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = p.ink)
            com.suryaprakash.medlog.ui.NumberWheel(years, pick, { pick = it })
            BigButton("Done, $pick", onClick = { onDone(pick) })
        }
    }
}

/** Problems as two columns of picture tiles: a big picture, the name close under it. */
@Composable
private fun SymptomGrid(ids: List<String>, chosen: List<String>, toggle: (String) -> Unit) {
    val ctx = LocalContext.current
    val cat = ctx.medlog.catalogue
    val sc = com.suryaprakash.medlog.ui.LocalScale.current
    com.suryaprakash.medlog.ui.TileGrid(ids, 2, aspect = 1.0f) { id, m ->
        val label = cat.problem(id)?.label ?: id
        com.suryaprakash.medlog.ui.PicTile(label, m, picture = 84.dp, selected = id in chosen, onClick = { toggle(id) }) {
            com.suryaprakash.medlog.pictogram.SpriteIcon(id, 84.dp)
        }
    }
}

/** Setup's sections, for "Skip this section". The first and choice pages aren't in any (they have no Skip). */
private val SECTIONS = listOf(
    "accessibility" to setOf(S.SIZE),
    "languages" to setOf(S.LANGS),
    "about you" to setOf(S.NAME, S.BORN, S.SEX),
    "health questions" to setOf(S.CONDITIONS, S.SYMPTOMS, S.MEDS, S.TREATMENTS, S.ALLERGY, S.RISKS),
    "doctors" to setOf(S.DOCTORS, S.DOCTOR_FORM),
    "helpers" to setOf(S.HELPERS, S.HELPER_FORM, S.EMERGENCIES),
    "daily check-in" to setOf(S.CHECKIN),
    "phone settings" to setOf(S.PERMISSIONS, S.WIDGET),
)

/** Skip: this section, or setup altogether. */
@Composable
private fun SkipSheet(section: String, onSection: () -> Unit, onAll: () -> Unit, onDismiss: () -> Unit) =
    OptionsSheet("Skip?", "You can answer these later in Settings.", listOf("Skip $section" to onSection, "Skip setup completely" to onAll), onDismiss)

/** A panel from the bottom with a title, a line of help, and a few big choices (the first is the main one). */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun OptionsSheet(title: String, help: String, options: List<Pair<String, () -> Unit>>, onDismiss: () -> Unit) {
    val p = com.suryaprakash.medlog.ui.LocalPalette.current
    val sc = com.suryaprakash.medlog.ui.LocalScale.current
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss, containerColor = p.card,
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = sc.margin).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, fontSize = sc.headline, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = p.ink)
            Body(help)
            Spacer(Modifier.height(4.dp))
            options.forEachIndexed { i, (label, go) -> BigButton(label, tone = if (i == 0) Tone.PRIMARY else Tone.SECONDARY, onClick = go) }
        }
    }
}

/** One accessibility option as a portrait card. */
private data class AccessCard(val title: String, val sub: String, val on: Boolean, val visual: @Composable () -> Unit, val toggle: () -> Unit)

/** One kind of difficulty: a header row (icon, name, how many chosen, arrow) that opens its cards below. */
@Composable
private fun AccessRow(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, expanded: Boolean, onHeader: () -> Unit, cards: List<AccessCard>) {
    val p = com.suryaprakash.medlog.ui.LocalPalette.current
    val sc = com.suryaprakash.medlog.ui.LocalScale.current
    val chosen = cards.count { it.on && it.title != "Regular words" }   // the usual word size is not a change
    // a plain heading row on the page (not a card), with a thin line under it; only the options are cards
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().heightIn(min = sc.target + 12.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
            .steady(title + if (expanded) ", open" else ", closed", onClick = onHeader)
            .padding(horizontal = 4.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            com.suryaprakash.medlog.ui.OptionIcon(icon, p.brand, 44.dp)
            Spacer(Modifier.width(14.dp))
            Text(title, fontSize = sc.headline, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = p.ink, modifier = Modifier.weight(1f))
            if (chosen > 0) Text("$chosen chosen", fontSize = sc.small, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, color = p.brand)
            Spacer(Modifier.width(8.dp))
            androidx.compose.material3.Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = p.inkSoft, modifier = Modifier.size(28.dp))
        }
        if (expanded) Column(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            cards.forEach { c ->
                val csh = androidx.compose.foundation.shape.RoundedCornerShape(18.dp)
                Row(
                    Modifier.fillMaxWidth().clip(csh)
                        .background(if (c.on) p.brandSoft else p.card)
                        .then(if (c.on) Modifier.border(3.dp, p.brand, csh) else Modifier)
                        .steady(c.title + ". " + c.sub + if (c.on) ", chosen" else ", not chosen", onClick = c.toggle)
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) { c.visual() }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(c.title, fontSize = sc.body * 1.1f, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = p.ink, lineHeight = sc.body * 1.35f)
                        Text(c.sub, fontSize = sc.body * 0.95f, color = p.inkSoft, lineHeight = sc.body * 1.3f)
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(p.line))
    }
}
