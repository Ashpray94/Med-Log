package com.suryaprakash.medlog.ui

import androidx.compose.material3.Icon
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.ExpandMore
import android.content.Intent
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import com.suryaprakash.medlog.ui.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Mic

/** A large, clearly labelled text box. The label stays visible above it (never a disappearing placeholder). */
@Composable
fun BigField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, keyboard: KeyboardType = KeyboardType.Text, lines: Int = 1, hint: String = "", onTap: (() -> Unit)? = null) {
    val p = LocalPalette.current
    val sc = LocalScale.current
    // The standard outlined text box: empty, the label is the placeholder inside; once you type (or tap in), it sits
    // in a notch in the outline, clear of the text. The box never changes height.
    val base = androidx.compose.material3.MaterialTheme.typography
    androidx.compose.material3.MaterialTheme(typography = base.copy(
        bodyLarge = base.bodyLarge.copy(fontSize = sc.body * 1.1f, fontFamily = LocalScript.current.family),
        bodySmall = base.bodySmall.copy(fontSize = sc.small, fontFamily = LocalScript.current.family),
    )) {
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box {
            androidx.compose.material3.OutlinedTextField(
                value = value, onValueChange = onChange, readOnly = onTap != null,
                modifier = Modifier.fillMaxWidth().heightIn(min = sc.target + 8.dp),
                label = { androidx.compose.material3.Text(tr(label), color = androidx.compose.material3.LocalContentColor.current) },
                textStyle = TextStyle(fontSize = sc.body * 1.1f, color = p.ink, fontFamily = LocalScript.current.family),
                singleLine = lines == 1, minLines = lines,
                shape = RoundedCornerShape(16.dp),
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = p.brand, unfocusedBorderColor = p.inkSoft.copy(alpha = 0.65f),
                    focusedLabelColor = p.brand, unfocusedLabelColor = if (value.isEmpty()) p.inkSoft.copy(alpha = 0.8f) else p.inkSoft,
                    focusedContainerColor = p.card, unfocusedContainerColor = p.card, cursorColor = p.brand,
                    focusedTextColor = p.ink, unfocusedTextColor = p.ink,
                ),
                keyboardOptions = KeyboardOptions(keyboardType = keyboard, capitalization = if (keyboard == KeyboardType.Text) KeyboardCapitalization.Sentences else KeyboardCapitalization.None),
                trailingIcon = if (onTap != null) ({ androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Rounded.ExpandMore, null, tint = p.brand) }) else null,
            )
            // a picker field: the whole box opens the picker, no keyboard
            if (onTap != null) Box(Modifier.matchParentSize().clip(RoundedCornerShape(16.dp)).steady(label, onClick = onTap))
            }
            // the note under the box lines up with the words inside it
            if (hint.isNotBlank()) Hint(hint, modifier = Modifier.padding(horizontal = 16.dp))
        }
    }
}

/** A big on/off row: words on the left, switch on the right; the whole row is the target. */
@Composable
fun Toggle(label: String, checked: Boolean, sub: String? = null, onChange: (Boolean) -> Unit) {
    val p = LocalPalette.current
    val sc = LocalScale.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = sc.target).clip(RoundedCornerShape(18.dp)).background(p.card).border(1.5.dp, p.outline, RoundedCornerShape(18.dp))
            .steady("$label, ${if (checked) "on" else "off"}") { onChange(!checked) }.padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = sc.body, fontWeight = FontWeight.Bold, color = p.ink)
            if (sub != null) Text(sub, fontSize = sc.small, color = p.inkSoft)
        }
        Spacer(Modifier.width(12.dp))
        AppSwitch(checked)
    }
}

/** The switch: off is a clear grey track with a dark-grey knob and outline; on is teal with a white knob. */
@Composable
fun AppSwitch(checked: Boolean) {
    val p = LocalPalette.current
    Switch(checked, null, colors = SwitchDefaults.colors(
        checkedTrackColor = p.brand, checkedThumbColor = androidx.compose.ui.graphics.Color.White, checkedBorderColor = p.brand,
        uncheckedTrackColor = p.fill, uncheckedThumbColor = p.inkSoft, uncheckedBorderColor = p.inkSoft.copy(alpha = 0.7f),
    ))
}

/** A switch as a row inside a [Group]: the words on the left, the switch on the right, the whole row tappable. */
@Composable
fun SwitchRow(label: String, checked: Boolean, sub: String? = null, onChange: (Boolean) -> Unit) {
    val p = LocalPalette.current
    val sc = LocalScale.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = sc.target + 8.dp).steady("$label, ${if (checked) "on" else "off"}") { onChange(!checked) }
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = sc.body, fontWeight = FontWeight.SemiBold, color = p.ink)
            if (sub != null) Text(sub, fontSize = sc.small, color = p.inkSoft)
        }
        Spacer(Modifier.width(12.dp))
        AppSwitch(checked)
    }
}

/** Pick a person from the phone's contacts (no contacts permission needed: the person chooses one). */
@Composable
fun rememberContactPicker(onPicked: (name: String, phone: String) -> Unit): () -> Unit {
    val ctx = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val uri = r.data?.data ?: return@rememberLauncherForActivityResult
        runCatching {
            ctx.contentResolver.query(uri, arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER), null, null, null)?.use { c ->
                if (c.moveToFirst()) onPicked(c.getString(0) ?: "", c.getString(1) ?: "")
            }
        }
    }
    return { launcher.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)) }
}

/**
 * The search box: always there, above any list of choices. Typing filters at once. The microphone button hands
 * over to the phone's own speech typing (Google or Samsung), which is far better at understanding people than
 * anything MedLog could carry, and needs no microphone permission here.
 */
@Composable
fun SearchBox(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val sc = LocalScale.current
    val ctx = LocalContext.current
    val lang = LocalSettings.current.languages.firstOrNull() ?: "en-IN"
    var focused by remember { mutableStateOf(false) }
    val dictate = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        r.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let(onChange)
    }
    val canDictate = remember { Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).resolveActivity(ctx.packageManager) != null }
    val sh = RoundedCornerShape(18.dp)
    Row(
        modifier.fillMaxWidth().heightIn(min = sc.target + 8.dp).clip(sh).background(p.card)
            .border(if (focused) 3.dp else 1.5.dp, if (focused) p.brand else p.inkSoft.copy(alpha = 0.65f), sh).padding(start = 16.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Rounded.Search, null, tint = p.inkSoft, modifier = Modifier.padding(end = 10.dp))
        androidx.compose.foundation.layout.Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(placeholder, fontSize = sc.body, color = p.inkSoft.copy(alpha = 0.8f))
            BasicTextField(
                value, onChange, Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused }.semantics { contentDescription = tr(placeholder) },
                textStyle = TextStyle(fontSize = sc.body * 1.05f, color = p.ink, fontFamily = LocalScript.current.family), singleLine = true, cursorBrush = SolidColor(p.brand),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
        }
        if (value.isNotEmpty()) androidx.compose.foundation.layout.Box(
            Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp)).steady("Clear") { onChange("") }.padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center,
        ) { androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Rounded.Close, "Clear", tint = p.inkSoft) }
        else if (canDictate) Row(
            Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(14.dp)).background(p.brand).steady("Speak instead of typing") {
                runCatching {
                    dictate.launch(Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                        .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, lang)
                        .putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, tr(placeholder)))
                }
            }.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Rounded.Mic, null, tint = p.onBrand)
            Spacer(Modifier.width(4.dp))
            Text("Speak", fontSize = sc.small, fontWeight = FontWeight.SemiBold, color = p.onBrand)
        }
    }
}

/** Opens the phone's own speech typing and hands back the words. Null when the phone has none. */
@Composable
fun rememberDictation(prompt: String, onText: (String) -> Unit): (() -> Unit)? {
    val ctx = LocalContext.current
    val lang = LocalSettings.current.languages.firstOrNull() ?: "en-IN"
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        r.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.takeIf { it.isNotBlank() }?.let(onText)
    }
    val ok = remember { Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).resolveActivity(ctx.packageManager) != null }
    if (!ok) return null
    return {
        runCatching {
            launcher.launch(Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, lang)
                .putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, tr(prompt)))
        }
    }
}

/**
 * A rolling wheel for picking a number (a year): five rows show, the middle one is the choice, it snaps to a row.
 * Big numbers, the chosen row highlighted.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun NumberWheel(values: List<Int>, selected: Int, onSelected: (Int) -> Unit, modifier: Modifier = Modifier, label: (Int) -> String = { "$it" }) {
    val p = LocalPalette.current
    val sc = LocalScale.current
    val rowH = if (sc.big) 64.dp else 56.dp
    val start = values.indexOf(selected).coerceAtLeast(0)
    val list = androidx.compose.foundation.lazy.rememberLazyListState(start)
    val snap = androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior(list)
    // open on the current choice (once the wheel has its size)
    androidx.compose.runtime.LaunchedEffect(Unit) { list.scrollToItem(start) }
    val centre by androidx.compose.runtime.remember { androidx.compose.runtime.derivedStateOf { list.firstVisibleItemIndex + if (list.firstVisibleItemScrollOffset > 0) 1 else 0 } }
    androidx.compose.runtime.LaunchedEffect(centre) { if (list.isScrollInProgress || centre == start) values.getOrNull(centre)?.let(onSelected) else if (list.firstVisibleItemIndex != 0 || start == 0) values.getOrNull(centre)?.let(onSelected) }
    Box(modifier.fillMaxWidth().height(rowH * 5)) {
        // the chosen row
        Box(Modifier.align(Alignment.Center).fillMaxWidth().height(rowH).clip(RoundedCornerShape(16.dp)).background(Color(0xFFBFE0DA)))
        androidx.compose.foundation.lazy.LazyColumn(state = list, flingBehavior = snap, modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = rowH * 2)) {
            items(values.size) { i ->
                val d = kotlin.math.abs(i - centre)
                Box(Modifier.fillMaxWidth().height(rowH), contentAlignment = Alignment.Center) {
                    Text(label(values[i]), fontSize = if (d == 0) sc.title else sc.headline, fontWeight = if (d == 0) FontWeight.Bold else FontWeight.Normal,
                        color = if (d == 0) p.ink else p.inkSoft.copy(alpha = if (d == 1) 0.7f else 0.4f))
                }
            }
        }
    }
}

/** The one counter used everywhere: a small outlined box with − value + inside (the value alone, no unit). */
@Composable
fun Counter(value: String, canMinus: Boolean, canPlus: Boolean, onMinus: () -> Unit, onPlus: () -> Unit, label: String = "") {
    val p = LocalPalette.current
    val sc = LocalScale.current
    val sh = RoundedCornerShape(12.dp)
    Row(Modifier.height(48.dp).width(168.dp).clip(sh).border(1.5.dp, p.inkSoft.copy(alpha = 0.65f), sh).background(p.card),
        verticalAlignment = Alignment.CenterVertically) {
        CounterHalf("−", "Less $label".trim(), canMinus, onMinus)
        Text(value, fontSize = sc.body, fontWeight = FontWeight.Bold, color = p.brand, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            maxLines = 1, modifier = Modifier.weight(1f))
        CounterHalf("+", "More $label".trim(), canPlus, onPlus)
    }
}

@Composable
private fun CounterHalf(symbol: String, label: String, enabled: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    androidx.compose.foundation.layout.Box(Modifier.size(width = 44.dp, height = 48.dp).steady(label, enabled, onClick = onClick), contentAlignment = Alignment.Center) {
        Text(symbol, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = if (enabled) p.brand else p.line)
    }
}

/** A heading, one line saying what it means, and the counter beside them. */
@Composable
fun CounterLine(title: String, sub: String, value: String, canMinus: Boolean, canPlus: Boolean, onMinus: () -> Unit, onPlus: () -> Unit) {
    val p = LocalPalette.current
    val sc = LocalScale.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, fontSize = sc.headline, fontWeight = FontWeight.Bold, color = p.ink)
            Text(sub, fontSize = sc.body, color = p.inkSoft)
        }
        Counter(value, canMinus, canPlus, onMinus, onPlus, title)
    }
}

/** The counter as a row inside a [Group]. */
@Composable
fun StepperRow(label: String, value: String, canMinus: Boolean, canPlus: Boolean, onMinus: () -> Unit, onPlus: () -> Unit, sub: String? = null) {
    val p = LocalPalette.current
    val sc = LocalScale.current
    Row(Modifier.fillMaxWidth().heightIn(min = sc.target + 8.dp).padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = sc.body, fontWeight = FontWeight.SemiBold, color = p.ink)
            if (sub != null) Text(sub, fontSize = sc.small, color = p.inkSoft)
        }
        Counter(value, canMinus, canPlus, onMinus, onPlus, label)
    }
}


/** When something happened, in words: "Now", "Today, 8:30 AM", "Yesterday, 9:00 PM", "Thu 24 Sep, 7:15 AM". */
fun whenWords(at: Long?): String {
    if (at == null) return "Now"
    val z = java.time.ZoneId.systemDefault()
    val t = java.time.Instant.ofEpochMilli(at).atZone(z)
    val d = t.toLocalDate(); val today = java.time.LocalDate.now(z)
    val day = when (d) { today -> "Today"; today.minusDays(1) -> "Yesterday"; else -> t.format(java.time.format.DateTimeFormatter.ofPattern("EEE d MMM", com.suryaprakash.medlog.speech.I18n.locale)) }
    return "$day, " + t.format(java.time.format.DateTimeFormatter.ofPattern("h:mm a", com.suryaprakash.medlog.speech.I18n.locale))
}

/**
 * "When": now, unless changed. Any entry can be logged later for an earlier time. [at] null means now.
 */
@Composable
fun WhenRow(at: Long?, onChange: (Long?) -> Unit) {
    val p = LocalPalette.current
    val sc = LocalScale.current
    var open by remember { mutableStateOf(false) }
    val sh = RoundedCornerShape(sc.radius)
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).clip(sh).background(p.card).border(1.5.dp, p.outline, sh).steady("When: ${whenWords(at)}. Tap to change") { open = true }
        .padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(androidx.compose.material.icons.Icons.Rounded.Schedule, null, tint = p.brand, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(12.dp))
        Text("When", fontSize = sc.body, fontWeight = FontWeight.SemiBold, color = p.ink, modifier = Modifier.weight(1f))
        Text(whenWords(at), fontSize = sc.body, fontWeight = FontWeight.SemiBold, color = if (at == null) p.inkSoft else p.brand)
        Icon(androidx.compose.material.icons.Icons.Rounded.ExpandMore, null, tint = p.inkSoft, modifier = Modifier.padding(start = 4.dp).size(24.dp))
    }
    if (open) WhenSheet(at, onDone = { onChange(it); open = false }, onDismiss = { open = false })
}

/** Day and time on wheels, never in the future. "Now" puts it back to the moment it's saved. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun WhenSheet(start: Long?, onDone: (Long?) -> Unit, onDismiss: () -> Unit) {
    val p = LocalPalette.current
    val sc = LocalScale.current
    val z = java.time.ZoneId.systemDefault()
    val now = java.time.ZonedDateTime.now(z)
    val t0 = start?.let { java.time.Instant.ofEpochMilli(it).atZone(z) } ?: now
    val today = now.toLocalDate()
    var back by remember { mutableStateOf(java.time.temporal.ChronoUnit.DAYS.between(t0.toLocalDate(), today).toInt().coerceIn(0, 13)) }
    var h by remember { mutableStateOf(((t0.hour + 11) % 12) + 1) }
    var mi by remember { mutableStateOf(t0.minute - t0.minute % 5) }
    var pm by remember { mutableStateOf(if (t0.hour >= 12) 1 else 0) }
    fun result(): Long {
        val hour24 = (h % 12) + if (pm == 1) 12 else 0
        val t = today.minusDays(back.toLong()).atTime(hour24, mi).atZone(z).toInstant().toEpochMilli()
        return minOf(t, System.currentTimeMillis())
    }
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss, containerColor = p.card,
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = sc.margin).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("When was it?", fontSize = sc.headline, fontWeight = FontWeight.Bold, color = p.ink)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberWheel((0..13).toList(), back, { back = it }, Modifier.weight(1.6f), label = { b ->
                    when (b) { 0 -> "Today"; 1 -> "Yesterday"; else -> today.minusDays(b.toLong()).format(java.time.format.DateTimeFormatter.ofPattern("EEE d", com.suryaprakash.medlog.speech.I18n.locale)) }
                })
                NumberWheel((1..12).toList(), h, { h = it }, Modifier.weight(0.9f))
                NumberWheel((0..55 step 5).toList(), mi, { mi = it }, Modifier.weight(0.9f), label = { "%02d".format(it) })
                NumberWheel(listOf(0, 1), pm, { pm = it }, Modifier.weight(0.9f), label = { if (it == 0) "AM" else "PM" })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BigButton("Now", Modifier.weight(1f), Tone.SECONDARY, onClick = { onDone(null) })
                BigButton("Done", Modifier.weight(1f), onClick = { onDone(result()) })
            }
        }
    }
}
