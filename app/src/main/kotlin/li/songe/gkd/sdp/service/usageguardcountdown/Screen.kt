@file:JvmName("UsageGuardCountdownScreen")

package li.songe.gkd.sdp.service

import android.content.Intent
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import li.songe.gkd.sdp.a11y.UsageGuardEngine
import li.songe.gkd.sdp.ui.style.AppTheme
import li.songe.gkd.sdp.util.BarUtils
import li.songe.gkd.sdp.util.LogUtils
import li.songe.gkd.sdp.util.ScreenUtils
import li.songe.gkd.sdp.util.UsageGuardCountdownOverlayCaptureController
import li.songe.gkd.sdp.util.UsageGuardCountdownOverlayCapturePolicy
import li.songe.gkd.sdp.util.UsageGuardCountdownOverlayLayoutPolicy
import li.songe.gkd.sdp.util.UsageGuardCountdownOverlayPolicy
import li.songe.gkd.sdp.util.UsageGuardCountdownOverlaySession
import li.songe.gkd.sdp.util.px
import kotlin.math.roundToInt
import androidx.compose.ui.res.stringResource

@Composable
internal fun UsageGuardCountdownOverlayContent(
    state: UsageGuardCountdownUiState,
    maxPillWidthPx: Int,
    onPillTap: () -> Unit,
    onDrag: (Float, Float) -> Unit,
    onDismissTerminate: () -> Unit,
    onHideForScreenshot: () -> Unit,
    onConfirmTerminate: () -> Unit,
) {
    if (state.showTerminateConfirm) {
        UsageGuardTerminateConfirmScreen(
            onDismiss = onDismissTerminate,
            onHideForScreenshot = onHideForScreenshot,
            onConfirm = onConfirmTerminate,
        )
    } else {
        UsageGuardCountdownPill(
            remainingMillis = state.remainingMillis,
            reasonText = state.reasonText,
            maxPillWidthPx = maxPillWidthPx,
            onTap = onPillTap,
            onDrag = onDrag,
        )
    }
}

@Composable
private fun UsageGuardCountdownPill(
    remainingMillis: Long,
    reasonText: String,
    maxPillWidthPx: Int,
    onTap: () -> Unit,
    onDrag: (Float, Float) -> Unit,
) {
    var remaining by remember(remainingMillis) { mutableLongStateOf(remainingMillis) }
    LaunchedEffect(remainingMillis) {
        val startedAt = android.os.SystemClock.elapsedRealtime()
        while (true) {
            remaining = (remainingMillis - (android.os.SystemClock.elapsedRealtime() - startedAt)).coerceAtLeast(0L)
            delay(1_000L)
        }
    }
    val remainingText = countdownRemainingText(remaining)
    val maxPillWidth = with(LocalDensity.current) {
        maxPillWidthPx.toDp()
    }

    Surface(
        color = Color.Black.copy(alpha = 0.72f),
        shape = RoundedCornerShape(999.dp),
        modifier = Modifier
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onTap() })
            }
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount.x, dragAmount.y)
                }
            },
    ) {
        Row(
            modifier = Modifier
                .widthIn(max = maxPillWidth)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = remainingText,
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
            )
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(24.dp)
                    .background(Color.White.copy(alpha = 0.36f)),
            )
            Text(
                text = reasonText,
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
    }
}

@Composable
private fun UsageGuardTerminateConfirmScreen(
    onDismiss: () -> Unit,
    onHideForScreenshot: () -> Unit,
    onConfirm: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = stringResource(R.string.s_17cf04336d),
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = stringResource(R.string.s_75358e224d),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = onHideForScreenshot,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.s_d3d9818b53))
                }
                Text(
                    text = stringResource(R.string.s_894ea44be1),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.align(Alignment.End),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.s_11d0241540))
                    }
                    Button(onClick = onConfirm) {
                        Text(stringResource(R.string.s_59f9bf3524))
                    }
                }
            }
        }
    }
}
