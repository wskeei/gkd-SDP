@file:JvmName("UsageGuardRequestWindowController")

package li.songe.gkd.sdp.service

import android.view.WindowManager

internal val USAGE_GUARD_REQUEST_OVERLAY_FLAGS =
    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
        WindowManager.LayoutParams.FLAG_SECURE

internal val USAGE_GUARD_REQUEST_OVERLAY_SOFT_INPUT_MODE =
    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
