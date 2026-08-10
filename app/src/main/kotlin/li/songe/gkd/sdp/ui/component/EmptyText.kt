package li.songe.gkd.sdp.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Legacy alias for a centered empty hint. New screens should use
 * [ContentState.Empty] through [ContentStateBox] so empty states keep a
 * title, description and a single action everywhere.
 */
@Composable
fun EmptyText(
    text: String = "暂无数据",
    modifier: Modifier = Modifier,
) {
    ContentStateBox(
        state = ContentState.Empty(title = text),
        modifier = modifier,
    ) {}
}
