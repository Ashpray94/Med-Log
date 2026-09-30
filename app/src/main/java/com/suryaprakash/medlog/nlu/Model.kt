package com.suryaprakash.medlog.nlu

import org.json.JSONArray
import org.json.JSONObject

/** Where a fact came from. The doctor page marks anything not SAID or ASKED with ◇. */
enum class Source { SAID, ASKED, INFERRED, TAPPED }

/** One recorded fact about a problem. [value] is Boolean, Int, Double, String or List<String>. */
data class Fact(
    val value: Any,
    val source: Source,
    /** false when the words were only a close match; the check screen asks about it. */
    val sure: Boolean = true,
    /** The person's own words, kept for the doctor page. */
    val quote: String? = null,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("v", when (value) { is List<*> -> JSONArray(value); else -> value })
        put("s", source.name)
        if (!sure) put("sure", false)
        quote?.let { put("q", it) }
    }

    companion object {
        fun fromJson(o: JSONObject): Fact {
            val raw = o.get("v")
            val v: Any = when (raw) {
                is JSONArray -> (0 until raw.length()).map { raw.getString(it) }
                is Number -> if (raw is Double || raw is Float) raw.toDouble() else raw.toInt()
                else -> raw
            }
            return Fact(v, Source.valueOf(o.optString("s", "SAID")), o.optBoolean("sure", true), o.optString("q").ifEmpty { null })
        }
    }
}

typealias Facts = MutableMap<String, Fact>

fun factsToJson(f: Map<String, Fact>): String = JSONObject().apply { f.forEach { (k, v) -> put(k, v.toJson()) } }.toString()

fun factsFromJson(s: String?): MutableMap<String, Fact> {
    if (s.isNullOrBlank()) return mutableMapOf()
    val o = JSONObject(s)
    return o.keys().asSequence().associateWith { Fact.fromJson(o.getJSONObject(it)) }.toMutableMap()
}

/** A problem found in what the person said. */
data class Mention(
    val problemId: String,
    /** "no fever", "didn't vomit" */
    var negated: Boolean = false,
    /** "vomiting stopped", "headache is gone" */
    var resolved: Boolean = false,
    val facts: Facts = mutableMapOf(),
    val matched: String = "",
    /** matched only approximately (speech-recognition slip); must be confirmed */
    val fuzzy: Boolean = false,
    val position: Int = 0,
)

data class Reading(val type: String, val v1: Double, val v2: Double? = null, val unit: String = "") {
    fun label(): String = when (type) {
        "bp" -> "BP ${v1.toInt()}/${v2?.toInt()}"
        "sugar" -> "Sugar ${v1.toInt()} mg/dL"
        "spo2" -> "Oxygen ${v1.toInt()}%"
        "pulse" -> "Pulse ${v1.toInt()}"
        "temp" -> "Temperature ${fmt1(v1)} °F"
        "weight" -> "Weight ${fmt1(v1)} kg"
        else -> "$type $v1"
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("type", type)
        put("v1", v1)
        if (v2 != null) put("v2", v2)
        if (unit.isNotBlank()) put("unit", unit)
    }
}

fun fmt1(d: Double): String = if (d % 1.0 == 0.0) d.toInt().toString() else String.format(java.util.Locale.US, "%.1f", d)

data class Parsed(
    val transcript: String,
    val mentions: List<Mention>,
    /** when it happened, if the person said ("an hour ago", "last night") */
    val occurredAt: Long?,
    val readings: List<Reading>,
    /** medicines the person said they took ("took a paracetamol") */
    val medicinesTaken: List<String>,
) {
    val main: Mention? get() = mentions.firstOrNull { !it.negated }
    val others: List<Mention> get() = mentions.filter { it !== main && !it.negated }
    val negatives: List<Mention> get() = mentions.filter { it.negated }
}
