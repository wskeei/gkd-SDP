# GKD-SDP Agent Guide

本文件是本仓库编码代理的根级操作手册，适用于整个目录树。它补充
`README.md`、`README_DEV.md` 和贡献文档，不替代这些文档。若以后某个子目录
需要不同规则，可在该目录增加更具体的 `AGENTS.md`；离目标文件更近的规则优先。
用户在当前任务中的明确要求始终优先。

## 项目目标与边界

GKD-SDP 是基于上游 GKD 的非官方 Android fork。项目保留选择器、订阅规则、
无障碍运行时和快照能力，同时增加使用申请、专注模式、应用/URL 拦截、自动重开、
锁定保护、无障碍守护和数字自律复盘。

- 不要把本项目当成单纯的 GKD 选择器项目；数字自律功能有独立的数据、引擎、
  Service、Overlay 和失败路径。
- 优先做小而可审查的改动。除非任务明确要求，不重写既有业务、批量改名、升级整套
  工具链或顺手清理无关代码。
- 新功能和修复不得削弱既有申请、拦截、锁定、自动重开、回桌面、通知、隐私保护或
  上游选择器行为。任何有意改变都要在计划、测试和 PR 中明确说明。
- 不作未经验证的医学、心理或行为效果承诺；用户文案使用中性、可证实的描述。

## 开始工作前

1. 运行 `git status --short --branch`，确认当前分支、已有修改和未跟踪文件。
2. 阅读与任务直接相关的代码、测试和文档；不要只根据文件名或旧计划猜测行为。
3. 高风险运行时改动先阅读 [`README_DEV.md`](README_DEV.md)。发布任务先阅读
   [`docs/releasing.md`](docs/releasing.md)。上游同步先阅读 [`UPSTREAM.md`](UPSTREAM.md)。
4. 如果用户指定了 `docs/plans/` 下的计划，以该计划为本任务验收基线；先核对计划中的
   版本、分支、路径和 API 是否仍与当前 `main` 一致。其他计划是历史资料，不代表待执行
   工作，也不能被顺手修改或提交。
5. 使用 `rg` / `rg --files` 搜索。先找到真实调用链、持久化入口和现有测试，再动代码。

现有工作区内容属于用户。保留无关的已修改或未跟踪文件，不覆盖、不格式化、不提交；
如果与任务冲突，停下来说明冲突。禁止用 `git reset --hard`、`git checkout -- <path>`、
强制推送或大范围删除来清理工作区。

## 仓库地图

- `app/`：Android 主应用。
  - `app/src/main/kotlin/li/songe/gkd/sdp/a11y/`：无障碍运行时、前台应用协调和限制引擎。
  - `app/src/main/kotlin/li/songe/gkd/sdp/service/`：Android Service、前台服务和 Overlay。
  - `app/src/main/kotlin/li/songe/gkd/sdp/ui/`：Compose 页面、组件和 ViewModel。
  - `app/src/main/kotlin/li/songe/gkd/sdp/data/`：Room entity、DAO 和领域数据模型。
  - `app/src/main/kotlin/li/songe/gkd/sdp/db/`：Room 数据库与迁移。
  - `app/src/main/kotlin/li/songe/gkd/sdp/store/`：文件持久化的 StateFlow 设置。
  - `app/src/main/kotlin/li/songe/gkd/sdp/shizuku/`：Shizuku/Binder 特权能力。
  - `app/src/test/`：JVM 单元与契约测试。
  - `app/schemas/`：必须进入版本控制的 Room schema 历史。
- `selector/`：Kotlin Multiplatform 选择器解析和匹配逻辑。
- `hidden_api/`：仅用于编译的 Android hidden API stubs，不是独立运行模块。
- `gradle/`：版本目录、自有版本元数据和依赖安全下限。
- `scripts/`：发布元数据、更新清单和依赖安全检查脚本。
- `.github/workflows/`：权威 CI、CodeQL、Nightly、依赖图和 Release 流程。
- `docs/plans/`：设计与实施交接文档；`docs/testing/` 和 `docs/maintenance/` 分别保存
  测试矩阵与维护手册。

## 工具链与构建事实

- Gradle 构建和 GitHub Actions 使用 JDK 21；应用编译为 Java/JVM 11 bytecode。
- `applicationId` 为 `li.songe.gkd.sdp`；`minSdk=26`，`compileSdk/targetSdk=37`。
- Android 有 `gkd` 与 `play` 两个 product flavor。共享代码至少要保证两者可编译；
  严格无障碍能力可能只在 `gkd` 生效，不能用 `gkd` 的假设破坏 `play`。
- 依赖版本集中在 `gradle/libs.versions.toml`。不要在源码、workflow 或文档中再硬编码
  一套版本；新增或升级依赖前说明必要性、许可证、APK/构建影响和安全影响。
- 项目的权威 Android 验证环境是 GitHub Actions。本地没有 JDK、Android SDK 或可用网络
  时，不要修改 Gradle wrapper、仓库或依赖来迁就本机，也不要把未运行写成“已通过”。

本地环境可用时，按改动范围运行：

```bash
./gradlew :selector:jvmTest
./gradlew :app:testGkdDebugUnitTest
./gradlew :app:lintGkdDebug :app:lintPlayDebug
./gradlew :app:assembleGkdDebug :app:assemblePlayDebug
```

CI 的完整质量与构建基线是：

```bash
python3 -m unittest discover -s scripts/tests -p 'test_*.py' -v
bash scripts/generate-security-dependency-report.sh
./gradlew \
  :selector:jvmTest \
  :app:testGkdDebugUnitTest \
  :app:lintGkdDebug \
  :app:lintPlayDebug
python3 scripts/verify-security-dependency-report.py \
  --report build/reports/security-dependencies.txt
./gradlew \
  :app:assembleGkdDebug \
  :app:assemblePlayDebug \
  :app:assembleGkdRelease
```

任何任务结束前至少运行 `git diff --check` 和所有可用的针对性测试。完整 Gradle 不可用时，
运行不依赖 Android 环境的 shell/Python/静态检查，推送任务分支后以 Actions 为准，并在交接
中明确列出未执行项和原因。

## 编码与设计约定

- Kotlin 使用官方风格（`kotlin.code.style=official`）：4 空格缩进、清晰命名、与相邻代码
  一致的换行和 trailing comma。仓库没有要求为了格式而进行全文件重写。
- 保持 UTF-8；不要扩散历史乱码。
- 优先扩展现有 policy、coordinator、launcher、DAO 和组件，不创建并行的第二套真相来源。
- 时间、时区、随机数和调度相关逻辑使用可注入依赖，避免测试依赖真实时钟或 `sleep`。
- 业务规则尽量放在纯 Kotlin policy 中，并为边界、失败、重复事件、时钟回拨和恢复路径
  添加单元测试。UI 不应成为业务规则的唯一实现位置。
- Coroutine/Flow 和无障碍事件代码对顺序、生命周期和取消很敏感。不要随意更换 dispatcher、
  scope、缓冲或去重策略；持久化成功边界不能因 Service 销毁而被意外取消。
- Compose 图表或自定义视觉必须提供等价文字/semantics，不能只靠颜色表达含义。秒级倒计时
  不要设置会持续打断 TalkBack 的 live region。
- 用户可见行为、设置项、权限、数据格式或维护流程变化时，同步更新相关 README、
  `CHANGELOG.md` 的 `[Unreleased]`、隐私/测试文档。纯内部修复只更新真正受影响的文档。

## 高风险运行时不变量

修改无障碍、前台应用识别、数字自律或 Overlay 前，必须保持以下约束：

1. `SdpRuntimeFeatureCoordinator` 是数字自律功能的进程级统一入口。Accessibility 与
   Automation/Shizuku 模式都会通过 `A11yRuleEngine` 连接 owner；禁止只在
   `A11yService` 增加功能 hook。
2. 前台应用检测以无障碍事件为主、Shizuku task-stack 为修正路径。改动后要覆盖有/无
   Shizuku、同一应用重新判定、配置保存后重算和 runtime owner 切换。
3. 自律 Overlay 通过 `SelfControlOverlayLauncher` 启停。调用方只能在
   `OverlayLaunchResult.Accepted` 后写入引擎冷却；Service 只能在
   `WindowManager.addView` 实际成功后写入拦截历史等“已展示”记录。挂载失败必须清除
   暂定冷却并触发重算；非法 intent、重复 start 或挂载失败不得产生展示历史。
4. Overlay 的 HOME/BACK 系统导航复用 `A11yRuleEngine` 的公共桥接能力，保留 Shizuku
   注入结果与无障碍 global action 的回退，不复制一套仅在某模式可用的实现。
5. `enabled`、`locked`、`intercepted` 和“稍后自动重开”是不同状态。修改关闭行为时，
   同时审查 UI、ViewModel、DAO/store、domain engine、锁定规则、每日关闭额度和
   `AutoReenableEnforcer`，不能只改一个开关。
6. 使用申请以成功写入的 `UsageGuardRecord` 为事实来源；取消申请不能伪造记录或重置
   “距离上一次”。活动理由从记录读取，不能在 Overlay 维护另一个可编辑副本。
7. 含倒计时和申请理由的悬浮条必须继续使用 `FLAG_SECURE`。截图/录屏结果受 Android/OEM
   影响，只能在真机验证，不能承诺恢复被遮挡的第三方像素。
8. 无障碍守护仅在适用的 `gkd`/A11y 场景启用，但用户可在无障碍服务关闭时开启守护。
   生产提醒累计时间固定为 `[15, 25, 30, 33, 35, 36]` 分钟；不要把调试秒级配置带入
   release，也不要改成 FCM、WorkManager、exact alarm 或 full-screen notification。
9. 首页无障碍开关是单向保护：开启/修复可从应用触发，关闭只能引导到系统设置，禁止
   恢复直接调用 `AccessibilityService.disableSelf()` 的路径。数字自律锁定生效时，无障碍
   守护也不能被关闭。
10. Android force-stop、前台服务任务管理器 Stop、返回键、Home、切后台和划掉最近任务
    是不同生命周期场景。文案、测试和 bug 结论必须准确区分。

更完整的架构和平台限制见 [`README_DEV.md`](README_DEV.md)。

## 持久化与兼容性

本项目同时使用 Room 和文件存储；不要默认把所有设置迁入同一种存储。

- Room entity/schema 变更必须递增 `AppDb` 版本、提供可升级的 migration/AutoMigration、
  提交新的 `app/schemas/.../<version>.json`，并增加迁移或 schema 契约测试。
- 不使用 destructive migration，不删除旧 schema，不通过卸载/清空数据掩盖迁移问题。
  从最近公开版本升级的真机验证属于发布证据。
- 升级时保留既有用户配置、历史记录、锁定状态和兼容列；若确实需要丢弃数据，必须有
  明确产品决策、迁移说明和发布说明。
- 文件设置使用 `StorageExt.kt` 的既有原子写入模式，区分普通 store 与 private store；
  不直接散落新的随意文件或阻塞主线程 I/O。
- 统计/复盘数据只能保存需求所需的最小字段，并遵守既有保留期限和容量上限。

## 隐私、安全与供应链

- 禁止提交或输出 keystore、密码、token、Cookie、Authorization header、GitHub secret、
  私有 `gradle.properties` 或 release Environment 内容。
- 使用申请理由、完整 URL/规则 pattern、无障碍 node text、截图、通知正文、应用列表、
  数据库和设备标识都可能敏感。测试使用合成数据；日志、fixture、Issue 和 PR 中只放脱敏
  后的最小信息。
- 新日志不得记录实际 URL、申请理由、屏幕文本或凭据。失败诊断优先记录类型、稳定 ID、
  阶段和脱敏状态。
- GitHub Actions 的 `uses:` 必须使用审查过的完整 commit SHA，保持最小 `permissions`；
  不得把 release secrets 暴露给 PR、Nightly 或 feature branch。
- 不通过关闭 Dependabot、CodeQL、dependency review、依赖图、安全 floor 或批量 dismiss
  来制造“已修复”。依赖策略见
  [`docs/maintenance/dependency-security.md`](docs/maintenance/dependency-security.md)。
- 公开漏洞按 [`SECURITY.md`](SECURITY.md) 的私密报告流程处理；通用隐私边界见
  [`PRIVACY.md`](PRIVACY.md)。

## 测试策略

按风险分层验证：

- 纯 policy、格式化、时间范围、稳定 key、状态转换：JVM 单元测试，覆盖正常、空数据、
  重复、溢出/边界和失败降级。
- DAO/Room：查询契约、稳定排序、升级 schema 和从最近公开版本迁移。
- Compose/图表：状态到呈现的契约、空态、长文本、字体缩放和无障碍文字替代。
- 无障碍、通知、前台/后台 Service、Overlay、`FLAG_SECURE`、Shizuku、系统导航、Doze、
  OEM 行为：真实 Android 设备验证；JVM 测试不能替代。
- 运行时相关变更至少回归使用申请、应用拦截、Self Control/选择器拦截、URL 拦截、锁定、
  自动重开和无障碍守护中受影响的链路，并检查 `gkd`/`play` flavor 边界。

测试失败先找根因；不要删除测试、放宽断言、吞异常或给生产逻辑加无条件 bypass。
如果环境没有设备，明确写“未执行真机验证”，不要声称功能在真机通过。

## Git、提交与 PR

- 从同步后的 `main` 创建 `codex/<topic>` 或同样清晰的任务分支。较大改动优先使用独立
  worktree；不要在用户正在使用的脏工作树上混入实现。
- 使用小而有意义的 Conventional Commit，例如 `feat:`、`fix:`、`test:`、`docs:`、
  `refactor:`、`build:`、`ci:`、`chore:`。提交只包含本任务文件。
- 除非用户或任务明确授权远端操作，不自行 push、创建 PR、合并或发布。远端流程在范围内
  时，禁止直接推送或强推 `main`；通过 PR 合并。
- PR 描述要写明目的、影响范围、明确未改内容、验证命令、Actions 链接、真机证据和未验证项。
- PR 必须等待 `quality`、`build`、`dependency-review` 以及 CodeQL 的适用检查完成。失败
  就读取日志、修复并重新验证，不能在 pending/failing 状态下宣称完成。
- 合并前确认分支未落后且 review conversations 已解决。合并后同步本地 `main`，确认
  `origin/main` 指向同一提交；只删除已确认合并的本任务分支/worktree，不删除其他人的分支。

常用远端检查：

```bash
gh pr checks <number> --watch --fail-fast
gh run view <run-id> --log-failed
gh pr view <number> --json state,mergeStateStatus,statusCheckRollup,url
```

## 版本与发布

只有用户明确要求发布时才执行以下流程。Nightly Artifact 不是 Release。

1. 功能 PR 先通过检查并合并到 `main`。
2. 查询现有 tag/Release；版本唯一来源是 `gradle/version.properties`。公开版本只使用稳定
   `X.Y.Z`：不兼容契约变更递增 MAJOR，兼容新功能或较大重构递增 MINOR，兼容修复、UI、
   性能、安全、文档或发布工具维护递增 PATCH；混合变更使用最高影响级别。选择未占用版本，
   `versionCode` 必须大于所有历史 GKD-SDP 版本且不可复用，并更新 `CHANGELOG.md` 日期段。
3. 在发布 PR 上运行：

   ```bash
   bash scripts/test-verify-release-metadata.sh
   bash scripts/test-generate-update-manifest.sh
   bash scripts/verify-release-metadata.sh --no-tag
   git diff --check
   ```

4. 发布 PR 通过 CI 并合并，确认合并后 `main` CI 绿色，再在该合并提交创建 annotated
   `vX.Y.Z` tag；运行带 `--tag` 的验证后推送 tag。新公开 `alpha`、`beta`、`rc` 版本被禁止，
   日常测试使用 Nightly Artifact。
5. 标签触发 `.github/workflows/release.yml`。它负责测试、Lint、release 编译、签名、验签、
   `update.json`、`SHA256SUMS.txt`、provenance attestation 和 Draft Release。
6. 下载 Draft 资产，核对版本、包名、大小、SHA-256、签名/证书和 attestation；按
   [`docs/testing/release-smoke-checklist.md`](docs/testing/release-smoke-checklist.md) 记录发布后
   由用户执行的真机/OEM 项目，不把未执行项写成已通过。

不创建字面量为 `latest` 的 tag/Release；GitHub 的“Latest”属性只标记最新稳定版。不移动或
覆盖已公开 tag，不替换不可变 Release 资产，不手工上传未经工作流签名验证的 APK。发布失败
需要修改代码时使用下一个稳定 PATCH 和更大的 `versionCode`，具体恢复方式遵循
[`docs/releasing.md`](docs/releasing.md) 和
[`docs/maintenance/recovery-runbook.md`](docs/maintenance/recovery-runbook.md)。

## 上游同步

上游 GKD 与 GKD-SDP 有独立产品历史。同步时使用专门分支和可审计 merge/cherry-pick，
禁止 `git reset --hard upstream/main` 覆盖 fork 历史。检查 application ID、版本元数据、
Room migration、Manifest/权限、两种运行模式和全部数字自律链路；上游 `v1.x` tag 不能复用
为 GKD-SDP Release。完整流程见 [`UPSTREAM.md`](UPSTREAM.md)。

## Code Review Rules

审查时优先指出会造成真实行为或数据风险的问题，并给出可验证的安全路径：

- 数字自律 hook 是否同时覆盖 Accessibility 与 Automation/Shizuku owner。
- Overlay 失败或重复启动是否仍错误写入成功状态、冷却或历史事件。
- 是否存在绕过锁定、每日关闭额度、自动重开或首页单向无障碍保护的入口。
- Room 升级是否缺 migration/schema/test，或可能清空、错配用户数据。
- `gkd` 专属能力是否破坏 `play` 编译或运行。
- Coroutine 生命周期、事件去重和配置保存后重算是否引入竞态或遗漏。
- 图表/倒计时是否缺少文字替代，敏感 Overlay 是否丢失 `FLAG_SECURE`。
- 日志、测试数据、通知、更新清单或 Release 资产是否泄露敏感信息或绕过供应链检查。
- 文档和验证声明是否与真实命令、Actions、设备证据一致。

格式问题交给现有编译器和 Lint；Review 不要用大规模风格意见淹没功能、安全和兼容性问题。

## 完成标准

任务只有在以下条件满足后才算完成：

- 用户要求和指定计划逐项落实，原有行为边界得到保留或有意变更已明确说明。
- 新增/修改逻辑有相称的自动化测试，所有已运行检查有真实结果。
- `git diff --check` 通过，没有误提交用户文件、构建产物、秘密或敏感数据。
- 需要远端验证时，GitHub Actions 的适用检查通过；需要发布时，Release 资产与证明已验证。
- 真机、OEM、权限或迁移项目无法验证时，清楚列出限制和后续手工检查，不夸大完成度。
- 工作区保留用户原有变更；分支、PR、main 同步和清理状态与交接说明一致。

维护本文件时以当前代码和 workflow 为准。若命令、架构不变量、required checks、版本或发布
流程变化，应在同一 PR 更新本文件；不要把临时任务、一次性分支名或容易过期的当前版本号
写成永久规则。
