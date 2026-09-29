package com.suryaprakash.medlog.feedback

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.suryaprakash.medlog.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** One answer from GitHub. */
data class HttpResult(val code: Int, val body: String) { val ok get() = code in 200..299 }

/** The only door to the internet, so tests can stand in a fake. Throws IOException when there is no connection. */
fun interface Http {
    fun call(method: String, url: String, token: String, body: String?): HttpResult
}

/** Plain HttpURLConnection, like the relay and updater. */
class UrlHttp : Http {
    override fun call(method: String, url: String, token: String, body: String?): HttpResult {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 15_000; c.readTimeout = 30_000; c.useCaches = false
            c.requestMethod = method // Android's HttpURLConnection accepts PATCH
            c.setRequestProperty("Accept", "application/vnd.github+json")
            c.setRequestProperty("Authorization", "Bearer $token")
            c.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            c.setRequestProperty("User-Agent", "MedLog")
            if (body != null) {
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json")
                c.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = c.responseCode
            val text = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
            return HttpResult(code, text)
        } finally { c.disconnect() }
    }
}

/** Everything that is decided without the internet: the words and shapes sent to GitHub. */
object Github {
    const val API = "https://api.github.com"

    fun title(category: Category, note: String): String {
        val n = note.replace(Regex("\\s+"), " ").trim().take(60).trim().ifEmpty { "(picture only)" }
        return "[Feedback] ${category.label}: $n"
    }

    /** Only a repo that says it is private may get screenshots. Unknown counts as not private. */
    fun canSendImage(repoResult: HttpResult): Boolean =
        repoResult.ok && runCatching { JSONObject(repoResult.body).optBoolean("private", false) }.getOrDefault(false)

    /** Open, or closed. A closed one the person has confirmed stays VERIFIED; one that is open again is just SENT. */
    fun statusFor(current: Status, issueState: String): Status = when {
        issueState == "open" -> Status.SENT
        issueState == "closed" -> if (current == Status.VERIFIED) Status.VERIFIED else Status.CLOSED
        else -> current
    }

    fun body(r: Report): String = buildString {
        appendLine(r.note.ifBlank { "_No words, see the picture._" })
        appendLine()
        when {
            r.imageUrl.isNotEmpty() -> appendLine("![screenshot](${r.imageUrl})")
            r.hasShot && r.textOnly -> appendLine("_Screenshot not sent: the tracker is not confirmed private._")
        }
        appendLine()
        appendLine("| Detail | Value |")
        appendLine("|---|---|")
        appendLine("| Page | ${r.route} |")
        appendLine("| App version | ${r.version} |")
        appendLine("| Role | ${r.role} |")
        appendLine("| Device | ${r.device} |")
        appendLine("| Android | ${r.android} |")
        appendLine("| Time | ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US).format(java.util.Date(r.createdAt))} |")
        appendLine("| Report id | ${r.id} |")
    }.trimEnd()

    fun issueJson(r: Report): String = JSONObject().apply {
        put("title", title(r.category, r.note)); put("body", body(r))
        put("labels", JSONArray(listOf("feedback", r.category.slug)))
    }.toString()

    fun uploadJson(id: String, base64: String): String = JSONObject().apply {
        put("message", "Feedback picture $id"); put("content", base64)
    }.toString()

    fun commentJson(text: String) = JSONObject().put("body", text).toString()
    fun labelsJson(vararg labels: String) = JSONObject().put("labels", JSONArray(labels.toList())).toString()
    fun reopenJson() = JSONObject().put("state", "open").toString()

    fun stillBrokenComment(note: String) = "Still broken on the phone." + note.trim().takeIf { it.isNotEmpty() }?.let { "\n\n$it" }.orEmpty()
}

/** Sends reports and follows them. Each call returns the updated report; the caller saves it. */
class FeedbackSender(
    private val http: Http,
    private val repo: String,
    private val token: String,
    private val base64: (ByteArray) -> String,
) {
    private var repoPrivate: Boolean? = null
    private fun url(path: String) = "${Github.API}/repos/$repo$path"

    private fun isPrivate(): Boolean =
        repoPrivate ?: Github.canSendImage(http.call("GET", url(""), token, null)).also { repoPrivate = it }

    /** Uploads the picture if allowed, then makes the issue. Safe to call again after a failure: it carries on where it stopped. */
    fun send(r0: Report, jpeg: () -> ByteArray?): Report {
        var r = r0
        if (r.issue > 0) return r
        if (r.hasShot && !r.textOnly && r.imageUrl.isEmpty()) {
            val bytes = jpeg()
            r = when {
                bytes == null -> r.copy(hasShot = false)
                !isPrivate() -> r.copy(textOnly = true)
                else -> {
                    val res = http.call("PUT", url("/contents/feedback/${r.id}.jpg"), token, Github.uploadJson(r.id, base64(bytes)))
                    when {
                        res.ok -> r.copy(imageUrl = imageLink(res.body, r.id))
                        res.code == 422 -> r.copy(imageUrl = fallbackLink(r.id)) // already there from an earlier try
                        res.code >= 500 || res.code == 429 -> return r.copy(error = "upload ${res.code}")
                        else -> r.copy(textOnly = true, error = "upload ${res.code}")
                    }
                }
            }
        }
        val res = http.call("POST", url("/issues"), token, Github.issueJson(r))
        if (!res.ok) return r.copy(error = "issue ${res.code}")
        val n = runCatching { JSONObject(res.body).getInt("number") }.getOrDefault(0)
        return if (n > 0) r.copy(issue = n, status = Status.SENT, error = "") else r.copy(error = "issue no number")
    }

    private fun fallbackLink(id: String) = "https://github.com/$repo/blob/HEAD/feedback/$id.jpg?raw=true"
    private fun imageLink(body: String, id: String): String =
        runCatching { JSONObject(body).getJSONObject("content").getString("html_url") + "?raw=true" }.getOrDefault(fallbackLink(id))

    /** Asks GitHub whether the issue is still open. */
    fun refresh(r: Report): Report {
        if (r.issue <= 0) return r
        val res = http.call("GET", url("/issues/${r.issue}"), token, null)
        if (!res.ok) return r
        val state = runCatching { JSONObject(res.body).getString("state") }.getOrDefault("")
        return r.copy(status = Github.statusFor(r.status, state))
    }

    /** "It works now": a comment and the label `verified`. */
    fun verify(r: Report): Report {
        if (r.issue <= 0) return r
        val c = http.call("POST", url("/issues/${r.issue}/comments"), token, Github.commentJson("Verified on the phone"))
        if (!c.ok) return r.copy(error = "comment ${c.code}")
        http.call("POST", url("/issues/${r.issue}/labels"), token, Github.labelsJson("verified"))
        return r.copy(status = Status.VERIFIED, error = "")
    }

    /** "Still broken": open it again and say so. */
    fun stillBroken(r: Report, note: String = ""): Report {
        if (r.issue <= 0) return r
        val p = http.call("PATCH", url("/issues/${r.issue}"), token, Github.reopenJson())
        if (!p.ok) return r.copy(error = "reopen ${p.code}")
        http.call("POST", url("/issues/${r.issue}/comments"), token, Github.commentJson(Github.stillBrokenComment(note)))
        return r.copy(status = Status.SENT, error = "")
    }

    companion object {
        val configured get() = BuildConfig.FEEDBACK_TOKEN.isNotBlank() && BuildConfig.FEEDBACK_REPO.isNotBlank()
        fun forApp() = FeedbackSender(UrlHttp(), BuildConfig.FEEDBACK_REPO, BuildConfig.FEEDBACK_TOKEN) {
            android.util.Base64.encodeToString(it, android.util.Base64.NO_WRAP)
        }
    }
}

/** Sends waiting reports whenever there is a connection. */
class FeedbackWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        if (!FeedbackSender.configured) return Result.success()
        val store = FeedbackStore(applicationContext)
        val sender = FeedbackSender.forApp()
        var again = false
        for (r in store.pending()) {
            try {
                val next = sender.send(r) { store.shot(r.id) }
                store.save(next)
                if (next.issue == 0) again = true
            } catch (e: IOException) { again = true }
        }
        return if (again && runAttemptCount < 6) Result.retry() else Result.success()
    }

    companion object {
        private const val NAME = "feedback-send"

        fun enqueue(ctx: Context) {
            val req = OneTimeWorkRequestBuilder<FeedbackWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, java.util.concurrent.TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(ctx).enqueueUniqueWork(NAME, ExistingWorkPolicy.REPLACE, req)
        }

        /** At app start: send whatever is still waiting. */
        fun enqueueIfPending(ctx: Context) {
            if (FeedbackSender.configured && FeedbackStore(ctx).pending().isNotEmpty()) enqueue(ctx)
        }
    }
}
