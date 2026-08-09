@file:JvmName("UsageGuardSections3")

package li.songe.gkd.sdp.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import li.songe.gkd.sdp.util.UsageGuardPolicy
import li.songe.gkd.sdp.util.UsageGuardUiStatePolicy

@Composable
internal fun UsageGuardStatusSection(state: UsageGuardSettingsRenderState) {
    val settings = state.settings
    val vm = state.vm
    val durationOptions = state.durationOptions
            SectionCard(
                title = "保护状态",
                subtitle = "先确认总开关，再核对当前保护范围和默认策略。",
            ) {
                SettingRow(
                    title = "使用申请总开关",
                    subtitle = if (settings.usageGuardEnabled) {
                        "已启用，打开受控应用前需要先申请"
                    } else {
                        "未启用，当前不会拦截受控应用"
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
                    label = "当前范围",
                    value = if (settings.usageGuardScopeMode == UsageGuardPolicy.SCOPE_SELECTED_ONLY) {
                        "仅选中应用"
                    } else {
                        "全局生效（白名单跳过）"
                    },
                )
                CompactInfoRow(
                    label = "默认授权",
                    value = if (settings.usageGuardDefaultGrantMode == UsageGuardPolicy.GRANT_MODE_STRICT) {
                        "严格模式"
                    } else {
                        "普通模式"
                    },
                )
                CompactInfoRow(
                    label = "快速时长",
                    value = durationOptions.joinToString(" / ", transform = ::usageGuardDurationLabel),
                )
                Text(
                    text = UsageGuardUiStatePolicy.protectionStatusAutoReenableMessage(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
}
