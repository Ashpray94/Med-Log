package com.suryaprakash.medlog.speech

import android.content.Context
import org.json.JSONObject

/**
 * The whole app in the person's main language (plan 4.2): every screen, button, question and spoken line.
 *
 * Translations ship with the app (assets/i18n/<language>.json), written once and checked, never made up on the
 * phone. A line with a name or number in it ("Call Ravi", "3 times") is matched by its pattern ("Call {0}").
 * Anything without a translation stays in English, so nothing is ever blank.
 */
object I18n {
    @Volatile var lang: String = "en"
        private set
    private var exact: Map<String, String> = emptyMap()
    private var templates: List<Pair<Regex, String>> = emptyList()
    private val cache = object : LinkedHashMap<String, String>(512, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?) = size > 2000
    }

    val active get() = lang != "en" && exact.isNotEmpty()

    /** Loads the language for [tag] ("ta-IN"); English, or a language with no file, turns translation off. */
    @Synchronized
    fun use(ctx: Context, tag: String) {
        val code = tag.substringBefore('-').lowercase()
        if (code == lang) return
        val map = if (code == "en") null else runCatching {
            ctx.assets.open("i18n/$code.json").bufferedReader().use { it.readText() }
        }.getOrNull()?.let { load(it) }
        exact = map ?: emptyMap()
        templates = compile(exact)
        lang = if (map == null) "en" else code
        synchronized(cache) { cache.clear() }
    }

    /** For tests: use this map directly. */
    fun useMap(code: String, map: Map<String, String>) {
        exact = map; templates = compile(map); lang = code
        synchronized(cache) { cache.clear() }
    }

    private fun load(json: String): Map<String, String> {
        val o = JSONObject(json)
        return o.keys().asSequence().associateWith { o.getString(it) }
    }

    private val SLOT = Regex("\\{(\\d)\\}")

    /** "Call {0}" → ^Call (.+?)$, longest fixed text first so the most specific pattern wins. */
    private fun compile(map: Map<String, String>) = map.filterKeys { SLOT.containsMatchIn(it) }.map { (en, tr) ->
        val parts = SLOT.split(en)
        val order = SLOT.findAll(en).map { it.groupValues[1] }.toList()
        val rx = buildString {
            append('^')
            parts.forEachIndexed { i, p -> append(Regex.escape(p)); if (i < order.size) append("(.+?)") }
            append('$')
        }
        Triple(Regex(rx, RegexOption.DOT_MATCHES_ALL), tr, order)
    }.sortedByDescending { (rx, _, _) -> rx.pattern.length }.map { (rx, tr, order) ->
        // translation slots refer to English slot numbers; remember the order they were captured in
        rx to (order.joinToString(",") + "\u0000" + tr)
    }

    /** [text] in the person's language, or [text] itself when there is no translation. */
    fun tr(text: String): String {
        if (!active || text.isBlank() || text.length > 600) return text
        synchronized(cache) { cache[text] }?.let { return it }
        val out = translate(text)
        synchronized(cache) { cache[text] = out }
        return out
    }

    private fun translate(text: String): String {
        val lead = text.takeWhile { it.isWhitespace() }
        val trail = text.takeLastWhile { it.isWhitespace() }
        val core = text.trim()
        exact[core]?.let { return lead + it + trail }
        for ((rx, packed) in templates) {
            val m = rx.find(core) ?: continue
            val order = packed.substringBefore('\u0000').split(',')
            val tr = packed.substringAfter('\u0000')
            val values = order.mapIndexed { i, slot -> slot to translateValue(m.groupValues[i + 1]) }.toMap()
            return lead + SLOT.replace(tr) { values[it.groupValues[1]] ?: it.value } + trail
        }
        // several sentences or lines: translate each one that can be
        if ('\n' in core) return lead + core.split('\n').joinToString("\n") { translate(it) } + trail
        val sentences = core.split(Regex("(?<=[.?!])\\s+"))
        if (sentences.size > 1) {
            val parts = sentences.map { s -> exact[s] ?: s }
            if (parts != sentences) return lead + parts.joinToString(" ") + trail
        }
        return text
    }

    /** A value inside a pattern: a problem name or a word is translated, a person's name or number is left alone. */
    private fun translateValue(v: String) = exact[v.trim()] ?: exact[v.trim().replaceFirstChar { it.uppercase() }] ?: v
}
