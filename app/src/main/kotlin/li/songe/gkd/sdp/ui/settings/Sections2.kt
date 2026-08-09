@file:JvmName("SettingsSections2")

package li.songe.gkd.sdp.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.*
import li.songe.gkd.sdp.MainActivity
import li.songe.gkd.sdp.MainViewModel
import li.songe.gkd.sdp.R
import li.songe.gkd.sdp.permission.*
import li.songe.gkd.sdp.service.TrackService
import li.songe.gkd.sdp.service.fixRestartAutomatorService
import li.songe.gkd.sdp.shizuku.shizukuContextFlow
import li.songe.gkd.sdp.store.SettingsStore
import li.songe.gkd.sdp.store.storeFlow
import li.songe.gkd.sdp.ui.*
import li.songe.gkd.sdp.ui.component.*
import li.songe.gkd.sdp.ui.share.LocalMainViewModel
import li.songe.gkd.sdp.ui.share.asMutableState
import li.songe.gkd.sdp.ui.style.EmptyHeight
import li.songe.gkd.sdp.ui.style.titleItemPadding
import li.songe.gkd.sdp.util.*

@Composable
internal fun SettingsContent(
    context: MainActivity,
    mainVm: MainViewModel,
    vm: HomeVm,
    store: SettingsStore,
    showToastInputDlg: MutableState<Boolean>,
    showNotifTextInputDlg: MutableState<Boolean>,
    showA11yBlockDlg: MutableState<Boolean>,
): ScaffoldExt {
    val scrollKey = rememberSaveable { mutableIntStateOf(0) }
    val (scrollBehavior, scrollState) = useScrollBehaviorState(scrollKey)
    LaunchedEffect(Unit) {
        mainVm.resetPageScrollEvent.collect {
            if (it == BottomNavItem.Settings) scrollKey.intValue++
        }
    }
    val showToastSettingsDlg by vm.showToastSettingsDlgFlow.asMutableState()
    val scope = rememberCoroutineScope()
    val lazyOn = remember {
        storeFlow.mapState(scope) { it.enableBlockA11yAppList }
            .debounce(300)
            .stateIn(scope, SharingStarted.Eagerly, store.enableBlockA11yAppList)
    }.collectAsStateWithLifecycle()
    val shizukuOk = shizukuContextFlow.collectAsStateWithLifecycle().value.ok

    return ScaffoldExt(
        navItem = BottomNavItem.Settings,
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            PerfTopAppBar(scrollBehavior = scrollBehavior, title = { Text(BottomNavItem.Settings.label) })
        },
    ) { contentPadding ->
        Column(modifier = Modifier.verticalScroll(scrollState).padding(contentPadding)) {
            SettingsGeneralSection(
                context = context,
                mainVm = mainVm,
                vm = vm,
                store = store,
                showToastInputDlg = showToastInputDlg,
                showNotifTextInputDlg = showNotifTextInputDlg,
                showToastSettingsDlg = showToastSettingsDlg,
                showA11yBlockDlg = showA11yBlockDlg,
                showA11ySection = lazyOn.value,
                shizukuOk = shizukuOk,
            )
            SettingsAppearanceSection(store = store)
            SettingsOtherSection(store = store, mainVm = mainVm, vm = vm)
            Spacer(modifier = Modifier.height(EmptyHeight))
        }
    }
}

@Composable
private fun SettingsGeneralSection(
    context: MainActivity,
    mainVm: MainViewModel,
    vm: HomeVm,
    store: SettingsStore,
    showToastInputDlg: MutableState<Boolean>,
    showNotifTextInputDlg: MutableState<Boolean>,
    showToastSettingsDlg: Boolean,
    showA11yBlockDlg: MutableState<Boolean>,
    showA11ySection: Boolean,
    shizukuOk: Boolean,
) {
    Text("常规", modifier = Modifier.titleItemPadding(showTop = false), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
    TextSwitch(
        title = "触发提示",
        subtitle = store.actionToast,
        checked = store.toastWhenClick,
        onClickLabel = "打开触发提示弹窗",
        onClick = { showToastInputDlg.value = true },
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
        onCheckedChange = { storeFlow.value = store.copy(toastWhenClick = it) },
    )
    AnimatedVisibility(showToastSettingsDlg) {
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
                onCheckedChange = { storeFlow.value = store.copy(useSystemToast = it) },
            )
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
                    } else TrackService.stop()
                },
            )
        }
    }
    val subsStatus by vm.subsStatusFlow.collectAsStateWithLifecycle()
    TextSwitch(
        title = "通知文案",
        subtitle = if (store.useCustomNotifText) "${store.customNotifTitle} / ${store.customNotifText}" else subsStatus,
        checked = store.useCustomNotifText,
        onClickLabel = "打开修改通知文案弹窗",
        onClick = { showNotifTextInputDlg.value = true },
        onCheckedChange = { storeFlow.value = store.copy(useCustomNotifText = it) },
    )
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
            storeFlow.value = store.copy(excludeFromRecents = !store.excludeFromRecents)
        },
    )
    if (showA11ySection) {
        Text("无障碍", modifier = Modifier.fillMaxWidth().titleItemPadding(), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        TextSwitch(
            title = "局部关闭",
            subtitle = "白名单内关闭服务",
            checked = store.enableBlockA11yAppList && shizukuOk,
            onCheckedChange = vm.viewModelScope.launchAsFn<Boolean> {
                if (it) showA11yBlockDlg.value = true else {
                    storeFlow.value = store.copy(enableBlockA11yAppList = false)
                    fixRestartAutomatorService()
                }
            },
        )
        SettingItem(title = "白名单", onClickLabel = "进入无障碍白名单页面", onClick = { mainVm.navigatePage(BlockA11yAppListRoute) })
    }
}

@Composable
private fun SettingsAppearanceSection(store: SettingsStore) {
    Text("外观", modifier = Modifier.titleItemPadding(), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
    TextMenu(
        title = "深色模式",
        option = DarkThemeOption.objects.findOption(store.enableDarkTheme),
        onOptionChange = { storeFlow.update { s -> s.copy(enableDarkTheme = it.value) } },
    )
    if (AndroidTarget.S) {
        TextSwitch(title = "动态配色", checked = store.enableDynamicColor, onCheckedChange = { storeFlow.update { s -> s.copy(enableDynamicColor = it) } })
    }
    TextMenu(
        title = "界面密度",
        option = DisplayDensityOption.objects.findOption(store.displayDensityScale),
        onOptionChange = { storeFlow.update { settings -> settings.copy(displayDensityScale = it.value) } },
    )
    TextMenu(
        title = "应用语言",
        option = LanguageOption.objects.findOption(store.languageTag),
        onOptionChange = { storeFlow.update { settings -> settings.copy(languageTag = it.value) } },
    )
}

@Composable
private fun SettingsOtherSection(store: SettingsStore, mainVm: MainViewModel, vm: HomeVm) {
    Text("其他", modifier = Modifier.titleItemPadding(), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
    val summary by ruleSummaryFlow.collectAsStateWithLifecycle()
    val constraints by FocusLockUtils.allConstraintsFlow.collectAsStateWithLifecycle()
    val activeLockCount = remember(summary, constraints) {
        val activeConstraints = constraints.filter { it.lockEndTime > System.currentTimeMillis() }
        fun locked(subsId: Long, appId: String?, groupKey: Int): Boolean = activeConstraints.any {
            when (it.targetType) {
                li.songe.gkd.sdp.data.ConstraintConfig.TYPE_SUBSCRIPTION -> it.subsId == subsId
                li.songe.gkd.sdp.data.ConstraintConfig.TYPE_APP -> appId != null && it.subsId == subsId && it.appId == appId
                li.songe.gkd.sdp.data.ConstraintConfig.TYPE_RULE_GROUP -> it.subsId == subsId && it.appId == appId && it.groupKey == groupKey
                else -> false
            }
        }
        summary.globalGroups.count { locked(it.subsItem.id, null, it.group.key) } +
            summary.appIdToAllGroups.values.flatten().count { locked(it.subsItem.id, it.appId, it.group.key) }
    }
    SettingItem(
        title = "数字自律",
        subtitle = if (activeLockCount > 0) "${activeLockCount} 项规则已锁定" else "未锁定",
        onClick = { mainVm.navigatePage(FocusLockRoute) },
    )
    SettingItem(title = "高级设置", onClick = { mainVm.navigatePage(AdvancedPageRoute) })
    SettingItem(title = "备份恢复", onClick = { vm.showBackupDlgFlow.value = true })
    SettingItem(title = "关于", onClick = { mainVm.navigatePage(AboutRoute) })
}
