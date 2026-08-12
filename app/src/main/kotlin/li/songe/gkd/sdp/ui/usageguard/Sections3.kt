@file:JvmName("UsageGuardSections3")

package li.songe.gkd.sdp.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import li.songe.gkd.sdp.util.UsageGuardPolicy
import li.songe.gkd.sdp.util.UsageGuardUiStatePolicy
import androidx.compose.ui.res.stringResource
import li.songe.gkd.sdp.R

@Composable
internal fun UsageGuardStatusSection(state: UsageGuardSettingsRenderState) {
    val settings = state.state.settings
    val durationOptions = state.state.durationOptions
    SectionCard(
        title = stringResource(R.string.s_e88c1f786e),
        subtitle = stringResource(R.string.s_21e2314ce6),
    ) {
        SettingRow(
            title = li.songe.gkd.sdp.app.getString(R.string.s_2755dbd77c),
            subtitle = if (settings.usageGuardEnabled) {
                li.songe.gkd.sdp.app.getString(R.string.s_bc3692dc28)
            } else {
                li.songe.gkd.sdp.app.getString(R.string.s_d0b1c07c8f)
            },
            trailing = {
                Switch(
                    checked = settings.usageGuardEnabled,
                    onCheckedChange = {
                        state.onDispatch(UsageGuardAction.UpdateEnabled(it))
                    },
                )
            },
        )
        HorizontalDivider()
        CompactInfoRow(
            label = li.songe.gkd.sdp.app.getString(R.string.s_96b029d1e1),
            value = if (settings.usageGuardScopeMode == UsageGuardPolicy.SCOPE_SELECTED_ONLY) {
                stringResource(R.string.focus_lock_scope_selected)
            } else {
                stringResource(R.string.usage_guard_scope_global_whitelist)
            },
        )
        CompactInfoRow(
            label = li.songe.gkd.sdp.app.getString(R.string.s_ae2399c597),
            value = if (settings.usageGuardDefaultGrantMode == UsageGuardPolicy.GRANT_MODE_STRICT) {
                stringResource(R.string.usage_guard_strict_mode_title)
            } else {
                stringResource(R.string.usage_guard_normal_mode_title)
            },
        )
        CompactInfoRow(
            label = li.songe.gkd.sdp.app.getString(R.string.s_30a60b1367),
            value = durationOptions.joinToString(" / ") {
                usageGuardDurationLabel(it, li.songe.gkd.sdp.app)
            },
        )
        Text(
            text = stringResource(R.string.usage_guard_auto_reenable_message),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
