package li.songe.gkd.sdp.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import kotlinx.coroutines.flow.MutableStateFlow
import li.songe.gkd.sdp.app

/**
 * Compile/runtime seam for the accessibility guard overlay.
 *
 * Task 6 supplies the actual enforcement UI. Keeping the service entry point
 * here lets the coordinator own lifecycle decisions without embedding fake
 * enforcement behavior in its reconcile loop.
 */
class AccessibilityGuardOverlayService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning.value = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        isRunning.value = false
        super.onDestroy()
    }

    companion object {
        val isRunning = MutableStateFlow(false)

        fun start(context: Context = app) {
            context.startService(Intent(context, AccessibilityGuardOverlayService::class.java))
        }

        fun stop(context: Context = app) {
            context.stopService(Intent(context, AccessibilityGuardOverlayService::class.java))
        }
    }
}
