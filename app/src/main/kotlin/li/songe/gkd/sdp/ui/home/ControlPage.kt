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
import li.songe.gkd.sdp.ui.capability.CapabilityCenterRoute
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

internal data class OverviewContentState(
    val appOpsRestricted: Boolean = false,
    val serviceTitle: String = "",
    val serviceSubtitle: String = "",
    val serviceChecked: Boolean = false,
    val notificationTitle: String = "",
    val notificationSubtitle: String = "",
    val notificationChecked: Boolean = false,
    val inspectorVisible: Boolean = false,
    val inspectorSubtitle: String = "",
    val reviewSubtitle: String = "",
    val activityLogVisible: Boolean = false,
    val activityLogSubtitle: String = "",
    val usedSubsItemCount: Int = 0,
    val serverStatus: String = "",
    val latestRecordText: String? = null,
    val latestRecordIsGlobal: Boolean = false,
)

@Composable
fun useControlPage(vm: HomeVm = viewModel()): ScaffoldExt {
    val context = LocalActivity.current as? MainActivity
    val mainVm = LocalMainViewModel.current
    var showA11yDisableInfoDialog by rememberSaveable { mutableStateOf(false) }
    val scrollKey = rememberSaveable { mutableIntStateOf(0) }
    val (scrollBehavior, scrollState) = useScrollBehaviorState(scrollKey)
    LaunchedEffect(null) {
        mainVm.resetPageScrollEvent.collect {
            if (it == HomeDestination.OVERVIEW) {
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
                    onClickLabel = stringResource(R.string.overview_go_work_mode_label),
                    contentDescription = stringResource(R.string.overview_work_mode_content_description),
                    onClick = throttle {
                        mainVm.navigatePage(CapabilityCenterRoute)
                    },
                )
            })
        }) { contentPadding ->
            val store by storeFlow.collectAsStateWithLifecycle()
            val a11yRunning by A11yService.isRunning.collectAsStateWithLifecycle()
            val manageRunning by StatusService.isRunning.collectAsStateWithLifecycle()
            val writeSecureSettings by writeSecureSettingsState.stateFlow.collectAsStateWithLifecycle()
            val useA11yPath = store.useA11y ||
                actualA11yScopeAppList.contains(topAppIdFlow.collectAsStateWithLifecycle().value)
            val automationRunning = uiAutomationFlow.collectAsStateWithLifecycle().value != null
            val serviceSubtitle = if (useA11yPath) {
                when {
                    a11yRunning -> stringResource(R.string.overview_service_accessibility_running)
                    mainVm.a11yServiceEnabledFlow.collectAsStateWithLifecycle().value ->
                        stringResource(R.string.overview_service_accessibility_fault)
                    writeSecureSettings -> {
                        if (store.enableAutomator && a11yPartDisabledFlow.collectAsStateWithLifecycle().value) {
                            stringResource(R.string.overview_service_accessibility_partial_off)
                        } else {
                            stringResource(R.string.overview_service_accessibility_off)
                        }
                    }
                    else -> stringResource(R.string.overview_service_accessibility_unauthorized)
                }
            } else {
                when {
                    automationRunning -> stringResource(R.string.overview_service_automation_running)
                    !shizukuContextFlow.collectAsStateWithLifecycle().value.ok ->
                        stringResource(R.string.overview_service_automation_unauthorized)
                    store.enableAutomator && a11yPartDisabledFlow.collectAsStateWithLifecycle().value ->
                        stringResource(R.string.overview_service_automation_partial_off)
                    else -> stringResource(R.string.overview_service_automation_off)
                }
            }
            val inspectorVisible = HttpService.isRunning.collectAsStateWithLifecycle().value
            val remoteSession by HttpService.remoteSessionStateFlow.collectAsStateWithLifecycle()
            val usageGuardSummary by vm.usageGuardReviewSummaryFlow.collectAsStateWithLifecycle()
            val digitalSelfDisciplineToday by vm.digitalSelfDisciplineTodaySummaryFlow.collectAsStateWithLifecycle()
            val usageGuardWidgetSummary = UsageGuardReviewPolicy.widgetSummary(usageGuardSummary)
            val usedSubsItemCount by vm.usedSubsItemCountFlow.collectAsStateWithLifecycle()
            val subsStatus by vm.subsStatusFlow.collectAsStateWithLifecycle()
            val latestRecordDesc by latestRecordDescFlow.collectAsStateWithLifecycle()
            val overview = OverviewContentState(
                appOpsRestricted = appOpsRestrictedFlow.collectAsStateWithLifecycle().value,
                serviceSubtitle = serviceSubtitle,
                serviceChecked = if (useA11yPath) a11yRunning else automationRunning,
                notificationChecked = manageRunning && store.enableStatusService,
                inspectorVisible = inspectorVisible,
                inspectorSubtitle = if (remoteSession.mode == RemoteListenMode.LOCAL_ONLY) {
                    stringResource(
                        R.string.overview_inspector_local_summary,
                        stringResource(
                            if (remoteSession.paired) {
                                R.string.overview_inspector_paired
                            } else {
                                R.string.overview_inspector_waiting_pair
                            },
                        ),
                        "${remoteSession.enabledScopes.size}/${RemoteScope.entries.size}",
                    )
                } else {
                    val minutes = remoteSession.accessExpiresAtMillis
                        ?.let { ((it - System.currentTimeMillis()).coerceAtLeast(0) + 59_999) / 60_000 }
                        ?: 0
                    stringResource(
                        R.string.overview_lan_session_summary,
                        stringResource(R.string.overview_lan_session_minutes, minutes),
                        remoteSession.clientSummary
                            ?: stringResource(R.string.overview_inspector_waiting_pair),
                        "${remoteSession.enabledScopes.size}/${RemoteScope.entries.size}",
                    )
                },
                reviewSubtitle = stringResource(
                    R.string.review_home_summary,
                    digitalSelfDisciplineToday.requestCount,
                    digitalSelfDisciplineToday.interceptCount,
                ) + "｜" + stringResource(usageGuardWidgetSummary.hintRes),
                activityLogVisible = ActivityService.isRunning.collectAsStateWithLifecycle().value,
                notificationTitle = stringResource(R.string.overview_notification_title),
                notificationSubtitle = stringResource(R.string.overview_notification_subtitle),
                activityLogSubtitle = stringResource(R.string.overview_activity_log_subtitle),
                serviceTitle = stringResource(R.string.overview_service_title),
                usedSubsItemCount = usedSubsItemCount,
                serverStatus = subsStatus,
                latestRecordText = latestRecordDesc,
                latestRecordIsGlobal = latestRecordFlow.collectAsStateWithLifecycle().value?.groupType ==
                    SubsConfig.GlobalGroupType,
            )

            Column(
                modifier = Modifier
                    .verticalScroll(scrollState)
                    .padding(contentPadding),
            ) {
                OverviewContent(
                    state = overview,
                    onOpenAppOps = { mainVm.navigateWebPage(ShortUrlSet.URL2) },
                    onToggleService = { newEnabled ->
                        when (HomeA11yServiceTogglePolicy.action(newEnabled, writeSecureSettings)) {
                            HomeA11yServiceTogglePolicy.Action.OPEN_AUTHORIZATION -> {
                                mainVm.navigatePage(CapabilityCenterRoute)
                            }

                            HomeA11yServiceTogglePolicy.Action.START_OR_REPAIR -> {
                                requestStartOrRepairAutomatorService()
                            }

                            HomeA11yServiceTogglePolicy.Action.EXPLAIN_SYSTEM_SETTINGS -> {
                                showA11yDisableInfoDialog = true
                            }
                        }
                    },
                    onToggleNotification = vm.viewModelScope.launchAsFn<Boolean> {
                        if (it) {
                            context?.let { StatusService.requestStart(it) }
                        } else if (store.accessibilityGuardEnabled) {
                            toast(li.songe.gkd.sdp.app.getString(R.string.s_f10262d528))
                        } else {
                            StatusService.stop()
                            storeFlow.value = store.copy(enableStatusService = false)
                        }
                    },
                    onOpenInspector = { mainVm.navigatePage(AdvancedPageRoute) },
                    onOpenReview = { mainVm.navigatePage(UsageGuardReviewRoute) },
                    onOpenActionLog = { mainVm.navigatePage(ActionLogRoute()) },
                    onOpenActivityLog = { mainVm.navigatePage(ActivityLogRoute) },
                    onOpenDocs = { mainVm.navigatePage(WebViewRoute(initUrl = HOME_PAGE_URL)) },
                    onOpenLatestRecord = {
                        latestRecordFlow.value?.let {
                            mainVm.navigatePage(AppConfigRoute(appId = it.appId, focusLog = it))
                        }
                    },
                )
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
internal fun OverviewContent(
    state: OverviewContentState,
    onOpenAppOps: () -> Unit = {},
    onToggleService: (Boolean) -> Unit = {},
    onToggleNotification: (Boolean) -> Unit = {},
    onOpenInspector: () -> Unit = {},
    onOpenReview: () -> Unit = {},
    onOpenActionLog: () -> Unit = {},
    onOpenActivityLog: () -> Unit = {},
    onOpenDocs: () -> Unit = {},
    onOpenLatestRecord: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .padding(horizontal = DimensionTokens.SpacingBase),
        verticalArrangement = Arrangement.spacedBy(DimensionTokens.SpacingBase / 2),
    ) {
        if (state.appOpsRestricted) {
            val removeRestrictionLabel = stringResource(R.string.overview_remove_restriction_label)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics(mergeDescendants = true) {
                        this.onClick(label = removeRestrictionLabel, action = null)
                    },
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                onClick = onOpenAppOps,
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

        PageSwitchItemCard(
            imageVector = PerfIcon.Memory,
            title = state.serviceTitle,
            subtitle = state.serviceSubtitle,
            checked = state.serviceChecked,
            onCheckedChange = onToggleService,
        )

        PageSwitchItemCard(
            imageVector = PerfIcon.Notifications,
            title = state.notificationTitle,
            subtitle = state.notificationSubtitle,
            checked = state.notificationChecked,
            onCheckedChange = onToggleNotification,
        )

        ServerStatusCardContent(state, onOpenLatestRecord)

        if (state.inspectorVisible) {
            PageItemCard(
                title = stringResource(R.string.overview_local_inspector_title),
                subtitle = state.inspectorSubtitle,
                imageVector = PerfIcon.Api,
                onClickLabel = stringResource(R.string.overview_open_inspector_settings),
                onClick = onOpenInspector,
            )
        }

        PageItemCard(
            title = stringResource(R.string.overview_review_title),
            subtitle = state.reviewSubtitle,
            imageVector = PerfIcon.Equalizer,
            onClickLabel = stringResource(R.string.overview_open_review),
            onClick = onOpenReview,
        )

        PageItemCard(
            title = stringResource(R.string.overview_trigger_log_title),
            subtitle = stringResource(R.string.overview_trigger_log_subtitle),
            imageVector = PerfIcon.History,
            onClickLabel = stringResource(R.string.overview_open_trigger_log),
            onClick = onOpenActionLog,
        )

        if (state.activityLogVisible) {
            PageItemCard(
                title = stringResource(R.string.overview_activity_log_title),
                subtitle = state.activityLogSubtitle,
                imageVector = PerfIcon.Layers,
                onClickLabel = stringResource(R.string.overview_open_activity_log),
                onClick = onOpenActivityLog,
            )
        }

        PageItemCard(
            title = stringResource(R.string.overview_learn_gkd_title),
            subtitle = stringResource(R.string.overview_learn_gkd_subtitle),
            imageVector = PerfIcon.HelpOutline,
            onClickLabel = stringResource(R.string.overview_open_gkd_docs),
            onClick = onOpenDocs,
        )
        Spacer(modifier = Modifier.height(EmptyHeight))
    }
}

@Composable
private fun ServerStatusCardContent(
    state: OverviewContentState,
    onOpenLatestRecord: () -> Unit,
) {
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
                    bottom = DimensionTokens.SpacingMd / 2,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PerfIcon(
                imageVector = PerfIcon.Equalizer,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(8.dp)
                    .size(24.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.width(DimensionTokens.SpacingBase))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.s_354077c764),
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (state.usedSubsItemCount > 0) {
                    Text(
                        text = stringResource(R.string.s_a6eec0915a, state.usedSubsItemCount.toString()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = DimensionTokens.SpacingMd),
        ) {
            if (state.serverStatus.isNotEmpty()) {
                Text(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    text = state.serverStatus,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            state.latestRecordText?.let { text ->
                Row(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .clip(MaterialTheme.shapes.extraSmall)
                        .clickable(onClickLabel = stringResource(R.string.overview_open_latest_record), onClick = onOpenLatestRecord)
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        GroupNameText(
                            modifier = Modifier.fillMaxWidth(),
                            preText = stringResource(R.string.overview_latest_trigger_prefix),
                            isGlobal = state.latestRecordIsGlobal,
                            text = text,
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
    val toggleLabel = stringResource(R.string.overview_toggle_action, title)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                this.onClick(label = toggleLabel, action = null)
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
                        .clickable(onClickLabel = stringResource(R.string.overview_open_latest_record), onClick = throttle {
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
                            preText = stringResource(R.string.overview_latest_trigger_prefix),
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
