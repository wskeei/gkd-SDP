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
import li.songe.gkd.sdp.privacy.DataCategory
import li.songe.gkd.sdp.privacy.DataDeletionCoordinator
import li.songe.gkd.sdp.settings.SettingsIndex
import li.songe.gkd.sdp.settings.SettingsSearchPolicy
import li.songe.gkd.sdp.ui.home.SettingsSearchResultsContent
import li.songe.gkd.sdp.ui.privacy.PrivacyDataContent
import li.songe.gkd.sdp.ui.component.AppDataChart
import li.songe.gkd.sdp.ui.component.AppConfirmationDialog
import li.songe.gkd.sdp.ui.component.ChartBucket
import li.songe.gkd.sdp.ui.component.PerfIcon
import li.songe.gkd.sdp.ui.selfcontrol.SelfControlEntryUi
import li.songe.gkd.sdp.ui.selfcontrol.SelfControlHubContent

@PreviewTest
@Preview(name = "Self-control hub compact", showBackground = true, widthDp = 360)
@Composable
fun ScreenshotSelfControlHubCompact() {
    MaterialTheme {
        SelfControlHubContent(
            entries = listOf(
                SelfControlEntryUi(
                    title = stringResource(R.string.s_356c996618),
                    description = stringResource(R.string.s_2755dbd77c),
                    icon = PerfIcon.ToggleOn,
                    onClick = {},
                    testTag = "self_control_usage",
                ),
                SelfControlEntryUi(
                    title = stringResource(R.string.s_c7380c3c20),
                    description = stringResource(R.string.s_302471b81d),
                    icon = PerfIcon.Equalizer,
                    onClick = {},
                    testTag = "self_control_review",
                ),
            ),
        )
    }
}

@PreviewTest
@Preview(
    name = "Self-control hub expanded dark en",
    showBackground = true,
    widthDp = 700,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
    locale = "en",
)
@Composable
fun ScreenshotSelfControlHubExpandedDarkEn() {
    MaterialTheme {
        SelfControlHubContent(
            entries = listOf(
                SelfControlEntryUi(
                    title = stringResource(R.string.s_356c996618),
                    description = stringResource(R.string.s_2755dbd77c),
                    icon = PerfIcon.ToggleOn,
                    onClick = {},
                    testTag = "self_control_usage",
                ),
                SelfControlEntryUi(
                    title = stringResource(R.string.s_c7380c3c20),
                    description = stringResource(R.string.s_302471b81d),
                    icon = PerfIcon.Equalizer,
                    onClick = {},
                    testTag = "self_control_review",
                ),
                SelfControlEntryUi(
                    title = stringResource(R.string.s_63c1371c03),
                    description = stringResource(R.string.s_6905b9f1f9),
                    icon = PerfIcon.Schedule,
                    onClick = {},
                    testTag = "self_control_focus",
                ),
                SelfControlEntryUi(
                    title = stringResource(R.string.s_e6bbd743b3),
                    description = stringResource(R.string.s_25d9aca60f),
                    icon = PerfIcon.Block,
                    onClick = {},
                    testTag = "self_control_app_blocker",
                ),
                SelfControlEntryUi(
                    title = stringResource(R.string.s_dcbbbab7a5),
                    description = stringResource(R.string.s_86629471c3),
                    icon = PerfIcon.Info,
                    onClick = {},
                    testTag = "self_control_url_blocker",
                ),
                SelfControlEntryUi(
                    title = stringResource(R.string.s_6337015d1f),
                    description = stringResource(R.string.s_0b707d6dcc),
                    icon = PerfIcon.Lock,
                    onClick = {},
                    testTag = "self_control_lock",
                ),
            ),
        )
    }
}

@PreviewTest
@Preview(name = "Settings dark en", showBackground = true, widthDp = 700, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES, locale = "en")
@Composable
fun ScreenshotSettingsDarkEn() {
    MaterialTheme {
        SettingsSearchResultsContent(
            entries = SettingsSearchPolicy.search(SettingsIndex.entries, "Privacy"),
            highlightedId = null,
            onOpen = {},
        )
    }
}

@PreviewTest
@Preview(name = "Settings default compact", showBackground = true, widthDp = 360)
@Composable
fun ScreenshotSettingsDefaultCompact() {
    MaterialTheme {
        SettingsSearchResultsContent(
            entries = SettingsIndex.entries,
            highlightedId = null,
            onOpen = {},
        )
    }
}

@PreviewTest
@Preview(
    name = "Settings search highlighted en dark",
    showBackground = true,
    widthDp = 700,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
    locale = "en",
)
@Composable
fun ScreenshotSettingsSearchHighlightedEnDark() {
    MaterialTheme {
        SettingsSearchResultsContent(
            entries = SettingsSearchPolicy.search(SettingsIndex.entries, "Privacy"),
            highlightedId = "privacy_data",
            onOpen = {},
        )
    }
}

@PreviewTest
@Preview(name = "Privacy data default", showBackground = true, widthDp = 360)
@Composable
fun ScreenshotPrivacyDataDefault() {
    MaterialTheme {
        PrivacyDataContent(
            inventory = DataCategory.entries.associateWith { category ->
                DataDeletionCoordinator.CategoryStatus(
                    recordCount = if (category == DataCategory.ALL_APP_DATA) 42L else 3L,
                    bytes = if (category == DataCategory.SNAPSHOTS || category == DataCategory.DIAGNOSTICS_CRASH_SUMMARY) {
                        204_800L
                    } else {
                        null
                    },
                )
            },
            onDelete = {},
            cleartextOrigins = setOf("http://example.com:80"),
        )
    }
}

@PreviewTest
@Preview(name = "Privacy data danger zone", showBackground = true, widthDp = 360)
@Composable
fun ScreenshotPrivacyDataDangerZone() {
    MaterialTheme {
        PrivacyDataContent(
            inventory = DataCategory.entries.associateWith { category ->
                DataDeletionCoordinator.CategoryStatus(
                    recordCount = if (category == DataCategory.ALL_APP_DATA) 42L else 0L,
                    bytes = null,
                )
            },
            onDelete = {},
            cleartextOrigins = emptySet(),
        )
    }
}

@PreviewTest
@Preview(name = "Privacy delete confirm", showBackground = true, widthDp = 360)
@Composable
fun ScreenshotPrivacyDeleteConfirm() {
    MaterialTheme {
        AppConfirmationDialog(
            title = stringResource(R.string.privacy_delete_title),
            objectName = stringResource(R.string.privacy_data_title),
            description = stringResource(R.string.privacy_data_intro),
            confirmText = stringResource(R.string.privacy_delete),
            onConfirm = {},
            onDismiss = {},
            destructive = true,
            requiresPhrase = true,
        )
    }
}

@PreviewTest
@Preview(name = "Settings search privacy", showBackground = true, widthDp = 360)
@Composable
fun ScreenshotSettingsSearchPrivacy() {
    MaterialTheme {
        SettingsSearchResultsContent(
            entries = SettingsSearchPolicy.search(SettingsIndex.entries, "隐私"),
            highlightedId = null,
            onOpen = {},
        )
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
