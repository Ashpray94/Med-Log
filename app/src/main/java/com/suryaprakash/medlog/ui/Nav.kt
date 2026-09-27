package com.suryaprakash.medlog.ui

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.staticCompositionLocalOf

/** The app's navigation, for the bottom bar on every screen. Null inside full-screen alerts. */
val LocalNav = staticCompositionLocalOf<Nav?> { null }

/** Every screen in the app (plan section 5). */
sealed interface Route {
    data object Home : Route
    data class Tell(val problemId: String? = null, val text: String? = null, val pick: Boolean = false, val noteId: Long? = null, val speak: Boolean = false) : Route
    data object Notes : Route
    data class ProblemHistory(val problemId: String) : Route
    data class NoteDetail(val id: Long) : Route
    data object Removed : Route
    data object Meds : Route
    data class MedEdit(val id: Long? = null) : Route
    data object DidITake : Route
    data object Food : Route
    data class SpeakAll(val text: String? = null) : Route
    data class Output(val tab: Int = -1) : Route
    data class FoodPick(val noteId: Long? = null) : Route
    data object FeedNew : Route
    data object Readings : Route
    data object Help : Route
    data object Messages : Route
    data object Emergency : Route
    data object Helpers : Route
    data class HelperEdit(val id: Long? = null) : Route
    data object Pair : Route
    data object Doctor : Route
    data object Visit : Route
    data object Appointments : Route
    data object Reports : Route
    data object Nutrition : Route
    data object Settings : Route
    data object EasySettings : Route
    data object Permissions : Route
    data object Onboarding : Route
    data object HelperHome : Route
    data object HelperChat : Route
    data object Import : Route
    data object Search : Route
    data object Devices : Route
    data object Backup : Route
    data object Privacy : Route
    data object HelperLock : Route
}

/** A plain back stack. Back always goes one step back; Home always goes home (plan 4.3 #13). */
class Nav(start: Route) {
    val stack = mutableStateListOf(start)
    val current: Route get() = stack.last()
    fun go(r: Route) { if (stack.last() != r) stack.add(r) }
    fun replace(r: Route) { stack.removeAt(stack.lastIndex); stack.add(r) }
    fun back(): Boolean { if (stack.size <= 1) return false; stack.removeAt(stack.lastIndex); return true }
    /** While first-time setup is running, "home" means back into setup, never past it. */
    var setupRunning: () -> Boolean = { false }
    fun home(root: Route = Route.Home) {
        if (root == Route.Home && setupRunning()) { while (stack.size > 1 && stack.last() != Route.Onboarding) stack.removeAt(stack.lastIndex); return }
        stack.clear(); stack.add(root)
    }
}
