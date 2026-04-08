package li.songe.gkd.sdp.service

import android.content.Intent
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.delay
import li.songe.gkd.sdp.a11y.UsageGuardEngine
import li.songe.gkd.sdp.ui.style.AppTheme
import li.songe.gkd.sdp.util.ScreenUtils
import li.songe.gkd.sdp.util.UsageGuardCountdownOverlayPolicy
import li.songe.gkd.sdp.util.px
import kotlin.math.roundToInt

class UsageGuardCountdownOverlayService : LifecycleService(), SavedStateRegistryOwner {
    private val windowManager by lazy { getSystemService(WINDOW_SERVICE) as WindowManager }
    private var view: ComposeView? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry = savedStateRegistryController.savedStateRegistry

    private var appId: String = ""
    private var recordId: Long = 0L
    private var expiresAt: Long = 0L
    private var expiresAtState by mutableStateOf(0L)

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
        val now = System.currentTimeMillis()
        if (incomingAppId.isBlank() || incomingRecordId <= 0L || incomingExpiresAt <= now) {
            stopSelf()
            return START_NOT_STICKY
        }

        appId = incomingAppId
        recordId = incomingRecordId
        expiresAt = incomingExpiresAt
        expiresAtState = incomingExpiresAt
        if (view == null) {
            showOverlay()
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
                    UsageGuardCountdownPill(
                        expiresAt = expiresAtState,
                        onDrag = { dx, dy -> updatePosition(dx, dy) },
                        onExpired = { stopSelf() },
                    )
                }
            }
        }

        val margin = 12.dp.px.toInt()
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.START or Gravity.TOP
            x = margin
            y = margin
        }
        view = overlayView
        layoutParams = params
        runCatching {
            windowManager.addView(overlayView, params)
        }.onFailure {
            view = null
            layoutParams = null
            stopSelf()
        }
    }

    private fun updatePosition(dx: Float, dy: Float) {
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

    override fun onDestroy() {
        super.onDestroy()
        view?.let {
            runCatching {
                windowManager.removeView(it)
            }
        }
        UsageGuardEngine.onCountdownOverlayStopped(appId.ifBlank { null })
        view = null
        layoutParams = null
        appId = ""
        recordId = 0L
        expiresAt = 0L
        expiresAtState = 0L
    }
}

@Composable
private fun UsageGuardCountdownPill(
    expiresAt: Long,
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

    Surface(
        color = Color.Black.copy(alpha = 0.72f),
        shape = RoundedCornerShape(999.dp),
        modifier = Modifier.pointerInput(Unit) {
            detectDragGestures { change, dragAmount ->
                change.consume()
                onDrag(dragAmount.x, dragAmount.y)
            }
        },
    ) {
        Text(
            text = remainingText,
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}
