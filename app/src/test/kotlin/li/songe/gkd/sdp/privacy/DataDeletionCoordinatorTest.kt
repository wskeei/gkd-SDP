package li.songe.gkd.sdp.privacy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DataDeletionCoordinatorTest {
    @Test
    fun configurationCategoriesAreIdentified() {
        assertTrue(DataDeletionCoordinator.isConfigurationCategory(DataCategory.SUBSCRIPTIONS_RULES_CONFIG))
        assertTrue(DataDeletionCoordinator.isConfigurationCategory(DataCategory.SELF_CONTROL_CONFIG))
        assertTrue(DataDeletionCoordinator.isConfigurationCategory(DataCategory.ALL_APP_DATA))
        assertFalse(DataDeletionCoordinator.isConfigurationCategory(DataCategory.USAGE_REQUEST_HISTORY))
        assertFalse(DataDeletionCoordinator.isConfigurationCategory(DataCategory.SNAPSHOTS))
    }

    @Test
    fun historyDeletionIsNeverBlockedByActiveSessions() {
        val active = DataDeletionCoordinator.CategoryStatus(
            recordCount = 10,
            bytes = 1024,
            hasActiveSession = true,
        )
        assertFalse(DataDeletionCoordinator.deletionBlocked(DataCategory.USAGE_REQUEST_HISTORY, active))
        assertNull(DataDeletionCoordinator.deletionBlockReasonRes(DataCategory.USAGE_REQUEST_HISTORY, active))
    }

    @Test
    fun configurationDeletionIsBlockedWhileActive() {
        val active = DataDeletionCoordinator.CategoryStatus(
            recordCount = 1,
            bytes = 512,
            hasActiveSession = true,
        )
        assertTrue(DataDeletionCoordinator.deletionBlocked(DataCategory.SELF_CONTROL_CONFIG, active))
        assertTrue(DataDeletionCoordinator.deletionBlocked(DataCategory.ALL_APP_DATA, active))
        assertTrue(DataDeletionCoordinator.deletionBlockReasonRes(DataCategory.ALL_APP_DATA, active) != null)
    }

    @Test
    fun configurationDeletionAllowedWithoutActiveSession() {
        val idle = DataDeletionCoordinator.CategoryStatus(recordCount = 1, bytes = 512, hasActiveSession = false)
        assertFalse(DataDeletionCoordinator.deletionBlocked(DataCategory.SELF_CONTROL_CONFIG, idle))
    }

    @Test
    fun historyDeletionPreservesConfiguration() {
        assertTrue(DataDeletionCoordinator.preservesConfiguration(DataCategory.FOCUS_SESSION_HISTORY))
        assertFalse(DataDeletionCoordinator.preservesConfiguration(DataCategory.ALL_APP_DATA))
    }

    @Test
    fun summaryShowsCountSizeAndRangeWithoutSensitiveContent() {
        val status = DataDeletionCoordinator.CategoryStatus(
            recordCount = 42,
            bytes = 2_097_152,
            earliestAt = 1_000L,
            latestAt = 2_000L,
        )
        val text = DataDeletionCoordinator.summaryText(status)
        assertTrue(text.contains("42 条"))
        assertTrue(text.contains("MiB"))
        assertTrue(text.contains("最早"))
        assertFalse(text.contains("理由"))
        assertFalse(text.contains("URL"))
    }
}
