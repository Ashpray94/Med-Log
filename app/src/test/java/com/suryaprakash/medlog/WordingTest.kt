package com.suryaprakash.medlog

import com.suryaprakash.medlog.help.Wording
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WordingTest {
    private val all = listOf(
        Wording.sos("Kamala", "I fell", "chest pain, 10:42", "https://maps.google.com/?q=13.1,80.2"),
        Wording.sos("", "SOS", null, null),
        Wording.seeDoctorNow("Kamala", "Chest pain"),
        Wording.seeDoctorToday("Kamala", "Fever"),
        Wording.missedDose("Kamala", "8 am", "Metformin 500"),
        Wording.takenTwice("Kamala", "Amlodipine"),
        Wording.noCheckIn("Kamala"),
        Wording.refill("Kamala", "Metformin has 3 days left."),
        Wording.message("Kamala", "I need water", voice = false),
        Wording.message("Kamala", "Voice message", voice = true),
        Wording.alertTitle("Kamala", urgent = true),
        Wording.alertTitle("Kamala", urgent = false),
    )

    @Test fun everyMessageIsCalm() {
        for (m in all) assertEquals("$m", emptyList<String>(), Wording.check(m))
    }

    @Test fun catchesScaryWording() {
        assertTrue(Wording.check("MedLog: Kamala reported a danger sign. Please go now!").isNotEmpty())
        assertTrue(Wording.check("URGENT call her").isNotEmpty())
        assertTrue(Wording.check("took it again (double dose)").isNotEmpty())
    }
}
