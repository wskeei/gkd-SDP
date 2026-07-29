package li.songe.gkd.sdp.shizuku

import android.content.Context
import android.view.IWindowManager

class SafeWindowManager(val value: IWindowManager) {
    companion object {
        fun newBinder() = getShizukuService(Context.WINDOW_SERVICE)?.let {
            SafeWindowManager(IWindowManager.Stub.asInterface(it))
        }
    }
}