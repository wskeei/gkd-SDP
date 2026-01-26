package li.songe.gkd.sdp.receiver

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import li.songe.gkd.sdp.util.LogUtils

class AdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        LogUtils.d("Device Admin Enabled")
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        LogUtils.d("Device Admin Disabled")
    }
}
