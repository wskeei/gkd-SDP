package li.songe.gkd.sdp.service

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.provider.Settings
import android.view.WindowManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import li.songe.gkd.sdp.R
import li.songe.gkd.sdp.app
import li.songe.gkd.sdp.ui.component.AppIcon
import li.songe.gkd.sdp.ui.style.AppTheme
import li.songe.gkd.sdp.util.LogUtils
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Full-screen accessibility permission reminder.
 *
 * This is deliberately a normal (non-foreground) overlay service. The
 * StatusService owns the decision to keep it alive; this service only owns a
 * single Compose view and removes it whenever its lifecycle ends.
 */
class AccessibilityGuardOverlayService : LifecycleService(), SavedStateRegistryOwner {
    private val windowManager by lazy { getSystemService(WINDOW_SERVICE) as WindowManager }
    private var view: ComposeView? = null
    private var activeRequestToken = Long.MIN_VALUE
    private val homeClickHandled = AtomicBoolean(false)

    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry = savedStateRegistryController.savedStateRegistry

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performAttach()
        savedStateRegistryController.performRestore(null)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        val token = intent?.getLongExtra(EXTRA_REQUEST_TOKEN, Long.MIN_VALUE)
            ?: Long.MIN_VALUE
        // A stop request can race an already queued startService call. Ignore
        // stale commands rather than allowing a removed overlay to reappear.
        if (!isStartAllowed(token)) {
            // Do not leave an empty service instance around after an old start
            // command arrives. If a newer request already attached a view,
            // leave that active instance untouched.
            if (view == null) stopSelf(startId)
            return START_NOT_STICKY
        }
        activeRequestToken = token
        if (view == null) showOverlay(token)
        return START_NOT_STICKY
    }

    private fun isStartAllowed(token: Long): Boolean = synchronized(requestLock) {
        requested && token == requestSequence.get()
    }

    private fun showOverlay(token: Long) {
        if (view != null || !isStartAllowed(token)) return
        if (!Settings.canDrawOverlays(this)) {
            LogUtils.d("AccessibilityGuard overlay skipped: draw-overlays permission is unavailable")
            stopSelf()
            return
        }

        val overlayView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@AccessibilityGuardOverlayService)
            setViewTreeSavedStateRegistryOwner(this@AccessibilityGuardOverlayService)
            setContent {
                AppTheme {
                    AccessibilityGuardOverlayContent(
                        appName = app.getString(R.string.app_name),
                        onGoHome = ::goHome,
                    )
                }
            }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        )

        // Keep the requested-state check and addView in one critical section.
        // stop() can be called from a coordinator thread while this service is
        // being started; it must not leave a view behind after a reset.
        synchronized(requestLock) {
            if (!requested || requestSequence.get() != token || view != null) {
                overlayView.disposeComposition()
                return
            }
            try {
                windowManager.addView(overlayView, params)
                view = overlayView
                _isRunning.value = true
            } catch (e: WindowManager.BadTokenException) {
                overlayView.disposeComposition()
                LogUtils.d("AccessibilityGuard overlay rejected by WindowManager", e)
                stopSelf()
            } catch (e: SecurityException) {
                overlayView.disposeComposition()
                LogUtils.d("AccessibilityGuard overlay denied by WindowManager", e)
                stopSelf()
            } catch (e: RuntimeException) {
                // OEM WindowManager implementations sometimes report a revoked
                // overlay permission as IllegalArgumentException. Keep the
                // guard coordinator alive even in that case.
                overlayView.disposeComposition()
                LogUtils.d("AccessibilityGuard overlay could not be attached", e)
                stopSelf()
            }
        }
    }

    private fun goHome() {
        if (!homeClickHandled.compareAndSet(false, true)) return
        // stopSelf() is intentionally first: the app should never remain
        // covered while its home activity is being brought to the foreground.
        stopSelf()
        runCatching { app.startLaunchActivity() }
            .onFailure { LogUtils.d("AccessibilityGuard failed to open app home", it) }
    }

    override fun onDestroy() {
        removeOverlayView()
        _isRunning.value = false
        synchronized(requestLock) {
            // A newer start/stop request wins over this instance's teardown.
            // Clearing only our own token prevents an old service instance
            // from cancelling a freshly queued start.
            if (requestSequence.get() == activeRequestToken) requested = false
        }
        super.onDestroy()
    }

    private fun removeOverlayView() {
        val currentView = synchronized(requestLock) {
            val current = view
            view = null
            current
        } ?: return

        try {
            windowManager.removeView(currentView)
        } catch (e: WindowManager.BadTokenException) {
            LogUtils.d("AccessibilityGuard overlay had an invalid removal token", e)
        } catch (e: SecurityException) {
            LogUtils.d("AccessibilityGuard overlay removal denied", e)
        } catch (e: IllegalArgumentException) {
            // The system may have removed the window already (for example,
            // after revoking SYSTEM_ALERT_WINDOW while the service stops).
            LogUtils.d("AccessibilityGuard overlay was already removed", e)
        } catch (e: RuntimeException) {
            LogUtils.d("AccessibilityGuard overlay removal failed", e)
        } finally {
            currentView.disposeComposition()
        }
    }

    companion object {
        private const val EXTRA_REQUEST_TOKEN =
            "li.songe.gkd.sdp.accessibility_guard.overlay_request_token"
        private val requestLock = Any()
        private val requestSequence = AtomicLong(0L)
        private var requested = false

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        /**
         * Starts a fresh request and cancels other full-screen overlays first.
         * The token is carried in the Intent so queued starts cannot resurrect
         * a service after a subsequent stop/reset.
         */
        fun start(context: Context = app) {
            val token = synchronized(requestLock) {
                requested = true
                requestSequence.incrementAndGet()
            }
            stopCompetingOverlays(context)
            try {
                context.startService(
                    Intent(context, AccessibilityGuardOverlayService::class.java)
                        .putExtra(EXTRA_REQUEST_TOKEN, token),
                )
            } catch (e: RuntimeException) {
                // Android may reject a background service start (or an OEM may
                // revoke the ability to start an overlay service). This is a
                // degraded enforcement path, not a reason to take down the
                // StatusService process.
                synchronized(requestLock) {
                    if (requestSequence.get() == token) requested = false
                }
                _isRunning.value = false
                LogUtils.d("AccessibilityGuard overlay service could not start", e)
            }
        }

        /** Stops both the active view and any queued start request. */
        fun stop(context: Context = app) {
            synchronized(requestLock) {
                requested = false
                requestSequence.incrementAndGet()
            }
            _isRunning.value = false
            context.stopService(Intent(context, AccessibilityGuardOverlayService::class.java))
        }

        private fun stopCompetingOverlays(context: Context) {
            arrayOf(
                FocusOverlayService::class.java,
                AppBlockerOverlayService::class.java,
                InterceptOverlayService::class.java,
                UsageGuardRequestOverlayService::class.java,
                UsageGuardTimeoutOverlayService::class.java,
                UsageGuardCountdownOverlayService::class.java,
            ).forEach { serviceClass ->
                runCatching { context.stopService(Intent(context, serviceClass)) }
                    .onFailure { LogUtils.d("AccessibilityGuard failed to stop competing overlay", it) }
            }
        }
    }

}

@Composable
private fun AccessibilityGuardOverlayContent(
    appName: String,
    onGoHome: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            AppIcon(appId = app.packageName)
            Text(
                text = appName,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = 16.dp),
            )
            Text(
                text = "无障碍权限已关闭",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 24.dp),
            )
            Text(
                text = "为保证已启用的自动化功能正常工作，请返回应用重新开启无障碍权限。",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp, bottom = 32.dp),
            )
            Button(
                onClick = onGoHome,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("前往")
            }
        }
    }
}
