package com.suryaprakash.medlog.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * What setup learns about the person, so the app fits them from the first day: their doctors (each with a
 * speciality, so the right one is called), what they feel these days, their treatments, their risks, and the
 * situations that are emergencies for them. Kept inside the encrypted profile.
 */
data class CarePlan(
    val doctors: List<Doctor> = emptyList(),
    /** problem ids from the catalogue the person has these days */
    val symptoms: List<String> = emptyList(),
    /** treatments that are not tablets: dialysis, physiotherapy, insulin, oxygen… */
    val treatments: List<String> = emptyList(),
    /** RISKS keys */
    val risks: List<String> = emptyList(),
    /** problem ids that, for this person, mean: call the helpers now */
    val emergencies: List<String> = emptyList(),
) {
    data class Doctor(val name: String, val speciality: String, val phone: String)

    fun toJson(): String = JSONObject()
        .put("doctors", JSONArray(doctors.map { JSONObject().put("name", it.name).put("speciality", it.speciality).put("phone", it.phone) }))
        .put("symptoms", JSONArray(symptoms)).put("treatments", JSONArray(treatments))
        .put("risks", JSONArray(risks)).put("emergencies", JSONArray(emergencies))
        .toString()

    /** The doctor to call about a problem in [dept] (the catalogue's department), else the family doctor, else the first. */
    fun doctorFor(dept: String?): Doctor? {
        val want = SPECIALITY_FOR_DEPT[dept?.lowercase()]
        return doctors.firstOrNull { want != null && it.speciality == want }
            ?: doctors.firstOrNull { it.speciality == "Family doctor" } ?: doctors.firstOrNull()
    }

    companion object {
        fun parse(json: String?): CarePlan {
            if (json.isNullOrBlank()) return CarePlan()
            return runCatching {
                val o = JSONObject(json)
                fun list(k: String) = o.optJSONArray(k)?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty()
                CarePlan(
                    doctors = o.optJSONArray("doctors")?.let { a ->
                        (0 until a.length()).map { a.getJSONObject(it) }.map { Doctor(it.optString("name"), it.optString("speciality"), it.optString("phone")) }
                    }.orEmpty(),
                    symptoms = list("symptoms"), treatments = list("treatments"), risks = list("risks"), emergencies = list("emergencies"),
                )
            }.getOrDefault(CarePlan())
        }

        val SPECIALITIES = listOf(
            "Family doctor", "Heart", "Diabetes", "Kidney", "Lungs", "Brain and nerves", "Bones and joints",
            "Stomach", "Cancer", "Eyes", "Skin", "Women's health", "Mind", "Other",
        )

        /** Catalogue departments → the speciality that usually handles them. */
        private val SPECIALITY_FOR_DEPT = mapOf(
            "cardiology" to "Heart", "endocrinology" to "Diabetes", "nephrology" to "Kidney", "urology" to "Kidney",
            "pulmonology" to "Lungs", "neurology" to "Brain and nerves", "orthopedics" to "Bones and joints", "rheumatology" to "Bones and joints",
            "gastroenterology" to "Stomach", "oncology" to "Cancer", "hematology" to "Cancer", "ophthalmology" to "Eyes", "dermatology" to "Skin",
            "gynecology" to "Women's health", "psychiatry" to "Mind",
        )

        val CONDITIONS = listOf(
            "Diabetes", "High BP", "Heart disease", "Asthma or COPD", "Kidney disease", "Arthritis", "Thyroid",
            "Stroke before", "Parkinson's", "Memory loss", "Cancer", "Depression or anxiety",
        )

        val TREATMENTS = listOf("Insulin", "Dialysis", "Oxygen at home", "Physiotherapy", "Chemotherapy", "Blood thinner", "Inhaler", "Wound dressing")

        /** Risks, with the words the person sees. */
        val RISKS = linkedMapOf(
            "falls" to "Falls or poor balance",
            "low_sugar" to "Low sugar spells",
            "breathing" to "Breathing trouble",
            "heart" to "Heart attack before",
            "stroke" to "Stroke before",
            "fits" to "Fits (seizures)",
            "allergy" to "Serious allergy",
            "wandering" to "Gets lost or confused",
            "alone" to "Lives alone",
        )

        /** Situations the person can mark as emergencies (catalogue problem ids). */
        val EMERGENCIES = listOf("fall", "chest_pain", "breathless", "fainted", "one_side_weak", "fits", "low_sugar", "confusion", "choking", "bleeding", "allergic_reaction")

        /** Emergencies suggested from the risks chosen, so the person only confirms. */
        fun emergenciesFor(risks: List<String>, conditions: List<String>): List<String> {
            val out = LinkedHashSet<String>()
            out += listOf("chest_pain", "breathless", "fainted")
            if ("falls" in risks || "alone" in risks) out += "fall"
            if ("low_sugar" in risks || "Diabetes" in conditions) out += "low_sugar"
            if ("stroke" in risks || "Stroke before" in conditions || "High BP" in conditions) out += "one_side_weak"
            if ("fits" in risks) out += "fits"
            if ("allergy" in risks) out += "allergic_reaction"
            if ("wandering" in risks || "Memory loss" in conditions) out += "confusion"
            return out.toList()
        }
    }
}

/** Reads and writes the plan inside the profile. */
suspend fun Repo.carePlan(): CarePlan = CarePlan.parse(profile().plan)
suspend fun Repo.saveCarePlan(f: (CarePlan) -> CarePlan) {
    val p = profile()
    db.profile().put(p.copy(plan = f(CarePlan.parse(p.plan)).toJson()))
}
