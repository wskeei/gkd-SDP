package li.songe.gkd.sdp.service

import li.songe.gkd.sdp.R
import android.app.Service
import android.content.Intent
import coil3.Bitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withTimeoutOrNull
import li.songe.gkd.sdp.app
import li.songe.gkd.sdp.notif.StopServiceReceiver
import li.songe.gkd.sdp.notif.screenshotNotif
import li.songe.gkd.sdp.util.DefaultSimpleLifeImpl
import li.songe.gkd.sdp.util.LogUtils
import li.songe.gkd.sdp.util.OnSimpleLife
import li.songe.gkd.sdp.util.ScreenshotUtil
import li.songe.gkd.sdp.util.componentName
import li.songe.gkd.sdp.util.stopServiceByClass

class ScreenshotService : Service(), OnSimpleLife by DefaultSimpleLifeImpl() {
    override fun onBind(intent: Intent?) = null
    override fun onCreate() = onCreated()
    override fun onDestroy() = onDestroyed()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            return super.onStartCommand(intent, flags, startId)
        } finally {
            intent?.let {
                screenshotUtil?.destroy()
                screenshotUtil = ScreenshotUtil(this, intent)
                LogUtils.d("screenshot restart")
            }
        }
    }

    private var screenshotUtil: ScreenshotUtil? = null

    init {
        useLogLifecycle()
        useAliveFlow(isRunning)
        useAliveToast(getString(R.string.s_df95c4025b))
        StopServiceReceiver.autoRegister()
        onCreated { screenshotNotif.notifyService() }
        onCreated { instance = this }
        onDestroyed {
            screenshotUtil?.destroy()
            instance = null
        }
    }

    companion object {
        private var instance: ScreenshotService? = null
        val isRunning = MutableStateFlow(false)
        suspend fun screenshot(): Bitmap? {
            if (!isRunning.value) return null
            return withTimeoutOrNull(5_000) {
                instance?.screenshotUtil?.execute()
            }
        }

        fun start(intent: Intent) {
            intent.component = ScreenshotService::class.componentName
            app.startForegroundService(intent)
        }

        fun stop() = stopServiceByClass(ScreenshotService::class)
    }
}
