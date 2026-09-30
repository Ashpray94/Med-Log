package com.suryaprakash.medlog.doctor

import com.suryaprakash.medlog.data.Medicine
import com.suryaprakash.medlog.data.Profile
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * HL7 FHIR R4 collection Bundle of what the patient reported. LOINC codes only for vitals (see CLAUDE.md section 2);
 * no SNOMED, ICD-10 or RxNorm: medicines, symptoms and allergies use plain text.
 */
object Fhir {
    private const val LOINC = "http://loinc.org"
    private const val UCUM = "http://unitsofmeasure.org"
    private val iso = DateTimeFormatter.ISO_OFFSET_DATE_TIME

    fun iso(t: Long): String = iso.format(Instant.ofEpochMilli(t).atZone(ZoneId.systemDefault()))

    private fun code(system: String, code: String, display: String) = JSONObject().put("system", system).put("code", code).put("display", display)
    private fun qty(v: Double, unit: String, ucum: String) = JSONObject().put("value", v).put("unit", unit).put("system", UCUM).put("code", ucum)
    private fun coding(system: String, code: String, display: String) = JSONObject().put("coding", JSONArray().put(code(system, code, display))).put("text", display)
    private fun fahrenheit(unit: String) = unit.contains("F", ignoreCase = true)

    /** The Observation for one reading, or null when the type is not one MedLog codes. */
    fun observationJson(o: DoctorNote.Obs): JSONObject? {
        val obs = JSONObject().put("resourceType", "Observation").put("status", "final")
            .put("category", JSONArray().put(JSONObject().put("coding", JSONArray().put(
                code("http://terminology.hl7.org/CodeSystem/observation-category", "vital-signs", "Vital Signs")))))
            .put("effectiveDateTime", iso(o.at))
        fun simple(loinc: String, display: String, q: JSONObject): JSONObject = obs.put("code", coding(LOINC, loinc, display)).put("valueQuantity", q)
        return when (o.type) {
            "bp" -> {
                if (o.v2 == null) return null
                obs.put("code", coding(LOINC, "85354-9", "Blood pressure panel with all children optional"))
                    .put("component", JSONArray()
                        .put(JSONObject().put("code", coding(LOINC, "8480-6", "Systolic blood pressure")).put("valueQuantity", qty(o.v1, "mmHg", "mm[Hg]")))
                        .put(JSONObject().put("code", coding(LOINC, "8462-4", "Diastolic blood pressure")).put("valueQuantity", qty(o.v2, "mmHg", "mm[Hg]"))))
            }
            "pulse" -> simple("8867-4", "Heart rate", qty(o.v1, "/min", "/min"))
            "spo2" -> simple("59408-5", "Oxygen saturation in Arterial blood by Pulse oximetry", qty(o.v1, "%", "%"))
            "temp" -> simple("8310-5", "Body temperature",
                if (fahrenheit(o.unit)) qty((o.v1 - 32.0) * 5.0 / 9.0, "°C", "Cel") else qty(o.v1, "°C", "Cel"))
            "weight" -> simple("29463-7", "Body weight", qty(o.v1, "kg", "kg"))
            "sugar" -> if (o.unit.contains("mmol", ignoreCase = true))
                simple("2345-7", "Glucose [Moles/volume] in Serum or Plasma", qty(o.v1, "mmol/L", "mmol/L"))
            else simple("2339-0", "Glucose [Mass/volume] in Blood", qty(o.v1, "mg/dL", "mg/dL"))
            else -> null
        }
    }

    fun bundle(n: DoctorNote, profile: Profile, meds: List<Medicine>, readings: List<DoctorNote.Obs>): String {
        val entries = JSONArray()
        fun add(r: JSONObject) = entries.put(JSONObject().put("resource", r))
        val patientRef = JSONObject().put("reference", "Patient/medlog-patient")

        val patient = JSONObject().put("resourceType", "Patient").put("id", "medlog-patient")
        if (profile.name.isNotBlank()) patient.put("name", JSONArray().put(JSONObject().put("text", profile.name)))
        when (profile.sex.lowercase()) { "male", "m" -> "male"; "female", "f" -> "female"; "other" -> "other"; else -> null }?.let { patient.put("gender", it) }
        runCatching { LocalDate.parse(profile.dob) }.getOrNull()?.let { patient.put("birthDate", it.toString()) }
        add(patient)

        for (r in readings) observationJson(r)?.put("subject", patientRef)?.let { add(it) }

        for (m in meds.filter { it.active }) {
            val text = listOf(m.strength, m.amount.takeIf { it.isNotBlank() }?.let { "$it ${m.form}" }, if (m.asNeeded) "as needed" else m.times.takeIf { it.isNotBlank() }?.let { "at $it" },
                DoctorNoteBuilder.routeFor(m.form)).filterNotNull().filter { it.isNotBlank() }.joinToString(", ")
            add(JSONObject().put("resourceType", "MedicationStatement").put("status", "active")
                .put("medicationCodeableConcept", JSONObject().put("text", m.name))
                .put("subject", patientRef)
                .put("dosage", JSONArray().put(JSONObject().put("text", text).put("asNeededBoolean", m.asNeeded))))
        }

        if (profile.allergies.isNotBlank()) add(JSONObject().put("resourceType", "AllergyIntolerance")
            .put("clinicalStatus", JSONObject().put("coding", JSONArray().put(code("http://terminology.hl7.org/CodeSystem/allergyintolerance-clinical", "active", "Active"))))
            .put("code", JSONObject().put("text", profile.allergies))
            .put("patient", patientRef))

        for (s in n.symptoms) {
            val c = JSONObject().put("resourceType", "Condition")
                .put("clinicalStatus", JSONObject().put("coding", JSONArray().put(code("http://terminology.hl7.org/CodeSystem/condition-clinical", "active", "Active"))))
                .put("code", JSONObject().put("text", s.name))
                .put("subject", patientRef)
            s.firstAt?.let { c.put("onsetDateTime", iso(it)) }
            val note = listOfNotNull(s.whenText, s.nature.takeIf { it != "–" }, s.where.takeIf { it != "–" }).joinToString("; ")
            if (note.isNotBlank()) c.put("note", JSONArray().put(JSONObject().put("text", note)))
            add(c)
        }

        val meta = JSONObject().put("tag", JSONArray().put(JSONObject().put("system", "https://medlog.app/fhir").put("code", "patient-reported")))
        for (i in 0 until entries.length()) entries.getJSONObject(i).getJSONObject("resource").put("meta", meta)
        return JSONObject().put("resourceType", "Bundle").put("type", "collection").put("meta", meta)
            .put("timestamp", iso(if (n.generatedAt > 0) n.generatedAt else System.currentTimeMillis()))
            .put("entry", entries).toString(2)
    }
}
