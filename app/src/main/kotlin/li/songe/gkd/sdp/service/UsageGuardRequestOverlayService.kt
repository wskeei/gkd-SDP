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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import li.songe.gkd.sdp.a11y.A11yRuleEngine
import li.songe.gkd.sdp.appScope
import li.songe.gkd.sdp.util.LogUtils
import li.songe.gkd.sdp.a11y.UsageGuardEngine
import li.songe.gkd.sdp.data.UsageGuardRecord
import li.songe.gkd.sdp.data.UsageGuardRecordRepository
import li.songe.gkd.sdp.data.UsageGuardTag
import li.songe.gkd.sdp.data.SelfControlIntervalRepository
import li.songe.gkd.sdp.db.DbSet
import li.songe.gkd.sdp.store.storeFlow
import li.songe.gkd.sdp.ui.component.SelfControlElapsedCard
import li.songe.gkd.sdp.ui.component.UsageRequestRhythmPresentation
import li.songe.gkd.sdp.ui.component.UsageDurationRatioFeedback
import li.songe.gkd.sdp.ui.style.AppTheme
import li.songe.gkd.sdp.util.SelfControlElapsedPolicy
import li.songe.gkd.sdp.util.SelfControlInsightWindowPolicy
import li.songe.gkd.sdp.util.UsageGuardPolicy
import li.songe.gkd.sdp.util.UsageGuardUiStatePolicy
import li.songe.gkd.sdp.widget.UsageGuardReviewWidget

internal val USAGE_GUARD_REQUEST_OVERLAY_FLAGS =
    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
        WindowManager.LayoutParams.FLAG_SECURE

internal val USAGE_GUARD_REQUEST_OVERLAY_SOFT_INPUT_MODE =
    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE

sealed interface UsageRequestDatasetState {
    data object Loading : UsageRequestDatasetState
    data class Ready(val data: SelfControlIntervalRepository.UsageRequestOverlayData) : UsageRequestDatasetState
    data object Unavailable : UsageRequestDatasetState
}

class UsageGuardRequestOverlayService : LifecycleService(), SavedStateRegistryOwner {
    private val windowManager by lazy { getSystemService(WINDOW_SERVICE) as WindowManager }
    private var view: ComposeView? = null

    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry = savedStateRegistryController.savedStateRegistry

    private var appId: String = ""
    private var appName: String = ""
    private var grantMode: Int = UsageGuardPolicy.GRANT_MODE_RESUMABLE
    private var minReasonLength: Int = 8
    private var datasetState by mutableStateOf<UsageRequestDatasetState>(UsageRequestDatasetState.Loading)
    private var selectedWindow by mutableStateOf(SelfControlInsightWindowPolicy.Window.LAST_24_HOURS)
    private var selectedMetric by mutableStateOf(SelfControlInsightWindowPolicy.Metric.INTERVAL)
    private var nowEpochMs by mutableLongStateOf(System.currentTimeMillis())
    private var isSubmitting by mutableStateOf(false)
    private var submitError by mutableStateOf<String?>(null)
    private var tickerJob: Job? = null

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
        datasetState = UsageRequestDatasetState.Loading
        selectedWindow = SelfControlInsightWindowPolicy.Window.LAST_24_HOURS
        selectedMetric = SelfControlInsightWindowPolicy.Metric.INTERVAL
        nowEpochMs = System.currentTimeMillis()
        isSubmitting = false
        submitError = null
        if (showOverlay()) {
            startTicker()
            loadElapsedState()
        }
        return START_NOT_STICKY
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = lifecycleScope.launch {
            while (isActive) {
                nowEpochMs = System.currentTimeMillis()
                delay(1_000L)
            }
        }
    }

    private fun loadElapsedState() {
        lifecycleScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    SelfControlIntervalRepository.fromDb().loadUsageRequestOverlayData(
                        appId = appId,
                        insightAnchorAt = System.currentTimeMillis(),
                    )
                }
            }
            result.onSuccess { overlay ->
                datasetState = UsageRequestDatasetState.Ready(overlay)
            }.onFailure {
                datasetState = UsageRequestDatasetState.Unavailable
            }
        }
    }

    private fun elapsedStateFor(
        state: UsageRequestDatasetState,
        nowEpochMs: Long,
    ): SelfControlElapsedPolicy.ElapsedState = when (state) {
        UsageRequestDatasetState.Loading -> SelfControlElapsedPolicy.ElapsedState.Loading
        UsageRequestDatasetState.Unavailable -> SelfControlElapsedPolicy.ElapsedState.Unavailable
        is UsageRequestDatasetState.Ready -> when (state.data.anchorStatus) {
                    SelfControlIntervalRepository.UsageGapAnchorStatus.NoPreviousRequest ->
                        SelfControlElapsedPolicy.ElapsedState.NoHistory

                    SelfControlIntervalRepository.UsageGapAnchorStatus.Available -> {
                        val anchorAt = state.data.previousLastUsageEndedAt
                        if (anchorAt == null || anchorAt > nowEpochMs) {
                            SelfControlElapsedPolicy.ElapsedState.Unavailable
                        } else {
                            SelfControlElapsedPolicy.ElapsedState.Running(
                                anchorAtEpochMs = anchorAt,
                                firstOccurrence = false,
                            )
                        }
                    }

                    SelfControlIntervalRepository.UsageGapAnchorStatus.MissingActualEnd ->
                        SelfControlElapsedPolicy.ElapsedState.MissingActualEnd
                }
    }

    private fun showOverlay(): Boolean {
        if (view != null) return false

        view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@UsageGuardRequestOverlayService)
            setViewTreeSavedStateRegistryOwner(this@UsageGuardRequestOverlayService)
            setContent {
                AppTheme {
                    val tags by DbSet.usageGuardTagDao.queryAll().collectAsState(initial = emptyList())
                    val settings by storeFlow.collectAsState()
                    UsageGuardRequestContent(
                        appName = appName,
                        tags = tags,
                        grantMode = grantMode,
                        minReasonLength = settings.usageGuardMinReasonLength,
                        elapsedState = elapsedStateFor(datasetState, nowEpochMs),
                        rhythmData = (datasetState as? UsageRequestDatasetState.Ready)?.data,
                        samples = (datasetState as? UsageRequestDatasetState.Ready)?.data?.samples.orEmpty(),
                        insightAnchorAt = (datasetState as? UsageRequestDatasetState.Ready)?.data?.insightAnchorAt,
                        selectedWindow = selectedWindow,
                        onWindowSelected = { selectedWindow = it },
                        selectedMetric = selectedMetric,
                        onMetricSelected = { selectedMetric = it },
                        nowEpochMs = nowEpochMs,
                        supportsUsageRatio = true,
                        durationOptions = UsageGuardUiStatePolicy.normalizeDurationOptions(
                            settings.usageGuardDurationOptionsMinutes,
                        ),
                        onAddTag = { name, existing ->
                            lifecycleScope.launch(Dispatchers.IO) {
                                addCustomTag(name, existing)
                            }
                        },
                        isSubmitting = isSubmitting,
                        submitError = submitError,
                        onSubmit = { selectedTags, reason, requestedDurationMinutes ->
                            if (!isSubmitting) {
                                isSubmitting = true
                                submitError = null
                                appScope.launch {
                                    val result = runCatching {
                                        withContext(Dispatchers.IO) {
                                            val now = System.currentTimeMillis()
                                            UsageGuardRecordRepository.insertRequestWithGap(
                                                record = UsageGuardRecord(
                                                    appId = appId,
                                                    appName = appName,
                                                    tagNames = selectedTags,
                                                    reasonText = reason.trim(),
                                                    grantMode = grantMode,
                                                    requestedDurationMinutes = requestedDurationMinutes,
                                                    requestedAt = now,
                                                    grantedAt = now,
                                                    expiresAt = now + requestedDurationMinutes.toLong() * 60_000L,
                                                ),
                                                replacedAt = now,
                                            )
                                        }
                                    }
                                    withContext(Dispatchers.Main) {
                                        result.onSuccess {
                                            UsageGuardReviewWidget.refreshAll(applicationContext)
                                            UsageGuardEngine.onRequestGranted(appId)
                                            stopSelf()
                                        }.onFailure {
                                            isSubmitting = false
                                            submitError = "暂时无法保存本次申请，请稍后重试"
                                        }
                                    }
                                }
                            }
                        },
                        onCancel = {
                            A11yRuleEngine.performActionHome()
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
            USAGE_GUARD_REQUEST_OVERLAY_FLAGS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            softInputMode = USAGE_GUARD_REQUEST_OVERLAY_SOFT_INPUT_MODE
        }
        runCatching { windowManager.addView(view, params) }.onFailure { error ->
            view?.let { runCatching { windowManager.removeViewImmediate(it) } }
            view = null
            LogUtils.d("usage guard request overlay mount rejected", error::class.java.simpleName)
            UsageGuardEngine.onOverlayMountFailed("request", appId)
            stopSelf()
        }
        return view != null
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
        tickerJob?.cancel()
        view?.let { runCatching { windowManager.removeView(it) } }
        view = null
        UsageGuardEngine.onRequestOverlayStopped(appId.ifBlank { null })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun rememberImeAwareBringIntoViewModifier(): Modifier {
    val requester = remember { BringIntoViewRequester() }
    var isFocused by remember { mutableStateOf(false) }
    val imeVisible = WindowInsets.isImeVisible
    LaunchedEffect(isFocused, imeVisible) {
        if (isFocused) requester.bringIntoView()
    }
    return Modifier
        .bringIntoViewRequester(requester)
        .onFocusChanged { isFocused = it.isFocused }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UsageGuardRequestContent(
    appName: String,
    tags: List<UsageGuardTag>,
    grantMode: Int,
    minReasonLength: Int,
    elapsedState: SelfControlElapsedPolicy.ElapsedState,
    rhythmData: SelfControlIntervalRepository.UsageRequestOverlayData?,
    samples: List<SelfControlInsightWindowPolicy.IntervalSample>,
    insightAnchorAt: Long?,
    selectedWindow: SelfControlInsightWindowPolicy.Window,
    onWindowSelected: (SelfControlInsightWindowPolicy.Window) -> Unit,
    selectedMetric: SelfControlInsightWindowPolicy.Metric,
    onMetricSelected: (SelfControlInsightWindowPolicy.Metric) -> Unit,
    nowEpochMs: Long,
    supportsUsageRatio: Boolean,
    durationOptions: List<Int>,
    onAddTag: (String, List<UsageGuardTag>) -> Unit,
    isSubmitting: Boolean,
    submitError: String?,
    onSubmit: (List<String>, String, Int) -> Unit,
    onCancel: () -> Unit,
) {
    var selectedTags by remember { mutableStateOf(setOf<String>()) }
    var reasonText by remember { mutableStateOf("") }
    var selectedDuration by remember(durationOptions) { mutableStateOf(durationOptions.firstOrNull() ?: 10) }
    var customMinutesText by remember { mutableStateOf("") }
    var newTagText by remember { mutableStateOf("") }
    var showAddTagEditor by remember { mutableStateOf(false) }
    var showCustomDuration by remember { mutableStateOf(false) }

    var reasonError by remember { mutableStateOf<String?>(null) }
    var durationError by remember { mutableStateOf<String?>(null) }
    var tagsError by remember { mutableStateOf<String?>(null) }
    val formScrollState = rememberScrollState()
    val newTagInputModifier = rememberImeAwareBringIntoViewModifier()
    val reasonInputModifier = rememberImeAwareBringIntoViewModifier()
    val customDurationInputModifier = rememberImeAwareBringIntoViewModifier()

    val effectiveRequestedDurationMinutes = if (showCustomDuration) {
        customMinutesText.toIntOrNull()?.takeIf { it > 0 }
    } else {
        selectedDuration
    }
    val rhythmHistory = remember(
        rhythmData?.insightAnchorAt,
        rhythmData?.samples,
    ) {
        UsageRequestRhythmPresentation.historicalStats(
            data = rhythmData,
            fallbackNowEpochMs = nowEpochMs,
        )
    }
    val rhythmPresentation = UsageRequestRhythmPresentation.from(
        data = rhythmData,
        nowEpochMs = nowEpochMs,
        requestedDurationMinutes = effectiveRequestedDurationMinutes ?: 0,
        selectedWindow = selectedWindow,
        cachedHistory = rhythmHistory,
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(formScrollState)
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

            SelfControlElapsedCard(
                context = SelfControlElapsedPolicy.Context.USAGE_REQUEST,
                state = elapsedState,
                samples = samples,
                insightAnchorAt = insightAnchorAt,
                selectedWindow = selectedWindow,
                onWindowSelected = onWindowSelected,
                selectedMetric = selectedMetric,
                onMetricSelected = onMetricSelected,
                supportsUsageRatio = supportsUsageRatio,
                currentReference = null,
                nowEpochMs = nowEpochMs,
            )

            Text("选择标签", style = MaterialTheme.typography.titleSmall)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                tags.forEach { tag ->
                    FilterChip(
                        selected = selectedTags.contains(tag.name),
                        enabled = !isSubmitting,
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

            TextButton(
                enabled = !isSubmitting,
                onClick = { showAddTagEditor = !showAddTagEditor },
            ) {
                Text(if (showAddTagEditor) "收起添加标签" else "没有合适的标签？添加标签")
            }
            if (showAddTagEditor) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newTagText,
                        onValueChange = { newTagText = it },
                        modifier = Modifier
                            .weight(1f)
                            .then(newTagInputModifier),
                        label = { Text("添加标签") },
                        singleLine = true,
                        enabled = !isSubmitting,
                    )
                    Button(
                        enabled = !isSubmitting,
                        onClick = {
                            val normalized = newTagText.trim()
                            if (normalized.isBlank()) return@Button
                            onAddTag(normalized, tags)
                            selectedTags = selectedTags + normalized
                            newTagText = ""
                            showAddTagEditor = false
                        },
                    ) {
                        Text("加入")
                    }
                }
            }

            OutlinedTextField(
                value = reasonText,
                onValueChange = {
                    reasonError = null
                    reasonText = it
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .then(reasonInputModifier),
                label = { Text("申请理由") },
                supportingText = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("至少 $minReasonLength 个字")
                        Text("${reasonText.trim().length} 字")
                    }
                },
                isError = reasonError != null,
                minLines = 3,
                enabled = !isSubmitting,
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
                        selected = selectedDuration == minutes && !showCustomDuration,
                        enabled = !isSubmitting,
                        onClick = {
                            durationError = null
                            selectedDuration = minutes
                            customMinutesText = ""
                            showCustomDuration = false
                        },
                        label = { Text("${minutes}分钟") },
                    )
                }
            }

            TextButton(
                enabled = !isSubmitting,
                onClick = { showCustomDuration = !showCustomDuration },
            ) {
                Text(if (showCustomDuration) "收起自定义时长" else "自定义时长")
            }
            if (showCustomDuration) {
                OutlinedTextField(
                    value = customMinutesText,
                    onValueChange = {
                        durationError = null
                        if (it.all(Char::isDigit)) {
                            customMinutesText = it
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(customDurationInputModifier),
                    label = { Text("自定义分钟数") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    isError = durationError != null,
                    enabled = !isSubmitting,
                )
            }
            durationError?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            UsageDurationRatioFeedback(
                presentation = rhythmPresentation,
            )

            submitError?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                enabled = !isSubmitting,
                onClick = {
                    val requestedDurationMinutes = if (showCustomDuration) {
                        customMinutesText.toIntOrNull() ?: 0
                    } else {
                        selectedDuration
                    }
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
                enabled = !isSubmitting,
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("取消")
            }
        }
    }
}
