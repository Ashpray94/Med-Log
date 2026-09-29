package com.suryaprakash.medlog.meds

import com.suryaprakash.medlog.data.Medicine

/**
 * The pill count is worked out, not stored: the medicine keeps the count at its last refill or edit ([Medicine.pillsLeft] at
 * [Medicine.pillsAt]) and every TAKEN dose acted after that time takes one dose's worth off. Doses travel between phones as rows of
 * their own, so two phones taking two doses at once always add up (a stored counter would lose one), and "taken" tapped by mistake
 * and undone puts the pill back by itself.
 */
object Pills {
    /** Per medicine with a count: how many TAKEN doses were acted after the time of the count. (One place, so the phone and the JVM tests run the same query.) */
    const val TAKEN_SINCE = "SELECT d.medicineId AS medicineId, COUNT(*) AS n FROM doses d JOIN medicines m ON m.id = d.medicineId " +
        "WHERE d.status = 'TAKEN' AND m.pillsLeft IS NOT NULL AND d.actedAt > m.pillsAt GROUP BY d.medicineId"

    /** How many pills one dose uses: "1", "½", "2" ... */
    fun per(amount: String): Double = amount.replace("½", "0.5").toDoubleOrNull() ?: 1.0

    /** What is left of [m] when [taken] doses were taken after its count; null when it has no count. */
    fun left(m: Medicine, taken: Int): Double? = m.pillsLeft?.let { (it - taken * per(m.amount)).coerceAtLeast(0.0) }

    /** Whole days the pills last at the medicine's usual doses a day (at least one dose a day). */
    fun daysLeft(m: Medicine, left: Double): Int = (left / (per(m.amount) * m.times.split(",").count { it.isNotBlank() }.coerceAtLeast(1))).toInt()
}
