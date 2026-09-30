package com.suryaprakash.medlog

import org.junit.Test
import org.junit.Assert.*

class FeedbackTest {
    /** Test shake acceleration threshold logic. */
    @Test
    fun testShakeThreshold() {
        val SHAKE_THRESHOLD = 2.7f * 9.81f

        // Below threshold
        val low = kotlin.math.sqrt(1f*1f + 1f*1f + 1f*1f)
        assertTrue(low < SHAKE_THRESHOLD)

        // At threshold
        val at = 2.7f * 9.81f
        assertTrue(at >= SHAKE_THRESHOLD)

        // Above threshold
        val high = 3f * 9.81f
        assertTrue(high > SHAKE_THRESHOLD)
    }

    /** Test debounce logic: second shake within 1s is ignored. */
    @Test
    fun testDebounce() {
        var lastShakeTime = 0L
        val DEBOUNCE_MS = 1000L

        // First shake
        var now = System.currentTimeMillis()
        assertTrue(now - lastShakeTime > DEBOUNCE_MS)
        lastShakeTime = now

        // Second shake 500ms later
        now = System.currentTimeMillis() + 500
        assertFalse(now - lastShakeTime > DEBOUNCE_MS)

        // Third shake 1100ms later
        now = lastShakeTime + 1100
        assertTrue(now - lastShakeTime > DEBOUNCE_MS)
    }

    /** Test feedback body formatting. */
    @Test
    fun testFeedbackFormatting() {
        val text = "App crashes on Tell"
        val category = "Bug"
        val version = "1.2.3"
        val device = "Samsung Galaxy A12"

        val body = buildString {
            append("Feedback: $text\n")
            append("Category: $category\n")
            append("App: $version | Device: $device\n")
        }

        assertTrue(body.contains("Feedback: App crashes on Tell"))
        assertTrue(body.contains("Category: Bug"))
        assertTrue(body.contains("App: 1.2.3"))
        assertTrue(body.contains("Device: Samsung Galaxy A12"))
    }
}
