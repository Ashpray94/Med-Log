package com.suryaprakash.medlog.sync

import org.json.JSONObject

// The parts of the two-way sharing engine that every phone must agree on. Pure Kotlin (org.json and the stdlib only), so the
// same code is tested on the JVM and used on the phone.

/**
 * Which version of a shared row a phone holds. [origin] is the phone that made this version and [oseq] is that phone's running
 * number for it. [at] is the wall-clock time of the edit and [by] the phone that made it (normally the same as [origin]).
 */
data class Version(val at: Long, val by: String, val origin: String, val oseq: Long, val del: Boolean) {
    /**
     * The merge rule. A version wins when its (at, by) is greater: time first, then device id as the tie-break. Origin and oseq
     * are only compared after that, so two different versions never compare equal and every phone picks the same winner.
     * Only an identical version compares equal, and then nothing needs to change.
     */
    operator fun compareTo(o: Version): Int {
        if (at != o.at) return at.compareTo(o.at)
        if (by != o.by) return by.compareTo(o.by)
        if (origin != o.origin) return origin.compareTo(o.origin)
        if (oseq != o.oseq) return oseq.compareTo(o.oseq)
        return del.compareTo(o.del)
    }
}

/**
 * One changed row, as it travels. A delete has [del] = true and no [row]. Foreign keys inside [row] are uids.
 * Every column has its own version (per-column last write wins, see [Merge]): the op's own version is the version of the columns not
 * listed in [cv]; [cv] holds the others (a row edited twice by two phones has columns of different ages). A peer that sends no [cv]
 * (an older app) simply gives every column the op's version.
 */
data class Op(
    val tbl: String, val uid: String, val origin: String, val oseq: Long,
    val at: Long, val by: String, val del: Boolean, val row: JSONObject?,
    val cv: Map<String, Version> = emptyMap(),
) {
    val version: Version get() = Version(at, by, origin, oseq, del)
    /** The version of column [c] (its wire name) in this op. */
    fun colVersion(c: String): Version = cv[c] ?: version.copy(del = false)
    /** The newest version of anything in this op: what the row's own version becomes when the op is merged. */
    val top: Version get() = if (del) version else cv.values.fold(version) { a, b -> if (b > a) b else a }
}

/**
 * The merge rule, shared by [InMemorySyncStore] and [SqlSyncStore] so both do the same.
 * A live row is merged column by column: a column of the op is taken when its version is greater than the held column's. The row's own
 * version is the newest of all its columns' (and of its delete). A delete wins only when it is newer than every column (that is, than the
 * row's version); an edit newer than a delete brings the row back with the op's columns.
 * Known limit: a delete D, an edit X older than D and a stale copy E newer than D can arrive in different orders on different phones; the
 * phones then can differ in the columns X and E both changed (the deleted row's column values are not kept, only its tombstone).
 */
object Merge {
    /** [delete]: remove the row. [create]: the row is new (or was deleted): write every column. Else write the [take] columns. [rowVersion] is the row's new version. */
    class Plan(val delete: Boolean, val create: Boolean, val take: List<String>, val rowVersion: Version)

    /** [cols] are the wire names of the columns present in the op's row. Null = the op changes nothing here. */
    fun plan(cur: Version?, curCols: Map<String, Version>, o: Op, cols: List<String>): Plan? {
        if (o.del) return if (cur == null || o.version > cur) Plan(true, false, emptyList(), o.version) else null
        val top = o.top
        if (cur == null || cur.del) return if (cur == null || top > cur) Plan(false, true, cols, top) else null
        val held = cur.copy(del = false)
        val take = cols.filter { c -> o.colVersion(c) > (curCols[c] ?: held) }
        val nv = if (top > cur) top else cur
        return if (take.isEmpty() && nv == cur) null else Plan(false, false, take, nv)
    }

    /**
     * The ops a phone sends for one row: for each origin that has something newer than [have] in the row (the row's own version or a
     * column's), one op numbered with that origin's highest number there, carrying the whole row and every column's version. So "every
     * current change of origin X above n" stays true also for a row whose columns come from several phones.
     */
    fun opsFor(tbl: String, uid: String, rv: Version, cols: Map<String, Version>, row: JSONObject?, have: Map<String, Long>): List<Op> {
        if (rv.del) return if (rv.oseq > (have[rv.origin] ?: 0L)) listOf(Op(tbl, uid, rv.origin, rv.oseq, rv.at, rv.by, true, null)) else emptyList()
        if (row == null) return emptyList()
        val best = HashMap<String, Version>()
        for (v in cols.values + rv) if (v.oseq > (have[v.origin] ?: 0L)) best.merge(v.origin, v) { a, b -> if (b.oseq > a.oseq) b else a }
        return best.values.sortedBy { it.oseq }.map { v ->
            Op(tbl, uid, v.origin, v.oseq, v.at, v.by, false, JSONObject(row.toString()), cols.filterValues { it != v.copy(del = false) })
        }
    }
}

/** What happened to a batch: written, ignored because the phone already had the same or a newer version, or waiting for a parent row. */
data class Applied(val applied: Int, val skipped: Int, val parked: Int, val tables: Set<String> = emptySet(), val written: List<Op> = emptyList()) {
    /**
     * [tables] are the tables whose rows were really written (so the phone can, for example, plan alarms again after "medicines" or "doses").
     * [written] are those ops themselves, so the phone can look at the new state of each row (a dose now TAKEN, a medicine now inactive).
     */
    operator fun plus(o: Applied) = Applied(applied + o.applied, skipped + o.skipped, parked + o.parked, tables + o.tables, written + o.written)
    companion object { val NONE = Applied(0, 0, 0) }
}

/**
 * What the engine needs from a phone's database. Room implements it; [InMemorySyncStore] is the spec.
 * Every method must be safe to call from one thread at a time (the engine does not lock).
 */
interface SyncStore {
    /** This phone's id. */
    val device: String
    /** Every row (live or deleted) whose (origin, oseq) is newer than [have]; an origin missing from [have] counts as 0. */
    fun changedSince(have: Map<String, Long>): List<Op>
    /** The version held for a row, or null if the row was never seen. A deleted row still has a version. */
    fun version(tbl: String, uid: String): Version?
    /** True when the row has been seen at all, live or deleted. Used to decide if a child row may be written yet. */
    fun knows(tbl: String, uid: String): Boolean
    /**
     * Writes the ops in one transaction with "applying" set, so nothing echoes back. Each op replaces the row only if its version
     * is greater than the held one (see [Version.compareTo]). It does NOT touch have: the engine moves have only over ranges
     * it knows are complete (see [advanceHave]). Returns applied and skipped counts (parked is 0 here; the engine fills it in).
     */
    fun apply(ops: List<Op>): Applied
    /**
     * For each origin, the number up to which this phone holds EVERY current row of that origin (a contiguous claim, not just the
     * highest number seen). For this phone's own origin it is [localSeq]. Persisted (sync_have).
     */
    fun have(): Map<String, Long>
    /** Raises have[origin] to at least [seq]. Never lowers it. */
    fun advanceHave(origin: String, seq: Long)
    /** The last local number handed out on this phone. */
    val localSeq: Long
}

/** Sends one wire message (a JSON string) to the family mailbox. */
fun interface SyncSender { fun send(json: String) }
