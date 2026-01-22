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
| 数据库 | Room (当前版本 21) |
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
│   ├── A11yContext.kt       # 节点查询
│   ├── AppBlockerEngine.kt  # 应用拦截引擎 (P5)
│   ├── UrlBlockerEngine.kt  # 网址拦截引擎 (P3)
│   └── FocusModeEngine.kt   # 专注模式引擎 (P4)
├── data/           # 数据模型
│   ├── ActionLog.kt         # 触发日志与统计 (P2)
│   ├── AppBlockerLock.kt    # 应用拦截全局锁定 (P5)
│   ├── AppGroup.kt          # 应用组实体 (P5)
│   ├── BlockTimeRule.kt     # 时间拦截规则 (P5)
│   ├── FocusLock.kt         # 规则锁定实体 (P0)
│   ├── FocusRule.kt         # 专注模式定时规则 (P4)
│   ├── FocusSession.kt      # 专注模式会话状态 (P4)
│   ├── InterceptConfig.kt   # 全屏拦截配置 (P1)
│   ├── UrlBlockRule.kt      # 网址拦截规则 (P3)
│   ├── BrowserConfig.kt     # 浏览器配置 (P3)
│   ├── SubsConfig.kt        # 规则配置
│   └── RawSubscription.kt   # 订阅数据结构
├── db/             # 数据库
│   └── AppDb.kt             # Room 数据库定义 (v21)
├── service/        # 服务
│   ├── A11yService.kt       # 无障碍服务
│   ├── AppBlockerOverlayService.kt # 应用拦截悬浮窗服务 (P5)
│   ├── InterceptOverlayService.kt # 全屏拦截悬浮窗服务 (P1)
│   ├── FocusOverlayService.kt     # 专注模式拦截服务 (P4)
│   └── OverlayWindowService.kt  # 悬浮窗基类
├── shizuku/        # Shizuku 相关
├── store/          # 持久化存储
├── ui/             # UI 层
│   ├── component/           # 可复用组件
│   ├── home/                # 首页 Tab
│   ├── style/               # 主题样式
│   └── *Page.kt / *Vm.kt    # 各功能页面 (FocusLockPage, AppBlockerPage等)
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
- **JVM 版本错误**：如果遇到 `Gradle requires JVM 17 or later`，请检查 `JAVA_HOME` 是否正确设置。
- **AIDL 路径问题**：编译时若报 AIDL 相关错误，请确保 `app/src/main/aidl` 下的包名路径与 `AndroidManifest.xml` 及代码引用一致。
- **包名引用**：项目已重构为 `li.songe.gkd.sdp`，请注意 `R` 类和其他资源的引用路径。

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
4.  ✅ **交互流程**：全屏页提供“我需要使用”（带倒计时）和“算了（退出）”选项。点击“使用”后进入短暂放行期。

### 功能 3：触发统计（Progress Tracker）- P2 ✅ 已完成

**目标**：可视化展示规则触发趋势，让用户看到进步。

**UI 位置**：
`首页` → `触发记录` → `统计图表` Tab

**核心实现**：
1.  ✅ **数据查询**：`ActionLogDao.queryDailyStats` 支持按天统计触发次数，并支持 `subsId` 和 `appId` 过滤。
2.  ✅ **图表集成**：引入 `Vico` 图表库 (v2.0.0-alpha.28) 绘制柱状图。
3.  ✅ **页面重构**：`ActionLogPage` 升级为双 Tab 布局（记录列表 / 统计图表）。
4.  ✅ **统计视图**：`ActionLogStatsView` 展示最近 14 天的触发趋势图及详细数据列表。

### 功能 4：网址拦截（URL Blocker）- P3 ✅ 已完成

**目标**：检测浏览器地址栏 URL，匹配规则时跳转到安全页面并显示全屏拦截。

**UI 位置**：
`设置页` → `数字自律` → `网址拦截`

**数据模型**：
```kotlin
@Entity(tableName = "url_block_rule")
data class UrlBlockRule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val pattern: String,           // 匹配模式
    val matchType: Int,            // 0=域名, 1=前缀, 2=正则
    val enabled: Boolean = true,
    val name: String,
    val redirectUrl: String,       // 跳转目标，默认 google.com
    val showIntercept: Boolean,    // 是否显示全屏拦截
    val interceptMessage: String,
)

@Entity(tableName = "browser_config")
data class BrowserConfig(
    @PrimaryKey val packageName: String,
    val name: String,
    val urlBarId: String,          // 地址栏控件 ID
    val enabled: Boolean = true,
    val isBuiltin: Boolean = false,
)
```

**核心实现**：
1.  ✅ **URL 检测**：`UrlBlockerEngine` 监听浏览器无障碍事件，读取地址栏内容。
2.  ✅ **规则匹配**：支持域名匹配、前缀匹配、正则匹配三种模式。
3.  ✅ **拦截流程**：匹配成功后先跳转到安全页面（默认 google.com），然后显示全屏拦截。
4.  ✅ **浏览器支持**：内置 Chrome、Edge、Firefox 等主流浏览器配置，支持用户自定义添加。
5.  ✅ **锁定功能**：支持锁定网址拦截功能，锁定后无法关闭规则或删除浏览器。

### 功能 5：专注模式（Focus Mode）- P4 ✅ 已完成

**目标**：在指定时间段内拦截所有非白名单应用（包括桌面），强制用户专注。

**UI 位置**：
`设置页` → `数字自律` → `专注模式`

**数据模型**：
```kotlin
@Entity(tableName = "focus_rule")
data class FocusRule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,              // 规则名称
    val startTime: String,         // 开始时间 "22:30"
    val endTime: String,           // 结束时间 "23:00"
    val daysOfWeek: String,        // 星期几 "1,2,3,4,5"
    val enabled: Boolean = true,
    val whitelistApps: String,     // JSON: List<String> 白名单包名
    val interceptMessage: String,
    val isLocked: Boolean = false,
    val lockEndTime: Long = 0,
)

@Entity(tableName = "focus_session")
data class FocusSession(
    @PrimaryKey val id: Long = 1,  // 单例记录
    val isActive: Boolean = false,
    val ruleId: Long? = null,
    val startTime: Long = 0,
    val endTime: Long = 0,
    val whitelistApps: String,
    val interceptMessage: String,
    val isManual: Boolean = false,
    val isLocked: Boolean = false,
    val lockEndTime: Long = 0,
)
```

**核心实现**：
1.  ✅ **触发方式**：支持手动开启（选择时长）和定时规则（设置时间段+星期）。
2.  ✅ **应用拦截**：`FocusModeEngine` 监听应用切换，非白名单应用触发全屏拦截。
3.  ✅ **桌面拦截**：Launcher 也会被拦截，真正实现"强制专注"。
4.  ✅ **白名单管理**：每个规则独立白名单，锁定时只能移除白名单应用不能添加。
5.  ✅ **全屏界面**：`FocusOverlayService` 显示拦截提示，提供"退出"和"打开白名单应用"按钮。
6.  ✅ **锁定功能**：支持锁定规则，锁定后无法关闭或删除规则。

### 功能 6：应用拦截（App Blocker）- P5 ✅ 已完成

**目标**：基于时间段拦截特定应用或应用组，支持黑名单模式的应用管理。

**UI 位置**：
`设置页` → `数字自律` → `应用拦截`

**数据模型**：
```kotlin
@Entity(tableName = "app_group")
data class AppGroup(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,              // 应用组名称
    val appIds: String,            // JSON: List<String> 应用包名列表
    val enabled: Boolean = true,
    val isLocked: Boolean = false,
    val lockEndTime: Long = 0,
    val orderIndex: Int = 0,
)

@Entity(tableName = "block_time_rule")
data class BlockTimeRule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val targetType: Int,           // 0=单独应用, 1=应用组
    val targetId: String,          // 应用包名或应用组ID
    val startTime: String,         // "22:00"
    val endTime: String,           // "08:00"
    val daysOfWeek: String,        // "1,2,3,4,5"
    val enabled: Boolean = true,
    val createdAt: Long,           // 用于冲突检测（优先最新）
    val interceptMessage: String,
    val isLocked: Boolean = false,
    val lockEndTime: Long = 0,
) {
    companion object {
        val TEMPLATES = listOf(
            TimeTemplate("工作日", "09:00", "18:00", "1,2,3,4,5", "周一至周五 9:00-18:00"),
            TimeTemplate("周末", "00:00", "23:59", "6,7", "周六日全天"),
            TimeTemplate("夜间", "22:00", "08:00", "1,2,3,4,5,6,7", "每天 22:00-次日 08:00"),
            // ...
        )
    }
}

@Entity(tableName = "app_blocker_lock")
data class AppBlockerLock(
    @PrimaryKey val id: Long = 1,  // 单例记录
    val isLocked: Boolean = false,
    val lockEndTime: Long = 0,
)
```

**核心实现**：
1.  ✅ **应用组管理**：创建应用组批量管理多个应用，支持搜索和系统应用过滤。
2.  ✅ **时间规则**：为应用/应用组配置多条时间规则，支持时间段和星期选择。
3.  ✅ **预设模板**：提供工作日、周末、夜间等6种预设模板，快速配置常用时间段。
4.  ✅ **冲突检测**：同一应用多条规则时，优先使用最新创建的规则（按 createdAt 排序）。
5.  ✅ **全屏拦截**：`AppBlockerEngine` 检测应用切换，匹配规则后触发 `AppBlockerOverlayService` 全屏拦截（10秒自动退出）。
6.  ✅ **三层锁定**：
    *   **全局锁定**：锁定后无法删除或修改任何应用/组/规则，但可新增。
    *   **应用组锁定**：锁定后无法关闭、删除或修改此应用组。
    *   **规则锁定**：锁定后无法关闭、删除或修改此规则。
7.  ✅ **搜索功能**：应用选择器支持实时搜索应用名称或包名。
8.  ✅ **系统应用过滤**：默认隐藏系统应用，可通过复选框展开查看。
9.  ✅ **规则可见性**：应用组卡片展示所有关联的时间规则，便于查看和管理。
10. ✅ **优化锁定UI**：参考网址拦截页面设计，使用圆角边框按钮和 Switch，提升视觉体验。

**与专注模式的区别**：
- **专注模式（白名单）**：默认拦截所有应用，仅允许白名单应用使用。适合需要高度专注的场景（如学习、工作）。
- **应用拦截（黑名单）**：默认允许所有应用，仅拦截黑名单应用/组。适合限制特定娱乐应用的使用时间。

### 功能 9：微信联系人白名单（WeChat Contact Whitelist）- P6 ✅ 已完成

**目标**：在专注模式中支持微信联系人级别的白名单控制，允许用户只与指定联系人聊天，而拦截微信的其他功能（朋友圈、视频号、发现页等）。

**UI 位置**：
`专注模式` → `添加规则/立即开始专注` → `微信联系人白名单`

**数据模型**：
```kotlin
@Entity(tableName = "wechat_contact")
data class WechatContact(
    @PrimaryKey val wechatId: String,  // 微信号（主键）
    val nickname: String,              // 昵称
    val remark: String = "",           // 备注名
    val avatarUrl: String = "",        // 头像（可选）
    val lastUpdated: Long = System.currentTimeMillis()
) {
    val displayName: String get() = remark.ifEmpty { nickname }
}

// FocusRule 和 FocusSession 扩展字段
@ColumnInfo(name = "wechat_whitelist", defaultValue = "[]")
val wechatWhitelist: String = "[]"  // JSON: List<String> 微信号列表
```

**核心实现**：
1.  ✅ **无障碍抓取**：`WechatContactFetcher` 自动抓取微信通讯录联系人（微信号、昵称、备注名）。
2.  ✅ **反检测机制**：随机点击偏移（±20%）、随机延迟（500-1500ms）、模拟人类行为（每10-15个联系人暂停2-4秒）。
3.  ✅ **微信专项检查**：`FocusModeEngine.checkWechatAccess()` 检测 ChattingUI Activity + 聊天标题匹配。
4.  ✅ **精准拦截**：只允许与白名单联系人聊天，其他微信界面（朋友圈、视频号、发现页）全部拦截。
5.  ✅ **快速跳转**：拦截页面显示白名单联系人列表，点击后通过微信 Scheme（`weixin://dl/chat?wechatId`）直接跳转到聊天界面。
6.  ✅ **UI 集成**：
    *   规则编辑器和快速启动 Sheet 中添加"微信联系人白名单"部分
    *   `WechatContactPickerDialog` 支持搜索、多选、实时更新
    *   "更新微信联系人"按钮显示抓取进度
7.  ✅ **数据库迁移**：v22 → v23，添加 `wechat_contact` 表和 `wechatWhitelist` 字段。
8.  ✅ **错误处理**：跳转失败时显示错误提示并保持拦截状态，不关闭拦截页。

**技术亮点**：
- 使用 `runBlocking` 在无障碍事件处理中同步查询数据库
- 通过 Activity 名称（ChattingUI）+ 聊天标题匹配实现精准拦截
- 微信 Scheme 跳转实现一键打开指定联系人聊天
- 完整的反检测机制确保抓取过程不被微信识别

### 功能 10：软件安装监控（App Install Monitor）- P7 ✅ 已完成

**目标**：监控并记录特定应用的安装与卸载行为，通过热力图直观展示应用存在时间，辅助用户反思数字习惯。

**UI 位置**：
`数字自律` → `软件安装监测` (新增卡片入口)

**数据模型**：
```kotlin
@Entity(tableName = "app_install_log")
data class AppInstallLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val appName: String,
    val action: String,           // "install" / "uninstall"
    val timestamp: Long,          // 事件时间戳
    val date: String              // "yyyy-MM-dd"
)

@Entity(tableName = "monitored_app")
data class MonitoredApp(
    @PrimaryKey val packageName: String,
    val displayName: String,
    val enabled: Boolean = true,
    val isCurrentlyInstalled: Boolean = false // 仅用于 UI 状态缓存
) {
    companion object {
        val DEFAULT_APPS = listOf(...) // 预置抖音、B站、知乎等
    }
}
```

**核心实现**：
1.  ✅ **自动记录**：`AppInstallReceiver` 监听 `PACKAGE_ADDED` 和 `PACKAGE_REMOVED` 广播，自动写入日志。
2.  ✅ **历史回溯**：添加监控时若应用已安装，利用 `PackageManager.firstInstallTime` 自动补录安装记录，确保热力图即刻可用。
3.  ✅ **热力图算法**：计算每日"存在应用数"（当日安装且未卸载）。即使无数据也显示网格，颜色深度反映当日并存应用量。
4.  ✅ **日详情增强**：点击热力图格子展示当日存在应用列表，包含**安装时长**及**卸载时间**（如"共存活 3天"）。
5.  ✅ **交互优化**：`AppInstallMonitorPage` 提供精美的热力图组件。添加监控时支持**从本机已安装列表选择**（带图标、搜索、系统应用过滤），告别手动输入包名。
6.  ✅ **数据导出**：支持导出 CSV 格式的完整安装/卸载记录，方便用户备份分析。

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
| 2026-01-09 | 🔄 优化整合：将 P0 与 P1 功能深度整合至"数字自律"页面，支持锁定状态下的规则添加与时长延长 |
| 2026-01-18 | ✅ 完成 P3 功能：网址拦截（URL Blocker），支持域名/前缀/正则匹配，内置主流浏览器配置 |
| 2026-01-18 | ✅ 完成 P4 功能：专注模式（Focus Mode），支持手动/定时触发，白名单管理，桌面拦截 |
| 2026-01-19 | ✅ 完成 P5 功能：应用拦截（App Blocker），支持应用组、时间规则、模板、三层锁定 |
| 2026-01-19 | 🎨 UI优化：应用选择器增加搜索和系统应用过滤，锁定UI参考网址拦截设计，应用组卡片显示规则详情 |
| 2026-01-19 | ✅ 完成 P6 功能：微信联系人白名单（WeChat Contact Whitelist），支持联系人级别的精准拦截 |
| 2026-01-19 | 🔧 实现细节：无障碍抓取微信联系人（反检测）、微信专项检查（Activity+标题匹配）、Scheme跳转 |
| 2026-01-19 | 🎨 UI完善：规则编辑器和快速启动中添加微信联系人白名单入口，联系人选择器支持搜索和实时更新 |
| 2026-01-22 | ✅ 完成 P7 功能：软件安装监控（App Install Monitor），包含热力图、日详情、历史回溯、CSV导出 |
| 2026-01-22 | 🎨 交互升级：添加监控支持"列表选择"模式，优化详情页展示卸载时间与存活天数 |
