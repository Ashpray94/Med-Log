package com.suryaprakash.medlog.sync

import org.json.JSONObject

/** A child table whose rows carry the uid of a parent row in [field]. The child waits until the parent is known. */
data class ParentRule(val childTbl: String, val field: String, val parentTbl: String)

/**
 * Merges what other phones send and tells them what this phone changed.
 *
 * Merge rule: last write wins per column (see [Merge]), by (at, by) with the device id as tie-break (see [Version.compareTo]). Deletes are
 * tombstones that take part in the same comparison, so a later edit brings a deleted row back and a later delete removes it,
 * the same on every phone. The same set of ops in any order gives the same state.
 *
 * Catch-up: [hello] says what this phone has; any phone that hears it answers with everything newer, whichever phone made it.
 */
class SyncEngine(
    private val store: SyncStore,
    private val sender: SyncSender,
    private val parents: List<ParentRule> = DEFAULT_PARENTS,
    private val maxBatchBytes: Int = WireJson.MAX_BATCH_BYTES,
    private val clock: () -> Long = System::currentTimeMillis,
    /** At most one gap-triggered HELLO per origin in this time. Tests pass 0. */
    private val gapHelloIntervalMs: Long = 60_000L,
) {
    private val pending = ArrayList<Op>()
    // ranges received out of order, per origin, waiting for the gap before them to be filled: (from, to]
    private val ranges = HashMap<String, MutableList<Pair<Long, Long>>>()
    private val lastGapHello = HashMap<String, Long>()
    val pendingCount: Int get() = pending.size

    /** This phone's own changes with a number above [sinceMine], as messages ready to send (each at most 200 KB, each covering a contiguous piece of the range). */
    fun localChanges(sinceMine: Long): List<String> {
        val to = store.localSeq
        if (to <= sinceMine) return emptyList()
        val mine = store.changedSince(mapOf(store.device to sinceMine)).filter { it.origin == store.device && it.oseq > sinceMine && it.oseq <= to }
        return WireJson.batches(store.device, store.device, mine, sinceMine, to, maxBatchBytes)
    }

    /** Sends this phone's changes above [sinceMine]. Returns how many messages went out. */
    fun publishLocal(sinceMine: Long): Int = localChanges(sinceMine).onEach { sender.send(it) }.size

    /**
     * The HELLO message: what this phone completely has from each device. Send it when listening starts, every 6 hours (the
     * caller does this) and, debounced, when a gap is seen. Pass [full] to ask for everything (first pairing only).
     */
    fun hello(full: Boolean = false): String = WireJson.hello(store.device, if (full) emptyMap() else store.have())

    /** Handles one message from the relay. Bad or unknown messages are ignored. */
    fun onMessage(json: String): Applied {
        val j = try { JSONObject(json) } catch (e: Exception) { return Applied.NONE }
        val from = j.optString("from")
        if (from == store.device) return Applied.NONE // our own echo
        return when (j.optString("kind")) {
            "ops" -> receive(WireJson.readOps(j), WireJson.readCovers(j))
            "hello" -> { answer(WireJson.readHave(j)); Applied.NONE }
            else -> Applied.NONE
        }
    }

    // TODO: every phone answers every HELLO, so a requester can get several identical answers. They are harmless (merging is
    // idempotent); the relay layer could wait a random moment and skip its answer if someone else's already came.
    private fun answer(requested: Map<String, Long>) {
        val mine = store.have()
        for (origin in (mine.keys + store.device).sorted()) {
            val from = requested[origin] ?: 0L
            val to = if (origin == store.device) store.localSeq else mine[origin] ?: 0L
            if (to <= from) continue
            val ops = store.changedSince(mapOf(origin to from)).filter { it.origin == origin && it.oseq > from && it.oseq <= to }
            WireJson.batches(store.device, origin, ops, from, to, maxBatchBytes).forEach { sender.send(it) }
        }
    }

    private fun receive(incoming: List<Op>, covers: Map<String, Pair<Long, Long>>): Applied {
        val ready = ArrayList<Op>()
        var total = Applied.NONE
        // ops in this same batch count as known parents, so a batch may hold a medicine and its doses in any order
        val inBatch = HashSet<String>()
        for (o in incoming) inBatch += key(o.tbl, o.uid)
        for (o in incoming) if (canApply(o, inBatch)) ready += o else park(o)
        // parents go first inside the batch so the store can write them before their children
        total += store.apply(ready.sortedBy { rank(it.tbl) })
        // a batch may have brought a parent; retry what was waiting until nothing more moves
        while (pending.isNotEmpty()) {
            val now = pending.filter { canApply(it, emptySet()) }
            if (now.isEmpty()) break
            pending.removeAll(now.toSet())
            total += store.apply(now.sortedBy { rank(it.tbl) })
        }
        settle(covers)
        return total.copy(parked = pending.size)
    }

    /**
     * Moves have only over complete ranges. A range that starts at or below have extends it; one that starts above leaves a gap
     * and is kept until the gap is filled. Have never passes the row just before the lowest parked op of that origin.
     */
    private fun settle(covers: Map<String, Pair<Long, Long>>) {
        for ((o, r) in covers) if (r.second > r.first) ranges.getOrPut(o) { ArrayList() } += r
        val have = store.have()
        for ((origin, list) in ranges.entries.toList()) {
            val start = have[origin] ?: 0L
            val cap = pending.filter { it.origin == origin }.minOfOrNull { it.oseq - 1 } ?: Long.MAX_VALUE
            var cur = start
            var moved = true
            while (moved) {
                moved = false
                for (r in list) if (r.first <= cur && r.second > cur && cur < cap) { cur = minOf(r.second, cap); moved = true }
            }
            if (cur > start) store.advanceHave(origin, cur)
            list.removeAll { it.second <= cur }
            if (list.isEmpty()) ranges.remove(origin)
            else if (list.any { it.first > cur }) askForGap(origin)
        }
    }

    private fun askForGap(origin: String) {
        val now = clock()
        val last = lastGapHello[origin]
        if (last != null && now - last < gapHelloIntervalMs) return
        lastGapHello[origin] = now
        sender.send(hello())
    }

    private fun park(o: Op) {
        // a newer copy of the same row from the same phone replaces an older parked one (another phone's copy may hold other columns)
        val i = pending.indexOfFirst { it.tbl == o.tbl && it.uid == o.uid && it.origin == o.origin }
        if (i < 0) pending += o else if (o.version > pending[i].version) pending[i] = o
    }

    private fun canApply(o: Op, inBatch: Set<String>): Boolean {
        val row = o.row
        if (o.del || row == null) return true
        for (r in parents) {
            if (r.childTbl != o.tbl) continue
            if (row.isNull(r.field)) continue
            val pu = row.optString(r.field, "")
            if (pu.isEmpty()) continue
            if (!store.knows(r.parentTbl, pu) && key(r.parentTbl, pu) !in inBatch) return false
        }
        return true
    }

    private fun rank(tbl: String) = if (parents.any { it.childTbl == tbl }) 1 else 0
    private fun key(t: String, u: String) = "$t\u0000$u"

    companion object {
        val DEFAULT_PARENTS = listOf(
            ParentRule("doses", "medicineUid", "medicines"),
            ParentRule("notes", "groupUid", "notes"),
        )
    }
}
