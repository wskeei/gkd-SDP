package li.songe.gkd.sdp.shizuku


import android.app.ActivityManager
import android.content.ComponentName
import android.content.pm.PackageManager
import androidx.annotation.WorkerThread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import li.songe.gkd.sdp.app
import li.songe.gkd.sdp.appScope
import li.songe.gkd.sdp.isActivityVisible
import li.songe.gkd.sdp.permission.shizukuGrantedState
import li.songe.gkd.sdp.permission.updatePermissionState
import li.songe.gkd.sdp.service.ExposeService
import li.songe.gkd.sdp.remote.ExposeAction
import li.songe.gkd.sdp.service.StatusService
import li.songe.gkd.sdp.service.currentAppBlocked
import li.songe.gkd.sdp.service.currentAppUseA11y
import li.songe.gkd.sdp.service.updateTopTaskAppId
import li.songe.gkd.sdp.store.storeFlow
import li.songe.gkd.sdp.util.AndroidTarget
import li.songe.gkd.sdp.util.LogUtils
import li.songe.gkd.sdp.util.MutexState
import li.songe.gkd.sdp.util.launchTry
import li.songe.gkd.sdp.util.toast
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import kotlin.system.exitProcess
import li.songe.gkd.sdp.R

inline fun <T> safeInvokeShizuku(
    block: () -> T
): T? = try {
    block()
} catch (_: ShizukuOffException) {
    null
} catch (e: IllegalStateException) {
    // https://github.com/RikkaApps/Shizuku-API/blob/a27f6e4151ba7b39965ca47edb2bf0aeed7102e5/api/src/main/java/rikka/shizuku/Shizuku.java#L430
    if (e.message == li.songe.gkd.sdp.app.getString(R.string.s_160af675fd)) {
        null
    } else {
        throw e
    }
}

class ShizukuOffException : IllegalStateException("Shizuku is off")

fun getShizukuService(name: String): ShizukuBinderWrapper? {
    return SystemServiceHelper.getSystemService(name)?.let(::ShizukuBinderWrapper)
}

// https://github.com/android-cs/16/blob/main/packages/Shell/AndroidManifest.xml
private fun checkRemotePermission(permission: String): Boolean {
    return Shizuku.checkRemotePermission(permission) == PackageManager.PERMISSION_GRANTED
}

private val isAdbRestricted: Boolean
    get() {
        if (!checkRemotePermission("android.permission.GRANT_RUNTIME_PERMISSIONS")) {
            return true
        }
        if (AndroidTarget.P && !checkRemotePermission("android.permission.MANAGE_APP_OPS_MODES")) {
            return true
        }
        return false
    }

class ShizukuContext(
    val serviceWrapper: UserServiceWrapper?,
    val packageManager: SafePackageManager?,
    val userManager: SafeUserManager?,
    val activityManager: SafeActivityManager?,
    val activityTaskManager: SafeActivityTaskManager?,
    val appOpsService: SafeAppOpsService?,
    val inputManager: SafeInputManager?,
    val a11yManager: SafeAccessibilityManager?,
    val wmManager: SafeWindowManager?,
) {
    val ok get() = this !== defaultShizukuContext
    fun destroy() {
        serviceWrapper?.destroy()
        if (activityTaskManager != null) {
            activityTaskManager.unregisterDefault()
        } else {
            activityManager?.unregisterDefault()
        }
    }

    val states = listOf(
        "IUserService" to serviceWrapper,
        "IActivityManager" to activityManager,
        "IActivityTaskManager" to activityTaskManager,
        "IAppOpsService" to appOpsService,
        "IInputManager" to inputManager,
        "IPackageManager" to packageManager,
        "IUserManager" to userManager,
        "IAccessibilityManager" to a11yManager,
        "IWindowManager" to wmManager,
    )

    fun grantSelf() {
        packageManager ?: return
        appOpsService ?: return
        if (isAdbRestricted) return
        appOpsService.allowAllSelfMode()
        packageManager.allowAllSelfPermission()
    }

    @WorkerThread
    fun tap(x: Float, y: Float, duration: Long = 0): Boolean {
        return serviceWrapper?.tap(x, y, duration) ?: (inputManager?.tap(x, y, duration) != null)
    }

    fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, duration: Long): Boolean {
        return serviceWrapper?.swipe(x1, y1, x2, y2, duration) ?: (inputManager?.swipe(
            x1,
            y1,
            x2,
            y2,
            duration
        ) != null)
    }

    fun getTasks(maxNum: Int = 1): List<ActivityManager.RunningTaskInfo> {
        return activityTaskManager?.getTasks(maxNum)
            ?: activityManager?.getTasks(maxNum)
            ?: emptyList()
    }

    fun topCpn(): ComponentName? = getTasks().firstOrNull()?.topActivity

    init {
        if (activityTaskManager != null) {
            activityTaskManager.registerDefault()
        } else {
            activityManager?.registerDefault()
        }
        grantSelf()
        // 某些情况下存在残留进程
        val size = serviceWrapper?.userService?.killLegacyService()
        if (size != null && size > 0) {
            LogUtils.d("killLegacyService $size")
        }
    }
}

private val defaultShizukuContext by lazy {
    ShizukuContext(
        serviceWrapper = null,
        packageManager = null,
        userManager = null,
        activityManager = null,
        activityTaskManager = null,
        appOpsService = null,
        inputManager = null,
        a11yManager = null,
        wmManager = null,
    )
}

val currentUserId by lazy { android.os.Process.myUserHandle().hashCode() }

val shizukuContextFlow by lazy { MutableStateFlow(defaultShizukuContext) }

val shizukuUsedFlow by lazy {
    combine(
        shizukuGrantedState.stateFlow,
        storeFlow.map { it.enableShizuku },
    ) { a, b ->
        a && b
    }.stateIn(appScope, SharingStarted.Eagerly, false)
}

val updateBinderMutex = MutexState()
private fun updateShizukuBinder() = updateBinderMutex.launchTry(appScope, Dispatchers.IO) {
    if (shizukuUsedFlow.value) {
        if (!app.justStarted) {
            toast(li.songe.gkd.sdp.app.getString(R.string.s_6fc15efa04))
        }
        val shizukuContext = ShizukuContext(
            serviceWrapper = buildServiceWrapper(),
            packageManager = SafePackageManager.newBinder(),
            userManager = SafeUserManager.newBinder(),
            activityManager = SafeActivityManager.newBinder(),
            activityTaskManager = SafeActivityTaskManager.newBinder(),
            appOpsService = SafeAppOpsService.newBinder(),
            inputManager = SafeInputManager.newBinder(),
            a11yManager = SafeAccessibilityManager.newBinder(),
            wmManager = SafeWindowManager.newBinder(),
        )
        shizukuContextFlow.value = shizukuContext
        shizukuContext.topCpn()?.let { cpn ->
            updateTopTaskAppId(cpn.packageName)
        }
        if (
            storeFlow.value.useAutomation &&
            !currentAppBlocked &&
            !currentAppUseA11y
        ) {
            AutomationService.tryConnect(true)
        }
        updatePermissionState()
        if (StatusService.needRestart) {
            //
            shizukuContext.activityManager?.startForegroundService(
                ExposeService.issueInternalIntent(ExposeAction.STATUS_AUTOSTART),
            )
        }
        val delayMillis = if (app.justStarted) 1200L else 0L
        if (shizukuContext.serviceWrapper == null) {
            if (shizukuContext.packageManager != null) {
                toast(li.songe.gkd.sdp.app.getString(R.string.s_b06217b584), delayMillis = delayMillis)
            } else {
                toast(li.songe.gkd.sdp.app.getString(R.string.s_9b66e8878b), delayMillis = delayMillis)
            }
        } else {
            toast(li.songe.gkd.sdp.app.getString(R.string.s_4e700140c6), delayMillis = delayMillis)
        }
    } else if (shizukuContextFlow.value.ok) {
        val willRelaunch = uiAutomationFlow.value != null && !shizukuGrantedState.updateAndGet()
        if (willRelaunch) {
            // 需要重启应用让系统释放 UiAutomation
            killRelaunchApp()
        } else {
            uiAutomationFlow.value?.shutdown(true)
            shizukuContextFlow.value.destroy()
            shizukuContextFlow.value = defaultShizukuContext
            toast(li.songe.gkd.sdp.app.getString(R.string.s_0335fe51da))
        }
    }
}

private suspend fun killRelaunchApp() {
    if (isActivityVisible) {
        toast(li.songe.gkd.sdp.app.getString(R.string.s_756f3b92c0), forced = true)
        delay(1500)
        app.startLaunchActivity()
    } else {
        toast(li.songe.gkd.sdp.app.getString(R.string.s_d5650458c9), forced = true)
        delay(1500)
    }
    android.os.Process.killProcess(android.os.Process.myPid())
    exitProcess(0)
}

fun initShizuku() {
    Shizuku.addBinderReceivedListener {
        LogUtils.d("Shizuku.addBinderReceivedListener")
        appScope.launchTry(Dispatchers.IO) {
            shizukuGrantedState.updateAndGet()
        }
    }
    Shizuku.addBinderDeadListener {
        LogUtils.d("Shizuku.addBinderDeadListener")
        shizukuGrantedState.stateFlow.value = false
    }
    appScope.launchTry {
        shizukuUsedFlow.collect { updateShizukuBinder() }
    }
}
