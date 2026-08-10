package li.songe.gkd.sdp.ui.privacy

import li.songe.gkd.sdp.privacy.DataCategory
import li.songe.gkd.sdp.privacy.DataDeletionCoordinator

object PrivacyDataPresenter {
    data class CategoryUi(
        val category: DataCategory,
        val title: String,
        val description: String,
        val summary: String,
        val deletable: Boolean,
        val blockReason: String? = null,
    )

    fun present(
        inventory: Map<DataCategory, DataDeletionCoordinator.CategoryStatus>,
    ): List<CategoryUi> = DataCategory.entries.map { category ->
        val status = inventory[category] ?: DataDeletionCoordinator.CategoryStatus(
            recordCount = 0L,
            bytes = 0L,
        )
        val blocked = DataDeletionCoordinator.deletionBlocked(category, status)
        val deletable = category !in setOf(
            DataCategory.SUBSCRIPTIONS_RULES_CONFIG,
            DataCategory.SELF_CONTROL_CONFIG,
            DataCategory.ALL_APP_DATA,
        ) && !blocked
        CategoryUi(
            category = category,
            title = title(category),
            description = description(category),
            summary = DataDeletionCoordinator.summaryText(status),
            deletable = deletable,
            blockReason = DataDeletionCoordinator.deletionBlockReason(category, status)
                ?: if (!deletable && category != DataCategory.ALL_APP_DATA) {
                    "请在对应设置页管理订阅与自律配置。"
                } else {
                    null
                },
        )
    }

    private fun title(category: DataCategory): String = when (category) {
        DataCategory.USAGE_REQUEST_HISTORY -> "使用申请历史"
        DataCategory.FOCUS_SESSION_HISTORY -> "专注会话历史"
        DataCategory.INTERCEPTION_TRIGGER_RECORDS -> "拦截与触发记录"
        DataCategory.APP_INSTALL_MONITOR_HISTORY -> "应用安装监控历史"
        DataCategory.SNAPSHOTS -> "快照与截图"
        DataCategory.EVENT_ACTIVITY_LOGS -> "事件与活动日志"
        DataCategory.DIAGNOSTICS_CRASH_SUMMARY -> "诊断与崩溃摘要"
        DataCategory.SUBSCRIPTIONS_RULES_CONFIG -> "订阅与规则配置"
        DataCategory.SELF_CONTROL_CONFIG -> "数字自律配置"
        DataCategory.ALL_APP_DATA -> "全部应用数据"
    }

    private fun description(category: DataCategory): String = when (category) {
        DataCategory.USAGE_REQUEST_HISTORY -> "删除本地使用申请记录，不改变当前自律开关。"
        DataCategory.FOCUS_SESSION_HISTORY -> "删除专注会话记录，不改变规则和锁定状态。"
        DataCategory.INTERCEPTION_TRIGGER_RECORDS -> "删除应用、网址、选择器拦截与触发历史。"
        DataCategory.APP_INSTALL_MONITOR_HISTORY -> "删除应用安装/卸载监控历史。"
        DataCategory.SNAPSHOTS -> "同时删除数据库记录与本地快照文件。"
        DataCategory.EVENT_ACTIVITY_LOGS -> "删除界面、无障碍事件和应用访问日志。"
        DataCategory.DIAGNOSTICS_CRASH_SUMMARY -> "删除本地脱敏诊断事件与崩溃摘要。"
        DataCategory.SUBSCRIPTIONS_RULES_CONFIG -> "删除订阅与规则配置。"
        DataCategory.SELF_CONTROL_CONFIG -> "重置数字自律配置，保留当前运行时状态。"
        DataCategory.ALL_APP_DATA -> "删除本机全部应用数据；需要输入确认短语。"
    }
}
