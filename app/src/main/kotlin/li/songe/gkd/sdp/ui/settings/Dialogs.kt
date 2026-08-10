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
import li.songe.gkd.sdp.settings.SettingsFormPolicy
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
import androidx.compose.ui.res.stringResource
import li.songe.gkd.sdp.ui.style.DimensionTokens

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
                Text(stringResource(R.string.s_5bf7ff408f))
                PerfIconButton(
                    imageVector = PerfIcon.HelpOutline,
                    contentDescription = "文案规则",
                    onClickLabel = "打开文案规则弹窗",
                    onClick = throttle {
                        showToastInputDlg.value = false
                        val confirmAction = { mainVm.dialogFlow.value = null; showToastInputDlg.value = true }
                        mainVm.dialogFlow.updateDialogOptions(
                            title = li.songe.gkd.sdp.app.getString(R.string.s_d88d6e6c25),
                            text = li.songe.gkd.sdp.app.getString(R.string.s_1941b8aa85),
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
                placeholder = { Text(li.songe.gkd.sdp.app.getString(R.string.s_29207cc695)) },
                onValueChange = { value = it.take(maxCharLen) },
                supportingText = { Text(li.songe.gkd.sdp.app.getString(R.string.s_61485b8822, (value.length).toString(), (maxCharLen).toString()), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End) },
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
                        toast(li.songe.gkd.sdp.app.getString(R.string.s_e2cff77372))
                    }
                    showToastInputDlg.value = false
                },
            ) { Text(stringResource(R.string.s_b56d9ac6c5)) }
        },
        dismissButton = { TextButton(onClick = { showToastInputDlg.value = false }) { Text(stringResource(R.string.s_4d0b4688c7)) } },
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
                Text(stringResource(R.string.s_ce7c7d71a7))
                PerfIconButton(
                    imageVector = PerfIcon.HelpOutline,
                    contentDescription = "文案规则",
                    onClickLabel = "打开文案规则弹窗",
                    onClick = throttle {
                        showNotifTextInputDlg.value = false
                        val confirmAction = { mainVm.dialogFlow.value = null; showNotifTextInputDlg.value = true }
                        mainVm.dialogFlow.updateDialogOptions(
                            title = li.songe.gkd.sdp.app.getString(R.string.s_d88d6e6c25),
                            text = li.songe.gkd.sdp.app.getString(R.string.s_3036ac5688),
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
                    label = { Text(li.songe.gkd.sdp.app.getString(R.string.s_e6dc2df4a4)) },
                    value = titleValue,
                    placeholder = { Text(li.songe.gkd.sdp.app.getString(R.string.s_d8eb8652a1)) },
                    onValueChange = { titleValue = it.take(titleMaxLen).filter { c -> c !in "\n\r" } },
                    supportingText = { Text(li.songe.gkd.sdp.app.getString(R.string.s_2990881615, (titleValue.length).toString(), (titleMaxLen).toString()), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(12.dp),
                )
                Spacer(modifier = Modifier.height(4.dp))
                CustomOutlinedTextField(
                    label = { Text(li.songe.gkd.sdp.app.getString(R.string.s_8344831e7c)) },
                    value = textValue,
                    placeholder = { Text(li.songe.gkd.sdp.app.getString(R.string.s_d8eb8652a1)) },
                    onValueChange = { textValue = it.take(textMaxLen) },
                    supportingText = { Text(li.songe.gkd.sdp.app.getString(R.string.s_98362e17dd, (textValue.length).toString(), (textMaxLen).toString()), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End) },
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
                if (SettingsFormPolicy.notificationTextChanged(store.customNotifTitle, store.customNotifText, titleValue, textValue)) {
                    storeFlow.update { it.copy(customNotifTitle = titleValue, customNotifText = textValue) }
                    toast(li.songe.gkd.sdp.app.getString(R.string.s_e2cff77372))
                }
                showNotifTextInputDlg.value = false
            }) { Text(stringResource(R.string.s_b56d9ac6c5)) }
        },
        dismissButton = { TextButton(onClick = { showNotifTextInputDlg.value = false }) { Text(stringResource(R.string.s_4d0b4688c7)) } },
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
                    Text(text = li.songe.gkd.sdp.app.getString(R.string.s_86613e925d))
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
                    Text(text = li.songe.gkd.sdp.app.getString(R.string.s_1fc1afc5c5))
                }
                Spacer(modifier = Modifier.width(DimensionTokens.SpacingBase))
            }
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(contentPadding)
                .padding(horizontal = DimensionTokens.SpacingBase)
        ) {
            CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.bodyMedium) {
                Text(text = li.songe.gkd.sdp.app.getString(R.string.s_be7bf1f6b3))
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = li.songe.gkd.sdp.app.getString(R.string.s_59e2c8e61d), style = MaterialTheme.typography.titleMedium)
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    RequiredTextItem(text = "切换服务会造成短暂触摸卡顿，请自行测试后再编辑白名单")
                    RequiredTextItem(text = "使用其它无障碍应用可能导致优化无效，可在服务关闭后自行确认")
                    RequiredTextItem(text = "必须确保服务关闭后的持续后台运行，否则会被系统暂停或结束运行导致重启失败")
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = li.songe.gkd.sdp.app.getString(R.string.s_b412fa069d), style = MaterialTheme.typography.titleMedium)
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
                                toast(li.songe.gkd.sdp.app.getString(R.string.s_7b29e9051a))
                            }
                        },
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = li.songe.gkd.sdp.app.getString(R.string.s_d0cd80bc26))
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
