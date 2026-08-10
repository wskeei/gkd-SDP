package li.songe.gkd.sdp.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import li.songe.gkd.sdp.ui.style.DimensionTokens
import androidx.compose.ui.res.stringResource
import li.songe.gkd.sdp.R

/**
 * The only page content states. Screens render through [ContentStateBox] so
 * loading, empty, error and content look and behave the same everywhere.
 */
sealed interface ContentState {
    data object Loading : ContentState

    data class Empty(
        val title: String,
        val description: String? = null,
        val actionText: String? = null,
        val onAction: (() -> Unit)? = null,
    ) : ContentState

    data object Content : ContentState

    data class Error(
        val errorCode: String,
        val description: String,
        val onRetry: (() -> Unit)? = null,
        val recoveryText: String? = null,
        val onRecovery: (() -> Unit)? = null,
    ) : ContentState
}

object ContentStatePolicy {
    /** Loading is only shown after this delay to avoid flicker. */
    const val LoadingDelayMs = 400
}

/**
 * Renders [state]. Loading is suppressed for the first
 * [ContentStatePolicy.LoadingDelayMs] ms; Error shows a stable error code,
 * description, retry and an optional context recovery action.
 */
@Composable
fun ContentStateBox(
    state: ContentState,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    when (state) {
        ContentState.Loading -> {
            var showProgress by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                delay(ContentStatePolicy.LoadingDelayMs.toLong())
                showProgress = true
            }
            Box(modifier = modifier.fillMaxSize()) {
                if (showProgress) {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }
        }

        is ContentState.Empty -> {
            Column(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(DimensionTokens.SpacingXxl),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = state.title,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
                if (state.description != null) {
                    Text(
                        text = state.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = DimensionTokens.SpacingSm),
                    )
                }
                if (state.actionText != null && state.onAction != null) {
                    Button(
                        onClick = state.onAction,
                        modifier = Modifier.padding(top = DimensionTokens.SpacingXl),
                    ) {
                        Text(state.actionText)
                    }
                }
            }
        }

        ContentState.Content -> content()

        is ContentState.Error -> {
            Column(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(DimensionTokens.SpacingXxl),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.s_f6a8a7f4ed, state.errorCode),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = state.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = DimensionTokens.SpacingSm),
                )
                if (state.onRetry != null) {
                    TextButton(onClick = state.onRetry) {
                        Text(stringResource(R.string.s_e2d53a6d3a))
                    }
                }
                if (state.recoveryText != null && state.onRecovery != null) {
                    TextButton(onClick = state.onRecovery) {
                        Text(state.recoveryText)
                    }
                }
            }
        }
    }
}
