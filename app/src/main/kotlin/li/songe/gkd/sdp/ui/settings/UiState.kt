@file:JvmName("SettingsUiState")

package li.songe.gkd.sdp.ui.home

import android.net.Uri
import li.songe.gkd.sdp.backup.PreparedBackupImport
import li.songe.gkd.sdp.backup.BackupErrorCode
import li.songe.gkd.sdp.util.BackupUtils
internal enum class BackupWorkflowStage {
    EXPORT_CATEGORIES,
    EXPORT_SUMMARY,
    EXPORT_PASSWORD,
    IMPORT_PASSWORD,
    IMPORT_PREVIEW,
}

internal data class BackupWorkflowState(
    val stage: BackupWorkflowStage,
    val selectedCategoryIds: Set<String> = BackupUtils.defaultCategoryIds,
    val sourceUri: Uri? = null,
    val password: String = "",
    val repeatedPassword: String = "",
    val preparedImport: PreparedBackupImport? = null,
    val busy: Boolean = false,
    val errorText: String? = null,
)

internal fun backupCategoryTitle(categoryId: String): String = when (categoryId) {
    "settings" -> "应用设置"
    "subscriptions" -> "订阅与规则配置"
    "self_control_config" -> "数字自律配置"
    "self_control_history" -> "数字自律历史"
    "upstream_history" -> "规则触发与活动历史"
    "sensitive_optional" -> "敏感可选数据"
    else -> categoryId
}

internal fun backupCategorySubtitle(categoryId: String): String = when (categoryId) {
    "settings" -> "普通 Store；不含私有 Store、令牌和本机权限"
    "subscriptions" -> "订阅表、配置表与订阅 JSON"
    "self_control_config" -> "专注、拦截、锁定、使用申请与监控配置"
    "self_control_history" -> "专注会话、使用申请、拦截尝试与安装记录"
    "upstream_history" -> "规则动作、Activity 与应用访问历史"
    else -> ""
}

internal fun backupErrorText(code: BackupErrorCode): String = when (code) {
    BackupErrorCode.WEAK_PASSWORD -> "密码至少需要 12 个 Unicode 字符"
    BackupErrorCode.AUTHENTICATION_FAILED -> "密码错误，或备份内容已损坏"
    BackupErrorCode.INVALID_MAGIC -> "所选文件不是 GKD-SDP 加密备份 v2"
    BackupErrorCode.TRUNCATED -> "备份文件不完整"
    BackupErrorCode.UNSUPPORTED_VERSION,
    BackupErrorCode.UNSUPPORTED_KDF -> "此备份格式暂不受当前版本支持"
    BackupErrorCode.SCHEMA_MISMATCH -> "备份数据结构与当前版本不兼容"
    BackupErrorCode.REFERENCE_MISMATCH -> "备份中的数据引用不完整"
    BackupErrorCode.NONCE_REUSE,
    BackupErrorCode.MALFORMED_HEADER,
    BackupErrorCode.INVALID_PAYLOAD -> "备份校验失败，文件可能已损坏"
    BackupErrorCode.IMPORT_NOT_CONFIRMED -> "导入尚未确认"
    BackupErrorCode.IMPORT_PREVIEW_STALE -> "当前数据已变化，请刷新冲突预览后再次确认"
    BackupErrorCode.IMPORT_FAILED -> "导入失败，新数据已撤销并恢复原状态"
    BackupErrorCode.IMPORT_RECOVERY_REQUIRED -> "导入未完成，恢复记录已保留；请重启应用继续恢复"
    BackupErrorCode.CRYPTO_FAILURE -> "加密处理失败"
}
