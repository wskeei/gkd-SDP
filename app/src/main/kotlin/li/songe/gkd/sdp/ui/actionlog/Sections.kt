@file:JvmName("ActionLogSections0")

package li.songe.gkd.sdp.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottomAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStartAxis
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.columnSeries
import com.patrykandpatrick.vico.core.cartesian.layer.ColumnCartesianLayer
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import li.songe.gkd.sdp.data.ActionLog
import li.songe.gkd.sdp.db.DbSet
import li.songe.gkd.sdp.ui.component.EmptyText
import li.songe.gkd.sdp.ui.component.LocalNumberCharWidth
import li.songe.gkd.sdp.ui.component.PerfIcon
import li.songe.gkd.sdp.ui.component.PerfIconButton
import li.songe.gkd.sdp.ui.component.PerfTopAppBar
import li.songe.gkd.sdp.ui.component.TowLineText
import li.songe.gkd.sdp.ui.component.animateListItem
import li.songe.gkd.sdp.ui.component.measureNumberTextWidth
import li.songe.gkd.sdp.ui.component.useListScrollState
import li.songe.gkd.sdp.ui.component.useSubs
import li.songe.gkd.sdp.ui.component.waitResult
import li.songe.gkd.sdp.ui.share.ListPlaceholder
import li.songe.gkd.sdp.ui.share.LocalMainViewModel
import li.songe.gkd.sdp.ui.share.noRippleClickable
import li.songe.gkd.sdp.ui.style.EmptyHeight
import li.songe.gkd.sdp.ui.style.scaffoldPadding
import li.songe.gkd.sdp.util.launchAsFn
import li.songe.gkd.sdp.util.throttle
import li.songe.gkd.sdp.util.toast
import androidx.compose.ui.res.stringResource
import li.songe.gkd.sdp.R
import li.songe.gkd.sdp.app

@Composable
fun ActionLogPageSections(route: ActionLogRoute) {
    val subsId = route.subsId
    val appId = route.appId
    val mainVm = LocalMainViewModel.current
    val vm = viewModel { ActionLogVm(route) }


    val resetKey = rememberSaveable { mutableIntStateOf(0) }
    val list = vm.pagingDataFlow.collectAsLazyPagingItems()
    val (scrollBehavior, listState) = useListScrollState(resetKey, list.itemCount > 0)
    val timeTextWidth = measureNumberTextWidth(MaterialTheme.typography.bodySmall)

    Scaffold(modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection), topBar = {
        PerfTopAppBar(
            scrollBehavior = scrollBehavior,
            navigationIcon = {
                PerfIconButton(
                    imageVector = PerfIcon.ArrowBack,
                    onClick = {
                        mainVm.popPage()
                    },
                )
            },
            title = {
                val title = app.getString(R.string.s_50532745b5)
                val titleModifier = Modifier.noRippleClickable {
                    resetKey.intValue++
                }
                if (subsId != null) {
                    TowLineText(
                        title = title,
                        subtitle = useSubs(subsId)?.name ?: subsId.toString(),
                        modifier = titleModifier,
                    )
                } else if (appId != null) {
                    TowLineText(
                        title = title,
                        subtitle = appId,
                        showApp = true,
                        modifier = titleModifier,
                    )
                } else {
                    Text(
                        text = title,
                        modifier = titleModifier,
                    )
                }
            },
            actions = {
                if (list.itemCount > 0) {
                    PerfIconButton(
                        imageVector = PerfIcon.Delete,
                        onClick = throttle(fn = mainVm.viewModelScope.launchAsFn {
                            val text = if (subsId != null) {
                                app.getString(R.string.s_1e540d190c)
                            } else if (appId != null) {
                                app.getString(R.string.s_c3a7a48256)
                            } else {
                                app.getString(R.string.s_cffd230efd)
                            }
                            mainVm.dialogFlow.waitResult(
                                title = app.getString(R.string.s_8f22c9908e),
                                text = text,
                                error = true,
                            )
                            if (subsId != null) {
                                DbSet.actionLogDao.deleteSubsAll(subsId)
                            } else if (appId != null) {
                                DbSet.actionLogDao.deleteAppAll(appId)
                            } else {
                                DbSet.actionLogDao.deleteAll()
                            }
                            toast(app.getString(R.string.s_86e8d12a79))
                        })
                    )
                }
            })
    }, content = { contentPadding ->
        Column(modifier = Modifier.scaffoldPadding(contentPadding)) {
            val selectedTab by vm.selectedTabIndex.collectAsStateWithLifecycle()
            PrimaryTabRow(
                selectedTabIndex = selectedTab,
                modifier = Modifier.fillMaxWidth(),
                containerColor = MaterialTheme.colorScheme.surface,
                divider = {}
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { vm.selectedTabIndex.value = 0 },
                    text = { Text(stringResource(R.string.s_8731a78cdd)) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { vm.selectedTabIndex.value = 1 },
                    text = { Text(stringResource(R.string.s_ad5386cf16)) }
                )
            }

            if (selectedTab == 0) {
                CompositionLocalProvider(
                    LocalNumberCharWidth provides timeTextWidth
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        state = listState,
                    ) {
                        items(
                            count = list.itemCount,
                            key = list.itemKey { c -> c.first.id }
                        ) { i ->
                            val item = list[i] ?: return@items
                            val lastItem = if (i > 0) list[i - 1] else null
                            ActionLogCard(
                                modifier = Modifier.animateListItem(),
                                i = i,
                                item = item,
                                lastItem = lastItem,
                                onClick = {
                                    vm.showActionLogFlow.value = item.first
                                },
                                subsId = subsId,
                                appId = appId,
                            )
                        }
                        item(ListPlaceholder.KEY, ListPlaceholder.TYPE) {
                            Spacer(modifier = Modifier.height(EmptyHeight))
                            if (list.itemCount == 0 && list.loadState.refresh !is LoadState.Loading) {
                                EmptyText(text = stringResource(R.string.s_b246458f20))
                            }
                        }
                    }
                }
            } else {
                ActionLogStatsView(vm)
            }
        }
    })

    vm.showActionLogFlow.collectAsStateWithLifecycle().value?.let {
        ActionLogDialog(
            vm = vm,
            actionLog = it,
            onDismissRequest = {
                vm.showActionLogFlow.value = null
            }
        )
    }
}


@Composable
internal fun ActionLogStatsView(vm: ActionLogVm) {
    val statsUiState by vm.statsUiStateFlow.collectAsStateWithLifecycle()
    if (!statsUiState.hasAnyStats) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyText(text = stringResource(R.string.s_9cf5deae81))
        }
        return
    }
    val stats = statsUiState.stats

    val modelProducer = remember { CartesianChartModelProducer() }
    LaunchedEffect(stats) {
        modelProducer.runTransaction {
            columnSeries {
                series(stats.map { it.count })
            }
        }
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = app.getString(R.string.s_d7e65414e9),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    CartesianChartHost(
                        chart = rememberCartesianChart(
                            rememberColumnCartesianLayer(
                                columnProvider = ColumnCartesianLayer.ColumnProvider.series(
                                    rememberLineComponent(
                                        color = MaterialTheme.colorScheme.primary,
                                        thickness = 12.dp,
                                    )
                                )
                            ),
                            startAxis = rememberStartAxis(),
                            bottomAxis = rememberBottomAxis(
                                valueFormatter = { x, _, _ ->
                                    stats.getOrNull(x.toInt())?.date?.substring(5) ?: ""
                                }
                            ),
                        ),
                        modelProducer = modelProducer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(250.dp)
                    )
                }
            }
        }

        item {
            Text(
                text = app.getString(R.string.s_c62e341470),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.titleSmall
            )
        }

        items(stats.reversed()) { stat ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = stat.date, style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = stringResource(R.string.s_f768d9701d, stat.count),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp)
        }

        item {
            Spacer(modifier = Modifier.height(EmptyHeight))
        }
    }
}
