package com.suryaprakash.medlog.ui.screens

import androidx.compose.runtime.collectAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material3.Icon
import com.suryaprakash.medlog.ui.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import com.suryaprakash.medlog.data.Helper
import com.suryaprakash.medlog.data.Profile
import com.suryaprakash.medlog.help.Calls
import com.suryaprakash.medlog.help.Sos
import com.suryaprakash.medlog.medlog
import com.suryaprakash.medlog.ui.LocalPalette
import com.suryaprakash.medlog.ui.LocalScale
import com.suryaprakash.medlog.ui.LocalSettings
import com.suryaprakash.medlog.ui.Nav
import com.suryaprakash.medlog.ui.Route
import com.suryaprakash.medlog.ui.Screen
import com.suryaprakash.medlog.ui.steady

/**
 * The emergency page, one tap from the bottom bar on every screen. It offers three things that look nothing alike:
 *   1. Ambulance: red, the biggest.   2. Alert my family: dark, hold to start.   3. Call one person: faces.
 */
@Composable
fun EmergencyScreen(nav: Nav) {
    val ctx = LocalContext.current
    val app = ctx.medlog
    val s = LocalSettings.current
    val p = LocalPalette.current
    val sc = LocalScale.current
    var helpers by remember { mutableStateOf<List<Helper>>(emptyList()) }
    var profile by remember { mutableStateOf<Profile?>(null) }
    val pv by app.db.profile().flow().collectAsState(null)
    val hv by app.db.helpers().flow().collectAsState(emptyList())
    LaunchedEffect(pv, hv) { helpers = app.db.helpers().all(); profile = app.repo.profile() }
    Screen("Emergency", "Three choices. One: the big red card calls ${s.emergencyNumber} for an ambulance. Two: hold the dark card to alert all your family. Three: tap a face to call one person.",
        onHome = { nav.home() }, onBack = { nav.back() }) {
        CallAmbulanceCard(s.emergencyNumber)
        AlertFamilyOrNotice(helpers.size) { Sos.start(ctx, "SOS") }
        val people = buildList {
            helpers.take(3).forEach { add(Person(it.name, it.relation.ifBlank { "Family" }, it.phone)) }
            profile?.takeIf { it.doctorPhone.isNotBlank() }?.let { add(Person(it.doctorName.ifBlank { "Doctor" }, "Doctor", it.doctorPhone)) }
        }.take(4)
        CallOnePerson(people) { nav.go(Route.HelperEdit(null)) }
        Text("Feeling low? Free helpline $MENTAL_HEALTH_LINE", fontSize = sc.small, color = p.brand, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).steady("Call the helpline $MENTAL_HEALTH_LINE") { Calls.ui(ctx, MENTAL_HEALTH_LINE) }.padding(vertical = 8.dp))
    }
}

data class Person(val name: String, val role: String, val phone: String)

/** Choice 1, and the largest thing on the page: call the ambulance. */
@Composable
fun CallAmbulanceCard(number: String, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val sc = LocalScale.current
    val p = LocalPalette.current
    val still = LocalSettings.current.lessMotion
    val pulse = rememberInfiniteTransition(label = "call")
    val k by pulse.animateFloat(1f, 1.3f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "k")
    Box(
        modifier.fillMaxWidth().heightIn(min = 236.dp).clip(RoundedCornerShape(sc.radius + 6.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFFE5392F), Color(0xFFB3211A))))
            .steady("Call $number now, ambulance") { Calls.ui(ctx, number) }
            .padding(20.dp),
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(96.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(76.dp).scale(if (still) 1.18f else k).clip(CircleShape).background(Color.White.copy(alpha = 0.2f)))
                Box(Modifier.size(74.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Call, null, tint = p.red, modifier = Modifier.size(40.dp))
                }
            }
            Spacer(Modifier.size(10.dp))
            Text("Call $number", color = Color.White, fontSize = sc.huge, fontWeight = FontWeight.ExtraBold, maxLines = 1)
            Text("Ambulance  ·  free  ·  any time", color = Color.White.copy(alpha = 0.92f), fontSize = sc.body, fontWeight = FontWeight.Medium)
        }
    }
}

/** Choice 2: hold to alert everyone. Dark, so it never looks like the ambulance card; a hold, so it never starts by accident. */
/** Hold-to-alert card on the person's own MedLog; on a replica it says where this runs instead (nothing texts or calls from the helper's phone). */
@Composable
fun AlertFamilyOrNotice(helpers: Int, onStart: () -> Unit) {
    val n = LocalContext.current.medlog.viewing.notice()
    if (n != null) com.suryaprakash.medlog.ui.Card { com.suryaprakash.medlog.ui.Body(n, bold = true) } else AlertFamilyCard(helpers, onStart = onStart)
}

@Composable
fun AlertFamilyCard(helpers: Int, modifier: Modifier = Modifier, onStart: () -> Unit) {
    val sc = LocalScale.current
    val p = LocalPalette.current
    val app = LocalContext.current.medlog
    var holding by remember { mutableStateOf(false) }
    val progress by animateFloatAsState(if (holding) 1f else 0f, tween(if (holding) 2000 else 150), label = "hold") {
        if (it >= 1f && holding) { holding = false; onStart() }
    }
    val words = if (helpers == 0) "Texts your location. Add family to call them too." else "Texts your location to $helpers, then calls each one"
    Box(
        modifier.fillMaxWidth().heightIn(min = 112.dp).clip(RoundedCornerShape(sc.radius + 6.dp)).background(Color(0xFF202327))
            .semantics { role = Role.Button; contentDescription = "Alert my family. Hold for 2 seconds. $words"; onClick("Alert my family") { onStart(); true } }
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    holding = true
                    val released = tryAwaitRelease()
                    holding = false
                    if (released && progress < 0.15f) app.speaker.say("Keep holding for 2 seconds to alert your family.")
                })
            },
    ) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(progress.coerceAtLeast(0.001f)).background(p.red.copy(alpha = 0.55f)))
        Column(Modifier.fillMaxWidth().align(Alignment.CenterStart).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(52.dp).clip(CircleShape).background(p.red), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Groups, null, tint = Color.White, modifier = Modifier.size(30.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (holding) "Keep holding…" else "Hold to alert my family", color = Color.White, fontSize = sc.headline, fontWeight = FontWeight.Bold)
                    Text(words, color = Color.White.copy(alpha = 0.75f), fontSize = sc.small)
                }
            }
        }
    }
}

/** Choice 3: call one person. Big faces in a row; the same shape the phone uses for contacts. */
@Composable
fun CallOnePerson(people: List<Person>, onAdd: () -> Unit) {
    val ctx = LocalContext.current
    val p = LocalPalette.current
    val sc = LocalScale.current
    val tints = listOf(p.tintBlue, p.tintGreen, p.tintOrange, p.tintPurple)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(sc.radius + 6.dp)).background(p.card).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Call one person", fontSize = sc.headline, fontWeight = FontWeight.Bold, color = p.ink)
        if (people.isEmpty()) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = sc.target).clip(RoundedCornerShape(16.dp)).background(p.fill).steady("Add family to call", onClick = onAdd).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.PersonAdd, null, tint = p.brand, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Add family", fontSize = sc.body, fontWeight = FontWeight.Bold, color = p.ink)
                    Text("Their faces will show here, to call with one tap", fontSize = sc.small, color = p.inkSoft)
                }
            }
            return@Column
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            people.forEachIndexed { i, who ->
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).steady("Call ${who.name}") { Calls.ui(ctx, who.phone) }.padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(contentAlignment = Alignment.BottomEnd) {
                        Box(Modifier.size(62.dp).clip(CircleShape).background(tints[i % tints.size].copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                            Text(who.name.take(1).uppercase(), color = tints[i % tints.size], fontSize = sc.title * 0.8f, fontWeight = FontWeight.Bold)
                        }
                        Box(Modifier.size(24.dp).clip(CircleShape).background(p.ok), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Call, null, tint = Color.White, modifier = Modifier.size(14.dp))
                        }
                    }
                    Spacer(Modifier.size(6.dp))
                    Text(who.name, fontSize = sc.small, fontWeight = FontWeight.Bold, color = p.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(who.role, fontSize = sc.small, color = p.inkSoft, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (people.size < 4) Column(
                Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).steady("Add someone to call", onClick = onAdd).padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.size(62.dp).clip(CircleShape).background(p.fill), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.PersonAdd, null, tint = p.inkSoft, modifier = Modifier.size(28.dp))
                }
                Spacer(Modifier.size(6.dp))
                Text("Add", fontSize = sc.small, fontWeight = FontWeight.Bold, color = p.inkSoft)
            }
            repeat((3 - people.size).coerceAtLeast(0)) { Spacer(Modifier.weight(1f)) }
        }
    }
}

/** Family to call, as the same faces row (used on the danger screen). */
@Composable
fun HelperCalls(helpers: List<Helper>) {
    val nav = com.suryaprakash.medlog.ui.LocalNav.current
    CallOnePerson(helpers.take(4).map { Person(it.name, it.relation.ifBlank { "Family" }, it.phone) }) { nav?.go(Route.HelperEdit(null)) }
}

@Composable
fun SectionLabel(text: String) {
    val p = LocalPalette.current
    Text(text.uppercase(), fontSize = LocalScale.current.small, fontWeight = FontWeight.Bold, color = p.inkSoft,
        letterSpacing = TextUnit(0.06f, TextUnitType.Em), modifier = Modifier.padding(top = 8.dp))
}
