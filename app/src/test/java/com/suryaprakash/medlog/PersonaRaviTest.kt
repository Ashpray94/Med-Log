package com.suryaprakash.medlog

import com.suryaprakash.medlog.clinical.Band
import com.suryaprakash.medlog.clinical.DangerRules
import com.suryaprakash.medlog.clinical.Level
import com.suryaprakash.medlog.clinical.Line
import com.suryaprakash.medlog.clinical.LimitsForm
import com.suryaprakash.medlog.clinical.Limits
import com.suryaprakash.medlog.clinical.PersonContext
import com.suryaprakash.medlog.data.CarePlan
import com.suryaprakash.medlog.data.Settings
import com.suryaprakash.medlog.feedback.Category
import com.suryaprakash.medlog.feedback.FeedbackSender
import com.suryaprakash.medlog.feedback.FeedbackStore
import com.suryaprakash.medlog.feedback.Github
import com.suryaprakash.medlog.feedback.Http
import com.suryaprakash.medlog.feedback.HttpResult
import com.suryaprakash.medlog.feedback.Report
import com.suryaprakash.medlog.feedback.ShakeLogic
import com.suryaprakash.medlog.feedback.Status
import com.suryaprakash.medlog.feedback.statusLine
import com.suryaprakash.medlog.nlu.Fact
import com.suryaprakash.medlog.nlu.Reading
import com.suryaprakash.medlog.nlu.Source
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files

/**
 * Persona 2: Ravi, 34, a helper, new to the app, testing with real data.
 * Scenario numbers (r1..r6) follow docs/WORK_PLAN.md "Persona tests".
 */
class PersonaRaviTest {
    private fun report(id: String = "1700000000000-1234", shot: Boolean = true, note: String = "The button is too small") = Report(
        id = id, createdAt = 1_700_000_000_000, category = Category.HARD, note = note, route = "HelperHome",
        version = "3.0.0", role = "helper", device = "Google Pixel", android = "Android 14 (API 34)", hasShot = shot,
    )

    private fun tmp(): File = Files.createTempDirectory("medlog-feedback").toFile().also { it.deleteOnExit() }
    private fun b64(b: ByteArray) = java.util.Base64.getEncoder().encodeToString(b)

    /** A fake GitHub. [failWith] makes every call throw, like a phone with no internet. */
    private class Fake(var isPrivate: Boolean = true, var offline: Boolean = false) : Http {
        val calls = mutableListOf<Triple<String, String, String?>>()
        var issueState = "open"
        override fun call(method: String, url: String, token: String, body: String?): HttpResult {
            if (offline) throw IOException("no internet")
            assertEquals("tok", token)
            val path = url.removePrefix("https://api.github.com/repos/o/r")
            calls += Triple(method, path, body)
            return when {
                method == "GET" && path == "" -> HttpResult(200, """{"private":$isPrivate}""")
                method == "PUT" && path.startsWith("/contents/feedback/") -> HttpResult(201, """{"content":{"html_url":"https://github.com/o/r/blob/main/feedback/a.jpg"}}""")
                method == "POST" && path == "/issues" -> HttpResult(201, """{"number":12}""")
                method == "GET" && path == "/issues/12" -> HttpResult(200, """{"state":"$issueState"}""")
                method == "POST" && (path == "/issues/12/comments" || path == "/issues/12/labels") -> HttpResult(201, "{}")
                method == "PATCH" && path == "/issues/12" -> { issueState = "open"; HttpResult(200, "{}") }
                else -> HttpResult(404, "{}")
            }
        }
    }

    private fun sender(f: Http) = FeedbackSender(f, "o/r", "tok", ::b64)

    // ── R1 ──
    @Test fun r1_shakeOpensReportOnHelperPhoneButNotOnThePersonsPhone() {
        assertTrue("helper phone, setting untouched", Settings(role = "helper").shakeOn)
        assertFalse("person's phone, setting untouched", Settings(role = "self").shakeOn)
        assertFalse("brand new install", Settings().shakeOn)
        // an explicit choice wins on both
        assertFalse(Settings(role = "helper", shakeToReport = false).shakeOn)
        assertTrue(Settings(role = "self", shakeToReport = true).shakeOn)
    }

    @Test fun r1_twoHardJoltsInASecondIsTheShakeAndAWalkingBumpIsNot() {
        assertEquals(listOf(600L), ShakeLogic.detect(listOf(0L to 1f, 100L to 3.2f, 300L to 1f, 600L to 3.0f, 700L to 1f)))
        // a phone dropped on a table (one hard bump) or a slow walk (1.5 g) never triggers
        assertTrue(ShakeLogic.detect(listOf(0L to 1f, 50L to 4.5f, 100L to 1f, 3000L to 1f)).isEmpty())
        assertTrue(ShakeLogic.detect((0..40).map { it * 100L to 1.5f }).isEmpty())
    }

    // ── R2 ──
    @Test fun r2_reportWithNoTokenIsQueuedSurvivesRestartAndIsSentOnceATokenExists() {
        val root = tmp()
        val first = FeedbackStore(root)
        first.save(report(), byteArrayOf(1, 2, 3))
        // "restart": a brand new store object over the same folder
        val second = FeedbackStore(root)
        val queued = second.pending()
        assertEquals(1, queued.size)
        assertEquals(Status.QUEUED, queued[0].status)
        assertEquals(0, queued[0].issue)
        assertEquals(listOf<Byte>(1, 2, 3), second.shot(queued[0].id)!!.toList())
        // now a token exists (the worker builds the sender): the queue drains
        val fake = Fake()
        val sent = sender(fake).send(queued[0]) { second.shot(queued[0].id) }
        second.save(sent)
        assertEquals(Status.SENT, sent.status); assertEquals(12, sent.issue)
        assertTrue(FeedbackStore(root).pending().isEmpty())
        assertEquals(12, FeedbackStore(root).load(sent.id)!!.issue)
    }

    @Test fun r2_noInternetKeepsTheReportQueuedAndASecondSendDoesNotCreateTwoIssues() {
        val root = tmp(); val store = FeedbackStore(root)
        store.save(report(), byteArrayOf(9))
        val off = Fake(offline = true)
        var threw = false
        try { sender(off).send(store.pending()[0]) { store.shot("1700000000000-1234") } } catch (e: IOException) { threw = true }
        assertTrue("the worker relies on IOException to retry", threw)
        assertEquals(1, store.pending().size)
        val on = Fake()
        val s = sender(on)
        val a = s.send(store.pending()[0]) { store.shot("1700000000000-1234") }; store.save(a)
        val b = s.send(a) { store.shot(a.id) }                     // e.g. the worker runs twice
        assertEquals(1, on.calls.count { it.first == "POST" && it.second == "/issues" })
        assertEquals(a.issue, b.issue)
    }

    @Test fun r2_newestReportIsFirstInMyReports() {
        val store = FeedbackStore(tmp())
        store.save(report(id = "a").copy(createdAt = 1_000)); store.save(report(id = "b").copy(createdAt = 3_000)); store.save(report(id = "c").copy(createdAt = 2_000))
        assertEquals(listOf("b", "c", "a"), store.all().map { it.id })
    }

    // ── R3 ──
    @Test fun r3_publicRepoGetsTextOnlyNeverTheScreenshot() {
        val f = Fake(isPrivate = false)
        val r = sender(f).send(report()) { byteArrayOf(1, 2, 3) }
        assertTrue(r.textOnly); assertEquals("", r.imageUrl)
        assertEquals(12, r.issue)
        assertFalse("no picture upload at all", f.calls.any { it.first == "PUT" })
        val posted = f.calls.first { it.first == "POST" && it.second == "/issues" }.third!!
        assertFalse(posted.contains("![screenshot]"))
        assertTrue(JSONObject(posted).getString("body").contains("Screenshot not sent"))
        assertFalse("the picture bytes are never in any request", f.calls.any { it.third?.contains("AQID") == true })
    }

    @Test fun r3_repoThatCannotBeCheckedCountsAsPublic() {
        // wrong token / repo not found -> GET /repos answers 404 -> no picture
        val f = object : Http {
            val puts = mutableListOf<String>()
            override fun call(method: String, url: String, token: String, body: String?): HttpResult = when {
                method == "PUT" -> { puts += url; HttpResult(201, "{}") }
                method == "GET" -> HttpResult(404, "{}")
                method == "POST" && url.endsWith("/issues") -> HttpResult(201, """{"number":5}""")
                else -> HttpResult(404, "{}")
            }
        }
        val r = sender(f).send(report()) { byteArrayOf(7) }
        assertTrue(r.textOnly); assertTrue(f.puts.isEmpty())
    }

    @Test fun r3_privateRepoGetsThePictureLinkInTheIssue() {
        val f = Fake(isPrivate = true)
        val r = sender(f).send(report()) { byteArrayOf(1, 2, 3) }
        assertFalse(r.textOnly)
        assertTrue(Github.body(r).contains("![screenshot](https://github.com/o/r/blob/main/feedback/a.jpg?raw=true)"))
    }

    // ── R4 ──
    @Test fun r4_closedIssueShowsFixedAndStillBrokenReopensWithTheNewNote() {
        val f = Fake(); val s = sender(f)
        var r = s.send(report(shot = false)) { null }
        assertEquals("Sent, the team will look at it", statusLine(r))
        f.issueState = "closed"
        r = s.refresh(r)
        assertEquals(Status.CLOSED, r.status)
        assertEquals("The team says it's fixed", statusLine(r))
        r = s.stillBroken(r, "It is still small on my phone")
        assertEquals(Status.SENT, r.status)
        val patch = f.calls.last { it.first == "PATCH" }
        assertEquals("open", JSONObject(patch.third!!).getString("state"))
        val comment = f.calls.last { it.second == "/issues/12/comments" }.third!!
        assertTrue(comment, JSONObject(comment).getString("body").endsWith("It is still small on my phone"))
        assertTrue(JSONObject(comment).getString("body").startsWith("Still broken on the phone."))
        // GitHub shows it open again, so a refresh keeps it SENT
        assertEquals(Status.SENT, s.refresh(r).status)
        // "It works now" is the only way to VERIFIED, and closing again does not undo it
        f.issueState = "closed"
        r = s.verify(s.refresh(r))
        assertEquals("You checked it works", statusLine(r))
        assertEquals(Status.VERIFIED, s.refresh(r).status)
    }

    @Test fun r4_stillBrokenWithNoWordsStillReopens() {
        val f = Fake(); val s = sender(f)
        f.issueState = "closed"
        val r = s.stillBroken(report().copy(issue = 12, status = Status.CLOSED), "")
        assertEquals(Status.SENT, r.status)
        assertEquals(Github.stillBrokenComment(""), JSONObject(f.calls.last { it.second == "/issues/12/comments" }.third!!).getString("body"))
    }

    @Test fun r4_everyStatusHasPlainWords() {
        assertEquals("Waiting to send", statusLine(report()))
        for (st in Status.entries) assertNotNull(statusLine(report().copy(status = st, issue = 3)))
    }

    // ── R5 (rules part) ──
    @Test fun r5_limitsSavedByTheHelperAreUsedByTheRulesAtOnce() {
        val f = mapOf("spo2" to Fact(1, Source.ASKED)).mapValues { it.value }   // unused, keeps the call shape of the rules
        val readings = listOf(Reading("spo2", 91.0))
        val before = DangerRules.evaluate(null, f, readings, emptyList(), PersonContext(70, false))
        assertEquals(Level.GREEN, before.level); assertEquals("spo2", before.needsLimit)
        // Ravi saves an amber line at 93 in the care plan; a fresh read of the plan gives the rules the new person
        val plan = CarePlan(limits = Limits(mapOf("spo2" to Band(amberLow = 93.0, redLow = 88.0)), doctorConfirmed = true, setBy = "Ravi", setAt = 1))
        val back = CarePlan.parse(plan.toJson())                         // what Repo.person() reads
        assertEquals(plan.limits, back.limits)
        val after = DangerRules.evaluate(null, f, readings, emptyList(), PersonContext(70, false, "", back.limits))
        assertEquals(Level.AMBER, after.level); assertEquals(null, after.needsLimit)
    }

    @Test fun r5_aPlanWithoutLimitsStillLoadsAndAHelperWhoSavesNothingChangesNothing() {
        val old = CarePlan.parse("""{"doctors":[],"symptoms":[],"treatments":[],"risks":[],"emergencies":[]}""")
        assertTrue(old.limits.bands.isEmpty())
        assertEquals(Limits(), CarePlan.parse("garbage").limits)
    }

    // ── R5 (Personal limits: skip at set-up, find again later) ──
    private val noFacts = emptyMap<String, Fact>()
    private fun judge(readings: List<Reading>, person: PersonContext, facts: Map<String, Fact> = noFacts) =
        DangerRules.evaluate(null, facts, readings, emptyList(), person)
    private fun plan(json: String = """{"doctors":[],"symptoms":[],"treatments":[],"risks":[],"emergencies":[]}""") = CarePlan.parse(json)

    /**
     * Pure pieces of "skip at set-up, find it again in Helper controls". UNVERIFIED (code read only, no emulator):
     *  - OnboardingScreen.kt S.LIMITS: the "Later" button (shown while no limits are set) only calls next(), so
     *    it saves nothing and moves on; "Set limits now" calls nav.go(Route.Limits).
     *  - SettingsScreens.kt "helperlock" (Helper controls): the first row "Personal limits" calls nav.go(Route.Limits)
     *    and shows LimitsForm.summary(plan.limits). That the PIN gate in front of "helperlock" opens, and that the
     *    row is tappable, is not proven here.
     *  - FoodReadings.kt NeedsLimitLine: only that the grey text is chosen (needsLimitLine) is proven, not that it is
     *    shown under the reading or that tapping it opens the page.
     */
    @Test fun r5_setupLimitsStepCanBeSkippedAndFoundAgainInHelperControls() {
        val over55 = PersonContext(70, false)
        // 1. Skipped at set-up: "Later" changes nothing, so the plan has no limits. Both the set-up step (isSet false =
        //    "Later" / "Set limits now" buttons) and the Helper controls row read exactly this.
        val skipped = plan()
        assertFalse(LimitsForm.isSet(skipped.limits))
        assertEquals("Not set", LimitsForm.summary(skipped.limits))
        assertEquals(skipped, CarePlan.parse(skipped.toJson()))            // nothing invented by saving the plan again
        // 2. Later she takes readings: the rules do not judge the numbers alone and a quiet grey line names the gap.
        val spo2 = listOf(Reading("spo2", 91.0))
        val bp = listOf(Reading("bp", 150.0, 95.0))
        val r1 = judge(spo2, over55); val r2 = judge(bp, over55)
        assertEquals("spo2", r1.needsLimit); assertEquals("bp", r2.needsLimit)
        assertEquals("Your helper hasn't set oxygen limits yet.", LimitsForm.needsLimitLine(r1.needsLimit))
        assertEquals("Your helper hasn't set blood pressure limits yet.", LimitsForm.needsLimitLine(r2.needsLimit))
        assertEquals(Level.GREEN, r2.level)
        // 3. Ravi opens the page later (what LimitsScreen.save() does): types numbers, validates, saves with the tick.
        val typed = mapOf(
            "spo2" to mapOf(Line.AMBER_LOW to "93", Line.RED_LOW to "88"),
            "bpSys" to mapOf(Line.AMBER_HIGH to "140", Line.RED_HIGH to "170"),
        )
        assertNull(LimitsForm.validateAll(typed))
        val limits = LimitsForm.toLimits(typed, doctorConfirmed = true, setBy = "helper", setAt = 5L)
        val saved = CarePlan.parse(skipped.copy(limits = limits).toJson())  // saveCarePlan then Repo.person() read
        assertEquals(setOf("spo2", "bpSys"), saved.limits.bands.keys)      // empty measures are not stored
        assertTrue(LimitsForm.isSet(saved.limits))
        assertEquals("Set · doctor agreed", LimitsForm.summary(saved.limits))   // the Helper controls row and the set-up step now say "Set"
        // 4. The same readings now use his numbers and the grey lines are gone.
        val p2 = PersonContext(70, false, "", saved.limits)
        val a1 = judge(spo2, p2); val a2 = judge(bp, p2)
        assertEquals(Level.AMBER, a1.level); assertNull(a1.needsLimit); assertNull(LimitsForm.needsLimitLine(a1.needsLimit))
        assertEquals(Level.AMBER, a2.level); assertNull(a2.needsLimit); assertNull(LimitsForm.needsLimitLine(a2.needsLimit))
        assertEquals(Level.RED, judge(listOf(Reading("spo2", 88.0)), p2).level)
        assertEquals(Level.RED, judge(listOf(Reading("bp", 172.0, 90.0)), p2).level)
        // 5. Without his numbers a 70-year-old with the same readings is never called red.
        assertEquals(Level.GREEN, judge(bp, over55).level)
        // 6. Someone who is 40 needs no helper numbers: no grey line, and the general rules apply.
        assertNull(judge(spo2, PersonContext(40, false)).needsLimit)
    }

    /** The limits page's pure parts: what was typed is checked, the fever is one number, suggestions, the words. UNVERIFIED: layout, keyboard, the Save button and nav.back() (LimitsScreen.kt). */
    @Test fun r5_limitsPageWalkThrough() {
        // Every measure Ravi can see is on the page, with the words that match what the rules do.
        assertEquals(listOf("bpSys", "bpDia", "spo2", "sugar", "temp", "pulse", "vomit", "loose", "constipationDays"), LimitsForm.SPECS.map { it.key })
        assertEquals("Amber at or below (%)", LimitsForm.spec("spo2").fieldLabel(Line.AMBER_LOW))
        assertEquals("Amber more than (times)", LimitsForm.spec("vomit").fieldLabel(Line.AMBER_HIGH))
        // Typing mistakes get plain words and nothing is saved.
        val spo2 = LimitsForm.spec("spo2")
        assertEquals("Oxygen (SpO₂): \"abc\" is not a number.", LimitsForm.validate(spo2, mapOf(Line.AMBER_LOW to "abc")))
        assertTrue(LimitsForm.validate(spo2, mapOf(Line.AMBER_LOW to "50"))!!.contains("between 70 and 100"))
        assertTrue(LimitsForm.validate(spo2, mapOf(Line.AMBER_LOW to "90", Line.RED_LOW to "92"))!!.contains("red low number"))
        assertTrue(LimitsForm.validate(LimitsForm.spec("bpSys"), mapOf(Line.AMBER_LOW to "150", Line.AMBER_HIGH to "140"))!!.contains("low numbers must be lower"))
        assertTrue(LimitsForm.validate(LimitsForm.spec("bpSys"), mapOf(Line.AMBER_HIGH to "170", Line.RED_HIGH to "160"))!!.contains("red high number"))
        assertNull(LimitsForm.validate(spo2, emptyMap()))                   // all empty = "use the general numbers"
        assertNotNull(LimitsForm.validateAll(mapOf("spo2" to mapOf(Line.AMBER_LOW to "x"))))
        assertFalse(LimitsForm.isSet(LimitsForm.toLimits(emptyMap(), false, "helper", 1)))   // saving an empty page stores no numbers

        // Fever is one number (B71): both lines set, whatever unit, and the cancer red line 100.0 no longer overrides it.
        val temp = LimitsForm.spec("temp")
        val typedTemp = LimitsForm.typed(temp, LimitsForm.initialTexts(temp, null), Line.AMBER_HIGH, "38.9")
        assertEquals(mapOf(Line.AMBER_HIGH to "38.9", Line.RED_HIGH to "38.9"), typedTemp)
        assertNull(LimitsForm.validate(temp, typedTemp))
        val tempBand = LimitsForm.band("temp", typedTemp)
        assertEquals(Band(amberHigh = 102.0, redHigh = 102.0), tempBand)    // 38.9 C = 102.0 F
        assertEquals("102", LimitsForm.initialTexts(temp, tempBand)[Line.AMBER_HIGH])
        assertTrue(LimitsForm.validate(temp, mapOf(Line.AMBER_HIGH to "105", Line.RED_HIGH to "105"))!!.contains("104"))
        assertTrue(LimitsForm.tempRule(67, true).contains("red at 100 °F")); assertFalse(LimitsForm.tempRule(67, true).contains("amber at"))
        val chemo = PersonContext(67, false, "Cancer", Limits(mapOf("temp" to tempBand), true, "helper", 1), cancerCare = true)
        assertEquals(Level.GREEN, judge(listOf(Reading("temp", 100.2)), chemo).level)
        val over = judge(listOf(Reading("temp", 102.4)), chemo)
        assertEquals(Level.RED, over.level); assertTrue(over.reasons.any { it.contains("above the limit set for you") })
        assertEquals(Level.RED, judge(listOf(Reading("temp", 104.0)), chemo).level)     // 104 is always red
        assertEquals(Level.RED, judge(listOf(Reading("temp", 100.2)), chemo.copy(limits = Limits())).level)   // without his number the general rule

        // "Suggest from readings": needs 3, uses the median, only the lines the measure has.
        assertNull(LimitsForm.suggest("spo2", listOf(96.0, 97.0)))
        assertEquals(Band(amberLow = 91.0, redLow = 86.0), LimitsForm.suggest("spo2", listOf(95.0, 96.0, 97.0, 96.0, 96.0)))
        assertEquals(Band(amberHigh = 100.4, redHigh = 101.9), LimitsForm.suggest("temp", listOf(98.2, 98.6, 98.4)))
        assertEquals(104.0, LimitsForm.suggest("temp", listOf(102.0, 102.0, 102.0))!!.redHigh!!, 0.0)     // red never above 104
        assertEquals(Band(amberLow = 90.0, redLow = 60.0, amberHigh = 180.0, redHigh = 240.0), LimitsForm.suggest("sugar", listOf(120.0, 118.0, 122.0)))
        // a suggestion is only a fill-in: it passes the same check as typed numbers
        val s = LimitsForm.suggest("bpSys", listOf(120.0, 118.0, 122.0))!!
        assertNull(LimitsForm.validate(LimitsForm.spec("bpSys"), LimitsForm.initialTexts(LimitsForm.spec("bpSys"), s)))

        // Hints show the general number the box replaces, in the same words as the rules.
        assertEquals("general: 93", LimitsForm.general("spo2", Line.AMBER_LOW, 40, false))
        assertEquals("general: 89", LimitsForm.general("spo2", Line.AMBER_LOW, 70, false))
        assertEquals("general: none", LimitsForm.general("bpSys", Line.AMBER_HIGH, 70, false))
        assertEquals("general: 4", LimitsForm.general("vomit", Line.AMBER_HIGH, 67, true))

        // Saved limits survive the care plan's JSON, and broken data reads as "not set".
        val limits = LimitsForm.toLimits(mapOf("temp" to typedTemp, "pulse" to mapOf(Line.AMBER_HIGH to "110")), true, "Ravi", 9)
        val back = CarePlan.parse(plan().copy(limits = limits).toJson()).limits
        assertEquals(limits, back)
        assertEquals("Ravi", back.setBy); assertTrue(back.doctorConfirmed)
        assertEquals(Limits(), Limits.fromJson(JSONObject("""{"bands":"oops"}""")))
        assertEquals("Set", LimitsForm.summary(back.copy(doctorConfirmed = false)))
        // Pulse: a helper who sets only the high line keeps the general low line
        val pulse = PersonContext(60, false, "", back)
        assertEquals(Level.AMBER, judge(listOf(Reading("pulse", 115.0)), pulse).level)
        assertEquals(Level.AMBER, judge(listOf(Reading("pulse", 38.0)), pulse).level)
    }
}
