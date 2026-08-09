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
import li.songe.gkd.sdp.ui.style.AppTheme
import li.songe.gkd.sdp.util.BarUtils
import li.songe.gkd.sdp.util.LogUtils
import li.songe.gkd.sdp.util.ScreenUtils
import li.songe.gkd.sdp.util.UsageGuardCountdownOverlayCapturePolicy
import li.songe.gkd.sdp.util.UsageGuardCountdownOverlayLayoutPolicy
import li.songe.gkd.sdp.util.UsageGuardCountdownOverlayPolicy
import li.songe.gkd.sdp.util.px
import kotlin.math.roundToInt

internal val USAGE_GUARD_COUNTDOWN_OVERLAY_FLAGS =
    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
        WindowManager.LayoutParams.FLAG_SECURE

class UsageGuardCountdownOverlayService : LifecycleService(), SavedStateRegistryOwner {
    companion object {
        const val EXTRA_REASON_TEXT = "reasonText"
    }

    private val windowManager by lazy { getSystemService(WINDOW_SERVICE) as WindowManager }
    private var view: ComposeView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var overlayMounted = false
    private var restoreOverlayJob: Job? = null

    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry = savedStateRegistryController.savedStateRegistry

    private var appId: String = ""
    private var recordId: Long = 0L
    private var expiresAt: Long = 0L
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
        val incomingReasonText = UsageGuardCountdownOverlayPolicy.displayReasonText(
            intent?.getStringExtra(EXTRA_REASON_TEXT).orEmpty(),
        )
        val now = System.currentTimeMillis()
        if (incomingAppId.isBlank() || incomingRecordId <= 0L || incomingExpiresAt <= now) {
            stopSelf()
            return START_NOT_STICKY
        }

        val shouldResetPosition = UsageGuardCountdownOverlayLayoutPolicy.shouldResetPosition(
            previousAppId = appId,
            previousRecordId = recordId,
            nextAppId = incomingAppId,
            nextRecordId = incomingRecordId,
        )
        appId = incomingAppId
        recordId = incomingRecordId
        expiresAt = incomingExpiresAt
        expiresAtState = incomingExpiresAt
        reasonTextState = incomingReasonText
        if (view == null) {
            showOverlay()
        } else if (shouldResetPosition) {
            restoreOverlayJob?.cancel()
            restoreOverlayJob = null
            showTerminateConfirm = false
            resetPosition()
        }
        return START_NOT_STICKY
    }

    private fun showOverlay() {
        if (view != null) return

        val overlayView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@UsageGuardCountdownOverlayService)
            setViewTreeSavedStateRegistryOwner(this@UsageGuardCountdownOverlayService)
            setContent {
                AppTheme {
                    val horizontalMarginPx = 12.dp.px.roundToInt()
                    val maxPillWidthPx = UsageGuardCountdownOverlayLayoutPolicy.maxPillWidthPx(
                        screenWidthPx = ScreenUtils.getScreenWidth(),
                        horizontalMarginPx = horizontalMarginPx,
                    )
                    UsageGuardCountdownOverlayContent(
                        expiresAt = expiresAtState,
                        reasonText = reasonTextState,
                        maxPillWidthPx = maxPillWidthPx,
                        showTerminateConfirm = showTerminateConfirm,
                        onPillTap = { showTerminateConfirmScreen() },
                        onDrag = { dx, dy -> updatePosition(dx, dy) },
                        onExpired = { stopSelf() },
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
            x = initialPosition.x
            y = initialPosition.y
        }
        view = overlayView
        layoutParams = params
        mountOverlayView(overlayView, params)
    }

    private fun mountOverlayView(
        overlayView: ComposeView,
        params: WindowManager.LayoutParams,
    ) {
        if (overlayMounted) return
        runCatching {
            windowManager.addView(overlayView, params)
            overlayMounted = true
        }.onFailure { error ->
            LogUtils.d(
                "usage guard countdown overlay mount rejected",
                error::class.java.simpleName,
            )
            UsageGuardEngine.onOverlayMountFailed("countdown", appId)
            stopSelf()
        }
    }

    private fun hideOverlayForScreenshot() {
        val overlayView = view ?: return
        val params = layoutParams ?: return
        if (!overlayMounted) return
        val hiddenAppId = appId
        val hiddenRecordId = recordId
        val removed = runCatching {
            windowManager.removeView(overlayView)
        }.onFailure { error ->
            LogUtils.d(
                "usage guard countdown overlay temporary hide rejected",
                error::class.java.simpleName,
            )
        }.isSuccess
        if (!removed) return

        overlayMounted = false
        showTerminateConfirm = false
        resetPillLayoutParams(params)
        restoreOverlayJob?.cancel()
        restoreOverlayJob = lifecycleScope.launch {
            delay(UsageGuardCountdownOverlayCapturePolicy.HIDE_DURATION_MS)
            restoreOverlayJob = null
            restoreOverlayAfterScreenshot(hiddenAppId, hiddenRecordId)
        }
    }

    private fun restoreOverlayAfterScreenshot(
        hiddenAppId: String,
        hiddenRecordId: Long,
    ) {
        val now = System.currentTimeMillis()
        val shouldRestore = UsageGuardCountdownOverlayCapturePolicy.shouldRestore(
            hiddenAppId = hiddenAppId,
            hiddenRecordId = hiddenRecordId,
            currentAppId = appId,
            currentRecordId = recordId,
            expiresAt = expiresAt,
            now = now,
        )
        if (!shouldRestore) {
            if (
                hiddenAppId == appId &&
                hiddenRecordId == recordId &&
                expiresAt <= now
            ) {
                stopSelf()
            }
            return
        }
        val overlayView = view ?: return
        val params = layoutParams ?: return
        mountOverlayView(overlayView, params)
    }

    private fun showTerminateConfirmScreen() {
        if (!overlayMounted) return
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
        if (!overlayMounted) return
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
        if (showTerminateConfirm || !overlayMounted) return
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
        if (!overlayMounted) {
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
        super.onDestroy()
        restoreOverlayJob?.cancel()
        restoreOverlayJob = null
        if (overlayMounted) {
            view?.let {
                runCatching {
                    windowManager.removeView(it)
                }
            }
        }
        UsageGuardEngine.onCountdownOverlayStopped(appId.ifBlank { null })
        view = null
        layoutParams = null
        overlayMounted = false
        appId = ""
        recordId = 0L
        expiresAt = 0L
        expiresAtState = 0L
        reasonTextState = UsageGuardCountdownOverlayPolicy.MISSING_REASON_TEXT
        showTerminateConfirm = false
    }
}

@Composable
private fun UsageGuardCountdownOverlayContent(
    expiresAt: Long,
    reasonText: String,
    maxPillWidthPx: Int,
    showTerminateConfirm: Boolean,
    onPillTap: () -> Unit,
    onDrag: (Float, Float) -> Unit,
    onExpired: () -> Unit,
    onDismissTerminate: () -> Unit,
    onHideForScreenshot: () -> Unit,
    onConfirmTerminate: () -> Unit,
) {
    if (showTerminateConfirm) {
        UsageGuardTerminateConfirmScreen(
            onDismiss = onDismissTerminate,
            onHideForScreenshot = onHideForScreenshot,
            onConfirm = onConfirmTerminate,
        )
    } else {
        UsageGuardCountdownPill(
            expiresAt = expiresAt,
            reasonText = reasonText,
            maxPillWidthPx = maxPillWidthPx,
            onTap = onPillTap,
            onDrag = onDrag,
            onExpired = onExpired,
        )
    }
}

@Composable
private fun UsageGuardCountdownPill(
    expiresAt: Long,
    reasonText: String,
    maxPillWidthPx: Int,
    onTap: () -> Unit,
    onDrag: (Float, Float) -> Unit,
    onExpired: () -> Unit,
) {
    var now by remember(expiresAt) { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(expiresAt) {
        while (true) {
            now = System.currentTimeMillis()
            if (now >= expiresAt) {
                onExpired()
                break
            }
            delay(1_000L)
        }
    }
    val remainingText = UsageGuardCountdownOverlayPolicy.formatRemainingText(expiresAt, now)
    val maxPillWidth = with(LocalDensity.current) {
        maxPillWidthPx.toDp()
    }

    Surface(
        color = Color.Black.copy(alpha = 0.72f),
        shape = RoundedCornerShape(999.dp),
        modifier = Modifier
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onTap() })
            }
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount.x, dragAmount.y)
                }
            },
    ) {
        Row(
            modifier = Modifier
                .widthIn(max = maxPillWidth)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = remainingText,
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
            )
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(24.dp)
                    .background(Color.White.copy(alpha = 0.36f)),
            )
            Text(
                text = reasonText,
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
    }
}

@Composable
private fun UsageGuardTerminateConfirmScreen(
    onDismiss: () -> Unit,
    onHideForScreenshot: () -> Unit,
    onConfirm: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = "使用控制",
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = "隐藏悬浮条不会暂停本次使用；提前终止会将倒计时归零并立即回到桌面。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = onHideForScreenshot,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                ) {
                    Text("隐藏 10 秒用于截图")
                }
                Text(
                    text = "隐藏期间倒计时继续，之后自动恢复。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.align(Alignment.End),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("返回")
                    }
                    Button(onClick = onConfirm) {
                        Text("终止使用")
                    }
                }
            }
        }
    }
}
