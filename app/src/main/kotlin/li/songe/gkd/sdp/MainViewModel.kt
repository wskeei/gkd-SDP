package li.songe.gkd.sdp

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.webkit.URLUtil
import androidx.lifecycle.viewModelScope
import androidx.navigation3.runtime.NavBackStack
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
import kotlinx.coroutines.withContext
import li.songe.gkd.sdp.a11y.useA11yServiceEnabledFlow
import li.songe.gkd.sdp.a11y.useEnabledA11yServicesFlow
import li.songe.gkd.sdp.data.CrashData
import li.songe.gkd.sdp.data.RawSubscription
import li.songe.gkd.sdp.data.SubsItem
import li.songe.gkd.sdp.db.DbSet
import li.songe.gkd.sdp.diagnostics.DiagnosticLogger
import li.songe.gkd.sdp.permission.AuthReason
import li.songe.gkd.sdp.permission.shizukuGrantedState
import li.songe.gkd.sdp.service.A11yService
import li.songe.gkd.sdp.shizuku.shizukuContextFlow
import li.songe.gkd.sdp.shizuku.uiAutomationFlow
import li.songe.gkd.sdp.shizuku.updateBinderMutex
import li.songe.gkd.sdp.store.createTextFlow
import li.songe.gkd.sdp.store.storeFlow
import li.songe.gkd.sdp.ui.AdvancedPageRoute
import li.songe.gkd.sdp.ui.AppOpsAllowRoute
import li.songe.gkd.sdp.ui.CrashReportRoute
import li.songe.gkd.sdp.ui.FocusLockRoute
import li.songe.gkd.sdp.ui.SnapshotPageRoute
import li.songe.gkd.sdp.ui.WebViewRoute
import li.songe.gkd.sdp.ui.component.AlertDialogOptions
import li.songe.gkd.sdp.ui.component.InputSubsLinkOption
import li.songe.gkd.sdp.ui.component.RuleGroupState
import li.songe.gkd.sdp.ui.component.UploadOptions
import li.songe.gkd.sdp.ui.home.BottomNavItem
import li.songe.gkd.sdp.ui.home.HomeRoute
import li.songe.gkd.sdp.ui.share.BaseViewModel
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
import li.songe.gkd.sdp.util.updateSubsMutex
import li.songe.gkd.sdp.util.updateSubscription
import li.songe.loc.Loc
import rikka.shizuku.Shizuku
import java.nio.file.Files
import kotlin.reflect.jvm.jvmName
import kotlin.time.Duration.Companion.days

class MainViewModel : BaseViewModel(), OnSimpleLife by DefaultSimpleLifeImpl() {
    companion object {
        private var _instance: MainViewModel? = null
        val instance get() = _instance!!
        private var tempTermsAccepted = false
    }

    init {
        LogUtils.d("MainViewModel:init")
        _instance = this
        addCloseable {
            LogUtils.d("MainViewModel:close")
            if (_instance == this) { // 可能同时存在 2 个 MainViewModel 实例
                _instance = null
            }
        }
    }

    override val scope get() = viewModelScope

    val backStack: NavBackStack<NavKey> = NavBackStack(HomeRoute)
    val topRoute get() = backStack.last()

    private val backThrottleTimer = ThrottleTimer()

    fun popPage(@Loc loc: String = "") = runMainPost {
        if (backThrottleTimer.expired() && backStack.size > 1) {
            val old = backStack.last()
            backStack.removeAt(backStack.lastIndex)
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
            if (replaced) {
                backStack[backStack.lastIndex] = navKey
            } else {
                backStack.add(navKey)
            }
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

    val appOrderListFlow = DbSet.actionLogDao.queryLatestUniqueAppIds().stateInit(emptyList())
    val appVisitOrderMapFlow = DbSet.appVisitLogDao.query().map {
        it.mapIndexed { i, appId -> appId to i }.toMap()
    }.debounce(500).stateInit(emptyMap())

    fun addOrModifySubs(
        url: String,
        oldItem: SubsItem? = null,
    ) = viewModelScope.launchTry(Dispatchers.IO) {
        if (updateSubsMutex.mutex.isLocked) return@launchTry
        updateSubsMutex.withStateLock {
            val subItems = subsItemsFlow.value
            val text = try {
                client.get(url).bodyAsText()
            } catch (e: Exception) {
                LogUtils.d(e)
                toast("下载订阅文件失败\n${DiagnosticLogger.userMessage(e)}")
                return@launchTry
            }
            val newSubsRaw = try {
                RawSubscription.parse(text)
            } catch (e: Exception) {
                LogUtils.d(e)
                toast("解析订阅文件失败\n${DiagnosticLogger.userMessage(e)}")
                return@launchTry
            }
            if (oldItem == null) {
                if (subItems.any { it.id == newSubsRaw.id }) {
                    toast("订阅已存在")
                    return@launchTry
                }
            } else {
                if (oldItem.id != newSubsRaw.id) {
                    toast("订阅id不对应")
                    return@launchTry
                }
            }
            if (newSubsRaw.id < 0) {
                toast("订阅id不可为${newSubsRaw.id}\n负数id为内部使用")
                return@launchTry
            }
            val newItem = oldItem?.copy(updateUrl = url) ?: SubsItem(
                id = newSubsRaw.id,
                updateUrl = url,
                order = if (subItems.isEmpty()) 1 else (subItems.maxBy { it.order }.order + 1)
            )
            updateSubscription(newSubsRaw)
            if (oldItem == null) {
                DbSet.subsItemDao.insert(newItem)
                toast("成功添加订阅")
            } else {
                DbSet.subsItemDao.update(newItem)
                toast("成功修改订阅")
            }
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

    val tabFlow = MutableStateFlow(BottomNavItem.Control.key)
    val resetPageScrollEvent = MutableSharedFlow<BottomNavItem>()
    private var lastClickTabTime = 0L
    fun handleClickTab(navItem: BottomNavItem) {
        val t = System.currentTimeMillis()
        // double click
        if (navItem.key == tabFlow.value && t - lastClickTabTime < 500) {
            viewModelScope.launch { resetPageScrollEvent.emit(navItem) }
        }
        tabFlow.value = navItem.key
        lastClickTabTime = t
    }

    fun handleGkdUri(uri: Uri) {
        val notFoundToast = { toast("未知URI\n${uri}") }
        when (uri.host) {
            "page" -> when (uri.path) {
                "" -> {
                    val tab = uri.getQueryParameter("tab")?.toIntOrNull()
                    if (tab != null && BottomNavItem.allSubObjects.any { it.key == tab }) {
                        tabFlow.value = tab
                    }
                }

                "/1" -> navigatePage(AdvancedPageRoute)
                "/2" -> navigatePage(SnapshotPageRoute)
                "/3" -> navigatePage(AppOpsAllowRoute)
                "/4" -> navigatePage(FocusLockRoute)
                else -> notFoundToast()
            }

            "invoke" -> when (uri.path) {
                "/1" -> openWeChatScaner()
                else -> notFoundToast()
            }

            else -> notFoundToast()
        }
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
            toast("正在连接中，请稍后")
            return
        }
        storeFlow.update { s -> s.copy(enableShizuku = value) }
    }

    fun requestShizuku() {
        if (shizukuContextFlow.value.ok) return
        if (updateBinderMutex.mutex.isLocked) {
            toast("正在连接中，请稍后")
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

    var tempCrashDataList = emptyList<CrashData>()

    init {
        // preload
        appIconMapFlow.value
        viewModelScope.launchTry(Dispatchers.IO) {
            val subsItems = DbSet.subsItemDao.queryAll()
            if (!subsItems.any { s -> s.id == LOCAL_SUBS_ID }) {
                if (!subsFolder.resolve("${LOCAL_SUBS_ID}.json").exists()) {
                    updateSubscription(
                        RawSubscription(
                            id = LOCAL_SUBS_ID,
                            name = "本地订阅",
                            version = 0
                        )
                    )
                }
                DbSet.subsItemDao.insert(
                    SubsItem(
                        id = LOCAL_SUBS_ID,
                        order = subsItems.minByOrNull { it.order }?.order ?: 0,
                    )
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
            tempCrashDataList = list
            if (list.isNotEmpty()) {
                navigatePage(CrashReportRoute)
            }
        }

        // for OnSimpleLife
        onCreated()
        addCloseable { onDestroyed() }
    }
}
