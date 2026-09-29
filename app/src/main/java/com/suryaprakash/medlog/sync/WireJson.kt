package com.suryaprakash.medlog.sync

import org.json.JSONArray
import org.json.JSONObject

/**
 * The messages that go over the relay:
 *   {"kind":"ops","from":dev,"ops":[{"t":tbl,"u":uid,"o":origin,"s":oseq,"a":at,"b":by,"d":1?,"r":{row}?}, ...]}
 *   {"kind":"hello","from":dev,"have":{origin:oseq, ...}}
 */
object WireJson {
    const val MAX_BATCH_BYTES = 200 * 1024

    fun op(o: Op): JSONObject = JSONObject().apply {
        put("t", o.tbl); put("u", o.uid); put("o", o.origin); put("s", o.oseq); put("a", o.at); put("b", o.by)
        if (o.del) put("d", 1)
        if (!o.del && o.row != null) put("r", o.row)
    }

    fun readOp(j: JSONObject): Op? {
        val tbl = j.optString("t"); val uid = j.optString("u"); val origin = j.optString("o")
        if (tbl.isEmpty() || uid.isEmpty() || origin.isEmpty() || !j.has("s") || !j.has("a")) return null
        val del = j.optInt("d", 0) == 1
        val row = if (del) null else j.optJSONObject("r")
        if (!del && row == null) return null
        return Op(tbl, uid, origin, j.getLong("s"), j.getLong("a"), j.optString("b"), del, row)
    }

    fun opsMessage(from: String, ops: List<Op>): String = opsMessageOf(from, ops.map { op(it).toString() })

    private fun opsMessageOf(from: String, encodedOps: List<String>): String =
        "{\"kind\":\"ops\",\"from\":" + JSONObject.quote(from) + ",\"ops\":[" + encodedOps.joinToString(",") + "]}"

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

    /** Splits ops into messages that each serialize to at most [maxBytes] (UTF-8). One op bigger than that goes alone. */
    fun batches(from: String, ops: List<Op>, maxBytes: Int = MAX_BATCH_BYTES): List<String> {
        val out = ArrayList<String>()
        val base = opsMessageOf(from, emptyList()).toByteArray().size
        var cur = ArrayList<String>()
        var size = base
        for (o in ops) {
            val enc = op(o).toString()
            val n = enc.toByteArray().size + 1 // plus the comma
            if (cur.isNotEmpty() && size + n > maxBytes) {
                out += opsMessageOf(from, cur); cur = ArrayList(); size = base
            }
            cur += enc; size += n
        }
        if (cur.isNotEmpty()) out += opsMessageOf(from, cur)
        return out
    }
}
