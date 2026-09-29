package com.suryaprakash.medlog

import com.suryaprakash.medlog.data.Profile
import com.suryaprakash.medlog.data.Settings
import com.suryaprakash.medlog.data.fillFrom
import com.suryaprakash.medlog.data.withPerson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PersonSettingsTest {
    @Test fun theProfilesValuesGoOverThePhonesAndAnUnsetOneStays() {
        val own = Settings(waterGoal = 6, snoozeMinutes = 15, emergencyNumber = "112", easyMode = false)
        val s = own.withPerson(Profile(waterGoal = 10, diabetic = true))
        assertEquals(10, s.waterGoal); assertEquals(true, s.diabetic)
        assertEquals(15, s.snoozeMinutes); assertEquals("112", s.emergencyNumber)      // not in the profile yet: the phone's own stays
        assertEquals(false, s.easyMode)                                                // how THIS phone looks is never touched
    }

    @Test fun whileAHelperLooksAtAReplicaTheHelpersOwnValuesAreNotShownAsThePersons() {
        val helper = Settings(waterGoal = 6, emergencyNumber = "112", bigMode = true)
        val shown = helper.withPerson(Profile(waterGoal = 10), unset = Settings())
        assertEquals(10, shown.waterGoal)
        assertEquals("108", shown.emergencyNumber)                                     // her profile has none: the default, not the helper's 112
        assertEquals(true, shown.bigMode)
        assertEquals(Settings().waterGoal, helper.withPerson(null, unset = Settings()).waterGoal)
    }

    @Test fun theOneTimeMoveFillsOnlyWhatTheProfileDoesNotHaveYet() {
        val s = Settings(waterGoal = 6, diabetic = true, snoozeMinutes = 5, checkInEnabled = true, checkInTime = "09:00")
        val p = Profile(waterGoal = 10).fillFrom(s, kcalTarget = 1800.0, proteinTarget = null, customFoods = "[]", followups = "")
        assertEquals(10, p.waterGoal)                                                  // a helper had already set it from a replica
        assertEquals(true, p.diabetic); assertEquals(5, p.snoozeMinutes); assertEquals("09:00", p.checkInTime)
        assertEquals(1800.0, p.kcalTarget); assertNull(p.proteinTarget); assertEquals("[]", p.customFoods); assertNull(p.followups)
    }
}
