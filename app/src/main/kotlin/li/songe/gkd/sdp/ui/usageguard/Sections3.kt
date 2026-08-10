@file:JvmName("UsageGuardSections3")

package li.songe.gkd.sdp.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import li.songe.gkd.sdp.util.UsageGuardPolicy
import li.songe.gkd.sdp.util.UsageGuardUiStatePolicy
import androidx.compose.ui.res.stringResource
import li.songe.gkd.sdp.R
import li.songe.gkd.sdp.app

@Composable
internal fun UsageGuardStatusSection(state: UsageGuardSettingsRenderState) {
    val settings = state.settings
    val vm = state.vm
    val durationOptions = state.durationOptions
    SectionCard(
        title = stringResource(R.string.s_e88c1f786e),
        subtitle = stringResource(R.string.s_21e2314ce6),
    ) {
        SettingRow(
            title = app.getString(R.string.s_2755dbd77c),
            subtitle = if (settings.usageGuardEnabled) {
                app.getString(R.string.s_bc3692dc28)
            } else {
                app.getString(R.string.s_d0b1c07c8f)
            },
            trailing = {
                Switch(
                    checked = settings.usageGuardEnabled,
                    onCheckedChange = vm::updateEnabled,
                )
            },
        )
        HorizontalDivider()
        CompactInfoRow(
            label = app.getString(R.string.s_96b029d1e1),
            value = if (settings.usageGuardScopeMode == UsageGuardPolicy.SCOPE_SELECTED_ONLY) {
                "仅选中应用"
            } else {
                "全局生效（白名单跳过）"
            },
        )
        CompactInfoRow(
            label = app.getString(R.string.s_ae2399c597),
            value = if (settings.usageGuardDefaultGrantMode == UsageGuardPolicy.GRANT_MODE_STRICT) {
                "严格模式"
            } else {
                "普通模式"
            },
        )
        CompactInfoRow(
            label = app.getString(R.string.s_30a60b1367),
            value = durationOptions.joinToString(" / ", transform = ::usageGuardDurationLabel),
        )
        Text(
            text = UsageGuardUiStatePolicy.protectionStatusAutoReenableMessage(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
