package li.songe.gkd.sdp.service

import android.content.Intent
import android.graphics.PixelFormat
import android.view.WindowManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import kotlinx.coroutines.delay
import li.songe.gkd.sdp.ui.style.AppTheme
import li.songe.gkd.sdp.util.InterceptUtils

class InterceptOverlayService : LifecycleService(), SavedStateRegistryOwner {

    private val windowManager by lazy { getSystemService(WINDOW_SERVICE) as WindowManager }
    private var view: ComposeView? = null
    
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry = savedStateRegistryController.savedStateRegistry

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performAttach()
        savedStateRegistryController.performRestore(null)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        val subsId = intent?.getLongExtra("subsId", -1) ?: -1
        val groupKey = intent?.getIntExtra("groupKey", -1) ?: -1
        val message = intent?.getStringExtra("message") ?: "这真的重要吗？"
        val cooldown = intent?.getIntExtra("cooldown", 5) ?: 5

        if (subsId != -1L && groupKey != -1) {
            showOverlay(subsId, groupKey, message, cooldown)
        } else {
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun showOverlay(subsId: Long, groupKey: Int, message: String, cooldown: Int) {
        if (view != null) return

        view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@InterceptOverlayService)
            setViewTreeSavedStateRegistryOwner(this@InterceptOverlayService)
            setContent {
                AppTheme {
                    InterceptScreen(
                        message = message,
                        cooldown = cooldown,
                        onContinue = {
                            InterceptUtils.setAllowed(subsId, groupKey, cooldown)
                            stopSelf()
                        },
                        onExit = {
                            A11yService.instance?.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME)
                            stopSelf()
                        }
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
        
        windowManager.addView(view, params)
    }

    override fun onDestroy() {
        super.onDestroy()
        view?.let { windowManager.removeView(it) }
        view = null
    }
}

@Composable
fun InterceptScreen(
    message: String,
    cooldown: Int, // Kept for API compatibility but we use 10s for auto-exit
    onContinue: () -> Unit, // Kept for API compatibility but unused
    onExit: () -> Unit
) {
    var timeLeft by remember { mutableIntStateOf(10) }
    
    LaunchedEffect(Unit) {
        while (timeLeft > 0) {
            delay(1000)
            timeLeft--
        }
        onExit()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background // Opaque
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(48.dp))
            
            Button(
                onClick = onExit,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("算了 (退出) ${timeLeft}s")
            }
        }
    }
}
