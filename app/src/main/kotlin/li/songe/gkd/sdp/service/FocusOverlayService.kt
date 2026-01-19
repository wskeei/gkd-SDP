package li.songe.gkd.sdp.service

import android.content.Intent
import android.graphics.PixelFormat
import android.view.WindowManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import li.songe.gkd.sdp.app
import li.songe.gkd.sdp.ui.component.AppIcon
import li.songe.gkd.sdp.ui.style.AppTheme
import li.songe.gkd.sdp.util.json

class FocusOverlayService : LifecycleService(), SavedStateRegistryOwner {

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
        val message = intent?.getStringExtra("message") ?: "专注当下"
        val whitelistJson = intent?.getStringExtra("whitelist") ?: "[]"
        val blockedApp = intent?.getStringExtra("blockedApp") ?: ""
        val isLocked = intent?.getBooleanExtra("isLocked", false) ?: false
        val endTime = intent?.getLongExtra("endTime", 0L) ?: 0L

        val whitelist = try {
            json.decodeFromString<List<String>>(whitelistJson)
        } catch (e: Exception) {
            emptyList()
        }

        showOverlay(message, whitelist, blockedApp, isLocked, endTime)
        return START_NOT_STICKY
    }

    private fun showOverlay(
        message: String,
        whitelist: List<String>,
        blockedApp: String,
        isLocked: Boolean,
        endTime: Long
    ) {
        if (view != null) return

        view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@FocusOverlayService)
            setViewTreeSavedStateRegistryOwner(this@FocusOverlayService)
            setContent {
                AppTheme {
                    FocusInterceptScreen(
                        message = message,
                        whitelist = whitelist,
                        blockedApp = blockedApp,
                        isLocked = isLocked,
                        endTime = endTime,
                        onOpenApp = { packageName ->
                            try {
                                val launchIntent = app.packageManager.getLaunchIntentForPackage(packageName)
                                if (launchIntent != null) {
                                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    startActivity(launchIntent)
                                }
                            } catch (e: Exception) {
                                // Ignore
                            }
                            stopSelf()
                        }
                    )
                }
            }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )

        windowManager.addView(view, params)
    }

    override fun onDestroy() {
        super.onDestroy()
        view?.let { windowManager.removeView(it) }
        view = null
    }
}

@Composable
fun FocusInterceptScreen(
    message: String,
    whitelist: List<String>,
    blockedApp: String,
    isLocked: Boolean,
    endTime: Long,
    onOpenApp: (String) -> Unit
) {
    var showWhitelistPicker by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        if (showWhitelistPicker) {
            WhitelistPickerContent(
                whitelist = whitelist,
                onBack = { showWhitelistPicker = false },
                onSelectApp = onOpenApp
            )
        } else {
            MainInterceptContent(
                message = message,
                whitelist = whitelist,
                isLocked = isLocked,
                endTime = endTime,
                onShowWhitelist = { showWhitelistPicker = true }
            )
        }
    }
}

@Composable
private fun MainInterceptContent(
    message: String,
    whitelist: List<String>,
    isLocked: Boolean,
    endTime: Long,
    onShowWhitelist: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 显示剩余时间（如果有结束时间）
        if (endTime > 0) {
            val remainingMinutes = ((endTime - System.currentTimeMillis()) / 60000).coerceAtLeast(0)
            if (remainingMinutes > 0) {
                Text(
                    text = if (remainingMinutes >= 60) {
                        "剩余 ${remainingMinutes / 60} 小时 ${remainingMinutes % 60} 分钟"
                    } else {
                        "剩余 $remainingMinutes 分钟"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        if (isLocked) {
            Text(
                text = "（已锁定，无法提前结束）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
            )
        }

        Spacer(modifier = Modifier.height(48.dp))

        if (whitelist.isNotEmpty()) {
            Button(
                onClick = onShowWhitelist,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("打开白名单应用")
            }
        } else {
            Text(
                text = "暂无白名单应用",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun WhitelistPickerContent(
    whitelist: List<String>,
    onBack: () -> Unit,
    onSelectApp: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "选择白名单应用",
                style = MaterialTheme.typography.titleLarge
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (whitelist.isEmpty()) {
            Text(
                text = "暂无白名单应用",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                modifier = Modifier.padding(16.dp)
            )
        } else {
            LazyColumn {
                items(whitelist) { packageName ->
                    WhitelistAppItem(
                        packageName = packageName,
                        onClick = { onSelectApp(packageName) }
                    )
                }
            }
        }
    }
}

@Composable
private fun WhitelistAppItem(
    packageName: String,
    onClick: () -> Unit
) {
    val appInfo = remember(packageName) {
        try {
            app.packageManager.getApplicationInfo(packageName, 0)
        } catch (e: Exception) {
            null
        }
    }

    val appName = remember(appInfo) {
        appInfo?.let {
            try {
                app.packageManager.getApplicationLabel(it).toString()
            } catch (e: Exception) {
                packageName
            }
        } ?: packageName
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIcon(appId = packageName)

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = appName,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
