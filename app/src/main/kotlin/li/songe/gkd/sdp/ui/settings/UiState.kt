@file:JvmName("SettingsUiState0")

package li.songe.gkd.sdp.ui.home
import androidx.compose.runtime.Immutable

import android.net.Uri
import androidx.navigation3.runtime.NavKey
import li.songe.gkd.sdp.R
import li.songe.gkd.sdp.backup.PreparedBackupImport
import li.songe.gkd.sdp.backup.BackupErrorCode
import li.songe.gkd.sdp.settings.SettingsEntry
import li.songe.gkd.sdp.store.SettingsStore
import li.songe.gkd.sdp.util.BackupUtils
internal enum class BackupWorkflowStage {
    EXPORT_CATEGORIES,
    EXPORT_SUMMARY,
    EXPORT_PASSWORD,
    IMPORT_PASSWORD,
    IMPORT_PREVIEW,
}

@Immutable
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

@Immutable
internal data class SettingsUiState(
    val backup: BackupWorkflowState = BackupWorkflowState(BackupWorkflowStage.EXPORT_CATEGORIES),
)

@Immutable
internal data class SettingsRenderState(
    val store: SettingsStore,
    val backupWorkflow: BackupWorkflowState?,
    val showToastInputDlg: Boolean,
    val showNotifTextInputDlg: Boolean,
    val showToastSettingsDlg: Boolean,
    val showA11yBlockDlg: Boolean,
    val showBackupDlg: Boolean,
    val subsStatus: String,
    val trackServiceRunning: Boolean,
    val shizukuOk: Boolean,
    val ignoreBatteryOptimizations: Boolean,
    val statusRunning: Boolean,
    val showA11ySection: Boolean,
    val activeLockCount: Int,
)

internal data class SettingsCallbacks(
    val updateStore: (SettingsStore) -> Unit,
    val navigateRoute: (NavKey) -> Unit,
    val showToastInput: () -> Unit,
    val dismissToastInput: () -> Unit,
    val confirmToast: (String) -> Unit,
    val showToastHelp: () -> Unit,
    val showNotifInput: () -> Unit,
    val dismissNotifInput: () -> Unit,
    val confirmNotif: (String, String) -> Unit,
    val showNotifHelp: () -> Unit,
    val dismissA11yBlock: () -> Unit,
    val confirmA11yBlock: () -> Unit,
    val guardShizuku: () -> Unit,
    val requestStatusService: () -> Unit,
    val openBatterySettings: () -> Unit,
    val openAppDetails: () -> Unit,
    val switchRecentApps: () -> Unit,
    val toggleToastSettings: () -> Unit,
    val showViewRestrictions: () -> Unit,
    val toggleTrackService: (Boolean) -> Unit,
    val toggleExcludeFromRecents: (Boolean) -> Unit,
    val enableBlockA11y: (Boolean) -> Unit,
    val navigateBlockA11y: () -> Unit,
    val showBackup: () -> Unit,
    val dismissBackup: () -> Unit,
    val importBackup: () -> Unit,
    val exportBackup: () -> Unit,
    val updateBackupWorkflow: (BackupWorkflowState?) -> Unit,
)

internal sealed interface SettingsAction {
    data class UpdateBackupCategory(val categoryId: String, val selected: Boolean) : SettingsAction
    data object ResetBackupWorkflow : SettingsAction
    data object ClearBackupError : SettingsAction
}

internal fun backupCategoryTitleRes(categoryId: String): Int = when (categoryId) {
    "settings" -> R.string.backup_category_settings_title
    "subscriptions" -> R.string.backup_category_subscriptions_title
    "self_control_config" -> R.string.backup_category_self_control_config_title
    "self_control_history" -> R.string.backup_category_self_control_history_title
    "upstream_history" -> R.string.backup_category_upstream_history_title
    "sensitive_optional" -> R.string.backup_category_sensitive_optional_title
    else -> R.string.backup_category_unknown_title
}

internal fun backupCategorySubtitleRes(categoryId: String): Int? = when (categoryId) {
    "settings" -> R.string.backup_category_settings_subtitle
    "subscriptions" -> R.string.backup_category_subscriptions_subtitle
    "self_control_config" -> R.string.backup_category_self_control_config_subtitle
    "self_control_history" -> R.string.backup_category_self_control_history_subtitle
    "upstream_history" -> R.string.backup_category_upstream_history_subtitle
    else -> null
}

internal fun backupErrorTextRes(code: BackupErrorCode): Int = when (code) {
    BackupErrorCode.WEAK_PASSWORD -> R.string.backup_error_weak_password
    BackupErrorCode.AUTHENTICATION_FAILED -> R.string.backup_error_authentication_failed
    BackupErrorCode.INVALID_MAGIC -> R.string.backup_error_invalid_magic
    BackupErrorCode.TRUNCATED -> R.string.backup_error_truncated
    BackupErrorCode.UNSUPPORTED_VERSION,
    BackupErrorCode.UNSUPPORTED_KDF -> R.string.backup_error_unsupported
    BackupErrorCode.SCHEMA_MISMATCH -> R.string.backup_error_schema_mismatch
    BackupErrorCode.REFERENCE_MISMATCH -> R.string.backup_error_reference_mismatch
    BackupErrorCode.NONCE_REUSE,
    BackupErrorCode.MALFORMED_HEADER,
    BackupErrorCode.INVALID_PAYLOAD -> R.string.backup_error_invalid_payload
    BackupErrorCode.IMPORT_NOT_CONFIRMED -> R.string.backup_error_import_not_confirmed
    BackupErrorCode.IMPORT_PREVIEW_STALE -> R.string.backup_error_import_preview_stale
    BackupErrorCode.IMPORT_FAILED -> R.string.backup_error_import_failed
    BackupErrorCode.IMPORT_RECOVERY_REQUIRED -> R.string.backup_error_import_recovery_required
    BackupErrorCode.CRYPTO_FAILURE -> R.string.backup_error_crypto_failure
}
