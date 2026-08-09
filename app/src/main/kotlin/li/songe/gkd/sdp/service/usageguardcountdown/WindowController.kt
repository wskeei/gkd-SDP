@file:JvmName("UsageGuardCountdownWindowController")

package li.songe.gkd.sdp.service

import android.view.WindowManager

internal val USAGE_GUARD_COUNTDOWN_OVERLAY_FLAGS =
    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
        WindowManager.LayoutParams.FLAG_SECURE
