package com.suryaprakash.medlog

import com.suryaprakash.medlog.clinical.Band
import com.suryaprakash.medlog.clinical.DangerRules
import com.suryaprakash.medlog.clinical.Level
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
import org.junit.Assert.assertTrue
import org.junit.Ignore
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

    @Ignore("PENDING W3: setup's 'Personal limits (for the helper)' step can be skipped, and the same page is found again in Helper controls (PIN) and from the 'no limit set' line. The page is not on this branch yet.")
    @Test fun r5_setupLimitsStepCanBeSkippedAndFoundAgainInHelperControls() {}

    @Ignore("PENDING W3: walk-through of the Personal limits page (see docs/PERSONA_TEST_REPORT.md, Ravi, item 5).")
    @Test fun r5_limitsPageWalkThrough() {}
}
