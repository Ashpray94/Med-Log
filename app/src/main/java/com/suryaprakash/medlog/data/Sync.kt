package com.suryaprakash.medlog.data

import android.content.Context
import android.util.Base64
import android.util.Log
import com.suryaprakash.medlog.help.Relay
import com.suryaprakash.medlog.medlog
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Keeps the person's phone and their helpers' phones showing the same history, medicines and doctor page.
 *
 * - The person's phone keeps its own records; each helper's phone keeps a copy per person it helps ([Mirror]).
 * - Anyone can add or change an entry on either side. Entries carry an id shared by both phones and the time they
 *   last changed; the newer copy wins.
 * - Only changes travel, packed and sealed with the pairing key, through the same encrypted mailbox the alerts use
 *   (so over the internet, from anywhere). Nothing is fetched on a timer: a change is sent about a minute after
 *   it's made, and the other phone already has its line open for alerts, so it arrives within seconds. A light
 *   check every 15 minutes (WorkManager) resends anything that didn't get through.
 * - A phone that was away longer than the mailbox keeps notes (12 hours) asks for everything since it last heard.
 */
object Sync {
    private const val TAG = "MedLogSync"
    private const val BATCH = 400

    /** When each person's data (helper's phone) or the helpers (person's phone) last came in, for "Updated 2 min ago". */
    val lastHeard = MutableStateFlow<Map<String, Long>>(emptyMap())
    private val lock = Mutex()
    private var pending: Job? = null

    /** One phone at the other end: its pairing id, its key, which of my databases it shares, and which mailbox I write to. */
    data class Peer(val id: String, val key: ByteArray, val db: MedDb, val dir: String, val name: String)

    suspend fun peers(ctx: Context): List<Peer> {
        val app = ctx.medlog
        val out = ArrayList<Peer>()
        // the person's phone: every paired helper sees my records
        if (app.settings.value.role != "helper" && app.settings.value.onboarded)
            app.db.helpers().all().filter { it.pairId != null && it.pairKey != null }.forEach { h ->
                out += Peer(h.pairId!!, Base64.decode(h.pairKey, Base64.NO_WRAP), app.db, Relay.DOWN, h.name)
            }
        // a helper's phone: one copy for each person it helps
        People.all(ctx).forEach { p -> out += Peer(p.pairId, p.keyBytes, Mirror.db(ctx, p.pairId), Relay.UP, p.name) }
        return out
    }

    // ───────── sending ─────────

    private val TABLES = arrayOf("notes", "medicines", "doses", "profile")

    /** Any change to [db]'s records schedules a send. Watching costs nothing until something changes. */
    fun watch(ctx: Context, db: MedDb) {
        db.invalidationTracker.addObserver(object : androidx.room.InvalidationTracker.Observer(TABLES) {
            override fun onInvalidated(tables: Set<String>) { soon(ctx) }
        })
    }

    /** Every 15 minutes, while there's someone to share with: resend anything that didn't get through. */
    fun schedule(ctx: Context) {
        val wm = androidx.work.WorkManager.getInstance(ctx)
        // no network condition: that needs a permission MedLog doesn't ask for; offline, the check just finds nothing to do
        val req = androidx.work.PeriodicWorkRequestBuilder<SyncWorker>(15, java.util.concurrent.TimeUnit.MINUTES).build()
        wm.enqueueUniquePeriodicWork("medlog-sync", androidx.work.ExistingPeriodicWorkPolicy.UPDATE, req)
    }

    /** Something changed here: send it in about a minute, so a burst of changes goes as one. */
    fun soon(ctx: Context, delayMs: Long = 60_000) {
        val app = ctx.medlog
        pending?.cancel()
        pending = app.scope.launch { delay(delayMs); push(ctx) }
    }

    /** Sends every peer what changed since it last got something from here. */
    suspend fun push(ctx: Context) = lock.withLock {
        val stuck = ArrayList<Peer>()
        for (p in peers(ctx)) {
            val ok = Relay.enabled(ctx) && runCatching { sendSince(ctx, p, sentUpTo(ctx, p.id)) }.onFailure { Log.w(TAG, "push ${p.id}", it) }.getOrDefault(false)
            if (!ok && p.dir == Relay.DOWN) stuck += p
        }
        if (stuck.isNotEmpty()) nearby(ctx, stuck)
    }

    /**
     * No internet (or sharing over the internet is off): hand the changes to helper phones in Bluetooth range
     * instead. At most once every 15 minutes, and only with something new, so it costs next to no battery.
     */
    private suspend fun nearby(ctx: Context, stuck: List<Peer>) {
        val app = ctx.medlog
        if (!com.suryaprakash.medlog.help.Nearby.allowed(ctx)) return
        val now = System.currentTimeMillis()
        if (now - app.settings.getLong("sync_bt_at") < 15 * 60_000L) return
        val helpers = app.db.helpers().all().filter { it.pairKey != null }
        val bodies = HashMap<Helper, JSONObject>(); val upTo = HashMap<String, Long>()
        for (p in stuck) {
            val h = helpers.firstOrNull { it.pairId == p.id } ?: continue
            val (body, to) = pack(ctx, p, sentUpTo(ctx, p.id)) ?: continue
            bodies[h] = body; upTo[p.id] = to
        }
        if (bodies.isEmpty()) return
        app.settings.putLong("sync_bt_at", now)
        com.suryaprakash.medlog.help.Nearby.nearbySendEach(ctx, bodies) { h -> upTo[h.pairId]?.let { app.settings.putLong("sync_sent_${h.pairId}", it) } }
    }

    private fun sentUpTo(ctx: Context, peer: String) = ctx.medlog.settings.getLong("sync_sent_$peer")

    /** Everything that changed after [since], in batches; the "sent up to" mark moves only once the mailbox has it. */
    /** True when everything got through (or there was nothing to send). */
    private suspend fun sendSince(ctx: Context, p: Peer, since: Long): Boolean {
        var from = since
        while (true) {
            val (body, to) = pack(ctx, p, from) ?: return true.also { Log.d(TAG, "send ${p.id} since $from: nothing") }
            Log.d(TAG, "send ${p.id} since $from to $to")
            if (!Relay.post(ctx, p.key, p.dir, body)) return false
            ctx.medlog.settings.putLong("sync_sent_${p.id}", to)
            if (to <= from) return true
            from = to
        }
    }

    /** The changes after [since] as one sealed-ready note, and the latest change time it covers; null when nothing changed. */
    internal suspend fun pack(ctx: Context, p: Peer, since: Long): Pair<JSONObject, Long>? {
        val db = p.db
        val meds = db.medicines().changedSince(since, BATCH)
        val notes = db.notes().changedSince(since, BATCH)
        val doses = db.doses().changedSince(since, BATCH)
        val profile = db.profile().get()?.takeIf { it.updatedAt > since }
        if (meds.isEmpty() && notes.isEmpty() && doses.isEmpty() && profile == null) return null
        // a full batch may stop part-way through a moment: cover only up to the earliest batch's end, the rest comes next
        val ends = listOfNotNull(meds.takeIf { it.size == BATCH }?.last()?.updatedAt, notes.takeIf { it.size == BATCH }?.last()?.updatedAt,
            doses.takeIf { it.size == BATCH }?.last()?.updatedAt)
        val to = ends.minOrNull() ?: listOfNotNull(meds.lastOrNull()?.updatedAt, notes.lastOrNull()?.updatedAt, doses.lastOrNull()?.updatedAt, profile?.updatedAt).max()
        val medUid = db.medicines().all().associate { it.id to it.uid }
        val o = JSONObject()
            .put("medicines", JSONArray(meds.filter { it.updatedAt <= to }.map { medJson(it) }))
            .put("notes", JSONArray(notes.filter { it.updatedAt <= to }.map { n ->
                // the group as the shared id of its first note: local row numbers differ from phone to phone
                noteJson(n).put("gu", n.groupId?.let { g -> if (g == n.id) n.uid else p.db.notes().get(g)?.uid }.orEmpty())
            }))
            .put("doses", JSONArray(doses.filter { it.updatedAt <= to }.mapNotNull { d -> medUid[d.medicineId]?.let { doseJson(d, it) } }))
        profile?.let { o.put("profile", profileJson(it)) }
        if (p.dir == Relay.DOWN) {
            // the helpers' names, so one helper can hand a message to another
            o.put("helpers", JSONArray(ctx.medlog.db.helpers().all().map { JSONObject().put("name", it.name).put("pairId", it.pairId ?: "").put("phone", it.phone).put("relation", it.relation) }))
        }
        val body = JSONObject().put("sync", 1).put("from", since).put("to", to).put("z", zip(o.toString()))
        return body to to
    }

    // ───────── receiving ─────────

    /** A note from [peer] with changes: apply the newer ones. A gap (notes missed while away) asks for the rest. */
    suspend fun received(ctx: Context, peerId: String, o: JSONObject) = lock.withLock {
        val p = peers(ctx).firstOrNull { it.id == peerId } ?: return@withLock
        val st = ctx.medlog.settings
        when {
            o.has("syncAsk") -> { Log.d(TAG, "ask from $peerId since ${o.optLong("syncAsk")}"); sendSince(ctx, p, o.optLong("syncAsk")); return@withLock }
            !o.has("sync") -> return@withLock
        }
        val heard = st.getLong("sync_got_$peerId")
        val from = o.optLong("from")
        if (from > heard + 1 && heard >= 0) {
            // something in between was missed: ask for everything since the last one that arrived
            Relay.post(ctx, p.key, p.dir, JSONObject().put("syncAsk", heard))
        }
        val data = JSONObject(unzip(o.getString("z")))
        Log.d(TAG, "got $peerId ${o.optLong("from")}..${o.optLong("to")}: ${data.optJSONArray("medicines")?.length()} meds, ${data.optJSONArray("notes")?.length()} notes, ${data.optJSONArray("doses")?.length()} doses")
        apply(p.db, data, hub = p.dir == Relay.DOWN)
        // copies of one entry under different ids (from before ids were kept) become one
        runCatching { ctx.medlog.repoFor(p.db).mergeCopies() }
        if (p.dir == Relay.UP) data.optJSONArray("helpers")?.let { st.putString("helpers_of_$peerId", it.toString()) }
        st.putLong("sync_got_$peerId", maxOf(heard, o.optLong("to")))
        val now = System.currentTimeMillis()
        st.putLong("sync_heard_$peerId", now)
        lastHeard.value = lastHeard.value + (peerId to now)
        if (p.dir == Relay.UP) runCatching { com.suryaprakash.medlog.meds.Scheduler.reschedule(ctx) }
    }

    /** A new pairing starts from nothing: forget what was sent to or heard from an earlier pairing with the same id. */
    fun forget(ctx: Context, pairId: String) {
        val st = ctx.medlog.settings
        listOf("sync_got_", "sync_sent_", "sync_heard_").forEach { st.putLong(it + pairId, 0L) }
        lastHeard.value = lastHeard.value - pairId
    }

    /** A helper's phone with nothing yet for this person: ask for everything. */
    suspend fun askAll(ctx: Context, pairId: String) {
        val p = peers(ctx).firstOrNull { it.id == pairId } ?: return
        Relay.post(ctx, p.key, p.dir, JSONObject().put("syncAsk", 0L))
    }

    /** Ask a phone for anything since the last update that arrived from it. */
    suspend fun askSince(ctx: Context, pairId: String) {
        val p = peers(ctx).firstOrNull { it.id == pairId } ?: return
        // an empty copy (just paired again, or cleared) asks for everything, whatever was heard before
        val since = if (p.db.medicines().lastChange() == null && p.db.notes().lastChange() == null) 0L else ctx.medlog.settings.getLong("sync_got_$pairId")
        Relay.post(ctx, p.key, p.dir, JSONObject().put("syncAsk", since))
    }

    fun heardAt(ctx: Context, peerId: String): Long = lastHeard.value[peerId] ?: ctx.medlog.settings.getLong("sync_heard_$peerId")

    /** Newer copies replace older ones; new entries are added. Their change times are kept, so they aren't sent back. */
    internal suspend fun apply(db: MedDb, o: JSONObject, hub: Boolean) {
        // the person's phone is the hub: what it takes in is stamped with its own time too, so it reaches every other
        // helper; helpers keep the time as sent, so nothing bounces back and forth
        val now = System.currentTimeMillis()
        fun t(u: Long) = if (hub) maxOf(u, now) else u
        o.optJSONArray("medicines")?.let { a ->
            for (i in 0 until a.length()) {
                val m = medFrom(a.getJSONObject(i))
                val old = db.medicines().byUid(m.uid)
                if (old == null) db.medicines().insert(m.copy(id = 0, updatedAt = t(m.updatedAt)))
                else if (m.updatedAt > old.updatedAt) db.medicines().update(m.copy(id = old.id, photoPath = old.photoPath, updatedAt = t(m.updatedAt)))
            }
        }
        val medId = db.medicines().all().associate { it.uid to it.id }
        o.optJSONArray("doses")?.let { a ->
            for (i in 0 until a.length()) {
                val j = a.getJSONObject(i)
                val mid = medId[j.optString("med")] ?: continue
                val d = doseFrom(j, mid)
                // by id; else the same dose (medicine and time) kept under another id: both phones settle on the smaller id
                val old = db.doses().byUid(d.uid) ?: db.doses().at(mid, d.scheduledAt)
                if (old == null) runCatching { db.doses().insert(d.copy(id = 0, updatedAt = t(d.updatedAt))) }
                else if (d.updatedAt > old.updatedAt) db.doses().update(d.copy(id = old.id, uid = minOf(old.uid, d.uid), updatedAt = t(d.updatedAt)))
                else if (d.uid < old.uid) db.doses().update(old.copy(uid = d.uid))
            }
        }
        o.optJSONArray("notes")?.let { a ->
            val groups = ArrayList<Pair<Long, String>>()   // the note here to its group's uid
            for (i in 0 until a.length()) {
                val j = a.getJSONObject(i)
                // groups are linked by shared id; an older phone sends only its own row number, which means nothing here
                val n = noteFrom(j).copy(groupId = null)
                // by id; else the same entry kept here under another id (entries that lost their id before 2.13 were given a
                // new one on each phone, and came back as copies): both phones settle on the smaller id. Removals match by id only.
                val old = db.notes().byUid(n.uid) ?: if (n.deletedAt != null) null else db.notes().sameEntry(n.kind, n.problemId.orEmpty(), n.occurredAt, n.createdAt, n.text)
                val here = old?.id ?: db.notes().insert(n.copy(id = 0, updatedAt = t(n.updatedAt)))
                j.optString("gu").takeIf { it.isNotBlank() }?.let { groups += here to it }
                if (old == null) Unit
                else if (n.updatedAt > old.updatedAt) db.notes().update(n.copy(id = old.id, uid = minOf(old.uid, n.uid), groupId = old.groupId, audioPath = old.audioPath, photoPath = old.photoPath, updatedAt = t(n.updatedAt)))
                else if (n.uid < old.uid) db.notes().update(old.copy(uid = n.uid))
            }
            for ((here, gu) in groups) {
                val n = db.notes().get(here) ?: continue
                val g = db.notes().byUid(gu)?.id ?: continue
                if (n.groupId != g) db.notes().update(n.copy(groupId = g))
            }
        }
        o.optJSONObject("profile")?.let { j ->
            val pr = profileFrom(j)
            val old = db.profile().get()
            if (old == null || pr.updatedAt > old.updatedAt) { db.profile().put(pr); db.profile().setUpdated(t(pr.updatedAt)) }
        }
    }

    // ───────── packing ─────────

    private fun noteJson(n: Note) = JSONObject().put("uid", n.uid).put("u", n.updatedAt).put("kind", n.kind).put("problemId", n.problemId ?: "").put("at", n.occurredAt)
        .put("created", n.createdAt).put("transcript", n.transcript ?: "").put("details", n.details).put("severity", n.severity ?: -1).put("count", n.count ?: -1)
        .put("triage", n.triage).put("reasons", n.triageReasons).put("group", n.groupId ?: -1).put("deleted", n.deletedAt ?: 0).put("text", n.text)
    private fun noteFrom(j: JSONObject) = Note(uid = j.getString("uid"), updatedAt = j.getLong("u"), kind = j.getString("kind"), problemId = j.optString("problemId").ifBlank { null },
        occurredAt = j.getLong("at"), createdAt = j.optLong("created"), transcript = j.optString("transcript").ifBlank { null }, details = j.optString("details", "{}"),
        severity = j.optInt("severity", -1).takeIf { it >= 0 }, count = j.optInt("count", -1).takeIf { it >= 0 }, triage = j.optString("triage", "GREEN"),
        triageReasons = j.optString("reasons"), groupId = j.optLong("group", -1).takeIf { it >= 0 }, deletedAt = j.optLong("deleted").takeIf { it > 0 }, text = j.optString("text"))

    private fun medJson(m: Medicine) = JSONObject().put("uid", m.uid).put("u", m.updatedAt).put("name", m.name).put("strength", m.strength).put("form", m.form).put("amount", m.amount)
        .put("food", m.food).put("times", m.times).put("days", m.days).put("start", m.startDate).put("end", m.endDate ?: 0).put("critical", m.critical).put("asNeeded", m.asNeeded)
        .put("gap", m.minGapHours).put("purpose", m.purpose).put("left", m.pillsLeft ?: -1.0).put("active", m.active).put("thinner", m.bloodThinner).put("changed", m.changedAt)
        .put("note", m.changeNote).put("shape", m.shape).put("color", m.color)
    private fun medFrom(j: JSONObject) = Medicine(uid = j.getString("uid"), updatedAt = j.getLong("u"), name = j.getString("name"), strength = j.optString("strength"), form = j.optString("form", "tablet"),
        amount = j.optString("amount", "1"), food = j.optString("food", "any"), times = j.optString("times"), days = j.optString("days"), startDate = j.optLong("start"),
        endDate = j.optLong("end").takeIf { it > 0 }, critical = j.optBoolean("critical"), asNeeded = j.optBoolean("asNeeded"), minGapHours = j.optInt("gap", 4), purpose = j.optString("purpose"),
        pillsLeft = j.optDouble("left", -1.0).takeIf { it >= 0 }, active = j.optBoolean("active", true), bloodThinner = j.optBoolean("thinner"), changedAt = j.optLong("changed"),
        changeNote = j.optString("note"), shape = j.optString("shape"), color = j.optString("color"))

    private fun doseJson(d: Dose, medUid: String) = JSONObject().put("uid", d.uid).put("u", d.updatedAt).put("med", medUid).put("at", d.scheduledAt).put("status", d.status)
        .put("acted", d.actedAt ?: 0).put("reason", d.reason ?: "")
    private fun doseFrom(j: JSONObject, medId: Long) = Dose(uid = j.getString("uid"), updatedAt = j.getLong("u"), medicineId = medId, scheduledAt = j.getLong("at"),
        status = j.optString("status", DoseStatus.DUE), actedAt = j.optLong("acted").takeIf { it > 0 }, reason = j.optString("reason").ifBlank { null })

    private fun profileJson(p: Profile) = JSONObject().put("u", p.updatedAt).put("name", p.name).put("dob", p.dob).put("sex", p.sex).put("blood", p.bloodGroup).put("hospitalId", p.hospitalId)
        .put("conditions", p.conditions).put("allergies", p.allergies).put("doctorName", p.doctorName).put("doctorPhone", p.doctorPhone).put("thinner", p.onBloodThinner)
        .put("notes", p.notes).put("plan", p.plan)
    private fun profileFrom(j: JSONObject) = Profile(updatedAt = j.getLong("u"), name = j.optString("name"), dob = j.optString("dob"), sex = j.optString("sex"), bloodGroup = j.optString("blood"),
        hospitalId = j.optString("hospitalId"), conditions = j.optString("conditions"), allergies = j.optString("allergies"), doctorName = j.optString("doctorName"),
        doctorPhone = j.optString("doctorPhone"), onBloodThinner = j.optBoolean("thinner"), notes = j.optString("notes"), plan = j.optString("plan"))

    private fun zip(s: String): String {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(s.toByteArray()) }
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }
    internal fun unzip(s: String): String = GZIPInputStream(Base64.decode(s, Base64.NO_WRAP).inputStream()).bufferedReader().readText()
}

/** A helper's phone keeps one copy of each person's records, in its own encrypted file. */
object Mirror {
    private val dbs = HashMap<String, MedDb>()
    private val repos = HashMap<String, Repo>()

    @Synchronized
    fun db(ctx: Context, pairId: String): MedDb = dbs.getOrPut(pairId) {
        MedDb.open(ctx.applicationContext, "mirror_${pairId.filter { it.isLetterOrDigit() }}.db").also { Sync.watch(ctx, it) }
    }

    @Synchronized
    fun repo(ctx: Context, pairId: String): Repo = repos.getOrPut(pairId) { ctx.medlog.repoFor(db(ctx, pairId)) }
}

/**
 * Whose records the screens show. Null: this phone's own. A pairing id: the person this helper is looking after,
 * from the copy kept here (changes go back to their phone). Background work (alarms, reminders) always uses the
 * phone's own records; only the screens follow this.
 */
object Viewing {
    val pairId = MutableStateFlow<String?>(null)
}

/** The 15-minute safety net: sends whatever hasn't reached the other phones yet. Usually there's nothing to do. */
class SyncWorker(ctx: Context, params: androidx.work.WorkerParameters) : androidx.work.CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result { runCatching { Sync.push(applicationContext) }; return Result.success() }
}

/** Taking, undoing or skipping a dose from a screen, on whichever records the screen shows. */
object Doses {
    private fun mirror() = Viewing.pairId.value
    /** Taken, now or at [at] (noted afterwards); on a dose already taken, [at] just changes the time. */
    suspend fun take(ctx: Context, id: Long, at: Long? = null) {
        if (mirror() == null) { com.suryaprakash.medlog.meds.Scheduler.take(ctx, id, at = at); return }
        val db = ctx.medlog.viewDb
        db.doses().get(id)?.let { db.doses().update(it.copy(status = DoseStatus.TAKEN, actedAt = at ?: it.actedAt?.takeIf { _ -> it.status == DoseStatus.TAKEN } ?: System.currentTimeMillis(), snoozeUntil = null)) }
    }
    suspend fun untake(ctx: Context, id: Long) {
        if (mirror() == null) { com.suryaprakash.medlog.meds.Scheduler.untake(ctx, id); return }
        val db = ctx.medlog.viewDb
        db.doses().get(id)?.let { db.doses().update(it.copy(status = DoseStatus.DUE, actedAt = null)) }
    }
    /** An extra feed, outside the feed's times: given now or at [at]. */
    suspend fun extra(ctx: Context, medicineId: Long, at: Long = System.currentTimeMillis()) {
        val db = ctx.medlog.viewDb
        db.doses().insert(Dose(medicineId = medicineId, scheduledAt = at, status = DoseStatus.TAKEN, actedAt = at, reason = EXTRA_FEED))
        if (mirror() == null) ctx.medlog.refreshWidgets()
    }
    /** Takes back an extra feed noted by mistake. */
    suspend fun unextra(ctx: Context, id: Long) {
        val db = ctx.medlog.viewDb
        db.doses().get(id)?.let { db.doses().update(it.copy(status = DoseStatus.SKIPPED, reason = EXTRA_REMOVED)) }
    }
    suspend fun skip(ctx: Context, id: Long, reason: String) {
        if (mirror() == null) { com.suryaprakash.medlog.meds.Scheduler.skip(ctx, id, reason); return }
        val db = ctx.medlog.viewDb
        db.doses().get(id)?.let { db.doses().update(it.copy(status = DoseStatus.SKIPPED, actedAt = System.currentTimeMillis(), reason = reason)) }
    }
}
