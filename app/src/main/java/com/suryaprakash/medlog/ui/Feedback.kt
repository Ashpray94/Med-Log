package com.suryaprakash.medlog.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.suryaprakash.medlog.medlog

data class FeedbackState(
    val isOpen: Boolean = false,
    val screenshot: Bitmap? = null,
)

/** Bottom sheet for sending feedback on shake. Categories: Bug, Idea, Confusing. */
@Composable
fun FeedbackSheet(
    state: FeedbackState,
    onDismiss: () -> Unit,
    onSend: (text: String, category: String, screenshot: Bitmap?) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var showScreenshot by remember { mutableStateOf(false) }

    if (state.isOpen) {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            containerColor = LocalPalette.current.paper,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.95f)
                    .padding(horizontal = Hs.Gutter)
                    .padding(bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    "Send feedback",
                    fontSize = Hs.Headline,
                    fontWeight = FontWeight.Bold,
                    color = Hs.Ink,
                )

                // Main text input
                TextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("What happened?", fontSize = Hs.Body) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = Hs.Body),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = LocalPalette.current.card,
                        unfocusedContainerColor = LocalPalette.current.card,
                    ),
                    shape = RoundedCornerShape(Hs.Radius),
                    singleLine = false,
                    maxLines = 5,
                )

                // Category chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf("Bug", "Idea", "Confusing").forEach { c ->
                        FilterChip(
                            selected = category == c,
                            onClick = { category = if (category == c) "" else c },
                            label = { Text(c, fontSize = 14.sp) },
                            modifier = Modifier.heightIn(min = Hs.TargetMin),
                        )
                    }
                }

                // Screenshot toggle + Draw button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { showScreenshot = !showScreenshot }
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = showScreenshot, onCheckedChange = null)
                        Text("Add screenshot", fontSize = 14.sp)
                    }
                    if (showScreenshot && state.screenshot != null) {
                        Text("Draw on it", fontSize = 14.sp, color = Hs.Blue)
                    }
                }

                // Screenshot thumbnail
                if (showScreenshot && state.screenshot != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp)
                            .background(Color.LightGray, RoundedCornerShape(Hs.Radius)),
                        contentAlignment = Alignment.CenterEnd,
                    ) {
                        Text("×", fontSize = 20.sp, color = Hs.Ink)
                    }
                }

                Spacer(Modifier.weight(1f))

                // Send button
                Button(
                    onClick = {
                        if (text.isNotBlank()) {
                            onSend(text, category, if (showScreenshot) state.screenshot else null)
                            text = ""
                            category = ""
                            showScreenshot = false
                            onDismiss()
                        }
                    },
                    enabled = text.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(Hs.TargetPrimary),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (text.isNotBlank()) Hs.Blue else Color.Gray,
                    ),
                ) {
                    Text("Send", fontSize = Hs.Body, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/** Save feedback locally and share via intent. */
fun sendFeedback(
    ctx: Context,
    text: String,
    category: String,
    screenshot: Bitmap?,
) {
    val app = ctx.medlog
    val version = runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "unknown"
    val device = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"

    val body = buildString {
        append("Feedback: $text\n")
        if (category.isNotBlank()) append("Category: $category\n")
        append("App: $version | Device: $device\n")
    }

    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_EMAIL, arrayOf("feedback@medlog.local"))
        putExtra(Intent.EXTRA_SUBJECT, "MedLog Feedback")
        putExtra(Intent.EXTRA_TEXT, body)
    }

    ctx.startActivity(Intent.createChooser(intent, "Send feedback"))
    Announce.done(ctx, null, "sent feedback")
}
