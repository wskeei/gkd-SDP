package li.songe.gkd.sdp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import li.songe.gkd.sdp.ui.component.AppDataChart
import li.songe.gkd.sdp.ui.component.ChartBucket
import li.songe.gkd.sdp.ui.component.ContentState
import li.songe.gkd.sdp.ui.component.ContentStateBox

@PreviewTest
@Preview(name = "Overview ready", showBackground = true, widthDp = 360)
@Composable
fun ScreenshotOverviewReady() {
    MaterialTheme {
        ContentStateBox(state = ContentState.Content) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.privacy_data_title), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(R.string.privacy_data_intro), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@PreviewTest
@Preview(name = "Overview action required", showBackground = true, widthDp = 700)
@Composable
fun ScreenshotOverviewActionRequired() {
    MaterialTheme {
        ContentStateBox(
            state = ContentState.Empty(
                title = stringResource(R.string.settings_search),
                description = stringResource(R.string.privacy_data_intro),
                actionText = stringResource(R.string.privacy_delete),
                onAction = {},
            ),
        ) {}
    }
}

@PreviewTest
@Preview(name = "Chart dense", showBackground = true, widthDp = 1000, fontScale = 2f)
@Composable
fun ScreenshotChartDense() {
    MaterialTheme {
        AppDataChart(
            title = stringResource(R.string.privacy_data_title),
            summaryText = "8 个样本",
            buckets = (0 until 24).map { index ->
                ChartBucket(
                    label = "%02d:00".format(index),
                    value = (index % 7).toDouble() + 1.0,
                    sampleCount = index % 3 + 1,
                    hasValue = true,
                )
            },
            unit = "分钟",
            formatValue = { it.toInt().toString() },
        )
    }
}
