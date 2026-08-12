@file:JvmName("AdvancedSections21")

package li.songe.gkd.sdp.ui

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
import li.songe.gkd.sdp.R
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
import li.songe.gkd.sdp.util.throttle
import li.songe.gkd.sdp.util.toast
import androidx.compose.ui.res.stringResource

internal data class AdvancedPageCallbacks(
    val onBack: () -> Unit,
    val onOpenShizukuState: () -> Unit,
    val onDismissShizukuDialog: () -> Unit,
    val onRequestShizuku: () -> Unit,
    val onToggleShizuku: (Boolean) -> Unit,
    val onOpenWeb: (String) -> Unit,
    val onToggleHttpServer: (Boolean) -> Unit,
    val onToggleHttpSetting: () -> Unit,
    val onOpenEditPortDialog: () -> Unit,
    val onDismissEditPortDialog: () -> Unit,
    val onApplyPort: (Int) -> Unit,
    val onUpdateSettings: (SettingsStore) -> Unit,
    val onRevokeCleartextOrigin: (String) -> Unit,
    val onNavigateSnapshotPage: () -> Unit,
    val onOpenCaptureScreenshotDialog: () -> Unit,
    val onDismissCaptureScreenshotDialog: () -> Unit,
    val onApplyCaptureScreenshot: (String, String) -> Unit,
    val onOpenCaptureHelp: () -> Unit,
    val onOpenCookieDialog: () -> Unit,
    val onToggleCaptureScreenshot: (Boolean) -> Unit,
    val onToggleScreenshotService: (Boolean) -> Unit,
    val onToggleButtonService: (Boolean) -> Unit,
    val onToggleActivityService: (Boolean) -> Unit,
    val onToggleEventService: (Boolean) -> Unit,
    val onNavigateActivityLog: () -> Unit,
    val onNavigateA11yEventLog: () -> Unit,
)

@androidx.annotation.StringRes
internal fun remoteScopeLabelRes(scope: RemoteScope): Int = when (scope) {
    RemoteScope.SERVER_INFO -> R.string.advanced_remote_scope_server_info
    RemoteScope.SNAPSHOT_LIST -> R.string.advanced_remote_scope_snapshot_list
    RemoteScope.VIEW_SNAPSHOT -> R.string.advanced_remote_scope_view_snapshot
    RemoteScope.CAPTURE_SNAPSHOT -> R.string.advanced_remote_scope_capture_snapshot
    RemoteScope.DELETE_SNAPSHOT -> R.string.advanced_remote_scope_delete_snapshot
    RemoteScope.UPDATE_SUBSCRIPTION -> R.string.advanced_remote_scope_update_subscription
    RemoteScope.EXEC_SELECTOR -> R.string.advanced_remote_scope_exec_selector
}

@Composable
internal fun AdvancedPageContent(
    uiState: AdvancedUiState,
    callbacks: AdvancedPageCallbacks,
) {
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            PerfTopAppBar(
                scrollBehavior = scrollBehavior,
                navigationIcon = { PerfIconButton(imageVector = PerfIcon.ArrowBack, onClick = callbacks.onBack) },
                title = { Text(li.songe.gkd.sdp.app.getString(R.string.s_dd07e641ca)) },
            )
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(contentPadding),
        ) {
            AdvancedShizukuSection(uiState, callbacks)
            AdvancedHttpSection(uiState, callbacks)
            AdvancedSnapshotSection(uiState, callbacks)
            AdvancedLogSection(callbacks)
            Spacer(modifier = Modifier.height(EmptyHeight))
        }
    }
}

@Composable
private fun AdvancedShizukuSection(
    uiState: AdvancedUiState,
    callbacks: AdvancedPageCallbacks,
) {
    Row(
        modifier = Modifier.fillMaxWidth().titleItemPadding(showTop = false),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(stringResource(R.string.s_8ecb7f0457), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        PerfIcon(
            modifier = Modifier
                .clip(MaterialTheme.shapes.extraSmall)
                .clickable(onClickLabel = stringResource(R.string.advanced_open_shizuku_state), onClick = throttle { callbacks.onOpenShizukuState() })
                .iconTextSize(textStyle = MaterialTheme.typography.titleSmall),
            imageVector = PerfIcon.Api,
            tint = MaterialTheme.colorScheme.primary,
            contentDescription = stringResource(R.string.advanced_shizuku_state),
        )
    }
    val shizukuGranted by shizukuGrantedState.stateFlow.collectAsStateWithLifecycle()
    AnimatedVisibility(uiState.store.enableShizuku && !shizukuGranted) {
        AuthCard(
            title = stringResource(R.string.advanced_unauthorized),
            subtitle = stringResource(R.string.advanced_authorize_hint),
            onAuthClick = callbacks.onRequestShizuku,
        )
    }
    TextSwitch(
        title = stringResource(R.string.s_6b0ad26edf),
        subtitle = stringResource(R.string.s_a3e561c7c6),
        suffix = li.songe.gkd.sdp.app.getString(R.string.learn_more),
        suffixUnderline = true,
        onSuffixClick = { callbacks.onOpenWeb(ShortUrlSet.URL14) },
        checked = uiState.store.enableShizuku,
        suffixIcon = {
            if (updateBinderMutex.state.collectAsStateWithLifecycle().value) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
            }
        },
        onCheckedChange = callbacks.onToggleShizuku,
        onClick = null,
    )
}

@Composable
private fun AdvancedHttpSection(
    uiState: AdvancedUiState,
    callbacks: AdvancedPageCallbacks,
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
                onClickLabel = li.songe.gkd.sdp.app.getString(R.string.s_66b10cf5e5),
                onClick = callbacks.onToggleHttpSetting,
                id = R.drawable.ic_page_info,
                contentDescription = li.songe.gkd.sdp.app.getString(R.string.s_c5f42f5a0f),
                tint = if (uiState.showHttpSettingDlg) MaterialTheme.colorScheme.primary else LocalContentColor.current,
            )
        },
        checked = httpServerRunning,
        onCheckedChange = callbacks.onToggleHttpServer,
    )
    AnimatedVisibility(httpServerRunning) {
        AdvancedHttpRunningContent(uiState.store, localNetworkIps, remoteSession)
    }
    AnimatedVisibility(uiState.showHttpSettingDlg) {
        AdvancedHttpSettingsContent(uiState.store, callbacks)
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
                    stringResource(R.string.advanced_listen_local)
                } else {
                    val remainingMinutes = remoteSession.accessExpiresAtMillis
                        ?.let { ((it - System.currentTimeMillis()).coerceAtLeast(0) + 59_999) / 60_000 }
                        ?: 0
                    stringResource(R.string.advanced_listen_lan, remainingMinutes)
                },
            )
            remoteSession.pairingCode?.let { Text(li.songe.gkd.sdp.app.getString(R.string.s_cc0dd8de47, (it).toString())) }
            remoteSession.clientSummary?.let { Text(li.songe.gkd.sdp.app.getString(R.string.s_759c95ad54, (it).toString())) }
            Row {
                val localUrl = "http://127.0.0.1:${store.httpServerPort}"
                Text(
                    text = localUrl,
                    color = MaterialTheme.colorScheme.primary,
                    style = LocalTextStyle.current.copy(textDecoration = TextDecoration.Underline),
                    modifier = Modifier.clickable(onClick = throttle {
                        copyText(localUrl)
                        toast(li.songe.gkd.sdp.app.getString(R.string.s_6c0be6e7b0))
                    }),
                )
                Spacer(modifier = Modifier.width(2.dp))
                Text(li.songe.gkd.sdp.app.getString(R.string.s_93794c92ab))
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
                            toast(li.songe.gkd.sdp.app.getString(R.string.s_7774d11343))
                        }),
                    )
                }
                TextButton(onClick = HttpService::disconnectLanSession) { Text(li.songe.gkd.sdp.app.getString(R.string.s_6579a5ba64)) }
            } else {
                TextButton(onClick = HttpService::startLanSession) { Text(li.songe.gkd.sdp.app.getString(R.string.s_f989d9d7f9)) }
            }
            Text(li.songe.gkd.sdp.app.getString(R.string.s_00e778c519), style = MaterialTheme.typography.titleSmall)
            RemoteScope.entries.forEach { scope ->
                TextSwitch(
                    title = stringResource(remoteScopeLabelRes(scope)),
                    subtitle = if (scope in setOf(RemoteScope.SERVER_INFO, RemoteScope.SNAPSHOT_LIST)) li.songe.gkd.sdp.app.getString(R.string.s_0748ac7579) else li.songe.gkd.sdp.app.getString(R.string.s_da3b95b64a),
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
    callbacks: AdvancedPageCallbacks,
) {
    Column {
        SettingItem(
            title = stringResource(R.string.s_6f77ee7c5c),
            subtitle = store.httpServerPort.toString(),
            imageVector = PerfIcon.Edit,
            onClickLabel = stringResource(R.string.s_07a62b1e96),
            onClick = callbacks.onOpenEditPortDialog,
        )
        TextSwitch(
            title = stringResource(R.string.s_6b582fbb9d),
            subtitle = stringResource(R.string.s_97424615a7),
            checked = store.autoClearMemorySubs,
            onCheckedChange = { callbacks.onUpdateSettings(store.copy(autoClearMemorySubs = it)) },
        )
        val cleartextOrigins by CleartextOriginAuthorizations.originsFlow.collectAsStateWithLifecycle()
        if (cleartextOrigins.isNotEmpty()) {
            Text(stringResource(R.string.s_d2a4e522d4), style = MaterialTheme.typography.titleSmall)
            cleartextOrigins.sorted().forEach { origin ->
                SettingItem(
                    title = origin,
                    subtitle = li.songe.gkd.sdp.app.getString(R.string.s_7cce6f6775),
                    imageVector = PerfIcon.Delete,
                    onClickLabel = li.songe.gkd.sdp.app.getString(R.string.s_8bba24fe03),
                    onClick = { callbacks.onRevokeCleartextOrigin(origin) },
                )
            }
        }
    }
}

@Composable
private fun AdvancedSnapshotSection(
    uiState: AdvancedUiState,
    callbacks: AdvancedPageCallbacks,
) {
    val store = uiState.store
    Text(stringResource(R.string.s_83caf1badc), modifier = Modifier.titleItemPadding(), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
    SettingItem(title = stringResource(R.string.s_26c9e586fc), subtitle = stringResource(R.string.s_8eddb6bd87), onClick = callbacks.onNavigateSnapshotPage)
    AdvancedSnapshotServiceControls(callbacks)
    TextSwitch(
        title = stringResource(R.string.s_97f98cd922),
        subtitle = stringResource(R.string.s_7790dc931f),
        checked = store.captureVolumeChange,
        onCheckedChange = { callbacks.onUpdateSettings(store.copy(captureVolumeChange = it)) },
    )
    TextSwitch(
        title = stringResource(R.string.s_ee5db675e1),
        subtitle = stringResource(R.string.s_492729f7f9),
        checked = store.captureScreenshot,
        suffixIcon = {
            PerfCustomIconButton(
                size = 32.dp,
                iconSize = 20.dp,
                onClickLabel = li.songe.gkd.sdp.app.getString(R.string.s_c41137ca16),
                onClick = throttle { callbacks.onOpenCaptureScreenshotDialog() },
                id = R.drawable.ic_page_info,
                contentDescription = li.songe.gkd.sdp.app.getString(R.string.s_bd7b6c1ed1),
            )
        },
        onCheckedChange = callbacks.onToggleCaptureScreenshot,
    )
    TextSwitch(
        title = stringResource(R.string.s_c7dddf757a),
        subtitle = stringResource(R.string.s_37fbc765c6),
        checked = store.hideSnapshotStatusBar,
        onCheckedChange = { callbacks.onUpdateSettings(store.copy(hideSnapshotStatusBar = it)) },
    )
    TextSwitch(
        title = stringResource(R.string.s_108a9199f2),
        subtitle = stringResource(R.string.s_24feb0f040),
        checked = store.showSaveSnapshotToast,
        onCheckedChange = { callbacks.onUpdateSettings(store.copy(showSaveSnapshotToast = it)) },
    )
    SettingItem(
        title = stringResource(R.string.s_ac245bfc80),
        subtitle = stringResource(R.string.s_58a79cef99),
        suffix = li.songe.gkd.sdp.app.getString(R.string.get_tutorial),
        suffixUnderline = true,
        onSuffixClick = { callbacks.onOpenWeb(ShortUrlSet.URL1) },
        imageVector = PerfIcon.Edit,
        onClick = callbacks.onOpenCookieDialog,
    )
}

@Composable
private fun AdvancedSnapshotServiceControls(callbacks: AdvancedPageCallbacks) {
    if (!AndroidTarget.R) {
        val screenshotRunning by ScreenshotService.isRunning.collectAsStateWithLifecycle()
        TextSwitch(
            title = stringResource(R.string.s_df95c4025b),
            subtitle = stringResource(R.string.s_0933e86c0e),
            checked = screenshotRunning,
            onCheckedChange = callbacks.onToggleScreenshotService,
        )
    }
    TextSwitch(
        title = stringResource(R.string.s_addb3c2ba2),
        subtitle = stringResource(R.string.s_ef5f9af603),
        checked = ButtonService.isRunning.collectAsStateWithLifecycle().value,
        onCheckedChange = callbacks.onToggleButtonService,
    )
}

@Composable
private fun AdvancedLogSection(callbacks: AdvancedPageCallbacks) {
    Text(stringResource(R.string.s_4de50894b8), modifier = Modifier.titleItemPadding(), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
    SettingItem(title = stringResource(R.string.s_48ff47e21f), subtitle = stringResource(R.string.s_3e5e447fd3), onClick = callbacks.onNavigateActivityLog)
    TextSwitch(
        title = stringResource(R.string.s_fcfcb10e4c),
        subtitle = stringResource(R.string.s_6c572506c2),
        checked = ActivityService.isRunning.collectAsStateWithLifecycle().value,
        onCheckedChange = callbacks.onToggleActivityService,
    )
    SettingItem(title = stringResource(R.string.s_12b64fb2df), subtitle = stringResource(R.string.s_69dc314d81), onClick = callbacks.onNavigateA11yEventLog)
    TextSwitch(
        title = stringResource(R.string.s_25af58e687),
        subtitle = stringResource(R.string.s_8d864071da),
        checked = EventService.isRunning.collectAsStateWithLifecycle().value,
        onCheckedChange = callbacks.onToggleEventService,
    )
}
