package li.songe.gkd.sdp.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import li.songe.gkd.sdp.data.InterceptConfig
import li.songe.gkd.sdp.data.RawSubscription
import li.songe.gkd.sdp.db.DbSet
import li.songe.gkd.sdp.ui.ImagePreviewRoute
import li.songe.gkd.sdp.ui.SubsAppGroupListRoute
import li.songe.gkd.sdp.ui.SubsGlobalGroupListRoute
import li.songe.gkd.sdp.ui.icon.ResetSettings
import li.songe.gkd.sdp.ui.share.LocalDarkTheme
import li.songe.gkd.sdp.ui.share.LocalMainViewModel
import li.songe.gkd.sdp.ui.style.getJson5AnnotatedString
import li.songe.gkd.sdp.util.copyText
import li.songe.gkd.sdp.util.throttle
import li.songe.gkd.sdp.util.toast
import androidx.compose.ui.res.stringResource
import li.songe.gkd.sdp.R

@Composable
fun RuleGroupDialog(
    subs: RawSubscription,
    group: RawSubscription.RawGroupProps,
    appId: String?,
    onDismissRequest: () -> Unit,
    onClickEdit: (() -> Unit) = {},
    onClickEditExclude: () -> Unit,
    onClickResetSwitch: (() -> Unit)?,
    onClickDelete: () -> Unit = {}
) {
    val mainVm = LocalMainViewModel.current
    val interceptConfig by remember(subs.id, appId, group.key) {
        DbSet.interceptConfigDao.getFlow(subs.id, appId ?: "", group.key)
    }.collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(text = stringResource(R.string.s_2b5f43357d)) },
        text = {
            Box(
                modifier = Modifier.fillMaxWidth()
            ) {
                val maxHeight = 300.dp
                Column(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .heightIn(min = 100.dp, max = maxHeight)
                        .clip(MaterialTheme.shapes.extraSmall)
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .verticalScroll(rememberScrollState())
                        .clearAndSetSemantics {
                            contentDescription = li.songe.gkd.sdp.app.getString(R.string.rule_group_content)
                        }
                ) {
                    SelectionContainer {
                        val textState = remember {
                            mutableStateOf(
                                group.cacheStr.run {
                                    // 优化: 大字符串第一次显示卡顿
                                    if (length > 1000) substring(0, 1000) else this
                                }
                            )
                        }
                        LaunchedEffect(group.cacheStr) {
                            delay(50)
                            if (group.cacheStr.length != textState.value.length) {
                                textState.value = group.cacheStr
                            }
                        }
                        val darkTheme = LocalDarkTheme.current
                        Text(
                            text = remember(textState.value, darkTheme) {
                                getJson5AnnotatedString(
                                    textState.value,
                                    darkTheme
                                )
                            },
                            modifier = Modifier.padding(4.dp),
                            color = MaterialTheme.colorScheme.secondary,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                PerfIcon(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .clickable(onClick = throttle {
                            copyText(group.cacheStr)
                        })
                        .padding(4.dp)
                        .size(24.dp),
                    imageVector = PerfIcon.ContentCopy,
                    tint = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.75f),
                )
                Text(
                    text = group.cacheStr.length.toString(),
                    modifier = Modifier
                        .padding(end = 4.dp, bottom = 4.dp)
                        .align(Alignment.BottomEnd)
                        .clip(MaterialTheme.shapes.extraSmall)
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .padding(horizontal = 2.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        },
        confirmButton = {
            Row {
                val direction = remember(subs.id, appId, group.key) {
                    if (group is RawSubscription.RawGlobalGroup) {
                        SubsGlobalGroupListRoute(
                            subsItemId = subs.id,
                            focusGroupKey = group.key
                        )
                    } else {
                        SubsAppGroupListRoute(
                            subsItemId = subs.id,
                            appId = appId.toString(),
                            focusGroupKey = group.key
                        )
                    }
                }
                PerfIconButton(imageVector = PerfIcon.ArrowForward, onClick = throttle {
                    onDismissRequest()
                    mainVm.navigatePage(direction)
                })
                if (group.allExampleUrls.isNotEmpty()) {
                    PerfIconButton(imageVector = PerfIcon.Image, onClick = throttle {
                        onDismissRequest()
                        mainVm.navigatePage(
                            ImagePreviewRoute(
                                title = group.name,
                                uris = group.allExampleUrls,
                            )
                        )
                    })
                }
                if (subs.isLocal) {
                    PerfIconButton(imageVector = PerfIcon.Edit, onClick = throttle(onClickEdit))
                }
                PerfIconButton(
                    imageVector = PerfIcon.Block,
                    onClickLabel = stringResource(R.string.rule_edit_exclusion),
                    onClick = throttle(onClickEditExclude),
                )
                AnimatedVisibility(
                    visible = onClickResetSwitch != null,
                ) {
                    PerfIconButton(
                        imageVector = ResetSettings,
                        onClickLabel = stringResource(R.string.rule_reset_switch_state),
                        onClick = throttle(onClickResetSwitch ?: {}),
                    )
                }
                if (subs.isLocal) {
                    PerfIconButton(
                        imageVector = PerfIcon.Delete,
                        onClick = throttle(onClickDelete),
                    )
                }
            }
        },
    )
}
