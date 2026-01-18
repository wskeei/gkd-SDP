# 应用直接拦截（App Blocker）功能设计

> 设计日期：2026-01-18
> 功能编号：P5

## 功能概述

### 定位

"应用直接拦截"是一个**黑名单模式**的应用限制功能，与现有"专注模式"（白名单模式）形成互补：

| 维度 | 专注模式 | 应用直接拦截 |
|------|---------|-------------|
| 模式 | 白名单（拦截所有，放行指定） | 黑名单（只拦截指定） |
| 场景 | 短期高强度专注 | 长期日常限制 |
| 粒度 | 按时间段统一生效 | 每个应用/组独立时间规则 |

### 核心概念

1. **拦截对象**：可以是单个应用，也可以是应用组
2. **时间规则**：每个拦截对象可配置多条时间规则（时间段 + 周几）
3. **规则叠加**：同一应用可同时属于应用组和单独配置，规则叠加生效
4. **冲突处理**：规则冲突时优先最新设定，并显示提示
5. **双层锁定**：支持全局锁定和单项锁定

## 数据模型设计

### 实体1：应用组 (AppGroup)

```kotlin
@Entity(tableName = "app_group")
data class AppGroup(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,              // 组名，如"短视频"
    val appIds: String,            // JSON: List<String> 包名列表
    val enabled: Boolean = true,
    val isLocked: Boolean = false,
    val lockEndTime: Long = 0,
    val orderIndex: Int = 0,
) {
    fun getAppList(): List<String> {
        return try {
            json.decodeFromString<List<String>>(appIds)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun withAppList(apps: List<String>): AppGroup {
        return copy(appIds = json.encodeToString(apps))
    }

    val isCurrentlyLocked: Boolean
        get() = isLocked && lockEndTime > System.currentTimeMillis()
}
```

### 实体2：拦截时间规则 (BlockTimeRule)

```kotlin
@Entity(tableName = "block_time_rule")
data class BlockTimeRule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val targetType: Int,           // 0=单个应用, 1=应用组
    val targetId: String,          // 应用包名 或 应用组ID
    val startTime: String,         // "22:00"
    val endTime: String,           // "08:00"
    val daysOfWeek: String,        // "1,2,3,4,5" (周一至周五)
    val enabled: Boolean = true,
    val isLocked: Boolean = false,
    val lockEndTime: Long = 0,
    val createdAt: Long,           // 创建时间，用于冲突时优先级判断
    val interceptMessage: String = "这真的重要吗？",
) {
    companion object {
        const val TARGET_TYPE_APP = 0
        const val TARGET_TYPE_GROUP = 1
    }

    fun getDaysOfWeekList(): List<Int> {
        return if (daysOfWeek.isBlank()) {
            emptyList()
        } else {
            daysOfWeek.split(",").mapNotNull { it.trim().toIntOrNull() }
        }
    }

    fun isActiveNow(): Boolean {
        if (!enabled) return false

        val now = java.time.LocalDateTime.now()
        val currentDayOfWeek = now.dayOfWeek.value
        val currentTime = now.toLocalTime()

        // 检查星期几
        if (currentDayOfWeek !in getDaysOfWeekList()) return false

        // 检查时间段
        val start = parseTime(startTime)
        val end = parseTime(endTime)

        return if (end.isAfter(start)) {
            currentTime.isAfter(start) && currentTime.isBefore(end) || currentTime == start
        } else {
            currentTime.isAfter(start) || currentTime.isBefore(end) || currentTime == start
        }
    }

    val isCurrentlyLocked: Boolean
        get() = isLocked && lockEndTime > System.currentTimeMillis()
}
```

### 实体3：全局锁定状态 (AppBlockerLock)

```kotlin
@Entity(tableName = "app_blocker_lock")
data class AppBlockerLock(
    @PrimaryKey val id: Long = 1,  // 单例
    val isLocked: Boolean = false,
    val lockEndTime: Long = 0,
) {
    val isCurrentlyLocked: Boolean
        get() = isLocked && lockEndTime > System.currentTimeMillis()
}
```

## 核心逻辑设计

### AppBlockerEngine 职责

1. **监听应用切换**：复用现有 `A11yFeat` 的 `topActivityFlow`
2. **判断是否拦截**：检查当前应用是否命中任何生效的拦截规则
3. **触发拦截界面**：启动 `AppBlockerOverlayService`

### 拦截判断流程

```
用户打开应用 App X
    ↓
检查 App X 是否有单独的 BlockTimeRule → 命中则记录
    ↓
检查 App X 所属的所有 AppGroup 是否有 BlockTimeRule → 命中则记录
    ↓
合并所有命中的规则，判断当前时间是否在任一规则的生效时间内
    ↓
是 → 显示拦截界面（使用最新规则的 interceptMessage）
否 → 放行
```

### 与专注模式的协同

统一在 `A11yFeat.useFocusMode()` 中处理：

```kotlin
fun onAppChanged(packageName: String) {
    // 1. 先检查专注模式
    if (FocusModeEngine.shouldBlock(packageName)) {
        showFocusOverlay(...)
        return
    }
    // 2. 再检查应用直接拦截
    if (AppBlockerEngine.shouldBlock(packageName)) {
        showBlockerOverlay(...)
        return
    }
}
```

### 规则冲突处理

**冲突判断与优先级**

```kotlin
fun getEffectiveRules(appId: String): List<BlockTimeRule> {
    // 1. 收集所有命中的规则
    val appRules = getAppRules(appId)
    val groupRules = getGroupRules(appId)
    val allRules = (appRules + groupRules).filter { it.isActiveNow() }

    // 2. 按创建时间排序（最新的在前）
    return allRules.sortedByDescending { it.createdAt }
}

fun shouldBlock(appId: String): Pair<Boolean, String?> {
    val rules = getEffectiveRules(appId)
    if (rules.isEmpty()) return false to null

    // 使用最新规则的拦截消息
    return true to rules.first().interceptMessage
}
```

## UI 设计

### 页面入口

`设置页` → `数字自律` → `应用拦截`（新增卡片，与专注模式、网址拦截并列）

### AppBlockerPage 主页面结构

```
┌─────────────────────────────────────┐
│ ← 应用拦截                    [🔒锁定] │  ← 全局锁定按钮
├─────────────────────────────────────┤
│ ┌─────────────────────────────────┐ │
│ │ 📱 单独应用 (3)            [+添加] │ │
│ ├─────────────────────────────────┤ │
│ │ 抖音        22:00-08:00 每天  🔒 │ │
│ │ 酷安        全天候         ▶    │ │
│ │ 微博        12:00-14:00 工作日  │ │
│ └─────────────────────────────────┘ │
│                                     │
│ ┌─────────────────────────────────┐ │
│ │ 📁 应用组 (2)              [+添加] │ │
│ ├─────────────────────────────────┤ │
│ │ 短视频 (5个应用)  22:00-08:00 🔒 │ │
│ │ 社交软件 (3个应用) 工作时间     │ │
│ └─────────────────────────────────┘ │
└─────────────────────────────────────┘
```

### 预设时间模板

| 模板名 | 时间段 | 周几 |
|--------|--------|------|
| 工作日 | 09:00-18:00 | 周一至周五 |
| 周末 | 00:00-23:59 | 周六日 |
| 每晚 | 22:00-08:00 | 每天 |
| 午休 | 12:00-14:00 | 周一至周五 |
| 全天候 | 00:00-23:59 | 每天 |
| 自定义 | 用户输入 | 用户选择 |

### 拦截界面设计

复用思路，创建简化版 `AppBlockerOverlayService`：

```
┌─────────────────────────────────────┐
│                                     │
│                                     │
│         这真的重要吗？               │  ← 拦截消息
│                                     │
│                                     │
│    ┌─────────────────────────────┐ │
│    │  算了（退出）10s          │     │  ← 倒计时按钮
│    └─────────────────────────────┘ │
│                                     │
└─────────────────────────────────────┘
```

**与专注模式拦截界面的差异**

| 元素 | 专注模式拦截 | 应用拦截 |
|------|-------------|---------|
| 提示消息 | 显示会话/规则的消息 | 显示规则的消息 |
| 主按钮 | "算了（退出）" | "算了（退出）" |
| 次要按钮 | "打开白名单应用" | **无** |
| 自动退出 | 15秒 | **10秒** |
| 剩余时间显示 | 显示专注模式剩余时间 | **不显示** |

## 锁定功能设计

### 双层锁定机制

| 锁定类型 | 作用范围 | 锁定后限制 |
|---------|---------|-----------|
| 全局锁定 | 整个应用拦截功能 | 不能删除任何应用/组，不能修改任何时间规则，不能关闭功能；**可以**新增应用/组/规则 |
| 单项锁定 | 特定应用或应用组 | 该项不能删除、不能修改时间规则；其他项不受影响 |

### 锁定状态判断逻辑

```kotlin
fun canDeleteApp(appId: String): Boolean {
    // 全局锁定时不能删除
    if (globalLock.isCurrentlyLocked) return false
    // 该应用有单项锁定时不能删除
    if (getAppLock(appId)?.isCurrentlyLocked == true) return false
    // 该应用所属的任一应用组被锁定时不能删除
    if (getGroupsContaining(appId).any { it.isCurrentlyLocked }) return false
    return true
}

fun canEditTimeRule(rule: BlockTimeRule): Boolean {
    // 全局锁定时不能编辑
    if (globalLock.isCurrentlyLocked) return false
    // 该规则本身被锁定时不能编辑
    if (rule.isCurrentlyLocked) return false
    return true
}
```

### 锁定时长选项

复用现有设计：8小时、1天、3天、自定义（X天X小时）

### 锁定提示文案

- 尝试删除被锁定项：`"该项已锁定，无法删除（剩余 X 小时）"`
- 尝试修改被锁定规则：`"该规则已锁定，无法修改（剩余 X 小时）"`

## 冲突处理设计

### 冲突提示 UI

在应用详情页显示：

```
┌─────────────────────────────────────┐
│ 抖音                                 │
├─────────────────────────────────────┤
│ ⚠️ 检测到规则冲突                    │
│ 该应用同时被以下规则拦截：            │
│ • 单独规则：22:00-08:00 每天         │
│ • 短视频组：12:00-14:00 工作日 (最新) │
│                                     │
│ 当前生效：短视频组规则               │
│ [查看详情] [解除冲突]                │
└─────────────────────────────────────┘
```

### 解除冲突选项

用户点击"解除冲突"后：
- 选项1：保留单独规则，从应用组中移除
- 选项2：保留应用组规则，删除单独规则
- 选项3：保留两者（规则叠加，按最新生效）

## 数据库设计

### 版本升级

当前版本：20 → 新版本：21

### DAO 接口

```kotlin
@Dao
interface AppGroupDao {
    @Query("SELECT * FROM app_group ORDER BY order_index ASC")
    fun queryAll(): Flow<List<AppGroup>>

    @Query("SELECT * FROM app_group WHERE enabled = 1")
    fun queryEnabled(): Flow<List<AppGroup>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(group: AppGroup): Long

    @Update
    suspend fun update(group: AppGroup)

    @Delete
    suspend fun delete(group: AppGroup)

    @Query("SELECT * FROM app_group WHERE id = :id")
    suspend fun getById(id: Long): AppGroup?
}

@Dao
interface BlockTimeRuleDao {
    @Query("SELECT * FROM block_time_rule WHERE target_type = :type AND target_id = :id")
    fun queryByTarget(type: Int, id: String): Flow<List<BlockTimeRule>>

    @Query("SELECT * FROM block_time_rule WHERE enabled = 1")
    fun queryEnabled(): Flow<List<BlockTimeRule>>

    @Query("SELECT * FROM block_time_rule ORDER BY created_at DESC")
    fun queryAll(): Flow<List<BlockTimeRule>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rule: BlockTimeRule): Long

    @Update
    suspend fun update(rule: BlockTimeRule)

    @Delete
    suspend fun delete(rule: BlockTimeRule)
}

@Dao
interface AppBlockerLockDao {
    @Query("SELECT * FROM app_blocker_lock WHERE id = 1")
    fun getLock(): Flow<AppBlockerLock?>

    @Query("SELECT * FROM app_blocker_lock WHERE id = 1")
    suspend fun getLockNow(): AppBlockerLock?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(lock: AppBlockerLock)
}
```

## 实现步骤

### Phase 1: 核心数据层
1. 创建数据模型：`AppGroup.kt`, `BlockTimeRule.kt`, `AppBlockerLock.kt`
2. 更新 `AppDb.kt` 至版本 21，添加 DAO
3. 编译验证数据库迁移

### Phase 2: 引擎逻辑
4. 创建 `AppBlockerEngine.kt`
   - 规则匹配逻辑
   - 冲突检测与优先级
   - 与专注模式协同
5. 集成到 `A11yFeat.kt`
6. 创建 `AppBlockerOverlayService.kt`（简化版拦截界面）

### Phase 3: UI 层
7. 创建 `AppBlockerPage.kt` 和 `AppBlockerVm.kt`
   - 应用列表管理
   - 应用组管理
   - 时间规则编辑器（含预设模板）
8. 创建锁定相关 UI 组件
9. 添加冲突提示 UI

### Phase 4: 入口与整合
10. 在 `FocusLockPage.kt` 添加"应用拦截"入口卡片
11. 注册 `AppBlockerOverlayService` 到 `AndroidManifest.xml`
12. 编译验证

## 预估工作量

- 数据层：1-2小时
- 引擎层：2-3小时
- UI层：3-4小时
- 整合测试：1小时

总计：约 7-10 小时开发时间

## 技术要点

1. **时间判断**：复用 `FocusRule` 的时间判断逻辑
2. **规则优先级**：使用 `createdAt` 字段排序
3. **锁定检查**：在所有修改操作前检查锁定状态
4. **冲突检测**：实时计算，不存储冲突状态
5. **界面复用**：参考 `FocusOverlayService` 创建简化版

## 注意事项

1. **性能优化**：规则匹配使用缓存，避免频繁数据库查询
2. **用户体验**：冲突提示要清晰，不要让用户困惑
3. **锁定逻辑**：确保锁定检查覆盖所有修改入口
4. **测试场景**：重点测试规则叠加、冲突处理、锁定限制
