package com.suryaprakash.medlog.sync

import org.json.JSONObject

/**
 * A map-based [SyncStore] used by the tests. It is also the spec for the Room one: the local* methods do what the SQLite
 * triggers will do (stamp at, by, origin, oseq; only the columns that changed get the new version), and [apply] is the "applying"
 * write that never stamps. Merging is [Merge], the same as [SqlSyncStore].
 */
class InMemorySyncStore(override val device: String, private val now: () -> Long) : SyncStore {
    private class Entry(var version: Version, var row: JSONObject?, var cols: MutableMap<String, Version>)

    private val rows = HashMap<Pair<String, String>, Entry>()
    private val seen = HashMap<String, Long>()
    private var seq = 0L
    private var hlc = 0L
    private fun stamp() = maxOf(now(), hlc + 1)

    override val localSeq: Long get() = seq

    fun localUpsert(tbl: String, uid: String, row: JSONObject) {
        val old = rows[tbl to uid]
        val live = old != null && !old.version.del && old.row != null
        val changed = row.keys().asSequence().filter { !live || !same(old!!.row!!.opt(it), row.opt(it)) || it !in old.cols }.toList()
        if (live && changed.isEmpty()) return // like the trigger: nothing changed, nothing stamped
        val t = stamp(); seq++
        val v = Version(t, device, device, seq, false)
        val cols = if (live) old!!.cols.toMutableMap() else HashMap()
        for (c in changed) cols[c] = v
        rows[tbl to uid] = Entry(v, JSONObject(row.toString()), cols)
        raise(device, seq)
    }

    fun localDelete(tbl: String, uid: String) {
        if (rows[tbl to uid]?.version?.del != false) return // nothing live to delete
        val t = stamp(); seq++
        rows[tbl to uid] = Entry(Version(t, device, device, seq, true), null, HashMap())
        raise(device, seq)
    }

    /** The live row, or null when missing or deleted. */
    fun row(tbl: String, uid: String): JSONObject? = rows[tbl to uid]?.row?.let { JSONObject(it.toString()) }

    /** Everything that matters for equality between phones: versions and rows, in a fixed order. */
    fun snapshot(): String = rows.entries.sortedWith(compareBy({ it.key.first }, { it.key.second })).joinToString("\n") { (k, e) ->
        "${k.first}/${k.second} ${e.version} ${e.row?.let { canonical(it) } ?: "-"} ${e.cols.toSortedMap()}"
    }

    override fun changedSince(have: Map<String, Long>): List<Op> =
        rows.entries.flatMap { (k, e) -> Merge.opsFor(k.first, k.second, e.version, e.cols, e.row, have) }.sortedBy { it.oseq }

    override fun version(tbl: String, uid: String): Version? = rows[tbl to uid]?.version
    override fun knows(tbl: String, uid: String): Boolean = rows.containsKey(tbl to uid)
    override fun have(): Map<String, Long> = HashMap(seen)

    override fun apply(ops: List<Op>): Applied {
        var applied = 0; var skipped = 0
        val tables = LinkedHashSet<String>(); val written = ArrayList<Op>()
        for (o in ops) {
            hlc = maxOf(hlc, o.top.at)
            val cur = rows[o.tbl to o.uid]
            val names = o.row?.keys()?.asSequence()?.toList().orEmpty()
            val plan = Merge.plan(cur?.version, cur?.cols ?: emptyMap(), o, names)
            if (plan == null) { skipped++; continue }
            when {
                plan.delete -> rows[o.tbl to o.uid] = Entry(plan.rowVersion, null, HashMap())
                plan.create -> rows[o.tbl to o.uid] = Entry(plan.rowVersion, JSONObject(o.row.toString()), names.associateWith { o.colVersion(it) }.toMutableMap())
                else -> {
                    cur!!.version = plan.rowVersion
                    for (c in plan.take) { cur.row!!.put(c, o.row!!.opt(c)); cur.cols[c] = o.colVersion(c) }
                }
            }
            applied++; tables += o.tbl; written += if (o.del) o else o.copy(row = JSONObject(rows[o.tbl to o.uid]!!.row.toString()))
        }
        return Applied(applied, skipped, 0, tables, written)
    }

    override fun advanceHave(origin: String, seq: Long) = raise(origin, seq)

    private fun raise(origin: String, n: Long) { if (n > (seen[origin] ?: 0L)) seen[origin] = n }

    private fun same(a: Any?, b: Any?) = (a ?: JSONObject.NULL).toString() == (b ?: JSONObject.NULL).toString()

    private fun canonical(j: JSONObject): String =
        j.keys().asSequence().sorted().joinToString(",", "{", "}") { "$it:${j.get(it)}" }
}
