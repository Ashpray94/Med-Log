package com.suryaprakash.medlog

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.suryaprakash.medlog.data.Dose
import com.suryaprakash.medlog.data.DoseStatus
import com.suryaprakash.medlog.data.Medicine
import com.suryaprakash.medlog.integration.TrustedLinks
import com.suryaprakash.medlog.meds.Scheduler
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A stopped medicine stops ringing and alerting; links only act when MedLog made them. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SafetyFixesTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val app get() = ctx as MedLogApp

    private fun medWithOpenDoses(name: String): Pair<Medicine, List<Dose>> = runBlocking {
        val id = app.db.medicines().insert(Medicine(name = name, times = "08:00"))
        val m = app.db.medicines().get(id)!!
        val now = System.currentTimeMillis()
        val ids = listOf(now - 60_000L, now + 3_600_000L).map { app.db.doses().insert(Dose(medicineId = id, scheduledAt = it)) }
        m to ids.map { app.db.doses().get(it)!! }
    }

    @Test fun stoppingAMedicineSkipsItsOpenDoses() = runBlocking {
        val (m, doses) = medWithOpenDoses("Stopme")
        app.db.doses().update(app.db.doses().get(doses[0].id)!!.let { it.copy(status = DoseStatus.SNOOZED, snoozeUntil = System.currentTimeMillis() + 300_000L) })
        app.db.medicines().update(m.copy(active = false))
        Scheduler.stopMedicine(ctx, m.copy(active = false))
        doses.forEach {
            val d = app.db.doses().get(it.id)!!
            assertEquals("Stopped", d.reason)
            assertEquals(DoseStatus.SKIPPED, d.status)
            assertNull(d.snoozeUntil)
        }
        assertTrue("nothing open is left for a stopped medicine", app.db.doses().open().none { it.medicineId == m.id })
    }

    @Test fun aTickDoesNotAlertForAStoppedMedicine() = runBlocking {
        val (m, doses) = medWithOpenDoses("Stopped one")
        // stopped from somewhere that did not close its doses (an older version, another phone)
        app.db.medicines().update(m.copy(active = false))
        Scheduler.tick(ctx)
        val d = app.db.doses().get(doses[0].id)!!
        assertEquals(DoseStatus.SKIPPED, d.status)
        assertEquals("Stopped", d.reason)
        assertEquals("it was never reminded", 0, d.reminded)
        assertFalse("and no helper was told it was missed", d.helperAlerted)
        assertNull("nor is it the next dose", Scheduler.nextDose(ctx)?.takeIf { it.second.id == m.id })
    }

    @Test fun theLinkSecretIsKeptAndChecked() {
        val s = TrustedLinks.secret(ctx)
        assertTrue(s.length >= 16)
        assertEquals("the same secret every time", s, TrustedLinks.secret(ctx))
        val own = TrustedLinks.trust(android.content.Intent(), ctx)
        assertTrue(TrustedLinks.isTrusted(own, ctx))
        assertFalse(TrustedLinks.isTrusted(android.content.Intent().putExtra(TrustedLinks.EXTRA, "guess"), ctx))
        assertFalse(TrustedLinks.isTrusted(android.content.Intent(), ctx))
        assertFalse(TrustedLinks.isTrusted(null, ctx))
    }
}
