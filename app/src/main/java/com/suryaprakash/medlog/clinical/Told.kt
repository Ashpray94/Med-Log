package com.suryaprakash.medlog.clinical

import com.suryaprakash.medlog.nlu.Fact
import com.suryaprakash.medlog.nlu.Source

/**
 * "Helpers were already told about this note at this level" (B57, B59). Kept in the note's details as the hidden fact
 * `_toldLevel` (keys starting with `_` are never shown), so leaving and coming back, or "This is wrong – change it" and
 * Save, never texts the helpers a second time. Helpers are texted again only when the level rises (AMBER, then RED).
 */
object Told {
    const val KEY = "_toldLevel"

    fun level(facts: Map<String, Fact>): Level? = (facts[KEY]?.value as? String)?.let { s -> Level.entries.firstOrNull { it.name == s } }

    /** true when [now] is higher than what was told before (nothing told yet counts as GREEN). */
    fun shouldTell(facts: Map<String, Fact>, now: Level): Boolean = now != Level.GREEN && now.ordinal > (level(facts)?.ordinal ?: -1)

    fun mark(facts: Map<String, Fact>, now: Level): Map<String, Fact> = facts + (KEY to Fact(now.name, Source.INFERRED))
}
