package li.songe.gkd.sdp.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import li.songe.gkd.sdp.MainActivity
import li.songe.gkd.sdp.data.Snapshot
import li.songe.gkd.sdp.db.DbSet
import li.songe.gkd.sdp.permission.canWriteExternalStorage
import li.songe.gkd.sdp.permission.requiredPermission
import li.songe.gkd.sdp.ui.component.EmptyText
import li.songe.gkd.sdp.ui.component.FixedTimeText
import li.songe.gkd.sdp.ui.component.LocalNumberCharWidth
import li.songe.gkd.sdp.ui.component.PerfIcon
import li.songe.gkd.sdp.ui.component.PerfIconButton
import li.songe.gkd.sdp.ui.component.PerfTopAppBar
import li.songe.gkd.sdp.ui.component.animateListItem
import li.songe.gkd.sdp.ui.component.measureNumberTextWidth
import li.songe.gkd.sdp.ui.component.useListScrollState
import li.songe.gkd.sdp.ui.component.waitResult
import li.songe.gkd.sdp.ui.share.ListPlaceholder
import li.songe.gkd.sdp.ui.share.LocalMainViewModel
import li.songe.gkd.sdp.ui.share.noRippleClickable
import li.songe.gkd.sdp.ui.style.EmptyHeight
import li.songe.gkd.sdp.ui.style.itemHorizontalPadding
import li.songe.gkd.sdp.ui.style.itemVerticalPadding
import li.songe.gkd.sdp.ui.style.scaffoldPadding
import li.songe.gkd.sdp.util.IMPORT_SHORT_URL
import li.songe.gkd.sdp.util.ImageUtils
import li.songe.gkd.sdp.util.SnapshotExt
import li.songe.gkd.sdp.util.UriUtils
import li.songe.gkd.sdp.util.appInfoMapFlow
import li.songe.gkd.sdp.util.copyText
import li.songe.gkd.sdp.util.createGkdTempDir
import li.songe.gkd.sdp.util.launchAsFn
import li.songe.gkd.sdp.util.saveFileToDownloads
import li.songe.gkd.sdp.util.shareFile
import li.songe.gkd.sdp.util.throttle
import li.songe.gkd.sdp.util.toast
import li.songe.gkd.sdp.R

@Serializable
data object SnapshotPageRoute : NavKey

@Composable
fun SnapshotPage() {
    val context = LocalActivity.current as MainActivity
    val mainVm = LocalMainViewModel.current
    val colorScheme = MaterialTheme.colorScheme
    val vm = viewModel<SnapshotVm>()

    val firstLoading by vm.firstLoadingFlow.collectAsStateWithLifecycle()
    val snapshots by vm.snapshotsState.collectAsStateWithLifecycle()
    var selectedSnapshot by remember { mutableStateOf<Snapshot?>(null) }
    val resetKey = rememberSaveable { mutableIntStateOf(0) }
    val (scrollBehavior, listState) = useListScrollState(
        resetKey,
        snapshots.isEmpty(),
        firstLoading,
    )
    val timeTextWidth = measureNumberTextWidth(MaterialTheme.typography.bodySmall)

    Scaffold(modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection), topBar = {
        PerfTopAppBar(
            scrollBehavior = scrollBehavior,
            navigationIcon = {
                PerfIconButton(imageVector = PerfIcon.ArrowBack, onClick = {
                    mainVm.popPage()
                })
            },
            title = {
                Text(
                    text = li.songe.gkd.sdp.app.getString(R.string.s_26c9e586fc),
                    modifier = Modifier.noRippleClickable { resetKey.intValue++ },
                )
            },
            actions = {
                if (snapshots.isNotEmpty()) {
                    PerfIconButton(
                        imageVector = PerfIcon.Delete,
                        onClick = throttle(fn = vm.viewModelScope.launchAsFn(Dispatchers.IO) {
                            mainVm.dialogFlow.waitResult(
                                title = li.songe.gkd.sdp.app.getString(R.string.s_5b62e0a895),
                                text = li.songe.gkd.sdp.app.getString(R.string.s_561d9917c6),
                                error = true,
                            )
                            SnapshotExt.deleteSnapshots(snapshots)
                        })
                    )
                }
            })
    }, content = { contentPadding ->
        CompositionLocalProvider(
            LocalNumberCharWidth provides timeTextWidth
        ) {
            LazyColumn(
                modifier = Modifier.scaffoldPadding(contentPadding),
                state = listState,
            ) {
                items(snapshots, { it.id }) { snapshot ->
                    SnapshotCard(
                        modifier = Modifier.animateListItem(),
                        snapshot = snapshot,
                        onClick = {
                            selectedSnapshot = snapshot
                        }
                    )
                }
                item(ListPlaceholder.KEY, ListPlaceholder.TYPE) {
                    Spacer(modifier = Modifier.height(EmptyHeight))
                    if (snapshots.isEmpty() && !firstLoading) {
                        EmptyText(text = li.songe.gkd.sdp.app.getString(R.string.s_b246458f20))
                    }
                }
            }
        }
    })

    selectedSnapshot?.let { snapshotVal ->
        Dialog(onDismissRequest = { selectedSnapshot = null }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                val modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                Text(
                    text = li.songe.gkd.sdp.app.getString(R.string.s_f7acefd2d4), modifier = Modifier
                        .clickable(onClick = throttle(fn = vm.viewModelScope.launchAsFn {
                            selectedSnapshot = null
                            mainVm.navigatePage(
                                ImagePreviewRoute(
                                    title = appInfoMapFlow.value[snapshotVal.appId]?.name
                                        ?: snapshotVal.appId,
                                    uri = snapshotVal.screenshotFile.absolutePath,
                                )
                            )
                        }))
                        .then(modifier)
                )
                HorizontalDivider()
                Text(
                    text = li.songe.gkd.sdp.app.getString(R.string.s_ad1a01b57a),
                    modifier = Modifier
                        .clickable(onClick = throttle(fn = vm.viewModelScope.launchAsFn {
                            selectedSnapshot = null
                            val zipFile = SnapshotExt.snapshotZipFile(
                                snapshotVal.id,
                                snapshotVal.appId,
                                snapshotVal.activityId
                            )
                            context.shareFile(zipFile, "分享快照文件")
                        }))
                        .then(modifier)
                )
                HorizontalDivider()
                Text(
                    text = li.songe.gkd.sdp.app.getString(R.string.s_973f07187d),
                    modifier = Modifier
                        .clickable(onClick = throttle(fn = vm.viewModelScope.launchAsFn(Dispatchers.IO) {
                            selectedSnapshot = null
                            toast(li.songe.gkd.sdp.app.getString(R.string.s_d8d9e2143a))
                            val zipFile = SnapshotExt.snapshotZipFile(
                                snapshotVal.id,
                                snapshotVal.appId,
                                snapshotVal.activityId
                            )
                            context.saveFileToDownloads(zipFile)
                        }))
                        .then(modifier)
                )
                HorizontalDivider()
                if (snapshotVal.githubAssetId != null) {
                    Text(
                        text = li.songe.gkd.sdp.app.getString(R.string.s_abb22bd95c), modifier = Modifier
                            .clickable(onClick = throttle {
                                selectedSnapshot = null
                                copyText(IMPORT_SHORT_URL + snapshotVal.githubAssetId)
                            })
                            .then(modifier)
                    )
                } else {
                    Text(
                        text = li.songe.gkd.sdp.app.getString(R.string.s_b9304f6294), modifier = Modifier
                            .clickable(onClick = throttle {
                                selectedSnapshot = null
                                mainVm.uploadOptions.startTask(
                                    getFile = { SnapshotExt.snapshotZipFile(snapshotVal.id) },
                                    showHref = { IMPORT_SHORT_URL + it.id },
                                    onSuccessResult = {
                                        DbSet.snapshotDao.update(snapshotVal.copy(githubAssetId = it.id))
                                    }
                                )
                            })
                            .then(modifier)
                    )
                }
                HorizontalDivider()

                Text(
                    text = li.songe.gkd.sdp.app.getString(R.string.s_1f3c14ed9e),
                    modifier = Modifier
                        .clickable(onClick = throttle(fn = vm.viewModelScope.launchAsFn(Dispatchers.IO) {
                            toast(li.songe.gkd.sdp.app.getString(R.string.s_d8d9e2143a))
                            selectedSnapshot = null
                            requiredPermission(context, canWriteExternalStorage)
                            ImageUtils.save2Album(BitmapFactory.decodeFile(snapshotVal.screenshotFile.absolutePath))
                            toast(li.songe.gkd.sdp.app.getString(R.string.s_7e68eb622d))
                        }))
                        .then(modifier)
                )
                HorizontalDivider()
                Text(
                    text = li.songe.gkd.sdp.app.getString(R.string.s_93cac1f331),
                    modifier = Modifier
                        .clickable(onClick = throttle(fn = vm.viewModelScope.launchAsFn(Dispatchers.IO) {
                            val uri = context.pickContentLauncher.launchForImageResult()
                            val oldBitmap =
                                BitmapFactory.decodeFile(snapshotVal.screenshotFile.absolutePath)
                            val replacementDir = createGkdTempDir()
                            try {
                                val replacementFile = replacementDir.resolve("replacement-image")
                                UriUtils.copyUriToFile(
                                    uri = uri,
                                    target = replacementFile,
                                    maxBytes = 32L * 1024L * 1024L,
                                )
                                val newBitmap = BitmapFactory.decodeFile(replacementFile.absolutePath)
                                if (oldBitmap != null && newBitmap != null &&
                                    oldBitmap.width == newBitmap.width &&
                                    oldBitmap.height == newBitmap.height
                                ) {
                                    replacementFile.copyTo(
                                        target = snapshotVal.screenshotFile,
                                        overwrite = true,
                                    )
                                    if (snapshotVal.githubAssetId != null) {
                                        // 当本地快照变更时, 移除快照链接
                                        DbSet.snapshotDao.deleteGithubAssetId(snapshotVal.id)
                                    }
                                    toast(li.songe.gkd.sdp.app.getString(R.string.s_34e1511e3b))
                                    selectedSnapshot = null
                                } else {
                                    toast(li.songe.gkd.sdp.app.getString(R.string.s_6ca959f5b2))
                                }
                            } finally {
                                replacementDir.deleteRecursively()
                            }
                        }))
                        .then(modifier)
                )
                HorizontalDivider()
                Text(
                    text = li.songe.gkd.sdp.app.getString(R.string.s_3755f56f2f), modifier = Modifier
                        .clickable(onClick = throttle(fn = vm.viewModelScope.launchAsFn {
                            selectedSnapshot = null
                            mainVm.dialogFlow.waitResult(
                                title = li.songe.gkd.sdp.app.getString(R.string.s_5b62e0a895),
                                text = li.songe.gkd.sdp.app.getString(R.string.s_632e6dd2c1),
                                error = true,
                            )
                            SnapshotExt.deleteSnapshot(snapshotVal)
                            toast(li.songe.gkd.sdp.app.getString(R.string.s_86e8d12a79))
                        }))
                        .then(modifier), color = colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun SnapshotCard(
    modifier: Modifier = Modifier,
    snapshot: Snapshot,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .clickable(onClick = onClick)
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .padding(horizontal = itemHorizontalPadding, vertical = itemVerticalPadding / 2)
    ) {
        Spacer(
            modifier = Modifier
                .fillMaxHeight()
                .width(2.dp)
                .background(MaterialTheme.colorScheme.primaryContainer),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(
            modifier = Modifier.weight(1f),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                val appInfo = appInfoMapFlow.collectAsStateWithLifecycle().value[snapshot.appId]
                val showAppName = appInfo?.name ?: snapshot.appId
                Text(
                    text = showAppName,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                    softWrap = false,
                )
                FixedTimeText(
                    text = snapshot.date,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            val showActivityId = if (snapshot.activityId != null) {
                if (snapshot.activityId.startsWith(snapshot.appId)) {
                    snapshot.activityId.substring(snapshot.appId.length)
                } else {
                    snapshot.activityId
                }
            } else {
                null
            }
            if (showActivityId != null) {
                Text(
                    modifier = Modifier.height(MaterialTheme.typography.bodyMedium.lineHeight.value.dp),
                    text = showActivityId,
                    style = MaterialTheme.typography.bodyMedium,
                    softWrap = false,
                    maxLines = 1,
                    overflow = TextOverflow.MiddleEllipsis,
                )
            } else {
                Text(
                    text = li.songe.gkd.sdp.app.getString(R.string.s_2be88ca424),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.typography.bodyMedium.color.copy(alpha = 0.5f)
                )
            }
        }
    }
}
