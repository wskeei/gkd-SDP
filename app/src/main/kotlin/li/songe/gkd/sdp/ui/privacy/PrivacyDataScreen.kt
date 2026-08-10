package li.songe.gkd.sdp.ui.privacy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import li.songe.gkd.sdp.R
import li.songe.gkd.sdp.privacy.DataCategory
import li.songe.gkd.sdp.privacy.DataDeletionCoordinator
import li.songe.gkd.sdp.privacy.DataInventoryRepository
import li.songe.gkd.sdp.ui.component.AppConfirmationDialog
import li.songe.gkd.sdp.ui.component.ContentState
import li.songe.gkd.sdp.ui.component.ContentStateBox
import li.songe.gkd.sdp.ui.component.PerfIcon
import li.songe.gkd.sdp.ui.component.PerfIconButton
import li.songe.gkd.sdp.ui.component.PerfTopAppBar
import li.songe.gkd.sdp.ui.share.LocalMainViewModel
import li.songe.gkd.sdp.ui.style.DimensionTokens
import li.songe.gkd.sdp.ui.style.surfaceCardColors

@Composable
fun PrivacyDataScreen() {
    val mainVm = LocalMainViewModel.current
    val scope = rememberCoroutineScope()
    var inventory by remember {
        mutableStateOf<Map<DataCategory, DataDeletionCoordinator.CategoryStatus>?>(null)
    }
    var deleting by remember { mutableStateOf<DataCategory?>(null) }
    var deleteError by remember { mutableStateOf<String?>(null) }
    val repository = remember { DataInventoryRepository() }
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
                title = { Text(stringResource(R.string.privacy_data_title)) },
            )
        },
    ) { padding ->
        val state = when (val data = inventory) {
            null -> ContentState.Loading
            else -> ContentState.Content
        }
        ContentStateBox(state = state, modifier = Modifier.padding(padding)) {
            val data = requireNotNull(inventory)
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
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
                items(
                    items = PrivacyDataPresenter.present(data),
                    key = { it.category.name },
                ) { item ->
                    PrivacyCategoryCard(
                        item = item,
                        onDelete = { deleting = item.category },
                    )
                }
            }
        }
    }

    deleting?.let { category ->
        val status = inventory?.get(category)
        val ui = PrivacyDataPresenter.present(inventory.orEmpty())
            .firstOrNull { it.category == category }
        AppConfirmationDialog(
            title = stringResource(R.string.privacy_delete_title),
            objectName = ui?.title.orEmpty(),
            description = ui?.description.orEmpty() +
                (status?.let { "\n\n${DataDeletionCoordinator.summaryText(it)}" }.orEmpty()),
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
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = item.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = item.summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = DimensionTokens.SpacingSm),
            )
            item.blockReason?.let { reason ->
                Text(
                    text = reason,
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
