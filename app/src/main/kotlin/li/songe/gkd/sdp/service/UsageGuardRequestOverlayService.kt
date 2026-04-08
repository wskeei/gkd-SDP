package li.songe.gkd.sdp.service

import android.content.Intent
import android.graphics.PixelFormat
import android.view.WindowManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import li.songe.gkd.sdp.a11y.UsageGuardEngine
import li.songe.gkd.sdp.data.UsageGuardRecord
import li.songe.gkd.sdp.data.UsageGuardTag
import li.songe.gkd.sdp.db.DbSet
import li.songe.gkd.sdp.ui.style.AppTheme
import li.songe.gkd.sdp.util.UsageGuardPolicy

class UsageGuardRequestOverlayService : LifecycleService(), SavedStateRegistryOwner {
    private val windowManager by lazy { getSystemService(WINDOW_SERVICE) as WindowManager }
    private var view: ComposeView? = null

    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry = savedStateRegistryController.savedStateRegistry

    private var appId: String = ""
    private var appName: String = ""
    private var grantMode: Int = UsageGuardPolicy.GRANT_MODE_RESUMABLE
    private var minReasonLength: Int = 8

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performAttach()
        savedStateRegistryController.performRestore(null)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (view != null) return START_NOT_STICKY
        appId = intent?.getStringExtra("appId").orEmpty()
        appName = intent?.getStringExtra("appName").orEmpty().ifBlank { appId }
        grantMode = intent?.getIntExtra("grantMode", UsageGuardPolicy.GRANT_MODE_RESUMABLE)
            ?: UsageGuardPolicy.GRANT_MODE_RESUMABLE
        minReasonLength = intent?.getIntExtra("minReasonLength", 8) ?: 8
        showOverlay()
        return START_NOT_STICKY
    }

    private fun showOverlay() {
        if (view != null) return

        view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@UsageGuardRequestOverlayService)
            setViewTreeSavedStateRegistryOwner(this@UsageGuardRequestOverlayService)
            setContent {
                AppTheme {
                    val tags by DbSet.usageGuardTagDao.queryAll().collectAsState(initial = emptyList())
                    UsageGuardRequestContent(
                        appName = appName,
                        tags = tags,
                        grantMode = grantMode,
                        minReasonLength = minReasonLength,
                        onAddTag = { name, existing ->
                            lifecycleScope.launch(Dispatchers.IO) {
                                addCustomTag(name, existing)
                            }
                        },
                        onSubmit = { selectedTags, reason, requestedDurationMinutes ->
                            lifecycleScope.launch(Dispatchers.IO) {
                                val now = System.currentTimeMillis()
                                DbSet.usageGuardRecordDao.getActiveRecord(appId)?.let { current ->
                                    DbSet.usageGuardRecordDao.closeRecord(
                                        id = current.id,
                                        endedAt = now,
                                        endReason = UsageGuardRecord.END_REASON_REPLACED,
                                    )
                                }
                                DbSet.usageGuardRecordDao.insert(
                                    UsageGuardRecord(
                                        appId = appId,
                                        appName = appName,
                                        tagNames = selectedTags,
                                        reasonText = reason.trim(),
                                        grantMode = grantMode,
                                        requestedDurationMinutes = requestedDurationMinutes,
                                        requestedAt = now,
                                        grantedAt = now,
                                        expiresAt = now + requestedDurationMinutes * 60_000L,
                                    )
                                )
                                UsageGuardEngine.onRequestGranted(appId)
                                stopSelf()
                            }
                        },
                        onCancel = {
                            A11yService.instance?.performGlobalAction(
                                android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME
                            )
                            stopSelf()
                        },
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

    private suspend fun addCustomTag(name: String, existing: List<UsageGuardTag>) {
        val normalized = name.trim()
        if (normalized.isBlank()) return
        val duplicated = existing.any { it.name.equals(normalized, ignoreCase = true) }
        if (duplicated) return
        DbSet.usageGuardTagDao.insert(
            UsageGuardTag(name = normalized, isPreset = false),
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        view?.let { windowManager.removeView(it) }
        view = null
        UsageGuardEngine.onRequestOverlayStopped(appId.ifBlank { null })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UsageGuardRequestContent(
    appName: String,
    tags: List<UsageGuardTag>,
    grantMode: Int,
    minReasonLength: Int,
    onAddTag: (String, List<UsageGuardTag>) -> Unit,
    onSubmit: (List<String>, String, Int) -> Unit,
    onCancel: () -> Unit,
) {
    val durationOptions = listOf(10, 15, 30, 60)

    var selectedTags by remember { mutableStateOf(setOf<String>()) }
    var reasonText by remember { mutableStateOf("") }
    var selectedDuration by remember { mutableStateOf(10) }
    var customMinutesText by remember { mutableStateOf("") }
    var newTagText by remember { mutableStateOf("") }

    var reasonError by remember { mutableStateOf<String?>(null) }
    var durationError by remember { mutableStateOf<String?>(null) }
    var tagsError by remember { mutableStateOf<String?>(null) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "使用申请",
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = "申请打开 $appName",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = if (grantMode == UsageGuardPolicy.GRANT_MODE_STRICT) {
                    "严格模式：离开应用后需要重新申请"
                } else {
                    "普通模式：到时前可继续回到应用"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )

            Text("选择标签", style = MaterialTheme.typography.titleSmall)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                tags.forEach { tag ->
                    FilterChip(
                        selected = selectedTags.contains(tag.name),
                        onClick = {
                            tagsError = null
                            selectedTags = if (selectedTags.contains(tag.name)) {
                                selectedTags - tag.name
                            } else {
                                selectedTags + tag.name
                            }
                        },
                        label = { Text(tag.name) },
                    )
                }
            }
            tagsError?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = newTagText,
                    onValueChange = { newTagText = it },
                    modifier = Modifier.weight(1f),
                    label = { Text("添加标签") },
                    singleLine = true,
                )
                Button(
                    onClick = {
                        val normalized = newTagText.trim()
                        if (normalized.isBlank()) return@Button
                        onAddTag(normalized, tags)
                        selectedTags = selectedTags + normalized
                        newTagText = ""
                    },
                ) {
                    Text("加入")
                }
            }

            OutlinedTextField(
                value = reasonText,
                onValueChange = {
                    reasonError = null
                    reasonText = it
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("申请理由") },
                supportingText = { Text("至少 $minReasonLength 个字") },
                isError = reasonError != null,
                minLines = 3,
            )
            reasonError?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Text("申请时长", style = MaterialTheme.typography.titleSmall)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                durationOptions.forEach { minutes ->
                    FilterChip(
                        selected = selectedDuration == minutes && customMinutesText.isBlank(),
                        onClick = {
                            durationError = null
                            selectedDuration = minutes
                            customMinutesText = ""
                        },
                        label = { Text("${minutes}分钟") },
                    )
                }
            }

            OutlinedTextField(
                value = customMinutesText,
                onValueChange = {
                    durationError = null
                    if (it.all(Char::isDigit)) {
                        customMinutesText = it
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("自定义分钟数") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                isError = durationError != null,
            )
            durationError?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    val requestedDurationMinutes = customMinutesText.toIntOrNull() ?: selectedDuration
                    val validation = UsageGuardPolicy.validateRequest(
                        selectedTags = selectedTags.toList(),
                        reason = reasonText,
                        minReasonLength = minReasonLength,
                        requestedDurationMinutes = requestedDurationMinutes,
                    )
                    tagsError = validation.tagsError
                    reasonError = validation.reasonError
                    durationError = validation.durationError
                    if (validation.accepted) {
                        onSubmit(selectedTags.toList(), reasonText, requestedDurationMinutes)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("开始使用")
            }
            TextButton(
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("取消")
            }
        }
    }
}
