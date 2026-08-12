package li.songe.gkd.sdp.ui.privacy

import androidx.compose.runtime.Immutable
import li.songe.gkd.sdp.R
import li.songe.gkd.sdp.privacy.DataCategory
import li.songe.gkd.sdp.privacy.DataDeletionCoordinator

object PrivacyDataPresenter {
    @Immutable
    data class CategoryUi(
        val category: DataCategory,
        val titleRes: Int,
        val descriptionRes: Int,
        val summary: String,
        val summaryRes: Int,
        val summaryArgs: List<Any>,
        val deletable: Boolean,
        val blockReasonRes: Int? = null,
    )

    fun present(
        inventory: Map<DataCategory, DataDeletionCoordinator.CategoryStatus>,
    ): List<CategoryUi> = DataCategory.entries.map { category ->
        val status = inventory[category] ?: DataDeletionCoordinator.CategoryStatus(
            recordCount = 0L,
            bytes = 0L,
        )
        val blocked = DataDeletionCoordinator.deletionBlocked(category, status)
        val deletable = !blocked
        CategoryUi(
            category = category,
            titleRes = titleRes(category),
            descriptionRes = descriptionRes(category),
            summary = DataDeletionCoordinator.summaryText(status),
            summaryRes = DataDeletionCoordinator.summaryTextRes(status).resId,
            summaryArgs = DataDeletionCoordinator.summaryTextRes(status).args,
            deletable = deletable,
            blockReasonRes = DataDeletionCoordinator.deletionBlockReasonRes(category, status)
                ?: if (!deletable && category != DataCategory.ALL_APP_DATA) {
                    R.string.privacy_block_config_manage
                } else {
                    null
                },
        )
    }

    private fun titleRes(category: DataCategory): Int = when (category) {
        DataCategory.USAGE_REQUEST_HISTORY -> R.string.privacy_category_usage_request_history_title
        DataCategory.FOCUS_SESSION_HISTORY -> R.string.privacy_category_focus_session_history_title
        DataCategory.INTERCEPTION_TRIGGER_RECORDS -> R.string.privacy_category_interception_trigger_records_title
        DataCategory.APP_INSTALL_MONITOR_HISTORY -> R.string.privacy_category_app_install_monitor_history_title
        DataCategory.SNAPSHOTS -> R.string.privacy_category_snapshots_title
        DataCategory.EVENT_ACTIVITY_LOGS -> R.string.privacy_category_event_activity_logs_title
        DataCategory.DIAGNOSTICS_CRASH_SUMMARY -> R.string.privacy_category_diagnostics_crash_summary_title
        DataCategory.SUBSCRIPTIONS_RULES_CONFIG -> R.string.privacy_category_subscriptions_rules_config_title
        DataCategory.SELF_CONTROL_CONFIG -> R.string.privacy_category_self_control_config_title
        DataCategory.ALL_APP_DATA -> R.string.privacy_category_all_app_data_title
    }

    private fun descriptionRes(category: DataCategory): Int = when (category) {
        DataCategory.USAGE_REQUEST_HISTORY -> R.string.privacy_category_usage_request_history_description
        DataCategory.FOCUS_SESSION_HISTORY -> R.string.privacy_category_focus_session_history_description
        DataCategory.INTERCEPTION_TRIGGER_RECORDS -> R.string.privacy_category_interception_trigger_records_description
        DataCategory.APP_INSTALL_MONITOR_HISTORY -> R.string.privacy_category_app_install_monitor_history_description
        DataCategory.SNAPSHOTS -> R.string.privacy_category_snapshots_description
        DataCategory.EVENT_ACTIVITY_LOGS -> R.string.privacy_category_event_activity_logs_description
        DataCategory.DIAGNOSTICS_CRASH_SUMMARY -> R.string.privacy_category_diagnostics_crash_summary_description
        DataCategory.SUBSCRIPTIONS_RULES_CONFIG -> R.string.privacy_category_subscriptions_rules_config_description
        DataCategory.SELF_CONTROL_CONFIG -> R.string.privacy_category_self_control_config_description
        DataCategory.ALL_APP_DATA -> R.string.privacy_category_all_app_data_description
    }
}
