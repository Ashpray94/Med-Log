package com.suryaprakash.medlog

import kotlinx.coroutines.launch

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.suryaprakash.medlog.help.Sos
import com.suryaprakash.medlog.ui.MedTheme
import com.suryaprakash.medlog.ui.Nav
import com.suryaprakash.medlog.ui.Route
import com.suryaprakash.medlog.ui.screens.*

class MainActivity : ComponentActivity() {
    private lateinit var nav: Nav

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val s = medlog.settings.value
        val root = rootRoute()
        nav = Nav(root).also { n -> n.setupRunning = { !medlog.settings.value.onboarded && medlog.settings.value.role != "helper" } }
        handle(intent)
        setContent {
            val settings by medlog.settings.flow.collectAsState()
            MedTheme(settings) { App(nav) }
        }
    }

    private fun rootRoute(): Route {
        val s = medlog.settings.value
        return when {
            !s.onboarded -> Route.Onboarding
            s.role == "helper" -> Route.HelperHome
            else -> Route.Home
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handle(intent)
    }

    /** medlog://tell?problem=vomiting&text=..., medlog://meds, medlog://help, medlog://doctor, medlog://feature?feature=... */
    private fun handle(i: Intent?) {
        val uri = i?.data ?: return
        if (uri.scheme != "medlog" || !medlog.settings.value.onboarded) return
        val host = uri.host ?: return
        val route: Route? = when (host) {
            "tell" -> Route.Tell(uri.getQueryParameter("problem"), uri.getQueryParameter("text"), noteId = uri.getQueryParameter("note")?.toLongOrNull())
            "meds" -> Route.Meds
            "help" -> {
                // from the family widget: send the chosen message straight away, then show who got it
                uri.getQueryParameter("send")?.let { key ->
                    medlog.settings.value.messages.firstOrNull { it.substringBefore('|') == key }?.let { m ->
                        com.suryaprakash.medlog.ui.screens.HelpMessages.send(this, m.substringAfter('|'))
                        medlog.speaker.say("Sending: ${m.substringAfter('|')}")
                    }
                }
                Route.Help
            }
            "messages" -> Route.Messages
            "call" -> { medlog.scope.launch { medlog.db.helpers().all().firstOrNull()?.let { com.suryaprakash.medlog.help.Calls.call(this@MainActivity, it.phone) } }; null }
            "emergency" -> Route.Emergency
            "sos" -> { Sos.start(this, "SOS"); null }
            "doctor" -> Route.Doctor
            "reports" -> Route.Reports
            "helper" -> Route.HelperHome
            "history" -> Route.Notes
            "checkin" -> Route.Home
            "feature" -> when (uri.getQueryParameter("feature")?.lowercase()?.trim()) {
                "tell", "tell how i feel", "log", "symptom" -> Route.Tell()
                "medicines", "medicine", "meds", "pills" -> Route.Meds
                "help" -> Route.Help
                "sos", "emergency" -> Route.Emergency
                "doctor", "doctor page" -> Route.Doctor
                else -> Route.Home
            }
            else -> null
        }
        if (route != null) { nav.home(rootRoute()); if (route != rootRoute()) nav.go(route) }
    }
}

@Composable
fun App(nav: Nav) {
    BackHandler(enabled = nav.stack.size > 1) { nav.back() }
    androidx.compose.runtime.CompositionLocalProvider(com.suryaprakash.medlog.ui.LocalNav provides nav) { Screens(nav) }
}

@Composable
private fun Screens(nav: Nav) {
    val route = nav.current
    val reduce = com.suryaprakash.medlog.ui.LocalSettings.current.lessMotion
    AnimatedContent(route, transitionSpec = { if (reduce) fadeIn(androidx.compose.animation.core.snap()) togetherWith fadeOut(androidx.compose.animation.core.snap()) else fadeIn() togetherWith fadeOut() }, label = "screen") { r ->
        when (r) {
            Route.Home -> HomeScreen(nav)
            is Route.Tell -> TellScreen(nav, r)
            Route.Notes -> NotesScreen(nav)
            is Route.ProblemHistory -> ProblemHistoryScreen(nav, r.problemId)
            is Route.NoteDetail -> NoteDetailScreen(nav, r.id)
            Route.Removed -> RemovedScreen(nav)
            Route.Search -> SearchScreen(nav)
            Route.Meds -> MedsScreen(nav)
            is Route.MedEdit -> MedEditScreen(nav, r.id)
            Route.DidITake -> DidITakeScreen(nav)
            Route.Food -> FoodScreen(nav)
            Route.Readings -> ReadingsScreen(nav)
            Route.Help -> HelpScreen(nav)
            Route.Messages -> com.suryaprakash.medlog.ui.screens.MessagesScreen(nav)
            Route.Emergency -> com.suryaprakash.medlog.ui.screens.EmergencyScreen(nav)
            Route.Helpers -> HelpersScreen(nav)
            is Route.HelperEdit -> HelperEditScreen(nav, r.id)
            Route.Pair -> PairScreen(nav)
            Route.HelperHome -> HelperHomeScreen(nav)
            Route.Doctor -> DoctorScreen(nav)
            Route.Visit -> VisitScreen(nav)
            Route.Appointments -> AppointmentsScreen(nav)
            Route.Reports -> ReportsScreen(nav)
            Route.Settings -> SettingsScreen(nav)
            Route.EasySettings -> EasySettingsScreen(nav)
            Route.Permissions -> PermissionsScreen(nav)
            Route.Backup -> BackupScreen(nav)
            Route.Privacy -> PrivacyScreen(nav)
            Route.HelperLock -> HelperLockScreen(nav)
            Route.Onboarding -> OnboardingScreen(nav)
            Route.Import -> ImportScreen(nav)
            Route.Devices -> DevicesScreen(nav)
        }
    }
}
