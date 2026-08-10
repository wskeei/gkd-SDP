@file:JvmName("SettingsDialogs")

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
import androidx.compose.runtime.MutableState
import li.songe.gkd.sdp.MainViewModel
import li.songe.gkd.sdp.store.SettingsStore

@Composable
internal fun SettingsTextDialogs(
    context: MainActivity,
    mainVm: MainViewModel,
    store: SettingsStore,
    showToastInputDlg: MutableState<Boolean>,
    showNotifTextInputDlg: MutableState<Boolean>,
) {
    SettingsToastDialog(mainVm, store, showToastInputDlg)
    SettingsNotificationDialog(context, mainVm, store, showNotifTextInputDlg)
}

@Composable
private fun SettingsToastDialog(
    mainVm: MainViewModel,
    store: SettingsStore,
    showToastInputDlg: MutableState<Boolean>,
) {
    if (!showToastInputDlg.value) return
    var value by remember { mutableStateOf(store.actionToast) }
    val maxCharLen = 64
    AlertDialog(
        properties = DialogProperties(dismissOnClickOutside = false),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("触发提示")
                PerfIconButton(
                    imageVector = PerfIcon.HelpOutline,
                    contentDescription = "文案规则",
                    onClickLabel = "打开文案规则弹窗",
                    onClick = throttle {
                        showToastInputDlg.value = false
                        val confirmAction = { mainVm.dialogFlow.value = null; showToastInputDlg.value = true }
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
                placeholder = { Text("请输入提示内容") },
                onValueChange = { value = it.take(maxCharLen) },
                supportingText = { Text("${value.length} / $maxCharLen", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End) },
                modifier = Modifier.fillMaxWidth().autoFocus(),
            )
        },
        onDismissRequest = { showToastInputDlg.value = false },
        confirmButton = {
            TextButton(
                enabled = value.isNotEmpty(),
                onClick = {
                    if (value != storeFlow.value.actionToast) {
                        storeFlow.update { it.copy(actionToast = value) }
                        toast("更新成功")
                    }
                    showToastInputDlg.value = false
                },
            ) { Text("确认") }
        },
        dismissButton = { TextButton(onClick = { showToastInputDlg.value = false }) { Text("取消") } },
    )
}

@Composable
private fun SettingsNotificationDialog(
    context: MainActivity,
    mainVm: MainViewModel,
    store: SettingsStore,
    showNotifTextInputDlg: MutableState<Boolean>,
) {
    if (!showNotifTextInputDlg.value) return
    var titleValue by remember { mutableStateOf(store.customNotifTitle) }
    var textValue by remember { mutableStateOf(store.customNotifText) }
    AlertDialog(
        properties = DialogProperties(dismissOnClickOutside = false),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("通知文案")
                PerfIconButton(
                    imageVector = PerfIcon.HelpOutline,
                    contentDescription = "文案规则",
                    onClickLabel = "打开文案规则弹窗",
                    onClick = throttle {
                        showNotifTextInputDlg.value = false
                        val confirmAction = { mainVm.dialogFlow.value = null; showNotifTextInputDlg.value = true }
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
            Column(modifier = Modifier.fillMaxWidth()) {
                val titleMaxLen = 32
                val textMaxLen = 64
                CustomOutlinedTextField(
                    label = { Text("主标题") },
                    value = titleValue,
                    placeholder = { Text("请输入内容，支持变量替换") },
                    onValueChange = { titleValue = it.take(titleMaxLen).filter { c -> c !in "\n\r" } },
                    supportingText = { Text("${titleValue.length} / $titleMaxLen", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(12.dp),
                )
                Spacer(modifier = Modifier.height(4.dp))
                CustomOutlinedTextField(
                    label = { Text("副标题") },
                    value = textValue,
                    placeholder = { Text("请输入内容，支持变量替换") },
                    onValueChange = { textValue = it.take(textMaxLen) },
                    supportingText = { Text("${textValue.length} / $textMaxLen", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End) },
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth().autoFocus(),
                    contentPadding = PaddingValues(12.dp),
                )
            }
        },
        onDismissRequest = { showNotifTextInputDlg.value = false },
        confirmButton = {
            TextButton(onClick = {
                context.justHideSoftInput()
                if (store.customNotifTitle != titleValue || store.customNotifText != textValue) {
                    storeFlow.update { it.copy(customNotifTitle = titleValue, customNotifText = textValue) }
                    toast("更新成功")
                }
                showNotifTextInputDlg.value = false
            }) { Text("确认") }
        },
        dismissButton = { TextButton(onClick = { showNotifTextInputDlg.value = false }) { Text("取消") } },
    )
}

@Composable
internal fun BlockA11yDialog(onDismissRequest: () -> Unit) = FullscreenDialog(onDismissRequest) {
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
