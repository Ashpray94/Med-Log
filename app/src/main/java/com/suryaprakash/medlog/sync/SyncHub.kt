package com.suryaprakash.medlog.sync

import android.content.Context
import android.util.Base64
import android.util.Log
import androidx.room.InvalidationTracker
import androidx.room.RoomDatabase
import com.suryaprakash.medlog.MedLogApp
import com.suryaprakash.medlog.data.DAY
import com.suryaprakash.medlog.data.DoseStatus
import com.suryaprakash.medlog.data.People
import com.suryaprakash.medlog.help.FamilyChat
import com.suryaprakash.medlog.help.Nearby
import com.suryaprakash.medlog.help.Relay
import com.suryaprakash.medlog.meds.DoseAlert
import com.suryaprakash.medlog.meds.Scheduler
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/** After incoming changes on the person's own phone: alarms, notification and widget (plan B.6). Thin glue over the Scheduler. */
class OwnEffects(private val app: MedLogApp) : SyncEffects {
    override suspend fun medicineChanged(uid: String) {
        val m = app.db.medicines().byUid(uid) ?: return
        val now = System.currentTimeMillis()
        val want = Scheduler.times(m, now, now + 2 * DAY).toSet()
        for (d in app.db.doses().open()) if (d.medicineId == m.id && d.status == DoseStatus.DUE && d.scheduledAt > now && d.scheduledAt !in want) app.db.doses().delete(d.id)
    }
    override suspend fun medicineStopped(uid: String) {
        val m = app.db.medicines().byUid(uid)
        if (m != null) Scheduler.stopMedicine(app, m) else DoseAlert.cancel(app, 0)
    }
    override suspend fun doseClosed(uid: String) = DoseAlert.cancel(app, app.db.doses().byUid(uid)?.id ?: 0)
    override suspend fun reschedule() = Scheduler.reschedule(app)
    override fun refreshWidget() = app.refreshWidgets()
}

/**
 * Wires [SyncRunner]s to the relay. One channel per shared database and family key: today the phone's own (its own family key,
 * once a helper is paired); a helper phone will attach one per replica with [attach] and the replica's own key.
 *
 * All store access runs on ONE thread. Messages go to the relay through a queue with retries; whatever is still lost is found again
 * by the next HELLO (every 6 hours, or when a phone starts listening), because the relay only keeps notes 12 hours.
 * Bluetooth: not used for sync (see docs/WORK_PLAN.md B.4 note): Nearby only connects for a minute during an alert.
 */
class SyncHub(private val app: MedLogApp) {
    private class Chan(val name: String, val key: ByteArray, val runner: SyncRunner, val db: RoomDatabase, val observer: InvalidationTracker.Observer) {
        val topic: String get() = Relay.topic(key, TOPIC)
    }

    private val dispatcher = Executors.newSingleThreadExecutor { Thread(it, "medlog-sync").apply { isDaemon = true } }.asCoroutineDispatcher()
    private val chans = ConcurrentHashMap<String, Chan>()
    private val outbox = Channel<Pair<Chan, String>>(Channel.UNLIMITED)

    init {
        app.scope.launch {
            for ((c, json) in outbox) {
                if (!Relay.enabled(app) || chans[c.name] !== c) continue // switched off, or the channel was detached (wiped)
                var ok = false
                for (attempt in 0 until 5) {
                    ok = Relay.post(app, c.key, TOPIC, JSONObject(json))
                    if (ok) break
                    delay(30_000L)
                    if (chans[c.name] !== c) break
                }
                if (!ok) Log.w(TAG, "gave up sending; the next HELLO will catch the family up")
            }
        }
    }

    /** The mailboxes to listen to (topic to family key), for [Relay.listen]. */
    fun topics(): Map<String, ByteArray> = chans.values.associate { it.topic to it.key }

    /** True when a channel took the note. */
    suspend fun onNote(topic: String, o: JSONObject): Boolean {
        val c = chans[topic] ?: return false
        runCatching { c.runner.onMessage(o.toString()) }.onFailure { Log.w(TAG, "apply", it) }
        return true
    }

    /** Does this phone share its data with family (a paired helper, or a family key it already gave out)? */
    suspend fun sharing(ctx: Context): Boolean =
        app.settings.getString("own_family_key") != null || People.any(ctx) || app.db.helpers().all().any { it.pairKey != null }

    /** Person's phone: attach the own database when a helper is paired, detach when none is left. Cheap to call repeatedly. */
    suspend fun refreshOwn(ctx: Context) {
        val st = app.settings.value
        val paired = st.role != "helper" && st.onboarded && app.db.helpers().all().any { it.pairKey != null }
        if (paired && chans[OWN] == null) attach(OWN, Base64.decode(FamilyChat.familyKey(ctx), Base64.NO_WRAP), app.db, OwnEffects(app))
        else if (!paired && chans[OWN] != null) detach(OWN)
    }

    /** Runs [name]'s database in sync over the mailbox of [key]. The database must be a [com.suryaprakash.medlog.data.MedDb] (own or replica). */
    fun attach(name: String, key: ByteArray, db: RoomDatabase, effects: SyncEffects): SyncRunner {
        chans[name]?.let { detach(name) }
        lateinit var chan: Chan
        val runner = SyncRunner(
            storeFactory = { RoomSyncStore(db.openHelper.writableDatabase) },
            send = { json -> outbox.trySend(chan to json) },
            effects = effects, dispatcher = dispatcher, scope = app.scope,
            sent = object : SentMark {
                override fun load(device: String) = app.settings.getLong("sync_sent_${name}_$device")
                override fun save(device: String, seq: Long) = app.settings.putLong("sync_sent_${name}_$device", seq)
            },
            log = { Log.w(TAG, it) },
        )
        val observer = object : InvalidationTracker.Observer("sync_rows") { override fun onInvalidated(tables: Set<String>) = runner.localChanged() }
        chan = Chan(name, key, runner, db, observer)
        chans[name] = chan
        db.invalidationTracker.addObserver(observer)
        app.scope.launch { runCatching { runner.start() }.onFailure { Log.w(TAG, "start", it) } }
        Relay.reconnect() // pick up the new mailbox
        return runner
    }

    fun detach(name: String) {
        val c = chans.remove(name) ?: return
        c.runner.stop()
        runCatching { c.db.invalidationTracker.removeObserver(c.observer) }
    }

    /** The database was restored: the phone is a new device now. Reopen the store, say HELLO. */
    suspend fun identityChanged(ctx: Context) {
        for (c in chans.values) runCatching { c.runner.reopen() }
        refreshOwn(ctx)
    }

    /**
     * Settings, Privacy, Delete everything. First the phone stops sharing and forgets the family key, so a wiped phone can neither
     * hand out nor receive family data; then the tables are emptied in one silent transaction (no deletes go out) and the phone gets
     * a new device id with number 0.
     */
    suspend fun wipeAll(ctx: Context) {
        for (n in chans.keys.toList()) detach(n)
        Nearby.stopListening(ctx); Relay.stop()
        app.settings.forgetFamily()
        withContext(dispatcher) {
            val db = app.db
            db.runInTransaction { val w = db.openHelper.writableDatabase; for (sql in SyncSql.wipe()) w.execSQL(sql) }
        }
    }

    companion object {
        const val TOPIC = "sync"
        const val OWN = "own"
        private const val TAG = "MedLogSync"
    }
}
