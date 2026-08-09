@file:JvmName("SettingsSections")

package li.songe.gkd.sdp.ui.home

import android.net.Uri
import android.view.KeyEvent
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import li.songe.gkd.sdp.MainActivity
import li.songe.gkd.sdp.R
import li.songe.gkd.sdp.backup.BackupCatalog
import li.songe.gkd.sdp.backup.BackupErrorCode
import li.songe.gkd.sdp.backup.BackupResult
import li.songe.gkd.sdp.backup.PreparedBackupImport
import li.songe.gkd.sdp.backup.BackupSourceFormat
import li.songe.gkd.sdp.permission.canDrawOverlaysState
import li.songe.gkd.sdp.permission.foregroundServiceSpecialUseState
import li.songe.gkd.sdp.permission.ignoreBatteryOptimizationsState
import li.songe.gkd.sdp.permission.notificationState
import li.songe.gkd.sdp.permission.requiredPermission
import li.songe.gkd.sdp.service.StatusService
import li.songe.gkd.sdp.service.TrackService
import li.songe.gkd.sdp.service.fixRestartAutomatorService
import li.songe.gkd.sdp.shizuku.shizukuContextFlow
import li.songe.gkd.sdp.store.storeFlow
import li.songe.gkd.sdp.ui.AboutRoute
import li.songe.gkd.sdp.ui.AdvancedPageRoute
import li.songe.gkd.sdp.ui.BlockA11yAppListRoute
import li.songe.gkd.sdp.ui.FocusLockRoute
import li.songe.gkd.sdp.ui.component.CustomOutlinedTextField
import li.songe.gkd.sdp.ui.component.FullscreenDialog
import li.songe.gkd.sdp.ui.component.PerfCustomIconButton
import li.songe.gkd.sdp.ui.component.PerfIcon
import li.songe.gkd.sdp.ui.component.PerfIconButton
import li.songe.gkd.sdp.ui.component.PerfTopAppBar
import li.songe.gkd.sdp.ui.component.SettingItem
import li.songe.gkd.sdp.ui.component.TextListDialog
import li.songe.gkd.sdp.ui.component.TextMenu
import li.songe.gkd.sdp.ui.component.TextSwitch
import li.songe.gkd.sdp.ui.component.autoFocus
import li.songe.gkd.sdp.ui.component.updateDialogOptions
import li.songe.gkd.sdp.ui.component.useScrollBehaviorState
import li.songe.gkd.sdp.ui.component.waitResult
import li.songe.gkd.sdp.ui.share.LocalMainViewModel
import li.songe.gkd.sdp.ui.share.asMutableState
import li.songe.gkd.sdp.ui.style.EmptyHeight
import li.songe.gkd.sdp.ui.style.iconTextSize
import li.songe.gkd.sdp.ui.style.itemHorizontalPadding
import li.songe.gkd.sdp.ui.style.titleItemPadding
import li.songe.gkd.sdp.util.AndroidTarget
import li.songe.gkd.sdp.util.BackupUtils
import li.songe.gkd.sdp.util.DarkThemeOption
import li.songe.gkd.sdp.util.DisplayDensityOption
import li.songe.gkd.sdp.util.FocusLockUtils
import li.songe.gkd.sdp.util.LanguageOption
import li.songe.gkd.sdp.util.findOption
import li.songe.gkd.sdp.util.launchAsFn
import li.songe.gkd.sdp.util.mapState
import li.songe.gkd.sdp.util.openAppDetailsSettings
import li.songe.gkd.sdp.util.ruleSummaryFlow
import li.songe.gkd.sdp.util.throttle
import li.songe.gkd.sdp.util.toast
import li.songe.gkd.sdp.util.UriUtils

private enum class BackupWorkflowStage {
    EXPORT_CATEGORIES,
    EXPORT_SUMMARY,
    EXPORT_PASSWORD,
    IMPORT_PASSWORD,
    IMPORT_PREVIEW,
}

private data class BackupWorkflowState(
    val stage: BackupWorkflowStage,
    val selectedCategoryIds: Set<String> = BackupUtils.defaultCategoryIds,
    val sourceUri: Uri? = null,
    val password: String = "",
    val repeatedPassword: String = "",
    val preparedImport: PreparedBackupImport? = null,
    val busy: Boolean = false,
    val errorText: String? = null,
)

@Composable
fun useSettingsPageSections(): ScaffoldExt {
    val mainVm = LocalMainViewModel.current
    val context = LocalActivity.current as MainActivity
    val store by storeFlow.collectAsStateWithLifecycle()
    val vm = viewModel<HomeVm>()
    val backupScope = rememberCoroutineScope()
    val pendingImportUri by BackupUtils.pendingImportUriFlow.collectAsStateWithLifecycle()
    var backupWorkflow by remember { mutableStateOf<BackupWorkflowState?>(null) }

    LaunchedEffect(pendingImportUri) {
        pendingImportUri?.let { uri ->
            backupWorkflow = BackupWorkflowState(
                stage = BackupWorkflowStage.IMPORT_PASSWORD,
                sourceUri = uri,
            )
            vm.showBackupDlgFlow.value = false
        }
    }

    var showToastInputDlg by vm.showToastInputDlgFlow.asMutableState()

    if (showToastInputDlg) {
        var value by remember {
            mutableStateOf(store.actionToast)
        }
        val maxCharLen = 64
        AlertDialog(
            properties = DialogProperties(dismissOnClickOutside = false),
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = "触发提示")
                    PerfIconButton(
                        imageVector = PerfIcon.HelpOutline,
                        contentDescription = "文案规则",
                        onClickLabel = "打开文案规则弹窗",
                        onClick = throttle {
                            showToastInputDlg = false
                            val confirmAction = {
                                mainVm.dialogFlow.value = null
                                showToastInputDlg = true
                            }
                            mainVm.dialogFlow.updateDialogOptions(
                                title = "文案规则",
                                text = $$"触发文案支持变量替换，规则如下\n${1} 子规则名称\n${2} 规则名称\n${3} 触发次数\n\n示例模板\n${1}/${2}/${3}\n\n替换结果\n子规则a/规则A/3",
                                confirmAction = confirmAction,
                                onDismissRequest = confirmAction,
                            )
                        },
                    )
                }
            },
            text = {
                OutlinedTextField(
                    value = value,
                    placeholder = {
                        Text(text = "请输入提示内容")
                    },
                    onValueChange = {
                        value = it.take(maxCharLen)
                    },
                    supportingText = {
                        Text(
                            text = "${value.length} / $maxCharLen",
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.End,
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .autoFocus()
                )
            },
            onDismissRequest = { showToastInputDlg = false },
            confirmButton = {
                TextButton(enabled = value.isNotEmpty(), onClick = {
                    if (value != storeFlow.value.actionToast) {
                        storeFlow.update { it.copy(actionToast = value) }
                        toast("更新成功")
                    }
                    showToastInputDlg = false
                }) {
                    Text(text = "确认")
                }
            },
            dismissButton = {
                TextButton(onClick = { showToastInputDlg = false }) {
                    Text(text = "取消")
                }
            }
        )
    }

    var showNotifTextInputDlg by vm.showNotifTextInputDlgFlow.asMutableState()
    if (showNotifTextInputDlg) {
        var titleValue by remember { mutableStateOf(store.customNotifTitle) }
        var textValue by remember { mutableStateOf(store.customNotifText) }
        AlertDialog(
            properties = DialogProperties(dismissOnClickOutside = false),
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = "通知文案")
                    PerfIconButton(
                        imageVector = PerfIcon.HelpOutline,
                        contentDescription = "文案规则",
                        onClickLabel = "打开文案规则弹窗",
                        onClick = throttle {
                            showNotifTextInputDlg = false
                            val confirmAction = {
                                mainVm.dialogFlow.value = null
                                showNotifTextInputDlg = true
                            }
                            mainVm.dialogFlow.updateDialogOptions(
                                title = "文案规则",
                                text = $$"通知文案支持变量替换，规则如下\n${i} 全局规则数\n${k} 应用数\n${u} 应用规则数\n${n} 触发次数\n\n示例模板\n${i}全局/${k}应用/${u}规则/${n}触发\n\n替换结果\n0全局/1应用/2规则/3触发",
                                confirmAction = confirmAction,
                                onDismissRequest = confirmAction,
                            )
                        },
                    )
                }
            },
            text = {
                val titleMaxLen = 32
                val textMaxLen = 64
                Column(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    CustomOutlinedTextField(
                        label = { Text("主标题") },
                        value = titleValue,
                        placeholder = { Text(text = "请输入内容，支持变量替换") },
                        onValueChange = {
                            titleValue = (if (it.length > titleMaxLen) it.take(titleMaxLen) else it)
                                .filter { c -> c !in "\n\r" }
                        },
                        supportingText = {
                            Text(
                                text = "${titleValue.length} / $titleMaxLen",
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.End,
                            )
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(12.dp),
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    CustomOutlinedTextField(
                        label = { Text("副标题") },
                        value = textValue,
                        placeholder = { Text(text = "请输入内容，支持变量替换") },
                        onValueChange = {
                            textValue = if (it.length > textMaxLen) it.take(textMaxLen) else it
                        },
                        supportingText = {
                            Text(
                                text = "${textValue.length} / $textMaxLen",
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.End,
                            )
                        },
                        maxLines = 4,
                        modifier = Modifier
                            .fillMaxWidth()
                            .autoFocus(),
                        contentPadding = PaddingValues(12.dp),
                    )
                }
            },
            onDismissRequest = {
                showNotifTextInputDlg = false
            },
            confirmButton = {
                TextButton(onClick = {
                    context.justHideSoftInput()
                    if (store.customNotifTitle != textValue || store.customNotifText != textValue) {
                        storeFlow.update {
                            it.copy(
                                customNotifTitle = titleValue,
                                customNotifText = textValue
                            )
                        }
                        toast("更新成功")
                    }
                    showNotifTextInputDlg = false
                }) {
                    Text(
                        text = "确认",
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showNotifTextInputDlg = false }) {
                    Text(
                        text = "取消",
                    )
                }
            })
    }


    var showA11yBlockDlg by vm.showA11yBlockDlgFlow.asMutableState()
    if (showA11yBlockDlg) {
        BlockA11yDialog(onDismissRequest = { showA11yBlockDlg = false })
    }
    if (vm.showBackupDlgFlow.collectAsStateWithLifecycle().value) {
        TextListDialog(
            onDismiss = { vm.showBackupDlgFlow.value = false },
            textList = listOf(
                "导入备份（v2 / 旧版）" to {
                    vm.showBackupDlgFlow.value = false
                    backupScope.launch {
                        context.pickFile("*/*")?.let { uri ->
                            BackupUtils.pendingImportUriFlow.value = uri
                        }
                    }
                },
                "导出备份" to {
                    vm.showBackupDlgFlow.value = false
                    backupWorkflow = BackupWorkflowState(
                        stage = BackupWorkflowStage.EXPORT_CATEGORIES,
                    )
                },
            )
        )
    }
    backupWorkflow?.let { workflow ->
        val dismissBackupWorkflow = {
            if (!workflow.busy) {
                backupWorkflow = null
                BackupUtils.pendingImportUriFlow.value = null
            }
        }
        val passwordValid = workflow.password.codePointCount(0, workflow.password.length) >= 12
        val confirmEnabled = !workflow.busy && when (workflow.stage) {
            BackupWorkflowStage.EXPORT_CATEGORIES -> workflow.selectedCategoryIds.isNotEmpty()
            BackupWorkflowStage.EXPORT_SUMMARY -> true
            BackupWorkflowStage.EXPORT_PASSWORD -> passwordValid &&
                workflow.password == workflow.repeatedPassword
            BackupWorkflowStage.IMPORT_PASSWORD ->
                (passwordValid || workflow.password.isEmpty()) && workflow.sourceUri != null
            BackupWorkflowStage.IMPORT_PREVIEW -> workflow.preparedImport != null
        }
        AlertDialog(
            properties = DialogProperties(
                dismissOnBackPress = !workflow.busy,
                dismissOnClickOutside = false,
            ),
            onDismissRequest = dismissBackupWorkflow,
            title = {
                Text(
                    when (workflow.stage) {
                        BackupWorkflowStage.EXPORT_CATEGORIES -> "选择备份内容"
                        BackupWorkflowStage.EXPORT_SUMMARY -> "确认导出清单"
                        BackupWorkflowStage.EXPORT_PASSWORD -> "设置备份密码"
                        BackupWorkflowStage.IMPORT_PASSWORD -> "输入备份密码"
                        BackupWorkflowStage.IMPORT_PREVIEW -> "确认导入影响"
                    },
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    when (workflow.stage) {
                        BackupWorkflowStage.EXPORT_CATEGORIES -> {
                            Text("前五类默认启用；包含截图、无障碍事件与微信联系人的敏感类别默认关闭。")
                            BackupCatalog.categories.forEach { category ->
                                TextSwitch(
                                    title = backupCategoryTitle(category.id),
                                    subtitle = if (category.sensitive) {
                                        "包含截图/节点或联系人数据；只在明确需要时开启"
                                    } else {
                                        backupCategorySubtitle(category.id)
                                    },
                                    checked = category.id in workflow.selectedCategoryIds,
                                    onCheckedChange = { checked ->
                                        backupWorkflow = workflow.copy(
                                            selectedCategoryIds = if (checked) {
                                                workflow.selectedCategoryIds + category.id
                                            } else {
                                                workflow.selectedCategoryIds - category.id
                                            },
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
                            OutlinedTextField(
                                value = workflow.password,
                                onValueChange = {
                                    backupWorkflow = workflow.copy(password = it, errorText = null)
                                },
                                label = { Text("备份密码") },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            if (workflow.stage == BackupWorkflowStage.EXPORT_PASSWORD) {
                                OutlinedTextField(
                                    value = workflow.repeatedPassword,
                                    onValueChange = {
                                        backupWorkflow = workflow.copy(
                                            repeatedPassword = it,
                                            errorText = null,
                                        )
                                    },
                                    label = { Text("再次输入密码") },
                                    singleLine = true,
                                    visualTransformation = PasswordVisualTransformation(),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                if (workflow.repeatedPassword.isNotEmpty() &&
                                    workflow.password != workflow.repeatedPassword
                                ) {
                                    Text(
                                        "两次输入的密码不一致",
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        }
                        BackupWorkflowStage.EXPORT_SUMMARY -> {
                            Text("将写入加密备份 v2：")
                            workflow.selectedCategoryIds.forEach { categoryId ->
                                Text("• ${backupCategoryTitle(categoryId)}")
                            }
                            Text("格式：GKDSDPBK2 / PBKDF2-SHA256 / AES-256-GCM")
                            Text("预计大小：不超过 65 MiB；实际大小由系统文件选择器显示。")
                            Text("不会包含诊断日志、崩溃文件、缓存、支持包、私有 Store、会话令牌、密钥或命令脚本。")
                        }
                        BackupWorkflowStage.IMPORT_PREVIEW -> {
                            val prepared = requireNotNull(workflow.preparedImport)
                            Text(
                                when (prepared.sourceFormat) {
                                    BackupSourceFormat.ENCRYPTED_V2 ->
                                        "格式版本：加密备份 v${prepared.payload.manifest.formatVersion}"
                                    BackupSourceFormat.LEGACY_V1 ->
                                        "格式版本：旧版未加密备份（已安全转换为 v2）"
                                },
                            )
                            Text("包含类别：")
                            prepared.payload.manifest.categoryIds.forEach { categoryId ->
                                Text("• ${backupCategoryTitle(categoryId)}")
                            }
                            Text("替换方式：只替换备份包含的类别，未包含类别保持不变。")
                            Text("冲突预览：")
                            prepared.conflicts.forEach { conflict ->
                                Text(
                                    "${backupCategoryTitle(conflict.categoryId)}：" +
                                        "新增 ${conflict.added}，覆盖 ${conflict.overwritten}，" +
                                        "删除 ${conflict.deleted}",
                                )
                            }
                        }
                    }
                    if (workflow.busy) Text("正在处理，请勿关闭应用…")
                    workflow.errorText?.let { errorText ->
                        Text(errorText, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = confirmEnabled,
                    onClick = {
                        when (workflow.stage) {
                            BackupWorkflowStage.EXPORT_CATEGORIES -> {
                                backupWorkflow = workflow.copy(
                                    stage = BackupWorkflowStage.EXPORT_SUMMARY,
                                )
                            }
                            BackupWorkflowStage.EXPORT_SUMMARY -> {
                                backupWorkflow = workflow.copy(
                                    stage = BackupWorkflowStage.EXPORT_PASSWORD,
                                )
                            }
                            BackupWorkflowStage.EXPORT_PASSWORD -> {
                                val selectedCategoryIds = workflow.selectedCategoryIds
                                val password = workflow.password.toCharArray()
                                backupWorkflow = BackupWorkflowState(
                                    stage = BackupWorkflowStage.EXPORT_PASSWORD,
                                    selectedCategoryIds = selectedCategoryIds,
                                    busy = true,
                                )
                                backupScope.launch {
                                    try {
                                        val filename =
                                            "gkd-sdp-backup-v2-${System.currentTimeMillis()}.gkdbak"
                                        val targetUri = context.createFile(
                                            contentType = "application/octet-stream",
                                            filename = filename,
                                        )
                                        if (targetUri == null) {
                                            backupWorkflow = BackupWorkflowState(
                                                stage = BackupWorkflowStage.EXPORT_PASSWORD,
                                                selectedCategoryIds = selectedCategoryIds,
                                                errorText = "未选择保存位置，请重新输入密码后导出",
                                            )
                                            return@launch
                                        }
                                        val result = withContext(Dispatchers.IO) {
                                            BackupUtils.exportBackUpData(
                                                selectedCategoryIds,
                                                password,
                                            )
                                        }
                                        when (result) {
                                            is BackupResult.Success -> {
                                                val file = result.value.file
                                                val copied = runCatching {
                                                    withContext(Dispatchers.IO) {
                                                        UriUtils.copyFileToUri(file, targetUri)
                                                    }
                                                }
                                                file.delete()
                                                if (copied.isSuccess) {
                                                    backupWorkflow = null
                                                    toast("加密备份已保存")
                                                } else {
                                                    backupWorkflow = BackupWorkflowState(
                                                        stage = BackupWorkflowStage.EXPORT_PASSWORD,
                                                        selectedCategoryIds = selectedCategoryIds,
                                                        errorText = "写入目标文件失败，请重新输入密码并选择保存位置",
                                                    )
                                                }
                                            }
                                            is BackupResult.Failure -> {
                                                runCatching {
                                                    context.contentResolver.delete(
                                                        targetUri,
                                                        null,
                                                        null,
                                                    )
                                                }
                                                backupWorkflow = BackupWorkflowState(
                                                    stage = BackupWorkflowStage.EXPORT_PASSWORD,
                                                    selectedCategoryIds = selectedCategoryIds,
                                                    errorText = backupErrorText(result.code),
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
                                backupWorkflow = BackupWorkflowState(
                                    stage = BackupWorkflowStage.IMPORT_PASSWORD,
                                    sourceUri = sourceUri,
                                    busy = true,
                                )
                                backupScope.launch {
                                    val result = try {
                                        withContext(Dispatchers.IO) {
                                            BackupUtils.prepareImport(sourceUri, password)
                                        }
                                    } finally {
                                        password.fill('\u0000')
                                    }
                                    when (result) {
                                        is BackupResult.Success -> {
                                            BackupUtils.pendingImportUriFlow.value = null
                                            backupWorkflow = BackupWorkflowState(
                                                stage = BackupWorkflowStage.IMPORT_PREVIEW,
                                                sourceUri = sourceUri,
                                                preparedImport = result.value,
                                            )
                                        }
                                        is BackupResult.Failure -> {
                                            backupWorkflow = BackupWorkflowState(
                                                stage = BackupWorkflowStage.IMPORT_PASSWORD,
                                                sourceUri = sourceUri,
                                                errorText = backupErrorText(result.code),
                                            )
                                        }
                                    }
                                }
                            }
                            BackupWorkflowStage.IMPORT_PREVIEW -> {
                                val preparedImport = requireNotNull(workflow.preparedImport)
                                val sourceUri = workflow.sourceUri
                                backupWorkflow = workflow.copy(busy = true, errorText = null)
                                backupScope.launch {
                                    val refreshed = withContext(Dispatchers.IO) {
                                        BackupUtils.refreshImportPreview(preparedImport)
                                    }
                                    if (refreshed is BackupResult.Failure) {
                                        backupWorkflow = BackupWorkflowState(
                                            stage = BackupWorkflowStage.IMPORT_PREVIEW,
                                            sourceUri = sourceUri,
                                            preparedImport = preparedImport,
                                            errorText = backupErrorText(refreshed.code),
                                        )
                                        return@launch
                                    }
                                    val refreshedImport =
                                        (refreshed as BackupResult.Success).value
                                    if (
                                        refreshedImport.previewStateHash !=
                                        preparedImport.previewStateHash ||
                                        refreshedImport.conflicts != preparedImport.conflicts
                                    ) {
                                        backupWorkflow = BackupWorkflowState(
                                            stage = BackupWorkflowStage.IMPORT_PREVIEW,
                                            sourceUri = sourceUri,
                                            preparedImport = refreshedImport,
                                            errorText = "当前数据已变化，冲突预览已刷新，请再次确认导入",
                                        )
                                        return@launch
                                    }
                                    val result = withContext(Dispatchers.IO) {
                                        BackupUtils.applyImport(
                                            refreshedImport,
                                            confirmed = true,
                                        )
                                    }
                                    when (result) {
                                        is BackupResult.Success -> {
                                            backupWorkflow = null
                                            BackupUtils.pendingImportUriFlow.value = null
                                            toast("备份导入完成")
                                        }
                                        is BackupResult.Failure -> {
                                            backupWorkflow = BackupWorkflowState(
                                                stage = BackupWorkflowStage.IMPORT_PREVIEW,
                                                sourceUri = sourceUri,
                                                preparedImport = refreshedImport,
                                                errorText = backupErrorText(result.code),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    },
                ) {
                    Text(
                        when (workflow.stage) {
                            BackupWorkflowStage.EXPORT_CATEGORIES -> "查看清单"
                            BackupWorkflowStage.EXPORT_SUMMARY -> "设置密码"
                            BackupWorkflowStage.EXPORT_PASSWORD -> "选择位置并导出"
                            BackupWorkflowStage.IMPORT_PASSWORD -> "解密并预览"
                            BackupWorkflowStage.IMPORT_PREVIEW -> "确认替换并导入"
                        },
                    )
                }
            },
            dismissButton = {
                TextButton(enabled = !workflow.busy, onClick = dismissBackupWorkflow) {
                    Text("取消")
                }
            },
        )
    }

    val scrollKey = rememberSaveable { mutableIntStateOf(0) }
    val (scrollBehavior, scrollState) = useScrollBehaviorState(scrollKey)
    LaunchedEffect(null) {
        mainVm.resetPageScrollEvent.collect {
            if (it == BottomNavItem.Settings) {
                scrollKey.intValue++
            }
        }
    }
    return ScaffoldExt(
        navItem = BottomNavItem.Settings,
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            PerfTopAppBar(
                scrollBehavior = scrollBehavior,
                title = {
                    Text(
                        text = BottomNavItem.Settings.label,
                    )
                },
            )
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .verticalScroll(scrollState)
                .padding(contentPadding)
        ) {

            Text(
                text = "常规",
                modifier = Modifier.titleItemPadding(showTop = false),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            val showToastSettingsDlg by vm.showToastSettingsDlgFlow.asMutableState()
            TextSwitch(
                title = "触发提示",
                subtitle = store.actionToast,
                checked = store.toastWhenClick,
                onClickLabel = "打开触发提示弹窗",
                onClick = {
                    showToastInputDlg = true
                },
                suffixIcon = {
                    PerfCustomIconButton(
                        size = 32.dp,
                        iconSize = 20.dp,
                        onClickLabel = "打开提示设置弹窗",
                        onClick = { vm.showToastSettingsDlgFlow.update { !it } },
                        id = R.drawable.ic_page_info,
                        contentDescription = "提示设置",
                        tint = if (showToastSettingsDlg) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                    )
                },
                onCheckedChange = {
                    storeFlow.value = store.copy(
                        toastWhenClick = it
                    )
                })

            AnimatedVisibility(visible = showToastSettingsDlg) {
                Column {
                    TextSwitch(
                        title = "提示样式",
                        subtitle = "使用系统样式",
                        suffix = "查看限制",
                        onSuffixClick = {
                            mainVm.dialogFlow.updateDialogOptions(
                                title = "限制说明",
                                text = "系统 Toast 存在频率限制, 触发过于频繁会被系统强制不显示\n\n如果只使用开屏一类低频率规则可使用系统提示, 否则建议关闭此项使用自定义样式提示",
                            )
                        },
                        checked = store.useSystemToast,
                        onCheckedChange = {
                            storeFlow.value = store.copy(
                                useSystemToast = it
                            )
                        })
                    TextSwitch(
                        title = "轨迹提示",
                        subtitle = "显示触发位置信息",
                        checked = TrackService.isRunning.collectAsStateWithLifecycle().value,
                        onCheckedChange = vm.viewModelScope.launchAsFn<Boolean> {
                            if (it) {
                                mainVm.dialogFlow.waitResult(
                                    title = "使用须知",
                                    text = "开启「轨迹提示」后点击或滑动后会在屏幕上使用悬浮窗绘制轨迹(一段时间后消失)，如果新触摸事件恰好在悬浮窗区域内，可能会被目标应用拒绝，从而导致点击或滑动无响应",
                                    confirmText = "继续",
                                )
                                requiredPermission(context, foregroundServiceSpecialUseState)
                                requiredPermission(context, notificationState)
                                requiredPermission(context, canDrawOverlaysState)
                                TrackService.start()
                            } else {
                                TrackService.stop()
                            }
                        }
                    )
                }
            }

            val subsStatus by vm.subsStatusFlow.collectAsStateWithLifecycle()
            TextSwitch(
                title = "通知文案",
                subtitle = if (store.useCustomNotifText) {
                    store.customNotifTitle + " / " + store.customNotifText
                } else {
                    subsStatus
                },
                checked = store.useCustomNotifText,
                onClickLabel = "打开修改通知文案弹窗",
                onClick = { showNotifTextInputDlg = true },
                onCheckedChange = {
                    storeFlow.value = store.copy(
                        useCustomNotifText = it
                    )
                })

            TextSwitch(
                title = "后台隐藏",
                subtitle = "在「最近任务」隐藏卡片",
                checked = store.excludeFromRecents,
                onCheckedChange = vm.viewModelScope.launchAsFn<Boolean> {
                    if (it) {
                        mainVm.dialogFlow.waitResult(
                            title = "后台隐藏",
                            text = "隐藏卡片后可能导致部分设备无法给任务卡片加锁后台，建议先加锁后再隐藏，若已加锁或没有锁后台机制请继续",
                            confirmText = "继续",
                        )
                    }
                    storeFlow.value = store.copy(
                        excludeFromRecents = !store.excludeFromRecents
                    )
                })

            val scope = rememberCoroutineScope()
            val lazyOn = remember {
                storeFlow.mapState(scope) { it.enableBlockA11yAppList }.debounce(300)
                    .stateIn(scope, SharingStarted.Eagerly, store.enableBlockA11yAppList)
            }.collectAsStateWithLifecycle()
            AnimatedVisibility(visible = lazyOn.value) {
                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .titleItemPadding(),
                    text = "无障碍",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            TextSwitch(
                title = "局部关闭",
                subtitle = "白名单内关闭服务",
                checked = store.enableBlockA11yAppList && shizukuContextFlow.collectAsStateWithLifecycle().value.ok,
                onCheckedChange = vm.viewModelScope.launchAsFn<Boolean> {
                    if (it) {
                        showA11yBlockDlg = true
                    } else {
                        storeFlow.value = store.copy(enableBlockA11yAppList = false)
                        fixRestartAutomatorService()
                    }
                },
            )
            AnimatedVisibility(visible = lazyOn.value) {
                SettingItem(title = "白名单", onClickLabel = "进入无障碍白名单页面", onClick = {
                    mainVm.navigatePage(BlockA11yAppListRoute)
                })
            }

            Text(
                text = "外观",
                modifier = Modifier.titleItemPadding(),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )

            TextMenu(
                title = "深色模式",
                option = DarkThemeOption.objects.findOption(store.enableDarkTheme),
                onOptionChange = {
                    storeFlow.update { s -> s.copy(enableDarkTheme = it.value) }
                }
            )

            if (AndroidTarget.S) {
                TextSwitch(
                    title = "动态配色",
                    checked = store.enableDynamicColor,
                    onCheckedChange = {
                        storeFlow.update { s -> s.copy(enableDynamicColor = it) }
                    }
                )
            }

            TextMenu(
                title = "界面密度",
                option = DisplayDensityOption.objects.findOption(store.displayDensityScale),
                onOptionChange = {
                    storeFlow.update { settings ->
                        settings.copy(displayDensityScale = it.value)
                    }
                },
            )

            TextMenu(
                title = "应用语言",
                option = LanguageOption.objects.findOption(store.languageTag),
                onOptionChange = {
                    storeFlow.update { settings -> settings.copy(languageTag = it.value) }
                },
            )

            Text(
                text = "其他",
                modifier = Modifier.titleItemPadding(),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )

            val summary by ruleSummaryFlow.collectAsStateWithLifecycle()
            val constraints by FocusLockUtils.allConstraintsFlow.collectAsStateWithLifecycle()
            val activeLockCount = remember(summary, constraints) {
                val now = System.currentTimeMillis()
                val activeConstraints = constraints.filter { it.lockEndTime > now }
                fun locked(subsId: Long, appId: String?, groupKey: Int): Boolean {
                    return activeConstraints.any {
                        when (it.targetType) {
                            li.songe.gkd.sdp.data.ConstraintConfig.TYPE_SUBSCRIPTION ->
                                it.subsId == subsId
                            li.songe.gkd.sdp.data.ConstraintConfig.TYPE_APP ->
                                appId != null && it.subsId == subsId && it.appId == appId
                            li.songe.gkd.sdp.data.ConstraintConfig.TYPE_RULE_GROUP ->
                                it.subsId == subsId && it.appId == appId && it.groupKey == groupKey
                            else -> false
                        }
                    }
                }
                summary.globalGroups.count { locked(it.subsItem.id, null, it.group.key) } +
                    summary.appIdToAllGroups.values.flatten().count {
                        locked(it.subsItem.id, it.appId, it.group.key)
                    }
            }
            SettingItem(
                title = "数字自律",
                subtitle = if (activeLockCount > 0) "${activeLockCount} 项规则已锁定" else "未锁定",
                onClick = { mainVm.navigatePage(FocusLockRoute) },
            )

            SettingItem(title = "高级设置", onClick = {
                mainVm.navigatePage(AdvancedPageRoute)
            })
            SettingItem(title = "备份恢复", onClick = {
                vm.showBackupDlgFlow.value = true
            })

            SettingItem(title = "关于", onClick = {
                mainVm.navigatePage(AboutRoute)
            })

            Spacer(modifier = Modifier.height(EmptyHeight))
        }
    }
}

private fun backupCategoryTitle(categoryId: String): String = when (categoryId) {
    "settings" -> "应用设置"
    "subscriptions" -> "订阅与规则配置"
    "self_control_config" -> "数字自律配置"
    "self_control_history" -> "数字自律历史"
    "upstream_history" -> "规则触发与活动历史"
    "sensitive_optional" -> "敏感可选数据"
    else -> categoryId
}

private fun backupCategorySubtitle(categoryId: String): String = when (categoryId) {
    "settings" -> "普通 Store；不含私有 Store、令牌和本机权限"
    "subscriptions" -> "订阅表、配置表与订阅 JSON"
    "self_control_config" -> "专注、拦截、锁定、使用申请与监控配置"
    "self_control_history" -> "专注会话、使用申请、拦截尝试与安装记录"
    "upstream_history" -> "规则动作、Activity 与应用访问历史"
    else -> ""
}

private fun backupErrorText(code: BackupErrorCode): String = when (code) {
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

@Composable
private fun BlockA11yDialog(onDismissRequest: () -> Unit) = FullscreenDialog(onDismissRequest) {
    val mainVm = LocalMainViewModel.current
    val statusRunning by StatusService.isRunning.collectAsStateWithLifecycle()
    val shizukuContext by shizukuContextFlow.collectAsStateWithLifecycle()
    val ignoreBatteryOptimizations by ignoreBatteryOptimizationsState.stateFlow.collectAsStateWithLifecycle()
    val context = LocalActivity.current as MainActivity
    Scaffold(
        topBar = {
            PerfTopAppBar(
                navigationIcon = {
                    PerfIconButton(
                        imageVector = PerfIcon.Close,
                        onClickLabel = "关闭弹窗",
                        onClick = onDismissRequest,
                    )
                },
                title = {
                    Text(text = "局部关闭")
                },
            )
        },
        bottomBar = {
            BottomAppBar {
                Spacer(modifier = Modifier.weight(1f))
                TextButton(
                    enabled = shizukuContext.ok && statusRunning && ignoreBatteryOptimizations,
                    onClick = mainVm.viewModelScope.launchAsFn {
                        onDismissRequest()
                        delay(200)
                        storeFlow.update { it.copy(enableBlockA11yAppList = true) }
                    }
                ) {
                    Text(text = "继续")
                }
                Spacer(modifier = Modifier.width(itemHorizontalPadding))
            }
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(contentPadding)
                .padding(horizontal = itemHorizontalPadding)
        ) {
            CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.bodyMedium) {
                Text(text = "「局部关闭」可在白名单应用内关闭服务，来解决界面异常，游戏掉帧或无障碍检测的问题")
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = "使用须知", style = MaterialTheme.typography.titleMedium)
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    RequiredTextItem(text = "切换服务会造成短暂触摸卡顿，请自行测试后再编辑白名单")
                    RequiredTextItem(text = "使用其它无障碍应用可能导致优化无效，可在服务关闭后自行确认")
                    RequiredTextItem(text = "必须确保服务关闭后的持续后台运行，否则会被系统暂停或结束运行导致重启失败")
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = "使用条件", style = MaterialTheme.typography.titleMedium)
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    RequiredTextItem(
                        text = "Shizuku 授权",
                        enabled = !shizukuContext.ok,
                        imageVector = if (shizukuContext.ok) PerfIcon.Check else PerfIcon.ArrowForward,
                        onClick = mainVm.viewModelScope.launchAsFn(Dispatchers.IO) {
                            mainVm.guardShizukuContext()
                        },
                    )
                    RequiredTextItem(
                        text = "开启「常驻通知」",
                        enabled = !statusRunning,
                        imageVector = if (statusRunning) PerfIcon.Check else PerfIcon.ArrowForward,
                        onClick = mainVm.viewModelScope.launchAsFn {
                            StatusService.requestStart(context)
                        },
                    )
                    RequiredTextItem(
                        text = "省电策略设置为无限制",
                        enabled = !ignoreBatteryOptimizations,
                        imageVector = if (ignoreBatteryOptimizations) PerfIcon.Check else PerfIcon.ArrowForward,
                        onClickLabel = "打开忽略电池优化设置页面",
                        onClick = mainVm.viewModelScope.launchAsFn {
                            requiredPermission(context, ignoreBatteryOptimizationsState)
                        },
                    )
                    RequiredTextItem(
                        text = "(可选) 允许自启动",
                        enabled = true,
                        imageVector = PerfIcon.OpenInNew,
                        onClickLabel = "打开应用详情页面",
                        onClick = {
                            openAppDetailsSettings()
                        },
                    )
                    RequiredTextItem(
                        text = "(可选) 在「最近任务」锁定",
                        enabled = true,
                        imageVector = PerfIcon.OpenInNew,
                        onClickLabel = "打开应用详情页面",
                        onClick = {
                            val m = shizukuContextFlow.value.inputManager
                            if (m != null) {
                                m.key(KeyEvent.KEYCODE_APP_SWITCH)
                            } else {
                                toast("请先授权 Shizuku")
                            }
                        },
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = "某些场景下服务刚启动时概率不工作，如多次遇到此情况则不建议使用此功能")
            }
            Spacer(modifier = Modifier.height(EmptyHeight))
        }
    }
}

@Composable
private fun RequiredTextItem(
    text: String,
    imageVector: ImageVector? = null,
    enabled: Boolean = false,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
) {
    Row(
        modifier = Modifier
            .clip(MaterialTheme.shapes.extraSmall)
            .run {
                if (onClick != null) {
                    clickable(
                        enabled = enabled,
                        onClick = throttle(onClick),
                        onClickLabel = onClickLabel
                    )
                } else {
                    this
                }
            }
            .padding(horizontal = 4.dp),
    ) {
        val lineHeightDp = LocalDensity.current.run { LocalTextStyle.current.lineHeight.toDp() }
        Spacer(
            modifier = Modifier
                .padding(vertical = (lineHeightDp - 4.dp) / 2)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.tertiary)
                .size(4.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = text)
        if (imageVector != null) {
            PerfIcon(
                imageVector = imageVector,
                modifier = Modifier.iconTextSize(),
            )
        }
    }

}
