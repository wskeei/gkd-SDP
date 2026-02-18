package li.songe.gkd.sdp.service

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class AutoReenableEnforcerTest {
    @Test
    fun intervalZeroUsesAggressivePoll() {
        assertEquals(15_000L, AutoReenableEnforcer.computeDelayMs(0))
    }

    @Test
    fun enforceOperationsExecutesAllAndReturnsUpdatedCount() = runBlocking {
        val updated = AutoReenableEnforcer.runEnableOperations(
            listOf(
                { 2 },
                { 0 },
                { 3 },
            )
        )
        assertEquals(5, updated)
    }
}
