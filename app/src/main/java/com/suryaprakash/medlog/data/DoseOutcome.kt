package com.suryaprakash.medlog.data

/** Why a dose was skipped when ordinary food was given in place of a feed. */
const val FOOD_INSTEAD = "Ate food instead"

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
