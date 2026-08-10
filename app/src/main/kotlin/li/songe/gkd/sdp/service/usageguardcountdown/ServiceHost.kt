@file:JvmName("UsageGuardCountdownServiceHost")

package li.songe.gkd.sdp.service

import android.content.Intent
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import li.songe.gkd.sdp.a11y.UsageGuardEngine
import li.songe.gkd.sdp.ui.share.ServiceOverlayLifecycleOwner
import li.songe.gkd.sdp.ui.style.AppTheme
import li.songe.gkd.sdp.util.BarUtils
import li.songe.gkd.sdp.util.LogUtils
import li.songe.gkd.sdp.util.ScreenUtils
import li.songe.gkd.sdp.util.UsageGuardCountdownOverlayCaptureController
import li.songe.gkd.sdp.util.UsageGuardCountdownOverlayCapturePolicy
import li.songe.gkd.sdp.util.UsageGuardCountdownOverlayLayoutPolicy
import li.songe.gkd.sdp.util.UsageGuardCountdownOverlayPolicy
import li.songe.gkd.sdp.util.UsageGuardCountdownOverlaySession
import li.songe.gkd.sdp.util.px
import kotlin.math.roundToInt

class UsageGuardCountdownOverlayService : LifecycleService(), SavedStateRegistryOwner {
    companion object {
        const val EXTRA_REASON_TEXT = "reasonText"
        const val EXTRA_OVERLAY_LEASE_ID = "overlayLeaseId"
        const val EXTRA_RUNTIME_GENERATION = "runtimeGeneration"
    }

    private val windowManager by lazy { getSystemService(WINDOW_SERVICE) as WindowManager }
    private var view: ComposeView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var overlayLifecycleOwner: ServiceOverlayLifecycleOwner? = null
    private var preservedPosition: Pair<Int, Int>? = null
    private val captureController = UsageGuardCountdownOverlayCaptureController()
    private var restoreOverlayJob: Job? = null

    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry = savedStateRegistryController.savedStateRegistry

    private var appId: String = ""
    private var recordId: Long = 0L
    private var overlayLeaseId: Long = 0L
    private var expiresAtState by mutableStateOf(0L)
    private var reasonTextState by mutableStateOf(
        UsageGuardCountdownOverlayPolicy.MISSING_REASON_TEXT,
    )
    private var showTerminateConfirm by mutableStateOf(false)

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performAttach()
        savedStateRegistryController.performRestore(null)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        val incomingAppId = intent?.getStringExtra("appId").orEmpty()
        val incomingRecordId = intent?.getLongExtra("recordId", 0L) ?: 0L
        val incomingExpiresAt = intent?.getLongExtra("expiresAt", 0L) ?: 0L
        val incomingOverlayLeaseId = intent?.getLongExtra(EXTRA_OVERLAY_LEASE_ID, 0L) ?: 0L
        val incomingRuntimeGeneration = intent?.getLongExtra(EXTRA_RUNTIME_GENERATION, 0L) ?: 0L
        val incomingReasonText = UsageGuardCountdownOverlayPolicy.displayReasonText(
            intent?.getStringExtra(EXTRA_REASON_TEXT).orEmpty(),
        )
        val now = System.currentTimeMillis()
        val incomingSession = UsageGuardCountdownOverlaySession(
            appId = incomingAppId,
            recordId = incomingRecordId,
            expiresAt = incomingExpiresAt,
            leaseId = incomingOverlayLeaseId,
            runtimeGeneration = incomingRuntimeGeneration,
        )
        if (!incomingSession.isValid(now)) {
            stopSelf()
            return START_NOT_STICKY
        }

        val startAction = captureController.onStart(
            session = incomingSession,
            hasView = view != null,
        )
        if (
            startAction ==
            UsageGuardCountdownOverlayCaptureController.StartAction.IGNORE_TERMINAL
        ) {
            UsageGuardEngine.onOverlayMountFailed(
                kind = "countdown",
                appId = incomingAppId,
                countdownLeaseId = incomingOverlayLeaseId,
            )
            stopSelf()
            return START_NOT_STICKY
        }
        appId = incomingAppId
        recordId = incomingRecordId
        overlayLeaseId = incomingOverlayLeaseId
        expiresAtState = incomingExpiresAt
        reasonTextState = incomingReasonText
        when (startAction) {
            UsageGuardCountdownOverlayCaptureController.StartAction.CREATE_AND_MOUNT ->
                showOverlay()

            UsageGuardCountdownOverlayCaptureController.StartAction.RESET_AND_MOUNT -> {
                restoreOverlayJob?.cancel()
                restoreOverlayJob = null
                showTerminateConfirm = false
                resetPosition()
            }

            UsageGuardCountdownOverlayCaptureController.StartAction.KEEP_MOUNTED,
            UsageGuardCountdownOverlayCaptureController.StartAction.KEEP_HIDDEN,
            UsageGuardCountdownOverlayCaptureController.StartAction.IGNORE_TERMINAL -> Unit
        }
        return START_NOT_STICKY
    }

    private fun showOverlay() {
        if (view != null) return

        val lifecycleOwner = ServiceOverlayLifecycleOwner()
        overlayLifecycleOwner = lifecycleOwner
        val overlayView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(this@UsageGuardCountdownOverlayService)
            setContent {
                AppTheme {
                    val horizontalMarginPx = 12.dp.px.roundToInt()
                    val maxPillWidthPx = UsageGuardCountdownOverlayLayoutPolicy.maxPillWidthPx(
                        screenWidthPx = ScreenUtils.getScreenWidth(),
                        horizontalMarginPx = horizontalMarginPx,
                    )
                    UsageGuardCountdownOverlayContent(
                        state = UsageGuardCountdownUiState(
                            remainingMillis = (expiresAtState - System.currentTimeMillis()).coerceAtLeast(0L),
                            reasonText = reasonTextState,
                            showTerminateConfirm = showTerminateConfirm,
                        ),
                        maxPillWidthPx = maxPillWidthPx,
                        onPillTap = { showTerminateConfirmScreen() },
                        onDrag = { dx, dy -> updatePosition(dx, dy) },
                        onDismissTerminate = { hideTerminateConfirm() },
                        onHideForScreenshot = { hideOverlayForScreenshot() },
                        onConfirmTerminate = {
                            UsageGuardEngine.terminateActiveUsage(appId, recordId)
                            stopSelf()
                        },
                    )
                }
            }
        }

        val initialPosition = getInitialPosition()
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            USAGE_GUARD_COUNTDOWN_OVERLAY_FLAGS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.START or Gravity.TOP
            x = preservedPosition?.first ?: initialPosition.x
            y = preservedPosition?.second ?: initialPosition.y
        }
        view = overlayView
        layoutParams = params
        mountOverlayView(overlayView, params)
    }

    private fun mountOverlayView(
        overlayView: ComposeView,
        params: WindowManager.LayoutParams,
    ) {
        if (captureController.isMounted || captureController.isTerminal) return
        runCatching {
            windowManager.addView(overlayView, params)
            captureController.onMountSucceeded()
            overlayLifecycleOwner?.onViewAdded()
            preservedPosition = null
        }.onFailure { error ->
            captureController.onMountFailed()
            overlayLifecycleOwner?.onViewRemoved()
            overlayLifecycleOwner = null
            restoreOverlayJob?.cancel()
            restoreOverlayJob = null
            view = null
            layoutParams = null
            LogUtils.d(
                "usage guard countdown overlay mount rejected",
                error::class.java.simpleName,
            )
            UsageGuardEngine.onOverlayMountFailed(
                kind = "countdown",
                appId = appId,
                countdownLeaseId = overlayLeaseId,
            )
            stopSelf()
        }
    }

    private fun hideOverlayForScreenshot() {
        val overlayView = view ?: return
        val params = layoutParams ?: return
        val hidden = captureController.snapshotForHide() ?: return
        preservedPosition = params.x to params.y
        val removed = runCatching {
            windowManager.removeView(overlayView)
            overlayLifecycleOwner?.onViewRemoved()
            overlayLifecycleOwner = null
            view = null
        }.onFailure { error ->
            LogUtils.d(
                "usage guard countdown overlay temporary hide rejected",
                error::class.java.simpleName,
            )
        }.isSuccess
        val shouldScheduleRestore = captureController.onHideResult(hidden, removed)
        if (!shouldScheduleRestore) return

        showTerminateConfirm = false
        resetPillLayoutParams(params)
        restoreOverlayJob?.cancel()
        restoreOverlayJob = lifecycleScope.launch {
            delay(UsageGuardCountdownOverlayCapturePolicy.HIDE_DURATION_MS)
            restoreOverlayJob = null
            restoreOverlayAfterScreenshot(hidden)
        }
    }

    private fun restoreOverlayAfterScreenshot(
        hidden: UsageGuardCountdownOverlaySession,
    ) {
        val now = System.currentTimeMillis()
        val restoreAction = captureController.restoreAction(
            hidden = hidden,
            now = now,
            leaseActive = UsageGuardEngine.canRestoreCountdownOverlay(hidden),
        )
        when (restoreAction) {
            UsageGuardCountdownOverlayCaptureController.RestoreAction.MOUNT -> showOverlay()

            UsageGuardCountdownOverlayCaptureController.RestoreAction.STOP_EXPIRED,
            UsageGuardCountdownOverlayCaptureController.RestoreAction.STOP_REVOKED -> {
                stopSelf()
            }

            UsageGuardCountdownOverlayCaptureController.RestoreAction.IGNORE -> Unit
        }
    }

    private fun showTerminateConfirmScreen() {
        if (!captureController.isMounted) return
        val params = layoutParams ?: return
        val overlayView = view ?: return
        params.width = WindowManager.LayoutParams.MATCH_PARENT
        params.height = WindowManager.LayoutParams.MATCH_PARENT
        params.x = 0
        params.y = 0
        showTerminateConfirm = true
        runCatching {
            windowManager.updateViewLayout(overlayView, params)
        }.onFailure {
            stopSelf()
        }
    }

    private fun hideTerminateConfirm() {
        if (!captureController.isMounted) return
        showTerminateConfirm = false
        val params = layoutParams ?: return
        val overlayView = view ?: return
        resetPillLayoutParams(params)
        runCatching {
            windowManager.updateViewLayout(overlayView, params)
        }.onFailure {
            stopSelf()
        }
    }

    private fun updatePosition(dx: Float, dy: Float) {
        if (showTerminateConfirm || !captureController.isMounted) return
        val params = layoutParams ?: return
        val overlayView = view ?: return
        val screenWidth = ScreenUtils.getScreenWidth()
        val screenHeight = ScreenUtils.getScreenHeight()
        val maxX = (screenWidth - overlayView.width).coerceAtLeast(0)
        val maxY = (screenHeight - overlayView.height).coerceAtLeast(0)
        params.x = (params.x + dx).roundToInt().coerceIn(0, maxX)
        params.y = (params.y + dy).roundToInt().coerceIn(0, maxY)
        runCatching {
            windowManager.updateViewLayout(overlayView, params)
        }.onFailure {
            stopSelf()
        }
    }

    private fun resetPosition() {
        val params = layoutParams ?: return
        val overlayView = view ?: return
        resetPillLayoutParams(params)
        if (!captureController.isMounted) {
            mountOverlayView(overlayView, params)
            return
        }
        runCatching {
            windowManager.updateViewLayout(overlayView, params)
        }.onFailure {
            stopSelf()
        }
    }

    private fun resetPillLayoutParams(params: WindowManager.LayoutParams) {
        val initialPosition = getInitialPosition()
        params.width = WindowManager.LayoutParams.WRAP_CONTENT
        params.height = WindowManager.LayoutParams.WRAP_CONTENT
        params.x = initialPosition.x
        params.y = initialPosition.y
    }

    private fun getInitialPosition(): UsageGuardCountdownOverlayLayoutPolicy.Position {
        return UsageGuardCountdownOverlayLayoutPolicy.initialPosition(
            marginPx = 12.dp.px.toInt(),
            statusBarHeightPx = BarUtils.getStatusBarHeight(),
        )
    }

    override fun onDestroy() {
        overlayLifecycleOwner?.onViewRemoved()
        overlayLifecycleOwner = null
        super.onDestroy()
        restoreOverlayJob?.cancel()
        restoreOverlayJob = null
        if (captureController.isMounted) {
            view?.let {
                runCatching {
                    windowManager.removeView(it)
                }
            }
        }
        UsageGuardEngine.onCountdownOverlayStopped(
            appId = appId.ifBlank { null },
            leaseId = overlayLeaseId.takeIf { it > 0L },
        )
        captureController.onDestroy()
        view = null
        layoutParams = null
        preservedPosition = null
        appId = ""
        recordId = 0L
        overlayLeaseId = 0L
        expiresAtState = 0L
        reasonTextState = UsageGuardCountdownOverlayPolicy.MISSING_REASON_TEXT
        showTerminateConfirm = false
    }
}
