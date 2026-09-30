package com.suryaprakash.medlog.ui.screens

import com.suryaprakash.medlog.ui.cardTitle
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material.icons.rounded.CheckCircle
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
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.MoreHoriz
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
import androidx.compose.ui.unit.sp
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
import com.suryaprakash.medlog.ui.Hs
import com.suryaprakash.medlog.ui.Route
import com.suryaprakash.medlog.ui.Senior
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
    val s = LocalSettings.current
    var name by remember { mutableStateOf("") }
    var remindersBlocked by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        name = app.repo.profile().name
        remindersBlocked = !Perms.exactAlarmsOk(ctx) || !Perms.has(ctx, *Perms.NOTIFY)
    }
    val hour = LocalTime.now().hour
    val greeting = when { hour < 12 -> "Good morning"; hour < 17 -> "Good afternoon"; else -> "Good evening" }
    val first = name.split(" ").first()
    val today = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date())

    Screen(if (first.isNotBlank()) first else greeting, "Tap a picture to open it.", onHome = null, subtitle = today, eyebrow = if (first.isNotBlank()) greeting else "") {
        PersonaSwitch(nav)

        // a new version, found by the daily check
        val update by com.suryaprakash.medlog.Updater.state.collectAsState()
        if (update !is com.suryaprakash.medlog.Updater.State.Idle && update !is com.suryaprakash.medlog.Updater.State.UpToDate && update !is com.suryaprakash.medlog.Updater.State.Checking) UpdateCard()

        if (remindersBlocked) Card(border = Hs.Amber, onClick = { nav.go(Route.Permissions) }, label = "Reminders are off. Tap to fix.") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Warning, null, tint = Hs.Amber, modifier = Modifier.size(28.dp)); Spacer(Modifier.width(12.dp))
                Text("Reminders are off. Fix", color = Hs.Ink, fontWeight = FontWeight.Bold, fontSize = Hs.Body)
            }
        }

        // Hero tiles: 2x2 grid with large icons and labels
        val heroTiles = listOf(
            HomeTile("How do I feel", Icons.Rounded.ChatBubble, Hs.Blue) { nav.go(Route.Tell()) },
            HomeTile("Medicines", Icons.Rounded.Medication, Hs.Ink) { nav.go(Route.Meds) },
            HomeTile("Food & water", Icons.Rounded.Restaurant, Hs.Green) { nav.go(Route.Food) },
            HomeTile("SOS", Icons.Rounded.Sos, Hs.Red) { nav.go(Route.Emergency) },
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Senior.Gap)) {
            heroTiles.take(2).forEach { t ->
                val sh = RoundedCornerShape(Hs.Radius)
                Column(
                    Modifier.weight(1f).heightIn(min = 150.dp).clip(sh).background(t.tint).steady(t.label, onClick = t.onClick).padding(Senior.Gap),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                ) {
                    Icon(t.icon, null, tint = Color.White, modifier = Modifier.size(Senior.HeroIcon))
                    Spacer(Modifier.height(8.dp))
                    Text(t.label, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center, maxLines = 2)
                }
            }
        }
        Spacer(Modifier.height(Senior.Gap))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Senior.Gap)) {
            heroTiles.drop(2).forEach { t ->
                val sh = RoundedCornerShape(Hs.Radius)
                Column(
                    Modifier.weight(1f).heightIn(min = 150.dp).clip(sh).background(t.tint).steady(t.label, onClick = t.onClick).padding(Senior.Gap),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                ) {
                    Icon(t.icon, null, tint = Color.White, modifier = Modifier.size(Senior.HeroIcon))
                    Spacer(Modifier.height(8.dp))
                    Text(t.label, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center, maxLines = 2)
                }
            }
        }

        // More section heading
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.MoreHoriz, null, tint = Hs.Ink, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(8.dp))
            Text("More", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Hs.Ink)
        }

        // Other tiles: 2 per row with min height 96.dp
        val otherTiles = listOfNotNull(
            if ("meds" !in s.hidden) HomeTile("Did I take it", Icons.Rounded.Checklist, Hs.Ink) { nav.go(Route.DidITake) } else null,
            if ("readings" !in s.hidden) HomeTile("BP & sugar", Icons.Rounded.MonitorHeart, Hs.Ink) { nav.go(Route.Readings) } else null,
            if ("doctor" !in s.hidden) HomeTile("Doctor page", Icons.Rounded.LocalHospital, Hs.Ink) { nav.go(Route.Doctor) } else null,
            if ("reports" !in s.hidden) HomeTile("My health", Icons.Rounded.Insights, Hs.Ink) { nav.go(Route.Reports) } else null,
            HomeTile("History", Icons.Rounded.History, Hs.Ink) { nav.go(Route.Notes) },
            HomeTile("Appointments", Icons.Rounded.CalendarMonth, Hs.Ink) { nav.go(Route.Appointments) },
            HomeTile("Helpers", Icons.Rounded.Groups, Hs.Ink) { nav.go(Route.HelpTab) },
        )
        for (i in otherTiles.indices step 2) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Hs.Gutter)) {
                (i until minOf(i + 2, otherTiles.size)).forEach { idx ->
                    val t = otherTiles[idx]
                    val sh = RoundedCornerShape(Hs.Radius)
                    Column(
                        Modifier.weight(1f).heightIn(min = 96.dp).clip(sh).background(Hs.Paper).border(2.dp, Hs.Ink, sh).steady(t.label, onClick = t.onClick).padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(t.icon, null, tint = Hs.Ink, modifier = Modifier.size(40.dp))
                        Spacer(Modifier.height(4.dp))
                        Text(t.label, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Hs.Ink, textAlign = TextAlign.Center, maxLines = 2)
                    }
                }
            }
        }
    }
}

data class HomeTile(val label: String, val icon: ImageVector, val tint: Color, val onClick: () -> Unit)

/** The main action: big, calm, unmistakable. */
@Composable
fun HeroTell(onClick: () -> Unit) {
    val p = LocalPalette.current
    val sc = LocalScale.current
    val sh = RoundedCornerShape(sc.radius + 4.dp)
    // one row: the question and what to do on the left, the arrow on the right, all centred
    Row(
        Modifier.fillMaxWidth().heightIn(min = if (sc.big) 168.dp else 152.dp).clip(sh).background(p.brand)
            .steady("How are you feeling? Tap to choose.", onClick = onClick).padding(horizontal = 24.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("How are you feeling?", color = Color.White, fontSize = sc.title, fontWeight = FontWeight.Bold, lineHeight = sc.title * 1.2f)
            Spacer(Modifier.height(8.dp))
            Text("Tap to choose", color = Color.White.copy(alpha = 0.85f), fontSize = sc.body)
        }
        Spacer(Modifier.width(16.dp))
        Box(Modifier.size(sc.target + 8.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = p.brand, modifier = Modifier.size(28.dp))
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
    Column(Modifier.fillMaxWidth().clip(sh).background(if (taken) p.okSoft else p.card).border(if (border == p.line) 1.dp else 2.dp, border, sh)
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
                Text(listOf(m.name, m.strength).filter { it.isNotBlank() }.joinToString(" "), fontSize = sc.cardTitle, fontWeight = FontWeight.Bold, color = p.ink,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = sc.cardTitle * 1.2f)
                Text(listOfNotNull(doseWords(m), m.purpose.ifBlank { null }?.let { "for ${it.lowercase()}" }).joinToString(" · ").replaceFirstChar(Char::uppercase),
                    fontSize = sc.body, color = p.inkSoft, maxLines = 2, overflow = TextOverflow.Ellipsis)
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
        } else BigButton(if (m.form == "feed") (if (missed) "Given late" else "Given") else if (missed) "I took it late" else if (dueNow) "I took it" else "Log taken", tone = if (dueNow) Tone.OK else Tone.TINT, height = 52.dp, onClick = onTaken)
    }
}

@Suppress("unused") private val keepHint: @Composable () -> Unit = { Hint("") }
@Suppress("unused") private val keepPad = Modifier.padding(0.dp)
