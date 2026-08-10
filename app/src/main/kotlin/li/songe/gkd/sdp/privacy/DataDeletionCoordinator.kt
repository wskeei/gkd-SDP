package li.songe.gkd.sdp.privacy

/** The user-visible data categories on the privacy & data page. */
enum class DataCategory {
    USAGE_REQUEST_HISTORY,
    FOCUS_SESSION_HISTORY,
    INTERCEPTION_TRIGGER_RECORDS,
    APP_INSTALL_MONITOR_HISTORY,
    SNAPSHOTS,
    EVENT_ACTIVITY_LOGS,
    DIAGNOSTICS_CRASH_SUMMARY,
    SUBSCRIPTIONS_RULES_CONFIG,
    SELF_CONTROL_CONFIG,
    ALL_APP_DATA,
}

/**
 * Deletion coordination rules. History deletions never change `enabled`,
 * `locked`, the daily close quota, auto re-enable or rule configuration;
 * configuration deletions and "delete all" are blocked while an active usage
 * request, focus session or lock protection exists.
 */
object DataDeletionCoordinator {
    data class CategoryStatus(
        val recordCount: Long,
        val bytes: Long,
        val earliestAt: Long? = null,
        val latestAt: Long? = null,
        val hasActiveSession: Boolean = false,
    )

    fun isConfigurationCategory(category: DataCategory): Boolean = when (category) {
        DataCategory.SUBSCRIPTIONS_RULES_CONFIG,
        DataCategory.SELF_CONTROL_CONFIG,
        DataCategory.ALL_APP_DATA,
        -> true

        else -> false
    }

    /** History categories can always be deleted; configuration cannot while active. */
    fun deletionBlocked(
        category: DataCategory,
        status: CategoryStatus,
    ): Boolean = isConfigurationCategory(category) && status.hasActiveSession

    fun deletionBlockReason(category: DataCategory, status: CategoryStatus): String? =
        if (deletionBlocked(category, status)) {
            "存在活动使用申请、专注会话或锁定保护，对应配置删除已禁用；历史删除可在活动会话结束后执行。"
        } else {
            null
        }

    /** History deletion never touches configuration or runtime state. */
    fun preservesConfiguration(category: DataCategory): Boolean =
        !isConfigurationCategory(category)

    fun summaryText(status: CategoryStatus): String {
        val count = status.recordCount
        val size = if (status.bytes >= 1_048_576L) {
            "%.1f MiB".format(status.bytes / 1_048_576.0)
        } else {
            "%.0f KiB".format(status.bytes / 1024.0)
        }
        val range = when {
            status.earliestAt != null && status.latestAt != null ->
                "，最早 ${status.earliestAt}，最新 ${status.latestAt}"
            else -> ""
        }
        return "$count 条，$size$range"
    }
}
