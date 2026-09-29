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

/** One changed row, as it travels. A delete has [del] = true and no [row]. Foreign keys inside [row] are uids. */
data class Op(
    val tbl: String, val uid: String, val origin: String, val oseq: Long,
    val at: Long, val by: String, val del: Boolean, val row: JSONObject?,
) {
    val version: Version get() = Version(at, by, origin, oseq, del)
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
