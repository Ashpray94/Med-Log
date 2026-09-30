package com.suryaprakash.medlog.data

import org.junit.Test
import org.junit.Assert.assertEquals

class SyncTest {
    @Test
    fun `shouldApply unknown entry should return true`() {
        val result = Sync.shouldApply(existingAt = null, incomingAt = 1000L, existingDeleted = false)
        assertEquals(true, result)
    }

    @Test
    fun `shouldApply newer should return true`() {
        val result = Sync.shouldApply(existingAt = 500L, incomingAt = 1000L, existingDeleted = false)
        assertEquals(true, result)
    }

    @Test
    fun `shouldApply older should return false`() {
        val result = Sync.shouldApply(existingAt = 1000L, incomingAt = 500L, existingDeleted = false)
        assertEquals(false, result)
    }

    @Test
    fun `shouldApply equal should return false`() {
        val result = Sync.shouldApply(existingAt = 1000L, incomingAt = 1000L, existingDeleted = false)
        assertEquals(false, result)
    }

    @Test
    fun `shouldApply tombstone newer than edit should return true`() {
        val result = Sync.shouldApply(existingAt = 1000L, incomingAt = 1000L, existingDeleted = true)
        assertEquals(true, result)
    }
}
