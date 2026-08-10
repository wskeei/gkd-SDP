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
@Preview(name = "Self-control hub compact", showBackground = true, widthDp = 360)
@Composable
fun ScreenshotSelfControlHubCompact() {
    MaterialTheme {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(stringResource(R.string.privacy_data_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.privacy_data_intro), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@PreviewTest
@Preview(name = "Settings dark en", showBackground = true, widthDp = 700, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
fun ScreenshotSettingsDarkEn() {
    MaterialTheme {
        Text(
            text = stringResource(R.string.privacy_data_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(16.dp),
        )
    }
}
