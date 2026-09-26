package com.suryaprakash.medlog.clinical

import kotlin.math.exp

/**
 * The problems to offer first on "How are you feeling?", so most of the time the answer is one tap away.
 *
 * Two lists, never mixed, so a suggestion is never mistaken for something the person told before:
 * - [Result.yours]: what the person actually logged, most frequent and most recent first.
 * - [Result.suggested]: what fits them: the symptoms they named in setup, their conditions, their age and the
 *   time of day. From day one this list is useful; as they log, their own list takes over.
 */
object Suggest {
    data class Logged(val problemId: String, val at: Long)
    data class Result(val yours: List<String>, val suggested: List<String>)

    /** Conditions (setup words) → problems that commonly come with them. */
    private val BY_CONDITION = mapOf(
        "Diabetes" to listOf("high_sugar", "low_sugar", "thirsty", "frequent_urine", "tingling", "foot_numb", "tired", "blurred_vision"),
        "High BP" to listOf("high_bp", "headache", "dizzy", "blurred_vision", "chest_pain"),
        "Heart disease" to listOf("chest_pain", "breathless", "palpitations", "swollen_ankles", "tired", "dizzy"),
        "Asthma or COPD" to listOf("breathless", "wheeze", "cough", "chest_tight", "low_oxygen"),
        "Kidney disease" to listOf("swollen_ankles", "face_swelling_morning", "no_urine", "itching", "tired", "nausea"),
        "Arthritis" to listOf("knee_pain", "joint_swelling", "morning_stiffness", "hip_pain", "back_pain", "shoulder_pain"),
        "Thyroid" to listOf("tired", "weight_loss", "palpitations", "hair_loss", "constipation"),
        "Stroke before" to listOf("one_side_weak", "speech_trouble", "balance", "numbness", "confusion"),
        "Parkinson's" to listOf("tremor", "balance", "near_fall", "constipation", "swallowing"),
        "Memory loss" to listOf("confusion", "memory", "cant_sleep", "anxious"),
        "Cancer" to listOf("tired", "nausea", "no_appetite", "body_ache", "weight_loss"),
        "Depression or anxiety" to listOf("low_mood", "anxious", "cant_sleep", "lonely", "tired"),
    )

    /** Common problems by age, most common first. */
    private val OLDER = listOf("knee_pain", "back_pain", "tired", "dizzy", "cant_sleep", "constipation", "breathless", "headache", "cough", "fever", "acidity", "body_ache")
    private val ADULT = listOf("headache", "fever", "cough", "acidity", "back_pain", "tired", "sore_throat", "runny_nose", "stomach_pain", "body_ache", "loose_motions", "cant_sleep")

    private const val DAY = 24 * 3600_000.0

    fun rank(
        history: List<Logged>,
        age: Int?,
        conditions: List<String>,
        declared: List<String>,
        hour: Int,
        known: (String) -> Boolean,
        now: Long = System.currentTimeMillis(),
        limit: Int = 6,
    ): Result {
        // yours: frequency with a slow decay (a two-week half-life), so both "often" and "lately" count
        val yours = history.groupBy { it.problemId }
            .mapValues { (_, l) -> l.sumOf { exp(-(now - it.at) / (20 * DAY)) } }
            .entries.sortedByDescending { it.value }.map { it.key }.filter(known).take(limit)

        val score = HashMap<String, Double>()
        fun add(ids: List<String>, weight: Double) = ids.forEachIndexed { i, id -> score[id] = (score[id] ?: 0.0) + weight - i * 0.05 }
        add(declared, 5.0)
        conditions.forEach { c -> BY_CONDITION[c]?.let { add(it, 3.0) } }
        add(if ((age ?: 60) >= 55) OLDER else ADULT, 1.5)
        when (hour) {
            in 5..10 -> add(listOf("morning_stiffness", "dizzy", "headache"), 0.6)
            in 20..23, in 0..4 -> add(listOf("cant_sleep", "acidity", "cough"), 0.6)
        }
        val suggested = score.entries.sortedByDescending { it.value }.map { it.key }
            .filter { known(it) && it !in yours }.take(limit)
        return Result(yours, suggested)
    }
}
