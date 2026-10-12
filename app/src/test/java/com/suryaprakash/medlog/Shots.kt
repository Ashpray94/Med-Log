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
        "onboarding" to Route.Onboarding, "onboardlimits" to Route.Onboarding, "limits" to Route.Limits, "speakall" to Route.SpeakAll(), "feednew" to Route.FeedNew, "foodpick" to Route.FoodPick(),
        "helpers" to Route.Helpers, "visit" to Route.Visit, "permissions" to Route.Permissions, "took" to Route.TookNow, "today" to Route.TodayMeds(), "helperchat" to Route.HelperChat, "messages" to Route.Messages, "notesremoved" to Route.Removed,
        "nutrition" to Route.Nutrition, "easy" to Route.EasySettings, "backup" to Route.Backup, "measure" to Route.Measure("bp"), "measureweight" to Route.Measure("weight"), "measuresymptoms" to Route.Measure("symptoms"),
    )

    /** Phone-sized pictures. A name like "history@Food" opens History, then taps "Food". */
    @Test fun shots() = run(System.getProperty("shots"), "")

    /** Whole pages, top to bottom (SHOTS_TALL=1). */
    @Test @Config(qualifiers = "w393dp-h2600dp-xxhdpi") fun tall() = run(System.getProperty("shots")?.takeIf { System.getProperty("shots.tall") != null }, "-tall")

    /** Very long pages, top to bottom, with the empty stretch above the bottom bar cut out (SHOTS_LONG=1). */
    @Test @Config(qualifiers = "w393dp-h6400dp-xxhdpi") fun long() = run(System.getProperty("shots")?.takeIf { System.getProperty("shots.long") != null }, "-long")

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
            val r = (if (n == "dosechoices") runBlocking {
                com.suryaprakash.medlog.data.Mirror.db(app, "amma").doses().between(0, Long.MAX_VALUE).firstOrNull { it.status == "DUE" }?.uid?.let { Route.DoseChoices("amma", it) }
            } else routes[n]) ?: continue
            if (n.startsWith("onboard")) app.settings.update { it.copy(onboarded = false) }
            // "onboardlimits": setup opened on the personal limits step (the 21st page)
            if (n == "onboardlimits") app.settings.putString("onboard_step", "20")
            // on a helper's phone, the person's pages show the person's records (as when opened from the helper's home)
            val helperView = System.getProperty("shots.role") == "helper" && n !in setOf("helper", "helperchat", "settings", "onboarding", "onboardlimits")
            com.suryaprakash.medlog.data.Viewing.pairId.value = if (helperView) "amma" else null
            rule.runOnUiThread { nav.home(when (n) { "helper" -> Route.HelperHome; "onboarding", "onboardlimits" -> Route.Onboarding; else -> Route.Home }); if (r != Route.Home && n != "helper" && !n.startsWith("onboard")) nav.go(r) }
            rule.mainClock.advanceTimeBy(3000)
            rule.waitForIdle()
            if (n == "doctor") rule.waitUntil(30_000) {
                rule.onAllNodesWithText("Notes").fetchSemanticsNodes().isNotEmpty()
            }
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
            val pic = if (suffix == "-long") trim(bmp) else bmp
            File(out, full.replace('@', '_').replace(' ', '-') + "$suffix.png").outputStream().use { pic.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    /** Cuts the longest run of plain background rows in the lower part of the page down to a small gap. */
    private fun trim(b: Bitmap): Bitmap {
        val bg = b.getPixel(4, b.height / 2)
        fun blank(y: Int) = (0 until b.width step 6).all { b.getPixel(it, y) == bg }
        var bestStart = -1; var bestLen = 0; var start = -1
        for (y in b.height / 4 until b.height) {
            if (blank(y)) { if (start < 0) start = y; val len = y - start + 1; if (len > bestLen) { bestLen = len; bestStart = start } } else start = -1
        }
        if (bestLen < 200) return b
        val keep = 60
        val out = Bitmap.createBitmap(b.width, b.height - bestLen + keep, Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(out)
        c.drawBitmap(b, android.graphics.Rect(0, 0, b.width, bestStart + keep), android.graphics.Rect(0, 0, b.width, bestStart + keep), null)
        c.drawBitmap(b, android.graphics.Rect(0, bestStart + bestLen, b.width, b.height), android.graphics.Rect(0, bestStart + keep, b.width, out.height), null)
        return out
    }

    @Suppress("UNCHECKED_CAST")
    private fun windows(): List<android.view.View> {
        val g = Class.forName("android.view.WindowManagerGlobal").getMethod("getInstance").invoke(null)
        val f = g.javaClass.getDeclaredField("mViews").apply { isAccessible = true }
        return (f.get(g) as List<android.view.View>).filter { it.isShown && it.width > 0 }
    }

    private fun seed(app: MedLogApp, role: String) = runBlocking {
        app.settings.update { it.copy(onboarded = true, role = role) }
        if (System.getProperty("shots.big") != null) app.settings.update { it.copy(bigMode = true) }
        System.getProperty("shots.lang")?.let { l -> app.settings.update { it.copy(languages = listOf(l)) }; com.suryaprakash.medlog.speech.I18n.use(app, l) }
        if (role == "helper") {
            // a helper's phone: one person looked after, their records in its copy, and a message waiting
            com.suryaprakash.medlog.data.People.put(app, com.suryaprakash.medlog.data.CaredFor("amma", com.suryaprakash.medlog.data.Keys.randomB64(32), "Lakshmi"))
            val now = System.currentTimeMillis()
            if (System.getProperty("shots.answered") != null) {
                app.db.inbox().insert(com.suryaprakash.medlog.data.InboxItem(fromName = "Lakshmi", text = "Not so well", kind = "MESSAGE", at = now - 5 * 3600_000L, acked = true))
                app.db.inbox().insert(com.suryaprakash.medlog.data.InboxItem(fromName = "Lakshmi", text = "Please come", kind = "MESSAGE", at = now - 3 * 3600_000L, acked = true))
                app.db.inbox().insert(com.suryaprakash.medlog.data.InboxItem(fromName = "Lakshmi", text = "MedLog: Lakshmi hasn't marked the 11:00 AM medicine (Amlodipine) as taken yet. A quick call may help.", kind = "MESSAGE", at = now - 600_000, acked = true))
            } else app.db.inbox().insert(com.suryaprakash.medlog.data.InboxItem(fromName = "Lakshmi", text = "Please come", kind = "MESSAGE", at = now - 120_000))
            fill(com.suryaprakash.medlog.data.Mirror.db(app, "amma"))
            // the other helpers, as the person's phone shares them, and this helper's own name
            app.settings.putString("my_name", "Ravi")
            app.settings.putString("helpers_of_amma", org.json.JSONArray(listOf(
                org.json.JSONObject().put("name", "Ravi").put("pairId", "amma").put("phone", "+91 98450 12345").put("relation", "Son"),
                org.json.JSONObject().put("name", "Meena").put("pairId", "meena1").put("phone", "+91 94440 67890").put("relation", "Daughter-in-law"),
                org.json.JSONObject().put("name", "Kalyan").put("pairId", "").put("phone", "+91 99000 11122").put("relation", "Neighbour"),
            )).toString())
            com.suryaprakash.medlog.data.People.put(app, com.suryaprakash.medlog.data.CaredFor("amma", com.suryaprakash.medlog.data.Keys.randomB64(32), "Lakshmi", com.suryaprakash.medlog.data.Keys.randomB64(32)))
            if (System.getProperty("shots.worst") != null) worstHelper(app)
            return@runBlocking
        }
        fill(app.db)
        if (System.getProperty("shots.worst") != null) worstSelf(app)
    }

    // ── worst case: long words everywhere, many of everything, every state at once ──
    private val LONG = "Lakshminarayanan Venkatasubramanian"

    private suspend fun worstSelf(app: MedLogApp) {
        app.settings.update { st -> st.copy(messages = listOf(
            "water|Please bring me a glass of warm water with a little salt and sugar",
            "please_come|Please come to my room quickly, I am not feeling well at all",
            "bathroom|I need help to go to the bathroom, I feel dizzy when I stand up",
            "medicine|I can't find my evening medicines, the blue box is empty",
            "hungry|I am hungry, could you make some soft idli with sambar",
            "call|Please call Dr. Ramachandran at Apollo Hospitals and ask about the new tablet",
        )) }
        listOf("Venkataraman Subramaniam (son, lives in Bengaluru)" to "+91 98450 12345", "Meenakshi Sundareswaran" to "+91 94440 67890",
            "Dr. Anantha Padmanabhan (family doctor)" to "+91 80 2222 3333", "Kalyanasundaram (neighbour, flat 4B)" to "+91 99000 11122")
            .forEach { (n, ph) -> app.db.helpers().insert(com.suryaprakash.medlog.data.Helper(name = n, phone = ph)) }
        worstRecords(app.db)
    }

    private suspend fun worstHelper(app: MedLogApp) {
        val now = System.currentTimeMillis()
        app.db.inbox().insert(com.suryaprakash.medlog.data.InboxItem(fromName = "Lakshmi", text = "I have chest pain spreading to my left arm and I am sweating a lot, please come quickly or call the ambulance", kind = "DANGER", at = now - 60_000))
        listOf(
            "MedLog: Lakshmi hasn't marked the 11:00 AM medicine (Isuvaconazole sulfate 100 mg) as taken yet. A quick call may help." to 2L,
            "Please bring me a glass of warm water with a little salt and sugar" to 3L, "Not so well" to 5L, "I'm OK" to 8L,
            "MedLog: Lakshmi pressed the SOS button and would like help. Please call or go to them." to 26L,
        ).forEach { (t, h) -> app.db.inbox().insert(com.suryaprakash.medlog.data.InboxItem(fromName = "Lakshmi", text = t, kind = "MESSAGE", at = now - h * 3600_000L, acked = true)) }
        listOf("Venkataraman" to "I'm going there now, I will be there in about forty minutes because of the traffic on the ring road",
            "Meenakshi" to "Can someone check on Lakshmi? She didn't answer my call twice this afternoon", "Kalyanasundaram" to "I can't go today")
            .forEachIndexed { i, (n, t) -> app.db.inbox().insert(com.suryaprakash.medlog.data.InboxItem(fromName = n, text = t, kind = com.suryaprakash.medlog.help.FamilyChat.KIND, at = now - (i + 1) * 5400_000L)) }
        worstRecords(com.suryaprakash.medlog.data.Mirror.db(app, "amma"))
    }

    private suspend fun worstRecords(db: com.suryaprakash.medlog.data.MedDb) {
        val plan = com.suryaprakash.medlog.data.CarePlan(doctors = listOf(
            com.suryaprakash.medlog.data.CarePlan.Doctor("Dr. Ramachandran Krishnamurthy", "Cardiology and heart failure clinic", "+91 80 4000 1234", "Apollo Hospitals, Bannerghatta Road"),
            com.suryaprakash.medlog.data.CarePlan.Doctor("Dr. Shanthi Venugopal", "Diabetes and thyroid", "+91 44 2811 5555", "Kauvery Hospital, Alwarpet"),
            com.suryaprakash.medlog.data.CarePlan.Doctor("Dr. Mohammed Abdul Rahman", "Lungs and infections", "+91 98860 44444", "")),
            symptoms = listOf("breathless", "cough", "swollen_ankles", "tired", "dizzy"))
        val prof = db.profile().get()
        db.profile().put((prof ?: Profile()).copy(name = LONG, dob = "1938-01-01", sex = "F",
            conditions = "Diabetes, high BP, heart failure, hypothyroidism, chronic kidney disease stage 3, lung fungal infection after chemotherapy", plan = plan.toJson()))
        val today = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val now = System.currentTimeMillis()
        val h = 3600_000L
        var u = 0
        suspend fun med(name: String, strength: String, hours: List<Int>, purpose: String, form: String = "tablet", amount: String = "1", states: List<String?> = emptyList()): Long {
            val id = db.medicines().insert(Medicine(name = name, strength = strength, times = hours.joinToString(",") { "%02d:00".format(it) }, purpose = purpose, form = form, amount = amount, uid = "w${u++}"))
            hours.forEachIndexed { i, hr ->
                val at = today + hr * h
                val st = states.getOrNull(i) ?: if (at < now - 20 * 60_000) DoseStatus.MISSED else DoseStatus.DUE
                db.doses().insert(Dose(medicineId = id, scheduledAt = at, status = st, actedAt = if (st == DoseStatus.TAKEN) at + 25 * 60_000 else null, uid = "wd${u++}"))
            }
            return id
        }
        med("Isuvaconazole sulfate (Cresemba) capsules", "100 mg", listOf(6, 11, 16, 21), "Fungal infection of the lungs after chemotherapy", "capsule", "2",
            listOf(DoseStatus.TAKEN, DoseStatus.MISSED, null, null))
        med("Metformin + Glimepiride extended release", "500 mg / 2 mg", listOf(8, 14, 20), "Diabetes", states = listOf(DoseStatus.SKIPPED, null, null))
        med("Budesonide + Formoterol inhaler (Foracort 400)", "400 mcg", listOf(9, 21), "Breathing", "inhaler", "2 puffs", listOf(DoseStatus.TAKEN, null))
        med("Levothyroxine", "100 mcg", listOf(6), "Thyroid", states = listOf(DoseStatus.TAKEN))
        med("Furosemide", "40 mg", listOf(8, 16), "Water in the legs and lungs", states = listOf(DoseStatus.MISSED, null))
        med("Insulin Glargine (Lantus SoloStar pen)", "18 units", listOf(22), "Diabetes", "injection", "18 units")
        // one due right now, whatever the time of day
        val dueId = db.medicines().insert(Medicine(name = "Pantoprazole + Domperidone", strength = "40 mg / 30 mg", times = "%02d:00".format(java.time.LocalTime.now().hour), purpose = "Acidity and vomiting", uid = "wdue"))
        db.doses().insert(Dose(medicineId = dueId, scheduledAt = now - 5 * 60_000, uid = "wdue1"))
        val mix = med("Mix - Plant protein powder + Ensure + ragi malt with jaggery", "", listOf(7, 10, 13, 16, 19, 21), "", "feed", "200 ml",
            listOf(DoseStatus.TAKEN, DoseStatus.SKIPPED, DoseStatus.MISSED, null, null, null))
        // the days before: some given, one food instead, some missed
        (1..3).forEach { back ->
            listOf(7, 10, 13, 16, 19, 21).forEachIndexed { i, hr ->
                val st = listOf(DoseStatus.TAKEN, DoseStatus.TAKEN, DoseStatus.MISSED, DoseStatus.SKIPPED, DoseStatus.TAKEN, DoseStatus.MISSED)[(i + back) % 6]
                db.doses().insert(Dose(medicineId = mix, scheduledAt = today - back * 24 * h + hr * h, status = st, reason = if (st == DoseStatus.SKIPPED) "Ate food instead" else null, uid = "wh${u++}"))
            }
        }
        med("Tender coconut water", "", listOf(11, 17), "", "feed", "250 ml")
        fun note(kind: String, pid: String?, at: Long, text: String, triage: String = "GREEN") =
            Note(kind = kind, problemId = pid, occurredAt = at, text = text, triage = triage, uid = "wn${u++}")
        listOf(
            note(Kind.SYMPTOM, "chest_pain", now - 40 * 60_000, "Chest pain, pressing, spreading to the left arm and jaw, with sweating, for about 20 minutes after climbing the stairs", "RED"),
            note(Kind.SYMPTOM, "breathless", today + 7 * h, "Hard to breathe when lying flat, needs three pillows, worse than last week, woke up twice at night", "AMBER"),
            note(Kind.SYMPTOM, "swollen_ankles", today + 8 * h, "Swollen ankles, both legs, pressing leaves a dent for a few seconds", "AMBER"),
            note(Kind.SYMPTOM, "dizzy", today + 9 * h + 1_200_000, "Dizzy on standing up, room spinning, nearly fell in the bathroom"),
            note(Kind.SYMPTOM, "vomiting", today + 10 * h, "Vomiting, 3 times, yellow, after the morning tablets"),
            note(Kind.SYMPTOM, "cough", today - 20 * h, "Cough with white phlegm, worse at night, for 12 days"),
            note(Kind.SYMPTOM, "tired", today - 30 * h, "Very tired, sleeping most of the afternoon"),
            note(Kind.SYMPTOM, "no_appetite", today - 50 * h, "No appetite, ate only half an idli"),
            note(Kind.SYMPTOM, "headache", today - 3 * 24 * h, "Headache on the right side, throbbing"),
            note(Kind.FOOD, null, today + 8 * h + 1_800_000, "Idli, 2 · Sambar · Coconut chutney · Filter coffee with sugar").copy(details = """{"items":[{"name":"idli","amount":"2"},{"name":"sambar","amount":"1 katori"},{"name":"coconut chutney","amount":"2 tbsp"},{"name":"filter coffee","amount":"1 tumbler"},{"name":"banana","amount":"1"}],"kcal":480}"""),
            note(Kind.FOOD, null, today + 13 * h, "Rice · Dal · Curd · Beans poriyal · Rasam · Papad").copy(details = """{"items":[{"name":"rice","amount":"1 plate"},{"name":"dal","amount":"1 katori"},{"name":"curd","amount":"1 katori"},{"name":"beans poriyal","amount":"1 katori"},{"name":"rasam","amount":"1 katori"},{"name":"papad","amount":"1"}],"kcal":720}"""),
            note(Kind.OUTPUT, "urine", today + 6 * h, "Urine, dark yellow, burning, small amount"),
            note(Kind.OUTPUT, "stool", today + 7 * h, "Stool, black and sticky, once"),
            note(Kind.OUTPUT, "vomit", today + 10 * h, "Vomit, yellow, about half a cup"),
            note(Kind.READING, null, today + 7 * h, "BP 168/104").copy(details = """{"type":"bp","v1":168,"v2":104,"unit":"mmHg"}"""),
            note(Kind.READING, null, today + 7 * h + 60_000, "Sugar 286 mg/dL").copy(details = """{"type":"sugar","v1":286,"unit":"mg/dL"}"""),
            note(Kind.READING, null, today + 7 * h + 120_000, "Oxygen 89%").copy(details = """{"type":"spo2","v1":89,"unit":"%"}"""),
            note(Kind.READING, null, today - 24 * h, "BP 150/96").copy(details = """{"type":"bp","v1":150,"v2":96,"unit":"mmHg"}"""),
            note(Kind.QUESTION, null, today - 24 * h, "Question for doctor: Can I stop the water tablet on days when I have loose motions and feel very dizzy?"),
        ).forEach { db.notes().insert(it) }
        (0 until 9).forEach { i -> db.notes().insert(note(Kind.WATER, null, today + (6 + i) * h + 900_000, "1 glass of water").copy(count = 1)) }
    }

    private suspend fun fill(db: com.suryaprakash.medlog.data.MedDb) {
        db.profile().put(Profile(name = "Lakshmi Narayanan", dob = "1948-01-01", sex = "F", conditions = "Diabetes, high BP"))
        val today = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val h = 3600_000L
        val met = db.medicines().insert(Medicine(name = "Metformin", strength = "500 mg", times = "08:00,14:00,20:00", purpose = "Diabetes", food = "after", uid = "m1"))
        val aml = db.medicines().insert(Medicine(name = "Amlodipine", strength = "5 mg", times = "08:00", purpose = "Blood pressure", uid = "m2"))
        val para = db.medicines().insert(Medicine(name = "Paracetamol", strength = "650 mg", times = "06:00,12:00,18:00,22:00", purpose = "Pain", uid = "m4"))
        listOf(6, 12, 18, 22).forEach { hr -> db.doses().insert(Dose(medicineId = para, scheduledAt = today + hr * h, status = if (hr == 6) DoseStatus.TAKEN else DoseStatus.DUE, actedAt = if (hr == 6) today + 6 * h else null, uid = "p$hr")) }
        db.medicines().insert(Medicine(name = "Ensure", form = "feed", amount = "200 ml", times = "10:00,16:00,21:00", uid = "m3"))
        db.doses().insert(Dose(medicineId = met, scheduledAt = today + 8 * h, status = DoseStatus.TAKEN, actedAt = today + 8 * h + 300_000, uid = "d1"))
        db.doses().insert(Dose(medicineId = met, scheduledAt = today + 14 * h, uid = "d2"))
        db.doses().insert(Dose(medicineId = met, scheduledAt = today + 20 * h, uid = "d3"))
        db.doses().insert(Dose(medicineId = aml, scheduledAt = today + 1 * h, status = DoseStatus.MISSED, uid = "d4"))
        fun note(kind: String, pid: String?, at: Long, text: String) = Note(kind = kind, problemId = pid, occurredAt = at, text = text, uid = "n$at$kind")
        listOf(
            note(Kind.SYMPTOM, "cough", today + 9 * h, "Cough, dry, for 3 days"),
            note(Kind.SYMPTOM, "vomiting", today + 11 * h, "Vomiting, 2 times"),
            note(Kind.SYMPTOM, "tired", today - 20 * h, "Tired"),
            note(Kind.FOOD, null, today + 8 * h + 1_800_000, "Idli, 2 · Sambar").copy(details = """{"items":[{"name":"idli","amount":"2"},{"name":"sambar","amount":"1 katori"},{"name":"coconut chutney","amount":"2 tbsp"}],"kcal":310}"""),
            note(Kind.FOOD, null, today + 13 * h, "Rice · Dal · Curd").copy(details = """{"items":[{"name":"rice","amount":"1 plate"},{"name":"dal","amount":"1 katori"},{"name":"curd","amount":"1 katori"}],"kcal":520}"""),
            note(Kind.WATER, null, today + 10 * h, "1 glass of water"),
            note(Kind.WATER, null, today + 12 * h, "1 glass of water"),
            note(Kind.OUTPUT, "urine", today + 7 * h, "Urine, normal"),
        ).forEach { db.notes().insert(it) }
    }
}
