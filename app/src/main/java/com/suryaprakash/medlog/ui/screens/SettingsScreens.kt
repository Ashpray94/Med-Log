package com.suryaprakash.medlog.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Translate
import com.suryaprakash.medlog.ui.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.suryaprakash.medlog.data.Backup
import com.suryaprakash.medlog.data.Profile
import com.suryaprakash.medlog.data.saveCarePlan
import com.suryaprakash.medlog.help.FallService
import com.suryaprakash.medlog.help.WhatsAppCallService
import com.suryaprakash.medlog.integration.CalendarSync
import com.suryaprakash.medlog.medlog
import com.suryaprakash.medlog.meds.Scheduler
import com.suryaprakash.medlog.ui.BigButton
import com.suryaprakash.medlog.ui.BigField
import com.suryaprakash.medlog.ui.Body
import com.suryaprakash.medlog.ui.Card
import com.suryaprakash.medlog.ui.Chip
import com.suryaprakash.medlog.ui.FlowRowOf
import com.suryaprakash.medlog.ui.Hint
import com.suryaprakash.medlog.ui.LocalPalette
import com.suryaprakash.medlog.ui.ListRow
import com.suryaprakash.medlog.ui.LocalScale
import com.suryaprakash.medlog.ui.LocalSettings
import com.suryaprakash.medlog.ui.Nav
import com.suryaprakash.medlog.ui.Perms
import com.suryaprakash.medlog.ui.Route
import com.suryaprakash.medlog.ui.Screen
import com.suryaprakash.medlog.ui.Title
import com.suryaprakash.medlog.ui.Toggle
import com.suryaprakash.medlog.help.Relay
import com.suryaprakash.medlog.ui.Tone
import com.suryaprakash.medlog.ui.rememberPermissionAsker
import com.suryaprakash.medlog.widget.QuickNotification
import kotlinx.coroutines.launch

private var unlockedUntil = 0L

@Composable
fun SettingsScreen(nav: Nav) {
    val ctx = LocalContext.current
    val app = ctx.medlog
    val s = LocalSettings.current
    val sc = LocalScale.current
    val scope = rememberCoroutineScope()
    var section by remember { mutableStateOf<String?>(null) }
    var profile by remember { mutableStateOf(Profile()) }
    var pinText by remember { mutableStateOf("") }
    var locked by remember { mutableStateOf(s.helperPin.isNotBlank() && System.currentTimeMillis() > unlockedUntil) }
    LaunchedEffect(Unit) { profile = app.repo.profile() }

    if (locked) {
        Screen("Settings", "Settings are locked by your helper. Easy mode can still be changed.", onHome = { nav.home() }) {
            BigButton("Easy mode and reading aloud", icon = Icons.Rounded.TextFields, onClick = { nav.go(Route.EasySettings) })
            Card {
                Body("Helper PIN", bold = true)
                BigField("PIN", pinText, { pinText = it.filter(Char::isDigit).take(6) }, keyboard = KeyboardType.NumberPassword)
                BigButton("Unlock", enabled = pinText.length >= 4, onClick = {
                    if (pinText == s.helperPin) { unlockedUntil = System.currentTimeMillis() + 10 * 60_000; locked = false } else { pinText = ""; app.speaker.say("That PIN is not right.") }
                })
            }
        }
        return
    }

    when (section) {
        "me" -> Screen("My details", "Your details for the doctor page.", onHome = { nav.home() }, onBack = { section = null }) {
            BigField("Name", profile.name, { profile = profile.copy(name = it) })
            BigField("Year of birth", profile.dob.take(4), { y -> val v = y.filter(Char::isDigit).take(4); profile = profile.copy(dob = if (v.length == 4) "$v-07-01" else v) },
                keyboard = KeyboardType.Number, hint = "For example 1948")
            run { val o = listOf("F" to "Woman", "M" to "Man", "" to "Not said"); com.suryaprakash.medlog.ui.Segmented(o.map { it.second }, o.indexOfFirst { it.first == profile.sex }) { profile = profile.copy(sex = o[it].first) } }
            BigField("Blood group", profile.bloodGroup, { profile = profile.copy(bloodGroup = it) })
            BigField("Hospital ID", profile.hospitalId, { profile = profile.copy(hospitalId = it) })
            BigField("Illnesses", profile.conditions, { profile = profile.copy(conditions = it) }, lines = 2)
            BigField("Allergies", profile.allergies, { profile = profile.copy(allergies = it) }, lines = 2)
            Toggle("I take a blood thinner", profile.onBloodThinner) { profile = profile.copy(onBloodThinner = it) }
            BigButton("Done", tone = Tone.PRIMARY, onClick = { scope.launch { app.db.profile().put(profile); section = null } })
        }
        "reminders" -> Screen("Medicine reminders", "How reminders work.", onHome = { nav.home() }, onBack = { section = null }) {
            Body("Remind again after", bold = true)
            run { val o = listOf(5, 10, 15); com.suryaprakash.medlog.ui.Segmented(o.map { "$it min" }, o.indexOf(s.snoozeMinutes)) { i -> app.settings.update { it.copy(snoozeMinutes = o[i]) } } }
            Body("Tell my helpers if not taken after", bold = true)
            run { val o = listOf(20, 30, 45, 60); com.suryaprakash.medlog.ui.Segmented(o.map { "$it min" }, o.indexOf(s.escalateMinutes)) { i -> app.settings.update { it.copy(escalateMinutes = o[i]) } } }
            Hint("Important medicines: after ${s.escalateCriticalMinutes} minutes.")
            Title("Google Calendar")
            CalendarPicker()
            Title("Meeting Timer")
            val mt = CalendarSync.meetingTimerInstalled(ctx)
            Toggle("Show reminders in Meeting Timer", s.useMeetingTimer && mt, if (mt) "Meeting Timer shows the medicine card; this app steps in if it doesn't within 2 minutes." else "Meeting Timer is not installed on this phone.") { on ->
                if (mt) { app.settings.update { it.copy(useMeetingTimer = on) }; scope.launch { Scheduler.reschedule(ctx) } }
            }
            if (mt) BigButton("Open Meeting Timer", tone = Tone.SECONDARY, onClick = { CalendarSync.openMeetingTimer(ctx) })
        }
        "care" -> Screen("Looking after you", "Morning check-in, fall detection and more.", onHome = { nav.home() }, onBack = { section = null }) {
            Toggle("Morning check-in", s.checkInEnabled, "Asks how you are each morning. If you don't answer in 2 hours, your helpers are told.") { on -> app.settings.update { it.copy(checkInEnabled = on) }; scope.launch { Scheduler.reschedule(ctx) } }
            if (s.checkInEnabled) run { val o = listOf("08:00", "09:00", "10:00", "11:00"); com.suryaprakash.medlog.ui.Segmented(o, o.indexOf(s.checkInTime)) { i -> app.settings.update { it.copy(checkInTime = o[i]) }; scope.launch { Scheduler.reschedule(ctx) } } }
            Toggle("Fall detection", s.fallDetection, "Asks \"Did you fall?\" after a hard fall, then starts SOS if you don't answer. Uses more battery. Can be wrong.") { on -> app.settings.update { it.copy(fallDetection = on) }; FallService.sync(ctx) }
            Toggle("Sunday summary", s.weeklySummary, "A short spoken summary of your week.") { on -> app.settings.update { it.copy(weeklySummary = on) } }
            Toggle("Note things from the lock screen", s.persistentNotification, "A Speak button in your notifications. Works without unlocking.") { on -> app.settings.update { it.copy(persistentNotification = on) }; QuickNotification.sync(ctx) }
            Toggle("I have diabetes", s.diabetic, "Shows sugar readings next to meals.") { on -> app.settings.update { it.copy(diabetic = on) } }
            Body("Glasses of water a day", bold = true)
            run { val o = listOf(6, 8, 10); com.suryaprakash.medlog.ui.Segmented(o.map { "$it" }, o.indexOf(s.waterGoal)) { i -> app.settings.update { it.copy(waterGoal = o[i]) } } }
        }
        "sos" -> Screen("SOS", "What happens when you press SOS.", onHome = { nav.home() }, onBack = { section = null }) {
            Card { Body("When you press SOS: a message with where you are goes to your helpers, their MedLog phones ring (nearby or far away), then MedLog calls each helper in turn, then ${s.emergencyNumber}.") }
            BigField("Emergency number", s.emergencyNumber, { v -> app.settings.update { it.copy(emergencyNumber = v.filter { c -> c.isDigit() }.take(4)) } }, keyboard = KeyboardType.Phone, hint = "India: 108 ambulance, 112 all emergencies")
            Body("Seconds before SOS starts", bold = true)
            run { val o = listOf(5, 10, 15); com.suryaprakash.medlog.ui.Segmented(o.map { "$it sec" }, o.indexOf(s.sosCountdown)) { i -> app.settings.update { it.copy(sosCountdown = o[i]) } } }
            Title("Helper phones far away")
            Toggle("Reach helper phones over the internet", s.internetLink, "When a helper is out of Bluetooth range, alerts go through the internet. They are locked so only their phone can read them. Your notes never go.") { on -> app.settings.update { it.copy(internetLink = on) } }
            if (s.internetLink) {
                var relay by remember { mutableStateOf(s.relayUrl) }
                BigField("Relay address (optional)", relay, { relay = it.trim() }, hint = "Leave empty to use ${Relay.DEFAULT_URL}. If you change it, pair helper phones again.")
                if (relay != s.relayUrl) BigButton("Done", tone = Tone.QUIET, enabled = relay.isEmpty() || relay.startsWith("https://"), onClick = { app.settings.update { it.copy(relayUrl = relay) } })
            }
            Title("WhatsApp group call (extra)")
            Hint("Optional. MedLog opens your family SOS group and presses the call button. It needs internet and can stop working when WhatsApp changes, so phone calls and SMS always follow.")
            BigField("Family group invite link", s.whatsappGroupLink, { v -> app.settings.update { it.copy(whatsappGroupLink = v.trim()) } }, hint = "In WhatsApp: group info → Invite via link → Copy link")
            val enabled = WhatsAppCallService.isEnabled(ctx)
            Toggle("Use WhatsApp group call in SOS", s.whatsappSos, if (enabled) "Ready" else "Needs the MedLog SOS helper turned on in Accessibility") { on -> app.settings.update { it.copy(whatsappSos = on) } }
            if (s.whatsappSos && !enabled) BigButton("Turn on in Accessibility", tone = Tone.QUIET, onClick = { Perms.open(ctx, Intent(AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS)) })
        }
        "lang" -> LanguagesSection(onBack = { section = null }, onHome = { nav.home() })
        "update" -> Screen("Updates", "Check for a new version and install it.", onHome = { nav.home() }, onBack = { section = null }) {
            UpdateCard(auto = true)
            Hint("You're on version ${com.suryaprakash.medlog.BuildConfig.VERSION_NAME}. It also checks once a day by itself.")
        }
        "helperlock" -> Screen("Helper controls", "Things a helper can set.", onHome = { nav.home() }, onBack = { section = null }) {
            Body("Hide what isn't needed", bold = true)
            listOf("meds" to "Medicines", "food" to "Food & water", "readings" to "BP, sugar & more", "reports" to "How am I doing", "doctor" to "For doctor", "help" to "Help").forEach { (k, l) ->
                Toggle("Show $l", k !in s.hidden) { on -> app.settings.update { it.copy(hidden = if (on) it.hidden - k else it.hidden + k) } }
            }
            var newPin by remember { mutableStateOf("") }
            BigField("Helper PIN (locks Settings)", newPin, { newPin = it.filter(Char::isDigit).take(6) }, keyboard = KeyboardType.NumberPassword, hint = if (s.helperPin.isBlank()) "No PIN set" else "A PIN is set")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BigButton("Set PIN", Modifier.weight(1f), enabled = newPin.length >= 4, onClick = { app.settings.update { it.copy(helperPin = newPin) }; newPin = "" })
                BigButton("Remove PIN", Modifier.weight(1f), Tone.SECONDARY, enabled = s.helperPin.isNotBlank(), onClick = { app.settings.update { it.copy(helperPin = "") } })
            }
        }
        "doctors" -> DoctorsSection(onBack = { section = null })
        else -> Screen("Settings", "Choose what to change.", onHome = { nav.home() }) {
            val helpers by app.db.helpers().flow().collectAsState(emptyList())
            val plan = com.suryaprakash.medlog.data.CarePlan.parse(profile.plan)
            fun langs() = s.languages.joinToString(", ") { t -> com.suryaprakash.medlog.clinical.Lang.ALL.firstOrNull { it.tag == t }?.name ?: t }
            com.suryaprakash.medlog.ui.Section("You")
            com.suryaprakash.medlog.ui.Group {
                com.suryaprakash.medlog.ui.ValueRow("My details", profile.name.ifBlank { null }) { section = "me" }
                com.suryaprakash.medlog.ui.GroupLine()
                com.suryaprakash.medlog.ui.ValueRow("My doctors", plan.doctors.size.takeIf { it > 0 }?.toString()) { section = "doctors" }
                com.suryaprakash.medlog.ui.GroupLine()
                com.suryaprakash.medlog.ui.ValueRow("My helpers", helpers.size.takeIf { it > 0 }?.toString()) { nav.go(Route.Helpers) }
                com.suryaprakash.medlog.ui.GroupLine()
                com.suryaprakash.medlog.ui.ValueRow("My messages", s.messages.size.takeIf { it > 0 }?.toString()) { nav.go(Route.Messages) }
            }
            com.suryaprakash.medlog.ui.Section("Seeing and hearing")
            com.suryaprakash.medlog.ui.Group {
                com.suryaprakash.medlog.ui.ValueRow("Text size", if (s.bigMode) "Large" else "Regular") { nav.go(Route.EasySettings) }
                com.suryaprakash.medlog.ui.GroupLine()
                com.suryaprakash.medlog.ui.ValueRow("Read aloud", when { !s.readAloud -> "Off"; s.autoRead -> "Every page"; else -> "When I tap" }) { nav.go(Route.EasySettings) }
                com.suryaprakash.medlog.ui.GroupLine()
                com.suryaprakash.medlog.ui.ValueRow("Languages", langs()) { section = "lang" }
            }
            com.suryaprakash.medlog.ui.Section("Care")
            com.suryaprakash.medlog.ui.Group {
                com.suryaprakash.medlog.ui.ValueRow("Medicine reminders", "Again in ${s.snoozeMinutes} min") { section = "reminders" }
                com.suryaprakash.medlog.ui.GroupLine()
                com.suryaprakash.medlog.ui.ValueRow("Daily check-in", if (s.checkInEnabled) s.checkInTime else "Off") { section = "care" }
                com.suryaprakash.medlog.ui.GroupLine()
                com.suryaprakash.medlog.ui.ValueRow("SOS", "Calls ${s.emergencyNumber} last") { section = "sos" }
            }
            ConnectionSettings(nav)
            SharingSettings()
            com.suryaprakash.medlog.ui.Section("This phone")
            com.suryaprakash.medlog.ui.Group {
                com.suryaprakash.medlog.ui.ValueRow("Home-screen widget", "Add") { pinWidget(ctx) }
                com.suryaprakash.medlog.ui.GroupLine()
                com.suryaprakash.medlog.ui.ValueRow("Permissions", null) { nav.go(Route.Permissions) }
                com.suryaprakash.medlog.ui.GroupLine()
                com.suryaprakash.medlog.ui.ValueRow("Backup and new phone", null) { nav.go(Route.Backup) }
                if (com.suryaprakash.medlog.Updater.allowed(ctx)) {
                    com.suryaprakash.medlog.ui.GroupLine()
                    com.suryaprakash.medlog.ui.ValueRow("Updates", com.suryaprakash.medlog.BuildConfig.VERSION_NAME) { section = "update" }
                }
            }
            com.suryaprakash.medlog.ui.Section("More")
            com.suryaprakash.medlog.ui.Group {
                com.suryaprakash.medlog.ui.ValueRow("Add my old reports", null, sub = "Photos or PDFs") { nav.go(Route.Import) }
                com.suryaprakash.medlog.ui.GroupLine()
                com.suryaprakash.medlog.ui.ValueRow("Bluetooth machines", null, sub = "BP, oxygen, thermometer, scale") { nav.go(Route.Devices) }
                com.suryaprakash.medlog.ui.GroupLine()
                com.suryaprakash.medlog.ui.ValueRow("Helper controls", if (s.helperPin.isNotBlank()) "PIN set" else null, sub = "Hide features, lock settings") { section = "helperlock" }
                com.suryaprakash.medlog.ui.GroupLine()
                com.suryaprakash.medlog.ui.ValueRow("Privacy", null) { nav.go(Route.Privacy) }
            }
            Hint("Version ${com.suryaprakash.medlog.BuildConfig.VERSION_NAME} · clinical content ${app.catalogue.version}${if (!app.catalogue.reviewed) " (not yet doctor-reviewed)" else ""}")
        }
    }
}

/** The person's doctors, each with what they treat, so the right one is offered to call. */
@Composable
private fun DoctorsSection(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val app = ctx.medlog
    val scope = rememberCoroutineScope()
    var plan by remember { mutableStateOf(com.suryaprakash.medlog.data.CarePlan()) }
    LaunchedEffect(Unit) { plan = com.suryaprakash.medlog.data.CarePlan.parse(app.repo.profile().plan) }
    fun save(list: List<com.suryaprakash.medlog.data.CarePlan.Doctor>) { plan = plan.copy(doctors = list); scope.launch { app.repo.saveCarePlan { it.copy(doctors = list) } } }
    var editing by remember { mutableStateOf<Int?>(null) }
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var spec by remember { mutableStateOf("Family doctor") }
    var hospital by remember { mutableStateOf("") }
    val pick = com.suryaprakash.medlog.ui.rememberContactPicker { n, ph -> if (name.isBlank()) name = n; phone = ph }
    val e = editing
    if (e != null) {
        com.suryaprakash.medlog.ui.FlowScreen("Doctor", if (e < 0) "Add a doctor" else "Change ${name.ifBlank { "doctor" }}", onBack = { editing = null },
            primary = "Done", primaryEnabled = name.isNotBlank(), onPrimary = {
                val d = com.suryaprakash.medlog.data.CarePlan.Doctor(name.trim(), spec, phone.trim(), hospital.trim())
                save(if (e < 0) plan.doctors + d else plan.doctors.mapIndexed { i, x -> if (i == e) d else x }); editing = null
            }, secondary = if (e >= 0) "Remove this doctor" else null, onSecondary = { save(plan.doctors.filterIndexed { i, _ -> i != e }); editing = null }) {
            BigField("Doctor's name", name, { name = it }, hint = "For example: Dr. Rao")
            BigField("Hospital or clinic", hospital, { hospital = it }, hint = "For example: City Hospital")
            com.suryaprakash.medlog.ui.Section("What do they treat?")
            com.suryaprakash.medlog.ui.FlowRowOf { com.suryaprakash.medlog.data.CarePlan.SPECIALITIES.forEach { sp -> com.suryaprakash.medlog.ui.Chip(sp, spec == sp) { spec = sp } } }
            com.suryaprakash.medlog.ui.Section("Phone number")
            BigButton("Choose from contacts", tone = Tone.SECONDARY, onClick = pick)
            BigField("Mobile number", phone, { phone = it }, keyboard = KeyboardType.Phone)
        }
        return
    }
    Screen("My doctors", "Your doctors and what they treat.", onHome = null, onBack = onBack, actions = {
        BigButton("Add a doctor", onClick = { name = ""; phone = ""; spec = "Family doctor"; hospital = ""; editing = -1 })
    }) {
        if (plan.doctors.isEmpty()) Hint("No doctors yet. Add each doctor with what they treat, so the right one is offered to call.")
        else com.suryaprakash.medlog.ui.Group {
            plan.doctors.forEachIndexed { i, d ->
                if (i > 0) com.suryaprakash.medlog.ui.GroupLine()
                com.suryaprakash.medlog.ui.ValueRow(d.name, d.speciality, sub = listOf(d.hospital, d.phone).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { null }) { name = d.name; phone = d.phone; spec = d.speciality; hospital = d.hospital; editing = i }
            }
        }
    }
}

@Composable
private fun CalendarPicker() {
    val ctx = LocalContext.current
    val app = ctx.medlog
    val s = LocalSettings.current
    val scope = rememberCoroutineScope()
    var granted by remember { mutableStateOf(Perms.has(ctx, *Perms.CALENDAR)) }
    val ask = rememberPermissionAsker { granted = it }
    if (!granted) {
        Hint("Put medicine times into your Google Calendar. Android syncs them; this app itself never sends them online.")
        BigButton("Allow calendar", tone = Tone.QUIET, onClick = { ask(Perms.CALENDAR) })
        return
    }
    val cals = remember { CalendarSync.calendars(ctx) }
    if (cals.isEmpty()) { Hint("No calendar found. Add your Google account to this phone first."); return }
    FlowRowOf {
        Chip("Off", s.calendarId <= 0) { app.settings.update { it.copy(calendarId = -1) }; scope.launch { app.db.medicines().all().forEach { CalendarSync.removeMedicine(ctx, it) } } }
        cals.forEach { c -> Chip("${c.name}${if (c.google) "" else " (phone only)"}", s.calendarId == c.id) { app.settings.update { it.copy(calendarId = c.id) }; scope.launch { CalendarSync.syncAll(ctx) } } }
    }
    Toggle("Plain titles (\"Medicine time\")", s.calendarNeutralTitles, "Keeps medicine names off Google's servers.") { on -> app.settings.update { it.copy(calendarNeutralTitles = on) }; scope.launch { CalendarSync.syncAll(ctx) } }
}

@Composable
fun EasySettingsScreen(nav: Nav) {
    val ctx = LocalContext.current
    val app = ctx.medlog
    val s = LocalSettings.current
    val speakerOk by app.speaker.available.collectAsState()
    fun set(f: (com.suryaprakash.medlog.data.Settings) -> com.suryaprakash.medlog.data.Settings) = app.settings.update(f)
    Screen("Seeing and hearing", "Change how it looks and sounds.", onHome = { nav.home() }, onBack = { nav.back() }) {
        com.suryaprakash.medlog.ui.Section("Text size")
        com.suryaprakash.medlog.ui.Choice("Regular", !s.bigMode) { set { it.copy(bigMode = false) } }
        com.suryaprakash.medlog.ui.Choice("Large", s.bigMode) { set { it.copy(bigMode = true) } }
        com.suryaprakash.medlog.ui.Section("Read aloud")
        com.suryaprakash.medlog.ui.Choice("Read every page", s.readAloud && s.autoRead) { set { it.copy(readAloud = true, autoRead = true) } }
        com.suryaprakash.medlog.ui.Choice("Only when I tap Read aloud", s.readAloud && !s.autoRead) { set { it.copy(readAloud = true, autoRead = false) } }
        com.suryaprakash.medlog.ui.Choice("Never", !s.readAloud) { app.speaker.stop(); set { it.copy(readAloud = false, autoRead = false) } }
        com.suryaprakash.medlog.ui.Section("More")
        Body("Reading speed", bold = true)
        run { val o = listOf(0.55f to "Very slow", 0.7f to "Slow", 0.85f to "Normal"); com.suryaprakash.medlog.ui.Segmented(o.map { it.second }, o.indexOfFirst { it.first == s.speechRate }) { i -> set { it.copy(speechRate = o[i].first) }; app.speaker.say("This is how I will sound.") } }
        if (!speakerOk) Card { Body("This phone has no reading voice. Tap to add one."); BigButton("Add a voice", tone = Tone.QUIET, onClick = { Perms.open(ctx, Intent("com.android.settings.TTS_SETTINGS")) }) }
        Toggle("High contrast", s.highContrast) { v -> set { it.copy(highContrast = v) } }
        Toggle("Bold text", s.boldText) { v -> set { it.copy(boldText = v) } }
        Toggle("Steady touch", s.steadyTouch, "For shaky hands: ignores double taps") { v -> set { it.copy(steadyTouch = v) } }
        Toggle("Touch to hear", s.touchToHear, "First tap reads the button, second tap presses it") { v -> set { it.copy(touchToHear = v) } }
        Toggle("Left-hand mode", s.leftHand, "Puts the bottom buttons in the opposite order") { v -> set { it.copy(leftHand = v) } }
        Toggle("Less movement", s.lessMotion) { v -> set { it.copy(lessMotion = v) } }
        Toggle("Flash the screen for alerts", s.flashAlerts, "For hard of hearing") { v -> set { it.copy(flashAlerts = v) } }
    }
}

/** Plain list of permissions with one Allow button each, plus the reliability settings (plan 16). */
@Composable
fun PermissionList(role: String) {
    val ctx = LocalContext.current
    val p = LocalPalette.current
    var tick by remember { mutableStateOf(0) }
    val ask = rememberPermissionAsker { tick++ }
    androidx.compose.runtime.key(tick) {
        Perms.list(role).forEach { item ->
            val ok = Perms.has(ctx, *item.perms)
            Card(color = if (ok) p.okSoft else p.card) {
                Text((if (ok) "✓ " else "") + item.title, fontWeight = FontWeight.Bold, color = p.ink, fontSize = LocalScale.current.body)
                Hint(item.why)
                if (!ok) BigButton("Allow", tone = Tone.QUIET, onClick = { ask(item.perms) })
            }
        }
        if (role == "self") {
            val alarms = Perms.exactAlarmsOk(ctx)
            Card(color = if (alarms) p.okSoft else p.card) {
                Text((if (alarms) "✓ " else "") + "Remind at the exact time", fontWeight = FontWeight.Bold, color = p.ink)
                if (!alarms) BigButton("Allow", tone = Tone.QUIET, onClick = { Perms.openExactAlarms(ctx) })
            }
            val fs = Perms.fullScreenOk(ctx)
            Card(color = if (fs) p.okSoft else p.card) {
                Text((if (fs) "✓ " else "") + "Show reminders on the lock screen", fontWeight = FontWeight.Bold, color = p.ink)
                if (!fs) BigButton("Allow", tone = Tone.QUIET, onClick = { Perms.openFullScreen(ctx) })
            }
        }
        val bat = Perms.batteryOk(ctx)
        Card(color = if (bat) p.okSoft else p.card) {
            Text((if (bat) "✓ " else "") + "Keep working when the phone sleeps", fontWeight = FontWeight.Bold, color = p.ink)
            Hint("So reminders and alerts are never stopped by battery saving.")
            if (!bat) BigButton("Allow", tone = Tone.QUIET, onClick = { Perms.openBattery(ctx) })
        }
        Perms.makerGuide()?.let { (text, intents) ->
            Card(border = p.amber) {
                Body(text)
                if (intents.isNotEmpty()) BigButton("Open phone settings", tone = Tone.QUIET, onClick = {
                    val ok = intents.any { i -> runCatching { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess }
                    if (!ok) Perms.openAppSettings(ctx)
                })
            }
        }
    }
}

@Composable
fun PermissionsScreen(nav: Nav) {
    val s = LocalSettings.current
    Screen("Permissions", "These are needed to remind you and get help. Tap Allow on each one that isn't ticked.", onHome = { nav.home() }, onBack = { nav.back() }) {
        PermissionList(s.role)
    }
}

@Composable
fun BackupScreen(nav: Nav) {
    val ctx = LocalContext.current
    val app = ctx.medlog
    val p = LocalPalette.current
    val scope = rememberCoroutineScope()
    var password by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri: Uri? ->
        if (uri != null) scope.launch { status = runCatching { Backup.export(ctx, uri, password); "Backup saved." }.getOrElse { "Backup failed: ${it.message}" } }
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch { status = runCatching { Backup.import(ctx, uri, password); Scheduler.reschedule(ctx); "Restored. Everything is back." }.getOrElse { "Could not restore. Check the password." } }
    }
    val saveSetup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri != null) scope.launch { status = runCatching { com.suryaprakash.medlog.data.SetupFile.export(ctx, uri); "Setup file saved." }.getOrElse { "Could not save: ${it.message}" } }
    }
    val loadSetup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch { status = runCatching { com.suryaprakash.medlog.data.SetupFile.import(ctx, uri); "Setup loaded." }.getOrElse { "That file isn't a setup file." } }
    }
    Screen("Backup and new phone", "Save a locked copy of everything, or bring it back on a new phone.", onHome = { nav.home() }, onBack = { nav.back() }) {
        Title("Setup file")
        Body("Your details, helpers, languages, messages and settings, in one file. Load it on a new phone to skip the setup questions. It has no notes or readings.")
        BigButton("Keep a setup file", onClick = { saveSetup.launch("MedLog-setup-${java.time.LocalDate.now()}.json") })
        BigButton("Load setup file", tone = Tone.SECONDARY, onClick = { loadSetup.launch(arrayOf("application/json", "application/octet-stream", "*/*")) })
        Hint("Keep the file private: it has your name, illnesses and helpers' numbers.")
        Title("Full backup")
        Body("The backup is locked with a password. Keep the password safe; without it the backup can't be opened.")
        BigField("Backup password", password, { password = it }, keyboard = KeyboardType.Password, hint = "At least 6 letters or numbers")
        BigButton("Make a backup copy", enabled = password.length >= 6, onClick = { save.launch("MedLog-backup-${java.time.LocalDate.now()}.medlog") })
        BigButton("Restore a backup", tone = Tone.SECONDARY, enabled = password.length >= 6, onClick = { open.launch(arrayOf("*/*")) })
        status?.let { Card() { Body(it, bold = true) } }
        Hint("You choose where the file goes: this phone, an SD card, a computer, or your own Drive.")
    }
}

/** New version: check, download, install. On Settings → Updates, and on the home screen when one is ready. */
/**
 * Tells about a new version once in the phone's lifetime (owner: "once in lifetime for a user, never shown again"), as a
 * bottom sheet on Home. After that, new versions are only under Settings → Updates.
 */
@Composable
fun UpdateSheetOnce() {
    val ctx = LocalContext.current
    val app = ctx.medlog
    val p = LocalPalette.current
    val sc = LocalScale.current
    val update by com.suryaprakash.medlog.Updater.state.collectAsState()
    var seen by remember { mutableStateOf(UpdateSheet.seen(app.settings.getString(UpdateSheet.KEY))) }
    val show = !seen && update is com.suryaprakash.medlog.Updater.State.Available
    LaunchedEffect(show) { if (show) app.settings.putString(UpdateSheet.KEY, "1") }   // shown = never again, whatever is tapped
    if (!show) return
    com.suryaprakash.medlog.ui.AppSheet(onDismissRequest = { seen = true }, containerColor = p.paper) {
        androidx.compose.foundation.layout.Column(Modifier.fillMaxWidth().padding(horizontal = sc.margin).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("A new version is ready", fontSize = sc.headline, fontWeight = FontWeight.Bold, color = p.ink)
            UpdateCard()
            Hint("From now on, new versions wait in Settings → Updates.")
            BigButton("Later", tone = Tone.SECONDARY, onClick = { seen = true })
        }
    }
}

/** The once-in-a-lifetime rule for the update sheet, kept pure for its test. */
object UpdateSheet {
    const val KEY = "update_sheet_seen"
    fun seen(stored: String?) = stored == "1"
}

@Composable
fun UpdateCard(auto: Boolean = false) {
    val ctx = LocalContext.current
    val p = LocalPalette.current
    val scope = rememberCoroutineScope()
    val st by com.suryaprakash.medlog.Updater.state.collectAsState()
    var canInstall by remember { mutableStateOf(com.suryaprakash.medlog.Updater.canInstall(ctx)) }
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) { canInstall = com.suryaprakash.medlog.Updater.canInstall(ctx); onPauseOrDispose {} }
    // opening Updates always asks again (an earlier "up to date" may be old), unless a download is under way
    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (auto && st !is com.suryaprakash.medlog.Updater.State.Downloading && st !is com.suryaprakash.medlog.Updater.State.Installing) com.suryaprakash.medlog.Updater.check(ctx)
    }
    when (val s = st) {
        is com.suryaprakash.medlog.Updater.State.Available -> Card(border = p.ok) {
            Body("A new version is ready: ${s.release.name}", bold = true)
            if (s.release.notes.isNotBlank()) Body(s.release.notes)
            if (!canInstall) {
                Body("First, allow updates to install. Turn on the switch, then come back.")
                BigButton("Allow updates", tone = Tone.QUIET, onClick = { com.suryaprakash.medlog.Updater.openInstallPermission(ctx) })
            } else BigButton("Update now", tone = Tone.PRIMARY, onClick = { scope.launch { com.suryaprakash.medlog.Updater.install(ctx, s.release) } })
            Hint("Your notes and settings stay. Android will ask you to confirm.")
        }
        is com.suryaprakash.medlog.Updater.State.Downloading -> Card() { Body("Downloading… ${s.percent}%", bold = true) }
        com.suryaprakash.medlog.Updater.State.Installing -> Card() { Body("Installing… Tap Update when Android asks.", bold = true) }
        com.suryaprakash.medlog.Updater.State.Checking -> Card { Body("Checking…") }
        com.suryaprakash.medlog.Updater.State.UpToDate -> Card(border = p.ok) {
            Body("You have the newest version.", bold = true)
            BigButton("Check again", tone = Tone.SECONDARY, onClick = { scope.launch { com.suryaprakash.medlog.Updater.check(ctx) } })
        }
        is com.suryaprakash.medlog.Updater.State.Failed -> Card(border = p.amber) {
            Body(s.why, bold = true)
            BigButton("Try again", tone = Tone.QUIET, onClick = { scope.launch { com.suryaprakash.medlog.Updater.check(ctx) } })
        }
        com.suryaprakash.medlog.Updater.State.Idle -> if (auto) BigButton("Check for updates", onClick = { scope.launch { com.suryaprakash.medlog.Updater.check(ctx) } })
    }
}

@Composable
fun PrivacyScreen(nav: Nav) {
    val ctx = LocalContext.current
    val app = ctx.medlog
    val p = LocalPalette.current
    val scope = rememberCoroutineScope()
    var confirm by remember { mutableStateOf(false) }
    Screen("Privacy", "Your notes stay on this phone. MedLog uses the internet only to pass locked alerts to your helpers' phones.", onHome = { nav.home() }, onBack = { nav.back() }) {
        Card() {
            Body("Your notes stay on this phone.", bold = true)
            Body("MedLog uses the internet for one thing only: passing help alerts to your helpers' phones when they are far away. Each alert is locked with a key only their phone has. You can turn this off in Settings → SOS.")
        }
        Body("Things leave the phone only when you choose:")
        listOf("SOS and help messages: by SMS and phone calls to your helpers", "Helper phones: by Bluetooth nearby, or the internet far away, locked with a key", "Your doctor page: when you tap Share or Print", "Google Calendar: only if you turn it on", "WhatsApp: only if you turn it on for SOS").forEach { Body("• $it") }
        Body("Your notes are locked (encrypted) on the phone. No ads. No tracking.")
        BigButton("Open App info", tone = Tone.SECONDARY, onClick = { Perms.openAppSettings(ctx) })
        if (!confirm) BigButton("Remove everything", tone = Tone.SECONDARY, onClick = { confirm = true })
        else Card(border = p.red) {
            Body("This deletes all notes, medicines and helpers from this phone. It cannot be undone.", bold = true)
            BigButton("Yes, delete everything", tone = Tone.DANGER, onClick = {
                scope.launch {
                    app.db.clearAllTables()
                    java.io.File(ctx.filesDir, "audio").deleteRecursively(); java.io.File(ctx.filesDir, "photos").deleteRecursively()
                    app.settings.update { com.suryaprakash.medlog.data.Settings() }
                    Scheduler.reschedule(ctx)
                    nav.home(Route.Onboarding)
                }
            })
            BigButton("No, keep it", tone = Tone.PRIMARY, onClick = { confirm = false })
        }
    }
}

@Composable
fun HelperLockScreen(nav: Nav) = SettingsScreen(nav)


fun pinWidget(ctx: android.content.Context) {
    val hint = "Long-press the home screen, tap Widgets, and choose MedLog."
    if (android.os.Build.VERSION.SDK_INT < 26) { android.widget.Toast.makeText(ctx, hint, android.widget.Toast.LENGTH_LONG).show(); return }
    val m = ctx.getSystemService(android.appwidget.AppWidgetManager::class.java)
    val ok = m.isRequestPinAppWidgetSupported && m.requestPinAppWidget(android.content.ComponentName(ctx, com.suryaprakash.medlog.widget.MedLogWidgetReceiver::class.java), null, null)
    if (!ok) android.widget.Toast.makeText(ctx, hint, android.widget.Toast.LENGTH_LONG).show()
}

/** Which languages the person speaks and reads, and the voices for reading aloud. */
@Composable
private fun LanguagesSection(onBack: () -> Unit, onHome: () -> Unit) {
    val ctx = LocalContext.current
    val app = ctx.medlog
    val s = LocalSettings.current
    val p = LocalPalette.current
    val sc = LocalScale.current
    fun name(t: String) = com.suryaprakash.medlog.clinical.Lang.ALL.firstOrNull { it.tag == t }?.name ?: t
    Screen("Languages and voice", "Tap the languages you speak. The first one is your main language.", onHome = onHome, onBack = onBack) {
        Hint("Tap every language you speak.")
        com.suryaprakash.medlog.clinical.Lang.ALL.forEach { l ->
            val on = l.tag in s.languages
            val main = s.languages.firstOrNull() == l.tag
            Toggle("${l.name}  \u00b7  ${l.native}", on, if (main) "Main language" else null) { v ->
                app.settings.update { st ->
                    val list = if (v) st.languages + l.tag else st.languages - l.tag
                    st.copy(languages = list.ifEmpty { listOf("en-IN") })
                }
            }
        }
        if (s.languages.size > 1) {
            Body("Main language", bold = true)
            FlowRowOf { s.languages.forEach { t -> Chip(name(t), s.languages.first() == t) { app.settings.update { st -> st.copy(languages = listOf(t) + (st.languages - t)) } } } }
        }
        Title("Reading aloud")
        s.languages.forEach { t ->
            val has = app.speaker.hasVoice(t)
            androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(name(t), fontSize = sc.body, color = p.ink, modifier = androidx.compose.ui.Modifier.weight(1f))
                Text(if (has) "Voice ready" else "No voice", color = if (has) p.ok else p.inkSoft, fontSize = sc.small)
            }
        }
        BigButton("Add a reading voice", tone = Tone.SECONDARY, onClick = { Perms.open(ctx, android.content.Intent("com.android.settings.TTS_SETTINGS")) })
        Hint("The whole app shows and speaks in your main language.")
    }
}
