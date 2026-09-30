package com.suryaprakash.medlog.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.suryaprakash.medlog.ui.BigButton
import com.suryaprakash.medlog.ui.LocalPalette
import com.suryaprakash.medlog.ui.Nav
import com.suryaprakash.medlog.ui.Route
import com.suryaprakash.medlog.ui.Screen
import com.suryaprakash.medlog.ui.Segmented
import com.suryaprakash.medlog.ui.Text
import com.suryaprakash.medlog.ui.Tone

/** Helpers tab: Tools | Helpers chat | Patient chat. STUB: each segment is to be filled in. */
@Composable
fun HelpTabScreen(nav: Nav) {
    val p = LocalPalette.current
    var seg by remember { mutableStateOf(0) }
    Screen("Helpers", "Helper tools and messages.", onHome = { nav.home() }) {
        Segmented(listOf("Tools", "Helpers chat", "Patient chat"), seg) { seg = it }
        when (seg) {
            0 -> {
                BigButton("My helpers", tone = Tone.SECONDARY, onClick = { nav.go(Route.Helpers) })
                BigButton("My messages", tone = Tone.SECONDARY, onClick = { nav.go(Route.Messages) })
                BigButton("Connect phones", tone = Tone.SECONDARY, onClick = { nav.go(Route.Pair) })
            }
            1 -> Text("Helpers chat: coming soon.", color = p.ink)
            else -> Text("Patient chat: coming soon.", color = p.ink)
        }
    }
}
