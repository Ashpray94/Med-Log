package com.suryaprakash.medlog

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.suryaprakash.medlog.data.Dose
import com.suryaprakash.medlog.data.DoseStatus
import com.suryaprakash.medlog.data.Kind
import com.suryaprakash.medlog.data.Medicine
import com.suryaprakash.medlog.data.Note
import com.suryaprakash.medlog.data.Profile
import com.suryaprakash.medlog.ui.MedTheme
import com.suryaprakash.medlog.ui.Nav
import com.suryaprakash.medlog.ui.Route
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

/**
 * Pictures of real screens, drawn on the computer (no phone or emulator needed), with a day of sample records.
 * Run: scripts/shots.sh [route …]   → build/shots/<route>.png
 * Skipped in normal test runs unless -Dshots is set.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w393dp-h873dp-xxhdpi")
class Shots {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val routes: Map<String, Route> = mapOf(
        "home" to Route.Home, "tell" to Route.Tell(), "history" to Route.Notes, "meds" to Route.Meds, "food" to Route.Food,
        "output" to Route.Output(), "readings" to Route.Readings, "help" to Route.Help, "sos" to Route.Emergency,
        "helper" to Route.HelperHome, "doctor" to Route.Doctor, "reports" to Route.Reports, "settings" to Route.Settings,
        "onboarding" to Route.Onboarding, "speakall" to Route.SpeakAll(), "feednew" to Route.FeedNew, "foodpick" to Route.FoodPick(),
        "helpers" to Route.Helpers, "visit" to Route.Visit, "permissions" to Route.Permissions,
    )

    /** Phone-sized pictures. A name like "history@Food" opens History, then taps "Food". */
    @Test fun shots() = run(System.getProperty("shots"), "")

    /** Whole pages, top to bottom (SHOTS_TALL=1). */
    @Test @Config(qualifiers = "w393dp-h2600dp-xxhdpi") fun tall() = run(System.getProperty("shots")?.takeIf { System.getProperty("shots.tall") != null }, "-tall")

    private fun run(want: String?, suffix: String) {
        assumeTrue(want != null)
        val app = rule.activity.application as MedLogApp
        seed(app, System.getProperty("shots.role") ?: "self")
        val out = File(System.getProperty("shots.dir") ?: "build/shots").apply { mkdirs() }
        val names = want!!.split(',').map { it.trim() }.filter { it.isNotEmpty() }.ifEmpty { routes.keys.toList() }
        val nav = Nav(Route.Home)
        rule.setContent {
            val s by app.settings.flow.collectAsState()
            MedTheme(s) { App(nav) }
        }
        for (full in names) {
            val n = full.substringBefore('@')
            val r = routes[n] ?: continue
            rule.runOnUiThread { nav.home(if (n == "helper") Route.HelperHome else Route.Home); if (r != Route.Home && n != "helper") nav.go(r) }
            rule.mainClock.advanceTimeBy(3000)
            rule.waitForIdle()
            full.split('@').drop(1).forEach { tap ->
                val exact = runCatching { rule.onAllNodesWithText(tap).onFirst().assertExists() }.isSuccess
                runCatching { rule.onAllNodesWithText(tap, substring = !exact).onFirst().performScrollTo() }
                runCatching { rule.onAllNodesWithText(tap, substring = !exact).onFirst().performClick() }
                rule.mainClock.advanceTimeBy(1500); rule.waitForIdle()
            }
            // draw the window straight into a picture (the test library's capture waits for a real screen)
            // every window, bottom to top, so sheets and dialogs show over the page
            val v = rule.activity.window.decorView
            val bmp = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
            rule.runOnUiThread {
                val c = android.graphics.Canvas(bmp)
                windows().forEach { w ->
                    val at = IntArray(2); w.getLocationOnScreen(at)
                    c.save(); c.translate(at[0].toFloat(), at[1].toFloat()); w.draw(c); c.restore()
                }
            }
            File(out, full.replace('@', '_').replace(' ', '-') + "$suffix.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun windows(): List<android.view.View> {
        val g = Class.forName("android.view.WindowManagerGlobal").getMethod("getInstance").invoke(null)
        val f = g.javaClass.getDeclaredField("mViews").apply { isAccessible = true }
        return (f.get(g) as List<android.view.View>).filter { it.isShown && it.width > 0 }
    }

    private fun seed(app: MedLogApp, role: String) = runBlocking {
        app.settings.update { it.copy(onboarded = true, role = role) }
        app.db.profile().put(Profile(name = "Lakshmi Narayanan", dob = "1948-01-01", sex = "F", conditions = "Diabetes, high BP"))
        val today = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val h = 3600_000L
        val met = app.db.medicines().insert(Medicine(name = "Metformin", strength = "500 mg", times = "08:00,14:00,20:00", purpose = "Diabetes", food = "after", uid = "m1"))
        val aml = app.db.medicines().insert(Medicine(name = "Amlodipine", strength = "5 mg", times = "08:00", purpose = "Blood pressure", uid = "m2"))
        val para = app.db.medicines().insert(Medicine(name = "Paracetamol", strength = "650 mg", times = "06:00,12:00,18:00,22:00", purpose = "Pain", uid = "m4"))
        listOf(6, 12, 18, 22).forEach { hr -> app.db.doses().insert(Dose(medicineId = para, scheduledAt = today + hr * h, status = if (hr == 6) DoseStatus.TAKEN else DoseStatus.DUE, actedAt = if (hr == 6) today + 6 * h else null, uid = "p$hr")) }
        app.db.medicines().insert(Medicine(name = "Ensure", form = "feed", amount = "200 ml", times = "10:00,16:00,21:00", uid = "m3"))
        app.db.doses().insert(Dose(medicineId = met, scheduledAt = today + 8 * h, status = DoseStatus.TAKEN, actedAt = today + 8 * h + 300_000, uid = "d1"))
        app.db.doses().insert(Dose(medicineId = met, scheduledAt = today + 14 * h, uid = "d2"))
        app.db.doses().insert(Dose(medicineId = met, scheduledAt = today + 20 * h, uid = "d3"))
        app.db.doses().insert(Dose(medicineId = aml, scheduledAt = today + 1 * h, status = DoseStatus.MISSED, uid = "d4"))
        fun note(kind: String, pid: String?, at: Long, text: String) = Note(kind = kind, problemId = pid, occurredAt = at, text = text, uid = "n$at$kind")
        listOf(
            note(Kind.SYMPTOM, "cough", today + 9 * h, "Cough, dry, for 3 days"),
            note(Kind.SYMPTOM, "vomiting", today + 11 * h, "Vomiting, 2 times"),
            note(Kind.SYMPTOM, "tiredness", today - 20 * h, "Tired"),
            note(Kind.FOOD, null, today + 8 * h + 1_800_000, "Idli, 2 · Sambar").copy(details = """{"items":[{"name":"idli","amount":"2"},{"name":"sambar","amount":"1 katori"},{"name":"coconut chutney","amount":"2 tbsp"}],"kcal":310}"""),
            note(Kind.FOOD, null, today + 13 * h, "Rice · Dal · Curd").copy(details = """{"items":[{"name":"rice","amount":"1 plate"},{"name":"dal","amount":"1 katori"},{"name":"curd","amount":"1 katori"}],"kcal":520}"""),
            note(Kind.WATER, null, today + 10 * h, "1 glass of water"),
            note(Kind.WATER, null, today + 12 * h, "1 glass of water"),
            note(Kind.OUTPUT, "urine", today + 7 * h, "Urine, normal"),
        ).forEach { app.db.notes().insert(it) }
    }
}
