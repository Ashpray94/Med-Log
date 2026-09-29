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
    fun broadcast(ctx: Context, kind: String, text: String, audio: File? = null) {
        val app = ctx.medlog
        if (app.settings.value.role != "self") return
        app.scope.launch {
            val helpers = app.ownDb.helpers().all().filter { it.pairId != null && it.pairKey != null }
            if (helpers.isEmpty()) return@launch
            val me = app.ownRepo.profile().name.ifBlank { "MedLog" }
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
        acks.value = acks.value + Ack(h.name, reply)
        ctx.medlog.speaker.say("${h.name}: ${replyWords(reply)}")
        ctx.medlog.scope.launch { ctx.medlog.ownRepo.addEvent(com.suryaprakash.medlog.data.Kind.MESSAGE, "${h.name} replied: ${replyWords(reply)}") }
    }

    /** Everything a helper's phone sends to the person's phone: receipts, replies and "How are you?". */
    internal fun fromHelper(ctx: Context, h: Helper, o: JSONObject) {
        when {
            o.has("reply") -> onReply(ctx, h, o)
            o.optString("ask").isNotEmpty() -> askedByHelper(ctx, h, o)
        }
    }

    private suspend fun nearbySend(ctx: Context, helpers: List<Helper>, body: JSONObject) {
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
                    c.sendPayload(id, Payload.fromBytes(Keys.seal(key(h), body.toString().toByteArray())))
                    reached.value = reached.value + h.name
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

    fun replyWords(r: String) = when (r) {
        "coming" -> "I'm coming"; "5min" -> "In 5 minutes"; "call" -> "I'll call you"; "cant" -> "Can't come now, I'll call"; "got" -> "Got it"; else -> r
    }

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
            val h = app.ownDb.helpers().all().firstOrNull { it.id == helperId && it.pairKey != null } ?: return@launch
            val me = app.ownRepo.profile().name.ifBlank { "MedLog" }
            val body = JSONObject().put("kind", "ANSWER").put("answer", answerKey).put("text", words).put("from", me)
                .put("at", System.currentTimeMillis()).put("mid", Keys.randomB64(9)).put("re", re)
            Relay.post(ctx, key(h), Relay.DOWN, body)
            app.ownRepo.addEvent(com.suryaprakash.medlog.data.Kind.CHECKIN, "Told ${h.name}: $words")
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
    fun reply(ctx: Context, r: String, re: String = lastMid, pairId: String? = null) {
        val msg = JSONObject().put("reply", r).put("re", re).put("at", System.currentTimeMillis())
        replyTo?.let { (id, key) -> runCatching { client(ctx).sendPayload(id, Payload.fromBytes(Keys.seal(key, msg.toString().toByteArray()))) } }
        val p = person(ctx, pairId) ?: return
        ctx.medlog.scope.launch { Relay.post(ctx, p.keyBytes, Relay.UP, msg) }
        // the other helpers see who answered, so nobody is left wondering who went
        if (r != "got") FamilyChat.announceReply(ctx, r, lastText, p)
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
                if (app.ownDb.helpers().all().any { it.pairKey != null }) { start(); FamilyChat.shareKey(ctx) } else stopListening(ctx)
            }
            else -> stopListening(ctx)
        }
    }

    fun stopListening(ctx: Context) = ctx.stopService(Intent(ctx, NearbyService::class.java))

    /** Mailboxes this phone listens to: its person's (helper) or every paired helper's reply mailbox (person). */
    internal suspend fun listenTopics(ctx: Context): Map<String, ByteArray> {
        val app = ctx.medlog
        app.sync.refreshOwn(ctx)
        app.sync.refreshReplicas(ctx)
        return buildMap {
            putAll(app.sync.topics())
            for (p in People.all(ctx)) {
                put(Relay.topic(p.keyBytes, Relay.DOWN), p.keyBytes)
                p.familyBytes?.let { put(Relay.topic(it, "family"), it) }
            }
            if (app.settings.value.role != "helper" && app.settings.value.onboarded)
                app.ownDb.helpers().all().forEach { h -> h.pairKey?.let { key(h) }?.let { put(Relay.topic(it, Relay.UP), it) } }
        }
    }

    internal suspend fun onRelayNote(ctx: Context, topic: String, o: JSONObject) {
        val app = ctx.medlog
        if (app.sync.onNote(topic, o)) return
        for (p in People.all(ctx)) {
            if (topic == Relay.topic(p.keyBytes, Relay.DOWN)) { received(ctx, o, viaNearby = false, pairId = p.pairId); return }
            if (p.familyBytes?.let { Relay.topic(it, "family") } == topic) { FamilyChat.received(ctx, o); return }
        }
        val h = app.ownDb.helpers().all().firstOrNull { it.pairKey != null && Relay.topic(key(it), Relay.UP) == topic } ?: return
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
                val id = app.ownDb.inbox().insert(InboxItem(fromName = o.optString("from"), text = o.optString("text"), kind = kind, at = sentAt, acked = true))
                val worried = o.optString("answer") in setOf("notwell", "call")
                val pi = PendingIntent.getActivity(ctx, id.toInt(), Intent(ctx, com.suryaprakash.medlog.MainActivity::class.java).setData(android.net.Uri.parse("medlog://helper")), PendingIntent.FLAG_IMMUTABLE)
                val n = NotificationCompat.Builder(ctx, MedLogApp.CH_ALERT).setSmallIcon(R.drawable.ic_stat)
                    .setContentTitle(com.suryaprakash.medlog.ui.tr(Wording.answer(o.optString("from"), o.optString("text"))))
                    .setContentText(com.suryaprakash.medlog.ui.tr(if (worried) "You may want to call them." else "Answer to your \"How are you?\""))
                    .setPriority(if (worried) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
                    .setContentIntent(pi).setAutoCancel(true).build()
                runCatching { androidx.core.app.NotificationManagerCompat.from(ctx).notify(5000 + id.toInt(), n) }
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
            val id = app.ownDb.inbox().insert(InboxItem(fromName = o.optString("from"), text = o.optString("text"), kind = kind, at = sentAt, audioPath = audio?.absolutePath))
            if (!fresh) {
                // old news: a normal notification, not an alarm in the middle of the night
                val sentWords = java.text.SimpleDateFormat("h:mm a, d MMM", java.util.Locale.getDefault()).format(java.util.Date(sentAt))
                val pi = PendingIntent.getActivity(ctx, id.toInt(), Intent(ctx, com.suryaprakash.medlog.MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
                val n = NotificationCompat.Builder(ctx, MedLogApp.CH_ALERT).setSmallIcon(R.drawable.ic_stat)
                    .setContentTitle(com.suryaprakash.medlog.ui.tr("${o.optString("from")}: ${o.optString("text")}")).setContentText(com.suryaprakash.medlog.ui.tr("Sent earlier (at $sentWords). A call can check they're OK."))
                    .setContentIntent(pi).setAutoCancel(true).build()
                runCatching { androidx.core.app.NotificationManagerCompat.from(ctx).notify(5000 + id.toInt(), n) }
                return@launch
            }
            val urgent = kind in setOf("SOS", "DANGER", "FALL")
            AlertSound.start(ctx, urgent = urgent)
            audio?.let { runCatching { android.media.MediaPlayer().apply { setDataSource(it.absolutePath); prepare(); start() } } }
            val open = Intent(ctx, AlertActivity::class.java).putExtra(AlertActivity.MODE, AlertActivity.HELPER)
                .putExtra("from", o.optString("from")).putExtra("text", o.optString("text")).putExtra("kind", kind).putExtra("id", id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val pi = PendingIntent.getActivity(ctx, id.toInt(), open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val n = NotificationCompat.Builder(ctx, MedLogApp.CH_ALERT).setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(com.suryaprakash.medlog.ui.tr(Wording.alertTitle(o.optString("from"), urgent))).setContentText(com.suryaprakash.medlog.ui.tr(o.optString("text")))
                .setPriority(NotificationCompat.PRIORITY_MAX).setCategory(NotificationCompat.CATEGORY_ALARM)
                .setFullScreenIntent(pi, true).setContentIntent(pi).setAutoCancel(true).build()
            runCatching { androidx.core.app.NotificationManagerCompat.from(ctx).notify(5000 + id.toInt(), n) }
            runCatching { ctx.startActivity(open) }
        }
    }

    // ───────────────────── pairing, face to face ─────────────────────

    data class PairState(val found: List<Pair<String, String>> = emptyList(), val digits: String? = null, val done: String? = null, val error: String? = null, val confirmed: Boolean = false)
    val pair = MutableStateFlow(PairState())
    private var pendingPair: String? = null
    // Held until the person taps "They match": nothing is accepted before the digits are compared.
    private var pendingPayload: PayloadCallback? = null

    /** Helper's phone: be findable for pairing, showing [myName]. */
    fun pairAsHelper(ctx: Context, myName: String) {
        val c = client(ctx)
        pair.value = PairState()
        c.stopAdvertising()
        c.startAdvertising("P|$myName", SERVICE, object : ConnectionLifecycleCallback() {
            override fun onConnectionInitiated(id: String, info: ConnectionInfo) {
                pendingPair = id
                pair.value = pair.value.copy(digits = info.authenticationDigits, confirmed = false)
                pendingPayload = object : PayloadCallback() {
                    override fun onPayloadReceived(eid: String, p: Payload) {
                        val o = JSONObject(String(p.asBytes() ?: return))
                        val s = ctx.medlog.settings
                        People.put(ctx, CaredFor(o.getString("pairId"), o.getString("key"), o.optString("name"), o.optString("family")))
                        s.putString("my_name", myName)
                        // use the same relay as the person's phone, so internet alerts meet in the same mailbox
                        s.update { it.copy(pairedWith = People.names(ctx), role = it.role, onboarded = if (it.role == "helper") true else it.onboarded,
                            relayUrl = o.optString("relay"), internetLink = o.optBoolean("internet", true)) }
                        pair.value = pair.value.copy(done = o.optString("name"))
                        c.stopAdvertising(); c.disconnectFromEndpoint(eid)
                        Relay.stop()
                        startListening(ctx)
                    }
                    override fun onPayloadTransferUpdate(eid: String, u: PayloadTransferUpdate) {}
                }
            }
            override fun onConnectionResult(id: String, r: ConnectionResolution) { if (!r.status.isSuccess) pair.value = pair.value.copy(error = "Pairing did not finish. Try again.") }
            override fun onDisconnected(id: String) {}
        }, AdvertisingOptions.Builder().setStrategy(STRATEGY).build()).addOnFailureListener { pair.value = pair.value.copy(error = "Bluetooth is off or not allowed.") }
    }

    /** The person's phone: look for helper phones that are in pairing mode. */
    fun findHelpers(ctx: Context) {
        val c = client(ctx)
        pair.value = PairState()
        c.stopDiscovery()
        c.startDiscovery(SERVICE, object : EndpointDiscoveryCallback() {
            override fun onEndpointFound(id: String, info: DiscoveredEndpointInfo) {
                if (info.endpointName.startsWith("P|")) pair.value = pair.value.copy(found = (pair.value.found + (id to info.endpointName.removePrefix("P|"))).distinctBy { it.first })
            }
            override fun onEndpointLost(id: String) { pair.value = pair.value.copy(found = pair.value.found.filter { it.first != id }) }
        }, DiscoveryOptions.Builder().setStrategy(STRATEGY).build()).addOnFailureListener { pair.value = pair.value.copy(error = "Bluetooth is off or not allowed.") }
    }

    /** The person's phone: connect to a found helper; both screens then show the same 4 digits. */
    fun pairWith(ctx: Context, endpointId: String, helperName: String, helperPhone: String) {
        val app = ctx.medlog
        val c = client(ctx)
        app.scope.launch {
            val me = app.ownRepo.profile().name.ifBlank { "MedLog" }
            val pairId = Keys.randomB64(9).replace('/', '_').replace('+', '-')
            val key = Keys.randomB64(32)
            c.requestConnection("PU|$me", endpointId, object : ConnectionLifecycleCallback() {
                override fun onConnectionInitiated(id: String, info: ConnectionInfo) {
                    pendingPair = id
                    pair.value = pair.value.copy(digits = info.authenticationDigits, confirmed = false)
                    pendingPayload = object : PayloadCallback() {
                        override fun onPayloadReceived(eid: String, p: Payload) {}
                        override fun onPayloadTransferUpdate(eid: String, u: PayloadTransferUpdate) {}
                    }
                }
                override fun onConnectionResult(id: String, r: ConnectionResolution) {
                    if (!r.status.isSuccess) { pair.value = pair.value.copy(error = "Pairing did not finish. Try again."); return }
                    c.sendPayload(id, Payload.fromBytes(JSONObject().put("pairId", pairId).put("key", key).put("name", me)
                        .put("relay", app.settings.value.relayUrl).put("internet", app.settings.value.internetLink)
                        .put("family", FamilyChat.familyKey(ctx)).toString().toByteArray()))
                    app.scope.launch {
                        val existing = app.ownDb.helpers().all().firstOrNull { it.phone.filter(Char::isDigit).takeLast(10) == helperPhone.filter(Char::isDigit).takeLast(10) && helperPhone.isNotBlank() }
                        if (existing != null) app.ownDb.helpers().update(existing.copy(pairId = pairId, pairKey = key))
                        else app.ownDb.helpers().insert(Helper(name = helperName, phone = helperPhone, pairId = pairId, pairKey = key))
                        pair.value = pair.value.copy(done = helperName)
                        startListening(ctx); Relay.reconnect()
                        delay(2000); c.disconnectFromEndpoint(id); c.stopDiscovery()
                    }
                }
                override fun onDisconnected(id: String) {}
            }).addOnFailureListener { pair.value = pair.value.copy(error = "Could not reach that phone. Bring them closer.") }
        }
    }

    /** This person said the digits match: only now accept. The connection finishes when both phones have accepted. */
    fun confirmDigits(ctx: Context) {
        val id = pendingPair ?: return
        val cb = pendingPayload ?: return
        pendingPayload = null
        pair.value = pair.value.copy(confirmed = true)
        client(ctx).acceptConnection(id, cb).addOnFailureListener { pair.value = pair.value.copy(error = "Pairing did not finish. Try again.", confirmed = false) }
    }

    fun cancelPairing(ctx: Context) {
        pendingPair?.let { client(ctx).rejectConnection(it) }
        pendingPair = null; pendingPayload = null
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
        return START_STICKY
    }
    override fun onDestroy() { runCatching { Nearby.client(this).stopAdvertising() }; Relay.stop(); super.onDestroy() }
}

