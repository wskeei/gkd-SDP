@file:JvmName("UsageGuardRequestScreen")

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
internal fun UsageGuardRequestContent(
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
    val form = UsageGuardRequestFormState(
        selectedTags = remember { mutableStateOf(emptySet()) },
        reasonText = remember { mutableStateOf("") },
        selectedDuration = remember(durationOptions) {
            mutableStateOf(durationOptions.firstOrNull() ?: 10)
        },
        customMinutesText = remember { mutableStateOf("") },
        newTagText = remember { mutableStateOf("") },
        showAddTagEditor = remember { mutableStateOf(false) },
        showCustomDuration = remember { mutableStateOf(false) },
        reasonError = remember { mutableStateOf<String?>(null) },
        durationError = remember { mutableStateOf<String?>(null) },
        tagsError = remember { mutableStateOf<String?>(null) },
    )
    val formScrollState = rememberScrollState()
    val newTagInputModifier = rememberImeAwareBringIntoViewModifier()
    val reasonInputModifier = rememberImeAwareBringIntoViewModifier()
    val customDurationInputModifier = rememberImeAwareBringIntoViewModifier()

    val effectiveRequestedDurationMinutes = if (form.showCustomDuration.value) {
        form.customMinutesText.value.toIntOrNull()?.takeIf { it > 0 }
    } else {
        form.selectedDuration.value
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
            UsageRequestHeaderAndRhythm(
                appName = appName,
                grantMode = grantMode,
                elapsedState = elapsedState,
                samples = samples,
                insightAnchorAt = insightAnchorAt,
                selectedWindow = selectedWindow,
                onWindowSelected = onWindowSelected,
                selectedMetric = selectedMetric,
                onMetricSelected = onMetricSelected,
                nowEpochMs = nowEpochMs,
                supportsUsageRatio = supportsUsageRatio,
            )
            UsageRequestTags(
                form = form,
                tags = tags,
                onAddTag = onAddTag,
                newTagInputModifier = newTagInputModifier,
                isSubmitting = isSubmitting,
            )
            UsageRequestReasonAndDuration(
                form = form,
                minReasonLength = minReasonLength,
                durationOptions = durationOptions,
                reasonInputModifier = reasonInputModifier,
                customDurationInputModifier = customDurationInputModifier,
                isSubmitting = isSubmitting,
                rhythmPresentation = rhythmPresentation,
            )
            UsageRequestActions(
                form = form,
                minReasonLength = minReasonLength,
                isSubmitting = isSubmitting,
                submitError = submitError,
                onSubmit = onSubmit,
                onCancel = onCancel,
            )

        }
    }
}
