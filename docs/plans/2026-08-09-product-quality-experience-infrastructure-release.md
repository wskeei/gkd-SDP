# GKD-SDP 产品质量、使用体验与工程基础设施实施计划

> **For Codex:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 在不削弱选择器、订阅、无障碍运行时、使用申请、应用/URL 拦截、专注模式、锁定、自动重开、回桌面、通知、隐私保护与截图悬浮条行为的前提下，完成安全与数据保护、架构与生命周期、统一设计系统、核心体验、自动化质量门禁和稳定版发布的整体验收。

**Architecture:** 继续以 SdpRuntimeFeatureCoordinator、A11yRuleEngine、SelfControlOverlayLauncher、Room 与现有文件 Store 为唯一运行时和持久化真相源；把时钟、调度器、导航、诊断、备份、远程调试和 UI 展示拆成可注入边界；把业务计算保留在纯 Kotlin policy/repository；Compose 只渲染不可变 UiState 并发送 UiAction；所有远程入口先经过显式会话授权；所有敏感导出先经过字段白名单和脱敏。

**Tech Stack:** Kotlin 2.3.21、Jetpack Compose 1.11.2、Material 3、Navigation 3、Room 2.8.4、Ktor 3.5.0、Gradle/AGP 9.3.0、JDK 21、Android API 26–37、JUnit4、Compose UI Test、Room Testing、Compose Preview Screenshot Testing 0.0.1-alpha15、Gradle Managed Devices、Kover 0.9.9、Macrobenchmark/Baseline Profile 1.4.1、GitHub Actions。

---

## 0. 执行边界

1. 按本文 Task 1 到 Task 38 的顺序执行，不跳步、不合并 Task、不提前发布。
2. 每个实施 PR 都从前一个已合并 PR 的 origin/main 创建独立 worktree 和分支。
3. 固定使用以下分支：
   - codex/quality-security-data
   - codex/quality-architecture-lifecycle
   - codex/quality-design-localization
   - codex/quality-product-experience
   - codex/quality-automation-performance
   - codex/release-2-2-0
4. 所有提交使用 Conventional Commit；每个 Task 只提交该 Task 列出的文件。
5. 禁止直接推送 main、强推、覆盖公开 tag、替换公开 Release 资产、删除历史 Room schema。
6. 每个 PR 等待 quality、build、dependency-review、CodeQL 和本文新增的适用检查全部成功后再 squash merge。
7. gkd 与 play 两个 flavor 的共享代码同时通过编译、Lint 和自动化测试。
8. 不引入用户行为遥测、广告 SDK、第三方崩溃上报 SDK、远程配置或云端账号体系。
9. 不把使用申请理由、完整 URL、规则 pattern、无障碍节点文本、截图、通知正文、联系人、数据库内容或设备标识写入日志、测试夹具、PR 或 Actions Artifact。
10. 不把真机/OEM 验收设置为 PR、合并、Tag 或 Release 发布门禁；公开 Release 后由用户下载安装并完成个人设备验证。
11. 倒计时与申请理由悬浮条继续使用 FLAG_SECURE；截图模式只临时隐藏该悬浮条，不对目标应用窗口设置 FLAG_SECURE。
12. Overlay 只在 WindowManager.addView 成功后写“已展示”记录；启动失败、非法 Intent、重复启动和挂载失败不写成功历史，并清除暂定冷却。
13. 每个 Task 的命令都从其代码块指定的目录执行；每个 PR 合并后先回到主仓库根目录，再创建下一个 worktree。

## 1. 已确认问题与固定完成指标

### 1.1 安全、隐私与数据

- LogUtils 在 release 可写文件日志，现有调用包含 Intent、Bundle、Throwable、节点文本、URL、联系人和命令参数；完成后生产日志只允许事件代码、阶段、稳定 ID 哈希和错误类别。
- 支持包当前包含完整数据库、Store、订阅、日志、崩溃与应用列表；完成后支持包只包含白名单诊断摘要，数据库、申请理由、URL、节点文本、截图和联系人均不得进入支持包。
- ZipUtils 未限制路径、条目数、单条大小和总解压大小；完成后拒绝 Zip Slip、重复路径、符号链接、超过 2,000 条目、单条超过 32 MiB、总解压超过 128 MiB、压缩包超过 64 MiB 的输入。
- BackupUtils 仅覆盖部分上游表和 Store，导入不是原子操作；完成后使用加密备份格式 v2，覆盖用户配置、订阅、拦截配置、锁定配置、使用申请配置与历史，并在事务与临时 Store 中全部校验后一次提交。
- Android Auto Backup 未声明 data-extraction-rules/full-backup-content；完成后数据库、Store、订阅、日志、崩溃、截图、缓存、命令文件和密钥全部排除，只保留无敏感内容的主题与显示偏好。
- HttpService 默认暴露局域网、允许宽泛 CORS 且接口无认证；完成后默认仅监听 127.0.0.1，局域网会话使用一次性配对码、固定来源、单客户端、15 分钟会话和速率/大小限制。
- ExposeService 为 exported 且调用无认证；完成后每次命令使用私有文件中的 256-bit 会话令牌，缺失、过期或不匹配时不执行任何动作。
- Manifest 另有 15 个 exported 组件，入口验证和 PendingIntent 约束未形成统一契约；完成后建立显式 allowlist、输入校验、系统 permission 校验与 immutable explicit PendingIntent 门禁。
- WebView 启用 JavaScript bridge 且缺少完整来源限制；完成后只允许固定 HTTPS 文档域使用 bridge，禁用 file/content access、混合内容、第三方 Cookie 和非白名单主框架导航。
- 全局 cleartext 放行；完成后固定项目服务只使用 HTTPS，用户 HTTP 订阅必须逐个确认并保存来源授权，WebView 永久拒绝 HTTP。
- 使用记录缺少集中式查看、导出与删除入口；完成后“隐私与数据”页提供按数据类别查看保留范围、单类删除、全部删除和加密导出。

### 1.2 架构、生命周期与可维护性

- 235 处 Flow 使用 collectAsState；完成后 Activity/页面/对话框使用 collectAsStateWithLifecycle，Service Overlay 使用明确的 STARTED 生命周期所有者。
- MainViewModel 使用全局 instance!!、导航栈未持久化、深链参数使用不透明数字；完成后移除全局单例，导航通过 AppNavigator 注入，NavBackStack 保存与恢复，深链使用语义化 route。
- 业务代码广泛直接读取 System.currentTimeMillis 与 Dispatchers；完成后自律、锁定、自动重开、记录与统计链路统一注入 SdpClock 和 SdpDispatchers。
- CoroutineExt 吞掉 CancellationException；完成后所有通用捕获先重新抛出 CancellationException，再映射业务错误。
- 多个 UI 文件超过 750 行，最高超过 1,500 行；完成后页面只承担 route/容器职责，所有页面与 Overlay 宿主生产文件不超过 500 行，policy、state、section、dialog 和 editor 分文件。
- 异常直接以 stackTraceToString/message 显示给用户；完成后 UI 只显示稳定错误码、可执行恢复动作和中性文案，详细脱敏诊断仅进入本地诊断摘要。

### 1.3 使用体验、视觉与无障碍

- 首页固定使用手机底部导航，数字自律入口分散；完成后一级信息架构固定为“概览 / 自律 / 规则 / 设置”，紧凑宽度使用 NavigationBar，中等及扩展宽度使用 NavigationRail。
- 首次启动和权限修复依赖多个弹窗叠加；完成后使用单一“运行能力中心”，按顺序展示无障碍/Automation、悬浮窗、通知、电池优化和 Shizuku 状态与唯一下一步操作。
- UI 存在大量硬编码颜色、间距、中文字符串、32dp 图标按钮、空 onClick 和 contentDescription=null；完成后统一 Token、最小 48dp 触控区、完整语义、中文基准资源和英文资源。
- 主题对全部 ColorScheme 字段固定执行 500ms 动画；完成后主题切换使用 180ms，系统“移除动画”开启时使用 0ms，倒计时不使用 TalkBack live region。
- 设置页层级长且“数字自律”埋在“其他”；完成后设置固定分组、搜索、状态摘要、危险操作区和隐私数据入口。
- 通知文案保存条件把标题值与副标题变量比较；完成后标题与副标题分别比较、分别保存，并用表单行为测试防止回归。
- 页面加载、空态、错误态、保存反馈和未保存退出规则不一致；完成后所有列表、表单和详情页使用统一四态组件与统一保存反馈。
- 图表密集数据缺少稳定聚合、文本等价和横屏/大字体适配；完成后 24 小时按小时、7 天按 6 小时、30 天按天聚合，并提供摘要、点选明细和可访问数据表。

### 1.4 测试、性能与发布基础设施

- androidTest 只有占位测试；完成后删除占位测试，增加导航、权限中心、设置、申请表单、删除数据、备份导入和 Room 32→33 迁移测试。
- 已声明 Compose UI Test 依赖但未建立真实 UI 测试；完成后关键页面使用语义节点和稳定 TestTag 覆盖。
- 缺少截图回归、托管模拟器、覆盖率、宏基准和 Baseline Profile；完成后 PR 运行截图回归与 API 26/API 35 GMD，纯 Kotlin/业务层 Kover 行覆盖率不低于 80%、分支覆盖率不低于 70%，Release 内置 Baseline Profile。
- 缺少 debug 主线程 I/O、资源泄漏和明文网络运行时检测；完成后 debug StrictMode 把违规转换为类型化诊断并令相应自动化测试失败，release 不增加检测开销。
- CI 未构建 playRelease；完成后构建 gkdDebug、playDebug、gkdRelease、playRelease 四个变体。
- 生产图表依赖 Vico 2.0.0-alpha.28，WebView 使用 2024 年的第三方 Android Compose wrapper；完成后图表升级到稳定 Vico 2.5.2，WebView 改为 AndroidView 直接托管系统 WebView 并移除 wrapper。
- gradlew 缺少可执行位且本地环境要求未自动检查；完成后提交可执行位、.java-version=21 和统一环境检查脚本。
- 发布流程继续使用稳定 SemVer；本次固定发布 2.2.0、versionCode 101，并在 Draft 静态验收后公开为 Latest。

## 2. 官方实现基线

执行代码时以以下官方资料为行为基线，并把链接保留在受影响源码或维护文档中：

- Flow 生命周期收集：https://developer.android.com/kotlin/flow/stateflow-and-sharedflow
- Compose 状态提升：https://developer.android.com/develop/ui/compose/state-hoisting
- 自适应导航：https://developer.android.com/develop/adaptive-apps/guides/build-adaptive-navigation
- Android 无障碍与 48dp 触控目标：https://developer.android.com/guide/topics/ui/accessibility/apps.html
- Compose UI 测试：https://developer.android.com/develop/ui/compose/testing
- Compose 截图测试：https://developer.android.com/studio/preview/compose-screenshot-testing
- Gradle Managed Devices：https://developer.android.com/studio/test/managed-devices
- Baseline Profile：https://developer.android.com/topic/performance/baselineprofiles/overview
- Compose 稳定性诊断：https://developer.android.com/develop/ui/compose/performance/stability/diagnose
- Android Auto Backup：https://developer.android.com/identity/data/autobackup
- WebView 文件访问安全：https://developer.android.com/privacy-and-security/risks/webview-unsafe-file-inclusion
- Network Security Configuration：https://developer.android.com/privacy-and-security/security-config
- Kover：https://kotlin.github.io/kotlinx-kover/gradle-plugin/
- Vico Releases：https://github.com/patrykandpatrick/vico/releases

## 3. PR 1：安全、隐私与数据可靠性

### Task 1：建立隔离分支、工具链契约与审查基线

**Files:**

- Modify: gradlew（仅文件模式）
- Create: .java-version
- Create: scripts/check-dev-environment.sh
- Create: scripts/tests/test_dev_environment_policy.py
- Modify: README_DEV.md
- Modify: .github/workflows/ci.yml

**Step 1：创建分支和 worktree**

~~~bash
git fetch origin --prune
git switch main
git pull --ff-only origin main
git worktree add .worktrees/quality-security-data -b codex/quality-security-data origin/main
cd .worktrees/quality-security-data
git status --short --branch
~~~

预期：分支为 codex/quality-security-data，工作区为空。

**Step 2：先写失败测试**

在 scripts/tests/test_dev_environment_policy.py 固定断言：

- .java-version 内容为单行 21。
- gradlew 带 owner executable 位。
- scripts/check-dev-environment.sh 检查 java、JAVA_HOME、JDK major=21、Android SDK、adb、python3、gh、git 和 gradlew。
- 检查失败只输出缺失能力名称，不输出环境变量值、路径中的用户名或凭据。
- CI 直接执行 ./gradlew，不再临时 chmod。

运行：

~~~bash
python3 -m unittest scripts.tests.test_dev_environment_policy -v
~~~

预期：测试失败并指出文件或可执行位缺失。

**Step 3：实现环境契约**

- 把 gradlew 文件模式改为 100755。
- 新建 .java-version，写入 21。
- 实现 scripts/check-dev-environment.sh；--ci 模式只要求 JDK 21、Python、Git 和可执行 Gradle wrapper，--android 模式额外要求 SDK/adb。
- README_DEV.md 的本地命令前增加 bash scripts/check-dev-environment.sh --android。
- .github/workflows/ci.yml 删除 chmod +x ./gradlew。

**Step 4：验证并提交**

~~~bash
python3 -m unittest scripts.tests.test_dev_environment_policy -v
bash scripts/check-dev-environment.sh --ci
git diff --check
git add gradlew .java-version scripts/check-dev-environment.sh scripts/tests/test_dev_environment_policy.py README_DEV.md .github/workflows/ci.yml
git commit -m "build: codify Android development environment"
~~~

### Task 2：建立类型化、脱敏的生产诊断

**Files:**

- Create: app/src/main/kotlin/li/songe/gkd/sdp/diagnostics/DiagnosticEvent.kt
- Create: app/src/main/kotlin/li/songe/gkd/sdp/diagnostics/DiagnosticLogger.kt
- Create: app/src/main/kotlin/li/songe/gkd/sdp/diagnostics/RedactionPolicy.kt
- Create: app/src/test/kotlin/li/songe/gkd/sdp/diagnostics/RedactionPolicyTest.kt
- Modify: app/src/main/kotlin/li/songe/gkd/sdp/util/LogUtils.kt
- Modify: app/src/main/kotlin/li/songe/gkd/sdp/util/CoroutineExt.kt
- Create: scripts/verify-sensitive-output-policy.py
- Create: scripts/tests/test_sensitive_output_policy.py
- Modify: .github/workflows/ci.yml
- Modify: app/src/main/kotlin/li/songe/gkd/sdp/data/CrashData.kt
- Modify: app/src/main/kotlin/li/songe/gkd/sdp/ui/CrashReportPage.kt
- Modify: app/src/main/kotlin/li/songe/gkd/sdp/ui/CrashReportVm.kt

**Step 1：先写失败测试**

RedactionPolicyTest 固定覆盖：

- Intent、Bundle、Uri、URL query、申请理由、无障碍 node text、通知正文、联系人、Cookie、Authorization、文件绝对路径和 Throwable message 不出现在输出。
- 稳定 ID 使用 private-store 中安装级 256-bit 随机盐计算 12 位十六进制哈希；盐不备份、不导出，删除全部应用数据时重建。
- 生产事件字段只允许 eventCode、stage、result、entityHash、count、durationBucket 和 errorCategory。
- CancellationException 原样重新抛出。
- 同一事件每 60 秒最多落盘 20 次，日志文件总量上限 2 MiB，最多保留 2 个轮转文件。

Python 契约测试固定扫描 app/src/main/kotlin，拒绝：

- printStackTrace、stackTraceToString、android.util.Log 和 println。
- LogUtils 参数中直接传 Intent、Bundle、Uri、AccessibilityNodeInfo、联系人实体和申请理由。
- Toast、Snackbar、Dialog 直接插入 Throwable.message。

运行：

~~~bash
./gradlew :app:testGkdDebugUnitTest --tests '*RedactionPolicyTest'
python3 -m unittest scripts.tests.test_sensitive_output_policy -v
~~~

预期：测试失败并列出现有敏感输出入口。

**Step 2：实现诊断模型**

- DiagnosticEvent 使用封闭事件代码，不接收自由文本 payload。
- DiagnosticLogger 在 debug 输出脱敏结构化事件，在 release 只保留 warning/error 类别与计数。
- RedactionPolicy 对所有字符串执行 URL、路径、邮箱、电话、令牌和长文本清洗；超出 80 字符截断。
- LogUtils 保留兼容壳并转发类型化事件；删除任意 vararg 对象序列化。
- CoroutineExt 捕获 Exception 前单独捕获并重新抛出 CancellationException。
- CrashData 在 release 只保存 errorCode、errorCategory、发生分钟、应用内栈帧方法名和计数，不保存 Throwable message、系统/第三方完整栈、Intent、线程内容或用户数据；CrashReportPage 只展示同一脱敏摘要，保留 7 天。

**Step 3：分批迁移全部调用点**

按以下固定顺序迁移，每批迁移后运行 testGkdDebugUnitTest：

1. service、a11y、shizuku。
2. util、data、db、store。
3. ui、widget、receiver、notif。
4. MainActivity、MainViewModel、App。

每个失败路径映射到稳定 errorCategory；用户界面映射到 R.string.error_* 文案和“重试 / 打开设置 / 复制错误码”动作。

**Step 4：接入 CI 并提交**

~~~bash
python3 scripts/verify-sensitive-output-policy.py
python3 -m unittest scripts.tests.test_sensitive_output_policy -v
./gradlew :app:testGkdDebugUnitTest
git diff --check
git add app/src/main/kotlin/li/songe/gkd/sdp/diagnostics app/src/main/kotlin/li/songe/gkd/sdp/util/LogUtils.kt app/src/main/kotlin/li/songe/gkd/sdp/util/CoroutineExt.kt app/src/main/kotlin/li/songe/gkd/sdp/data/CrashData.kt app/src/main/kotlin/li/songe/gkd/sdp/ui/CrashReportPage.kt app/src/main/kotlin/li/songe/gkd/sdp/ui/CrashReportVm.kt app/src/main/kotlin app/src/test/kotlin/li/songe/gkd/sdp/diagnostics scripts/verify-sensitive-output-policy.py scripts/tests/test_sensitive_output_policy.py .github/workflows/ci.yml
git commit -m "fix: redact production diagnostics"
~~~

### Task 3：把支持包改为白名单诊断摘要

**Files:**

- Create: app/src/main/kotlin/li/songe/gkd/sdp/diagnostics/SupportBundleManifest.kt
- Create: app/src/main/kotlin/li/songe/gkd/sdp/diagnostics/SupportBundleBuilder.kt
- Create: app/src/test/kotlin/li/songe/gkd/sdp/diagnostics/SupportBundleBuilderTest.kt
- Modify: app/src/main/kotlin/li/songe/gkd/sdp/util/FolderExt.kt
- Modify: app/src/main/kotlin/li/songe/gkd/sdp/ui/component/ShareLogDlg.kt
- Modify: PRIVACY.md

**Step 1：先写失败测试**

构造包含数据库、Store、订阅、申请理由、URL、截图、节点文本、联系人、Cookie 和绝对路径的测试目录，断言输出 zip 只包含：

- manifest.json
- app-summary.json
- capability-summary.json
- diagnostic-events.jsonl
- crash-summary.json

同时断言：

- manifest 记录格式版本、应用版本、flavor、Android API、生成时间和每个文件 SHA-256。
- app-summary 只包含安装来源类别、版本、ABI、API 和功能开关布尔值。
- diagnostic-events 最多 500 条，时间精度降为分钟。
- 支持包生成前展示内容清单和隐私说明，用户再次确认后才写共享目录。

**Step 2：实现白名单构建**

- SupportBundleBuilder 从类型化来源组装新对象，不复制原始数据库、Store、订阅、日志或 crash 文件。
- FolderExt.buildLogFile 改为调用 SupportBundleBuilder，并删除旧的目录递归打包路径。
- ShareLogDlg 固定展示将包含与不会包含的类别、大小估算和二次确认。

**Step 3：验证并提交**

~~~bash
./gradlew :app:testGkdDebugUnitTest --tests '*SupportBundleBuilderTest'
python3 scripts/verify-sensitive-output-policy.py
git diff --check
git add app/src/main/kotlin/li/songe/gkd/sdp/diagnostics app/src/main/kotlin/li/songe/gkd/sdp/util/FolderExt.kt app/src/main/kotlin/li/songe/gkd/sdp/ui/component/ShareLogDlg.kt app/src/test/kotlin/li/songe/gkd/sdp/diagnostics/SupportBundleBuilderTest.kt PRIVACY.md
git commit -m "fix: minimize support bundle data"
~~~

### Task 4：封堵压缩包路径穿越与资源耗尽

**Files:**

- Modify: app/src/main/kotlin/li/songe/gkd/sdp/util/ZipUtils.kt
- Modify: app/src/main/kotlin/li/songe/gkd/sdp/util/UriUtils.kt
- Create: app/src/test/kotlin/li/songe/gkd/sdp/util/SafeArchivePolicyTest.kt
- Create: app/src/androidTest/kotlin/li/songe/gkd/sdp/util/SafeArchiveInstrumentedTest.kt

**Step 1：先写失败测试**

固定覆盖：

- ../、绝对路径、Windows 盘符、反斜杠穿越、NUL、空名称、重复规范化路径和符号链接条目全部拒绝。
- 压缩包大于 64 MiB、条目超过 2,000、单条解压超过 32 MiB、总解压超过 128 MiB 全部拒绝。
- 写入任何文件前先完成目录表和大小上限校验。
- 中断、校验失败或磁盘异常后删除本次临时目录。
- Uri 输入使用流式复制，不调用 readBytes。

**Step 2：实现 SafeArchivePolicy**

- 在 ZipUtils 内新增 validateEntryName、ArchiveLimits 和受计数 InputStream。
- 使用 destDir.canonicalFile 与 output.canonicalFile 校验父子关系。
- 先扫描 ZipFile 元数据，再解压到 createGkdTempDir 创建的唯一子目录。
- UriUtils 增加 copyUriToFile(uri, target, maxBytes)，达到上限立即抛出稳定错误类型。

**Step 3：验证并提交**

~~~bash
./gradlew :app:testGkdDebugUnitTest --tests '*SafeArchivePolicyTest'
./gradlew :app:compileGkdDebugAndroidTestKotlin
git diff --check
git add app/src/main/kotlin/li/songe/gkd/sdp/util/ZipUtils.kt app/src/main/kotlin/li/songe/gkd/sdp/util/UriUtils.kt app/src/test/kotlin/li/songe/gkd/sdp/util/SafeArchivePolicyTest.kt app/src/androidTest/kotlin/li/songe/gkd/sdp/util/SafeArchiveInstrumentedTest.kt
git commit -m "fix: validate imported archives"
~~~

SafeArchiveInstrumentedTest 在 Task 28 的 API 26 与 API 35 GMD 中执行并成为合并门禁。

### Task 5：实现加密、完整、可回滚的备份格式 v2

**Files:**

- Create: app/src/main/kotlin/li/songe/gkd/sdp/backup/BackupFormatV2.kt
- Create: app/src/main/kotlin/li/songe/gkd/sdp/backup/BackupCrypto.kt
- Create: app/src/main/kotlin/li/songe/gkd/sdp/backup/BackupCatalog.kt
- Create: app/src/main/kotlin/li/songe/gkd/sdp/backup/BackupExportCoordinator.kt
- Create: app/src/main/kotlin/li/songe/gkd/sdp/backup/BackupImportCoordinator.kt
- Create: app/src/main/kotlin/li/songe/gkd/sdp/backup/BackupImportJournal.kt
- Create: app/src/main/kotlin/li/songe/gkd/sdp/backup/LegacyBackupImporter.kt
- Create: app/src/test/kotlin/li/songe/gkd/sdp/backup/BackupCryptoTest.kt
- Create: app/src/test/kotlin/li/songe/gkd/sdp/backup/BackupCatalogTest.kt
- Create: app/src/test/kotlin/li/songe/gkd/sdp/backup/BackupImportCoordinatorTest.kt
- Modify: app/src/main/kotlin/li/songe/gkd/sdp/util/BackupUtils.kt
- Modify: app/src/main/kotlin/li/songe/gkd/sdp/db/AppDb.kt
- Modify: app/src/main/kotlin/li/songe/gkd/sdp/store/StorageExt.kt
- Modify: app/src/main/kotlin/li/songe/gkd/sdp/ui/home/SettingsPage.kt
- Modify: PRIVACY.md
- Modify: docs/testing/release-smoke-checklist.md

**Step 1：先写密码学与格式失败测试**

固定格式：

- 文件 magic 为 GKDSDPBK2。
- 明文 header 只包含 formatVersion=2、KDF 名称、迭代次数、16-byte salt、12-byte nonce 和密文长度。
- KDF 固定 PBKDF2WithHmacSHA256、600000 次、256-bit key。
- 内容固定 AES/GCM/NoPadding、128-bit tag；header 的规范化 UTF-8 字节作为 AAD。
- 密码长度至少 12 个 Unicode code point；密码只保存在调用栈内 char array，使用后覆盖为零。
- payload 是 SafeArchivePolicy 校验过的 zip；manifest.json 列出每个对象的 schema、count、bytes 和 SHA-256。
- 错误密码、修改 header、修改密文、重复 nonce、截断文件和不支持版本全部返回稳定错误码，不返回底层异常。

运行：

~~~bash
./gradlew :app:testGkdDebugUnitTest --tests '*BackupCryptoTest'
~~~

预期：测试因类不存在而失败。

**Step 2：先写数据覆盖失败测试**

BackupCatalog 固定数据类别：

1. settings：全部普通 Store，排除 private-store、会话令牌和本机权限状态。
2. subscriptions：SubsItem、SubsConfig、CategoryConfig、AppConfig 与订阅 JSON。
3. self_control_config：FocusLock、InterceptConfig、ConstraintConfig、FocusRule、AppGroup、BlockTimeRule、AppBlockerLock、UrlBlockRule、BrowserConfig、UrlRuleGroup、UrlTimeRule、UrlBlockerLock、UsageGuardAppProfile、UsageGuardTag、MonitoredApp。
4. self_control_history：FocusSession、UsageGuardRecord、SelfControlAttempt、SelfControlAttemptEvent、AppInstallLog。
5. upstream_history：ActionLog、ActivityLog、AppVisitLog。
6. sensitive_optional：Snapshot 元数据与文件、A11yEventLog、WechatContact；默认关闭，导出前逐项显示敏感说明。

固定断言：

- 默认开启前五类，默认关闭 sensitive_optional。
- 不导出日志、崩溃、缓存、支持包、命令脚本、私有 Store、远程会话和密钥。
- 所有表使用稳定主键排序，同一输入产生相同 payload 顺序。
- LegacyBackupImporter 只读取旧格式已知路径，并映射到 v2 catalog；不运行任意文件。

**Step 3：先写原子导入失败测试**

BackupImportCoordinatorTest 固定覆盖：

- 完整解密、schema 校验、引用完整性校验和冲突预览完成前不修改任何数据。
- 导入模式固定为“替换备份包含的类别”，未包含类别保持不变。
- 冲突预览显示每类新增、覆盖、删除数量；用户确认后才执行。
- BackupImportJournal 保存导入前的受影响 Store 值和 Room 行、目标 payload 哈希、阶段 PREPARED/APPLYING/COMMITTED。
- Room 在单个 DbSet.withTransaction 中替换；Store 使用 StorageExt 原子临时文件加 rename。
- 任一步失败立刻根据 journal 恢复旧 Store 与旧 Room 行。
- 进程在 APPLYING 阶段退出后，下次启动先执行 rollback，再开放主界面。
- 导入完成后调用 SdpRuntimeFeatureCoordinator.reconcile、UsageGuardConfigurationReconciler、AutoReenableEnforcer 和订阅刷新，不伪造任何触发或使用记录。

**Step 4：实现并替换 BackupUtils**

- BackupUtils 只保留兼容 facade，所有导出/导入调用 coordinator。
- 设置页导出流程固定为：选择类别 → 输入两次密码 → 展示清单与估算大小 → 写入 SAF 目标。
- 设置页导入流程固定为：选择文件 → 输入密码 → 展示格式/版本/类别/冲突预览 → 二次确认 → 导入 → 重算运行时。
- 不把备份密码、文件名、外部 Uri 或解密错误详情写入日志。

**Step 5：验证并提交**

~~~bash
./gradlew :app:testGkdDebugUnitTest --tests '*Backup*'
./gradlew :app:testGkdDebugUnitTest
python3 scripts/verify-sensitive-output-policy.py
git diff --check
git add app/src/main/kotlin/li/songe/gkd/sdp/backup app/src/main/kotlin/li/songe/gkd/sdp/util/BackupUtils.kt app/src/main/kotlin/li/songe/gkd/sdp/db/AppDb.kt app/src/main/kotlin/li/songe/gkd/sdp/store/StorageExt.kt app/src/main/kotlin/li/songe/gkd/sdp/ui/home/SettingsPage.kt app/src/test/kotlin/li/songe/gkd/sdp/backup PRIVACY.md docs/testing/release-smoke-checklist.md
git commit -m "feat: add encrypted transactional backups"
~~~

### Task 6：限制 Android 系统备份范围

**Files:**

- Create: app/src/main/res/xml/backup_rules.xml
- Create: app/src/main/res/xml/data_extraction_rules.xml
- Modify: app/src/main/AndroidManifest.xml
- Create: app/src/test/kotlin/li/songe/gkd/sdp/backup/AndroidBackupRulesContractTest.kt
- Modify: PRIVACY.md

**Step 1：先写失败契约测试**

断言 Manifest 同时声明 android:fullBackupContent 与 android:dataExtractionRules；两个 XML：

- 排除 database、sharedpref、file、external、root、device_* 全部默认域。
- 只 include app_theme.json、display_density.json 和 language.json。
- cloud-backup 与 device-transfer 使用同一白名单。
- backupInForeground=false、killAfterRestore=true。

**Step 2：实现资源与说明**

- 保留 android:allowBackup=true。
- 把敏感数据排除与加密手工备份 v2 的差异写入 PRIVACY.md。
- 恢复系统显示偏好后不自动开启无障碍、悬浮窗、锁定、拦截、HTTP 或通知功能。

**Step 3：验证并提交**

~~~bash
./gradlew :app:testGkdDebugUnitTest --tests '*AndroidBackupRulesContractTest'
./gradlew :app:processGkdDebugMainManifest :app:processPlayDebugMainManifest
git diff --check
git add app/src/main/res/xml/backup_rules.xml app/src/main/res/xml/data_extraction_rules.xml app/src/main/AndroidManifest.xml app/src/test/kotlin/li/songe/gkd/sdp/backup/AndroidBackupRulesContractTest.kt PRIVACY.md
git commit -m "fix: restrict Android backup data"
~~~

### Task 7：收紧 HTTP、导出 Service、WebView 与明文网络边界

**Files:**

- Create: app/src/main/kotlin/li/songe/gkd/sdp/remote/RemoteSession.kt
- Create: app/src/main/kotlin/li/songe/gkd/sdp/remote/RemoteSessionPolicy.kt
- Create: app/src/main/kotlin/li/songe/gkd/sdp/remote/RemoteRateLimiter.kt
- Create: app/src/main/kotlin/li/songe/gkd/sdp/remote/ExposeCommandIssuer.kt
- Create: app/src/main/kotlin/li/songe/gkd/sdp/remote/CleartextOriginPolicy.kt
- Create: app/src/main/kotlin/li/songe/gkd/sdp/remote/WebOriginPolicy.kt
- Create: app/src/main/assets/http-inspector/index.html
- Create: app/src/main/assets/http-inspector/app.js
- Create: app/src/main/assets/http-inspector/app.css
- Create: app/src/test/kotlin/li/songe/gkd/sdp/remote/RemoteSessionPolicyTest.kt
- Create: app/src/test/kotlin/li/songe/gkd/sdp/remote/RemoteRateLimiterTest.kt
- Create: app/src/test/kotlin/li/songe/gkd/sdp/remote/ExposeCommandIssuerTest.kt
- Create: app/src/test/kotlin/li/songe/gkd/sdp/remote/CleartextOriginPolicyTest.kt
- Create: app/src/test/kotlin/li/songe/gkd/sdp/remote/WebOriginPolicyTest.kt
- Create: app/src/test/kotlin/li/songe/gkd/sdp/remote/ManifestExportedComponentContractTest.kt
- Modify: gradle/libs.versions.toml
- Modify: app/build.gradle.kts
- Modify: app/src/main/kotlin/li/songe/gkd/sdp/service/HttpService.kt
- Modify: app/src/main/kotlin/li/songe/gkd/sdp/service/ExposeService.kt
- Modify: app/src/main/kotlin/li/songe/gkd/sdp/ui/WebViewPage.kt
- Modify: app/src/main/kotlin/li/songe/gkd/sdp/ui/AuthA11yPage.kt
- Modify: app/src/main/kotlin/li/songe/gkd/sdp/ui/home/ControlPage.kt
- Modify: app/src/main/kotlin/li/songe/gkd/sdp/OpenSchemeActivity.kt
- Modify: app/src/main/kotlin/li/songe/gkd/sdp/OpenFileActivity.kt
- Modify: app/src/main/kotlin/li/songe/gkd/sdp/OpenTileActivity.kt
- Modify: app/src/main/kotlin/li/songe/gkd/sdp/notif/Notif.kt
- Modify: app/src/main/kotlin/li/songe/gkd/sdp/widget/FocusQuickStartWidget.kt
- Modify: app/src/main/kotlin/li/songe/gkd/sdp/widget/UsageGuardReviewWidget.kt
- Modify: app/src/main/kotlin/li/songe/gkd/sdp/widget/FocusWidgetConfigActivity.kt
- Modify: app/src/main/kotlin/li/songe/gkd/sdp/util/NetworkExt.kt
- Modify: app/src/main/kotlin/li/songe/gkd/sdp/util/FolderExt.kt
- Modify: app/src/main/res/xml/network_security_config.xml
- Modify: app/src/main/AndroidManifest.xml
- Modify: README_DEV.md
- Modify: PRIVACY.md

**Step 1：先写 HTTP 会话失败测试**

RemoteSessionPolicy 固定规则：

- HttpService 默认 host=127.0.0.1。
- 用户点击“开启 15 分钟局域网调试”后才监听 0.0.0.0。
- 每次启动显示 8 位一次性配对码，有效期 60 秒，最多失败 5 次。
- 配对成功签发 256-bit bearer token，固定绑定首个客户端 IP 与 User-Agent 哈希，绝对有效期 15 分钟。
- 同一时刻只允许一个局域网会话；新会话启动先终止旧会话。
- 用户锁屏、关闭服务、切回仅本机、到期或点击“立即断开”后令牌立即失效。
- 默认 scope 只有 server_info 与 snapshot_list；view_snapshot、capture_snapshot、delete_snapshot、update_subscription、exec_selector 六个 scope 全部默认关闭并由本机 UI 独立开关。
- 请求总限额 60 次/分钟，capture 6 次/分钟，exec 10 次/分钟，请求体 1 MiB，单文件响应 32 MiB。
- 除 GET /、GET 静态资源和 POST /api/session/pair 外，全部 /api 路由要求 Authorization: Bearer。
- CORS 只回显当前会话精确 Origin；无 Origin、null Origin、通配 Origin 和非会话 Origin 拒绝。
- 错误响应只返回 code、requestId 和 retryAfterSeconds，不返回异常、路径、URI 或堆栈。

**Step 2：实现本地 Inspector 与 Ktor 守卫**

- 删除 SERVER_SCRIPT_URL 远程脚本加载，根路由只提供 app/src/main/assets/http-inspector。
- 静态响应设置 default-src 'self'、script-src 'self'、style-src 'self'、object-src 'none'、frame-ancestors 'none'、X-Content-Type-Options=nosniff 和 Cache-Control=no-store。
- 认证、scope、来源、大小和限流插件在 routing 前安装；每个现有 API 映射到唯一 scope。
- ControlPage 显示监听范围、剩余时间、客户端摘要、已授权 scope、复制本机地址、复制局域网地址和“立即断开”。
- 通知持续显示“仅本机”或“局域网会话剩余 N 分钟”，通知停止动作立即撤销令牌。

**Step 3：先写 ExposeService 失败测试并实现一次性能力令牌**

固定规则：

- ExposeCommandIssuer 为每个 action 签发 256-bit 随机 token，只保存 SHA-256、action、expiresAt 和 consumedAt。
- 外部 shell 命令 token 有效 5 分钟且只能使用一次；每次打开授权命令页重新生成 expose.sh，旧 token 立即撤销。
- 应用内部 Shizuku 调用使用独立 30 秒 token。
- ExposeService 收到缺 token、错误 action、过期、已消费、隐式 Intent 或未知 extra 时只记录拒绝事件并 stopSelf。
- token 校验与消费在单个 Mutex 临界区完成。
- expose.sh 权限固定 0600，过期后由调度器删除；支持外部 filesDir 时也执行同一权限与过期策略。

删除 Intent/data 的原值日志和“未知调用”原值 Toast。

**Step 4：先写 WebView 与明文来源失败测试并实现**

WebOriginPolicy 固定规则：

- 主框架只允许 https://gkd.li。
- gkd:// 交给内部 DeepLinkParser。
- 其他 HTTPS 主框架导航交给外部浏览器。
- HTTP、file、content、data、javascript、intent 和未知 scheme 永久拒绝。
- WebView 删除 addJavascriptInterface；file access、content access、universal file access、mixed content 和 third-party cookies 全部关闭。
- 删除 io.github.kevinnzou:compose-webview:0.33.6，WebViewPage 使用 AndroidView 直接托管系统 WebView，并在 DisposableEffect 中 stopLoading、移除 client、removeAllViews、destroy。
- JavaScript 只在 https://gkd.li 主框架开启；WebView 调试只在 META.debuggable 开启；Safe Browsing 开启。
- shouldInterceptRequest 只代理固定 HTTPS 文档镜像 registry.npmmirror.com，响应 MIME、编码、CSP 与目标路径逐项验证。

CleartextOriginPolicy 固定规则：

- 项目内固定 URL 全部为 HTTPS。
- 用户添加 HTTP 订阅时展示 scheme、host、port、明文风险和“仅授权此来源”确认。
- 授权键只保存规范化 scheme://host:port，不保存 path/query；每次重定向重新校验来源。
- HTTP 来源授权可在“隐私与数据 → 明文来源”逐项撤销；撤销后立即停止该来源更新。
- OkHttp 拦截器在连接前拒绝未授权 HTTP；WebView 和更新检查不读取此授权。
- network_security_config 保留 cleartext 传输能力以兼容用户明确授权来源，并在注释中声明应用层守卫；不再以 tools:ignore 隐藏规则。

**Step 5：验证并提交**

提交前新增 ManifestExportedComponentContractTest，固定 allowlist 为 MainActivity、OpenSchemeActivity、OpenFileActivity、OpenTileActivity、ShizukuProvider、ExposeService、AppInstallReceiver、七个 Quick Settings Tile、两个 Widget Receiver 和 FocusWidgetConfigActivity；逐项断言：

- Tile Service 保留 android.permission.BIND_QUICK_SETTINGS_TILE。
- ShizukuProvider 保留 android.permission.INTERACT_ACROSS_USERS_FULL。
- OpenSchemeActivity 只接受 DeepLinkParser 的语义 route。
- OpenFileActivity 只接受 content Uri、已授予读权限、zip magic 与 SafeArchivePolicy。
- OpenTileActivity、Widget Receiver、Widget Config 只接受对应系统 action 与有效 appWidgetId。
- 所有通知和 Widget PendingIntent 使用显式 component、FLAG_IMMUTABLE 与唯一 requestCode。
- allowlist 之外的 activity/service/receiver/provider 不得 exported=true。

~~~bash
./gradlew :app:testGkdDebugUnitTest --tests '*Remote*' --tests '*ExposeCommand*' --tests '*CleartextOrigin*' --tests '*WebOrigin*'
./gradlew :app:testGkdDebugUnitTest
./gradlew :app:lintGkdDebug :app:lintPlayDebug
python3 scripts/verify-sensitive-output-policy.py
git diff --check
git add gradle/libs.versions.toml app/build.gradle.kts app/src/main/kotlin/li/songe/gkd/sdp/remote app/src/main/assets/http-inspector app/src/test/kotlin/li/songe/gkd/sdp/remote app/src/main/kotlin/li/songe/gkd/sdp/service/HttpService.kt app/src/main/kotlin/li/songe/gkd/sdp/service/ExposeService.kt app/src/main/kotlin/li/songe/gkd/sdp/ui/WebViewPage.kt app/src/main/kotlin/li/songe/gkd/sdp/ui/AuthA11yPage.kt app/src/main/kotlin/li/songe/gkd/sdp/ui/home/ControlPage.kt app/src/main/kotlin/li/songe/gkd/sdp/OpenSchemeActivity.kt app/src/main/kotlin/li/songe/gkd/sdp/OpenFileActivity.kt app/src/main/kotlin/li/songe/gkd/sdp/OpenTileActivity.kt app/src/main/kotlin/li/songe/gkd/sdp/notif/Notif.kt app/src/main/kotlin/li/songe/gkd/sdp/widget/FocusQuickStartWidget.kt app/src/main/kotlin/li/songe/gkd/sdp/widget/UsageGuardReviewWidget.kt app/src/main/kotlin/li/songe/gkd/sdp/widget/FocusWidgetConfigActivity.kt app/src/main/kotlin/li/songe/gkd/sdp/util/NetworkExt.kt app/src/main/kotlin/li/songe/gkd/sdp/util/FolderExt.kt app/src/main/res/xml/network_security_config.xml app/src/main/AndroidManifest.xml README_DEV.md PRIVACY.md
git commit -m "fix: secure local remote interfaces"
~~~

### Task 8：完成 PR 1 验证、审查、合并

**Step 1：运行完整验证**

~~~bash
python3 -m unittest discover -s scripts/tests -p 'test_*.py' -v
bash scripts/generate-security-dependency-report.sh
./gradlew :selector:jvmTest :app:testGkdDebugUnitTest :app:lintGkdDebug :app:lintPlayDebug
python3 scripts/verify-security-dependency-report.py --report build/reports/security-dependencies.txt
./gradlew :app:assembleGkdDebug :app:assemblePlayDebug :app:assembleGkdRelease :app:assemblePlayRelease
git diff --check
git status --short
~~~

预期：全部命令退出码为 0，git status 为空。

**Step 2：推送并创建 PR**

~~~bash
git push -u origin codex/quality-security-data
PR_URL="$(gh pr create --base main --head codex/quality-security-data --title "fix: harden data and remote interfaces" --body '目的：最小化诊断与支持包数据，增加加密事务备份，限制系统备份，并为远程入口增加授权边界。影响：诊断、备份、远程调试与网络来源。未改：运行时 owner、Overlay 成功记录边界、锁定与自动重开规则。验证：Python 契约、JVM、Lint、四变体构建。真机/OEM：未执行。')"
gh pr edit "$PR_URL" --body "目的：最小化诊断与支持包数据，增加加密事务备份，限制系统备份，并为远程入口增加授权边界。影响：诊断、备份、远程调试与网络来源。未改：运行时 owner、Overlay 成功记录边界、锁定与自动重开规则。验证：Python 契约、JVM、Lint、四变体构建。Actions：$PR_URL/checks。真机/OEM：未执行。"
gh pr checks --watch --fail-fast
~~~

**Step 3：完成代码审查并合并**

~~~bash
gh pr view --json state,mergeStateStatus,statusCheckRollup,reviewDecision,url
gh pr merge --squash --delete-branch
git -C ../.. switch main
git -C ../.. pull --ff-only origin main
cd ../..
~~~

预期：PR state=MERGED，main 与 origin/main 指向同一提交。

## 4. PR 2：架构、生命周期与可维护性

### Task 9：注入统一时钟、调度器与应用依赖

**Files:**

- Create: app/src/main/kotlin/li/songe/gkd/sdp/runtime/SdpClock.kt
- Create: app/src/main/kotlin/li/songe/gkd/sdp/runtime/SdpDispatchers.kt
- Create: app/src/main/kotlin/li/songe/gkd/sdp/runtime/AppDependencies.kt
- Create: app/src/test/kotlin/li/songe/gkd/sdp/runtime/FakeSdpClock.kt
- Modify: app/src/main/kotlin/li/songe/gkd/sdp/App.kt
- Modify: app/src/main/kotlin/li/songe/gkd/sdp/a11y/SdpRuntimeFeatureCoordinator.kt
- Modify: app/src/main/kotlin/li/songe/gkd/sdp/a11y/UsageGuardEngine.kt
- Modify: app/src/main/kotlin/li/songe/gkd/sdp/a11y/AppBlockerEngine.kt
- Modify: app/src/main/kotlin/li/songe/gkd/sdp/a11y/UrlBlockerEngine.kt
- Modify: app/src/main/kotlin/li/songe/gkd/sdp/a11y/FocusModeEngine.kt
- Modify: app/src/main/kotlin/li/songe/gkd/sdp/service/AutoReenableEnforcer.kt
- Modify: app/src/main/kotlin/li/songe/gkd/sdp/service/AccessibilityGuardCoordinator.kt
- Modify: app/src/main/kotlin/li/songe/gkd/sdp/data/SelfControlIntervalRepository.kt
- Modify: app/src/main/kotlin/li/songe/gkd/sdp/util/DigitalSelfDisciplineReviewPolicy.kt
- Modify: existing tests for each modified class

**Step 1：创建分支**

~~~bash
git fetch origin --prune
git worktree add .worktrees/quality-architecture-lifecycle -b codex/quality-architecture-lifecycle origin/main
cd .worktrees/quality-architecture-lifecycle
~~~

**Step 2：先写失败测试**

固定接口：

- SdpClock.nowEpochMillis() 与 elapsedRealtimeMillis() 分离墙钟和单调时钟。
- SdpDispatchers 提供 main、io、default、a11yEvent、a11yQuery、a11yAction；后三者继续沿用现有单线程顺序。
- AppDependencies 在 App 创建一次；测试构造函数直接传 fake，不读取全局对象。
- 时钟回拨只影响日期窗口重算，不产生负 requestGap、负使用时长、提前解锁或重复自动重开。
- owner 在 Accessibility 与 Automation/Shizuku 间切换时继续共用同一 coordinator、clock 和 repository。

**Step 3：迁移数字自律链路**

按 coordinator → engines → services → repositories → policies 顺序，把默认 System.currentTimeMillis/Dispatchers 替换为构造参数；Compose 的纯显示 ticker 保留单独 SdpClock。

禁止改变：

- A11yRuleEngine 事件、查询、动作 dispatcher 的线程数与顺序。
- Accepted 后才写冷却、addView 成功后才写历史。
- 使用申请只以成功 UsageGuardRecord 为事实来源。
- 锁定、每日关闭额度与自动重开规则。

**Step 4：验证并提交**

~~~bash
./gradlew :app:testGkdDebugUnitTest --tests '*SdpRuntimeFeatureCoordinatorTest' --tests '*UsageGuard*' --tests '*AutoReenable*' --tests '*SelfControl*'
./gradlew :app:testGkdDebugUnitTest
git diff --check
git add app/src/main/kotlin/li/songe/gkd/sdp/runtime app/src/test/kotlin/li/songe/gkd/sdp/runtime app/src/main/kotlin/li/songe/gkd/sdp/App.kt app/src/main/kotlin/li/songe/gkd/sdp/a11y app/src/main/kotlin/li/songe/gkd/sdp/service app/src/main/kotlin/li/songe/gkd/sdp/data/SelfControlIntervalRepository.kt app/src/main/kotlin/li/songe/gkd/sdp/util/DigitalSelfDisciplineReviewPolicy.kt app/src/test
git commit -m "refactor: inject self-control runtime dependencies"
~~~

### Task 10：让所有 Compose Flow 收集遵守生命周期

**Files:**

- Modify: gradle/libs.versions.toml
- Modify: app/build.gradle.kts
- Create: app/src/main/kotlin/li/songe/gkd/sdp/ui/share/ServiceOverlayLifecycleOwner.kt
- Modify: app/src/main/kotlin/li/songe/gkd/sdp/ui/share/StateExt.kt
- Modify: all app/src/main Kotlin files importing androidx.compose.runtime.collectAsState
- Create: scripts/verify-compose-lifecycle-policy.py
- Create: scripts/tests/test_compose_lifecycle_policy.py
- Modify: .github/workflows/ci.yml

**Step 1：先写失败契约**

- 加入 androidx.lifecycle:lifecycle-runtime-compose:2.10.0。
- 页面、Activity 与 Dialog 的 Flow/StateFlow 使用 collectAsStateWithLifecycle。
- Service Overlay 在 addView 前安装 ServiceOverlayLifecycleOwner，状态顺序固定 INITIALIZED → CREATED → STARTED；removeView 后固定 DESTROYED。
- Lifecycle DESTROYED 后 Flow 更新不触发 Compose 重组。
- 静态脚本拒绝生产源码 import androidx.compose.runtime.collectAsState。
- 非 Compose 的长期进程收集继续使用明确 appScope/service scope，不转换为 UI API。

**Step 2：实现并批量迁移**

按 MainActivity → home → ui → component → service overlays 的顺序迁移；每一组完成后编译 gkdDebug 和 playDebug。

**Step 3：验证并提交**

~~~bash
python3 scripts/verify-compose-lifecycle-policy.py
python3 -m unittest scripts.tests.test_compose_lifecycle_policy -v
./gradlew :app:testGkdDebugUnitTest :app:compileGkdDebugKotlin :app:compilePlayDebugKotlin
git diff --check
git add gradle/libs.versions.toml app/build.gradle.kts app/src/main/kotlin app/src/test scripts/verify-compose-lifecycle-policy.py scripts/tests/test_compose_lifecycle_policy.py .github/workflows/ci.yml
git commit -m "refactor: collect Compose state with lifecycle"
~~~

### Task 11：持久化导航并移除 MainViewModel 全局单例

**Files:**

- Create: app/src/main/kotlin/li/songe/gkd/sdp/navigation/AppDestination.kt
- Create: app/src/main/kotlin/li/songe/gkd/sdp/navigation/AppNavigator.kt
- Create: app/src/main/kotlin/li/songe/gkd/sdp/navigation/DeepLinkParser.kt
- Create: app/src/test/kotlin/li/songe/gkd/sdp/navigation/DeepLinkParserTest.kt
- Create: app/src/androidTest/kotlin/li/songe/gkd/sdp/navigation/NavigationRestoreTest.kt
- Modify: app/src/main/kotlin/li/songe/gkd/sdp/MainActivity.kt
- Modify: app/src/main/kotlin/li/songe/gkd/sdp/MainViewModel.kt
- Modify: app/src/main/kotlin/li/songe/gkd/sdp/permission/PermissionState.kt
- Modify: app/src/main/kotlin/li/songe/gkd/sdp/ui/CrashReportVm.kt
- Modify: app/src/main/kotlin/li/songe/gkd/sdp/ui/share/AppFilter.kt
- Modify: app/src/main/kotlin/li/songe/gkd/sdp/notif/Notif.kt
- Modify: app/src/main/kotlin/li/songe/gkd/sdp/widget/UsageGuardReviewWidget.kt
- Modify: gradle/libs.versions.toml
- Modify: app/src/main/kotlin/li/songe/gkd/sdp/service/StatusService.kt
- Modify: app/src/main/kotlin/li/songe/gkd/sdp/ui/WebViewPage.kt

**Step 1：先写失败测试**

固定语义深链：

- gkd://overview
- gkd://self-control
- gkd://rules/subscriptions
- gkd://rules/apps
- gkd://settings
- gkd://settings/capabilities
- gkd://settings/privacy-data
- gkd://usage-guard
- gkd://usage-review
- gkd://action-log

旧 gkd://page/N 与 tab=N 只在 2.2.0 保留兼容映射，并在解析后立即转换为语义 destination。

测试覆盖：

- 未知 scheme/host/path/query 不导航并返回 INVALID_DEEP_LINK。
- Activity 重建后完整恢复栈与一级 tab。
- 外部 Intent 重放不重复压入同一 destination。
- 所有 NavKey 可序列化。
- 源码不再包含 MainViewModel.instance。

**Step 2：实现导航边界**

- AppNavigator 持有 saveable NavBackStack 与 SharedFlow NavigationEffect。
- MainViewModel 接收 AppNavigator，不暴露静态 instance。
- PermissionState 发送 NavigationEffect；CrashReportVm 与 AppFilter 通过构造参数接收所需 repository/flow。
- 通知、Widget、StatusService 全部生成语义 Uri。
- WebView gkd:// 链接交给 DeepLinkParser，不直接访问 Activity.mainVm。

**Step 3：验证并提交**

~~~bash
./gradlew :app:testGkdDebugUnitTest --tests '*DeepLinkParserTest'
./gradlew :app:compileGkdDebugKotlin :app:compilePlayDebugKotlin
rg -n 'MainViewModel\\.instance|gkd://page' app/src/main/kotlin && exit 1 || true
git diff --check
git add app/src/main/kotlin/li/songe/gkd/sdp/navigation app/src/test/kotlin/li/songe/gkd/sdp/navigation app/src/androidTest/kotlin/li/songe/gkd/sdp/navigation app/src/main/kotlin/li/songe/gkd/sdp/MainActivity.kt app/src/main/kotlin/li/songe/gkd/sdp/MainViewModel.kt app/src/main/kotlin/li/songe/gkd/sdp/permission/PermissionState.kt app/src/main/kotlin/li/songe/gkd/sdp/ui/CrashReportVm.kt app/src/main/kotlin/li/songe/gkd/sdp/ui/share/AppFilter.kt app/src/main/kotlin/li/songe/gkd/sdp/notif/Notif.kt app/src/main/kotlin/li/songe/gkd/sdp/widget/UsageGuardReviewWidget.kt app/src/main/kotlin/li/songe/gkd/sdp/service/StatusService.kt app/src/main/kotlin/li/songe/gkd/sdp/ui/WebViewPage.kt
git commit -m "refactor: persist semantic navigation"
~~~

### Task 12：拆分超大页面与 Overlay 宿主

**Files:**

- Split: app/src/main/kotlin/li/songe/gkd/sdp/ui/AppBlockerPage.kt → ui/appblocker/
- Split: app/src/main/kotlin/li/songe/gkd/sdp/ui/FocusLockPage.kt → ui/focuslock/
- Split: app/src/main/kotlin/li/songe/gkd/sdp/ui/FocusModePage.kt → ui/focusmode/
- Split: app/src/main/kotlin/li/songe/gkd/sdp/ui/UrlBlockerComponents.kt → ui/urlblocker/
- Split: app/src/main/kotlin/li/songe/gkd/sdp/ui/UsageGuardPage.kt → ui/usageguard/
- Split: app/src/main/kotlin/li/songe/gkd/sdp/ui/UsageGuardReviewPage.kt → ui/usagereview/
- Split: app/src/main/kotlin/li/songe/gkd/sdp/ui/ActionLogPage.kt → ui/actionlog/
- Split: app/src/main/kotlin/li/songe/gkd/sdp/ui/home/SettingsPage.kt → ui/settings/
- Split: app/src/main/kotlin/li/songe/gkd/sdp/service/UsageGuardRequestOverlayService.kt → service/usageguardrequest/
- Split: app/src/main/kotlin/li/songe/gkd/sdp/service/UsageGuardCountdownOverlayService.kt → service/usageguardcountdown/
- Create: scripts/verify-ui-file-boundaries.py
- Create: scripts/tests/test_ui_file_boundaries.py

**Step 1：先锁定行为测试**

在移动代码前为每个页面建立 presenter/state/action 契约，覆盖：

- 初始、加载、空、内容、错误状态。
- 编辑、保存、取消、删除、锁定、禁用额度和自动重开事件。
- 使用申请成功/取消/超时、倒计时与截图模式。
- 拦截来源、ActionLog outcome 与图表聚合。

全部现有单元测试先通过并保存测试输出。

**Step 2：按固定结构拆分**

每个目录固定包含：

- Route.kt：NavKey 与 route composable。
- Screen.kt：Scaffold 和顶层布局。
- UiState.kt：不可变 state、UiAction、UiEffect。
- Presenter.kt：StateFlow 组合与 action 分发。
- Sections.kt：页面 section。
- Dialogs.kt：对话框与 sheet。
- Editor.kt：表单编辑器。

Overlay 目录固定包含 ServiceHost.kt、WindowController.kt、Screen.kt、UiState.kt、Presenter.kt。

页面与 Overlay 宿主文件上限 500 行；静态脚本扫描 ui、ui/home 和 service/*overlay*，拒绝超限与单个 composable 超过 180 行。A11yRuleEngine、RawSubscription 与未修改上游解析器不进入此行数门禁。

**Step 3：验证无行为变化**

~~~bash
python3 scripts/verify-ui-file-boundaries.py
./gradlew :app:testGkdDebugUnitTest
./gradlew :app:compileGkdDebugKotlin :app:compilePlayDebugKotlin
git diff --check
git add app/src/main/kotlin/li/songe/gkd/sdp/ui app/src/main/kotlin/li/songe/gkd/sdp/service app/src/test scripts/verify-ui-file-boundaries.py scripts/tests/test_ui_file_boundaries.py
git commit -m "refactor: split large UI and overlay hosts"
~~~

### Task 13：完成 PR 2 验证、审查、合并

**Step 1：更新文档**

- README_DEV.md 增加 AppDependencies、LifecycleOwner、AppNavigator 和页面目录约定。
- docs/testing/architecture-regression-matrix.md 固定记录 Accessibility 与 Automation/Shizuku parity、进程重建、时钟回拨、Overlay 挂载失败、取消协程和两 flavor 编译用例。
- CHANGELOG.md 的 [Unreleased] 增加内部可靠性说明，不写未验证的体验承诺。

**Step 2：运行、推送、合并**

~~~bash
python3 -m unittest discover -s scripts/tests -p 'test_*.py' -v
./gradlew :selector:jvmTest :app:testGkdDebugUnitTest :app:lintGkdDebug :app:lintPlayDebug
./gradlew :app:assembleGkdDebug :app:assemblePlayDebug :app:assembleGkdRelease :app:assemblePlayRelease
git diff --check
git add README_DEV.md docs/testing/architecture-regression-matrix.md CHANGELOG.md
git commit -m "docs: document runtime architecture contracts"
git push -u origin codex/quality-architecture-lifecycle
PR_URL="$(gh pr create --base main --head codex/quality-architecture-lifecycle --title "refactor: strengthen lifecycle and navigation architecture" --body '目的：注入自律运行时依赖，修复 Compose 生命周期收集，持久化语义导航，并拆分超大 UI/Overlay 宿主。影响：架构、导航与生命周期。未改：无障碍线程顺序、运行模式 parity、Overlay 记录与锁定规则。验证：Python 契约、JVM、Lint、四变体构建。真机/OEM：未执行。')"
gh pr edit "$PR_URL" --body "目的：注入自律运行时依赖，修复 Compose 生命周期收集，持久化语义导航，并拆分超大 UI/Overlay 宿主。影响：架构、导航与生命周期。未改：无障碍线程顺序、运行模式 parity、Overlay 记录与锁定规则。验证：Python 契约、JVM、Lint、四变体构建。Actions：$PR_URL/checks。真机/OEM：未执行。"
gh pr checks --watch --fail-fast
gh pr view --json state,mergeStateStatus,statusCheckRollup,reviewDecision,url
gh pr merge --squash --delete-branch
git -C ../.. switch main
git -C ../.. pull --ff-only origin main
cd ../..
~~~

预期：PR state=MERGED，main 与 origin/main 指向同一提交。

## 5. PR 3：统一设计系统、内容与无障碍

### Task 14：建立设计 Token 与动效规则

**Files:**

- Create: app/src/main/kotlin/li/songe/gkd/sdp/ui/style/ColorTokens.kt
- Create: app/src/main/kotlin/li/songe/gkd/sdp/ui/style/DimensionTokens.kt
- Create: app/src/main/kotlin/li/songe/gkd/sdp/ui/style/ShapeTokens.kt
- Create: app/src/main/kotlin/li/songe/gkd/sdp/ui/style/MotionTokens.kt
- Create: app/src/main/kotlin/li/songe/gkd/sdp/ui/style/TypographyTokens.kt
- Create: app/src/main/kotlin/li/songe/gkd/sdp/ui/style/ResponsiveTokens.kt
- Create: app/src/test/kotlin/li/songe/gkd/sdp/ui/style/DesignTokenContractTest.kt
- Modify: app/src/main/kotlin/li/songe/gkd/sdp/ui/style/Theme.kt
- Modify: app/src/main/kotlin/li/songe/gkd/sdp/ui/style/Color.kt
- Modify: app/src/main/kotlin/li/songe/gkd/sdp/ui/style/Padding.kt
- Create: docs/design/design-system.md

**Step 1：创建分支**

~~~bash
git fetch origin --prune
git worktree add .worktrees/quality-design-localization -b codex/quality-design-localization origin/main
cd .worktrees/quality-design-localization
~~~

**Step 2：先写失败 Token 契约**

固定颜色：

| 角色 | Light | Dark |
|---|---:|---:|
| primary | #4F46E5 | #A5B4FC |
| onPrimary | #FFFFFF | #1E1B4B |
| primaryContainer | #E0E7FF | #312E81 |
| secondary | #0F766E | #5EEAD4 |
| secondaryContainer | #CCFBF1 | #134E4A |
| tertiary | #B45309 | #FCD34D |
| error | #B3261E | #FFB4AB |
| background | #F8FAFC | #0F172A |
| surface | #FFFFFF | #111827 |
| surfaceVariant | #F1F5F9 | #1E293B |
| onSurface | #0F172A | #F8FAFC |
| onSurfaceVariant | #475569 | #CBD5E1 |
| outline | #64748B | #94A3B8 |

固定尺寸：

- spacing：4、8、12、16、20、24、32、40、48dp。
- 页面水平 padding：紧凑 16dp、中等 24dp、扩展 32dp。
- 内容最大宽度：表单 720dp、列表/复盘 960dp。
- 触控目标最小 48×48dp；图标视觉尺寸 20/24dp。
- 圆角：8、12、16、24dp；卡片默认 16dp。
- 顶部栏 64dp；紧凑底部导航使用 Material 3 默认高度。

固定字体：

- displaySmall 32/40sp、headlineSmall 24/32sp、titleLarge 20/28sp、titleMedium 16/24sp、bodyLarge 16/24sp、bodyMedium 14/20sp、labelLarge 14/20sp。
- 使用系统无衬线字体，不捆绑新字体文件。
- 用户字体缩放完全交给 sp，不对 body 文本设置 maxLines=1。

固定动效：

- 微交互 120ms、页面/主题过渡 180ms、强调过渡 240ms。
- easing 使用 FastOutSlowInEasing；进度变化使用 LinearEasing。
- LocalMotionDurationScale.scaleFactor=0 时所有非必要动画时长为 0。
- 倒计时只更新文本，不做每秒缩放、闪烁或 live region。

**Step 3：实现并迁移 Theme**

- Theme.kt 只组装 light/dark ColorScheme 与 CompositionLocal Token。
- 删除对每个 ColorScheme 字段的 500ms animateColorAsState。
- Color.kt 和 Padding.kt 的散落常量迁移到 Token；保留兼容别名一个 PR，Task 18 前删除别名。
- design-system.md 写明组件层级、颜色角色、栅格、字体、触控、动效、图表与状态使用规则。

**Step 4：验证并提交**

~~~bash
./gradlew :app:testGkdDebugUnitTest --tests '*DesignTokenContractTest'
./gradlew :app:compileGkdDebugKotlin :app:compilePlayDebugKotlin
git diff --check
git add app/src/main/kotlin/li/songe/gkd/sdp/ui/style app/src/test/kotlin/li/songe/gkd/sdp/ui/style docs/design/design-system.md
git commit -m "feat: establish accessible design tokens"
~~~

### Task 15：建立统一页面、反馈、表单与图表组件

**Files:**

- Create: app/src/main/kotlin/li/songe/gkd/sdp/ui/component/AppScaffold.kt
- Create: app/src/main/kotlin/li/songe/gkd/sdp/ui/component/ContentState.kt
- Create: app/src/main/kotlin/li/songe/gkd/sdp/ui/component/InlineMessage.kt
- Create: app/src/main/kotlin/li/songe/gkd/sdp/ui/component/AppFormField.kt
- Create: app/src/main/kotlin/li/songe/gkd/sdp/ui/component/AppActionBar.kt
- Create: app/src/main/kotlin/li/songe/gkd/sdp/ui/component/AppConfirmationDialog.kt
- Create: app/src/main/kotlin/li/songe/gkd/sdp/ui/component/AppDataChart.kt
- Create: app/src/main/kotlin/li/songe/gkd/sdp/ui/component/AppMetricCard.kt
- Create: app/src/main/kotlin/li/songe/gkd/sdp/ui/component/AccessibleIconButton.kt
- Create: app/src/test/kotlin/li/songe/gkd/sdp/ui/component/CommonComponentContractTest.kt
- Modify: app/src/main/kotlin/li/songe/gkd/sdp/ui/component/CustomIconButton.kt
- Modify: app/src/main/kotlin/li/songe/gkd/sdp/ui/component/EmptyText.kt
- Modify: app/src/main/kotlin/li/songe/gkd/sdp/ui/component/SettingItem.kt
- Modify: app/src/main/kotlin/li/songe/gkd/sdp/ui/home/ControlPage.kt

**Step 1：先写失败组件契约**

固定行为：

- ContentState 只有 Loading、Empty、Content、Error 四种；Loading 超过 400ms 才显示进度，避免闪烁。
- Empty 包含标题、说明、单一主动作；Error 包含稳定错误码、说明、重试与上下文恢复动作。
- InlineMessage 有 info/success/warning/error 图标、标题和文本，不只用颜色。
- AppFormField 同时提供 label、value、supportingText、errorText、字符计数、键盘类型、IME action；错误出现时保持焦点字段可见。
- AppActionBar 在紧凑宽度固定底部，在中等/扩展宽度固定内容列底部；主按钮防重复提交。
- AppConfirmationDialog 的破坏性动作使用 error 色、明确对象名称和结果；全量删除要求输入“删除全部数据”。
- AppDataChart 同时提供可视图、摘要文字、点选明细和语义数据表。
- AccessibleIconButton 外层最小 48dp、图标 24dp、必须提供 contentDescription 与 onClickLabel。

**Step 2：替换明显违规组件**

- CustomIconButton 和 PerfIconButton 全部转发到 48dp 触控壳。
- decorative icon 使用 null contentDescription，并由父节点提供合并语义；可点击 icon 禁止 null。
- 删除 ControlPage.ServerStatusCard 的空 onClick 和“不执行操作”语义；非交互状态卡不添加 clickable。
- SettingItem 整行点击与 Switch 合并为单一语义动作，避免双重焦点。
- EmptyText 改为 ContentState.Empty。

**Step 3：验证并提交**

~~~bash
./gradlew :app:testGkdDebugUnitTest --tests '*CommonComponentContractTest'
./gradlew :app:testGkdDebugUnitTest
./gradlew :app:lintGkdDebug :app:lintPlayDebug
git diff --check
git add app/src/main/kotlin/li/songe/gkd/sdp/ui/component app/src/main/kotlin/li/songe/gkd/sdp/ui/home/ControlPage.kt app/src/test/kotlin/li/songe/gkd/sdp/ui/component
git commit -m "feat: unify UI state and feedback components"
~~~

### Task 16：迁移用户文案并建立中英文本门禁

**Files:**

- Create: quality-lint/build.gradle.kts
- Create: quality-lint/src/main/kotlin/li/songe/gkd/sdp/lint/ComposeHardcodedTextDetector.kt
- Create: quality-lint/src/main/kotlin/li/songe/gkd/sdp/lint/SdpIssueRegistry.kt
- Create: quality-lint/src/test/kotlin/li/songe/gkd/sdp/lint/ComposeHardcodedTextDetectorTest.kt
- Modify: settings.gradle.kts
- Modify: gradle/libs.versions.toml
- Modify: app/build.gradle.kts
- Expand: app/src/main/res/values/strings.xml
- Create: app/src/main/res/values-en/strings.xml
- Create: app/src/main/res/values/plurals.xml
- Create: app/src/main/res/values-en/plurals.xml
- Modify: all app/src/main production Kotlin files containing user-visible literal strings
- Create: docs/design/content-style-guide.md

**Step 1：先写失败自定义 Lint 测试**

使用 com.android.tools.lint:lint-api:32.3.0 与 lint-tests:32.3.0，固定检测：

- Text、Button、TextField、Snackbar、Toast、Dialog title/text、contentDescription、onClickLabel 和 Notification 用户文案中的硬编码字符串。
- 字符串拼接改为 stringResource 格式参数或 plurals。
- debug-only 日志事件代码、测试数据、正则、SQL、route、稳定错误码和协议常量不报用户文案问题。
- lintChecks(project(":quality-lint")) 只参与构建，不打入 APK。

运行：

~~~bash
./gradlew :quality-lint:test :app:lintGkdDebug
~~~

预期：自定义 Lint 测试先因模块不存在失败；模块建立后 app lint 因现有硬编码文案失败。

**Step 2：固定中文术语**

content-style-guide.md 固定：

- Usage Guard：使用申请。
- request gap：距离上次结束使用。
- interval-to-request ratio：间用比。
- 解释文案：间用比 = 距离上次结束使用 ÷ 本次申请时长。
- self-control review：数字自律复盘。
- interception source：拦截来源。
- Automation/Shizuku：自动化模式（Shizuku）。
- Accessibility：无障碍模式。
- 使用“开启 / 关闭”“保存 / 取消”“删除 / 保留”，不混用“确定 / 好的 / 继续”表达同一动作。
- 文案只描述功能和数据，不承诺治疗、成瘾改善、意志力提升或心理效果。

**Step 3：按固定顺序迁移资源**

1. Manifest、通知、Widget、Service、Overlay。
2. 首页、设置、权限中心与导航。
3. 使用申请、复盘、拦截、专注、应用/URL 规则。
4. 订阅、应用列表、快照、日志、WebView、关于页。
5. 通用组件与错误文案。

资源命名固定为 feature_element_state，例如 usage_request_duration_label、remote_session_expired_error。数量使用 plurals；日期、数字和时长使用 Locale 与现有 formatter，不拼接单位。

values 为简体中文基准，values-en 为完整英文；两边 key 集合完全一致。app_name、技术协议名和用户生成标签不翻译。

**Step 4：验证并提交**

~~~bash
./gradlew :quality-lint:test
./gradlew :app:lintGkdDebug :app:lintPlayDebug
./gradlew :app:testGkdDebugUnitTest
git diff --check
git add quality-lint settings.gradle.kts gradle/libs.versions.toml app/build.gradle.kts app/src/main/res app/src/main/kotlin docs/design/content-style-guide.md
git commit -m "refactor: localize all user-facing content"
~~~

### Task 17：完成全局无障碍与大字体修复

**Files:**

- Modify: all app/src/main/kotlin/li/songe/gkd/sdp/ui/**
- Modify: app/src/main/kotlin/li/songe/gkd/sdp/service/**/*Screen.kt
- Create: app/src/test/kotlin/li/songe/gkd/sdp/ui/AccessibilityPresentationContractTest.kt
- Create: app/src/androidTest/kotlin/li/songe/gkd/sdp/ui/AccessibilitySmokeTest.kt
- Create: docs/testing/accessibility-matrix.md

**Step 1：先写失败语义测试**

关键节点固定断言：

- 所有可点击、可切换、可拖动和可展开元素有 role、stateDescription、onClickLabel 或 progressBarRangeInfo。
- 最小触控尺寸 48dp。
- 规则启用、服务状态、错误、图表系列和正负趋势不只靠颜色。
- 图标按钮的可访问名称在同一屏唯一。
- 表单 label 永久可见，placeholder 不承担 label。
- 错误焦点移动到第一个无效字段，并由 assertive announcement 只播报一次。
- 倒计时只在用户聚焦时读取剩余时长，不设置持续 liveRegion。
- 图表父节点提供摘要，数据表逐行提供时间、数值、单位与状态。

**Step 2：逐页修复**

固定检查顺序：

1. 首页与一级导航。
2. 权限中心与设置。
3. 使用申请、倒计时、拦截 Overlay 与复盘。
4. 专注、应用拦截、URL 拦截与锁定。
5. 订阅、应用列表、快照与日志。
6. Dialog、BottomSheet、Menu、日期/时间选择器。

每页同时验证字体 scale 1.0、1.3、2.0；横向与纵向布局不截断主动作、不覆盖输入框、不丢失滚动。

**Step 3：记录自动化矩阵**

accessibility-matrix.md 对每个一级页面记录：

- 可访问标题。
- 焦点顺序。
- 主动作。
- 状态表达。
- 大字体结果。
- 图表文字替代。
- 对应自动化测试名。

**Step 4：验证并提交**

~~~bash
./gradlew :app:testGkdDebugUnitTest --tests '*Accessibility*'
./gradlew :app:lintGkdDebug :app:lintPlayDebug
git diff --check
git add app/src/main/kotlin/li/songe/gkd/sdp/ui app/src/main/kotlin/li/songe/gkd/sdp/service app/src/test/kotlin/li/songe/gkd/sdp/ui app/src/androidTest/kotlin/li/songe/gkd/sdp/ui docs/testing/accessibility-matrix.md
git commit -m "fix: make core UI accessible at large text"
~~~

### Task 18：完成 PR 3 验证、审查、合并

**Step 1：删除临时兼容别名并执行静态扫描**

~~~bash
rg -n '500\\.milliseconds|size\\((24|28|32|36|40)\\.dp\\).*clickable|clickable.*onClick = \\{ \\}' app/src/main/kotlin
./gradlew :quality-lint:test :app:lintGkdDebug :app:lintPlayDebug
~~~

把扫描结果逐项清零；删除 Task 14 保留的旧 Token 别名。

**Step 2：运行、推送、合并**

~~~bash
python3 -m unittest discover -s scripts/tests -p 'test_*.py' -v
./gradlew :selector:jvmTest :app:testGkdDebugUnitTest :app:lintGkdDebug :app:lintPlayDebug
./gradlew :app:assembleGkdDebug :app:assemblePlayDebug :app:assembleGkdRelease :app:assemblePlayRelease
git diff --check
git status --short
git push -u origin codex/quality-design-localization
PR_URL="$(gh pr create --base main --head codex/quality-design-localization --title "feat: unify design accessibility and content" --body '目的：建立设计 Token、统一状态/表单/图表组件、完成中英文本与全局无障碍修复。影响：全部 Compose 页面与用户文案。未改：业务计算与运行时状态机。验证：自定义 Lint、JVM、Android Lint、四变体构建。真机/OEM：未执行。')"
gh pr edit "$PR_URL" --body "目的：建立设计 Token、统一状态/表单/图表组件、完成中英文本与全局无障碍修复。影响：全部 Compose 页面与用户文案。未改：业务计算与运行时状态机。验证：自定义 Lint、JVM、Android Lint、四变体构建。Actions：$PR_URL/checks。真机/OEM：未执行。"
gh pr checks --watch --fail-fast
gh pr view --json state,mergeStateStatus,statusCheckRollup,reviewDecision,url
gh pr merge --squash --delete-branch
git -C ../.. switch main
git -C ../.. pull --ff-only origin main
cd ../..
~~~

预期：PR state=MERGED，main 与 origin/main 指向同一提交。

## 6. PR 4：信息架构与核心产品体验

### Task 19：重构一级信息架构与自适应导航

**Files:**

- Modify: gradle/libs.versions.toml
- Modify: app/build.gradle.kts
- Create: app/src/main/kotlin/li/songe/gkd/sdp/ui/home/HomeDestination.kt
- Create: app/src/main/kotlin/li/songe/gkd/sdp/ui/home/AdaptiveHomeScaffold.kt
- Create: app/src/main/kotlin/li/songe/gkd/sdp/ui/overview/OverviewScreen.kt
- Create: app/src/main/kotlin/li/songe/gkd/sdp/ui/selfcontrol/SelfControlHubScreen.kt
- Create: app/src/main/kotlin/li/songe/gkd/sdp/ui/rules/RulesScreen.kt
- Create: app/src/test/kotlin/li/songe/gkd/sdp/ui/home/HomeInformationArchitectureTest.kt
- Modify: app/src/main/kotlin/li/songe/gkd/sdp/ui/home/HomePage.kt
- Modify: app/src/main/kotlin/li/songe/gkd/sdp/ui/home/ControlPage.kt
- Modify: app/src/main/kotlin/li/songe/gkd/sdp/ui/home/SubsManagePage.kt
- Modify: app/src/main/kotlin/li/songe/gkd/sdp/ui/home/AppListPage.kt
- Modify: app/src/main/kotlin/li/songe/gkd/sdp/navigation/DeepLinkParser.kt

**Step 1：创建分支并加入稳定自适应依赖**

~~~bash
git fetch origin --prune
git worktree add .worktrees/quality-product-experience -b codex/quality-product-experience origin/main
cd .worktrees/quality-product-experience
~~~

在版本目录固定新增：

- androidx.compose.material3.adaptive:adaptive:1.2.0
- androidx.compose.material3.adaptive:adaptive-navigation-suite:1.2.0

**Step 2：先写失败信息架构测试**

一级目标固定为：

1. 概览：服务总状态、当前运行模式、待处理能力、今日摘要、最近触发、快速开始/停止。
2. 自律：使用申请、数字自律复盘、专注模式、应用拦截、URL 拦截、锁定保护。
3. 规则：二级 tab 固定“订阅规则 / 应用规则”，保留现有订阅管理与应用列表全部能力。
4. 设置：搜索、运行能力、规则与自律设置、显示与无障碍、隐私与数据、诊断、关于。

测试固定断言：

- 旧 Control 映射到 Overview；SubsManage/AppList 映射到 Rules 对应 tab；旧 Settings 映射到 Settings。
- 深链进入二级页后返回到所属一级目标。
- 重复点击当前一级目标滚动到顶部；再次点击不重建 ViewModel。
- tab、滚动位置和搜索条件在 Activity 重建后恢复。

**Step 3：实现宽度规则**

- Compact：window width <600dp，底部 NavigationBar。
- Medium：600–839dp，左侧 NavigationRail。
- Expanded：>=840dp，左侧 NavigationRail + 右侧最大 960dp 内容；表单内容最大 720dp。
- 折叠/分屏时实时切换壳，不重置 back stack、tab、表单或滚动状态。
- 一级图标与标签同时显示；当前目标同时使用 indicator、字体权重和语义 selected。

**Step 4：迁移内容并验证**

~~~bash
./gradlew :app:testGkdDebugUnitTest --tests '*HomeInformationArchitectureTest'
./gradlew :app:compileGkdDebugKotlin :app:compilePlayDebugKotlin
git diff --check
git add gradle/libs.versions.toml app/build.gradle.kts app/src/main/kotlin/li/songe/gkd/sdp/ui/home app/src/main/kotlin/li/songe/gkd/sdp/ui/overview app/src/main/kotlin/li/songe/gkd/sdp/ui/selfcontrol app/src/main/kotlin/li/songe/gkd/sdp/ui/rules app/src/main/kotlin/li/songe/gkd/sdp/navigation/DeepLinkParser.kt app/src/test/kotlin/li/songe/gkd/sdp/ui/home
git commit -m "feat: introduce adaptive product navigation"
~~~

### Task 20：用运行能力中心替换权限弹窗堆叠

**Files:**

- Create: app/src/main/kotlin/li/songe/gkd/sdp/capability/Capability.kt
- Create: app/src/main/kotlin/li/songe/gkd/sdp/capability/CapabilityGraph.kt
- Create: app/src/main/kotlin/li/songe/gkd/sdp/capability/CapabilityResolver.kt
- Create: app/src/main/kotlin/li/songe/gkd/sdp/ui/capability/CapabilityCenterRoute.kt
- Create: app/src/main/kotlin/li/songe/gkd/sdp/ui/capability/CapabilityCenterScreen.kt
- Create: app/src/main/kotlin/li/songe/gkd/sdp/ui/capability/CapabilityCenterPresenter.kt
- Create: app/src/test/kotlin/li/songe/gkd/sdp/capability/CapabilityResolverTest.kt
- Create: app/src/androidTest/kotlin/li/songe/gkd/sdp/ui/capability/CapabilityCenterTest.kt
- Modify: app/src/main/kotlin/li/songe/gkd/sdp/permission/AuthDialog.kt
- Modify: app/src/main/kotlin/li/songe/gkd/sdp/ui/AuthA11yPage.kt
- Modify: app/src/main/kotlin/li/songe/gkd/sdp/ui/overview/OverviewScreen.kt
- Modify: app/src/main/kotlin/li/songe/gkd/sdp/ui/settings/SettingsScreen.kt
- Modify: app/src/main/kotlin/li/songe/gkd/sdp/ui/home/ControlPage.kt

**Step 1：先写失败能力图测试**

能力节点固定为：

- runtime_mode：Accessibility 或 Automation/Shizuku，二者至少一个 ready。
- overlay：使用申请、拦截与倒计时需要。
- notification：前台服务与守护提醒需要。
- battery_exemption：长期运行推荐项，状态为 limited 时不阻断普通设置保存。
- shizuku：Automation 模式需要；Accessibility 模式不需要。
- a11y_guard：只在 gkd/A11y 场景运行，但可在无障碍服务关闭时先开启。
- app_list_access：应用选择与应用拦截需要。

每个节点状态固定为 unavailable、actionRequired、ready、active、limited；每个状态只提供一个 primaryAction。

固定不变量：

- 首页无障碍开关只允许“开启/修复”；关闭只导航系统设置。
- 数字自律锁定生效时禁止关闭无障碍守护。
- 用户选择 Accessibility 模式时不强迫授权 Shizuku。
- 用户选择 Automation 模式时不把 Accessibility 状态误报为阻断项。
- 系统授权返回后自动重新解析能力图，不连续弹出下一个权限 Dialog。

**Step 2：实现单页流程**

CapabilityCenterScreen 固定布局：

1. 顶部总状态与当前模式。
2. 唯一“下一步”卡片，显示动作、用途和返回后的预期状态。
3. 已就绪能力列表。
4. 受限但非阻断能力列表。
5. “为什么需要这些权限”可展开说明。

AuthDialog 只保留系统动作的最末确认，不再负责串联多个权限。首次启动、首页状态卡和设置入口全部导航 CapabilityCenter。

**Step 3：验证并提交**

~~~bash
./gradlew :app:testGkdDebugUnitTest --tests '*CapabilityResolverTest'
./gradlew :app:testGkdDebugUnitTest --tests '*HomeA11y*' --tests '*AccessibilityGuard*' --tests '*SelfControlModeParity*'
./gradlew :app:compileGkdDebugKotlin :app:compilePlayDebugKotlin
git diff --check
git add app/src/main/kotlin/li/songe/gkd/sdp/capability app/src/main/kotlin/li/songe/gkd/sdp/ui/capability app/src/test/kotlin/li/songe/gkd/sdp/capability app/src/androidTest/kotlin/li/songe/gkd/sdp/ui/capability app/src/main/kotlin/li/songe/gkd/sdp/permission/AuthDialog.kt app/src/main/kotlin/li/songe/gkd/sdp/ui/AuthA11yPage.kt app/src/main/kotlin/li/songe/gkd/sdp/ui/overview app/src/main/kotlin/li/songe/gkd/sdp/ui/settings app/src/main/kotlin/li/songe/gkd/sdp/ui/home/ControlPage.kt
git commit -m "feat: centralize runtime capability setup"
~~~

### Task 21：重构设置、隐私与数据控制

**Files:**

- Create: app/src/main/kotlin/li/songe/gkd/sdp/settings/SettingsIndex.kt
- Create: app/src/main/kotlin/li/songe/gkd/sdp/settings/SettingsSearchPolicy.kt
- Create: app/src/main/kotlin/li/songe/gkd/sdp/privacy/DataCategory.kt
- Create: app/src/main/kotlin/li/songe/gkd/sdp/privacy/DataInventoryRepository.kt
- Create: app/src/main/kotlin/li/songe/gkd/sdp/privacy/DataDeletionCoordinator.kt
- Create: app/src/main/kotlin/li/songe/gkd/sdp/ui/settings/SettingsRoute.kt
- Create: app/src/main/kotlin/li/songe/gkd/sdp/ui/settings/SettingsScreen.kt
- Create: app/src/main/kotlin/li/songe/gkd/sdp/ui/privacy/PrivacyDataRoute.kt
- Create: app/src/main/kotlin/li/songe/gkd/sdp/ui/privacy/PrivacyDataScreen.kt
- Create: app/src/main/kotlin/li/songe/gkd/sdp/ui/privacy/PrivacyDataPresenter.kt
- Create: app/src/test/kotlin/li/songe/gkd/sdp/settings/SettingsSearchPolicyTest.kt
- Create: app/src/test/kotlin/li/songe/gkd/sdp/settings/SettingsFormPolicyTest.kt
- Create: app/src/test/kotlin/li/songe/gkd/sdp/privacy/DataDeletionCoordinatorTest.kt
- Create: app/src/androidTest/kotlin/li/songe/gkd/sdp/ui/privacy/PrivacyDataScreenTest.kt
- Modify: app/src/main/kotlin/li/songe/gkd/sdp/ui/settings/*
- Modify: app/src/main/kotlin/li/songe/gkd/sdp/data/UsageGuardRecord.kt
- Modify: app/src/main/kotlin/li/songe/gkd/sdp/data/FocusSession.kt
- Modify: app/src/main/kotlin/li/songe/gkd/sdp/data/ActionLog.kt
- Modify: app/src/main/kotlin/li/songe/gkd/sdp/data/ActivityLog.kt
- Modify: app/src/main/kotlin/li/songe/gkd/sdp/data/A11yEventLog.kt
- Modify: app/src/main/kotlin/li/songe/gkd/sdp/data/AppVisitLog.kt
- Modify: app/src/main/kotlin/li/songe/gkd/sdp/data/AppInstallLog.kt
- Modify: app/src/main/kotlin/li/songe/gkd/sdp/data/Snapshot.kt
- Modify: app/src/main/kotlin/li/songe/gkd/sdp/data/SelfControlAttempt.kt
- Modify: app/src/main/kotlin/li/songe/gkd/sdp/data/UsageGuardAppProfile.kt
- Modify: app/src/main/kotlin/li/songe/gkd/sdp/data/UsageGuardTag.kt
- Modify: app/src/main/kotlin/li/songe/gkd/sdp/data/FocusRule.kt
- Modify: app/src/main/kotlin/li/songe/gkd/sdp/data/AppGroup.kt
- Modify: app/src/main/kotlin/li/songe/gkd/sdp/data/BlockTimeRule.kt
- Modify: app/src/main/kotlin/li/songe/gkd/sdp/data/UrlBlockRule.kt
- Modify: app/src/main/kotlin/li/songe/gkd/sdp/data/UrlRuleGroup.kt
- Modify: app/src/main/kotlin/li/songe/gkd/sdp/data/UrlTimeRule.kt
- Modify: PRIVACY.md

**Step 1：先写设置索引失败测试**

设置分组与顺序固定为：

1. 运行能力与权限。
2. 数字自律。
3. 规则、订阅与触发。
4. 显示、主题与无障碍。
5. 隐私与数据。
6. 诊断与开发者工具。
7. 关于与更新。

搜索规则固定：

- 同时匹配中文标题、英文标题、关键词和语义别名。
- 结果显示所属分组与当前状态摘要。
- 点击结果直接滚动并高亮目标 1.5 秒。
- 搜索为空显示最近访问的 5 项；无结果显示清除搜索动作。
- locked 项仍可被搜索，但动作区显示锁定到期时间。
- 通知文案保存时 customNotifTitle 只与 titleValue 比较，customNotifText 只与 textValue 比较；任一字段变化都保存两个当前值，完全未变化不写 Store。

**Step 2：先写数据删除失败测试**

DataCategory 固定为：

- 使用申请历史。
- 专注会话历史。
- 拦截与触发记录。
- 应用安装监控历史。
- 快照与截图。
- 事件/活动日志。
- 诊断与崩溃摘要。
- 订阅与规则配置。
- 数字自律配置。
- 全部应用数据。

固定行为：

- 每类显示记录数量、磁盘占用和最早/最新时间；不在摘要显示理由、URL、节点文本或联系人。
- 历史删除不改变 enabled、locked、每日关闭额度、自动重开和规则配置。
- 存在活动使用申请、专注会话或锁定保护时，对应配置删除和“全部应用数据”按钮禁用，并显示阻断状态；历史删除在活动会话结束后执行。
- 单类删除需确认对象与数量；“全部应用数据”需输入“删除全部数据”。
- 删除使用记录后重算申请间隔与复盘；删除触发记录后重算首页统计；删除快照同时删除数据库行和文件。
- 文件删除失败时 Room 不提交；Room 失败时保留文件并返回可重试状态。
- 删除不关闭无障碍、不更改当前 runtime owner、不重置锁定。

**Step 3：实现 PrivacyDataScreen**

固定布局：

1. 数据仅存本机说明。
2. 加密备份：导出、导入、上次结果。
3. 数据清单：每类摘要、查看、删除。
4. 明文订阅来源：逐项撤销。
5. 支持包：白名单清单和生成。
6. 危险区：重置配置、删除全部。

数据保留固定为：诊断事件 7 天/2 MiB 自动轮转；业务历史默认不自动删除；用户在此页手动删除。PRIVACY.md 与页面使用同一 DataCategory 文案来源生成核对表。

**Step 4：验证并提交**

~~~bash
./gradlew :app:testGkdDebugUnitTest --tests '*SettingsSearchPolicyTest' --tests '*SettingsFormPolicyTest' --tests '*DataDeletionCoordinatorTest'
./gradlew :app:testGkdDebugUnitTest
./gradlew :app:lintGkdDebug :app:lintPlayDebug
git diff --check
git add app/src/main/kotlin/li/songe/gkd/sdp/settings app/src/main/kotlin/li/songe/gkd/sdp/privacy app/src/main/kotlin/li/songe/gkd/sdp/ui/settings app/src/main/kotlin/li/songe/gkd/sdp/ui/privacy app/src/main/kotlin/li/songe/gkd/sdp/data/UsageGuardRecord.kt app/src/main/kotlin/li/songe/gkd/sdp/data/FocusSession.kt app/src/main/kotlin/li/songe/gkd/sdp/data/ActionLog.kt app/src/main/kotlin/li/songe/gkd/sdp/data/ActivityLog.kt app/src/main/kotlin/li/songe/gkd/sdp/data/A11yEventLog.kt app/src/main/kotlin/li/songe/gkd/sdp/data/AppVisitLog.kt app/src/main/kotlin/li/songe/gkd/sdp/data/AppInstallLog.kt app/src/main/kotlin/li/songe/gkd/sdp/data/Snapshot.kt app/src/main/kotlin/li/songe/gkd/sdp/data/SelfControlAttempt.kt app/src/main/kotlin/li/songe/gkd/sdp/data/UsageGuardAppProfile.kt app/src/main/kotlin/li/songe/gkd/sdp/data/UsageGuardTag.kt app/src/main/kotlin/li/songe/gkd/sdp/data/FocusRule.kt app/src/main/kotlin/li/songe/gkd/sdp/data/AppGroup.kt app/src/main/kotlin/li/songe/gkd/sdp/data/BlockTimeRule.kt app/src/main/kotlin/li/songe/gkd/sdp/data/UrlBlockRule.kt app/src/main/kotlin/li/songe/gkd/sdp/data/UrlRuleGroup.kt app/src/main/kotlin/li/songe/gkd/sdp/data/UrlTimeRule.kt app/src/test/kotlin/li/songe/gkd/sdp/settings app/src/test/kotlin/li/songe/gkd/sdp/privacy app/src/androidTest/kotlin/li/songe/gkd/sdp/ui/privacy PRIVACY.md
git commit -m "feat: add searchable settings and data controls"
~~~

### Task 22：重构使用申请与拦截 Overlay 的表单体验

**Files:**

- Create: app/src/main/kotlin/li/songe/gkd/sdp/usage/UsageRequestUiState.kt
- Create: app/src/main/kotlin/li/songe/gkd/sdp/usage/UsageRequestPresenter.kt
- Create: app/src/main/kotlin/li/songe/gkd/sdp/usage/UsageRequestRhythmPolicy.kt
- Create: app/src/main/kotlin/li/songe/gkd/sdp/usage/UsageDurationPresentation.kt
- Create: app/src/main/kotlin/li/songe/gkd/sdp/usage/UsageRequestValidationPolicy.kt
- Create: app/src/test/kotlin/li/songe/gkd/sdp/usage/UsageRequestPresenterTest.kt
- Create: app/src/test/kotlin/li/songe/gkd/sdp/usage/UsageRequestRhythmPolicyTest.kt
- Create: app/src/test/kotlin/li/songe/gkd/sdp/usage/UsageDurationPresentationTest.kt
- Create: app/src/androidTest/kotlin/li/songe/gkd/sdp/ui/usage/UsageRequestImeTest.kt
- Modify: app/src/main/kotlin/li/songe/gkd/sdp/service/usageguardrequest/*
- Modify: app/src/main/kotlin/li/songe/gkd/sdp/service/AppBlockerOverlayService.kt
- Modify: app/src/main/kotlin/li/songe/gkd/sdp/service/InterceptOverlayService.kt
- Modify: app/src/main/kotlin/li/songe/gkd/sdp/ui/component/SelfControlElapsedCard.kt
- Modify: app/src/main/kotlin/li/songe/gkd/sdp/ui/component/SelfControlIntervalChart.kt
- Modify: app/src/main/kotlin/li/songe/gkd/sdp/ui/component/SelfControlIntervalInsightCard.kt
- Modify: app/src/main/kotlin/li/songe/gkd/sdp/ui/component/UsageRequestRhythmPresentation.kt
- Modify: app/src/main/kotlin/li/songe/gkd/sdp/data/UsageGuardRecord.kt
- Modify: app/src/main/kotlin/li/songe/gkd/sdp/data/UsageGuardRecordRepository.kt
- Modify: app/src/main/kotlin/li/songe/gkd/sdp/data/UsageGuardTag.kt
- Modify: existing UsageGuard and Overlay tests

**Step 1：先锁定计算与查询测试**

固定定义：

- 当前间隔 = max(0, nowEpochMs - 最近一次已结束使用的 endedAt)。
- 历史间隔 = max(0, 本次 requestedAt - 上一次已结束使用的 endedAt)。
- 不把上次申请到上次结束之间的使用时间计入间隔。
- 当前间用比 = 当前间隔毫秒 ÷ 当前选择申请时长毫秒。
- 历史间用比 = 历史间隔毫秒 ÷ 当次申请时长毫秒。
- 窗口平均间用比 = 窗口内有效历史记录的逐条算术平均；duration<=0 或缺失 endedAt 的记录不进入平均值，并计入“未纳入 N 条”。
- 所有计算使用 Long 毫秒与 Double，不先转换分钟，不发生整数截断。
- 比值 <0.01 显示“<0.01”，0.01–9.99 保留 2 位，10.0–99.9 保留 1 位，>=100 显示整数。
- 时长 <60 秒显示秒；<60 分钟显示分秒；<24 小时显示小时分钟；>=24 小时显示天小时。

固定数据查询：

- 24 小时窗口使用 [now-24h, now)，按 1 小时桶。
- 7 天窗口使用 [now-7d, now)，按 6 小时桶。
- 30 天窗口使用 [now-30d, now)，按本地日历日桶。
- DAO 返回窗口内全部成功 UsageGuardRecord，不使用 LIMIT 5，不从当前分页列表二次裁剪。
- 同一 requestedAt 使用 id 稳定排序；时钟回拨记录保持但间隔截为 0。
- 当前申请以虚线空心点叠加，不写数据库；取消后立即移除。

**Step 2：先锁定标签与表单状态测试**

- 内置“其他”标签永远排最后。
- 新标签插入“其他”之前；同 order 使用 createdAt、id 升序。
- 标签名称 trim 后 1–20 字，同一规范化名称不可重复。
- 表单初始焦点不自动弹键盘；点击理由后键盘打开且输入框与错误文本完整可见。
- 用户编辑标签、理由或时长后返回，显示未保存确认；完全未编辑直接关闭。
- 提交按钮在验证、写 UsageGuardRecord 和 Overlay 切换期间禁用；重复点击只产生一条记录。
- 取消申请不写记录、不重置距离上次结束使用。

**Step 3：固定申请 Overlay 视觉顺序**

从上到下固定为：

1. 应用图标、名称与关闭按钮。
2. “距离上次结束使用”卡：当前间隔、上次结束时间、24 小时/7 天/30 天 selector、样本数、间隔与间用比图表。
3. 标签：横向可换行 chips，“新建标签”入口，“其他”始终最后。
4. 使用理由：多行输入、字符计数、清晰标签和 inline error。
5. 申请时长与间用比反馈：时长选择和“本次 X / 窗口平均 Y / 有效样本 N”放在同一卡片。
6. 固定底部操作栏：“取消 / 开始使用 N 分钟”。

默认窗口为近 24 小时；用户本次选择只在当前 Overlay 生命周期保存，不改变全局默认。

**Step 4：固定 IME 与窗口行为**

- UsageGuardRequestOverlayService 使用 SOFT_INPUT_ADJUST_RESIZE，不使用 ADJUST_NOTHING。
- Compose 根节点使用 imePadding、navigationBarsPadding 和可滚动内容。
- 每个输入字段使用 BringIntoViewRequester，在焦点与 IME 高度变化后 bringIntoView。
- 底部操作栏始终位于 IME 上方；键盘打开后图表可滚出屏幕，当前输入字段、错误与主按钮可到达。
- IME action Next 移动到下一个字段，Done 隐藏键盘，不自动提交。
- 窗口关闭时清理焦点、lifecycle owner 与暂存表单。

**Step 5：同步拦截 Overlay**

- App、URL、选择器与 Usage Guard 拦截页顶部显示 InterceptionSourceCard：来源类型、订阅名、规则组、规则名、稳定规则 ID、发生时间。
- 来源缺失时显示“来源记录缺失”和事件 ID，不隐藏卡片。
- 拦截页使用同一节奏卡、三个窗口、样本数和文本数据表。
- 规则触发历史只在 addView 成功后写入；挂载失败不写“已展示”。
- 倒计时悬浮条继续 FLAG_SECURE；截图模式只切换悬浮条自身可见性，租约结束后恢复，不设置第三方窗口 flag。

**Step 6：验证并提交**

~~~bash
./gradlew :app:testGkdDebugUnitTest --tests '*UsageRequest*' --tests '*UsageGuard*' --tests '*SelfControlInterval*' --tests '*InterceptionSource*' --tests '*Overlay*'
./gradlew :app:testGkdDebugUnitTest
./gradlew :app:lintGkdDebug :app:lintPlayDebug
git diff --check
git add app/src/main/kotlin/li/songe/gkd/sdp/usage app/src/test/kotlin/li/songe/gkd/sdp/usage app/src/androidTest/kotlin/li/songe/gkd/sdp/ui/usage app/src/main/kotlin/li/songe/gkd/sdp/service app/src/main/kotlin/li/songe/gkd/sdp/ui/component app/src/main/kotlin/li/songe/gkd/sdp/data app/src/test
git commit -m "fix: refine usage request and interception UX"
~~~

### Task 23：重构数字自律复盘与全部节奏图表

**Files:**

- Refactor: app/src/main/kotlin/li/songe/gkd/sdp/util/DigitalSelfDisciplineReviewPolicy.kt
- Modify: app/src/main/kotlin/li/songe/gkd/sdp/data/SelfControlIntervalRepository.kt
- Modify: app/src/main/kotlin/li/songe/gkd/sdp/ui/usagereview/*
- Modify: app/src/main/kotlin/li/songe/gkd/sdp/ui/component/DigitalSelfDisciplineReviewPresentation.kt
- Modify: app/src/main/kotlin/li/songe/gkd/sdp/ui/component/DigitalSelfDisciplineTrendChart.kt
- Modify: app/src/main/kotlin/li/songe/gkd/sdp/ui/component/SelfControlIntervalChart.kt
- Modify: app/src/main/kotlin/li/songe/gkd/sdp/widget/UsageGuardReviewWidget.kt
- Create: app/src/test/kotlin/li/songe/gkd/sdp/ui/review/ReviewDashboardPresentationTest.kt
- Create: app/src/androidTest/kotlin/li/songe/gkd/sdp/ui/review/ReviewDashboardTest.kt

**Step 1：先写失败聚合测试**

先把 Vico 从 2.0.0-alpha.28 固定升级到维护中的稳定版 2.5.2，按官方迁移 API 更新三个图表组件；依赖安全报告中不再出现 Vico prerelease。

三个窗口统一使用 Task 22 的半开区间和桶。固定摘要指标：

- 申请次数。
- 申请总时长。
- 实际使用时长。
- 实际/申请时长百分比。
- 中位间隔。
- 平均间用比。
- 拦截展示次数与记录缺失次数。

固定规则：

- 申请总时长只统计成功 UsageGuardRecord。
- 实际使用时长使用 min(endedAt, expiresAt)-startedAt 并截到 [0, approvedDuration]。
- 活动记录以 min(now, expiresAt) 计算，并标注“进行中”。
- 中位数使用排序后的标准中位数；平均间用比使用 Task 22 定义。
- 空窗口返回 typed empty state，不显示 0% 改善或趋势箭头。
- 当前窗口与前一个等长窗口比较；差值只描述“增加/减少”，不评价好坏。

**Step 2：固定复盘页面结构**

1. 顶部标题、24 小时/7 天/30 天 selector、数据截止时间。
2. 两列自适应摘要卡；紧凑宽度单列。
3. “申请与实际使用”：同桶成对柱状图，不使用双 Y 轴。
4. “距离上次结束使用”：中位数折线/点图。
5. “间用比”：平均值折线/点图与样本数。
6. “应用分布”：按实际使用时长降序的水平条形图，最多显示前 8 项，其余合并“其他”。
7. “数据明细”：可展开数据表与口径说明。

图表固定：

- 少于 4 个有效点显示点图和列表，不连趋势线。
- 4–48 个点显示完整点；超过 48 个点只显示聚合桶。
- 轴标签最多 6 个，使用本地日期/时间，单位写在轴标题。
- 触摸点显示时间、数值、样本数；TalkBack 使用同一字符串。
- 不使用 3D、渐变面积、双轴、饼图、装饰动画或红绿二元评价。

**Step 3：同步 Widget**

Widget 只显示窗口、申请次数、实际/申请时长和中位间隔；点击进入完整复盘。Widget 不显示理由、标签和具体应用名称。

**Step 4：验证并提交**

~~~bash
./gradlew :app:testGkdDebugUnitTest --tests '*DigitalSelfDisciplineReview*' --tests '*ReviewDashboard*' --tests '*SelfControlInterval*'
./gradlew :app:testGkdDebugUnitTest
git diff --check
git add gradle/libs.versions.toml app/src/main/kotlin/li/songe/gkd/sdp/util/DigitalSelfDisciplineReviewPolicy.kt app/src/main/kotlin/li/songe/gkd/sdp/data/SelfControlIntervalRepository.kt app/src/main/kotlin/li/songe/gkd/sdp/ui/usagereview app/src/main/kotlin/li/songe/gkd/sdp/ui/component app/src/main/kotlin/li/songe/gkd/sdp/widget/UsageGuardReviewWidget.kt app/src/test/kotlin/li/songe/gkd/sdp/ui/review app/src/androidTest/kotlin/li/songe/gkd/sdp/ui/review
git commit -m "feat: rebuild self-control review dashboard"
~~~

### Task 24：统一其余页面的状态、表单和操作细节

**Files:**

- Modify: app/src/main/kotlin/li/songe/gkd/sdp/ui/appblocker/*
- Modify: app/src/main/kotlin/li/songe/gkd/sdp/ui/focuslock/*
- Modify: app/src/main/kotlin/li/songe/gkd/sdp/ui/focusmode/*
- Modify: app/src/main/kotlin/li/songe/gkd/sdp/ui/urlblocker/*
- Modify: app/src/main/kotlin/li/songe/gkd/sdp/ui/actionlog/*
- Modify: app/src/main/kotlin/li/songe/gkd/sdp/ui/rules/*
- Modify: app/src/main/kotlin/li/songe/gkd/sdp/ui/SnapshotPage.kt
- Modify: app/src/main/kotlin/li/songe/gkd/sdp/ui/AppInstallMonitorPage.kt
- Modify: app/src/main/kotlin/li/songe/gkd/sdp/ui/A11yEventLogPage.kt
- Modify: app/src/main/kotlin/li/songe/gkd/sdp/ui/ActivityLogPage.kt
- Create: app/src/test/kotlin/li/songe/gkd/sdp/ui/ScreenStateConsistencyTest.kt

**Step 1：固定列表页面规则**

- 初次加载使用 ContentState.Loading；无记录使用带主动作的 Empty；失败使用 Error + 重试。
- 搜索框保留查询，清除按钮触控区 48dp；结果数与筛选条件可访问。
- 规则、订阅和日志使用稳定 key；删除后焦点移动到相邻项。
- 批量操作进入明确选择模式，顶部显示选择数，返回先退出选择模式。
- 列表行主点击打开详情；Switch 只切换启用状态；两个动作不重叠。
- 长标题最多两行，副标题最多三行；继续溢出时详情页完整显示。

**Step 2：固定编辑表单规则**

- 标题、描述、匹配条件、时间范围、应用选择和锁定设置分 section。
- 必填字段离开焦点后验证，提交时聚焦第一个错误。
- 保存期间禁用重复提交，成功后 Snackbar 显示“已保存”并返回；失败保留全部输入。
- 有未保存修改时返回显示“放弃修改 / 继续编辑”。
- 删除显示对象名称与影响；锁定中的对象不提供绕过删除或禁用入口。
- 时间范围显示时区和跨日状态；00:00、23:59、跨午夜、夏令时和时钟回拨由 policy 测试覆盖。

**Step 3：固定日志与详情规则**

- ActionLog 默认最新在前，来源快照与 outcome 显示在同一详情卡。
- 来源缺失、未展示、挂载失败和实际展示使用不同文字与 icon。
- 快照/截图删除同步文件与 Room；预览加载失败提供重试和删除。
- 日志导出只走 SupportBundleBuilder，不提供原始数据库分享。

**Step 4：验证并提交**

~~~bash
./gradlew :app:testGkdDebugUnitTest --tests '*ScreenStateConsistencyTest' --tests '*EditorPolicyTest' --tests '*ActionLog*'
./gradlew :app:testGkdDebugUnitTest
./gradlew :app:lintGkdDebug :app:lintPlayDebug
git diff --check
git add app/src/main/kotlin/li/songe/gkd/sdp/ui app/src/test/kotlin/li/songe/gkd/sdp/ui
git commit -m "fix: standardize screen states and forms"
~~~

### Task 25：完成 PR 4 验证、审查、合并

**Step 1：更新产品与测试文档**

- README.md 更新四个一级入口、运行能力中心、加密备份和隐私数据入口。
- README_DEV.md 更新 UiState/UiAction/Presenter、AdaptiveHomeScaffold、Overlay IME 和图表聚合口径。
- docs/testing/product-experience-matrix.md 固定记录 compact/medium/expanded、中文/英文、light/dark、字体 1.0/1.3/2.0、空/少/密集数据、IME、进程重建和两 flavor。
- CHANGELOG.md [Unreleased] 记录信息架构、申请表单、复盘、无障碍、隐私数据与远程调试变化。

**Step 2：运行、推送、合并**

~~~bash
python3 -m unittest discover -s scripts/tests -p 'test_*.py' -v
./gradlew :quality-lint:test :selector:jvmTest :app:testGkdDebugUnitTest :app:lintGkdDebug :app:lintPlayDebug
./gradlew :app:assembleGkdDebug :app:assemblePlayDebug :app:assembleGkdRelease :app:assemblePlayRelease
git diff --check
git add README.md README_DEV.md docs/testing/product-experience-matrix.md CHANGELOG.md
git commit -m "docs: document refined product experience"
git push -u origin codex/quality-product-experience
PR_URL="$(gh pr create --base main --head codex/quality-product-experience --title "feat: refine product information architecture and core UX" --body '目的：重构四级入口、能力中心、设置与隐私数据控制，并统一申请、拦截、复盘和表单体验。影响：核心产品流程。未改：成功记录、锁定、自动重开和 FLAG_SECURE 不变量。验证：JVM、Lint、四变体构建。真机/OEM：未执行。')"
gh pr edit "$PR_URL" --body "目的：重构四级入口、能力中心、设置与隐私数据控制，并统一申请、拦截、复盘和表单体验。影响：核心产品流程。未改：成功记录、锁定、自动重开和 FLAG_SECURE 不变量。验证：JVM、Lint、四变体构建。Actions：$PR_URL/checks。真机/OEM：未执行。"
gh pr checks --watch --fail-fast
gh pr view --json state,mergeStateStatus,statusCheckRollup,reviewDecision,url
gh pr merge --squash --delete-branch
git -C ../.. switch main
git -C ../.. pull --ff-only origin main
cd ../..
~~~

预期：PR state=MERGED，main 与 origin/main 指向同一提交。

## 7. PR 5：自动化测试、性能与 CI 基础设施

### Task 26：替换占位/源码契约测试并建立真实 UI 与迁移测试

**Files:**

- Delete: app/src/test/kotlin/li/songe/gkd/sdp/ExampleUnitTest.kt
- Delete: app/src/androidTest/kotlin/li/songe/gkd/sdp/ExampleInstrumentedTest.kt
- Replace: app/src/test/kotlin/li/songe/gkd/sdp/a11y/UsageGuardCountdownOverlayLeaseContractTest.kt
- Replace: app/src/test/kotlin/li/songe/gkd/sdp/service/UsageGuardCountdownOverlayScreenshotModeContractTest.kt
- Replace: app/src/test/kotlin/li/songe/gkd/sdp/service/UsageGuardRequestLayoutContractTest.kt
- Replace: app/src/test/kotlin/li/songe/gkd/sdp/ui/UsageGuardReviewStateContractTest.kt
- Create: app/src/androidTest/kotlin/li/songe/gkd/sdp/db/Migration32To33Test.kt
- Create: app/src/androidTest/kotlin/li/songe/gkd/sdp/ui/AppNavigationTest.kt
- Create: app/src/androidTest/kotlin/li/songe/gkd/sdp/ui/CapabilityFlowTest.kt
- Create: app/src/androidTest/kotlin/li/songe/gkd/sdp/ui/SettingsSearchTest.kt
- Create: app/src/androidTest/kotlin/li/songe/gkd/sdp/ui/EncryptedBackupFlowTest.kt
- Create: app/src/androidTest/kotlin/li/songe/gkd/sdp/ui/DataDeletionFlowTest.kt
- Create: app/src/androidTest/kotlin/li/songe/gkd/sdp/ui/UsageRequestFlowTest.kt
- Create: app/src/androidTest/kotlin/li/songe/gkd/sdp/ui/ReviewDashboardFlowTest.kt
- Create: scripts/verify-test-quality-policy.py
- Create: scripts/tests/test_test_quality_policy.py
- Modify: gradle/libs.versions.toml
- Modify: app/build.gradle.kts

**Step 1：创建分支**

~~~bash
git fetch origin --prune
git worktree add .worktrees/quality-automation-performance -b codex/quality-automation-performance origin/main
cd .worktrees/quality-automation-performance
~~~

**Step 2：建立测试依赖**

固定加入：

- androidx.compose.ui:ui-test-manifest:1.11.2，仅 debugImplementation。
- androidx.room:room-testing:2.8.4，仅 androidTestImplementation。
- androidx.test:rules:1.7.0，仅 androidTestImplementation。
- androidx.test:runner:1.7.0，仅 androidTestImplementation。

保留现有 Compose JUnit4、AndroidX JUnit 和 Espresso。

**Step 3：替换源码字符串测试**

verify-test-quality-policy.py 固定拒绝：

- app/src/test 读取 app/src/main 源文件内容。
- 通过字符串包含、正则或反射检查实现名称。
- Thread.sleep、真实墙钟等待和网络请求。
- 空 test、无断言 test 和 Example* 占位类。

四个现有源码契约测试改为调用公开 policy/controller/presenter：

- Overlay lease 测试真实 acquire/release 状态机。
- screenshot mode 测试真实 capture controller 与 window flag policy。
- request layout 测试 UsageRequestUiState section 顺序与 IME policy。
- review state 测试真实 presenter 输出。

**Step 4：建立 Room 迁移测试**

Migration32To33Test 使用 app/schemas 32.json 创建 v32 数据库，插入合成 ActionLog 与 UsageGuardRecord，执行 MIGRATION_32_33，固定断言：

- 原有行、主键、索引与外键保持。
- action_log.outcome=1、matched_at=0，三个 snapshot 名称为 null。
- usage_guard_record.last_usage_ended_at 与 request_gap_ms 为 null。
- Room validateMigration 成功。
- 打开当前 v33 schema 不触发 destructive migration。

**Step 5：建立关键 UI 流程**

所有测试使用 FakeAppDependencies、内存 Room、临时 Store、合成应用与记录，不开启真实无障碍/Shizuku/Overlay。固定覆盖：

- 四个一级目标导航与进程重建恢复。
- 能力中心每个状态与唯一主动作。
- 设置搜索与目标高亮。
- 加密备份错误密码、预览、确认和成功。
- 数据删除活动会话阻断、历史删除和配置保留。
- 使用申请标签/理由/时长/间用比、IME、重复提交和取消。
- 复盘空、少量、密集数据与数据表。

**Step 6：验证并提交**

~~~bash
python3 scripts/verify-test-quality-policy.py
python3 -m unittest scripts.tests.test_test_quality_policy -v
./gradlew :app:testGkdDebugUnitTest
./gradlew :app:compileGkdDebugAndroidTestKotlin
git diff --check
git add app/src/test app/src/androidTest gradle/libs.versions.toml app/build.gradle.kts scripts/verify-test-quality-policy.py scripts/tests/test_test_quality_policy.py
git commit -m "test: replace contracts with behavioral coverage"
~~~

### Task 27：建立 Compose 截图回归

**Files:**

- Modify: build.gradle.kts
- Modify: gradle/libs.versions.toml
- Modify: app/build.gradle.kts
- Create: app/src/screenshotTest/kotlin/li/songe/gkd/sdp/PreviewFixtures.kt
- Create: app/src/screenshotTest/kotlin/li/songe/gkd/sdp/CoreScreensScreenshotTest.kt
- Create: app/src/screenshotTest/kotlin/li/songe/gkd/sdp/OverlayScreensScreenshotTest.kt
- Create: app/src/screenshotTest/kotlin/li/songe/gkd/sdp/ChartScreensScreenshotTest.kt
- Create: app/src/screenshotTest/reference/**
- Create: docs/testing/screenshot-testing.md

**Step 1：配置官方插件**

- 固定 com.android.compose.screenshot:0.0.1-alpha15。
- imageDifferenceThreshold 固定 0.001。
- 测试 JVM 最大堆固定 4 GiB。
- reference 图片只在 Ubuntu/JDK21 环境生成并进入版本控制。
- PreviewFixtures 使用固定时区 Asia/Shanghai、固定 Locale、FakeSdpClock=2026-08-09T12:00:00+08:00 和合成数据。

**Step 2：建立固定矩阵**

每个目标至少生成以下 reference：

- 概览：ready 与 actionRequired。
- 自律中心：compact light zh、expanded dark en。
- 规则：订阅 tab 与应用 tab。
- 设置：默认与搜索结果。
- 能力中心：Accessibility 路径与 Automation/Shizuku 路径。
- 使用申请：空历史、密集 24 小时、fontScale=2.0 + IME 预留。
- 拦截页：完整来源与来源缺失。
- 复盘：空、2 条、30 天密集。
- 隐私与数据：默认与删除确认。

通用变体固定覆盖 widthDp=360/700/1000、light/dark、zh-rCN/en、fontScale=1.0/2.0。组合总 reference 控制在 48 张，按上述用例一一指定，不做笛卡尔积。

**Step 3：生成与验证**

~~~bash
./gradlew :app:updateGkdDebugScreenshotTest
git add app/src/screenshotTest/reference
./gradlew :app:validateGkdDebugScreenshotTest
git diff --check
git add build.gradle.kts gradle/libs.versions.toml app/build.gradle.kts app/src/screenshotTest docs/testing/screenshot-testing.md
git commit -m "test: add Compose visual regression suite"
~~~

CI 永远只运行 validate；更新 reference 只允许在明确的视觉变更提交中运行 update，PR 描述逐张列出变化。

### Task 28：建立 API 26 与 API 35 托管模拟器门禁

**Files:**

- Modify: app/build.gradle.kts
- Create: app/src/androidTest/kotlin/li/songe/gkd/sdp/TestAppDependencies.kt
- Create: app/src/androidTest/kotlin/li/songe/gkd/sdp/ManagedDeviceSuite.kt
- Create: docs/testing/managed-device-matrix.md

**Step 1：配置设备**

在 testOptions.managedDevices.devices 固定：

- pixel2Api26：Pixel 2、API 26、aosp、x86_64。
- pixel6Api35：Pixel 6、API 35、aosp、x86_64。

设置 maxConcurrentDevices=1、统一英文系统 Locale；应用内测试单独切换 zh/en。测试执行前清空 app data，禁止访问公网。

**Step 2：固定套件**

ManagedDeviceSuite 包含 Task 4、11、17、20、21、22、23、26 的 instrumentation tests，并额外覆盖：

- API 26 的 edge-to-edge、IME resize、SAF 备份与通知兼容。
- API 35 的 predictive back 关闭配置、edge-to-edge、动态窗口宽度与 WebView 安全设置。
- gkdDebug 全套 UI 流程。
- playDebug 启动、四个一级目标、设置和不展示 gkd 专属强权限动作。

不在模拟器中伪造真实 Accessibility/Shizuku/FLAG_SECURE/OEM 结论；这些路径只验证 policy、状态展示和 flavor 边界。

**Step 3：运行并提交**

~~~bash
./gradlew :app:pixel2Api26GkdDebugAndroidTest
./gradlew :app:pixel6Api35GkdDebugAndroidTest
./gradlew :app:pixel6Api35PlayDebugAndroidTest
git diff --check
git add app/build.gradle.kts app/src/androidTest docs/testing/managed-device-matrix.md
git commit -m "test: add managed Android device matrix"
~~~

### Task 29：建立业务层覆盖率门禁

**Files:**

- Modify: build.gradle.kts
- Modify: gradle/libs.versions.toml
- Modify: app/build.gradle.kts
- Create: config/quality/kover-includes.txt
- Create: config/quality/kover-excludes.txt
- Create: docs/testing/coverage-policy.md

**Step 1：配置 Kover 0.9.9**

只对 gkdDebug JVM 测试计入门禁。includes 固定包含：

- li.songe.gkd.sdp.*Policy*
- li.songe.gkd.sdp.*Repository*
- li.songe.gkd.sdp.runtime.*
- li.songe.gkd.sdp.backup.*
- li.songe.gkd.sdp.remote.*
- li.songe.gkd.sdp.capability.*
- li.songe.gkd.sdp.settings.*
- li.songe.gkd.sdp.privacy.*
- li.songe.gkd.sdp.usage.*
- 所有 UiState/Presenter。

excludes 固定包含：

- Compose 函数与 Preview。
- Android Service/Activity/Receiver/Widget 宿主。
- Room/KSP/BuildConfig/R/META 生成类。
- 图标与纯资源映射。
- 测试 fake。

**Step 2：固定阈值**

- LINE covered percent >=80。
- BRANCH covered percent >=70。
- 新增 included 类不得为 0%。
- XML 与 HTML 报告上传 Actions，保留 7 天。
- 禁止通过扩大 excludes、删除测试或标记 generated 修复门禁。

**Step 3：运行并提交**

~~~bash
./gradlew :app:koverVerifyGkdDebug :app:koverXmlReportGkdDebug :app:koverHtmlReportGkdDebug
git diff --check
git add build.gradle.kts gradle/libs.versions.toml app/build.gradle.kts config/quality docs/testing/coverage-policy.md
git commit -m "build: enforce business logic coverage"
~~~

### Task 30：建立 Baseline Profile、启动宏基准与 Compose 稳定性门禁

**Files:**

- Create: baselineprofile/build.gradle.kts
- Create: baselineprofile/src/main/AndroidManifest.xml
- Create: baselineprofile/src/main/kotlin/li/songe/gkd/sdp/baselineprofile/BaselineProfileGenerator.kt
- Create: baselineprofile/src/main/kotlin/li/songe/gkd/sdp/baselineprofile/StartupBenchmark.kt
- Modify: settings.gradle.kts
- Modify: build.gradle.kts
- Modify: gradle/libs.versions.toml
- Modify: app/build.gradle.kts
- Create: app/src/main/kotlin/li/songe/gkd/sdp/performance/AppDrawReporter.kt
- Create: app/src/debug/kotlin/li/songe/gkd/sdp/performance/DebugRuntimeChecks.kt
- Create: app/src/gkdRelease/generated/baselineProfiles/baseline-prof.txt
- Create: config/quality/performance-thresholds.json
- Create: config/quality/compose-stability-baseline.json
- Create: scripts/verify-performance-reports.py
- Create: scripts/tests/test_performance_report_policy.py
- Create: docs/testing/performance-baseline.md

**Step 1：配置稳定依赖与模块**

固定使用：

- androidx.benchmark:benchmark-macro-junit4:1.4.1。
- androidx.profileinstaller:profileinstaller:1.4.1。
- androidx.baselineprofile Gradle plugin 1.4.1。
- Pixel 6 API 35 aosp 生成 profile。
- 目标包固定 li.songe.gkd.sdp。
- baselineProfile 配置 managedDevices=["pixel6Api35"]、useConnectedDevices=false、saveInSrc=true、automaticGenerationDuringBuild=false。

BaselineProfileGenerator 固定走：

1. 冷启动到概览。
2. 打开自律中心与使用申请设置。
3. 打开数字自律复盘。
4. 打开规则的两个 tab。
5. 打开设置、能力中心与隐私数据。

测试只使用合成数据，不把理由、应用列表或设备信息写入 profile/报告。

**Step 2：实现启动完成边界**

AppDrawReporter 在概览首个可交互 Content 状态后调用 reportFullyDrawn；Loading、权限弹窗和后台初始化不提前报告。进程重建与深链分别报告自己的首个可交互页面。

DebugRuntimeChecks 只在 debug 启用 StrictMode：检测主线程磁盘/网络、资源未关闭、Activity 泄漏和明文网络；违规通过类型化诊断与测试 listener 失败，不在 release 启用 penaltyDeath 或额外检测。

**Step 3：固定性能阈值**

performance-thresholds.json 固定：

- coldStartupTimeToInitialDisplayMedianMs <=1200。
- coldStartupTimeToInitialDisplayP95Ms <=2000。
- warmStartupP95Ms <=700。
- frameOverrunP95Ms <=16。
- releaseApkBytes 相对 origin/main 增长 <=8% 且绝对增长 <=2 MiB。
- Baseline Profile 必须存在 assets/dexopt/baseline.prof 与 baseline.profm。
- Compose compiler unstable class count 不高于 compose-stability-baseline.json。
- 核心 UiState、chart point、settings entry 和 capability state 全部标记 stable。

宏基准每个启动模式固定 10 次；丢弃第一次预热只适用于 warm 模式，并在 JSON 记录全部原始值。

**Step 4：生成 profile 与验证**

~~~bash
./gradlew :app:generateGkdReleaseBaselineProfile -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.enabledRules=BaselineProfile
./gradlew :baselineprofile:pixel6Api35GkdNonMinifiedReleaseAndroidTest -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.enabledRules=Macrobenchmark
./gradlew :app:assembleGkdRelease
python3 scripts/verify-performance-reports.py --thresholds config/quality/performance-thresholds.json
python3 -m unittest scripts.tests.test_performance_report_policy -v
git diff --check
git add baselineprofile settings.gradle.kts build.gradle.kts gradle/libs.versions.toml app/build.gradle.kts app/src/main/kotlin/li/songe/gkd/sdp/performance app/src/debug/kotlin/li/songe/gkd/sdp/performance app/src/gkdRelease/generated/baselineProfiles/baseline-prof.txt config/quality scripts/verify-performance-reports.py scripts/tests/test_performance_report_policy.py docs/testing/performance-baseline.md
git commit -m "perf: add startup baseline and regression gates"
~~~

### Task 31：扩展 GitHub Actions 与报告留存

**Files:**

- Modify: .github/workflows/ci.yml
- Modify: .github/workflows/nightly.yml
- Create: scripts/apply-main-ruleset.sh
- Create: scripts/tests/test_ci_quality_policy.py
- Create: docs/maintenance/ci-quality-gates.md

**Step 1：先写失败 workflow 契约**

test_ci_quality_policy.py 固定断言：

- 所有 actions uses 使用完整 commit SHA。
- permissions 默认为 contents: read；只有现有 Release job 保留写权限。
- PR 不读取 release Environment 或 signing secrets。
- CI 存在且名称固定为 quality、coverage、visual-regression、managed-device-api26、managed-device-api35、performance、build、dependency-review。
- build 生成 gkdDebug、playDebug、gkdRelease、playRelease。
- 所有报告 Artifact 保留 7 天且不包含 APK 以外的用户数据。
- 每个 job 有 10–60 分钟固定 timeout 和 concurrency cancel。

**Step 2：固定 job 命令**

quality：

~~~bash
python3 -m unittest discover -s scripts/tests -p 'test_*.py' -v
bash scripts/test-verify-release-metadata.sh
bash scripts/test-generate-update-manifest.sh
bash scripts/generate-security-dependency-report.sh
python3 scripts/verify-sensitive-output-policy.py
python3 scripts/verify-compose-lifecycle-policy.py
python3 scripts/verify-ui-file-boundaries.py
python3 scripts/verify-test-quality-policy.py
./gradlew :quality-lint:test :selector:jvmTest :app:testGkdDebugUnitTest :app:lintGkdDebug :app:lintPlayDebug
python3 scripts/verify-security-dependency-report.py --report build/reports/security-dependencies.txt
~~~

coverage：

~~~bash
./gradlew :app:koverVerifyGkdDebug :app:koverXmlReportGkdDebug :app:koverHtmlReportGkdDebug
~~~

visual-regression：

~~~bash
./gradlew :app:validateGkdDebugScreenshotTest
~~~

managed-device-api26：

~~~bash
./gradlew :app:pixel2Api26GkdDebugAndroidTest
~~~

managed-device-api35：

~~~bash
./gradlew :app:pixel6Api35GkdDebugAndroidTest :app:pixel6Api35PlayDebugAndroidTest
~~~

performance：

~~~bash
./gradlew :app:generateGkdReleaseBaselineProfile -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.enabledRules=BaselineProfile
./gradlew :baselineprofile:pixel6Api35GkdNonMinifiedReleaseAndroidTest -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.enabledRules=Macrobenchmark
./gradlew :app:assembleGkdRelease
python3 scripts/verify-performance-reports.py --thresholds config/quality/performance-thresholds.json
~~~

build：

~~~bash
./gradlew :app:assembleGkdDebug :app:assemblePlayDebug :app:assembleGkdRelease :app:assemblePlayRelease
~~~

**Step 3：配置 nightly**

Nightly 每日执行 quality、coverage、全部 GMD、performance 和四变体构建；只上传 7 天 Artifact，不创建 Release，不访问签名 Environment。

**Step 4：建立 Ruleset 同步脚本**

scripts/apply-main-ruleset.sh：

- 读取仓库现有 main Ruleset。
- 保留所有现有 review、签名、线性历史、CodeQL、dependency-review 和管理员规则。
- 把 quality、coverage、visual-regression、managed-device-api26、managed-device-api35、performance、build 加入 required status checks。
- strict_required_status_checks_policy=true。
- 输出变更前后 required check 名称，不输出 token。
- --dry-run 只生成并验证目标 payload，--check 校验远端与目标一致，--apply 执行 PUT；找不到名为 main-protection 的 Ruleset 时使用固定 JSON 创建同名 Ruleset。

**Step 5：验证并提交**

~~~bash
python3 -m unittest scripts.tests.test_ci_quality_policy -v
bash scripts/apply-main-ruleset.sh --dry-run
git diff --check
git add .github/workflows scripts/apply-main-ruleset.sh scripts/tests/test_ci_quality_policy.py docs/maintenance/ci-quality-gates.md
git commit -m "ci: enforce product quality gates"
~~~

### Task 32：完成 PR 5、启用门禁并合并

**Step 1：运行完整本地/模拟器验证**

~~~bash
python3 -m unittest discover -s scripts/tests -p 'test_*.py' -v
bash scripts/generate-security-dependency-report.sh
./gradlew :quality-lint:test :selector:jvmTest :app:testGkdDebugUnitTest :app:lintGkdDebug :app:lintPlayDebug
python3 scripts/verify-security-dependency-report.py --report build/reports/security-dependencies.txt
./gradlew :app:koverVerifyGkdDebug :app:validateGkdDebugScreenshotTest
./gradlew :app:pixel2Api26GkdDebugAndroidTest
./gradlew :app:pixel6Api35GkdDebugAndroidTest :app:pixel6Api35PlayDebugAndroidTest
./gradlew :app:generateGkdReleaseBaselineProfile -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.enabledRules=BaselineProfile
./gradlew :baselineprofile:pixel6Api35GkdNonMinifiedReleaseAndroidTest -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.enabledRules=Macrobenchmark
python3 scripts/verify-performance-reports.py --thresholds config/quality/performance-thresholds.json
./gradlew :app:assembleGkdDebug :app:assemblePlayDebug :app:assembleGkdRelease :app:assemblePlayRelease
git diff --check
git status --short
~~~

**Step 2：推送、等待全部检查、合并**

~~~bash
git push -u origin codex/quality-automation-performance
PR_URL="$(gh pr create --base main --head codex/quality-automation-performance --title "ci: add automated product quality gates" --body '目的：建立行为/UI/迁移测试、截图回归、API 26/35 GMD、覆盖率、Baseline Profile、性能和四变体 CI 门禁。影响：测试与 GitHub Actions。未改：生产业务规则。验证：全部新增门禁。真机/OEM：未执行。')"
gh pr edit "$PR_URL" --body "目的：建立行为/UI/迁移测试、截图回归、API 26/35 GMD、覆盖率、Baseline Profile、性能和四变体 CI 门禁。影响：测试与 GitHub Actions。未改：生产业务规则。验证：全部新增门禁。Actions：$PR_URL/checks。真机/OEM：未执行。"
gh pr checks --watch --fail-fast
gh pr view --json state,mergeStateStatus,statusCheckRollup,reviewDecision,url
gh pr merge --squash --delete-branch
git -C ../.. switch main
git -C ../.. pull --ff-only origin main
~~~

预期：PR state=MERGED，main 与 origin/main 指向同一提交。

**Step 3：启用并核对 main Ruleset**

~~~bash
cd ../..
bash scripts/apply-main-ruleset.sh --apply
bash scripts/apply-main-ruleset.sh --check
gh api repos/wskeei/gkd-SDP/rulesets
~~~

预期：main-protection 含原有检查及七个新增检查，strict=true。

## 8. Release 2.2.0

### Task 33：创建固定版本发布分支

**Files:**

- Modify: gradle/version.properties
- Modify: CHANGELOG.md
- Modify: docs/testing/release-smoke-checklist.md
- Modify: docs/releasing.md
- Create: scripts/verify-release-notes-privacy.py
- Create: scripts/tests/test_release_notes_privacy.py

**Step 1：确认版本唯一并创建 worktree**

~~~bash
git fetch origin --prune --tags
git switch main
git pull --ff-only origin main
test "$(git rev-parse main)" = "$(git rev-parse origin/main)"
! git rev-parse --verify refs/tags/v2.2.0
! gh release view v2.2.0
git worktree add .worktrees/release-2-2-0 -b codex/release-2-2-0 origin/main
cd .worktrees/release-2-2-0
~~~

预期：v2.2.0 tag 与 Release 均不存在。

**Step 2：更新唯一版本源**

gradle/version.properties 固定：

~~~properties
versionName=2.2.0
versionCode=101
~~~

保留 upstreamBase 与 upstreamVersionCode 原值。

**Step 3：整理 CHANGELOG**

- 把 [Unreleased] 的本计划内容移动到 [2.2.0]，日期使用命令 date -u +%F 的输出。
- 分类固定为 Added、Changed、Fixed、Security、Testing。
- 写明四级信息架构、能力中心、申请/拦截/复盘体验、加密备份、数据控制、远程入口加固、无障碍/中英文本、自动化门禁与 Baseline Profile。
- 明确倒计时悬浮条继续 FLAG_SECURE，截图模式只隐藏悬浮条。
- 不写医疗、心理、自律效果、设备兼容或真机通过承诺。

**Step 4：调整 Release 检查说明**

- docs/testing/release-smoke-checklist.md 分为“发布前自动化与资产静态验收”和“公开发布后个人设备验证”。
- 把真机/OEM、实际 Accessibility/Shizuku、FLAG_SECURE、升级安装和应用内更新放到公开发布后区域，不作为 Draft 发布门禁。
- docs/releasing.md 明确本项目先完成自动化与静态资产验证，再公开 Release；用户在公开后自行下载安装验证。
- scripts/verify-release-notes-privacy.py 拒绝凭据格式、Authorization/Cookie、绝对路径、申请理由样例、规则 pattern、节点文本、联系人、设备标识和非公开 URL；只允许仓库、Android 官方文档和 Release 资产 URL。

**Step 5：验证并提交**

~~~bash
bash scripts/test-verify-release-metadata.sh
bash scripts/verify-release-metadata.sh --no-tag
python3 -m unittest discover -s scripts/tests -p 'test_*.py' -v
git diff --check
git add gradle/version.properties CHANGELOG.md docs/testing/release-smoke-checklist.md docs/releasing.md scripts/verify-release-notes-privacy.py scripts/tests/test_release_notes_privacy.py
git commit -m "chore: prepare 2.2.0 release"
~~~

### Task 34：完成发布 PR 与 main 全量验证

**Step 1：运行发布分支全量门禁**

~~~bash
./gradlew :quality-lint:test :selector:jvmTest :app:testGkdDebugUnitTest :app:lintGkdDebug :app:lintPlayDebug
./gradlew :app:koverVerifyGkdDebug :app:validateGkdDebugScreenshotTest
./gradlew :app:pixel2Api26GkdDebugAndroidTest
./gradlew :app:pixel6Api35GkdDebugAndroidTest :app:pixel6Api35PlayDebugAndroidTest
./gradlew :app:generateGkdReleaseBaselineProfile -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.enabledRules=BaselineProfile
./gradlew :baselineprofile:pixel6Api35GkdNonMinifiedReleaseAndroidTest -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.enabledRules=Macrobenchmark
python3 scripts/verify-performance-reports.py --thresholds config/quality/performance-thresholds.json
./gradlew :app:assembleGkdDebug :app:assemblePlayDebug :app:assembleGkdRelease :app:assemblePlayRelease
bash scripts/verify-release-metadata.sh --no-tag
git diff --check
git status --short
~~~

**Step 2：推送、创建 PR、等待、合并**

~~~bash
git push -u origin codex/release-2-2-0
PR_URL="$(gh pr create --base main --head codex/release-2-2-0 --title "chore: release GKD-SDP 2.2.0" --body '目的：发布稳定版 2.2.0（versionCode 101）。影响：版本元数据、CHANGELOG 与发布说明。未改：签名、tag 和资产不可变策略。验证：全部 required checks、发布元数据、四变体、GMD、截图、覆盖率和性能。真机/OEM：公开 Release 后由用户验证。')"
gh pr edit "$PR_URL" --body "目的：发布稳定版 2.2.0（versionCode 101）。影响：版本元数据、CHANGELOG 与发布说明。未改：签名、tag 和资产不可变策略。验证：全部 required checks、发布元数据、四变体、GMD、截图、覆盖率和性能。Actions：$PR_URL/checks。真机/OEM：公开 Release 后由用户验证。"
gh pr checks --watch --fail-fast
gh pr view --json state,mergeStateStatus,statusCheckRollup,reviewDecision,url
gh pr merge --squash --delete-branch
git -C ../.. switch main
git -C ../.. pull --ff-only origin main
~~~

**Step 3：等待合并提交的 main CI**

~~~bash
MAIN_SHA="$(git -C ../.. rev-parse origin/main)"
gh run list --branch main --commit "$MAIN_SHA" --limit 20
gh run watch "$(gh run list --branch main --commit "$MAIN_SHA" --workflow ci.yml --json databaseId --jq '.[0].databaseId')" --exit-status
~~~

预期：main 的全部 required checks 成功。

### Task 35：创建并推送 annotated v2.2.0 tag

**Step 1：在合并提交验证元数据**

~~~bash
cd ../..
git switch main
git pull --ff-only origin main
test "$(git rev-parse HEAD)" = "$(git rev-parse origin/main)"
bash scripts/verify-release-metadata.sh --no-tag
git status --short
~~~

预期：工作区为空。

**Step 2：创建 tag 并带 tag 验证**

~~~bash
git tag -a v2.2.0 -m "GKD-SDP 2.2.0"
bash scripts/verify-release-metadata.sh --tag v2.2.0
git show --no-patch --decorate v2.2.0
git push origin v2.2.0
~~~

禁止移动、重建或覆盖 v2.2.0。

### Task 36：等待 Release workflow 并验收 Draft 资产

**Step 1：等待 tag workflow**

~~~bash
TAG_SHA="$(git rev-list -n 1 v2.2.0)"
RUN_ID="$(gh run list --workflow release.yml --commit "$TAG_SHA" --json databaseId --jq '.[0].databaseId')"
test -n "$RUN_ID"
gh run watch "$RUN_ID" --exit-status
gh run view "$RUN_ID" --json conclusion,headSha,url
~~~

预期：conclusion=success，headSha 等于 TAG_SHA。

**Step 2：下载不可变 Draft 资产**

~~~bash
REPO_ROOT="$(git rev-parse --show-toplevel)"
AUDIT_DIR="$(mktemp -d)"
gh release view v2.2.0 --json isDraft,isPrerelease,tagName,assets,url
gh release download v2.2.0 --dir "$AUDIT_DIR"
find "$AUDIT_DIR" -maxdepth 1 -type f -print
~~~

预期：isDraft=true、isPrerelease=false，只有一个 APK、update.json、SHA256SUMS.txt。

**Step 3：核对 checksum、包、版本、签名与 profile**

~~~bash
cd "$AUDIT_DIR"
shasum -a 256 -c SHA256SUMS.txt
APK_PATH="$(find . -maxdepth 1 -name '*.apk' -print -quit)"
test -n "$APK_PATH"
APKSIGNER="$(find "$ANDROID_HOME/build-tools" -type f -name apksigner -print | sort -V | tail -n 1)"
APKANALYZER="$(command -v apkanalyzer || find "$ANDROID_HOME" -type f -name apkanalyzer -print | head -n 1)"
"$APKSIGNER" verify --verbose --print-certs "$APK_PATH"
EXPECTED_CERT="$(gh variable get RELEASE_CERT_SHA256 --env release | tr -d '[:space:]:')"
ACTUAL_CERT="$("$APKSIGNER" verify --print-certs "$APK_PATH" | awk -F': ' '/certificate SHA-256 digest:/ {print $NF; exit}' | tr -d '[:space:]:')"
test -n "$EXPECTED_CERT"
test "$(printf '%s' "$ACTUAL_CERT" | tr '[:upper:]' '[:lower:]')" = "$(printf '%s' "$EXPECTED_CERT" | tr '[:upper:]' '[:lower:]')"
unset EXPECTED_CERT ACTUAL_CERT
test "$("$APKANALYZER" manifest application-id "$APK_PATH")" = "li.songe.gkd.sdp"
test "$("$APKANALYZER" manifest version-name "$APK_PATH")" = "2.2.0"
test "$("$APKANALYZER" manifest version-code "$APK_PATH")" = "101"
unzip -l "$APK_PATH" | rg 'assets/dexopt/baseline\\.prof'
unzip -l "$APK_PATH" | rg 'assets/dexopt/baseline\\.profm'
gh attestation verify "$APK_PATH" --repo wskeei/gkd-SDP
~~~

**Step 4：核对 update.json 与说明**

~~~bash
jq -e '.versionName == "2.2.0" and .versionCode == 101' update.json
jq -e --arg tag "v2.2.0" '.downloadUrl | contains($tag)' update.json
gh release view v2.2.0 --json body --jq .body > release-notes.txt
rg -n '2\\.2\\.0|Security|Fixed|Changed' release-notes.txt
python3 "$REPO_ROOT/scripts/verify-release-notes-privacy.py" release-notes.txt
~~~

预期：Release notes 隐私扫描退出码为 0；退出码非 0 时不发布，按“固定发布失败处理”执行 2.2.1。

### Task 37：公开 Release 并验证 Latest

**Step 1：发布 Draft**

~~~bash
gh release edit v2.2.0 --draft=false --prerelease=false --latest
gh release view v2.2.0 --json isDraft,isPrerelease,tagName,assets,url
~~~

预期：isDraft=false、isPrerelease=false、tagName=v2.2.0，三项资产名称与 Draft 完全一致。

**Step 2：验证公开元数据**

~~~bash
gh api repos/wskeei/gkd-SDP/releases/latest --jq '.tag_name'
git ls-remote --tags origin refs/tags/v2.2.0
curl --fail --location --silent --show-error https://api.github.com/repos/wskeei/gkd-SDP/releases/latest | jq -e '.tag_name == "v2.2.0"'
~~~

预期：Latest tag 为 v2.2.0。

### Task 38：同步、清理与公开后验证交接

**Step 1：同步 main**

~~~bash
git switch main
git pull --ff-only origin main
test "$(git rev-parse main)" = "$(git rev-parse origin/main)"
git status --short --branch
~~~

**Step 2：删除已合并任务 worktree**

只删除本文六个已确认合并分支对应 worktree：

~~~bash
git worktree remove .worktrees/quality-security-data
git worktree remove .worktrees/quality-architecture-lifecycle
git worktree remove .worktrees/quality-design-localization
git worktree remove .worktrees/quality-product-experience
git worktree remove .worktrees/quality-automation-performance
git worktree remove .worktrees/release-2-2-0
git worktree prune
git worktree list
~~~

不删除其他用户分支、worktree、未跟踪计划或本地修改。

**Step 3：交付公开 Release**

交接固定包含：

- 五个实施 PR、一个发布 PR、合并提交、tag、Release 和 Actions 链接。
- 21 项以上 Python 契约测试、JVM、Lint、截图、GMD、覆盖率、性能与四变体构建的实际结果。
- APK 文件名、字节数、SHA-256、证书 SHA-256 核对结果、attestation 结果和 Baseline Profile 路径。
- 明确写“未执行真机/OEM 验收；公开 Release 后由用户自行下载安装验证”。
- 用户公开后验证入口：升级安装、申请 Overlay IME、倒计时截图模式、Accessibility/Automation owner、FLAG_SECURE、通知、锁屏、Home/Back、force-stop 和应用内更新。

## 9. 固定发布失败处理

1. v2.2.0 tag 推送前任何检查失败：保留 2.2.0/101，在 codex/release-2-2-0 修复，重新通过 PR 与 main CI 后再执行 Task 35。
2. v2.2.0 tag 推送后任何 workflow、资产或 Draft 检查失败：不移动 v2.2.0，不发布其 Draft；从最新 origin/main 创建 codex/release-2-2-1，修复根因，设置 2.2.1/102，新增 CHANGELOG 2.2.1 段，重新执行 Task 34–38，tag 改为 v2.2.1。
3. v2.2.0 已公开后发现问题：不修改 Release 资产、不删除 tag；从最新 origin/main 创建 codex/fix-2-2-1，修复并用 2.2.1/102 完整重新发布。
4. 任何失败都保留失败 Actions 链接和脱敏错误类别；不关闭测试、不放宽阈值、不跳过签名/checksum/attestation。

## 10. 最终自动化验收命令

在最终 main 上按顺序执行：

~~~bash
bash scripts/check-dev-environment.sh --android
python3 -m unittest discover -s scripts/tests -p 'test_*.py' -v
bash scripts/test-verify-release-metadata.sh
bash scripts/test-generate-update-manifest.sh
bash scripts/generate-security-dependency-report.sh
python3 scripts/verify-security-dependency-report.py --report build/reports/security-dependencies.txt
python3 scripts/verify-sensitive-output-policy.py
python3 scripts/verify-compose-lifecycle-policy.py
python3 scripts/verify-ui-file-boundaries.py
python3 scripts/verify-test-quality-policy.py
./gradlew :quality-lint:test
./gradlew :selector:jvmTest
./gradlew :app:testGkdDebugUnitTest
./gradlew :app:lintGkdDebug :app:lintPlayDebug
./gradlew :app:koverVerifyGkdDebug
./gradlew :app:validateGkdDebugScreenshotTest
./gradlew :app:pixel2Api26GkdDebugAndroidTest
./gradlew :app:pixel6Api35GkdDebugAndroidTest :app:pixel6Api35PlayDebugAndroidTest
./gradlew :app:generateGkdReleaseBaselineProfile -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.enabledRules=BaselineProfile
./gradlew :baselineprofile:pixel6Api35GkdNonMinifiedReleaseAndroidTest -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.enabledRules=Macrobenchmark
python3 scripts/verify-performance-reports.py --thresholds config/quality/performance-thresholds.json
./gradlew :app:assembleGkdDebug :app:assemblePlayDebug :app:assembleGkdRelease :app:assemblePlayRelease
bash scripts/verify-release-metadata.sh --no-tag
git diff --check
git status --short --branch
~~~

全部命令退出码为 0、工作区为空、PR checks 全绿、Draft 三项资产与 attestation 静态验收通过后，执行 Task 37 公开 Release。
