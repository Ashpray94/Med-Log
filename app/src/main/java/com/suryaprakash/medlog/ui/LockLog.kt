package com.suryaprakash.medlog.ui

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import com.suryaprakash.medlog.medlog
import com.suryaprakash.medlog.ui.screens.SpeakAllScreen

/**
 * Logging from the lock screen: opens over it without unlocking, and shows only "Speak it all" and what was saved.
 * Nothing already recorded (history, medicines, the doctor page) can be seen from here; anything past saving
 * closes this and asks the phone to unlock.
 */
class LockLogActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) { setShowWhenLocked(true); setTurnScreenOn(true) }
        else @Suppress("DEPRECATION") window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        val nav = Nav(Route.SpeakAll())
        setContent {
            val settings by medlog.settings.flow.collectAsState()
            // leaving the logging screen (Done, Home, opening an entry) ends this: the rest needs the phone unlocked
            LaunchedEffect(Unit) { snapshotFlow { nav.stack.toList() }.collect { if (it.lastOrNull() !is Route.SpeakAll) finish() } }
            MedTheme(settings) {
                CompositionLocalProvider(LocalNav provides null) { SpeakAllScreen(nav) }
            }
        }
    }
}
