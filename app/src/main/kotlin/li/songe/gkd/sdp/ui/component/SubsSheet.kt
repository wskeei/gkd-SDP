package li.songe.gkd.sdp.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import li.songe.gkd.sdp.META
import li.songe.gkd.sdp.ui.ActionLogRoute
import li.songe.gkd.sdp.ui.SubsAppListRoute
import li.songe.gkd.sdp.ui.SubsCategoryRoute
import li.songe.gkd.sdp.ui.SubsGlobalGroupListRoute
import li.songe.gkd.sdp.ui.share.LocalMainViewModel
import li.songe.gkd.sdp.ui.style.EmptyHeight
import li.songe.gkd.sdp.ui.style.itemHorizontalPadding
import li.songe.gkd.sdp.util.LOCAL_SUBS_ID
import li.songe.gkd.sdp.util.checkSubsUpdate
import li.songe.gkd.sdp.util.deleteSubscription
import li.songe.gkd.sdp.util.launchAsFn
import li.songe.gkd.sdp.util.launchTry
import li.songe.gkd.sdp.util.subsItemsFlow
import li.songe.gkd.sdp.util.subsMapFlow
import li.songe.gkd.sdp.util.throttle
import li.songe.gkd.sdp.util.toast
import li.songe.gkd.sdp.util.updateSubsMutex
import androidx.compose.ui.res.stringResource
import li.songe.gkd.sdp.R

@Composable
fun SubsSheet(
    vm: ViewModel,
    sheetSubsIdFlow: MutableStateFlow<Long?>
) {
    val subsItems by subsItemsFlow.collectAsStateWithLifecycle()
    val (subsId, setSubsId) = remember { mutableStateOf(sheetSubsIdFlow.value) }
    val subsItem = subsItems.find { it.id == subsId }
    if (subsItem == null) {
        LaunchedEffect(null) {
            sheetSubsIdFlow.collect {
                setSubsId(it)
            }
        }
    } else {
        val mainVm = LocalMainViewModel.current
        val subsIdToRaw by subsMapFlow.collectAsStateWithLifecycle()
        var swipeEnabled by remember { mutableStateOf(false) }
        val sheetState = rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
            confirmValueChange = { swipeEnabled }
        )
        LaunchedEffect(null) {
            sheetSubsIdFlow.collect {
                if (it == null && sheetState.isVisible) {
                    launch {
                        sheetState.hide()
                    }.invokeOnCompletion {
                        if (!sheetState.isVisible) {
                            setSubsId(null)
                        }
                    }
                } else {
                    setSubsId(it)
                }
            }
        }
        val scrollState = rememberScrollState()
        remember {
            derivedStateOf {
                scrollState.value == 0
            }
        }.let { a ->
            LaunchedEffect(a.value) {
                swipeEnabled = a.value
            }
        }
        ModalBottomSheet(
            onDismissRequest = {
                sheetSubsIdFlow.value = null
            },
            sheetState = sheetState
        ) {
            val subscription = subsIdToRaw[subsItem.id]
            val showName = subscription?.name ?: "id=${subsItem.id}"
            val childModifier = remember {
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = itemHorizontalPadding, vertical = 8.dp)
            }
            Column(
                modifier = Modifier
                    .verticalScroll(
                        state = scrollState,
                        enabled = sheetState.currentValue == SheetValue.Expanded
                    )
                    .fillMaxWidth(),
            ) {
                Text(
                    text = showName,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = childModifier
                )
                if (subscription != null) {
                    Column(
                        modifier = childModifier.clearAndSetSemantics {
                            contentDescription =
                                "作者：${subscription.author ?: "未知"}, 版本号：v${subscription.version}, 更新时间：${subsItem.mtimeStr}"
                        }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = stringResource(R.string.s_698bea5124),
                                style = MaterialTheme.typography.labelLarge,
                            )
                            Text(
                                text = stringResource(R.string.s_82b86f78a2, subscription.version),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier
                                    .clip(MaterialTheme.shapes.extraSmall)
                                    .background(MaterialTheme.colorScheme.tertiaryContainer)
                                    .padding(horizontal = 2.dp),
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            if (!subsItem.isLocal) {
                                Text(
                                    text = subscription.author ?: stringResource(R.string.s_d9c32a4c3d),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.let {
                                        if (subscription.author == null) {
                                            it.copy(alpha = 0.5f)
                                        } else {
                                            it
                                        }
                                    },
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            } else {
                                Text(
                                    text = META.appName,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.secondary,
                                )
                            }
                            Text(
                                text = subsItem.mtimeStr,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (subscription.globalGroups.isNotEmpty() || subsItem.isLocal) {
                        Row(
                            modifier = Modifier
                                .clickable(onClickLabel = "查看全局规则列表", onClick = throttle {
                                    setSubsId(null)
                                    sheetSubsIdFlow.value = null
                                    mainVm.navigatePage(SubsGlobalGroupListRoute(subsItem.id))
                                })
                                .then(childModifier),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = stringResource(R.string.s_9effd4ccc9),
                                    style = MaterialTheme.typography.labelLarge,
                                )
                                Text(
                                    text = if (subscription.globalGroups.isNotEmpty()) stringResource(R.string.s_966f4322a8, subscription.globalGroups.size) else stringResource(R.string.s_5dbd015496),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.let {
                                        if (subscription.globalGroups.isEmpty()) {
                                            it.copy(alpha = 0.5f)
                                        } else {
                                            it
                                        }
                                    },
                                )
                            }
                            PerfIcon(
                                imageVector = PerfIcon.KeyboardArrowRight,
                            )
                        }
                    }
                    if (subscription.appGroups.isNotEmpty() || subsItem.isLocal) {
                        Row(
                            modifier = Modifier
                                .clickable(onClickLabel = "查看应用规则列表", onClick = throttle {
                                    setSubsId(null)
                                    sheetSubsIdFlow.value = null
                                    mainVm.navigatePage(SubsAppListRoute(subsItem.id))
                                })
                                .then(childModifier),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = stringResource(R.string.s_da6a6dc1af),
                                    style = MaterialTheme.typography.labelLarge,
                                )
                                Text(
                                    text = if (subscription.appGroups.isNotEmpty()) stringResource(R.string.s_eac59394f9, subscription.apps.size, subscription.appGroups.size) else stringResource(R.string.s_5dbd015496),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.let {
                                        if (subscription.appGroups.isEmpty()) {
                                            it.copy(alpha = 0.5f)
                                        } else {
                                            it
                                        }
                                    },
                                )
                            }
                            PerfIcon(
                                imageVector = PerfIcon.KeyboardArrowRight,
                            )
                        }

                    }
                    if (subscription.categories.isNotEmpty() || subsItem.isLocal) {
                        Row(
                            modifier = Modifier
                                .clickable(onClickLabel = "查看规则类别列表", onClick = throttle {
                                    setSubsId(null)
                                    sheetSubsIdFlow.value = null
                                    mainVm.navigatePage(SubsCategoryRoute(subsItem.id))
                                })
                                .then(childModifier),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = stringResource(R.string.s_53c76c1349),
                                    style = MaterialTheme.typography.labelLarge,
                                )
                                Text(
                                    text = if (subscription.categories.isNotEmpty()) stringResource(R.string.s_f6140ad79e, subscription.categories.size) else stringResource(R.string.s_5dbd015496),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.let {
                                        if (subscription.categories.isEmpty()) {
                                            it.copy(alpha = 0.5f)
                                        } else {
                                            it
                                        }
                                    },
                                )
                            }
                            PerfIcon(
                                imageVector = PerfIcon.KeyboardArrowRight,
                            )
                        }
                    }
                    if (!subsItem.isLocal && subsItem.updateUrl != null) {
                        Row(
                            modifier = Modifier
                                .clickable(onClickLabel = "编辑订阅链接", onClick = throttle {
                                    if (updateSubsMutex.mutex.isLocked) {
                                        toast(li.songe.gkd.sdp.app.getString(R.string.s_2c20f3fd5e))
                                        return@throttle
                                    }
                                    mainVm.viewModelScope.launchTry {
                                        val url =
                                            mainVm.inputSubsLinkOption.getResult(initValue = subsItem.updateUrl)
                                                ?: return@launchTry
                                        mainVm.addOrModifySubs(url, subsItem)
                                    }
                                })
                                .then(childModifier),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(
                                    text = stringResource(R.string.s_b1a934b247),
                                    style = MaterialTheme.typography.labelLarge,
                                )
                                Text(
                                    text = subsItem.updateUrl,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.secondary,
                                    softWrap = false,
                                    overflow = TextOverflow.MiddleEllipsis,
                                    modifier = Modifier
                                        .clearAndSetSemantics {}
                                        .clickable(onClickLabel = "查看订阅链接", onClick = {
                                            mainVm.textFlow.value = subsItem.updateUrl
                                        })
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            PerfIcon(
                                imageVector = PerfIcon.Edit,
                            )
                        }
                    }
                } else {
                    val loading by updateSubsMutex.state.collectAsStateWithLifecycle()
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Spacer(modifier = Modifier.height(EmptyHeight))
                        if (loading) {
                            CircularProgressIndicator()
                        } else {
                            Text(
                                text = li.songe.gkd.sdp.app.getString(R.string.s_c3159c4450),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.error,
                            )
                            TextButton(onClick = throttle { checkSubsUpdate(showToast = true) }) {
                                Text(text = li.songe.gkd.sdp.app.getString(R.string.s_5982c44c18))
                            }
                        }
                    }
                }

                Row(
                    modifier = childModifier,
                    horizontalArrangement = Arrangement.End
                ) {
                    if (!subsItem.isLocal && subscription?.supportUri != null) {
                        PerfIconButton(
                            imageVector = PerfIcon.HelpOutline,
                            onClick = throttle {
                                mainVm.textFlow.value = subscription.supportUri
                            },
                        )
                    }
                    PerfIconButton(imageVector = PerfIcon.History, onClick = throttle {
                        setSubsId(null)
                        sheetSubsIdFlow.value = null
                        mainVm.navigatePage(ActionLogRoute(subsId = subsItem.id))
                    })
                    if (subsItem.id != LOCAL_SUBS_ID) {
                        PerfIconButton(
                            imageVector = PerfIcon.Delete,
                            onClick = throttle(
                                vm.viewModelScope.launchAsFn {
                                    mainVm.dialogFlow.waitResult(
                                        title = li.songe.gkd.sdp.app.getString(R.string.s_fe7b16b5c0),
                                        text = li.songe.gkd.sdp.app.getString(R.string.s_59fbf95a82, subscription?.name ?: subsItem.id),
                                        error = true,
                                    )
                                    sheetSubsIdFlow.value = null
                                    setSubsId(null)
                                    deleteSubscription(subsItem.id)
                                }
                            ),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(EmptyHeight / 2))
            }
        }
    }
}
