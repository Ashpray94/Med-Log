package com.suryaprakash.medlog

import com.suryaprakash.medlog.sync.Applied
import com.suryaprakash.medlog.sync.InMemorySyncStore
import com.suryaprakash.medlog.sync.Op
import com.suryaprakash.medlog.sync.SyncEngine
import com.suryaprakash.medlog.sync.SyncSender
import com.suryaprakash.medlog.sync.WireJson
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class SyncEngineTest {
    private var clock = 1000L
    private val relay = FakeRelay()

    private inner class Phone(val id: String) {
        val store = InMemorySyncStore(id) { clock }
        val engine = SyncEngine(store, relay.sender(id))
        var published = 0L
        init { relay.join(id, engine) }
        fun push() { engine.publishLocal(published); published = store.localSeq }
        fun hello() = relay.publish(id, engine.hello())
    }

    private fun tick() { clock += 10 }
    private fun row(vararg kv: Pair<String, Any>) = JSONObject().apply { kv.forEach { put(it.first, it.second) } }
    private fun same(vararg p: Phone) { for (i in 1 until p.size) assertEquals(p[0].store.snapshot(), p[i].store.snapshot()) }

    @Test fun twoDevicesConvergeOnCreateUpdateDelete() {
        val a = Phone("A"); val b = Phone("B")
        a.store.localUpsert("medicines", "m1", row("name" to "Metformin")); tick()
        b.store.localUpsert("notes", "n1", row("text" to "dizzy")); tick()
        a.push(); b.push(); relay.pump(); same(a, b)
        assertEquals("dizzy", a.store.row("notes", "n1")!!.getString("text"))
        b.store.localUpsert("medicines", "m1", row("name" to "Metformin 500")); tick()
        b.push(); relay.pump()
        assertEquals("Metformin 500", a.store.row("medicines", "m1")!!.getString("name"))
        a.store.localDelete("notes", "n1"); tick()
        a.push(); relay.pump(); same(a, b)
        assertNull(b.store.row("notes", "n1"))
        assertTrue(b.store.version("notes", "n1")!!.del)
    }

    @Test fun offlinePhoneCatchesUpFromAnyPeerAfterMessagesExpired() {
        val a = Phone("A"); val b = Phone("B"); val c = Phone("C")
        a.store.localUpsert("medicines", "m1", row("name" to "X")); tick()
        a.push(); relay.pump(); same(a, b, c)
        relay.setOnline("A", false)
        b.store.localUpsert("notes", "n1", row("text" to "b1")); tick()
        b.push(); relay.pump()
        assertEquals("b1", c.store.row("notes", "n1")!!.getString("text"))
        a.store.localUpsert("notes", "n2", row("text" to "a-offline")); tick() // A edits while cut off
        // the relay forgets B's message before A returns
        relay.advance(13 * 3600_000L)
        relay.setOnline("A", true)
        relay.pump()
        assertNull(a.store.row("notes", "n1"))
        // A says hello; B and C both answer, in any case A ends up with B's data
        a.hello(); relay.pump()
        assertEquals("b1", a.store.row("notes", "n1")!!.getString("text"))
        a.push(); relay.pump()
        same(a, b, c)
        // a brand new phone can be caught up by C alone, including A's data
        relay.setOnline("A", false); relay.setOnline("B", false)
        val d = Phone("D"); d.hello(); relay.pump(); same(c, d)
        assertEquals("a-offline", d.store.row("notes", "n2")!!.getString("text"))
    }

    @Test fun concurrentEditsResolveTheSameEverywhere() {
        val a = Phone("A"); val b = Phone("B"); val c = Phone("C")
        a.store.localUpsert("notes", "n", row("text" to "start")); a.push(); relay.pump(); tick()
        // same millisecond on A and B: device id decides, so B wins
        a.store.localUpsert("notes", "n", row("text" to "from A"))
        b.store.localUpsert("notes", "n", row("text" to "from B"))
        a.push(); b.push(); relay.pump(); same(a, b, c)
        assertEquals("from B", c.store.row("notes", "n")!!.getString("text"))
        // a later edit beats the tie
        tick(); a.store.localUpsert("notes", "n", row("text" to "later A")); a.push(); relay.pump()
        same(a, b, c); assertEquals("later A", b.store.row("notes", "n")!!.getString("text"))
    }

    @Test fun deleteVersusConcurrentUpdateLaterWins() {
        val a = Phone("A"); val b = Phone("B")
        a.store.localUpsert("notes", "n", row("text" to "x")); a.push(); relay.pump(); tick()
        a.store.localDelete("notes", "n"); tick()
        b.store.localUpsert("notes", "n", row("text" to "edited after")) // later than the delete
        a.push(); b.push(); relay.pump(); same(a, b)
        assertEquals("edited after", a.store.row("notes", "n")!!.getString("text")) // brought back
        tick(); b.store.localDelete("notes", "n"); b.push(); relay.pump(); same(a, b)
        assertNull(a.store.row("notes", "n"))
        // an update older than the tombstone stays dead
        val old = Op("notes", "n", "Z", 1, 5, "Z", false, row("text" to "ancient"))
        a.engine.onMessage(WireJson.opsMessage("Z", listOf(old)))
        assertNull(a.store.row("notes", "n"))
    }

    @Test fun doseBeforeMedicineIsParkedThenApplied() {
        val a = Phone("A"); val b = Phone("B")
        a.store.localUpsert("medicines", "m1", row("name" to "M")); tick()
        a.store.localUpsert("doses", "d1", row("medicineUid" to "m1", "state" to "TAKEN")); tick()
        val all = a.store.changedSince(emptyMap()).sortedBy { it.oseq }
        val med = all.first { it.tbl == "medicines" }; val dose = all.first { it.tbl == "doses" }
        val r1 = b.engine.onMessage(WireJson.opsMessage("A", listOf(dose)))
        assertEquals(Applied(0, 0, 1), r1)
        assertNull(b.store.row("doses", "d1")); assertEquals(1, b.engine.pendingCount)
        val r2 = b.engine.onMessage(WireJson.opsMessage("A", listOf(med)))
        assertEquals(2, r2.applied); assertEquals(0, r2.parked)
        assertNotNull(b.store.row("doses", "d1")); assertEquals(0, b.engine.pendingCount)
        same(a, b)
        // a batch holding both in the wrong order also works
        val c = Phone("C")
        assertEquals(2, c.engine.onMessage(WireJson.opsMessage("A", listOf(dose, med))).applied)
        same(a, c)
    }

    @Test fun noteGroupParentIsParkedToo() {
        val a = Phone("A"); val b = Phone("B")
        a.store.localUpsert("notes", "g", row("text" to "group")); tick()
        a.store.localUpsert("notes", "n", row("text" to "child", "groupUid" to "g"))
        val ops = a.store.changedSince(emptyMap()).sortedByDescending { it.oseq }
        assertEquals(1, b.engine.onMessage(WireJson.opsMessage("A", listOf(ops[0]))).parked)
        b.engine.onMessage(WireJson.opsMessage("A", listOf(ops[1])))
        same(a, b)
    }

    @Test fun duplicatesAndReplaysAreIdempotent() {
        val a = Phone("A"); val b = Phone("B")
        a.store.localUpsert("notes", "n", row("text" to "x")); tick()
        a.store.localDelete("notes", "n")
        val msgs = a.engine.localChanges(0)
        for (m in msgs + msgs) b.engine.onMessage(m)
        val snap = b.store.snapshot()
        assertEquals(0, b.engine.onMessage(msgs[0]).applied)
        assertEquals(snap, b.store.snapshot())
        // own echo is ignored
        assertEquals(Applied.NONE, a.engine.onMessage(msgs[0]))
    }

    @Test fun bigSetsSplitUnderTheLimit() {
        val a = Phone("A"); val b = Phone("B")
        val filler = "x".repeat(2000)
        for (i in 0 until 400) { a.store.localUpsert("notes", "n$i", row("text" to filler + i)); tick() }
        val msgs = a.engine.localChanges(0)
        assertTrue(msgs.size > 3)
        msgs.forEach { assertTrue(it.toByteArray().size <= 200 * 1024) }
        msgs.forEach { b.engine.onMessage(it) }
        same(a, b)
        // asking for only what is newer sends less
        assertEquals(0, a.engine.localChanges(a.store.localSeq).size)
        // a single op bigger than the limit still goes, alone
        val big = Op("notes", "big", "A", 1, 1, "A", false, row("text" to "y".repeat(500)))
        assertEquals(3, WireJson.batches("A", listOf(big, big, big), 300).size)
        // multi-byte text is measured in bytes
        val jp = Op("notes", "jp", "A", 1, 1, "A", false, row("text" to "薬".repeat(100)))
        WireJson.batches("A", List(20) { jp }, 1000).forEach { assertTrue(it.toByteArray().size <= 1000) }
    }

    @Test fun helloIsAnsweredOnlyWithWhatIsMissing() {
        val a = Phone("A"); val b = Phone("B")
        a.store.localUpsert("notes", "n1", row("text" to "1")); a.push(); relay.pump()
        a.store.localUpsert("notes", "n2", row("text" to "2")) // not published
        val before = relay.sent
        b.hello(); relay.pump()
        assertEquals(before + 2, relay.sent) // hello + one answer
        assertNotNull(b.store.row("notes", "n2"))
        b.hello(); relay.pump()
        assertEquals(before + 3, relay.sent) // hello only, nothing missing
        // a full hello asks for everything again
        relay.publish("B", b.engine.hello(full = true)); relay.pump()
        assertEquals(before + 5, relay.sent)
    }

    @Test fun badMessagesAreIgnored() {
        val a = Phone("A")
        for (m in listOf("", "nope", "{}", """{"kind":"ops","from":"Z","ops":[{"t":"notes"}]}""", """{"kind":"weird","from":"Z"}"""))
            assertEquals(Applied.NONE, a.engine.onMessage(m))
    }

    @Test fun wireRoundTrip() {
        val ops = listOf(
            Op("notes", "n", "A", 3, 99, "A", false, row("text" to "héllo \"q\"", "n" to 5)),
            Op("notes", "m", "B", 4, 100, "B", true, null),
        )
        val back = WireJson.readOps(JSONObject(WireJson.opsMessage("A", ops)))
        assertEquals(2, back.size)
        assertEquals(ops[1], back[1])
        assertEquals(ops[0].version, back[0].version)
        assertEquals("héllo \"q\"", back[0].row!!.getString("text"))
        val h = JSONObject(WireJson.hello("A", mapOf("A" to 3L, "B" to 9L)))
        assertEquals(mapOf("A" to 3L, "B" to 9L), WireJson.readHave(h))
    }

    @Test fun randomOpsInAnyOrderGiveTheSameState() {
        val rnd = Random(42)
        val devices = listOf("A", "B", "C")
        val seq = HashMap<String, Long>()
        val ops = ArrayList<Op>()
        repeat(300) {
            val d = devices[rnd.nextInt(3)]
            val s = (seq[d] ?: 0L) + 1; seq[d] = s
            val tbl = listOf("medicines", "notes", "doses")[rnd.nextInt(3)]
            val uid = "u${rnd.nextInt(12)}"
            val at = 100L + rnd.nextInt(40) // many ties on purpose
            val del = rnd.nextInt(4) == 0
            val r = if (del) null else row("v" to rnd.nextInt(1000), "medicineUid" to "u${rnd.nextInt(12)}")
            ops += Op(tbl, uid, d, s, at, d, del, r)
        }
        // every uid also has an oldest medicine, so the doses always have a parent somewhere
        val base = (0 until 12).map { Op("medicines", "u$it", "Z", (it + 1).toLong(), 1, "Z", false, row("v" to 0)) }
        val all = base + ops
        var expected: String? = null
        for (round in 0 until 8) {
            val s = InMemorySyncStore("Q$round") { 0 }
            val e = SyncEngine(s, SyncSender { })
            val shuffled = all.shuffled(Random(round.toLong()))
            var i = 0
            while (i < shuffled.size) {
                val n = 1 + rnd.nextInt(25)
                e.onMessage(WireJson.opsMessage("X", shuffled.subList(i, minOf(i + n, shuffled.size))))
                i += n
            }
            assertEquals(0, e.pendingCount)
            val snap = s.snapshot()
            if (expected == null) expected = snap else assertEquals(expected, snap)
            assertEquals(seq, s.have().filterKeys { it != "Z" })
        }
    }

    @Test fun flakyRelayStillConvergesWithFullHellos() {
        val ph = listOf(Phone("A"), Phone("B"), Phone("C"))
        relay.dropRate = 0.4
        val rnd = Random(7)
        repeat(60) {
            val p = ph[rnd.nextInt(3)]
            p.store.localUpsert("notes", "n${rnd.nextInt(10)}", row("v" to it)); tick()
            if (rnd.nextInt(5) == 0) p.store.localDelete("notes", "n${rnd.nextInt(10)}")
            p.push(); relay.pump()
        }
        relay.dropRate = 0.0
        // a full hello is the safety net when messages were lost
        ph.forEach { relay.publish(it.id, it.engine.hello(full = true)) }
        relay.pump()
        same(ph[0], ph[1], ph[2])
    }

    @Test fun heldMessagesArriveLater() {
        val a = Phone("A"); val b = Phone("B")
        relay.holdMs = 5000
        a.store.localUpsert("notes", "n", row("v" to 1)); a.push(); relay.pump()
        assertNull(b.store.row("notes", "n"))
        relay.now += 5000; relay.pump()
        same(a, b)
    }
}
