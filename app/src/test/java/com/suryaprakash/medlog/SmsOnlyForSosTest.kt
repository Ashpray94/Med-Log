package com.suryaprakash.medlog

import com.suryaprakash.medlog.data.Helper
import com.suryaprakash.medlog.help.Alerts
import com.suryaprakash.medlog.help.SosPlan
import com.suryaprakash.medlog.help.SosPlan.Next
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Owner decision: SMS costs money, so it is sent only in an SOS, and only when no helper answers in the app. */
class SmsOnlyForSosTest {
    private val src = File("src/main/java/com/suryaprakash/medlog").takeIf { it.exists() } ?: File("app/src/main/java/com/suryaprakash/medlog")

    @Test fun onlySosCanSendSms() {
        val callers = src.walk().filter { it.isFile && it.extension == "kt" && it.name != "Calls.kt" }
            .filter { it.readText().contains("Calls.sms(") }.map { it.name }.toList()
        assertEquals(listOf("Sos.kt"), callers)
    }

    @Test fun sosWaitsForAnAnswerInTheAppBeforeSms() {
        assertEquals(Next.WAIT, SosPlan.decide(paired = 2, receipts = 1, replies = emptyList(), elapsedSec = 30, smsNow = false))
        assertEquals(Next.WAIT, SosPlan.decide(2, 1, listOf("cant"), 60, false))              // "can't come" is not help
        assertEquals(Next.SMS, SosPlan.decide(2, 1, listOf("cant"), SosPlan.WAIT_SEC, false))  // nobody coming after the wait
    }

    @Test fun anAnswerInTheAppMeansNoSms() {
        for (r in listOf("coming", "5min", "call")) assertEquals(Next.HELP_COMING, SosPlan.decide(2, 1, listOf(r), 10, false))
        assertEquals(Next.HELP_COMING, SosPlan.decide(2, 1, listOf("coming"), 200, true))
        assertEquals("the automatic receipt is not an answer", Next.WAIT, SosPlan.decide(2, 2, listOf("got"), 10, false))
    }

    @Test fun smsGoesSoonerWhenTheAppCannotReachAnyone() {
        assertEquals("no helper phone paired", Next.SMS, SosPlan.decide(0, 0, emptyList(), 0, false))
        assertEquals(Next.WAIT, SosPlan.decide(2, 0, emptyList(), SosPlan.RECEIPT_SEC - 1, false))
        assertEquals("no phone confirmed in 20 s", Next.SMS, SosPlan.decide(2, 0, emptyList(), SosPlan.RECEIPT_SEC, false))
        assertEquals("the person tapped Send SMS now", Next.SMS, SosPlan.decide(2, 1, emptyList(), 3, true))
    }

    @Test fun anAppAlertReachesOnlyHelpersWithAPairedPhone() {
        val paired = Helper(id = 1, name = "Meena", phone = "1", pairId = "p", pairKey = "k")
        val unpaired = Helper(id = 2, name = "Ravi", phone = "2")
        assertEquals(setOf(1L), Alerts.recipients(listOf(paired, unpaired)))
        assertTrue("a helper with no paired phone is not texted instead", Alerts.recipients(listOf(unpaired)).isEmpty())
        assertFalse(2L in Alerts.recipients(listOf(paired, unpaired)))
    }
}
