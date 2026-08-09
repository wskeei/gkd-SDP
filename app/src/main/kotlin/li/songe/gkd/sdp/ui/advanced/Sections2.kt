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
                title = { Text("高级设置") },
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
        Text("Shizuku", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        PerfIcon(
            modifier = Modifier
                .clip(MaterialTheme.shapes.extraSmall)
                .clickable(onClickLabel = "打开 Shizuku 状态弹窗", onClick = throttle { showShizukuState.value = true })
                .iconTextSize(textStyle = MaterialTheme.typography.titleSmall),
            imageVector = PerfIcon.Api,
            tint = MaterialTheme.colorScheme.primary,
            contentDescription = "Shizuku 状态",
        )
    }
    val shizukuGranted by shizukuGrantedState.stateFlow.collectAsStateWithLifecycle()
    AnimatedVisibility(store.enableShizuku && !shizukuGranted) {
        AuthCard(title = "未授权", subtitle = "点击授权以优化体验", onAuthClick = mainVm::requestShizuku)
    }
    TextSwitch(
        title = "启用优化",
        subtitle = "提升权限优化体验",
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
    Text("HTTP", modifier = Modifier.titleItemPadding(), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
    TextSwitch(
        title = "HTTP服务",
        subtitle = "在浏览器下连接调试",
        suffixIcon = {
            PerfCustomIconButton(
                size = 32.dp,
                iconSize = 20.dp,
                onClickLabel = "打开HTTP设置弹窗",
                onClick = { showHttpSettingDlg.value = !showHttpSettingDlg.value },
                id = R.drawable.ic_page_info,
                contentDescription = "HTTP设置",
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
            remoteSession.pairingCode?.let { Text("一次性配对码：$it（60 秒内有效）") }
            remoteSession.clientSummary?.let { Text("已连接客户端：$it") }
            Row {
                val localUrl = "http://127.0.0.1:${store.httpServerPort}"
                Text(
                    text = localUrl,
                    color = MaterialTheme.colorScheme.primary,
                    style = LocalTextStyle.current.copy(textDecoration = TextDecoration.Underline),
                    modifier = Modifier.clickable(onClick = throttle {
                        copyText(localUrl)
                        toast("已复制本机地址")
                    }),
                )
                Spacer(modifier = Modifier.width(2.dp))
                Text("点击复制")
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
                            toast("已复制局域网地址")
                        }),
                    )
                }
                TextButton(onClick = HttpService::disconnectLanSession) { Text("立即断开局域网会话") }
            } else {
                TextButton(onClick = HttpService::startLanSession) { Text("开启 15 分钟局域网调试") }
            }
            Text("授权范围", style = MaterialTheme.typography.titleSmall)
            RemoteScope.entries.forEach { scope ->
                TextSwitch(
                    title = remoteScopeLabel(scope),
                    subtitle = if (scope in setOf(RemoteScope.SERVER_INFO, RemoteScope.SNAPSHOT_LIST)) "基础只读范围" else "敏感范围，默认关闭",
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
            title = "服务端口",
            subtitle = store.httpServerPort.toString(),
            imageVector = PerfIcon.Edit,
            onClickLabel = "编辑服务端口",
            onClick = { showHttpSettingDlg.value = false; showEditPortDlg.value = true },
        )
        TextSwitch(
            title = "清除订阅",
            subtitle = "关闭服务时删除内存订阅",
            checked = store.autoClearMemorySubs,
            onCheckedChange = { storeFlow.update { it.copy(autoClearMemorySubs = !it.autoClearMemorySubs) } },
        )
        val cleartextOrigins by CleartextOriginAuthorizations.originsFlow.collectAsStateWithLifecycle()
        if (cleartextOrigins.isNotEmpty()) {
            Text("已授权明文来源", style = MaterialTheme.typography.titleSmall)
            cleartextOrigins.sorted().forEach { origin ->
                SettingItem(
                    title = origin,
                    subtitle = "点击撤销；后续更新请求会立即拒绝",
                    imageVector = PerfIcon.Delete,
                    onClickLabel = "撤销明文来源授权",
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
    Text("快照", modifier = Modifier.titleItemPadding(), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
    SettingItem(title = "快照记录", subtitle = "应用界面节点信息及截图", onClick = { mainVm.navigatePage(SnapshotPageRoute) })
    AdvancedSnapshotServiceControls(context, vm)
    TextSwitch(
        title = "音量快照",
        subtitle = "音量变化时保存快照",
        checked = store.captureVolumeChange,
        onCheckedChange = { storeFlow.value = store.copy(captureVolumeChange = it) },
    )
    TextSwitch(
        title = "截屏快照",
        subtitle = "截屏时保存快照",
        checked = store.captureScreenshot,
        suffixIcon = {
            PerfCustomIconButton(
                size = 32.dp,
                iconSize = 20.dp,
                onClickLabel = "打开配置截屏快照弹窗",
                onClick = throttle { showCaptureScreenshotDlg.value = true },
                id = R.drawable.ic_page_info,
                contentDescription = "截屏快照设置",
            )
        },
        onCheckedChange = {
            storeFlow.value = store.copy(captureScreenshot = it)
            if (it && store.screenshotTargetAppId.isEmpty() || store.screenshotEventSelector.isEmpty()) {
                toast("请配置目标应用和特征事件选择器")
            }
        },
    )
    TextSwitch(
        title = "隐藏状态栏",
        subtitle = "隐藏快照截图状态栏",
        checked = store.hideSnapshotStatusBar,
        onCheckedChange = { storeFlow.value = store.copy(hideSnapshotStatusBar = it) },
    )
    TextSwitch(
        title = "保存提示",
        subtitle = "提示「正在保存快照」",
        checked = store.showSaveSnapshotToast,
        onCheckedChange = { storeFlow.value = store.copy(showSaveSnapshotToast = it) },
    )
    SettingItem(
        title = "Github Cookie",
        subtitle = "生成快照/日志链接",
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
            title = "截屏服务",
            subtitle = "生成快照需要获取屏幕截图",
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
        title = "快照按钮",
        subtitle = "显示按钮点击保存快照",
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
    Text("日志", modifier = Modifier.titleItemPadding(), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
    SettingItem(title = "界面日志", subtitle = "界面切换日志", onClick = { mainVm.navigatePage(ActivityLogRoute) })
    TextSwitch(
        title = "界面服务",
        subtitle = "显示当前界面信息",
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
    SettingItem(title = "事件日志", subtitle = "无障碍事件日志", onClick = { mainVm.navigatePage(A11yEventLogRoute) })
    TextSwitch(
        title = "事件服务",
        subtitle = "显示无障碍事件",
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
