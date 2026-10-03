package com.suryaprakash.medlog.data

/** Why a dose was skipped when ordinary food was given in place of a feed. */
const val FOOD_INSTEAD = "Ate food instead"

/** How food in place of a feed is said on every screen, page and message. */
const val FOOD_INSTEAD_WORDS = "Food taken instead of feed"

/** A feed given outside its times, for hunger in between: noted under the same feed, never counted against the plan. */
const val EXTRA_FEED = "Extra feed"
/** An extra feed taken back (noted by mistake): kept for sharing between phones, shown nowhere. */
const val EXTRA_REMOVED = "Extra feed removed"
val Dose.extra get() = reason == EXTRA_FEED || reason == EXTRA_REMOVED
val Dose.removed get() = reason == EXTRA_REMOVED
/** The doses that were planned: what "3 of 4 given" and every percentage count. */
fun List<Dose>.planned() = filter { !it.extra && it.status != DoseStatus.CANCELLED && it.reason != "Stopped" }
/** What to show in a history: planned doses and extra feeds, never a taken-back extra. */
fun List<Dose>.shown() = filter { !it.removed && it.status != DoseStatus.CANCELLED }

/** A skip reason as words: food in place of a feed is said the one way everywhere. */
fun reasonWords(r: String?): String? = if (r == FOOD_INSTEAD) FOOD_INSTEAD_WORDS else r

/**
 * What became of one dose, the same everywhere (cards, Details, history, helpers' phones):
 * given, food in place of a feed, not given (a choice), missed (the time passed with no answer),
 * due now, or later today.
 */
enum class Outcome { GIVEN, FOOD_INSTEAD, NOT_GIVEN, MISSED, DUE, LATER;
    /** Done for this time: given, or replaced by food. Neither counts as missed. */
    val covered get() = this == GIVEN || this == FOOD_INSTEAD
}

fun Dose.outcome(now: Long = System.currentTimeMillis()): Outcome = when {
    status == DoseStatus.TAKEN -> Outcome.GIVEN
    status == DoseStatus.SKIPPED && reason == FOOD_INSTEAD -> Outcome.FOOD_INSTEAD
    status == DoseStatus.SKIPPED -> Outcome.NOT_GIVEN
    status == DoseStatus.MISSED -> Outcome.MISSED
    scheduledAt <= now + 10 * 60_000 -> Outcome.DUE
    else -> Outcome.LATER
}
