# Minified Release Startup Hotfix Implementation Plan

> **For Codex:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 修复 v2.2.0 至 v2.2.2 的正式 APK 在启动阶段崩溃的问题，并让 CI 直接安装和启动经过 R8 的正式变体，阻止同类回归进入 Release。

**Architecture:** 保留现有 DAO 动态代理与备份互斥语义，但由 `DbSet` 显式传入每个 Room DAO 的接口 `Class`，并在 R8 中保留运行时动态代理所需的 `@Dao` 接口类型。发布 smoke 不再引入会改变 target classloader 的独立测试 APK，而是由 shell runner 创建 API 26/API 35 临时模拟器，直接安装并启动已经过 R8 的 `gkdRelease` 与 `playRelease` APK；CI 和 Nightly 共用同一 runner。

**Tech Stack:** Kotlin, Room, Java dynamic proxy, R8, ADB/emulator, Python unittest, GitHub Actions.

---

### Task 1: 固化事故证据与 CI 契约

**Files:**
- Create: `docs/2026-08-12-v2.2.x-release-startup-incident-review.md`
- Modify: `scripts/tests/test_ci_quality_policy.py`
- Create: `scripts/smoke-test-release-apk.sh`
- Create: `scripts/run-release-apk-smoke-emulator.sh`
- Modify: `.github/workflows/ci.yml`
- Modify: `.github/workflows/nightly.yml`

**Step 1: Write the failing policy test**

在 `test_ci_quality_policy.py` 断言 CI/Nightly 调用统一的 APK smoke runner，并覆盖 API 26/35、两个正式 flavor 和真实 R8 APK。

**Step 2: Run test to verify it fails**

Run: `python3 -m unittest scripts.tests.test_ci_quality_policy -v`

Expected: FAIL，提示缺少 release APK smoke runner。

**Step 3: Add the minimal release smoke module and workflow gates**

创建 ADB smoke runner：为每个 API 创建临时 emulator（按宿主架构选择 `arm64-v8a` 或 `x86_64`），安装 APK，冷启动 `MainActivity`，轮询进程和 resumed activity，并检查本包崩溃/ANR；设备启动有明确超时。API 26/35 CI 与 Nightly 都运行 `gkdRelease` 和 `playRelease`。

**Step 4: Run the current v2.2.2 minified APK smoke to verify the product failure**

Run `scripts/smoke-test-release-apk.sh` against the pre-fix minified APK on API 35.

Expected: FAIL during application initialization with the retraced `GatedRoomDao.kt` interface requirement.

**Step 5: Record the incident**

记录受影响版本、复现矩阵、根因、为什么旧测试漏检、逐项修复步骤和发布恢复规则；不把模拟器结果写成真机/OEM 通过。

### Task 2: Remove R8-dependent DAO type discovery

**Files:**
- Modify: `app/src/main/kotlin/li/songe/gkd/sdp/backup/GatedRoomDao.kt`
- Modify: `app/src/main/kotlin/li/songe/gkd/sdp/db/AppDb.kt`
- Modify: `app/src/test/kotlin/li/songe/gkd/sdp/backup/GatedRoomDaoTest.kt`

**Step 1: Write the failing unit test/API calls**

更新测试以显式传入 `GatedRoomDaoTestContract::class.java`，并覆盖非接口类型被拒绝、同一 delegate 仍复用代理、同步/异步/失败/取消语义不变。

**Step 2: Run the focused test before implementation**

Run: `./gradlew :app:testGkdDebugUnitTest --tests 'li.songe.gkd.sdp.backup.GatedRoomDaoTest'`

Expected: compilation FAIL because the explicit class-token API does not exist yet.

**Step 3: Implement the minimal fix**

将 API 改为 `gateRoomDao(daoType: Class<T>, delegate: T)`，只验证显式类型为接口；`DbSet` 的每个 getter 使用对应 DAO 接口 class literal，并在 `app/proguard-rules.pro` 保留 `@Dao` 接口。不得增加无关的宽泛 keep rule。

**Step 4: Run focused verification**

Run the focused unit test and assemble both `gkdRelease`/`playRelease`.

Expected: PASS.

**Step 5: Run real minified startup smokes**

在 API 26/35 启动 `gkdRelease`，并在 API 35 手动安装启动 `playRelease`。

Expected: Application 与 MainActivity 保持存活，logcat 无本包 FATAL EXCEPTION/ANR。

### Task 3: Audit adjacent startup and R8 risks

**Files:**
- Modify only files proven defective by a reproducible failing test.
- Update: `docs/2026-08-12-v2.2.x-release-startup-incident-review.md`

**Step 1: Search risky runtime reflection and startup assertions**

Run: `rg 'interfaces|Proxy.newProxyInstance|Class.forName|getDeclared|java.lang.reflect|require\(|check\(' app/src/main/kotlin`

**Step 2: Trace every hit reachable from `App.onCreate`**

确认 R8 保留条件、错误降级和 flavor 边界；只对可复现缺陷新增失败测试并修复。

**Step 3: Verify clean install and same-signature upgrade**

用相同本地签名分别验证上一稳定代码构建升级到候选包，以及 v2.2.2 本地构建覆盖安装后恢复启动；不得清除 Room/store 数据来伪造通过。

### Task 4: Full verification and functional PR

**Files:**
- Modify: `CHANGELOG.md` only under `[Unreleased]` for the functional PR.

**Step 1: Run repository quality gates**

Run Python contracts, selector/JVM tests, app unit tests, both lint variants, both release assembles, release smoke tests, security report verification, and `git diff --check`.

**Step 2: Review the diff and sensitive output**

确认无用户文件、APK、日志、凭据或测试设备数据进入提交。

**Step 3: Commit and open a functional PR**

使用 Conventional Commits，push `codex/release-startup-hotfix`，创建 PR，等待 quality/build/dependency-review/CodeQL/Managed Device/performance 检查全部成功后合并。

### Task 5: Publish the next stable patch

**Files:**
- Modify in a new release branch: `gradle/version.properties`
- Modify in a new release branch: `CHANGELOG.md`

**Step 1: Select unused monotonic metadata**

查询全部 tag/Release 和历史 versionCode；选择下一个未占用 PATCH（预期 `2.2.3`）及更大的 versionCode。

**Step 2: Prepare and validate release PR**

Run release metadata tests, update manifest tests, `verify-release-metadata.sh --no-tag`, all required CI checks, then merge the release PR into `main`。

**Step 3: Tag only the verified main commit**

等待 main CI 绿色，在该提交创建 annotated `vX.Y.Z` tag，执行带 `--tag` 的验证后 push tag。

**Step 4: Inspect and publish the immutable Draft**

等待 Release workflow 成功；下载 APK、`update.json`、`SHA256SUMS.txt`，验证版本/包名/签名/checksum/attestation，并用真实 minified 签名资产做启动 smoke。确认后将 Draft 发布为 stable/latest；不得移动旧 tag 或替换旧资产。

**Step 5: Mark affected releases**

在 v2.2.0、v2.2.1、v2.2.2 的说明中增加启动崩溃警告与新版本链接，不删除历史 Release/资产。
