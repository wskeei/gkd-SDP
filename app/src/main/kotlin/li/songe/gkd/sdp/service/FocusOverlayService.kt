package li.songe.gkd.sdp.service

import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.widget.Toast
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
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
import li.songe.gkd.sdp.a11y.FocusModeEngine
import li.songe.gkd.sdp.ui.component.AppIcon
import li.songe.gkd.sdp.ui.share.ServiceOverlayLifecycleOwner
import li.songe.gkd.sdp.ui.style.AppTheme
import li.songe.gkd.sdp.util.LogUtils
import li.songe.gkd.sdp.util.FocusTimeFormatter
import li.songe.gkd.sdp.util.json
import androidx.compose.ui.res.stringResource
import li.songe.gkd.sdp.R

class FocusOverlayService : LifecycleService(), SavedStateRegistryOwner {

    private val windowManager by lazy { getSystemService(WINDOW_SERVICE) as WindowManager }
    private var view: ComposeView? = null
    private var overlayLifecycleOwner: ServiceOverlayLifecycleOwner? = null

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

        val lifecycleOwner = ServiceOverlayLifecycleOwner()
        overlayLifecycleOwner = lifecycleOwner
        view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(this@FocusOverlayService)
            setContent {
                AppTheme {
                    FocusInterceptScreen(
                        message = message,
                        whitelist = whitelist,
                        blockedApp = blockedApp,
                        isLocked = isLocked,
                        endTime = endTime,
                        onSessionExpired = { stopSelf() },
                        onOpenApp = { packageName ->
                            try {
                                val launchIntent = app.packageManager.getLaunchIntentForPackage(packageName)
                                if (launchIntent != null) {
                                    // 使用正确的启动标志
                                    launchIntent.addFlags(
                                        Intent.FLAG_ACTIVITY_NEW_TASK or
                                        Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED or
                                        Intent.FLAG_ACTIVITY_CLEAR_TOP
                                    )

                                    // 启动应用
                                    startActivity(launchIntent)

                                    // 延迟关闭服务，确保应用已启动
                                    Handler(Looper.getMainLooper()).postDelayed({
                                        stopSelf()
                                    }, 300)
                                } else {
                                    Toast.makeText(this@FocusOverlayService, "无法启动该应用", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                Toast.makeText(this@FocusOverlayService, "启动失败：${e.message}", Toast.LENGTH_SHORT).show()
                            }
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

        runCatching {
            windowManager.addView(view, params)
            lifecycleOwner.onViewAdded()
        }.onFailure { error ->
            view?.let { runCatching { windowManager.removeViewImmediate(it) } }
            lifecycleOwner.onViewRemoved()
            overlayLifecycleOwner = null
            view = null
            LogUtils.d("focus overlay mount rejected", error::class.java.simpleName)
            FocusModeEngine.clearCooldown()
            stopSelf()
        }
    }

    override fun onDestroy() {
        overlayLifecycleOwner?.onViewRemoved()
        overlayLifecycleOwner = null
        super.onDestroy()
        view?.let { runCatching { windowManager.removeView(it) } }
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
    onSessionExpired: () -> Unit,
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
                onSessionExpired = onSessionExpired,
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
    onSessionExpired: () -> Unit,
    onShowWhitelist: () -> Unit
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(endTime) {
        if (endTime <= 0L) return@LaunchedEffect
        while (true) {
            val current = System.currentTimeMillis()
            now = current
            if (current >= endTime) {
                onSessionExpired()
                break
            }
            kotlinx.coroutines.delay(1_000L)
        }
    }

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

        FocusTimeFormatter.formatRemainingText(endTime = endTime, now = now)?.let { remaining ->
            Text(
                text = remaining,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (isLocked) {
            Text(
                text = stringResource(R.string.s_62da9fb9ac),
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
                Text(stringResource(R.string.s_1a2443f7d2))
            }
        } else {
            Text(
                text = li.songe.gkd.sdp.app.getString(R.string.s_877a0e2923),
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
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = li.songe.gkd.sdp.app.getString(R.string.s_11d0241540))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = li.songe.gkd.sdp.app.getString(R.string.s_a63ec9e8f8),
                style = MaterialTheme.typography.titleLarge
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (whitelist.isEmpty()) {
            Text(
                text = stringResource(R.string.s_877a0e2923),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                modifier = Modifier.padding(16.dp)
            )
        } else {
            LazyColumn {
                // 应用白名单
                if (whitelist.isNotEmpty()) {
                    item {
                        Text(
                            text = li.songe.gkd.sdp.app.getString(R.string.s_8a87deaa49),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
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
