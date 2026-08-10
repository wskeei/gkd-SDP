package li.songe.gkd.sdp.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import li.songe.gkd.sdp.ui.style.DimensionTokens
import li.songe.gkd.sdp.ui.style.ResponsiveTokens
import androidx.compose.ui.res.stringResource
import li.songe.gkd.sdp.R

object AppActionBarPolicy {
    /** Compact windows anchor the bar to the screen bottom. */
    fun isCompactWindow(windowWidthDp: Int): Boolean =
        windowWidthDp < ResponsiveTokens.CompactMaxWidthDp
}

/**
 * Fixed bottom action row. On compact windows it stays anchored to the screen
 * bottom; on medium/expanded windows it anchors to the bottom of the content
 * column (pass the content width through [Modifier.widthIn]).
 *
 * [submitting] disables the primary button so repeated taps cannot double
 * submit.
 */
@Composable
fun AppActionBar(
    confirmText: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    cancelText: String? = null,
    onCancel: (() -> Unit)? = null,
    confirmEnabled: Boolean = true,
    submitting: Boolean = false,
) {
    val actions = Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(DimensionTokens.SpacingBase),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (cancelText != null && onCancel != null) {
            OutlinedButton(
                onClick = onCancel,
                enabled = !submitting,
            ) {
                Text(cancelText)
            }
            Spacer(modifier = Modifier.width(DimensionTokens.SpacingBase))
        }
        Button(
            onClick = onConfirm,
            enabled = confirmEnabled && !submitting,
            modifier = Modifier.weight(1f),
        ) {
            Text(if (submitting) stringResource(R.string.s_1cac8ac7f5) else confirmText)
        }
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        androidx.compose.material3.HorizontalDivider()
        actions
    }
}
