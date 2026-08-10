@file:JvmName("FocusLockSections21")

package li.songe.gkd.sdp.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import li.songe.gkd.sdp.a11y.sdpRuntimeFeatureCoordinator
import li.songe.gkd.sdp.data.ConstraintConfig
import li.songe.gkd.sdp.permission.canDrawOverlaysState
import li.songe.gkd.sdp.ui.component.PerfIcon
import li.songe.gkd.sdp.ui.style.surfaceCardColors
import li.songe.gkd.sdp.util.format
import androidx.compose.ui.res.stringResource
import li.songe.gkd.sdp.R
import li.songe.gkd.sdp.app

@Composable
fun SelfControlRuntimeStatusCard() {
    val context = LocalContext.current
    val runtime by sdpRuntimeFeatureCoordinator.statusFlow.collectAsStateWithLifecycle()
    val overlayPermission by canDrawOverlaysState.stateFlow.collectAsStateWithLifecycle()
    val readiness = li.songe.gkd.sdp.util.SelfControlRuntimeReadiness.evaluate(
        mode = runtime.mode,
        connected = runtime.connected,
        switching = runtime.switching,
        overlayPermission = overlayPermission,
    )
    val issueText = when (readiness.issue) {
        li.songe.gkd.sdp.util.SelfControlRuntimeReadiness.Issue.None -> "可拦截"
        li.songe.gkd.sdp.util.SelfControlRuntimeReadiness.Issue.Switching -> "运行模式切换中"
        li.songe.gkd.sdp.util.SelfControlRuntimeReadiness.Issue.RuntimeUnavailable -> "运行引擎未连接"
        li.songe.gkd.sdp.util.SelfControlRuntimeReadiness.Issue.OverlayPermissionMissing -> "悬浮窗权限缺失"
    }
    ElevatedCard(
        colors = surfaceCardColors,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.s_7e3742f2bf),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.s_18a07b6ed5, readiness.modeLabel, when {
                    runtime.switching -> "切换中"
                    runtime.connected -> "已连接"
                    else -> "未连接"
                }),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = stringResource(R.string.s_95e55216b8, if (overlayPermission) "已授权" else "未授权", issueText),
                style = MaterialTheme.typography.bodySmall,
                color = if (readiness.ready) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
            runtime.lastDecision?.let { decision ->
                Text(
                    text = app.getString(R.string.s_af2816cc17, decision.feature, decision.decision, li.songe.gkd.sdp.util.SelfControlElapsedPolicy.formatAbsolute(decision.atEpochMs)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!overlayPermission) {
                TextButton(
                    onClick = {
                        context.startActivity(
                            android.content.Intent(
                                android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                android.net.Uri.parse("package:${context.packageName}"),
                            )
                        )
                    },
                ) {
                    Text(stringResource(R.string.s_ec206f889f))
                }
            }
        }
    }
}

// --- Data Models for Sheet Targets ---


@Composable
fun SubscriptionCard(
    subState: SubscriptionState,
    isExpanded: Boolean,
    expandedApps: Set<String>,
    onExpandSubs: () -> Unit,
    onExpandApp: (String) -> Unit,
    onLockClick: (LockTarget) -> Unit,
    onPauseClick: (PauseTarget) -> Unit
) {
    ElevatedCard(
        colors = surfaceCardColors,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        SubscriptionHeader(
            subState = subState,
            isExpanded = isExpanded,
            onExpand = onExpandSubs,
            onLockClick = onLockClick,
            onPauseClick = onPauseClick,
        )

        // Expanded Content
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                // Global Rules
                if (subState.globalRules.isNotEmpty()) {
                    Text(
                        text = app.getString(R.string.s_9effd4ccc9),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 56.dp, top = 8.dp, bottom = 4.dp)
                    )
                    subState.globalRules.forEach { rule ->
                        RuleItem(
                            state = rule,
                            paddingStart = 40.dp, // Indented
                            onLockClick = { onLockClick(LockTarget(ConstraintConfig.TYPE_RULE_GROUP, subState.subsId, null, rule.group.group.key, rule.group.group.name, currentEndTime = rule.lockEndTime)) },
                            onPauseClick = { onPauseClick(PauseTarget(subState.subsId, "", rule.group.group.key, rule.group.group.name, rule.interceptConfig, isLocked = rule.isLocked)) }
                        )
                    }
                }

                // App Rules
                subState.apps.forEach { appState ->
                    val isAppExpanded = expandedApps.contains("${subState.subsId}_${appState.appId}")

                    // App Header
                    Row(
                        modifier = Modifier
                            .clickable { onExpandApp(appState.appId) }
                            .padding(vertical = 8.dp, horizontal = 16.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Spacer(modifier = Modifier.width(24.dp)) // Indent
                        PerfIcon(
                            imageVector = if (isAppExpanded) PerfIcon.ArrowDownward else PerfIcon.KeyboardArrowRight,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = appState.appName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            if (appState.isLocked) {
                                Text(
                                    text = app.getString(R.string.s_1ebce11b0b, formatRemainingTime(appState.lockEndTime - System.currentTimeMillis())),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        // Batch Pause Button (App)
                        IconButton(
                            onClick = {
                                onPauseClick(PauseTarget(subState.subsId, appState.appId, null, appState.appName, null, isLocked = appState.isLocked, initialEnabled = appState.allInterceptEnabled))
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            PerfIcon(
                                imageVector = PerfIcon.Mindful,
                                tint = if (appState.allInterceptEnabled) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                onLockClick(LockTarget(ConstraintConfig.TYPE_APP, subState.subsId, appState.appId, null, appState.appName, currentEndTime = appState.lockEndTime))
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            PerfIcon(
                                imageVector = if (appState.isLocked) PerfIcon.Lock else PerfIcon.History,
                                tint = if (appState.isLocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // App Rules List
                    AnimatedVisibility(
                        visible = isAppExpanded,
                        enter = expandVertically(),
                        exit = shrinkVertically()
                    ) {
                        Column {
                            appState.rules.forEach { rule ->
                                RuleItem(
                                    state = rule,
                                    paddingStart = 64.dp, // More indented
                                    onLockClick = { onLockClick(LockTarget(ConstraintConfig.TYPE_RULE_GROUP, subState.subsId, appState.appId, rule.group.group.key, rule.group.group.name, currentEndTime = rule.lockEndTime)) },
                                    onPauseClick = { onPauseClick(PauseTarget(subState.subsId, appState.appId, rule.group.group.key, rule.group.group.name, rule.interceptConfig, isLocked = rule.isLocked)) }
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun SubscriptionHeader(
    subState: SubscriptionState,
    isExpanded: Boolean,
    onExpand: () -> Unit,
    onLockClick: (LockTarget) -> Unit,
    onPauseClick: (PauseTarget) -> Unit,
) {
    Row(
        modifier = Modifier
            .clickable(onClick = onExpand)
            .padding(16.dp)
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PerfIcon(
            imageVector = if (isExpanded) PerfIcon.ArrowDownward else PerfIcon.KeyboardArrowRight,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = subState.subsName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (subState.isLocked) {
                Text(
                    text = stringResource(R.string.s_72effd1c3d, formatRemainingTime(subState.lockEndTime - System.currentTimeMillis())),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        IconButton(
            onClick = {
                onPauseClick(
                    PauseTarget(
                        subsId = subState.subsId,
                        appId = null,
                        groupKey = null,
                        groupName = subState.subsName,
                        config = null,
                        isLocked = subState.isLocked,
                        initialEnabled = subState.allInterceptEnabled,
                    )
                )
            },
        ) {
            PerfIcon(
                imageVector = PerfIcon.Mindful,
                tint = if (subState.allInterceptEnabled) {
                    MaterialTheme.colorScheme.tertiary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                },
            )
        }
        IconButton(
            onClick = {
                onLockClick(
                    LockTarget(
                        type = ConstraintConfig.TYPE_SUBSCRIPTION,
                        subsId = subState.subsId,
                        appId = null,
                        groupKey = null,
                        name = subState.subsName,
                        currentEndTime = subState.lockEndTime,
                    )
                )
            },
        ) {
            PerfIcon(
                imageVector = if (subState.isLocked) PerfIcon.Lock else PerfIcon.History,
                tint = if (subState.isLocked) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                },
            )
        }
    }
}


internal fun formatRemainingTime(millis: Long): String {
    if (millis <= 0) return "已结束"
    val minutes = millis / 60000
    val hours = minutes / 60
    val remainingMinutes = minutes % 60
    val days = hours / 24
    val remainingHours = hours % 24

    return if (days > 0) {
        "${days}天${remainingHours}小时"
    } else if (hours > 0) {
        "${hours}小时${remainingMinutes}分钟"
    } else {
        "${minutes}分钟"
    }
}


@Composable
fun AccessibilityGuardCard(
    enabled: Boolean,
    armed: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    ElevatedCard(
        colors = surfaceCardColors,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PerfIcon(
                imageVector = PerfIcon.VerifiedUser,
                tint = if (enabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.s_748db62aba),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = when {
                        enabled -> stringResource(R.string.s_0c96adb235)
                        armed -> stringResource(R.string.s_c9f77c0be6)
                        else -> stringResource(R.string.s_9645ef9c2d)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (enabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = onCheckedChange,
            )
        }
    }
}
