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
import androidx.compose.ui.res.stringResource

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
    Text(stringResource(R.string.s_f1484fa78b), modifier = Modifier.titleItemPadding(showTop = false), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
    TextSwitch(
        title = stringResource(R.string.s_5bf7ff408f),
        subtitle = store.actionToast,
        checked = store.toastWhenClick,
        onClickLabel = stringResource(R.string.s_2313ce7d22),
        onClick = { showToastInputDlg.value = true },
        suffixIcon = {
            PerfCustomIconButton(
                size = 32.dp,
                iconSize = 20.dp,
                onClickLabel = li.songe.gkd.sdp.app.getString(R.string.s_be1e5b4074),
                onClick = { vm.showToastSettingsDlgFlow.update { !it } },
                id = R.drawable.ic_page_info,
                contentDescription = li.songe.gkd.sdp.app.getString(R.string.s_aaa3b3883f),
                tint = if (showToastSettingsDlg) MaterialTheme.colorScheme.primary else LocalContentColor.current,
            )
        },
        onCheckedChange = { storeFlow.value = store.copy(toastWhenClick = it) },
    )
    AnimatedVisibility(showToastSettingsDlg) {
        Column {
            TextSwitch(
                title = li.songe.gkd.sdp.app.getString(R.string.s_29ea25d303),
                subtitle = li.songe.gkd.sdp.app.getString(R.string.s_565e1ff87a),
                suffix = "查看限制",
                onSuffixClick = {
                    mainVm.dialogFlow.updateDialogOptions(
                        title = li.songe.gkd.sdp.app.getString(R.string.s_1b2219a307),
                        text = li.songe.gkd.sdp.app.getString(R.string.s_5118a80944),
                    )
                },
                checked = store.useSystemToast,
                onCheckedChange = { storeFlow.value = store.copy(useSystemToast = it) },
            )
            TextSwitch(
                title = li.songe.gkd.sdp.app.getString(R.string.s_6a1b839874),
                subtitle = li.songe.gkd.sdp.app.getString(R.string.s_e78582fda3),
                checked = TrackService.isRunning.collectAsStateWithLifecycle().value,
                onCheckedChange = vm.viewModelScope.launchAsFn<Boolean> {
                    if (it) {
                        mainVm.dialogFlow.waitResult(
                            title = li.songe.gkd.sdp.app.getString(R.string.s_59e2c8e61d),
                            text = li.songe.gkd.sdp.app.getString(R.string.s_881aca9e23),
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
        title = stringResource(R.string.s_ce7c7d71a7),
        subtitle = if (store.useCustomNotifText) stringResource(R.string.s_bedcb574bc, (store.customNotifTitle).toString(), (store.customNotifText).toString()) else subsStatus,
        checked = store.useCustomNotifText,
        onClickLabel = stringResource(R.string.s_ac04fdc8b3),
        onClick = { showNotifTextInputDlg.value = true },
        onCheckedChange = { storeFlow.value = store.copy(useCustomNotifText = it) },
    )
    TextSwitch(
        title = stringResource(R.string.s_8c91b02262),
        subtitle = stringResource(R.string.s_95a85cd30d),
        checked = store.excludeFromRecents,
        onCheckedChange = vm.viewModelScope.launchAsFn<Boolean> {
            if (it) {
                mainVm.dialogFlow.waitResult(
                    title = li.songe.gkd.sdp.app.getString(R.string.s_8c91b02262),
                    text = li.songe.gkd.sdp.app.getString(R.string.s_7834885df6),
                    confirmText = "继续",
                )
            }
            storeFlow.value = store.copy(excludeFromRecents = !store.excludeFromRecents)
        },
    )
    if (showA11ySection) {
        Text(stringResource(R.string.s_04c62c8f3d), modifier = Modifier.fillMaxWidth().titleItemPadding(), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        TextSwitch(
            title = stringResource(R.string.s_86613e925d),
            subtitle = stringResource(R.string.s_2e268f4e64),
            checked = store.enableBlockA11yAppList && shizukuOk,
            onCheckedChange = vm.viewModelScope.launchAsFn<Boolean> {
                if (it) showA11yBlockDlg.value = true else {
                    storeFlow.value = store.copy(enableBlockA11yAppList = false)
                    fixRestartAutomatorService()
                }
            },
        )
        SettingItem(title = stringResource(R.string.s_8f74cd015b), onClickLabel = stringResource(R.string.s_7e4c9176ba), onClick = { mainVm.navigatePage(BlockA11yAppListRoute) })
    }
}

@Composable
private fun SettingsAppearanceSection(store: SettingsStore) {
    Text(stringResource(R.string.s_09b58aa342), modifier = Modifier.titleItemPadding(), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
    TextMenu(
        title = stringResource(R.string.s_750642ccd6),
        option = DarkThemeOption.objects.findOption(store.enableDarkTheme),
        onOptionChange = { storeFlow.update { s -> s.copy(enableDarkTheme = it.value) } },
    )
    if (AndroidTarget.S) {
        TextSwitch(title = stringResource(R.string.s_a75357bc35), checked = store.enableDynamicColor, onCheckedChange = { storeFlow.update { s -> s.copy(enableDynamicColor = it) } })
    }
    TextMenu(
        title = stringResource(R.string.s_c2fd3fcdd3),
        option = DisplayDensityOption.objects.findOption(store.displayDensityScale),
        onOptionChange = { storeFlow.update { settings -> settings.copy(displayDensityScale = it.value) } },
    )
    TextMenu(
        title = stringResource(R.string.s_ea10de41b6),
        option = LanguageOption.objects.findOption(store.languageTag),
        onOptionChange = { storeFlow.update { settings -> settings.copy(languageTag = it.value) } },
    )
}

@Composable
private fun SettingsOtherSection(store: SettingsStore, mainVm: MainViewModel, vm: HomeVm) {
    Text(stringResource(R.string.s_1a26edf94a), modifier = Modifier.titleItemPadding(), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
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
        title = stringResource(R.string.s_6337015d1f),
        subtitle = if (activeLockCount > 0) stringResource(R.string.s_ed7465c7b9, (activeLockCount).toString()) else stringResource(R.string.s_74b0d1f601),
        onClick = { mainVm.navigatePage(FocusLockRoute) },
    )
    SettingItem(title = stringResource(R.string.s_dd07e641ca), onClick = { mainVm.navigatePage(AdvancedPageRoute) })
    SettingItem(title = stringResource(R.string.s_8233960bfd), onClick = { vm.showBackupDlgFlow.value = true })
    SettingItem(title = stringResource(R.string.s_bed172efc9), onClick = { mainVm.navigatePage(AboutRoute) })
}
