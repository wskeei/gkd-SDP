package li.songe.gkd.sdp.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import li.songe.gkd.sdp.ui.component.PerfIcon
import li.songe.gkd.sdp.ui.share.LocalMainViewModel
import li.songe.gkd.sdp.ui.style.DimensionTokens
import li.songe.gkd.sdp.ui.style.ResponsiveTokens

/**
 * Adaptive home shell: compact windows use a bottom NavigationBar, medium and
 * expanded windows use a left NavigationRail with a bounded content column.
 * Switching width keeps the current destination and its back stack.
 */
@Composable
fun AdaptiveHomeScaffold(
    destination: HomeDestination,
    modifier: Modifier = Modifier,
    content: @Composable (HomeDestination) -> Unit,
) {
    val mainVm = LocalMainViewModel.current
    val widthDp = LocalConfiguration.current.screenWidthDp
    val useRail = widthDp >= ResponsiveTokens.CompactMaxWidthDp

    if (useRail) {
        Row(modifier = modifier.fillMaxSize()) {
            NavigationRail(modifier = Modifier.width(DimensionTokens.TopBarHeight)) {
                Spacer(modifier = Modifier.height(DimensionTokens.SpacingMd))
                HomeDestination.all.forEach { item ->
                    val selected = item == destination
                    NavigationRailItem(
                        selected = selected,
                        onClick = { if (!selected) mainVm.handleClickDestination(item) },
                        icon = {
                            PerfIcon(
                                imageVector = item.icon,
                                contentDescription = if (selected) item.label else null,
                            )
                        },
                        label = {
                            Text(
                                text = item.label,
                                style = MaterialTheme.typography.labelLarge,
                            )
                        },
                        modifier = Modifier.semantics {
                            this.selected = selected
                        },
                    )
                }
            }
            HorizontalDivider()
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = DimensionTokens.ListMaxWidth),
            ) {
                content(destination)
            }
        }
    } else {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            bottomBar = {
                NavigationBar {
                    HomeDestination.all.forEach { item ->
                        val selected = item == destination
                        NavigationBarItem(
                            selected = selected,
                            onClick = { if (!selected) mainVm.handleClickDestination(item) },
                            icon = {
                                PerfIcon(
                                    imageVector = item.icon,
                                    contentDescription = if (selected) item.label else null,
                                )
                            },
                            label = {
                                Text(
                                    text = item.label,
                                    maxLines = 1,
                                )
                            },
                        )
                    }
                }
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .widthIn(max = DimensionTokens.ListMaxWidth),
            ) {
                content(destination)
            }
        }
    }
}
