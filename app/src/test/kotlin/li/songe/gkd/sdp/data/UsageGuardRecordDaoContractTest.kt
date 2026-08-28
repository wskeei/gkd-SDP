package li.songe.gkd.sdp.data

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertFalse
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
        assertNotNull(UsageGuardRecord.UsageGuardRecordDao::getLatestInsightRow)
    }

    @Test
    fun usageRhythmColumnsAndLifecycleQueriesExist() {
        val fields = UsageGuardRecord::class.java.declaredFields.map { it.name }.toSet()
        assertTrue(fields.contains("lastUsageEndedAt"))
        assertTrue(fields.contains("requestGapMs"))

        val methods = UsageGuardRecord.UsageGuardRecordDao::class.java.declaredMethods
            .map { it.name }
            .toSet()
        assertTrue(methods.contains("markUsageEnded"))
        assertTrue(methods.contains("markUsageStarted"))
        assertTrue(methods.contains("closeRecordFromActiveUse"))
        assertTrue(methods.contains("queryInsightRowsByAppAndRequestedAtRange"))
        assertTrue(methods.contains("getLatestInsightRow"))
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

    @Test
    fun reviewProjectionContainsOnlyNonSensitiveSummaryFields() {
        val fields = UsageReviewRow::class.java.declaredFields.map { it.name }.toSet()
        assertTrue(fields.containsAll(setOf(
            "id",
            "appId",
            "appName",
            "tagNames",
            "requestedDurationMinutes",
            "requestedAt",
            "endReason",
            "requestGapMs",
        )))
        assertFalse(fields.any { it in setOf("reasonText", "grantedAt", "expiresAt", "endedAt", "lastUsageEndedAt") })
    }

    @Test
    fun reviewProjectionQueryContractExists() {
        assertNotNull(UsageGuardRecord.UsageGuardRecordDao::queryReviewRowsByRequestedAtRange)
        val sql = UsageGuardRecord.UsageGuardRecordDao.REVIEW_ROWS_QUERY_SQL
            .replace(Regex("\\s+"), " ")
        assertTrue(sql.contains("SELECT id, app_id, app_name, tag_names"))
        assertTrue(sql.contains("requested_at >= :startAt"))
        assertTrue(sql.contains("requested_at < :endAt"))
    }
}
