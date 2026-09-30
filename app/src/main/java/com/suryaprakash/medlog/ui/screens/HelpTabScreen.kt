package com.suryaprakash.medlog.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.suryaprakash.medlog.medlog
import com.suryaprakash.medlog.ui.BigButton
import com.suryaprakash.medlog.ui.Hs
import com.suryaprakash.medlog.ui.LocalPalette
import com.suryaprakash.medlog.ui.Nav
import com.suryaprakash.medlog.ui.Route
import com.suryaprakash.medlog.ui.Screen
import com.suryaprakash.medlog.ui.Segmented
import com.suryaprakash.medlog.ui.Text
import com.suryaprakash.medlog.ui.Tone
import com.suryaprakash.medlog.help.FamilyChat
import com.suryaprakash.medlog.ui.Announce
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Helpers tab: Tools | Helpers chat | Patient chat. */
@Composable
fun HelpTabScreen(nav: Nav) {
    val ctx = LocalContext.current
    val p = LocalPalette.current
    var seg by remember { mutableStateOf(0) }

    // Get unread counts for segment labels
    val helpersUnread = getUnreadCount(ctx, "helpers")
    val patientUnread = getUnreadCount(ctx, "patient")

    val segmentLabels = listOf(
        "Tools",
        buildSegmentLabel("Helpers chat", helpersUnread),
        buildSegmentLabel("Patient chat", patientUnread)
    )

    Screen("Helpers", "Helper tools and messages.", onHome = { nav.home() }) {
        Segmented(segmentLabels, seg) { seg = it }
        when (seg) {
            0 -> ToolsSegment(nav)
            1 -> HelpersSegment(ctx, nav)
            else -> PatientSegment(ctx, nav)
        }
    }
}

@Composable
private fun ToolsSegment(nav: Nav) {
    val p = LocalPalette.current
    BigButton("My helpers", tone = Tone.SECONDARY, onClick = { nav.go(Route.Helpers) })
    BigButton("My messages", tone = Tone.SECONDARY, onClick = { nav.go(Route.Messages) })
    BigButton("Connect phones", tone = Tone.SECONDARY, onClick = { nav.go(Route.Pair) })

    // New tool buttons as rows with icon and text
    ToolButton("My helpers", Icons.Rounded.Groups, onClick = { nav.go(Route.Helpers) })
    ToolButton("Connect phone", Icons.Rounded.Phone, onClick = { nav.go(Route.Pair) })
    ToolButton("SOS", Icons.Rounded.Call, onClick = { nav.go(Route.Emergency) })
    ToolButton("Nearby help", Icons.Rounded.LocationOn, onClick = { })
    ToolButton("Share doctor page", Icons.Rounded.Share, onClick = { })
}

@Composable
private fun ToolButton(text: String, icon: ImageVector, onClick: () -> Unit) {
    val p = LocalPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Hs.Gutter, vertical = 8.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
            .background(p.card)
            .padding(12.dp)
            .androidx.compose.foundation.clickable(onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = p.brand, modifier = Modifier.size(24.dp))
        Text(text, fontSize = 14.sp, color = p.ink, maxLines = 1)
    }
}

@Composable
private fun HelpersSegment(ctx: Context, nav: Nav) {
    val app = ctx.medlog
    val helpers = remember { mutableStateOf(emptyList<ChatMsg>()) }

    // Load helper messages from inbox (placeholder for actual integration)
    val msgs = helpers.value.filter { it.channel == "helpers" }

    if (msgs.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Hs.Gutter),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            BigButton("Add helper", tone = Tone.PRIMARY, onClick = { nav.go(Route.HelperEdit()) })
        }
    } else {
        ChatView(
            msgs = msgs,
            onSend = { text ->
                ChatAdapter.send(ctx, "helpers", text)
                Announce.done(ctx, null, "sent a message", "message")
                updateLastSeen(ctx, "helpers")
            },
            onRetry = { msg ->
                ChatAdapter.send(ctx, "helpers", msg.text)
                Announce.done(ctx, null, "sent a message", "message")
            },
            empty = "No helper messages"
        )
    }
}

@Composable
private fun PatientSegment(ctx: Context, nav: Nav) {
    val app = ctx.medlog
    val patient = remember { mutableStateOf(emptyList<ChatMsg>()) }

    val msgs = patient.value.filter { it.channel == "patient" }

    ChatView(
        msgs = msgs,
        onSend = { text ->
            ChatAdapter.send(ctx, "patient", text)
            Announce.done(ctx, null, "sent a message", "message")
            updateLastSeen(ctx, "patient")
        },
        onRetry = { msg ->
            ChatAdapter.send(ctx, "patient", msg.text)
            Announce.done(ctx, null, "sent a message", "message")
        },
        empty = "No patient messages"
    )
}

private fun getUnreadCount(ctx: Context, channel: String): Int {
    val lastSeen = ctx.medlog.settings.getLong("chat_seen_$channel", 0L)
    // Count messages received after lastSeen (placeholder - integrate with actual message store)
    return 0
}

private fun updateLastSeen(ctx: Context, channel: String) {
    ctx.medlog.settings.putLong("chat_seen_$channel", System.currentTimeMillis())
}

private fun buildSegmentLabel(label: String, unread: Int): String {
    return if (unread > 0) "$label ($unread)" else label
}

