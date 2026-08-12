@file:JvmName("AdvancedScreen")

package li.songe.gkd.sdp.ui

import android.app.Activity
import android.content.Context
import android.media.projection.MediaProjectionManager
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dylanc.activityresult.launcher.launchForResult
import li.songe.gkd.sdp.MainActivity
import li.songe.gkd.sdp.R
import li.songe.gkd.sdp.permission.canDrawOverlaysState
import li.songe.gkd.sdp.permission.foregroundServiceSpecialUseState
import li.songe.gkd.sdp.permission.notificationState
import li.songe.gkd.sdp.permission.requiredPermission
import li.songe.gkd.sdp.remote.CleartextOriginAuthorizations
import li.songe.gkd.sdp.service.ActivityService
import li.songe.gkd.sdp.service.ButtonService
import li.songe.gkd.sdp.service.EventService
import li.songe.gkd.sdp.service.HttpService
import li.songe.gkd.sdp.service.ScreenshotService
import li.songe.gkd.sdp.store.storeFlow
import li.songe.gkd.sdp.ui.share.LocalMainViewModel
import li.songe.gkd.sdp.util.ShortUrlSet
import li.songe.gkd.sdp.util.launchAsFn
import li.songe.gkd.sdp.util.toast

@Composable
fun AdvancedPage() {
    val mainVm = LocalMainViewModel.current
    val context = LocalActivity.current as MainActivity
    val vm = viewModel<AdvancedVm>()
    val store by storeFlow.collectAsStateWithLifecycle()
    val showEditPortDlg by vm.showEditPortDlgFlow.collectAsStateWithLifecycle()
    val showShizukuState by vm.showShizukuStateFlow.collectAsStateWithLifecycle()
    val showCaptureScreenshotDlg by vm.showCaptureScreenshotDlgFlow.collectAsStateWithLifecycle()
    var showHttpSettingDlg by rememberSaveable { mutableStateOf(false) }
    val uiState = AdvancedUiState(
        showEditPortDlg = showEditPortDlg,
        showShizukuState = showShizukuState,
        showCaptureScreenshotDlg = showCaptureScreenshotDlg,
        showHttpSettingDlg = showHttpSettingDlg,
        store = store,
    )
    val callbacks = AdvancedPageCallbacks(
        onBack = mainVm::popPage,
        onOpenShizukuState = { vm.showShizukuStateFlow.value = true },
        onDismissShizukuDialog = { vm.showShizukuStateFlow.value = false },
        onRequestShizuku = mainVm::requestShizuku,
        onToggleShizuku = mainVm::switchEnableShizuku,
        onOpenWeb = mainVm::navigateWebPage,
        onToggleHttpServer = vm.viewModelScope.launchAsFn<Boolean> {
            if (it) {
                requiredPermission(context, foregroundServiceSpecialUseState)
                requiredPermission(context, notificationState)
                HttpService.start()
            } else {
                HttpService.stop()
            }
        },
        onToggleHttpSetting = { showHttpSettingDlg = !showHttpSettingDlg },
        onOpenEditPortDialog = {
            showHttpSettingDlg = false
            vm.showEditPortDlgFlow.value = true
        },
        onDismissEditPortDialog = { vm.showEditPortDlgFlow.value = false },
        onApplyPort = { newPort ->
            vm.showEditPortDlgFlow.value = false
            if (newPort != store.httpServerPort) {
                storeFlow.value = store.copy(httpServerPort = newPort)
                toast(li.songe.gkd.sdp.app.getString(R.string.s_e2cff77372))
            }
        },
        onUpdateSettings = { updated -> storeFlow.value = updated },
        onRevokeCleartextOrigin = { origin -> CleartextOriginAuthorizations.revoke(origin) },
        onNavigateSnapshotPage = { mainVm.navigatePage(SnapshotPageRoute) },
        onOpenCaptureScreenshotDialog = { vm.showCaptureScreenshotDlgFlow.value = true },
        onDismissCaptureScreenshotDialog = { vm.showCaptureScreenshotDlgFlow.value = false },
        onApplyCaptureScreenshot = { appId, eventSelector ->
            vm.showCaptureScreenshotDlgFlow.value = false
            if (appId != store.screenshotTargetAppId || eventSelector != store.screenshotEventSelector) {
                storeFlow.value = store.copy(
                    screenshotTargetAppId = appId,
                    screenshotEventSelector = eventSelector,
                )
                toast(li.songe.gkd.sdp.app.getString(R.string.s_e2cff77372))
            }
        },
        onOpenCaptureHelp = { mainVm.navigateWebPage(ShortUrlSet.URL15) },
        onOpenCookieDialog = { mainVm.showEditCookieDlgFlow.value = true },
        onToggleCaptureScreenshot = { enabled ->
            storeFlow.value = store.copy(captureScreenshot = enabled)
            if (enabled && (store.screenshotTargetAppId.isEmpty() || store.screenshotEventSelector.isEmpty())) {
                toast(li.songe.gkd.sdp.app.getString(R.string.s_c456ae2487))
            }
        },
        onToggleScreenshotService = vm.viewModelScope.launchAsFn<Boolean> {
            if (it) {
                requiredPermission(context, notificationState)
                val mediaProjectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                val activityResult = context.launcher.launchForResult(
                    mediaProjectionManager.createScreenCaptureIntent(),
                )
                if (activityResult.resultCode == Activity.RESULT_OK && activityResult.data != null) {
                    ScreenshotService.start(intent = activityResult.data!!)
                }
            } else {
                ScreenshotService.stop()
            }
        },
        onToggleButtonService = vm.viewModelScope.launchAsFn<Boolean> {
            if (it) {
                requiredPermission(context, foregroundServiceSpecialUseState)
                requiredPermission(context, notificationState)
                requiredPermission(context, canDrawOverlaysState)
                ButtonService.start()
            } else {
                ButtonService.stop()
            }
        },
        onToggleActivityService = vm.viewModelScope.launchAsFn<Boolean> {
            if (it) {
                requiredPermission(context, foregroundServiceSpecialUseState)
                requiredPermission(context, notificationState)
                requiredPermission(context, canDrawOverlaysState)
                ActivityService.start()
            } else {
                ActivityService.stop()
            }
        },
        onToggleEventService = vm.viewModelScope.launchAsFn<Boolean> {
            if (it) {
                requiredPermission(context, foregroundServiceSpecialUseState)
                requiredPermission(context, notificationState)
                requiredPermission(context, canDrawOverlaysState)
                EventService.start()
            } else {
                EventService.stop()
            }
        },
        onNavigateActivityLog = { mainVm.navigatePage(ActivityLogRoute) },
        onNavigateA11yEventLog = { mainVm.navigatePage(A11yEventLogRoute) },
    )

    AdvancedPageSections(
        uiState = uiState,
        callbacks = callbacks,
    )
}
