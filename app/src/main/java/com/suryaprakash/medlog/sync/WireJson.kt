package com.suryaprakash.medlog.sync

import org.json.JSONArray
import org.json.JSONObject

/**
 * The messages that go over the relay:
 *   {"kind":"ops","from":dev,"ops":[{"t":tbl,"u":uid,"o":origin,"s":oseq,"a":at,"b":by,"d":1?,"r":{row}?,"c":[[at,by,origin,oseq,[col,...]],...]?}, ...]}
 *   {"kind":"hello","from":dev,"have":{origin:oseq, ...}}
 */
object WireJson {
    const val MAX_BATCH_BYTES = 200 * 1024

    fun op(o: Op): JSONObject = JSONObject().apply {
        put("t", o.tbl); put("u", o.uid); put("o", o.origin); put("s", o.oseq); put("a", o.at); put("b", o.by)
        if (o.del) put("d", 1)
        if (!o.del && o.row != null) put("r", o.row)
        if (!o.del && o.cv.isNotEmpty()) {
            // columns grouped by version: [[at, by, origin, oseq, [col, ...]], ...]
            val g = LinkedHashMap<Version, MutableList<String>>()
            for ((c, v) in o.cv.toSortedMap()) g.getOrPut(v) { ArrayList() } += c
            put("c", JSONArray().also { a -> g.forEach { (v, cs) -> a.put(JSONArray().put(v.at).put(v.by).put(v.origin).put(v.oseq).put(JSONArray(cs))) } })
        }
    }

    fun readOp(j: JSONObject): Op? {
        val tbl = j.optString("t"); val uid = j.optString("u"); val origin = j.optString("o")
        if (tbl.isEmpty() || uid.isEmpty() || origin.isEmpty() || !j.has("s") || !j.has("a")) return null
        val del = j.optInt("d", 0) == 1
        val row = if (del) null else j.optJSONObject("r")
        if (!del && row == null) return null
        val cv = HashMap<String, Version>()
        val groups = j.optJSONArray("c")
        if (!del && groups != null) for (i in 0 until groups.length()) {
            val g = groups.optJSONArray(i) ?: continue
            val cs = g.optJSONArray(4) ?: continue
            val v = Version(g.optLong(0), g.optString(1), g.optString(2), g.optLong(3), false)
            for (k in 0 until cs.length()) cv[cs.getString(k)] = v
        }
        return Op(tbl, uid, origin, j.getLong("s"), j.getLong("a"), j.optString("b"), del, row, cv)
    }

    /**
     * [covers] says, per origin, "these ops are EVERY current row of that origin whose oseq is in (from, to]". The receiver may
     * move its have over a range only when it starts at or below what it already has.
     */
    fun opsMessage(from: String, ops: List<Op>, covers: Map<String, Pair<Long, Long>> = emptyMap()): String =
        opsMessageOf(from, ops.map { op(it).toString() }, covers)

    private fun opsMessageOf(from: String, encodedOps: List<String>, covers: Map<String, Pair<Long, Long>>): String {
        val c = JSONObject()
        covers.toSortedMap().forEach { (k, v) -> c.put(k, org.json.JSONArray().put(v.first).put(v.second)) }
        return "{\"kind\":\"ops\",\"from\":" + JSONObject.quote(from) + ",\"covers\":" + c + ",\"ops\":[" + encodedOps.joinToString(",") + "]}"
    }

    fun readCovers(j: JSONObject): Map<String, Pair<Long, Long>> {
        val c = j.optJSONObject("covers") ?: return emptyMap()
        val out = HashMap<String, Pair<Long, Long>>()
        for (k in c.keys()) {
            val a = c.optJSONArray(k) ?: continue
            if (a.length() == 2) out[k] = a.optLong(0) to a.optLong(1)
        }
        return out
    }

    fun hello(from: String, have: Map<String, Long>): String = JSONObject().apply {
        put("kind", "hello"); put("from", from)
        put("have", JSONObject().also { h -> have.toSortedMap().forEach { (k, v) -> h.put(k, v) } })
    }.toString()

    fun readHave(j: JSONObject): Map<String, Long> {
        val h = j.optJSONObject("have") ?: return emptyMap()
        val out = HashMap<String, Long>()
        for (k in h.keys()) out[k] = h.optLong(k, 0L)
        return out
    }

    fun readOps(j: JSONObject): List<Op> {
        val a = j.optJSONArray("ops") ?: JSONArray()
        return (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let(::readOp) }
    }

    /**
     * Splits the ops of one [origin] into messages that each serialize to at most [maxBytes] (UTF-8). [ops] must be every current
     * row of that origin with oseq in ([coverFrom], [coverTo]]. They are sorted by oseq and each message covers the next
     * contiguous piece of the range, so together the messages cover exactly the whole range (also when there are no ops).
     * One op bigger than the limit goes alone.
     */
    fun batches(from: String, origin: String, ops: List<Op>, coverFrom: Long, coverTo: Long, maxBytes: Int = MAX_BATCH_BYTES): List<String> {
        if (coverTo <= coverFrom) return emptyList()
        val out = ArrayList<String>()
        val base = opsMessageOf(from, emptyList(), mapOf(origin to (Long.MAX_VALUE to Long.MAX_VALUE))).toByteArray().size
        var cur = ArrayList<String>()
        var last = coverFrom      // end of the range already sent
        var lastInCur = coverFrom // highest oseq in the current message
        var size = base
        for (o in ops.sortedBy { it.oseq }) {
            val enc = op(o).toString()
            val n = enc.toByteArray().size + 1 // plus the comma
            if (cur.isNotEmpty() && size + n > maxBytes) {
                out += opsMessageOf(from, cur, mapOf(origin to (last to lastInCur)))
                last = lastInCur; cur = ArrayList(); size = base
            }
            cur += enc; size += n; lastInCur = o.oseq
        }
        out += opsMessageOf(from, cur, mapOf(origin to (last to coverTo)))
        return out
    }
}
