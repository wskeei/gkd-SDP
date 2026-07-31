package li.songe.gkd.sdp.ui.home

import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import li.songe.gkd.sdp.META
import li.songe.gkd.sdp.MainActivity
import li.songe.gkd.sdp.MainViewModel
import li.songe.gkd.sdp.R
import li.songe.gkd.sdp.data.SubsConfig
import li.songe.gkd.sdp.permission.appOpsRestrictedFlow
import li.songe.gkd.sdp.permission.canDrawOverlaysState
import li.songe.gkd.sdp.permission.foregroundServiceSpecialUseState
import li.songe.gkd.sdp.permission.notificationState
import li.songe.gkd.sdp.permission.requiredPermission
import li.songe.gkd.sdp.permission.writeSecureSettingsState
import li.songe.gkd.sdp.service.A11yService
import li.songe.gkd.sdp.service.ActivityService
import li.songe.gkd.sdp.service.AccessibilityGuardRuntime
import li.songe.gkd.sdp.service.StatusService
import li.songe.gkd.sdp.service.a11yPartDisabledFlow
import li.songe.gkd.sdp.service.switchAutomatorService
import li.songe.gkd.sdp.service.topAppIdFlow
import li.songe.gkd.sdp.shizuku.shizukuContextFlow
import li.songe.gkd.sdp.shizuku.uiAutomationFlow
import li.songe.gkd.sdp.store.actualA11yScopeAppList
import li.songe.gkd.sdp.store.storeFlow
import li.songe.gkd.sdp.ui.ActionLogRoute
import li.songe.gkd.sdp.ui.ActivityLogRoute
import li.songe.gkd.sdp.ui.AppConfigRoute
import li.songe.gkd.sdp.ui.AuthA11yRoute
import li.songe.gkd.sdp.ui.UsageGuardReviewRoute
import li.songe.gkd.sdp.ui.WebViewRoute
import li.songe.gkd.sdp.ui.component.GroupNameText
import li.songe.gkd.sdp.ui.component.PerfIcon
import li.songe.gkd.sdp.ui.component.PerfIconButton
import li.songe.gkd.sdp.ui.component.PerfSwitch
import li.songe.gkd.sdp.ui.component.PerfTopAppBar
import li.songe.gkd.sdp.ui.component.textSize
import li.songe.gkd.sdp.ui.component.useScrollBehaviorState
import li.songe.gkd.sdp.ui.share.LocalMainViewModel
import li.songe.gkd.sdp.ui.style.EmptyHeight
import li.songe.gkd.sdp.ui.style.itemHorizontalPadding
import li.songe.gkd.sdp.ui.style.itemVerticalPadding
import li.songe.gkd.sdp.ui.style.surfaceCardColors
import li.songe.gkd.sdp.util.HOME_PAGE_URL
import li.songe.gkd.sdp.util.ShortUrlSet
import li.songe.gkd.sdp.util.UsageGuardReviewPolicy
import li.songe.gkd.sdp.util.latestRecordDescFlow
import li.songe.gkd.sdp.util.latestRecordFlow
import li.songe.gkd.sdp.util.launchAsFn
import li.songe.gkd.sdp.util.throttle
import li.songe.gkd.sdp.util.toast

@Composable
fun useControlPage(): ScaffoldExt {
    val context = LocalActivity.current as MainActivity
    val mainVm = LocalMainViewModel.current
    val vm = viewModel<HomeVm>()
    val scrollKey = rememberSaveable { mutableIntStateOf(0) }
    var showAccessibilityGuardDialog by rememberSaveable { mutableStateOf(false) }
    val enableGuard = vm.viewModelScope.launchAsFn {
        enableAccessibilityGuard(context, mainVm)
    }
    val (scrollBehavior, scrollState) = useScrollBehaviorState(scrollKey)
    LaunchedEffect(null) {
        mainVm.resetPageScrollEvent.collect {
            if (it == BottomNavItem.Control) {
                scrollKey.intValue++
            }
        }
    }
    if (showAccessibilityGuardDialog) {
        AlertDialog(
            onDismissRequest = { showAccessibilityGuardDialog = false },
            title = { Text("开启无障碍权限守护") },
            text = {
                Text(
                    "开启后，无障碍权限关闭时将在 15、25、30、33、35、36 分钟" +
                        "分别提醒一次（间隔为 15/10/5/3/2/1 分钟）。" +
                        "第 36 分钟最后一次提醒后仍未恢复，会显示全屏悬浮窗。" +
                        "点击“前往”可回到应用首页；权限未恢复时离开应用，悬浮窗会再次出现。" +
                        "你可以随时在本页关闭守护。",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showAccessibilityGuardDialog = false
                        enableGuard()
                    },
                ) {
                    Text("同意并开启")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAccessibilityGuardDialog = false }) {
                    Text("取消")
                }
            },
        )
    }
    return ScaffoldExt(
        navItem = BottomNavItem.Control,
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            PerfTopAppBar(scrollBehavior = scrollBehavior, title = {
                Text(
                    text = stringResource(R.string.app_name)
                )
            }, actions = {
                PerfIconButton(
                    imageVector = PerfIcon.RocketLaunch,
                    onClickLabel = "前往工作模式页面",
                    contentDescription = "工作模式",
                    onClick = throttle {
                        mainVm.navigatePage(AuthA11yRoute)
                    },
                )
            })
        }) { contentPadding ->
        val store by storeFlow.collectAsState()

        val a11yRunning by A11yService.isRunning.collectAsState()
        val manageRunning by StatusService.isRunning.collectAsState()
        val writeSecureSettings by writeSecureSettingsState.stateFlow.collectAsState()

        Column(
            modifier = Modifier
                .verticalScroll(scrollState)
                .padding(contentPadding)
                .padding(horizontal = itemHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(itemHorizontalPadding / 2)
        ) {
            if (appOpsRestrictedFlow.collectAsState().value) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics(mergeDescendants = true) {
                            this.onClick(label = "前往解除限制页面", action = null)
                        },
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    onClick = throttle {
                        mainVm.navigateWebPage(ShortUrlSet.URL2)
                    },
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(itemVerticalPadding),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        PerfIcon(imageVector = PerfIcon.WarningAmber)
                        Text(
                            modifier = Modifier.weight(1f),
                            text = "检测到权限受限制，请前往解除",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        PerfIcon(imageVector = PerfIcon.KeyboardArrowRight)
                    }
                }
            }
            if (store.useA11y || actualA11yScopeAppList.contains(topAppIdFlow.collectAsState().value)) {
                PageSwitchItemCard(
                    imageVector = PerfIcon.Memory,
                    title = "服务状态",
                    subtitle = if (a11yRunning) {
                        "无障碍正在运行"
                    } else if (mainVm.a11yServiceEnabledFlow.collectAsState().value) {
                        "无障碍发生故障"
                    } else if (writeSecureSettings) {
                        if (store.enableAutomator && a11yPartDisabledFlow.collectAsState().value) {
                            "无障碍局部关闭"
                        } else {
                            "无障碍已关闭"
                        }
                    } else {
                        "无障碍未授权"
                    },
                    checked = a11yRunning,
                    onCheckedChange = { newEnabled ->
                        if (newEnabled && !writeSecureSettingsState.value) {
                            mainVm.navigatePage(AuthA11yRoute)
                        } else {
                            switchAutomatorService()
                        }
                    },
                )
            } else {
                PageSwitchItemCard(
                    imageVector = PerfIcon.Memory,
                    title = "服务状态",
                    subtitle = if (uiAutomationFlow.collectAsState().value != null) {
                        "自动化正在运行"
                    } else if (!shizukuContextFlow.collectAsState().value.ok) {
                        "自动化未授权"
                    } else {
                        if (store.enableAutomator && a11yPartDisabledFlow.collectAsState().value) {
                            "自动化局部关闭"
                        } else {
                            "自动化已关闭"
                        }
                    },
                    checked = uiAutomationFlow.collectAsState().value != null,
                    onCheckedChange = vm.viewModelScope.launchAsFn(Dispatchers.IO) { newEnabled ->
                        if (newEnabled) {
                            mainVm.guardShizukuContext()
                        }
                        switchAutomatorService()
                    },
                )
            }

            PageSwitchItemCard(
                imageVector = PerfIcon.Notifications,
                title = "常驻通知",
                subtitle = "显示运行状态及统计数据",
                checked = manageRunning && store.enableStatusService,
                onCheckedChange = vm.viewModelScope.launchAsFn<Boolean> {
                    if (it) {
                        StatusService.requestStart(context)
                    } else if (store.accessibilityGuardEnabled) {
                        toast("请先关闭无障碍权限守护")
                    } else {
                        StatusService.stop()
                        storeFlow.value = store.copy(
                            enableStatusService = false
                        )
                    }
                },
            )

            if (META.isGkdChannel) {
                PageSwitchItemCard(
                    imageVector = PerfIcon.VerifiedUser,
                    title = "无障碍权限守护",
                    subtitle = "关闭后按 15/10/5/3/2/1 分钟提醒，最后进入全屏提示",
                    checked = store.accessibilityGuardEnabled,
                    onCheckedChange = { enabled ->
                        if (enabled) {
                            showAccessibilityGuardDialog = true
                        } else {
                            disableAccessibilityGuard()
                        }
                    },
                )
            }

            ServerStatusCard()

            val usageGuardSummary by vm.usageGuardReviewSummaryFlow.collectAsState()
            val usageGuardWidgetSummary = UsageGuardReviewPolicy.widgetSummary(usageGuardSummary)
            PageItemCard(
                title = "数字自律复盘",
                subtitle = "${usageGuardWidgetSummary.metric}｜${usageGuardWidgetSummary.hint}",
                imageVector = PerfIcon.Equalizer,
                onClickLabel = "打开数字自律复盘页面",
                onClick = {
                    mainVm.navigatePage(UsageGuardReviewRoute)
                },
            )

            PageItemCard(
                title = "触发记录",
                subtitle = "规则误触可定位关闭",
                imageVector = PerfIcon.History,
                onClickLabel = "打开触发记录页面",
                onClick = {
                    mainVm.navigatePage(ActionLogRoute())
                })

            if (ActivityService.isRunning.collectAsState().value) {
                PageItemCard(
                    title = "界面日志",
                    subtitle = "记录打开的应用及界面",
                    imageVector = PerfIcon.Layers,
                    onClickLabel = "打开界面日志页面",
                    onClick = {
                        mainVm.navigatePage(ActivityLogRoute)
                    })
            }

            PageItemCard(
                title = "了解 GKD",
                subtitle = "查阅规则文档和常见问题",
                imageVector = PerfIcon.HelpOutline,
                onClickLabel = "打开 GKD 文档页面",
                onClick = {
                    mainVm.navigatePage(WebViewRoute(initUrl = HOME_PAGE_URL))
                })
            Spacer(modifier = Modifier.height(EmptyHeight))
        }
    }
}

private fun disableAccessibilityGuard() {
    storeFlow.update { it.copy(accessibilityGuardEnabled = false) }
    AccessibilityGuardRuntime.disableAndReset()
}

private suspend fun enableAccessibilityGuard(
    context: MainActivity,
    mainVm: MainViewModel,
) {
    if (!META.isGkdChannel) return
    if (!storeFlow.value.useA11y) {
        toast("请先切换到无障碍模式")
        mainVm.navigatePage(AuthA11yRoute)
        return
    }
    if (!mainVm.a11yServiceEnabledFlow.value) {
        toast("请先开启无障碍权限")
        mainVm.navigatePage(AuthA11yRoute)
        return
    }

    requiredPermission(context, notificationState)
    requiredPermission(context, foregroundServiceSpecialUseState)
    requiredPermission(context, canDrawOverlaysState)
    StatusService.requestStart(context)

    if (!storeFlow.value.useA11y || !mainVm.a11yServiceEnabledFlow.value ||
        !notificationState.updateAndGet() ||
        !foregroundServiceSpecialUseState.updateAndGet() ||
        !canDrawOverlaysState.updateAndGet()
    ) {
        return
    }
    storeFlow.update { it.copy(accessibilityGuardEnabled = true) }
    AccessibilityGuardRuntime.requestReconcile()
}


@Composable
private fun PageItemCard(
    imageVector: ImageVector,
    title: String,
    subtitle: String,
    onClickLabel: String,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                this.onClick(label = onClickLabel, action = null)
            },
        shape = MaterialTheme.shapes.large,
        colors = surfaceCardColors,
        onClick = throttle(fn = onClick)
    ) {
        IconTextCard(
            imageVector = imageVector,
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PageSwitchItemCard(
    imageVector: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val onClick = throttle { onCheckedChange(!checked) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                this.onClick(label = "切换$title", action = null)
            },
        shape = MaterialTheme.shapes.large,
        colors = surfaceCardColors,
        onClick = onClick,
    ) {
        IconTextCard(
            imageVector = imageVector,
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            PerfSwitch(
                checked = checked,
                onCheckedChange = null,
            )
        }
    }
}

@Composable
private fun IconTextCard(
    imageVector: ImageVector, content: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(itemVerticalPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PerfIcon(
            imageVector = imageVector,
            modifier = Modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(8.dp)
                .size(24.dp),
            tint = MaterialTheme.colorScheme.primary,
            contentDescription = null,
        )
        Spacer(modifier = Modifier.width(itemHorizontalPadding))
        content()
    }
}

@Composable
private fun ServerStatusCard() {
    val mainVm = LocalMainViewModel.current
    val vm = viewModel<HomeVm>()
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                onClick(label = "不执行操作", action = null)
            }, shape = RoundedCornerShape(20.dp), colors = surfaceCardColors, onClick = {}) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = itemVerticalPadding,
                    end = itemVerticalPadding,
                    top = itemVerticalPadding,
                    bottom = itemVerticalPadding / 2
                ), verticalAlignment = Alignment.CenterVertically
        ) {
            PerfIcon(
                imageVector = PerfIcon.Equalizer,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(8.dp)
                    .size(24.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(itemHorizontalPadding))
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "数据概览",
                    style = MaterialTheme.typography.bodyLarge,
                )
                val usedSubsItemCount by vm.usedSubsItemCountFlow.collectAsState()
                AnimatedVisibility(usedSubsItemCount > 0) {
                    Text(
                        text = "已开启 $usedSubsItemCount 条订阅",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = itemVerticalPadding)
        ) {
            val subsStatus by vm.subsStatusFlow.collectAsState()
            AnimatedVisibility(subsStatus.isNotEmpty()) {
                Text(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    text = subsStatus,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            val latestRecordDesc by latestRecordDescFlow.collectAsState()
            if (latestRecordDesc != null) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .clip(MaterialTheme.shapes.extraSmall)
                        .clickable(onClickLabel = "前往应用的规则汇总页面", onClick = throttle {
                            latestRecordFlow.value?.let {
                                mainVm.navigatePage(
                                    AppConfigRoute(
                                        appId = it.appId, focusLog = it
                                    )
                                )
                            }
                        })
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                    ) {
                        GroupNameText(
                            modifier = Modifier.fillMaxWidth(),
                            preText = "最近触发: ",
                            isGlobal = latestRecordFlow.collectAsState().value?.groupType == SubsConfig.GlobalGroupType,
                            text = latestRecordDesc ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    PerfIcon(
                        imageVector = PerfIcon.KeyboardArrowRight,
                        modifier = Modifier.textSize(style = MaterialTheme.typography.bodyMedium),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Spacer(modifier = Modifier.height(itemVerticalPadding))
        }
    }
}
