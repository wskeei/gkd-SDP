package li.songe.gkd.sdp.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import li.songe.gkd.sdp.R

/**
 * Legacy alias for a centered empty hint. New screens should use
 * [ContentState.Empty] through [ContentStateBox] so empty states keep a
 * title, description and a single action everywhere.
 */
@Composable
fun EmptyText(
    text: String = "",
    modifier: Modifier = Modifier,
) {
    ContentStateBox(
        state = ContentState.Empty(title = text.ifBlank { stringResource(R.string.common_empty) }),
        modifier = modifier,
    ) {}
}
