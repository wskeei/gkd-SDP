package li.songe.gkd.sdp.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoReenablePolicyTest {
    @Test
    fun normalizeIntervalClampsToBounds() {
        assertEquals(0, AutoReenablePolicy.normalizeIntervalMinutes(-1))
        assertEquals(240, AutoReenablePolicy.normalizeIntervalMinutes(999))
    }

    @Test
    fun cooldownBlocksEditBefore72Hours() {
        val now = 1_000_000_000L
        val last = now - (71L * 60 * 60 * 1000)
        assertFalse(AutoReenablePolicy.canChangeInterval(last, now))
    }

    @Test
    fun cooldownAllowsEditAt72HoursOrLater() {
        val now = 1_000_000_000L
        val last = now - (72L * 60 * 60 * 1000)
        assertTrue(AutoReenablePolicy.canChangeInterval(last, now))
    }
}
