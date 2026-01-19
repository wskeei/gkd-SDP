# 微信联系人白名单设计方案

## 概述

在专注模式中支持微信联系人级别的白名单控制，允许用户只与指定联系人聊天，而拦截微信的其他功能（朋友圈、视频号、发现页等）。

## 需求背景

用户在专注模式中有时需要与特定人员沟通（如导师讨论论文），但微信的其他功能充满诱惑力。单纯将微信加入应用白名单粒度太大，需要更精细的控制。

## 核心功能

1. **联系人抓取** - 使用无障碍服务自动抓取微信通讯录联系人
2. **白名单设置** - 在专注模式开始前选择允许聊天的联系人
3. **精准拦截** - 只允许与白名单联系人聊天，其他微信界面全部拦截
4. **快速跳转** - 在拦截页点击联系人直接跳转到聊天界面

## 数据模型设计

### 新增实体：WechatContact

```kotlin
@Entity(tableName = "wechat_contact")
data class WechatContact(
    @PrimaryKey val wechatId: String,  // 微信号（主键）
    @ColumnInfo(name = "nickname") val nickname: String,  // 昵称
    @ColumnInfo(name = "remark") val remark: String = "",  // 备注名
    @ColumnInfo(name = "avatar_url") val avatarUrl: String = "",  // 头像（可选）
    @ColumnInfo(name = "last_updated") val lastUpdated: Long = System.currentTimeMillis()
) {
    val displayName: String get() = remark.ifEmpty { nickname }
}
```

### 扩展现有实体

**FocusRule.kt 和 FocusSession.kt 添加字段：**

```kotlin
@ColumnInfo(name = "wechat_whitelist", defaultValue = "[]")
val wechatWhitelist: String = "[]"  // JSON: List<String> 微信号列表

fun getWechatWhitelist(): List<String> {
    return try {
        json.decodeFromString<List<String>>(wechatWhitelist)
    } catch (e: Exception) {
        emptyList()
    }
}

fun withWechatWhitelist(wechatIds: List<String>): FocusRule {
    return copy(wechatWhitelist = json.encodeToString(wechatIds))
}
```

### 数据库迁移

```kotlin
@Database(
    version = 23,  // 从 22 升级到 23
    entities = [
        // ... 现有实体
        WechatContact::class,  // 新增
    ],
    autoMigrations = [
        AutoMigration(from = 22, to = 23),
    ]
)
```

### DAO 接口

```kotlin
@Dao
interface WechatContactDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(contacts: List<WechatContact>)

    @Query("SELECT * FROM wechat_contact ORDER BY remark, nickname")
    fun queryAll(): Flow<List<WechatContact>>

    @Query("SELECT * FROM wechat_contact WHERE wechatId IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<WechatContact>

    @Query("SELECT wechatId FROM wechat_contact WHERE nickname = :name OR remark = :name LIMIT 1")
    suspend fun findIdByName(name: String): String?

    @Query("DELETE FROM wechat_contact")
    suspend fun deleteAll()
}
```

## 无障碍服务抓取设计

### 抓取流程

1. **用户触发** - 在设置页点击"更新微信联系人"按钮
2. **引导用户** - Toast 提示"请打开微信通讯录页面"
3. **自动抓取** - 检测到通讯录页面后：
   - 遍历当前可见的联系人列表项
   - 逐个点击进入详情页
   - 读取微信号、昵称、备注名
   - 按返回键回到列表
   - 继续下一个
4. **滚动支持** - 抓取完当前屏幕后，自动向下滚动，继续抓取
5. **完成提示** - 抓取完成后 Toast 显示"已更新 X 个联系人"

### 反检测机制

**1. 点击位置随机偏移**

```kotlin
fun clickWithRandomOffset(node: AccessibilityNodeInfo) {
    val rect = Rect()
    node.getBoundsInScreen(rect)

    // 在控件范围内随机偏移 ±20%
    val offsetX = (rect.width() * 0.2 * (Math.random() - 0.5)).toInt()
    val offsetY = (rect.height() * 0.2 * (Math.random() - 0.5)).toInt()

    val centerX = rect.centerX() + offsetX
    val centerY = rect.centerY() + offsetY

    // 使用 GestureDescription 在指定坐标点击
}
```

**2. 操作间隔随机化**

- 点击联系人后等待：500-1000ms（随机）
- 读取详情后等待：300-600ms（随机）
- 返回后等待：400-800ms（随机）
- 滚动后等待：800-1500ms（随机）

**3. 模拟人类行为**

- 每抓取 10-15 个联系人，随机暂停 2-4 秒
- 滚动距离随机化（不是每次都滚动固定距离）

### 关键实现点

- 使用 `AccessibilityNodeInfo` 查找联系人列表项（`ListView` 或 `RecyclerView`）
- 识别详情页的微信号控件（通常是 `TextView`，文本格式为"微信号：xxx"）
- 使用 `performAction(ACTION_CLICK)` 和 `performGlobalAction(GLOBAL_ACTION_BACK)` 模拟操作
- 添加适当延迟避免过快导致抓取失败

## 微信内拦截逻辑

### 扩展 FocusModeEngine

当前逻辑是包名级别拦截，需要扩展为：
- 如果是微信（`com.tencent.mm`）→ 进入**微信专项检查**
- 其他应用 → 保持原有逻辑

### 微信专项检查流程

```kotlin
fun checkWechatAccess(event: AccessibilityEvent): Boolean {
    // 1. 检查是否在聊天界面
    val currentActivity = event.className?.toString() ?: ""
    if (!currentActivity.contains("ChattingUI")) {
        return false  // 不在聊天界面，拦截
    }

    // 2. 读取聊天标题栏的联系人名称
    val chatTitle = extractChatTitle(event.source) ?: return false

    // 3. 查询该名称对应的微信号
    val wechatId = DbSet.wechatContactDao.findIdByName(chatTitle) ?: return false

    // 4. 检查是否在白名单
    val whitelist = getEffectiveWechatWhitelist()
    return whitelist.contains(wechatId)
}

private fun extractChatTitle(rootNode: AccessibilityNodeInfo?): String? {
    // 查找标题栏控件（通常是顶部的 TextView）
    // 实现细节根据微信版本调整
}

private fun getEffectiveWechatWhitelist(): List<String> {
    // 优先使用手动会话的白名单
    val session = cachedSession
    if (session?.isValidNow() == true) {
        return session.getWechatWhitelist()
    }
    // 使用当前生效规则的白名单
    val activeRule = cachedRules.firstOrNull { it.isActiveNow() }
    return activeRule?.getWechatWhitelist() ?: emptyList()
}
```

### 拦截策略

- **立即拦截** - 只要不是白名单联系人的聊天界面，立即弹出全屏拦截
- **无缓冲时间** - 严格执行"只能和指定人聊天"的要求
- **群聊默认拦截** - 群聊标题无法匹配个人微信号，默认拦截

## UI 设计

### 规则编辑器扩展

**原有结构：**
```
白名单应用
  [应用列表选择器]
```

**扩展为：**
```
白名单应用
  [应用列表选择器]

微信联系人白名单
  [+ 添加微信联系人] 按钮
  [已选联系人列表]
    - 张老师 (wechat_id_123) [删除]
    - 李同学 (wechat_id_456) [删除]
```

### 微信联系人选择器

```kotlin
@Composable
fun WechatContactPicker(
    selectedIds: List<String>,
    onContactsSelected: (List<String>) -> Unit
) {
    Column {
        // 顶部：更新按钮
        Button(
            onClick = { triggerWechatContactFetch() }
        ) {
            Text("更新微信联系人")
        }

        // 搜索框
        TextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("搜索联系人") }
        )

        // 联系人列表（带复选框）
        LazyColumn {
            items(filteredContacts) { contact ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { toggleSelection(contact.wechatId) }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = contact.wechatId in selectedIds,
                        onCheckedChange = { toggleSelection(contact.wechatId) }
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(contact.displayName)  // 优先显示备注，否则昵称
                    if (contact.remark.isNotEmpty()) {
                        Text(
                            text = " (${contact.nickname})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
```

### 拦截页面扩展

**当前拦截页显示：**
```
专注当下
[剩余时间]
[白名单应用列表]
[退出按钮]
```

**扩展为：**
```
专注当下
[剩余时间]

白名单应用
  [应用图标] 应用名

微信联系人
  [头像] 张老师
  [头像] 李同学

[退出按钮]
```

### 跳转逻辑

```kotlin
fun openWechatChat(wechatId: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("weixin://dl/chat?$wechatId")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)

        // 跳转成功，关闭拦截页面
        stopSelf()
    } catch (e: Exception) {
        // 跳转失败，显示错误提示
        Toast.makeText(
            context,
            "跳转失败：${e.message ?: "微信 Scheme 不支持"}",
            Toast.LENGTH_LONG
        ).show()

        // 继续保持拦截状态，不关闭拦截页
    }
}
```

## 错误处理和边界情况

### 1. 微信版本兼容性问题

**问题：** 微信更新可能改变 Activity 名称或控件 ID

**处理：**
- 维护多个版本的控件特征配置
- 抓取失败时提示"当前微信版本不支持，请反馈"
- 拦截失败时降级为整个微信拦截

### 2. 联系人名称冲突

**问题：** 多个联系人可能有相同昵称/备注

**处理：**
- 使用微信号作为唯一标识
- 显示时如果有重复名称，附加微信号后缀
- 匹配时优先匹配备注名，其次昵称

### 3. 抓取中断

**问题：** 用户在抓取过程中退出微信或切换页面

**处理：**
- 检测页面变化，自动暂停抓取
- 保存已抓取的联系人
- Toast 提示"抓取已中断，已保存 X 个联系人"

### 4. 群聊处理

**问题：** 用户可能需要在群聊中讨论

**处理：**
- 群聊标题通常不是个人微信号，无法匹配
- 默认拦截所有群聊
- 未来可扩展支持群聊白名单（存储群 ID）

## 实现优先级

### P0 - 核心功能
1. 数据模型和数据库迁移
2. 无障碍服务抓取微信联系人
3. 微信内拦截逻辑
4. UI - 联系人选择器

### P1 - 用户体验
1. 拦截页面联系人跳转
2. 搜索和过滤功能
3. 抓取进度提示

### P2 - 优化和容错
1. 反检测机制优化
2. 版本兼容性处理
3. 错误提示和降级策略

## 技术风险

1. **微信版本更新** - 控件 ID 和 Activity 名称可能变化，需要持续维护
2. **Scheme 跳转限制** - 微信可能限制外部 Scheme 调用，需要测试验证
3. **抓取效率** - 大量联系人抓取耗时较长，需要优化用户体验
4. **隐私问题** - 联系人数据存储在本地数据库，需要确保安全性

## 后续扩展

1. **群聊白名单** - 支持添加特定群聊到白名单
2. **联系人分组** - 支持创建联系人组（如"工作"、"学习"）
3. **自动更新** - 定期自动更新联系人列表
4. **云同步** - 支持联系人白名单云端备份和同步
