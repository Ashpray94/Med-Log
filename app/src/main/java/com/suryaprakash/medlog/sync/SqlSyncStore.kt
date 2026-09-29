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

    /** The version of every column of a live row, by wire name (sync_cols). */
    private fun colVersions(tbl: String, uid: String): Map<String, Version> =
        db.query("SELECT col, origin, oseq, at, `by` FROM sync_cols WHERE tbl = ? AND uid = ?", listOf(tbl, uid))
            .associate { it["col"] as String to Version(it["at"] as Long, it["by"] as String, it["origin"] as String, it["oseq"] as Long, false) }

    override fun changedSince(have: Map<String, Long>): List<Op> {
        val args = ArrayList<Any?>()
        val where = if (have.isEmpty()) "1" else {
            val per = have.entries.joinToString(" OR ") { args += it.key; args += it.value; "(origin = ? AND oseq > ?)" }
            args.addAll(have.keys)
            "$per OR origin NOT IN (${have.keys.joinToString(",") { "?" }})"
        }
        // a row is sent when its own version or any of its columns' versions is newer than what the asker has
        val keys = db.query("SELECT tbl, uid FROM sync_rows WHERE $where UNION SELECT tbl, uid FROM sync_cols WHERE $where", args + args)
        val out = ArrayList<Op>()
        for (k in keys) {
            val tbl = k["tbl"] as String; val uid = k["uid"] as String
            val rv = version(tbl, uid) ?: continue
            if (rv.del) { out += Merge.opsFor(tbl, uid, rv, emptyMap(), null, have); continue }
            val row = readRow(tbl, uid) ?: continue // a live version whose row is gone (should not happen): nothing to send
            out += Merge.opsFor(tbl, uid, rv, colVersions(tbl, uid), row, have)
        }
        return out.sortedWith(compareBy({ it.oseq }, { it.tbl }, { it.uid }))
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
            val tables = LinkedHashSet<String>(); val written = ArrayList<Op>()
            for (o in ops) {
                raiseClock(o.top.at)
                val spec = SyncSql.spec(o.tbl)
                if (spec == null) { skipped++; continue }
                val cur = version(o.tbl, o.uid)
                val names = o.row?.let { r -> spec.keys.filter { r.has(it) } }.orEmpty()
                val plan = Merge.plan(cur, if (cur != null && !cur.del) colVersions(o.tbl, o.uid) else emptyMap(), o, names)
                if (plan == null || !write(spec, o, plan)) { skipped++; continue }
                db.exec("INSERT OR REPLACE INTO sync_rows(tbl, uid, origin, oseq, at, `by`, del) VALUES(?, ?, ?, ?, ?, ?, ?)",
                    listOf(o.tbl, o.uid, plan.rowVersion.origin, plan.rowVersion.oseq, plan.rowVersion.at, plan.rowVersion.by, if (plan.delete) 1L else 0L))
                applied++; tables += o.tbl; written += if (o.del) o else o.copy(row = readRow(o.tbl, o.uid) ?: o.row)
            }
            db.exec("UPDATE sync_state SET v = '0' WHERE k = 'applying'")
            Applied(applied, skipped, 0, tables, written)
        }
    }

    /**
     * The highest edit time seen from any phone; a local edit is stamped above it (see [SyncSql.STAMP]), so a phone whose clock ran
     * ahead can't keep winning. (Not capped: a cap, e.g. now + 10 min, would let a phone that is an hour ahead keep winning over edits
     * made after receiving its change, which is what this clock is for.)
     */
    private fun raiseClock(at: Long) =
        db.exec("INSERT INTO sync_state(k, v) VALUES('hlc', ?) ON CONFLICT(k) DO UPDATE SET v = excluded.v WHERE CAST(excluded.v AS INTEGER) > CAST(sync_state.v AS INTEGER)", listOf(at.toString()))

    /** Writes what [plan] says into the op's table and column versions. False = could not (the row's parent is missing, or the dose clashes with another), nothing written. */
    private fun write(s: TableSpec, o: Op, plan: Merge.Plan): Boolean {
        if (plan.delete) {
            if (s.hasUid) db.exec("DELETE FROM `${s.name}` WHERE uid = ?", listOf(o.uid)) // the profile is never deleted
            db.exec("DELETE FROM sync_cols WHERE tbl = ? AND uid = ?", listOf(o.tbl, o.uid))
            return true
        }
        val row = o.row ?: return false
        val cols = ArrayList<String>(); val vals = ArrayList<Any?>()
        val fk = s.fk
        val take = if (plan.create) s.keys else plan.take // a new row also gets a default for a column the sender left out
        for (k in take) {
            val c = s.cols.firstOrNull { it.n == k }
            if (c != null) { cols += c.n; vals += arg(c, row); continue }
            if (fk == null || k != fk.json) continue
            val pu = if (row.isNull(fk.json)) "" else row.optString(fk.json, "")
            val local = if (pu.isEmpty()) null else db.query("SELECT id FROM `${fk.parent}` WHERE uid = ?", listOf(pu)).firstOrNull()?.get("id")
            // a dose without its medicine cannot be kept; a note whose group is gone just loses the link
            if (local == null && s.name == "doses") return false
            cols += fk.col; vals += local
        }
        val id: Long? = if (s.hasUid) db.query("SELECT id FROM `${s.name}` WHERE uid = ?", listOf(o.uid)).firstOrNull()?.get("id") as Long?
        else db.query("SELECT id FROM `${s.name}` WHERE id = 1").firstOrNull()?.get("id") as Long?
        if (s.name == "doses" && ("medicineId" in cols || "scheduledAt" in cols || id == null)) { // the unique index on (medicineId, scheduledAt): a different row already holds that slot
            val held = if (id == null) null else db.query("SELECT medicineId, scheduledAt FROM doses WHERE id = ?", listOf(id)).firstOrNull()
            val med = if ("medicineId" in cols) vals[cols.indexOf("medicineId")] else held?.get("medicineId")
            val at = if ("scheduledAt" in cols) vals[cols.indexOf("scheduledAt")] else held?.get("scheduledAt")
            val clash = db.query("SELECT id FROM doses WHERE medicineId = ? AND scheduledAt = ? AND uid <> ?", listOf(med, at, o.uid)).firstOrNull()
            if (clash != null) return false
        }
        val rv = plan.rowVersion
        if (id != null) {
            val set = (cols.map { "`$it` = ?" } + if (s.hasUid) listOf("updatedAt = ?", "updatedBy = ?") else emptyList()).joinToString(", ")
            if (set.isNotEmpty()) {
                val args = ArrayList(vals); if (s.hasUid) { args += rv.at; args += rv.by }
                args += id
                db.exec("UPDATE `${s.name}` SET $set WHERE id = ?", args)
            }
        } else {
            val names = ArrayList(cols); val args = ArrayList(vals)
            if (s.hasUid) { names += listOf("uid", "updatedAt", "updatedBy"); args.addAll(listOf(o.uid, rv.at, rv.by)) } else { names += "id"; args += 1L }
            db.exec("INSERT INTO `${s.name}`(${names.joinToString(", ") { "`$it`" }}) VALUES(${names.joinToString(", ") { "?" }})", args)
        }
        for (k in take) {
            val v = o.colVersion(k)
            db.exec("INSERT OR REPLACE INTO sync_cols(tbl, uid, col, at, `by`, origin, oseq) VALUES(?, ?, ?, ?, ?, ?, ?)", listOf(o.tbl, o.uid, k, v.at, v.by, v.origin, v.oseq))
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
