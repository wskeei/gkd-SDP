# Product Quality / Experience / Infrastructure / Release 执行审查

审查日期：2026-08-11

审查对象：`docs/plans/2026-08-09-product-quality-experience-infrastructure-release.md`

计划基线：`b9d98cb748a6e37c0eb88caac99dae156d915fc3`

审查 HEAD：`e7efc108a1a0e7e7dfd8cc55bcc32bb31cf43fef`（`main`、`origin/main`、`v2.2.1`）

## 结论

本轮执行包含大量实质工作，不是空壳提交。加密备份 v2、安全压缩包读取、支持包白名单、远程会话边界、统一运行时依赖、生命周期 Flow 收集、Room 32 → 33、设计组件、双 flavor 构建，以及后续加入的 GMD/性能工作流都已形成真实代码。

但当前状态**不能按原计划 38 个 Task 全部完成验收**。本次审查确认 7 个 P1 和 6 个 P2 问题，其中最主要的风险是：多个 CI 门禁虽然显示成功，却没有验证计划声称的行为或阈值。`v2.2.0` 在托管设备与性能作业加入前已经公开，`v2.2.1` 虽补上作业名称，但这些作业仍受本报告所述占位测试和非强制校验影响。

建议在下一次稳定发布前至少关闭全部 P1，并使性能、GMD、截图、Lint 和覆盖率门禁先具备真实失败能力；否则继续新增绿色检查不会增加可信发布证据。

严重度定义：P1 为下一次稳定发布前应关闭的安全、核心功能或验收失真问题；P2 为重要的产品、架构或流程偏差，应明确修复或形成经审查的范围调整。

## Findings

### P1-1：图片预览网络客户端绕过明文来源授权

关联计划：Task 7。

证据：

- `app/src/main/res/xml/network_security_config.xml:2-6` 对整个应用保留 `cleartextTrafficPermitted="true"`，安全边界完全依赖应用层客户端拦截。
- `app/src/main/kotlin/li/songe/gkd/sdp/util/Singleton.kt:28-36` 只给共享 Ktor/OkHttp 客户端安装了 `CleartextOriginInterceptor`。
- `app/src/main/kotlin/li/songe/gkd/sdp/ui/imagepreview/Sections.kt:73-99` 为 Coil 另建了一个没有该拦截器的 `OkHttpClient`；`Sections.kt:144-161` 使用它预取网络图片。
- `app/src/main/kotlin/li/songe/gkd/sdp/ui/imagepreview/Sections2.kt:58-111` 首图显示又使用 `context.imageLoader`，同样没有接入项目的明文来源策略。
- `app/src/main/kotlin/li/songe/gkd/sdp/ui/component/RuleGroupDialog.kt:161-168` 会把订阅提供的 `allExampleUrls` 直接交给图片预览，因此 URL 不是项目可信常量。

影响：订阅中的 `http://` 示例图片可在没有逐来源授权的情况下发起明文请求，撤销订阅来源授权也无法阻止该图片客户端。这破坏了“全局 cleartext 仅作为传输兼容，所有连接在应用层 fail closed”的安全边界。

修复要求：

- 图片预览默认仅允许本地 URI 与 HTTPS；如产品确需允许 HTTP 图片，必须使用独立、显式且可撤销的来源授权，不能依赖 manifest 全局放行。
- 前台加载、预取和重定向必须走同一策略与同一受控 client；移除 `context.imageLoader` 与自建裸 `OkHttpClient` 的策略分叉。
- 增加直连 HTTP、HTTPS → HTTP 重定向、授权后访问、撤销后立即拒绝，以及前台/预取两条路径的行为测试。

### P1-2：性能门禁不比较任何性能阈值，StrictMode 与首帧报告也是空实现

关联计划：Task 30、31、32。

证据：

- `scripts/verify-performance-reports.py:54-66` 只检查阈值 JSON 是否含有键；`26-46` 只检查指标存在和运行次数，未将 cold median/P95、warm P95 或 frame overrun P95 与阈值比较。
- 同一脚本没有比较 release APK 与 `origin/main` 的大小，没有检查 APK 中的 `assets/dexopt/baseline.prof`/`baseline.profm`，也没有读取 `config/quality/compose-stability-baseline.json`。
- `scripts/tests/test_performance_report_policy.py:10-69` 只覆盖“文件缺失”，没有任何超阈值应失败的测试。
- `app/src/debug/kotlin/li/songe/gkd/sdp/performance/DebugRuntimeChecks.kt:3-5` 的 `enable()` 是 `Unit`；`app/src/main/kotlin/li/songe/gkd/sdp/performance/AppDrawReporter.kt:3-5` 的 `reportFullyDrawn()` 也是 `Unit`。全仓搜索只找到定义，没有初始化或调用点。
- `baselineprofile/src/main/kotlin/li/songe/gkd/sdp/baselineprofile/BaselineProfileGenerator.kt:38-41` 用 `onElementOrNull(... )?.click()`，目标页面缺失时静默继续，因此 Profile 流程未真正保证遍历计划指定页面。
- `docs/testing/performance-baseline.md:21-25` 明确说明模拟器运行只要求生成指标、不因时序阈值失败，这与计划固定的发布门禁不同。

影响：GitHub 上 `performance: SUCCESS` 只能证明生成了一些文件和字段，不能证明启动、帧、APK 体积、Baseline Profile 打包或 Compose 稳定性满足阈值；debug 主线程 I/O、资源泄漏、Activity 泄漏和明文网络也没有运行时检测。

修复要求：

- 校验脚本逐项读取并比较所有阈值，输出测量值、阈值、样本数和失败原因；不可用或格式异常必须 fail closed。
- 对 checkout 的基线提交构建/下载同 variant APK，执行相对与绝对增长双门槛；解包当前 APK 验证 `.prof` 和 `.profm`。
- 解析 Compose compiler 报告并与稳定性基线比较，验证核心类型。
- 在真实首个可交互 `Content` 状态调用 Activity 的 `reportFullyDrawn()`；debug source set 安装计划规定的 StrictMode/诊断 listener，release source set保持零额外检测。
- Baseline Profile 的每个必经节点使用失败即终止的查找，并加入流程完成断言。
- 新测试至少覆盖每个指标刚好等于阈值、超过阈值、缺失、NaN/错误类型、APK 双门槛、缺 profile assets、Compose 回归。

### P1-3：托管设备“UI 流程”没有启动或操作 UI

关联计划：Task 26、28、31、32。

证据：

- `app/src/androidTest/.../ui/AppNavigationTest.kt:10-17` 只遍历 enum 并检查 `toNavKey()` 非空。
- `CapabilityFlowTest`、`DataDeletionFlowTest`、`SettingsSearchTest`、`UsageRequestFlowTest`、`ReviewDashboardFlowTest` 都只调用纯 policy/presenter；`EncryptedBackupFlowTest` 只调用 `BackupCrypto`。
- `AccessibilitySmokeTest.kt:13-25` 只检查常量，没有语义树断言。
- `NavigationRestoreTest` 没有启动 Activity 或执行 recreation。整个 `app/src/androidTest` 没有 `createAndroidComposeRule`、`ActivityScenario`、`onNode...`、`performClick` 或 `UiDevice` 使用。
- 计划要求的 `TestAppDependencies.kt` 和 `ManagedDeviceSuite.kt` 不存在；托管设备使用 `systemImageSource = "google"`（`app/build.gradle.kts:235-253`），也不是计划固定的 `aosp`。
- `docs/testing/managed-device-matrix.md:6-9` 把这些测试描述成 “full UI” 和 play 启动/导航/设置 smoke，与实际测试内容不符。
- `scripts/verify-test-quality-policy.py:12-17,44-46` 只禁止四个指定文件读取源码；当前仍有多份 JVM 测试通过 `sourceFile`/`readText()` 检查生产源码，脚本却报告 OK。脚本也没有实现计划要求的空测试、无断言测试、反射/实现名称检查。

影响：API 26/API 35 的绿色作业没有证明 Activity 能启动、导航可点击、状态可渲染、IME 可用、进程重建可恢复、play flavor 不显示 gkd 强权限动作，或隐私删除/备份 UI 可执行。

修复要求：

- 引入可替换的 App dependencies、内存 Room、临时 Store 与合成数据，在真实 Activity/Compose rule 上执行计划列出的完整 UI 流程。
- 用 suite 明确分配 API 26、API 35、gkd 和 play 用例，并确保 play smoke 真正启动四个一级目标和设置页。
- 对导航恢复使用 `ActivityScenario.recreate()` 或等价真实重建；对设置搜索执行输入、点击、滚动/高亮断言；对备份/删除/申请/复盘执行 UI 与持久化联合断言。
- 将测试质量脚本扩展到所有测试文件，禁止读取生产源码、无断言/空测试、真实网络和真实墙钟等待；为每条规则添加负向 fixture。
- 修正文档，只有真实 UI 流程通过后才能恢复 “full UI” 表述。

### P1-4：截图回归只保存了 7 张占位内容，不覆盖目标页面

关联计划：Task 27、31、32。

证据：

- 当前 `app/src/screenshotTestGkdDebug/reference` 只有 7 张 PNG，计划矩阵要求概览、自律、规则、设置、能力中心、使用申请、拦截、复盘、隐私页及宽度/主题/语言/字号的明确组合。
- `CoreScreensScreenshotTest.kt:14-35` 中名为 “Self-control hub compact” 和 “Settings dark en” 的预览只渲染隐私页标题/说明；“Settings dark en” 没有设置 `locale = "en"`。
- `OverlayScreensScreenshotTest.kt:14-35` 中名为拦截页和使用申请的预览也只渲染通用 `Text`。
- `PreviewFixtures.kt:20-53` 中的两个 Overview 状态没有渲染 `OverviewScreen`；仅 dense chart 是实际组件级截图。
- `docs/testing/screenshot-testing.md:20-22` 声称当前矩阵覆盖 compact/expanded、light/dark、核心页面与 overlay，超出了实际证据。

影响：实际页面发生布局溢出、组件丢失、语言混用、fontScale=2.0 截断或适配宽度回归时，`visual-regression: SUCCESS` 仍会保持绿色。

修复要求：

- 使用真实 Screen/section composable 或正式的纯渲染入口，并通过确定性的 UiState fixture 注入数据。
- 按计划逐张建立矩阵；每张命名必须与实际页面、状态、locale、uiMode、widthDp、fontScale 一致。
- 为 dialog/overlay 提供可预览的纯 UI 边界，不用隐私页字符串替代。
- CI 继续只 validate；修复 PR 需列出新增/变更的每张 reference。

### P1-5：自定义本地化 Lint 没有注册到 app lint，英文界面仍会混入大量中文

关联计划：Task 16、17、18。

证据：

- `quality-lint/build.gradle.kts:1-30` 没有为 jar 写入 `Lint-Registry-v2` manifest；`quality-lint/src` 也不存在 `META-INF/services/com.android.tools.lint.client.api.IssueRegistry`。
- `ComposeHardcodedTextDetector.kt:27` 仅把 `SCANNED_METHODS` 注册为适用方法，但 `93-107` 又列出大量不在 `SCANNED_METHODS` 中的自定义 UI 调用；这些方法永远不会触发 handler。
- 当前生产 Kotlin 中仍有 198 个文件包含中文字符（这个数字也包含注释、规则匹配文本等合理场景）。明确的用户可见硬编码示例包括 `app/src/main/kotlin/li/songe/gkd/sdp/ui/capability/CapabilityCenterScreen.kt:87-152`、`app/src/main/kotlin/li/songe/gkd/sdp/capability/Capability.kt:78-215`、`app/src/main/kotlin/li/songe/gkd/sdp/notif/Notif.kt:118-185`、`app/src/main/kotlin/li/songe/gkd/sdp/settings/SettingsIndex.kt:5-65` 和 `app/src/main/kotlin/li/songe/gkd/sdp/ui/home/ControlPage.kt:175-310`。
- 例如 `app/src/main/kotlin/li/songe/gkd/sdp/ui/capability/CapabilityCenterScreen.kt:135` 直接调用 `Text("重新检查")`，但 PR #40 的 `lintGkdDebug`/`lintPlayDebug` 仍成功，说明 app lint 没有把该规则作为发布门禁执行。

影响：计划要求的完整中英文资源迁移没有完成；切换英文后会出现中文能力状态、设置搜索项、通知和关键操作。当前 Lint 绿色不能防止继续引入硬编码文案。

修复要求：

- 正确注册 `IssueRegistry`，增加一个集成测试：构建 lint jar 后让 app fixture 上的 `Text("硬编码")` 必须失败，而不是只直接实例化 detector。
- 修复适用方法集合，使自定义组件调用可被扫描；补充 Toast、Notification、Dialog、semantics、字符串模板/拼接和 plurals 用例。
- 逐模块迁移所有用户可见文字；协议常量、规则匹配文本、SQL、route 等非 UI 文字应通过明确的窄豁免处理。
- 校验 `values` 与 `values-en` key 集合一致，并增加英文 locale 的真实截图/UI 测试。

### P1-6：语义“运行能力”目标映射到了旧 AppOps 页面

关联计划：Task 11、19、20。

证据：

- `app/src/main/kotlin/li/songe/gkd/sdp/navigation/AppDestination.kt:37-49` 把 `SETTINGS_CAPABILITIES` 映射为 `AppOpsAllowRoute`，而新的运行能力中心实际路由是 `app/src/main/kotlin/li/songe/gkd/sdp/ui/capability/CapabilityCenterRoute.kt:7`，并已在 `app/src/main/kotlin/li/songe/gkd/sdp/MainActivity.kt:311` 注册。
- `app/src/main/kotlin/li/songe/gkd/sdp/navigation/DeepLinkParser.kt:21-24` 会把 `gkd://settings/capabilities` 解析到这个语义目标。
- `app/src/main/kotlin/li/songe/gkd/sdp/service/StatusService.kt:92-95` 的权限受限通知和 `app/src/main/kotlin/li/songe/gkd/sdp/permission/PermissionState.kt:109-113` 的修复动作都会走该语义目标，因此会打开旧的应用列表特殊权限页，而不是能力中心。
- `app/src/test/kotlin/li/songe/gkd/sdp/navigation/DeepLinkParserTest.kt:75-78` 反而把错误映射固定成了期望值。

影响：通知、后台修复入口和语义深链无法到达计划要求的统一能力中心，用户得到与问题不匹配的页面。

修复要求：

- 将语义目标映射到 `CapabilityCenterRoute`，仅为真正的应用列表授权保留 `AppOpsAllowRoute`。
- 用真实 navigator/deep-link Activity 测试断言最终页面语义节点，而不只比较 NavKey。
- 回归首次启动、首页状态卡、StatusService 通知、PermissionState、legacy `gkd://page/3` 和两个 flavor。

### P1-7：“隐私与数据”页缺少计划中的核心控制，配置与全部删除永久不可用

关联计划：Task 5、7、21、26。

证据：

- `app/src/main/kotlin/li/songe/gkd/sdp/ui/privacy/PrivacyDataPresenter.kt:23-28` 无条件把订阅/规则配置、数字自律配置和全部应用数据设为不可删除，而不是只在活动会话或锁定时阻断。
- `app/src/main/kotlin/li/songe/gkd/sdp/privacy/DataInventoryRepository.kt:123-126` 对这三类操作直接抛错，没有实际 reset/delete-all 路径。
- `app/src/main/kotlin/li/songe/gkd/sdp/ui/privacy/PrivacyDataScreen.kt:81-102` 只展示说明和分类卡片；计划固定的加密备份、明文来源撤销、支持包白名单、重置配置和删除全部危险区均不存在。
- `app/src/main/kotlin/li/songe/gkd/sdp/privacy/DataInventoryRepository.kt:31-74,131-139` 对大多数数据库类别只返回数量，磁盘占用固定为 0，也没有最早/最新时间；这不满足每类的数量、空间和时间范围摘要。
- 当前删除流程没有实现计划要求的“删除使用记录后重算申请间隔/复盘”和“删除触发记录后重算首页统计”的显式成功边界。

影响：产品最敏感的数据管理入口只完成了部分历史删除；用户不能从该页完成计划承诺的备份、来源撤销、支持包审查、配置重置或全部删除，数据清单还可能给出误导性的 0 B 摘要。

修复要求：

- 实现计划规定的六段布局，并让配置/全部删除仅在对应活动状态下禁用。
- 把 reset/delete-all 放入统一 coordinator，保持无障碍 owner、锁定、enabled、每日额度和自动重开不变量；全部删除必须输入固定短语。
- 为每类提供真实数量、磁盘估算、最早/最新时间；无法可靠计算时明确显示“不可用”，不能伪装为 0。
- 对 Room + 文件联合删除使用可恢复的两阶段/补偿策略，并在提交成功后触发所需重算。
- 增加真实 UI、DAO/文件失败、活动状态阻断、配置保留、全部删除和恢复路径测试。

### P2-1：设置搜索结果点击只清空查询，没有跳转、滚动或高亮

关联计划：Task 21、26。

证据：

- `app/src/main/kotlin/li/songe/gkd/sdp/ui/settings/Sections2.kt:90-98` 的搜索结果 `onClick` 仅执行 `searchQuery = ""`。
- `app/src/main/kotlin/li/songe/gkd/sdp/settings/SettingsIndex.kt:5-55` 只有 7 个分组级条目，没有索引具体设置项、当前状态、锁定到期或目标锚点。
- 空查询没有调用 `SettingsSearchPolicy.recent()`；无结果也没有清除搜索动作。

影响：搜索看似存在，但不能把用户带到目标设置。计划要求的最近 5 项、状态摘要、锁定状态、1.5 秒高亮和重建恢复均未实现。

修复要求：为每个可搜索设置定义稳定 ID、分组、目标 route/anchor、状态 provider 和锁定信息；点击后导航或滚动到目标并高亮；持久化最近访问；增加 Activity recreation 与实际点击测试。

### P2-2：Kover 80/70 门禁只覆盖很窄的易测集合，配置文件和“新类不得 0%”没有生效

关联计划：Task 29、31、32。

证据：

- `app/build.gradle.kts:256-299` 硬编码 includes，仅覆盖 capability/settings/usage/runtime、部分 `*Policy*` 和一个 deletion coordinator；缺少计划规定的所有 `*Repository*`、`backup.*`、完整 `remote.*`、完整 `privacy.*` 及 UiState/Presenter。
- `config/quality/kover-includes.txt` 与 `kover-excludes.txt` 没有被 Gradle 或脚本读取，和构建脚本形成两套真相来源。
- `docs/testing/coverage-policy.md:8` 声称新 included class 不得 0%，但构建脚本没有对应规则或报告后处理。

影响：当前 80% line / 70% branch 可以在一个显著收窄的集合上通过，无法代表备份、远程、安全、仓库和 UI 状态层的业务覆盖率。

修复要求：让 Gradle 从受版本控制的唯一配置读取 include/exclude；恢复计划完整范围；对 XML 报告执行逐类 0% 检查；为范围和负向失败写脚本测试；修复覆盖率时不得扩大 excludes。

### P2-3：UI 拆分主要是按文件名切块，没有形成 UiState/UiAction/Presenter 架构

关联计划：Task 12、29。

证据：

- `app/src/main/kotlin/li/songe/gkd/sdp/ui/appblocker/Presenter.kt:1-7` 只是 `String -> String` 恒等函数，`UiState.kt:1-7` 只有未使用的 `isLoading`；focusmode、urlblocker、actionlog、advanced、imagepreview 等目录存在相同占位模式。
- `app/src/main/kotlin/li/songe/gkd/sdp/ui/appblocker/Sections.kt:46-75` 仍在 Composable 内创建 ViewModel、收集多个 Flow、持有编辑/对话框状态并直接调用 VM，不是“Compose 只渲染不可变 UiState 并发送 UiAction”。
- `app/src/main/kotlin/li/songe/gkd/sdp/ui/focuslock/UiState.kt` 甚至包含 Composable UI，而不是只定义 state/action/effect。
- AppBlocker 被拆成 `Sections2.kt`、`Sections3.kt`、`Editor2.kt` 等多个近 500 行文件，总计 2347 行；这满足了单文件数字，却没有建立职责边界。
- `app/src/main/kotlin/li/songe/gkd/sdp/ui/home/ControlPage.kt` 仍有 555 行。`scripts/verify-ui-file-boundaries.py:218-226` 只显式检查 `ui/home/SettingsPage.kt`，因此漏掉其它 `ui/home/*Page.kt`；脚本仍报告 OK。

影响：架构债务仍然存在，覆盖率也无法围绕稳定 presenter/state 建立；静态门禁鼓励继续通过后缀分片满足数字，而不是降低耦合。

修复要求：按功能逐页建立真实不可变 UiState、sealed UiAction/UiEffect 和 Presenter/VM 组合边界；Screen/Sections 只接受状态和回调。更新脚本递归覆盖所有 page/overlay host，并加入“Presenter/UiState 必须被生产代码引用、UiState 不得含 Composable、禁止恒等占位”的契约测试。

### P2-4：中/大屏导航壳存在布局与重复点击回归

关联计划：Task 19、27、28。

证据：

- `app/src/main/kotlin/li/songe/gkd/sdp/ui/home/AdaptiveHomeScaffold.kt:46-80` 在横向 `Row` 中放置 `HorizontalDivider()`；该组件会占用横向可用宽度，内容 Column 可能被压缩到零宽。这里应使用纵向分隔线并给内容明确 `weight(1f)`/宽度约束。
- 同文件 `54` 和 `91` 在目标已选中时完全不调用 `handleClickDestination`，因此“重复点击当前一级目标滚动到顶部”不会发生。
- 即使恢复调用，`app/src/main/kotlin/li/songe/gkd/sdp/MainViewModel.kt:224-230` 对所有 `HomeDestination` 都发出 `BottomNavItem.Control` 的 reset event，而不是目标对应事件，并且实现成 500 ms 内双击，不是普通的再次点击契约。
- 当前占位截图与非 UI GMD 测试无法发现这些问题。

影响：600dp 以上窗口可能无法正常显示主内容；所有宽度下重复点击当前一级目标都不能按计划回到顶部。

修复要求：改用 `VerticalDivider` 和有权重的内容容器；建立 `HomeDestination -> reset target` 的稳定映射；保留 ViewModel 与 back stack；用 700/1000dp 真正页面截图和 Compose UI 点击测试覆盖选择态、重复点击和动态宽度切换。

### P2-5：Automation 模式仍把无障碍守护显示为可用/已启用

关联计划：Task 20 及根级运行时不变量 8。

证据：

- `app/src/main/kotlin/li/songe/gkd/sdp/capability/Capability.kt:185-203` 的 A11y Guard 分支只判断 flavor、锁定和 enabled，没有判断 `chosenMode`。
- `app/src/test/kotlin/li/songe/gkd/sdp/capability/CapabilityResolverTest.kt:99-111` 在 Automation 模式下明确期待 A11y Guard 为 `ACTIVE`，把计划中的错误行为固化成测试。

影响：GKD 的 Automation/Shizuku owner 下会向用户展示只适用于 A11y 场景的守护状态或操作，能力图与真实运行边界不一致。

修复要求：非 gkd 或非 Accessibility 模式显示 unavailable/明确不适用；仍需保留“已选择 A11y 模式但服务暂时关闭时可以先开启守护”的既有能力。覆盖 mode × flavor × service-running × enabled × locked 矩阵。

### P2-6：发布顺序与审查证据不符合计划，2.2.0 在最终门禁加入前已公开

关联计划：Task 8、13、18、25、32、34-38。

证据：

- PR #37（2.2.0）和 #38 的检查只有 quality/build/coverage/visual-regression 等，没有 managed-device-api26、managed-device-api35、performance。
- `v2.2.0` 于 2026-08-10 23:34:56Z 公开；补充 GMD/性能作业的 PR #39 于 2026-08-11 02:31:10Z 才合并。
- PR #40/v2.2.1 才同时显示 GMD 与 performance 成功，但这些成功仍受 P1-2、P1-3、P1-4 和 P2-2 的门禁缺陷影响。
- PR #31-#40 的 `reviewDecision` 均为空，没有计划中“完成代码审查、解决 conversations”可核验的 GitHub 审查证据。
- 两个 release 都已 immutable；`v2.2.0` 不能删除、移动或覆盖。

影响：公开发布先于验收基础设施完成，且当前绿色状态不足以证明计划验收标准。此项是历史流程偏差，不能通过改 tag 修正。

修复要求：保留两个历史 Release；关闭本报告问题后使用新的稳定 PATCH 和更大的 `versionCode`。发布 PR 必须在 tag 前等待修复后的全部门禁，并保留独立 review/已解决 conversation、真实设备未验证项和资产验证记录。

GitHub 证据：

- PR #39：<https://github.com/wskeei/gkd-SDP/pull/39>
- PR #40：<https://github.com/wskeei/gkd-SDP/pull/40>
- v2.2.0：<https://github.com/wskeei/gkd-SDP/releases/tag/v2.2.0>
- v2.2.1：<https://github.com/wskeei/gkd-SDP/releases/tag/v2.2.1>

## 计划执行状态概览

| 计划区域 | 审查结论 | 主要依据 |
| --- | --- | --- |
| 安全、隐私与数据 | 部分完成 | 备份/压缩包/支持包/远程会话有实质实现；图片明文客户端和集中数据控制未闭环 |
| 架构与生命周期 | 部分完成 | 依赖注入、Flow 生命周期和导航边界有推进；UI Presenter/UiState 多为占位，语义能力路由错误 |
| 设计、内容与无障碍 | 部分完成 | Token/组件和部分 semantics 已加入；本地化 Lint 未成为 app 门禁，真实多语言/大字号截图不足 |
| 产品 IA 与体验 | 部分完成 | 四个一级目标和能力/隐私入口存在；自适应布局、能力模式、设置搜索和隐私危险区未满足验收 |
| 自动化、覆盖率与性能 | 不满足验收 | 作业存在，但 GMD、截图、Kover、性能阈值和 debug runtime checks 均存在实质覆盖缺口 |
| 发布 | 已发布但流程不合规 | 2.2.0 先于最终门禁，2.2.1 补作业但未修复门禁有效性；没有可核验 reviewDecision |

## 逐项修复执行手册

> **给执行 AI：**按本节顺序逐项执行。每个修复都必须先使用 `test-driven-development` 写出能复现问题的失败测试；遇到非预期失败先使用 `systematic-debugging`；准备声明完成前使用 `verification-before-completion`。高风险运行时修改还要重新阅读根级 `AGENTS.md` 和 `README_DEV.md`。不得把本节当成一次大提交。

### 通用执行规则

1. 每个 finding 从最新 `origin/main` 创建独立 `codex/<topic>` 分支或 worktree；开始前运行 `git status --short --branch`，保留用户已有文件。
2. 一个 PR 只关闭一个 finding，P1-2、P1-3、P1-7、P2-3 允许按本节指定的子阶段拆成多个连续 PR。
3. 每个行为修复都执行 red → green：先提交或至少保留失败输出，再实现最小修复，再跑同一测试确认通过。禁止先改生产代码再补只会通过的测试。
4. 不用源码字符串测试、截图名称、空 presenter、扩大 excludes、放宽阈值或删除断言制造绿色结果。
5. 每个 PR 至少运行针对性测试、双 flavor 编译、`git diff --check`；影响 UI 时加截图/GMD，影响运行时时加相关自律回归。
6. 每个 PR 描述列出：关闭的 finding、明确未改内容、自动化证据、真机未验证项、敏感数据检查。等待适用 Actions 和独立 review 后再合并。
7. 每个 finding 完成后更新下方跟踪表的 PR 和证据；只有验收命令真实通过才能把状态改为“完成”。
8. 用户可见行为修复同步写入 `CHANGELOG.md` 的 `[Unreleased]`；当前文件缺少该段，首个修复 PR 应先恢复它，但不得提前写入发布日期或版本号。

| Finding | 初始状态 | 修复 PR | 验收证据 |
| --- | --- | --- | --- |
| P1-1 图片明文请求绕过 | 待修复 |  |  |
| P1-2 性能与 debug runtime 门禁失真 | 待修复 |  |  |
| P1-3 GMD 非真实 UI 流程 | 待修复 |  |  |
| P1-4 截图为占位内容 | 待修复 |  |  |
| P1-5 本地化 Lint 未生效 | 待修复 |  |  |
| P1-6 能力中心语义路由错误 | 待修复 |  |  |
| P1-7 隐私与数据控制未完成 | 待修复 |  |  |
| P2-1 设置搜索不可操作 | 待修复 |  |  |
| P2-2 Kover 范围失真 | 待修复 |  |  |
| P2-3 UI 架构为机械分片 | 待修复 |  |  |
| P2-4 自适应导航回归 | 待修复 |  |  |
| P2-5 Automation 误报 A11y Guard | 待修复 |  |  |
| P2-6 发布流程与证据缺口 | 待修复 |  |  |

### 修复卡 P1-1：封闭图片预览的明文网络路径

**目标：**图片预览只允许本地资源和 HTTPS；任何 HTTP 直连或重定向在网络连接前失败，并且前台显示与预取使用同一个 client。

**修改文件：**

- Create: `app/src/main/kotlin/li/songe/gkd/sdp/remote/ImagePreviewNetworkPolicy.kt`
- Create: `app/src/main/kotlin/li/songe/gkd/sdp/remote/ImagePreviewHttpsOnlyNetworkInterceptor.kt`
- Modify: `app/src/main/kotlin/li/songe/gkd/sdp/ui/imagepreview/Sections.kt`
- Modify: `app/src/main/kotlin/li/songe/gkd/sdp/ui/imagepreview/Sections2.kt`
- Test: `app/src/test/kotlin/li/songe/gkd/sdp/remote/ImagePreviewNetworkPolicyTest.kt`
- Test: `app/src/test/kotlin/li/songe/gkd/sdp/ui/ImagePreviewRequestPolicyTest.kt`
- Create: `docs/design/security-boundaries.md`

**具体步骤：**

1. 写 `ImagePreviewNetworkPolicyTest`，固定允许 `https://host/path`，拒绝 `http://`、user-info、非 http(s) 网络 scheme、无 host 和畸形 URI；测试错误只暴露稳定错误码，不回显完整 URL。
2. 写重定向策略测试：初始 HTTPS 请求的下一跳若为 HTTP 也必须拒绝。把每一跳的 request URL 作为合成输入直接交给 policy/network interceptor，不访问真实网络。
3. 运行 `./gradlew :app:testGkdDebugUnitTest --tests '*ImagePreviewNetworkPolicyTest' --tests '*ImagePreviewRequestPolicyTest'`，确认因 policy 不存在或 HTTP 仍被允许而失败。
4. 实现纯 Kotlin `ImagePreviewNetworkPolicy` 和 `ImagePreviewHttpsOnlyNetworkInterceptor`。interceptor 必须作为 OkHttp **network interceptor** 注册，并在 `chain.proceed` 前检查当前 exchange 的 request URL；application interceptor 不会为每个重定向重新执行，不能用它代替这一层。
5. 在 `Sections.kt` 创建唯一 `imagePreviewImageLoader`：保留缓存、GIF 和超时配置，但给网络 fetcher 使用的 OkHttp client 安装 `addNetworkInterceptor(ImagePreviewHttpsOnlyNetworkInterceptor(...))`。不要复用订阅 `CleartextOriginAuthorizations`，因为图片客户端不是订阅更新客户端。
6. 在 `Sections2.kt` 删除 `context.imageLoader`，把同一个受控 loader 显式传给 `rememberAsyncImagePainter`；预取继续使用同一实例。
7. 在构造 `ImageRequest` 前调用纯 policy，以便 HTTP 立即呈现本地、可重试且不含 URL 的错误状态；interceptor 作为重定向和绕过的第二道防线。
8. 回归本地快照文件、`content://`/SAF、HTTPS 静态图、GIF、分页预取和“在外部打开”动作。外部打开仍交给系统浏览器，但应用自身不得先下载 HTTP 内容。
9. 更新安全文档，明确 manifest 的 cleartext 放行只服务订阅更新客户端，图片/WebView/update 均 HTTPS-only。

**验收命令：**

~~~bash
./gradlew :app:testGkdDebugUnitTest --tests '*ImagePreviewNetworkPolicyTest' --tests '*ImagePreviewRequestPolicyTest' --tests '*CleartextOriginPolicyTest'
./gradlew :app:compileGkdDebugKotlin :app:compilePlayDebugKotlin
./gradlew :app:lintGkdDebug :app:lintPlayDebug
git diff --check
~~~

**完成标准：**测试能证明 HTTP 直连和 HTTPS → HTTP 重定向都在连接前失败；`rg 'context.imageLoader|OkHttpClient.Builder' app/src/main/kotlin/li/songe/gkd/sdp/ui/imagepreview` 不再发现未受控加载路径；两个 flavor 编译通过。

**建议提交：**`fix: enforce image preview transport policy`。

### 修复卡 P1-2：让性能、Baseline Profile、首帧和 StrictMode 门禁真实生效

**目标：**`performance` 作业对所有计划阈值 fail closed；首个可交互页面真实调用 `reportFullyDrawn`；debug StrictMode 能产生类型化失败证据，release 无额外检测。

**修改文件：**

- Modify: `scripts/verify-performance-reports.py`
- Modify: `scripts/tests/test_performance_report_policy.py`
- Modify: `.github/workflows/ci.yml`
- Modify: `.github/workflows/nightly.yml`
- Modify: `baselineprofile/src/main/kotlin/li/songe/gkd/sdp/baselineprofile/BaselineProfileGenerator.kt`
- Modify: `app/src/main/kotlin/li/songe/gkd/sdp/performance/AppDrawReporter.kt`
- Modify: `app/src/debug/kotlin/li/songe/gkd/sdp/performance/DebugRuntimeChecks.kt`
- Create: `app/src/release/kotlin/li/songe/gkd/sdp/performance/DebugRuntimeChecks.kt` 或等价 build-type factory
- Modify: `app/src/main/kotlin/li/songe/gkd/sdp/App.kt`
- Modify: `app/src/main/kotlin/li/songe/gkd/sdp/MainActivity.kt`
- Modify: `app/src/main/kotlin/li/songe/gkd/sdp/ui/overview/OverviewScreen.kt` 及深链首屏的纯渲染入口
- Test: `app/src/test/kotlin/li/songe/gkd/sdp/performance/AppDrawReporterTest.kt`
- Test: `app/src/test/kotlin/li/songe/gkd/sdp/performance/DebugRuntimeChecksTest.kt`
- Modify: `docs/testing/performance-baseline.md`

**子阶段 A：先修报告校验器。**

1. 在 Python 测试中建立完整临时 fixture，写入合法 profile、benchmark JSON、当前/基线 APK、Compose 报告和阈值。
2. 分别写失败测试：cold median 超限、cold P95 超限、warm P95 超限、frame P95 超限、运行次数不足、NaN/字符串类型、APK 相对超限、APK 绝对超限、APK 缺 `baseline.prof`、缺 `baseline.profm`、unstable class 增长、核心 stable type 丢失。
3. 运行 `python3 -m unittest scripts.tests.test_performance_report_policy -v`，确认新增用例在旧脚本上失败。
4. 给脚本增加显式参数 `--current-apk`、`--baseline-apk`、`--compose-report`、`--compose-baseline`；所有输入缺失、多个歧义 benchmark 文件或非有限数值都失败。
5. 逐项比较 benchmark 数据。cold 使用 median 与 P95，warm 使用 P95，两个模式均检查 frameOverrun P95 和至少 10 个原始样本；输出“测量值 / 阈值 / 文件”。
6. 用 ZIP 读取两个 APK 大小并执行“相对增长 <=8% 且绝对增长 <=2 MiB”；打开当前 APK 的 `assets/dexopt` 检查两个 profile 资产非空。
7. 解析 Compose compiler 报告，比较 unstable class count，并逐个确认 `compose-stability-baseline.json` 的 `coreStableTypes` 为 stable。
8. 修改 CI：PR 使用 `merge-base HEAD origin/main` 的同 variant 基线 APK；main/Nightly 使用上一稳定 tag 或明确保存的基线，不得把当前 HEAD 与自己比较。把所有新参数显式传入脚本。

**子阶段 B：实现首个可交互页面报告。**

1. 为 `AppDrawReporter` 写测试：Loading、权限弹窗和后台初始化不得报告；同一页面首次进入 Content 只报告一次；Activity recreation 和不同深链实例各自可报告一次。
2. 把 reporter 改为 Activity 实例范围，不使用进程级“永远只报一次”单例；注入实际回调 `activity.reportFullyDrawn()`。
3. 在 Overview 以及可被深链直接打开的页面，由 Presenter/UiState 提供 `isInteractiveContent`，在 Compose `LaunchedEffect`/`ReportDrawnWhen` 上报告。不得用固定延时。
4. 增加 Activity/Compose 测试，先显示 Loading，再切到 Content，断言调用时点与次数。

**子阶段 C：实现 debug runtime checks。**

1. 在 main 定义小接口和类型化事件（main-thread disk、main-thread network、leaked closable、Activity leak、cleartext network），debug/release source set 各提供 factory，避免 release 引用不存在的 debug 类。
2. debug 实现安装 `StrictMode.ThreadPolicy` 和 `VmPolicy`，使用 `penaltyListener` 把违规交给 `DiagnosticLogger` 和测试 listener；不得记录 URL、路径、屏幕文本或凭据，不使用生产 `penaltyDeath`。
3. release factory 为 no-op，并增加构建/字节码契约，确认 release 不安装 listener。
4. 在 `App.onCreate` 只调用 build-type factory；测试通过注入 listener 触发合成 violation，断言类型、脱敏内容和失败信号。

**子阶段 D：让 Profile 流程 fail closed。**

1. 把 `clickText` 改为 `clickRequiredText`：超时即抛出带稳定步骤名的 AssertionError，不使用安全调用。
2. 按计划补齐使用申请设置、规则两个 tab、能力中心和隐私数据；每一步点击后等待目标页面唯一语义节点。
3. 在流程末断言已经访问完整步骤集合；不要把理由、应用列表或设备信息写入报告。

**验收命令：**

~~~bash
python3 -m unittest scripts.tests.test_performance_report_policy -v
./gradlew :app:testGkdDebugUnitTest --tests '*AppDrawReporterTest' --tests '*DebugRuntimeChecksTest'
./gradlew :app:generateGkdReleaseBaselineProfile -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.enabledRules=BaselineProfile
./gradlew :baselineprofile:pixel6Api35GkdNonMinifiedReleaseAndroidTest -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.enabledRules=Macrobenchmark
./gradlew :app:assembleGkdRelease
python3 scripts/verify-performance-reports.py --thresholds config/quality/performance-thresholds.json --compose-baseline config/quality/compose-stability-baseline.json --current-apk <current-apk> --baseline-apk <baseline-apk> --compose-report <compose-report>
git diff --check
~~~

**完成标准：**每个超阈值 fixture 会失败；当前完整 fixture 通过；APK 内两个 profile 资产被实际检查；Profile 目标节点缺一即失败；debug violation 能到测试 listener；release 双 flavor 构建不含检测开销。

**建议提交：**先 `test: cover performance policy failures`，再 `perf: enforce release performance gates`，最后 `perf: report interactive draw and enable debug runtime checks`。

### 修复卡 P1-3：把 GMD 作业改成真实 Activity/Compose 行为测试

**目标：**API 26/API 35 的 gkd/play 作业真实启动应用、点击 UI、验证持久化与 recreation，而不是只在设备上运行纯 policy。

**修改文件：**

- Create: `app/src/androidTest/kotlin/li/songe/gkd/sdp/TestAppDependencies.kt`
- Create: `app/src/androidTest/kotlin/li/songe/gkd/sdp/ManagedDeviceSuite.kt`
- Create: `app/src/androidTest/kotlin/li/songe/gkd/sdp/SdpUiTestHostActivity.kt`
- Create: `app/src/androidTest/AndroidManifest.xml`
- Modify: `app/src/main/kotlin/li/songe/gkd/sdp/runtime/AppDependencies.kt`
- Modify: `app/src/main/kotlin/li/songe/gkd/sdp/MainActivity.kt`
- Modify: `app/src/androidTest/kotlin/li/songe/gkd/sdp/ui/*.kt`
- Modify: `app/src/androidTest/kotlin/li/songe/gkd/sdp/navigation/NavigationRestoreTest.kt`
- Modify: `app/build.gradle.kts`
- Modify: `scripts/verify-test-quality-policy.py`
- Modify: `scripts/tests/test_test_quality_policy.py`
- Modify: `docs/testing/managed-device-matrix.md`

**具体步骤：**

1. 先扩展 `verify-test-quality-policy.py` 的负向 fixture：任意测试读取 `app/src/main`、空 `@Test`、无断言/无 Compose 节点操作、反射实现名、`Thread.sleep` 或真实网络时必须失败。移除“四个文件白名单式检查”。
2. 运行 Python 测试，确认当前源码读取测试和占位 instrumentation 测试被新规则识别；不要通过新增文件名豁免修复。
3. 扩展现有 `runtime/AppDependencies.kt`，只增加 Screen/Presenter 所需的 repository、file root 和 network gateway；`App.onCreate` 安装 production 实现，`MainActivity` 把同一依赖对象显式传到导航根节点。不得加入 `isTest` 条件，也不得绕过锁定、权限、数据库 migration 或业务 policy。
4. 建立 `TestAppDependencies` factory：返回隔离的 `AppDependencies` 及其 cleanup handle，内部使用 `FakeSdpClock`、`StandardTestDispatcher`、测试 scope、内存 Room、临时 store/file roots、合成 app/subscription 数据和遇到公网请求立即失败的 network fake。每个测试结束调用 handle 取消 scope、关闭数据库并删除临时文件。
5. 创建 `SdpUiTestHostActivity` 并只在 `app/src/androidTest/AndroidManifest.xml` 注册。该 Activity 使用与 `MainActivity` 相同的导航根节点和 production Screen，把 factory 返回的依赖作为函数参数传入，不覆盖进程级 `installAppDependencies`，避免影响已启动的 runtime owner；不要继承或替换生产 `App`，也不要维护第二套测试专用页面。
6. 重写 `AppNavigationTest`：先用 `createAndroidComposeRule<MainActivity>()` 做真实冷启动和四个一级目标 smoke；再用 `createAndroidComposeRule<SdpUiTestHostActivity>()` 逐个点击概览/自律/规则/设置，验证 selected 语义和目标内容，进入二级页后 Back 回所属一级目标。
7. 重写 `NavigationRestoreTest`：在 test host 中设置 tab、搜索词、列表位置和二级路由，调用 `ActivityScenario.recreate()`，断言 back stack、`SavedStateHandle` 和可保存 UI 状态恢复，ViewModel 没有因重复点击重建。
8. 重写能力、设置、备份、删除、申请和复盘测试：必须对生产 Compose 节点执行输入/点击，并查询 `TestAppDependencies` 的内存 Room/临时 Store 验证写入或不写入。错误密码、重复提交、取消、活动会话阻断和 dense/empty 数据都要覆盖。
9. 重写 `AccessibilitySmokeTest`：检查触摸目标、contentDescription、selected/stateDescription、图表等价文字和 fontScale=2.0 可滚动；不要只断言常量。
10. 创建 `ManagedDeviceSuite` 聚合明确的测试类。API 26 增加 edge-to-edge、IME resize、SAF、通知兼容；API 35 增加 predictive back 配置、动态宽度和 WebView 安全；play 套件必须真实启动四个一级目标且找不到 gkd-only 守护动作。
11. 将设备改为计划固定的 Pixel 2/API26/aosp/x86_64 与 Pixel 6/API35/aosp/x86_64；固定单设备并发、英文系统 locale、每次清 app data 和禁止公网。
12. 删除或重写剩余源码字符串契约测试。确需检查 manifest/XML 时改为资源解析或 Android 组件查询，不读取 Kotlin 生产源码。
13. 文档只列实际 suite 类与真实操作；在报告中区分 UI 模拟器证据和 Accessibility/Shizuku/OEM 真机证据。

**验收命令：**

~~~bash
python3 scripts/verify-test-quality-policy.py
python3 -m unittest scripts.tests.test_test_quality_policy -v
./gradlew :app:testGkdDebugUnitTest
./gradlew :app:compileGkdDebugAndroidTestKotlin :app:compilePlayDebugAndroidTestKotlin
./gradlew :app:pixel2Api26GkdDebugAndroidTest
./gradlew :app:pixel6Api35GkdDebugAndroidTest
./gradlew :app:pixel6Api35PlayDebugAndroidTest
git diff --check
~~~

**完成标准：**至少有真实 Activity 启动、节点点击、文本输入、recreation、Room/Store 断言；把关键节点或按钮故意改名时测试会失败；play 测试真实证明强权限动作不可见；test-quality 脚本扫描全部测试。

**建议提交：**`test: enforce behavioral test quality`、`test: add injected managed-device UI flows`。

### 修复卡 P1-4：用真实页面替换 7 张占位截图

**目标：**每张 reference 渲染命名所指的真实产品 UI，并覆盖计划矩阵，不再用通用隐私文字代替页面。

**修改文件：**

- Modify: `app/src/screenshotTest/kotlin/li/songe/gkd/sdp/PreviewFixtures.kt`
- Modify: `app/src/screenshotTest/kotlin/li/songe/gkd/sdp/CoreScreensScreenshotTest.kt`
- Modify: `app/src/screenshotTest/kotlin/li/songe/gkd/sdp/OverlayScreensScreenshotTest.kt`
- Create: `app/src/screenshotTest/kotlin/li/songe/gkd/sdp/ChartScreensScreenshotTest.kt`
- Modify/Create: 各目标页面的纯 `*Content(UiState, onAction)` 渲染入口
- Replace: `app/src/screenshotTestGkdDebug/reference/**`
- Modify: `docs/testing/screenshot-testing.md`

**具体步骤：**

1. 建立类型化 `ScreenshotCaseId` 与 case registry，枚举计划中的用例 ID；结构测试断言每个 ID 恰好注册一次、包含 renderer/locale/width/theme/fontScale 元数据，且总数不超过 48。不要读取 Kotlin 源码字符串来判定覆盖。
2. 抽取或复用真实页面的纯渲染入口。截图函数只能传确定性 UiState 和 no-op action，不能自己拼一组无关 `Text`。
3. 在 `PreviewFixtures` 固定 `FakeSdpClock=2026-08-09T12:00:00+08:00`、Asia/Shanghai、合成应用/规则/记录；不得使用真实时间、网络、用户应用列表或理由。
4. 逐组实现：Overview ready/actionRequired；Self-control compact light zh/expanded dark en；Rules 两个 tab；Settings 默认/搜索结果；Capability A11y/Automation；Usage request empty/dense/fontScale2+IME reserve；Interception 完整/缺来源；Review empty/2 条/30 天 dense；Privacy 默认/删除确认。
5. 给每个 Preview 明确 `widthDp`、`uiMode`、`locale` 和 `fontScale`。名称带 “en” 的 Preview 必须设置 `locale = "en"`。
6. 对 overlay/dialog 抽取 Screen 层，不启动 Service/WindowManager；渲染的 layout、semantics 和文字必须与生产调用相同。
7. 删除原 7 张占位 reference，在 Ubuntu/JDK21 的固定环境运行 update；人工逐张核对页面与名称，再提交 PNG。
8. CI 继续只运行 validate。case registry 测试检查 renderer 非空以及名称中的 locale/theme/width 与结构化元数据一致；页面真实性由 renderer 必须调用 production `*Content` 的类型边界、reference review 和 validate 共同保证，不新增读取生产源码的脆弱测试。
9. 更新文档为准确的 reference 清单，不声称未覆盖的 OEM/`FLAG_SECURE` 行为。

**验收命令：**

~~~bash
./gradlew :app:updateGkdDebugScreenshotTest
./gradlew :app:validateGkdDebugScreenshotTest
./gradlew :app:testGkdDebugUnitTest --tests '*ScreenshotMatrix*'
git diff --check
~~~

**完成标准：**reference 数量和用例清单一致；每张图能从源码追溯到真实 Screen/Content；删除某个关键 section、切错 locale 或制造 2.0 字号溢出会导致 validate 失败。

**建议提交：**`test: replace placeholder screenshot baselines`。

### 修复卡 P1-5：注册 Lint 并完成用户文案资源化

**目标：**自定义 `HardcodedText` 真正参与 app lint，中英文 key 完整一致，生产用户界面不再混入硬编码中文。

**修改文件：**

- Modify: `quality-lint/build.gradle.kts`
- Modify: `quality-lint/src/main/kotlin/li/songe/gkd/sdp/lint/SdpIssueRegistry.kt`
- Modify: `quality-lint/src/main/kotlin/li/songe/gkd/sdp/lint/ComposeHardcodedTextDetector.kt`
- Modify: `quality-lint/src/test/kotlin/li/songe/gkd/sdp/lint/ComposeHardcodedTextDetectorTest.kt`
- Create: `quality-lint/src/test/kotlin/li/songe/gkd/sdp/lint/SdpIssueRegistryTest.kt`
- Create: `scripts/verify-localization-resources.py`
- Create: `scripts/tests/test_localization_resources.py`
- Modify: `app/src/main/res/values/strings.xml`、`values-en/strings.xml`、两个 plurals 文件
- Modify: 所有被新 Lint 报告的生产用户文案
- Modify: `docs/design/content-style-guide.md`

**具体步骤：**

1. 写 registry 测试，断言 `SdpIssueRegistry().issues` 包含 `HardcodedText`，`api`/`minApi` 合法；写打包策略测试，要求 jar manifest 包含 `Lint-Registry-v2=li.songe.gkd.sdp.lint.SdpIssueRegistry`。
2. 在 `quality-lint/build.gradle.kts` 配置 jar manifest，并在 Registry 中显式覆盖 `api = CURRENT_API`；不要把 lint 依赖打进 APK。
3. 扩充 detector 负向测试：自定义 `SettingItem`/`InlineMessage`、Toast、Notification、Dialog、`contentDescription`、`onClickLabel`、字符串模板/拼接和数量文字都必须报错。
4. 修复方法分发：`getApplicableMethodNames()` 返回 `SCANNED_METHODS + UI_CALLS` 的去重合集，或改为扫描所有 `UCallExpression` 后在 handler 内匹配；不能保留“UI_CALLS 永远不会回调”的结构。
5. 增加窄豁免测试：SQL、route、稳定错误码、正则、无障碍目标匹配文字和测试 fixture 不报；豁免依据调用上下文/注解，不使用整文件 `@Suppress`。
6. 写资源一致性脚本：比较 `values`/`values-en` string 与 plurals key、检测空英文值和不合法格式参数；脚本必须有缺 key、参数不一致的失败 fixture。
7. 先运行 `:app:lintGkdDebug`，保存现有错误清单。按计划顺序迁移 Manifest/通知/Service/Overlay、首页/设置/能力/导航、自律/规则、订阅/快照/WebView、通用组件。
8. 状态/Presenter 不直接保存中文；改为稳定 enum/typed model，由 Android UI 层映射 `@StringRes`/`stringResource`。数量使用 plurals，日期/数字使用 locale formatter。
9. 将 `SettingsIndex`、`CapabilityNode` 等需要双语搜索的领域数据拆成稳定 ID + 本地化 search document，避免为了搜索再次硬编码两套 UI 文案。
10. 增加英文 locale 的真实 Compose UI 与截图测试，覆盖能力中心、设置、通知文案映射和自律核心流程。
11. 更新内容指南，并在 CI quality job 中先构建 lint jar，再执行两个 flavor lint 与资源一致性脚本。

**验收命令：**

~~~bash
./gradlew :quality-lint:test :quality-lint:jar
python3 scripts/verify-localization-resources.py
python3 -m unittest scripts.tests.test_localization_resources -v
./gradlew :app:lintGkdDebug :app:lintPlayDebug
./gradlew :app:testGkdDebugUnitTest
git diff --check
~~~

**完成标准：**jar manifest 可读到 Registry；在临时 fixture 加入 `Text("硬编码")` 会让 app lint 失败；values/en key 与格式参数一致；英文真实 UI 不出现能力、设置、通知和自律操作中文。

**建议提交：**`build: activate product copy lint`、`refactor: complete user-facing localization`。

### 修复卡 P1-6：把所有能力语义入口路由到 CapabilityCenter

**目标：**`SETTINGS_CAPABILITIES`、`gkd://settings/capabilities`、legacy `gkd://page/3`、通知和权限修复动作都落到 `CapabilityCenterRoute`。

**修改文件：**

- Modify: `app/src/main/kotlin/li/songe/gkd/sdp/navigation/AppDestination.kt`
- Modify: `app/src/test/kotlin/li/songe/gkd/sdp/navigation/DeepLinkParserTest.kt`
- Modify: `app/src/androidTest/kotlin/li/songe/gkd/sdp/ui/AppNavigationTest.kt`
- Test: `app/src/androidTest/kotlin/li/songe/gkd/sdp/navigation/CapabilityDeepLinkTest.kt`

**具体步骤：**

1. 先把 JVM 期望从 `AppOpsAllowRoute` 改为 `CapabilityCenterRoute`，并增加 semantic URI 与 legacy URI 的 NavKey 断言；运行测试确认失败。
2. 在 `AppDestination.kt` 导入 `CapabilityCenterRoute`，仅修改 `SETTINGS_CAPABILITIES` 分支。不要改变真正的 `LegacyDeepLinkTarget.APP_OPS`。
3. 搜索所有 `SETTINGS_CAPABILITIES` 和 `gkd://settings/capabilities` 调用方，逐个记录预期：StatusService、PermissionState、首页、首次启动和设置入口都应进入能力中心。
4. 增加 Activity deep-link 测试：用 intent 启动 semantic/legacy URI，断言能力中心唯一标题/语义节点存在，旧 AppOps 页面节点不存在。
5. 测试 Back 返回所属一级 Settings/Overview 的现有规则，确保修复不重复 push route。
6. 分别在 gkd/play fixture 验证页面可打开，play 只隐藏/禁用不适用节点，不因 route 缺失崩溃。

**验收命令：**

~~~bash
./gradlew :app:testGkdDebugUnitTest --tests '*DeepLinkParserTest'
./gradlew :app:pixel6Api35GkdDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=li.songe.gkd.sdp.navigation.CapabilityDeepLinkTest
./gradlew :app:pixel6Api35PlayDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=li.songe.gkd.sdp.navigation.CapabilityDeepLinkTest
./gradlew :app:compileGkdDebugKotlin :app:compilePlayDebugKotlin
git diff --check
~~~

**完成标准：**所有语义/legacy/通知入口都显示 CapabilityCenter；`AppOpsAllowRoute` 只剩真正的应用列表特殊权限用途；旧错误断言被删除。

**建议提交：**`fix: route capability links to capability center`。

### 修复卡 P1-7：完成“隐私与数据”清单、备份、来源、支持包和危险区

**目标：**实现计划固定的六段布局与真实数据操作；删除/重置具有活动状态阻断、Room/文件一致性、成功后重算和可恢复失败语义。

**修改文件：**

- Modify: `app/src/main/kotlin/li/songe/gkd/sdp/privacy/DataDeletionCoordinator.kt`
- Modify: `app/src/main/kotlin/li/songe/gkd/sdp/privacy/DataInventoryRepository.kt`
- Create: `app/src/main/kotlin/li/songe/gkd/sdp/privacy/DataMutationCoordinator.kt`
- Create: `app/src/main/kotlin/li/songe/gkd/sdp/privacy/DataInventoryDataSource.kt`
- Modify: 相关 DAO 的 count/min/max/estimated-size/delete 查询
- Modify: `app/src/main/kotlin/li/songe/gkd/sdp/ui/privacy/PrivacyDataPresenter.kt`
- Modify: `app/src/main/kotlin/li/songe/gkd/sdp/ui/privacy/PrivacyDataScreen.kt`
- Reuse/Modify: `backup/BackupImportCoordinator.kt`、`util/BackupUtils.kt`
- Reuse: `remote/CleartextOriginPolicy.kt`、`diagnostics/SupportBundleBuilder.kt`
- Test: `app/src/test/kotlin/li/songe/gkd/sdp/privacy/DataInventoryRepositoryTest.kt`
- Test: `app/src/test/kotlin/li/songe/gkd/sdp/privacy/DataMutationCoordinatorTest.kt`
- Modify: `app/src/test/kotlin/li/songe/gkd/sdp/privacy/DataDeletionCoordinatorTest.kt`
- Rewrite: `app/src/androidTest/kotlin/li/songe/gkd/sdp/ui/DataDeletionFlowTest.kt`
- Modify: `PRIVACY.md`、`CHANGELOG.md`

**子阶段 A：先建立可测试的数据清单。**

1. 将 `CategoryStatus.bytes` 改为可表达“精确/估算/不可用”的 typed value，不能再用 0 代表未知；保留 count、earliestAt、latestAt 和 active blocker。
2. 给每类写 repository 失败测试，使用合成 DAO/file source，断言数量、时间范围、大小口径和敏感字段不进入 UI model。
3. 为 Room 类别增加 `COUNT/MIN/MAX` 投影；大小采用明确标注的行内容估算。快照/诊断使用文件真实大小；`ALL_APP_DATA` 使用数据库文件和受管目录总大小。UI 对估算显示“约”，不可计算显示“不可用”。
4. 把 `DbSet`、真实目录和墙钟包进 `DataInventoryDataSource`，Repository 只组合结果，测试不碰用户数据库。

**子阶段 B：建立唯一变更协调器。**

1. 为 `DataMutationCoordinator` 写 red tests：历史删除保留 enabled/locked/额度/自动重开；活动使用/专注/锁定时对应历史或配置操作被阻断；全部删除要求固定短语；文件失败不提交 Room；Room 失败可恢复文件。
2. 复用 `StorageMutationBarrier`/备份 mutation gate，串行化备份导入、导出、删除和配置重置，避免并发写入。
3. 对纯 Room 类别使用单事务；对 Room + 文件类别先把文件原子移动到同文件系统 quarantine，再提交 Room，成功后删除 quarantine，事务失败则移回。禁止先永久删文件再尝试数据库。
4. 使用申请记录活动时不允许删除其事实来源；专注活动时同理。UI 显示阻断原因，结束后用户重试，不新增不可靠的进程内待办。
5. 删除使用历史成功后调用统一的 interval/review/home recompute 或使其 repository invalidation 明确发生；删除 trigger/history 后刷新首页统计。为“调用一次且只在成功后”写测试。
6. 配置重置分别实现“订阅与规则配置”和“数字自律配置”，先枚举精确表/Store/文件；活动锁定时阻断，不通过清空数据库绕过 migration 或锁定。
7. `ALL_APP_DATA` 复用各类别操作和统一恢复日志，不调用 destructive migration，不删除权限状态或直接关闭无障碍 owner；操作开始前再次读取 blocker，避免确认后状态变化竞态。

**子阶段 C：完成页面六段布局。**

1. Presenter 输出一个不可变 UiState：本机说明、备份状态、数据类别、cleartext origins、支持包白名单、危险区状态、进行中动作和可重试错误。
2. 把 Settings 旧备份 Dialog 的业务复用到 Privacy 页“导出/导入/上次结果”，不要复制第二套 BackupCrypto/ImportCoordinator。
3. 订阅 `CleartextOriginAuthorizations.originsFlow`，逐项显示 canonical origin 和撤销按钮；撤销后立即刷新订阅更新状态，不显示完整订阅 URL。
4. 支持包区先展示 `SupportBundleManifest` 白名单，再由用户显式生成；完成提示只显示文件名/大小，不显示绝对路径。
5. 数据清单卡显示 count、大小、最早/最新、查看与删除；活动阻断按钮 disabled 且有文字原因。
6. 危险区提供两个配置重置和全部删除；全部删除必须输入 `删除全部数据`，确认 Dialog 再显示一次类别/数量。
7. 错误使用 typed code 映射本地化文案，提供重试；不得把异常 message、URL、理由或路径直接呈现。
8. 把 `CHANGELOG.md` 2.2.0 已声称但未实现的能力作为下一版本修复说明，不重写或覆盖已发布资产。

**验收命令：**

~~~bash
./gradlew :app:testGkdDebugUnitTest --tests '*DataInventoryRepositoryTest' --tests '*DataMutationCoordinatorTest' --tests '*DataDeletionCoordinatorTest' --tests '*PrivacyDataPresenterTest'
./gradlew :app:pixel6Api35GkdDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=li.songe.gkd.sdp.ui.DataDeletionFlowTest
./gradlew :app:compileGkdDebugKotlin :app:compilePlayDebugKotlin
./gradlew :app:lintGkdDebug :app:lintPlayDebug
git diff --check
~~~

**完成标准：**六段布局全部存在；三个配置/全部数据动作在无活动状态可执行、在活动状态不可执行；每类摘要不再伪造 0 B；Room/文件任一失败都可恢复；成功后重算只发生一次；双 flavor 编译和真实 UI 流程通过。

**建议提交：**`test: define privacy data mutation contracts`、`feat: complete privacy data inventory and controls`。

### 修复卡 P2-1：让设置搜索可以定位、恢复和高亮具体设置

**目标：**搜索匹配具体设置，结果显示分组/状态/锁定信息；点击后滚动或导航并高亮 1.5 秒；空查询显示真实最近 5 项。

**修改文件：**

- Modify: `app/src/main/kotlin/li/songe/gkd/sdp/settings/SettingsSearchPolicy.kt`
- Modify: `app/src/main/kotlin/li/songe/gkd/sdp/settings/SettingsIndex.kt`
- Create: `app/src/main/kotlin/li/songe/gkd/sdp/settings/SettingsTarget.kt`
- Modify: `app/src/main/kotlin/li/songe/gkd/sdp/ui/settings/Sections2.kt` 及各设置 section
- Modify: `app/src/main/kotlin/li/songe/gkd/sdp/store/SettingsStore.kt` 或使用既有普通 store 保存最近 ID
- Test: `app/src/test/kotlin/li/songe/gkd/sdp/settings/SettingsSearchPolicyTest.kt`
- Rewrite: `app/src/androidTest/kotlin/li/songe/gkd/sdp/ui/SettingsSearchTest.kt`

**具体步骤：**

1. 先扩展 `SettingsEntry`：稳定 `id`、group、localized search document、`SettingsTarget`、状态摘要、`lockedUntil`。测试拒绝重复 ID、无目标和超过 5 个 recent。
2. 将索引从 7 个分组扩展到每个实际可操作设置；标题由资源层提供，领域层不硬编码中英文 UI 文案。
3. 定义 `SettingsTarget.InPage(anchorId)` 和 `SettingsTarget.Route(AppDestination/NavKey)`。每个 section/item 使用稳定 key/anchor。
4. 将 Settings 主列表改为可按 key 定位的 `LazyColumn`，或为现有 scroll 容器维护可靠坐标；优先 `LazyColumn.animateScrollToItem(index)`，避免像素硬编码。
5. 点击搜索结果时记录 recent ID、关闭结果层、导航或滚动目标；设置 `highlightedId`，1.5 秒后清除。Compose 测试用 `mainClock` 推进时间，不在测试 `sleep`。
6. 空查询读取最近访问的 5 个稳定 ID；不存在/已移除 ID 过滤掉。无结果显示本地化空态和“清除搜索”动作。
7. 保存 `searchQuery`、目标和 scroll state；Activity recreation 后恢复查询/结果或已定位页面，不重复导航。
8. locked 项仍显示结果，但 action disabled，摘要显示到期时间；当前状态来自 flow/presenter，不写进静态索引。

**验收命令：**

~~~bash
./gradlew :app:testGkdDebugUnitTest --tests '*SettingsSearchPolicyTest' --tests '*SettingsFormPolicyTest'
./gradlew :app:pixel6Api35GkdDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=li.songe.gkd.sdp.ui.SettingsSearchTest
./gradlew :app:validateGkdDebugScreenshotTest
git diff --check
~~~

**完成标准：**真实输入“通知/notification/隐私”等可点击到具体设置；目标高亮时长由测试验证；recent 来自实际点击；recreation 后状态保留；locked 信息正确。

**建议提交：**`fix: make settings search actionable`。

### 修复卡 P2-2：恢复计划规定的 Kover 范围和逐类 0% 门禁

**目标：**覆盖率只使用一份 include/exclude 配置，并覆盖全部 Policy、Repository、backup、remote、capability、settings、privacy、usage、UiState/Presenter；任何新增 included 类 0% 都失败。

**修改文件：**

- Modify: `config/quality/kover-includes.txt`
- Modify: `config/quality/kover-excludes.txt`
- Modify: `app/build.gradle.kts`
- Create: `scripts/verify-kover-report.py`
- Create: `scripts/tests/test_kover_report_policy.py`
- Modify: `.github/workflows/ci.yml`
- Modify: `docs/testing/coverage-policy.md`

**具体步骤：**

1. 先写 Python fixture：完整 included 类有覆盖通过；任一 included 类 0 line、缺失于 XML、配置重复/空行错误、生产包被宽泛 exclude 时失败。
2. 把 includes 改为计划完整范围：`*Policy*`、`*Repository*`、`runtime.*`、`backup.*`、`remote.*`、`capability.*`、`settings.*`、`privacy.*`、`usage.*`、`*UiState*`、`*Presenter*`。
3. 收窄 excludes：只排除 Android host、生成类、Preview、图标和明确的纯资源 facade。不能用 `li.songe.gkd.sdp.ui.**`，否则 UiState/Presenter 永远进不来。
4. 在 Gradle 配置阶段读取两个文本文件，过滤注释/空行并作为 Kover filter；删除 build.gradle 中重复硬编码列表。
5. `verify-kover-report.py` 读取 Kover XML：先验证全局 line >=80、branch >=70，再枚举源码/配置命中的生产类，要求每类出现在报告且 line covered >0。
6. 首次扩大范围后运行报告并保存失败清单；逐类补行为测试，不降低阈值、不扩大 excludes、不把生产类标记 generated。
7. CI coverage job 在生成 XML 后调用验证脚本；无 XML 或配置无法匹配必须失败，报告 artifact 保留 7 天。
8. 文档说明唯一配置、匹配规则和新增类责任，并列出实际 excludes 理由。

**验收命令：**

~~~bash
python3 -m unittest scripts.tests.test_kover_report_policy -v
./gradlew :app:koverXmlReportGkdDebug :app:koverHtmlReportGkdDebug
python3 scripts/verify-kover-report.py --xml app/build/reports/kover/reportGkdDebug.xml --includes config/quality/kover-includes.txt --excludes config/quality/kover-excludes.txt
./gradlew :app:koverVerifyGkdDebug
git diff --check
~~~

**完成标准：**Gradle 与脚本读取同一配置；完整计划范围达到 80/70；临时加入一个未测试的 included class 会让 CI 因 0% 失败。

**建议提交：**`test: enforce complete business coverage scope`。

### 修复卡 P2-3：逐页建立真实 UiState/UiAction/UiEffect/Presenter 边界

**目标：**删除恒等 Presenter 和空 UiState；Route/Presenter 组合状态，Screen/Sections 只渲染不可变 state 并发送 action；文件门禁检查职责而非只看行数。

**修改范围：**

- `ui/appblocker/`、`focuslock/`、`focusmode/`、`urlblocker/`
- `ui/usageguard/`、`usagereview/`、`actionlog/`
- `ui/settings/`、`advanced/`、`imagepreview/`
- `service/usageguardrequest/`、`service/usageguardcountdown/`
- `scripts/verify-ui-file-boundaries.py`
- `scripts/tests/test_ui_file_boundaries.py`
- 各 feature 对应 JVM presenter/action tests

**每个 feature 都重复以下步骤，一次只迁移一个：**

1. 先列出现有页面状态、事件、副作用和失败路径，特别标出保存成功边界、锁定、每日额度、自动重开、Overlay mounted、取消和重复事件。
2. 写 presenter 测试覆盖 Loading/Empty/Content/Error，以及该 feature 的保存、取消、删除、锁定和失败事件。使用 fake repository/clock/dispatcher，不实例化 Compose。
3. 定义不可变 `<Feature>UiState`、sealed `<Feature>UiAction`、sealed `<Feature>UiEffect`。UiState 不得持有 `Context`、ViewModel、`MutableState`、Composable 或可变集合。
4. Presenter/VM 只组合 Flow 和分发 action；一次性导航/Toast/Dialog 结果走 effect。成功消息只能在持久化成功后产生。
5. Route 负责拿 ViewModel、生命周期收集 state 和 effect；Screen/Sections 接收 `state` 与 `onAction`，不得自己 `viewModel()`、直接读 `DbSet`/Store 或启动后台 scope。
6. 将大 section 按产品职责命名，不再创建 `Sections2/3`、`Editor2`。共享组件提取到有语义名称的文件，避免按数字切片。
7. 运行原有 feature 回归与新 presenter tests；对应用/URL/使用申请/Overlay 额外验证 Accessibility 与 Automation owner、Accepted/mounted、锁定/自动重开不变量。
8. 删除已无引用的恒等函数、空 state 和旧 flat host；单 feature 提交并独立 review 后再迁移下一个。

**推荐迁移顺序：**

1. actionlog、advanced、imagepreview（较小，先验证模式）。
2. appblocker、focusmode、urlblocker。
3. usagereview、usageguard、settings。
4. focuslock。
5. usageguardrequest/countdown overlay（最高风险，最后单独 PR）。

**同步加强文件门禁：**

1. 递归扫描所有 `ui/**/*Page.kt`、目标 feature 与 `service/**/*Overlay*.kt`，包括 `ui/home/ControlPage.kt`。
2. 拒绝生产未引用的 Presenter/UiState、`String -> String` 恒等 presenter、UiState 文件内 `@Composable`、Screen/Sections 内 `viewModel()`。
3. 文件 500 行和 Composable 180 行继续保留，但数字后缀不能成为逃逸手段；可对 `Sections2.kt`/`Editor2.kt` 设置迁移期 allowlist，并在最后清零。

**每个 feature 的验收命令模板：**

~~~bash
./gradlew :app:testGkdDebugUnitTest --tests '*<Feature>*'
python3 scripts/verify-ui-file-boundaries.py
./gradlew :app:compileGkdDebugKotlin :app:compilePlayDebugKotlin
./gradlew :app:lintGkdDebug :app:lintPlayDebug
git diff --check
~~~

Overlay 最终还要运行适用 GMD，并记录 `FLAG_SECURE`、IME、HOME/BACK、挂载失败的真机未验证项。

**完成标准：**所有目标目录不存在恒等/空占位；UiState 无 Android/Compose mutable 依赖；Screen 层可被真实 screenshot fixture 直接渲染；`ControlPage.kt` 与所有宿主满足门禁；原有高风险行为测试不退化。

**建议提交：**每个 feature 一个 `refactor: establish <feature> ui state boundary`，不要压成一个超大 commit。

### 修复卡 P2-4：修复中大屏布局和当前目标重复点击

**目标：**600dp 起正确显示 NavigationRail 与内容；再次点击当前一级目标立即滚动该目标到顶部，不重建 ViewModel 或 back stack。

**修改文件：**

- Modify: `app/src/main/kotlin/li/songe/gkd/sdp/ui/home/AdaptiveHomeScaffold.kt`
- Modify: `app/src/main/kotlin/li/songe/gkd/sdp/MainViewModel.kt`
- Modify: 四个一级页面的 reset event 收集
- Test: `app/src/test/kotlin/li/songe/gkd/sdp/ui/home/HomeNavigationPolicyTest.kt`
- Test: `app/src/androidTest/kotlin/li/songe/gkd/sdp/ui/AppNavigationTest.kt`
- Modify: 700/1000dp screenshot cases

**具体步骤：**

1. 写纯 `HomeNavigationPolicyTest`，固定宽度 599=bottom bar、600/839=rail、840=expanded；固定“当前目标再次点击=ResetCurrent，不导航”“其它目标点击=Navigate”。
2. 写 Compose 测试，在 700dp/1000dp 下断言 rail 和内容唯一语义节点同时可见；旧实现应因内容不可见或宽度错误失败。
3. 将 `HorizontalDivider` 改为 `VerticalDivider`；给内容容器 `Modifier.weight(1f).fillMaxHeight()`，再在内部应用 960dp/720dp 最大宽度和居中规则。
4. 两种导航组件都始终把点击交给同一个 handler，不在 Composable 层用 `if (!selected)` 吞掉。
5. 将 `resetPageScrollEvent` 类型改为 `HomeDestination` 或等价稳定 target；删除 500ms 双击和统一 `BottomNavItem.Control`。当前目标一次再次点击即 emit 对应 target，且不调用 navigate。
6. Overview/Self-control/Rules/Settings 各自只消费自己的 reset；Rules 保留当前二级 tab，Settings 保留搜索条件，只把当前列表滚到顶部。
7. Activity 测试保存 ViewModel 实例标识/back stack 大小，重复点击后断言未重建；切宽度后 selected、tab、表单和滚动状态保留。
8. 更新 360/700/1000dp 的真实页面截图并验证 light/dark、fontScale2。

**验收命令：**

~~~bash
./gradlew :app:testGkdDebugUnitTest --tests '*HomeNavigationPolicyTest' --tests '*HomeInformationArchitectureTest'
./gradlew :app:pixel6Api35GkdDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=li.songe.gkd.sdp.ui.AppNavigationTest
./gradlew :app:validateGkdDebugScreenshotTest
./gradlew :app:compileGkdDebugKotlin :app:compilePlayDebugKotlin
git diff --check
~~~

**完成标准：**700/1000dp 页面内容可见；四个一级目标重复点击都回顶部；不产生额外 NavKey/ViewModel；动态宽度切换状态不丢。

**建议提交：**`fix: restore adaptive home navigation behavior`。

### 修复卡 P2-5：按 runtime mode 正确限定 A11y Guard

**目标：**A11y Guard 只在 gkd + Accessibility 模式适用；Accessibility 已选但服务关闭时仍允许先开启；Automation/play 不展示可用动作。

**修改文件：**

- Modify: `app/src/main/kotlin/li/songe/gkd/sdp/capability/Capability.kt`
- Modify: `app/src/test/kotlin/li/songe/gkd/sdp/capability/CapabilityResolverTest.kt`
- Modify: `app/src/main/kotlin/li/songe/gkd/sdp/ui/capability/CapabilityCenterPresenter.kt`
- Modify: `app/src/androidTest/kotlin/li/songe/gkd/sdp/ui/CapabilityFlowTest.kt`

**具体步骤：**

1. 写参数化矩阵测试：flavor(gkd/play) × mode(null/A11y/Automation) × a11y service(on/off) × guard(enabled/disabled) × lock(on/off)。
2. 固定期望：play=UNAVAILABLE；Automation=UNAVAILABLE；mode 未选=UNAVAILABLE/等待选择；gkd+A11y+disabled=READY 且可开启；gkd+A11y+enabled=ACTIVE；locked+enabled=ACTIVE 且无关闭动作。
3. 运行 `CapabilityResolverTest`，确认旧的 Automation=ACTIVE 断言失败。
4. 在 resolver 的 A11y Guard 分支先判断 flavor，再判断 mode，最后处理 enabled/locked；不要把 `a11yReady=false` 当成不可开启，因为守护允许服务关闭时先开启。
5. Presenter 从统一 runtime owner/mode 状态生成 `CapabilityInput`，不要从 Shizuku ready 猜模式。
6. UI 对 UNAVAILABLE 显示明确“不适用于当前模式”，不提供 toggle；切回 A11y 后自动重算并恢复正确状态。
7. 真实 Compose 测试切换 mode，断言节点状态、唯一 primary action 和锁定动作；回归首页单向无障碍开关。

**验收命令：**

~~~bash
./gradlew :app:testGkdDebugUnitTest --tests '*CapabilityResolverTest' --tests '*HomeA11y*' --tests '*AccessibilityGuard*' --tests '*SelfControlModeParity*'
./gradlew :app:pixel6Api35GkdDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=li.songe.gkd.sdp.ui.CapabilityFlowTest
./gradlew :app:compileGkdDebugKotlin :app:compilePlayDebugKotlin
git diff --check
~~~

**完成标准：**完整矩阵通过；Automation/play 没有 Guard 操作；A11y 服务关闭但已选 A11y 模式仍可开启；锁定时不可关闭。

**建议提交：**`fix: scope accessibility guard capability to a11y mode`。

### 修复卡 P2-6：恢复可审计发布流程并发布修复版本

**目标：**不修改既有 v2.2.0/v2.2.1，所有修复先经独立 PR 和真实门禁，再发布新的稳定版本。

**前置条件：**P1-1 至 P1-7 全部完成；P2-1 至 P2-5 完成或有用户批准的书面范围调整；修复后的 quality/build/coverage/visual/GMD/performance 门禁能真实失败。

**具体步骤：**

1. 不删除、不移动、不重新签名、不替换 v2.2.0/v2.2.1 tag 或资产。保留本报告作为历史偏差说明。
2. 每个修复 PR 获取至少一条可核验 review；处理完所有 review conversations 后再合并。`reviewDecision` 为空时不得把“已审查”写进发布证据。
3. 所有修复合并后同步 `main`，确认 `git rev-parse main` 与 `git rev-parse origin/main` 一致，并等待 main 的完整 CI。
4. 新建 release 分支。若届时没有更新版本占用，推荐 `versionName=2.2.2`、`versionCode=103`；执行前必须重新查询所有 tag/Release/versionCode，若已占用则选择下一个稳定 PATCH 和更大 code。
5. 在 `CHANGELOG.md` 增加新的日期段，准确写明：明文图片网络修复、能力路由、真实门禁、隐私数据控制、设置/自适应/能力状态修复和已知真机限制。不得复制 2.2.0 的未验证承诺。
6. 更新 `gradle/version.properties`，保持 `upstreamBase`/`upstreamVersionCode` 除非本次确实同步上游。
7. 在 release PR 运行元数据测试、Python 全套、security report、selector/JVM、两个 flavor lint/build、coverage、截图、三个 GMD task 和 performance。任何失败回到对应修复 PR，不在 release PR 放宽门禁。
8. 推送 release 分支并创建 PR；等待 quality、build、dependency-review、CodeQL、coverage、visual-regression、managed-device-api26、managed-device-api35、performance 全部 SUCCESS 和独立 review。
9. 合并后再次等待 main CI。只在通过的 main merge commit 创建 annotated `vX.Y.Z` tag；先运行带 `--tag` 的元数据验证，再 push tag。
10. Release workflow 只能生成 Draft。下载 APK、`update.json`、`SHA256SUMS.txt`，核对版本、包名、大小、SHA-256、签名证书和 attestation；检查 release notes 不含路径、理由、URL、设备标识或凭据。
11. 用户确认 Draft 证据后再设为公开 stable/latest。公开后执行升级安装、应用内更新和 `docs/testing/release-smoke-checklist.md`；未做的 Accessibility/Shizuku/OEM/`FLAG_SECURE` 项明确标为未执行。
12. 发布失败若需要改代码，使用再下一个 PATCH/code；绝不移动已推送 tag。

**发布 PR 前完整命令：**

~~~bash
python3 -m unittest discover -s scripts/tests -p 'test_*.py' -v
bash scripts/generate-security-dependency-report.sh
./gradlew :selector:jvmTest :app:testGkdDebugUnitTest :app:lintGkdDebug :app:lintPlayDebug :app:koverVerifyGkdDebug :app:validateGkdDebugScreenshotTest
python3 scripts/verify-security-dependency-report.py --report build/reports/security-dependencies.txt
./gradlew :app:pixel2Api26GkdDebugAndroidTest :app:pixel6Api35GkdDebugAndroidTest :app:pixel6Api35PlayDebugAndroidTest
./gradlew :app:generateGkdReleaseBaselineProfile -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.enabledRules=BaselineProfile
./gradlew :baselineprofile:pixel6Api35GkdNonMinifiedReleaseAndroidTest -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.enabledRules=Macrobenchmark
./gradlew :app:assembleGkdDebug :app:assemblePlayDebug :app:assembleGkdRelease :app:assemblePlayRelease
python3 scripts/verify-performance-reports.py --thresholds config/quality/performance-thresholds.json --compose-baseline config/quality/compose-stability-baseline.json --current-apk <current-apk> --baseline-apk <baseline-apk> --compose-report <compose-report>
bash scripts/test-verify-release-metadata.sh
bash scripts/test-generate-update-manifest.sh
bash scripts/verify-release-metadata.sh --no-tag
git diff --check
~~~

**tag 与 Draft 验收：**

~~~bash
git tag -a vX.Y.Z -m "GKD-SDP vX.Y.Z" <verified-main-commit>
bash scripts/verify-release-metadata.sh --tag vX.Y.Z
git push origin vX.Y.Z
gh pr checks <release-pr-number> --watch --fail-fast
gh release view vX.Y.Z --json isDraft,isPrerelease,assets,url
gh attestation verify <downloaded-apk> --repo wskeei/gkd-SDP
~~~

**完成标准：**新 tag 指向已验证 main；Release workflow 资产、签名、checksum、attestation 全部验证；Draft 经人工确认后才公开；Latest 指向新稳定版；旧 Release 未被改写；真机未验证项记录准确。

**建议提交：**`chore: prepare GKD-SDP 2.2.2 release`（仅在 2.2.2/103 仍可用时）。

## 建议修复顺序

按依赖关系执行，不按严重度编号机械并行：

1. **安全与入口止血：**P1-1 → P1-6 → P2-5。三个 finding 各自独立 PR，先消除明文请求和错误能力入口。
2. **让非 UI 门禁先会失败：**P1-2 的报告校验/StrictMode/首帧 → P1-5 → P2-2 → P1-3 的 test-quality 脚本。每项先提交失败 fixture。
3. **建立可测 UI 边界：**按 P2-3 推荐顺序逐 feature 迁移；每个 feature 独立 PR。迁移一个页面时同步给 P1-3 增加该页面的真实 UI 测试，不等全部页面结束才补测试。
4. **修产品行为：**P2-4 → P2-1 → P1-7。每个修复必须在第三阶段建立的真实 GMD 环境上增加 red/green 流程。
5. **关闭自动化缺口：**完成 P1-3 的完整 API/flavor suite，再做 P1-4 的最终真实截图矩阵；截图放在产品布局稳定后，避免反复更新基线。
6. **全量回归：**重新运行 Python、JVM、Lint、Kover、四 variant、截图、三个 GMD task、Baseline Profile 和性能校验；逐行核对跟踪表，任何 P1 未完成都停止发布。
7. **新版本：**最后执行 P2-6 的 release PR/tag/Draft/资产/公开流程；不得移动或覆盖 v2.2.0/v2.2.1。

建议的最小 PR 队列：

- PR 1：P1-1 image preview transport。
- PR 2：P1-6 capability semantic route。
- PR 3：P2-5 A11y Guard mode matrix。
- PR 4-6：P1-2 的 verifier、runtime checks/draw、Profile traversal。
- PR 7：P1-5 localization lint activation；随后可按模块拆多个文案迁移 PR，最后一个关闭 finding。
- PR 8：P2-2 Kover scope。
- PR 9：P1-3 test-quality 与 TestAppDependencies/GMD harness。
- PR 10 起：P2-3 每个 feature 一个 PR，并逐步补 P1-3 UI flow。
- 后续独立 PR：P2-4、P2-1、P1-7。
- 倒数第二个 PR：完成 P1-3 suite 与 P1-4 screenshot matrix。
- 最后一个 PR：P2-6 release。

## 已执行验证

成功：

- `git diff --check b9d98cb748a6e37c0eb88caac99dae156d915fc3..e7efc108a1a0e7e7dfd8cc55bcc32bb31cf43fef`
- `python3 -m unittest discover -s scripts/tests -p 'test_*.py' -v`：46 tests，全部通过。
- `python3 scripts/verify-test-quality-policy.py`
- `python3 scripts/verify-ui-file-boundaries.py`
- `python3 scripts/verify-compose-lifecycle-policy.py`
- `python3 scripts/verify-sensitive-output-policy.py`
- GitHub PR #39、#40 的 quality/build/coverage/visual-regression/GMD/performance 记录均为 SUCCESS。

上述脚本通过不推翻 Findings；相反，P1-2、P1-3、P1-4、P1-5、P2-2、P2-3 说明这些门禁为什么会在不满足计划目标时仍然绿色。

未执行：

- 本地 Gradle：`bash scripts/check-dev-environment.sh --ci` 报告缺少 `JAVA_HOME` 和 JDK 21，因此没有把本地未运行写成通过；Android 编译/Lint/单测采用 PR #40 的 Actions 记录作为当前远端证据。
- 真机/OEM：未执行 Accessibility、Automation/Shizuku、Overlay、`FLAG_SECURE`、IME、Doze、通知、前后台 Service 与 force-stop 手工回归。
- 本地 GMD、截图生成和宏基准：未重复运行；本报告对其有效性的结论来自测试/脚本源码和 GitHub 作业证据。

## 审查边界

本次是按计划验收项、风险调用链和发布证据进行的系统审查，不是对 488 个变更文件逐行证明无缺陷。没有在本次审查中发现明显退化的高风险自律运行时路径，不等于已完成真机 parity 证明；后续修复不得削弱 `SdpRuntimeFeatureCoordinator` 双 owner、Overlay Accepted/mounted 成功边界、锁定/每日额度/自动重开、使用申请事实来源和 `FLAG_SECURE` 等根级不变量。
