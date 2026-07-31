package li.songe.gkd.sdp.service

import android.app.Service
import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import li.songe.gkd.sdp.META
import li.songe.gkd.sdp.MainActivity
import li.songe.gkd.sdp.activityVisibleCountFlow
import li.songe.gkd.sdp.a11y.useA11yServiceEnabledFlow
import li.songe.gkd.sdp.app
import li.songe.gkd.sdp.notif.abNotif
import li.songe.gkd.sdp.permission.appOpsRestrictedFlow
import li.songe.gkd.sdp.permission.foregroundServiceSpecialUseState
import li.songe.gkd.sdp.permission.notificationState
import li.songe.gkd.sdp.permission.requiredPermission
import li.songe.gkd.sdp.permission.shizukuGrantedState
import li.songe.gkd.sdp.permission.writeSecureSettingsState
import li.songe.gkd.sdp.shizuku.uiAutomationFlow
import li.songe.gkd.sdp.store.actionCountFlow
import li.songe.gkd.sdp.store.storeFlow
import li.songe.gkd.sdp.util.DefaultSimpleLifeImpl
import li.songe.gkd.sdp.util.OnSimpleLife
import li.songe.gkd.sdp.util.RuleSummary
import li.songe.gkd.sdp.util.appInfoMapFlow
import li.songe.gkd.sdp.util.getSubsStatus
import li.songe.gkd.sdp.util.ruleSummaryFlow
import li.songe.gkd.sdp.util.startForegroundServiceByClass
import li.songe.gkd.sdp.util.stopServiceByClass
import li.songe.gkd.sdp.util.toast

class StatusService : Service(), OnSimpleLife by DefaultSimpleLifeImpl() {
    private var accessibilityGuardCoordinator: AccessibilityGuardCoordinator? = null

    override fun onBind(intent: Intent?) = null
    override fun onCreate() {
        super.onCreate()
        onCreated()
    }

    override fun onDestroy() {
        onDestroyed()
        super.onDestroy()
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    val shizukuWarnFlow = combine(
        shizukuGrantedState.stateFlow,
        storeFlow.map { it.enableShizuku },
    ) { a, b ->
        !a && b
    }.stateIn(scope, SharingStarted.Eagerly, false)

    val a11yServiceEnabledFlow = useA11yServiceEnabledFlow()

    private val guardCurrentAppBlockedFlow = combine(
        a11yPartDisabledFlow,
        storeFlow.map { it.enableBlockA11yAppList },
    ) { appBlocked, blockListEnabled ->
        appBlocked && blockListEnabled
    }.stateIn(
        scope,
        SharingStarted.Eagerly,
        storeFlow.value.enableBlockA11yAppList && a11yPartDisabledFlow.value,
    )

    fun statusTriple(): Triple<String, String, String?> {
        val abRunning = A11yService.isRunning.value
        val automationRunning = uiAutomationFlow.value != null
        val store = storeFlow.value
        val ruleSummary = ruleSummaryFlow.value
        val count = actionCountFlow.value
        val shizukuWarn = shizukuWarnFlow.value
        val title = if (store.useCustomNotifText) {
            store.customNotifTitle.replaceTemplate(ruleSummary, count)
        } else {
            META.appName
        }
        return if (appOpsRestrictedFlow.value) {
            Triple(title, "权限受限，请解除限制", "gkd://page/3")
        } else if (shizukuWarn) {
            Triple(title, "Shizuku 未连接，请授权或关闭优化", "gkd://page/1")
        } else if (!automationRunning && !abRunning) {
            if (currentAppUseA11y) {
                val text = if (a11yServiceEnabledFlow.value) {
                    "无障碍发生故障"
                } else if (writeSecureSettingsState.updateAndGet()) {
                    if (store.enableAutomator && store.enableBlockA11yAppList && a11yPartDisabledFlow.value) {
                        val name =
                            appInfoMapFlow.value[topAppIdFlow.value]?.name ?: topAppIdFlow.value
                        "局部关闭 · $name"
                    } else {
                        "无障碍已关闭"
                    }
                } else {
                    "无障碍未授权"
                }
                Triple(title, text, abNotif.uri)
            } else {
                val text =
                    if (store.enableAutomator && store.enableBlockA11yAppList && a11yPartDisabledFlow.value) {
                        val name =
                            appInfoMapFlow.value[topAppIdFlow.value]?.name ?: topAppIdFlow.value
                        "局部关闭 · $name"
                    } else {
                        "自动化已关闭"
                    }
                Triple(title, text, abNotif.uri)
            }
        } else if (!store.enableMatch) {
            Triple(title, "暂停规则匹配", "gkd://page?tab=1")
        } else if (store.useCustomNotifText) {
            Triple(
                title,
                store.customNotifText.replaceTemplate(ruleSummary, count),
                abNotif.uri
            )
        } else {
            Triple(title, getSubsStatus(ruleSummary, count), abNotif.uri)
        }
    }

    init {
        useAliveFlow(isRunning)
        useAliveToast(
            name = "常驻通知",
            delayMillis = if (app.justStarted) 1000 else 0,
        )
        onCreated {
            abNotif.notifyService()
            accessibilityGuardCoordinator = AccessibilityGuardCoordinator(
                context = this@StatusService,
                scope = scope,
                a11yServiceEnabledFlow = a11yServiceEnabledFlow,
                currentAppBlockedFlow = guardCurrentAppBlockedFlow,
                activityVisibleCountFlow = activityVisibleCountFlow,
            ).also { coordinator ->
                coordinator.start()
            }
            scope.launch {
                combine(
                    A11yService.isRunning,
                    uiAutomationFlow,
                    storeFlow,
                    ruleSummaryFlow,
                    shizukuWarnFlow,
                    a11yServiceEnabledFlow,
                    writeSecureSettingsState.stateFlow,
                    appOpsRestrictedFlow,
                    topAppIdFlow,
                    actionCountFlow.debounce(1000L),
                ) {
                    statusTriple()
                }
                    .stateIn(
                        scope,
                        SharingStarted.Eagerly,
                        Triple(abNotif.title, abNotif.text, abNotif.uri)
                    )
                    .collect {
                        abNotif.copy(
                            title = it.first,
                            text = it.second,
                            uri = it.third,
                        ).notifyService()
                    }
            }
        }
        onDestroyed {
            accessibilityGuardCoordinator?.close()
            accessibilityGuardCoordinator = null
        }
    }

    companion object {
        val isRunning = MutableStateFlow(false)
        val needRestart
            get() = (storeFlow.value.enableStatusService || storeFlow.value.accessibilityGuardEnabled)
                    && !isRunning.value
                    && notificationState.updateAndGet()
                    && foregroundServiceSpecialUseState.updateAndGet()

        fun start() = startForegroundServiceByClass(StatusService::class)
        fun stop() {
            if (storeFlow.value.accessibilityGuardEnabled) {
                toast("请先关闭无障碍权限守护")
                return
            }
            stopServiceByClass(StatusService::class)
        }
        suspend fun requestStart(context: MainActivity) {
            requiredPermission(context, foregroundServiceSpecialUseState)
            requiredPermission(context, notificationState)
            start()
            storeFlow.update { it.copy(enableStatusService = true) }
        }

        private var lastAutoStart = 0L
        fun autoStart() {
            if (System.currentTimeMillis() - lastAutoStart < 1000) return
            // 重启自动打开通知栏状态服务
            // 需要已有服务或前台才能自主启动，否则报错 startForegroundService() not allowed due to mAllowStartForeground false
            if (needRestart) {
                start()
                lastAutoStart = System.currentTimeMillis()
            }
        }
    }
}

private fun String.replaceTemplate(ruleSummary: RuleSummary, count: Long): String {
    return replace($$"${i}", ruleSummary.globalGroups.size.toString())
        .replace($$"${k}", ruleSummary.appSize.toString())
        .replace($$"${u}", ruleSummary.appGroupSize.toString())
        .replace($$"${n}", count.toString())
}
