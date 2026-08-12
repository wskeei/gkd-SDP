package li.songe.gkd.sdp.ui.selfcontrol

import androidx.compose.runtime.Immutable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import li.songe.gkd.sdp.R
import li.songe.gkd.sdp.ui.AppBlockerRoute
import li.songe.gkd.sdp.ui.component.PerfIcon
import li.songe.gkd.sdp.ui.FocusLockRoute
import li.songe.gkd.sdp.ui.FocusModeRoute
import li.songe.gkd.sdp.ui.share.LocalMainViewModel
import li.songe.gkd.sdp.ui.style.DimensionTokens
import li.songe.gkd.sdp.ui.style.surfaceCardColors
import li.songe.gkd.sdp.ui.UrlBlockRoute
import li.songe.gkd.sdp.ui.UsageGuardRoute
import li.songe.gkd.sdp.ui.UsageGuardReviewRoute

@Immutable
data class SelfControlEntryUi(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
    val testTag: String,
)

/**
 * Self-control hub: usage request, review, focus mode, app blocking, URL
 * blocking and lock protection entry points.
 */
@Composable
fun SelfControlHubScreen() {
    val mainVm = LocalMainViewModel.current
    val entries = listOf(
        SelfControlEntryUi(
            title = stringResource(R.string.s_356c996618),
            description = stringResource(R.string.s_2755dbd77c),
            icon = PerfIcon.ToggleOn,
            onClick = { mainVm.navigatePage(UsageGuardRoute) },
            testTag = "self_control_usage",
        ),
        SelfControlEntryUi(
            title = stringResource(R.string.s_c7380c3c20),
            description = stringResource(R.string.s_302471b81d),
            icon = PerfIcon.Equalizer,
            onClick = { mainVm.navigatePage(UsageGuardReviewRoute) },
            testTag = "self_control_review",
        ),
        SelfControlEntryUi(
            title = stringResource(R.string.s_63c1371c03),
            description = stringResource(R.string.s_6905b9f1f9),
            icon = PerfIcon.Schedule,
            onClick = { mainVm.navigatePage(FocusModeRoute) },
            testTag = "self_control_focus",
        ),
        SelfControlEntryUi(
            title = stringResource(R.string.s_e6bbd743b3),
            description = stringResource(R.string.s_25d9aca60f),
            icon = PerfIcon.Block,
            onClick = { mainVm.navigatePage(AppBlockerRoute) },
            testTag = "self_control_app_blocker",
        ),
        SelfControlEntryUi(
            title = stringResource(R.string.s_dcbbbab7a5),
            description = stringResource(R.string.s_86629471c3),
            icon = PerfIcon.Info,
            onClick = { mainVm.navigatePage(UrlBlockRoute) },
            testTag = "self_control_url_blocker",
        ),
        SelfControlEntryUi(
            title = stringResource(R.string.s_6337015d1f),
            description = stringResource(R.string.s_0b707d6dcc),
            icon = PerfIcon.Lock,
            onClick = { mainVm.navigatePage(FocusLockRoute) },
            testTag = "self_control_lock",
        ),
    )
    SelfControlHubContent(entries = entries)
}

@Composable
internal fun SelfControlHubContent(
    entries: List<SelfControlEntryUi>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(DimensionTokens.SpacingBase)) {
        entries.forEach { entry ->
            SelfControlEntryCard(entry = entry)
        }
    }
}

@Composable
private fun SelfControlEntryCard(entry: SelfControlEntryUi) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = entry.onClick)
            .testTag(entry.testTag),
        colors = surfaceCardColors,
        shape = CardDefaults.shape,
    ) {
        Row(
            modifier = Modifier.padding(DimensionTokens.SpacingBase),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PerfIcon(
                imageVector = entry.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.width(DimensionTokens.SpacingBase))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = entry.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            PerfIcon(
                imageVector = PerfIcon.KeyboardArrowRight,
                contentDescription = null,
            )
        }
    }
}
