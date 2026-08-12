@file:JvmName("SettingsDialogs2")

package li.songe.gkd.sdp.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import li.songe.gkd.sdp.MainActivity
import li.songe.gkd.sdp.app
import li.songe.gkd.sdp.backup.*
import li.songe.gkd.sdp.ui.component.TextListDialog
import li.songe.gkd.sdp.ui.component.TextSwitch
import li.songe.gkd.sdp.util.BackupUtils
import li.songe.gkd.sdp.util.UriUtils
import li.songe.gkd.sdp.util.toast
import androidx.compose.ui.res.stringResource
import li.songe.gkd.sdp.R

@Composable
internal fun SettingsBackupDialogs(
    context: MainActivity?,
    backupScope: CoroutineScope,
    workflow: BackupWorkflowState?,
    onUpdateWorkflow: (BackupWorkflowState?) -> Unit,
    showBackupDlg: Boolean,
    onDismissBackup: () -> Unit,
    onImportBackup: () -> Unit,
    onExportBackup: () -> Unit,
) {
    SettingsBackupChooser(
        showBackupDlg = showBackupDlg,
        onDismissBackup = onDismissBackup,
        onImportBackup = onImportBackup,
        onExportBackup = onExportBackup,
    )
    workflow?.let {
        BackupWorkflowDialog(
            context = context,
            backupScope = backupScope,
            workflow = it,
            onUpdateWorkflow = onUpdateWorkflow,
        )
    }
}

@Composable
private fun SettingsBackupChooser(
    showBackupDlg: Boolean,
    onDismissBackup: () -> Unit,
    onImportBackup: () -> Unit,
    onExportBackup: () -> Unit,
) {
    if (!showBackupDlg) return
    TextListDialog(
        onDismiss = onDismissBackup,
        textList = listOf(
            stringResource(R.string.backup_import_entry) to {
                onImportBackup()
            },
            stringResource(R.string.backup_export_entry) to {
                onExportBackup()
            },
        ),
    )
}

@Composable
private fun BackupWorkflowDialog(
    context: MainActivity?,
    backupScope: CoroutineScope,
    workflow: BackupWorkflowState,
    onUpdateWorkflow: (BackupWorkflowState?) -> Unit,
) {
    val dismiss = {
        if (!workflow.busy) {
            onUpdateWorkflow(null)
            BackupUtils.pendingImportUriFlow.value = null
        }
    }
    val passwordValid = settingsPasswordIsValid(workflow.password)
    val confirmEnabled = !workflow.busy && when (workflow.stage) {
        BackupWorkflowStage.EXPORT_CATEGORIES -> workflow.selectedCategoryIds.isNotEmpty()
        BackupWorkflowStage.EXPORT_SUMMARY -> true
        BackupWorkflowStage.EXPORT_PASSWORD -> passwordValid && workflow.password == workflow.repeatedPassword
        BackupWorkflowStage.IMPORT_PASSWORD -> (passwordValid || workflow.password.isEmpty()) && workflow.sourceUri != null
        BackupWorkflowStage.IMPORT_PREVIEW -> workflow.preparedImport != null
    }
    AlertDialog(
        properties = DialogProperties(dismissOnBackPress = !workflow.busy, dismissOnClickOutside = false),
        onDismissRequest = dismiss,
        title = { Text(stringResource(workflowTitleRes(workflow.stage))) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BackupWorkflowStageContent(workflow, onUpdateWorkflow)
                if (workflow.busy) Text(stringResource(R.string.s_1ac3e91414))
                workflow.errorText?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            BackupWorkflowConfirmButton(
                enabled = confirmEnabled,
                stage = workflow.stage,
                onClick = {
                    launchBackupWorkflowAction(
                        context = context,
                        backupScope = backupScope,
                        workflow = workflow,
                        onUpdateWorkflow = onUpdateWorkflow,
                    )
                },
            )
        },
        dismissButton = { TextButton(enabled = !workflow.busy, onClick = dismiss) { Text(stringResource(R.string.s_4d0b4688c7)) } },
    )
}

private fun workflowTitleRes(stage: BackupWorkflowStage): Int = when (stage) {
    BackupWorkflowStage.EXPORT_CATEGORIES -> R.string.backup_workflow_export_categories
    BackupWorkflowStage.EXPORT_SUMMARY -> R.string.backup_workflow_export_summary
    BackupWorkflowStage.EXPORT_PASSWORD -> R.string.backup_workflow_export_password
    BackupWorkflowStage.IMPORT_PASSWORD -> R.string.backup_workflow_import_password
    BackupWorkflowStage.IMPORT_PREVIEW -> R.string.backup_workflow_import_preview
}

private fun workflowConfirmTextRes(stage: BackupWorkflowStage): Int = when (stage) {
    BackupWorkflowStage.EXPORT_CATEGORIES -> R.string.backup_workflow_confirm_view_summary
    BackupWorkflowStage.EXPORT_SUMMARY -> R.string.backup_workflow_confirm_set_password
    BackupWorkflowStage.EXPORT_PASSWORD -> R.string.backup_workflow_confirm_choose_location
    BackupWorkflowStage.IMPORT_PASSWORD -> R.string.backup_workflow_confirm_decrypt_preview
    BackupWorkflowStage.IMPORT_PREVIEW -> R.string.backup_workflow_confirm_replace_import
}

@Composable
private fun BackupWorkflowStageContent(
    workflow: BackupWorkflowState,
    onUpdateWorkflow: (BackupWorkflowState?) -> Unit,
) {
    when (workflow.stage) {
        BackupWorkflowStage.EXPORT_CATEGORIES -> {
            Text(stringResource(R.string.s_5bf9577af0))
            BackupCatalog.categories.forEach { category ->
                TextSwitch(
                    title = stringResource(backupCategoryTitleRes(category.id)),
                    subtitle = if (category.sensitive) {
                        li.songe.gkd.sdp.app.getString(R.string.s_e317d798a8)
                    } else {
                        backupCategorySubtitleRes(category.id)?.let { stringResource(it) }
                    },
                    checked = category.id in workflow.selectedCategoryIds,
                    onCheckedChange = { checked ->
                        onUpdateWorkflow(
                            workflow.copy(
                            selectedCategoryIds = if (checked) workflow.selectedCategoryIds + category.id else workflow.selectedCategoryIds - category.id,
                            errorText = null,
                            ),
                        )
                    },
                )
            }
        }
        BackupWorkflowStage.EXPORT_PASSWORD,
        BackupWorkflowStage.IMPORT_PASSWORD -> {
            Text(
                if (workflow.stage == BackupWorkflowStage.IMPORT_PASSWORD) {
                    stringResource(R.string.backup_password_import_explanation)
                } else {
                    stringResource(R.string.backup_password_export_explanation)
                },
            )
            SettingsPasswordField(
                value = workflow.password,
                label = stringResource(R.string.backup_password_label),
                onValueChange = { onUpdateWorkflow(workflow.copy(password = it, errorText = null)) },
            )
            if (workflow.stage == BackupWorkflowStage.EXPORT_PASSWORD) {
                SettingsPasswordField(
                    value = workflow.repeatedPassword,
                    label = stringResource(R.string.backup_password_repeat_label),
                    onValueChange = { onUpdateWorkflow(workflow.copy(repeatedPassword = it, errorText = null)) },
                )
                if (workflow.repeatedPassword.isNotEmpty() && workflow.password != workflow.repeatedPassword) {
                    Text(stringResource(R.string.s_3e2b222d98), color = MaterialTheme.colorScheme.error)
                }
            }
        }
        BackupWorkflowStage.EXPORT_SUMMARY -> {
            Text(stringResource(R.string.s_e8c1720f03))
            workflow.selectedCategoryIds.forEach {
                Text(
                    li.songe.gkd.sdp.app.getString(
                        R.string.s_28b124759e,
                        li.songe.gkd.sdp.app.getString(backupCategoryTitleRes(it)),
                    ),
                )
            }
            Text(stringResource(R.string.s_58bad7a807))
            Text(stringResource(R.string.s_d2e08cab80))
            Text(stringResource(R.string.s_6ed70747fd))
        }
        BackupWorkflowStage.IMPORT_PREVIEW -> {
            val prepared = requireNotNull(workflow.preparedImport)
            Text(
                when (prepared.sourceFormat) {
                    BackupSourceFormat.ENCRYPTED_V2 -> stringResource(R.string.s_82275eff6f, (prepared.payload.manifest.formatVersion).toString())
                    BackupSourceFormat.LEGACY_V1 -> stringResource(R.string.s_c9d242c96c)
                },
            )
            Text(stringResource(R.string.s_f3c459c9c3))
            prepared.payload.manifest.categoryIds.forEach {
                Text(
                    li.songe.gkd.sdp.app.getString(
                        R.string.s_28b124759e,
                        li.songe.gkd.sdp.app.getString(backupCategoryTitleRes(it)),
                    ),
                )
            }
            Text(stringResource(R.string.s_9eba7fa3e2))
            Text(stringResource(R.string.s_b3af13eb8f))
            prepared.conflicts.forEach { conflict ->
                Text(
                    li.songe.gkd.sdp.app.getString(
                        R.string.s_a237a78c90,
                        li.songe.gkd.sdp.app.getString(backupCategoryTitleRes(conflict.categoryId)),
                        conflict.added.toString(),
                        conflict.overwritten.toString(),
                        conflict.deleted.toString(),
                    ),
                )
            }
        }
    }
}

@Composable
private fun BackupWorkflowConfirmButton(enabled: Boolean, stage: BackupWorkflowStage, onClick: () -> Unit) {
    TextButton(enabled = enabled, onClick = onClick) {
        Text(stringResource(workflowConfirmTextRes(stage)))
    }
}

private fun launchBackupWorkflowAction(
    context: MainActivity?,
    backupScope: CoroutineScope,
    workflow: BackupWorkflowState,
    onUpdateWorkflow: (BackupWorkflowState?) -> Unit,
) {
    when (workflow.stage) {
        BackupWorkflowStage.EXPORT_CATEGORIES ->
            onUpdateWorkflow(workflow.copy(stage = BackupWorkflowStage.EXPORT_SUMMARY))
        BackupWorkflowStage.EXPORT_SUMMARY ->
            onUpdateWorkflow(workflow.copy(stage = BackupWorkflowStage.EXPORT_PASSWORD))
        BackupWorkflowStage.EXPORT_PASSWORD -> {
            val selectedCategoryIds = workflow.selectedCategoryIds
            val password = workflow.password.toCharArray()
            onUpdateWorkflow(
                BackupWorkflowState(
                    stage = BackupWorkflowStage.EXPORT_PASSWORD,
                    selectedCategoryIds = selectedCategoryIds,
                    busy = true,
                ),
            )
            backupScope.launch {
                try {
                    if (context == null) {
                        onUpdateWorkflow(
                            BackupWorkflowState(
                            stage = BackupWorkflowStage.EXPORT_PASSWORD,
                            selectedCategoryIds = selectedCategoryIds,
                            errorText = app.getString(R.string.backup_save_location_unavailable),
                            ),
                        )
                        return@launch
                    }
                    val targetUri = context.createFile(
                        contentType = "application/octet-stream",
                        filename = "gkd-sdp-backup-v2-${System.currentTimeMillis()}.gkdbak",
                    )
                    if (targetUri == null) {
                        onUpdateWorkflow(
                            BackupWorkflowState(
                            stage = BackupWorkflowStage.EXPORT_PASSWORD,
                            selectedCategoryIds = selectedCategoryIds,
                            errorText = app.getString(R.string.backup_no_save_location),
                            ),
                        )
                        return@launch
                    }
                    when (val result = withContext(Dispatchers.IO) { BackupUtils.exportBackUpData(selectedCategoryIds, password) }) {
                        is BackupResult.Success -> {
                            val file = result.value.file
                            val copied = runCatching { withContext(Dispatchers.IO) { UriUtils.copyFileToUri(file, targetUri) } }
                            file.delete()
                            if (copied.isSuccess) {
                                onUpdateWorkflow(null)
                                toast(li.songe.gkd.sdp.app.getString(R.string.s_fad8721370))
                            } else {
                                onUpdateWorkflow(
                                    BackupWorkflowState(
                                    stage = BackupWorkflowStage.EXPORT_PASSWORD,
                                    selectedCategoryIds = selectedCategoryIds,
                                    errorText = app.getString(R.string.backup_write_failed),
                                    ),
                                )
                            }
                        }
                        is BackupResult.Failure -> {
                            runCatching { context.contentResolver.delete(targetUri, null, null) }
                            onUpdateWorkflow(
                                BackupWorkflowState(
                                stage = BackupWorkflowStage.EXPORT_PASSWORD,
                                selectedCategoryIds = selectedCategoryIds,
                                errorText = app.getString(backupErrorTextRes(result.code)),
                                ),
                            )
                        }
                    }
                } finally {
                    password.fill('\u0000')
                }
            }
        }
        BackupWorkflowStage.IMPORT_PASSWORD -> {
            val sourceUri = requireNotNull(workflow.sourceUri)
            val password = workflow.password.toCharArray()
            onUpdateWorkflow(
                BackupWorkflowState(
                    stage = BackupWorkflowStage.IMPORT_PASSWORD,
                    sourceUri = sourceUri,
                    busy = true,
                ),
            )
            backupScope.launch {
                val result = try {
                    withContext(Dispatchers.IO) { BackupUtils.prepareImport(sourceUri, password) }
                } finally {
                    password.fill('\u0000')
                }
                when (result) {
                    is BackupResult.Success -> {
                        BackupUtils.pendingImportUriFlow.value = null
                        onUpdateWorkflow(
                            BackupWorkflowState(
                                stage = BackupWorkflowStage.IMPORT_PREVIEW,
                                sourceUri = sourceUri,
                                preparedImport = result.value,
                            ),
                        )
                    }
                    is BackupResult.Failure -> {
                        onUpdateWorkflow(
                            BackupWorkflowState(
                            stage = BackupWorkflowStage.IMPORT_PASSWORD,
                            sourceUri = sourceUri,
                            errorText = app.getString(backupErrorTextRes(result.code)),
                            ),
                        )
                    }
                }
            }
        }
        BackupWorkflowStage.IMPORT_PREVIEW -> {
            val preparedImport = requireNotNull(workflow.preparedImport)
            val sourceUri = workflow.sourceUri
            onUpdateWorkflow(workflow.copy(busy = true, errorText = null))
            backupScope.launch {
                val refreshed = withContext(Dispatchers.IO) { BackupUtils.refreshImportPreview(preparedImport) }
                if (refreshed is BackupResult.Failure) {
                    onUpdateWorkflow(
                        workflow.copy(
                        busy = false,
                        errorText = app.getString(backupErrorTextRes(refreshed.code)),
                        ),
                    )
                    return@launch
                }
                val refreshedImport = (refreshed as BackupResult.Success).value
                if (refreshedImport.previewStateHash != preparedImport.previewStateHash || refreshedImport.conflicts != preparedImport.conflicts) {
                    onUpdateWorkflow(
                        BackupWorkflowState(
                        stage = BackupWorkflowStage.IMPORT_PREVIEW,
                        sourceUri = sourceUri,
                        preparedImport = refreshedImport,
                        errorText = app.getString(R.string.backup_preview_changed),
                        ),
                    )
                    return@launch
                }
                when (val result = withContext(Dispatchers.IO) { BackupUtils.applyImport(refreshedImport, confirmed = true) }) {
                    is BackupResult.Success -> {
                        onUpdateWorkflow(null)
                        BackupUtils.pendingImportUriFlow.value = null
                        toast(li.songe.gkd.sdp.app.getString(R.string.s_74010fe072))
                    }
                    is BackupResult.Failure -> {
                        onUpdateWorkflow(
                            BackupWorkflowState(
                            stage = BackupWorkflowStage.IMPORT_PREVIEW,
                            sourceUri = sourceUri,
                            preparedImport = refreshedImport,
                            errorText = app.getString(backupErrorTextRes(result.code)),
                            ),
                        )
                    }
                }
            }
        }
    }
}
