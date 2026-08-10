@file:JvmName("AdvancedSections21")

package li.songe.gkd.sdp.ui

import android.app.Activity
import android.content.Context
import android.media.projection.MediaProjectionManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import com.dylanc.activityresult.launcher.launchForResult
import kotlinx.coroutines.flow.update
import li.songe.gkd.sdp.MainActivity
import li.songe.gkd.sdp.MainViewModel
import li.songe.gkd.sdp.R
import li.songe.gkd.sdp.permission.canDrawOverlaysState
import li.songe.gkd.sdp.permission.foregroundServiceSpecialUseState
import li.songe.gkd.sdp.permission.notificationState
import li.songe.gkd.sdp.permission.requiredPermission
import li.songe.gkd.sdp.permission.shizukuGrantedState
import li.songe.gkd.sdp.remote.CleartextOriginAuthorizations
import li.songe.gkd.sdp.remote.RemoteListenMode
import li.songe.gkd.sdp.remote.RemoteScope
import li.songe.gkd.sdp.remote.RemoteSessionSnapshot
import li.songe.gkd.sdp.service.ActivityService
import li.songe.gkd.sdp.service.ButtonService
import li.songe.gkd.sdp.service.EventService
import li.songe.gkd.sdp.service.HttpService
import li.songe.gkd.sdp.service.ScreenshotService
import li.songe.gkd.sdp.shizuku.updateBinderMutex
import li.songe.gkd.sdp.store.SettingsStore
import li.songe.gkd.sdp.store.storeFlow
import li.songe.gkd.sdp.ui.component.AuthCard
import li.songe.gkd.sdp.ui.component.PerfCustomIconButton
import li.songe.gkd.sdp.ui.component.PerfIcon
import li.songe.gkd.sdp.ui.component.PerfIconButton
import li.songe.gkd.sdp.ui.component.PerfTopAppBar
import li.songe.gkd.sdp.ui.component.SettingItem
import li.songe.gkd.sdp.ui.component.TextSwitch
import li.songe.gkd.sdp.ui.style.EmptyHeight
import li.songe.gkd.sdp.ui.style.iconTextSize
import li.songe.gkd.sdp.ui.style.itemPadding
import li.songe.gkd.sdp.ui.style.titleItemPadding
import li.songe.gkd.sdp.util.AndroidTarget
import li.songe.gkd.sdp.util.ShortUrlSet
import li.songe.gkd.sdp.util.copyText
import li.songe.gkd.sdp.util.launchAsFn
import li.songe.gkd.sdp.util.throttle
import li.songe.gkd.sdp.util.toast
import androidx.compose.ui.res.stringResource
import li.songe.gkd.sdp.app

internal fun remoteScopeLabel(scope: RemoteScope): String = when (scope) {
    RemoteScope.SERVER_INFO -> "服务信息"
    RemoteScope.SNAPSHOT_LIST -> "快照列表"
    RemoteScope.VIEW_SNAPSHOT -> "查看快照/截图"
    RemoteScope.CAPTURE_SNAPSHOT -> "捕获快照"
    RemoteScope.DELETE_SNAPSHOT -> "删除快照"
    RemoteScope.UPDATE_SUBSCRIPTION -> "更新内存订阅"
    RemoteScope.EXEC_SELECTOR -> "执行选择器"
}

@Composable
internal fun AdvancedPageContent(
    context: MainActivity,
    mainVm: MainViewModel,
    vm: AdvancedVm,
    store: SettingsStore,
    showEditPortDlg: MutableState<Boolean>,
    showShizukuState: MutableState<Boolean>,
    showCaptureScreenshotDlg: MutableState<Boolean>,
    showHttpSettingDlg: MutableState<Boolean>,
) {
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            PerfTopAppBar(
                scrollBehavior = scrollBehavior,
                navigationIcon = { PerfIconButton(imageVector = PerfIcon.ArrowBack, onClick = mainVm::popPage) },
                title = { Text(app.getString(R.string.s_dd07e641ca)) },
            )
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(contentPadding),
        ) {
            AdvancedShizukuSection(mainVm, store, showShizukuState)
            AdvancedHttpSection(context, vm, store, showEditPortDlg, showHttpSettingDlg)
            AdvancedSnapshotSection(context, mainVm, vm, store, showCaptureScreenshotDlg)
            AdvancedLogSection(context, mainVm, vm)
            Spacer(modifier = Modifier.height(EmptyHeight))
        }
    }
}

@Composable
private fun AdvancedShizukuSection(
    mainVm: MainViewModel,
    store: SettingsStore,
    showShizukuState: MutableState<Boolean>,
) {
    Row(
        modifier = Modifier.fillMaxWidth().titleItemPadding(showTop = false),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(stringResource(R.string.s_8ecb7f0457), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        PerfIcon(
            modifier = Modifier
                .clip(MaterialTheme.shapes.extraSmall)
                .clickable(onClickLabel = stringResource(R.string.s_3b8328c91c), onClick = throttle { showShizukuState.value = true })
                .iconTextSize(textStyle = MaterialTheme.typography.titleSmall),
            imageVector = PerfIcon.Api,
            tint = MaterialTheme.colorScheme.primary,
            contentDescription = stringResource(R.string.s_74c1843061),
        )
    }
    val shizukuGranted by shizukuGrantedState.stateFlow.collectAsStateWithLifecycle()
    AnimatedVisibility(store.enableShizuku && !shizukuGranted) {
        AuthCard(title = stringResource(R.string.s_86bddceb9d), subtitle = stringResource(R.string.s_a7bd6fc9bb), onAuthClick = mainVm::requestShizuku)
    }
    TextSwitch(
        title = stringResource(R.string.s_6b0ad26edf),
        subtitle = stringResource(R.string.s_a3e561c7c6),
        suffix = "了解更多",
        suffixUnderline = true,
        onSuffixClick = { mainVm.navigateWebPage(ShortUrlSet.URL14) },
        checked = store.enableShizuku,
        suffixIcon = {
            if (updateBinderMutex.state.collectAsStateWithLifecycle().value) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
            }
        },
        onCheckedChange = mainVm::switchEnableShizuku,
        onClick = null,
    )
}

@Composable
private fun AdvancedHttpSection(
    context: MainActivity,
    vm: AdvancedVm,
    store: SettingsStore,
    showEditPortDlg: MutableState<Boolean>,
    showHttpSettingDlg: MutableState<Boolean>,
) {
    val server by HttpService.httpServerFlow.collectAsStateWithLifecycle()
    val httpServerRunning = server != null
    val localNetworkIps by HttpService.localNetworkIpsFlow.collectAsStateWithLifecycle()
    val remoteSession by HttpService.remoteSessionStateFlow.collectAsStateWithLifecycle()
    Text(stringResource(R.string.s_f40b27d6b8), modifier = Modifier.titleItemPadding(), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
    TextSwitch(
        title = stringResource(R.string.s_6a43e3e09d),
        subtitle = stringResource(R.string.s_e330f2e53c),
        suffixIcon = {
            PerfCustomIconButton(
                size = 32.dp,
                iconSize = 20.dp,
                onClickLabel = app.getString(R.string.s_66b10cf5e5),
                onClick = { showHttpSettingDlg.value = !showHttpSettingDlg.value },
                id = R.drawable.ic_page_info,
                contentDescription = app.getString(R.string.s_c5f42f5a0f),
                tint = if (showHttpSettingDlg.value) MaterialTheme.colorScheme.primary else LocalContentColor.current,
            )
        },
        checked = httpServerRunning,
        onCheckedChange = throttle(fn = vm.viewModelScope.launchAsFn<Boolean> {
            if (it) {
                requiredPermission(context, foregroundServiceSpecialUseState)
                requiredPermission(context, notificationState)
                HttpService.start()
            } else {
                HttpService.stop()
            }
        }),
    )
    AnimatedVisibility(httpServerRunning) {
        AdvancedHttpRunningContent(store, localNetworkIps, remoteSession)
    }
    AnimatedVisibility(showHttpSettingDlg.value) {
        AdvancedHttpSettingsContent(store, showEditPortDlg, showHttpSettingDlg)
    }
}

@Composable
private fun AdvancedHttpRunningContent(
    store: SettingsStore,
    localNetworkIps: List<String>,
    remoteSession: RemoteSessionSnapshot,
) {
    CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.bodyMedium) {
        Column(modifier = Modifier.itemPadding()) {
            Text(
                text = if (remoteSession.mode == RemoteListenMode.LOCAL_ONLY) {
                    "监听范围：仅本机"
                } else {
                    val remainingMinutes = remoteSession.accessExpiresAtMillis
                        ?.let { ((it - System.currentTimeMillis()).coerceAtLeast(0) + 59_999) / 60_000 }
                        ?: 0
                    "监听范围：局域网｜剩余 $remainingMinutes 分钟"
                },
            )
            remoteSession.pairingCode?.let { Text(app.getString(R.string.s_cc0dd8de47, it)) }
            remoteSession.clientSummary?.let { Text(app.getString(R.string.s_759c95ad54, it)) }
            Row {
                val localUrl = "http://127.0.0.1:${store.httpServerPort}"
                Text(
                    text = localUrl,
                    color = MaterialTheme.colorScheme.primary,
                    style = LocalTextStyle.current.copy(textDecoration = TextDecoration.Underline),
                    modifier = Modifier.clickable(onClick = throttle {
                        copyText(localUrl)
                        toast(app.getString(R.string.s_6c0be6e7b0))
                    }),
                )
                Spacer(modifier = Modifier.width(2.dp))
                Text(stringResource(R.string.s_93794c92ab))
            }
            if (remoteSession.mode == RemoteListenMode.LAN) {
                localNetworkIps.forEach { host ->
                    val lanUrl = "http://${host}:${store.httpServerPort}"
                    Text(
                        text = lanUrl,
                        color = MaterialTheme.colorScheme.primary,
                        style = LocalTextStyle.current.copy(textDecoration = TextDecoration.Underline),
                        modifier = Modifier.clickable(onClick = throttle {
                            copyText(lanUrl)
                            toast(app.getString(R.string.s_7774d11343))
                        }),
                    )
                }
                TextButton(onClick = HttpService::disconnectLanSession) { Text(stringResource(R.string.s_6579a5ba64)) }
            } else {
                TextButton(onClick = HttpService::startLanSession) { Text(app.getString(R.string.s_f989d9d7f9)) }
            }
            Text(stringResource(R.string.s_00e778c519), style = MaterialTheme.typography.titleSmall)
            RemoteScope.entries.forEach { scope ->
                TextSwitch(
                    title = remoteScopeLabel(scope),
                    subtitle = if (scope in setOf(RemoteScope.SERVER_INFO, RemoteScope.SNAPSHOT_LIST)) app.getString(R.string.s_0748ac7579) else app.getString(R.string.s_da3b95b64a),
                    checked = scope in remoteSession.enabledScopes,
                    onCheckedChange = { HttpService.setRemoteScope(scope, it) },
                )
            }
        }
    }
}

@Composable
private fun AdvancedHttpSettingsContent(
    store: SettingsStore,
    showEditPortDlg: MutableState<Boolean>,
    showHttpSettingDlg: MutableState<Boolean>,
) {
    Column {
        SettingItem(
            title = stringResource(R.string.s_6f77ee7c5c),
            subtitle = store.httpServerPort.toString(),
            imageVector = PerfIcon.Edit,
            onClickLabel = stringResource(R.string.s_07a62b1e96),
            onClick = { showHttpSettingDlg.value = false; showEditPortDlg.value = true },
        )
        TextSwitch(
            title = stringResource(R.string.s_6b582fbb9d),
            subtitle = stringResource(R.string.s_97424615a7),
            checked = store.autoClearMemorySubs,
            onCheckedChange = { storeFlow.update { it.copy(autoClearMemorySubs = !it.autoClearMemorySubs) } },
        )
        val cleartextOrigins by CleartextOriginAuthorizations.originsFlow.collectAsStateWithLifecycle()
        if (cleartextOrigins.isNotEmpty()) {
            Text(stringResource(R.string.s_d2a4e522d4), style = MaterialTheme.typography.titleSmall)
            cleartextOrigins.sorted().forEach { origin ->
                SettingItem(
                    title = origin,
                    subtitle = app.getString(R.string.s_7cce6f6775),
                    imageVector = PerfIcon.Delete,
                    onClickLabel = app.getString(R.string.s_8bba24fe03),
                    onClick = { CleartextOriginAuthorizations.revoke(origin) },
                )
            }
        }
    }
}

@Composable
private fun AdvancedSnapshotSection(
    context: MainActivity,
    mainVm: MainViewModel,
    vm: AdvancedVm,
    store: SettingsStore,
    showCaptureScreenshotDlg: MutableState<Boolean>,
) {
    Text(stringResource(R.string.s_83caf1badc), modifier = Modifier.titleItemPadding(), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
    SettingItem(title = stringResource(R.string.s_26c9e586fc), subtitle = stringResource(R.string.s_8eddb6bd87), onClick = { mainVm.navigatePage(SnapshotPageRoute) })
    AdvancedSnapshotServiceControls(context, vm)
    TextSwitch(
        title = stringResource(R.string.s_97f98cd922),
        subtitle = stringResource(R.string.s_7790dc931f),
        checked = store.captureVolumeChange,
        onCheckedChange = { storeFlow.value = store.copy(captureVolumeChange = it) },
    )
    TextSwitch(
        title = stringResource(R.string.s_ee5db675e1),
        subtitle = stringResource(R.string.s_492729f7f9),
        checked = store.captureScreenshot,
        suffixIcon = {
            PerfCustomIconButton(
                size = 32.dp,
                iconSize = 20.dp,
                onClickLabel = app.getString(R.string.s_c41137ca16),
                onClick = throttle { showCaptureScreenshotDlg.value = true },
                id = R.drawable.ic_page_info,
                contentDescription = app.getString(R.string.s_bd7b6c1ed1),
            )
        },
        onCheckedChange = {
            storeFlow.value = store.copy(captureScreenshot = it)
            if (it && store.screenshotTargetAppId.isEmpty() || store.screenshotEventSelector.isEmpty()) {
                toast(app.getString(R.string.s_c456ae2487))
            }
        },
    )
    TextSwitch(
        title = stringResource(R.string.s_c7dddf757a),
        subtitle = stringResource(R.string.s_37fbc765c6),
        checked = store.hideSnapshotStatusBar,
        onCheckedChange = { storeFlow.value = store.copy(hideSnapshotStatusBar = it) },
    )
    TextSwitch(
        title = stringResource(R.string.s_108a9199f2),
        subtitle = stringResource(R.string.s_24feb0f040),
        checked = store.showSaveSnapshotToast,
        onCheckedChange = { storeFlow.value = store.copy(showSaveSnapshotToast = it) },
    )
    SettingItem(
        title = stringResource(R.string.s_ac245bfc80),
        subtitle = stringResource(R.string.s_58a79cef99),
        suffix = "获取教程",
        suffixUnderline = true,
        onSuffixClick = { mainVm.navigateWebPage(ShortUrlSet.URL1) },
        imageVector = PerfIcon.Edit,
        onClick = { mainVm.showEditCookieDlgFlow.value = true },
    )
}

@Composable
private fun AdvancedSnapshotServiceControls(context: MainActivity, vm: AdvancedVm) {
    if (!AndroidTarget.R) {
        val screenshotRunning by ScreenshotService.isRunning.collectAsStateWithLifecycle()
        TextSwitch(
            title = stringResource(R.string.s_df95c4025b),
            subtitle = stringResource(R.string.s_0933e86c0e),
            checked = screenshotRunning,
            onCheckedChange = vm.viewModelScope.launchAsFn<Boolean> {
                if (it) {
                    requiredPermission(context, notificationState)
                    val mediaProjectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                    val activityResult = context.launcher.launchForResult(mediaProjectionManager.createScreenCaptureIntent())
                    if (activityResult.resultCode == Activity.RESULT_OK && activityResult.data != null) {
                        ScreenshotService.start(intent = activityResult.data!!)
                    }
                } else {
                    ScreenshotService.stop()
                }
            },
        )
    }
    TextSwitch(
        title = stringResource(R.string.s_addb3c2ba2),
        subtitle = stringResource(R.string.s_ef5f9af603),
        checked = ButtonService.isRunning.collectAsStateWithLifecycle().value,
        onCheckedChange = vm.viewModelScope.launchAsFn<Boolean> {
            if (it) {
                requiredPermission(context, foregroundServiceSpecialUseState)
                requiredPermission(context, notificationState)
                requiredPermission(context, canDrawOverlaysState)
                ButtonService.start()
            } else {
                ButtonService.stop()
            }
        },
    )
}

@Composable
private fun AdvancedLogSection(context: MainActivity, mainVm: MainViewModel, vm: AdvancedVm) {
    Text(stringResource(R.string.s_4de50894b8), modifier = Modifier.titleItemPadding(), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
    SettingItem(title = stringResource(R.string.s_48ff47e21f), subtitle = stringResource(R.string.s_3e5e447fd3), onClick = { mainVm.navigatePage(ActivityLogRoute) })
    TextSwitch(
        title = stringResource(R.string.s_fcfcb10e4c),
        subtitle = stringResource(R.string.s_6c572506c2),
        checked = ActivityService.isRunning.collectAsStateWithLifecycle().value,
        onCheckedChange = vm.viewModelScope.launchAsFn<Boolean> {
            if (it) {
                requiredPermission(context, foregroundServiceSpecialUseState)
                requiredPermission(context, notificationState)
                requiredPermission(context, canDrawOverlaysState)
                ActivityService.start()
            } else {
                ActivityService.stop()
            }
        },
    )
    SettingItem(title = stringResource(R.string.s_12b64fb2df), subtitle = stringResource(R.string.s_69dc314d81), onClick = { mainVm.navigatePage(A11yEventLogRoute) })
    TextSwitch(
        title = stringResource(R.string.s_25af58e687),
        subtitle = stringResource(R.string.s_8d864071da),
        checked = EventService.isRunning.collectAsStateWithLifecycle().value,
        onCheckedChange = vm.viewModelScope.launchAsFn<Boolean> {
            if (it) {
                requiredPermission(context, foregroundServiceSpecialUseState)
                requiredPermission(context, notificationState)
                requiredPermission(context, canDrawOverlaysState)
                EventService.start()
            } else {
                EventService.stop()
            }
        },
    )
}
