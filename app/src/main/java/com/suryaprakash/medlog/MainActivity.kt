package com.suryaprakash.medlog

import kotlinx.coroutines.launch

import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.statusBars
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
        handle(intent)
        com.suryaprakash.medlog.feedback.FeedbackWorker.enqueueIfPending(this)
        // Shake to report: listen only while this screen is showing, and only if the setting is on.
        val shake = com.suryaprakash.medlog.feedback.ShakeDetector(this) { com.suryaprakash.medlog.feedback.Capture.openFeedback(this, nav) }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                try { medlog.settings.flow.collect { if (it.shakeOn) shake.start() else shake.stop() } } finally { shake.stop() }
            }
        }
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
        // links that do something (call, SOS, send a message) only act when MedLog made the link itself
        val trusted = com.suryaprakash.medlog.integration.TrustedLinks.isTrusted(i, this)
        val action = com.suryaprakash.medlog.integration.LinkPolicy.linkAction(host, uri.getQueryParameter("send") != null, trusted)
        val route: Route? = when (host) {
            "tell" -> Route.Tell(uri.getQueryParameter("problem"), uri.getQueryParameter("text"), noteId = uri.getQueryParameter("note")?.toLongOrNull())
            "meds" -> Route.Meds
            "help" -> {
                // from the family widget: send the chosen message straight away, then show who got it
                uri.getQueryParameter("send")?.takeIf { action == com.suryaprakash.medlog.integration.LinkPolicy.SEND }?.let { key ->
                    medlog.settings.value.messages.firstOrNull { it.substringBefore('|') == key }?.let { m ->
                        com.suryaprakash.medlog.ui.screens.HelpMessages.send(this, m.substringAfter('|'))
                        medlog.speaker.say("Sending: ${m.substringAfter('|')}")
                    }
                }
                Route.Help
            }
            "messages" -> Route.Messages
            "call" -> if (action != com.suryaprakash.medlog.integration.LinkPolicy.CALL) Route.Emergency else { medlog.scope.launch { medlog.ownDb.helpers().all().firstOrNull()?.let { com.suryaprakash.medlog.help.Calls.call(this@MainActivity, it.phone) } }; null }
            "emergency" -> Route.Emergency
            "sos" -> if (action != com.suryaprakash.medlog.integration.LinkPolicy.SOS) Route.Emergency else { Sos.start(this, "SOS"); null }
            "doctor" -> Route.Doctor
            // any main screen by name (used by shortcuts and for checking screens)
            "open" -> when (uri.getQueryParameter("name")) {
                "meds" -> Route.Meds; "medadd" -> Route.MedEdit(null); "didtake" -> Route.DidITake; "food" -> Route.Food; "readings" -> Route.Readings
                "family" -> Route.Help; "messages" -> Route.Messages; "helpers" -> Route.Helpers; "helperadd" -> Route.HelperEdit(null); "pair" -> Route.Pair
                "visit" -> Route.Visit; "appointments" -> Route.Appointments; "reports" -> Route.Reports; "settings" -> Route.Settings
                "easy" -> Route.EasySettings; "permissions" -> Route.Permissions; "backup" -> Route.Backup; "privacy" -> Route.Privacy
                "search" -> Route.Search; "removed" -> Route.Removed; "import" -> Route.Import; "devices" -> Route.Devices; "history" -> Route.Notes
                else -> null
            }
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
    val app = com.suryaprakash.medlog.MedLogApp.app
    val viewed by app.viewing.state.collectAsState()
    androidx.compose.runtime.CompositionLocalProvider(com.suryaprakash.medlog.ui.LocalNav provides nav) {
        // a switch of database rebuilds every page, so nothing keeps showing the other person's data
        androidx.compose.runtime.key(viewed?.pairId) {
            if (viewed == null) Screens(nav)
            else androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.fillMaxSize()) {
                ViewingBanner(viewed!!.name) { app.viewing.back(); nav.home(if (app.settings.value.role == "helper") Route.HelperHome else Route.Home) }
                androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.weight(1f).consumeWindowInsets(androidx.compose.foundation.layout.WindowInsets.statusBars)) { Screens(nav) }
            }
        }
    }
}

/** Always on top while a replica is open: whose MedLog this is, and the way back. */
@Composable
private fun ViewingBanner(name: String, onBack: () -> Unit) {
    val p = com.suryaprakash.medlog.ui.LocalPalette.current
    val who = name.ifBlank { "their" }.let { if (name.isBlank()) it else "$it's" }
    androidx.compose.foundation.layout.Row(androidx.compose.ui.Modifier.fillMaxWidth().background(p.brand).statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        androidx.compose.material3.Text("Viewing $who MedLog", color = androidx.compose.ui.graphics.Color.White, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            modifier = androidx.compose.ui.Modifier.weight(1f))
        androidx.compose.material3.Text("Back to mine", color = androidx.compose.ui.graphics.Color.White, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
            modifier = androidx.compose.ui.Modifier.heightIn(min = 48.dp).clickable(onClickLabel = "Back to my own MedLog") { onBack() }.padding(start = 12.dp, top = 12.dp))
    }
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
            Route.Food -> FoodScreen(nav)
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
            Route.Doctor -> DoctorScreen(nav)
            Route.Visit -> VisitScreen(nav)
            Route.Appointments -> AppointmentsScreen(nav)
            Route.Reports -> ReportsScreen(nav)
            Route.Nutrition -> NutritionScreen(nav)
            Route.Settings -> SettingsScreen(nav)
            Route.EasySettings -> EasySettingsScreen(nav)
            Route.Permissions -> PermissionsScreen(nav)
            Route.Backup -> BackupScreen(nav)
            Route.Privacy -> PrivacyScreen(nav)
            Route.HelperLock -> HelperLockScreen(nav)
            Route.Onboarding -> OnboardingScreen(nav)
            Route.Import -> ImportScreen(nav)
            Route.Devices -> DevicesScreen(nav)
            Route.Feedback -> com.suryaprakash.medlog.feedback.FeedbackScreen(nav)
            Route.MyReports -> com.suryaprakash.medlog.feedback.MyReportsScreen(nav)
            Route.Limits -> LimitsScreen(nav)
        }
    }
}
