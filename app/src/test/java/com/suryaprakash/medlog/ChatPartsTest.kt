package com.suryaprakash.medlog

import com.suryaprakash.medlog.ui.screens.ChatMsg
import com.suryaprakash.medlog.ui.screens.shouldShowDateSeparator
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatPartsTest {
    private fun message(at: Long) = ChatMsg("$at", "patient", "A", false, "Hello", at, "sent")

    @Test fun oneMessageHasADateSeparator() {
        assertTrue(shouldShowDateSeparator(listOf(message(0L)), 0))
    }

    @Test fun dateSeparatorsFollowDayGroupsWithoutReadingPastTheList() {
        val newestFirst = listOf(
            message(172_800_000L), message(172_800_000L),
            message(86_400_000L), message(86_400_000L),
            message(0L)
        )
        assertTrue(shouldShowDateSeparator(newestFirst, 0))
        assertFalse(shouldShowDateSeparator(newestFirst, 1))
        assertTrue(shouldShowDateSeparator(newestFirst, 2))
        assertFalse(shouldShowDateSeparator(newestFirst, 3))
        assertTrue(shouldShowDateSeparator(newestFirst, 4))
    }
}
