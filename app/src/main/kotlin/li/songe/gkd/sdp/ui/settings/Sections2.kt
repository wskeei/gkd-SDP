@file:JvmName("SettingsSections2")

package li.songe.gkd.sdp.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import li.songe.gkd.sdp.R
import li.songe.gkd.sdp.store.SettingsStore
import li.songe.gkd.sdp.ui.*
import li.songe.gkd.sdp.ui.component.*
import li.songe.gkd.sdp.ui.style.EmptyHeight
import li.songe.gkd.sdp.ui.style.titleItemPadding
import li.songe.gkd.sdp.util.AndroidTarget
import li.songe.gkd.sdp.util.DarkThemeOption
import li.songe.gkd.sdp.util.DisplayDensityOption
import li.songe.gkd.sdp.util.LanguageOption
import li.songe.gkd.sdp.util.findOption
import li.songe.gkd.sdp.settings.SettingsIndex
import li.songe.gkd.sdp.settings.SettingsEntry
import li.songe.gkd.sdp.settings.SettingsSearchPolicy
import li.songe.gkd.sdp.settings.SettingsTarget
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.NavKey

@Composable
internal fun SettingsContent(
    state: SettingsRenderState,
    callbacks: SettingsCallbacks,
    resetPageScrollEvent: kotlinx.coroutines.flow.Flow<HomeDestination>,
): ScaffoldExt {
    val store = state.store
    val scrollKey = rememberSaveable { mutableIntStateOf(0) }
    val (scrollBehavior, scrollState) = useScrollBehaviorState(scrollKey)
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var highlightedId by rememberSaveable { mutableStateOf<String?>(null) }
    val anchorPositions = remember { mutableStateMapOf<String, Int>() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        resetPageScrollEvent.collect {
            if (it == HomeDestination.SETTINGS) scrollKey.intValue++
        }
    }
    val showToastSettingsDlg = state.showToastSettingsDlg

    return ScaffoldExt(
        navItem = BottomNavItem.Settings,
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            PerfTopAppBar(scrollBehavior = scrollBehavior, title = { Text(stringResource(BottomNavItem.Settings.labelRes)) })
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .verticalScroll(scrollState)
                .padding(contentPadding)
                .testTag("settings_page_root"),
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("settings_search_field"),
                label = { Text(stringResource(R.string.settings_search)) },
                leadingIcon = {
                    PerfIcon(
                        imageVector = PerfIcon.Search,
                        contentDescription = null,
                    )
                },
                singleLine = true,
            )
            if (searchQuery.isNotBlank()) {
                SettingsSearchResultsContent(
                    entries = SettingsSearchPolicy.search(SettingsIndex.entries, searchQuery),
                    highlightedId = highlightedId,
                    onOpen = { entry ->
                        callbacks.updateStore(
                            store.copy(
                            recentSettingsIds = SettingsSearchPolicy.rememberRecent(
                                recentIds = store.recentSettingsIds,
                                selectedId = entry.id,
                            ),
                            ),
                        )
                        searchQuery = ""
                        openSettingsTarget(
                            entry = entry,
                            anchorPositions = anchorPositions,
                            highlightedId = { highlightedId = it },
                            scrollState = scrollState,
                            scope = scope,
                            onNavigateRoute = callbacks.navigateRoute,
                        )
                    },
                )
                return@Column
            }
            if (searchQuery.isBlank()) {
                SettingsSearchPolicy.recent(
                    entries = SettingsIndex.entries,
                    recentIds = store.recentSettingsIds,
                ).takeIf { it.isNotEmpty() }?.let { recentEntries ->
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = stringResource(R.string.settings_recent),
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                        recentEntries.forEach { entry ->
                            SettingItem(
                                title = stringResource(entry.titleRes),
                                subtitle = stringResource(entry.group.labelRes),
                                onClick = {
                                    searchQuery = ""
                                    openSettingsTarget(
                                        entry = entry,
                                        anchorPositions = anchorPositions,
                                        highlightedId = { highlightedId = it },
                                        scrollState = scrollState,
                                        scope = scope,
                                        onNavigateRoute = callbacks.navigateRoute,
                                    )
                                },
                            )
                        }
                    }
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned {
                        anchorPositions["self_control_settings"] = it.positionInParent().y.roundToInt()
                    },
            ) {
                SettingsGeneralSection(
                    store = store,
                    callbacks = callbacks,
                    showToastSettingsDlg = showToastSettingsDlg,
                    trackServiceRunning = state.trackServiceRunning,
                    subsStatus = state.subsStatus,
                    showA11ySection = state.showA11ySection,
                    shizukuOk = state.shizukuOk,
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned {
                        anchorPositions["appearance_settings"] = it.positionInParent().y.roundToInt()
                    },
            ) {
                SettingsAppearanceSection(store = store, updateStore = callbacks.updateStore)
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned {
                        anchorPositions["about_settings"] = it.positionInParent().y.roundToInt()
                    },
            ) {
                SettingsOtherSection(
                    store = store,
                    activeLockCount = state.activeLockCount,
                    callbacks = callbacks,
                )
            }
            Spacer(modifier = Modifier.height(EmptyHeight))
        }
    }
}

@Composable
internal fun SettingsSearchResultsContent(
    entries: List<SettingsEntry>,
    highlightedId: String?,
    onOpen: (SettingsEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        entries.forEach { entry ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (highlightedId == entry.id) {
                            Modifier.background(MaterialTheme.colorScheme.primaryContainer)
                        } else {
                            Modifier
                        }
                    ),
            ) {
                SettingItem(
                    title = stringResource(entry.titleRes),
                    subtitle = stringResource(entry.group.labelRes),
                    onClick = { onOpen(entry) },
                )
            }
        }
    }
}

private fun openSettingsTarget(
    entry: SettingsEntry,
    anchorPositions: MutableMap<String, Int>,
    highlightedId: (String?) -> Unit,
    scrollState: ScrollState,
    scope: CoroutineScope,
    onNavigateRoute: (NavKey) -> Unit,
) {
    when (val target = entry.target) {
        is SettingsTarget.Route -> {
            when (target.routeKey) {
                "capability_center" -> onNavigateRoute(li.songe.gkd.sdp.ui.capability.CapabilityCenterRoute)
                "privacy_data" -> onNavigateRoute(li.songe.gkd.sdp.ui.privacy.PrivacyDataRoute)
                "advanced" -> onNavigateRoute(AdvancedPageRoute)
            }
        }
        is SettingsTarget.InPage -> {
            highlightedId(entry.id)
            anchorPositions[target.anchorId]?.let { position ->
                scope.launch { scrollState.animateScrollTo(position) }
            }
            scope.launch {
                delay(1_500)
                highlightedId(null)
            }
        }
    }
}

@Composable
private fun SettingsGeneralSection(
    store: SettingsStore,
    callbacks: SettingsCallbacks,
    showToastSettingsDlg: Boolean,
    trackServiceRunning: Boolean,
    subsStatus: String,
    showA11ySection: Boolean,
    shizukuOk: Boolean,
) {
    Text(stringResource(R.string.s_f1484fa78b), modifier = Modifier.titleItemPadding(showTop = false), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
    TextSwitch(
        title = stringResource(R.string.s_5bf7ff408f),
        subtitle = store.actionToast,
        checked = store.toastWhenClick,
        onClickLabel = stringResource(R.string.s_2313ce7d22),
        onClick = callbacks.showToastInput,
        suffixIcon = {
            PerfCustomIconButton(
                size = 32.dp,
                iconSize = 20.dp,
                onClickLabel = li.songe.gkd.sdp.app.getString(R.string.s_be1e5b4074),
                onClick = callbacks.toggleToastSettings,
                id = R.drawable.ic_page_info,
                contentDescription = li.songe.gkd.sdp.app.getString(R.string.s_aaa3b3883f),
                tint = if (showToastSettingsDlg) MaterialTheme.colorScheme.primary else LocalContentColor.current,
            )
        },
        onCheckedChange = { callbacks.updateStore(store.copy(toastWhenClick = it)) },
    )
    AnimatedVisibility(showToastSettingsDlg) {
        Column {
            TextSwitch(
                title = li.songe.gkd.sdp.app.getString(R.string.s_29ea25d303),
                subtitle = li.songe.gkd.sdp.app.getString(R.string.s_565e1ff87a),
                suffix = li.songe.gkd.sdp.app.getString(R.string.view_restrictions),
                onSuffixClick = callbacks.showViewRestrictions,
                checked = store.useSystemToast,
                onCheckedChange = { callbacks.updateStore(store.copy(useSystemToast = it)) },
            )
            TextSwitch(
                title = li.songe.gkd.sdp.app.getString(R.string.s_6a1b839874),
                subtitle = li.songe.gkd.sdp.app.getString(R.string.s_e78582fda3),
                checked = trackServiceRunning,
                onCheckedChange = callbacks.toggleTrackService,
            )
        }
    }
    TextSwitch(
        title = stringResource(R.string.s_ce7c7d71a7),
        subtitle = if (store.useCustomNotifText) stringResource(R.string.s_bedcb574bc, (store.customNotifTitle).toString(), (store.customNotifText).toString()) else subsStatus,
        checked = store.useCustomNotifText,
        onClickLabel = stringResource(R.string.s_ac04fdc8b3),
        onClick = callbacks.showNotifInput,
        onCheckedChange = { callbacks.updateStore(store.copy(useCustomNotifText = it)) },
    )
    TextSwitch(
        title = stringResource(R.string.s_8c91b02262),
        subtitle = stringResource(R.string.s_95a85cd30d),
        checked = store.excludeFromRecents,
        onCheckedChange = callbacks.toggleExcludeFromRecents,
    )
    if (showA11ySection) {
        Text(stringResource(R.string.s_04c62c8f3d), modifier = Modifier.fillMaxWidth().titleItemPadding(), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        TextSwitch(
            title = stringResource(R.string.s_86613e925d),
            subtitle = stringResource(R.string.s_2e268f4e64),
            checked = store.enableBlockA11yAppList && shizukuOk,
            onCheckedChange = callbacks.enableBlockA11y,
        )
        SettingItem(
            title = stringResource(R.string.s_8f74cd015b),
            onClickLabel = stringResource(R.string.s_7e4c9176ba),
            onClick = callbacks.navigateBlockA11y,
        )
    }
}

@Composable
private fun SettingsAppearanceSection(
    store: SettingsStore,
    updateStore: (SettingsStore) -> Unit,
) {
    Text(stringResource(R.string.s_09b58aa342), modifier = Modifier.titleItemPadding(), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
    TextMenu(
        title = stringResource(R.string.s_750642ccd6),
        option = DarkThemeOption.objects.findOption(store.enableDarkTheme),
        onOptionChange = { updateStore(store.copy(enableDarkTheme = it.value)) },
    )
    if (AndroidTarget.S) {
        TextSwitch(
            title = stringResource(R.string.s_a75357bc35),
            checked = store.enableDynamicColor,
            onCheckedChange = { updateStore(store.copy(enableDynamicColor = it)) },
        )
    }
    TextMenu(
        title = stringResource(R.string.s_c2fd3fcdd3),
        option = DisplayDensityOption.objects.findOption(store.displayDensityScale),
        onOptionChange = { updateStore(store.copy(displayDensityScale = it.value)) },
    )
    TextMenu(
        title = stringResource(R.string.s_ea10de41b6),
        option = LanguageOption.objects.findOption(store.languageTag),
        onOptionChange = { updateStore(store.copy(languageTag = it.value)) },
    )
}

@Composable
private fun SettingsOtherSection(
    store: SettingsStore,
    activeLockCount: Int,
    callbacks: SettingsCallbacks,
) {
    Text(stringResource(R.string.s_1a26edf94a), modifier = Modifier.titleItemPadding(), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
    SettingItem(
        title = stringResource(R.string.s_6337015d1f),
        subtitle = if (activeLockCount > 0) stringResource(R.string.s_ed7465c7b9, (activeLockCount).toString()) else stringResource(R.string.s_74b0d1f601),
        onClick = { callbacks.navigateRoute(FocusLockRoute) },
    )
    SettingItem(
        title = li.songe.gkd.sdp.app.getString(R.string.privacy_data_title),
        subtitle = stringResource(R.string.privacy_settings_subtitle),
        onClick = { callbacks.navigateRoute(li.songe.gkd.sdp.ui.privacy.PrivacyDataRoute) },
    )
    SettingItem(title = stringResource(R.string.s_8233960bfd), onClick = callbacks.showBackup)
    SettingItem(title = stringResource(R.string.s_bed172efc9), onClick = { callbacks.navigateRoute(AboutRoute) })
}
