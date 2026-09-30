package com.suryaprakash.medlog.ui.screens

import androidx.compose.runtime.Composable
import com.suryaprakash.medlog.ui.LocalPalette
import com.suryaprakash.medlog.ui.Nav
import com.suryaprakash.medlog.ui.Screen
import com.suryaprakash.medlog.ui.Text

/** Timeline tab: today's medicines, doses and what happened, in time order. STUB: to be filled in. */
@Composable
fun TimelineScreen(nav: Nav) {
    val p = LocalPalette.current
    Screen("Timeline", "Today and what happened.", onHome = { nav.home() }) {
        Text("Nothing here yet.", color = p.ink)
    }
}
