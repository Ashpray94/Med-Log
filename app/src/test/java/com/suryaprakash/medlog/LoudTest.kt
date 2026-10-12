package com.suryaprakash.medlog

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.suryaprakash.medlog.help.AlertSound
import com.suryaprakash.medlog.help.Loud
import com.suryaprakash.medlog.help.SilenceReceiver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** A loud reminder always goes quiet when swiped or answered, and what it was about stays in view until answered. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LoudTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val nm get() = ctx.getSystemService(NotificationManager::class.java)

    @Test fun swipeSilencesAndLeavesAPinnedQuietReminder() {
        AlertSound.start(ctx, urgent = true)
        assertTrue(AlertSound.deadline.value != null)
        // what Android sends when the notification is swiped away
        val swipe = SilenceReceiver.intent(ctx, 5042, "Amma: Please come", "Not answered yet. Tap to answer.", null)
        shadowOf(swipe).savedIntent.let { SilenceReceiver().onReceive(ctx, it) }
        assertNull(AlertSound.deadline.value)
        assertFalse(AlertSound.screaming.value)
        val quiet = shadowOf(nm).getNotification(5042)
        assertTrue("stays in view until answered", quiet.flags and android.app.Notification.FLAG_ONGOING_EVENT != 0)
        assertEquals("Amma: Please come", shadowOf(quiet).contentTitle.toString())
    }

    @Test fun answeringStopsTheSoundAndClearsTheReminder() {
        AlertSound.start(ctx, urgent = false)
        shadowOf(SilenceReceiver.intent(ctx, Loud.alertId(7), "Amma: Water", "Not answered yet.", null)).savedIntent.let { SilenceReceiver().onReceive(ctx, it) }
        Loud.done(ctx, Loud.alertId(7))
        assertNull(AlertSound.deadline.value)
        assertNull(shadowOf(nm).getNotification(Loud.alertId(7)))
    }

    @Test fun anotherHelperAnsweringQuietsThisPhone() {
        val app = ctx as MedLogApp
        Loud.rang(ctx, "mid123", 9)
        AlertSound.start(ctx, urgent = false)
        Loud.answeredElsewhere(ctx, "mid123")
        assertNull(AlertSound.deadline.value)
        assertTrue(app.settings.getLong("rang_mid123") == 9L)
    }
}
