package com.suryaprakash.medlog.help

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Base64
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import com.suryaprakash.medlog.MedLogApp
import com.suryaprakash.medlog.R
import com.suryaprakash.medlog.data.CaredFor
import com.suryaprakash.medlog.data.Helper
import com.suryaprakash.medlog.data.People
import com.suryaprakash.medlog.data.InboxItem
import com.suryaprakash.medlog.data.Keys
import com.suryaprakash.medlog.medlog
import com.suryaprakash.medlog.ui.Perms
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import com.google.android.gms.nearby.Nearby as GmsNearby

/**
 * Phone-to-phone help messages (plan 13.2). Phones in range are reached with no internet, using Google
 * Nearby Connections (Bluetooth, BLE and Wi-Fi Direct); phones anywhere else through [Relay]. Every message
 * is encrypted with a key the two phones agreed when they were paired face to face.
 *
 * Helper phones advertise "H|<pairId>". The person's phone looks for them only when it has something to send.
 */
object Nearby {
    private const val SERVICE = "com.suryaprakash.medlog.help"
    private const val TAG = "MedLogNearby"
    private val STRATEGY = Strategy.P2P_CLUSTER

    data class Ack(val name: String, val reply: String, val at: Long = System.currentTimeMillis())
    /** Replies from helpers ("Ravi: I'm coming"), shown on the Help screen. */
    val acks = MutableStateFlow<List<Ack>>(emptyList())
    /** Helpers whose phones got the last message. */
    val reached = MutableStateFlow<Set<String>>(emptySet())
    val sending = MutableStateFlow(false)
    /** The message the Help screen is following; receipts for older ones don't count as "got it". */
    @Volatile private var currentMid = ""

    fun client(ctx: Context): ConnectionsClient = GmsNearby.getConnectionsClient(ctx.applicationContext)
    fun allowed(ctx: Context) = Perms.has(ctx, *Perms.NEARBY)

    private fun key(h: Helper) = Base64.decode(h.pairKey, Base64.NO_WRAP)
    private val FRESH_MS = 30 * 60_000L

    // ───────────────────── the person's phone: send ─────────────────────

    /**
     * Sends [text] (and optional voice) to every paired helper phone at once: over the internet ([Relay]) to
     * phones anywhere, and over Bluetooth / Wi-Fi Direct to phones in range. Receipts and replies come back
     * through the always-on listener ([NearbyService]) within a second or two, and land in [reached] and [acks].
     */
    /** [only]: when given, just those helpers' phones (e.g. AMBER goes only to the helpers chosen for it). */
    fun broadcast(ctx: Context, kind: String, text: String, audio: File? = null, only: ((Helper) -> Boolean)? = null) {
        val app = ctx.medlog
        if (app.settings.value.role != "self") return
        app.scope.launch {
            val helpers = app.db.helpers().all().filter { it.pairId != null && it.pairKey != null && (only == null || only(it)) }
            if (helpers.isEmpty()) return@launch
            val me = app.repo.profile().name.ifBlank { "MedLog" }
            val mid = Keys.randomB64(9)
            currentMid = mid
            val body = JSONObject().put("kind", kind).put("text", text).put("from", me).put("at", System.currentTimeMillis()).put("mid", mid)
            audio?.takeIf { it.exists() && it.length() < 20_000 }?.let { body.put("audio", Base64.encodeToString(it.readBytes(), Base64.NO_WRAP)) }
            sending.value = true
            reached.value = emptySet()
            heard.clear()
            startListening(ctx)
            val jobs = mutableListOf<kotlinx.coroutines.Job>()
            if (Relay.enabled(ctx)) for (h in helpers) jobs += launch {
                // a voice note can be too big for the relay; the words alone must still get through
                if (!Relay.post(ctx, key(h), Relay.DOWN, body) && body.has("audio"))
                    Relay.post(ctx, key(h), Relay.DOWN, JSONObject(body.toString()).apply { remove("audio") })
            }
            if (allowed(ctx)) jobs += launch { nearbySend(ctx, helpers, body) }
            jobs.forEach { it.join() }
            sending.value = false
        }
    }

    /** Replies already shown, so one sent both ways is counted once. */
    private val heard = java.util.Collections.synchronizedSet(HashSet<String>())

    private fun onReply(ctx: Context, h: Helper, o: JSONObject) {
        val reply = o.optString("reply")
        val re = o.optString("re")
        if (reply.isBlank() || !heard.add("${h.id}|$reply|$re")) return
        // "got" is sent by the helper's phone itself as soon as the alert is on its screen
        if (reply == "got") { if (re == currentMid) reached.value = reached.value + h.name; return }
        if (re == currentMid) reached.value = reached.value + h.name
        // "ask" says who was asked ("Ravi asked Meena to go")
        val shown = if (reply == "ask" && o.optString("to").isNotBlank()) "ask:${o.optString("to")}" else reply
        val line = if (shown.startsWith("ask:")) "${h.name} ${replyWords(shown)}" else "${h.name}: ${replyWords(shown)}"
        acks.value = acks.value + Ack(h.name, shown)
        ctx.medlog.speaker.say(line)
        ctx.medlog.scope.launch { ctx.medlog.repo.addEvent(com.suryaprakash.medlog.data.Kind.MESSAGE, if (shown.startsWith("ask:")) line else "${h.name} replied: ${replyWords(shown)}") }
    }

    /** One helper asked another to go: the other helper's phone rings with the message, and it's noted here. */
    private fun handoff(ctx: Context, from: Helper, o: JSONObject) {
        val app = ctx.medlog
        app.scope.launch {
            val to = app.db.helpers().all().firstOrNull { it.pairId == o.optString("handoff") && it.pairKey != null } ?: return@launch
            val me = app.repo.profile().name.ifBlank { "MedLog" }
            val text = "${from.name} asked you to go: ${o.optString("text")}"
            Relay.post(ctx, key(to), Relay.DOWN, JSONObject().put("kind", "HANDOFF").put("text", text).put("from", me)
                .put("at", System.currentTimeMillis()).put("mid", Keys.randomB64(9)))
            app.repo.addEvent(com.suryaprakash.medlog.data.Kind.MESSAGE, "${from.name} asked ${to.name} to go")
        }
    }

    /** Helper's phone: pass the latest message to another helper, through the person's phone. */
    fun askOther(ctx: Context, pairId: String, toPairId: String, text: String) {
        val p = person(ctx, pairId) ?: return
        val me = ctx.medlog.settings.getString("my_name") ?: "A helper"
        ctx.medlog.scope.launch { Relay.post(ctx, p.keyBytes, Relay.UP, JSONObject().put("handoff", toPairId).put("text", text).put("from", me).put("at", System.currentTimeMillis())) }
    }

    /** Everything a helper's phone sends to the person's phone: receipts, replies and "How are you?". */
    internal fun fromHelper(ctx: Context, h: Helper, o: JSONObject) {
        when {
            o.optString("handoff").isNotEmpty() -> handoff(ctx, h, o)
            o.has("reply") -> onReply(ctx, h, o)
            o.optString("ask").isNotEmpty() -> askedByHelper(ctx, h, o)
        }
    }

    private suspend fun nearbySend(ctx: Context, helpers: List<Helper>, body: JSONObject) = nearbySendEach(ctx, helpers.associateWith { body })

    /**
     * Bluetooth / Wi-Fi Direct to helper phones in range, a different note for each ([bodies]); [onSent] for each
     * phone that took its note. Looks for 20 seconds, then keeps the line open 40 seconds for replies.
     */
    suspend fun nearbySendEach(ctx: Context, bodies: Map<Helper, JSONObject>, onSent: (Helper) -> Unit = {}) {
        val helpers = bodies.keys.toList()
        val c = client(ctx)
        val connected = HashMap<String, Helper>()
        val lifecycle = object : ConnectionLifecycleCallback() {
            override fun onConnectionInitiated(id: String, info: ConnectionInfo) {
                c.acceptConnection(id, object : PayloadCallback() {
                    override fun onPayloadReceived(eid: String, p: Payload) {
                        val h = connected[eid] ?: return
                        val bytes = p.asBytes() ?: return
                        runCatching { fromHelper(ctx, h, JSONObject(String(Keys.open(key(h), bytes)))) }
                    }
                    override fun onPayloadTransferUpdate(eid: String, u: PayloadTransferUpdate) {}
                })
            }
            override fun onConnectionResult(id: String, r: ConnectionResolution) {
                val h = connected[id] ?: return
                if (r.status.isSuccess) {
                    val body = bodies[h] ?: return
                    c.sendPayload(id, Payload.fromBytes(Keys.seal(key(h), body.toString().toByteArray())))
                    if (body.has("sync")) onSent(h) else reached.value = reached.value + h.name
                }
            }
            override fun onDisconnected(id: String) {}
        }
        c.startDiscovery(SERVICE, object : EndpointDiscoveryCallback() {
            override fun onEndpointFound(id: String, info: DiscoveredEndpointInfo) {
                val ids = info.endpointName.removePrefix("H|").split(",")
                val h = helpers.firstOrNull { it.pairId in ids } ?: return
                val pairId = h.pairId!!
                connected[id] = h
                c.requestConnection("U|$pairId", id, lifecycle).addOnFailureListener { Log.w(TAG, "connect", it) }
            }
            override fun onEndpointLost(id: String) {}
        }, DiscoveryOptions.Builder().setStrategy(STRATEGY).build()).addOnFailureListener { Log.w(TAG, "discovery", it) }
        delay(20_000)
        c.stopDiscovery()
        delay(40_000)          // keep connections open for replies
        c.stopAllEndpoints()
    }

    fun replyWords(r: String) = AlertReplies.words(r)

    // ───────────────────── "How are you?" from a helper ─────────────────────

    /** Answers the person can give; the same words go back to the helper. */
    val ANSWERS = listOf("good" to "I'm good", "ok" to "I'm OK", "notwell" to "Not so well", "call" to "Please call me")

    private val askedSeen = object : LinkedHashMap<String, Boolean>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?) = size > 100
    }

    private fun askedByHelper(ctx: Context, h: Helper, o: JSONObject) {
        val mid = o.optString("mid")
        if (mid.isNotEmpty()) synchronized(askedSeen) { if (askedSeen.put(mid, true) != null) return }
        val app = ctx.medlog
        // tell the helper's phone it arrived
        app.scope.launch { Relay.post(ctx, key(h), Relay.DOWN, JSONObject().put("reply", "got").put("re", mid).put("at", System.currentTimeMillis())) }
        if (System.currentTimeMillis() - o.optLong("at", System.currentTimeMillis()) > FRESH_MS) return
        val open = Intent(ctx, AlertActivity::class.java).putExtra(AlertActivity.MODE, AlertActivity.ASKED)
            .putExtra("helperId", h.id).putExtra("from", h.name).putExtra("re", mid).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pi = PendingIntent.getActivity(ctx, 7000 + h.id.toInt(), open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(ctx, MedLogApp.CH_ALERT).setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(com.suryaprakash.medlog.ui.tr("${h.name} asks: How are you?")).setContentText(com.suryaprakash.medlog.ui.tr("Tap to answer"))
            .setPriority(NotificationCompat.PRIORITY_HIGH).setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setFullScreenIntent(pi, true).setContentIntent(pi).setAutoCancel(true).build()
        runCatching { androidx.core.app.NotificationManagerCompat.from(ctx).notify(7000 + h.id.toInt(), n) }
        runCatching { ctx.startActivity(open) }
    }

    /** The person's answer, back to the helper who asked. */
    fun answer(ctx: Context, helperId: Long, re: String, answerKey: String) {
        val app = ctx.medlog
        val words = ANSWERS.firstOrNull { it.first == answerKey }?.second ?: answerKey
        app.scope.launch {
            val h = app.db.helpers().all().firstOrNull { it.id == helperId && it.pairKey != null } ?: return@launch
            val me = app.repo.profile().name.ifBlank { "MedLog" }
            val body = JSONObject().put("kind", "ANSWER").put("answer", answerKey).put("text", words).put("from", me)
                .put("at", System.currentTimeMillis()).put("mid", Keys.randomB64(9)).put("re", re)
            Relay.post(ctx, key(h), Relay.DOWN, body)
            app.repo.addEvent(com.suryaprakash.medlog.data.Kind.CHECKIN, "Told ${h.name}: $words")
        }
    }

    // ───────────────────── helper's phone ─────────────────────

    private var replyTo: Pair<String, ByteArray>? = null
    /** The last message received (and whose), so a reply says which one it answers and goes to the right person. */
    @Volatile private var lastMid: String = ""
    @Volatile private var lastText: String = ""
    @Volatile private var lastPerson: String = ""

    /** The person a message or reply belongs to: the one named, else the one who wrote last, else the first. */
    private fun person(ctx: Context, pairId: String? = null) =
        People.all(ctx).let { all -> all.firstOrNull { it.pairId == (pairId ?: lastPerson) } ?: all.firstOrNull() }

    /** From the helper's alert screen: "I'm coming". Goes back both ways; the person's phone counts it once. */
    fun reply(ctx: Context, r: String, re: String = lastMid, pairId: String? = null, to: String? = null) {
        val msg = JSONObject().put("reply", r).put("re", re).put("at", System.currentTimeMillis())
        // "ask": whom the helper asked to go
        if (!to.isNullOrBlank()) msg.put("to", to)
        // remembered, so the helper's page can say what was answered ("Can't come now"), not just that it was
        ctx.medlog.settings.putString("my_last_reply", "$r|${System.currentTimeMillis()}")
        replyTo?.let { (id, key) -> runCatching { client(ctx).sendPayload(id, Payload.fromBytes(Keys.seal(key, msg.toString().toByteArray()))) } }
        val p = person(ctx, pairId) ?: return
        ctx.medlog.scope.launch { Relay.post(ctx, p.keyBytes, Relay.UP, msg) }
        // the other helpers see who answered, so nobody is left wondering who went
        if (r != "got") FamilyChat.announceReply(ctx, if (r == "ask" && !to.isNullOrBlank()) "ask:$to" else r, lastText, p, re)
    }

    /** The helper's "How are you?" and what came of it, shown on the helper's home screen. */
    data class AskState(val mid: String, val at: Long, val sent: Boolean? = null, val got: Boolean = false, val answer: String? = null, val answerAt: Long = 0, val pairId: String = "")
    val asking = MutableStateFlow<AskState?>(null)

    /** Helper's phone: ask the person "How are you?". Their phone shows big answer buttons. */
    fun ask(ctx: Context, pairId: String? = null) {
        val p = person(ctx, pairId) ?: return
        val key = p.keyBytes
        val mid = Keys.randomB64(9)
        asking.value = AskState(mid, System.currentTimeMillis(), pairId = p.pairId)
        ctx.medlog.scope.launch {
            val ok = Relay.post(ctx, key, Relay.UP, JSONObject().put("ask", "how").put("mid", mid).put("at", System.currentTimeMillis()))
            asking.value = asking.value?.takeIf { it.mid == mid }?.copy(sent = ok) ?: asking.value
        }
    }

    /** Messages already alerted, so one that arrives by Bluetooth and by internet rings once. */
    private val seen = object : LinkedHashMap<String, Boolean>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?) = size > 200
    }

    /**
     * Keeps the phone reachable (foreground service, small notification):
     * a helper's phone listens for its person, nearby and far away; the person's phone listens for replies
     * and "How are you?" from paired helpers (internet only).
     */
    fun startListening(ctx: Context) {
        val app = ctx.medlog
        val st = app.settings.value
        val i = Intent(ctx, NearbyService::class.java)
        fun start() = runCatching { if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i) }
        when {
            People.any(ctx) -> start()
            st.onboarded && st.internetLink -> app.scope.launch {
                if (app.db.helpers().all().any { it.pairKey != null }) { start(); FamilyChat.shareKey(ctx) } else stopListening(ctx)
            }
            else -> stopListening(ctx)
        }
    }

    fun stopListening(ctx: Context) = ctx.stopService(Intent(ctx, NearbyService::class.java))

    /** Mailboxes this phone listens to: its person's (helper) or every paired helper's reply mailbox (person). */
    internal suspend fun listenTopics(ctx: Context): Map<String, ByteArray> {
        val app = ctx.medlog
        return buildMap {
            for (p in People.all(ctx)) {
                put(Relay.topic(p.keyBytes, Relay.DOWN), p.keyBytes)
                p.familyBytes?.let { put(Relay.topic(it, "family"), it) }
            }
            if (app.settings.value.role != "helper" && app.settings.value.onboarded)
                app.db.helpers().all().forEach { h -> h.pairKey?.let { key(h) }?.let { put(Relay.topic(it, Relay.UP), it) } }
        }
    }

    internal suspend fun onRelayNote(ctx: Context, topic: String, o: JSONObject) {
        val app = ctx.medlog
        if (o.has("sync") || o.has("syncAsk")) {
            val peer = People.all(ctx).firstOrNull { topic == Relay.topic(it.keyBytes, Relay.DOWN) }?.pairId
                ?: app.db.helpers().all().firstOrNull { it.pairKey != null && Relay.topic(key(it), Relay.UP) == topic }?.pairId ?: return
            com.suryaprakash.medlog.data.Sync.received(ctx, peer, o)
            return
        }
        for (p in People.all(ctx)) {
            if (topic == Relay.topic(p.keyBytes, Relay.DOWN)) { received(ctx, o, viaNearby = false, pairId = p.pairId); return }
            if (p.familyBytes?.let { Relay.topic(it, "family") } == topic) { FamilyChat.received(ctx, o); return }
        }
        val h = app.db.helpers().all().firstOrNull { it.pairKey != null && Relay.topic(key(it), Relay.UP) == topic } ?: return
        fromHelper(ctx, h, o)
    }

    internal fun advertise(ctx: Context) {
        val people = People.all(ctx)
        if (people.isEmpty()) return
        val c = client(ctx)
        c.stopAdvertising()
        c.startAdvertising("H|" + people.joinToString(",") { it.pairId }, SERVICE, object : ConnectionLifecycleCallback() {
            override fun onConnectionInitiated(id: String, info: ConnectionInfo) {
                val who = people.firstOrNull { info.endpointName == "U|${it.pairId}" } ?: run { c.rejectConnection(id); return }
                val key = who.keyBytes
                c.acceptConnection(id, object : PayloadCallback() {
                    override fun onPayloadReceived(eid: String, p: Payload) {
                        val bytes = p.asBytes() ?: return
                        val o = runCatching { JSONObject(String(Keys.open(key, bytes))) }.getOrNull() ?: return
                        replyTo = eid to key
                        received(ctx, o, viaNearby = true, pairId = who.pairId)
                    }
                    override fun onPayloadTransferUpdate(eid: String, u: PayloadTransferUpdate) {}
                })
            }
            override fun onConnectionResult(id: String, r: ConnectionResolution) {}
            override fun onDisconnected(id: String) {}
        }, AdvertisingOptions.Builder().setStrategy(STRATEGY).build()).addOnFailureListener { Log.w(TAG, "advertise", it) }
    }

    /** Helper's phone: something arrived from the person's phone. */
    internal fun received(ctx: Context, o: JSONObject, viaNearby: Boolean, pairId: String = "") {
        val app = ctx.medlog
        if ((o.has("sync") || o.has("syncAsk")) && pairId.isNotEmpty()) { app.scope.launch { com.suryaprakash.medlog.data.Sync.received(ctx, pairId, o) }; return }
        if (o.optString("kind") == "FAMILY_KEY") { FamilyChat.gotKey(ctx, o, pairId); return }
        // a receipt for the helper's own "How are you?"
        if (o.has("reply")) {
            val re = o.optString("re")
            asking.value?.takeIf { it.mid == re }?.let { asking.value = it.copy(got = true, sent = true) }
            return
        }
        val mid = o.optString("mid")
        if (mid.isNotEmpty()) synchronized(seen) { if (seen.put(mid, true) != null) return }
        val kind = o.optString("kind")
        val sentAt = o.optLong("at", System.currentTimeMillis())
        val fresh = System.currentTimeMillis() - sentAt < FRESH_MS
        if (kind == "ANSWER") {
            asking.value?.takeIf { it.mid == o.optString("re") }?.let { asking.value = it.copy(got = true, sent = true, answer = o.optString("text"), answerAt = sentAt) }
            app.scope.launch {
                val id = app.db.inbox().insert(InboxItem(fromName = o.optString("from"), text = o.optString("text"), kind = kind, at = sentAt, acked = true))
                val worried = o.optString("answer") in setOf("notwell", "call")
                val pi = PendingIntent.getActivity(ctx, id.toInt(), Intent(ctx, com.suryaprakash.medlog.MainActivity::class.java).setData(android.net.Uri.parse("medlog://helper")), PendingIntent.FLAG_IMMUTABLE)
                val n = NotificationCompat.Builder(ctx, MedLogApp.CH_ALERT).setSmallIcon(R.drawable.ic_stat)
                    .setContentTitle(com.suryaprakash.medlog.ui.tr(Wording.answer(o.optString("from"), o.optString("text"))))
                    .setContentText(com.suryaprakash.medlog.ui.tr(if (worried) "You may want to call them." else "Answer to your \"How are you?\""))
                    .setPriority(if (worried) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
                    .setContentIntent(pi).setAutoCancel(true).build()
                runCatching { androidx.core.app.NotificationManagerCompat.from(ctx).notify(Loud.alertId(id), n) }
            }
            return
        }
        if (mid.isNotEmpty()) lastMid = mid
        lastText = o.optString("text")
        if (pairId.isNotEmpty()) lastPerson = pairId
        if (!viaNearby) replyTo = null
        reply(ctx, "got", mid, pairId.ifEmpty { null })
        app.scope.launch {
            val audio = o.optString("audio").takeIf { it.isNotEmpty() }?.let { b ->
                File(ctx.filesDir, "audio").apply { mkdirs() }.let { File(it, "msg_${System.currentTimeMillis()}.amr") }.also { it.writeBytes(Base64.decode(b, Base64.NO_WRAP)) }
            }
            val id = app.db.inbox().insert(InboxItem(fromName = o.optString("from"), text = o.optString("text"), kind = kind, at = sentAt, audioPath = audio?.absolutePath))
            if (!fresh) {
                // old news: a normal notification, not an alarm in the middle of the night
                val sentWords = java.text.SimpleDateFormat("h:mm a, d MMM", java.util.Locale.getDefault()).format(java.util.Date(sentAt))
                val pi = PendingIntent.getActivity(ctx, id.toInt(), Intent(ctx, com.suryaprakash.medlog.MainActivity::class.java).setData(android.net.Uri.parse("medlog://helper")), PendingIntent.FLAG_IMMUTABLE)
                val n = NotificationCompat.Builder(ctx, MedLogApp.CH_ALERT).setSmallIcon(R.drawable.ic_stat)
                    .setContentTitle(com.suryaprakash.medlog.ui.tr("${o.optString("from")}: ${o.optString("text")}")).setContentText(com.suryaprakash.medlog.ui.tr("Sent earlier (at $sentWords). A call can check they're OK."))
                    .setContentIntent(pi).setAutoCancel(true).build()
                runCatching { androidx.core.app.NotificationManagerCompat.from(ctx).notify(Loud.alertId(id), n) }
                return@launch
            }
            Loud.rang(ctx, mid, id)
            audio?.let { runCatching { android.media.MediaPlayer().apply { setDataSource(it.absolutePath); prepare(); start() } } }
            ring(ctx, id, mid, pairId, o.optString("from"), o.optString("text"), kind)
        }
    }

    /** The helper's alarm: sound, the full-screen alert and a notification with the three answers for this kind of alert. */
    internal fun ring(ctx: Context, id: Long, mid: String, pairId: String, from: String, text: String, kind: String) {
        val urgent = AlertReplies.urgent(kind)
        AlertSound.start(ctx, urgent = urgent)
        val open = Intent(ctx, AlertActivity::class.java).putExtra(AlertActivity.MODE, AlertActivity.HELPER)
            .putExtra("from", from).putExtra("text", text).putExtra("kind", kind).putExtra("id", id)
            .putExtra("mid", mid).putExtra("pairId", pairId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pi = PendingIntent.getActivity(ctx, id.toInt(), open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val nid = Loud.alertId(id)
        // loud, but never pinned: a swipe (even of the pop-up) silences it and leaves a quiet reminder until answered
        val b = NotificationCompat.Builder(ctx, MedLogApp.CH_ALERT).setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(com.suryaprakash.medlog.ui.tr(Wording.alertTitle(from, urgent))).setContentText(com.suryaprakash.medlog.ui.tr(text))
            .setPriority(NotificationCompat.PRIORITY_MAX).setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(pi, true).setContentIntent(pi).setAutoCancel(false)
            .setDeleteIntent(SilenceReceiver.intent(ctx, nid, "$from: $text", "Not answered yet. Tap to answer.", pi))
        // the same three answers as the alert screen
        AlertReplies.forKind(kind).forEachIndexed { slot, r ->
            b.addAction(0, com.suryaprakash.medlog.ui.tr(r.words), AlertReplyReceiver.intent(ctx, id, r.code, mid, pairId.ifEmpty { null }, slot, kind, text, from))
        }
        runCatching { androidx.core.app.NotificationManagerCompat.from(ctx).notify(nid, b.build()) }
        runCatching { ctx.startActivity(open) }
    }

    // ───────────────────── pairing, face to face ─────────────────────

    /**
     * Pairing, face to face. Both phones look and can be found at once: each lists the MedLog phones nearby by name,
     * and either person picks the one to connect. Both then see the same 4 digits. Connecting again (a reset or new
     * phone) replaces the old link on both phones rather than adding a second one.
     * [found]: nearby phones (endpoint id, name). [incoming]: a helper's phone asking the person's phone to connect.
     */
    data class PairState(val found: List<Pair<String, String>> = emptyList(), val digits: String? = null, val done: String? = null, val error: String? = null,
                         val incoming: Pair<String, String>? = null, val connecting: String? = null,
                         val confirmed: Boolean = false)
    val pair = MutableStateFlow(PairState())
    private var pendingPair: String? = null
    // Held until the person taps "They match": no connection is accepted before the two numbers have been compared.
    private var pendingAccept: (() -> Unit)? = null

    // ── the helper's phone ──

    /** Helper's phone: be findable as [myName], and list the phones nearby of people looking for a helper. */
    fun pairAsHelper(ctx: Context, myName: String) {
        val c = client(ctx)
        pair.value = PairState()
        c.stopAdvertising(); c.stopDiscovery()
        c.startAdvertising("P|$myName", SERVICE, helperSide(ctx, myName), AdvertisingOptions.Builder().setStrategy(STRATEGY).build())
            .addOnFailureListener { pair.value = pair.value.copy(error = "Bluetooth is off or not allowed.") }
        c.startDiscovery(SERVICE, finder("Q|"), DiscoveryOptions.Builder().setStrategy(STRATEGY).build())
    }

    /** Helper's phone: connect to the person's phone the helper picked from the list. */
    fun requestPerson(ctx: Context, endpointId: String, myName: String) {
        val name = pair.value.found.firstOrNull { it.first == endpointId }?.second
        pair.value = pair.value.copy(connecting = name, error = null)
        client(ctx).requestConnection("PH|$myName", endpointId, helperSide(ctx, myName))
            .addOnFailureListener { pair.value = pair.value.copy(connecting = null, error = "Could not reach that phone. Bring them closer.") }
    }

    /** What the helper's phone does with a connection, whichever side started it: accept, then take the pairing it's sent. */
    private fun helperSide(ctx: Context, myName: String) = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(id: String, info: ConnectionInfo) {
            val c = client(ctx)
            pendingPair = id
            pair.value = pair.value.copy(digits = info.authenticationDigits, confirmed = false)
            val cb = object : PayloadCallback() {
                override fun onPayloadReceived(eid: String, p: Payload) {
                    val o = JSONObject(String(p.asBytes() ?: return))
                    val s = ctx.medlog.settings
                    // connecting again: the old link to the same phone goes, so the person isn't listed twice
                    o.optString("replaces").takeIf { it.isNotBlank() && it != o.getString("pairId") }?.let { People.remove(ctx, it) }
                    People.put(ctx, CaredFor(o.getString("pairId"), o.getString("key"), o.optString("name"), o.optString("family")))
                    s.putString("my_name", myName)
                    // use the same relay as the person's phone, so internet alerts meet in the same mailbox
                    s.update { it.copy(pairedWith = People.names(ctx), role = it.role, onboarded = if (it.role == "helper") true else it.onboarded,
                        relayUrl = o.optString("relay"), internetLink = o.optBoolean("internet", true)) }
                    pair.value = pair.value.copy(done = o.optString("name"), connecting = null)
                    c.stopAdvertising(); c.stopDiscovery(); c.disconnectFromEndpoint(eid)
                    Relay.stop()
                    startListening(ctx)
                }
                override fun onPayloadTransferUpdate(eid: String, u: PayloadTransferUpdate) {}
            }
            pendingAccept = { c.acceptConnection(id, cb).addOnFailureListener { pair.value = pair.value.copy(digits = null, connecting = null, confirmed = false, error = "Pairing did not finish. Try again.") } }
        }
        override fun onConnectionResult(id: String, r: ConnectionResolution) {
            if (!r.status.isSuccess) pair.value = pair.value.copy(digits = null, connecting = null, error = "Pairing did not finish. Try again.")
        }
        override fun onDisconnected(id: String) {}
    }

    // ── the person's phone ──

    /** The person's phone: list helper phones nearby that are ready to pair, and be findable by them too. */
    fun findHelpers(ctx: Context) {
        val app = ctx.medlog
        val c = client(ctx)
        pair.value = PairState()
        c.stopDiscovery(); c.stopAdvertising()
        c.startDiscovery(SERVICE, finder("P|"), DiscoveryOptions.Builder().setStrategy(STRATEGY).build())
            .addOnFailureListener { pair.value = pair.value.copy(error = "Bluetooth is off or not allowed.") }
        app.scope.launch {
            val me = app.repo.profile().name.ifBlank { "MedLog" }
            c.startAdvertising("Q|$me", SERVICE, personSide(ctx), AdvertisingOptions.Builder().setStrategy(STRATEGY).build())
        }
    }

    private fun finder(prefix: String) = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(id: String, info: DiscoveredEndpointInfo) {
            if (info.endpointName.startsWith(prefix)) pair.value = pair.value.copy(found = (pair.value.found + (id to info.endpointName.removePrefix(prefix))).distinctBy { it.first })
        }
        override fun onEndpointLost(id: String) { pair.value = pair.value.copy(found = pair.value.found.filter { it.first != id }) }
    }

    /** Which helper the person's phone is connecting, once chosen: name, number, and the list entry it replaces. */
    private data class Chosen(val name: String, val phone: String, val helperId: Long?)
    private var chosen: Chosen? = null

    /** The person's phone connects to a helper phone it found; both screens then show the same 4 digits. */
    fun pairWith(ctx: Context, endpointId: String, helperName: String, helperPhone: String, helperId: Long? = null) {
        val app = ctx.medlog
        chosen = Chosen(helperName, helperPhone, helperId)
        pair.value = pair.value.copy(connecting = helperName, error = null)
        app.scope.launch {
            val me = app.repo.profile().name.ifBlank { "MedLog" }
            client(ctx).requestConnection("PU|$me", endpointId, personSide(ctx))
                .addOnFailureListener { pair.value = pair.value.copy(connecting = null, error = "Could not reach that phone. Bring them closer.") }
        }
    }

    /** A helper's phone asked this phone to connect; the person said which helper it is. */
    fun acceptIncoming(ctx: Context, helperName: String, helperPhone: String, helperId: Long?) {
        val (id, _) = pair.value.incoming ?: return
        if (!pair.value.confirmed) return
        chosen = Chosen(helperName, helperPhone, helperId)
        pair.value = pair.value.copy(connecting = helperName)
        client(ctx).acceptConnection(id, object : PayloadCallback() {
            override fun onPayloadReceived(eid: String, p: Payload) {}
            override fun onPayloadTransferUpdate(eid: String, u: PayloadTransferUpdate) {}
        })
    }

    /** What the person's phone does with a connection: accept its own request, and once through, send the pairing. */
    private fun personSide(ctx: Context) = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(id: String, info: ConnectionInfo) {
            pendingPair = id
            if (info.endpointName.startsWith("PH|")) {
                // a helper picked this phone: the person says who it is before anything is sent
                pair.value = pair.value.copy(incoming = id to info.endpointName.removePrefix("PH|"), digits = info.authenticationDigits)
                return
            }
            pair.value = pair.value.copy(digits = info.authenticationDigits, confirmed = false)
            val cb = object : PayloadCallback() {
                override fun onPayloadReceived(eid: String, p: Payload) {}
                override fun onPayloadTransferUpdate(eid: String, u: PayloadTransferUpdate) {}
            }
            pendingAccept = { client(ctx).acceptConnection(id, cb).addOnFailureListener { pair.value = pair.value.copy(digits = null, connecting = null, confirmed = false, error = "Pairing did not finish. Try again.") } }
        }
        override fun onConnectionResult(id: String, r: ConnectionResolution) {
            if (!r.status.isSuccess) { pair.value = pair.value.copy(digits = null, incoming = null, connecting = null, error = "Pairing did not finish. Try again."); return }
            val who = chosen ?: return
            sendPairing(ctx, id, who)
        }
        override fun onDisconnected(id: String) {}
    }

    private fun sendPairing(ctx: Context, endpointId: String, who: Chosen) {
        val app = ctx.medlog
        val c = client(ctx)
        app.scope.launch {
            val me = app.repo.profile().name.ifBlank { "MedLog" }
            val pairId = Keys.randomB64(9).replace('/', '_').replace('+', '-')
            val key = Keys.randomB64(32)
            val helpers = app.db.helpers().all()
            val existing = helpers.firstOrNull { it.id == who.helperId }
                ?: helpers.firstOrNull { who.phone.isNotBlank() && it.phone.filter(Char::isDigit).takeLast(10) == who.phone.filter(Char::isDigit).takeLast(10) }
            c.sendPayload(endpointId, Payload.fromBytes(JSONObject().put("pairId", pairId).put("key", key).put("name", me)
                .put("relay", app.settings.value.relayUrl).put("internet", app.settings.value.internetLink)
                .put("replaces", existing?.pairId ?: "")
                .put("family", FamilyChat.familyKey(ctx)).toString().toByteArray()))
            com.suryaprakash.medlog.data.Sync.forget(ctx, pairId)
            if (existing != null) app.db.helpers().update(existing.copy(pairId = pairId, pairKey = key))
            else app.db.helpers().insert(Helper(name = who.name, phone = who.phone, pairId = pairId, pairKey = key))
            pair.value = pair.value.copy(done = who.name, connecting = null, incoming = null)
            chosen = null
            startListening(ctx); Relay.reconnect()
            delay(2000); c.disconnectFromEndpoint(endpointId); c.stopDiscovery(); c.stopAdvertising()
        }
    }

    /** This person said the two numbers match: only now is the connection accepted (it finishes when both phones have). */
    fun confirmDigits(ctx: Context) {
        pair.value = pair.value.copy(confirmed = true)
        val go = pendingAccept ?: return   // a helper's request: accepted when the person has said which helper it is
        pendingAccept = null
        go()
    }

    fun cancelPairing(ctx: Context) {
        pendingPair?.let { client(ctx).rejectConnection(it) }
        pendingPair = null; pendingAccept = null
        client(ctx).stopAdvertising(); client(ctx).stopDiscovery()
        pair.value = PairState()
    }
}

/** Keeps the phone reachable: the helper's phone for its person, the person's phone for its helpers (small notification). */
class NearbyService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val st = medlog.settings.value
        val helper = People.any(this)
        val n: Notification = NotificationCompat.Builder(this, MedLogApp.CH_SERVICE).setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(com.suryaprakash.medlog.ui.tr(if (helper) "Listening for ${People.names(this)}" else "Connected to your family"))
            .setContentText(com.suryaprakash.medlog.ui.tr(if (helper) "You'll be alerted when they need you, near or far." else "So your helpers' answers reach you straight away."))
            .setOngoing(true).setPriority(NotificationCompat.PRIORITY_LOW).build()
        // passing messages between the family's phones is "remote messaging"; Bluetooth to a nearby phone is "connected device"
        val type = when {
            Build.VERSION.SDK_INT >= 34 -> ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING or (if (helper && Nearby.allowed(this)) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0)
            Build.VERSION.SDK_INT >= 29 -> ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            else -> 0
        }
        runCatching { ServiceCompat.startForeground(this, 42, n, type) }.onFailure { stopSelf(); return START_NOT_STICKY }
        if (helper && Nearby.allowed(this)) Nearby.advertise(this)
        val ctx = this
        Relay.listen(this, medlog.scope, { Nearby.listenTopics(ctx) }) { topic, o -> Nearby.onRelayNote(ctx, topic, o) }
        medlog.scope.launch {
            kotlinx.coroutines.delay(5_000)
            People.all(ctx).forEach { com.suryaprakash.medlog.data.Sync.askSince(ctx, it.pairId) }
            com.suryaprakash.medlog.data.Sync.push(ctx)
        }
        return START_STICKY
    }
    override fun onDestroy() { runCatching { Nearby.client(this).stopAdvertising() }; Relay.stop(); super.onDestroy() }

    /** MedLog swiped away from recent apps: stop any alarm sound (the message itself stays on the helper page). */
    override fun onTaskRemoved(rootIntent: Intent?) { AlertSound.stop(); com.suryaprakash.medlog.meds.AlarmTone.stop(); super.onTaskRemoved(rootIntent) }
}

