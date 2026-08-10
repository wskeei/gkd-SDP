@file:JvmName("UsageGuardRequestServiceHost")

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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import li.songe.gkd.sdp.ui.share.ServiceOverlayLifecycleOwner
import li.songe.gkd.sdp.ui.style.AppTheme
import li.songe.gkd.sdp.util.SelfControlElapsedPolicy
import li.songe.gkd.sdp.util.SelfControlInsightWindowPolicy
import li.songe.gkd.sdp.util.UsageGuardPolicy
import li.songe.gkd.sdp.util.UsageGuardUiStatePolicy
import li.songe.gkd.sdp.widget.UsageGuardReviewWidget

class UsageGuardRequestOverlayService : LifecycleService(), SavedStateRegistryOwner {
    private val windowManager by lazy { getSystemService(WINDOW_SERVICE) as WindowManager }
    private val overlayLifecycleOwner = ServiceOverlayLifecycleOwner()
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

    private fun showOverlay(): Boolean {
        if (view != null) return false

        view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(overlayLifecycleOwner)
            setViewTreeSavedStateRegistryOwner(this@UsageGuardRequestOverlayService)
            setContent {
                AppTheme {
                    val tags by DbSet.usageGuardTagDao.queryAll().collectAsStateWithLifecycle(initialValue = emptyList())
                    val settings by storeFlow.collectAsStateWithLifecycle()
                    UsageGuardRequestContent(
                        appName = appName,
                        tags = tags,
                        grantMode = grantMode,
                        minReasonLength = settings.usageGuardMinReasonLength,
                        elapsedState = usageRequestElapsedState(datasetState, nowEpochMs),
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
        runCatching {
            windowManager.addView(view, params)
            overlayLifecycleOwner.onViewAdded()
        }.onFailure { error ->
            view?.let { runCatching { windowManager.removeViewImmediate(it) } }
            overlayLifecycleOwner.onViewRemoved()
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
        overlayLifecycleOwner.onViewRemoved()
        view?.let { runCatching { windowManager.removeView(it) } }
        view = null
        UsageGuardEngine.onRequestOverlayStopped(appId.ifBlank { null })
    }
}
