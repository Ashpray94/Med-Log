package com.suryaprakash.medlog.ui

import android.content.Intent
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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

/** A large, clearly labelled text box. The label stays visible above it (never a disappearing placeholder). */
@Composable
fun BigField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, keyboard: KeyboardType = KeyboardType.Text, lines: Int = 1, hint: String = "") {
    val p = LocalPalette.current
    val sc = LocalScale.current
    var focused by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, fontSize = sc.body, fontWeight = FontWeight.Bold, color = p.ink)
        BasicTextField(
            value, onChange,
            Modifier.fillMaxWidth().heightIn(min = sc.target * (if (lines > 1) 1.6f else 1f)).clip(RoundedCornerShape(18.dp)).background(p.card)
                .border(if (focused) 3.dp else 2.dp, if (focused) p.focus else p.line, RoundedCornerShape(18.dp))
                .onFocusChanged { focused = it.isFocused }.padding(horizontal = 18.dp, vertical = 14.dp)
                .semantics { contentDescription = label },
            textStyle = TextStyle(fontSize = sc.body * 1.1f, color = p.ink, fontFamily = Atkinson),
            singleLine = lines == 1, minLines = lines, cursorBrush = SolidColor(p.brand),
            keyboardOptions = KeyboardOptions(keyboardType = keyboard, capitalization = if (keyboard == KeyboardType.Text) KeyboardCapitalization.Sentences else KeyboardCapitalization.None),
        )
        if (hint.isNotBlank()) Hint(hint)
    }
}

/** A big on/off row: words on the left, switch on the right; the whole row is the target. */
@Composable
fun Toggle(label: String, checked: Boolean, sub: String? = null, onChange: (Boolean) -> Unit) {
    val p = LocalPalette.current
    val sc = LocalScale.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = sc.target).clip(RoundedCornerShape(18.dp)).background(p.card).border(1.dp, p.line, RoundedCornerShape(18.dp))
            .steady("$label, ${if (checked) "on" else "off"}") { onChange(!checked) }.padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = sc.body, fontWeight = FontWeight.Bold, color = p.ink)
            if (sub != null) Text(sub, fontSize = sc.small, color = p.inkSoft)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked, null, colors = SwitchDefaults.colors(checkedTrackColor = p.brand))
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
