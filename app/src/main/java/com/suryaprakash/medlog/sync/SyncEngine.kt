package com.suryaprakash.medlog.sync

import org.json.JSONObject

/** A child table whose rows carry the uid of a parent row in [field]. The child waits until the parent is known. */
data class ParentRule(val childTbl: String, val field: String, val parentTbl: String)

/**
 * Merges what other phones send and tells them what this phone changed.
 *
 * Merge rule: last write wins per row, by (at, by) with the device id as tie-break (see [Version.compareTo]). Deletes are
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
) {
    private val pending = ArrayList<Op>()
    val pendingCount: Int get() = pending.size

    /** This phone's own changes with a number above [sinceMine], as messages ready to send (each at most 200 KB). */
    fun localChanges(sinceMine: Long): List<String> {
        val mine = store.changedSince(mapOf(store.device to sinceMine)).filter { it.origin == store.device && it.oseq > sinceMine }
        return WireJson.batches(store.device, mine.sortedBy { it.oseq }, maxBatchBytes)
    }

    /** Sends this phone's changes above [sinceMine]. Returns how many messages went out. */
    fun publishLocal(sinceMine: Long): Int = localChanges(sinceMine).onEach { sender.send(it) }.size

    /** The HELLO message: what this phone has from each device. Pass [full] to ask for everything (a slow safety net). */
    fun hello(full: Boolean = false): String = WireJson.hello(store.device, if (full) emptyMap() else store.have())

    /** Handles one message from the relay. Bad or unknown messages are ignored. */
    fun onMessage(json: String): Applied {
        val j = try { JSONObject(json) } catch (e: Exception) { return Applied.NONE }
        val from = j.optString("from")
        if (from == store.device) return Applied.NONE // our own echo
        return when (j.optString("kind")) {
            "ops" -> receive(WireJson.readOps(j))
            "hello" -> { answer(WireJson.readHave(j)); Applied.NONE }
            else -> Applied.NONE
        }
    }

    private fun answer(have: Map<String, Long>) {
        val ops = store.changedSince(have)
        if (ops.isEmpty()) return
        WireJson.batches(store.device, ops.sortedWith(compareBy({ it.origin }, { it.oseq })), maxBatchBytes).forEach { sender.send(it) }
    }

    private fun receive(incoming: List<Op>): Applied {
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
        return total.copy(parked = pending.size)
    }

    private fun park(o: Op) {
        // a newer copy of the same row replaces an older parked one
        val i = pending.indexOfFirst { it.tbl == o.tbl && it.uid == o.uid }
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
