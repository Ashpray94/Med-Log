package com.suryaprakash.medlog.sync

import org.json.JSONObject

/**
 * A map-based [SyncStore] used by the tests. It is also the spec for the Room one: the local* methods do what the SQLite
 * triggers will do (stamp at, by, origin, oseq), and [apply] is the "applying" write that never stamps.
 */
class InMemorySyncStore(override val device: String, private val now: () -> Long) : SyncStore {
    private class Entry(var version: Version, var row: JSONObject?)

    private val rows = HashMap<Pair<String, String>, Entry>()
    private val seen = HashMap<String, Long>()
    private var seq = 0L

    override val localSeq: Long get() = seq

    fun localUpsert(tbl: String, uid: String, row: JSONObject) {
        val t = now(); seq++
        rows[tbl to uid] = Entry(Version(t, device, device, seq, false), JSONObject(row.toString()))
        raise(device, seq)
    }

    fun localDelete(tbl: String, uid: String) {
        if (rows[tbl to uid]?.version?.del != false) return // nothing live to delete
        val t = now(); seq++
        rows[tbl to uid] = Entry(Version(t, device, device, seq, true), null)
        raise(device, seq)
    }

    /** The live row, or null when missing or deleted. */
    fun row(tbl: String, uid: String): JSONObject? = rows[tbl to uid]?.row?.let { JSONObject(it.toString()) }

    /** Everything that matters for equality between phones: versions and rows, in a fixed order. */
    fun snapshot(): String = rows.entries.sortedWith(compareBy({ it.key.first }, { it.key.second })).joinToString("\n") { (k, e) ->
        "${k.first}/${k.second} ${e.version} ${e.row?.let { canonical(it) } ?: "-"}"
    }

    override fun changedSince(have: Map<String, Long>): List<Op> =
        rows.entries.filter { (_, e) -> e.version.oseq > (have[e.version.origin] ?: 0L) }
            .map { (k, e) -> Op(k.first, k.second, e.version.origin, e.version.oseq, e.version.at, e.version.by, e.version.del, e.row?.let { JSONObject(it.toString()) }) }

    override fun version(tbl: String, uid: String): Version? = rows[tbl to uid]?.version
    override fun knows(tbl: String, uid: String): Boolean = rows.containsKey(tbl to uid)
    override fun have(): Map<String, Long> = HashMap(seen)

    override fun apply(ops: List<Op>): Applied {
        var applied = 0; var skipped = 0
        for (o in ops) {
            val cur = rows[o.tbl to o.uid]
            if (cur == null || o.version > cur.version) {
                rows[o.tbl to o.uid] = Entry(o.version, if (o.del) null else o.row?.let { JSONObject(it.toString()) })
                applied++
            } else skipped++
        }
        return Applied(applied, skipped, 0)
    }

    override fun advanceHave(origin: String, seq: Long) = raise(origin, seq)

    private fun raise(origin: String, n: Long) { if (n > (seen[origin] ?: 0L)) seen[origin] = n }

    private fun canonical(j: JSONObject): String =
        j.keys().asSequence().sorted().joinToString(",", "{", "}") { "$it:${j.get(it)}" }
}
