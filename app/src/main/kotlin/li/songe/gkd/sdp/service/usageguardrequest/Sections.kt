@file:JvmName("UsageGuardRequestSections")

package li.songe.gkd.sdp.service

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import li.songe.gkd.sdp.data.SelfControlIntervalRepository
import li.songe.gkd.sdp.data.UsageGuardTag
import li.songe.gkd.sdp.ui.component.SelfControlElapsedCard
import li.songe.gkd.sdp.ui.component.UsageDurationRatioFeedback
import li.songe.gkd.sdp.ui.component.UsageRequestRhythmPresentation
import li.songe.gkd.sdp.util.SelfControlElapsedPolicy
import li.songe.gkd.sdp.util.SelfControlInsightWindowPolicy
import li.songe.gkd.sdp.util.UsageGuardPolicy
import androidx.compose.ui.res.stringResource
import li.songe.gkd.sdp.R
import li.songe.gkd.sdp.app

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun UsageRequestHeaderAndRhythm(
    appName: String,
    grantMode: Int,
    elapsedState: SelfControlElapsedPolicy.ElapsedState,
    samples: List<SelfControlInsightWindowPolicy.IntervalSample>,
    insightAnchorAt: Long?,
    selectedWindow: SelfControlInsightWindowPolicy.Window,
    onWindowSelected: (SelfControlInsightWindowPolicy.Window) -> Unit,
    selectedMetric: SelfControlInsightWindowPolicy.Metric,
    onMetricSelected: (SelfControlInsightWindowPolicy.Metric) -> Unit,
    nowEpochMs: Long,
    supportsUsageRatio: Boolean,
) {
    Text(
        text = stringResource(R.string.s_356c996618),
        style = MaterialTheme.typography.headlineMedium,
    )
    Text(
        text = stringResource(R.string.s_d5da946f96, appName),
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
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun UsageRequestTags(
    form: UsageGuardRequestFormState,
    tags: List<UsageGuardTag>,
    onAddTag: (String, List<UsageGuardTag>) -> Unit,
    newTagInputModifier: Modifier,
    isSubmitting: Boolean,
) {
    val selectedTags = form.selectedTags
    val tagsError = form.tagsError
    val newTagText = form.newTagText
    val showAddTagEditor = form.showAddTagEditor

    Text(stringResource(R.string.s_608b8e137e), style = MaterialTheme.typography.titleSmall)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        tags.forEach { tag ->
            FilterChip(
                selected = selectedTags.value.contains(tag.name),
                enabled = !isSubmitting,
                onClick = {
                    tagsError.value = null
                    selectedTags.value = if (selectedTags.value.contains(tag.name)) {
                        selectedTags.value - tag.name
                    } else {
                        selectedTags.value + tag.name
                    }
                },
                label = { Text(tag.name) },
            )
        }
    }
    tagsError.value?.let {
        Text(
            text = it,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }

    TextButton(
        enabled = !isSubmitting,
        onClick = { showAddTagEditor.value = !showAddTagEditor.value },
    ) {
        Text(if (showAddTagEditor.value) app.getString(R.string.s_8f68cd6535) else app.getString(R.string.s_12460118b0))
    }
    if (showAddTagEditor.value) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = newTagText.value,
                onValueChange = { newTagText.value = it },
                modifier = Modifier
                    .weight(1f)
                    .then(newTagInputModifier),
                label = { Text(stringResource(R.string.s_df06c7a718)) },
                singleLine = true,
                enabled = !isSubmitting,
            )
            Button(
                enabled = !isSubmitting,
                onClick = {
                    val normalized = newTagText.value.trim()
                    if (normalized.isBlank()) return@Button
                    onAddTag(normalized, tags)
                    selectedTags.value = selectedTags.value + normalized
                    newTagText.value = ""
                    showAddTagEditor.value = false
                },
            ) {
                Text(stringResource(R.string.s_b7db442622))
            }
        }
    }
}

@Composable
internal fun UsageRequestReasonAndDuration(
    form: UsageGuardRequestFormState,
    minReasonLength: Int,
    durationOptions: List<Int>,
    reasonInputModifier: Modifier,
    customDurationInputModifier: Modifier,
    isSubmitting: Boolean,
    rhythmPresentation: UsageRequestRhythmPresentation,
) {
    val reasonText = form.reasonText
    val reasonError = form.reasonError
    val selectedDuration = form.selectedDuration
    val customMinutesText = form.customMinutesText
    val showCustomDuration = form.showCustomDuration
    val durationError = form.durationError

    OutlinedTextField(
        value = reasonText.value,
        onValueChange = {
            reasonError.value = null
            reasonText.value = it
        },
        modifier = Modifier
            .fillMaxWidth()
            .then(reasonInputModifier),
        label = { Text(stringResource(R.string.s_781d71bd97)) },
        supportingText = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(app.getString(R.string.s_58d3737769, minReasonLength))
                Text(app.getString(R.string.s_cf989beabc, reasonText.value.trim().length))
            }
        },
        isError = reasonError.value != null,
        minLines = 3,
        enabled = !isSubmitting,
    )
    reasonError.value?.let {
        Text(
            text = it,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }

    Text(stringResource(R.string.s_7c0311beef), style = MaterialTheme.typography.titleSmall)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        durationOptions.forEach { minutes ->
            FilterChip(
                selected = selectedDuration.value == minutes && !showCustomDuration.value,
                enabled = !isSubmitting,
                onClick = {
                    durationError.value = null
                    selectedDuration.value = minutes
                    customMinutesText.value = ""
                    showCustomDuration.value = false
                },
                label = { Text(app.getString(R.string.s_5f4ec4b0ec, minutes)) },
            )
        }
    }

    TextButton(
        enabled = !isSubmitting,
        onClick = { showCustomDuration.value = !showCustomDuration.value },
    ) {
        Text(if (showCustomDuration.value) app.getString(R.string.s_bda66bc5a5) else app.getString(R.string.s_ea6dccc0a6))
    }
    if (showCustomDuration.value) {
        OutlinedTextField(
            value = customMinutesText.value,
            onValueChange = {
                durationError.value = null
                if (it.all(Char::isDigit)) {
                    customMinutesText.value = it
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .then(customDurationInputModifier),
            label = { Text(stringResource(R.string.s_a6f825af51)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            isError = durationError.value != null,
            enabled = !isSubmitting,
        )
    }
    durationError.value?.let {
        Text(
            text = it,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }

    UsageDurationRatioFeedback(presentation = rhythmPresentation)
}

@Composable
internal fun UsageRequestActions(
    form: UsageGuardRequestFormState,
    minReasonLength: Int,
    isSubmitting: Boolean,
    submitError: String?,
    onSubmit: (List<String>, String, Int) -> Unit,
    onCancel: () -> Unit,
) {
    val selectedTags = form.selectedTags
    val reasonText = form.reasonText
    val selectedDuration = form.selectedDuration
    val showCustomDuration = form.showCustomDuration
    val customMinutesText = form.customMinutesText
    val tagsError = form.tagsError
    val reasonError = form.reasonError
    val durationError = form.durationError

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
            val requestedDurationMinutes = if (showCustomDuration.value) {
                customMinutesText.value.toIntOrNull() ?: 0
            } else {
                selectedDuration.value
            }
            val validation = UsageGuardPolicy.validateRequest(
                selectedTags = selectedTags.value.toList(),
                reason = reasonText.value,
                minReasonLength = minReasonLength,
                requestedDurationMinutes = requestedDurationMinutes,
            )
            tagsError.value = validation.tagsError
            reasonError.value = validation.reasonError
            durationError.value = validation.durationError
            if (validation.accepted) {
                onSubmit(selectedTags.value.toList(), reasonText.value, requestedDurationMinutes)
            }
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(R.string.s_60ff133549))
    }
    TextButton(
        enabled = !isSubmitting,
        onClick = onCancel,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(app.getString(R.string.s_4d0b4688c7))
    }
}
