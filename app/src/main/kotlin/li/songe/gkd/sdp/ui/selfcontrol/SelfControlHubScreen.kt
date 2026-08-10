package li.songe.gkd.sdp.ui.selfcontrol

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

private data class SelfControlEntry(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val navigate: () -> Unit,
)

/**
 * Self-control hub: usage request, review, focus mode, app blocking, URL
 * blocking and lock protection entry points.
 */
@Composable
fun SelfControlHubScreen() {
    val mainVm = LocalMainViewModel.current
    val entries = listOf(
        SelfControlEntry(
            title = stringResource(R.string.s_356c996618),
            description = stringResource(R.string.s_2755dbd77c),
            icon = PerfIcon.ToggleOn,
            navigate = { mainVm.navigatePage(UsageGuardRoute) },
        ),
        SelfControlEntry(
            title = stringResource(R.string.s_c7380c3c20),
            description = stringResource(R.string.s_302471b81d),
            icon = PerfIcon.Equalizer,
            navigate = { mainVm.navigatePage(UsageGuardReviewRoute) },
        ),
        SelfControlEntry(
            title = stringResource(R.string.s_63c1371c03),
            description = stringResource(R.string.s_6905b9f1f9),
            icon = PerfIcon.Schedule,
            navigate = { mainVm.navigatePage(FocusModeRoute) },
        ),
        SelfControlEntry(
            title = stringResource(R.string.s_e6bbd743b3),
            description = stringResource(R.string.s_25d9aca60f),
            icon = PerfIcon.Block,
            navigate = { mainVm.navigatePage(AppBlockerRoute) },
        ),
        SelfControlEntry(
            title = stringResource(R.string.s_dcbbbab7a5),
            description = stringResource(R.string.s_86629471c3),
            icon = PerfIcon.Info,
            navigate = { mainVm.navigatePage(UrlBlockRoute) },
        ),
        SelfControlEntry(
            title = stringResource(R.string.s_6337015d1f),
            description = stringResource(R.string.s_0b707d6dcc),
            icon = PerfIcon.Lock,
            navigate = { mainVm.navigatePage(FocusLockRoute) },
        ),
    )
    Column(modifier = Modifier.padding(DimensionTokens.SpacingBase)) {
        entries.forEach { entry ->
            SelfControlEntryCard(entry = entry)
        }
    }
}

@Composable
private fun SelfControlEntryCard(entry: SelfControlEntry) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = entry.navigate),
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
