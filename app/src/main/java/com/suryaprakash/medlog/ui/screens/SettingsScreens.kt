package com.suryaprakash.medlog.ui.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.rounded.Widgets
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
        Screen("Settings", "Settings are locked by your helper. Easy mode can still be changed.", onHome = { nav.home() }, onBack = { nav.back() }) {
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
            BigField("Date of birth (YYYY-MM-DD)", profile.dob, { profile = profile.copy(dob = it) }, hint = "For example 1948-03-12")
            run { val o = listOf("F" to "Woman", "M" to "Man", "" to "Not said"); com.suryaprakash.medlog.ui.Segmented(o.map { it.second }, o.indexOfFirst { it.first == profile.sex }) { profile = profile.copy(sex = o[it].first) } }
            BigField("Blood group", profile.bloodGroup, { profile = profile.copy(bloodGroup = it) })
            BigField("Hospital ID", profile.hospitalId, { profile = profile.copy(hospitalId = it) })
            BigField("Illnesses", profile.conditions, { profile = profile.copy(conditions = it) }, lines = 2)
            BigField("Allergies", profile.allergies, { profile = profile.copy(allergies = it) }, lines = 2)
            Toggle("I take a blood thinner", profile.onBloodThinner) { profile = profile.copy(onBloodThinner = it) }
            BigField("Doctor's name", profile.doctorName, { profile = profile.copy(doctorName = it) })
            BigField("Doctor's phone", profile.doctorPhone, { profile = profile.copy(doctorPhone = it) }, keyboard = KeyboardType.Phone)
            BigButton("Save", tone = Tone.OK, onClick = { scope.launch { app.db.profile().put(profile); section = null } })
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
            Toggle("Show reminders in Meeting Timer", s.useMeetingTimer && mt, if (mt) "Meeting Timer shows the medicine card; MedLog steps in if it doesn't within 2 minutes." else "Meeting Timer is not installed on this phone.") { on ->
                if (mt) { app.settings.update { it.copy(useMeetingTimer = on) }; scope.launch { Scheduler.reschedule(ctx) } }
            }
            if (mt) BigButton("Open Meeting Timer", tone = Tone.SECONDARY, onClick = { CalendarSync.openMeetingTimer(ctx) })
        }
        "care" -> Screen("Looking after you", "Morning check-in, fall detection and more.", onHome = { nav.home() }, onBack = { section = null }) {
            Toggle("Morning check-in", s.checkInEnabled, "Asks how you are each morning. If you don't answer in 2 hours, your helpers are told.") { on -> app.settings.update { it.copy(checkInEnabled = on) }; scope.launch { Scheduler.reschedule(ctx) } }
            if (s.checkInEnabled) run { val o = listOf("08:00", "09:00", "10:00", "11:00"); com.suryaprakash.medlog.ui.Segmented(o, o.indexOf(s.checkInTime)) { i -> app.settings.update { it.copy(checkInTime = o[i]) }; scope.launch { Scheduler.reschedule(ctx) } } }
            Toggle("Fall detection", s.fallDetection, "Asks \"Did you fall?\" after a hard fall, then starts SOS if you don't answer. Uses more battery. Can be wrong.") { on -> app.settings.update { it.copy(fallDetection = on) }; FallService.sync(ctx) }
            Toggle("Sunday summary", s.weeklySummary, "A short spoken summary of your week.") { on -> app.settings.update { it.copy(weeklySummary = on) } }
            Toggle("Always-there buttons", s.persistentNotification, "Tell and Help buttons in your notifications, even on the lock screen.") { on -> app.settings.update { it.copy(persistentNotification = on) }; QuickNotification.sync(ctx) }
            Toggle("I have diabetes", s.diabetic, "Shows sugar readings next to meals.") { on -> app.settings.update { it.copy(diabetic = on) } }
            Body("Glasses of water a day", bold = true)
            run { val o = listOf(6, 8, 10); com.suryaprakash.medlog.ui.Segmented(o.map { "$it glasses" }, o.indexOf(s.waterGoal)) { i -> app.settings.update { it.copy(waterGoal = o[i]) } } }
            Body("Keep voice recordings for", bold = true)
            run { val o = listOf(0 to "Never", 30 to "30 days", 90 to "90 days"); com.suryaprakash.medlog.ui.Segmented(o.map { it.second }, o.indexOfFirst { it.first == s.keepAudioDays }) { i -> app.settings.update { it.copy(keepAudioDays = o[i].first) } } }
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
                if (relay != s.relayUrl) BigButton("Save address", tone = Tone.QUIET, enabled = relay.isEmpty() || relay.startsWith("https://"), onClick = { app.settings.update { it.copy(relayUrl = relay) } })
            }
            Title("WhatsApp group call (extra)")
            Hint("Optional. MedLog opens your family SOS group and presses the call button. It needs internet and can stop working when WhatsApp changes, so phone calls and SMS always follow.")
            BigField("Family group invite link", s.whatsappGroupLink, { v -> app.settings.update { it.copy(whatsappGroupLink = v.trim()) } }, hint = "In WhatsApp: group info → Invite via link → Copy link")
            val enabled = WhatsAppCallService.isEnabled(ctx)
            Toggle("Use WhatsApp group call in SOS", s.whatsappSos, if (enabled) "Ready" else "Needs the MedLog SOS helper turned on in Accessibility") { on -> app.settings.update { it.copy(whatsappSos = on) } }
            if (s.whatsappSos && !enabled) BigButton("Turn on in Accessibility", tone = Tone.QUIET, onClick = { Perms.open(ctx, Intent(AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS)) })
        }
        "lang" -> LanguagesSection(onBack = { section = null }, onHome = { nav.home() })
        "update" -> Screen("Updates", "Check for a new version of MedLog and install it.", onHome = { nav.home() }, onBack = { section = null }) {
            UpdateCard(auto = true)
            Hint("You're on MedLog ${com.suryaprakash.medlog.BuildConfig.VERSION_NAME}. MedLog also checks once a day by itself.")
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
        else -> Screen("Settings", "Choose what to change.", onHome = { nav.home() }, onBack = { nav.back() }) {
            val p = LocalPalette.current
            RoleSwitch(nav)
            ListRow("Easy mode and reading aloud", "Text size, voice, touch", Icons.Rounded.TextFields, p.tintTeal) { nav.go(Route.EasySettings) }
            ListRow("Languages and voice", s.languages.joinToString(", ") { t -> com.suryaprakash.medlog.clinical.Lang.ALL.firstOrNull { it.tag == t }?.name ?: t }, Icons.Rounded.Translate, p.tintPurple) { section = "lang" }
            ListRow("Add MedLog to home screen", "The quick buttons widget", Icons.Rounded.Widgets, p.tintGreen) { pinWidget(ctx) }
            ListRow("My details", "For the doctor page", Icons.Rounded.Person, p.tintBlue) { section = "me" }
            ListRow("My helpers", "Who to call and message", Icons.Rounded.Group, p.tintGreen) { nav.go(Route.Helpers) }
            ListRow("My messages", "What you can send to family", Icons.Rounded.Group, p.tintTeal) { nav.go(Route.Messages) }
            ListRow("Medicine reminders", "Google Calendar, Meeting Timer", Icons.Rounded.Tune, p.tintOrange) { section = "reminders" }
            ListRow("Looking after you", "Check-in, falls, summary", Icons.Rounded.Tune, p.tintPurple) { section = "care" }
            ListRow("SOS", "Emergency number, WhatsApp", Icons.Rounded.Tune, p.red) { section = "sos" }
            ListRow("Permissions", "Make reminders reliable", Icons.Rounded.Security, p.tintBlue) { nav.go(Route.Permissions) }
            ListRow("Add my old reports", "Photos or PDFs", Icons.Rounded.Description, p.tintTeal) { nav.go(Route.Import) }
            ListRow("Bluetooth machines", "BP, oxygen, thermometer, scale", Icons.Rounded.Bluetooth, p.tintBlue) { nav.go(Route.Devices) }
            ListRow("Backup and new phone", "Setup file, full backup", Icons.Rounded.Backup, p.tintGreen) { nav.go(Route.Backup) }
            ListRow("Helper controls", "Hide features, PIN", Icons.Rounded.Lock, p.tintPurple) { section = "helperlock" }
            if (com.suryaprakash.medlog.Updater.allowed(ctx)) ListRow("Check for updates", "Version ${com.suryaprakash.medlog.BuildConfig.VERSION_NAME}", Icons.Rounded.Backup, p.tintBlue) { section = "update" }
            ListRow("Privacy", "Your notes stay on this phone", Icons.Rounded.Security, p.tintTeal) { nav.go(Route.Privacy) }
            Hint("MedLog ${com.suryaprakash.medlog.BuildConfig.VERSION_NAME} · clinical content ${app.catalogue.version}${if (!app.catalogue.reviewed) " (not yet doctor-reviewed)" else ""}")
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
        Hint("Put medicine times into your Google Calendar. Android syncs them; MedLog itself never uses the internet.")
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
    Screen("Easy mode", "Change how MedLog looks and sounds.", onHome = { nav.home() }, onBack = { nav.back() }) {
        Toggle("Big text and buttons", s.bigMode) { v -> set { it.copy(bigMode = v) } }
        Toggle("Read screens aloud when they open", s.autoRead) { v -> set { it.copy(autoRead = v, easyMode = true) } }
        Body("Reading speed", bold = true)
        run { val o = listOf(0.55f to "Very slow", 0.7f to "Slow", 0.85f to "Normal"); com.suryaprakash.medlog.ui.Segmented(o.map { it.second }, o.indexOfFirst { it.first == s.speechRate }) { i -> set { it.copy(speechRate = o[i].first) }; app.speaker.say("This is how I will sound.") } }
        if (!speakerOk) Card { Body("This phone has no reading voice. Tap to add one."); BigButton("Add a voice", tone = Tone.QUIET, onClick = { Perms.open(ctx, Intent("com.android.settings.TTS_SETTINGS")) }) }
        Toggle("High contrast", s.highContrast) { v -> set { it.copy(highContrast = v) } }
        Toggle("Bold text", s.boldText) { v -> set { it.copy(boldText = v) } }
        Toggle("Steady touch", s.steadyTouch, "For shaky hands: ignores double taps") { v -> set { it.copy(steadyTouch = v) } }
        Toggle("Touch to hear", s.touchToHear, "First tap reads the button, second tap presses it") { v -> set { it.copy(touchToHear = v) } }
        Toggle("Left-hand mode", s.leftHand, "Moves the Home and Read aloud buttons") { v -> set { it.copy(leftHand = v) } }
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
            Card(color = p.amberSoft, border = p.amber) {
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
    Screen("Permissions", "MedLog needs these to remind you and get help. Tap Allow on each one that isn't ticked.", onHome = { nav.home() }, onBack = { nav.back() }) {
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
        if (uri != null) scope.launch { status = runCatching { com.suryaprakash.medlog.data.SetupFile.import(ctx, uri); "Setup loaded." }.getOrElse { "That file isn't a MedLog setup file." } }
    }
    Screen("Backup and new phone", "Save a locked copy of everything, or bring it back on a new phone.", onHome = { nav.home() }, onBack = { nav.back() }) {
        Title("Setup file")
        Body("Your details, helpers, languages, messages and settings, in one file. Load it on a new phone to skip the setup questions. It has no notes or readings.")
        BigButton("Save setup file", onClick = { saveSetup.launch("MedLog-setup-${java.time.LocalDate.now()}.json") })
        BigButton("Load setup file", tone = Tone.SECONDARY, onClick = { loadSetup.launch(arrayOf("application/json", "application/octet-stream", "*/*")) })
        Hint("Keep the file private: it has your name, illnesses and helpers' numbers.")
        Title("Full backup")
        Body("The backup is locked with a password. Keep the password safe; without it the backup can't be opened.")
        BigField("Backup password", password, { password = it }, keyboard = KeyboardType.Password, hint = "At least 6 letters or numbers")
        BigButton("Save a backup", enabled = password.length >= 6, onClick = { save.launch("MedLog-backup-${java.time.LocalDate.now()}.medlog") })
        BigButton("Restore a backup", tone = Tone.SECONDARY, enabled = password.length >= 6, onClick = { open.launch(arrayOf("*/*")) })
        status?.let { Card(color = p.brandSoft) { Body(it, bold = true) } }
        Hint("You choose where the file goes: this phone, an SD card, a computer, or your own Drive.")
    }
}

/** New version: check, download, install. On Settings → Updates, and on the home screen when one is ready. */
@Composable
fun UpdateCard(auto: Boolean = false) {
    val ctx = LocalContext.current
    val p = LocalPalette.current
    val scope = rememberCoroutineScope()
    val st by com.suryaprakash.medlog.Updater.state.collectAsState()
    var canInstall by remember { mutableStateOf(com.suryaprakash.medlog.Updater.canInstall(ctx)) }
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) { canInstall = com.suryaprakash.medlog.Updater.canInstall(ctx); onPauseOrDispose {} }
    androidx.compose.runtime.LaunchedEffect(Unit) { if (auto && st is com.suryaprakash.medlog.Updater.State.Idle) com.suryaprakash.medlog.Updater.check(ctx) }
    when (val s = st) {
        is com.suryaprakash.medlog.Updater.State.Available -> Card(color = p.okSoft) {
            Body("A new MedLog is ready: ${s.release.name}", bold = true)
            if (s.release.notes.isNotBlank()) Body(s.release.notes)
            if (!canInstall) {
                Body("First, let MedLog install updates. Turn on the switch, then come back.")
                BigButton("Allow updates", tone = Tone.QUIET, onClick = { com.suryaprakash.medlog.Updater.openInstallPermission(ctx) })
            } else BigButton("Update now", tone = Tone.OK, onClick = { scope.launch { com.suryaprakash.medlog.Updater.install(ctx, s.release) } })
            Hint("Your notes and settings stay. Android will ask you to confirm.")
        }
        is com.suryaprakash.medlog.Updater.State.Downloading -> Card(color = p.brandSoft) { Body("Downloading… ${s.percent}%", bold = true) }
        com.suryaprakash.medlog.Updater.State.Installing -> Card(color = p.brandSoft) { Body("Installing… Tap Update when Android asks.", bold = true) }
        com.suryaprakash.medlog.Updater.State.Checking -> Card { Body("Checking…") }
        com.suryaprakash.medlog.Updater.State.UpToDate -> Card(color = p.okSoft) { Body("MedLog is up to date.", bold = true) }
        is com.suryaprakash.medlog.Updater.State.Failed -> Card(color = p.amberSoft) {
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
        Card(color = p.brandSoft) {
            Body("Your notes stay on this phone.", bold = true)
            Body("MedLog uses the internet for one thing only: passing help alerts to your helpers' phones when they are far away. Each alert is locked with a key only their phone has. You can turn this off in Settings → SOS.")
        }
        Body("Things leave the phone only when you choose:")
        listOf("SOS and help messages: by SMS and phone calls to your helpers", "Helper phones: by Bluetooth nearby, or the internet far away, locked with a key", "Your doctor page: when you tap Share or Print", "Google Calendar: only if you turn it on", "WhatsApp: only if you turn it on for SOS").forEach { Body("• $it") }
        Body("Your notes are locked (encrypted) on the phone. No ads. No tracking.")
        BigButton("Open App info", tone = Tone.SECONDARY, onClick = { Perms.openAppSettings(ctx) })
        if (!confirm) BigButton("Delete everything", tone = Tone.SECONDARY, onClick = { confirm = true })
        else Card(color = p.redSoft, border = p.red) {
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
            BigButton("No, keep it", tone = Tone.OK, onClick = { confirm = false })
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

/** Which languages the person speaks. Speech in Indian languages uses the phone's own offline recogniser. */
@Composable
private fun LanguagesSection(onBack: () -> Unit, onHome: () -> Unit) {
    val ctx = LocalContext.current
    val app = ctx.medlog
    val s = LocalSettings.current
    val p = LocalPalette.current
    val sc = LocalScale.current
    val phone = app.listener.phone
    val phoneOk = phone.available()
    var support by remember { mutableStateOf<com.suryaprakash.medlog.speech.PhoneRecognizer.Support?>(null) }
    var tick by remember { mutableStateOf(0) }
    androidx.compose.runtime.LaunchedEffect(tick) { if (phoneOk && android.os.Build.VERSION.SDK_INT >= 33) phone.checkSupport { support = it } }
    fun name(t: String) = com.suryaprakash.medlog.clinical.Lang.ALL.firstOrNull { it.tag == t }?.name ?: t
    Screen("Languages and voice", "Tap the languages you speak. The first one is your main language.", onHome = onHome, onBack = onBack) {
        Hint("Tap every language you speak. MedLog listens for all of them.")
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
        Title("Understanding your voice")
        if (!phoneOk) Card(color = p.amberSoft) {
            Body("This phone has no speech service, so MedLog uses its own small English model. Please speak clearly in English, or tap the answers.", bold = true)
        } else {
            Card {
                Body("MedLog uses your phone's own speech, the same as the keyboard microphone.")
                Hint(if (android.os.Build.VERSION.SDK_INT >= 34 && s.languages.size > 1) "It will notice which of your languages you are speaking." else "It listens in your main language.")
            }
            Toggle("Use the internet if needed", s.voiceOnline, "Only when your phone can't understand a language offline. Your words then go to your phone's speech service (Google or Samsung) to be written down.") { on -> app.settings.update { it.copy(voiceOnline = on) } }
            if (phone.onDevice()) s.languages.forEach { t ->
                val sp = support
                val installed = sp?.installed?.any { it.equals(t, true) } == true
                val pending = sp?.pending?.any { it.equals(t, true) } == true
                Card {
                    androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text(name(t), fontSize = sc.body, fontWeight = FontWeight.SemiBold, color = p.ink, modifier = androidx.compose.ui.Modifier.weight(1f))
                        Text(when { installed -> "Works offline"; pending -> "Downloading"; sp == null -> ""; else -> "Needs internet" }, color = if (installed) p.ok else p.inkSoft, fontSize = sc.small)
                    }
                    if (!installed && sp != null && android.os.Build.VERSION.SDK_INT >= 33) BigButton("Download ${name(t)} for offline", tone = Tone.QUIET, onClick = { phone.download(t); tick++ })
                }
            }
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
