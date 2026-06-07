# App Blocker Group Fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the `应用拦截` group-rule sheet bounce and add an explicit append-only `添加应用` flow for existing app groups without allowing already-added apps to be removed.

**Architecture:** Keep persistence unchanged in `AppGroup` and `AppBlockerVm.saveGroup()`. Add one small pure policy/helper layer for append-only app merging and bottom-sheet drag gating, then wire that policy through `AppBlockerVm`, `AppBlockerPage`, and `AppPickerDialog` so the new behavior is easy to test and the UI stays aligned with the current Compose structure.

**Tech Stack:** Kotlin, Jetpack Compose Material3, Room, Kotlin Flow, JUnit4 unit tests, Gradle.

---

## Problem Summary

1. **Rule-sheet bounce root cause**

`app/src/main/kotlin/li/songe/gkd/sdp/ui/AppBlockerPage.kt` currently renders `RuleEditorSheet` with `ModalBottomSheet` plus a nested `LazyColumn`, but the sheet state uses bare `rememberModalBottomSheetState(skipPartiallyExpanded = true)` and never gates sheet dragging based on inner scroll position. That means an upward swipe at the top of the content is handed to the sheet itself, which produces the visible vertical bounce. The repo already has the intended pattern in `app/src/main/kotlin/li/songe/gkd/sdp/ui/component/SubsSheet.kt`.

2. **Existing-group app expansion root cause**

The current group editor is replacement-oriented, not append-oriented:

- `app/src/main/kotlin/li/songe/gkd/sdp/ui/AppBlockerVm.kt` only keeps one `groupApps` list and exposes `removeAppFromGroup(...)`.
- `app/src/main/kotlin/li/songe/gkd/sdp/ui/AppBlockerPage.kt` makes every selected app chip removable in `GroupEditorSheet`.
- `app/src/main/kotlin/li/songe/gkd/sdp/ui/component/AppPickerDialog.kt` toggles existing selections on and off, so existing members can be removed and there is no dedicated “只新增、不删减” flow for already-created groups.

The implementation must change the editor interaction model, not the database schema.

## File Map

**Create:**
- `app/src/main/kotlin/li/songe/gkd/sdp/ui/AppBlockerEditorPolicy.kt`
- `app/src/test/kotlin/li/songe/gkd/sdp/ui/AppBlockerEditorPolicyTest.kt`
- `app/src/test/kotlin/li/songe/gkd/sdp/ui/AppBlockerVmGroupEditorTest.kt`

**Modify:**
- `app/src/main/kotlin/li/songe/gkd/sdp/ui/AppBlockerVm.kt`
- `app/src/main/kotlin/li/songe/gkd/sdp/ui/AppBlockerPage.kt`
- `app/src/main/kotlin/li/songe/gkd/sdp/ui/component/AppPickerDialog.kt`

**Responsibilities:**
- `AppBlockerEditorPolicy.kt`: pure append-only app merge rules plus scroll-top drag gating for `ModalBottomSheet`.
- `AppBlockerEditorPolicyTest.kt`: locks down append-vs-replace behavior and sheet drag gating.
- `AppBlockerVmGroupEditorTest.kt`: locks down VM-facing picker configuration for create/edit/append flows.
- `AppBlockerVm.kt`: owns `GroupEditorMode`, picker config generation, and group-app merge plumbing.
- `AppBlockerPage.kt`: adds the visible `添加应用` affordance, removes existing-app deletion affordances in edit/append flows, and applies scroll-aware sheet state to both group and rule editors.
- `AppPickerDialog.kt`: supports excluding already-added apps and custom labels so the dialog can be reused for append-only selection without breaking current call sites.

**No schema changes planned:**
- `app/src/main/kotlin/li/songe/gkd/sdp/data/AppGroup.kt`
- `app/src/main/kotlin/li/songe/gkd/sdp/db/AppDb.kt`

---

### Task 1: Lock the editor policy with pure tests first

**Files:**
- Create: `app/src/test/kotlin/li/songe/gkd/sdp/ui/AppBlockerEditorPolicyTest.kt`
- Create: `app/src/main/kotlin/li/songe/gkd/sdp/ui/AppBlockerEditorPolicy.kt`

- [ ] **Step 1: Write the failing tests for append-only merging and sheet drag gating**

```kotlin
package li.songe.gkd.sdp.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppBlockerEditorPolicyTest {
    @Test
    fun appendOnlyMergeKeepsExistingAppsAndAddsOnlyNewOnes() {
        val resolved = AppBlockerEditorPolicy.resolveGroupApps(
            existingApps = listOf("video.app", "chat.app"),
            pickedApps = listOf("chat.app", "music.app", "reader.app"),
            appendOnly = true,
        )

        assertEquals(
            listOf("video.app", "chat.app", "music.app", "reader.app"),
            resolved,
        )
    }

    @Test
    fun replaceModeUsesOnlyPickedAppsForNewGroupCreation() {
        val resolved = AppBlockerEditorPolicy.resolveGroupApps(
            existingApps = listOf("old.app"),
            pickedApps = listOf("new.app", "new.app", "reader.app"),
            appendOnly = false,
        )

        assertEquals(listOf("new.app", "reader.app"), resolved)
    }

    @Test
    fun sheetDragIsAllowedOnlyWhenScrollableContentIsAtTop() {
        assertTrue(
            AppBlockerEditorPolicy.canDragSheet(
                firstVisibleItemIndex = 0,
                firstVisibleItemScrollOffset = 0,
            )
        )
        assertFalse(
            AppBlockerEditorPolicy.canDragSheet(
                firstVisibleItemIndex = 0,
                firstVisibleItemScrollOffset = 12,
            )
        )
        assertFalse(
            AppBlockerEditorPolicy.canDragSheet(
                firstVisibleItemIndex = 1,
                firstVisibleItemScrollOffset = 0,
            )
        )
    }
}
```

- [ ] **Step 2: Run the new test target and confirm it fails because the policy does not exist yet**

Run: `.\gradlew.bat :app:testGkdDebugUnitTest --tests "li.songe.gkd.sdp.ui.AppBlockerEditorPolicyTest"`

Expected: FAIL with compilation errors such as `Unresolved reference: AppBlockerEditorPolicy`.

- [ ] **Step 3: Add the minimal pure policy implementation**

```kotlin
package li.songe.gkd.sdp.ui

object AppBlockerEditorPolicy {
    fun resolveGroupApps(
        existingApps: List<String>,
        pickedApps: List<String>,
        appendOnly: Boolean,
    ): List<String> {
        val normalizedPickedApps = pickedApps.distinct()
        return if (appendOnly) {
            (existingApps + normalizedPickedApps).distinct()
        } else {
            normalizedPickedApps
        }
    }

    fun canDragSheet(
        firstVisibleItemIndex: Int,
        firstVisibleItemScrollOffset: Int,
    ): Boolean {
        return firstVisibleItemIndex == 0 && firstVisibleItemScrollOffset == 0
    }
}
```

- [ ] **Step 4: Re-run the policy test target and confirm it passes**

Run: `.\gradlew.bat :app:testGkdDebugUnitTest --tests "li.songe.gkd.sdp.ui.AppBlockerEditorPolicyTest"`

Expected: PASS and `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit the pure policy layer**

```bash
git add app/src/main/kotlin/li/songe/gkd/sdp/ui/AppBlockerEditorPolicy.kt app/src/test/kotlin/li/songe/gkd/sdp/ui/AppBlockerEditorPolicyTest.kt
git commit -m "test: lock app blocker editor policy"
```

### Task 2: Add VM support for create/edit/append group flows

**Files:**
- Create: `app/src/test/kotlin/li/songe/gkd/sdp/ui/AppBlockerVmGroupEditorTest.kt`
- Modify: `app/src/main/kotlin/li/songe/gkd/sdp/ui/AppBlockerVm.kt`
- Modify: `app/src/main/kotlin/li/songe/gkd/sdp/ui/AppBlockerEditorPolicy.kt`

- [ ] **Step 1: Write the failing tests for VM picker configuration**

```kotlin
package li.songe.gkd.sdp.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class AppBlockerVmGroupEditorTest {
    @Test
    fun appendAppsModeStartsWithEmptyPickerSelectionAndExcludesExistingApps() {
        val config = AppBlockerVm.buildGroupPickerConfig(
            currentApps = listOf("video.app", "chat.app"),
            mode = AppBlockerVm.GroupEditorMode.AppendApps,
        )

        assertEquals(emptyList<String>(), config.initialSelection)
        assertEquals(setOf("video.app", "chat.app"), config.excludedApps)
    }

    @Test
    fun createModeKeepsSelectionEditable() {
        val config = AppBlockerVm.buildGroupPickerConfig(
            currentApps = listOf("video.app"),
            mode = AppBlockerVm.GroupEditorMode.Create,
        )

        assertEquals(listOf("video.app"), config.initialSelection)
        assertEquals(emptySet<String>(), config.excludedApps)
    }
}
```

- [ ] **Step 2: Run the VM test target and confirm it fails until the new editor mode API exists**

Run: `.\gradlew.bat :app:testGkdDebugUnitTest --tests "li.songe.gkd.sdp.ui.AppBlockerVmGroupEditorTest"`

Expected: FAIL with compilation errors such as `Unresolved reference: GroupEditorMode` or `Unresolved reference: buildGroupPickerConfig`.

- [ ] **Step 3: Add the minimal VM-facing editor mode and picker config**

```kotlin
class AppBlockerVm : BaseViewModel() {
    enum class GroupEditorMode {
        Create,
        Edit,
        AppendApps,
    }

    data class GroupPickerConfig(
        val initialSelection: List<String>,
        val excludedApps: Set<String>,
    )

    var groupEditorMode by mutableStateOf(GroupEditorMode.Create)

    fun resetGroupForm() {
        editingGroup = null
        groupEditorMode = GroupEditorMode.Create
        groupName = ""
        groupApps = emptyList()
        showGroupEditor = false
    }

    fun loadGroupForEdit(
        group: AppGroup,
        mode: GroupEditorMode = GroupEditorMode.Edit,
    ) {
        editingGroup = group
        groupEditorMode = mode
        groupName = group.name
        groupApps = group.getAppList()
        showGroupEditor = true
    }

    fun applyPickedApps(pickedApps: List<String>) {
        groupApps = AppBlockerEditorPolicy.resolveGroupApps(
            existingApps = groupApps,
            pickedApps = pickedApps,
            appendOnly = groupEditorMode == GroupEditorMode.AppendApps,
        )
    }

    companion object {
        fun buildGroupPickerConfig(
            currentApps: List<String>,
            mode: GroupEditorMode,
        ): GroupPickerConfig {
            return if (mode == GroupEditorMode.AppendApps) {
                GroupPickerConfig(
                    initialSelection = emptyList(),
                    excludedApps = currentApps.toSet(),
                )
            } else {
                GroupPickerConfig(
                    initialSelection = currentApps,
                    excludedApps = emptySet(),
                )
            }
        }
    }
}
```

- [ ] **Step 4: Re-run the VM test target and confirm it passes**

Run: `.\gradlew.bat :app:testGkdDebugUnitTest --tests "li.songe.gkd.sdp.ui.AppBlockerVmGroupEditorTest"`

Expected: PASS and `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit the VM state changes**

```bash
git add app/src/main/kotlin/li/songe/gkd/sdp/ui/AppBlockerVm.kt app/src/test/kotlin/li/songe/gkd/sdp/ui/AppBlockerVmGroupEditorTest.kt
git commit -m "feat: add app blocker group editor modes"
```

### Task 3: Expose a visible append-only `添加应用` flow in the UI

**Files:**
- Modify: `app/src/main/kotlin/li/songe/gkd/sdp/ui/AppBlockerPage.kt`
- Modify: `app/src/main/kotlin/li/songe/gkd/sdp/ui/component/AppPickerDialog.kt`
- Modify: `app/src/main/kotlin/li/songe/gkd/sdp/ui/AppBlockerVm.kt`

- [ ] **Step 1: Extend `AppPickerDialog` so append-only callers can exclude existing group members**

```kotlin
@Composable
fun AppPickerDialog(
    currentApps: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit,
    singleSelect: Boolean = false,
    excludedApps: Set<String> = emptySet(),
    titleText: String = if (singleSelect) "选择应用" else "选择应用列表",
    emptyText: String = "未找到匹配的应用",
) {
    var selectedApps by remember { mutableStateOf(currentApps.toSet()) }
    var searchQuery by remember { mutableStateOf("") }
    var showSystemApps by remember { mutableStateOf(false) }
    val appInfoMap by appInfoMapFlow.collectAsState()

    val filteredApps = remember(appInfoMap, searchQuery, showSystemApps, excludedApps) {
        appInfoMap.values
            .filterNot { it.hidden || excludedApps.contains(it.id) }
            .filter { appInfo ->
                if (!showSystemApps && appInfo.isSystem) {
                    false
                } else if (searchQuery.isBlank()) {
                    true
                } else {
                    appInfo.name.contains(searchQuery, ignoreCase = true) ||
                        appInfo.id.contains(searchQuery, ignoreCase = true)
                }
            }
            .sortedBy { it.name }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(titleText) },
        text = {
            Column {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("搜索应用") },
                    placeholder = { Text("输入应用名称或包名") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = {
                        Icon(PerfIcon.Search, contentDescription = "搜索")
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(PerfIcon.Close, contentDescription = "清除")
                            }
                        }
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showSystemApps = !showSystemApps }
                        .padding(vertical = 8.dp)
                ) {
                    Checkbox(
                        checked = showSystemApps,
                        onCheckedChange = { showSystemApps = it }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "显示系统应用",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                ) {
                    if (filteredApps.isEmpty()) {
                        item {
                            Text(
                                text = emptyText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    } else {
                        items(filteredApps) { appInfo ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedApps = if (singleSelect) {
                                            setOf(appInfo.id)
                                        } else {
                                            if (selectedApps.contains(appInfo.id)) {
                                                selectedApps - appInfo.id
                                            } else {
                                                selectedApps + appInfo.id
                                            }
                                        }
                                    }
                                    .padding(vertical = 8.dp)
                            ) {
                                Checkbox(
                                    checked = selectedApps.contains(appInfo.id),
                                    onCheckedChange = {
                                        selectedApps = if (singleSelect) {
                                            setOf(appInfo.id)
                                        } else {
                                            if (it) {
                                                selectedApps + appInfo.id
                                            } else {
                                                selectedApps - appInfo.id
                                            }
                                        }
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                AppIcon(appId = appInfo.id)
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = appInfo.name,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    if (appInfo.isSystem) {
                                        Text(
                                            text = "系统应用",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selectedApps.toList()) }) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
```

- [ ] **Step 2: Add a dedicated `添加应用` action to each unlocked group card and route it into append mode**

```kotlin
private fun AppGroupCard(
    group: AppGroup,
    rules: List<BlockTimeRule>,
    globalLock: AppBlockerLock?,
    onToggleEnabled: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onLock: () -> Unit,
    onAddRule: () -> Unit,
    onAddApps: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.End,
        modifier = Modifier.fillMaxWidth()
    ) {
        if (!group.isCurrentlyLocked) {
            TextButton(onClick = onAddApps) {
                Icon(PerfIcon.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("添加应用")
            }
        }

        if (!group.isCurrentlyLocked) {
            TextButton(onClick = onAddRule) {
                Icon(PerfIcon.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("添加规则")
            }
        }

        TextButton(onClick = onLock) {
            Icon(PerfIcon.Lock, contentDescription = null)
            Spacer(modifier = Modifier.width(4.dp))
            Text(if (group.isCurrentlyLocked) "延长锁定" else "锁定")
        }
    }
}

AppGroupCard(
    group = group,
    rules = allRules.filter {
        it.targetType == BlockTimeRule.TARGET_TYPE_GROUP &&
            it.targetId == group.id.toString()
    },
    globalLock = globalLock,
    onToggleEnabled = { vm.toggleGroupEnabled(group) },
    onEdit = { vm.loadGroupForEdit(group) },
    onDelete = { vm.deleteGroup(group) },
    onLock = {
        lockTargetGroup = group
        showGroupLockSheet = true
    },
    onAddRule = {
        vm.resetRuleForm()
        vm.ruleTargetType = BlockTimeRule.TARGET_TYPE_GROUP
        vm.ruleTargetId = group.id.toString()
        vm.showRuleEditor = true
    },
    onAddApps = {
        vm.loadGroupForEdit(group, AppBlockerVm.GroupEditorMode.AppendApps)
    }
)
```

- [ ] **Step 3: Make existing apps read-only in edit/append mode and wire the picker through the new VM config**

```kotlin
@Composable
private fun GroupEditorSheet(
    vm: AppBlockerVm,
    isLocked: Boolean = false,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    var showAppPicker by remember { mutableStateOf(false) }
    val pickerConfig = remember(vm.groupApps, vm.groupEditorMode) {
        AppBlockerVm.buildGroupPickerConfig(
            currentApps = vm.groupApps,
            mode = vm.groupEditorMode,
        )
    }
    val isAppendMode = vm.groupEditorMode == AppBlockerVm.GroupEditorMode.AppendApps

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(
            text = when {
                vm.editingGroup == null -> "添加应用组"
                isAppendMode -> "添加应用"
                isLocked -> "查看应用组 (已锁定)"
                else -> "编辑应用组"
            },
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        OutlinedTextField(
            value = vm.groupName,
            onValueChange = { vm.groupName = it },
            label = { Text("应用组名称") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !isLocked && !isAppendMode
        )

        if (vm.editingGroup != null) {
            Text(
                text = "已添加的应用不可移除；如需移除，请删除整个应用组后重建。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            vm.groupApps.forEach { packageName ->
                FilterChip(
                    selected = true,
                    onClick = { },
                    label = { Text(packageName) },
                    enabled = false
                )
            }
        }

        TextButton(
            onClick = { showAppPicker = true },
            enabled = !isLocked
        ) {
            Text(if (isAppendMode) "添加应用" else "选择")
        }
    }

    if (showAppPicker) {
        AppPickerDialog(
            currentApps = pickerConfig.initialSelection,
            excludedApps = pickerConfig.excludedApps,
            titleText = if (isAppendMode) "添加应用" else "选择应用列表",
            emptyText = if (isAppendMode) "没有可继续添加的应用" else "未找到匹配的应用",
            onDismiss = { showAppPicker = false },
            onConfirm = { selected ->
                vm.applyPickedApps(selected)
                showAppPicker = false
            }
        )
    }
}
```

- [ ] **Step 4: Compile the module and run the focused unit tests**

Run: `.\gradlew.bat :app:compileGkdDebugKotlin :app:testGkdDebugUnitTest --tests "li.songe.gkd.sdp.ui.AppBlockerEditorPolicyTest" --tests "li.songe.gkd.sdp.ui.AppBlockerVmGroupEditorTest"`

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit the append-only group UI flow**

```bash
git add app/src/main/kotlin/li/songe/gkd/sdp/ui/AppBlockerPage.kt app/src/main/kotlin/li/songe/gkd/sdp/ui/AppBlockerVm.kt app/src/main/kotlin/li/songe/gkd/sdp/ui/component/AppPickerDialog.kt
git commit -m "feat: add append-only app blocker group app flow"
```

### Task 4: Stop the rule sheet from bouncing when the content is already at the top

**Files:**
- Modify: `app/src/main/kotlin/li/songe/gkd/sdp/ui/AppBlockerPage.kt`

- [ ] **Step 1: Apply scroll-aware `ModalBottomSheet` state to `RuleEditorSheet`**

```kotlin
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun RuleEditorSheet(
    vm: AppBlockerVm,
    allGroups: List<AppGroup>,
    isLocked: Boolean = false,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    var showAppPicker by remember { mutableStateOf(false) }
    var showTemplateDialog by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    var swipeEnabled by remember { mutableStateOf(true) }
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { swipeEnabled },
    )

    LaunchedEffect(
        listState.firstVisibleItemIndex,
        listState.firstVisibleItemScrollOffset,
    ) {
        swipeEnabled = AppBlockerEditorPolicy.canDragSheet(
            firstVisibleItemIndex = listState.firstVisibleItemIndex,
            firstVisibleItemScrollOffset = listState.firstVisibleItemScrollOffset,
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            item {
                Text(
                    text = if (vm.editingRule != null) {
                        if (isLocked) "查看规则 (已锁定)" else "编辑规则"
                    } else "添加规则",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "拦截对象",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = vm.ruleTargetType == BlockTimeRule.TARGET_TYPE_APP,
                        onClick = {
                            vm.ruleTargetType = BlockTimeRule.TARGET_TYPE_APP
                            vm.ruleTargetId = ""
                        },
                        label = { Text("单独应用") },
                        enabled = !isLocked
                    )
                    FilterChip(
                        selected = vm.ruleTargetType == BlockTimeRule.TARGET_TYPE_GROUP,
                        onClick = {
                            vm.ruleTargetType = BlockTimeRule.TARGET_TYPE_GROUP
                            vm.ruleTargetId = ""
                        },
                        label = { Text("应用组") },
                        enabled = !isLocked
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 2: Apply the same drag-gating pattern to `GroupEditorSheet` with a scroll state**

```kotlin
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun GroupEditorSheet(
    vm: AppBlockerVm,
    isLocked: Boolean = false,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    var showAppPicker by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    var swipeEnabled by remember { mutableStateOf(true) }
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { swipeEnabled },
    )

    LaunchedEffect(scrollState.value) {
        swipeEnabled = AppBlockerEditorPolicy.canDragSheet(
            firstVisibleItemIndex = 0,
            firstVisibleItemScrollOffset = scrollState.value,
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(16.dp)
        ) {
            Text(
                text = if (vm.editingGroup != null) "编辑应用组" else "添加应用组",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = vm.groupName,
                onValueChange = { vm.groupName = it },
                label = { Text("应用组名称") },
                placeholder = { Text("如：娱乐应用") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isLocked
            )
        }
    }
}
```

- [ ] **Step 3: Build the debug variant and run the focused unit tests again**

Run: `.\gradlew.bat :app:compileGkdDebugKotlin :app:testGkdDebugUnitTest --tests "li.songe.gkd.sdp.ui.AppBlockerEditorPolicyTest" --tests "li.songe.gkd.sdp.ui.AppBlockerVmGroupEditorTest"`

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Perform the manual regression check on device or emulator**

1. Open `数字自律 -> 应用拦截`.
2. Create one app group with at least two apps if none exists.
3. Tap that group’s `添加规则`.
4. Swipe upward several times from the middle and bottom portions of the sheet.
5. Confirm the sheet content scrolls normally when there is overflow, and when already at the top the sheet no longer does a visible vertical spring/bounce.
6. Return to the group card and tap `添加应用`.
7. Add one new app and save.
8. Re-open `添加应用` and confirm already-added apps are not removable and do not appear as toggleable selections in the picker.
9. Confirm the group card now shows the original apps plus the newly appended app.

- [ ] **Step 5: Commit the sheet interaction fix**

```bash
git add app/src/main/kotlin/li/songe/gkd/sdp/ui/AppBlockerPage.kt
git commit -m "fix: stabilize app blocker sheets"
```

## Coverage Check

- The rule-sheet bounce is covered by Task 1 policy tests plus Task 4 UI wiring and manual regression.
- The missing append-only `添加应用` flow for existing groups is covered by Task 2 VM state, Task 3 UI wiring, and Task 4 manual regression.
- The “already-added apps cannot be removed, delete the whole group instead” requirement is enforced by Task 2 picker config and Task 3 read-only existing app chips plus excluded picker items.
