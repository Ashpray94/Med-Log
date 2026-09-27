package com.suryaprakash.medlog.clinical

/**
 * Problems that often come together or one after another (a cough with a fever, nausea then vomiting), so noting
 * one offers the others next to it. Groups, not diagnoses: they only save a search.
 */
object Related {
    private val GROUPS = listOf(
        // coughing often brings on vomiting (and both are common during cancer treatment), so vomiting sits near the top
        listOf("cough", "breathless", "fever", "vomiting", "sore_throat", "wheeze", "chest_tight", "runny_nose", "tired"),
        listOf("fever", "chills", "body_ache", "headache", "tired", "no_appetite", "night_sweats", "sore_throat"),
        listOf("nausea", "vomiting", "no_appetite", "stomach_pain", "dizzy", "loose_motions", "dehydrated", "acidity"),
        listOf("loose_motions", "stomach_pain", "dehydrated", "vomiting", "tired", "fever", "cramps"),
        listOf("constipation", "bloating", "stomach_pain", "no_appetite", "acidity", "burp"),
        listOf("acidity", "burp", "bloating", "chest_pain", "nausea", "swallowing"),
        listOf("headache", "dizzy", "blurred_vision", "high_bp", "nausea", "neck_pain", "cant_sleep"),
        listOf("dizzy", "fainted", "low_bp", "low_sugar", "balance", "near_fall", "dehydrated", "weakness"),
        listOf("chest_pain", "breathless", "palpitations", "chest_tight", "dizzy", "swollen_ankles", "acidity"),
        listOf("breathless", "swollen_ankles", "tired", "cough", "palpitations", "low_oxygen", "wheeze"),
        listOf("tired", "weakness", "no_appetite", "weight_loss", "pale", "cant_sleep", "low_mood", "body_ache"),
        listOf("no_appetite", "weight_loss", "trouble_eating", "swallowing", "mouth_ulcer", "nausea", "tired"),
        listOf("mouth_ulcer", "dry_mouth", "swallowing", "sore_throat", "trouble_eating", "toothache", "bleeding_gums"),
        listOf("burning_urine", "frequent_urine", "fever", "kidney_pain", "blood_urine", "leaking_urine", "confusion"),
        listOf("back_pain", "leg_pain", "numbness", "hip_pain", "trouble_walking", "night_pain", "cramps"),
        listOf("knee_pain", "joint_swelling", "morning_stiffness", "hip_pain", "trouble_walking", "gout"),
        listOf("rash", "itching", "hives", "allergic_reaction", "blisters", "fever", "face_swelling_morning"),
        listOf("low_mood", "anxious", "cant_sleep", "lonely", "tired", "no_appetite", "sleep_too_much"),
        listOf("confusion", "memory", "fever", "fall", "burning_urine", "low_sugar", "speech_trouble", "hallucination"),
        listOf("fall", "head_injury", "bruise", "hip_pain", "cut", "dizzy", "near_fall", "swelling"),
        listOf("high_sugar", "thirsty", "frequent_urine", "tired", "blurred_vision", "foot_numb", "wound_not_healing"),
        listOf("low_sugar", "dizzy", "confusion", "fainted", "weakness", "palpitations"),
        listOf("swallowing", "choking", "cough", "weight_loss", "trouble_eating", "hoarse"),
        listOf("numbness", "tingling", "foot_numb", "one_side_weak", "weakness", "balance"),
    )

    /** Up to [n] problems that often go with [id], the closest first, never [id] itself or ones in [skip]. */
    fun to(id: String, n: Int = 6, skip: Set<String> = emptySet()): List<String> {
        val score = HashMap<String, Int>()
        GROUPS.filter { id in it }.forEach { g -> g.forEachIndexed { i, x -> if (x != id && x !in skip) score[x] = (score[x] ?: 0) + (g.size - i) + if (g.first() == id) 3 else 0 } }
        return score.entries.sortedByDescending { it.value }.map { it.key }.take(n)
    }
}
