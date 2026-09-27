package com.suryaprakash.medlog.ui

import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.rounded.Add
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.ScrollState
import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Sos
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Icon
import com.suryaprakash.medlog.ui.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.material.icons.rounded.Person
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.suryaprakash.medlog.medlog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ───────────────────────── Read Aloud ─────────────────────────

/** What the Read Aloud button says for the screen that is showing. */
object ReadAloud {
    var text by mutableStateOf("")
    var heardLabel: String? = null
    var heardAt = 0L
}

// ───────────────────────── steady touch ─────────────────────────

private var lastTapAt = 0L

/**
 * Tremor-friendly tap (plan 4.3 #3): acts when the finger lifts, ignores repeat taps within 0.6 s,
 * tolerates small movement. In "touch to hear" mode the first tap reads the label, the second acts.
 */
@Composable
fun Modifier.steady(label: String, enabled: Boolean = true, onPress: (Boolean) -> Unit = {}, onClick: () -> Unit): Modifier {
    val s = LocalSettings.current
    val haptic = LocalHapticFeedback.current
    val ctx = LocalContext.current
    // always run the latest action: the tap handler below lives across recompositions, so it must not keep an
    // old copy of onClick (that made a second tap undo the first on multi-choice pages)
    val latest = androidx.compose.runtime.rememberUpdatedState(onClick)
    val latestPress = androidx.compose.runtime.rememberUpdatedState(onPress)
    val act = {
        val now = System.currentTimeMillis()
        val debounce = if (s.steadyTouch) 600 else 250
        if (enabled && now - lastTapAt > debounce) {
            lastTapAt = now
            if (s.touchToHear && !(ReadAloud.heardLabel == label && now - ReadAloud.heardAt < 4000)) {
                ReadAloud.heardLabel = label; ReadAloud.heardAt = now
                ctx.medlog.speaker.say(label)
            } else {
                ReadAloud.heardLabel = null
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                latest.value()
            }
        }
    }
    return this
        .semantics { role = Role.Button; contentDescription = label; onClick(label) { act(); true } }
        .pointerInput(label, enabled) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                latestPress.value(true)
                val up = waitForUpOrCancellation()
                latestPress.value(false)
                if (up != null) act()
            }
        }
}

/** Soft press feedback shared by every tappable surface. */
@Composable
private fun pressScale(pressed: Boolean): Float {
    val reduce = LocalSettings.current.lessMotion
    val k by animateFloatAsState(if (pressed && !reduce) 0.975f else 1f, tween(90), label = "press")
    return k
}

// ───────────────────────── feedback ─────────────────────────

fun savedFeedback(ctx: android.content.Context) {
    runCatching { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 60).startTone(ToneGenerator.TONE_PROP_ACK, 180) }
    runCatching {
        val v = ctx.getSystemService(android.os.Vibrator::class.java)
        if (android.os.Build.VERSION.SDK_INT >= 26) v?.vibrate(android.os.VibrationEffect.createOneShot(120, 180)) else @Suppress("DEPRECATION") v?.vibrate(120)
    }
}

// ───────────────────────── undo ─────────────────────────

object UndoHost {
    var message by mutableStateOf<String?>(null)
    var action: (() -> Unit)? = null
    var shownAt by mutableLongStateOf(0L)
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    /** Shows [msg] with Undo for 3 seconds. The timer is not tied to a screen, so moving on can't leave it stuck. */
    fun show(msg: String, undo: () -> Unit) {
        val at = System.currentTimeMillis()
        message = msg; action = undo; shownAt = at
        handler.postDelayed({ if (shownAt == at) clear() }, 3_000)
    }
    fun clear() { message = null; action = null }
}

@Composable
fun UndoBar(modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val sc = LocalScale.current
    val msg = UndoHost.message
    AnimatedVisibility(msg != null, modifier = modifier, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = sc.margin, vertical = 8.dp).shadow(8.dp, RoundedCornerShape(18.dp)).clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF2C2C2E)).padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(msg ?: "", color = Color.White, fontSize = sc.body, modifier = Modifier.weight(1f))
            Box(
                Modifier.heightIn(min = sc.target - 8.dp).clip(RoundedCornerShape(12.dp)).steady("Undo") { UndoHost.action?.invoke(); UndoHost.clear() }.padding(horizontal = 18.dp),
                contentAlignment = Alignment.Center,
            ) { Text("Undo", color = Color(0xFF7DD3C8), fontWeight = FontWeight.SemiBold, fontSize = sc.button) }
        }
    }
}

// ───────────────────────── screen frame ─────────────────────────

/**
 * Every screen: optional Back, the title, content, an optional sticky action area, and the bottom navigation.
 *
 * [actions] stay pinned above the navigation (the main action is always in reach, never scrolled away).
 * The bottom navigation holds Read aloud; where there is no navigation (setup, helper phones, alerts) Read
 * aloud is a round button in the corner instead. Read aloud is hidden when the person turned it off.
 */
@Composable
fun Screen(
    title: String,
    speak: String,
    onHome: (() -> Unit)?,
    onBack: (() -> Unit)? = null,
    scroll: Boolean = true,
    background: Color? = null,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    actions: (@Composable ColumnScope.() -> Unit)? = null,
    eyebrow: String? = null,
    /** Beside the title and subtitle, centred on both (Home's Me | Helping switch). */
    side: (@Composable () -> Unit)? = null,
    /** A thin strip across the whole width, right under the status bar: how up to date the records are. */
    banner: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val p = LocalPalette.current
    val s = LocalSettings.current
    val sc = LocalScale.current
    val ctx = LocalContext.current
    val nav = LocalNav.current
    val full = "$title. $speak"
    // the bottom bar only on the main pages; deeper pages get the room back, with SOS up by Read instead
    val here = nav?.current
    val topLevel = here == Route.Home || here == Route.Help || here == Route.HelperHome || here == Route.HelperChat || here == Route.Settings
    val hasNav = nav != null && topLevel
    val sosUp = nav != null && !topLevel && here != Route.Emergency
    val typing = imeShowing()
    LaunchedEffect(full) { ReadAloud.text = full }
    LaunchedEffect(title) { if (s.autoRead && s.readAloud) { delay(350); ctx.medlog.speaker.say(full) } }
    Box(Modifier.fillMaxSize().background(background ?: p.paper)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            banner?.invoke()
            // the title, the Read switch and Back stay put; only what's under them scrolls
            // one header for every page, the same size everywhere so nothing jumps between pages:
            // a fixed row (Back or a greeting on the left, Read on the right), a one-line title, and a line for the subtitle
            val header: @Composable ColumnScope.() -> Unit = {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp).height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (onBack != null) { BackLink(onBack); Spacer(Modifier.weight(1f)) }
                    // the greeting takes what the buttons leave, and wraps; the buttons are never squeezed
                    else if (!eyebrow.isNullOrBlank()) Text(tr(eyebrow).uppercase(), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = p.inkSoft, letterSpacing = 1.2.sp,
                        modifier = Modifier.weight(1f).padding(end = 8.dp))
                    else Spacer(Modifier.weight(1f))
                    // on a page with a greeting (Home), its one extra button sits up here, level with the greeting
                    val up = !eyebrow.isNullOrBlank() && trailing != null
                    if (up) { trailing?.invoke(); Spacer(Modifier.width(10.dp)) }
                    if (sosUp) { SosPill(); Spacer(Modifier.width(10.dp)) }
                    // the main pages keep their top corner clear: Read is in Settings (and on every other page)
                    if (!topLevel || nav == null) ReadToggle()
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(title, fontSize = sc.title, fontWeight = FontWeight.Bold, color = p.ink, lineHeight = sc.title * 1.15f,
                                modifier = Modifier.weight(1f).semantics { heading() })
                            if (eyebrow.isNullOrBlank()) trailing?.invoke()
                        }
                        Text(subtitle ?: " ", fontSize = sc.body, color = if (eyebrow != null) p.ink else p.inkSoft, fontWeight = if (eyebrow != null) FontWeight.Medium else null,
                            modifier = Modifier.padding(top = 2.dp))
                    }
                    if (side != null) { Spacer(Modifier.width(12.dp)); side() }
                }
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = sc.margin).padding(bottom = sc.gap), content = header)
            val state = rememberScrollState()
            Box(Modifier.weight(1f).fillMaxWidth()) {
                val inner = Modifier.fillMaxSize().padding(horizontal = sc.margin)
                Column(if (scroll) inner.verticalScroll(state) else inner, verticalArrangement = Arrangement.spacedBy(sc.gap)) {
                    content()
                    // room so the Read aloud button never covers the last row
                    if (scroll) Spacer(Modifier.height(24.dp))
                }
                if (scroll && !typing) MoreBelow(state, Modifier.align(Alignment.BottomCenter))
            }
            if (actions != null) ActionArea(actions)
            // while typing, the keyboard needs the room: the bottom bar steps aside
            if (hasNav && !typing) BottomBar(onHome)
        }
        UndoBar(Modifier.align(Alignment.BottomCenter).padding(bottom = sc.target + 28.dp).navigationBarsPadding())
    }
}

/**
 * The thin strip under the status bar: one line, edge to edge. Grey when all is well, amber when something is
 * waiting, red when it failed.
 */
@Composable
fun TopBanner(text: String, tone: Color? = null, icon: ImageVector = Icons.Rounded.Sync) {
    val p = LocalPalette.current
    val sc = LocalScale.current
    val (bg, fg) = when (tone) { p.amber -> p.amberSoft to p.amber; p.red -> p.redSoft to p.red; else -> p.fill to p.inkSoft }
    Row(Modifier.fillMaxWidth().background(bg).padding(horizontal = sc.margin, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, fontSize = sc.small * 0.9f, fontWeight = FontWeight.SemiBold, color = fg)
    }
}

/** SOS in the top corner, on pages without the bottom bar: always one tap away, never taking the page's room. */
@Composable
fun SosPill() {
    val p = LocalPalette.current
    val sc = LocalScale.current
    val nav = LocalNav.current ?: return
    Box(Modifier.height(48.dp).clip(RoundedCornerShape(24.dp)).background(p.red).steady("SOS, emergency help") { nav.go(Route.Emergency) }.padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center) {
        Text("SOS", color = Color.White, fontSize = sc.small, fontWeight = FontWeight.ExtraBold)
    }
}

/**
 * Me | Helping as two icons beside the name: switches at once, no question. Each side shows its own colour
 * (teal for my health, blue for helping), the same colour the whole app takes in that mode.
 */
@Composable
fun ModeSwitch(helping: Boolean, onChange: (Boolean) -> Unit) {
    val p = LocalPalette.current
    Row(Modifier.clip(RoundedCornerShape(26.dp)).background(p.fill).padding(3.dp)) {
        listOf(Triple(false, Icons.Rounded.Person, "My health"), Triple(true, Icons.Rounded.Groups, "Helping someone")).forEach { (h, icon, label) ->
            val on = h == helping
            val tint = if (h) HELPER_BRAND else MY_BRAND
            Box(Modifier.size(48.dp).clip(CircleShape).background(if (on) tint else Color.Transparent)
                .semantics { role = Role.Tab; selected = on }.steady(label + if (on) ", chosen" else "") { if (!on) onChange(h) },
                contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = if (on) Color.White else p.inkSoft, modifier = Modifier.size(24.dp))
            }
        }
    }
}

/** True while the on-screen keyboard is up. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun imeShowing(): Boolean = androidx.compose.foundation.layout.WindowInsets.isImeVisible && android.os.Build.FINGERPRINT != "robolectric"

/** The pinned area for a screen's main action: a thin line above, then the buttons. */
@Composable
fun ActionArea(content: @Composable ColumnScope.() -> Unit) {
    val p = LocalPalette.current
    val sc = LocalScale.current
    // the page's main action on its own white band, so it stands apart from the page and the bar under it
    Column(Modifier.fillMaxWidth().background(p.card)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(p.line))
        Column(Modifier.fillMaxWidth().padding(horizontal = sc.margin, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            androidx.compose.runtime.CompositionLocalProvider(LocalOnCard provides true) { content() }
        }
    }
}

/**
 * True inside a white card. Quiet controls (grey buttons, segmented choices, chips) take the opposite surface of
 * what they sit on: grey on a card, white (lifted by a soft shadow) on the page, so they never melt into it.
 */
val LocalOnCard = androidx.compose.runtime.compositionLocalOf { false }

/** A soft shadow and a faint edge (8% black, never a heavy frame) that lift a white surface off the page. */
fun Modifier.lift(shape: Shape, on: Boolean = true): Modifier = if (on) this.shadow(1.5.dp, shape, clip = false, ambientColor = Color(0x33000000), spotColor = Color(0x33000000))
    .border(1.dp, Color(0x14000000), shape) else this

/** True when a task page is shown inside a sheet: then its top has only Close. */
val LocalInSheet = androidx.compose.runtime.compositionLocalOf { false }

/** Set by first-time setup: "Skip" on any page without its own second button (opens the skip panel). */
val LocalFlowSkip = androidx.compose.runtime.compositionLocalOf<(() -> Unit)?> { null }

/** True inside a chosen (accent-filled) option, so pictures and words switch to their light versions. */
val LocalOnAccent = androidx.compose.runtime.compositionLocalOf { false }

/**
 * Read aloud, as a toggle in the same top-right place on every screen. On: every page is read out by itself,
 * and stays on until turned off. Off: silent. Tapping it on also reads the page you are on.
 */
@Composable
fun ReadToggle(modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val sc = LocalScale.current
    val s = LocalSettings.current
    val ctx = LocalContext.current
    if (!s.readAloud) return
    val on = s.autoRead
    val sh = RoundedCornerShape(24.dp)
    Row(
        modifier.height(48.dp).clip(sh).background(if (on) p.ok else p.card)
            .semantics { role = Role.Switch; stateDescription = if (on) "On" else "Off" }
            .steady("Read aloud") {
                val now = !on
                ctx.medlog.settings.update { it.copy(autoRead = now) }
                if (now) ctx.medlog.speaker.say(ReadAloud.text) else ctx.medlog.speaker.stop()
            }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(if (on) Icons.Rounded.VolumeUp else Icons.Rounded.VolumeOff, null, tint = if (on) Color.White else p.ink, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(6.dp))
        Text("Read", fontSize = sc.small, fontWeight = FontWeight.Bold, color = if (on) Color.White else p.ink, maxLines = 1)
    }
}

@Composable
private fun BackLink(onBack: () -> Unit) {
    val p = LocalPalette.current
    val sc = LocalScale.current
    @Suppress("UNUSED_VARIABLE") val unused = sc
    val sh = RoundedCornerShape(24.dp)
    Box(Modifier.size(48.dp).lift(sh).clip(sh).background(p.card).steady("Back", onClick = onBack), contentAlignment = Alignment.Center) {
        Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, tint = p.ink, modifier = Modifier.size(24.dp))
    }
}

/** The person's navigation: Home, History, SOS, Family, and Read aloud (when on). Same places, same order, always. */
@Composable
fun BottomBar(@Suppress("UNUSED_PARAMETER") onHome: (() -> Unit)?) {
    val p = LocalPalette.current
    val s = LocalSettings.current
    val sc = LocalScale.current
    val ctx = LocalContext.current
    val nav = LocalNav.current ?: return
    val speaking by ctx.medlog.speaker.speaking.collectAsState()
    val read = { if (speaking) ctx.medlog.speaker.stop() else ctx.medlog.speaker.say(ReadAloud.text) }
    Column(Modifier.fillMaxWidth().background(p.paper)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(p.line))
        val here = nav.current
        // the same three places on both kinds of phone: home, SOS in the middle, and the people around you
        val helper = s.role == "helper"
        val home = if (helper) Route.HelperHome else Route.Home
        val people = if (helper) Route.HelperChat else Route.Help
        val tabs = listOfNotNull(
            Triple("Home", Icons.Rounded.Home, here == home) to { nav.home(home) },
            Triple(if (helper) "Helpers" else "Family", Icons.Rounded.Groups, here == people) to { nav.home(home); if (people != home) nav.go(people) },
            Triple("SOS", Icons.Rounded.Sos, here == Route.Emergency) to { if (here != Route.Emergency) nav.go(Route.Emergency) },
            Triple("Settings", Icons.Rounded.Settings, here == Route.Settings) to { nav.home(home); nav.go(Route.Settings) },
        )
        val ordered = if (s.leftHand) tabs.reversed() else tabs
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp)) {
            ordered.forEach { (t, go) ->
                val (label, icon, on) = t
                if (label == "SOS") {
                    // the one thing that must always be reachable: a solid red button, bigger than the rest
                    Column(
                        Modifier.weight(1f).heightIn(min = sc.target + 6.dp).clip(RoundedCornerShape(16.dp)).steady("SOS, emergency help", onClick = go),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                    ) {
                        Box(Modifier.size(if (sc.big) 62.dp else 58.dp).clip(RoundedCornerShape(18.dp)).background(p.red), contentAlignment = Alignment.Center) {
                            Text("SOS", color = Color.White, fontSize = sc.body, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                    return@forEach
                }
                val tint = if (on) p.brand else p.inkSoft
                // the current place: a quiet grey square, the icon in the accent, the word in bold dark
                Column(
                    Modifier.weight(1f).padding(horizontal = 3.dp).heightIn(min = sc.target + 6.dp).clip(RoundedCornerShape(16.dp))
                        .background(if (on) p.fill else Color.Transparent).steady(label, onClick = go).padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                ) {
                    Icon(icon, null, tint = tint, modifier = Modifier.size(if (sc.big) 30.dp else 26.dp))
                    Spacer(Modifier.height(3.dp))
                    Text(label, fontSize = sc.small * 0.9f, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, color = if (on) p.ink else p.inkSoft, maxLines = 1)
                }
            }
        }
    }
}

@Composable
fun PillButton(text: String, icon: ImageVector?, bg: Color, fg: Color, border: Color? = null, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val sc = LocalScale.current
    var pressed by remember { mutableStateOf(false) }
    Row(
        modifier.scale(pressScale(pressed)).heightIn(min = sc.target).clip(RoundedCornerShape(16.dp)).background(bg)
            .then(if (border != null) Modifier.border(1.dp, border, RoundedCornerShape(16.dp)) else Modifier)
            .steady(text, onPress = { pressed = it }, onClick = onClick).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) { Icon(icon, null, tint = fg, modifier = Modifier.size(24.dp)); Spacer(Modifier.width(10.dp)) }
        Text(text, color = fg, fontSize = sc.button, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun RoundIcon(icon: ImageVector, label: String, onClick: () -> Unit) {
    val p = LocalPalette.current
    Box(
        Modifier.size(48.dp).clip(CircleShape).background(p.card).steady(label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = p.inkSoft, modifier = Modifier.size(28.dp)) }
}

// ───────────────────────── buttons ─────────────────────────

enum class Tone { PRIMARY, SECONDARY, OUTLINE, TINT, DANGER, OK, QUIET, AMBER }

@Composable
fun BigButton(
    text: String,
    modifier: Modifier = Modifier,
    tone: Tone = Tone.PRIMARY,
    icon: ImageVector? = null,
    sub: String? = null,
    height: Dp? = null,
    enabled: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    val p = LocalPalette.current
    val sc = LocalScale.current
    // PRIMARY is the one main action on a screen. SECONDARY is white with a grey outline, OUTLINE a grey outline with teal words, TINT a light teal fill with teal words, QUIET is a grey fill:
    // both clearly less important, never competing with the accent.
    val onCard = LocalOnCard.current
    val quiet = if (onCard) p.fill else p.card
    val (bg, fg) = when (tone) {
        Tone.PRIMARY -> p.brand to p.onBrand
        Tone.SECONDARY -> quiet to p.ink
        Tone.OUTLINE -> quiet to p.ink
        Tone.TINT -> quiet to p.ink
        Tone.DANGER -> p.red to Color.White
        Tone.OK -> p.ok to Color.White
        Tone.AMBER -> p.amberSoft to p.amber
        Tone.QUIET -> quiet to p.ink
    }
    var pressed by remember { mutableStateOf(false) }
    val left = leading != null || sub != null
    Row(
        modifier.fillMaxWidth().scale(pressScale(pressed)).heightIn(min = height ?: sc.target)
            .lift(RoundedCornerShape(16.dp), on = !onCard && enabled && tone in setOf(Tone.SECONDARY, Tone.OUTLINE, Tone.TINT, Tone.QUIET)).clip(RoundedCornerShape(16.dp))
            .background(if (enabled) bg else p.fill.copy(alpha = 0.5f))
            
            .steady(text + (sub?.let { ". $it" } ?: ""), enabled, onPress = { pressed = it }, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (left) Arrangement.Start else Arrangement.Center,
    ) {
        leading?.let { it(); Spacer(Modifier.width(14.dp)) }
        if (icon != null) { Icon(icon, null, tint = if (enabled) fg else p.inkSoft, modifier = Modifier.size(24.dp)); Spacer(Modifier.width(10.dp)) }
        Column(Modifier.then(if (left) Modifier.weight(1f) else Modifier)) {
            Text(text, color = if (enabled) fg else p.inkSoft, fontSize = sc.button, fontWeight = FontWeight.SemiBold, lineHeight = sc.button * 1.2f, maxLines = 3,
                textAlign = if (left) TextAlign.Start else TextAlign.Center)
            if (sub != null) Text(sub, color = (if (enabled) fg else p.inkSoft).copy(alpha = 0.8f), fontSize = sc.small, lineHeight = sc.small * 1.25f)
        }
    }
}

/**
 * Yes and No, never looking alike: Yes is green with a tick, No is white with a cross. Colour, symbol and word
 * all differ, so it works for colour-blind eyes too. Equal size, side by side.
 */
@Composable
fun YesNo(yes: String = "Yes", no: String = "No", onYes: () -> Unit, onNo: () -> Unit) {
    val sc = LocalScale.current
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        BigButton(yes, Modifier.weight(1f), Tone.OK, icon = Icons.Rounded.Check, height = sc.target * 1.4f, onClick = onYes)
        BigButton(no, Modifier.weight(1f), Tone.SECONDARY, icon = Icons.Rounded.Close, height = sc.target * 1.4f, onClick = onNo)
    }
}

/** The question on a question page: the largest words on the screen, with an optional line of help under it. */
@Composable
fun Question(text: String, hint: String? = null, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val sc = LocalScale.current
    Column(modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp)) {
        Text(text, fontSize = sc.question, fontWeight = FontWeight.Bold, color = p.ink, lineHeight = sc.question * 1.18f, modifier = Modifier.semantics { heading() })
        if (hint != null) Text(hint, fontSize = sc.body, color = p.inkSoft, lineHeight = sc.body * 1.35f, modifier = Modifier.padding(top = 6.dp))
    }
}

/**
 * One answer in a list of answers (NHS radios and checkboxes): the mark on the left, the words next to it, the
 * whole row tappable. Chosen: accent outline, light accent fill, filled mark with a tick. Not chosen: white,
 * grey outline, empty mark. [multi] shows square marks (pick any), otherwise round (pick one).
 */
@Composable
fun Choice(text: String, selected: Boolean, multi: Boolean = false, sub: String? = null, modifier: Modifier = Modifier, icon: ImageVector? = null, tint: Color? = null, onClick: () -> Unit) {
    val p = LocalPalette.current
    val sc = LocalScale.current
    val sh = RoundedCornerShape(14.dp)
    var pressed by remember { mutableStateOf(false) }
    Row(
        modifier.fillMaxWidth().scale(pressScale(pressed)).heightIn(min = sc.target + 8.dp)
            .clip(sh)
            .background(if (selected) p.brandSoft else p.card).then(if (selected) Modifier.border(3.dp, p.brand, sh) else Modifier)
            .steady(text + if (selected) ", chosen" else ", not chosen", onPress = { pressed = it }, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            val t = tint ?: p.brand
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(13.dp)).background(if (selected) Color.White else t.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = t, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(text, fontSize = sc.body, fontWeight = FontWeight.SemiBold, color = p.ink, lineHeight = sc.body * 1.25f)
            if (sub != null) Text(sub, fontSize = sc.small, color = p.inkSoft, lineHeight = sc.small * 1.3f)
        }
    }
}

/**
 * The one text scale for cards, so every page reads the same:
 * page title (sc.title) > section title (sc.headline) > card title ([cardTitle]) > body > caption (sc.small, grey).
 */
val Scale.cardTitle get() = headline * 0.9f

/** A section title with a short caption under it (how many, when last used) and one small action on its right. */
@Composable
fun SectionHeader(title: String, caption: String, action: String?, actionIcon: ImageVector? = null, onAction: () -> Unit = {}) {
    val p = LocalPalette.current
    val sc = LocalScale.current
    Row(Modifier.fillMaxWidth().padding(top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = sc.headline, fontWeight = FontWeight.Bold, color = p.ink, modifier = Modifier.semantics { heading() })
            Text(caption, fontSize = sc.small * 0.88f, color = p.inkSoft)
        }
        if (action != null) Row(Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp)).background(p.fill).steady("$action: $title", onClick = onAction)
            .padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (actionIcon != null) { Icon(actionIcon, null, tint = p.ink, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(6.dp)) }
            Text(action, fontSize = sc.small, fontWeight = FontWeight.SemiBold, color = p.ink)
        }
    }
}

/**
 * One part of a longer form, in its own white container: an icon, the part's name and what's chosen so far, and a
 * chevron. Tap the head to open or close it; only what's inside the open one needs reading.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun Panel(title: String, icon: ImageVector, tint: Color, summary: String, open: Boolean, onToggle: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val p = LocalPalette.current
    val sc = LocalScale.current
    val still = LocalSettings.current.lessMotion
    val sh = RoundedCornerShape(sc.radius)
    // opening: the body grows down from the header, then its contents fade in; closing is the same, reversed and a little quicker
    val ease = androidx.compose.animation.core.FastOutSlowInEasing
    fun <T> t(ms: Int, delay: Int = 0) = androidx.compose.animation.core.tween<T>(if (still) 0 else ms, if (still) 0 else delay, ease)
    val keep = remember { androidx.compose.foundation.relocation.BringIntoViewRequester() }
    LaunchedEffect(open) { if (open) { delay(if (still) 0 else 300); keep.bringIntoView() } }
    Column(Modifier.fillMaxWidth().bringIntoViewRequester(keep).clip(sh).background(p.card)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).steady("$title, $summary. ${if (open) "Close" else "Open"}", onClick = onToggle).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            OptionIcon(icon, tint, 44.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = sc.cardTitle, fontWeight = FontWeight.Bold, color = p.ink)
                Text(summary, fontSize = sc.small, color = p.inkSoft)
            }
            val turn by androidx.compose.animation.core.animateFloatAsState(if (open) 180f else 0f, t(260), label = "chevron")
            Icon(Icons.Rounded.ExpandMore, null, tint = p.inkSoft, modifier = Modifier.size(28.dp).graphicsLayer { rotationZ = turn })
        }
        androidx.compose.animation.AnimatedVisibility(open,
            enter = androidx.compose.animation.expandVertically(t(280), expandFrom = Alignment.Top) + androidx.compose.animation.fadeIn(t(200, 100)),
            exit = androidx.compose.animation.fadeOut(t(120)) + androidx.compose.animation.shrinkVertically(t(240, 40), shrinkTowards = Alignment.Top)) {
            Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(p.line))
                content()
            }
        }
    }
}

/** "Add …" as a dashed card: the empty place where the first item will go. */
@Composable
fun DashedAddCard(text: String, onClick: () -> Unit) {
    val p = LocalPalette.current
    val sc = LocalScale.current
    Row(Modifier.fillMaxWidth().heightIn(min = 88.dp).clip(RoundedCornerShape(sc.radius))
        .drawBehind { drawRoundRect(p.brand.copy(alpha = 0.7f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(sc.radius.toPx()),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(14f, 10f)))) }
        .steady(text, onClick = onClick), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        Icon(Icons.Rounded.Add, null, tint = p.brand, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, fontSize = sc.button, fontWeight = FontWeight.SemiBold, color = p.brand)
    }
}

/** The radio (round) or checkbox (square) mark used by [Choice] and [Tile]. */
@Composable
fun Mark(selected: Boolean, multi: Boolean, size: Dp = 30.dp) {
    val p = LocalPalette.current
    val shape = if (multi) RoundedCornerShape(7.dp) else CircleShape
    Box(
        Modifier.size(size).clip(shape).background(if (selected) p.brand else p.paper).border(2.dp, if (selected) p.brand else p.inkSoft, shape),
        contentAlignment = Alignment.Center,
    ) { if (selected) Icon(Icons.Rounded.Check, null, tint = p.onBrand, modifier = Modifier.size(size * 0.7f)) }
}

/** Hold for [holdMs] to confirm (SOS). The fill shows how long is left. */
@Composable
fun HoldButton(text: String, holdText: String, modifier: Modifier = Modifier, holdMs: Long = 2000, tone: Tone = Tone.DANGER, icon: ImageVector? = null, onDone: () -> Unit, onTap: () -> Unit = {}) {
    val p = LocalPalette.current
    val sc = LocalScale.current
    var holding by remember { mutableStateOf(false) }
    val progress by animateFloatAsState(if (holding) 1f else 0f, tween(if (holding) holdMs.toInt() else 150), label = "hold") {
        if (it >= 1f && holding) { holding = false; onDone() }
    }
    val bg = if (tone == Tone.DANGER) p.red else p.brand
    Box(
        modifier.fillMaxWidth().height(sc.target * 1.5f).clip(RoundedCornerShape(sc.radius)).background(bg)
            .semantics { role = Role.Button; contentDescription = "$text. $holdText"; onClick(text) { onDone(); true } }
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    holding = true
                    val released = tryAwaitRelease()
                    holding = false
                    if (released && progress < 0.15f) onTap()
                })
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(progress.coerceAtLeast(0.001f)).background(Color.Black.copy(alpha = 0.22f)))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 22.dp)) {
            if (icon != null) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(40.dp)); Spacer(Modifier.width(16.dp)) }
            Column {
                Text(text, color = Color.White, fontSize = sc.headline * 1.2f, fontWeight = FontWeight.Bold)
                Text(holdText, color = Color.White.copy(alpha = 0.9f), fontSize = sc.small)
            }
        }
    }
}

// ───────────────────────── text & surfaces ─────────────────────────

/** Section heading inside a screen. */
@Composable
fun Title(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier.padding(top = 20.dp).semantics { heading() }, fontSize = LocalScale.current.headline, fontWeight = FontWeight.Bold, color = LocalPalette.current.ink)
}

@Composable
fun Body(text: String, modifier: Modifier = Modifier, color: Color? = null, center: Boolean = false, bold: Boolean = false) {
    Text(text, modifier, color = color ?: LocalPalette.current.ink, fontSize = LocalScale.current.body,
        textAlign = if (center) TextAlign.Center else TextAlign.Start, fontWeight = if (bold) FontWeight.SemiBold else null)
}

@Composable
fun Hint(text: String, modifier: Modifier = Modifier, center: Boolean = false) {
    Text(text, modifier, color = LocalPalette.current.inkSoft, fontSize = LocalScale.current.small, lineHeight = LocalScale.current.small * 1.35f,
        textAlign = if (center) TextAlign.Center else TextAlign.Start)
}

/** A white card that lifts off the warm page with a soft shadow. A coloured [border] marks warnings. */
@Composable
fun Card(modifier: Modifier = Modifier, color: Color? = null, border: Color? = null, shape: Shape? = null, padding: PaddingValues = PaddingValues(18.dp), onClick: (() -> Unit)? = null, label: String = "", content: @Composable ColumnScope.() -> Unit) {
    val p = LocalPalette.current
    val sh = shape ?: RoundedCornerShape(LocalScale.current.radius)
    var pressed by remember { mutableStateOf(false) }
    val white = (color ?: p.card) == p.card
    Column(
        modifier.fillMaxWidth().scale(pressScale(pressed))
            .lift(sh, on = white)
            .clip(sh).background(color ?: p.card)
            .then(when {
                border == null -> Modifier
                border == p.line || border == p.outline -> Modifier
                else -> Modifier
            })
            .then(if (onClick != null) Modifier.steady(label, onPress = { pressed = it }, onClick = onClick) else Modifier)
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) { androidx.compose.runtime.CompositionLocalProvider(LocalOnCard provides white) { content() } }
}

/** A row: grey icon tile, title, optional subtitle, chevron. */
@Composable
fun ListRow(title: String, sub: String? = null, icon: ImageVector? = null, tint: Color? = null, leading: (@Composable () -> Unit)? = null, onClick: () -> Unit) {
    val p = LocalPalette.current
    val sc = LocalScale.current
    var pressed by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().scale(pressScale(pressed)).heightIn(min = sc.target + 8.dp)
            
            .lift(RoundedCornerShape(sc.radius)).clip(RoundedCornerShape(sc.radius)).background(p.card)
            .steady(title + (sub?.let { ". $it" } ?: ""), onPress = { pressed = it }, onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) { leading(); Spacer(Modifier.width(14.dp)) }
        else if (icon != null) { IconTile(icon, tint ?: p.ink, 44.dp); Spacer(Modifier.width(14.dp)) }
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = sc.body * 1.05f, fontWeight = FontWeight.SemiBold, color = p.ink)
            if (sub != null) Text(sub, fontSize = sc.small, color = p.inkSoft)
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = p.inkSoft.copy(alpha = 0.6f), modifier = Modifier.size(28.dp))
    }
}

/**
 * A feature's icon: a solid square in the feature's own colour with a white glyph, so it is found by colour and
 * shape at a glance. An ink tint (no feature colour) gets a soft cream square instead.
 */
@Composable
fun IconTile(icon: ImageVector, tint: Color, size: Dp) {
    val p = LocalPalette.current
    val plain = tint == p.ink || tint == p.inkSoft
    val sh = RoundedCornerShape(size * 0.3f)
    Box(
        Modifier.size(size)
            .clip(sh).background(if (plain) p.fill else tint),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = if (plain) p.ink else Color.White, modifier = Modifier.size(size * 0.5f))
    }
}

/** Set by [TileGrid] when it lays tiles out: the height every tile gets, and the height the tallest tile needs. */
data class TileFit(val height: Dp, val tallest: Dp)
val LocalTileFit = androidx.compose.runtime.compositionLocalOf<TileFit?> { null }

/**
 * Tiles in a grid, all exactly the same size: every tile is first measured at its natural height, and all of them get
 * the tallest one's (never less than [aspect] allows). So the longest label, at the largest text size, sets the size
 * for all, and nothing is ever cut off or squeezed.
 */
@Composable
fun <T> TileGrid(items: List<T>, cols: Int, aspect: Float = 1f, gap: Dp = 12.dp, minHeight: Dp = 0.dp, fixedCols: Boolean = false, tile: @Composable (T, Modifier) -> Unit) {
    androidx.compose.ui.layout.SubcomposeLayout { c ->
        val g = gap.roundToPx()
        // the widest single word any label has: fewer columns rather than a word broken in two ("Bleedin / g")
        val need = TileNeed()
        subcompose("need") { CompositionLocalProvider(LocalTileNeed provides need) { items.forEach { tile(it, Modifier) } } }
        var cols = cols
        while (!fixedCols && cols > 2 && need.widest > (c.maxWidth - g * (cols - 1)) / cols) cols--
        val w = ((c.maxWidth - g * (cols - 1)) / cols).coerceAtLeast(0)
        val natural = subcompose("measure") { items.forEach { tile(it, Modifier) } }
            .map { it.measure(androidx.compose.ui.unit.Constraints(minWidth = w, maxWidth = w)) }
        val tallest = natural.maxOfOrNull { it.height } ?: 0
        val h = maxOf(tallest, (w / aspect).toInt(), minHeight.roundToPx())
        val fit = TileFit(h.toDp(), tallest.toDp())
        val placed = subcompose("place") {
            CompositionLocalProvider(LocalTileFit provides fit) { items.forEach { tile(it, Modifier) } }
        }.map { it.measure(androidx.compose.ui.unit.Constraints.fixed(w, h)) }
        val rows = (items.size + cols - 1) / cols
        layout(c.maxWidth, (rows * h + (rows - 1).coerceAtLeast(0) * g)) {
            placed.forEachIndexed { i, pl -> pl.place((i % cols) * (w + g), (i / cols) * (h + g)) }
        }
    }
}

/**
 * The one picture-and-label tile. The picture sits on the same line in every tile of the grid, and the label is
 * centred in a box as tall as the longest label needs. Labels are never cut: they wrap, and long words hyphenate.
 */
@Composable
fun PicTile(label: String, modifier: Modifier, picture: Dp, selected: Boolean = false, speak: String = label, color: Color? = null,
            onClick: () -> Unit, under: (@Composable () -> Unit)? = null, pic: @Composable () -> Unit) {
    val p = LocalPalette.current
    val sc = LocalScale.current
    val fit = LocalTileFit.current
    // tell the grid how wide the longest word is, so it never has to break one
    LocalTileNeed.current?.let { need ->
        val m = androidx.compose.ui.text.rememberTextMeasurer()
        val style = androidx.compose.ui.text.TextStyle(fontSize = sc.small, fontWeight = FontWeight.SemiBold)
        // the label as shown (translated), in the script's own font where it has one
        val shown = tr(label)
        val script = LocalScript.current
        val st = if (script.indic) style.copy(fontFamily = script.family) else style
        val px = remember(shown, sc.small) { shown.split(' ', '\n').maxOfOrNull { w -> m.measure(w, st).size.width } ?: 0 }
        val pad = with(androidx.compose.ui.platform.LocalDensity.current) { (TILE_PAD * 2 + 4.dp).roundToPx() }
        need.widest = maxOf(need.widest, px + pad)
    }
    Tile(speak, modifier, selected = selected, color = color, onClick = onClick) {
        // everything but the label box has a fixed height, so the label box gets what the tallest tile's label needed
        val fixed = TILE_PAD * 2 + picture + TILE_GAP
        Box(Modifier.size(picture), contentAlignment = Alignment.Center) { pic() }
        Spacer(Modifier.height(TILE_GAP))
        Column(Modifier.fillMaxWidth().then(if (fit != null) Modifier.height(fit.tallest - fixed) else Modifier),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(label, fontSize = sc.small, fontWeight = FontWeight.SemiBold, color = p.ink, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                lineHeight = sc.small * 1.3f, style = androidx.compose.ui.text.TextStyle(hyphens = androidx.compose.ui.text.style.Hyphens.Auto,
                    lineBreak = androidx.compose.ui.text.style.LineBreak.Paragraph))
            under?.invoke()
        }
    }
}

/** Filled while a [TileGrid] works out its columns: the width the widest word in any label needs. */
class TileNeed { var widest = 0 }
val LocalTileNeed = androidx.compose.runtime.compositionLocalOf<TileNeed?> { null }

val TILE_PAD = 12.dp
val TILE_GAP = 10.dp

/**
 * A tappable tile, same size as its neighbours: white with a grey outline. Chosen tiles get the accent outline,
 * a light accent fill and a tick in the corner, so "chosen" never depends on colour alone.
 */
@Composable
fun Tile(label: String, modifier: Modifier, selected: Boolean = false, color: Color? = null, onClick: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val p = LocalPalette.current
    val sc = LocalScale.current
    var pressed by remember { mutableStateOf(false) }
    val sh = RoundedCornerShape(sc.radius)
    Box(
        modifier.scale(pressScale(pressed)).lift(sh, on = !LocalOnCard.current).clip(sh)
            .background(if (selected) p.brandSoft else color ?: p.card).then(if (selected) Modifier.border(3.dp, p.brand, sh) else Modifier)
            .steady(label + if (selected) ", chosen" else "", onPress = { pressed = it }, onClick = onClick),
    ) {
        Column(Modifier.fillMaxSize().padding(TILE_PAD), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center, content = content)
    }
}

/** Green ● OK, Amber ▲ Call doctor today, Red ■ Get help now: colour + shape + word (plan 4.1). */
@Composable
fun LevelMark(level: String, withWord: Boolean = true) {
    val p = LocalPalette.current
    val sc = LocalScale.current
    val (c, shape, word) = when (level) {
        "RED" -> Triple(p.red, "■", "Get help now")
        "AMBER" -> Triple(p.amber, "▲", "Call doctor today")
        else -> Triple(p.ok, "●", "OK")
    }
    if (!withWord) {
        // a symbol alone means nothing to most people: a short word in a pill instead
        val short = when (level) { "RED" -> "Urgent"; "AMBER" -> "Watch"; else -> "OK" }
        Text(short, color = Color.White, fontSize = sc.small, fontWeight = FontWeight.Bold, maxLines = 1,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(c).padding(horizontal = 8.dp, vertical = 2.dp))
        return
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(shape, color = c, fontSize = sc.small)
        Spacer(Modifier.width(6.dp)); Text(word, color = c, fontSize = sc.small, fontWeight = FontWeight.SemiBold)
    }
}

/** A small choice. Chosen: accent fill with a tick. Not chosen: white with a grey outline. */
@Composable
fun Chip(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val p = LocalPalette.current
    val sc = LocalScale.current
    Row(
        modifier.heightIn(min = 52.dp).clip(RoundedCornerShape(26.dp))
            .background(if (selected) p.brand else if (LocalOnCard.current) p.fill else p.card)
            .steady(text + if (selected) ", chosen" else "", onClick = onClick).padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected) { Icon(Icons.Rounded.Check, null, tint = p.onBrand, modifier = Modifier.size(22.dp)); Spacer(Modifier.width(6.dp)) }
        Text(text, color = if (selected) p.onBrand else p.ink, fontSize = sc.body, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun FlowRowOf(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
}

@Composable
fun Gap(h: Dp = 8.dp) = Spacer(Modifier.height(h))

@Composable
fun rememberLaunch(): (suspend () -> Unit) -> Unit {
    val scope = rememberCoroutineScope()
    return { block -> scope.launch { block() } }
}

@Composable
fun RowScope.Fill() = Spacer(Modifier.weight(1f))

@Composable
fun BoxScope.Centered(content: @Composable () -> Unit) = Box(Modifier.align(Alignment.Center)) { content() }

/** Equal-width choices in one pill: every option the same size, never wrapping. */
@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val p = LocalPalette.current
    val sc = LocalScale.current
    // every choice as tall as the tallest: a long label (in Hindi or Tamil, or large words) wraps instead of being cut
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).lift(RoundedCornerShape(16.dp), on = !LocalOnCard.current).clip(RoundedCornerShape(16.dp)).background(if (LocalOnCard.current) p.fill else p.card).padding(4.dp)) {
        options.forEachIndexed { i, o ->
            Box(
                Modifier.weight(1f).fillMaxHeight().heightIn(min = sc.target - 12.dp).clip(RoundedCornerShape(12.dp)).background(if (i == selected) p.brand else Color.Transparent)
                    .steady(o + if (i == selected) ", chosen" else "") { onSelect(i) }.padding(horizontal = 6.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) { Text(o, fontSize = sc.body, fontWeight = if (i == selected) FontWeight.Bold else FontWeight.Medium, color = if (i == selected) p.onBrand else p.inkSoft, textAlign = TextAlign.Center) }
        }
    }
}


// ───────────────────────── flows, groups, values ─────────────────────────

/**
 * A step in a task (setup, telling how you feel, adding a medicine): round Back on the left, the task's name small
 * in the middle, round Close on the right; then progress, the one question in the largest words, the answers,
 * and the main button pinned at the bottom with a quiet second choice under it. Nothing else competes.
 */
@Composable
fun FlowScreen(
    task: String,
    question: String,
    speak: String = "",
    hint: String? = null,
    step: Int? = null, steps: Int? = null,
    onBack: (() -> Unit)? = null,
    onClose: (() -> Unit)? = null,
    primary: String? = null, primaryEnabled: Boolean = true, onPrimary: () -> Unit = {},
    secondary: String? = null, onSecondary: () -> Unit = {},
    center: Boolean = false,
    /** Put the answers in the middle of the free space (for a few big choices) instead of under the question. */
    middle: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val p = LocalPalette.current
    val sc = LocalScale.current
    val s = LocalSettings.current
    val ctx = LocalContext.current
    val full = listOfNotNull(question, hint, speak.ifBlank { null }).joinToString(". ")
    LaunchedEffect(full) { ReadAloud.text = full }
    LaunchedEffect(question) { if (s.autoRead && s.readAloud) { delay(350); ctx.medlog.speaker.say(full) } }
    val scroll = rememberScrollState()
    val inSheet = LocalInSheet.current
    Box(Modifier.fillMaxSize().background(p.paper)) {
        Column(Modifier.fillMaxSize().then(if (inSheet) Modifier else Modifier.statusBarsPadding()).navigationBarsPadding().imePadding()) {
            if (inSheet) {
                // a sheet: a small handle, the task's name, and Close; nothing else up here
                Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(width = 40.dp, height = 5.dp).clip(RoundedCornerShape(3.dp)).background(p.line))
                }
                Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (onClose != null) RoundButton(Icons.Rounded.Close, "Close", onClose) else Spacer(Modifier.size(56.dp))
                    Text(task, fontSize = sc.body, fontWeight = FontWeight.SemiBold, color = p.inkSoft, textAlign = TextAlign.Center, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
                    Spacer(Modifier.size(56.dp))
                }
            } else
            // the top bar keeps its height on every page (empty on a first page), so the title never moves between pages
            Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (onBack != null) RoundButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack) else Spacer(Modifier.size(56.dp))
                Text(if (onBack != null || onClose != null) task else "", fontSize = sc.body, fontWeight = FontWeight.SemiBold, color = p.inkSoft, textAlign = TextAlign.Center, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
                ReadToggle()
                if (onClose != null) { Spacer(Modifier.width(8.dp)); RoundButton(Icons.Rounded.Close, "Close", onClose) }
                else if (!s.readAloud) Spacer(Modifier.size(56.dp))
            }
            Row(Modifier.fillMaxWidth().height(13.dp).padding(horizontal = sc.margin, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (step != null && steps != null) repeat(steps) { i -> Box(Modifier.weight(1f).height(5.dp).clip(RoundedCornerShape(3.dp)).background(if (i < step) p.brand else p.fill)) }
            }
            // The question stays at the top; the answers settle at the bottom, near the thumb and the main button.
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val viewport = maxHeight
                Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = sc.margin)) {
                    Column(Modifier.fillMaxWidth().heightIn(min = viewport), horizontalAlignment = if (center) Alignment.CenterHorizontally else Alignment.Start) {
                        Spacer(Modifier.height(12.dp))
                        Text(question, fontSize = sc.question, fontWeight = FontWeight.Bold, color = p.ink, lineHeight = sc.question * 1.18f,
                            textAlign = if (center) TextAlign.Center else TextAlign.Start, modifier = Modifier.fillMaxWidth().semantics { heading() })
                        if (hint != null) Text(hint, fontSize = sc.body, color = p.inkSoft, lineHeight = sc.body * 1.35f,
                            textAlign = if (center) TextAlign.Center else TextAlign.Start, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                        Spacer(Modifier.height(36.dp))
                        if (middle) Spacer(Modifier.weight(1f))
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(sc.gap / 1.4f),
                            horizontalAlignment = if (center) Alignment.CenterHorizontally else Alignment.Start, content = content)
                        if (middle) Spacer(Modifier.weight(1f))
                        Spacer(Modifier.height(16.dp))
                    }
                }
                MoreBelow(scroll, Modifier.align(Alignment.BottomCenter))
            }
            val skip = LocalFlowSkip.current
            if (inSheet) FlowActions(primary, primaryEnabled, onPrimary, if (onBack != null) "Previous" else null, onBack ?: {})
            else if (secondary == null && skip != null) FlowActions(primary, primaryEnabled, onPrimary, "Skip", skip)
            else FlowActions(primary, primaryEnabled, onPrimary, secondary, onSecondary)
        }
    }
}

/** The bottom of a task page: the main action, then at most one quiet second choice. */
@Composable
fun FlowActions(primary: String?, primaryEnabled: Boolean, onPrimary: () -> Unit, secondary: String?, onSecondary: () -> Unit) {
    val p = LocalPalette.current
    val sc = LocalScale.current
    if (primary == null && secondary == null) return
    // at most two: a small quiet choice on the left (only as wide as its words), the main action filling the rest
    Row(Modifier.fillMaxWidth().padding(horizontal = sc.margin, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        if (secondary != null) {
            Text(secondary, fontSize = sc.body, fontWeight = FontWeight.SemiBold, color = p.brand, maxLines = 1,
                modifier = Modifier.heightIn(min = sc.target).clip(RoundedCornerShape(16.dp))
                    .background(p.fill).steady(secondary, onClick = onSecondary)
                    .padding(horizontal = 20.dp).wrapContentHeight(Alignment.CenterVertically))
            if (primary != null) Spacer(Modifier.width(20.dp))
        }
        if (primary != null) BigButton(primary, Modifier.weight(1f), enabled = primaryEnabled, onClick = onPrimary)
        else Spacer(Modifier.weight(1f))
    }
}

/** When a page continues below the edge: a soft fade and a "More below" button, so it never looks finished. */
@Composable
fun MoreBelow(scroll: ScrollState, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val sc = LocalScale.current
    val scope = rememberCoroutineScope()
    if (!scroll.canScrollForward) return
    // a thin strip along the bottom edge, not a button: a soft fade and a small "More below", tap to scroll
    Box(modifier.fillMaxWidth().height(48.dp).background(Brush.verticalGradient(listOf(p.paper.copy(alpha = 0f), p.paper.copy(alpha = 0.92f), p.paper)))
        .steady("More below") { scope.launch { scroll.animateScrollTo(scroll.value + (scroll.viewportSize * 0.8f).toInt()) } },
        contentAlignment = Alignment.BottomCenter) {
        Row(Modifier.padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("More below", fontSize = sc.small * 0.9f, fontWeight = FontWeight.SemiBold, color = p.inkSoft)
            Icon(Icons.Rounded.KeyboardArrowDown, null, tint = p.inkSoft, modifier = Modifier.size(20.dp))
        }
    }
}

/** One big option, for questions with only two answers. */
data class BigOption(val title: String, val sub: String?, val selected: Boolean, val visual: @Composable () -> Unit, val onClick: () -> Unit)

/**
 * Two answers as two large cards side by side: big targets low on the screen, a picture on each, and a clear
 * chosen state (accent outline, soft accent fill, a tick). Uses the room that two small rows would waste.
 */
@Composable
fun ChoicePair(a: BigOption, b: BigOption, vertical: Boolean = false) {
    if (vertical) {
        // stacked: each option gets a full-width card with room to breathe, its picture and words centred
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            BigOptionCard(a, Modifier.fillMaxWidth().heightIn(min = if (LocalScale.current.big) 210.dp else 190.dp))
            BigOptionCard(b, Modifier.fillMaxWidth().heightIn(min = if (LocalScale.current.big) 210.dp else 190.dp))
        }
        return
    }
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        BigOptionCard(a, Modifier.weight(1f).fillMaxHeight())
        BigOptionCard(b, Modifier.weight(1f).fillMaxHeight())
    }
}

@Composable
private fun BigOptionCard(o: BigOption, modifier: Modifier) {
    val p = LocalPalette.current
    val sc = LocalScale.current
    val sh = RoundedCornerShape(24.dp)
    var pressed by remember { mutableStateOf(false) }
    Box(
        modifier.heightIn(min = if (sc.big) 230.dp else 200.dp).scale(pressScale(pressed))
            
            .clip(sh).background(if (o.selected) p.brandSoft else p.card)
            .then(if (o.selected) Modifier.border(3.dp, p.brand, sh) else Modifier)
            .steady(o.title + (o.sub?.let { ". $it" } ?: "") + if (o.selected) ", chosen" else "", onPress = { pressed = it }, onClick = o.onClick),
        contentAlignment = Alignment.Center,          // the picture and words sit in the middle of the card
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            androidx.compose.runtime.CompositionLocalProvider(LocalOnAccent provides o.selected) { o.visual() }
            Spacer(Modifier.height(14.dp))
            Text(o.title, fontSize = sc.headline, fontWeight = FontWeight.Bold, color = p.ink, textAlign = TextAlign.Center)
            if (o.sub != null) Text(o.sub, fontSize = sc.small, color = p.inkSoft, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

/**
 * Many short answers, pick any: two columns of equal tiles (same height in a row), the words only.
 * Chosen: deeper teal tint and a teal outline. More of the list fits on one screen.
 */
@Composable
fun ChoiceGrid(items: List<String>, isOn: (String) -> Boolean, onToggle: (String) -> Unit) {
    val p = LocalPalette.current
    val sc = LocalScale.current
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { item ->
                    val on = isOn(item)
                    val sh = RoundedCornerShape(18.dp)
                    Box(
                        Modifier.weight(1f).fillMaxHeight().heightIn(min = sc.target + 12.dp).clip(sh)
                            .background(if (on) p.brandSoft else p.card).then(if (on) Modifier.border(3.dp, p.brand, sh) else Modifier)
                            .steady(item + if (on) ", chosen" else ", not chosen") { onToggle(item) }.padding(horizontal = 16.dp, vertical = 12.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) { Text(item, fontSize = sc.body, fontWeight = FontWeight.SemiBold, color = p.ink) }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** One answer to a yes / no / maybe question, saying what it confirms. */
data class Answer(val title: String, val sub: String?, val icon: ImageVector, val accent: Color, val selected: Boolean, val onClick: () -> Unit)

/**
 * Yes / no / maybe answers as stacked cards. Not chosen: white, a thin border, icon and words in the answer's colour.
 * Chosen: filled with a soft tint of that colour and a thicker border, so the choice never depends on colour alone.
 */
@Composable
fun AnswerCards(answers: List<Answer>) {
    val p = LocalPalette.current
    val sc = LocalScale.current
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        answers.forEach { a ->
            val sh = RoundedCornerShape(24.dp)
            var pressed by remember { mutableStateOf(false) }
            Column(
                Modifier.fillMaxWidth().heightIn(min = if (sc.big) 190.dp else 172.dp).scale(pressScale(pressed)).clip(sh)
                    .background(if (a.selected) a.accent.copy(alpha = 0.14f) else p.card)
                    .border(if (a.selected) 3.dp else 1.5.dp, a.accent.copy(alpha = if (a.selected) 1f else 0.55f), sh)
                    .steady(a.title + (a.sub?.let { ". $it" } ?: "") + if (a.selected) ", chosen" else "", onPress = { pressed = it }, onClick = a.onClick)
                    .padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
            ) {
                Icon(a.icon, null, tint = a.accent, modifier = Modifier.size(40.dp))
                Spacer(Modifier.height(10.dp))
                Text(a.title, fontSize = sc.headline, fontWeight = FontWeight.Bold, color = a.accent, textAlign = TextAlign.Center)
                if (a.sub != null) Text(a.sub, fontSize = sc.body, color = p.inkSoft, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}

/** Three or more big options: stacked wide cards (picture left, words beside it, tick right), each centred top to bottom. */
@Composable
fun ChoiceCards(options: List<BigOption>) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) { options.forEach { BigOptionRow(it) } }
}

/** A big option as a wide card: picture on the left, words beside it, the tick on the right. */
@Composable
private fun BigOptionRow(o: BigOption) {
    val p = LocalPalette.current
    val sc = LocalScale.current
    val sh = RoundedCornerShape(24.dp)
    var pressed by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().heightIn(min = if (sc.big) 132.dp else 116.dp).scale(pressScale(pressed)).clip(sh)
            .background(if (o.selected) p.brandSoft else p.card).then(if (o.selected) Modifier.border(3.dp, p.brand, sh) else Modifier)
            .steady(o.title + (o.sub?.let { ". $it" } ?: "") + if (o.selected) ", chosen" else "", onPress = { pressed = it }, onClick = o.onClick)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.runtime.CompositionLocalProvider(LocalOnAccent provides o.selected) { o.visual() }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(o.title, fontSize = sc.headline, fontWeight = FontWeight.Bold, color = p.ink)
            if (o.sub != null) Text(o.sub, fontSize = sc.small, color = p.inkSoft, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

/** The picture on a big option: the feature colour as a soft disc, the glyph in full colour. */
@Composable
fun OptionIcon(icon: ImageVector, tint: Color, size: Dp = 84.dp) {
    val onAccent = LocalOnAccent.current
    // one shape for every icon background in the app: a rounded square
    Box(Modifier.size(size).clip(RoundedCornerShape(size * 0.28f)).background(if (onAccent) Color.White else tint.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(size * 0.5f))
    }
}

/** A round icon button for the top of a task: Back, Close. Labelled for screen readers. */
@Composable
fun RoundButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    val p = LocalPalette.current
    Box(Modifier.size(56.dp).clip(CircleShape).background(p.card).steady(label, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, label, tint = p.ink, modifier = Modifier.size(26.dp))
    }
}

/** A section: its name on the left, an optional text action on the right ("Change", "See all"). */
@Composable
fun Section(title: String, action: String? = null, onAction: () -> Unit = {}) {
    val p = LocalPalette.current
    val sc = LocalScale.current
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = sc.headline, fontWeight = FontWeight.Bold, color = p.ink, modifier = Modifier.weight(1f).semantics { heading() })
        if (action != null) Text(action, fontSize = sc.body, fontWeight = FontWeight.SemiBold, color = p.brand,
            modifier = Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp)).steady("$action $title", onClick = onAction).padding(horizontal = 8.dp).wrapContentHeight(Alignment.CenterVertically))
    }
}

/** Rows that belong together, on one white card, separated by thin lines. */
@Composable
fun Group(content: @Composable ColumnScope.() -> Unit) {
    val sh = RoundedCornerShape(LocalScale.current.radius)
    Column(Modifier.fillMaxWidth().lift(sh).clip(sh).background(LocalPalette.current.card)) {
        androidx.compose.runtime.CompositionLocalProvider(LocalOnCard provides true) { content() }
    }
}

@Composable
fun GroupLine() = Box(Modifier.padding(start = 18.dp).fillMaxWidth().height(1.dp).background(LocalPalette.current.line))

/**
 * One short fact in a [Group], as a key and its value side by side ("Calories a day  1,600 kcal"). Only for
 * summaries: when either side is long they stack (the key small on top, the value under it), so nothing is squeezed.
 * A row you can tap is never a key-value row: it becomes a [NavRow].
 */
@Composable
fun ValueRow(label: String, value: String?, sub: String? = null, valueColor: Color? = null, onClick: (() -> Unit)? = null) {
    if (onClick != null) { NavRow(label, value, sub, valueColor, onClick); return }
    val p = LocalPalette.current
    val sc = LocalScale.current
    val v = value?.ifBlank { null } ?: "–"
    val side = label.length <= 24 && v.length <= 16
    if (side) Row(Modifier.fillMaxWidth().heightIn(min = sc.target + 4.dp).padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = sc.body, color = p.ink, fontWeight = FontWeight.Medium)
            if (sub != null) Text(sub, fontSize = sc.small, color = p.inkSoft)
        }
        Spacer(Modifier.width(12.dp))
        Text(v, fontSize = sc.body, fontWeight = FontWeight.SemiBold, color = valueColor ?: p.ink, textAlign = TextAlign.End)
    } else Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp)) {
        Text(label, fontSize = sc.small, color = p.inkSoft, fontWeight = FontWeight.Medium)
        Text(v, fontSize = sc.body, fontWeight = FontWeight.SemiBold, color = valueColor ?: p.ink)
        if (sub != null) Text(sub, fontSize = sc.small, color = p.inkSoft)
    }
}

/**
 * A row that opens something, in a [Group]: its name, what it's set to on the line under it (never squeezed into a
 * second column), and a chevron.
 */
@Composable
fun NavRow(title: String, value: String? = null, sub: String? = null, valueColor: Color? = null, onClick: () -> Unit) {
    val p = LocalPalette.current
    val sc = LocalScale.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = sc.target + 4.dp).steady(title + ": " + (value ?: sub ?: ""), onClick = onClick).padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = sc.body, color = p.ink, fontWeight = FontWeight.Medium)
            value?.ifBlank { null }?.let { Text(it, fontSize = sc.small, fontWeight = FontWeight.SemiBold, color = valueColor ?: p.inkSoft) }
            if (sub != null) Text(sub, fontSize = sc.small, color = p.inkSoft)
        }
        Spacer(Modifier.width(8.dp))
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = p.inkSoft, modifier = Modifier.size(26.dp))
    }
}

/**
 * One row of a timeline, on its own (inside a group that is already a card) or in a [Timeline]: a dot and the
 * line down to the next, the time small on top, the words under it at full width, a chevron when it opens.
 */
@Composable
fun TimelineRow(it: TimelineItem, last: Boolean, trailing: (@Composable () -> Unit)? = null) {
    val p = LocalPalette.current
    val sc = LocalScale.current
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)
        .then(if (it.onClick != null) Modifier.clip(RoundedCornerShape(12.dp)).steady("${it.time}. ${it.text}", onClick = it.onClick) else Modifier)) {
        Column(Modifier.width(18.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.padding(top = 6.dp).size(10.dp).clip(CircleShape).background(it.mark ?: p.outline))
            if (!last) Box(Modifier.padding(top = 4.dp).width(2.dp).weight(1f).background(p.line))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).padding(bottom = 14.dp)) {
            Text(it.time, fontSize = sc.small, fontWeight = FontWeight.SemiBold, color = p.inkSoft)
            Text(it.text, fontSize = sc.body, color = it.mark?.takeIf { m -> m == p.red } ?: p.ink, fontWeight = FontWeight.Medium)
            if (it.sub != null) Text(it.sub, fontSize = sc.small, color = p.inkSoft)
        }
        trailing?.invoke()
        if (it.onClick != null) Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = p.inkSoft, modifier = Modifier.padding(top = 10.dp).size(24.dp))
    }
}

/** One entry on a [Timeline]: when, what (any length), an optional line under it, and a dot colour only when it matters. */
data class TimelineItem(val time: String, val text: String, val sub: String? = null, val mark: Color? = null, val onClick: (() -> Unit)? = null)

/**
 * Things that happened, newest first, down a thin line: the time small on top, the words under it at full width,
 * so a long message reads as easily as a short one. For history only; facts go in [ValueRow].
 */
@Composable
fun Timeline(items: List<TimelineItem>) {
    val p = LocalPalette.current
    val sc = LocalScale.current
    val sh = RoundedCornerShape(sc.radius)
    Column(Modifier.fillMaxWidth().lift(sh).clip(sh).background(p.card).padding(start = 16.dp, end = 12.dp, top = 16.dp, bottom = 4.dp)) {
        items.forEachIndexed { i, it -> TimelineRow(it, last = i == items.lastIndex) }
    }
}

/** One number that matters: a small label and time on top, the value large with its unit, optional line under. */
@Composable
fun Metric(label: String, value: String, unit: String = "", time: String? = null, note: String? = null, noteColor: Color? = null, onClick: (() -> Unit)? = null) {
    val p = LocalPalette.current
    val sc = LocalScale.current
    Card(onClick = onClick, label = "$label: $value $unit. ${note.orEmpty()}") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = sc.body, fontWeight = FontWeight.SemiBold, color = p.inkSoft, modifier = Modifier.weight(1f))
            if (time != null) Text(time, fontSize = sc.small, color = p.inkSoft)
            if (onClick != null) Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = p.inkSoft.copy(alpha = 0.6f), modifier = Modifier.size(24.dp))
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, fontSize = sc.huge * 0.85f, fontWeight = FontWeight.Bold, color = p.ink, lineHeight = sc.huge)
            if (unit.isNotBlank()) Text(" $unit", fontSize = sc.body, color = p.inkSoft, modifier = Modifier.padding(bottom = 6.dp))
        }
        if (note != null) Text(note, fontSize = sc.body, fontWeight = FontWeight.SemiBold, color = noteColor ?: p.inkSoft)
    }
}

/** An intro point: icon, a bold line, a quiet line under it. */
@Composable
fun Point(icon: ImageVector, title: String, sub: String) {
    val p = LocalPalette.current
    val sc = LocalScale.current
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, null, tint = p.brand, modifier = Modifier.padding(top = 2.dp).size(32.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = sc.body, fontWeight = FontWeight.Bold, color = p.ink)
            Text(sub, fontSize = sc.body, color = p.inkSoft, lineHeight = sc.body * 1.3f)
        }
    }
}

/** The part of the day for a time, as a picture with its own colour: sunrise orange, sun yellow, sunset red, moon blue. */
data class DayPart(val icon: ImageVector, val tint: Color, val name: String)

fun dayPart(hour: Int): DayPart = when (hour) {
    in 4..9 -> DayPart(DayIcons.Sunrise, Color(0xFFC4600A), "Morning")
    in 10..15 -> DayPart(Icons.Rounded.WbSunny, Color(0xFFA06F00), if (hour < 12) "Morning" else "Afternoon")
    in 16..19 -> DayPart(DayIcons.Sunset, Color(0xFFC0271F), "Evening")
    else -> DayPart(Icons.Rounded.NightsStay, Color(0xFF2266DD), "Night")
}

/** Sunrise and sunset: half a sun on the horizon, with an arrow going up or down. */
object DayIcons {
    val Sunrise: ImageVector by lazy { horizonSun(up = true) }
    val Sunset: ImageVector by lazy { horizonSun(up = false) }
}

private fun horizonSun(up: Boolean): ImageVector {
    val ink = androidx.compose.ui.graphics.SolidColor(Color.Black)
    val round = androidx.compose.ui.graphics.StrokeCap.Round
    return ImageVector.Builder(if (up) "Sunrise" else "Sunset", 24.dp, 24.dp, 24f, 24f).apply {
        // horizon
        addPath(androidx.compose.ui.graphics.vector.PathParser().parsePathString("M2.5,18.5 L21.5,18.5").toNodes(), stroke = ink, strokeLineWidth = 2f, strokeLineCap = round)
        // half sun
        addPath(androidx.compose.ui.graphics.vector.PathParser().parsePathString("M6.5,17 A5.5,5.5 0 0 1 17.5,17 Z").toNodes(), fill = ink)
        // rays
        addPath(androidx.compose.ui.graphics.vector.PathParser().parsePathString(
            "M2.5,15 L4,15 M20,15 L21.5,15 M5.3,9.8 L6.4,10.9 M18.7,9.8 L17.6,10.9").toNodes(), stroke = ink, strokeLineWidth = 2f, strokeLineCap = round)
        // arrow
        val arrow = if (up) "M12,2.5 L12,8.5 M9.5,5 L12,2.5 L14.5,5" else "M12,2.5 L12,8.5 M9.5,6 L12,8.5 L14.5,6"
        addPath(androidx.compose.ui.graphics.vector.PathParser().parsePathString(arrow).toNodes(), stroke = ink, strokeLineWidth = 2f, strokeLineCap = round,
            strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round)
    }.build()
}

/**
 * Every bottom sheet in the app: it opens at most three quarters of the screen high, grows to nine tenths as its
 * content is scrolled up, and never reaches under the status bar. [scroll] = false for content that scrolls itself.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AppSheet(onDismissRequest: () -> Unit, containerColor: Color = LocalPalette.current.paper, scroll: Boolean = true,
             content: @Composable ColumnScope.() -> Unit) {
    val sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val density = androidx.compose.ui.platform.LocalDensity.current
    val screen = with(density) { androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp.toPx() }
    var limit by remember { androidx.compose.runtime.mutableFloatStateOf(screen * 0.75f) }
    val grow = remember(screen) {
        object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
            override fun onPreScroll(available: androidx.compose.ui.geometry.Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): androidx.compose.ui.geometry.Offset {
                if (available.y < 0 && limit < screen * 0.9f) {
                    val d = minOf(-available.y, screen * 0.9f - limit)
                    limit += d
                    return androidx.compose.ui.geometry.Offset(0f, -d)
                }
                return androidx.compose.ui.geometry.Offset.Zero
            }
        }
    }
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismissRequest, sheetState = sheetState, containerColor = containerColor) {
        Box(Modifier.fillMaxWidth().heightIn(max = with(density) { limit.toDp() }).nestedScroll(grow)) {
            Column(Modifier.fillMaxWidth().then(if (scroll) Modifier.verticalScroll(rememberScrollState()) else Modifier), content = content)
        }
    }
}
