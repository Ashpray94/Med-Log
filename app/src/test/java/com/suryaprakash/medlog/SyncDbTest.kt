package com.suryaprakash.medlog

import com.suryaprakash.medlog.sync.RestoreSql
import com.suryaprakash.medlog.sync.SqlDb
import com.suryaprakash.medlog.sync.SqlSyncStore
import com.suryaprakash.medlog.sync.SyncEngine
import com.suryaprakash.medlog.sync.SyncSql
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.sql.Connection
import java.sql.DriverManager

/** SqlDb over JDBC: the same mapping code as the phone's RoomSyncStore runs here on a real SQLite file. */
class JdbcSqlDb(val conn: Connection) : SqlDb {
    private var depth = 0
    private fun bind(st: java.sql.PreparedStatement, args: List<Any?>) { args.forEachIndexed { i, a -> st.setObject(i + 1, a) } }

    override fun query(sql: String, args: List<Any?>): List<Map<String, Any?>> = conn.prepareStatement(sql).use { st ->
        bind(st, args)
        st.executeQuery().use { rs ->
            val md = rs.metaData
            val out = ArrayList<Map<String, Any?>>()
            while (rs.next()) {
                val m = HashMap<String, Any?>()
                for (i in 1..md.columnCount) m[md.getColumnLabel(i)] = when (val v = rs.getObject(i)) { is Int -> v.toLong(); is Long, is Double, is String, null -> v; else -> v.toString() }
                out += m
            }
            out
        }
    }

    override fun exec(sql: String, args: List<Any?>) { conn.prepareStatement(sql).use { st -> bind(st, args); st.executeUpdate() } }

    override fun <T> transaction(block: () -> T): T {
        if (depth++ == 0) conn.autoCommit = false
        try {
            val r = block()
            if (depth == 1) conn.commit()
            return r
        } catch (e: Throwable) {
            if (depth == 1) conn.rollback()
            throw e
        } finally { if (--depth == 0) conn.autoCommit = true }
    }
}

class SyncDbTest {
    private val files = ArrayList<File>()
    private val conns = ArrayList<Connection>()

    @After fun close() { conns.forEach { runCatching { it.close() } }; files.forEach { it.delete() } }

    private fun schemaJson(v: Int): JSONObject {
        val f = listOf("schemas", "app/schemas").map { File("$it/com.suryaprakash.medlog.data.MedDb/$v.json") }.first { it.exists() }
        return JSONObject(f.readText())
    }

    private fun create(entity: JSONObject): List<String> {
        val t = entity.getString("tableName")
        val out = arrayListOf(entity.getString("createSql").replace("\${TABLE_NAME}", t))
        val idx = entity.optJSONArray("indices")
        if (idx != null) for (i in 0 until idx.length()) out += idx.getJSONObject(i).getString("createSql").replace("\${TABLE_NAME}", t)
        return out
    }

    private fun open(f: File = File.createTempFile("medlog", ".db").also { files += it }): Connection =
        DriverManager.getConnection("jdbc:sqlite:${f.absolutePath}").also { conns += it }

    /** A database exactly as version 3 of the app made it. */
    private fun v3(): Connection {
        val c = open()
        val ents = schemaJson(3).getJSONObject("database").getJSONArray("entities")
        for (i in 0 until ents.length()) for (sql in create(ents.getJSONObject(i))) c.createStatement().use { it.execute(sql) }
        return c
    }

    private fun run(c: Connection, sqls: List<String>) { for (s in sqls) c.createStatement().use { it.execute(s) } }
    private fun q(c: Connection, sql: String): List<Map<String, Any?>> = JdbcSqlDb(c).query(sql)
    private fun one(c: Connection, sql: String): Any? = q(c, sql).firstOrNull()?.values?.firstOrNull()
    private fun n(c: Connection, sql: String): Long = (one(c, sql) as Long?) ?: 0L
    private fun x(c: Connection, sql: String) { c.createStatement().use { it.execute(sql) } }
    private fun seq(c: Connection) = one(c, "SELECT v FROM sync_state WHERE k='seq'").toString().toLong()
    private fun device(c: Connection) = one(c, "SELECT v FROM sync_state WHERE k='device'") as String
    private fun rows(c: Connection) = n(c, "SELECT count(*) FROM sync_rows")

    /** A fully migrated database with the given device name and the triggers installed. */
    private fun v4(name: String): Connection {
        val c = v3()
        run(c, SyncSql.migration3to4())
        x(c, "UPDATE sync_state SET v='$name' WHERE k='device'")
        return c
    }

    private fun addNote(c: Connection, text: String, extra: String = "") =
        x(c, "INSERT INTO notes(kind, occurredAt, createdAt, details, triage, triageReasons, text$extra) VALUES('SYMPTOM', 1000, 1000, '{}', 'GREEN', '', '$text'${if (extra.isEmpty()) "" else ", 'x'"})")

    // ---- the schema and the migration -------------------------------------------------------------------------------------

    @Test fun syncTablesMatchTheExportedSchema() {
        val ents = schemaJson(4).getJSONObject("database").getJSONArray("entities")
        val want = HashSet<String>()
        for (i in 0 until ents.length()) { val e = ents.getJSONObject(i); if (e.getString("tableName").startsWith("sync_")) want += create(e) }
        assertEquals(4, want.size)
        assertEquals(want, SyncSql.CREATE_TABLES.toSet())
    }

    @Test fun addedColumnsMatchTheEntities() {
        val ents = schemaJson(4).getJSONObject("database").getJSONArray("entities")
        for (i in 0 until ents.length()) {
            val e = ents.getJSONObject(i)
            if (SyncSql.UID_TABLES.none { it.name == e.getString("tableName") }) continue
            val sql = e.getString("createSql")
            assertTrue(sql, sql.endsWith("`uid` TEXT NOT NULL DEFAULT '', `updatedAt` INTEGER NOT NULL DEFAULT 0, `updatedBy` TEXT NOT NULL DEFAULT '')"))
        }
        assertEquals(6 * 3, SyncSql.alterColumns().size)
    }

    @Test fun migrationSeedsExistingRows() {
        val c = v3()
        x(c, "INSERT INTO profile(id,name,dob,sex,bloodGroup,hospitalId,conditions,allergies,doctorName,doctorPhone,onBloodThinner,notes,plan) VALUES(1,'Asha','','','','','','','','',0,'','')")
        x(c, "INSERT INTO helpers(name,phone,relation,sos,alerts,pairId,pairKey,canSeeNotes,sortOrder) VALUES('Ravi','999','son',1,1,'p','k',1,0)")
        addNote(c, "one"); addNote(c, "two")
        x(c, "INSERT INTO medicines(name,strength,form,amount,food,times,days,startDate,critical,asNeeded,minGapHours,purpose,active,bloodThinner,changedAt,changeNote,shape,color) VALUES('Met','','tablet','1','any','08:00','',1,0,0,4,'',1,0,1,'','','')")
        x(c, "INSERT INTO doses(medicineId,scheduledAt,status,reminded,helperAlerted) VALUES(1, 5000, 'DUE', 0, 0)")
        x(c, "INSERT INTO appointments(at,doctor,place,purpose,done) VALUES(9,'','','',0)")
        x(c, "INSERT INTO doc_lines(source,content,importedAt) VALUES('s','c',1)")
        run(c, SyncSql.migration3to4())
        val uids = q(c, "SELECT uid FROM notes UNION ALL SELECT uid FROM helpers UNION ALL SELECT uid FROM medicines UNION ALL SELECT uid FROM appointments UNION ALL SELECT uid FROM doc_lines").map { it["uid"] as String }
        assertTrue(uids.all { it.length == 32 })
        assertEquals(uids.size, uids.toSet().size)
        val medUid = one(c, "SELECT uid FROM medicines") as String
        assertEquals("$medUid:5000", one(c, "SELECT uid FROM doses"))
        assertEquals(8L, rows(c)) // profile, helper, 2 notes, medicine, dose, appointment, doc line
        assertEquals(8L, seq(c))
        assertEquals(setOf(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L), q(c, "SELECT oseq FROM sync_rows").map { it["oseq"] as Long }.toSet())
        assertEquals(1L, n(c, "SELECT count(*) FROM sync_rows WHERE tbl='profile' AND uid='profile'"))
        assertEquals(device(c), one(c, "SELECT DISTINCT origin FROM sync_rows"))
        assertEquals("0", one(c, "SELECT v FROM sync_state WHERE k='applying'"))
        assertEquals(0L, n(c, "SELECT count(*) FROM notes WHERE updatedAt = 0 OR updatedBy = ''"))
        // running the seeding again changes nothing
        run(c, SyncSql.onOpen() + SyncSql.backfill())
        assertEquals(8L, seq(c)); assertEquals(8L, rows(c))
    }

    // ---- the triggers ----------------------------------------------------------------------------------------------------

    @Test fun insertStampsUidAndWritesOneSyncRow() {
        val c = v4("A")
        addNote(c, "headache")
        val r = q(c, "SELECT uid, updatedAt, updatedBy FROM notes").single()
        assertEquals(32, (r["uid"] as String).length)
        assertTrue((r["updatedAt"] as Long) > 1_600_000_000_000L)
        assertEquals("A", r["updatedBy"])
        assertEquals(1L, seq(c))
        val s = q(c, "SELECT * FROM sync_rows").single()
        assertEquals("notes", s["tbl"]); assertEquals(r["uid"], s["uid"]); assertEquals("A", s["origin"]); assertEquals(1L, s["oseq"])
        assertEquals(r["updatedAt"], s["at"]); assertEquals("A", s["by"]); assertEquals(0L, s["del"])
        addNote(c, "second")
        assertEquals(2L, seq(c)) // exactly one bump per insert: the stamping UPDATE did not fire the triggers
        assertEquals(2L, rows(c))
    }

    @Test fun updateBumpsOnceAndOnlyForSharedColumns() {
        val c = v4("A")
        addNote(c, "a")
        val uid = one(c, "SELECT uid FROM notes") as String
        x(c, "UPDATE notes SET text = 'b'")
        assertEquals(2L, seq(c))
        assertEquals(2L, n(c, "SELECT oseq FROM sync_rows WHERE uid='$uid'"))
        x(c, "UPDATE notes SET text = 'b'") // nothing changed
        assertEquals(2L, seq(c))
        x(c, "UPDATE notes SET photoPath = '/p.jpg', audioPath = '/a.m4a'") // phone-only columns
        assertEquals(2L, seq(c))
        x(c, "UPDATE notes SET groupId = 7")
        assertEquals(3L, seq(c))
        assertEquals(uid, one(c, "SELECT uid FROM notes"))
    }

    @Test fun staleCopyWithEmptyUidKeepsTheUid() {
        val c = v4("A")
        addNote(c, "a")
        val uid = one(c, "SELECT uid FROM notes") as String
        x(c, "UPDATE notes SET uid = '', updatedAt = 5, text = 'z'") // what @Update does with an old copy that never had a uid
        assertEquals(uid, one(c, "SELECT uid FROM notes"))
        assertTrue(n(c, "SELECT updatedAt FROM notes") > 1_600_000_000_000L)
        assertEquals(2L, seq(c))
    }

    @Test fun deleteWritesATombstone() {
        val c = v4("A")
        addNote(c, "a")
        val uid = one(c, "SELECT uid FROM notes") as String
        x(c, "DELETE FROM notes")
        assertEquals(2L, seq(c))
        val s = q(c, "SELECT * FROM sync_rows WHERE uid='$uid'").single()
        assertEquals(1L, s["del"]); assertEquals(2L, s["oseq"]); assertEquals("A", s["origin"])
    }

    @Test fun incomingWritesDoNotCreateSyncRows() {
        val c = v4("A")
        x(c, "UPDATE sync_state SET v='1' WHERE k='applying'")
        addNote(c, "from B")
        x(c, "UPDATE notes SET text = 'changed'")
        x(c, "DELETE FROM notes")
        x(c, "UPDATE sync_state SET v='0' WHERE k='applying'")
        assertEquals(0L, seq(c)); assertEquals(0L, rows(c))
    }

    @Test fun profileUsesTheFixedUid() {
        val c = v4("A")
        x(c, "INSERT OR REPLACE INTO profile(id,name,dob,sex,bloodGroup,hospitalId,conditions,allergies,doctorName,doctorPhone,onBloodThinner,notes,plan) VALUES(1,'A','','','','','','','','',0,'','')")
        assertEquals(1L, seq(c))
        x(c, "INSERT OR REPLACE INTO profile(id,name,dob,sex,bloodGroup,hospitalId,conditions,allergies,doctorName,doctorPhone,onBloodThinner,notes,plan) VALUES(1,'B','','','','','','','','',0,'','{}')")
        assertEquals(2L, seq(c)) // REPLACE is a delete plus insert; with recursive triggers off only the insert trigger runs
        x(c, "UPDATE profile SET plan = '{\"x\":1}'")
        assertEquals(3L, seq(c))
        assertEquals(listOf("profile"), q(c, "SELECT uid FROM sync_rows").map { it["uid"] })
    }

    @Test fun doseUidIsMadeFromMedicineAndTime() {
        val c = v4("A")
        x(c, "INSERT INTO medicines(name,strength,form,amount,food,times,days,startDate,critical,asNeeded,minGapHours,purpose,active,bloodThinner,changedAt,changeNote,shape,color) VALUES('M','','tablet','1','any','','',1,0,0,4,'',1,0,1,'','','')")
        x(c, "INSERT INTO doses(medicineId,scheduledAt,status,reminded,helperAlerted) VALUES(1, 777, 'DUE', 0, 0)")
        assertEquals(one(c, "SELECT uid FROM medicines") as String + ":777", one(c, "SELECT uid FROM doses"))
        x(c, "INSERT OR IGNORE INTO doses(medicineId,scheduledAt,status,reminded,helperAlerted) VALUES(1, 777, 'DUE', 0, 0)") // ignored: no change
        assertEquals(2L, seq(c))
    }

    @Test fun triggersCanBeInstalledTwice() {
        val c = v4("A")
        run(c, SyncSql.onOpen()); run(c, SyncSql.onOpen())
        addNote(c, "a")
        assertEquals(1L, seq(c))
    }

    // ---- two databases and the engine ----------------------------------------------------------------------------------

    private class Phone(val c: Connection, val id: String) {
        val store = SqlSyncStore(JdbcSqlDb(c))
        lateinit var engine: SyncEngine
        var published = 0L
        fun push() { engine.publishLocal(published); published = store.localSeq }
    }

    private fun phone(relay: FakeRelay, id: String): Phone {
        val p = Phone(v4(id), id)
        p.engine = SyncEngine(p.store, relay.sender(id), gapHelloIntervalMs = 0)
        relay.join(id, p.engine)
        return p
    }

    private fun snapshot(c: Connection): String = SqlSyncStore(JdbcSqlDb(c)).changedSince(emptyMap()).joinToString("\n") {
        "${it.tbl}/${it.uid} ${it.version} ${it.row?.let { r -> r.keys().asSequence().sorted().joinToString(",") { k -> "$k=${r.get(k)}" } }}"
    }

    @Test fun twoDatabasesConvergeThroughTheEngine() {
        val relay = FakeRelay()
        val a = phone(relay, "A"); val b = phone(relay, "B")
        // B already has other rows, so local ids differ between the phones
        x(b.c, "INSERT INTO medicines(name,strength,form,amount,food,times,days,startDate,critical,asNeeded,minGapHours,purpose,active,bloodThinner,changedAt,changeNote,shape,color) VALUES('Other','','tablet','1','any','','',1,0,0,4,'',1,0,1,'','','')")
        x(b.c, "INSERT INTO medicines(name,strength,form,amount,food,times,days,startDate,critical,asNeeded,minGapHours,purpose,active,bloodThinner,changedAt,changeNote,shape,color,calendarEventId) VALUES('B-med','','tablet','1','any','','',1,0,0,4,'',1,0,1,'','','',55)")
        x(a.c, "INSERT INTO medicines(name,strength,form,amount,food,times,days,startDate,critical,asNeeded,minGapHours,purpose,active,bloodThinner,changedAt,changeNote,shape,color,photoPath,calendarEventId,pillsLeft) VALUES('Met','500 mg','tablet','1','any','08:00','',1,1,0,4,'',1,0,1,'','round','white','/p.jpg',42,12.5)")
        x(a.c, "INSERT INTO doses(medicineId,scheduledAt,status,reminded,helperAlerted) VALUES(1, 8000, 'DUE', 0, 0)")
        x(a.c, "INSERT INTO helpers(name,phone,relation,sos,alerts,pairId,pairKey,canSeeNotes,sortOrder) VALUES('Ravi','999','son',1,1,'PID','KEY',1,2)")
        addNote(a.c, "first"); addNote(a.c, "second", ", audioPath")
        x(a.c, "UPDATE notes SET groupId = 1 WHERE text = 'second'")
        x(a.c, "INSERT INTO appointments(at,doctor,place,purpose,calendarEventId,done) VALUES(9,'Dr','','',7,0)")
        x(a.c, "INSERT INTO profile(id,name,dob,sex,bloodGroup,hospitalId,conditions,allergies,doctorName,doctorPhone,onBloodThinner,notes,plan) VALUES(1,'Asha','','','','','','','','',1,'','{\"limits\":1}')")
        a.push(); b.push(); relay.pump()

        // B holds A's rows with its own local ids and none of A's phone-only columns
        assertEquals("Met", one(b.c, "SELECT name FROM medicines WHERE uid <> ''  AND name='Met'"))
        assertEquals(12.5, one(b.c, "SELECT pillsLeft FROM medicines WHERE name='Met'"))
        assertNull(one(b.c, "SELECT photoPath FROM medicines WHERE name='Met'"))
        assertNull(one(b.c, "SELECT calendarEventId FROM medicines WHERE name='Met'"))
        val bMetId = n(b.c, "SELECT id FROM medicines WHERE name='Met'")
        assertEquals(3L, bMetId)
        assertEquals(bMetId, n(b.c, "SELECT medicineId FROM doses"))
        assertEquals(1L, n(b.c, "SELECT sos FROM helpers WHERE name='Ravi'"))
        assertNull(one(b.c, "SELECT pairKey FROM helpers WHERE name='Ravi'"))
        assertNull(one(b.c, "SELECT pairId FROM helpers WHERE name='Ravi'"))
        assertNull(one(b.c, "SELECT audioPath FROM notes WHERE text='second'"))
        assertEquals(one(b.c, "SELECT id FROM notes WHERE text='first'"), one(b.c, "SELECT groupId FROM notes WHERE text='second'"))
        assertNull(one(b.c, "SELECT calendarEventId FROM appointments"))
        assertEquals("{\"limits\":1}", one(b.c, "SELECT plan FROM profile"))
        assertEquals(one(a.c, "SELECT uid FROM notes WHERE text='first'"), one(b.c, "SELECT uid FROM notes WHERE text='first'"))
        assertEquals(one(a.c, "SELECT updatedAt FROM notes WHERE text='first'"), one(b.c, "SELECT updatedAt FROM notes WHERE text='first'"))
        assertEquals("A", one(b.c, "SELECT updatedBy FROM notes WHERE text='first'"))
        // nothing echoed: B only counts its own two medicines
        assertEquals(2L, seq(b.c))
        assertEquals(mapOf("A" to 12L, "B" to 2L).filterKeys { it == "B" }, b.store.have().filterKeys { it == "B" })
        assertTrue(b.store.have().getValue("A") >= 8L)
        // the two phones hold the same shared data
        assertEquals(snapshot(a.c), snapshot(b.c))

        // B edits, A deletes; both catch up
        x(b.c, "UPDATE notes SET text = 'first (edited on B)' WHERE text='first'")
        x(a.c, "DELETE FROM helpers")
        b.push(); a.push(); relay.pump()
        assertEquals("first (edited on B)", one(a.c, "SELECT text FROM notes WHERE uid = '${one(b.c, "SELECT uid FROM notes WHERE text LIKE 'first%'")}'"))
        assertEquals(0L, n(b.c, "SELECT count(*) FROM helpers"))
        assertEquals(snapshot(a.c), snapshot(b.c))
        // a new edit on A brings a deleted note back? no: a later edit on the other phone wins over an earlier delete
        val aUid = one(a.c, "SELECT uid FROM notes WHERE text='second'") as String
        x(a.c, "DELETE FROM notes WHERE uid='$aUid'")
        Thread.sleep(3)
        x(b.c, "UPDATE notes SET text = 'second (B, later)' WHERE uid='$aUid'")
        a.push(); b.push(); relay.pump()
        assertEquals(snapshot(a.c), snapshot(b.c))
        assertEquals("second (B, later)", one(a.c, "SELECT text FROM notes WHERE uid='$aUid'"))
    }

    @Test fun newerVersionWinsAndOlderIsSkipped() {
        val c = v4("B")
        val b = SqlSyncStore(JdbcSqlDb(c))
        val op = { at: Long, text: String -> com.suryaprakash.medlog.sync.Op("notes", "u1", "X", at, at, "X", false,
            JSONObject("{\"kind\":\"SYMPTOM\",\"occurredAt\":1,\"createdAt\":1,\"details\":\"{}\",\"triage\":\"GREEN\",\"triageReasons\":\"\",\"text\":\"$text\"}")) }
        val r1 = b.apply(listOf(op(10, "old")))
        assertEquals(setOf("notes"), r1.tables); assertEquals(1, r1.applied)
        val r2 = b.apply(listOf(op(5, "older")))
        assertEquals(1, r2.skipped); assertTrue(r2.tables.isEmpty())
        b.apply(listOf(op(20, "new")))
        assertEquals("new", one(c, "SELECT text FROM notes"))
        assertEquals(0L, seq(c))
        assertEquals("0", one(c, "SELECT v FROM sync_state WHERE k='applying'"))
    }

    @Test fun failedApplyRollsBackAndResetsTheFlag() {
        val c = v4("A")
        val s = SqlSyncStore(JdbcSqlDb(c))
        x(c, "DROP TABLE appointments")
        val ok = com.suryaprakash.medlog.sync.Op("notes", "u1", "X", 1, 1, "X", false, JSONObject("{\"kind\":\"K\",\"occurredAt\":1,\"createdAt\":1,\"details\":\"{}\",\"triage\":\"GREEN\",\"triageReasons\":\"\",\"text\":\"t\"}"))
        val bad = com.suryaprakash.medlog.sync.Op("appointments", "u2", "X", 2, 2, "X", false, JSONObject("{\"at\":1}"))
        try { s.apply(listOf(ok, bad)); throw AssertionError("expected failure") } catch (e: java.sql.SQLException) { }
        assertEquals(0L, n(c, "SELECT count(*) FROM notes"))
        assertEquals("0", one(c, "SELECT v FROM sync_state WHERE k='applying'"))
        assertNull(s.version("notes", "u1"))
    }

    @Test fun doseWithoutItsMedicineIsRefusedAndClashingDoseIsSkipped() {
        val c = v4("A")
        val s = SqlSyncStore(JdbcSqlDb(c))
        val dose = { uid: String, med: String -> com.suryaprakash.medlog.sync.Op("doses", uid, "X", 1, 1, "X", false,
            JSONObject("{\"medicineUid\":\"$med\",\"scheduledAt\":100,\"status\":\"DUE\",\"reminded\":0,\"helperAlerted\":0}")) }
        assertEquals(1, s.apply(listOf(dose("d1", "nomed"))).skipped)
        x(c, "INSERT INTO medicines(name,strength,form,amount,food,times,days,startDate,critical,asNeeded,minGapHours,purpose,active,bloodThinner,changedAt,changeNote,shape,color) VALUES('M','','tablet','1','any','','',1,0,0,4,'',1,0,1,'','','')")
        val m = one(c, "SELECT uid FROM medicines") as String
        assertEquals(1, s.apply(listOf(dose("d2", m))).applied)
        assertEquals(1, s.apply(listOf(dose("d3", m))).skipped) // same medicine and time, other uid: the unique index would refuse it
        assertEquals(1L, n(c, "SELECT count(*) FROM doses"))
    }

    @Test fun haveIsPersistedNeverLoweredAndIncludesOwnSeq() {
        val c = v4("A")
        val s = SqlSyncStore(JdbcSqlDb(c))
        addNote(c, "x")
        s.advanceHave("B", 5); s.advanceHave("B", 3); s.advanceHave("A", 99)
        assertEquals(mapOf("A" to 1L, "B" to 5L), s.have())
        assertEquals(mapOf("A" to 1L, "B" to 5L), SqlSyncStore(JdbcSqlDb(c)).have()) // a new store over the same file
    }

    // ---- backup restore --------------------------------------------------------------------------------------------------

    private fun cols(c: Connection, schema: String): Map<String, List<RestoreSql.ColInfo>> =
        q(c, "SELECT name FROM $schema.sqlite_master WHERE type='table'").associate { t ->
            val name = t["name"] as String
            name to q(c, "PRAGMA $schema.table_info(`$name`)").map { RestoreSql.ColInfo(it["name"] as String, it["type"] as String, (it["notnull"] as Long) != 0L, it["dflt_value"] != null) }
        }
    private fun bkCols(c: Connection) = cols(c, "bk").mapValues { e -> e.value.map { it.name } }

    @Test fun restoreOfAnOldBackupCopiesCommonColumnsAndSeedsSharing() {
        val old = File.createTempFile("backup", ".db").also { files += it }
        val oc = open(old)
        // a "version 1" backup: no plan on profile, no shape/color on medicines, no uid anywhere
        x(oc, "CREATE TABLE profile (id INTEGER PRIMARY KEY, name TEXT NOT NULL, dob TEXT NOT NULL, sex TEXT NOT NULL, bloodGroup TEXT NOT NULL, hospitalId TEXT NOT NULL, conditions TEXT NOT NULL, allergies TEXT NOT NULL, doctorName TEXT NOT NULL, doctorPhone TEXT NOT NULL, onBloodThinner INTEGER NOT NULL, notes TEXT NOT NULL)")
        x(oc, "INSERT INTO profile VALUES(1,'Old','','','','','','','','',0,'')")
        x(oc, "CREATE TABLE medicines (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, strength TEXT NOT NULL, form TEXT NOT NULL, amount TEXT NOT NULL, food TEXT NOT NULL, times TEXT NOT NULL, days TEXT NOT NULL, startDate INTEGER NOT NULL, endDate INTEGER, critical INTEGER NOT NULL, asNeeded INTEGER NOT NULL, minGapHours INTEGER NOT NULL, purpose TEXT NOT NULL, photoPath TEXT, pillsLeft REAL, active INTEGER NOT NULL, bloodThinner INTEGER NOT NULL, changedAt INTEGER NOT NULL, changeNote TEXT NOT NULL, calendarEventId INTEGER)")
        x(oc, "INSERT INTO medicines VALUES(1,'OldMed','','tablet','1','any','','',1,NULL,0,0,4,'',NULL,NULL,1,0,1,'',NULL)")
        oc.close()
        val c = v4("A")
        addNote(c, "will be replaced")
        x(c, "ATTACH DATABASE '${old.absolutePath}' AS bk")
        for (s in RestoreSql.statements(cols(c, "main"), bkCols(c))) x(c, s)
        x(c, "DETACH DATABASE bk")
        assertEquals("Old", one(c, "SELECT name FROM profile"))
        assertEquals("", one(c, "SELECT plan FROM profile"))
        assertEquals("OldMed", one(c, "SELECT name FROM medicines"))
        assertEquals("", one(c, "SELECT shape FROM medicines"))
        assertEquals(1L, n(c, "SELECT count(*) FROM notes")) // the old backup has no notes table: that table is left alone
        assertEquals(32, (one(c, "SELECT uid FROM medicines") as String).length)
        // sharing is seeded for what was restored; the wipe and the restore are not shared as deletes
        assertEquals(0L, n(c, "SELECT count(*) FROM sync_rows WHERE del = 1"))
        assertEquals(setOf("medicines", "notes", "profile"), q(c, "SELECT tbl FROM sync_rows").map { it["tbl"] }.toSet())
        assertEquals("0", one(c, "SELECT v FROM sync_state WHERE k='applying'"))
        assertNotEquals("", device(c))
    }

    @Test fun restoreOfANewBackupKeepsTheSyncTablesAndDoesNotShareTheWipe() {
        val src = v4("SRC")
        addNote(src, "kept"); addNote(src, "kept too")
        val f = File.createTempFile("backup", ".db").also { files += it }
        x(src, "VACUUM INTO '${f.absolutePath}'".also { f.delete() })
        val c = v4("NEW")
        addNote(c, "local")
        x(c, "ATTACH DATABASE '${f.absolutePath}' AS bk")
        for (s in RestoreSql.statements(cols(c, "main"), bkCols(c))) x(c, s)
        x(c, "DETACH DATABASE bk")
        assertEquals(2L, n(c, "SELECT count(*) FROM notes"))
        assertEquals("SRC", device(c))
        assertEquals(2L, seq(c))
        assertEquals(2L, rows(c))
        assertEquals(0L, n(c, "SELECT count(*) FROM sync_rows WHERE del = 1"))
        assertEquals("0", one(c, "SELECT v FROM sync_state WHERE k='applying'"))
        addNote(c, "after") // triggers work after the restore
        assertEquals(3L, seq(c))
        assertFalse(rows(c) == 2L)
    }

    // ---- what Room's validation would compare ---------------------------------------------------------------------------

    /** SQLite's type affinity rules, as Room's TableInfo.findAffinity does them. */
    private fun affinity(type: String): String {
        val t = type.uppercase()
        return when {
            t.contains("INT") -> "INTEGER"
            t.contains("CHAR") || t.contains("CLOB") || t.contains("TEXT") -> "TEXT"
            t.contains("BLOB") || t.isEmpty() -> "BLOB"
            t.contains("REAL") || t.contains("FLOA") || t.contains("DOUB") -> "REAL"
            else -> "NUMERIC"
        }
    }

    /**
     * Room 2.6 TableInfo/Column/Index equality against 4.json: column name, affinity, notNull, primary key position, and the
     * default value text ONLY when the entity declares one (an entity without a default does not mind a DEFAULT in the database,
     * which is why shape and color from migration 2->3 are fine). Indices: name, unique, columns in order (only those made by
     * CREATE INDEX, as Room reads them).
     */
    private fun assertMatchesRoomSchema(c: Connection, label: String) {
        val ents = schemaJson(4).getJSONObject("database").getJSONArray("entities")
        for (e in 0 until ents.length()) {
            val ent = ents.getJSONObject(e); val t = ent.getString("tableName"); val at = "$label $t"
            val info = q(c, "PRAGMA table_info(`$t`)")
            val fields = ent.getJSONArray("fields")
            assertEquals("$at column count", fields.length(), info.size)
            val pk = ent.getJSONObject("primaryKey").getJSONArray("columnNames").let { a -> (0 until a.length()).map { a.getString(it) } }
            for (f in 0 until fields.length()) {
                val fld = fields.getJSONObject(f); val name = fld.getString("columnName")
                val col = info.firstOrNull { it["name"] == name } ?: throw AssertionError("$at: column $name missing")
                assertEquals("$at.$name affinity", fld.getString("affinity"), affinity(col["type"] as String))
                assertEquals("$at.$name notNull", fld.getBoolean("notNull"), (col["notnull"] as Long) != 0L)
                assertEquals("$at.$name pk position", (pk.indexOf(name) + 1).toLong(), col["pk"] as Long)
                if (fld.has("defaultValue")) assertEquals("$at.$name default", fld.getString("defaultValue"), col["dflt_value"])
            }
            val want = HashMap<String, Pair<Boolean, List<String>>>()
            val idx = ent.optJSONArray("indices")
            if (idx != null) for (i in 0 until idx.length()) {
                val ix = idx.getJSONObject(i); val cn = ix.getJSONArray("columnNames")
                want[ix.getString("name")] = ix.getBoolean("unique") to (0 until cn.length()).map { cn.getString(it) }
            }
            val got = HashMap<String, Pair<Boolean, List<String>>>()
            for (ix in q(c, "PRAGMA index_list(`$t`)")) {
                if (ix["origin"] != "c") continue
                val name = ix["name"] as String
                got[name] = ((ix["unique"] as Long) != 0L) to q(c, "PRAGMA index_info(`$name`)").sortedBy { it["seqno"] as Long }.map { it["name"] as String }
            }
            assertEquals("$at indices", want, got)
        }
    }

    @Test fun roomValidationMatchesAfterMigrationFrom3() {
        val c = v3()
        x(c, "INSERT INTO helpers(name,phone,relation,sos,alerts,pairId,pairKey,canSeeNotes,sortOrder) VALUES('Ravi','999','son',1,1,'p','k',1,0)")
        addNote(c, "one")
        x(c, "INSERT INTO medicines(name,strength,form,amount,food,times,days,startDate,critical,asNeeded,minGapHours,purpose,active,bloodThinner,changedAt,changeNote,shape,color) VALUES('Met','','tablet','1','any','08:00','',1,0,0,4,'',1,0,1,'','','')")
        x(c, "INSERT INTO doses(medicineId,scheduledAt,status,reminded,helperAlerted) VALUES(1, 5000, 'DUE', 0, 0)")
        run(c, SyncSql.migration3to4())
        assertMatchesRoomSchema(c, "migrated")
    }

    @Test fun roomValidationMatchesOnAFreshV4Database() {
        val c = open()
        val ents = schemaJson(4).getJSONObject("database").getJSONArray("entities")
        for (i in 0 until ents.length()) run(c, create(ents.getJSONObject(i)))
        run(c, SyncSql.onOpen())
        assertMatchesRoomSchema(c, "fresh")
        addNote(c, "works")
        assertEquals(1L, seq(c))
    }
}
