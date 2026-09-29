package com.suryaprakash.medlog.sync

import org.json.JSONObject

/**
 * The little bit of SQL access [SqlSyncStore] needs. Values are Long, Double, String or null. The phone implements it over
 * SupportSQLiteDatabase (see [RoomSyncStore]); the JVM tests implement it over JDBC, so the same mapping code runs in both.
 */
interface SqlDb {
    fun query(sql: String, args: List<Any?> = emptyList()): List<Map<String, Any?>>
    fun exec(sql: String, args: List<Any?> = emptyList())
    /** Runs [block] in one transaction (commit on return, roll back on exception). May be nested. */
    fun <T> transaction(block: () -> T): T
}

/**
 * A [SyncStore] over the real tables. Behaves like [InMemorySyncStore]: versions live in sync_rows, and apply() writes with
 * sync_state.applying = '1' so the triggers stay silent.
 */
open class SqlSyncStore(private val db: SqlDb) : SyncStore {
    override val device: String = run {
        var d = db.query("SELECT v FROM sync_state WHERE k = 'device'").firstOrNull()?.get("v") as String?
        if (d == null) { for (s in SyncSql.seedState()) db.exec(s); d = db.query("SELECT v FROM sync_state WHERE k = 'device'").first()["v"] as String }
        d
    }

    override val localSeq: Long get() = (db.query("SELECT v FROM sync_state WHERE k = 'seq'").firstOrNull()?.get("v") as String?)?.toLongOrNull() ?: 0L

    private fun version(r: Map<String, Any?>) = Version(r["at"] as Long, r["by"] as String, r["origin"] as String, r["oseq"] as Long, (r["del"] as Long) != 0L)

    override fun version(tbl: String, uid: String): Version? =
        db.query("SELECT origin, oseq, at, `by`, del FROM sync_rows WHERE tbl = ? AND uid = ?", listOf(tbl, uid)).firstOrNull()?.let(::version)

    override fun knows(tbl: String, uid: String): Boolean =
        db.query("SELECT 1 AS x FROM sync_rows WHERE tbl = ? AND uid = ?", listOf(tbl, uid)).isNotEmpty()

    override fun have(): Map<String, Long> {
        val out = HashMap<String, Long>()
        for (r in db.query("SELECT origin, seq FROM sync_have")) out[r["origin"] as String] = r["seq"] as Long
        out[device] = localSeq
        return out
    }

    override fun advanceHave(origin: String, seq: Long) {
        if (origin == device) return // our own number is localSeq
        db.exec("INSERT INTO sync_have(origin, seq) VALUES(?, ?) ON CONFLICT(origin) DO UPDATE SET seq = excluded.seq WHERE excluded.seq > sync_have.seq", listOf(origin, seq))
    }

    override fun changedSince(have: Map<String, Long>): List<Op> {
        val args = ArrayList<Any?>()
        val where = if (have.isEmpty()) "1" else {
            val per = have.entries.joinToString(" OR ") { args += it.key; args += it.value; "(origin = ? AND oseq > ?)" }
            args.addAll(have.keys)
            "$per OR origin NOT IN (${have.keys.joinToString(",") { "?" }})"
        }
        val rows = db.query("SELECT tbl, uid, origin, oseq, at, `by`, del FROM sync_rows WHERE $where ORDER BY oseq, tbl, uid", args)
        val out = ArrayList<Op>()
        for (r in rows) {
            val tbl = r["tbl"] as String; val uid = r["uid"] as String
            val v = version(r)
            if (v.del) { out += Op(tbl, uid, v.origin, v.oseq, v.at, v.by, true, null); continue }
            val row = readRow(tbl, uid) ?: continue // a live version whose row is gone (should not happen): nothing to send
            out += Op(tbl, uid, v.origin, v.oseq, v.at, v.by, false, row)
        }
        return out
    }

    /** The shared columns of one row as JSON, with the foreign key as a uid; null if there is no such row. */
    fun readRow(tbl: String, uid: String): JSONObject? {
        val s = SyncSql.spec(tbl) ?: return null
        val fk = s.fk
        val select = s.cols.joinToString(", ") { "t.`${it.n}` AS `${it.n}`" } +
            (fk?.let { ", (SELECT p.uid FROM `${it.parent}` p WHERE p.id = t.`${it.col}`) AS `${it.json}`" } ?: "")
        val r = if (s.hasUid) db.query("SELECT $select FROM `${s.name}` t WHERE t.uid = ?", listOf(uid)).firstOrNull()
        else db.query("SELECT $select FROM `${s.name}` t WHERE t.id = 1").firstOrNull()
        r ?: return null
        val j = JSONObject()
        for (c in s.cols) j.put(c.n, r[c.n] ?: JSONObject.NULL)
        if (fk != null) j.put(fk.json, r[fk.json] ?: JSONObject.NULL)
        return j
    }

    override fun apply(ops: List<Op>): Applied {
        if (ops.isEmpty()) return Applied.NONE
        return db.transaction {
            db.exec("UPDATE sync_state SET v = '1' WHERE k = 'applying'")
            var applied = 0; var skipped = 0
            val tables = LinkedHashSet<String>()
            for (o in ops) {
                val spec = SyncSql.spec(o.tbl)
                val cur = version(o.tbl, o.uid)
                if (spec == null || (cur != null && !(o.version > cur)) || !write(spec, o)) { skipped++; continue }
                db.exec("INSERT OR REPLACE INTO sync_rows(tbl, uid, origin, oseq, at, `by`, del) VALUES(?, ?, ?, ?, ?, ?, ?)",
                    listOf(o.tbl, o.uid, o.origin, o.oseq, o.at, o.by, if (o.del) 1L else 0L))
                applied++; tables += o.tbl
            }
            db.exec("UPDATE sync_state SET v = '0' WHERE k = 'applying'")
            Applied(applied, skipped, 0, tables)
        }
    }

    /** Writes one op into its table. False = could not (the row's parent is missing, or the dose clashes with another), nothing written. */
    private fun write(s: TableSpec, o: Op): Boolean {
        if (o.del) {
            if (s.hasUid) db.exec("DELETE FROM `${s.name}` WHERE uid = ?", listOf(o.uid)) // the profile is never deleted
            return true
        }
        val row = o.row ?: return false
        val cols = ArrayList<String>(); val vals = ArrayList<Any?>()
        for (c in s.cols) { cols += c.n; vals += arg(c, row) }
        val fk = s.fk
        if (fk != null) {
            val pu = if (row.isNull(fk.json)) "" else row.optString(fk.json, "")
            val local = if (pu.isEmpty()) null else db.query("SELECT id FROM `${fk.parent}` WHERE uid = ?", listOf(pu)).firstOrNull()?.get("id")
            // a dose without its medicine cannot be kept; a note whose group is gone just loses the link
            if (local == null && s.name == "doses") return false
            cols += fk.col; vals += local
        }
        val id: Long? = if (s.hasUid) db.query("SELECT id FROM `${s.name}` WHERE uid = ?", listOf(o.uid)).firstOrNull()?.get("id") as Long?
        else db.query("SELECT id FROM `${s.name}` WHERE id = 1").firstOrNull()?.get("id") as Long?
        if (s.name == "doses") { // the unique index on (medicineId, scheduledAt): a different row already holds that slot
            val med = vals[cols.indexOf("medicineId")]; val at = vals[cols.indexOf("scheduledAt")]
            val clash = db.query("SELECT id FROM doses WHERE medicineId = ? AND scheduledAt = ? AND uid <> ?", listOf(med, at, o.uid)).firstOrNull()
            if (clash != null) return false
        }
        if (id != null) {
            val set = cols.joinToString(", ") { "`$it` = ?" } + if (s.hasUid) ", updatedAt = ?, updatedBy = ?" else ""
            val args = ArrayList(vals); if (s.hasUid) { args += o.at; args += o.by }
            args += id
            db.exec("UPDATE `${s.name}` SET $set WHERE id = ?", args)
        } else {
            val names = ArrayList(cols); val args = ArrayList(vals)
            if (s.hasUid) { names += listOf("uid", "updatedAt", "updatedBy"); args.addAll(listOf(o.uid, o.at, o.by)) } else { names += "id"; args += 1L }
            db.exec("INSERT INTO `${s.name}`(${names.joinToString(", ") { "`$it`" }}) VALUES(${names.joinToString(", ") { "?" }})", args)
        }
        return true
    }

    private fun arg(c: Col, j: JSONObject): Any? {
        val v = if (!j.has(c.n) || j.isNull(c.n)) null else j.get(c.n)
        if (v == null) return if (c.nullable) null else when (c.k) { K.I -> 0L; K.R -> 0.0; K.T -> "" }
        return when (c.k) {
            K.I -> when (v) { is Boolean -> if (v) 1L else 0L; is Number -> v.toLong(); is String -> v.toLongOrNull() ?: 0L; else -> 0L }
            K.R -> (v as? Number)?.toDouble() ?: (v as? String)?.toDoubleOrNull() ?: 0.0
            K.T -> v.toString()
        }
    }
}
