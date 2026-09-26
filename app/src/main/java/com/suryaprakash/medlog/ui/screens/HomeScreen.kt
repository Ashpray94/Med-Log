package com.suryaprakash.medlog.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.Mic
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
    var askBetter by remember { mutableStateOf<String?>(null) }
    var remindersBlocked by remember { mutableStateOf(false) }
    var version by remember { mutableStateOf(0) }

    LaunchedEffect(version) {
        name = app.repo.profile().name
        recent = app.repo.recentProblems(3)
        next = Scheduler.nextDose(ctx)
        val now = System.currentTimeMillis()
        askBetter = recent.firstOrNull { it.ongoing && now - it.lastAt > 20 * 3600_000L && app.settings.getString("asked_better_${it.problemId}") != java.time.LocalDate.now().toString() }?.problemId
        remindersBlocked = !Perms.exactAlarmsOk(ctx) || !Perms.has(ctx, *Perms.NOTIFY)
    }
    val hour = LocalTime.now().hour
    val hello = when { hour < 12 -> "Good morning"; hour < 17 -> "Good afternoon"; else -> "Good evening" } + if (name.isNotBlank()) ",\n${name.split(" ").first()}" else ""
    val today = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date())
    val due = next?.let { it.first.scheduledAt <= System.currentTimeMillis() + 10 * 60_000 } == true
    val speak = "Tap How are you feeling to tell me. " + (next?.let { "Next medicine at ${DoseActivity.time(it.first.scheduledAt)}, ${it.second.name}. " } ?: "") + "Help is at the bottom of every screen."

    Screen(hello, speak, onHome = null, subtitle = today, trailing = { RoundIcon(Icons.Rounded.Settings, "Settings") { nav.go(Route.Settings) } }) {
        // ── the one main action ──
        HeroTell { nav.go(Route.Tell()) }

        // a new version, found by the daily check
        val update by com.suryaprakash.medlog.Updater.state.collectAsState()
        if (update !is com.suryaprakash.medlog.Updater.State.Idle && update !is com.suryaprakash.medlog.Updater.State.UpToDate && update !is com.suryaprakash.medlog.Updater.State.Checking) UpdateCard()

        // ── family: one tap away, like the widget ──
        if ("help" !in s.hidden) FamilyCard(nav)

        if (remindersBlocked) Card(color = p.amberSoft, onClick = { nav.go(Route.Permissions) }, label = "Reminders are off. Tap to fix.") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Warning, null, tint = p.amber, modifier = Modifier.size(26.dp)); Spacer(Modifier.width(12.dp))
                Column { Text("Reminders are off", color = p.amber, fontWeight = FontWeight.Bold, fontSize = sc.body); Text("Tap to turn them on", color = p.amber, fontSize = sc.small) }
            }
        }

        askBetter?.let { pid ->
            val label = app.catalogue.problem(pid)?.label ?: return@let
            Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SpriteIcon(pid, 56.dp); Spacer(Modifier.width(14.dp))
                    Text("Is your ${label.lowercase()} better now?", fontSize = sc.headline, fontWeight = FontWeight.SemiBold, color = p.ink)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BigButton("Yes, better", Modifier.weight(1f), Tone.OK, onClick = { scope.launch { app.repo.markBetter(pid); app.settings.putString("asked_better_$pid", java.time.LocalDate.now().toString()); app.refreshWidgets(); version++ } })
                    BigButton("Still there", Modifier.weight(1f), Tone.SECONDARY, onClick = { app.settings.putString("asked_better_$pid", java.time.LocalDate.now().toString()); nav.go(Route.Tell(pid)) })
                }
            }
        }

        // ── next medicine ──
        if ("meds" !in s.hidden) Card(onClick = { nav.go(Route.Meds) }, label = next?.let { "Next medicine: ${DoseActivity.time(it.first.scheduledAt)}, ${it.second.name}" } ?: "Medicines") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(Icons.Rounded.Medication, p.tintOrange, 48.dp); Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (next == null) "Medicines" else if (due) "Medicine due now" else "Next medicine", fontSize = sc.small, color = if (due) p.red else p.inkSoft, fontWeight = FontWeight.SemiBold)
                    Text(next?.let { "${DoseActivity.time(it.first.scheduledAt)} · ${it.second.name}" } ?: "No more today",
                        fontSize = sc.headline, fontWeight = FontWeight.Bold, color = p.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (due) BigButton("I took it", tone = Tone.OK, onClick = { scope.launch { next?.first?.id?.let { Scheduler.take(ctx, it) }; savedFeedback(ctx); version++ } })
            else BigButton("Did I take my medicines?", tone = Tone.QUIET, icon = Icons.Rounded.Checklist, onClick = { nav.go(Route.DidITake) })
        }

        // ── recent problems: one tap to tell more ──
        if (recent.isNotEmpty()) {
            Title("Recent")
            TileGrid(recent, 3, aspect = 0.84f) { r, m ->
                val pr = app.catalogue.problem(r.problemId)
                Tile((pr?.label ?: "") + if (r.todayCount > 0) ", ${r.todayCount} today" else "", m, onClick = { nav.go(Route.Tell(r.problemId)) }) {
                    SpriteIcon(r.problemId, sc.target * 1.4f)
                    Spacer(Modifier.height(6.dp))
                    Text(pr?.label ?: "", fontSize = sc.small, fontWeight = FontWeight.SemiBold, color = p.ink, textAlign = TextAlign.Center, maxLines = 2, minLines = 2, lineHeight = sc.small * 1.15f, overflow = TextOverflow.Ellipsis)
                    Text(if (r.todayCount > 0) "${r.todayCount} today" else " ", fontSize = sc.small, color = p.amber, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // ── everything else, equal tiles ──
        val tiles = listOfNotNull(
            if ("food" !in s.hidden) HomeTile("Food & water", Icons.Rounded.Restaurant, p.tintGreen) { nav.go(Route.Food) } else null,
            if ("readings" !in s.hidden) HomeTile("BP & sugar", Icons.Rounded.MonitorHeart, p.tintPink) { nav.go(Route.Readings) } else null,
            if ("doctor" !in s.hidden) HomeTile("Doctor page", Icons.Rounded.LocalHospital, p.tintBlue) { nav.go(Route.Doctor) } else null,
            if ("reports" !in s.hidden) HomeTile("My health", Icons.Rounded.Insights, p.tintPurple) { nav.go(Route.Reports) } else null,
            HomeTile("History", Icons.Rounded.History, p.tintTeal) { nav.go(Route.Notes) },
            if ("meds" !in s.hidden) HomeTile("Medicines", Icons.Rounded.Medication, p.tintOrange) { nav.go(Route.Meds) } else null,
        )
        Title("More")
        TileGrid(tiles, if (sc.big) 2 else 3, aspect = if (sc.big) 1.25f else 1f) { t, m ->
            Tile(t.label, m, onClick = t.onClick) {
                IconTile(t.icon, t.tint, if (sc.big) 56.dp else 48.dp)
                Spacer(Modifier.height(10.dp))
                Text(t.label, fontSize = sc.small, fontWeight = FontWeight.SemiBold, color = p.ink, textAlign = TextAlign.Center, maxLines = 2, lineHeight = sc.small * 1.15f)
            }
        }

    }
}

/** Up to three of the person's own messages, one tap to send, and a call to the first helper. */
@Composable
fun FamilyCard(nav: Nav) {
    val ctx = LocalContext.current
    val app = ctx.medlog
    val p = LocalPalette.current
    val sc = LocalScale.current
    val s = LocalSettings.current
    val helpers by app.db.helpers().flow().collectAsState(emptyList())
    val status by HelpMessages.status.collectAsState()
    val acks by com.suryaprakash.medlog.help.Nearby.acks.collectAsState()
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Family", fontSize = sc.headline, fontWeight = FontWeight.Bold, color = p.ink, modifier = Modifier.weight(1f))
            Text("More", color = p.brand, fontSize = sc.body, fontWeight = FontWeight.SemiBold, modifier = Modifier.steady("More ways to ask family") { nav.go(Route.Help) }.padding(8.dp))
        }
        // the last message and its answer, so nobody has to go looking
        status?.takeIf { System.currentTimeMillis() - it.at < 30 * 60_000L }?.let { st ->
            Hint("You sent \"${st.text}\" at ${timeLabel(st.at)}")
            acks.lastOrNull()?.let { a: com.suryaprakash.medlog.help.Nearby.Ack -> com.suryaprakash.medlog.ui.Body("✓ ${a.name}: ${com.suryaprakash.medlog.help.Nearby.replyWords(a.reply)}", bold = true) }
        }
        val msgs = s.messages.take(3)
        if (helpers.isEmpty()) BigButton("Add a helper", tone = Tone.QUIET, onClick = { nav.go(Route.HelperEdit(null)) })
        else if (msgs.isEmpty()) BigButton("Choose messages to send", tone = Tone.QUIET, onClick = { nav.go(Route.Messages) })
        else msgs.forEach { m ->
            val text = m.substringAfter('|')
            BigButton(text, tone = Tone.SECONDARY, icon = HelpMessages.icon(m.substringBefore('|')), onClick = { HelpMessages.send(ctx, text); app.speaker.say("Sending: $text"); nav.go(Route.Help) })
        }
        helpers.firstOrNull()?.let { h -> BigButton("Call ${h.name}", tone = Tone.QUIET, icon = Icons.Rounded.Call, onClick = { com.suryaprakash.medlog.help.Calls.call(ctx, h.phone) }) }
    }
}

data class HomeTile(val label: String, val icon: ImageVector, val tint: Color, val onClick: () -> Unit)

/** The main action: big, calm, unmistakable. */
@Composable
fun HeroTell(onClick: () -> Unit) {
    val p = LocalPalette.current
    val sc = LocalScale.current
    val sh = RoundedCornerShape(sc.radius + 4.dp)
    Row(
        Modifier.fillMaxWidth().heightIn(min = sc.target * 2.1f).shadow(6.dp, sh, spotColor = p.brand.copy(alpha = 0.4f)).clip(sh)
            .background(Brush.linearGradient(listOf(Color(0xFF0E8A7F), p.brand)))
            .steady("How are you feeling? Tap and tell me.", onClick = onClick).padding(horizontal = 22.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("How are you feeling?", color = Color.White, fontSize = sc.headline * 1.15f, fontWeight = FontWeight.Bold, lineHeight = sc.headline * 1.3f)
            Spacer(Modifier.height(4.dp))
            Text("Tap and tell me", color = Color.White.copy(alpha = 0.88f), fontSize = sc.body)
        }
        Box(Modifier.size(sc.target * 1.15f).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Mic, null, tint = p.brand, modifier = Modifier.size(sc.target * 0.6f))
        }
    }
}

@Suppress("unused") private val keepHint: @Composable () -> Unit = { Hint("") }
@Suppress("unused") private val keepPad = Modifier.padding(0.dp)
