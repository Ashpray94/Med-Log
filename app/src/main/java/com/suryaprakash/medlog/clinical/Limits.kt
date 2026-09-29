package com.suryaprakash.medlog.clinical

import org.json.JSONObject

/**
 * One measure's lines. null = not set. Values are in the app's units
 * (mmHg, %, mg/dL, °F, per minute, times a day, days).
 */
data class Band(val amberLow: Double? = null, val redLow: Double? = null, val amberHigh: Double? = null, val redHigh: Double? = null) {
    val isEmpty get() = amberLow == null && redLow == null && amberHigh == null && redHigh == null

    fun toJson(): JSONObject = JSONObject().also { o ->
        amberLow?.let { o.put("amberLow", it) }; redLow?.let { o.put("redLow", it) }
        amberHigh?.let { o.put("amberHigh", it) }; redHigh?.let { o.put("redHigh", it) }
    }

    companion object {
        fun fromJson(o: JSONObject?): Band {
            if (o == null) return Band()
            fun d(k: String) = if (o.has(k) && !o.isNull(k)) o.optDouble(k).takeIf { !it.isNaN() } else null
            return Band(d("amberLow"), d("redLow"), d("amberHigh"), d("redHigh"))
        }
    }
}

/**
 * The lines to use: each of the four lines comes from the helper's [helper] band when the helper set it,
 * otherwise from the general [defaults]. So a helper who sets only the high lines never switches off the low ones.
 */
fun effective(helper: Band?, defaults: Band): Band = Band(
    amberLow = helper?.amberLow ?: defaults.amberLow, redLow = helper?.redLow ?: defaults.redLow,
    amberHigh = helper?.amberHigh ?: defaults.amberHigh, redHigh = helper?.redHigh ?: defaults.redHigh,
)

/**
 * The lines a helper sets for one person, with the doctor's agreement. Kept inside the care plan (so it is
 * encrypted, backed up and synced). Where a line is set, it replaces the general number.
 */
data class Limits(
    /** keys: "bpSys", "bpDia", "spo2", "sugar", "temp", "pulse", "vomit", "loose", "constipationDays" */
    val bands: Map<String, Band> = emptyMap(),
    /** the helper ticked "the doctor said these are OK" */
    val doctorConfirmed: Boolean = false,
    /** "helper", "self" or a helper's name */
    val setBy: String = "",
    val setAt: Long = 0,
) {
    /** The band for [key], or null when the helper set nothing for it. */
    fun band(key: String): Band? = bands[key]?.takeIf { !it.isEmpty }

    fun toJson(): JSONObject = JSONObject()
        .put("bands", JSONObject().also { o -> bands.filterValues { !it.isEmpty }.forEach { (k, b) -> o.put(k, b.toJson()) } })
        .put("doctorConfirmed", doctorConfirmed).put("setBy", setBy).put("setAt", setAt)

    companion object {
        /** Tolerant: anything missing or broken reads as "not set". */
        fun fromJson(o: JSONObject?): Limits {
            if (o == null) return Limits()
            return runCatching {
                val b = o.optJSONObject("bands")
                val bands = b?.keys()?.asSequence()?.associateWith { Band.fromJson(b.optJSONObject(it)) }.orEmpty()
                Limits(bands, o.optBoolean("doctorConfirmed"), o.optString("setBy"), o.optLong("setAt"))
            }.getOrDefault(Limits())
        }
    }
}
