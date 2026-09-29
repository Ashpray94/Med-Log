package com.suryaprakash.medlog

import com.suryaprakash.medlog.sync.InMemorySyncStore
import com.suryaprakash.medlog.sync.Op
import com.suryaprakash.medlog.sync.SyncEngine
import com.suryaprakash.medlog.sync.Version
import com.suryaprakash.medlog.sync.WireJson
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Per-column last write wins, on the in-memory store (the spec of the SQLite one, which PersonaSharingTest runs on real triggers). */
class SyncColumnsTest {
    private var clock = 1000L
    private val relay = FakeRelay()

    private inner class Phone(val id: String) {
        val store = InMemorySyncStore(id) { clock }
        val engine = SyncEngine(store, relay.sender(id), gapHelloIntervalMs = 0)
        var published = 0L
        init { relay.join(id, engine) }
        fun push() { engine.publishLocal(published); published = store.localSeq }
        fun hello() = relay.publish(id, engine.hello())
        /** The messages for this phone's changes since the last push, without sending them. */
        fun take(): List<String> { val m = engine.localChanges(published); published = store.localSeq; return m }
    }

    private fun tick() { clock += 10 }
    private fun row(vararg kv: Pair<String, Any>) = JSONObject().apply { kv.forEach { put(it.first, it.second) } }
    private fun same(vararg p: Phone) { for (i in 1 until p.size) assertEquals(p[0].store.snapshot(), p[i].store.snapshot()) }
    private fun m(purpose: String = "", times: String = "08:00", active: Int = 1) = row("name" to "Dexa", "purpose" to purpose, "times" to times, "active" to active)

    @Test fun differentColumnsEditedAtOnceBothSurviveInAnyOrder() {
        val a = Phone("A"); val b = Phone("B")
        a.store.localUpsert("medicines", "m", m()); a.push(); relay.pump(); same(a, b)
        tick(); a.store.localUpsert("medicines", "m", m(purpose = "nausea"))
        tick(); b.store.localUpsert("medicines", "m", m(times = "07:00"))
        val fromA = a.take(); val fromB = b.take()
        fromB.forEach { a.engine.onMessage(it) }; fromA.forEach { b.engine.onMessage(it) }
        same(a, b)
        for (p in listOf(a, b)) { val r = p.store.row("medicines", "m")!!; assertEquals("nausea", r.getString("purpose")); assertEquals("07:00", r.getString("times")) }
    }

    @Test fun aPhoneThatOnlyHeardTheFirstEditStillGetsTheOthersColumnFromAnyPeer() {
        // the case a row-level version cannot do: A's edit (purpose) and B's edit (times) merge on A; C heard only A's message
        val a = Phone("A"); val b = Phone("B"); val c = Phone("C")
        a.store.localUpsert("medicines", "m", m()); a.push(); relay.pump(); same(a, b, c)
        tick(); a.store.localUpsert("medicines", "m", m(purpose = "nausea"))
        tick(); b.store.localUpsert("medicines", "m", m(times = "07:00"))
        val fromA = a.take(); val fromB = b.take()
        fromA.forEach { c.engine.onMessage(it) }                      // C hears A only; B's message to C is lost
        fromB.forEach { a.engine.onMessage(it) }; fromA.forEach { b.engine.onMessage(it) }
        assertEquals("08:00", c.store.row("medicines", "m")!!.getString("times"))
        c.hello(); relay.pump()                                        // C asks: whoever holds B's column answers
        same(a, b, c)
        assertEquals("07:00", c.store.row("medicines", "m")!!.getString("times"))
        assertEquals("nausea", c.store.row("medicines", "m")!!.getString("purpose"))
    }

    @Test fun theSameColumnEditedOnBothTheLaterEditWins() {
        val a = Phone("A"); val b = Phone("B")
        a.store.localUpsert("medicines", "m", m()); a.push(); relay.pump()
        tick(); a.store.localUpsert("medicines", "m", m(times = "08:30"))
        tick(); b.store.localUpsert("medicines", "m", m(times = "07:00"))
        a.push(); b.push(); relay.pump(); same(a, b)
        assertEquals("07:00", a.store.row("medicines", "m")!!.getString("times"))
    }

    @Test fun aDeleteWinsOnlyOverColumnsOlderThanIt_inEitherOrder() {
        for (order in listOf(true, false)) {
            val a = Phone("A"); val b = Phone("B")
            a.store.localUpsert("appointments", "x", row("doctor" to "Dr", "place" to "OPD")); a.push(); relay.pump(); same(a, b)
            tick(); b.store.localUpsert("appointments", "x", row("doctor" to "Dr", "place" to "Clinic"))   // a column edit ...
            tick(); a.store.localDelete("appointments", "x")                                               // ... then a later delete: gone
            val fromA = a.take(); val fromB = b.take()
            if (order) { fromA.forEach { b.engine.onMessage(it) }; fromB.forEach { a.engine.onMessage(it) } } else { fromB.forEach { a.engine.onMessage(it) }; fromA.forEach { b.engine.onMessage(it) } }
            same(a, b)
            assertNull(a.store.row("appointments", "x")); assertTrue(b.store.version("appointments", "x")!!.del)
            // an edit newer than the delete brings the row back with the editor's columns
            tick(); b.store.localUpsert("appointments", "x", row("doctor" to "Dr", "place" to "Home"))
            b.push(); relay.pump(); same(a, b)
            assertEquals("Home", a.store.row("appointments", "x")!!.getString("place"))
        }
    }

    @Test fun columnVersionsSurviveTheWire_compactlyGroupedByVersion() {
        val v1 = Version(10, "A", "A", 1, false); val v2 = Version(20, "B", "B", 4, false)
        val o = Op("medicines", "m", "B", 5, 30, "B", false, m(), mapOf("name" to v1, "purpose" to v1, "times" to v2))
        val back = WireJson.readOp(JSONObject(WireJson.op(o).toString()))!!
        assertEquals(o.cv, back.cv)
        assertEquals(v1, back.colVersion("purpose")); assertEquals(v2, back.colVersion("times"))
        assertEquals(Version(30, "B", "B", 5, false), back.colVersion("active"))      // not listed = the op's own
        assertEquals(2, JSONObject(WireJson.op(o).toString()).getJSONArray("c").length()) // three columns, two versions, two groups
    }

    @Test fun anOpWithoutColumnVersionsFromAnOlderAppGivesEveryColumnTheOpsVersion() {
        val a = Phone("A")
        val old = JSONObject("{\"kind\":\"ops\",\"from\":\"Z\",\"covers\":{\"Z\":[0,1]},\"ops\":[{\"t\":\"medicines\",\"u\":\"m\",\"o\":\"Z\",\"s\":1,\"a\":5000,\"b\":\"Z\",\"r\":{\"name\":\"Old\",\"purpose\":\"p\"}}]}")
        a.engine.onMessage(old.toString())
        assertEquals("Old", a.store.row("medicines", "m")!!.getString("name"))
        assertEquals(Version(5000, "Z", "Z", 1, false), a.store.version("medicines", "m"))
    }
}
