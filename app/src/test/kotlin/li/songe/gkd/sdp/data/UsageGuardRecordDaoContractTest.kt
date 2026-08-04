package li.songe.gkd.sdp.data

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageGuardRecordDaoContractTest {
    @Test
    fun latestRecordForAppContractExists() {
        assertNotNull(UsageGuardRecord.UsageGuardRecordDao::getLatestRecord)
    }

    @Test
    fun intervalQueryContractsExist() {
        assertNotNull(UsageGuardRecord.UsageGuardRecordDao::queryRecentRecords)
        assertNotNull(UsageGuardRecord.UsageGuardRecordDao::getPreviousRecord)
    }

    @Test
    fun usageRhythmColumnsAndLifecycleQueriesExist() {
        val fields = UsageGuardRecord::class.java.declaredFields.map { it.name }.toSet()
        assertTrue(fields.contains("lastUsageEndedAt"))
        assertTrue(fields.contains("requestGapMs"))

        val methods = UsageGuardRecord.UsageGuardRecordDao::class.java.declaredMethods
            .map { it.name }
            .toSet()
    }

    @Test
    fun insightProjectionDoesNotLoadReasonTagsOrNames() {
        val fields = UsageRequestInsightRow::class.java.declaredFields.map { it.name }.toSet()
        assertTrue(fields.contains("id"))
        assertTrue(fields.contains("requestedAt"))
        assertTrue(fields.contains("requestedDurationMinutes"))
        assertTrue(fields.contains("lastUsageEndedAt"))
        assertTrue(fields.contains("requestGapMs"))
        assertTrue(fields.none { it in setOf("reasonText", "tagNames", "appName") })
    }
}
