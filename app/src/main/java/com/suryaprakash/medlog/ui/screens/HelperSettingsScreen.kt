package com.suryaprakash.medlog.ui.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.suryaprakash.medlog.medlog
import com.suryaprakash.medlog.ui.Announce
import com.suryaprakash.medlog.ui.Hint
import com.suryaprakash.medlog.ui.LocalPalette
import com.suryaprakash.medlog.ui.LocalScale
import com.suryaprakash.medlog.ui.LocalSettings
import com.suryaprakash.medlog.ui.Nav
import com.suryaprakash.medlog.ui.Route
import com.suryaprakash.medlog.ui.Screen
import com.suryaprakash.medlog.ui.Text
import com.suryaprakash.medlog.ui.Toggle
import com.suryaprakash.medlog.ui.ValueRow
import kotlinx.coroutines.launch

/** Settings tab when the phone is in helper mode. */
@Composable
fun HelperSettingsScreen(nav: Nav) {
    val ctx = LocalContext.current
    val app = ctx.medlog
    val s = LocalSettings.current
    val sc = LocalScale.current
    val scope = rememberCoroutineScope()
    var section by remember { mutableStateOf<String?>(null) }
    var notifyEntryAdded by remember { mutableStateOf(s.notifyEntryAdded) }

    when (section) {
        "lang" -> LanguagesSection(onBack = { section = null }, onHome = { nav.home() })
        else -> Screen("Settings", "Settings for helping.", onHome = { nav.home(Route.HelperHome) }, onBack = { nav.back() }) {
            com.suryaprakash.medlog.ui.Section("Alerts")
            com.suryaprakash.medlog.ui.Group {
                Toggle("New entry notifications", notifyEntryAdded, "Alert me when entries are added to the log.") { on ->
                    notifyEntryAdded = on
                    app.settings.update { it.copy(notifyEntryAdded = on) }
                    scope.launch {
                        Announce.done(ctx, null, "changed notifications", "setting")
                    }
                }
            }

            com.suryaprakash.medlog.ui.Section("People")
            com.suryaprakash.medlog.ui.Group {
                ValueRow("Who I help", null) { nav.go(Route.Helpers) }
                com.suryaprakash.medlog.ui.GroupLine()
                ValueRow("Pair a phone", null) { nav.go(Route.Pair) }
                com.suryaprakash.medlog.ui.GroupLine()
                ValueRow("Stop helping", null) {
                    scope.launch {
                        app.settings.update { it.copy(helperPin = "") }
                        nav.home(Route.Onboarding)
                    }
                }
            }

            com.suryaprakash.medlog.ui.Section("Lock")
            com.suryaprakash.medlog.ui.Group {
                ValueRow("Helper lock", if (s.helperPin.isNotBlank()) "PIN set" else null, sub = "Hide features, lock settings") { nav.go(Route.HelperLock) }
            }

            com.suryaprakash.medlog.ui.Section("Display")
            com.suryaprakash.medlog.ui.Group {
                fun langs() = s.languages.joinToString(", ") { t -> com.suryaprakash.medlog.clinical.Lang.ALL.firstOrNull { it.tag == t }?.name ?: t }
                ValueRow("Language", langs()) { section = "lang" }
                com.suryaprakash.medlog.ui.GroupLine()
                ValueRow("Text size", if (s.bigMode) "Large" else "Regular") { nav.go(Route.EasySettings) }
            }

            com.suryaprakash.medlog.ui.Section(null)
            androidx.compose.foundation.layout.Box(Modifier.padding(vertical = 12.dp)) {
                Text("Switch to self mode", fontSize = sc.small, color = LocalPalette.current.inkSoft, modifier = Modifier.fillMaxWidth().padding(16.dp))
            }

            Hint("MedLog ${com.suryaprakash.medlog.BuildConfig.VERSION_NAME}")
        }
    }
}
