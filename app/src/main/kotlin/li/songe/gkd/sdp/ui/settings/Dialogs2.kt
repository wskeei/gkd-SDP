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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import li.songe.gkd.sdp.MainActivity
import li.songe.gkd.sdp.backup.*
import li.songe.gkd.sdp.store.storeFlow
import li.songe.gkd.sdp.ui.component.TextListDialog
import li.songe.gkd.sdp.ui.component.TextSwitch
import li.songe.gkd.sdp.util.BackupUtils
import li.songe.gkd.sdp.util.UriUtils
import li.songe.gkd.sdp.util.toast
import androidx.compose.ui.res.stringResource
import li.songe.gkd.sdp.R
import li.songe.gkd.sdp.app

@Composable
internal fun SettingsBackupDialogs(
    context: MainActivity,
    vm: HomeVm,
    backupScope: CoroutineScope,
    backupWorkflow: MutableState<BackupWorkflowState?>,
) {
    SettingsBackupChooser(context, vm, backupScope, backupWorkflow)
    backupWorkflow.value?.let { workflow ->
        BackupWorkflowDialog(
            context = context,
            backupScope = backupScope,
            workflowState = backupWorkflow,
            workflow = workflow,
        )
    }
}

@Composable
private fun SettingsBackupChooser(
    context: MainActivity,
    vm: HomeVm,
    backupScope: CoroutineScope,
    backupWorkflow: MutableState<BackupWorkflowState?>,
) {
    if (!vm.showBackupDlgFlow.collectAsStateWithLifecycle().value) return
    TextListDialog(
        onDismiss = { vm.showBackupDlgFlow.value = false },
        textList = listOf(
            "导入备份（v2 / 旧版）" to {
                vm.showBackupDlgFlow.value = false
                backupScope.launch {
                    context.pickFile("*/*")?.let { BackupUtils.pendingImportUriFlow.value = it }
                }
            },
            "导出备份" to {
                vm.showBackupDlgFlow.value = false
                backupWorkflow.value = BackupWorkflowState(stage = BackupWorkflowStage.EXPORT_CATEGORIES)
            },
        ),
    )
}

@Composable
private fun BackupWorkflowDialog(
    context: MainActivity,
    backupScope: CoroutineScope,
    workflowState: MutableState<BackupWorkflowState?>,
    workflow: BackupWorkflowState,
) {
    val dismiss = {
        if (!workflow.busy) {
            workflowState.value = null
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
        title = { Text(workflowTitle(workflow.stage)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BackupWorkflowStageContent(workflow, workflowState)
                if (workflow.busy) Text(stringResource(R.string.s_1ac3e91414))
                workflow.errorText?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            BackupWorkflowConfirmButton(
                enabled = confirmEnabled,
                stage = workflow.stage,
                onClick = { launchBackupWorkflowAction(context, backupScope, workflowState, workflow) },
            )
        },
        dismissButton = { TextButton(enabled = !workflow.busy, onClick = dismiss) { Text(stringResource(R.string.s_4d0b4688c7)) } },
    )
}

private fun workflowTitle(stage: BackupWorkflowStage): String = when (stage) {
    BackupWorkflowStage.EXPORT_CATEGORIES -> "选择备份内容"
    BackupWorkflowStage.EXPORT_SUMMARY -> "确认导出清单"
    BackupWorkflowStage.EXPORT_PASSWORD -> "设置备份密码"
    BackupWorkflowStage.IMPORT_PASSWORD -> "输入备份密码"
    BackupWorkflowStage.IMPORT_PREVIEW -> "确认导入影响"
}

private fun workflowConfirmText(stage: BackupWorkflowStage): String = when (stage) {
    BackupWorkflowStage.EXPORT_CATEGORIES -> "查看清单"
    BackupWorkflowStage.EXPORT_SUMMARY -> "设置密码"
    BackupWorkflowStage.EXPORT_PASSWORD -> "选择位置并导出"
    BackupWorkflowStage.IMPORT_PASSWORD -> "解密并预览"
    BackupWorkflowStage.IMPORT_PREVIEW -> "确认替换并导入"
}

@Composable
private fun BackupWorkflowStageContent(
    workflow: BackupWorkflowState,
    workflowState: MutableState<BackupWorkflowState?>,
) {
    when (workflow.stage) {
        BackupWorkflowStage.EXPORT_CATEGORIES -> {
            Text(stringResource(R.string.s_5bf9577af0))
            BackupCatalog.categories.forEach { category ->
                TextSwitch(
                    title = backupCategoryTitle(category.id),
                    subtitle = if (category.sensitive) app.getString(R.string.s_e317d798a8) else backupCategorySubtitle(category.id),
                    checked = category.id in workflow.selectedCategoryIds,
                    onCheckedChange = { checked ->
                        workflowState.value = workflow.copy(
                            selectedCategoryIds = if (checked) workflow.selectedCategoryIds + category.id else workflow.selectedCategoryIds - category.id,
                            errorText = null,
                        )
                    },
                )
            }
        }
        BackupWorkflowStage.EXPORT_PASSWORD,
        BackupWorkflowStage.IMPORT_PASSWORD -> {
            Text(
                if (workflow.stage == BackupWorkflowStage.IMPORT_PASSWORD) {
                    "加密 v2 密码至少包含 12 个 Unicode 字符；导入旧版未加密备份时留空。密码不会持久化。"
                } else {
                    "密码至少包含 12 个 Unicode 字符。密码不写入日志、备份或持久化设置。"
                },
            )
            SettingsPasswordField(
                value = workflow.password,
                label = stringResource(R.string.s_54ed97d204),
                onValueChange = { workflowState.value = workflow.copy(password = it, errorText = null) },
            )
            if (workflow.stage == BackupWorkflowStage.EXPORT_PASSWORD) {
                SettingsPasswordField(
                    value = workflow.repeatedPassword,
                    label = stringResource(R.string.s_f27a153008),
                    onValueChange = { workflowState.value = workflow.copy(repeatedPassword = it, errorText = null) },
                )
                if (workflow.repeatedPassword.isNotEmpty() && workflow.password != workflow.repeatedPassword) {
                    Text(stringResource(R.string.s_3e2b222d98), color = MaterialTheme.colorScheme.error)
                }
            }
        }
        BackupWorkflowStage.EXPORT_SUMMARY -> {
            Text(stringResource(R.string.s_e8c1720f03))
            workflow.selectedCategoryIds.forEach { Text(app.getString(R.string.s_28b124759e, backupCategoryTitle(it))) }
            Text(stringResource(R.string.s_58bad7a807))
            Text(stringResource(R.string.s_d2e08cab80))
            Text(stringResource(R.string.s_6ed70747fd))
        }
        BackupWorkflowStage.IMPORT_PREVIEW -> {
            val prepared = requireNotNull(workflow.preparedImport)
            Text(
                when (prepared.sourceFormat) {
                    BackupSourceFormat.ENCRYPTED_V2 -> stringResource(R.string.s_82275eff6f, prepared.payload.manifest.formatVersion)
                    BackupSourceFormat.LEGACY_V1 -> stringResource(R.string.s_c9d242c96c)
                },
            )
            Text(stringResource(R.string.s_f3c459c9c3))
            prepared.payload.manifest.categoryIds.forEach { Text(app.getString(R.string.s_28b124759e, backupCategoryTitle(it))) }
            Text(stringResource(R.string.s_9eba7fa3e2))
            Text(stringResource(R.string.s_b3af13eb8f))
            prepared.conflicts.forEach { conflict ->
                Text(app.getString(R.string.s_a237a78c90, backupCategoryTitle(conflict.categoryId), conflict.added, conflict.overwritten, conflict.deleted))
            }
        }
    }
}

@Composable
private fun BackupWorkflowConfirmButton(enabled: Boolean, stage: BackupWorkflowStage, onClick: () -> Unit) {
    TextButton(enabled = enabled, onClick = onClick) { Text(workflowConfirmText(stage)) }
}

private fun launchBackupWorkflowAction(
    context: MainActivity,
    backupScope: CoroutineScope,
    workflowState: MutableState<BackupWorkflowState?>,
    workflow: BackupWorkflowState,
) {
    when (workflow.stage) {
        BackupWorkflowStage.EXPORT_CATEGORIES -> workflowState.value = workflow.copy(stage = BackupWorkflowStage.EXPORT_SUMMARY)
        BackupWorkflowStage.EXPORT_SUMMARY -> workflowState.value = workflow.copy(stage = BackupWorkflowStage.EXPORT_PASSWORD)
        BackupWorkflowStage.EXPORT_PASSWORD -> {
            val selectedCategoryIds = workflow.selectedCategoryIds
            val password = workflow.password.toCharArray()
            workflowState.value = BackupWorkflowState(stage = BackupWorkflowStage.EXPORT_PASSWORD, selectedCategoryIds = selectedCategoryIds, busy = true)
            backupScope.launch {
                try {
                    val targetUri = context.createFile(
                        contentType = "application/octet-stream",
                        filename = "gkd-sdp-backup-v2-${System.currentTimeMillis()}.gkdbak",
                    )
                    if (targetUri == null) {
                        workflowState.value = BackupWorkflowState(stage = BackupWorkflowStage.EXPORT_PASSWORD, selectedCategoryIds = selectedCategoryIds, errorText = app.getString(R.string.s_6349c9a19f))
                        return@launch
                    }
                    when (val result = withContext(Dispatchers.IO) { BackupUtils.exportBackUpData(selectedCategoryIds, password) }) {
                        is BackupResult.Success -> {
                            val file = result.value.file
                            val copied = runCatching { withContext(Dispatchers.IO) { UriUtils.copyFileToUri(file, targetUri) } }
                            file.delete()
                            if (copied.isSuccess) {
                                workflowState.value = null
                                toast(app.getString(R.string.s_fad8721370))
                            } else {
                                workflowState.value = BackupWorkflowState(stage = BackupWorkflowStage.EXPORT_PASSWORD, selectedCategoryIds = selectedCategoryIds, errorText = app.getString(R.string.s_13df62ffcf))
                            }
                        }
                        is BackupResult.Failure -> {
                            runCatching { context.contentResolver.delete(targetUri, null, null) }
                            workflowState.value = BackupWorkflowState(stage = BackupWorkflowStage.EXPORT_PASSWORD, selectedCategoryIds = selectedCategoryIds, errorText = backupErrorText(result.code))
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
            workflowState.value = BackupWorkflowState(stage = BackupWorkflowStage.IMPORT_PASSWORD, sourceUri = sourceUri, busy = true)
            backupScope.launch {
                val result = try {
                    withContext(Dispatchers.IO) { BackupUtils.prepareImport(sourceUri, password) }
                } finally {
                    password.fill('\u0000')
                }
                when (result) {
                    is BackupResult.Success -> {
                        BackupUtils.pendingImportUriFlow.value = null
                        workflowState.value = BackupWorkflowState(stage = BackupWorkflowStage.IMPORT_PREVIEW, sourceUri = sourceUri, preparedImport = result.value)
                    }
                    is BackupResult.Failure -> {
                        workflowState.value = BackupWorkflowState(stage = BackupWorkflowStage.IMPORT_PASSWORD, sourceUri = sourceUri, errorText = backupErrorText(result.code))
                    }
                }
            }
        }
        BackupWorkflowStage.IMPORT_PREVIEW -> {
            val preparedImport = requireNotNull(workflow.preparedImport)
            val sourceUri = workflow.sourceUri
            workflowState.value = workflow.copy(busy = true, errorText = null)
            backupScope.launch {
                val refreshed = withContext(Dispatchers.IO) { BackupUtils.refreshImportPreview(preparedImport) }
                if (refreshed is BackupResult.Failure) {
                    workflowState.value = workflow.copy(busy = false, errorText = backupErrorText(refreshed.code))
                    return@launch
                }
                val refreshedImport = (refreshed as BackupResult.Success).value
                if (refreshedImport.previewStateHash != preparedImport.previewStateHash || refreshedImport.conflicts != preparedImport.conflicts) {
                    workflowState.value = BackupWorkflowState(stage = BackupWorkflowStage.IMPORT_PREVIEW, sourceUri = sourceUri, preparedImport = refreshedImport, errorText = app.getString(R.string.s_53c71581fe))
                    return@launch
                }
                when (val result = withContext(Dispatchers.IO) { BackupUtils.applyImport(refreshedImport, confirmed = true) }) {
                    is BackupResult.Success -> {
                        workflowState.value = null
                        BackupUtils.pendingImportUriFlow.value = null
                        toast(app.getString(R.string.s_74010fe072))
                    }
                    is BackupResult.Failure -> {
                        workflowState.value = BackupWorkflowState(stage = BackupWorkflowStage.IMPORT_PREVIEW, sourceUri = sourceUri, preparedImport = refreshedImport, errorText = backupErrorText(result.code))
                    }
                }
            }
        }
    }
}
