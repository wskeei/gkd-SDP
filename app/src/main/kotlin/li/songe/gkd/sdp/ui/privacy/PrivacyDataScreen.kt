package li.songe.gkd.sdp.ui.privacy

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import li.songe.gkd.sdp.R
import li.songe.gkd.sdp.MainActivity
import li.songe.gkd.sdp.privacy.DataCategory
import li.songe.gkd.sdp.privacy.DataDeletionCoordinator
import li.songe.gkd.sdp.privacy.DataInventoryRepository
import li.songe.gkd.sdp.remote.CleartextOriginAuthorizations
import li.songe.gkd.sdp.ui.component.AppConfirmationDialog
import li.songe.gkd.sdp.ui.component.ContentState
import li.songe.gkd.sdp.ui.component.ContentStateBox
import li.songe.gkd.sdp.ui.component.PerfIcon
import li.songe.gkd.sdp.ui.component.PerfIconButton
import li.songe.gkd.sdp.ui.component.PerfTopAppBar
import li.songe.gkd.sdp.ui.home.BackupWorkflowState
import li.songe.gkd.sdp.ui.home.HomeVm
import li.songe.gkd.sdp.ui.home.SettingsBackupDialogs
import li.songe.gkd.sdp.ui.share.LocalMainViewModel
import li.songe.gkd.sdp.ui.style.DimensionTokens
import li.songe.gkd.sdp.ui.style.surfaceCardColors

@Composable
fun PrivacyDataScreen() {
    val mainVm = LocalMainViewModel.current
    val context = LocalActivity.current as? MainActivity
    val homeVm = viewModel<HomeVm>()
    val scope = rememberCoroutineScope()
    val backupScope = rememberCoroutineScope()
    val backupWorkflow = remember { mutableStateOf<BackupWorkflowState?>(null) }
    var inventory by remember {
        mutableStateOf<Map<DataCategory, DataDeletionCoordinator.CategoryStatus>?>(null)
    }
    var deleting by remember { mutableStateOf<DataCategory?>(null) }
    var deleteError by remember { mutableStateOf<String?>(null) }
    val repository = remember { DataInventoryRepository() }
    val cleartextOrigins by CleartextOriginAuthorizations.originsFlow.collectAsStateWithLifecycle()
    val deleteFailedText = stringResource(R.string.privacy_delete_failed)

    suspend fun refresh() {
        inventory = withContext(Dispatchers.IO) { repository.inventory() }
    }

    LaunchedEffect(Unit) { refresh() }

    Scaffold(
        topBar = {
            PerfTopAppBar(
                navigationIcon = {
                    PerfIconButton(
                        imageVector = PerfIcon.ArrowBack,
                        onClick = { mainVm.popPage() },
                    )
                },
                title = {
                    Text(
                        text = stringResource(R.string.privacy_data_title),
                        modifier = Modifier.testTag("privacy_data_title"),
                    )
                },
            )
        },
    ) { padding ->
        val state = when (val data = inventory) {
            null -> ContentState.Loading
            else -> ContentState.Content
        }
        ContentStateBox(state = state, modifier = Modifier.padding(padding)) {
            val data = requireNotNull(inventory)
            PrivacyDataContent(
                inventory = data,
                onDelete = { deleting = it },
                cleartextOrigins = cleartextOrigins,
                onRevokeOrigin = CleartextOriginAuthorizations::revoke,
                onBackupClick = { homeVm.showBackupDlgFlow.value = true },
            )
        }
    }

    SettingsBackupDialogs(
        context = context,
        vm = homeVm,
        backupScope = backupScope,
        backupWorkflow = backupWorkflow,
    )

    deleting?.let { category ->
        val status = inventory?.get(category)
        val ui = PrivacyDataPresenter.present(inventory.orEmpty())
            .firstOrNull { it.category == category }
        AppConfirmationDialog(
            title = stringResource(R.string.privacy_delete_title),
            objectName = ui?.let { stringResource(it.titleRes) }.orEmpty(),
            description = ui?.let { stringResource(it.descriptionRes) }.orEmpty() +
                (status?.let {
                    val summary = DataDeletionCoordinator.summaryTextRes(it)
                    "\n\n" + stringResource(summary.resId, *summary.args.toTypedArray())
                }.orEmpty()),
            confirmText = stringResource(R.string.privacy_delete),
            destructive = true,
            requiresPhrase = category == DataCategory.ALL_APP_DATA,
            onConfirm = {
                scope.launch {
                    val result = repository.delete(category)
                    result.onSuccess {
                        deleting = null
                        deleteError = null
                        refresh()
                    }.onFailure {
                        deleteError = deleteFailedText
                    }
                }
            },
            onDismiss = { deleting = null },
        )
    }

    deleteError?.let { error ->
        Text(
            text = error,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(DimensionTokens.SpacingBase),
        )
    }
}

@Composable
internal fun PrivacyDataContent(
    inventory: Map<DataCategory, DataDeletionCoordinator.CategoryStatus>,
    onDelete: (DataCategory) -> Unit,
    cleartextOrigins: Set<String> = emptySet(),
    onRevokeOrigin: (String) -> Unit = {},
    onBackupClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val categories = PrivacyDataPresenter.present(inventory)
    val normalCategories = categories.filterNot { it.category == DataCategory.ALL_APP_DATA }
    val dangerCategories = categories.filter { it.category == DataCategory.ALL_APP_DATA }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(DimensionTokens.SpacingBase),
        verticalArrangement = Arrangement.spacedBy(DimensionTokens.SpacingSm),
    ) {
        item(key = "privacy_intro") {
            Text(
                text = stringResource(R.string.privacy_data_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item(key = "privacy_backup_title") {
            Text(
                text = stringResource(R.string.privacy_backup_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = DimensionTokens.SpacingBase),
            )
        }
        item(key = "privacy_backup_action") {
            TextButton(
                onClick = onBackupClick,
                modifier = Modifier.testTag("privacy_backup_action"),
            ) {
                Text(stringResource(R.string.privacy_backup_action))
            }
        }
        item(key = "privacy_inventory_title") {
            Text(
                text = stringResource(R.string.privacy_inventory_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = DimensionTokens.SpacingSm),
            )
        }
        item(key = "privacy_cleartext_title") {
            Text(
                text = stringResource(R.string.privacy_cleartext_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = DimensionTokens.SpacingBase),
            )
        }
        if (cleartextOrigins.isEmpty()) {
            item(key = "privacy_cleartext_empty") {
                Text(
                    text = stringResource(R.string.privacy_cleartext_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(
                items = cleartextOrigins.sorted(),
                key = { "origin_$it" },
            ) { origin ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = origin,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    TextButton(onClick = { onRevokeOrigin(origin) }) {
                        Text(stringResource(R.string.privacy_revoke_origin))
                    }
                }
            }
        }
        item(key = "privacy_support_title") {
            Text(
                text = stringResource(R.string.privacy_support_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = DimensionTokens.SpacingBase),
            )
        }
        item(key = "privacy_support_summary") {
            Text(
                text = stringResource(R.string.privacy_support_summary),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(
            items = normalCategories,
            key = { it.category.name },
        ) { item ->
            PrivacyCategoryCard(
                item = item,
                onDelete = { onDelete(item.category) },
            )
        }
        if (dangerCategories.isNotEmpty()) {
            item(key = "privacy_danger_zone_title") {
                Text(
                    text = stringResource(R.string.privacy_danger_zone_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = DimensionTokens.SpacingBase),
                )
            }
            items(
                items = dangerCategories,
                key = { it.category.name },
            ) { item ->
                PrivacyCategoryCard(
                    item = item,
                    onDelete = { onDelete(item.category) },
                )
            }
        }
    }
}

@Composable
private fun PrivacyCategoryCard(
    item: PrivacyDataPresenter.CategoryUi,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = surfaceCardColors,
        shape = CardDefaults.shape,
    ) {
        Column(modifier = Modifier.padding(DimensionTokens.SpacingBase)) {
            Text(
                text = stringResource(item.titleRes),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(item.descriptionRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(item.summaryRes, *item.summaryArgs.toTypedArray()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = DimensionTokens.SpacingSm),
            )
            item.blockReasonRes?.let { reasonRes ->
                Text(
                    text = stringResource(reasonRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (item.deletable) {
                TextButton(onClick = onDelete) {
                    Text(stringResource(R.string.privacy_delete))
                }
            }
        }
    }
}
