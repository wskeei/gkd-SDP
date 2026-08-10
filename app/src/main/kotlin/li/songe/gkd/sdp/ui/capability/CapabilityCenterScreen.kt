package li.songe.gkd.sdp.ui.capability

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import li.songe.gkd.sdp.MainActivity
import li.songe.gkd.sdp.MainViewModel
import li.songe.gkd.sdp.R
import li.songe.gkd.sdp.capability.CapabilityActionTarget
import li.songe.gkd.sdp.capability.CapabilityId
import li.songe.gkd.sdp.capability.CapabilityNode
import li.songe.gkd.sdp.capability.CapabilityStatus
import li.songe.gkd.sdp.capability.RuntimeModeChoice
import li.songe.gkd.sdp.ui.component.PerfIcon
import li.songe.gkd.sdp.ui.component.PerfTopAppBar
import li.songe.gkd.sdp.ui.share.LocalMainViewModel
import li.songe.gkd.sdp.ui.style.DimensionTokens
import li.songe.gkd.sdp.ui.style.surfaceCardColors
import li.songe.gkd.sdp.util.AutomatorModeOption
import li.songe.gkd.sdp.util.openA11ySettings
import li.songe.gkd.sdp.util.openAppDetailsSettings
import li.songe.gkd.sdp.util.openBatterySettings
import li.songe.gkd.sdp.util.openNotificationSettings
import li.songe.gkd.sdp.util.openOverlaySettings
import li.songe.gkd.sdp.util.throttle

/**
 * Single-page runtime capability setup: total status, the one next step,
 * ready capabilities, limited-but-not-blocking capabilities and a short
 * explanation of why permissions are needed.
 */
@Composable
fun CapabilityCenterScreen(mainVm: MainViewModel) {
    val activity = androidx.activity.compose.LocalActivity.current as MainActivity
    var refreshKey by remember { mutableIntStateOf(0) }
    val graph = resolveCapabilityGraph(mainVm, refreshKey)
    val nextStep = graph.nextStep

    Scaffold(
        topBar = {
            PerfTopAppBar(
                navigationIcon = {
                    PerfIconButtonBack { mainVm.popPage() }
                },
                title = {
                    Text(stringResource(R.string.s_4a338bcd08))
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            TotalStatusHeader(
                graph = graph,
                onRefresh = { refreshKey++ },
            )
            nextStep?.let { node ->
                NextStepCard(node = node, onAction = { onCapabilityAction(mainVm, activity, node) })
            }
            CapabilitySection(
                title = "已就绪",
                nodes = graph.nodes.filter { it.status == CapabilityStatus.READY || it.status == CapabilityStatus.ACTIVE },
                onAction = { onCapabilityAction(mainVm, activity, it) },
            )
            CapabilitySection(
                title = "受限但不阻断",
                nodes = graph.nodes.filter { it.status == CapabilityStatus.LIMITED },
                onAction = { onCapabilityAction(mainVm, activity, it) },
            )
            Text(
                text = "为什么需要这些权限？",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(DimensionTokens.SpacingBase),
            )
            Text(
                text = "运行模式决定使用哪些系统能力：无障碍模式通过系统无障碍服务读取屏幕；自动化模式通过 Shizuku 直接调用系统接口。其余权限只用于展示申请、拦截提示、守护提醒与规则管理。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = DimensionTokens.SpacingBase),
            )
        }
    }
}

@Composable
private fun TotalStatusHeader(graph: li.songe.gkd.sdp.capability.CapabilityGraph, onRefresh: () -> Unit) {
    val ready = graph.nodes.count {
        it.status == CapabilityStatus.READY || it.status == CapabilityStatus.ACTIVE
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(DimensionTokens.SpacingBase),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "运行能力：$ready/${graph.nodes.size} 就绪",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = graph.nextStep?.let { "下一步：${it.summary}" } ?: "全部就绪，可以开始使用",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Button(onClick = throttle(onRefresh)) {
            Text("重新检查")
        }
    }
    HorizontalDivider()
}

@Composable
private fun NextStepCard(node: CapabilityNode, onAction: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(DimensionTokens.SpacingBase),
        colors = surfaceCardColors,
        shape = CardDefaults.shape,
    ) {
        Column(modifier = Modifier.padding(DimensionTokens.SpacingBase)) {
            Text(
                text = "唯一下一步",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = node.summary,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = DimensionTokens.SpacingSm),
            )
            node.primaryAction?.let { action ->
                Button(
                    onClick = onAction,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = DimensionTokens.SpacingBase),
                ) {
                    Text(action.label)
                }
            }
        }
    }
}

@Composable
private fun CapabilitySection(
    title: String,
    nodes: List<CapabilityNode>,
    onAction: (CapabilityNode) -> Unit,
) {
    if (nodes.isEmpty()) return
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(horizontal = DimensionTokens.SpacingBase),
    )
    nodes.forEach { node ->
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = DimensionTokens.SpacingBase, vertical = DimensionTokens.SpacingSm),
            colors = surfaceCardColors,
            shape = CardDefaults.shape,
        ) {
            Row(
                modifier = Modifier.padding(DimensionTokens.SpacingBase),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = node.summary,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                node.primaryAction?.let { action ->
                    Button(onClick = { onAction(node) }) {
                        Text(action.label)
                    }
                }
            }
        }
    }
}

private fun onCapabilityAction(
    mainVm: MainViewModel,
    activity: MainActivity,
    node: CapabilityNode,
) {
    val target = node.primaryAction?.target ?: return
    when (target) {
        CapabilityActionTarget.SET_MODE_ACCESSIBILITY ->
            mainVm.updateAutomatorMode(AutomatorModeOption.A11yMode)

        CapabilityActionTarget.SET_MODE_AUTOMATION ->
            mainVm.updateAutomatorMode(AutomatorModeOption.AutomationMode)

        CapabilityActionTarget.OPEN_A11Y_SETTINGS -> openA11ySettings()
        CapabilityActionTarget.OPEN_OVERLAY_SETTINGS -> openOverlaySettings()
        CapabilityActionTarget.OPEN_NOTIFICATION_SETTINGS -> openNotificationSettings()
        CapabilityActionTarget.OPEN_BATTERY_SETTINGS -> openBatterySettings()
        CapabilityActionTarget.OPEN_SHIZUKU ->
            runCatching { li.songe.gkd.sdp.shizuku.shizukuContextFlow.value.grantSelf() }

        CapabilityActionTarget.TOGGLE_A11Y_GUARD -> mainVm.toggleAccessibilityGuard(activity)
        CapabilityActionTarget.OPEN_APP_LIST_SETTINGS -> openAppDetailsSettings()
    }
}

@Composable
private fun PerfIconButtonBack(onClick: () -> Unit) {
    li.songe.gkd.sdp.ui.component.PerfIconButton(
        imageVector = PerfIcon.ArrowBack,
        onClick = onClick,
    )
}
