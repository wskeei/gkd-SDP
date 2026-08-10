@file:JvmName("UsageGuardSections")

package li.songe.gkd.sdp.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import li.songe.gkd.sdp.data.AppInfo
import li.songe.gkd.sdp.data.UsageGuardRecord
import li.songe.gkd.sdp.store.storeFlow
import li.songe.gkd.sdp.ui.component.AppIcon
import li.songe.gkd.sdp.ui.component.AppPickerDialog
import li.songe.gkd.sdp.ui.component.PerfIcon
import li.songe.gkd.sdp.ui.component.PerfIconButton
import li.songe.gkd.sdp.ui.component.PerfTopAppBar
import li.songe.gkd.sdp.ui.share.LocalMainViewModel
import li.songe.gkd.sdp.ui.style.itemPadding
import li.songe.gkd.sdp.ui.style.scaffoldPadding
import li.songe.gkd.sdp.ui.style.surfaceCardColors
import li.songe.gkd.sdp.util.UsageGuardPolicy
import li.songe.gkd.sdp.util.UsageGuardReviewPolicy
import li.songe.gkd.sdp.util.UsageGuardUiStatePolicy
import li.songe.gkd.sdp.util.appInfoMapFlow
import androidx.compose.ui.res.stringResource
import li.songe.gkd.sdp.R

@Composable
fun UsageGuardPageSections() {
    val mainVm = LocalMainViewModel.current
    val vm = viewModel<UsageGuardVm>()
    val settings by storeFlow.collectAsStateWithLifecycle()
    val appProfiles by vm.appProfilesFlow.collectAsStateWithLifecycle()
    val tags by vm.tagsFlow.collectAsStateWithLifecycle()
    val history by vm.historyFlow.collectAsStateWithLifecycle()
    val groupedApps by vm.selectedAppSectionsFlow.collectAsStateWithLifecycle()
    val durationOptions by vm.durationOptionsFlow.collectAsStateWithLifecycle()
    val appInfoMap by appInfoMapFlow.collectAsStateWithLifecycle()
    val context = LocalContext.current

    UsageGuardSettingsList(
        mainVm = mainVm,
        vm = vm,
        settings = settings,
        appProfiles = appProfiles,
        tags = tags,
        history = history,
        groupedApps = groupedApps,
        durationOptions = durationOptions,
        appInfoMap = appInfoMap,
        context = context,
    )
}

@Composable
internal fun SectionCard(
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    ElevatedCard(
        colors = surfaceCardColors,
        modifier = Modifier.fillMaxWidth().itemPadding(),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}

@Composable
internal fun SettingRow(
    title: String,
    subtitle: String,
    trailing: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(modifier = Modifier.width(12.dp))
        trailing()
    }
}

@Composable
internal fun CompactInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.width(12.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
internal fun PreferenceBlock(
    title: String,
    supporting: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text(supporting, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        content()
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
internal fun SelectedAppModeBoard(
    title: String,
    subtitle: String,
    appIds: List<String>,
    appInfoMap: Map<String, AppInfo>,
    onBoardBoundsChanged: (Rect) -> Unit,
    onAppClick: (String) -> Unit,
    onDragStart: (String) -> Unit,
    onDragEnd: (String, Offset) -> Unit,
) {
    Column(
        modifier = Modifier.onGloballyPositioned { coordinates ->
            onBoardBoundsChanged(coordinates.boundsInWindow())
        },
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (appIds.isEmpty()) {
            Text(stringResource(R.string.s_1fa7fa3095), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                appIds.forEach { appId ->
                    DraggableAppIcon(
                        appId = appId,
                        appName = appInfoMap[appId]?.name ?: appId,
                        onClick = { onAppClick(appId) },
                        onDragStart = { onDragStart(appId) },
                        onDrop = { onDragEnd(appId, it) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun IconAppFlow(
    appIds: List<String>,
    appInfoMap: Map<String, AppInfo>,
    emptyText: String,
    onAppClick: (String) -> Unit,
) {
    if (appIds.isEmpty()) {
        Text(emptyText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            appIds.forEach { appId ->
                Column(
                    modifier = Modifier.width(72.dp).clickable { onAppClick(appId) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(modifier = Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                        AppIcon(appId = appId, modifier = Modifier.size(48.dp))
                    }
                    Text(
                        text = appInfoMap[appId]?.name ?: appId,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun DraggableAppIcon(
    appId: String,
    appName: String,
    onClick: () -> Unit,
    onDragStart: () -> Unit,
    onDrop: (Offset) -> Unit,
) {
    var dragOffset by remember(appId) { mutableStateOf(Offset.Zero) }
    var originInWindow by remember(appId) { mutableStateOf(Offset.Zero) }
    var tileSize by remember(appId) { mutableStateOf(IntSize.Zero) }
    Column(
        modifier = Modifier
            .width(72.dp)
            .pointerInput(appId) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { onDragStart() },
                    onDragEnd = {
                        val center = Offset(tileSize.width / 2f, tileSize.height / 2f)
                        onDrop(originInWindow + dragOffset + center)
                        dragOffset = Offset.Zero
                    },
                    onDragCancel = { dragOffset = Offset.Zero },
                ) { _, dragAmount ->
                    dragOffset += dragAmount
                }
            }
            .clickable(onClick = onClick)
            .onGloballyPositioned { coordinates ->
                originInWindow = coordinates.positionInWindow()
                tileSize = coordinates.size
            }
            .graphicsLayer {
                translationX = dragOffset.x
                translationY = dragOffset.y
            }
            .zIndex(if (dragOffset != Offset.Zero) 1f else 0f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(modifier = Modifier.size(52.dp), contentAlignment = Alignment.Center) {
            AppIcon(appId = appId, modifier = Modifier.size(48.dp))
        }
        Text(appName, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
internal fun HistoryRow(record: UsageGuardRecord, appName: String) {
    val requestedAt = remember(record.requestedAt) {
        Instant.ofEpochMilli(record.requestedAt).atZone(ZoneId.systemDefault()).toLocalDateTime()
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(appName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                requestedAt.format(usageGuardTimeFormatter),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(record.tagNames.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        Text(record.reasonText, style = MaterialTheme.typography.bodyMedium)
        Text(
            stringResource(R.string.s_d159fb78f0, (record.requestedDurationMinutes).toString(), (UsageGuardReviewPolicy.formatUsedDuration(UsageGuardReviewPolicy.effectiveUsedSeconds(record))).toString()),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(record.endStateText(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
