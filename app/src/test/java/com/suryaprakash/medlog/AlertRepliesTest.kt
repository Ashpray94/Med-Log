package com.suryaprakash.medlog

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.suryaprakash.medlog.care.HelperDose
import com.suryaprakash.medlog.data.Dose
import com.suryaprakash.medlog.data.DoseStatus
import com.suryaprakash.medlog.data.Medicine
import com.suryaprakash.medlog.data.Mirror
import com.suryaprakash.medlog.help.AlertReplies
import com.suryaprakash.medlog.help.Alerts
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Every helper alert has exactly three answers, with the words the owner chose, in one table. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AlertRepliesTest {
    private fun words(t: Alerts.Type) = AlertReplies.forType(t).map { it.words }
    private fun codes(t: Alerts.Type) = AlertReplies.forType(t).map { it.code }

    @Test fun exactlyThreeForEveryType() {
        for (t in Alerts.Type.values()) assertEquals("$t", 3, AlertReplies.forType(t).size)
        assertEquals(3, AlertReplies.forKind("HANDOFF").size)
        assertEquals(3, AlertReplies.forKind("something new").size)
    }

    @Test fun redAlertsAndMessages() {
        for (t in listOf(Alerts.Type.SOS, Alerts.Type.FALL, Alerts.Type.DANGER, Alerts.Type.MESSAGE)) {
            assertEquals(listOf("I'm coming", "In 5 min", "Ask another helper"), words(t))
            assertEquals(listOf("coming", "5min", "ask"), codes(t))
        }
    }

    @Test fun missedDose() {
        assertEquals(listOf("I'll give it", "Skip this dose", "Ask another helper"), words(Alerts.Type.MISSED_DOSE))
        assertEquals(listOf("give", "skipdose", "ask"), codes(Alerts.Type.MISSED_DOSE))
    }

    @Test fun otherAlerts() {
        for (t in listOf(Alerts.Type.AMBER, Alerts.Type.CHECKIN, Alerts.Type.REFILL, Alerts.Type.LOW_BATTERY)) {
            assertEquals(listOf("I'll handle it", "Skip", "Ask another helper"), words(t))
            assertEquals(listOf("handle", "skip", "ask"), codes(t))
        }
    }

    @Test fun unknownKindIsAMessage() {
        assertEquals(AlertReplies.forType(Alerts.Type.MESSAGE), AlertReplies.forKind("HANDOFF"))
    }

    @Test fun oldPhonesRepliesStayReadable() {
        assertEquals("I'll call you", AlertReplies.words("call"))
        assertEquals("Can't come now, I'll call", AlertReplies.words("cant"))
        assertEquals("Got it", AlertReplies.words("got"))
        assertEquals("I'm coming", AlertReplies.words("coming"))
        assertEquals("In 5 minutes", AlertReplies.words("5min"))
        assertEquals("asked Meena to go", AlertReplies.words("ask:Meena"))
        assertEquals("Skipped this dose", AlertReplies.words("skipdose"))
        // every code the table can send has words of its own
        for (t in Alerts.Type.values()) for (r in AlertReplies.forType(t)) assertTrue(r.code, AlertReplies.words(r.code) != r.code)
    }

    @Test fun titlesMatchTheAlertKind() {
        assertEquals("SOS from Lakshmi", AlertReplies.title("SOS", "Lakshmi", "x"))
        assertEquals("A feed isn't marked as given", AlertReplies.title("MISSED_DOSE", "Lakshmi", "The 8:00 AM feed (Ensure)"))
        assertEquals("Message from Lakshmi", AlertReplies.title("MESSAGE", "Lakshmi", "Please come"))
    }

    /** "Skip this dose" on an alert marks that one dose not given, the same as the helper's own "not given". */
    @Test fun skipThisDoseFromAnAlertSkipsOnlyThatDose() = runBlocking {
        val ctx: Context = ApplicationProvider.getApplicationContext()
        val db = Mirror.db(ctx, "amma-${System.nanoTime()}".also { pair = it })
        val now = System.currentTimeMillis()
        val amlo = db.medicines().insert(Medicine(name = "Amlodipine", strength = "5 mg", amount = "1 tablet"))
        val metf = db.medicines().insert(Medicine(name = "Metformin", strength = "500 mg", amount = "1 tablet"))
        val at = now - 40 * 60_000L
        db.doses().insert(Dose(medicineId = amlo, scheduledAt = at, status = DoseStatus.DUE))
        db.doses().insert(Dose(medicineId = metf, scheduledAt = at, status = DoseStatus.DUE))
        val time = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault()).format(java.util.Date(at))
        HelperDose.skipFromAlert(ctx, pair, com.suryaprakash.medlog.help.Wording.missedDose("Lakshmi", time, "Amlodipine"))
        val doses = db.doses().between(now - 3600_000L, now + 3600_000L)
        assertEquals(DoseStatus.SKIPPED, doses.first { it.medicineId == amlo }.status)
        assertEquals(DoseStatus.DUE, doses.first { it.medicineId == metf }.status)
        // an alert that names nothing we know changes nothing
        HelperDose.skipFromAlert(ctx, pair, "MedLog: someone hasn't taken something")
        assertEquals(DoseStatus.DUE, db.doses().between(now - 3600_000L, now + 3600_000L).first { it.medicineId == metf }.status)
    }

    private var pair = ""
}
