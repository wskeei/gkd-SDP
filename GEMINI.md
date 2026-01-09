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
| 数据库 | Room (当前版本 18) |
| 导航 | compose-destinations |
| 依赖管理 | 单例模式 + CompositionLocal（无 Hilt） |
| 网络 | Ktor |
| 权限框架 | Shizuku |

### 核心目录结构

```
app/src/main/kotlin/li/songe/gkd/
├── a11y/           # 无障碍服务核心逻辑
│   ├── A11yRuleEngine.kt    # 规则引擎 (含全屏拦截检查)
│   ├── A11yState.kt         # 状态管理
│   └── A11yContext.kt       # 节点查询
├── data/           # 数据模型
│   ├── ActionLog.kt         # 触发日志与统计 (P2)
│   ├── FocusLock.kt         # 规则锁定实体 (P0)
│   ├── InterceptConfig.kt   # 全屏拦截配置 (P1)
│   ├── SubsConfig.kt        # 规则配置
│   └── RawSubscription.kt   # 订阅数据结构
├── db/             # 数据库
│   └── AppDb.kt             # Room 数据库定义 (v18)
├── service/        # 服务
│   ├── A11yService.kt       # 无障碍服务
│   ├── InterceptOverlayService.kt # 全屏拦截悬浮窗服务 (P1)
│   └── OverlayWindowService.kt  # 悬浮窗基类
├── shizuku/        # Shizuku 相关
├── store/          # 持久化存储
├── ui/             # UI 层
│   ├── component/           # 可复用组件
│   ├── home/                # 首页 Tab
│   ├── style/               # 主题样式
│   └── *Page.kt / *Vm.kt    # 各功能页面 (FocusLockPage, ActionLogPage等)
└── util/           # 工具类
```

---

## 环境配置与调试
**重要**：每次修改代码后，请务必执行重新编译以确保无错误。

### 使用 JDK 21 进行 Debug

JDK 21 路径：`D:\Download\tools\jdk_ms_21`

**命令行编译/调试：**
在执行 Gradle 命令前，需手动设置 `JAVA_HOME` 环境变量指向 JDK 21 目录。

```powershell

# PowerShell 示例
$env:JAVA_HOME = 'D:\Download\tools\jdk_ms_21'
./gradlew assembleDebug
```

**常见问题：**
- **JVM 版本错误** : 如果遇到 `Gradle requires JVM 17 or later`，请检查 `JAVA_HOME` 是否正确设置。
- **AIDL 路径问题** : 编译时若报 AIDL 相关错误，请确保 `app/src/main/aidl` 下的包名路径与 `AndroidManifest.xml` 及代码引用一致。
- **包名引用** : 项目已重构为 `li.songe.gkd.sdp`，请注意 `R` 类和其他资源的引用路径。

---

## 新功能开发计划：数字自律模式

### 背景痛点

> "我自己写的限制规则可以随时关闭，关闭了我就可以看视频号了"

参考 Cold Turkey Blocker 的设计理念，计划开发三个核心功能。

### 功能 1：规则锁定（Focus Lock）- P0 ✅ 已完成

**目标**：在设定时间内无法关闭已启用的规则，支持选择性锁定和时长扩展。

**UI 位置**：
`设置页` → `数字自律` (原规则锁定)

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
    // ...
}
```

**核心实现**：
1.  ✅ **选择性锁定**：在 `FocusLockPage` 中列出所有已启用的规则，用户可勾选特定规则进行锁定。
2.  ✅ **时长设置优化**：预设时长调整为 **8小时**、**1天**、**3天**，并支持 **自定义时长 (X天 X小时)**。
3.  **动态更新**：
    *   **锁定中查看**：即使锁定激活，也能查看规则列表。
    *   **锁定扩展**：支持在锁定期间**添加新规则**到锁定列表。
    *   **时间延长**：支持在锁定期间**追加锁定时长**。
4.  **强制拦截**：`FocusLockUtils` 提供锁定状态检查，`RuleGroupCard` 在用户尝试关闭规则时进行拦截。

### 功能 2：全屏拦截（Mindful Pause）- P1 ✅ 已完成

**目标**：规则触发时显示全屏“沉思”页面（"这真的重要吗？"），而非直接执行跳过。

**UI 位置**：
`设置页` → `数字自律` (与规则锁定整合在同一列表)

**数据模型**：
```kotlin
@Entity(tableName = "intercept_config")
data class InterceptConfig(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subsId: Long,
    val appId: String = "", // Added in v18
    val groupKey: Int,
    val enabled: Boolean,
    val cooldownSeconds: Int = 5,
    val message: String = "这真的重要吗？",
)
```

**核心实现**：
1.  ✅ **功能整合**：将“自律模式”开关整合进 `FocusLockPage` 的规则列表项中，不再分散于弹窗。
2.  ✅ **全屏服务**：`InterceptOverlayService` 使用 `TYPE_APPLICATION_OVERLAY` 创建全屏悬浮窗，遮挡应用内容。
3.  ✅ **拦截逻辑**：`A11yRuleEngine` 在执行规则动作前检查 `InterceptConfig`。若开启拦截且未处于“放行期”，则启动全屏服务并中止规则执行。
4.  ✅ **交互流程**：全屏页仅提供“算了（退出）”选项，并会在10秒无操作后自动退出（强制冷静），不再提供“我需要使用”的临时放行选项。
5.  ✅ **批量配置**：支持对整个应用或整个订阅下的规则进行统一开启/关闭全屏拦截。

### 功能 3：触发统计（Progress Tracker）- P2 ✅ 已完成

**目标**：可视化展示规则触发趋势，让用户看到进步。

**UI 位置**：
`首页` → `触发记录` → `统计图表` Tab

**核心实现**：
1.  ✅ **数据查询**：`ActionLogDao.queryDailyStats` 支持按天统计触发次数，并支持 `subsId` 和 `appId` 过滤。
2.  ✅ **图表集成**：引入 `Vico` 图表库 (v2.0.0-alpha.28) 绘制柱状图。
3.  ✅ **页面重构**：`ActionLogPage` 升级为双 Tab 布局（记录列表 / 统计图表）。
4.  ✅ **统计视图**：`ActionLogStatsView` 展示最近 14 天的触发趋势图及详细数据列表。

---

## UI 设计规范

### 必须遵循的规范

| 元素 | 规范 | 参考文件 |
|------|------|----------|
| 间距 | `itemHorizontalPadding = 16.dp`, `itemVerticalPadding = 12.dp` | `ui/style/Padding.kt` |
| 卡片颜色 | `surfaceCardColors` | `ui/style/Color.kt` |
| 开关组件 | `Switch` (新规范) / `TextSwitch` | `ui/FocusLockPage.kt` |
| 设置项 | `SettingItem` | `ui/component/SettingItem.kt` |
| 图标 | `PerfIcon` | `ui/component/PerfIcon.kt` |
| 顶部栏 | `PerfTopAppBar` | `ui/component/PerfTopAppBar.kt` |

---

## 更新日志

| 日期 | 内容 |
|------|------|
| 2026-01-08 | 📝 创建文档，完成数字自律功能头脑风暴 |
| 2026-01-08 | ✅ 完成 P0 功能：规则锁定（Focus Lock），修复编译错误并完善选择逻辑 |
| 2026-01-09 | 📝 更新文档，添加 JDK 21 Debug 指南，标记 P0 为完全完成 |
| 2026-01-09 | ✅ 完成 P2 功能：触发统计（Progress Tracker），集成 Vico 图表 |
| 2026-01-09 | ✅ 完成 P1 功能：全屏拦截（Mindful Pause），实现全屏悬浮窗服务 |
| 2026-01-09 | 🔄 优化整合：将 P0 与 P1 功能深度整合至“数字自律”页面，支持锁定状态下的规则添加与时长延长 |
| 2026-01-09 | 🎨 UI优化：数字自律页面卡片化重构，支持折叠收纳 |
| 2026-01-09 | 🔄 功能增强：全屏拦截支持批量配置（按应用/订阅），数据库升级至 v18 |