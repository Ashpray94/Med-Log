package com.suryaprakash.medlog

import com.suryaprakash.medlog.data.CaredFor
import com.suryaprakash.medlog.data.ReplicaFiles
import com.suryaprakash.medlog.data.ReplicaPlan
import com.suryaprakash.medlog.data.Viewing
import com.suryaprakash.medlog.sync.SqlSyncStore
import com.suryaprakash.medlog.sync.SyncEffects
import com.suryaprakash.medlog.sync.SyncRunner
import com.suryaprakash.medlog.sync.SyncSql
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.util.concurrent.Executors

/** Helper-phone replicas (plan B.5): file names and lifecycle, the "runs on their phone" gate, and both directions of sharing with the person's phone. */
class ReplicaTest {
    private val files = ArrayList<File>()
    private val conns = ArrayList<Connection>()
    private val pools = ArrayList<java.util.concurrent.ExecutorService>()

    @After fun close() { pools.forEach { it.shutdownNow() }; conns.forEach { runCatching { it.close() } }; files.forEach { it.delete() } }

    // ───────── names and lifecycle ─────────

    @Test fun replicaFileNamesAreSafeAndNeverTheOwnDatabase() {
        assertEquals("medlog-abc123.db", ReplicaFiles.name("abc123"))
        assertEquals("medlog-a_b_c-d.db", ReplicaFiles.name("a/b+c-d"))
        assertEquals("p-abc", ReplicaFiles.channel("abc"))
        assertTrue(ReplicaFiles.name("x") != "medlog.db")
    }

    @Test fun onlyPeopleWithAFamilyKeyGetAReplicaAndTheDiffSaysWhatToAttachAndDetach() {
        val people = listOf(CaredFor("a", "k", "Amma", "fk"), CaredFor("b", "k", "Ravi", ""), CaredFor("c", "k", "Latha", "fk2"))
        assertEquals(listOf("a", "c"), ReplicaPlan.wanted(people).map { it.pairId })
        val d = ReplicaPlan.diff(listOf("p-a", "p-c"), listOf("p-a", "p-old"))
        assertEquals(listOf("p-c"), d.attach)
        assertEquals(listOf("p-old"), d.detach)
        assertEquals(ReplicaPlan.Diff(emptyList(), emptyList()), ReplicaPlan.diff(listOf("p-a"), listOf("p-a")))
    }

    @Test fun aChannelWhoseFamilyKeyChangedIsDetachedAndAttachedAgainWithTheNewKey_B78() {
        // Kamala wiped and restored: her family key is new, the channel p-a is still attached with the old one
        val d = ReplicaPlan.diffKeys(mapOf("p-a" to "newKey", "p-c" to "k2"), mapOf("p-a" to "oldKey", "p-c" to "k2", "p-old" to "x"))
        assertEquals(listOf("p-a"), d.attach)
        assertEquals(listOf("p-old", "p-a"), d.detach)
        assertEquals(ReplicaPlan.Diff(emptyList(), emptyList()), ReplicaPlan.diffKeys(mapOf("p-a" to "k"), mapOf("p-a" to "k")))
        assertEquals(ReplicaPlan.Diff(listOf("p-n"), emptyList()), ReplicaPlan.diffKeys(mapOf("p-n" to "k"), emptyMap()))
    }

    @Test fun unpairDeletesTheReplicaAndItsSideFilesAndNothingElse() {
        val dir = java.nio.file.Files.createTempDirectory("dbs").toFile().also { it.deleteOnExit() }
        val own = File(dir, "medlog.db").also { it.writeText("own") }
        for (f in ReplicaFiles.files(dir, "amma") + ReplicaFiles.files(dir, "ravi")) f.writeText("x")
        assertTrue(ReplicaFiles.exists(dir, "amma"))
        assertTrue(ReplicaFiles.delete(dir, "amma"))
        assertFalse(ReplicaFiles.exists(dir, "amma"))
        assertTrue(File(dir, "medlog-amma.db-wal").let { !it.exists() })
        assertTrue(ReplicaFiles.exists(dir, "ravi"))
        assertTrue(own.exists())
        assertTrue(ReplicaFiles.delete(dir, "nobody"))   // nothing to delete is fine
        // Delete everything: every replica goes, the own file is left to the wipe
        assertEquals(4, ReplicaFiles.deleteAll(dir))
        assertEquals(listOf("medlog.db"), dir.list()!!.toList())
        dir.deleteRecursively()
    }

    // ───────── the gate ─────────

    @Test fun theGateIsOffOnTheOwnMedLogAndSaysWhoseWhenAReplicaIsOpen() {
        val v = Viewing()
        assertFalse(v.active); assertNull(v.notice())
        v.open("p1", "Amma")
        assertTrue(v.active)
        assertEquals("This runs on Amma's phone.", v.notice())
        v.open("p2", "")
        assertEquals("This runs on their phone.", v.notice())
        v.back()
        assertFalse(v.active); assertNull(v.notice()); assertNull(v.state.value)
    }

    // ───────── both directions over real SQLite ─────────

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
    private fun one(c: Connection, sql: String): Any? = JdbcSqlDb(c).query(sql).firstOrNull()?.values?.firstOrNull()

    private class Fx : SyncEffects {
        val calls = ArrayList<String>()
        override suspend fun medicineChanged(uid: String) { calls += "changed" }
        override suspend fun medicineStopped(uid: String) { calls += "stopped" }
        override suspend fun doseClosed(uid: String) { calls += "cancelDose" }
        override suspend fun reschedule() { calls += "reschedule" }
        override fun refreshWidget() { calls += "widget" }
    }

    private inner class Ph(val name: String, relay: FakeRelay, val fx: SyncEffects) {
        val c = freshDb(name)
        val disp = Executors.newSingleThreadExecutor().also { pools += it }.asCoroutineDispatcher()
        val runner = SyncRunner({ SqlSyncStore(JdbcSqlDb(c)) }, { relay.publish(name, it) }, fx, disp, CoroutineScope(disp), debounceMs = 1_000_000L, helloEveryMs = 1_000_000L)
        init { relay.join(name) { json -> runBlocking { runner.onMessage(json) } } }
        fun push() = runBlocking { runner.publishNow() }
        fun medicine(n: String) = x(c, "INSERT INTO medicines(name,strength,form,amount,food,times,days,startDate,critical,asNeeded,minGapHours,purpose,active,bloodThinner,changedAt,changeNote,shape,color) VALUES('$n','','tablet','1','any','08:00','',1,0,0,4,'',1,0,1,'','','')")
    }

    @Test fun helperEditsInTheReplicaReachThePersonAndTheirPhoneReactsAndBackAgain() {
        val relay = FakeRelay()
        val fxPerson = Fx()
        val person = Ph("person", relay, fxPerson)
        val helperOwn = freshDb("helper-own")                       // the helper's own MedLog: never part of this family channel
        val replica = Ph("helper-of-amma", relay, SyncEffects.NONE) // its replica of Amma: no effects on the helper's phone
        x(helperOwn, "INSERT INTO notes(kind, occurredAt, createdAt, details, triage, triageReasons, text) VALUES('SYMPTOM', 1, 1, '{}', 'GREEN', '', 'my own')")

        person.medicine("Metformin")
        x(person.c, "INSERT INTO profile(id,name,dob,sex,bloodGroup,hospitalId,conditions,allergies,doctorName,doctorPhone,onBloodThinner,notes,plan) VALUES(1,'Amma','','','','','','','','',0,'','')")
        person.push(); relay.pump()
        // person -> helper: the replica is filled and nothing rang on the helper's phone
        assertEquals("Metformin", one(replica.c, "SELECT name FROM medicines"))
        assertEquals("Amma", one(replica.c, "SELECT name FROM profile"))
        assertEquals(0L, one(replica.c, "SELECT count(*) FROM notes"))

        // helper -> person: edit a medicine, add another, change the profile
        Thread.sleep(3)
        x(replica.c, "UPDATE medicines SET times = '08:00,20:00', purpose = 'sugar' WHERE name = 'Metformin'")
        replica.medicine("Aspirin")
        x(replica.c, "UPDATE profile SET conditions = 'diabetes'")
        fxPerson.calls.clear()
        replica.push(); relay.pump()
        assertEquals("08:00,20:00", one(person.c, "SELECT times FROM medicines WHERE name = 'Metformin'"))
        assertEquals("Aspirin", one(person.c, "SELECT name FROM medicines WHERE name = 'Aspirin'"))
        assertEquals("diabetes", one(person.c, "SELECT conditions FROM profile"))
        assertTrue("the person's phone reschedules: ${fxPerson.calls}", "changed" in fxPerson.calls && "reschedule" in fxPerson.calls && "widget" in fxPerson.calls)

        // helper stops a medicine (delete): the person's phone stops it
        Thread.sleep(3)
        x(replica.c, "DELETE FROM medicines WHERE name = 'Aspirin'")
        fxPerson.calls.clear()
        replica.push(); relay.pump()
        assertEquals(0L, one(person.c, "SELECT count(*) FROM medicines WHERE name = 'Aspirin'"))
        assertTrue(fxPerson.calls.toString(), "stopped" in fxPerson.calls)

        // person -> helper again, and the helper's own database was never touched
        Thread.sleep(3)
        x(person.c, "UPDATE medicines SET purpose = 'morning sugar' WHERE name = 'Metformin'")
        person.push(); relay.pump()
        assertEquals("morning sugar", one(replica.c, "SELECT purpose FROM medicines WHERE name = 'Metformin'"))
        assertEquals(0L, one(helperOwn, "SELECT count(*) FROM medicines"))
        assertEquals("my own", one(helperOwn, "SELECT text FROM notes"))
    }

    @Test fun twoPeopleOnOneHelperPhoneStayApart() {
        val ra = FakeRelay(); val rb = FakeRelay()   // one mailbox per family key
        val amma = Ph("amma", ra, Fx()); val ravi = Ph("ravi", rb, Fx())
        val ha = Ph("helper-a", ra, SyncEffects.NONE); val hb = Ph("helper-b", rb, SyncEffects.NONE)
        amma.medicine("AmmaPill"); ravi.medicine("RaviPill")
        amma.push(); ravi.push(); ra.pump(); rb.pump()
        assertEquals("AmmaPill", one(ha.c, "SELECT group_concat(name) FROM medicines"))
        assertEquals("RaviPill", one(hb.c, "SELECT group_concat(name) FROM medicines"))
    }
}
