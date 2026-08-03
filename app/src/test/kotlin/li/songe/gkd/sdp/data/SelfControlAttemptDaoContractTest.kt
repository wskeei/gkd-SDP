package li.songe.gkd.sdp.data

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertEquals
import org.junit.Test

class SelfControlAttemptDaoContractTest {
    @Test
    fun recordAndGetPreviousContractExists() {
        assertNotNull(SelfControlAttempt.SelfControlAttemptDao::recordAndGetPrevious)
    }

    @Test
    fun eventRecordingAndRetentionContractsExist() {
        assertNotNull(SelfControlAttempt.SelfControlAttemptDao::recordEventAndGetInsight)
        assertNotNull(SelfControlAttempt.SelfControlAttemptDao::queryRecentCompletedIntervals)
        assertNotNull(SelfControlAttempt.SelfControlAttemptDao::queryByOccurredAtRange)
        assertEquals(90L, SelfControlAttempt.RETENTION_DAYS)
        assertEquals(10_000, SelfControlAttempt.MAX_EVENT_ROWS)
    }
}
