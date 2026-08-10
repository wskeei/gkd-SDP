package li.songe.gkd.sdp.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import li.songe.gkd.sdp.a11y.A11yRuleEngine
import li.songe.gkd.sdp.appScope
import li.songe.gkd.sdp.util.LogUtils
import li.songe.gkd.sdp.util.SnapshotExt
import li.songe.gkd.sdp.util.launchTry
import li.songe.gkd.sdp.util.toast
import li.songe.gkd.sdp.R

class SnapshotTileService() : BaseTileService() {
    override val activeFlow = MutableStateFlow(false)

    init {
        onTileClicked { execSnapshot() }
    }
}

private fun execSnapshot() {
    LogUtils.d("SnapshotTileService::onClick")
    val service = A11yRuleEngine.instance
    if (service == null) {
        A11yRuleEngine.performActionBack()
        toast(li.songe.gkd.sdp.app.getString(R.string.s_035bb01220), forced = true)
        return
    }
    appScope.launchTry(Dispatchers.IO) {
        val oldAppId = service.safeActiveWindowAppId

        if (oldAppId == null) {
            A11yRuleEngine.performActionBack()
            toast(li.songe.gkd.sdp.app.getString(R.string.s_1acf004269), forced = true)
            return@launchTry
        }

        val startTime = System.currentTimeMillis()
        fun timeout(): Boolean {
            return System.currentTimeMillis() - startTime > 3000L
        }

        var ok = false
        while (isActive) {
            val latestAppId = service.safeActiveWindowAppId
            if (latestAppId == null) {
                // https://github.com/gkd-kit/gkd/issues/713
                delay(250)
                if (timeout()) {
                    toast(li.songe.gkd.sdp.app.getString(R.string.s_b4450d038a), forced = true)
                    break
                }
            } else if (latestAppId != oldAppId) {
                ok = true
                LogUtils.d("SnapshotTileService::eventExecutor.execute")
                appScope.launchTry { SnapshotExt.captureSnapshot(forcedCropStatusBar = true) }
                break
            } else {
                A11yRuleEngine.performActionBack()
                delay(500)
                if (timeout()) {
                    toast(li.songe.gkd.sdp.app.getString(R.string.s_c6d8dd919b), forced = true)
                    break
                }
            }
        }
        if (!ok) {
            A11yRuleEngine.performActionBack()
        }
    }
}
