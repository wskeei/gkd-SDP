package li.songe.gkd.sdp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import li.songe.gkd.sdp.appScope
import li.songe.gkd.sdp.data.CategoryConfig
import li.songe.gkd.sdp.data.RawSubscription
import li.songe.gkd.sdp.db.DbSet
import li.songe.gkd.sdp.ui.component.EmptyText
import li.songe.gkd.sdp.ui.component.FullscreenDialog
import li.songe.gkd.sdp.ui.component.PerfIcon
import li.songe.gkd.sdp.ui.component.PerfIconButton
import li.songe.gkd.sdp.ui.component.PerfTopAppBar
import li.songe.gkd.sdp.ui.component.PerfTriStateSwitch
import li.songe.gkd.sdp.ui.component.TowLineText
import li.songe.gkd.sdp.ui.component.autoFocus
import li.songe.gkd.sdp.ui.component.updateDialogOptions
import li.songe.gkd.sdp.ui.component.useListScrollState
import li.songe.gkd.sdp.ui.share.ListPlaceholder
import li.songe.gkd.sdp.ui.share.LocalMainViewModel
import li.songe.gkd.sdp.ui.share.noRippleClickable
import li.songe.gkd.sdp.ui.style.EmptyHeight
import li.songe.gkd.sdp.ui.style.scaffoldPadding
import li.songe.gkd.sdp.util.EnableGroupOption
import li.songe.gkd.sdp.util.findOption
import li.songe.gkd.sdp.util.getCategoryEnable
import li.songe.gkd.sdp.util.launchAsFn
import li.songe.gkd.sdp.util.throttle
import li.songe.gkd.sdp.util.toast
import li.songe.gkd.sdp.util.updateSubscription
import li.songe.gkd.sdp.R

@Serializable
data class SubsCategoryRoute(val subsItemId: Long) : NavKey

@Composable
fun SubsCategoryPage(@Suppress("unused") route: SubsCategoryRoute) {
    val mainVm = LocalMainViewModel.current

    val vm = viewModel { SubsCategoryVm(route) }
    val subs = vm.subsRawFlow.collectAsStateWithLifecycle().value
    val categoryConfigMap = vm.categoryConfigMapFlow.collectAsStateWithLifecycle().value

    val categories = subs.categories

    val scrollKey = rememberSaveable { mutableIntStateOf(0) }
    val (scrollBehavior, listState) = useListScrollState(scrollKey)
    Scaffold(modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection), topBar = {
        PerfTopAppBar(scrollBehavior = scrollBehavior, navigationIcon = {
            PerfIconButton(
                imageVector = PerfIcon.ArrowBack,
                onClick = mainVm::popPage,
            )
        }, title = {
            TowLineText(
                title = subs.name,
                subtitle = stringResource(R.string.subs_rule_category),
                modifier = Modifier.noRippleClickable(onClick = { scrollKey.intValue++ })
            )
        }, actions = {
            PerfIconButton(imageVector = PerfIcon.Info, onClick = throttle {
                mainVm.dialogFlow.updateDialogOptions(
                    title = li.songe.gkd.sdp.app.getString(R.string.s_a866534a0d),
                    text = arrayOf(
                        li.songe.gkd.sdp.app.getString(R.string.subs_category_help_intro),
                        li.songe.gkd.sdp.app.getString(R.string.subs_category_help_priority),
                        li.songe.gkd.sdp.app.getString(R.string.subs_category_help_reset),
                    ).joinToString("\n\n"),
                )
            })
        })
    }, floatingActionButton = {
        if (subs.isLocal) {
            FloatingActionButton(onClick = { vm.showAddCategoryFlow.value = true }) {
                PerfIcon(
                    imageVector = PerfIcon.Add,
                )
            }
        }
    }) { contentPadding ->
        LazyColumn(
            modifier = Modifier.scaffoldPadding(contentPadding),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(categories, { it.key }) { category ->
                CategoryItemCard(
                    subs = subs,
                    category = category,
                    categoryConfig = categoryConfigMap[category.key],
                )
            }
            item(ListPlaceholder.KEY, ListPlaceholder.TYPE) {
                Spacer(modifier = Modifier.height(EmptyHeight))
                if (categories.isEmpty()) {
                    EmptyText(text = li.songe.gkd.sdp.app.getString(R.string.s_90fa56d24c))
                }
            }
        }
    }

    if (vm.showAddCategoryFlow.collectAsStateWithLifecycle().value) {
        UpsertCategoryDialog(
            subs = subs,
            category = null,
        ) {
            vm.showAddCategoryFlow.value = false
        }
    }
}

@Composable
private fun CategoryItemCard(
    subs: RawSubscription,
    category: RawSubscription.RawCategory,
    categoryConfig: CategoryConfig?,
) {
    val mainVm = LocalMainViewModel.current
    Card(
        onClick = {
            mainVm.navigatePage(
                SubsCategoryGroupRoute(
                    subsId = subs.id,
                    categoryKey = category.key
                )
            )
        },
        shape = MaterialTheme.shapes.extraSmall,
        modifier = Modifier.padding(
            horizontal = 8.dp,
        ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .padding(8.dp)
                    .weight(1f)
            ) {
                Text(
                    text = category.name,
                    style = MaterialTheme.typography.bodyLarge,
                )
                val desc = subs.getCategoryCompatDesc(
                    category.key,
                    li.songe.gkd.sdp.app,
                )
                if (desc != null) {
                    Text(
                        text = desc,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        text = li.songe.gkd.sdp.app.getString(R.string.s_cff584d9ab),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }
            PerfTriStateSwitch(
                modifier = Modifier
                    .noRippleClickable(onClick = {})
                    .padding(8.dp),
                checked = getCategoryEnable(category, categoryConfig),
                onCheckedChange = throttle(appScope.launchAsFn<Boolean?> {
                    val option = EnableGroupOption.objects.findOption(it)
                    DbSet.categoryConfigDao.insert(
                        (categoryConfig ?: CategoryConfig(
                            enable = option.value,
                            subsId = subs.id,
                            categoryKey = category.key
                        )).copy(enable = option.value)
                    )
                    toast(li.songe.gkd.sdp.app.getString(option.labelRes))
                })
            )
        }
    }
}

@Composable
fun UpsertCategoryDialog(
    subs: RawSubscription,
    category: RawSubscription.RawCategory?,
    onDismissRequest: () -> Unit,
) {
    var nameValue by remember { mutableStateOf(category?.name ?: "") }
    var descValue by remember { mutableStateOf(category?.desc ?: "") }
    val onClick = appScope.launchAsFn {
        if (category != null) {
            if (subs.categories.any { c -> c.key != category.key && c.name == nameValue }) {
                error(li.songe.gkd.sdp.app.getString(R.string.subs_category_duplicate))
            }
            onDismissRequest()
            val changed = category.name != nameValue || (category.desc ?: "") != descValue
            if (changed) {
                updateSubscription(
                    subs.copy(categories = subs.categories.toMutableList().apply {
                        set(
                            indexOfFirst { c -> c.key == category.key },
                            category.copy(name = nameValue, desc = descValue)
                        )
                    })
                )
                toast(li.songe.gkd.sdp.app.getString(R.string.s_e2cff77372))
            } else {
                toast(li.songe.gkd.sdp.app.getString(R.string.s_fff8cc4d94))
            }
        } else {
            if (subs.categories.any { c -> c.name == nameValue }) {
                error(li.songe.gkd.sdp.app.getString(R.string.subs_category_duplicate))
            }
            onDismissRequest()
            updateSubscription(
                subs.copy(categories = subs.categories.toMutableList().apply {
                    val c = RawSubscription.RawCategory(
                        key = (subs.categories.maxOfOrNull { c -> c.key } ?: -1) + 1,
                        enable = null,
                        name = nameValue,
                        desc = descValue,
                    )
                    add(c)
                })
            )
            toast(li.songe.gkd.sdp.app.getString(R.string.s_6950d05c09))
        }
    }
    FullscreenDialog(onDismissRequest = onDismissRequest) {
        Scaffold(
            topBar = {
                PerfTopAppBar(
                    navigationIcon = {
                        PerfIconButton(
                            imageVector = PerfIcon.Close,
                            onClick = throttle(onDismissRequest),
                        )
                    },
                    title = { Text(text = if (category == null) li.songe.gkd.sdp.app.getString(R.string.s_f0250d57b9) else li.songe.gkd.sdp.app.getString(R.string.s_40635c4228)) },
                    actions = {
                        PerfIconButton(
                            imageVector = PerfIcon.Save,
                            enabled = nameValue.isNotEmpty(),
                            onClick = throttle(onClick),
                        )
                    }
                )
            },
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp),
            ) {
                OutlinedTextField(
                    label = { Text(li.songe.gkd.sdp.app.getString(R.string.s_54900435fc)) },
                    value = nameValue,
                    onValueChange = { nameValue = it.trim() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .autoFocus(),
                    placeholder = { Text(text = li.songe.gkd.sdp.app.getString(R.string.s_b4676e2715)) },
                    singleLine = true,
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    label = { Text(li.songe.gkd.sdp.app.getString(R.string.s_3aba810f58)) },
                    value = descValue,
                    onValueChange = { descValue = it.trim() },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(text = li.songe.gkd.sdp.app.getString(R.string.s_90059a73fc)) },
                    singleLine = true,
                )
            }
        }
    }
}
