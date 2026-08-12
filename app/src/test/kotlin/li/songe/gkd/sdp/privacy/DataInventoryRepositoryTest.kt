package li.songe.gkd.sdp.privacy

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DataInventoryRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun inventoryReportsCountsSizesAndActiveState() = runBlocking {
        val source = FakeSource(
            usageCount = 5,
            focusCount = 2,
            triggerCount = 9,
            appInstallCount = 3,
            snapshotCount = 1,
            activityLogCount = 4,
            a11yLogCount = 2,
            appVisitCount = 1,
            subsCount = 7,
        )
        val snapshot = temporaryFolder.newFile("snapshot.bin").apply { writeBytes(ByteArray(512)) }
        val diagnostic = temporaryFolder.newFile("diagnostic.log").apply { writeBytes(ByteArray(128)) }
        val repository = DataInventoryRepository(
            source = source,
            nowEpochMs = { 123L },
            snapshotDir = { listOf(snapshot) },
            diagnosticsDir = { listOf(diagnostic) },
        )

        val inventory = repository.inventory()

        assertEquals(5, inventory.getValue(DataCategory.USAGE_REQUEST_HISTORY).recordCount)
        assertEquals(2, inventory.getValue(DataCategory.FOCUS_SESSION_HISTORY).recordCount)
        assertEquals(9, inventory.getValue(DataCategory.INTERCEPTION_TRIGGER_RECORDS).recordCount)
        assertEquals(3, inventory.getValue(DataCategory.APP_INSTALL_MONITOR_HISTORY).recordCount)
        assertEquals(512L, inventory.getValue(DataCategory.SNAPSHOTS).bytes)
        assertEquals(7, inventory.getValue(DataCategory.EVENT_ACTIVITY_LOGS).recordCount)
        assertEquals(1, inventory.getValue(DataCategory.DIAGNOSTICS_CRASH_SUMMARY).recordCount)
        assertEquals(128L, inventory.getValue(DataCategory.DIAGNOSTICS_CRASH_SUMMARY).bytes)
        assertEquals(7, inventory.getValue(DataCategory.SUBSCRIPTIONS_RULES_CONFIG).recordCount)
        assertFalse(inventory.getValue(DataCategory.SELF_CONTROL_CONFIG).hasActiveSession)
        assertFalse(inventory.getValue(DataCategory.ALL_APP_DATA).hasActiveSession)
    }

    @Test
    fun activeLockMarksConfigurationCategoriesAsBlocked() = runBlocking {
        val source = FakeSource(activeLock = true)
        val repository = DataInventoryRepository(
            source = source,
            snapshotDir = { emptyList() },
            diagnosticsDir = { emptyList() },
        )

        val inventory = repository.inventory()

        assertTrue(inventory.getValue(DataCategory.SUBSCRIPTIONS_RULES_CONFIG).hasActiveSession)
        assertTrue(inventory.getValue(DataCategory.SELF_CONTROL_CONFIG).hasActiveSession)
        assertTrue(inventory.getValue(DataCategory.ALL_APP_DATA).hasActiveSession)
        assertFalse(inventory.getValue(DataCategory.USAGE_REQUEST_HISTORY).hasActiveSession)
    }

    @Test
    fun deleteAllWalksEveryNonAllCategory() = runBlocking {
        val source = FakeSource()
        val repository = DataInventoryRepository(
            source = source,
            snapshotDir = { emptyList() },
            diagnosticsDir = { emptyList() },
        )

        val result = repository.delete(DataCategory.ALL_APP_DATA)

        assertTrue(result.isSuccess)
        assertEquals(
            DataCategory.entries
                .filterNot { it == DataCategory.ALL_APP_DATA }
                .filterNot { it == DataCategory.DIAGNOSTICS_CRASH_SUMMARY }
                .toSet(),
            source.deletedCategories,
        )
    }

    @Test
    fun deleteDiagnosticsRemovesOnlyListedDiagnosticFiles() = runBlocking {
        val source = FakeSource()
        val diagnostic = temporaryFolder.newFile("diagnostic.json")
        val kept = temporaryFolder.newFile("user-file.txt")
        val repository = DataInventoryRepository(
            source = source,
            snapshotDir = { emptyList() },
            diagnosticsDir = { listOf(diagnostic) },
        )

        val result = repository.delete(DataCategory.DIAGNOSTICS_CRASH_SUMMARY)

        assertTrue(result.isSuccess)
        assertFalse(diagnostic.exists())
        assertTrue(kept.exists())
    }

    private class FakeSource(
        private val activeLock: Boolean = false,
        private val usageCount: Long = 0,
        private val focusCount: Long = 0,
        private val triggerCount: Long = 0,
        private val appInstallCount: Long = 0,
        private val snapshotCount: Long = 0,
        private val activityLogCount: Long = 0,
        private val a11yLogCount: Long = 0,
        private val appVisitCount: Long = 0,
        private val subsCount: Long = 0,
    ) : DataInventorySource {
        val deletedCategories = mutableSetOf<DataCategory>()

        override suspend fun hasActiveLock(nowEpochMs: Long): Boolean = activeLock

        override suspend fun usageRecordCount(): Long = usageCount

        override suspend fun usageRecordActiveCount(): Long = 0

        override suspend fun focusSessionActive(): Boolean = false

        override suspend fun focusSessionCount(): Long = focusCount

        override suspend fun interceptionTriggerCount(): Long = triggerCount

        override suspend fun appInstallCount(): Long = appInstallCount

        override suspend fun snapshotCount(): Long = snapshotCount

        override suspend fun activityLogCount(): Long = activityLogCount

        override suspend fun a11yEventLogCount(): Long = a11yLogCount

        override suspend fun appVisitCount(): Long = appVisitCount

        override suspend fun subsItemCount(): Long = subsCount

        override suspend fun deleteUsageRecords() {
            deletedCategories += DataCategory.USAGE_REQUEST_HISTORY
        }

        override suspend fun deleteFocusSessions() {
            deletedCategories += DataCategory.FOCUS_SESSION_HISTORY
        }

        override suspend fun deleteInterceptionTriggers() {
            deletedCategories += DataCategory.INTERCEPTION_TRIGGER_RECORDS
        }

        override suspend fun deleteAppInstallHistory() {
            deletedCategories += DataCategory.APP_INSTALL_MONITOR_HISTORY
        }

        override suspend fun deleteSnapshotRowsAndFiles() {
            deletedCategories += DataCategory.SNAPSHOTS
        }

        override suspend fun deleteEventLogs() {
            deletedCategories += DataCategory.EVENT_ACTIVITY_LOGS
        }

        override suspend fun deleteSubscriptionsConfig() {
            deletedCategories += DataCategory.SUBSCRIPTIONS_RULES_CONFIG
        }

        override suspend fun deleteSelfControlConfig() {
            deletedCategories += DataCategory.SELF_CONTROL_CONFIG
        }
    }
}
