package li.songe.gkd.sdp.a11y

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

object FetchOverlayController {
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var statusTextView: TextView? = null
    private var countTextView: TextView? = null

    fun show(context: Context) {
        if (overlayView != null) return

        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val layoutParams = WindowManager.LayoutParams().apply {
            type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            format = PixelFormat.TRANSLUCENT
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            width = WindowManager.LayoutParams.WRAP_CONTENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
            gravity = Gravity.BOTTOM or Gravity.END
            x = 30
            y = 300 // Avoid covering navigation bar
        }

        // Create UI programmatically
        val backgroundDrawable = GradientDrawable().apply {
            setColor(Color.parseColor("#CC000000")) // Semi-transparent black
            cornerRadius = 24f
        }

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = backgroundDrawable
            setPadding(24, 24, 24, 24)
        }

        statusTextView = TextView(context).apply {
            setTextColor(Color.WHITE)
            textSize = 14f
            text = "准备抓取..."
        }

        countTextView = TextView(context).apply {
            setTextColor(Color.GREEN)
            textSize = 12f
            text = "已获取: 0"
            setPadding(0, 8, 0, 8)
        }

        val stopButton = Button(context).apply {
            text = "停止"
            textSize = 12f
            setPadding(16, 0, 16, 0)
            setBackgroundColor(Color.RED)
            setTextColor(Color.WHITE)
            minHeight = 0
            minimumHeight = 0
            setOnClickListener {
                WechatContactFetcher.stopFetch()
            }
        }

        container.addView(statusTextView)
        container.addView(countTextView)
        container.addView(stopButton)

        val frame = FrameLayout(context)
        frame.addView(container)
        overlayView = frame

        try {
            windowManager?.addView(overlayView, layoutParams)
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
    }

    fun hide() {
        if (overlayView != null && windowManager != null) {
            try {
                windowManager?.removeView(overlayView)
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
            overlayView = null
            windowManager = null
            statusTextView = null
            countTextView = null
        }
    }

    fun update(state: FetchState) {
        // UI updates must happen on main thread
        overlayView?.post {
            statusTextView?.text = state.statusText
            countTextView?.text = "已获取: ${state.fetchedCount} 人"
            if (state.currentTarget != null) {
                statusTextView?.append("\n正在处理: ${state.currentTarget}")
            }
        }
    }
}