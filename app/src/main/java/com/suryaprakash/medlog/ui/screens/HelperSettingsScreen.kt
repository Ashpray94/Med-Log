package com.suryaprakash.medlog.ui.screens

import androidx.compose.runtime.Composable
import com.suryaprakash.medlog.ui.LocalPalette
import com.suryaprakash.medlog.ui.Nav
import com.suryaprakash.medlog.ui.Screen
import com.suryaprakash.medlog.ui.Text

/** Settings tab when the phone is in helper mode. STUB: to be filled in. */
@Composable
fun HelperSettingsScreen(nav: Nav) {
    val p = LocalPalette.current
    Screen("Settings", "Settings for helping.", onHome = { nav.home(com.suryaprakash.medlog.ui.Route.HelperHome) }) {
        Text("Nothing here yet.", color = p.ink)
    }
}
