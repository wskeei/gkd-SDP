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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import li.songe.gkd.sdp.MainActivity
import li.songe.gkd.sdp.R
import li.songe.gkd.sdp.data.SubsConfig
import li.songe.gkd.sdp.permission.appOpsRestrictedFlow
import li.songe.gkd.sdp.permission.writeSecureSettingsState
import li.songe.gkd.sdp.service.A11yService
import li.songe.gkd.sdp.service.ActivityService
import li.songe.gkd.sdp.service.StatusService
import li.songe.gkd.sdp.service.HttpService
import li.songe.gkd.sdp.service.a11yPartDisabledFlow
import li.songe.gkd.sdp.service.requestStartOrRepairAutomatorService
import li.songe.gkd.sdp.service.switchAutomatorService
import li.songe.gkd.sdp.service.topAppIdFlow
import li.songe.gkd.sdp.shizuku.shizukuContextFlow
import li.songe.gkd.sdp.shizuku.uiAutomationFlow
import li.songe.gkd.sdp.store.actualA11yScopeAppList
import li.songe.gkd.sdp.store.storeFlow
import li.songe.gkd.sdp.ui.ActionLogRoute
import li.songe.gkd.sdp.ui.ActivityLogRoute
import li.songe.gkd.sdp.ui.AdvancedPageRoute
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
import li.songe.gkd.sdp.ui.style.surfaceCardColors
import li.songe.gkd.sdp.util.HOME_PAGE_URL
import li.songe.gkd.sdp.util.HomeA11yServiceTogglePolicy
import li.songe.gkd.sdp.util.ShortUrlSet
import li.songe.gkd.sdp.util.UsageGuardReviewPolicy
import li.songe.gkd.sdp.ui.component.DigitalSelfDisciplineReviewPresentation
import li.songe.gkd.sdp.util.latestRecordDescFlow
import li.songe.gkd.sdp.util.latestRecordFlow
import li.songe.gkd.sdp.util.launchAsFn
import li.songe.gkd.sdp.util.openA11ySettings
import li.songe.gkd.sdp.util.throttle
import li.songe.gkd.sdp.util.toast
import li.songe.gkd.sdp.remote.RemoteListenMode
import li.songe.gkd.sdp.remote.RemoteScope
import li.songe.gkd.sdp.ui.style.DimensionTokens

@Composable
fun useControlPage(): ScaffoldExt {
    val context = LocalActivity.current as MainActivity
    val mainVm = LocalMainViewModel.current
    val vm = viewModel<HomeVm>()
    var showA11yDisableInfoDialog by rememberSaveable { mutableStateOf(false) }
    val scrollKey = rememberSaveable { mutableIntStateOf(0) }
    val (scrollBehavior, scrollState) = useScrollBehaviorState(scrollKey)
    LaunchedEffect(null) {
        mainVm.resetPageScrollEvent.collect {
            if (it == BottomNavItem.Control) {
                scrollKey.intValue++
            }
        }
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
        val store by storeFlow.collectAsStateWithLifecycle()

        val a11yRunning by A11yService.isRunning.collectAsStateWithLifecycle()
        val manageRunning by StatusService.isRunning.collectAsStateWithLifecycle()
        val writeSecureSettings by writeSecureSettingsState.stateFlow.collectAsStateWithLifecycle()

        Column(
            modifier = Modifier
                .verticalScroll(scrollState)
                .padding(contentPadding)
                .padding(horizontal = DimensionTokens.SpacingBase),
            verticalArrangement = Arrangement.spacedBy(DimensionTokens.SpacingBase / 2)
        ) {
            if (appOpsRestrictedFlow.collectAsStateWithLifecycle().value) {
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
                            .padding(DimensionTokens.SpacingMd),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        PerfIcon(imageVector = PerfIcon.WarningAmber)
                        Text(
                            modifier = Modifier.weight(1f),
                            text = stringResource(R.string.s_7917327c68),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        PerfIcon(imageVector = PerfIcon.KeyboardArrowRight)
                    }
                }
            }
            if (store.useA11y || actualA11yScopeAppList.contains(topAppIdFlow.collectAsStateWithLifecycle().value)) {
                PageSwitchItemCard(
                    imageVector = PerfIcon.Memory,
                    title = "服务状态",
                    subtitle = if (a11yRunning) {
                        "无障碍正在运行"
                    } else if (mainVm.a11yServiceEnabledFlow.collectAsStateWithLifecycle().value) {
                        "无障碍发生故障"
                    } else if (writeSecureSettings) {
                        if (store.enableAutomator && a11yPartDisabledFlow.collectAsStateWithLifecycle().value) {
                            "无障碍局部关闭"
                        } else {
                            "无障碍已关闭"
                        }
                    } else {
                        "无障碍未授权"
                    },
                    checked = a11yRunning,
                    onCheckedChange = { newEnabled ->
                        when (HomeA11yServiceTogglePolicy.action(newEnabled, writeSecureSettings)) {
                            HomeA11yServiceTogglePolicy.Action.OPEN_AUTHORIZATION -> {
                                mainVm.navigatePage(AuthA11yRoute)
                            }

                            HomeA11yServiceTogglePolicy.Action.START_OR_REPAIR -> {
                                requestStartOrRepairAutomatorService()
                            }

                            HomeA11yServiceTogglePolicy.Action.EXPLAIN_SYSTEM_SETTINGS -> {
                                showA11yDisableInfoDialog = true
                            }
                        }
                    },
                )
            } else {
                PageSwitchItemCard(
                    imageVector = PerfIcon.Memory,
                    title = "服务状态",
                    subtitle = if (uiAutomationFlow.collectAsStateWithLifecycle().value != null) {
                        "自动化正在运行"
                    } else if (!shizukuContextFlow.collectAsStateWithLifecycle().value.ok) {
                        "自动化未授权"
                    } else {
                        if (store.enableAutomator && a11yPartDisabledFlow.collectAsStateWithLifecycle().value) {
                            "自动化局部关闭"
                        } else {
                            "自动化已关闭"
                        }
                    },
                    checked = uiAutomationFlow.collectAsStateWithLifecycle().value != null,
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
                        toast(li.songe.gkd.sdp.app.getString(R.string.s_f10262d528))
                    } else {
                        StatusService.stop()
                        storeFlow.value = store.copy(
                            enableStatusService = false
                        )
                    }
                },
            )

            ServerStatusCard()
            if (HttpService.isRunning.collectAsStateWithLifecycle().value) {
                val remoteSession by HttpService.remoteSessionStateFlow.collectAsStateWithLifecycle()
                PageItemCard(
                    title = "本地 Inspector",
                    subtitle = if (remoteSession.mode == RemoteListenMode.LOCAL_ONLY) {
                        "仅本机监听｜${if (remoteSession.paired) "已配对" else "等待配对"}｜" +
                            "授权 ${remoteSession.enabledScopes.size}/${RemoteScope.entries.size}"
                    } else {
                        val minutes = remoteSession.accessExpiresAtMillis
                            ?.let { ((it - System.currentTimeMillis()).coerceAtLeast(0) + 59_999) / 60_000 }
                            ?: 0
                        "局域网会话剩余 $minutes 分钟｜${remoteSession.clientSummary ?: "等待配对"}｜" +
                            "授权 ${remoteSession.enabledScopes.size}/${RemoteScope.entries.size}"
                    },
                    imageVector = PerfIcon.Api,
                    onClickLabel = "打开本地 Inspector 设置",
                    onClick = { mainVm.navigatePage(AdvancedPageRoute) },
                )
            }

            val usageGuardSummary by vm.usageGuardReviewSummaryFlow.collectAsStateWithLifecycle()
            val digitalSelfDisciplineToday by vm.digitalSelfDisciplineTodaySummaryFlow.collectAsStateWithLifecycle()
            val usageGuardWidgetSummary = UsageGuardReviewPolicy.widgetSummary(usageGuardSummary)
            PageItemCard(
                title = "数字自律复盘",
                subtitle = "${DigitalSelfDisciplineReviewPresentation.homeSummary(
                    digitalSelfDisciplineToday.requestCount,
                    digitalSelfDisciplineToday.interceptCount,
                )}｜" +
                    usageGuardWidgetSummary.hint,
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

            if (ActivityService.isRunning.collectAsStateWithLifecycle().value) {
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

        if (showA11yDisableInfoDialog) {
            AlertDialog(
                onDismissRequest = { showA11yDisableInfoDialog = false },
                title = { Text(stringResource(R.string.s_f90b38d994)) },
                text = {
                    Text(stringResource(R.string.s_48594302d1))
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showA11yDisableInfoDialog = false
                            openA11ySettings()
                        },
                    ) {
                        Text(stringResource(R.string.s_9ea2c95e8d))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showA11yDisableInfoDialog = false }) {
                        Text(stringResource(R.string.s_4d0b4688c7))
                    }
                },
            )
        }
    }
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
            .padding(DimensionTokens.SpacingMd),
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
        Spacer(modifier = Modifier.width(DimensionTokens.SpacingBase))
        content()
    }
}

@Composable
private fun ServerStatusCard() {
    val mainVm = LocalMainViewModel.current
    val vm = viewModel<HomeVm>()
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = surfaceCardColors,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = DimensionTokens.SpacingMd,
                    end = DimensionTokens.SpacingMd,
                    top = DimensionTokens.SpacingMd,
                    bottom = DimensionTokens.SpacingMd / 2
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
            Spacer(modifier = Modifier.width(DimensionTokens.SpacingBase))
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = stringResource(R.string.s_354077c764),
                    style = MaterialTheme.typography.bodyLarge,
                )
                val usedSubsItemCount by vm.usedSubsItemCountFlow.collectAsStateWithLifecycle()
                AnimatedVisibility(usedSubsItemCount > 0) {
                    Text(
                        text = stringResource(R.string.s_a6eec0915a, (usedSubsItemCount).toString()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = DimensionTokens.SpacingMd)
        ) {
            val subsStatus by vm.subsStatusFlow.collectAsStateWithLifecycle()
            AnimatedVisibility(subsStatus.isNotEmpty()) {
                Text(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    text = subsStatus,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            val latestRecordDesc by latestRecordDescFlow.collectAsStateWithLifecycle()
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
                            isGlobal = latestRecordFlow.collectAsStateWithLifecycle().value?.groupType == SubsConfig.GlobalGroupType,
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
            Spacer(modifier = Modifier.height(DimensionTokens.SpacingMd))
        }
    }
}
