package com.suryaprakash.medlog

import com.suryaprakash.medlog.help.SosPlan
import com.suryaprakash.medlog.help.SosPlan.Next
import com.suryaprakash.medlog.sync.Op
import com.suryaprakash.medlog.sync.SyncRunner
import org.json.JSONObject
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

    private fun note(triage: String, at: Long, deleted: Boolean = false, del: Boolean = false) =
        Op("notes", "u1", "dev", 1, at, "dev", del, if (del) null else JSONObject().put("triage", triage).put("occurredAt", at).apply { if (deleted) put("deletedAt", at) else put("deletedAt", JSONObject.NULL) })

    @Test fun aRedOrAmberNoteFromAHelperPhoneAlertsTheOthers() {
        val now = 100L * 24 * 3600_000L
        assertTrue(SyncRunner.alerting(note("RED", now - 60_000), now))
        assertTrue(SyncRunner.alerting(note("AMBER", now - 60_000), now))
        assertFalse(SyncRunner.alerting(note("GREEN", now - 60_000), now))
        assertFalse("removed", SyncRunner.alerting(note("RED", now - 60_000, deleted = true), now))
        assertFalse("deleted", SyncRunner.alerting(note("RED", now - 60_000, del = true), now))
        assertFalse("old news caught up later", SyncRunner.alerting(note("RED", now - 25 * 3600_000L), now))
    }
}
