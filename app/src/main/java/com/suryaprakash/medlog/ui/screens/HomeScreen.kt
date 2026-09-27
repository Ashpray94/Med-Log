package com.suryaprakash.medlog.ui.screens
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material.icons.rounded.Wc

import com.suryaprakash.medlog.ui.cardTitle
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.ui.draw.drawBehind
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Sos
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import com.suryaprakash.medlog.ui.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.suryaprakash.medlog.data.Dose
import com.suryaprakash.medlog.data.Medicine
import com.suryaprakash.medlog.data.Repo
import com.suryaprakash.medlog.medlog
import com.suryaprakash.medlog.meds.DoseActivity
import com.suryaprakash.medlog.meds.Scheduler
import com.suryaprakash.medlog.pictogram.SpriteIcon
import com.suryaprakash.medlog.ui.BigButton
import com.suryaprakash.medlog.ui.Card
import com.suryaprakash.medlog.ui.Hint
import com.suryaprakash.medlog.ui.IconTile
import com.suryaprakash.medlog.ui.LocalPalette
import com.suryaprakash.medlog.ui.LocalScale
import com.suryaprakash.medlog.ui.LocalSettings
import com.suryaprakash.medlog.ui.Nav
import com.suryaprakash.medlog.ui.Perms
import com.suryaprakash.medlog.ui.Route
import com.suryaprakash.medlog.ui.RoundIcon
import com.suryaprakash.medlog.ui.Screen
import com.suryaprakash.medlog.ui.Tile
import com.suryaprakash.medlog.ui.TileGrid
import com.suryaprakash.medlog.ui.Title
import com.suryaprakash.medlog.ui.Tone
import com.suryaprakash.medlog.ui.savedFeedback
import com.suryaprakash.medlog.ui.steady
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.LocalTime
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(nav: Nav) {
    val ctx = LocalContext.current
    val app = ctx.medlog
    val p = LocalPalette.current
    val sc = LocalScale.current
    val s = LocalSettings.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var recent by remember { mutableStateOf<List<Repo.Recent>>(emptyList()) }
    var next by remember { mutableStateOf<Pair<Dose, Medicine>?>(null) }
    var todays by remember { mutableStateOf<List<Pair<Dose, Medicine>>>(emptyList()) }
    var askBetter by remember { mutableStateOf<String?>(null) }
    var remindersBlocked by remember { mutableStateOf(false) }
    var version by remember { mutableStateOf(0) }

    LaunchedEffect(version) {
        name = app.repo.profile().name
        recent = app.repo.recentProblems(6).filter { app.catalogue.problem(it.problemId) != null }.take(3)
        next = Scheduler.nextDose(ctx)
        val (from, to) = Scheduler.today()
        val meds = app.db.medicines().all().associateBy { it.id }
        todays = app.db.doses().between(from, to).mapNotNull { d -> meds[d.medicineId]?.let { d to it } }.filter { it.second.form != "feed" }
        val now = System.currentTimeMillis()
        askBetter = recent.firstOrNull { it.ongoing && now - it.lastAt > 20 * 3600_000L && app.settings.getString("asked_better_${it.problemId}") != java.time.LocalDate.now().toString() }?.problemId
        remindersBlocked = !Perms.exactAlarmsOk(ctx) || !Perms.has(ctx, *Perms.NOTIFY)
    }
    val hour = LocalTime.now().hour
    val greeting = when { hour < 12 -> "Good morning"; hour < 17 -> "Good afternoon"; else -> "Good evening" }
    val first = name.split(" ").first()
    val hello = if (first.isNotBlank()) "$greeting, $first" else greeting
    val today = SimpleDateFormat("EEEE, d MMMM", com.suryaprakash.medlog.speech.I18n.locale).format(Date())
    var unshared by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(version, recent) {
        val peers = com.suryaprakash.medlog.data.Sync.peers(ctx).filter { it.dir == com.suryaprakash.medlog.help.Relay.DOWN }
        val last = listOfNotNull(app.db.notes().lastChange(), app.db.doses().lastChange(), app.db.medicines().lastChange()).maxOrNull() ?: 0L
        val sent = peers.minOfOrNull { app.settings.getLong("sync_sent_${it.id}") } ?: Long.MAX_VALUE
        unshared = if (peers.isNotEmpty() && last > sent + 90_000) sent else null
    }
    val due = next?.let { it.first.scheduledAt <= System.currentTimeMillis() + 10 * 60_000 } == true
    val speak = "Tap How are you feeling to choose. " + (next?.let { "Next medicine at ${DoseActivity.time(it.first.scheduledAt)}, ${it.second.name}. " } ?: "") + "Help is at the bottom of every screen."

    Screen(if (first.isNotBlank()) first else greeting, speak, onHome = null, subtitle = today, eyebrow = if (first.isNotBlank()) greeting else "",
        side = { PersonaSwitch(nav) }) {
        unshared?.let { t -> Text("Not shared with your helpers yet" + if (t > 0) " · last shared ${com.suryaprakash.medlog.ui.whenWords(t).lowercase()}" else "",
            fontSize = sc.small, color = p.amber, fontWeight = FontWeight.SemiBold) }
        // ── the one main action ──
        HeroTell(onChoose = { nav.go(Route.Tell()) }, onSpeak = { nav.go(Route.Tell(speak = true)) })

        // a new version, found by the daily check
        val update by com.suryaprakash.medlog.Updater.state.collectAsState()
        if (update !is com.suryaprakash.medlog.Updater.State.Idle && update !is com.suryaprakash.medlog.Updater.State.UpToDate && update !is com.suryaprakash.medlog.Updater.State.Checking) UpdateCard()

        if (remindersBlocked) Card(border = p.amber, onClick = { nav.go(Route.Permissions) }, label = "Reminders are off. Tap to fix.") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Warning, null, tint = p.amber, modifier = Modifier.size(26.dp)); Spacer(Modifier.width(12.dp))
                Column { Text("Reminders are off", color = p.ink, fontWeight = FontWeight.Bold, fontSize = sc.body); Text("Tap to turn them on", color = p.inkSoft, fontSize = sc.small) }
            }
        }

        askBetter?.let { pid ->
            val label = app.catalogue.problem(pid)?.label ?: return@let
            Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SpriteIcon(pid, 48.dp); Spacer(Modifier.width(12.dp))
                    Text("$label: is it better now?", fontSize = sc.cardTitle, fontWeight = FontWeight.SemiBold, color = p.ink, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BigButton("Better", Modifier.weight(1f), Tone.SECONDARY, height = 52.dp, onClick = { scope.launch { app.repo.markBetter(pid); app.settings.putString("asked_better_$pid", java.time.LocalDate.now().toString()); app.refreshWidgets(); version++ } })
                    BigButton("Still there", Modifier.weight(1f), Tone.SECONDARY, height = 52.dp, onClick = { app.settings.putString("asked_better_$pid", java.time.LocalDate.now().toString()); nav.go(Route.Tell(pid)) })
                }
            }
        }

        // ── next medicine ──
        if ("meds" !in s.hidden) {
            val takenCount = todays.count { it.first.status == com.suryaprakash.medlog.data.DoseStatus.TAKEN }
            val days = todays.groupBy { it.second.id }.values.map { g -> g.first().second to g.map { it.first } }
            com.suryaprakash.medlog.ui.SectionHeader("Today's medicines",
                if (todays.isEmpty()) "Nothing to take today" else if (takenCount == todays.size) "All ${todays.size} taken" else "$takenCount of ${todays.size} taken",
                if (todays.isNotEmpty()) "See all ${days.size}" else null) { nav.go(Route.TodayMeds()) }
            if (todays.isEmpty()) com.suryaprakash.medlog.ui.DashedAddCard("Add a medicine") { nav.go(Route.MedEdit(null)) }
            // one card at a time, the one that needs attention first; swipe for the next, "See all" for the whole list
            TodayMedsPreview(days) { (m, ds), mod ->
                DayCard(m, ds, onOpen = { nav.go(Route.Meds) },
                    onTaken = { d -> scope.launch { com.suryaprakash.medlog.data.Doses.take(ctx, d.id); savedFeedback(ctx); version++ } },
                    onUndo = { d -> scope.launch { com.suryaprakash.medlog.data.Doses.untake(ctx, d.id); version++ } },
                    onTakenAt = { d, at -> scope.launch { com.suryaprakash.medlog.data.Doses.take(ctx, d.id, at); savedFeedback(ctx); version++ } }, modifier = mod)
            }
        }

        // ── recent problems: one tap to tell more ──
        if (recent.isNotEmpty()) {
            Title("Recent")
            TileGrid(recent, 3, aspect = 0.84f) { r, m ->
                val pr = app.catalogue.problem(r.problemId)
                com.suryaprakash.medlog.ui.PicTile(pr?.label ?: "", m, picture = 64.dp, speak = (pr?.label ?: "") + if (r.todayCount > 0) ", ${r.todayCount} today" else "",
                    onClick = { nav.go(Route.Tell(r.problemId)) },
                    under = { Text(if (r.todayCount > 0) "${r.todayCount} today" else " ", fontSize = sc.small, color = p.inkSoft, fontWeight = FontWeight.SemiBold) }) {
                    SpriteIcon(r.problemId, 64.dp)
                }
            }
        }

        // ── everything else, equal tiles ──
        val tiles = listOfNotNull(
            if ("food" !in s.hidden) HomeTile("Food & water", Icons.Rounded.Restaurant, p.tintGreen) { nav.go(Route.Food) } else null,
            HomeTile("Toilet and tummy", Icons.Rounded.Wc, p.tintTeal) { nav.go(Route.Output()) },
            if ("readings" !in s.hidden) HomeTile("BP & sugar", Icons.Rounded.MonitorHeart, p.tintPink) { nav.go(Route.Readings) } else null,
            if ("doctor" !in s.hidden) HomeTile("Doctor page", Icons.Rounded.LocalHospital, p.tintBlue) { nav.go(Route.Doctor) } else null,
            if ("reports" !in s.hidden) HomeTile("My health", Icons.Rounded.Insights, p.tintPurple) { nav.go(Route.Reports) } else null,
            if ("meds" !in s.hidden) HomeTile("Medicines", Icons.Rounded.Medication, p.tintOrange) { nav.go(Route.Meds) } else null,
        )
        // ── history: what you've noted, one tap away ──
        run {
            val last = recent.maxByOrNull { it.lastAt }
            val lastWords = last?.let { r ->
                val label = app.catalogue.problem(r.problemId)?.label ?: return@let null
                "Last: $label, " + SimpleDateFormat("d MMMM", com.suryaprakash.medlog.speech.I18n.locale).format(Date(r.lastAt))
            } ?: "Everything you have noted"
            val hsh = RoundedCornerShape(sc.radius)
            Row(Modifier.fillMaxWidth().clip(hsh).background(p.card).steady("History. $lastWords") { nav.go(Route.Notes) }
                .padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                IconTile(Icons.Rounded.History, p.tintTeal, 56.dp)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text("History", fontSize = sc.cardTitle, fontWeight = FontWeight.Bold, color = p.ink)
                    Text(lastWords, fontSize = sc.body, color = p.inkSoft)
                }
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = p.inkSoft, modifier = Modifier.size(28.dp))
            }
        }

        Title("More")
        TileGrid(tiles, if (sc.big) 2 else 3, aspect = if (sc.big) 1.05f else 0.82f) { t, m ->
            com.suryaprakash.medlog.ui.PicTile(t.label, m, picture = 48.dp, onClick = t.onClick) { IconTile(t.icon, t.tint, 48.dp) }
        }

    }
}

data class HomeTile(val label: String, val icon: ImageVector, val tint: Color, val onClick: () -> Unit)

/**
 * The main action: big, calm, unmistakable. One card, the question in the largest words, and two equal ways to
 * answer side by side: choose from pictures, or speak (the mic opens straight away on the same page).
 */
@Composable
fun HeroTell(title: String = "How are you feeling?", onChoose: () -> Unit, onSpeak: () -> Unit) {
    val p = LocalPalette.current
    val sc = LocalScale.current
    val sh = RoundedCornerShape(sc.radius + 4.dp)
    Column(Modifier.fillMaxWidth().clip(sh).background(p.brandSoft).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(title, color = p.ink, fontSize = sc.title, fontWeight = FontWeight.Bold, lineHeight = sc.title * 1.2f, modifier = Modifier.semantics { heading() })
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(Triple("Choose", Icons.Rounded.TouchApp, onChoose), Triple("Speak", Icons.Rounded.Mic, onSpeak)).forEach { (label, icon, go) ->
                Column(Modifier.weight(1f).fillMaxHeight().heightIn(min = sc.target + 36.dp).clip(RoundedCornerShape(18.dp)).background(p.card)
                    .steady(if (label == "Speak") "Speak: say how you feel" else "Choose from pictures", onClick = go).padding(vertical = 14.dp, horizontal = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Icon(icon, null, tint = p.brand, modifier = Modifier.size(32.dp))
                    Spacer(Modifier.height(6.dp))
                    Text(label, color = p.ink, fontSize = sc.button, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

/**
 * One of today's doses, read top to bottom: when (the part of the day and the time), what (picture, name, how much, what for),
 * then where it stands. Later today: white, "Log taken". Due now: amber, "I took it". Taken: green, the time and Undo.
 * Missed: red, "I took it late".
 */
@Composable
fun DoseCard(d: Dose, m: Medicine, onOpen: () -> Unit, onTaken: () -> Unit, onUndo: () -> Unit, onNotGiven: (() -> Unit)? = null) {
    val p = LocalPalette.current
    val sc = LocalScale.current
    val now = System.currentTimeMillis()
    val taken = d.status == com.suryaprakash.medlog.data.DoseStatus.TAKEN
    val missed = d.status == com.suryaprakash.medlog.data.DoseStatus.MISSED || d.status == com.suryaprakash.medlog.data.DoseStatus.SKIPPED
    val dueNow = !taken && !missed && d.scheduledAt <= now + 10 * 60_000
    val time = DoseActivity.time(d.scheduledAt)
    val part = com.suryaprakash.medlog.ui.dayPart(java.time.Instant.ofEpochMilli(d.scheduledAt).atZone(java.time.ZoneId.systemDefault()).hour)
    val border = when { taken -> p.ok; missed -> p.red; dueNow -> p.amber; else -> p.line }
    val whenWords = when {
        dueNow -> "Due now · $time"
        missed -> "${if (d.status == com.suryaprakash.medlog.data.DoseStatus.SKIPPED) (if (m.form == "feed") "Not given" else "Skipped") else "Missed"} · $time"
        else -> "${part.name} · $time"
    }
    val sh = RoundedCornerShape(sc.radius)
    @Suppress("UNUSED_VARIABLE") val unusedBorder = border
    Column(Modifier.fillMaxWidth().clip(sh).background(p.card)
        .padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // when
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(part.icon, null, tint = part.tint, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(8.dp))
            Text(whenWords, fontSize = sc.body, fontWeight = FontWeight.SemiBold,
                color = when { dueNow -> p.amber; missed -> p.red; else -> p.inkSoft })
        }
        // what
        Row(Modifier.fillMaxWidth().steady("$time, ${m.name}", onClick = onOpen), verticalAlignment = Alignment.CenterVertically) {
            MedicinePicture(m, 64.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(listOf(m.name, m.strength.takeIf { m.form != "feed" }.orEmpty()).filter { it.isNotBlank() }.joinToString(" "), fontSize = sc.cardTitle, fontWeight = FontWeight.Bold, color = p.ink, lineHeight = sc.cardTitle * 1.2f)
                Text(listOfNotNull(doseWords(m), m.purpose.ifBlank { null }?.let { "for ${it.lowercase()}" }).joinToString(" · ").replaceFirstChar(Char::uppercase),
                    fontSize = sc.body, color = p.inkSoft)
            }
        }
        // where it stands
        if (taken) Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.CheckCircle, null, tint = p.ok, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(10.dp))
            Text("${if (m.form == "feed") "Given" else "Taken"} at ${DoseActivity.time(d.actedAt ?: now)}", fontSize = sc.body, fontWeight = FontWeight.SemiBold, color = p.ok, modifier = Modifier.weight(1f))
            Text("Undo", fontSize = sc.body, fontWeight = FontWeight.SemiBold, color = p.inkSoft,
                modifier = Modifier.clip(RoundedCornerShape(12.dp)).steady("Undo taken", onClick = onUndo).padding(horizontal = 12.dp, vertical = 10.dp))
        } else if (m.form == "feed" && !missed && onNotGiven != null) Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BigButton("Given", Modifier.weight(1f), if (dueNow) Tone.OK else Tone.TINT, height = 52.dp, onClick = onTaken)
            BigButton("Not given", Modifier.weight(1f), Tone.SECONDARY, height = 52.dp, onClick = onNotGiven)
        } else BigButton(if (m.form == "feed") (if (missed) "Given late" else "Given") else if (missed) "I took it late" else "I took it", tone = if (dueNow) Tone.OK else Tone.TINT, height = 52.dp, onClick = onTaken)
    }
}

/**
 * One medicine (or feed) for the whole day, compact: picture, name and dose; every time today as plain words with a
 * mark (✓ taken, ✕ missed); one button only when a dose needs an answer (due now, or missed); and a thin banner
 * along the bottom saying how the day stands, the only coloured part of the card. Tapping the card opens every time
 * with its own answer.
 */
@Composable
fun DayCard(m: Medicine, doses: List<Dose>, onOpen: () -> Unit, onTaken: (Dose) -> Unit, onUndo: (Dose) -> Unit, onNotGiven: ((Dose) -> Unit)? = null,
            onTakenAt: ((Dose, Long) -> Unit)? = null, who: String? = null, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val sc = LocalScale.current
    val now = System.currentTimeMillis()
    val S = com.suryaprakash.medlog.data.DoseStatus
    fun taken(d: Dose) = d.status == S.TAKEN
    fun missed(d: Dose) = d.status == S.MISSED || d.status == S.SKIPPED
    fun due(d: Dose) = !taken(d) && !missed(d) && d.scheduledAt <= now + 10 * 60_000
    val sorted = doses.sortedBy { it.scheduledAt }
    // the dose that needs an answer on the card: one due now, else the latest missed one (to note it late)
    val next = sorted.firstOrNull(::due) ?: sorted.lastOrNull { it.status == S.MISSED }
    val feed = m.form == "feed"
    var sheet by remember { mutableStateOf(false) }
    // a dose whose time has passed: ask when it was taken (on time, just now, or another time), so it can all be noted later
    var asking by remember { mutableStateOf<Dose?>(null) }
    val answer: (Dose) -> Unit = { d -> if (onTakenAt != null && d.scheduledAt < now - 30 * 60_000) asking = d else onTaken(d) }
    val sh = RoundedCornerShape(sc.radius)
    val name = listOf(m.name, m.strength.takeIf { !feed }.orEmpty()).filter { it.isNotBlank() }.joinToString(" ")
    val taken = sorted.count(::taken)
    Column(modifier.fillMaxWidth().clip(sh).background(p.card)
        .steady("$name. $taken of ${sorted.size} ${if (feed) "given" else "taken"} today. Tap to see each time.") { sheet = true }) {
        Column(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                MedicinePicture(m, 44.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(name, fontSize = sc.cardTitle, fontWeight = FontWeight.Bold, color = p.ink, lineHeight = sc.cardTitle * 1.2f)
                    Text(listOfNotNull(doseWords(m), m.purpose.ifBlank { null }?.takeIf { !feed }?.let { "for ${it.lowercase()}" }).joinToString(" · ").replaceFirstChar(Char::uppercase),
                        fontSize = sc.small, color = p.inkSoft)
                }
            }
            if (sorted.size > 1) TimesLine(sorted)
            next?.let { d -> NextDoseButton(d, feed, due(d), missed(d), answer, onNotGiven, who) }
        }
        StatusBanner(sorted, feed, ::taken, ::due)
    }
    if (sheet) DaySheet(m, name, sorted, ::taken, ::missed, ::due, onOpen = { sheet = false; onOpen() }, onTaken = answer, onUndo = onUndo, onNotGiven = onNotGiven,
        onChangeTime = onTakenAt, onDismiss = { sheet = false })
    asking?.let { d -> TakenWhenSheet(d, feed, who = who, onPick = { at -> asking = null; if (at == null) onTaken(d) else onTakenAt?.invoke(d, at) }, onDismiss = { asking = null }) }
}

/** Every time today as plain words, wrapping onto a second line if needed (never cut off): a tick when taken, a cross when missed. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun TimesLine(sorted: List<Dose>) {
    val p = LocalPalette.current
    val sc = LocalScale.current
    val S = com.suryaprakash.medlog.data.DoseStatus
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        sorted.forEach { d ->
            val (icon, tint, color) = when (d.status) {
                S.TAKEN -> Triple(Icons.Rounded.CheckCircle, p.ok, p.ink)
                S.MISSED -> Triple(Icons.Rounded.Cancel, p.red, p.red)
                S.SKIPPED -> Triple(Icons.Rounded.RemoveCircleOutline, p.inkSoft, p.inkSoft)
                else -> Triple(Icons.Rounded.Schedule, p.inkSoft, p.ink)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(chipTime(d.scheduledAt), fontSize = sc.small, fontWeight = FontWeight.SemiBold, color = color, maxLines = 1, softWrap = false)
            }
        }
    }
}

/**
 * How today stands for one medicine, as a thin banner along the bottom of its card: missed (red) or due now (amber)
 * when something needs doing, green when all are done, grey otherwise. "Skipped" was someone's choice, never a warning.
 */
@Composable
private fun StatusBanner(sorted: List<Dose>, feed: Boolean, taken: (Dose) -> Boolean, due: (Dose) -> Boolean) {
    val p = LocalPalette.current
    val sc = LocalScale.current
    val S = com.suryaprakash.medlog.data.DoseStatus
    val missed = sorted.filter { it.status == S.MISSED }
    val skipped = sorted.count { it.status == S.SKIPPED }
    val done = sorted.count(taken)
    val nowDue = sorted.firstOrNull(due)
    val upcoming = sorted.firstOrNull { !taken(it) && it.status != S.MISSED && it.status != S.SKIPPED && !due(it) }
    val gave = if (feed) "given" else "taken"
    data class B(val icon: androidx.compose.ui.graphics.vector.ImageVector, val words: String, val fg: Color, val bg: Color)
    val b = when {
        missed.isNotEmpty() -> B(Icons.Rounded.Cancel, "Missed · " + missed.joinToString(", ") { chipTime(it.scheduledAt) }, p.red, p.redSoft)
        nowDue != null -> B(Icons.Rounded.Schedule, "Due now · ${chipTime(nowDue.scheduledAt)}", p.amber, p.amberSoft)
        done == sorted.size -> B(Icons.Rounded.CheckCircle, if (sorted.size == 1) "${gave.replaceFirstChar(Char::uppercase)} at ${chipTime(sorted[0].actedAt ?: sorted[0].scheduledAt)}" else "All ${sorted.size} $gave", p.ok, p.okSoft)
        upcoming != null -> B(Icons.Rounded.Schedule, "Next · ${chipTime(upcoming.scheduledAt)}", p.inkSoft, p.fill)
        else -> B(Icons.Rounded.RemoveCircleOutline, "$done $gave · $skipped ${if (feed) "not given" else "skipped"}", p.inkSoft, p.fill)
    }
    Row(Modifier.fillMaxWidth().background(b.bg).padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(b.icon, null, tint = b.fg, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(b.words, fontSize = sc.small, fontWeight = FontWeight.Bold, color = b.fg, modifier = Modifier.weight(1f))
        if (sorted.size > 1) Text("$done of ${sorted.size} $gave", fontSize = sc.small, color = p.inkSoft, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * "When did you take it?" for a dose whose time has passed: on time (its own time), just now, or another time
 * (up to two weeks back), so a whole day can be noted at night, by the person or a helper.
 * [onPick] gets null for "just now".
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun TakenWhenSheet(d: Dose, feed: Boolean, who: String? = null, onPick: (Long?) -> Unit, onDismiss: () -> Unit) {
    val p = LocalPalette.current
    val sc = LocalScale.current
    var other by remember { mutableStateOf(false) }
    if (other) { com.suryaprakash.medlog.ui.WhenSheet(d.scheduledAt, onDone = { t -> onPick(t) }, onDismiss = onDismiss); return }
    val onDay = java.time.Instant.ofEpochMilli(d.scheduledAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
    val day = if (onDay == java.time.LocalDate.now()) "" else ", " + dayLabel(d.scheduledAt).lowercase()
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss, containerColor = p.paper,
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = sc.margin).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            com.suryaprakash.medlog.ui.SectionHeader(if (feed) "When was it given?" else if (who != null) "When did they take it?" else "When did you take it?", "The ${chipTime(d.scheduledAt)} ${if (feed) "feed" else "dose"}$day", null)
            BigButton("On time · ${chipTime(d.scheduledAt)}$day", tone = Tone.OK, icon = Icons.Rounded.CheckCircle, onClick = { onPick(d.scheduledAt) })
            if (onDay == java.time.LocalDate.now()) BigButton("Just now", tone = Tone.TINT, onClick = { onPick(null) })
            BigButton("Another time", tone = Tone.SECONDARY, onClick = { other = true })
        }
    }
}

@Composable
private fun NextDoseButton(d: Dose, feed: Boolean, due: Boolean, missed: Boolean, onTaken: (Dose) -> Unit, onNotGiven: ((Dose) -> Unit)?, who: String? = null) {
    val t = chipTime(d.scheduledAt)
    if (feed && onNotGiven != null && !missed) Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        BigButton("Given · $t", Modifier.weight(1f), if (due) Tone.PRIMARY else Tone.TINT, height = 48.dp, onClick = { onTaken(d) })
        BigButton("Not given", Modifier.weight(1f), Tone.SECONDARY, height = 48.dp, onClick = { onNotGiven(d) })
    } else BigButton(when {
            // on a helper's phone the button speaks about the person: "Lakshmi took the 8 AM dose"
            missed -> if (feed) "${who ?: "I"} gave the $t feed late" else "${who ?: "I"} took the $t dose late"
            else -> if (feed) "${who ?: "I"} gave the $t feed" else "${who ?: "I"} took the $t dose"
        },
        tone = if (due) Tone.PRIMARY else Tone.TINT, height = 48.dp, onClick = { onTaken(d) })
}

/** "8 AM" for whole hours, "8:30 AM" otherwise: short enough for two chips side by side, and easy to read. */
fun chipTime(at: Long): String {
    val t = java.time.Instant.ofEpochMilli(at).atZone(java.time.ZoneId.systemDefault())
    return SimpleDateFormat(if (t.minute == 0) "h a" else "h:mm a", com.suryaprakash.medlog.speech.I18n.locale).format(Date(at))
}

/** Every time today for one medicine, each on its own line with its own answer. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun DaySheet(m: Medicine, name: String, sorted: List<Dose>, taken: (Dose) -> Boolean, missed: (Dose) -> Boolean, due: (Dose) -> Boolean,
                     onOpen: () -> Unit, onTaken: (Dose) -> Unit, onUndo: (Dose) -> Unit, onNotGiven: ((Dose) -> Unit)?, onChangeTime: ((Dose, Long) -> Unit)? = null, onDismiss: () -> Unit) {
    var changing by remember { mutableStateOf<Dose?>(null) }
    changing?.let { d -> com.suryaprakash.medlog.ui.WhenSheet(d.actedAt ?: d.scheduledAt, onDone = { t -> changing = null; onChangeTime?.invoke(d, t ?: System.currentTimeMillis()) }, onDismiss = { changing = null }); return }
    val p = LocalPalette.current
    val sc = LocalScale.current
    val feed = m.form == "feed"
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss, containerColor = p.paper,
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = sc.margin).padding(bottom = 24.dp).verticalScroll(androidx.compose.foundation.rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MedicinePicture(m, 52.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(name, fontSize = sc.headline, fontWeight = FontWeight.Bold, color = p.ink)
                    Text("${sorted.size} time${if (sorted.size == 1) "" else "s"} today · ${sorted.count(taken)} ${if (feed) "given" else "taken"}", fontSize = sc.small, color = p.inkSoft)
                }
            }
            com.suryaprakash.medlog.ui.Group {
                sorted.forEachIndexed { i, d ->
                    if (i > 0) com.suryaprakash.medlog.ui.GroupLine()
                    val part = com.suryaprakash.medlog.ui.dayPart(java.time.Instant.ofEpochMilli(d.scheduledAt).atZone(java.time.ZoneId.systemDefault()).hour)
                    val t = DoseActivity.time(d.scheduledAt)
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (taken(d)) Icons.Rounded.CheckCircle else part.icon, null, tint = if (taken(d)) p.ok else part.tint, modifier = Modifier.size(26.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(t, fontSize = sc.body, fontWeight = FontWeight.Bold, color = p.ink)
                            Text(when {
                                taken(d) -> "${if (feed) "Given" else "Taken"} at ${DoseActivity.time(d.actedAt ?: d.scheduledAt)}"
                                d.status == com.suryaprakash.medlog.data.DoseStatus.SKIPPED -> if (feed) "Not given" else "Skipped"
                                missed(d) -> "Missed"
                                due(d) -> "Due now"
                                else -> part.name
                            }, fontSize = sc.small, fontWeight = FontWeight.SemiBold, color = when { taken(d) -> p.ok; missed(d) -> p.red; due(d) -> p.amber; else -> p.inkSoft })
                            // the time it was taken can be put right
                            if (taken(d) && onChangeTime != null) Text("Change the time", fontSize = sc.small, fontWeight = FontWeight.Bold, color = p.brand,
                                modifier = Modifier.padding(top = 2.dp).clip(RoundedCornerShape(8.dp)).steady("Change the time it was taken") { changing = d }.padding(vertical = 8.dp))
                        }
                        when {
                            taken(d) -> BigButton("Undo", Modifier.width(IntrinsicSize.Max), tone = Tone.SECONDARY, height = 48.dp, onClick = { onUndo(d) })
                            d.status == com.suryaprakash.medlog.data.DoseStatus.SKIPPED -> BigButton(if (feed) "Given" else "Took it", Modifier.width(IntrinsicSize.Max), tone = Tone.TINT, height = 48.dp, onClick = { onTaken(d) })
                            else -> BigButton(if (feed) "Given" else if (missed(d)) "Took it late" else "Took it", Modifier.width(IntrinsicSize.Max), tone = if (due(d)) Tone.OK else Tone.TINT, height = 48.dp, onClick = { onTaken(d) })
                        }
                    }
                }
            }
            BigButton(if (feed) "See all feeds" else "See all medicines", tone = Tone.OUTLINE, height = 52.dp, onClick = onOpen)
        }
    }
}

@Suppress("unused") private val keepHint: @Composable () -> Unit = { Hint("") }
@Suppress("unused") private val keepPad = Modifier.padding(0.dp)
