# 无障碍权限守护防误关与自动恢复 Implementation Plan

> **For Codex:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 将“无障碍权限守护”迁入“数字自律”，允许在无障碍已经关闭时开启守护；检测到关闭后立即显示下一次提醒倒计时，并继续执行既有 `15 → 10 → 5 → 3 → 2 → 1` 分钟提醒与第 36 分钟全屏提醒；同时把守护纳入自动重开、锁定保护，并消除首页“服务状态”误触关闭无障碍的路径。

**Architecture:** 保留现有 `StatusService + AccessibilityGuardCoordinator` 单协调器架构，把散落在 `ControlPage.kt` 的启停事务抽成进程级 `AccessibilityGuardController`。新增纯策略层处理启用、锁定、关闭限额和自动恢复决策；用一个只读 Room DAO 汇总现有数字自律锁；用一条稳定的系统 chronometer 通知表示“距离下一次提醒”，六条既有高优先级通知继续负责阶段性顶部提醒。首页无障碍开关改为“开启/修复”单向命令，关闭手势只显示说明并跳转 Android 系统无障碍设置。

**Tech Stack:** Kotlin、Kotlin Coroutines/Flow、Jetpack Compose Material3、Room、Android AccessibilityService、Foreground Service、NotificationCompat chronometer、JUnit4、Gradle、GitHub CLI、GitHub Actions（Ubuntu + Zulu JDK 21）。

---

## 1. 已确认的产品规则

### 1.1 无障碍关闭后的时间线

第一条状态通知不再等待 15 分钟，而是在守护首次确认无障碍组件关闭时立即提交；它显示距离下一次阶段提醒的系统倒计时。六次阶段提醒的业务时间线不变：

| 时刻 | 稳定状态通知 | 阶段提醒/强制动作 |
|---|---|---|
| `T+0` | “无障碍权限已关闭 · 距离第 1 次提醒”，倒计时到 `T+15` | 初次提交，可触发一次顶部提醒 |
| `T+15` | 更新为倒计时到 `T+25` | 第 1 次高优先级提醒 |
| `T+25` | 更新为倒计时到 `T+30` | 第 2 次高优先级提醒 |
| `T+30` | 更新为倒计时到 `T+33` | 第 3 次高优先级提醒 |
| `T+33` | 更新为倒计时到 `T+35` | 第 4 次高优先级提醒 |
| `T+35` | 更新为倒计时到 `T+36` | 第 5 次高优先级提醒 |
| `T+36` | 更新为“最后提醒已发送，请立即开启无障碍”，不再显示倒计时 | 第 6 次提醒；复查仍关闭后进入既有全屏悬浮窗流程 |

这里的 `15、10、5、3、2、1` 仍然是相邻提醒之间的等待时间，累计检查点仍为 `[15, 25, 30, 33, 35, 36]` 分钟。不得重新实现为六段互相串联的 `delay()`。

### 1.2 启用条件

- 严格守护仍只在 `META.isGkdChannel` 可用。
- 仍要求当前选择“无障碍模式”；Automation/Shizuku 模式不会被守护误报。
- **不再要求本应用的无障碍组件当前已开启或服务当前正在运行。** 用户可在权限已经关闭时开启守护，成功后协调器立即建立 `T+0` 会话和倒计时通知。
- 开启时仍需取得通知、特殊用途前台服务和悬浮窗权限，并成功启动 `StatusService`。守护本身不替用户自动授予无障碍权限。
- 用户首次成功开启守护后，记录“已加入自动重开保护”。从未主动开启过守护的用户不能被自动重开逻辑凭空开启该侵入性功能。

### 1.3 关闭、锁定与自动重开

- 现有代码没有一个统一的“锁定模式”设置。因此本计划把用户所说的“开启了锁定模式”定义为：**数字自律任意现存锁的截止时间仍在未来**。
- 锁来源包括订阅/应用/规则组锁、专注会话和专注规则锁、应用拦截全局/分组/时间规则锁、网址拦截全局/分组/网址规则/时间规则锁，以及兼容保留的旧 `focus_lock` 记录。
- 只要任一锁仍有效，守护开关不能从 `true` 变成 `false`；拒绝动作不能消耗“每日关闭限额”。
- 没有锁时，关闭守护与其他受自动重开保护的开关一致：只在 `true → false` 时消费一次共享关闭限额；限额已用完则拒绝关闭。
- 允许关闭后，`accessibilityGuardEnabled=false`，但保留自动重开 enrollment。到现有自动重开检查点后重新写回 `true`，唤醒协调器并尝试恢复 `StatusService`。
- Android 12 及更高版本可能拒绝后台启动前台服务。自动重开必须先持久化守护为开启并做一次安全启动尝试；若系统拒绝，不能崩溃或高频重试，等用户下一次打开应用、无障碍服务回调或其他现有允许入口调用 `StatusService.autoStart()` 后继续。
- 用户强制停止应用、撤销通知/悬浮窗权限、系统限制通知渠道时，应用不能绕过系统决定；文案和验收必须如实体现这一边界。

### 1.4 首页“服务状态”必须单向

- 在无障碍分支中，开关从关闭状态点到开启状态时，只执行“授权、开启或修复”。
- 在无障碍正在运行时点关闭，不得调用 `switchAutomatorService()`、`AccessibilityService.disableSelf()` 或修改系统已启用组件列表。
- 关闭手势只弹出说明：`为防止误触，应用内不支持在此关闭无障碍。若确需关闭，请前往系统无障碍设置。`
- 对话框提供“取消”和“前往系统设置”；确认后复用 `openA11ySettings()`。
- Compose 的 `checked` 继续由真实运行状态驱动，用户取消对话框后开关保持开启。
- “无障碍组件已启用但进程故障”仍走开启/修复路径，不能误判为用户想关闭。
- 本任务只改变首页“服务状态”的无障碍分支；Automation/Shizuku 分支和快捷设置磁贴维持现有行为。不能全局删除 `disableSelf()`，因为局部屏蔽等内部临时关闭流程仍依赖它。

### 1.5 页面位置与跳转

- 从首页 `ControlPage` 完整移除“无障碍权限守护”卡片、启停对话框和启停实现。
- 在“数字自律”页中放到“使用申请”之后、“软件安装监测”之前，和其他保护功能处于同一栏目。
- 守护通知点击后进入“数字自律”页，而不是已经没有该入口的首页。为此新增内部 URI `gkd://page/4` 到 `FocusLockRoute` 的映射。
- 第 36 分钟全屏悬浮窗的“前往”按钮仍按原需求回到应用首页，不在本任务中改变。

---

## 2. 推荐方案与取舍

### 2.1 使用两类通知

推荐保留两类通知，而不是用同一 ID 反复承担两个角色：

1. 一条稳定、ongoing 的状态通知，固定 ID `116`，负责从 `T+0` 开始展示距离下一检查点的系统倒计时。
2. 六条既有、非 ongoing、高优先级提醒，ID `110..115`，负责在每个业务检查点重新提交并尽力触发顶部横幅。

稳定通知设置 `when=nextWakeAtEpochMs`、`usesChronometer=true`、`chronometerCountDown=true`、`onlyAlertOnce=true`。倒计时由 SystemUI 渲染，不需要应用每秒唤醒、更新或写入存储。

不推荐每秒更新通知：它会增加唤醒、IPC 和厂商限流风险，也会让协调器更难保持幂等。不推荐只修改 `StatusService` 的前台通知：该通知还承载规则状态，且阶段提醒需要独立的高重要性语义。

### 2.2 使用显式 enrollment，而不是看到 false 就恢复

在 `SettingsStore` 增加：

```kotlin
val accessibilityGuardAutoReenableArmed: Boolean = false
```

只有一次显式启用成功后才写为 `true`。手动关闭不会清除它，自动重开据此判断是否恢复。这样既满足“关闭后会自动重开”，又不会让旧用户或新安装用户在没有接受通知/悬浮窗说明时被后台强制打开守护。

这是设置 JSON 的向后兼容默认字段，不是 Room 实体字段，因此不增加数据库版本，也不生成 schema 迁移。

### 2.3 用跨表现层控制器统一守护事务

新增 `AccessibilityGuardController`，承接当前 `ControlPage.kt` 顶层的 request sequence、desired state、权限请求、StatusService 启动、失败清理、关闭重置和自动恢复副作用。Compose 页面只负责显示和解释结果，不能直接写 `accessibilityGuardEnabled`。

这样移动页面时不会复制当前已经存在的竞态防护，也能让自动重开复用同一套恢复入口。

### 2.4 用只读 DAO 判定任意锁

新增不对应新表的 `DigitalSelfDisciplineLockDao`，通过一个 `EXISTS(UNION ALL ...)` 查询现有表。关闭点击时传入当前 `nowEpochMs` 做一次 suspend 查询，不缓存“是否锁定”的布尔值，避免锁刚创建或自然过期时读取旧 Compose/Flow 快照。

不新增“无障碍守护专属锁”表或单独的锁定 UI：用户要求复用现有锁定模式，额外模型会产生两套含义。

### 2.5 首页调用显式“开启/修复”，不调用 toggle

在 `GkdTileService.kt` 中抽出显式的 `requestStartOrRepairAutomatorService()`：

- 无障碍分支已经运行时直接 no-op；未运行时执行现有授权后开启/故障修复逻辑。
- Automation 分支如需复用，只做“未连接则连接”，不先 shutdown。
- 现有 `switchAutomatorService()` 仍保留给 Automation 首页分支和快捷设置磁贴。

这不仅移除显式关闭分支，也消除“界面看到 off、异步执行时服务已经 on，toggle 反而将其关闭”的竞态。

---

## 3. Android 平台调研结论

- [`Notification.Builder.setChronometerCountDown`](https://developer.android.com/reference/android/app/Notification.Builder#setChronometerCountDown(boolean)) 在 API 24 加入，需与 `setUsesChronometer(true)` 一起使用；本项目 `minSdk` 足以直接使用兼容封装。
- [Android 通知文档](https://developer.android.com/develop/ui/views/notifications) 说明横幅表现受通知渠道重要性、用户设置、勿扰模式和系统策略控制，所以验收用“应用提交高优先级通知”，不能承诺所有设备百分之百弹出顶部横幅。
- [Android 13 通知运行时权限](https://developer.android.com/develop/ui/views/notifications/notification-permission) 仍必须由用户授予；拒绝后守护无法显示任何通知。
- [`Settings.ACTION_ACCESSIBILITY_SETTINGS`](https://developer.android.com/reference/android/provider/Settings#ACTION_ACCESSIBILITY_SETTINGS) 是跳转系统无障碍设置的标准入口；继续使用现有带失败保护的 `openA11ySettings()`。
- [`AccessibilityService.disableSelf()`](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService#disableSelf()) 会主动停止服务；首页关闭路径必须不再到达它。
- [前台服务后台启动限制](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start) 表明 Android 12+ 通常不允许后台任意启动前台服务；自动重开必须实现为“持久化恢复 + 尽力启动 + 下个合法入口补启动”，不能宣称绕过系统限制。

---

## 4. File Map

### Create

- `app/src/main/kotlin/li/songe/gkd/sdp/util/AccessibilityGuardControlPolicy.kt`
- `app/src/main/kotlin/li/songe/gkd/sdp/service/AccessibilityGuardController.kt`
- `app/src/main/kotlin/li/songe/gkd/sdp/db/DigitalSelfDisciplineLockDao.kt`
- `app/src/main/kotlin/li/songe/gkd/sdp/util/HomeA11yServiceTogglePolicy.kt`
- `app/src/test/kotlin/li/songe/gkd/sdp/util/AccessibilityGuardControlPolicyTest.kt`
- `app/src/test/kotlin/li/songe/gkd/sdp/db/DigitalSelfDisciplineLockDaoContractTest.kt`
- `app/src/test/kotlin/li/songe/gkd/sdp/util/HomeA11yServiceTogglePolicyTest.kt`

### Modify

- `app/src/main/kotlin/li/songe/gkd/sdp/store/SettingsStore.kt`
- `app/src/main/kotlin/li/songe/gkd/sdp/db/AppDb.kt`
- `app/src/main/kotlin/li/songe/gkd/sdp/service/GkdTileService.kt`
- `app/src/main/kotlin/li/songe/gkd/sdp/service/AutoReenableEnforcer.kt`
- `app/src/main/kotlin/li/songe/gkd/sdp/service/AccessibilityGuardCoordinator.kt`
- `app/src/main/kotlin/li/songe/gkd/sdp/notif/Notif.kt`
- `app/src/main/kotlin/li/songe/gkd/sdp/util/AccessibilityGuardNotificationPolicy.kt`
- `app/src/main/kotlin/li/songe/gkd/sdp/ui/home/ControlPage.kt`
- `app/src/main/kotlin/li/songe/gkd/sdp/ui/FocusLockPage.kt`
- `app/src/main/kotlin/li/songe/gkd/sdp/MainViewModel.kt`
- `app/src/test/kotlin/li/songe/gkd/sdp/service/AutoReenableEnforcerTest.kt`
- `app/src/test/kotlin/li/songe/gkd/sdp/util/AccessibilityGuardNotificationPolicyTest.kt`
- `app/src/test/kotlin/li/songe/gkd/sdp/service/AccessibilityGuardRuntimeTest.kt`
- `README_DEV.md`

### Explicitly unchanged unless compilation proves otherwise

- `app/src/main/AndroidManifest.xml`：现有通知、`StatusService`、无障碍服务和悬浮窗服务声明已经够用。
- `app/src/main/kotlin/li/songe/gkd/sdp/store/AccessibilityGuardStore.kt`：倒计时目标可由现有 session 和策略推导，不增加持久化字段。
- `app/src/main/kotlin/li/songe/gkd/sdp/service/AccessibilityGuardOverlayService.kt`：第 36 分钟和“前往首页”行为不变。
- `app/src/main/kotlin/li/songe/gkd/sdp/notif/NotifChannel.kt`：继续复用已存在的高重要性 `AccessibilityGuard` 渠道。
- Room schema/version：本计划不新增或修改实体列。
- `.github/workflows/Verify-Merge.yml`、`.github/workflows/Build-Apk.yml`：现有 PR 流程已运行 JVM 测试并编译 `gkdDebug`、`playDebug`、`gkdRelease`；不要为本功能绕开或削弱这些检查。

---

## 5. Implementation Tasks

### Task 0: 建立隔离工作区并验证基线

**Files:** 无源码修改。

本任务开始时使用 `superpowers:using-git-worktrees`，后续按 `superpowers:test-driven-development` 执行每个功能增量。

**Step 1: 同步并创建功能 worktree**

```bash
git fetch origin main
git worktree add .worktrees/accessibility-guard-hardening \
  -b codex/accessibility-guard-hardening origin/main
git -C .worktrees/accessibility-guard-hardening status -sb
```

Expected: worktree 位于 `codex/accessibility-guard-hardening`，且没有修改或未跟踪文件。

**Step 2: 记录基线版本和现有 CI**

```bash
git -C .worktrees/accessibility-guard-hardening rev-parse HEAD
git -C .worktrees/accessibility-guard-hardening status --short
gh workflow view Verify-Merge.yml
gh workflow view Build-Apk.yml
```

Expected: 基线来自最新 `origin/main`；`Verify-Merge.yml` 包含 `:selector:jvmTest`、`:app:testGkdDebugUnitTest`、`assembleGkdDebug`、`assemblePlayDebug`、`assembleGkdRelease`。

**Step 3: 运行现有聚焦测试**

```bash
./gradlew :app:testGkdDebugUnitTest \
  --tests '*AccessibilityGuardPolicyTest' \
  --tests '*AccessibilityGuardNotificationPolicyTest' \
  --tests '*AccessibilityGuardRuntimeTest' \
  --tests '*AutoReenableEnforcerTest' \
  --tests '*AutoReenableDisableGuardTest'
```

Expected: PASS。若本机因依赖/KSP 环境而失败，保存原始错误并用未修改基线在 GitHub Actions 复核；不能为了绕过本机环境问题修改业务代码。

---

### Task 1: 先锁定启用、关闭和自动恢复纯策略

**Files:**

- Create: `app/src/test/kotlin/li/songe/gkd/sdp/util/AccessibilityGuardControlPolicyTest.kt`
- Create: `app/src/main/kotlin/li/songe/gkd/sdp/util/AccessibilityGuardControlPolicy.kt`
- Modify: `app/src/main/kotlin/li/songe/gkd/sdp/store/SettingsStore.kt`

**Step 1: 写失败测试**

测试至少覆盖：

```kotlin
@Test
fun guardCanBeEnabledWhenAccessibilityIsAlreadyOff() {
    assertEquals(
        AccessibilityGuardControlPolicy.EnableDecision.ALLOW,
        AccessibilityGuardControlPolicy.enableDecision(
            strictChannelAvailable = true,
            useA11yMode = true,
            accessibilityComponentEnabled = false,
        ),
    )
}

@Test
fun automationModeStillCannotEnableAccessibilityGuard() { /* REQUIRE_A11Y_MODE */ }

@Test
fun activeLockBlocksDisableBeforeQuotaIsConsidered() { /* BLOCKED_BY_LOCK */ }

@Test
fun unlockedDisableRequiresAvailableQuota() { /* ALLOW or BLOCKED_BY_QUOTA */ }

@Test
fun autoReenableRequiresEnrollmentAndCurrentOffState() { /* true only when armed */ }

@Test
fun neverEnabledGuardIsNotAutoEnabled() { /* armed=false => false */ }
```

让 `EnableDecision` 显式接收 `accessibilityComponentEnabled`，但 `false` 不能成为拒绝原因。这样未来维护者无法不知情地恢复旧前置条件。

**Step 2: 验证测试失败**

```bash
./gradlew :app:testGkdDebugUnitTest --tests '*AccessibilityGuardControlPolicyTest'
```

Expected: FAIL，类或决策方法尚不存在。

**Step 3: 实现最小纯策略**

建议定义：

```kotlin
object AccessibilityGuardControlPolicy {
    enum class EnableDecision { ALLOW, UNAVAILABLE_CHANNEL, REQUIRE_A11Y_MODE }
    enum class DisableDecision { NO_CHANGE, ALLOW, BLOCKED_BY_LOCK, BLOCKED_BY_QUOTA }

    fun enableDecision(
        strictChannelAvailable: Boolean,
        useA11yMode: Boolean,
        accessibilityComponentEnabled: Boolean,
    ): EnableDecision

    fun disableDecision(
        currentlyEnabled: Boolean,
        anyActiveLock: Boolean,
        quotaAllowed: Boolean,
    ): DisableDecision

    fun shouldAutoReenable(
        strictChannelAvailable: Boolean,
        useA11yMode: Boolean,
        armed: Boolean,
        currentlyEnabled: Boolean,
    ): Boolean
}
```

决策优先级固定为 `NO_CHANGE → BLOCKED_BY_LOCK → BLOCKED_BY_QUOTA → ALLOW`。锁定拒绝必须发生在调用共享 quota 之前。

**Step 4: 增加向后兼容设置字段**

在 `SettingsStore` 的守护字段附近添加：

```kotlin
val accessibilityGuardEnabled: Boolean = false,
val accessibilityGuardAutoReenableArmed: Boolean = false,
```

不要修改 Room 版本。

**Step 5: 运行测试**

```bash
./gradlew :app:testGkdDebugUnitTest --tests '*AccessibilityGuardControlPolicyTest'
```

Expected: PASS。

**Step 6: 提交并推送一个小增量**

```bash
git add app/src/main/kotlin/li/songe/gkd/sdp/util/AccessibilityGuardControlPolicy.kt \
  app/src/main/kotlin/li/songe/gkd/sdp/store/SettingsStore.kt \
  app/src/test/kotlin/li/songe/gkd/sdp/util/AccessibilityGuardControlPolicyTest.kt
git commit -m 'feat: define accessibility guard control policy'
git push -u origin codex/accessibility-guard-hardening
```

---

### Task 2: 汇总现有数字自律锁，并在数据层做实时关闭门禁

**Files:**

- Create: `app/src/main/kotlin/li/songe/gkd/sdp/db/DigitalSelfDisciplineLockDao.kt`
- Create: `app/src/test/kotlin/li/songe/gkd/sdp/db/DigitalSelfDisciplineLockDaoContractTest.kt`
- Modify: `app/src/main/kotlin/li/songe/gkd/sdp/db/AppDb.kt`

**Step 1: 写失败的 DAO contract 测试**

测试引用 `DigitalSelfDisciplineLockDao::hasAnyActiveLock`，确保 DAO 和方法成为稳定编译契约。纯策略行为已由 Task 1 覆盖；Room KSP 编译负责校验 SQL 的表名和列名。

```kotlin
@Test
fun activeLockQueryContractExists() {
    assertNotNull(DigitalSelfDisciplineLockDao::hasAnyActiveLock)
}
```

**Step 2: 验证失败**

```bash
./gradlew :app:testGkdDebugUnitTest --tests '*DigitalSelfDisciplineLockDaoContractTest'
```

Expected: FAIL，DAO 不存在。

**Step 3: 创建只读 DAO**

`hasAnyActiveLock(nowEpochMs)` 使用一个 `EXISTS` 查询，并 `UNION ALL` 以下条件：

- `constraint_config.lock_end_time > :nowEpochMs`
- `focus_lock.end_time > :nowEpochMs`
- `focus_session.is_locked = 1 AND focus_session.lock_end_time > :nowEpochMs`
- `focus_rule.is_locked = 1 AND focus_rule.lock_end_time > :nowEpochMs`
- `app_blocker_lock.is_locked = 1 AND app_blocker_lock.lock_end_time > :nowEpochMs`
- `app_group.is_locked = 1 AND app_group.lock_end_time > :nowEpochMs`
- `block_time_rule.is_locked = 1 AND block_time_rule.lock_end_time > :nowEpochMs`
- `url_blocker_lock.is_locked = 1 AND url_blocker_lock.lock_end_time > :nowEpochMs`
- `url_rule_group.is_locked = 1 AND url_rule_group.lock_end_time > :nowEpochMs`
- `url_block_rule.is_locked = 1 AND url_block_rule.lock_end_time > :nowEpochMs`
- `url_time_rule.is_locked = 1 AND url_time_rule.lock_end_time > :nowEpochMs`

返回 `Boolean`；只需存在一行，不读取完整实体列表。

**Step 4: 注册 DAO**

在 `AppDb` 添加：

```kotlin
abstract fun digitalSelfDisciplineLockDao(): DigitalSelfDisciplineLockDao
```

在 `DbSet` 添加对应 getter。不要改变 `@Database(version = 30)`。

**Step 5: 编译 KSP 并运行测试**

```bash
./gradlew :app:kspGkdDebugKotlin \
  :app:testGkdDebugUnitTest --tests '*DigitalSelfDisciplineLockDaoContractTest'
```

Expected: PASS；Room 不报告未知表/列；没有新增 schema 文件。

**Step 6: 提交**

```bash
git add app/src/main/kotlin/li/songe/gkd/sdp/db/AppDb.kt \
  app/src/main/kotlin/li/songe/gkd/sdp/db/DigitalSelfDisciplineLockDao.kt \
  app/src/test/kotlin/li/songe/gkd/sdp/db/DigitalSelfDisciplineLockDaoContractTest.kt
git commit -m 'feat: detect active digital self discipline locks'
```

---

### Task 3: 抽取守护控制器，并允许“权限已关闭时开启”

**Files:**

- Create: `app/src/main/kotlin/li/songe/gkd/sdp/service/AccessibilityGuardController.kt`
- Modify: `app/src/main/kotlin/li/songe/gkd/sdp/ui/home/ControlPage.kt`
- Modify: `app/src/test/kotlin/li/songe/gkd/sdp/service/AccessibilityGuardRuntimeTest.kt`

**Step 1: 为控制器结果和竞态写失败测试**

把不依赖 Android permission launcher 的状态转换留在纯策略中；在现有 runtime 测试中补充：

- 启用请求期间发生取消/关闭时，旧 request id 不能在权限返回后写回 `true`。
- `disableAndReset()` 继续推进 generation 并清除 session。
- 自动恢复唤醒不会重置已开始、仍合法的关闭会话。

如直接实例化控制器需要 Android 环境，则把 request fence 提取为 controller 内部可注入的小状态对象并做 JVM 测试，不引入 Robolectric 只为测试 Compose/Activity。

**Step 2: 验证新增测试失败**

```bash
./gradlew :app:testGkdDebugUnitTest \
  --tests '*AccessibilityGuardRuntimeTest' \
  --tests '*AccessibilityGuardControlPolicyTest'
```

Expected: 新增用例 FAIL。

**Step 3: 把启停事务从 `ControlPage.kt` 移到控制器**

控制器至少提供：

```kotlin
object AccessibilityGuardController {
    sealed interface EnableResult
    sealed interface DisableResult

    suspend fun enable(activity: MainActivity): EnableResult
    suspend fun disable(nowEpochMs: Long = System.currentTimeMillis()): DisableResult
    fun autoReenableIfEligible(): Int
}
```

迁移并保留现有 request sequence/desired 状态的竞态围栏、权限取消清理和 `StatusService.requestStart()` 超时清理。不要让页面直接操作这些原子变量。

**Step 4: 删除错误的无障碍开启前置条件**

从 enable 流程的初次检查和权限返回后的复查中删除：

```kotlin
mainVm.a11yServiceEnabledFlow.value
secureA11yServiceEnabled()
```

保留：

- `META.isGkdChannel`
- `storeFlow.value.useA11y`
- notification permission
- foreground-service special-use permission
- overlay permission
- StatusService 成功启动

成功时一次更新：

```kotlin
settings.copy(
    accessibilityGuardEnabled = true,
    accessibilityGuardAutoReenableArmed = true,
)
```

随后调用 `AccessibilityGuardRuntime.requestReconcile()`。不能自动调用开启无障碍的逻辑；守护负责提醒，而不是替用户更改该系统权限。

**Step 5: 实现关闭门禁**

控制器关闭顺序：

1. 当前已经关闭则返回 `NoChange`。
2. `DbSet.digitalSelfDisciplineLockDao.hasAnyActiveLock(nowEpochMs)`；为 true 时返回 `BlockedByLock`，不调用 quota。
3. 调用 `AutoReenableDisableGuard.tryConsumeForDisable(nowEpochMs)`；失败返回 `BlockedByQuota`。
4. 推进 request sequence，写 `accessibilityGuardEnabled=false`，但保持 enrollment 为 true。
5. 调用 `AccessibilityGuardRuntime.disableAndReset()`、`AccessibilityGuardOverlayService.stop()` 并取消守护通知。

如果当前守护只是在 activation 中尚未成功，也要让新 request id 废弃旧权限回调。

**Step 6: 从首页删除旧实现并临时改接控制器**

移除 `ControlPage.kt` 顶层守护原子变量、`enableAccessibilityGuard`、`disableAccessibilityGuard` 和辅助 `secureA11yServiceEnabled`。为保证本 task 的 commit 可独立编译，在 Task 4 正式迁移卡片之前，先让现有首页卡片调用新控制器并映射相同结果；不能暂时保留两套可写入口。

**Step 7: 运行测试**

```bash
./gradlew :app:testGkdDebugUnitTest \
  --tests '*AccessibilityGuardControlPolicyTest' \
  --tests '*AccessibilityGuardRuntimeTest' \
  --tests '*AutoReenableDisableGuardTest'
```

Expected: PASS。

**Step 8: 提交并推送**

```bash
git add app/src/main/kotlin/li/songe/gkd/sdp/service/AccessibilityGuardController.kt \
  app/src/main/kotlin/li/songe/gkd/sdp/ui/home/ControlPage.kt \
  app/src/test/kotlin/li/songe/gkd/sdp/service/AccessibilityGuardRuntimeTest.kt
git commit -m 'refactor: centralize accessibility guard controls'
git push
```

---

### Task 4: 把守护卡片迁入“数字自律”并接入锁定反馈

**Files:**

- Modify: `app/src/main/kotlin/li/songe/gkd/sdp/ui/home/ControlPage.kt`
- Modify: `app/src/main/kotlin/li/songe/gkd/sdp/ui/FocusLockPage.kt`
- Modify: `app/src/main/kotlin/li/songe/gkd/sdp/MainViewModel.kt`

**Step 1: 完整移除首页守护 UI**

从 `ControlPage` 删除：

- `showAccessibilityGuardDialog`
- “无障碍权限守护” `PageSwitchItemCard`
- 仅为守护启停存在的 import

“常驻通知”在 `accessibilityGuardEnabled=true` 时不能关闭的既有保护保持不变。

**Step 2: 在 `FocusLockPage` 添加卡片**

位置固定为：

```text
使用申请
无障碍权限守护
软件安装监测
自动重开保护
```

仅 `META.isGkdChannel` 渲染。卡片文案建议：

- 标题：`无障碍权限守护`
- 开启：`关闭后立即倒计时提醒；15/10/5/3/2/1 分钟分阶段提醒`
- 关闭且已 enrollment：`已暂时关闭，将由自动重开保护恢复`
- 关闭且未 enrollment：`检测权限关闭并分阶段提醒，最后显示全屏提示`

**Step 3: 更新首次开启说明**

对话框必须明确：

- 无障碍当前关闭也可以开启守护；开启后会立即开始倒计时。
- 阶段提醒累计发生在 15、25、30、33、35、36 分钟。
- 第 36 分钟仍关闭时显示全屏悬浮窗。
- 关闭会受当前锁定、每日关闭限额和自动重开保护约束。

不要再写“你可以随时在本页关闭守护”。

**Step 4: 映射控制器结果**

- `RequiresA11yMode`：提示“请先切换到无障碍模式”，导航 `AuthA11yRoute`。
- `Enabled`：不跳无障碍授权页；让 T+0 通知引导用户。
- `BlockedByLock`：提示“数字自律锁定生效中，无法关闭无障碍权限守护”。
- `BlockedByQuota(limit)`：复用现有“今日关闭次数已用完”措辞。
- 用户取消 Android 权限请求：保持关闭，不残留 StatusService activation。

**Step 5: 新增通知内部路由**

在 `MainViewModel.handleGkdUri()` 中添加：

```kotlin
"/4" -> navigatePage(FocusLockRoute)
```

并导入 `FocusLockRoute`。此 URI 由显式 `MainActivity` PendingIntent 使用，不需要新增 manifest intent-filter。

**Step 6: 编译两个 flavor**

```bash
./gradlew :app:compileGkdDebugKotlin :app:compilePlayDebugKotlin
```

Expected: PASS；Play 构建能编译但不显示卡片。

**Step 7: 提交**

```bash
git add app/src/main/kotlin/li/songe/gkd/sdp/ui/home/ControlPage.kt \
  app/src/main/kotlin/li/songe/gkd/sdp/ui/FocusLockPage.kt \
  app/src/main/kotlin/li/songe/gkd/sdp/MainViewModel.kt
git commit -m 'feat: move accessibility guard into digital self discipline'
```

---

### Task 5: 将守护纳入自动重开保护

**Files:**

- Modify: `app/src/test/kotlin/li/songe/gkd/sdp/service/AutoReenableEnforcerTest.kt`
- Modify: `app/src/main/kotlin/li/songe/gkd/sdp/service/AutoReenableEnforcer.kt`
- Modify: `app/src/main/kotlin/li/songe/gkd/sdp/ui/FocusLockPage.kt`

**Step 1: 写失败测试**

增加：

```kotlin
@Test
fun defaultOperationsIncludeAccessibilityGuardRecovery() {
    assertTrue(
        AutoReenableEnforcer.defaultOperationNames()
            .contains("accessibility_guard_switch")
    )
}
```

并通过 `AccessibilityGuardControlPolicyTest` 验证以下矩阵：

| GKD channel | A11y mode | armed | 当前 enabled | 是否恢复 |
|---|---|---|---|---|
| true | true | true | false | yes |
| true | true | false | false | no |
| false | true | true | false | no |
| true | false | true | false | no |
| true | true | true | true | no-op |

**Step 2: 验证失败**

```bash
./gradlew :app:testGkdDebugUnitTest \
  --tests '*AutoReenableEnforcerTest' \
  --tests '*AccessibilityGuardControlPolicyTest'
```

Expected: 默认操作名用例 FAIL。

**Step 3: 新增恢复操作**

在 `defaultEnableOperationEntries()` 的 `usage_guard_switch` 后添加：

```kotlin
"accessibility_guard_switch" to {
    AccessibilityGuardController.autoReenableIfEligible()
}
```

控制器在符合策略时：

1. 先原子写 `accessibilityGuardEnabled=true`，保持 enrollment。
2. 调用 `AccessibilityGuardRuntime.requestReconcile()`。
3. 调用一次 `StatusService.autoStart()`；捕获现有启动失败路径，不在 enforcer 中循环重试。
4. 返回 `1`；无变化返回 `0`。

若升级前 `accessibilityGuardEnabled=true` 但 enrollment 是默认 false，在一次 normalization 中将 enrollment 补为 true，确保这类用户之后手动关闭也能被恢复。

**Step 4: 更新自动重开文案**

两处文案都改为包含守护：

- 对话框：`会恢复已关闭的规则、使用申请开关与已加入保护的无障碍权限守护。`
- 卡片：`会恢复规则、使用申请开关与无障碍权限守护`

不要暗示它能自动重新授予被用户从系统设置撤销的无障碍权限；自动恢复的是“守护功能开关”。

**Step 5: 运行测试**

```bash
./gradlew :app:testGkdDebugUnitTest \
  --tests '*AutoReenableEnforcerTest' \
  --tests '*AccessibilityGuardControlPolicyTest' \
  --tests '*FocusLockVmAutoReenableTest'
```

Expected: PASS。

**Step 6: 提交并推送**

```bash
git add app/src/main/kotlin/li/songe/gkd/sdp/service/AutoReenableEnforcer.kt \
  app/src/main/kotlin/li/songe/gkd/sdp/ui/FocusLockPage.kt \
  app/src/test/kotlin/li/songe/gkd/sdp/service/AutoReenableEnforcerTest.kt
git commit -m 'feat: auto reenable accessibility guard'
git push
```

---

### Task 6: 添加 T+0 稳定倒计时通知

**Files:**

- Modify: `app/src/test/kotlin/li/songe/gkd/sdp/util/AccessibilityGuardNotificationPolicyTest.kt`
- Modify: `app/src/main/kotlin/li/songe/gkd/sdp/util/AccessibilityGuardNotificationPolicy.kt`
- Modify: `app/src/main/kotlin/li/songe/gkd/sdp/notif/Notif.kt`
- Modify: `app/src/main/kotlin/li/songe/gkd/sdp/service/AccessibilityGuardCoordinator.kt`
- Modify: `app/src/test/kotlin/li/songe/gkd/sdp/service/AccessibilityGuardRuntimeTest.kt`

**Step 1: 写失败的状态通知策略测试**

至少覆盖：

- 新会话 `lastReminderIndex=-1` 指向第 1 次提醒和 `disabledAt+15min`。
- 每次已提交 reminder 后指向下一累计检查点。
- 进程在 `T+34` 恢复、补记第 4 次后，状态通知只倒计时到 `T+35`。
- `enforcementStarted=true` 返回无 chronometer 的最终文案。
- invalid session 不生成状态通知模型。

推荐纯模型：

```kotlin
data class GuardStatusNotification(
    val text: String,
    val targetEpochMs: Long?,
    val nextReminderIndex: Int?,
)

fun status(
    disabledAtEpochMs: Long,
    lastReminderIndex: Int,
    enforcementStarted: Boolean,
): GuardStatusNotification?
```

**Step 2: 验证失败**

```bash
./gradlew :app:testGkdDebugUnitTest \
  --tests '*AccessibilityGuardNotificationPolicyTest'
```

Expected: FAIL，新状态模型/方法不存在。

**Step 3: 实现纯状态策略**

目标时间严格从 `AccessibilityGuardPolicy.REMINDER_OFFSETS_MS` 推导，不能复制一份魔法分钟数组。建议文案：

- 倒计时阶段：`距离第 N 次提醒`
- 最终阶段：`最后提醒已发送，请立即开启无障碍`

**Step 4: 扩展通用 `Notif` 构造能力**

在 `Notif` 增加可选字段：

```kotlin
val whenEpochMs: Long? = null,
val usesChronometer: Boolean = false,
val chronometerCountDown: Boolean = false,
val onlyAlertOnce: Boolean = false,
```

在 `NotificationCompat.Builder` 中仅按字段设置，不改变现有通知默认行为。

新增：

```kotlin
const val ACCESSIBILITY_GUARD_STATUS_NOTIF_ID = 116
```

以及 `postAccessibilityGuardStatusNotification(...)`、`cancelAccessibilityGuardStatusNotification()`。状态通知必须：

- 使用 `NotifChannel.AccessibilityGuard`
- `ongoing=true`
- `autoCancel=false`
- `priority=PRIORITY_HIGH`
- `category=CATEGORY_ERROR`
- `onlyAlertOnce=true`
- 倒计时时设置 `whenEpochMs`、`usesChronometer`、`chronometerCountDown`
- URI 使用 `gkd://page/4`

重命名/拆分取消方法，确保提交下一条阶段提醒时只取消 `110..115`，不会顺手取消并重建状态通知。完整 reset 时才同时取消状态和阶段提醒。

**Step 5: 在协调器中加入幂等状态通知 token**

新增进程内 token，至少包含：

```kotlin
private data class StatusNotificationToken(
    val generation: Long,
    val nextReminderIndex: Int?,
    val targetEpochMs: Long?,
    val enforcementStarted: Boolean,
)
```

规则：

1. 首次从 `IDLE → TRACK` 建立 session 后，直接读取一次系统已启用无障碍组件并通过 generation fence；仍关闭才提交 T+0 状态通知。
2. 同一 token 的 noisy Flow reconcile 不重复 notify。
3. 每个阶段提醒持久化 `lastReminderIndex` 后，用新 token 更新到下一个目标。
4. 最后 reminder 持久化 `enforcementStarted=true` 后更新为最终静态文案。
5. `RESET`、权限恢复、功能关闭时取消所有守护通知并清空 token。
6. `SUPPRESSED_TEMPORARY` 时取消状态通知、保留既有 session 语义；恢复 TRACK 后重新按绝对时间计算。
7. 状态通知和六次提醒都继续使用 `sideEffectFenceOpen()` 与 `secureA11yServiceEnabled()` 的临提交复查，不能向已经恢复的用户发送陈旧通知。

不能添加秒级 timer；现有 timer 只唤醒下一个业务检查点。

**Step 6: 运行聚焦测试**

```bash
./gradlew :app:testGkdDebugUnitTest \
  --tests '*AccessibilityGuardNotificationPolicyTest' \
  --tests '*AccessibilityGuardPolicyTest' \
  --tests '*AccessibilityGuardRuntimeTest'
```

Expected: PASS，既有 `[15,25,30,33,35,36]` 断言不变。

**Step 7: 提交**

```bash
git add app/src/main/kotlin/li/songe/gkd/sdp/notif/Notif.kt \
  app/src/main/kotlin/li/songe/gkd/sdp/service/AccessibilityGuardCoordinator.kt \
  app/src/main/kotlin/li/songe/gkd/sdp/util/AccessibilityGuardNotificationPolicy.kt \
  app/src/test/kotlin/li/songe/gkd/sdp/service/AccessibilityGuardRuntimeTest.kt \
  app/src/test/kotlin/li/songe/gkd/sdp/util/AccessibilityGuardNotificationPolicyTest.kt
git commit -m 'feat: show accessibility guard countdown notification'
```

---

### Task 7: 将首页无障碍服务开关改为单向开启/修复

**Files:**

- Create: `app/src/test/kotlin/li/songe/gkd/sdp/util/HomeA11yServiceTogglePolicyTest.kt`
- Create: `app/src/main/kotlin/li/songe/gkd/sdp/util/HomeA11yServiceTogglePolicy.kt`
- Modify: `app/src/main/kotlin/li/songe/gkd/sdp/service/GkdTileService.kt`
- Modify: `app/src/main/kotlin/li/songe/gkd/sdp/ui/home/ControlPage.kt`

**Step 1: 写失败测试**

纯策略动作建议：

```kotlin
enum class Action {
    OPEN_AUTHORIZATION,
    START_OR_REPAIR,
    EXPLAIN_SYSTEM_SETTINGS,
}
```

测试：

- `requestedEnabled=false` 无条件返回 `EXPLAIN_SYSTEM_SETTINGS`。
- `requestedEnabled=true && !writeSecureSettings` 返回 `OPEN_AUTHORIZATION`。
- `requestedEnabled=true && writeSecureSettings` 返回 `START_OR_REPAIR`。
- 运行状态变化不会让一个 start/repair 命令变成 disable 命令。

**Step 2: 验证失败**

```bash
./gradlew :app:testGkdDebugUnitTest --tests '*HomeA11yServiceTogglePolicyTest'
```

Expected: FAIL。

**Step 3: 从 service 层抽出显式开启/修复命令**

重构 `GkdTileService.kt` 时保留一个内部 `enableOrRepairA11yService()`，规则为：

- 已运行：return，不做任何关闭动作。
- 无写安全设置权限：沿现有错误提示返回。
- 组件存在但服务没运行：执行现有 remove、等待、add 修复。
- 组件不存在：add 并等待启动。

导出 `requestStartOrRepairAutomatorService()` 给首页调用。现有 `switchAutomatorService()` 继续服务 Automation 分支/快捷磁贴，不把 `disableSelf()` 暴露给首页 A11y onCheckedChange。

**Step 4: 改首页无障碍分支**

在 `ControlPage` 增加 `showA11yDisableInfoDialog`：

- off → on：按纯策略进入授权页或调用显式 start/repair。
- on → off：只打开说明对话框。
- 对话框确认：调用 `openA11ySettings()`。
- 对话框取消：不做任何状态改变。

不要把 `checked` 暂存在本地；继续绑定 `a11yRunning`。

**Step 5: 静态审计调用路径**

```bash
rg -n 'switchAutomatorService|disableSelf|requestStartOrRepairAutomatorService' \
  app/src/main/kotlin/li/songe/gkd/sdp/ui/home/ControlPage.kt \
  app/src/main/kotlin/li/songe/gkd/sdp/service/GkdTileService.kt
```

Expected:

- `ControlPage` 的无障碍分支只出现 `requestStartOrRepairAutomatorService`。
- `switchAutomatorService` 只在 Automation 分支继续使用。
- `disableSelf` 不可从首页无障碍 off 手势到达。

**Step 6: 运行测试和双 flavor 编译**

```bash
./gradlew :app:testGkdDebugUnitTest --tests '*HomeA11yServiceTogglePolicyTest'
./gradlew :app:compileGkdDebugKotlin :app:compilePlayDebugKotlin
```

Expected: PASS。

**Step 7: 提交并推送**

```bash
git add app/src/main/kotlin/li/songe/gkd/sdp/service/GkdTileService.kt \
  app/src/main/kotlin/li/songe/gkd/sdp/ui/home/ControlPage.kt \
  app/src/main/kotlin/li/songe/gkd/sdp/util/HomeA11yServiceTogglePolicy.kt \
  app/src/test/kotlin/li/songe/gkd/sdp/util/HomeA11yServiceTogglePolicyTest.kt
git commit -m 'fix: prevent home from disabling accessibility'
git push
```

---

### Task 8: 补开发文档并完成设备级验收

**Files:**

- Modify: `README_DEV.md`

**Step 1: 记录状态机和限制**

在开发文档加入“Accessibility guard hardening”小节，至少写明：

- T+0 稳定倒计时和六个累计检查点。
- enabled 与 auto-reenable enrollment 的区别。
- 任意有效数字自律锁阻止关闭。
- Home A11y 开关是 start/repair-only。
- SystemUI/通知权限/FGS 后台限制/force-stop 的平台边界。

**Step 2: 在一台 Android 13+ GKD 构建设备执行矩阵**

| 场景 | 预期 |
|---|---|
| 无障碍已关闭，首次开启守护 | 权限流程成功后守护为 ON，立即出现倒计时到 15 分钟的通知；不强制跳无障碍授权页 |
| 无障碍已开启，开启守护后去系统设置关闭 | 立即出现 T+0 倒计时通知 |
| 到达 15/25/30/33/35/36 分钟 | 每次只提交当前阶段提醒；状态通知更新到下一检查点 |
| T+36 仍关闭 | 最后一条通知先提交，离开应用后全屏悬浮窗按既有逻辑出现 |
| 重新开启无障碍 | 状态通知、阶段通知、timer、overlay 全部清理 |
| 开启任意一种数字自律锁后尝试关闭守护 | 拒绝，守护保持 ON，关闭 quota 不增加 |
| 无锁且 quota 可用时关闭守护 | 关闭成功，session/通知/overlay 清理，enrollment 保留 |
| 等到自动重开检查点 | 守护回到 ON；无障碍仍关闭时恢复计时/通知 |
| 从未开启过守护的新设置 | 自动重开不得自行把守护打开 |
| 首页无障碍运行时点击关闭 | 只显示说明；取消后服务仍运行 |
| 对话框点“前往系统设置” | 打开 Android 无障碍设置；应用内未调用 disableSelf |
| 无障碍组件存在但服务故障 | 首页点击开关执行修复，不执行关闭 |
| Automation/Shizuku 模式首页开关 | 维持现有开关行为；不启动无障碍守护 session |
| Android 13 拒绝通知权限 | 开启流程不能假装成功；给出既有权限反馈 |
| 自动重开在后台触发且 FGS 被系统拒绝 | enabled 持久化，无崩溃/高频重试；下次合法入口补启动 |
| 点击倒计时/阶段通知 | 进入“数字自律”页 |
| 点击最终悬浮窗“前往” | 仍进入首页，悬浮窗消失；再次退出且权限未恢复时重现 |

开发期间可用 debug-only 时间缩放或直接调用纯策略测试检查边界，但生产常量必须保持真实分钟值，不提交缩短后的时间表。

**Step 3: 提交文档**

```bash
git add README_DEV.md
git commit -m 'docs: describe accessibility guard hardening'
git push
```

---

### Task 9: 完整验证、代码审查、GitHub Actions、PR 与合并

**Files:** 所有本计划改动。

执行此任务前使用 `superpowers:verification-before-completion`；合并前使用 `superpowers:requesting-code-review` 和 `superpowers:finishing-a-development-branch`。

**Step 1: 检查改动范围和提交粒度**

```bash
git status --short
git diff --check
git log --oneline origin/main..HEAD
git diff --stat origin/main...HEAD
```

Expected: worktree clean；没有尾随空格；至少有上述按功能拆分的定期 commit，而不是一个混合大提交。

**Step 2: 运行完整本地测试**

```bash
./gradlew :selector:jvmTest :app:testGkdDebugUnitTest
```

Expected: PASS。

**Step 3: 本地构建可用时执行三变体编译**

```bash
./gradlew \
  :app:assembleGkdDebug \
  :app:assemblePlayDebug \
  :app:assembleGkdRelease
```

Expected: PASS。若 release signing 只在 GitHub Secrets 中可用，记录本机的准确签名错误，不得伪造签名或跳过 GitHub Actions。

**Step 4: 最终静态回归检查**

```bash
rg -n '请先开启无障碍权限|a11yServiceEnabledFlow.value|secureA11yServiceEnabled\(\)' \
  app/src/main/kotlin/li/songe/gkd/sdp/service/AccessibilityGuardController.kt
rg -n '无障碍权限守护' \
  app/src/main/kotlin/li/songe/gkd/sdp/ui/home/ControlPage.kt \
  app/src/main/kotlin/li/songe/gkd/sdp/ui/FocusLockPage.kt
git diff --name-only origin/main...HEAD | rg 'AppDb|schema|AndroidManifest|workflows'
```

Expected:

- 控制器没有恢复“无障碍必须已经开启”的前置检查。
- 守护卡片只在 `FocusLockPage`。
- `AppDb.kt` 只增加 DAO accessor，version 仍为 30。
- 没有 schema、Manifest 或 workflow 意外改动。

**Step 5: 创建 Draft PR 并让 GitHub Actions 做权威编译**

```bash
git push -u origin codex/accessibility-guard-hardening
gh pr create \
  --base main \
  --head codex/accessibility-guard-hardening \
  --draft \
  --title 'feat: harden accessibility guard controls' \
  --body-file docs/plans/2026-07-31-accessibility-guard-hardening.md
gh pr checks --watch
```

PR 描述可引用计划，但在正文顶部另加简短摘要、测试结果和设备验收结果，避免只有长计划。

Expected: `Verify Merge / test-and-build` 绿色，证明：

- `:selector:jvmTest`
- `:app:testGkdDebugUnitTest`
- `:app:assembleGkdDebug`
- `:app:assemblePlayDebug`
- `:app:assembleGkdRelease`

全部通过。

**Step 6: 若 Actions 失败，先取证再修复**

使用 `github:gh-fix-ci`/`superpowers:systematic-debugging`：

```bash
gh run list --branch codex/accessibility-guard-hardening --limit 10
gh run view <run-id> --log-failed
```

只修复日志证明的根因，新增一个针对回归的测试，单独提交并推送；继续等待同一个 PR 的最新 checks。不得在红灯下合并，不得删减 `playDebug` 或 `gkdRelease` 来获得绿色。

**Step 7: 请求代码审查**

审查重点：

- `ControlPage` 的无障碍 off 动作是否仍有任何路径调用 toggle/disableSelf。
- 控制器是否在锁查询后、quota 前做正确排序。
- auto-reenable 是否只恢复 enrolled 用户。
- T+0 通知是否受 generation/secure setting 双重 fence。
- reminder cancel 是否不会误删 ongoing countdown。
- 背景 FGS 启动失败是否安全降级。
- Play flavor 是否不会展示严格守护入口。

处理完反馈后重新跑测试并等待 Actions 绿色。

**Step 8: Ready、合并并验证 main 构建**

```bash
gh pr ready
gh pr checks --watch
gh pr merge --merge --delete-branch
git fetch origin main
```

取得合并后的 main SHA，并等待 `Build Latest APK`：

```bash
MERGE_SHA=$(git rev-parse origin/main)
MAIN_RUN_ID=$(gh run list \
  --workflow Build-Apk.yml \
  --branch main \
  --commit "$MERGE_SHA" \
  --limit 1 \
  --json databaseId \
  --jq '.[0].databaseId')
gh run watch "$MAIN_RUN_ID" --exit-status
```

Expected: main 的 `Build Latest APK` 测试、签名 release 构建、latest prerelease 发布全部成功。若没有查询到对应 SHA 的 run，不得误用旧绿色 run；先用 `gh run list --workflow Build-Apk.yml --branch main` 核对触发状态。

---

## 6. Acceptance Criteria

1. 首页不再显示“无障碍权限守护”，数字自律页在“使用申请”后显示该卡片。
2. 无障碍组件关闭且服务不运行时，可以成功开启守护；不会提示“请先开启无障碍权限”。
3. 守护确认无障碍关闭后立即提交一条倒计时到 15 分钟的 ongoing 通知。
4. 阶段提醒仍严格使用累计 `15/25/30/33/35/36` 分钟，最终全屏行为不回退。
5. 每个阶段完成后状态通知倒计时到下一阶段；最终阶段变为静态紧急文案。
6. 通知点击进入数字自律页；最终悬浮窗“前往”仍进入首页。
7. 任意有效数字自律锁存在时，守护无法关闭，且不消费每日关闭限额。
8. 无锁时的 `true → false` 关闭受每日限额约束；允许关闭后能被现有自动重开检查恢复。
9. 从未主动开启过守护的用户不会被自动重开逻辑自动 enrollment。
10. 首页“服务状态”在无障碍运行时点击关闭，只出现说明/系统设置入口，应用内不调用 `disableSelf()`。
11. 首页对关闭/故障的无障碍执行显式开启或修复，不使用可能反向关闭的 toggle。
12. Automation/Shizuku 模式、快捷设置磁贴和内部局部关闭语义不回退。
13. 权限恢复、守护关闭和 session reset 会清除状态通知、阶段通知、timer 与 overlay。
14. 没有 Room schema/version、Manifest 或 CI workflow 的无关改动。
15. GitHub Actions PR 检查的测试及三个构建变体全部绿色；合并后 main 的 `Build Latest APK` 也绿色。

## 7. Out of Scope

- 自动替用户开启或重新授予 Android 无障碍权限。
- 绕过 force-stop、后台前台服务限制、通知权限或用户的通知渠道设置。
- 修改第 36 分钟全屏悬浮窗的视觉设计和“前往首页”语义。
- 改变六次提醒的业务间隔。
- 新建无障碍守护专属锁定模式或 Room 实体。
- 将严格守护开放到 Play 渠道。
- 改变快捷设置磁贴的开关语义；若以后要求全应用所有用户入口都只能开启不能关闭，应作为独立需求审查磁贴行为。
