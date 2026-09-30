package com.suryaprakash.medlog.doctor

/** Urgent handover in SBAR order (Situation, Background, Assessment, Recommendation/Request). Plain text, at most 15 lines. */
object Sbar {
    private fun one(s: String) = s.replace(Regex("\\s+"), " ").trim()

    fun build(n: DoctorNote): String {
        val who = listOfNotNull(n.name.ifBlank { null }, n.ageYears?.let { "$it y" }, n.sexText.ifBlank { null }).joinToString(", ").ifBlank { "Patient" }
        val now = n.concerns.firstOrNull() ?: "no concerning finding logged"
        val meds = n.medicines.filter { it.active }
        val medText = if (meds.isEmpty()) "none recorded" else
            meds.take(6).joinToString("; ") { m -> "${m.name} ${m.strength} ${m.amount} ${m.freq}".replace(Regex("\\s+"), " ").trim() } + if (meds.size > 6) "; +${meds.size - 6} more" else ""
        val allergy = if (n.allergiesRecorded) n.allergies else "not recorded"
        val recent = n.readings.takeLast(4).joinToString("; ").ifBlank { "none in period" }
        val off = n.tiles.filter { it.off }.joinToString("; ") { "${it.name} ${it.latest} ${it.unit}".trim() }
        val trends = n.symptoms.filter { it.trend != null }.take(3).joinToString("; ") { "${it.name} ${it.trend}" }
        val flags = (n.concerns.filterIndexed { i, _ -> n.concernLevels.getOrNull(i).let { it == "RED" || it == "AMBER" } } + n.symptoms.flatMap { it.flags })
            .distinct().take(4).joinToString("; ")

        val lines = listOf(
            "SBAR handover, ${n.period}",
            "S: ${one(who)}. Now: ${one(now)}. Triage level: ${n.level}.",
            "B: Age ${n.ageYears?.toString() ?: "not recorded"}. Conditions: ${one(n.conditions)}.",
            "   Medicines: ${one(medText)}.",
            "   Allergies: ${one(allergy)}. Blood thinner: ${if (n.bloodThinner) "yes" else "no"}.",
            "   Recent readings: ${one(recent)}.",
            "A: Objective: ${one(off.ifBlank { "no out-of-range reading" })}.",
            "   Trends: ${one(trends.ifBlank { "none noted" })}.",
            "R: Request: clinician review.",
            "   Red flags: ${one(flags.ifBlank { "none recorded" })}.",
            "Patient-reported; not a diagnosis. Generated ${if (n.generatedAt > 0) DoctorNoteBuilder.stamp(n.generatedAt) else "n/a"}. MedLog ${n.appVersion}, rules ${n.rulesVersion}",
        )
        return lines.joinToString("\n")
    }
}
