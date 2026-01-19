# 专注模式功能改进设计文档

**创建日期**：2026-01-19
**状态**：已批准，待实施
**优先级**：P4

## 背景

专注模式（Focus Mode）是 GKD-SDP 的核心数字自律功能之一，当前版本存在以下 5 个用户体验问题：

1. 时长设置使用滑块（5-240分钟），不够精确和直观
2. 白名单应用选择器缺少搜索和系统应用过滤功能
3. 会话不会在时间到期后自动结束
4. 全屏拦截界面的剩余时间不会自动刷新
5. 启动白名单应用时，系统权限弹窗会被拦截

## 目标

改进专注模式的交互体验，使其更加精确、可靠和易用。

---

## 改进 1：时间输入方式优化

### 当前问题
使用 Slider 设置时长（5-240分钟），精度为 5 分钟，用户无法精确设置任意时长。

### 改进方案

#### UI 设计
- 使用两个 `OutlinedTextField` 输入框：
  - 第一个输入框：小时数（0-48 小时）
  - 第二个输入框：分钟数（0-59 分钟）
- 布局：水平排列，中间显示"小时"和"分钟"标签
- 最小时长限制：总时长至少 5 分钟

#### 数据模型变更
```kotlin
// FocusModeVm.kt
// 修改前：
var manualDurationMinutes by mutableIntStateOf(30)

// 修改后：
var manualHours by mutableIntStateOf(0)
var manualMinutes by mutableIntStateOf(30)
val totalDurationMinutes: Int
    get() = manualHours * 60 + manualMinutes
```

#### 验证逻辑
```kotlin
fun validateDuration(): Boolean {
    return totalDurationMinutes >= 5
}
```

#### UI 实现
```kotlin
// FocusModePage.kt - QuickStartSheet
Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalAlignment = Alignment.CenterVertically
) {
    OutlinedTextField(
        value = vm.manualHours.toString(),
        onValueChange = {
            val hours = it.toIntOrNull()?.coerceIn(0, 48) ?: 0
            vm.manualHours = hours
        },
        label = { Text("小时") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.weight(1f)
    )

    OutlinedTextField(
        value = vm.manualMinutes.toString(),
        onValueChange = {
            val minutes = it.toIntOrNull()?.coerceIn(0, 59) ?: 0
            vm.manualMinutes = minutes
        },
        label = { Text("分钟") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.weight(1f)
    )
}

// 显示验证提示
if (vm.totalDurationMinutes < 5) {
    Text(
        text = "最短时长为 5 分钟",
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall
    )
}
```

---

## 改进 2：白名单选择器优化

### 当前问题
白名单应用选择器是简单的应用列表，没有搜索功能，也不能过滤系统应用。

### 改进方案

#### UI 设计
参考 `AppBlockerPage.kt` 的应用选择器设计：
- 顶部添加搜索框（带清除按钮）
- 添加"显示系统应用"开关
- 应用列表显示应用图标、名称和包名
- 已选中的应用显示勾选图标

#### 数据模型
```kotlin
// FocusModeVm.kt 新增字段
var whitelistSearchQuery by mutableStateOf("")
var showSystemAppsInWhitelist by mutableStateOf(false)

val filteredWhitelistApps: StateFlow<List<Pair<AppInfo, Drawable?>>> =
    combine(
        allAppsFlow,
        manualWhitelistAppsState,
        ::Pair
    ).map { (apps, selectedIds) ->
        apps.filter { (appInfo, _) ->
            // 搜索过滤
            val matchSearch = if (whitelistSearchQuery.isBlank()) {
                true
            } else {
                appInfo.name.contains(whitelistSearchQuery, ignoreCase = true) ||
                appInfo.id.contains(whitelistSearchQuery, ignoreCase = true)
            }

            // 系统应用过滤
            val matchSystemFilter = if (showSystemAppsInWhitelist) {
                true
            } else {
                !appInfo.isSystem
            }

            matchSearch && matchSystemFilter
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
```

#### UI 实现
```kotlin
// FocusModePage.kt - WhitelistPickerDialog
Column(modifier = Modifier.fillMaxSize()) {
    // 搜索框
    OutlinedTextField(
        value = vm.whitelistSearchQuery,
        onValueChange = { vm.whitelistSearchQuery = it },
        placeholder = { Text("搜索应用") },
        leadingIcon = { Icon(PerfIcon.Search, null) },
        trailingIcon = {
            if (vm.whitelistSearchQuery.isNotEmpty()) {
                IconButton(onClick = { vm.whitelistSearchQuery = "" }) {
                    Icon(PerfIcon.Close, "清除")
                }
            }
        },
        modifier = Modifier.fillMaxWidth()
    )

    // 系统应用开关
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { vm.showSystemAppsInWhitelist = !vm.showSystemAppsInWhitelist }
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("显示系统应用")
        Switch(
            checked = vm.showSystemAppsInWhitelist,
            onCheckedChange = { vm.showSystemAppsInWhitelist = it }
        )
    }

    // 应用列表
    val filteredApps by vm.filteredWhitelistApps.collectAsState()
    LazyColumn {
        items(filteredApps, key = { it.first.id }) { (appInfo, icon) ->
            AppListItem(
                appInfo = appInfo,
                icon = icon,
                isSelected = vm.manualWhitelistApps.contains(appInfo.id),
                onToggle = { vm.toggleWhitelistApp(appInfo.id) }
            )
        }
    }
}
```

---

## 改进 3：会话自动结束机制

### 当前问题
专注会话创建后，即使时间到期也不会自动结束，需要手动停止。

### 改进方案

#### 实现思路
在 `FocusModeEngine` 中添加后台监控协程，定期检查会话是否过期，过期时自动结束并发送通知。

#### 核心代码
```kotlin
// FocusModeEngine.kt
init {
    // 现有监听器...

    // 新增：监听会话过期
    appScope.launch(Dispatchers.IO) {
        while (true) {
            delay(30_000L)  // 每 30 秒检查一次

            val session = cachedSession
            if (session != null && session.isActive && !session.isValidNow()) {
                // 会话已过期，停用
                DbSet.focusSessionDao.deactivate()
                LogUtils.d("Focus session expired, deactivated")

                // 发送通知
                NotificationUtils.sendSessionEndNotification(
                    title = "专注结束",
                    message = "专注时间已结束，做得很好！"
                )
            }
        }
    }
}
```

#### 通知实现
```kotlin
// 新增文件：util/NotificationUtils.kt
object NotificationUtils {
    private const val CHANNEL_ID = "focus_mode"
    private const val NOTIFICATION_ID_SESSION_END = 1001

    fun sendSessionEndNotification(title: String, message: String) {
        createNotificationChannel()

        val notification = NotificationCompat.Builder(app, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(app).notify(NOTIFICATION_ID_SESSION_END, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "专注模式",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "专注模式会话通知"
            }
            val manager = app.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}
```

#### 技术细节
- 检查频率：30 秒一次（平衡准确性和性能）
- 通知优先级：DEFAULT（不打扰用户）
- 自动取消：点击通知后自动消失

---

## 改进 4：剩余时间自动刷新

### 当前问题
全屏拦截界面显示的剩余时间是静态的，不会自动更新。

### 改进方案

#### 实现思路
在 `FocusOverlayService.kt` 的 Composable 中使用 `LaunchedEffect` 每分钟触发一次重组。

#### 核心代码
```kotlin
// FocusOverlayService.kt - MainInterceptContent
@Composable
private fun MainInterceptContent(
    message: String,
    whitelist: List<String>,
    isLocked: Boolean,
    endTime: Long,
    onShowWhitelist: () -> Unit
) {
    // 添加自动刷新机制
    var refreshTrigger by remember { mutableIntStateOf(0) }

    LaunchedEffect(endTime) {
        while (endTime > 0) {
            delay(60_000L)  // 每 60 秒刷新一次
            refreshTrigger++
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 显示剩余时间（会随 refreshTrigger 变化自动重组）
        if (endTime > 0) {
            val now = System.currentTimeMillis()
            val remainingMinutes = ((endTime - now) / 60000).coerceAtLeast(0)

            if (remainingMinutes > 0) {
                Text(
                    text = if (remainingMinutes >= 60) {
                        "剩余 ${remainingMinutes / 60} 小时 ${remainingMinutes % 60} 分钟"
                    } else {
                        "剩余 $remainingMinutes 分钟"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        if (isLocked) {
            Text(
                text = "（已锁定，无法提前结束）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
            )
        }

        Spacer(modifier = Modifier.height(48.dp))

        if (whitelist.isNotEmpty()) {
            Button(
                onClick = onShowWhitelist,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("打开白名单应用")
            }
        } else {
            Text(
                text = "暂无白名单应用",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                textAlign = TextAlign.Center
            )
        }
    }
}
```

#### 技术细节
- 刷新频率：60 秒一次
- 性能优化：使用 `refreshTrigger` 触发重组，避免频繁读取系统时间
- 协程管理：`LaunchedEffect` 自动管理生命周期

---

## 改进 5：应用启动权限预检

### 当前问题
启动白名单应用时，如果系统弹出权限确认对话框，该对话框也会被专注模式拦截。

### 改进方案

#### 实现思路
优化应用启动的 Intent 标志，使用标准的应用恢复方式，避免触发权限弹窗。同时延迟关闭全屏服务，确保应用已获得焦点。

#### 核心代码
```kotlin
// FocusOverlayService.kt
@Composable
fun FocusInterceptScreen(
    message: String,
    whitelist: List<String>,
    blockedApp: String,
    isLocked: Boolean,
    endTime: Long,
    onOpenApp: (String) -> Unit
) {
    var showWhitelistPicker by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        if (showWhitelistPicker) {
            WhitelistPickerContent(
                whitelist = whitelist,
                onBack = { showWhitelistPicker = false },
                onSelectApp = { packageName ->
                    launchAppSafely(packageName)
                }
            )
        } else {
            MainInterceptContent(
                message = message,
                whitelist = whitelist,
                isLocked = isLocked,
                endTime = endTime,
                onShowWhitelist = { showWhitelistPicker = true }
            )
        }
    }
}

private fun FocusOverlayService.launchAppSafely(packageName: String) {
    try {
        val launchIntent = app.packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent != null) {
            // 使用正确的启动标志
            launchIntent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED or
                Intent.FLAG_ACTIVITY_CLEAR_TOP
            )

            // 启动应用
            startActivity(launchIntent)

            // 延迟关闭服务，确保应用已启动
            Handler(Looper.getMainLooper()).postDelayed({
                stopSelf()
            }, 300)
        } else {
            Toast.makeText(this, "无法启动该应用", Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        LogUtils.d("Failed to launch app: ${e.message}")
        Toast.makeText(this, "启动失败：${e.message}", Toast.LENGTH_SHORT).show()
    }
}
```

#### 技术细节
- **Intent 标志优化**：
  - `FLAG_ACTIVITY_NEW_TASK`：在新任务栈中启动
  - `FLAG_ACTIVITY_RESET_TASK_IF_NEEDED`：如果应用已在后台，直接恢复
  - `FLAG_ACTIVITY_CLEAR_TOP`：清除目标应用之上的其他活动
- **延迟关闭**：启动应用后延迟 300ms 关闭全屏服务
- **异常处理**：捕获所有启动异常并通过 Toast 提示
- **无需修改白名单**：`FocusModeEngine.isWhitelisted()` 已对 `com.android.systemui` 特殊处理

---

## 文件修改清单

### 需要修改的文件

1. **app/src/main/kotlin/li/songe/gkd/sdp/ui/FocusModeVm.kt**
   - 修改时长字段：`manualHours` + `manualMinutes`
   - 新增白名单搜索字段：`whitelistSearchQuery`, `showSystemAppsInWhitelist`
   - 新增过滤后的应用列表：`filteredWhitelistApps`

2. **app/src/main/kotlin/li/songe/gkd/sdp/ui/FocusModePage.kt**
   - 修改 `QuickStartSheet`：替换 Slider 为双输入框
   - 修改 `WhitelistPickerDialog`：添加搜索框和系统应用过滤

3. **app/src/main/kotlin/li/songe/gkd/sdp/a11y/FocusModeEngine.kt**
   - 新增会话过期监听协程
   - 集成通知发送逻辑

4. **app/src/main/kotlin/li/songe/gkd/sdp/service/FocusOverlayService.kt**
   - 修改 `MainInterceptContent`：添加时间自动刷新
   - 修改应用启动逻辑：优化 Intent 标志和延迟关闭

### 需要新增的文件

5. **app/src/main/kotlin/li/songe/gkd/sdp/util/NotificationUtils.kt**
   - 创建通知工具类
   - 实现会话结束通知

---

## 实施步骤

1. 创建 `NotificationUtils.kt` 工具类
2. 修改 `FocusModeVm.kt` 数据模型
3. 修改 `FocusModePage.kt` UI 实现
4. 修改 `FocusModeEngine.kt` 添加自动结束逻辑
5. 修改 `FocusOverlayService.kt` 添加时间刷新和应用启动优化
6. 编译测试
7. 提交代码

---

## 预期效果

- ✅ 用户可以精确设置任意小时和分钟的专注时长
- ✅ 白名单选择器支持搜索和系统应用过滤，操作更高效
- ✅ 会话到期后自动结束，无需手动操作
- ✅ 全屏拦截界面的剩余时间每分钟自动更新
- ✅ 启动白名单应用不会被系统权限弹窗阻挡
