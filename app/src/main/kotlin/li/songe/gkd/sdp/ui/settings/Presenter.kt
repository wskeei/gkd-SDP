@file:JvmName("SettingsPresenter")

package li.songe.gkd.sdp.ui.home

/** Form policy shared by the backup route and its editor. */
internal fun settingsPasswordIsValid(password: String): Boolean =
    password.codePointCount(0, password.length) >= 12

internal fun SettingsUiState.reduce(action: SettingsAction): SettingsUiState = when (action) {
    is SettingsAction.UpdateBackupCategory -> copy(
        backup = backup.copy(
            selectedCategoryIds = if (action.selected) {
                backup.selectedCategoryIds + action.categoryId
            } else {
                backup.selectedCategoryIds - action.categoryId
            },
        ),
    )
    SettingsAction.ResetBackupWorkflow -> SettingsUiState()
    SettingsAction.ClearBackupError -> copy(backup = backup.copy(errorText = null))
}

internal fun SettingsUiState.withError(text: String?): SettingsUiState =
    copy(backup = backup.copy(errorText = text))
