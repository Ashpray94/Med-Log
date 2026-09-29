package com.suryaprakash.medlog

import com.suryaprakash.medlog.feedback.Category
import com.suryaprakash.medlog.feedback.FeedbackSender
import com.suryaprakash.medlog.feedback.Github
import com.suryaprakash.medlog.feedback.Http
import com.suryaprakash.medlog.feedback.HttpResult
import com.suryaprakash.medlog.feedback.Report
import com.suryaprakash.medlog.feedback.ReportJson
import com.suryaprakash.medlog.feedback.ShakeLogic
import com.suryaprakash.medlog.feedback.Status
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedbackTest {
    private fun report(note: String = "The button is too small", shot: Boolean = true) = Report(
        id = "1700000000000-1234", createdAt = 1_700_000_000_000, category = Category.HARD, note = note, route = "HelperHome",
        version = "2.9.0", role = "helper", device = "Google Pixel", android = "Android 14 (API 34)", hasShot = shot,
    )

    // ── meta.json ──
    @Test fun metaRoundTrip() {
        val r = report().copy(status = Status.CLOSED, issue = 12, textOnly = true, imageUrl = "https://x/y.jpg?raw=true", error = "upload 500")
        assertEquals(r, ReportJson.fromJson(ReportJson.toJson(r)))
    }

    @Test fun metaBadJsonIsNull() {
        assertNull(ReportJson.fromJson("not json"))
        assertNotNull(ReportJson.fromJson("""{"id":"a"}"""))
    }

    // ── words sent to GitHub ──
    @Test fun titleUsesCategoryAndFirst60Chars() {
        val t = Github.title(Category.WRONG, "x".repeat(100))
        assertEquals("[Feedback] Wrong information: " + "x".repeat(60), t)
        assertEquals("[Feedback] Idea: (picture only)", Github.title(Category.IDEA, "  "))
        assertEquals("[Feedback] Bug: two lines", Github.title(Category.BUG, "two\nlines"))
    }

    @Test fun issueBodyHasNoteImageAndDetails() {
        val b = Github.body(report().copy(imageUrl = "https://img"))
        assertTrue(b.startsWith("The button is too small"))
        assertTrue(b.contains("![screenshot](https://img)"))
        for (d in listOf("HelperHome", "2.9.0", "helper", "Google Pixel", "Android 14 (API 34)")) assertTrue(d, b.contains(d))
        val textOnly = Github.body(report().copy(textOnly = true))
        assertFalse(textOnly.contains("!["))
        assertTrue(textOnly.contains("Screenshot not sent"))
    }

    @Test fun issueJsonLabels() {
        val o = JSONObject(Github.issueJson(report()))
        assertEquals(listOf("feedback", "hard-to-use"), (0 until o.getJSONArray("labels").length()).map { o.getJSONArray("labels").getString(it) })
        assertTrue(o.getString("title").startsWith("[Feedback] Hard to use:"))
    }

    // ── status ──
    @Test fun statusMapping() {
        assertEquals(Status.SENT, Github.statusFor(Status.SENT, "open"))
        assertEquals(Status.CLOSED, Github.statusFor(Status.SENT, "closed"))
        assertEquals(Status.VERIFIED, Github.statusFor(Status.VERIFIED, "closed"))
        assertEquals(Status.SENT, Github.statusFor(Status.VERIFIED, "open"))
        assertEquals(Status.QUEUED, Github.statusFor(Status.QUEUED, ""))
    }

    // ── privacy ──
    @Test fun onlyPrivateRepoGetsPictures() {
        assertTrue(Github.canSendImage(HttpResult(200, """{"private":true}""")))
        assertFalse(Github.canSendImage(HttpResult(200, """{"private":false}""")))
        assertFalse(Github.canSendImage(HttpResult(200, "{}")))
        assertFalse(Github.canSendImage(HttpResult(404, """{"private":true}""")))
        assertFalse(Github.canSendImage(HttpResult(200, "garbage")))
    }

    // ── the whole sending story with a fake GitHub ──
    private class Fake(var isPrivate: Boolean = true) : Http {
        val calls = mutableListOf<Triple<String, String, String?>>()
        var issueState = "open"
        override fun call(method: String, url: String, token: String, body: String?): HttpResult {
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

    private fun sender(f: Fake) = FeedbackSender(f, "o/r", "tok") { java.util.Base64.getEncoder().encodeToString(it) }

    @Test fun queuedToSentToClosedToStillBrokenReopens() {
        val f = Fake(); val s = sender(f)
        var r = report()
        assertEquals(Status.QUEUED, r.status)

        r = s.send(r) { byteArrayOf(1, 2, 3) }
        assertEquals(Status.SENT, r.status); assertEquals(12, r.issue)
        assertTrue(r.imageUrl.endsWith("feedback/a.jpg?raw=true"))
        assertEquals(listOf("GET", "PUT", "POST"), f.calls.map { it.first })
        assertEquals("AQID", JSONObject(f.calls[1].third!!).getString("content")) // base64 of 1,2,3

        f.issueState = "closed"
        r = s.refresh(r); assertEquals(Status.CLOSED, r.status)

        r = s.stillBroken(r, "It is still small")
        assertEquals(Status.SENT, r.status)
        assertTrue(f.calls.any { it.first == "PATCH" && JSONObject(it.third!!).getString("state") == "open" })
        assertTrue(f.calls.last().third!!.contains("It is still small"))

        f.issueState = "closed"
        r = s.refresh(r); assertEquals(Status.CLOSED, r.status)
        r = s.verify(r); assertEquals(Status.VERIFIED, r.status)
        assertTrue(f.calls.any { it.second == "/issues/12/labels" && it.third!!.contains("verified") })
        assertEquals(Status.VERIFIED, s.refresh(r).status)
    }

    @Test fun publicRepoGetsTextOnly() {
        val f = Fake(isPrivate = false)
        val r = sender(f).send(report()) { byteArrayOf(9) }
        assertTrue(r.textOnly); assertEquals("", r.imageUrl); assertEquals(12, r.issue)
        assertFalse(f.calls.any { it.first == "PUT" })
    }

    @Test fun failedIssueStaysQueuedAndKeepsImage() {
        val f = object : Http {
            val inner = Fake()
            override fun call(method: String, url: String, token: String, body: String?) =
                if (method == "POST" && url.endsWith("/issues")) HttpResult(500, "") else inner.call(method, url, token, body)
        }
        val r = sender(Fake()).let { FeedbackSender(f, "o/r", "tok") { java.util.Base64.getEncoder().encodeToString(it) } }.send(report()) { byteArrayOf(1) }
        assertEquals(Status.QUEUED, r.status); assertEquals(0, r.issue)
        assertTrue(r.imageUrl.isNotEmpty()) // next try will not upload again
    }

    // ── shake ──
    private fun samples(vararg p: Pair<Long, Float>) = p.toList()

    @Test fun twoPeaksWithinASecondIsAShake() {
        assertEquals(listOf(600L), ShakeLogic.detect(samples(0L to 1f, 100L to 3.2f, 300L to 1f, 600L to 3.0f, 700L to 1f)))
    }

    @Test fun singlePeakOrSlowPeaksAreNot() {
        assertTrue(ShakeLogic.detect(samples(0L to 3.5f, 100L to 1f)).isEmpty())
        assertTrue(ShakeLogic.detect(samples(0L to 3.5f, 1500L to 3.5f)).isEmpty())
        assertTrue(ShakeLogic.detect(samples(0L to 2.5f, 400L to 2.6f, 800L to 2.7f)).isEmpty()) // at or under 2.7 g
    }

    @Test fun oneJoltSeenTwiceIsNotTwoPeaks() {
        assertTrue(ShakeLogic.detect(samples(0L to 3.5f, 20L to 3.6f, 40L to 3.1f)).isEmpty())
    }

    @Test fun cooldownIsThreeSeconds() {
        val s = samples(0L to 3f, 200L to 3f, 1000L to 3f, 1200L to 3f, 3300L to 3f, 3500L to 3f)
        // shake at 200; 1200 is inside the cooldown; the pair at 3300/3500 is after it
        assertEquals(listOf(200L, 3500L), ShakeLogic.detect(s))
    }
}
