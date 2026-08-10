package li.songe.gkd.sdp

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.webkit.URLUtil
import androidx.lifecycle.viewModelScope
import androidx.navigation3.runtime.NavKey
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import li.songe.gkd.sdp.a11y.useA11yServiceEnabledFlow
import li.songe.gkd.sdp.a11y.useEnabledA11yServicesFlow
import li.songe.gkd.sdp.data.RawSubscription
import li.songe.gkd.sdp.data.CrashData
import li.songe.gkd.sdp.data.SubsItem
import li.songe.gkd.sdp.db.DbSet
import li.songe.gkd.sdp.diagnostics.DiagnosticLogger
import li.songe.gkd.sdp.permission.AuthReason
import li.songe.gkd.sdp.permission.shizukuGrantedState
import li.songe.gkd.sdp.remote.WebOriginPolicy
import li.songe.gkd.sdp.service.A11yService
import li.songe.gkd.sdp.shizuku.shizukuContextFlow
import li.songe.gkd.sdp.shizuku.uiAutomationFlow
import li.songe.gkd.sdp.shizuku.updateBinderMutex
import li.songe.gkd.sdp.store.createTextFlow
import li.songe.gkd.sdp.store.storeFlow
import li.songe.gkd.sdp.ui.AdvancedPageRoute
import li.songe.gkd.sdp.ui.ActionLogRoute
import li.songe.gkd.sdp.ui.AppOpsAllowRoute
import li.songe.gkd.sdp.ui.CrashReportRoute
import li.songe.gkd.sdp.ui.FocusLockRoute
import li.songe.gkd.sdp.ui.SnapshotPageRoute
import li.songe.gkd.sdp.ui.UsageGuardReviewRoute
import li.songe.gkd.sdp.ui.UsageGuardRoute
import li.songe.gkd.sdp.ui.WebViewRoute
import li.songe.gkd.sdp.ui.CrashReportRepository
import li.songe.gkd.sdp.ui.component.AlertDialogOptions
import li.songe.gkd.sdp.ui.component.InputSubsLinkOption
import li.songe.gkd.sdp.ui.component.RuleGroupState
import li.songe.gkd.sdp.ui.component.UploadOptions
import li.songe.gkd.sdp.ui.home.BottomNavItem
import li.songe.gkd.sdp.ui.home.HomeRoute
import li.songe.gkd.sdp.navigation.AppDestination
import li.songe.gkd.sdp.navigation.AppNavigationRequests
import li.songe.gkd.sdp.navigation.AppNavigator
import li.songe.gkd.sdp.navigation.DeepLinkParseResult
import li.songe.gkd.sdp.navigation.DeepLinkParser
import li.songe.gkd.sdp.ui.share.BaseViewModel
import li.songe.gkd.sdp.ui.share.defaultAppOrderListFlow
import li.songe.gkd.sdp.ui.share.defaultAppVisitOrderMapFlow
import li.songe.gkd.sdp.util.AutomatorModeOption
import li.songe.gkd.sdp.util.BackupUtils
import li.songe.gkd.sdp.util.DefaultSimpleLifeImpl
import li.songe.gkd.sdp.util.LOCAL_SUBS_ID
import li.songe.gkd.sdp.util.LogUtils
import li.songe.gkd.sdp.util.OnSimpleLife
import li.songe.gkd.sdp.util.ThrottleTimer
import li.songe.gkd.sdp.util.UpdateStatus
import li.songe.gkd.sdp.util.appIconMapFlow
import li.songe.gkd.sdp.util.clearCache
import li.songe.gkd.sdp.util.client
import li.songe.gkd.sdp.util.crashFolder
import li.songe.gkd.sdp.util.crashTempFolder
import li.songe.gkd.sdp.util.findOption
import li.songe.gkd.sdp.util.json
import li.songe.gkd.sdp.util.launchTry
import li.songe.gkd.sdp.util.openUri
import li.songe.gkd.sdp.util.openWeChatScaner
import li.songe.gkd.sdp.util.runMainPost
import li.songe.gkd.sdp.util.stopCoroutine
import li.songe.gkd.sdp.util.subsFolder
import li.songe.gkd.sdp.util.subsItemsFlow
import li.songe.gkd.sdp.util.toast
import li.songe.gkd.sdp.util.updateSubscription
import li.songe.loc.Loc
import rikka.shizuku.Shizuku
import java.nio.file.Files
import kotlin.reflect.jvm.jvmName
import kotlin.time.Duration.Companion.days
import li.songe.gkd.sdp.R

class MainViewModel(
    val navigator: AppNavigator = AppNavigator(),
) : BaseViewModel(), OnSimpleLife by DefaultSimpleLifeImpl() {
    companion object {
        private var tempTermsAccepted = false
    }

    init {
        viewModelScope.launch {
            AppNavigationRequests.flow.collect(::selectDestination)
        }
    }

    override val scope get() = viewModelScope

    val backStack get() = navigator.backStack
    val topRoute get() = backStack.last()

    fun bindBackStack(backStack: androidx.navigation3.runtime.NavBackStack<androidx.navigation3.runtime.NavKey>) {
        val pending = navigator.backStack.toList()
        if (backStack.size == 1 && pending.size > 1) {
            backStack.addAll(pending.drop(1))
        }
        navigator.bindBackStack(backStack)
    }

    private val backThrottleTimer = ThrottleTimer()

    fun popPage(@Loc loc: String = "") = runMainPost {
        if (backThrottleTimer.expired() && backStack.size > 1) {
            val old = backStack.last()
            navigator.pop()
            LogUtils.d("popPage", "$old -> ${backStack.last()}", loc = loc)
        }
    }

    fun navigatePage(
        navKey: NavKey,
        replaced: Boolean = false,
        @Loc loc: String = "",
    ) = runMainPost {
        if (navKey != backStack.last()) {
            val old = backStack.last()
            navigator.navigate(navKey, replaced)
            LogUtils.d("navigatePage", "$old -> ${backStack.last()}", loc = loc)
        }
    }

    fun navigateWebPage(url: String) = navigatePage(WebViewRoute(url))

    val dialogFlow = MutableStateFlow<AlertDialogOptions?>(null)
    val authReasonFlow = MutableStateFlow<AuthReason?>(null)

    val updateStatus = if (META.updateEnabled) UpdateStatus(viewModelScope) else null

    val shizukuErrorFlow = MutableStateFlow<Throwable?>(null)

    val uploadOptions = UploadOptions(this)

    val showEditCookieDlgFlow = MutableStateFlow(false)

    val inputSubsLinkOption = InputSubsLinkOption()

    val sheetSubsIdFlow = MutableStateFlow<Long?>(null)

    val appOrderListFlow = defaultAppOrderListFlow
    val appVisitOrderMapFlow = defaultAppVisitOrderMapFlow

    private val addOrModifySubsMutex = Mutex()

    fun addOrModifySubs(
        url: String,
        oldItem: SubsItem? = null,
    ) = viewModelScope.launchTry(Dispatchers.IO) {
        if (!addOrModifySubsMutex.tryLock()) return@launchTry
        try {
            val subItems = subsItemsFlow.value
            val text = try {
                client.get(url).bodyAsText()
            } catch (e: Exception) {
                LogUtils.d(e)
                toast(li.songe.gkd.sdp.app.getString(R.string.s_11d1976e38, (DiagnosticLogger.userMessage(e)).toString()))
                return@launchTry
            }
            val newSubsRaw = try {
                RawSubscription.parse(text)
            } catch (e: Exception) {
                LogUtils.d(e)
                toast(li.songe.gkd.sdp.app.getString(R.string.s_dea3d845c4, (DiagnosticLogger.userMessage(e)).toString()))
                return@launchTry
            }
            if (oldItem == null) {
                if (subItems.any { it.id == newSubsRaw.id }) {
                    toast(li.songe.gkd.sdp.app.getString(R.string.s_60cd8a5af2))
                    return@launchTry
                }
            } else {
                if (oldItem.id != newSubsRaw.id) {
                    toast(li.songe.gkd.sdp.app.getString(R.string.s_8dc09bd1b4))
                    return@launchTry
                }
            }
            if (newSubsRaw.id < 0) {
                toast(li.songe.gkd.sdp.app.getString(R.string.s_1f4d53235c, (newSubsRaw.id).toString()))
                return@launchTry
            }
            val newItem = oldItem?.copy(updateUrl = url) ?: SubsItem(
                id = newSubsRaw.id,
                updateUrl = url,
                order = if (subItems.isEmpty()) 1 else (subItems.maxBy { it.order }.order + 1)
            )
            updateSubscription(newSubsRaw, newItem)
            toast(if (oldItem == null) "成功添加订阅" else "成功修改订阅")
        } finally {
            addOrModifySubsMutex.unlock()
        }
    }

    val ruleGroupState = RuleGroupState(this)

    val textFlow = MutableStateFlow<String?>(null)
    fun openUrl(url: String) {
        if (URLUtil.isNetworkUrl(url)) {
            textFlow.value = url
        } else {
            openUri(url)
        }
    }

    val resetPageScrollEvent = MutableSharedFlow<BottomNavItem>()
    private var lastClickTabTime = 0L
    fun handleClickTab(navItem: BottomNavItem) {
        val t = System.currentTimeMillis()
        val currentTab = (backStack.firstOrNull() as? HomeRoute)?.tabKey
        // double click
        if (navItem.key == currentTab && t - lastClickTabTime < 500) {
            viewModelScope.launch { resetPageScrollEvent.emit(navItem) }
        }
        navigator.navigateHome(navItem.key)
        lastClickTabTime = t
    }

    fun handleGkdUri(uri: Uri) {
        val notFoundToast = { toast(li.songe.gkd.sdp.app.getString(R.string.s_55c1c91c04, (uri).toString())) }
        when (val parsed = DeepLinkParser.parse(uri.toString())) {
            is DeepLinkParseResult.Destination -> selectDestination(parsed.value)
            DeepLinkParseResult.Invalid -> when (WebOriginPolicy.legacyDeepLinkTarget(uri.toString())) {
                li.songe.gkd.sdp.remote.LegacyDeepLinkTarget.ADVANCED -> navigatePage(AdvancedPageRoute)
                li.songe.gkd.sdp.remote.LegacyDeepLinkTarget.SNAPSHOT -> navigatePage(SnapshotPageRoute)
                li.songe.gkd.sdp.remote.LegacyDeepLinkTarget.APP_OPS -> navigatePage(AppOpsAllowRoute)
                li.songe.gkd.sdp.remote.LegacyDeepLinkTarget.WECHAT_SCANNER -> openWeChatScaner()
                else -> notFoundToast()
            }
        }
    }

    private fun selectDestination(destination: AppDestination) {
        navigator.tabFor(destination)?.let { tab ->
            navigator.navigateHome(tab.key)
            return
        }
        navigator.navigate(destination)
    }

    fun handleIntent(intent: Intent) = viewModelScope.launchTry {
        LogUtils.d("handleIntent")
        val uri = intent.data?.normalizeScheme()
        val source = intent.getStringExtra(activityNavSourceName)
        if (uri?.scheme == "gkd") {
            handleGkdUri(uri)
        } else if (source == OpenFileActivity::class.jvmName && uri != null) {
            withContext(Dispatchers.IO) { BackupUtils.importBackUpData(uri) }
        }
    }

    val termsAcceptedFlow by lazy {
        if (tempTermsAccepted) {
            MutableStateFlow(true)
        } else {
            createTextFlow(
                key = "terms_accepted",
                decode = { it == "true" },
                encode = {
                    tempTermsAccepted = it
                    it.toString()
                },
                scope = viewModelScope,
            ).apply {
                tempTermsAccepted = value
            }
        }
    }

    val githubCookieFlow by lazy {
        createTextFlow(
            key = "github_cookie",
            decode = { it ?: "" },
            encode = { it },
            private = true,
            scope = viewModelScope,
        )
    }

    fun switchEnableShizuku(value: Boolean) {
        if (updateBinderMutex.mutex.isLocked) {
            toast(li.songe.gkd.sdp.app.getString(R.string.s_103787f23f))
            return
        }
        storeFlow.update { s -> s.copy(enableShizuku = value) }
    }

    fun requestShizuku() {
        if (shizukuContextFlow.value.ok) return
        if (updateBinderMutex.mutex.isLocked) {
            toast(li.songe.gkd.sdp.app.getString(R.string.s_103787f23f))
            return
        }
        try {
            Shizuku.requestPermission(Activity.RESULT_OK)
        } catch (e: Throwable) {
            shizukuErrorFlow.value = e
        }
    }

    suspend fun guardShizukuContext() {
        if (shizukuContextFlow.value.ok) return
        if (!storeFlow.value.enableShizuku) {
            storeFlow.update { it.copy(enableShizuku = true) }
        }
        if (!shizukuGrantedState.updateAndGet()) {
            requestShizuku()
            stopCoroutine()
        }
        if (shizukuContextFlow.value.ok) return
        stopCoroutine()
    }

    private val a11yServicesFlow = useEnabledA11yServicesFlow()
    val a11yServiceEnabledFlow = useA11yServiceEnabledFlow(a11yServicesFlow)

    val automatorModeFlow = storeFlow.mapNew {
        AutomatorModeOption.objects.findOption(it.automatorMode)
    }

    fun updateAutomatorMode(option: AutomatorModeOption) {
        if (automatorModeFlow.value == option) return
        storeFlow.update { it.copy(automatorMode = option.value, enableAutomator = false) }
        A11yService.instance?.shutdown()
        uiAutomationFlow.value?.shutdown()
    }

    val showShareLogDlgFlow = MutableStateFlow(false)

    init {
        // preload
        appIconMapFlow.value
        viewModelScope.launchTry(Dispatchers.IO) {
            val subsItems = DbSet.subsItemDao.queryAll()
            if (!subsItems.any { s -> s.id == LOCAL_SUBS_ID }) {
                val localFile = subsFolder.resolve("${LOCAL_SUBS_ID}.json")
                val localSubscription = if (localFile.exists()) {
                    RawSubscription.parse(localFile.readText(), json5 = false)
                } else {
                    RawSubscription(
                        id = LOCAL_SUBS_ID,
                        name = "本地订阅",
                        version = 0,
                    )
                }
                updateSubscription(
                    subscription = localSubscription,
                    subsItem = SubsItem(
                        id = LOCAL_SUBS_ID,
                        order = subsItems.minByOrNull { it.order }?.order ?: 0,
                    ),
                )
            }
        }

        viewModelScope.launchTry(Dispatchers.IO) {
            // 每次进入删除缓存
            clearCache()
        }

        if (termsAcceptedFlow.value && updateStatus?.canRecheck == true) {
            updateStatus.checkUpdate()
        }

        viewModelScope.launch(Dispatchers.IO) {
            // preload
            githubCookieFlow.value
        }
        viewModelScope.launchTry(Dispatchers.IO) {
            val list = (crashTempFolder.listFiles() ?: emptyArray()).mapNotNull {
                try {
                    json.decodeFromString<CrashData>(it.readText())
                } catch (e: Exception) {
                    LogUtils.d("解析崩溃日志失败: ${it.name}", e)
                    null
                }
            }.sortedBy { -it.mtime }
            crashTempFolder.deleteRecursively()
            val t = System.currentTimeMillis()
            crashFolder.listFiles()?.filter {
                val name = it.name
                !list.any { f -> name == f.filename }
            }?.forEach {
                val mtime = Files.getLastModifiedTime(it.toPath()).toMillis()
                if (t - mtime > 7.days.inWholeMilliseconds) {
                    it.delete()
                }
            }
            CrashReportRepository.publish(list)
            if (list.isNotEmpty()) {
                navigatePage(CrashReportRoute)
            }
        }

        // for OnSimpleLife
        onCreated()
        addCloseable { onDestroyed() }
    }
}
