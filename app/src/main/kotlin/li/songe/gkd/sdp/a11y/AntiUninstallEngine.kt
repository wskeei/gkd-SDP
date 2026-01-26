package li.songe.gkd.sdp.a11y

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import li.songe.gkd.sdp.service.A11yService
import li.songe.gkd.sdp.util.FocusLockUtils
import li.songe.gkd.sdp.util.toast

object AntiUninstallEngine {
    private var lastToastTime = 0L
    private const val TOAST_COOLDOWN = 3000L

    fun onAccessibilityEvent(event: AccessibilityEvent, service: A11yService) {
        // 1. 检查锁定状态
        if (!FocusLockUtils.isAntiUninstallLocked()) return

        // 2. 只关注窗口变化或内容变化
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            return
        }

        // 3. 检查包名是否为设置
        if (event.packageName != "com.android.settings") return

        // 4. 检查是否为设备管理器页面
        val className = event.className?.toString() ?: ""
        if (className.contains("DeviceAdmin", ignoreCase = true)) {
            // 5. 阻止进入：强制返回
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            
            // 6. 显示提示 (带冷却)
            val now = System.currentTimeMillis()
            if (now - lastToastTime > TOAST_COOLDOWN) {
                lastToastTime = now
                toast("防卸载保护锁定中，无法取消激活")
            }
        }
    }
}
