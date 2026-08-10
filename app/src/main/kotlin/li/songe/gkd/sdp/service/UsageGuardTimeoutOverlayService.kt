package li.songe.gkd.sdp.service

import android.content.Intent
import android.graphics.PixelFormat
import android.view.WindowManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import li.songe.gkd.sdp.a11y.A11yRuleEngine
import li.songe.gkd.sdp.a11y.UsageGuardEngine
import li.songe.gkd.sdp.ui.style.AppTheme
import li.songe.gkd.sdp.ui.share.ServiceOverlayLifecycleOwner
import li.songe.gkd.sdp.util.LogUtils

class UsageGuardTimeoutOverlayService : LifecycleService(), SavedStateRegistryOwner {
    private val windowManager by lazy { getSystemService(WINDOW_SERVICE) as WindowManager }
    private var view: ComposeView? = null
    private var overlayLifecycleOwner: ServiceOverlayLifecycleOwner? = null

    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry = savedStateRegistryController.savedStateRegistry

    private var appId: String = ""
    private var recordId: Long = 0L
    private var reasonText: String = ""

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performAttach()
        savedStateRegistryController.performRestore(null)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (view != null) return START_NOT_STICKY
        appId = intent?.getStringExtra("appId").orEmpty()
        recordId = intent?.getLongExtra("recordId", 0L) ?: 0L
        reasonText = intent?.getStringExtra("reasonText").orEmpty()
        showOverlay()
        return START_NOT_STICKY
    }

    private fun showOverlay() {
        if (view != null) return

        val lifecycleOwner = ServiceOverlayLifecycleOwner()
        overlayLifecycleOwner = lifecycleOwner
        view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(this@UsageGuardTimeoutOverlayService)
            setContent {
                AppTheme {
                    UsageGuardTimeoutScreen(
                        reasonText = reasonText,
                        onGoHome = {
                            lifecycleScope.launch(Dispatchers.IO) {
                                UsageGuardEngine.markRecordHomeButton(recordId)
                                A11yRuleEngine.performActionHome()
                                stopSelf()
                            }
                        },
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
            PixelFormat.TRANSLUCENT
        )
        runCatching {
            windowManager.addView(view, params)
            lifecycleOwner.onViewAdded()
        }.onFailure { error ->
            view?.let { runCatching { windowManager.removeViewImmediate(it) } }
            lifecycleOwner.onViewRemoved()
            overlayLifecycleOwner = null
            view = null
            LogUtils.d("usage guard timeout overlay mount rejected", error::class.java.simpleName)
            UsageGuardEngine.onOverlayMountFailed("timeout", appId)
            stopSelf()
        }
    }

    override fun onDestroy() {
        overlayLifecycleOwner?.onViewRemoved()
        overlayLifecycleOwner = null
        super.onDestroy()
        view?.let { runCatching { windowManager.removeView(it) } }
        view = null
        UsageGuardEngine.onTimeoutOverlayStopped(appId.ifBlank { null })
    }
}

@Composable
private fun UsageGuardTimeoutScreen(
    reasonText: String,
    onGoHome: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "时间已到",
                style = MaterialTheme.typography.displaySmall,
            )
            Text(
                text = reasonText,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 16.dp, bottom = 32.dp),
            )
            Button(
                onClick = onGoHome,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("回到桌面")
            }
        }
    }
}
