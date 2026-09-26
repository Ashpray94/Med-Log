package com.suryaprakash.medlog

import com.suryaprakash.medlog.clinical.Suggest
import com.suryaprakash.medlog.data.CarePlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SuggestTest {
    private val all: (String) -> Boolean = { true }
    private val now = 1_000_000_000_000L

    @Test fun ownHistoryIsKeptApartFromSuggestions() {
        val h = listOf(Suggest.Logged("cough", now - 3600_000), Suggest.Logged("cough", now - 7200_000), Suggest.Logged("fever", now - 86_400_000))
        val r = Suggest.rank(h, 70, emptyList(), emptyList(), 12, all, now)
        assertEquals(listOf("cough", "fever"), r.yours)
        assertFalse(r.suggested.any { it in r.yours })
    }

    @Test fun dayOneUsesSetupConditionsAndAge() {
        val r = Suggest.rank(emptyList(), 72, listOf("Diabetes"), listOf("knee_pain"), 12, all, now)
        assertTrue(r.yours.isEmpty())
        assertEquals("knee_pain", r.suggested.first())
        assertTrue("high_sugar" in r.suggested)
    }

    @Test fun carePlanRoundTripsAndPicksTheRightDoctor() {
        val p = CarePlan(doctors = listOf(CarePlan.Doctor("Dr. A", "Family doctor", "1"), CarePlan.Doctor("Dr. B", "Heart", "2")), emergencies = listOf("fall"))
        val back = CarePlan.parse(p.toJson())
        assertEquals(p, back)
        assertEquals("Dr. B", back.doctorFor("Cardiology")?.name)
        assertEquals("Dr. A", back.doctorFor("Dermatology")?.name)
        assertEquals(CarePlan(), CarePlan.parse("not json"))
    }
}
