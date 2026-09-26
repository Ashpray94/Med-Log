package com.suryaprakash.medlog.ui

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Sos
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Icon
import com.suryaprakash.medlog.ui.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
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
                onClick()
            }
        }
    }
    return this
        .semantics { role = Role.Button; contentDescription = label; onClick(label) { act(); true } }
        .pointerInput(label, enabled) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                onPress(true)
                val up = waitForUpOrCancellation()
                onPress(false)
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
 * Every screen: optional Back, a large title, content, and a bottom bar whose two buttons never move:
 * Read aloud and Home (plan 4.3 #1, #13). Both are the same size.
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
    content: @Composable ColumnScope.() -> Unit,
) {
    val p = LocalPalette.current
    val s = LocalSettings.current
    val sc = LocalScale.current
    val ctx = LocalContext.current
    val full = "$title. $speak"
    LaunchedEffect(full) { ReadAloud.text = full }
    LaunchedEffect(title) { if (s.autoRead && s.easyMode) { delay(350); ctx.medlog.speaker.say(full) } }
    Box(Modifier.fillMaxSize().background(background ?: p.paper)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            val body: @Composable ColumnScope.() -> Unit = {
                Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp)) {
                    if (onBack != null) BackLink(onBack) else Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(title, fontSize = sc.title, fontWeight = FontWeight.Bold, color = p.ink, lineHeight = sc.title * 1.12f,
                            modifier = Modifier.weight(1f).semantics { heading() })
                        trailing?.invoke()
                    }
                    if (subtitle != null) Text(subtitle, fontSize = sc.body, color = p.inkSoft, modifier = Modifier.padding(top = 4.dp))
                }
                content()
            }
            val inner = Modifier.weight(1f).fillMaxWidth().padding(horizontal = sc.margin)
            Column(if (scroll) inner.verticalScroll(rememberScrollState()) else inner, verticalArrangement = Arrangement.spacedBy(sc.gap)) {
                body()
                if (scroll) Spacer(Modifier.height(24.dp))
            }
            BottomBar(onHome)
        }
        UndoBar(Modifier.align(Alignment.BottomCenter).padding(bottom = sc.target + 28.dp).navigationBarsPadding())
    }
}

@Composable
private fun BackLink(onBack: () -> Unit) {
    val p = LocalPalette.current
    val sc = LocalScale.current
    Row(
        Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp)).steady("Back", onClick = onBack).padding(end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, tint = p.brand, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(6.dp))
        Text("Back", color = p.brand, fontSize = sc.button, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun BottomBar(onHome: (() -> Unit)?) {
    val p = LocalPalette.current
    val s = LocalSettings.current
    val sc = LocalScale.current
    val ctx = LocalContext.current
    val nav = LocalNav.current
    val speaking by ctx.medlog.speaker.speaking.collectAsState()
    val read = { if (speaking) ctx.medlog.speaker.stop() else ctx.medlog.speaker.say(ReadAloud.text) }
    Column(Modifier.fillMaxWidth().background(p.card)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(p.line))
        if (nav == null || s.role == "helper") {
            Row(Modifier.fillMaxWidth().padding(horizontal = sc.margin, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BarButton(if (speaking) "Stop" else "Read aloud", if (speaking) Icons.Rounded.Stop else Icons.Rounded.VolumeUp, p.brandSoft, p.brand, Modifier.weight(1f), read)
                if (onHome != null) BarButton("Home", Icons.Rounded.Home, p.fill, p.ink, Modifier.weight(1f), onHome)
            }
            return@Column
        }
        // The large navigation: always the same four places, same order, same size.
        val here = nav.current
        val tabs = listOf(
            Triple("Home", Icons.Rounded.Home, here == Route.Home) to { nav.home() },
            Triple("History", Icons.Rounded.History, here == Route.Notes || here is Route.NoteDetail || here is Route.ProblemHistory) to { nav.home(); nav.go(Route.Notes) },
            Triple("SOS", Icons.Rounded.Sos, here == Route.Emergency) to { if (here != Route.Emergency) nav.go(Route.Emergency) },
            Triple("Family", Icons.Rounded.Groups, here == Route.Help) to { nav.home(); nav.go(Route.Help) },
            Triple(if (speaking) "Stop" else "Read aloud", if (speaking) Icons.Rounded.Stop else Icons.Rounded.VolumeUp, speaking) to read,
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
                        Box(Modifier.size(if (sc.big) 60.dp else 54.dp).clip(CircleShape).background(p.red), contentAlignment = Alignment.Center) {
                            Text("SOS", color = Color.White, fontSize = sc.small, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                    return@forEach
                }
                val danger = false
                val tint = when { danger -> p.red; on -> p.brand; else -> p.inkSoft }
                Column(
                    Modifier.weight(1f).heightIn(min = sc.target + 6.dp).clip(RoundedCornerShape(16.dp))
                        .background(if (on) p.brandSoft else Color.Transparent).steady(label, onClick = go).padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                ) {
                    Icon(icon, null, tint = tint, modifier = Modifier.size(if (sc.big) 32.dp else 28.dp))
                    Spacer(Modifier.height(4.dp))
                    Text(label, fontSize = sc.small, fontWeight = if (on || danger) FontWeight.Bold else FontWeight.Medium, color = tint, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun BarButton(text: String, icon: ImageVector, bg: Color, fg: Color, modifier: Modifier, onClick: () -> Unit) {
    val sc = LocalScale.current
    var pressed by remember { mutableStateOf(false) }
    Row(
        modifier.scale(pressScale(pressed)).height(sc.target).clip(RoundedCornerShape(16.dp)).background(bg)
            .steady(text, onPress = { pressed = it }, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(24.dp)); Spacer(Modifier.width(10.dp))
        Text(text, color = fg, fontSize = sc.button, fontWeight = FontWeight.SemiBold)
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
        Modifier.size(52.dp).clip(CircleShape).background(p.card).steady(label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = p.inkSoft, modifier = Modifier.size(28.dp)) }
}

// ───────────────────────── buttons ─────────────────────────

enum class Tone { PRIMARY, SECONDARY, DANGER, OK, QUIET, AMBER }

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
    val (bg, fg) = when (tone) {
        Tone.PRIMARY -> p.brand to p.onBrand
        Tone.SECONDARY -> p.fill to p.ink
        Tone.DANGER -> p.red to Color.White
        Tone.OK -> p.ok to Color.White
        Tone.AMBER -> p.amberSoft to p.amber
        Tone.QUIET -> p.brandSoft to p.brand
    }
    var pressed by remember { mutableStateOf(false) }
    val left = leading != null || sub != null
    Row(
        modifier.fillMaxWidth().scale(pressScale(pressed)).heightIn(min = height ?: sc.target).clip(RoundedCornerShape(16.dp))
            .background(if (enabled) bg else p.fill.copy(alpha = 0.6f))
            .steady(text + (sub?.let { ". $it" } ?: ""), enabled, onPress = { pressed = it }, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (left) Arrangement.Start else Arrangement.Center,
    ) {
        leading?.let { it(); Spacer(Modifier.width(14.dp)) }
        if (icon != null) { Icon(icon, null, tint = if (enabled) fg else p.inkSoft, modifier = Modifier.size(26.dp)); Spacer(Modifier.width(14.dp)) }
        Column(Modifier.then(if (left) Modifier.weight(1f) else Modifier)) {
            Text(text, color = if (enabled) fg else p.inkSoft, fontSize = sc.button, fontWeight = FontWeight.SemiBold, lineHeight = sc.button * 1.2f, maxLines = 3,
                textAlign = if (left) TextAlign.Start else TextAlign.Center)
            if (sub != null) Text(sub, color = (if (enabled) fg else p.inkSoft).copy(alpha = 0.8f), fontSize = sc.small, lineHeight = sc.small * 1.25f)
        }
    }
}

@Composable
fun YesNo(yes: String = "Yes", no: String = "No", onYes: () -> Unit, onNo: () -> Unit) {
    val sc = LocalScale.current
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        BigButton(yes, Modifier.weight(1f), Tone.PRIMARY, height = sc.target * 1.3f, onClick = onYes)
        BigButton(no, Modifier.weight(1f), Tone.SECONDARY, height = sc.target * 1.3f, onClick = onNo)
    }
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
    Text(text, modifier.padding(top = 8.dp).semantics { heading() }, fontSize = LocalScale.current.headline, fontWeight = FontWeight.Bold, color = LocalPalette.current.ink)
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

/** Raised white surface. A coloured [border] marks warnings. */
@Composable
fun Card(modifier: Modifier = Modifier, color: Color? = null, border: Color? = null, shape: Shape? = null, padding: PaddingValues = PaddingValues(18.dp), onClick: (() -> Unit)? = null, label: String = "", content: @Composable ColumnScope.() -> Unit) {
    val p = LocalPalette.current
    val sh = shape ?: RoundedCornerShape(LocalScale.current.radius)
    var pressed by remember { mutableStateOf(false) }
    Column(
        modifier.fillMaxWidth().scale(pressScale(pressed))
            .then(if (color == null || color == p.card) Modifier.shadow(1.5.dp, sh, ambientColor = Color(0x22000000), spotColor = Color(0x22000000)) else Modifier)
            .clip(sh).background(color ?: p.card)
            .then(if (border != null) Modifier.border(2.dp, border, sh) else Modifier)
            .then(if (onClick != null) Modifier.steady(label, onPress = { pressed = it }, onClick = onClick) else Modifier)
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

/** iOS-style row: coloured icon tile, title, optional subtitle, chevron. */
@Composable
fun ListRow(title: String, sub: String? = null, icon: ImageVector? = null, tint: Color? = null, leading: (@Composable () -> Unit)? = null, onClick: () -> Unit) {
    val p = LocalPalette.current
    val sc = LocalScale.current
    var pressed by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().scale(pressScale(pressed)).heightIn(min = sc.target + 8.dp)
            .shadow(1.5.dp, RoundedCornerShape(sc.radius), ambientColor = Color(0x22000000), spotColor = Color(0x22000000))
            .clip(RoundedCornerShape(sc.radius)).background(p.card)
            .steady(title + (sub?.let { ". $it" } ?: ""), onPress = { pressed = it }, onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) { leading(); Spacer(Modifier.width(14.dp)) }
        else if (icon != null) { IconTile(icon, tint ?: p.brand, 44.dp); Spacer(Modifier.width(14.dp)) }
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = sc.body * 1.05f, fontWeight = FontWeight.SemiBold, color = p.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (sub != null) Text(sub, fontSize = sc.small, color = p.inkSoft, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = p.inkSoft.copy(alpha = 0.6f), modifier = Modifier.size(28.dp))
    }
}

/** White glyph on a coloured rounded square. */
@Composable
fun IconTile(icon: ImageVector, tint: Color, size: Dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(size * 0.28f)).background(tint), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(size * 0.56f))
    }
}

/** Equal-sized tiles in a grid: every tile in every row has the same width and height. */
@Composable
fun <T> TileGrid(items: List<T>, cols: Int, aspect: Float = 1f, gap: Dp = 12.dp, tile: @Composable (T, Modifier) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(gap)) {
        items.chunked(cols).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                row.forEach { tile(it, Modifier.weight(1f).aspectRatio(aspect)) }
                repeat(cols - row.size) { Spacer(Modifier.weight(1f).aspectRatio(aspect)) }
            }
        }
    }
}

/** A tappable tile: content centred on a white card, same size as its neighbours. */
@Composable
fun Tile(label: String, modifier: Modifier, selected: Boolean = false, color: Color? = null, onClick: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val p = LocalPalette.current
    val sc = LocalScale.current
    var pressed by remember { mutableStateOf(false) }
    val sh = RoundedCornerShape(sc.radius)
    Column(
        modifier.scale(pressScale(pressed)).shadow(1.5.dp, sh, ambientColor = Color(0x22000000), spotColor = Color(0x22000000)).clip(sh)
            .background(color ?: p.card).then(if (selected) Modifier.border(3.dp, p.brand, sh) else Modifier)
            .steady(label, onPress = { pressed = it }, onClick = onClick).padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
        content = content,
    )
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

@Composable
fun Chip(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val p = LocalPalette.current
    val sc = LocalScale.current
    Box(
        modifier.heightIn(min = 52.dp).clip(RoundedCornerShape(26.dp))
            .background(if (selected) p.brand else p.card)
            .then(if (!selected) Modifier.border(1.dp, p.line, RoundedCornerShape(26.dp)) else Modifier)
            .steady(text + if (selected) ", selected" else "", onClick = onClick).padding(horizontal = 20.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = if (selected) p.onBrand else p.ink, fontSize = sc.body, fontWeight = FontWeight.Medium) }
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
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(p.fill).padding(4.dp)) {
        options.forEachIndexed { i, o ->
            Box(
                Modifier.weight(1f).height(sc.target - 12.dp).clip(RoundedCornerShape(12.dp)).background(if (i == selected) p.card else Color.Transparent).steady(o) { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) { Text(o, fontSize = sc.body, fontWeight = if (i == selected) FontWeight.Bold else FontWeight.Medium, color = if (i == selected) p.ink else p.inkSoft, maxLines = 1) }
        }
    }
}

