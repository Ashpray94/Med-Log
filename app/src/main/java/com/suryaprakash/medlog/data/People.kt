package com.suryaprakash.medlog.data

import android.content.Context
import android.util.Base64
import com.suryaprakash.medlog.medlog
import org.json.JSONArray
import org.json.JSONObject

/** Someone this phone looks after: paired once, face to face. Their messages arrive here, near or far. */
data class CaredFor(val pairId: String, val key: String, val name: String, val familyKey: String = "") {
    val keyBytes: ByteArray get() = Base64.decode(key, Base64.NO_WRAP)
    val familyBytes: ByteArray? get() = familyKey.takeIf { it.isNotBlank() }?.let { Base64.decode(it, Base64.NO_WRAP) }
}

/**
 * The people this phone helps (any number). A phone can also keep its own health at the same time:
 * helping is not a separate mode, it is this list being non-empty.
 */
object People {
    private const val KEY = "people"

    fun all(ctx: Context): List<CaredFor> {
        val s = ctx.medlog.settings
        migrate(ctx)
        val a = runCatching { JSONArray(s.getString(KEY) ?: "[]") }.getOrDefault(JSONArray())
        return (0 until a.length()).map { a.getJSONObject(it) }.map {
            CaredFor(it.getString("pairId"), it.getString("key"), it.optString("name"), it.optString("family"))
        }
    }

    fun any(ctx: Context) = all(ctx).isNotEmpty()

    fun byPairId(ctx: Context, pairId: String) = all(ctx).firstOrNull { it.pairId == pairId }

    /** Adds or replaces (same pairing id) one person. */
    fun put(ctx: Context, p: CaredFor) = save(ctx, all(ctx).filter { it.pairId != p.pairId } + p)

    fun remove(ctx: Context, pairId: String) = save(ctx, all(ctx).filter { it.pairId != pairId })

    /** Their names in plain words: "Amma", "Amma and Ravi", "Amma, Ravi and Latha". */
    fun names(ctx: Context): String {
        val n = all(ctx).map { it.name.ifBlank { "your person" } }
        return when (n.size) { 0 -> ""; 1 -> n[0]; else -> n.dropLast(1).joinToString(", ") + " and " + n.last() }
    }

    private fun save(ctx: Context, list: List<CaredFor>) {
        val a = JSONArray()
        list.forEach { a.put(JSONObject().put("pairId", it.pairId).put("key", it.key).put("name", it.name).put("family", it.familyKey)) }
        ctx.medlog.settings.putString(KEY, a.toString())
    }

    /** Phones paired before this list existed kept one person in single settings: move them in once. */
    private fun migrate(ctx: Context) {
        val s = ctx.medlog.settings
        val id = s.getString("pair_id") ?: return
        val key = s.getString("pair_key")
        if (s.getString(KEY) == null && key != null) {
            val a = JSONArray().put(JSONObject().put("pairId", id).put("key", key).put("name", s.value.pairedWith).put("family", s.getString("family_key") ?: ""))
            s.putString(KEY, a.toString())
        }
        s.putString("pair_id", null); s.putString("pair_key", null)
    }
}
