package com.suryaprakash.medlog

import com.suryaprakash.medlog.data.Medicine
import com.suryaprakash.medlog.data.Profile
import com.suryaprakash.medlog.doctor.DoctorNote
import com.suryaprakash.medlog.doctor.Fhir
import com.suryaprakash.medlog.doctor.Sbar
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DoctorReportTest {
    private val t = 1_790_000_000_000L
    private val obs = listOf(
        DoctorNote.Obs("bp", 150.0, 95.0, "mmHg", t),
        DoctorNote.Obs("pulse", 88.0, null, "bpm", t),
        DoctorNote.Obs("spo2", 97.0, null, "%", t),
        DoctorNote.Obs("temp", 100.4, null, "°F", t),
        DoctorNote.Obs("weight", 62.0, null, "kg", t),
        DoctorNote.Obs("sugar", 6.1, null, "mmol/L", t),
        DoctorNote.Obs("sugar", 110.0, null, "mg/dL", t),
    )
    private val note = DoctorNote(
        period = "1 September – 14 September 2026", patient = "Asha, 54 y", allergies = "Penicillin", conditions = "Hypertension",
        currentMeds = "Amlodipine 5 mg OD", concerns = listOf("Chest pain: pain with effort"), symptoms = listOf(
            DoctorNote.Row(1, "Chest pain", "RED", "Twice in 2 days (1 September)", "chest", "tight; 6/10", "–", trend = "increasing", flags = listOf("on a blood thinner"), firstAt = t)),
        pins = emptyList(), medicines = listOf(DoctorNote.Med("Amlodipine", "5 mg OD", "5/6", "", strength = "5 mg", amount = "1 tablet", route = "oral", freq = "OD")),
        readings = listOf("BP: 150/95 mmHg on 1 September"), links = emptyList(), questions = emptyList(), footer = "f",
        concernLevels = listOf("RED"), tiles = listOf(DoctorNote.Reading("BP", "150/95", "mmHg", null, 1, "1 September", true)),
        name = "Asha", sexText = "female", ageYears = 54, dob = "1972-03-01", allergiesRecorded = true, generatedAt = t, appVersion = "1.0", rulesVersion = "rules-x", obs = obs,
    )
    private val profile = Profile(name = "Asha", dob = "1972-03-01", sex = "F", allergies = "Penicillin")
    private val meds = listOf(Medicine(name = "Amlodipine", strength = "5 mg", times = "08:00"))

    @Test fun bpIsPanelWithBothComponents() {
        val o = Fhir.observationJson(obs[0])!!
        assertEquals("85354-9", o.getJSONObject("code").getJSONArray("coding").getJSONObject(0).getString("code"))
        val c = o.getJSONArray("component")
        assertEquals("8480-6", c.getJSONObject(0).getJSONObject("code").getJSONArray("coding").getJSONObject(0).getString("code"))
        assertEquals("8462-4", c.getJSONObject(1).getJSONObject("code").getJSONArray("coding").getJSONObject(0).getString("code"))
        assertEquals("mm[Hg]", c.getJSONObject(0).getJSONObject("valueQuantity").getString("code"))
    }

    @Test fun loincAndUnitsPerType() {
        fun code(o: DoctorNote.Obs) = Fhir.observationJson(o)!!.getJSONObject("code").getJSONArray("coding").getJSONObject(0).getString("code")
        fun unit(o: DoctorNote.Obs) = Fhir.observationJson(o)!!.getJSONObject("valueQuantity").getString("code")
        assertEquals("8867-4", code(obs[1])); assertEquals("/min", unit(obs[1]))
        assertEquals("59408-5", code(obs[2])); assertEquals("%", unit(obs[2]))
        assertEquals("8310-5", code(obs[3])); assertEquals("Cel", unit(obs[3]))
        assertEquals(38.0, Fhir.observationJson(obs[3])!!.getJSONObject("valueQuantity").getDouble("value"), 0.01)
        assertEquals("29463-7", code(obs[4])); assertEquals("kg", unit(obs[4]))
        assertEquals("2345-7", code(obs[5])); assertEquals("mmol/L", unit(obs[5]))
        assertEquals("2339-0", code(obs[6])); assertEquals("mg/dL", unit(obs[6]))
    }

    @Test fun bundleParsesTaggedAndUncoded() {
        val json = Fhir.bundle(note, profile, meds, obs)
        val b = JSONObject(json)
        assertEquals("Bundle", b.getString("resourceType")); assertEquals("collection", b.getString("type"))
        assertEquals("patient-reported", b.getJSONObject("meta").getJSONArray("tag").getJSONObject(0).getString("code"))
        val types = (0 until b.getJSONArray("entry").length()).map { b.getJSONArray("entry").getJSONObject(it).getJSONObject("resource").getString("resourceType") }
        assertTrue("Patient" in types && "Observation" in types && "MedicationStatement" in types && "AllergyIntolerance" in types && "Condition" in types)
        assertEquals("female", b.getJSONArray("entry").getJSONObject(0).getJSONObject("resource").getString("gender"))
        val lower = json.lowercase()
        assertFalse("snomed" in lower); assertFalse("icd" in lower); assertFalse("rxnorm" in lower)
        assertNotNull(Regex("\\d{4}-\\d\\d-\\d\\dT\\d\\d:\\d\\d:\\d\\d[+-Z]").find(json))
    }

    @Test fun noAllergyResourceWhenNoneStored() {
        val json = Fhir.bundle(note, profile.copy(allergies = ""), meds, emptyList())
        assertFalse("AllergyIntolerance" in json)
    }

    @Test fun sbarHasFourLettersInOrderAndIsShort() {
        val lines = Sbar.build(note).lines()
        assertTrue(lines.size <= 15)
        val idx = listOf("S: ", "B: ", "A: ", "R: ").map { p -> lines.indexOfFirst { it.startsWith(p) } }
        assertTrue(idx.all { it >= 0 })
        assertEquals(idx.sorted(), idx)
        assertTrue(lines.any { it.contains("Request: clinician review") })
    }
}
