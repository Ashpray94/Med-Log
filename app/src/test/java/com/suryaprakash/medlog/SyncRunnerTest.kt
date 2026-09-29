package com.suryaprakash.medlog

import com.suryaprakash.medlog.sync.Op
import com.suryaprakash.medlog.sync.RestoreSql
import com.suryaprakash.medlog.sync.SqlSyncStore
import com.suryaprakash.medlog.sync.SyncEffects
import com.suryaprakash.medlog.sync.SyncRunner
import com.suryaprakash.medlog.sync.SyncSql
import com.suryaprakash.medlog.sync.WireJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.util.concurrent.Executors

/** The sync runner over real SQLite files (JDBC) and the fake relay: what the person's phone and a helper's phone do with each other's changes. */
class SyncRunnerTest {
    private val files = ArrayList<File>()
    private val conns = ArrayList<Connection>()
    private val pools = ArrayList<java.util.concurrent.ExecutorService>()

    @After fun close() { pools.forEach { it.shutdownNow() }; conns.forEach { runCatching { it.close() } }; files.forEach { it.delete() } }

    private fun schema(): org.json.JSONArray {
        val f = listOf("schemas", "app/schemas").map { File("$it/com.suryaprakash.medlog.data.MedDb/5.json") }.first { it.exists() }
        return JSONObject(f.readText()).getJSONObject("database").getJSONArray("entities")
    }

    private fun freshDb(device: String): Connection {
        val f = File.createTempFile("medlog", ".db").also { files += it }
        val c = DriverManager.getConnection("jdbc:sqlite:${f.absolutePath}").also { conns += it }
        val ents = schema()
        for (i in 0 until ents.length()) {
            val e = ents.getJSONObject(i); val t = e.getString("tableName")
            c.createStatement().use { it.execute(e.getString("createSql").replace("\${TABLE_NAME}", t)) }
            val idx = e.optJSONArray("indices")
            if (idx != null) for (j in 0 until idx.length()) c.createStatement().use { it.execute(idx.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", t)) }
        }
        for (s in SyncSql.onOpen()) c.createStatement().use { it.execute(s) }
        x(c, "UPDATE sync_state SET v='$device' WHERE k='device'")
        return c
    }

    private fun x(c: Connection, sql: String) { c.createStatement().use { it.execute(sql) } }
    private fun q(c: Connection, sql: String) = JdbcSqlDb(c).query(sql)
    private fun one(c: Connection, sql: String): Any? = q(c, sql).firstOrNull()?.values?.firstOrNull()
    private fun n(c: Connection, sql: String): Long = (one(c, sql) as Long?) ?: 0L

    class Fx : SyncEffects {
        val calls = ArrayList<String>()
        override suspend fun medicineChanged(uid: String) { calls += "changed:$uid" }
        override suspend fun medicineStopped(uid: String) { calls += "stopped:$uid" }
        override suspend fun doseClosed(uid: String) { calls += "cancelDose:$uid" }
        override suspend fun reschedule() { calls += "reschedule" }
        override fun refreshWidget() { calls += "widget" }
    }

    private inner class P(val name: String, relay: FakeRelay, debounceMs: Long = 1_000_000L) {
        val c = freshDb(name)
        val fx = Fx()
        val logs = ArrayList<String>()
        val out = ArrayList<String>()
        val pool = Executors.newSingleThreadExecutor().also { pools += it }
        val disp = pool.asCoroutineDispatcher()
        val runner = SyncRunner({ SqlSyncStore(JdbcSqlDb(c)) }, { out += it; relay.publish(name, it) }, fx, disp, CoroutineScope(disp),
            debounceMs = debounceMs, helloEveryMs = 1_000_000L, log = { logs += it })
        init { relay.join(name) { json -> runBlocking { runner.onMessage(json) } } }
        fun push() = runBlocking { runner.publishNow() }
        fun hello() = runBlocking { runner.hello() }
        fun kinds() = out.map { JSONObject(it).getString("kind") }
        fun medicine(name: String, times: String = "08:00", extra: String = "") =
            x(c, "INSERT INTO medicines(name,strength,form,amount,food,times,days,startDate,critical,asNeeded,minGapHours,purpose,active,bloodThinner,changedAt,changeNote,shape,color$extra) VALUES('$name','','tablet','1','any','$times','',1,0,0,4,'',1,0,1,'','',''${if (extra.isEmpty()) "" else ", 1"})")
        fun note(text: String) = x(c, "INSERT INTO notes(kind, occurredAt, createdAt, details, triage, triageReasons, text) VALUES('SYMPTOM', 1000, 1000, '{}', 'GREEN', '', '$text')")
        fun dose(med: Long, at: Long) = x(c, "INSERT INTO doses(medicineId,scheduledAt,status,reminded,helperAlerted) VALUES($med, $at, 'DUE', 0, 0)")
        fun uid(tbl: String, where: String) = one(c, "SELECT uid FROM $tbl WHERE $where") as String
    }

    private fun trio(): Triple<FakeRelay, P, P> { val r = FakeRelay(); return Triple(r, P("A", r), P("B", r)) }

    @Test fun createEditDeleteOnThePersonPhoneAppearsOnTheHelperAndBack() {
        val (relay, a, b) = trio()
        a.medicine("Metformin"); a.dose(1, 8000); a.note("dizzy today")
        x(a.c, "INSERT INTO profile(id,name,dob,sex,bloodGroup,hospitalId,conditions,allergies,doctorName,doctorPhone,onBloodThinner,notes,plan) VALUES(1,'Amma','','','','','','','','',0,'','')")
        a.push(); relay.pump()
        assertEquals("Metformin", one(b.c, "SELECT name FROM medicines"))
        assertEquals("dizzy today", one(b.c, "SELECT text FROM notes"))
        assertEquals(1L, n(b.c, "SELECT count(*) FROM doses"))
        assertEquals("Amma", one(b.c, "SELECT name FROM profile"))

        // helper edits the medicine, deletes the note and adds an appointment: the person's phone follows
        Thread.sleep(3)
        x(b.c, "UPDATE medicines SET times = '08:00,20:00', purpose = 'sugar' WHERE name = 'Metformin'")
        x(b.c, "DELETE FROM notes")
        x(b.c, "INSERT INTO appointments(at,doctor,place,purpose,done) VALUES(99,'Dr Rao','','',0)")
        x(b.c, "UPDATE profile SET conditions = 'diabetes'")
        b.push(); relay.pump()
        assertEquals("08:00,20:00", one(a.c, "SELECT times FROM medicines"))
        assertEquals(0L, n(a.c, "SELECT count(*) FROM notes"))
        assertEquals("Dr Rao", one(a.c, "SELECT doctor FROM appointments"))
        assertEquals("diabetes", one(a.c, "SELECT conditions FROM profile"))

        // and the person's phone creates and deletes again
        Thread.sleep(3)
        a.note("second"); x(a.c, "DELETE FROM appointments")
        a.push(); relay.pump()
        assertEquals("second", one(b.c, "SELECT text FROM notes"))
        assertEquals(0L, n(b.c, "SELECT count(*) FROM appointments"))
        // a helper that starts later gets everything through HELLO
        val late = P("C", relay)
        runBlocking { late.runner.start() }; relay.pump()
        assertEquals("Metformin", one(late.c, "SELECT name FROM medicines"))
        assertEquals("second", one(late.c, "SELECT text FROM notes"))
        assertEquals(0L, n(late.c, "SELECT count(*) FROM appointments WHERE 1"))
    }

    @Test fun startSaysHelloAndNothingIsSentTwice() {
        val (relay, a, b) = trio()
        a.note("one"); a.push(); relay.pump()
        val before = a.out.size
        assertEquals(0, a.push()) // nothing new: no message
        assertEquals(before, a.out.size)
        runBlocking { b.runner.start() }
        assertEquals("hello", b.kinds().first())
        relay.pump()
        assertEquals(1L, n(b.c, "SELECT count(*) FROM notes"))
    }

    @Test fun aPhoneNeverHeardFromGetsAHello() {
        val (relay, a, b) = trio()
        b.note("x"); b.push()
        val helloBefore = a.kinds().count { it == "hello" }
        relay.pump() // A hears B for the first time
        assertEquals(helloBefore + 1, a.kinds().count { it == "hello" })
        relay.pump()
        assertEquals(helloBefore + 1, a.kinds().count { it == "hello" }) // not again for the same phone
    }

    @Test fun changesAreSentAfterTheDebounce() {
        val relay = FakeRelay()
        val a = P("A", relay, debounceMs = 60)
        a.note("1"); a.runner.localChanged(); a.note("2"); a.runner.localChanged(); a.note("3"); a.runner.localChanged()
        assertEquals(0, a.out.size)
        val end = System.currentTimeMillis() + 3000
        while (a.out.isEmpty() && System.currentTimeMillis() < end) Thread.sleep(20)
        Thread.sleep(150)
        assertEquals(1, a.out.size) // one batch for the three edits
        assertEquals(3, JSONObject(a.out[0]).getJSONArray("ops").length())
    }

    // ---- wipe and restore ---------------------------------------------------------------------------------------------

    @Test fun wipeReseedsTheIdentityAndSendsNoDeletes() {
        val (relay, a, b) = trio()
        a.medicine("Met"); a.dose(1, 8000); a.note("n")
        x(a.c, "INSERT INTO helpers(name,phone,relation,sos,alerts,pairId,pairKey,canSeeNotes,sortOrder) VALUES('R','9','',1,1,'p','k',0,0)")
        a.push(); relay.pump()
        assertEquals(1L, n(b.c, "SELECT count(*) FROM notes"))
        val oldDevice = one(a.c, "SELECT v FROM sync_state WHERE k='device'")
        val sentBefore = relay.sent

        runBlocking { a.pool.submit { for (s in SyncSql.wipe()) x(a.c, s) }.get() }
        assertEquals(0L, n(a.c, "SELECT count(*) FROM notes") + n(a.c, "SELECT count(*) FROM medicines") + n(a.c, "SELECT count(*) FROM helpers") + n(a.c, "SELECT count(*) FROM doses"))
        assertEquals(0L, n(a.c, "SELECT count(*) FROM sync_rows"))
        assertEquals(0L, n(a.c, "SELECT count(*) FROM sync_have"))
        assertEquals("0", one(a.c, "SELECT v FROM sync_state WHERE k='seq'"))
        assertEquals("0", one(a.c, "SELECT v FROM sync_state WHERE k='applying'"))
        assertNotEquals(oldDevice, one(a.c, "SELECT v FROM sync_state WHERE k='device'"))
        assertNull(one(a.c, "SELECT v FROM sync_state WHERE k='hlc'"))
        // the wiped phone's runner reopens with the new id and has nothing to send
        runBlocking { a.runner.reopen() }
        assertEquals(setOf("hello"), a.kinds().drop(2).toSet()) // only HELLO after the wipe, never an op
        assertEquals(sentBefore + 1, relay.sent)
        // nothing was deleted on the other phone, and it holds no tombstones
        assertEquals(1L, n(b.c, "SELECT count(*) FROM notes"))
        assertEquals(1L, n(b.c, "SELECT count(*) FROM medicines"))
        assertEquals(0L, n(b.c, "SELECT count(*) FROM sync_rows WHERE del = 1"))
        // triggers work again for the new identity
        a.note("after")
        assertEquals(1L, n(a.c, "SELECT count(*) FROM sync_rows WHERE origin = '${one(a.c, "SELECT v FROM sync_state WHERE k='device'")}'"))
    }

    @Test fun restoreGetsANewDeviceIdKeepsTheRowsAndCatchesUp() {
        val (relay, a0, b) = trio()
        a0.note("first"); a0.push(); relay.pump()
        val backup = File.createTempFile("backup", ".db").also { files += it }
        x(a0.c, "VACUUM INTO '${backup.absolutePath}'".also { backup.delete() })
        a0.note("second, after the backup"); a0.push(); relay.pump()
        assertEquals(2L, n(b.c, "SELECT count(*) FROM notes"))

        // the same person restores the backup on a phone (or the same phone): it must not reuse the id "A"
        val a1 = P("R", relay)
        x(a1.c, "ATTACH DATABASE '${backup.absolutePath}' AS bk")
        fun cols(schema: String): Map<String, List<String>> = q(a1.c, "SELECT name FROM $schema.sqlite_master WHERE type='table'").associate { t ->
            (t["name"] as String) to q(a1.c, "PRAGMA $schema.table_info(`${t["name"]}`)").map { it["name"] as String }
        }
        val main = cols("main").mapValues { e -> e.value.map { RestoreSql.ColInfo(it, "", false, true) } }
        for (s in RestoreSql.statements(main, cols("bk"))) x(a1.c, s)
        x(a1.c, "DETACH DATABASE bk")
        val dev = one(a1.c, "SELECT v FROM sync_state WHERE k='device'") as String
        assertNotEquals("A", dev)
        assertEquals("first", one(a1.c, "SELECT text FROM notes"))
        assertEquals("A", one(a1.c, "SELECT origin FROM sync_rows")) // the restored rows keep their versions
        assertEquals(1L, n(a1.c, "SELECT seq FROM sync_have WHERE origin = 'A'"))
        assertEquals(0L, n(a1.c, "SELECT count(*) FROM sync_rows WHERE del = 1"))
        // it says HELLO at once, and the row the old id made after the backup comes back from the family
        runBlocking { a1.runner.reopen() }
        assertEquals("hello", a1.kinds().first())
        relay.pump()
        assertEquals(2L, n(a1.c, "SELECT count(*) FROM notes"))
        assertEquals(2L, n(a1.c, "SELECT seq FROM sync_have WHERE origin = 'A'"))
        // new local edits use the new id, numbered from 1
        a1.note("mine")
        assertEquals(dev, one(a1.c, "SELECT origin FROM sync_rows WHERE tbl='notes' AND uid = (SELECT uid FROM notes WHERE text='mine')"))
        assertEquals(1L, n(a1.c, "SELECT oseq FROM sync_rows WHERE origin = '$dev'"))
        a1.push(); relay.pump()
        assertEquals(3L, n(b.c, "SELECT count(*) FROM notes"))
    }

    // ---- clocks -------------------------------------------------------------------------------------------------------

    @Test fun aFarFutureClockIsLoggedAndCannotWinForeverBecauseLocalEditsStampAboveIt() {
        val (relay, a, b) = trio()
        val c = P("C", relay)
        a.note("original"); a.push(); relay.pump()
        val uid = a.uid("notes", "1=1")
        // B's clock is a year ahead: its edit arrives on A with a far-future time
        val future = System.currentTimeMillis() + 365L * 24 * 3600_000
        val row = JSONObject(runBlocking { a.pool.submit<String> { SqlSyncStore(JdbcSqlDb(a.c)).readRow("notes", uid)!!.toString() }.get() }).put("text", "from the future")
        val op = Op("notes", uid, "B", 1, future, "B", false, row)
        val msg = WireJson.opsMessage("B", listOf(op), mapOf("B" to (0L to 1L)))
        runBlocking { a.runner.onMessage(msg) }
        assertEquals("from the future", one(a.c, "SELECT text FROM notes"))
        assertTrue(a.logs.single().contains("ahead"))
        // a normal edit on A right afterwards wins anyway: it is stamped above the newest time A has seen
        x(a.c, "UPDATE notes SET text = 'fixed on A'")
        assertTrue(n(a.c, "SELECT updatedAt FROM notes") > future)
        a.push(); relay.pump()
        // C got A's ops and then B's stale message in either order: it ends with A's edit
        runBlocking { c.runner.onMessage(msg) }
        assertEquals("fixed on A", one(c.c, "SELECT text FROM notes"))
        assertEquals("fixed on A", one(b.c, "SELECT text FROM notes"))
    }

    // ---- reactions (plan B.6) -----------------------------------------------------------------------------------------

    @Test fun remoteChangesCancelAlarmsAndReschedule() {
        val (relay, a, b) = trio()
        a.medicine("Met"); a.dose(1, 4_000_000_000_000L); a.dose(1, 4_000_000_100_000L)
        a.push(); relay.pump(); relay.pump()
        b.fx.calls.clear()
        val medUid = a.uid("medicines", "1=1")
        val doseUid = a.uid("doses", "scheduledAt = 4000000000000")
        val doseUid2 = a.uid("doses", "scheduledAt = 4000000100000")

        // the helper marks a dose TAKEN and the other SKIPPED... first only the person's phone reacts to what it receives
        Thread.sleep(3)
        b.dose(1, 4_000_000_200_000L)
        x(b.c, "UPDATE doses SET status = 'TAKEN', actedAt = 5 WHERE scheduledAt = 4000000000000")
        b.push(); relay.pump()
        assertEquals(listOf("cancelDose:$doseUid", "reschedule", "widget"), a.fx.calls.filterNot { it.startsWith("changed") })
        a.fx.calls.clear()
        // a dose that is still DUE (a new one) cancels nothing
        assertEquals(1L, n(a.c, "SELECT count(*) FROM doses WHERE scheduledAt = 4000000200000"))

        Thread.sleep(3)
        x(b.c, "UPDATE doses SET status = 'SKIPPED' WHERE scheduledAt = 4000000100000")
        b.push(); relay.pump()
        assertEquals(listOf("cancelDose:$doseUid2", "reschedule", "widget"), a.fx.calls)
        a.fx.calls.clear()

        // an edit of the medicine: its doses are planned again
        Thread.sleep(3)
        x(b.c, "UPDATE medicines SET times = '09:00'")
        b.push(); relay.pump()
        assertEquals(listOf("changed:$medUid", "reschedule", "widget"), a.fx.calls)
        a.fx.calls.clear()

        // stopped, then deleted
        Thread.sleep(3)
        x(b.c, "UPDATE medicines SET active = 0")
        b.push(); relay.pump()
        assertEquals(listOf("stopped:$medUid", "reschedule", "widget"), a.fx.calls)
        a.fx.calls.clear()
        Thread.sleep(3)
        x(b.c, "DELETE FROM medicines")
        b.push(); relay.pump()
        assertEquals("stopped:$medUid", a.fx.calls.first())
        assertTrue(a.fx.calls.contains("widget"))
        a.fx.calls.clear()

        // profile and notes only refresh the widget
        b.note("hello"); x(b.c, "INSERT INTO profile(id,name,dob,sex,bloodGroup,hospitalId,conditions,allergies,doctorName,doctorPhone,onBloodThinner,notes,plan) VALUES(1,'X','','','','','','','','',0,'','')")
        b.push(); relay.pump()
        assertEquals(listOf("widget"), a.fx.calls)
    }
}
