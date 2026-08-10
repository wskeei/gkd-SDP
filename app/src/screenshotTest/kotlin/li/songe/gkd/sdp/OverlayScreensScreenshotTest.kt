package li.songe.gkd.sdp

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest

@PreviewTest
@Preview(name = "Interception source missing", showBackground = true, widthDp = 360)
@Composable
fun ScreenshotInterceptionSourceMissing() {
    MaterialTheme {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(stringResource(R.string.privacy_data_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.privacy_data_intro), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@PreviewTest
@Preview(name = "Usage request dense", showBackground = true, widthDp = 360, fontScale = 2f)
@Composable
fun ScreenshotUsageRequestDense() {
    MaterialTheme {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(stringResource(R.string.settings_search), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.privacy_data_intro), style = MaterialTheme.typography.bodyMedium)
        }
    }
}
