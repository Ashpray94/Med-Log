package com.suryaprakash.medlog.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.maxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text as MaterialText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.suryaprakash.medlog.help.FamilyChat
import com.suryaprakash.medlog.ui.Announce
import com.suryaprakash.medlog.ui.Hs
import com.suryaprakash.medlog.ui.LocalPalette
import com.suryaprakash.medlog.ui.Text
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.json.JSONObject

/** Chat message model. */
data class ChatMsg(
    val id: String,
    val channel: String, // "helpers" or "patient"
    val from: String,
    val mine: Boolean,
    val text: String,
    val at: Long,
    val state: String // "sending", "sent", "failed"
)

/** Channel-aware chat adapter to prefix messages. */
object ChatAdapter {
    /**
     * Send a message on a channel by prefixing it with [h] for helpers or [p] for patient.
     * Calls FamilyChat.send internally.
     */
    fun send(ctx: Context, channel: String, text: String) {
        val prefix = when (channel) {
            "helpers" -> "[h] "
            "patient" -> "[p] "
            else -> ""
        }
        FamilyChat.send(ctx, prefix + text)
    }

    /**
     * Parse a stored message text and extract the channel and cleaned text.
     * Returns Pair of (channel, cleanedText). No prefix → "patient".
     */
    fun parse(storedText: String): Pair<String, String> {
        return when {
            storedText.startsWith("[h] ") -> "helpers" to storedText.substring(4)
            storedText.startsWith("[p] ") -> "patient" to storedText.substring(4)
            else -> "patient" to storedText
        }
    }
}

/** Chat message view with day separators, bubbles, and composer. */
@Composable
fun ChatView(
    msgs: List<ChatMsg>,
    onSend: (String) -> Unit,
    onRetry: (ChatMsg) -> Unit,
    empty: String
) {
    val p = LocalPalette.current
    val listState = rememberLazyListState()
    var textInput by remember { mutableStateOf("") }
    val quickReplies = listOf("OK", "On my way", "Call me", "Thanks")

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        if (msgs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(empty, color = p.inkSoft, fontSize = 14.sp)
            }
        } else {
            // Reverse layout: newest at bottom
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                state = listState,
                reverseLayout = true
            ) {
                itemsIndexed(msgs.asReversed()) { idx, msg ->
                    val showDateSeparator = idx == 0 || {
                        val current = msgs[msgs.size - 1 - idx].at
                        val prev = msgs[msgs.size - 2 - idx].at
                        isSameDay(prev, current)
                    }()

                    if (idx == 0 || !isSameDay(
                            msgs[msgs.size - 1 - idx].at,
                            msgs[msgs.size - 2 - idx].at
                        )
                    ) {
                        DateSeparator(msgs[msgs.size - 1 - idx].at)
                    }

                    ChatBubble(msg, onRetry)
                }
            }
        }

        // Quick reply chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Hs.Gutter, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            quickReplies.forEach { reply ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(p.card)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        reply,
                        fontSize = 13.sp,
                        color = p.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Composer
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Hs.Gutter, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = textInput,
                onValueChange = { textInput = it },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 18.sp.value.dp),
                placeholder = { MaterialText("Message", fontSize = 18.sp) },
                textStyle = androidx.compose.material3.LocalTextStyle.current.copy(fontSize = 18.sp),
                singleLine = false
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (textInput.isNotBlank()) Hs.Blue else p.inkSoft.copy(alpha = 0.3f))
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                MaterialText(
                    "Send",
                    color = if (textInput.isNotBlank()) Color.White else p.ink.copy(alpha = 0.3f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun ChatBubble(msg: ChatMsg, onRetry: (ChatMsg) -> Unit) {
    val p = LocalPalette.current
    val bubbleColor = if (msg.mine) Hs.Blue else Color(0xFFF0F0F0)
    val textColor = if (msg.mine) Color.White else Hs.Ink

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Hs.Gutter, vertical = 4.dp),
        horizontalArrangement = if (msg.mine) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier.maxWidth(0.8f * 16 * 5), // ~80% of a reasonable screen width
            horizontalAlignment = if (msg.mine) Alignment.End else Alignment.Start
        ) {
            if (!msg.mine) {
                Text(
                    msg.from,
                    fontSize = 14.sp,
                    color = Hs.Ink,
                    fontWeight = FontWeight.Bold
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(bubbleColor)
                    .padding(12.dp)
            ) {
                Text(msg.text, color = textColor, fontSize = 16.sp, lineHeight = 20.sp)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp, end = if (msg.mine) 0.dp else 12.dp, start = if (msg.mine) 12.dp else 0.dp),
                horizontalArrangement = if (msg.mine) Arrangement.End else Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    formatTime(msg.at),
                    fontSize = 13.sp,
                    color = p.inkSoft
                )

                if (msg.state == "sent") {
                    Icon(
                        Icons.Rounded.Check,
                        null,
                        modifier = Modifier.padding(start = 4.dp),
                        tint = Hs.Green
                    )
                } else if (msg.state == "sending") {
                    Text("...", fontSize = 12.sp, color = p.inkSoft)
                } else if (msg.state == "failed") {
                    Text(
                        "Tap to retry",
                        fontSize = 12.sp,
                        color = Hs.Red,
                        modifier = Modifier.clickable { onRetry(msg) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DateSeparator(at: Long) {
    val p = LocalPalette.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            formatDate(at),
            fontSize = 13.sp,
            color = p.inkSoft
        )
    }
}

private fun isSameDay(time1: Long, time2: Long): Boolean {
    val format = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    return format.format(Date(time1)) == format.format(Date(time2))
}

private fun formatDate(at: Long): String {
    val now = System.currentTimeMillis()
    val dayMillis = 24L * 60L * 60L * 1000L
    val diff = now - at

    return when {
        isSameDay(now, at) -> "Today"
        diff < 2 * dayMillis -> "Yesterday"
        else -> SimpleDateFormat("MMM dd", Locale.US).format(Date(at))
    }
}

private fun formatTime(at: Long): String {
    return SimpleDateFormat("h:mm a", Locale.US).format(Date(at))
}
