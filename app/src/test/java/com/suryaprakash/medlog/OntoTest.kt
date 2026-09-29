package com.suryaprakash.medlog

import com.suryaprakash.medlog.data.Medicine
import com.suryaprakash.medlog.data.Profile
import com.suryaprakash.medlog.data.onto
import com.suryaprakash.medlog.meds.Pills
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OntoTest {
    @Test fun aPageWritesOnlyWhatItChangedOntoTheFreshestProfile() {
        val opened = Profile(name = "Kamala", conditions = "Cancer", plan = "{old}")
        val fresh = opened.copy(plan = "{limits from Ravi}", allergies = "penicillin")      // Ravi's phone changed these while the page was open
        val saved = opened.copy(conditions = "Cancer, diabetes").onto(opened, fresh)
        assertEquals("Cancer, diabetes", saved.conditions)
        assertEquals("{limits from Ravi}", saved.plan)
        assertEquals("penicillin", saved.allergies)
        assertEquals("Kamala", saved.name)
    }

    @Test fun aPageThatChangedNothingWritesTheFreshRowAsItIs() {
        val opened = Profile(name = "Kamala", plan = "{old}")
        val fresh = opened.copy(plan = "{new}")
        assertEquals(fresh, opened.onto(opened, fresh))
    }

    @Test fun aMedicineRaviStoppedIsNotStartedAgainBySavingAnOldCopy() {
        val opened = Medicine(id = 3, name = "Dexa", purpose = "", pillsLeft = 14.0, active = true)
        val fresh = opened.copy(active = false, changeNote = "stopped")
        val saved = opened.copy(purpose = "with food").onto(opened, fresh)
        assertFalse(saved.active); assertEquals("stopped", saved.changeNote); assertEquals("with food", saved.purpose)
    }

    @Test fun whatThePageChangedWinsOverTheFreshValue() {
        val opened = Medicine(id = 3, name = "Dexa", times = "08:00")
        val fresh = opened.copy(times = "09:00")                       // both changed the times: the page is the later writer
        assertEquals("07:00", opened.copy(times = "07:00").onto(opened, fresh).times)
    }

    @Test fun pillsLeftIsTheCountMinusTheDosesTakenSince() {
        val m = Medicine(id = 1, name = "Dexa", amount = "1", times = "08:00,20:00", pillsLeft = 14.0)
        assertEquals(12.0, Pills.left(m, 2)!!, 0.0)
        assertEquals(0.0, Pills.left(m, 20)!!, 0.0)                    // never below zero
        assertNull(Pills.left(m.copy(pillsLeft = null), 2))
        assertEquals(13.5, Pills.left(m.copy(amount = "½"), 1)!!, 0.0)  // half a pill a dose
        assertEquals(6, Pills.daysLeft(m, 12.0))                       // 2 a day
        assertTrue(Pills.per("½") == 0.5 && Pills.per("2") == 2.0 && Pills.per("10 ml") == 1.0)
    }
}
