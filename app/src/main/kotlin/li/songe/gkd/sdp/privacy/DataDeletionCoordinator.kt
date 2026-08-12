package li.songe.gkd.sdp.privacy

import androidx.compose.runtime.Immutable
import li.songe.gkd.sdp.R

/**
 * Deletion coordination rules. History deletions never change `enabled`,
 * `locked`, the daily close quota, auto re-enable or rule configuration;
 * configuration deletions and "delete all" are blocked while an active usage
 * request, focus session or lock protection exists.
 */
object DataDeletionCoordinator {
    @Immutable
    data class SummaryText(
        val resId: Int,
        val args: List<Any>,
    )

    data class CategoryStatus(
        val recordCount: Long,
        val bytes: Long? = null,
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
    ): Boolean = isConfigurationCategory(category) &&
        !DataMutationCoordinator.configurationDeletionAllowed(status.hasActiveSession)

    fun deletionBlockReasonRes(category: DataCategory, status: CategoryStatus): Int? =
        if (deletionBlocked(category, status)) {
            R.string.privacy_block_active_session
        } else {
            null
        }

    /** History deletion never touches configuration or runtime state. */
    fun preservesConfiguration(category: DataCategory): Boolean =
        !isConfigurationCategory(category)

    fun summaryText(status: CategoryStatus): String {
        val count = status.recordCount
        val size = status.bytes?.let { bytes ->
            if (bytes >= 1_048_576L) {
                "%.1f MiB".format(bytes / 1_048_576.0)
            } else {
                "%.0f KiB".format(bytes / 1024.0)
            }
        // i18n-ignore: legacy fallback or non-display heuristic data
        } ?: "不可用"
        val range = when {
            status.earliestAt != null && status.latestAt != null ->
                // i18n-ignore: legacy fallback or non-display heuristic data
                "，最早 ${status.earliestAt}，最新 ${status.latestAt}"
            else -> ""
        }
        // i18n-ignore: legacy fallback or non-display heuristic data
        return "$count 条，$size$range"
    }

    fun summaryTextRes(status: CategoryStatus): SummaryText {
        val count = status.recordCount
        val size = status.bytes?.let { bytes ->
            if (bytes >= 1_048_576L) {
                "%.1f MiB".format(bytes / 1_048_576.0)
            } else {
                "%.0f KiB".format(bytes / 1024.0)
            }
        }
        return if (status.earliestAt != null && status.latestAt != null) {
            if (size == null) {
                SummaryText(
                    R.string.privacy_summary_count_range_unavailable,
                    listOf(count, status.earliestAt.toString(), status.latestAt.toString()),
                )
            } else {
                SummaryText(
                    R.string.privacy_summary_count_size_range,
                    listOf(count, size, status.earliestAt.toString(), status.latestAt.toString()),
                )
            }
        } else if (size == null) {
            SummaryText(R.string.privacy_summary_count_unavailable, listOf(count))
        } else {
            SummaryText(
                R.string.privacy_summary_count_size,
                listOf(count, size),
            )
        }
    }
}
