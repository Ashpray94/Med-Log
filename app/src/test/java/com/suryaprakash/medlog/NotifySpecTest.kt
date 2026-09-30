package com.suryaprakash.medlog

import com.suryaprakash.medlog.help.Wording
import com.suryaprakash.medlog.notify.NotifySpec
import com.suryaprakash.medlog.notify.NotifySpec.Fallback
import com.suryaprakash.medlog.notify.NotifySpec.Quiet
import com.suryaprakash.medlog.notify.NotifySpec.Show
import com.suryaprakash.medlog.notify.NotifySpec.Tier
import com.suryaprakash.medlog.notify.NotifySpec.Type
import com.suryaprakash.medlog.notify.NotifySpec.Who
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotifySpecTest {
    private fun words(s: String) = s.trim().split(Regex("\\s+")).filter { it.isNotBlank() }

    @Test fun everyTypeHasExactlyOneRow() {
        assertEquals(Type.values().toSet(), NotifySpec.table.map { it.type }.toSet())
        assertEquals(Type.values().size, NotifySpec.table.size)
    }

    @Test fun everyTypeHasResponsesAndAudience() {
        for (r in NotifySpec.table) {
            assertTrue("${r.type} responses", r.responses.isNotEmpty())
            assertTrue("${r.type} audience", r.to.isNotEmpty())
            assertEquals("${r.type} unique response ids", r.responses.size, r.responses.map { it.id }.toSet().size)
            assertTrue("${r.type} notification id", r.notifId > 0)
        }
        assertEquals(4, NotifySpec[Type.DOSE_DUE].responses.size)
        assertTrue(NotifySpec[Type.DOSE_DUE].resp("skip")!!.asksWhy)
    }

    @Test fun importantAndAboveEscalate() {
        for (r in NotifySpec.table.filter { it.tier >= Tier.IMPORTANT }) assertTrue("${r.type} needs escalation", r.escalation.isNotEmpty())
        for (r in NotifySpec.table.filter { it.tier >= Tier.URGENT }) {
            assertEquals("${r.type} never held by quiet hours", Quiet.DELIVER, r.quiet)
            assertEquals(NotifySpec.Sound.ALARM, r.sound)
        }
    }

    @Test fun hospitalSignWording() {
        for (r in NotifySpec.table) {
            assertTrue("${r.type} title: ${r.title}", words(r.title).size <= 5)
            assertTrue("${r.type} body: ${r.body}", words(r.body).size <= 8)
            assertEquals("${r.type} title tone", emptyList<String>(), Wording.check(r.title.replace(Regex("\\{\\w+}"), "x")))
            assertEquals("${r.type} body tone", emptyList<String>(), Wording.check(r.body.replace(Regex("\\{\\w+}"), "x")))
            for (b in r.responses) {
                val w = words(b.label)
                assertTrue("${r.type}/${b.id} <=3 words: ${b.label}", w.size <= 3)
                val first = w.first().lowercase().trim(':', ',')
                assertTrue("${r.type}/${b.id} verb first: ${b.label}", first in NotifySpec.VERBS)
            }
        }
    }

    @Test fun everyStateResolves() {
        val states = NotifySpec.allStates()
        assertEquals(2 * 2 * 2 * 2 * 2 * 2 * 3 * 3, states.size)
        for (t in Type.values()) for (s in states) {
            val row = NotifySpec[t]
            val b = NotifySpec.resolve(t, s)
            if (s.receiver !in row.to) { assertEquals(Show.NOT_ADDRESSED, b.show); continue }
            assertTrue("$t $s", b.show != Show.NOT_ADDRESSED)
            if (row.tier >= Tier.URGENT) {
                assertTrue("$t $s urgent must be seen", b.show != Show.HOLD_UNTIL_MORNING)
                assertEquals("$t $s urgent keeps alarm", NotifySpec.Sound.ALARM, b.sound)
            }
            if (s.perm == NotifySpec.Perm.NOTIFICATIONS_DENIED) {
                assertTrue("$t $s fallback when notifications denied", Fallback.IN_APP_BANNER in b.fallbacks)
                if (row.tier >= Tier.IMPORTANT) assertTrue("$t $s speaks", Fallback.SPEAK in b.fallbacks)
            }
            if (s.net != NotifySpec.Net.ONLINE && s.receiver == Who.PATIENT && Who.HELPERS in row.to) assertTrue("$t $s SMS fallback", Fallback.SMS_HELPERS in b.fallbacks)
            if (s.net == NotifySpec.Net.RELAY_DOWN) assertTrue("$t $s queue", Fallback.RELAY_QUEUE in b.fallbacks)
            if (s.dnd && row.tier < Tier.URGENT) assertEquals(NotifySpec.Sound.NONE, b.sound)
        }
    }

    @Test fun quietHoursAndDoseSchedule() {
        assertTrue(NotifySpec.isQuiet(23)); assertTrue(NotifySpec.isQuiet(3)); assertFalse(NotifySpec.isQuiet(12))
        assertEquals(2, NotifySpec.doseRepeats)
        assertEquals(15, NotifySpec.helperDelayMin(true, 30, 15))
        assertEquals(30, NotifySpec.helperDelayMin(false, 30, 15))
        assertEquals("Snooze 10 min", NotifySpec.label(Type.DOSE_DUE, "snooze", 10))
    }

    @Test fun answersTellTheRightPeople() {
        var heard = 0
        NotifySpec.announce = { _, _, _ -> heard++ }
        try {
            assertEquals(emptySet<Who>(), NotifySpec.responded(Type.DOSE_DUE, "take", Who.PATIENT, escalated = false))
            assertEquals(setOf(Who.HELPERS), NotifySpec.responded(Type.DOSE_DUE, "take", Who.PATIENT, escalated = true))
            assertEquals(setOf(Who.PATIENT, Who.HELPERS), NotifySpec.responded(Type.HELPER_ALERT, "coming", Who.HELPERS))
            assertEquals(2, heard)
        } finally { NotifySpec.announce = { _, _, _ -> } }
    }

    @Test fun entryWordingIsShortAndCalm() {
        val t = Wording.entryAddedTitle("Ravi")
        assertTrue(words(t).size <= 5); assertEquals(emptyList<String>(), Wording.check(t))
        assertEquals(emptyList<String>(), Wording.check(Wording.doseAnswered("Kamala", "Metformin", "skipped")))
    }
}
