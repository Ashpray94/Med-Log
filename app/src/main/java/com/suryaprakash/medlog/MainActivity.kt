package com.suryaprakash.medlog

import kotlinx.coroutines.launch

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
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
        // a link is acted on once: not again when the screen is rebuilt (turning the phone, dark mode, text size) or the
        // app is reopened from Recents, which hands back the same link. Acting on it again made a second, empty note.
        val fromRecents = ((intent?.flags ?: 0) and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0
        if (savedInstanceState == null && !fromRecents) handle(intent)
        consume()
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

    // back in front: look for a new version (at most every 30 minutes)
    override fun onResume() {
        super.onResume()
        medlog.scope.launch { runCatching { com.suryaprakash.medlog.Updater.dailyCheck(this@MainActivity) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handle(intent)
        consume()
    }

    /** Forgets the link once it has been acted on. */
    private fun consume() { intent?.let { setIntent(Intent(it).setData(null)) } }

    /** medlog://tell?problem=vomiting&text=..., medlog://meds, medlog://help, medlog://doctor, medlog://feature?feature=... */
    private fun handle(i: Intent?) {
        val uri = i?.data ?: return
        if (uri.scheme != "medlog" || !medlog.settings.value.onboarded) return
        val host = uri.host ?: return
        // from a widget's "Add details": its question is answered here, so it goes back to its usual face
        uri.getQueryParameter("clear")?.let { src ->
            medlog.settings.putString("${src}_ask", null)
            medlog.scope.launch { com.suryaprakash.medlog.widget.MedLogWidget.refresh(this@MainActivity) }
        }
        val route: Route? = when (host) {
            "tell" -> Route.Tell(uri.getQueryParameter("problem"), uri.getQueryParameter("text"), noteId = uri.getQueryParameter("note")?.toLongOrNull())
            "meds" -> Route.Meds
            "speak" -> Route.Tell(speak = true)
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
            // any main screen by name (used by shortcuts and for checking screens)
            "open" -> when (uri.getQueryParameter("name")) {
                "meds" -> Route.Meds; "medadd" -> Route.MedEdit(null); "foodadd" -> Route.FoodPick(); "toilet" -> Route.Output(); "didtake" -> Route.DidITake; "took" -> Route.TookNow; "food" -> Route.Food; "readings" -> Route.Readings
                "family" -> Route.Help; "messages" -> Route.Messages; "helpers" -> Route.Helpers; "helperadd" -> Route.HelperEdit(null); "pair" -> Route.Pair
                "visit" -> Route.Visit; "appointments" -> Route.Appointments; "reports" -> Route.Reports; "settings" -> Route.Settings
                "easy" -> Route.EasySettings; "permissions" -> Route.Permissions; "backup" -> Route.Backup; "privacy" -> Route.Privacy
                "search" -> Route.Search; "removed" -> Route.Removed; "import" -> Route.Import; "devices" -> Route.Devices; "history" -> Route.Notes
                else -> null
            }
            "reports" -> Route.Reports
            // debug builds only: pair this phone with itself through the relay, to test sharing end to end on one device
            "debugloop" -> if (!BuildConfig.DEBUG) null else {
                medlog.scope.launch {
                    val key = com.suryaprakash.medlog.data.Keys.randomB64(32)
                    com.suryaprakash.medlog.data.Sync.forget(this@MainActivity, "looptest")
                    medlog.db.helpers().insert(com.suryaprakash.medlog.data.Helper(name = "Loop helper", phone = "0000000", pairId = "looptest", pairKey = key, sos = false, alerts = false))
                    com.suryaprakash.medlog.data.People.put(this@MainActivity, com.suryaprakash.medlog.data.CaredFor("looptestB", key, "Loop person"))
                    medlog.settings.update { it.copy(internetLink = true) }
                    com.suryaprakash.medlog.help.Nearby.startListening(this@MainActivity)
                }
                null
            }
            // debug builds only: fire the next medicine alarm now, to test the alarm screen and swipe-to-silence
            "debugalarm" -> if (!BuildConfig.DEBUG) null else {
                medlog.scope.launch {
                    val d = medlog.db.doses().between(System.currentTimeMillis() - 3 * com.suryaprakash.medlog.data.HOUR, System.currentTimeMillis() + 2 * com.suryaprakash.medlog.data.DAY)
                        .firstOrNull { it.status == com.suryaprakash.medlog.data.DoseStatus.DUE && medlog.db.medicines().get(it.medicineId)?.form != "feed" }
                    if (d != null) com.suryaprakash.medlog.meds.DoseAlert.show(this@MainActivity, listOf(d), louder = false)
                }
                null
            }
            "debugunloop" -> if (!BuildConfig.DEBUG) null else {
                medlog.scope.launch {
                    medlog.db.helpers().all().filter { it.pairId == "looptest" }.forEach { medlog.db.helpers().delete(it.id) }
                    com.suryaprakash.medlog.data.People.remove(this@MainActivity, "looptestB")
                    com.suryaprakash.medlog.help.Nearby.startListening(this@MainActivity)
                }
                null
            }
            "output" -> Route.Output(uri.getQueryParameter("tab")?.toIntOrNull()?.coerceIn(0, 2) ?: 0)
            "helper" -> Route.HelperHome
            "dose" -> { val pr = uri.getQueryParameter("pair"); val u = uri.getQueryParameter("uid"); if (pr != null && u != null) Route.DoseChoices(pr, u) else Route.HelperHome }
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
    // Some tasks open as a sheet over the page they came from (adding a medicine). The page behind stays visible,
    // pushed back like a card in a stack, so it's clear the sheet is on top.
    val sheetRoute = route as? Route.MedEdit
    var lastSheet by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<Route.MedEdit?>(null) }
    if (sheetRoute != null) lastSheet = sheetRoute
    val base = if (sheetRoute != null) nav.stack.getOrNull(nav.stack.size - 2) ?: Route.Home else route
    val pushed by androidx.compose.animation.core.animateFloatAsState(if (sheetRoute != null) 1f else 0f,
        androidx.compose.animation.core.tween(if (reduce) 0 else 380), label = "stack")
    androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(0xFF1C1C1E))) {
        androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize()
            .graphicsLayer {
                val k = 1f - 0.07f * pushed
                scaleX = k; scaleY = k
                translationY = 18.dp.toPx() * pushed
                shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp * pushed)
                clip = pushed > 0f
            }) {
            BaseScreens(nav, base, reduce)
            if (pushed > 0f) androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.3f * pushed)))
        }
        androidx.compose.animation.AnimatedVisibility(sheetRoute != null,
            enter = androidx.compose.animation.slideInVertically(androidx.compose.animation.core.tween(if (reduce) 0 else 380)) { it },
            exit = androidx.compose.animation.slideOutVertically(androidx.compose.animation.core.tween(if (reduce) 0 else 300)) { it }) {
            androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize().statusBarsPadding().padding(top = 36.dp)) {
                androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize()
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(com.suryaprakash.medlog.ui.LocalPalette.current.paper)) {
                    lastSheet?.let { com.suryaprakash.medlog.ui.screens.MedicineFlow(nav, it.id, inSheet = true) }
                }
            }
        }
    }
}

@Composable
private fun BaseScreens(nav: Nav, route: Route, reduce: Boolean) {
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
            Route.TookNow -> TookNowScreen(nav)
            is Route.TodayMeds -> com.suryaprakash.medlog.ui.screens.TodayMedsScreen(nav, r.feeds)
            Route.Food -> FoodScreen(nav)
            is Route.SpeakAll -> SpeakAllScreen(nav, r.text)
            is Route.Output -> OutputScreen(nav, r.tab)
            is Route.FoodPick -> FoodPickScreen(nav, r.noteId)
            Route.FeedNew -> FeedNewScreen(nav)
            Route.Readings -> ReadingsScreen(nav)
            Route.Help -> HelpScreen(nav)
            Route.Messages -> com.suryaprakash.medlog.ui.screens.MessagesScreen(nav)
            Route.Emergency -> com.suryaprakash.medlog.ui.screens.EmergencyScreen(nav)
            Route.Helpers -> HelpersScreen(nav)
            is Route.HelperEdit -> HelperEditScreen(nav, r.id)
            Route.Pair -> PairScreen(nav)
            Route.HelperHome -> HelperHomeScreen(nav)
            Route.HelperChat -> HelperChatScreen(nav)
            Route.Doctor -> DoctorScreen(nav)
            Route.Visit -> VisitScreen(nav)
            Route.Appointments -> AppointmentsScreen(nav)
            Route.Reports -> ReportsScreen(nav)
            is Route.Measure -> com.suryaprakash.medlog.ui.screens.MeasureScreen(nav, r.key)
            Route.Nutrition -> NutritionScreen(nav)
            Route.Settings -> SettingsScreen(nav)   // one Settings page for both modes (owner: "Settings page should never change")
            Route.EasySettings -> EasySettingsScreen(nav)
            Route.Permissions -> PermissionsScreen(nav)
            Route.Backup -> BackupScreen(nav)
            Route.Privacy -> PrivacyScreen(nav)
            Route.HelperLock -> HelperLockScreen(nav)
            is Route.DoseChoices -> com.suryaprakash.medlog.ui.screens.DoseChoicesScreen(nav, r.pairId, r.uid)
            Route.Onboarding -> OnboardingScreen(nav)
            Route.Import -> ImportScreen(nav)
            Route.Devices -> DevicesScreen(nav)
        }
    }
}
