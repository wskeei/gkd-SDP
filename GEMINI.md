# GKD-SDP 项目开发指南

> 本文档用于 AI 助手快速了解项目背景和开发规范

## 项目概述

**GKD-SDP** 是基于 [GKD](https://github.com/gkd-kit/gkd) 的二次开发项目，目标是将 GKD 从"自动点击工具"升级为"数字自律助手"。

### 技术栈

| 技术 | 说明 |
|------|------|
| 语言 | Kotlin |
| UI 框架 | Jetpack Compose + Material 3 |
| 架构 | MVVM (Page + ViewModel) |
| 数据库 | Room (当前版本 15) |
| 导航 | compose-destinations |
| 依赖管理 | 单例模式 + CompositionLocal（无 Hilt） |
| 网络 | Ktor |
| 权限框架 | Shizuku |

### 核心目录结构

```
app/src/main/kotlin/li/songe/gkd/
├── a11y/           # 无障碍服务核心逻辑
│   ├── A11yRuleEngine.kt    # 规则引擎
│   ├── A11yState.kt         # 状态管理
│   └── A11yContext.kt       # 节点查询
├── data/           # 数据模型
│   ├── ActionLog.kt         # 触发日志
│   ├── SubsConfig.kt        # 规则配置
│   └── RawSubscription.kt   # 订阅数据结构
├── db/             # 数据库
│   └── AppDb.kt             # Room 数据库定义
├── service/        # 服务
│   ├── A11yService.kt       # 无障碍服务
│   └── OverlayWindowService.kt  # 悬浮窗基类
├── shizuku/        # Shizuku 相关
├── store/          # 持久化存储
├── ui/             # UI 层
│   ├── component/           # 可复用组件
│   ├── home/                # 首页 Tab
│   ├── style/               # 主题样式
│   └── *Page.kt / *Vm.kt    # 各功能页面
└── util/           # 工具类
```

---

## 环境配置与调试
请注意！在每一次修改后，都需要重新编译项目！！确保不出现错误！！

### 使用 JDK 21 进行 Debug

jdk21地址：D:\Download\tools\jdk_ms_21

**命令行编译/调试：**
在执行 Gradle 命令前，需手动设置 `JAVA_HOME` 环境变量指向 JDK 21 目录。

```powershell

# PowerShell 示例
$env:JAVA_HOME = 'D:\Download\tools\jdk_ms_21'
./gradlew assembleDebug
```

**常见问题：**
- 如果遇到 `Gradle requires JVM 17 or later` 错误，请检查 `gradle.properties` 或当前终端的 `JAVA_HOME`。
- 编译时如果遇到 AIDL 相关错误，请确保 `app/src/main/aidl` 路径与包名一致。

---

## 新功能开发计划：数字自律模式

### 背景痛点

> "我自己写的限制规则可以随时关闭，关闭了我就可以看视频号了"

参考 Cold Turkey Blocker 的设计理念，计划开发三个核心功能。

### 功能 1：规则锁定（Focus Lock）- P0 ✅ 已完成

**目标**：在设定时间内无法关闭已启用的规则

**实现状态**：已完成。支持在 `FocusLockPage` 中选择特定已启用的规则组进行锁定。

**数据模型**：
```kotlin
@Entity(tableName = "focus_lock")
data class FocusLock(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTime: Long,           // 锁定开始时间
    val endTime: Long,             // 锁定结束时间
    val lockedRules: String,       // JSON: List<LockedRule>
) {
    @Serializable
    data class LockedRule(
        val subsId: Long,
        val groupKey: Int,
        val appId: String? = null, // null = 全局规则
    )

    val isActive: Boolean get() = System.currentTimeMillis() < endTime
    val remainingTime: Long get() = (endTime - System.currentTimeMillis()).coerceAtLeast(0)
}
```

**UI 位置**：
```
设置页 → 规则锁定 → FocusLockPage
```


**关键实现**：
1. ✅ `FocusLockUtils` 提供锁定检查逻辑，并在 `RuleGroupCard` 的开关回调中拦截关闭操作。
2. ✅ `FocusLockVm` 自动筛选当前已启用的规则组供用户选择。
3. ✅ 修复了大量包名引用错误（`li.songe.gkd.sdp.sdp.R` -> `li.songe.gkd.sdp.R`）和 AIDL 路径问题。

### 功能 2：全屏拦截（Mindful Pause）- P1 ⏳ 待开始

**目标**：规则触发时显示"这真的重要吗？"全屏页面

```kotlin
@Entity(tableName = "intercept_config")
data class InterceptConfig(
    @PrimaryKey val id: Long,
    val subsId: Long,
    val groupKey: Int,
    val enabled: Boolean,
    val cooldownSeconds: Int = 5,
    val message: String = "这真的重要吗？",
)
```

**实现方案**：使用 `TYPE_ACCESSIBILITY_OVERLAY` 创建全屏悬浮窗。


**UI 位置**：
```
设置页 → 规则锁定 → "启用拦截模式"
```

**关键实现**：
1. 新增 `InterceptConfig` 实体和 DAO
2. 新增 `InterceptOverlayService` 全屏悬浮窗服务
3. 修改 `A11yRuleEngine` 在触发时检查拦截配置
4. 拦截页面：提示语 + 退出按钮 + 继续使用按钮（带冷静期）


### 功能 3：触发统计（Progress Tracker）- P2

**目标**：可视化展示规则触发趋势，让用户看到进步

**数据查询**（基于现有 ActionLog）：
```kotlin
// 按天统计
@Query("""
    SELECT date(ctime/1000, 'unixepoch', 'localtime') as date,
           COUNT(*) as count
    FROM action_log
    WHERE ctime >= :startTime
    GROUP BY date
    ORDER BY date DESC
""")
fun queryDailyStats(startTime: Long): Flow<List<DailyStat>>
```

**UI 位置**：
```
首页 → 触发记录 → 统计图表 Tab
```

**图表库**：推荐 Vico (https://github.com/patrykandpatrick/vico)
---

## UI 设计规范

### 必须遵循的规范

| 元素 | 规范 | 参考文件 |
|------|------|----------|
| 间距 | `itemHorizontalPadding = 16.dp`, `itemVerticalPadding = 12.dp` | `ui/style/Padding.kt` |
| 卡片颜色 | `surfaceCardColors` | `ui/style/Color.kt` |
| 开关组件 | `TextSwitch` | `ui/component/TextSwitch.kt` |
| 设置项 | `SettingItem` | `ui/component/SettingItem.kt` |
| 图标 | `PerfIcon` | `ui/component/PerfIcon.kt` |
| 顶部栏 | `PerfTopAppBar` | `ui/component/PerfTopAppBar.kt` |

---

## 更新日志

| 日期 | 内容 |
|------|------|
| 2026-01-08 | 创建文档，完成数字自律功能头脑风暴 |
| 2026-01-08 | ✅ 完成 P0 功能：规则锁定（Focus Lock），修复编译错误并完善选择逻辑 |
| 2026-01-09 | 📝 更新文档，添加 JDK 21 Debug 指南，标记 P0 为完全完成 |