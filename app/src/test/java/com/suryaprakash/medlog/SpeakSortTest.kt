package com.suryaprakash.medlog

import androidx.test.core.app.ApplicationProvider
import com.suryaprakash.medlog.data.Kind
import com.suryaprakash.medlog.data.Medicine
import com.suryaprakash.medlog.data.Profile
import com.suryaprakash.medlog.ui.screens.SpeakSort
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** "Tell it all at once": one spoken sentence, each part in its own place; emergencies flagged with no questions. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SpeakSortTest {
    private val app: MedLogApp = ApplicationProvider.getApplicationContext()

    @Before fun setUp() { runBlocking {
        app.settings.update { it.copy(onboarded = true, role = "self") }
        app.db.profile().put(Profile(name = "Lakshmi", dob = "1948-01-01"))
        app.db.medicines().insert(Medicine(name = "Metformin", strength = "500 mg", times = "08:00", uid = "m1"))
    } }

    private fun kinds(r: SpeakSort.Result) = r.saved.map { it.kind }

    @Test fun oneSentenceGoesToEveryPlace() { runBlocking {
        val r = SpeakSort.sort(app, "Knee hurts. Had two idli and coffee. Loose motion twice. Drank two glasses of water.")
        val k = kinds(r)
        assertTrue("knee pain is how I feel: $k", Kind.SYMPTOM in k)
        assertTrue("loose motion goes to toilet: $k", Kind.OUTPUT in k)
        assertTrue("idli goes to food: $k", Kind.FOOD in k)
        assertTrue("water goes to water: $k", Kind.WATER in k)
        assertNull("nothing urgent", r.urgent)
        // loose motion is noted once, in the toilet log, not again as a symptom
        assertEquals(1, r.saved.count { it.title.contains("Loose", true) || it.title == "Stool" })
    } }

    @Test fun bloodInVomitIsUrgentWithNoQuestions() { runBlocking {
        val r = SpeakSort.sort(app, "Vomited blood this morning")
        assertNotNull("urgent", r.urgent)
        assertTrue(Kind.OUTPUT in kinds(r))
    } }

    @Test fun chestPainIsUrgent() { runBlocking {
        val r = SpeakSort.sort(app, "Severe chest pain and sweating, can't breathe")
        assertNotNull("chest pain with breathlessness must alert helpers: ${r.saved.map { it.title }}", r.urgent)
    } }

    @Test fun medicineTakenTicksTodaysDose() { runBlocking {
        val r = SpeakSort.sort(app, "Took metformin")
        assertTrue(Kind.MED_TAKEN in kinds(r))
    } }

    @Test fun notSaidMeansNotSaved() { runBlocking {
        val r = SpeakSort.sort(app, "No fever today")
        assertTrue("a denied symptom isn't noted: ${r.saved.map { it.title }}", r.saved.none { it.kind == Kind.SYMPTOM && it.title.contains("Fever", true) })
    } }
}
