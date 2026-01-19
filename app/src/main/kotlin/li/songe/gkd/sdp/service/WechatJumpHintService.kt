package li.songe.gkd.sdp.service

import android.content.Intent
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.delay
import li.songe.gkd.sdp.a11y.FocusModeEngine
import li.songe.gkd.sdp.ui.style.AppTheme
import li.songe.gkd.sdp.util.LogUtils

/**
 * 微信联系人跳转提示悬浮窗服务
 * 在左上角显示半透明提示窗，倒计时后检查是否在目标联系人聊天页
 */
class WechatJumpHintService : LifecycleService(), SavedStateRegistryOwner {

    companion object {
        const val EXTRA_CONTACT_NAME = "contact_name"
        const val EXTRA_WECHAT_ID = "wechat_id"
        const val COUNTDOWN_SECONDS = 10
        private const val TAG = "WechatJumpHintService"
    }

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
        
        val contactName = intent?.getStringExtra(EXTRA_CONTACT_NAME) ?: ""
        val wechatId = intent?.getStringExtra(EXTRA_WECHAT_ID) ?: ""
        
        if (contactName.isNotEmpty() && wechatId.isNotEmpty()) {
            showHintOverlay(contactName, wechatId)
        } else {
            LogUtils.d("$TAG: Missing contact info, stopping")
            stopSelf()
        }
        
        return START_NOT_STICKY
    }

    private fun showHintOverlay(contactName: String, wechatId: String) {
        if (view != null) {
            // 已经有一个悬浮窗了，移除它
            try {
                windowManager.removeView(view)
                view = null
            } catch (e: Exception) {
                LogUtils.d("$TAG: Failed to remove existing view: ${e.message}")
            }
        }

        view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@WechatJumpHintService)
            setViewTreeSavedStateRegistryOwner(this@WechatJumpHintService)
            setContent {
                AppTheme {
                    WechatJumpHintContent(
                        contactName = contactName,
                        wechatId = wechatId,
                        onCountdownComplete = { success ->
                            if (!success) {
                                // 未成功打开目标联系人，触发拦截
                                LogUtils.d("$TAG: Failed to open $contactName, triggering intercept")
                                FocusModeEngine.triggerWechatIntercept()
                            }
                            stopSelf()
                        }
                    )
                }
            }
        }

        // 左上角小窗口，不遮挡全屏
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 16  // 左边距
            y = 100 // 顶部距离（避开状态栏）
        }
        
        windowManager.addView(view, params)
        LogUtils.d("$TAG: Hint overlay shown for $contactName")
    }

    override fun onDestroy() {
        super.onDestroy()
        view?.let { 
            try {
                windowManager.removeView(it) 
            } catch (e: Exception) {
                LogUtils.d("$TAG: Failed to remove view on destroy: ${e.message}")
            }
        }
        view = null
    }
}

@Composable
private fun WechatJumpHintContent(
    contactName: String,
    wechatId: String,
    onCountdownComplete: (Boolean) -> Unit
) {
    var timeLeft by remember { mutableIntStateOf(WechatJumpHintService.COUNTDOWN_SECONDS) }
    var checkResult by remember { mutableStateOf<Boolean?>(null) }
    
    LaunchedEffect(Unit) {
        while (timeLeft > 0 && checkResult == null) {
            delay(1000)
            timeLeft--
            
            // 每秒检查一次是否已经打开了目标联系人
            val isOnTarget = FocusModeEngine.checkIfOnTargetChat(wechatId, contactName)
            LogUtils.d("WechatJumpHint: Check at ${timeLeft}s, result: $isOnTarget")
            
            if (isOnTarget) {
                // 成功打开，立即关闭
                checkResult = true
                onCountdownComplete(true)
                return@LaunchedEffect
            }
        }
        
        // 倒计时结束，最终检查
        if (checkResult == null) {
            val finalCheck = FocusModeEngine.checkIfOnTargetChat(wechatId, contactName)
            LogUtils.d("WechatJumpHint: Final check, result: $finalCheck")
            onCountdownComplete(finalCheck)
        }
    }
    
    // 如果已经检测成功，显示成功状态
    if (checkResult == true) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF4CAF50).copy(alpha = 0.9f))
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = "✓ 已打开",
                color = Color.White,
                fontSize = 14.sp
            )
        }
    } else {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black.copy(alpha = 0.75f))
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = "请在 ${timeLeft}s 内打开",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 12.sp
            )
            Text(
                text = contactName,
                color = Color.White,
                fontSize = 16.sp,
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}
