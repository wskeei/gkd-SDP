package li.songe.gkd.sdp.privacy

/**
 * Aggregate boundary used by [DataInventoryRepository]. Keeping the inventory
 * and deletion workflow behind a narrow source makes the sensitive reset paths
 * testable without Android Room or a real device.
 */
internal interface DataInventorySource {
    suspend fun hasActiveLock(nowEpochMs: Long): Boolean
    suspend fun usageRecordCount(): Long
    suspend fun usageRecordActiveCount(): Long
    suspend fun focusSessionActive(): Boolean
    suspend fun focusSessionCount(): Long
    suspend fun interceptionTriggerCount(): Long
    suspend fun appInstallCount(): Long
    suspend fun snapshotCount(): Long
    suspend fun activityLogCount(): Long
    suspend fun a11yEventLogCount(): Long
    suspend fun appVisitCount(): Long
    suspend fun subsItemCount(): Long

    suspend fun deleteUsageRecords()
    suspend fun deleteFocusSessions()
    suspend fun deleteInterceptionTriggers()
    suspend fun deleteAppInstallHistory()
    suspend fun deleteSnapshotRowsAndFiles()
    suspend fun deleteEventLogs()
    suspend fun deleteSubscriptionsConfig()
    suspend fun deleteSelfControlConfig()
}
