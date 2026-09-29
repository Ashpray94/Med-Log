package com.suryaprakash.medlog.sync

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * What the phone does after other phones' changes were written (plan B.6). Android implements it with the Scheduler, DoseAlert and the
 * widget; the tests use a fake. Uids are the shared ids of the rows.
 */
interface SyncEffects {
    /** A medicine was added or edited and is active: its future DUE doses that no longer match its times go, missing ones are planned again. */
    suspend fun medicineChanged(uid: String)
    /** A medicine was deleted or is no longer active: its open doses are closed and its alarm and notification go. */
    suspend fun medicineStopped(uid: String)
    /** A dose was taken, skipped, missed or deleted on another phone: its alarm and notification go. */
    suspend fun doseClosed(uid: String)
    /** Once after a batch that touched medicines or doses: arm the next alarm. */
    suspend fun reschedule()
    /** After any batch that changed something. */
    fun refreshWidget()

    companion object { val NONE = object : SyncEffects {
        override suspend fun medicineChanged(uid: String) {}
        override suspend fun medicineStopped(uid: String) {}
        override suspend fun doseClosed(uid: String) {}
        override suspend fun reschedule() {}
        override fun refreshWidget() {}
    } }
}

/** Where the runner keeps "my changes up to this number are already sent", per device id (a new device id starts again at 0). */
interface SentMark {
    fun load(device: String): Long
    fun save(device: String, seq: Long)
    companion object { fun memory() = object : SentMark {
        private val m = HashMap<String, Long>()
        override fun load(device: String) = m[device] ?: 0L
        override fun save(device: String, seq: Long) { m[device] = seq }
    } }
}

/**
 * Runs one shared database's side of the sync (the person's own phone today; one per replica on a helper phone later).
 * Everything that touches the store runs on [dispatcher], which must be single-threaded. Pass every incoming message to [onMessage]
 * and call [localChanged] whenever the database's sync_rows changed. Messages go out through [send] (the relay layer posts them).
 *
 * HELLO goes out when [start] is called, every [helloEveryMs] (6 hours), when a message comes from a phone never heard from before,
 * and (from the engine) when a gap is seen. Local changes go out [debounceMs] after the last write.
 */
class SyncRunner(
    private val storeFactory: () -> SyncStore,
    private val send: (String) -> Unit,
    private val effects: SyncEffects = SyncEffects.NONE,
    private val dispatcher: CoroutineDispatcher,
    private val scope: CoroutineScope,
    private val sent: SentMark = SentMark.memory(),
    private val clock: () -> Long = System::currentTimeMillis,
    private val debounceMs: Long = 2_000L,
    private val helloEveryMs: Long = 6 * 3600_000L,
    private val log: (String) -> Unit = {},
) {
    // touched only on [dispatcher]
    private var store: SyncStore? = null
    private var engine: SyncEngine? = null
    private var seen = HashSet<String>()
    private var published = 0L
    private var push: Job? = null
    private var loop: Job? = null

    private fun open() {
        val s = storeFactory()
        store = s
        engine = SyncEngine(s, { send(it) }, clock = clock)
        seen = HashSet(s.have().keys)
        published = sent.load(s.device)
    }

    private fun ready(): SyncEngine { if (engine == null) open(); return engine!! }

    /** This phone's id (opens the store on first use). */
    suspend fun device(): String = withContext(dispatcher) { ready(); store!!.device }

    /** Starts the 6-hourly HELLO, sends one now and sends any local changes not sent yet. */
    suspend fun start() {
        hello()
        publishNow()
        loop?.cancel()
        loop = scope.launch { while (isActive) { delay(helloEveryMs); hello() } }
    }

    fun stop() { loop?.cancel(); push?.cancel() }

    suspend fun hello() = withContext(dispatcher) { send(ready().hello()) }

    /** The database's own rows changed: send them after a short pause (a burst of edits goes as one batch). */
    fun localChanged() {
        push?.cancel()
        push = scope.launch { delay(debounceMs); publishNow() }
    }

    /** Sends this phone's changes since the last send. Returns the number of messages. */
    suspend fun publishNow(): Int = withContext(dispatcher) {
        val e = ready(); val s = store!!
        val n = e.publishLocal(published)
        published = s.localSeq
        sent.save(s.device, published)
        n
    }

    /**
     * The database was erased or restored: this phone has a new identity. Opens the store again (a new device id, its own numbers)
     * and says HELLO so the family sends what this phone lacks.
     */
    suspend fun reopen() = withContext(dispatcher) {
        open()
        send(engine!!.hello())
        val s = store!!
        val n = engine!!.publishLocal(published); published = s.localSeq; sent.save(s.device, published)
        n
    }

    /** One message from the family mailbox. Writes it, then reacts (alarms, notification, widget). */
    suspend fun onMessage(json: String): Applied = withContext(dispatcher) {
        val e = ready()
        val from = try { JSONObject(json).optString("from") } catch (ex: Exception) { return@withContext Applied.NONE }
        val a = e.onMessage(json)
        if (from.isNotEmpty() && from != store!!.device && seen.add(from)) send(e.hello()) // a phone never heard from before: tell it what we have
        val now = clock()
        for (o in a.written) if (o.at > now + SKEW_LOG_MS) log("op ${o.tbl}/${o.uid} from ${o.by} is dated ${(o.at - now) / 60_000} min ahead of this clock")
        if (a.applied > 0) react(a)
        a
    }

    private suspend fun react(a: Applied) {
        val meds = LinkedHashSet<String>(); val stopped = LinkedHashSet<String>(); val doses = LinkedHashSet<String>()
        for (o in a.written) when (o.tbl) {
            "medicines" -> if (o.del || o.row?.optInt("active", 1) == 0) stopped += o.uid else meds += o.uid
            "doses" -> if (o.del || o.row?.optString("status") in CLOSED) doses += o.uid
        }
        for (u in stopped) effects.medicineStopped(u)
        for (u in meds) effects.medicineChanged(u)
        for (u in doses) effects.doseClosed(u)
        if (a.tables.any { it == "medicines" || it == "doses" }) effects.reschedule()
        effects.refreshWidget()
    }

    companion object {
        const val SKEW_LOG_MS = 10 * 60_000L
        private val CLOSED = setOf("TAKEN", "SKIPPED", "MISSED")
    }
}
