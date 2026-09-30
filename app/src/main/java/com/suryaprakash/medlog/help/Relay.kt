package com.suryaprakash.medlog.help

import android.content.Context
import android.util.Base64
import android.util.Log
import com.suryaprakash.medlog.data.Keys
import com.suryaprakash.medlog.medlog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Reaches helper phones over the internet when they are too far away for Bluetooth (plan 13.2).
 *
 * The two phones never talk to a MedLog server, and there are no accounts. They drop sealed notes into a
 * mailbox on a relay (the open-source ntfy protocol: ntfy.sh, or a relay the family runs) and pick them up.
 * - Every note is sealed with the key the phones agreed when they were paired face to face (AES-256-GCM).
 *   The relay only ever sees scrambled bytes.
 * - The mailbox names are worked out from that key, so both phones know them without asking and nobody
 *   else can guess them.
 * - Only help messages and replies go this way. Notes, medicines and the doctor page never leave the phone.
 *
 * Person → helper uses the [DOWN] mailbox; helper → person (replies, "How are you?") uses [UP].
 */
object Relay {
    const val DEFAULT_URL = "https://ntfy.sh"
    const val DOWN = "down"
    const val UP = "up"
    private const val TAG = "MedLogRelay"

    enum class Link { OFF, CONNECTING, ON, NO_INTERNET }
    /** The helper phone's connection to the relay, shown on the helper's home screen. */
    val link = MutableStateFlow(Link.OFF)

    fun enabled(ctx: Context) = ctx.medlog.settings.value.internetLink
    fun base(ctx: Context) = ctx.medlog.settings.value.relayUrl.trim().trimEnd('/').ifBlank { DEFAULT_URL }

    /** Mailbox name for one direction of one pairing: "medlog-" + 32 hex characters from HMAC-SHA256(key). */
    fun topic(key: ByteArray, dir: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return "medlog-" + mac.doFinal("medlog-relay-$dir".toByteArray()).take(16).joinToString("") { "%02x".format(it) }
    }

    /** One line of the relay's JSON stream. [text] is the sealed note, or [attachment] a link to it when it was too big. */
    data class Incoming(val id: String, val topic: String, val text: String?, val attachment: String?, val time: Long = 0)

    fun parse(line: String): Incoming? = runCatching {
        val o = JSONObject(line)
        if (o.optString("event") != "message") return null
        val att = o.optJSONObject("attachment")?.optString("url")?.takeIf { it.isNotBlank() }
        Incoming(o.getString("id"), o.optString("topic"), if (att == null) o.optString("message") else null, att, o.optLong("time"))
    }.getOrNull()

    private fun connect(url: String, readTimeoutMs: Int) = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 15_000
        readTimeout = readTimeoutMs
        useCaches = false
    }

    /** Seals [body] and leaves it in the mailbox. Tries three times; true once the relay has it. */
    suspend fun post(ctx: Context, key: ByteArray, dir: String, body: JSONObject): Boolean = withContext(Dispatchers.IO) {
        if (!enabled(ctx)) return@withContext false
        val sealed = Base64.encodeToString(Keys.seal(key, body.toString().toByteArray()), Base64.NO_WRAP).toByteArray()
        val url = "${base(ctx)}/${topic(key, dir)}"
        for (attempt in 0 until 3) {
            val ok = runCatching {
                val c = connect(url, 20_000)
                try {
                    c.requestMethod = "POST"
                    c.doOutput = true
                    c.setRequestProperty("Content-Type", "text/plain")
                    c.outputStream.use { it.write(sealed) }
                    c.responseCode in 200..299
                } finally { c.disconnect() }
            }.onFailure { Log.w(TAG, "post", it) }.getOrDefault(false)
            if (ok) return@withContext true
            delay(1_000L shl attempt)
        }
        false
    }

    /** The sealed bytes of [m], fetching the attachment when the relay stored the note as a file. */
    private fun sealedBytes(m: Incoming): ByteArray? = runCatching {
        val b64 = m.attachment?.let { u ->
            val c = connect(u, 20_000)
            try { c.inputStream.bufferedReader().readText() } finally { c.disconnect() }
        } ?: m.text ?: return null
        Base64.decode(b64.trim(), Base64.NO_WRAP)
    }.getOrNull()

    fun open(key: ByteArray, m: Incoming): JSONObject? =
        sealedBytes(m)?.let { runCatching { JSONObject(String(Keys.open(key, it))) }.getOrNull() }

    // ───────────────────── listening (both phones) ─────────────────────

    private var job: Job? = null
    @Volatile private var current: HttpURLConnection? = null
    /** Relay message ids already handled, so a reconnect never delivers a note twice. */
    private val handled = object : LinkedHashMap<String, Boolean>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?) = size > 300
    }

    /**
     * Keeps one connection open to the mailboxes from [topics] (mailbox name → key) and hands each note to
     * [onNote] the moment it arrives. The helper's phone listens to its person's [DOWN] mailbox; the person's
     * phone listens to every helper's [UP] mailbox (replies, "How are you?").
     * Reconnects on its own after a lost signal, and picks up notes left while the phone was offline (the
     * relay keeps them 12 hours).
     */
    @Synchronized
    fun listen(ctx: Context, scope: CoroutineScope, topics: suspend () -> Map<String, ByteArray>, onNote: suspend (topic: String, o: JSONObject) -> Unit) {
        if (job?.isActive == true) { reconnect(); return }
        val app = ctx.medlog
        job = scope.launch(Dispatchers.IO) {
            var backoff = 1_000L
            while (isActive) {
                val map = topics()
                if (!enabled(ctx) || map.isEmpty()) { link.value = Link.OFF; delay(30_000); continue }
                // first start: only notes from now on; afterwards: everything since the last one (a few seconds of overlap, de-duplicated)
                val since = app.settings.getLong("relay_since", System.currentTimeMillis() / 1000).let { if (it > 0) it - 5 else System.currentTimeMillis() / 1000 }
                link.value = Link.CONNECTING
                runCatching {
                    // the relay sends a keep-alive every 45 s, so a silent minute and a half means the line is dead
                    val c = connect("${base(ctx)}/${map.keys.sorted().joinToString(",")}/json?since=$since", 90_000)
                    current = c
                    try {
                        c.inputStream.bufferedReader().use { r ->
                            link.value = Link.ON
                            backoff = 1_000L
                            while (isActive) {
                                val line = r.readLine() ?: break
                                val m = parse(line) ?: continue
                                if (synchronized(handled) { handled.put(m.id, true) } != null) continue
                                val key = map[m.topic] ?: continue
                                open(key, m)?.let { if (!com.suryaprakash.medlog.data.Sync.onNote(ctx, m.topic, it)) onNote(m.topic, it) }
                                if (m.time > 0) app.settings.putLong("relay_since", m.time)
                            }
                        }
                    } finally { c.disconnect(); current = null }
                }.onFailure { if (isActive) { Log.w(TAG, "listen", it); link.value = Link.NO_INTERNET } }
                delay(backoff)
                backoff = (backoff * 2).coerceAtMost(30_000L)
            }
        }
    }

    /** Drops the current connection so the next one picks up changed mailboxes (a new helper paired). */
    fun reconnect() { runCatching { current?.disconnect() } }

    @Synchronized
    fun stop() {
        job?.cancel(); job = null
        runCatching { current?.disconnect() }
        link.value = Link.OFF
    }
}
