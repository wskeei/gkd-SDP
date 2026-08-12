package li.songe.gkd.sdp

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.dylanc.activityresult.launcher.PickContentLauncher
import com.dylanc.activityresult.launcher.StartActivityLauncher
import com.dylanc.activityresult.launcher.launchForResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import li.songe.gkd.sdp.a11y.topActivityFlow
import li.songe.gkd.sdp.a11y.updateSystemDefaultAppId
import li.songe.gkd.sdp.a11y.updateTopActivity
import li.songe.gkd.sdp.diagnostics.DiagnosticLogger
import li.songe.gkd.sdp.permission.AuthDialog
import li.songe.gkd.sdp.permission.canDrawOverlaysState
import li.songe.gkd.sdp.permission.updatePermissionState
import li.songe.gkd.sdp.performance.AppDrawReporter
import li.songe.gkd.sdp.service.A11yService
import li.songe.gkd.sdp.service.AccessibilityGuardRuntime
import li.songe.gkd.sdp.service.StatusService
import li.songe.gkd.sdp.service.fixRestartAutomatorService
import li.songe.gkd.sdp.service.updateTopTaskAppId
import li.songe.gkd.sdp.shizuku.automationRegisteredExceptionFlow
import li.songe.gkd.sdp.shizuku.shizukuContextFlow
import li.songe.gkd.sdp.store.storeFlow
import li.songe.gkd.sdp.ui.A11YScopeAppListRoute
import li.songe.gkd.sdp.ui.A11yEventLogPage
import li.songe.gkd.sdp.ui.A11yEventLogRoute
import li.songe.gkd.sdp.ui.A11yScopeAppListPage
import li.songe.gkd.sdp.ui.AboutPage
import li.songe.gkd.sdp.ui.AboutRoute
import li.songe.gkd.sdp.ui.ActionLogPage
import li.songe.gkd.sdp.ui.ActionLogRoute
import li.songe.gkd.sdp.ui.ActivityLogPage
import li.songe.gkd.sdp.ui.ActivityLogRoute
import li.songe.gkd.sdp.ui.AdvancedPage
import li.songe.gkd.sdp.ui.AdvancedPageRoute
import li.songe.gkd.sdp.ui.AppConfigPage
import li.songe.gkd.sdp.ui.AppConfigRoute
import li.songe.gkd.sdp.ui.AppBlockerPage
import li.songe.gkd.sdp.ui.AppBlockerRoute
import li.songe.gkd.sdp.ui.AppInstallMonitorPage
import li.songe.gkd.sdp.ui.AppInstallMonitorRoute
import li.songe.gkd.sdp.ui.AppOpsAllowPage
import li.songe.gkd.sdp.ui.AppOpsAllowRoute
import li.songe.gkd.sdp.ui.capability.CapabilityCenterRoute
import li.songe.gkd.sdp.ui.capability.CapabilityCenterScreen
import li.songe.gkd.sdp.ui.AuthA11yPage
import li.songe.gkd.sdp.ui.AuthA11yRoute
import li.songe.gkd.sdp.ui.BlockA11yAppListPage
import li.songe.gkd.sdp.ui.BlockA11yAppListRoute
import li.songe.gkd.sdp.ui.CrashReportPage
import li.songe.gkd.sdp.ui.CrashReportRoute
import li.songe.gkd.sdp.ui.EditBlockAppListPage
import li.songe.gkd.sdp.ui.EditBlockAppListRoute
import li.songe.gkd.sdp.ui.FocusLockPage
import li.songe.gkd.sdp.ui.FocusLockRoute
import li.songe.gkd.sdp.ui.FocusModePage
import li.songe.gkd.sdp.ui.FocusModeRoute
import li.songe.gkd.sdp.ui.ImagePreviewPage
import li.songe.gkd.sdp.ui.ImagePreviewRoute
import li.songe.gkd.sdp.ui.SlowGroupPage
import li.songe.gkd.sdp.ui.SlowGroupRoute
import li.songe.gkd.sdp.ui.SnapshotPage
import li.songe.gkd.sdp.ui.SnapshotPageRoute
import li.songe.gkd.sdp.ui.SubsAppGroupListPage
import li.songe.gkd.sdp.ui.SubsAppGroupListRoute
import li.songe.gkd.sdp.ui.SubsAppListPage
import li.songe.gkd.sdp.ui.SubsAppListRoute
import li.songe.gkd.sdp.ui.SubsCategoryGroupPage
import li.songe.gkd.sdp.ui.SubsCategoryGroupRoute
import li.songe.gkd.sdp.ui.SubsCategoryPage
import li.songe.gkd.sdp.ui.SubsCategoryRoute
import li.songe.gkd.sdp.ui.SubsGlobalGroupExcludePage
import li.songe.gkd.sdp.ui.SubsGlobalGroupExcludeRoute
import li.songe.gkd.sdp.ui.SubsGlobalGroupListPage
import li.songe.gkd.sdp.ui.SubsGlobalGroupListRoute
import li.songe.gkd.sdp.ui.UpsertRuleGroupPage
import li.songe.gkd.sdp.ui.UpsertRuleGroupRoute
import li.songe.gkd.sdp.ui.UrlBlockerRoute
import li.songe.gkd.sdp.ui.UrlBlockRoute
import li.songe.gkd.sdp.ui.UsageGuardPage
import li.songe.gkd.sdp.ui.UsageGuardRoute
import li.songe.gkd.sdp.ui.UsageGuardReviewPage
import li.songe.gkd.sdp.ui.UsageGuardReviewRoute
import li.songe.gkd.sdp.ui.privacy.PrivacyDataScreen
import li.songe.gkd.sdp.ui.privacy.PrivacyDataRoute
import li.songe.gkd.sdp.ui.WebViewPage
import li.songe.gkd.sdp.ui.WebViewRoute
import li.songe.gkd.sdp.ui.component.BuildDialog
import li.songe.gkd.sdp.ui.component.PerfIcon
import li.songe.gkd.sdp.ui.component.ShareLogDlg
import li.songe.gkd.sdp.ui.component.SubsSheet
import li.songe.gkd.sdp.ui.component.TermsAcceptDialog
import li.songe.gkd.sdp.ui.component.TextDialog
import li.songe.gkd.sdp.ui.home.HomePage
import li.songe.gkd.sdp.ui.home.HomeRoute
import li.songe.gkd.sdp.ui.share.FixedWindowInsets
import li.songe.gkd.sdp.ui.share.appTopBarWindowInsets
import li.songe.gkd.sdp.ui.share.LocalDrawReporter
import li.songe.gkd.sdp.ui.share.LocalMainViewModel
import li.songe.gkd.sdp.ui.style.AppTheme
import li.songe.gkd.sdp.util.AndroidTarget
import li.songe.gkd.sdp.util.EditGithubCookieDlg
import li.songe.gkd.sdp.util.KeyboardUtils
import li.songe.gkd.sdp.util.LogUtils
import li.songe.gkd.sdp.util.ShortUrlSet
import li.songe.gkd.sdp.util.appInfoMapFlow
import li.songe.gkd.sdp.util.componentName
import li.songe.gkd.sdp.util.copyText
import li.songe.gkd.sdp.util.fixSomeProblems
import li.songe.gkd.sdp.util.launchTry
import li.songe.gkd.sdp.util.mapState
import li.songe.gkd.sdp.util.openApp
import li.songe.gkd.sdp.util.openUri
import li.songe.gkd.sdp.util.shizukuAppId
import li.songe.gkd.sdp.util.throttle
import li.songe.gkd.sdp.util.toast
import kotlin.concurrent.Volatile
import kotlin.reflect.jvm.jvmName
import androidx.compose.ui.res.stringResource
import li.songe.gkd.sdp.R

class MainActivity : ComponentActivity() {
    val startTime = System.currentTimeMillis()
    val mainVm by viewModels<MainViewModel>()
    private val drawReporter = AppDrawReporter { reportFullyDrawn() }
    val launcher by lazy { StartActivityLauncher(this) }
    val pickContentLauncher by lazy { PickContentLauncher(this) }

    val imeFullHiddenFlow = MutableStateFlow(true)
    val imePlayingFlow = MutableStateFlow(false)

    private val imeVisible: Boolean
        get() = ViewCompat.getRootWindowInsets(window.decorView)
            ?.isVisible(WindowInsetsCompat.Type.ime()) == true  // fix #1315

    private fun watchKeyboardVisible() {
        if (AndroidTarget.R) {
            ViewCompat.setWindowInsetsAnimationCallback(
                window.decorView,
                object : WindowInsetsAnimationCompat.Callback(DISPATCH_MODE_CONTINUE_ON_SUBTREE) {
                    override fun onStart(
                        animation: WindowInsetsAnimationCompat,
                        bounds: WindowInsetsAnimationCompat.BoundsCompat
                    ): WindowInsetsAnimationCompat.BoundsCompat {
                        imePlayingFlow.update { imeVisible }
                        return super.onStart(animation, bounds)
                    }

                    override fun onProgress(
                        insets: WindowInsetsCompat,
                        runningAnimations: List<WindowInsetsAnimationCompat>
                    ): WindowInsetsCompat {
                        return insets
                    }

                    override fun onEnd(animation: WindowInsetsAnimationCompat) {
                        imeFullHiddenFlow.update { !imeVisible }
                        imePlayingFlow.update { false }
                        super.onEnd(animation)
                    }
                })
        } else {
            KeyboardUtils.registerSoftInputChangedListener(window) { height ->
                // onEnd
                imeFullHiddenFlow.update { height == 0 }
            }
        }
    }

    suspend fun hideSoftInput(): Boolean {
        if (!imeFullHiddenFlow.updateAndGet { !imeVisible }) {
            KeyboardUtils.hideSoftInput(this@MainActivity)
            imeFullHiddenFlow.drop(1).first()
            return true
        }
        return false
    }

    fun justHideSoftInput(): Boolean {
        if (!imeFullHiddenFlow.updateAndGet { !imeVisible }) {
            KeyboardUtils.hideSoftInput(this@MainActivity)
            return true
        }
        return false
    }

    suspend fun pickFile(contentType: String): Uri? {
        val u = launcher.launchForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = contentType
        }).data?.data
        if (u == null) {
            toast(li.songe.gkd.sdp.app.getString(R.string.s_dbb4430dc0))
        }
        return u
    }

    suspend fun createFile(contentType: String, filename: String): Uri? =
        launcher.launchForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = contentType
            putExtra(Intent.EXTRA_TITLE, filename)
        }).data?.data

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        fixSomeProblems()
        super.onCreate(savedInstanceState)
        LogUtils.d()
        mainVm
        launcher
        pickContentLauncher
        lifecycleScope.launch {
            storeFlow.mapState(lifecycleScope) { s -> s.excludeFromRecents }.collect {
                app.activityManager.appTasks.forEach { task ->
                    task.setExcludeFromRecents(it)
                }
            }
        }
        addOnNewIntentListener {
            mainVm.handleIntent(it)
            intent = null
        }
        watchKeyboardVisible()
        StatusService.autoStart()
        if (storeFlow.value.enableBlockA11yAppList) {
            updateTopTaskAppId(META.appId)
        }
        setContent {
            val saveableBackStack = rememberNavBackStack(HomeRoute())
            mainVm.bindBackStack(saveableBackStack)
            val latestInsets = TopAppBarDefaults.windowInsets
            val density = LocalDensity.current
            if (latestInsets.getTop(density) > appTopBarWindowInsets.getTop(density)) {
                appTopBarWindowInsets = FixedWindowInsets(latestInsets)
            }
            CompositionLocalProvider(
                LocalMainViewModel provides mainVm,
                LocalDrawReporter provides drawReporter,
            ) {
                AppTheme {
                    SideEffect {
                        drawReporter.reportInteractiveContent()
                    }
                    NavDisplay(
                        entryDecorators = listOf(
                            rememberSaveableStateHolderNavEntryDecorator(),
                            rememberViewModelStoreNavEntryDecorator(),
                        ),
                        backStack = saveableBackStack,
                        onBack = mainVm::popPage,
                        entryProvider = entryProvider {
                            entry<HomeRoute> { HomePage(it) }
                            entry<AuthA11yRoute> { AuthA11yPage() }
                            entry<CapabilityCenterRoute> { CapabilityCenterScreen(mainVm) }
                            entry<AboutRoute> { AboutPage() }
                            entry<BlockA11yAppListRoute> { BlockA11yAppListPage() }
                            entry<AdvancedPageRoute> { AdvancedPage() }
                            entry<PrivacyDataRoute> { PrivacyDataScreen() }
                            entry<SnapshotPageRoute> { SnapshotPage() }
                            entry<AppOpsAllowRoute> { AppOpsAllowPage() }
                            entry<A11YScopeAppListRoute> { A11yScopeAppListPage() }
                            entry<ActivityLogRoute> { ActivityLogPage() }
                            entry<A11yEventLogRoute> { A11yEventLogPage() }
                            entry<EditBlockAppListRoute> { EditBlockAppListPage() }
                            entry<SlowGroupRoute> { SlowGroupPage() }
                            entry<SubsAppListRoute> { SubsAppListPage(it) }
                            entry<WebViewRoute> { WebViewPage(it) }
                            entry<SubsCategoryRoute> { SubsCategoryPage(it) }
                            entry<SubsGlobalGroupListRoute> { SubsGlobalGroupListPage(it) }
                            entry<SubsGlobalGroupExcludeRoute> { SubsGlobalGroupExcludePage(it) }
                            entry<ActionLogRoute> { ActionLogPage(it) }
                            entry<ImagePreviewRoute> { ImagePreviewPage(it) }
                            entry<UpsertRuleGroupRoute> { UpsertRuleGroupPage(it) }
                            entry<SubsAppGroupListRoute> { SubsAppGroupListPage(it) }
                            entry<AppConfigRoute> { AppConfigPage(it) }
                            entry<CrashReportRoute> { CrashReportPage() }
                            entry<SubsCategoryGroupRoute> { SubsCategoryGroupPage(it) }
                            entry<FocusLockRoute> { FocusLockPage() }
                            entry<FocusModeRoute> { FocusModePage() }
                            entry<UrlBlockRoute> { UrlBlockerRoute() }
                            entry<AppBlockerRoute> { AppBlockerPage() }
                            entry<UsageGuardRoute> { UsageGuardPage() }
                            entry<UsageGuardReviewRoute> { UsageGuardReviewPage() }
                            entry<AppInstallMonitorRoute> { AppInstallMonitorPage() }
                        },
                        transitionSpec = {
                            slideInHorizontally(initialOffsetX = { it }) togetherWith
                                    slideOutHorizontally(targetOffsetX = { -it })
                        },
                        popTransitionSpec = {
                            slideInHorizontally(initialOffsetX = { -it }) togetherWith
                                    slideOutHorizontally(targetOffsetX = { it })
                        },
                        predictivePopTransitionSpec = {
                            slideInHorizontally(initialOffsetX = { -it }) togetherWith
                                    slideOutHorizontally(targetOffsetX = { it })
                        },
                    )
                    if (!mainVm.termsAcceptedFlow.collectAsStateWithLifecycle().value) {
                        TermsAcceptDialog()
                    } else {
                        UiAutomationAlreadyRegisteredDlg()
                        AccessRestrictedSettingsDlg()
                        ShizukuErrorDialog(mainVm.shizukuErrorFlow)
                        AuthDialog(mainVm.authReasonFlow)
                        BuildDialog(mainVm.dialogFlow)
                        mainVm.uploadOptions.ShowDialog()
                        EditGithubCookieDlg()
                        mainVm.updateStatus?.UpgradeDialog()
                        SubsSheet(mainVm, mainVm.sheetSubsIdFlow)
                        mainVm.inputSubsLinkOption.ContentDialog()
                        mainVm.ruleGroupState.Render()
                        TextDialog(mainVm.textFlow)
                        ShareLogDlg(mainVm.showShareLogDlgFlow)
                    }
                }
            }
            LaunchedEffect(null) {
                intent?.let {
                    mainVm.handleIntent(it)
                    intent = null
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        LogUtils.d()
        activityVisibleCountFlow.update { it + 1 }
        AccessibilityGuardRuntime.onAppVisible()
        if (META.isGkdChannel && storeFlow.value.accessibilityGuardEnabled &&
            !canDrawOverlaysState.updateAndGet()
        ) {
            toast(li.songe.gkd.sdp.app.getString(R.string.s_4a6c3f7937))
        }
        if (topActivityFlow.value.appId != META.appId) {
            synchronized(topActivityFlow) {
                updateTopActivity(
                    META.appId,
                    MainActivity::class.jvmName
                )
            }
        }
    }

    var isFirstResume = true
    override fun onResume() {
        super.onResume()
        LogUtils.d()
        if (isFirstResume && startTime - app.startTime < 2000) {
            isFirstResume = false
        } else {
            syncFixState()
        }
    }

    override fun onStop() {
        super.onStop()
        LogUtils.d()
        activityVisibleCountFlow.update { (it - 1).coerceAtLeast(0) }
    }

    override fun onDestroy() {
        super.onDestroy()
        LogUtils.d()
    }
}

val activityVisibleCountFlow = MutableStateFlow(0)
val isActivityVisible get() = activityVisibleCountFlow.value > 0

val activityNavSourceName by lazy { META.appId + ".activity.nav.source" }

fun Activity.navToMainActivity() {
    if (intent != null) {
        val navIntent = Intent(intent)
        navIntent.component = MainActivity::class.componentName
        navIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        navIntent.putExtra(activityNavSourceName, this::class.jvmName)
        startActivity(navIntent)
    }
    finish()
}

private val syncStateMutex = Mutex()
fun syncFixState() {
    appScope.launchTry(Dispatchers.IO) {
        if (syncStateMutex.isLocked) {
            LogUtils.d("syncFixState isLocked")
        }
        syncStateMutex.withLock {
            updateSystemDefaultAppId()
            shizukuContextFlow.value.grantSelf()
            updatePermissionState()
            fixRestartAutomatorService()
        }
    }
}

@Composable
private fun ShizukuErrorDialog(stateFlow: MutableStateFlow<Throwable?>) {
    val state = stateFlow.collectAsStateWithLifecycle().value
    if (state != null) {
        val errorText = remember(state) { DiagnosticLogger.userMessage(state) }
        val appInfoCache = appInfoMapFlow.collectAsStateWithLifecycle().value
        val installed = appInfoCache.contains(shizukuAppId)
        AlertDialog(
            onDismissRequest = { stateFlow.value = null },
            title = { Text(text = stringResource(R.string.s_9c8db95f12)) },
            text = {
                Column {
                    Text(
                        text = if (installed) {
                            stringResource(R.string.s_f08db2ab6e)
                        } else {
                            stringResource(R.string.s_6dc32911b1)
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        SelectionContainer(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .fillMaxWidth()
                        ) {
                            Text(
                                text = errorText,
                                modifier = Modifier
                                    .clip(MaterialTheme.shapes.extraSmall)
                                    .background(MaterialTheme.colorScheme.secondaryContainer)
                                    .padding(8.dp)
                                    .heightIn(max = 400.dp)
                                    .verticalScroll(rememberScrollState()),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        PerfIcon(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .clickable(onClick = throttle {
                                    copyText(errorText)
                                })
                                .padding(4.dp)
                                .size(20.dp),
                            imageVector = PerfIcon.ContentCopy,
                            tint = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.75f),
                        )
                    }
                }
            },
            confirmButton = {
                if (installed) {
                    TextButton(onClick = {
                        stateFlow.value = null
                        openApp(shizukuAppId)
                    }) {
                        Text(text = stringResource(R.string.s_894a72442f))
                    }
                } else {
                    TextButton(onClick = {
                        stateFlow.value = null
                        openUri(ShortUrlSet.URL4)
                    }) {
                        Text(text = stringResource(R.string.s_21654037e2))
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { stateFlow.value = null }) {
                    Text(text = stringResource(R.string.s_dd3760c80a))
                }
            }
        )
    }
}


val accessRestrictedSettingsShowFlow = MutableStateFlow(false)

@Composable
fun AccessRestrictedSettingsDlg() {
    val a11yRunning by A11yService.isRunning.collectAsStateWithLifecycle()
    LaunchedEffect(a11yRunning) {
        if (a11yRunning) {
            accessRestrictedSettingsShowFlow.value = false
        }
    }
    val accessRestrictedSettingsShow by accessRestrictedSettingsShowFlow.collectAsStateWithLifecycle()
    val mainVm = LocalMainViewModel.current
    val isA11yPage = mainVm.topRoute is AuthA11yRoute
    LaunchedEffect(isA11yPage, accessRestrictedSettingsShow) {
        if (isA11yPage && accessRestrictedSettingsShow && !a11yRunning) {
            toast(li.songe.gkd.sdp.app.getString(R.string.s_a0995a1cf8))
            accessRestrictedSettingsShowFlow.value = false
        }
    }
    if (accessRestrictedSettingsShow && !isA11yPage && !a11yRunning) {
        AlertDialog(
            title = {
                Text(text = stringResource(R.string.s_17bfc950b7))
            },
            text = {
                Text(text = stringResource(R.string.s_1262ae439f))
            },
            onDismissRequest = {
                accessRestrictedSettingsShowFlow.value = false
            },
            confirmButton = {
                TextButton({
                    accessRestrictedSettingsShowFlow.value = false
                    mainVm.navigateWebPage(ShortUrlSet.URL2)
                }) {
                    Text(text = stringResource(R.string.s_ec7ae06b09))
                }
            },
            dismissButton = {
                TextButton({
                    accessRestrictedSettingsShowFlow.value = false
                }) {
                    Text(text = stringResource(R.string.s_6c14bd7f6f))
                }
            },
        )
    }
}

@Composable
fun UiAutomationAlreadyRegisteredDlg() {
    if (automationRegisteredExceptionFlow.collectAsStateWithLifecycle().value != null) {
        AlertDialog(
            onDismissRequest = {
                automationRegisteredExceptionFlow.value = null
            },
            title = { Text(text = stringResource(R.string.s_65525f0f44)) },
            text = {
                Text(text = stringResource(R.string.s_914a3f7e15))
            },
            confirmButton = {
                TextButton(onClick = {
                    automationRegisteredExceptionFlow.value = null
                }) {
                    Text(text = stringResource(R.string.s_dd3760c80a))
                }
            }
        )
    }
}
