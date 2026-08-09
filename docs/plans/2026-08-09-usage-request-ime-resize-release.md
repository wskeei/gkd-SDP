# 使用申请表单输入法避让修复 Implementation Plan

> **For Codex:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 修复使用申请 Overlay 打开软键盘后遮挡“添加标签”“申请理由”和“自定义分钟数”输入框的问题，使窗口为 IME 腾出可用高度、表单保持可滚动、当前输入框自动进入键盘上方可见区域，并发布 `v2.0.0-beta.6` prerelease。

**Architecture:** 保留 `UsageGuardRequestOverlayService` 的 `TYPE_APPLICATION_OVERLAY` 和 `FLAG_SECURE`，把窗口参数固定为可获取输入焦点且使用 `SOFT_INPUT_ADJUST_RESIZE`，移除会让窗口越过屏幕边界的 `FLAG_LAYOUT_NO_LIMITS`。Compose 表单在唯一纵向滚动容器上消费 IME inset，并为三个输入框绑定独立的 `BringIntoViewRequester`；焦点获得和 IME 可见状态变化时各执行一次 `bringIntoView()`，保证键盘动画完成后输入框仍位于可见视口内。

**Tech Stack:** Kotlin、Android `WindowManager`、`TYPE_APPLICATION_OVERLAY`、Jetpack Compose Material 3、Compose `WindowInsets`、`imePadding`、`BringIntoViewRequester`、JUnit 4、Gradle、GitHub Actions、GitHub Releases。

---

## 0. 固定执行合同

从 Task 1 顺序执行到 Task 12，保持以下要求不变：

1. 以 `origin/main` 的 `v2.0.0-beta.5` 代码为功能基线，功能分支固定为 `codex/usage-request-ime-resize`。
2. 使用申请窗口继续使用 `WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY`、`MATCH_PARENT` 宽高和 `PixelFormat.TRANSLUCENT`。
3. 使用申请窗口标志固定为 `FLAG_LAYOUT_IN_SCREEN or FLAG_SECURE`。
4. 使用申请窗口不得包含 `FLAG_LAYOUT_NO_LIMITS`、`FLAG_NOT_FOCUSABLE`、`FLAG_ALT_FOCUSABLE_IM`、`FLAG_NOT_TOUCHABLE`。
5. 使用申请窗口的 `softInputMode` 固定为 `SOFT_INPUT_ADJUST_RESIZE`，并在 `WindowManager.addView()` 前完成赋值。
6. Compose 表单保留一个 `Column + verticalScroll` 真相来源，在滚动容器上应用 `imePadding()`。
7. “添加标签”“申请理由”“自定义分钟数”三个 `OutlinedTextField` 分别持有独立的 `BringIntoViewRequester`。
8. 输入框获得焦点时执行一次 `bringIntoView()`；IME 从隐藏变为显示时再次执行一次 `bringIntoView()`。
9. 不自动聚焦输入框，不主动弹出键盘，不改变用户原有输入、标签、校验、时长、提交和取消语义。
10. 不修改 `UsageGuardRecord`、Room entity、DAO、migration、schema、申请间隔、间用比、倒计时和 Overlay launcher。
11. 不新增依赖，不修改 `AndroidManifest.xml` 的 Activity/Service 声明，不把 `android:windowSoftInputMode` 写到 Service。
12. Overlay 挂载失败、重复 start、取消申请和保存失败的原有处理保持不变。
13. `FLAG_SECURE` 必须由 JVM 契约测试继续固定，输入内容不得进入日志、测试夹具、语义摘要、PR 或 Release notes。
14. `gkd` 和 `play` 两个 flavor 继续编译。
15. 功能 PR 合并后发布 `v2.0.0-beta.6`，`versionCode` 固定为 `98`。
16. prerelease 发布前不执行真机/OEM 验收；公开 Release 后由用户下载验证。

## 1. 已确认根因与固定完成状态

执行时使用以下已确认代码事实：

1. `UsageGuardRequestOverlayService.showOverlay()` 直接通过 `WindowManager.addView()` 挂载 `TYPE_APPLICATION_OVERLAY`，Manifest 的 Activity `windowSoftInputMode` 不控制该窗口。
2. 当前 `WindowManager.LayoutParams` 未设置 `softInputMode`，保留默认 `SOFT_INPUT_ADJUST_UNSPECIFIED`。
3. 当前窗口包含 `FLAG_LAYOUT_NO_LIMITS`，窗口仍按全屏范围布局，IME 出现后表单没有获得稳定的可用高度边界。
4. 当前表单只有 `verticalScroll(rememberScrollState())`，没有 `imePadding()`，IME inset 没有转换为滚动视口的底部避让空间。
5. 当前三个 `OutlinedTextField` 没有 `BringIntoViewRequester`，键盘动画改变可见区域后不会再次请求父滚动容器移动当前输入框。
6. `TYPE_APPLICATION_OVERLAY` 位于 IME 之下；窗口保持可聚焦、显式使用 `SOFT_INPUT_ADJUST_RESIZE` 后接收 IME 调整，再由 Compose inset 和焦点滚动完成内容避让。

固定完成状态：

- 点击“添加标签”输入框后，输入框和“加入”按钮完整位于键盘上方。
- 点击“申请理由”后，输入框当前编辑区域及 supporting text 位于键盘上方。
- 点击“自定义分钟数”后，数字输入框完整位于键盘上方。
- 键盘打开期间表单仍可上下滚动，顶部距离上次结束使用卡、标签、申请时长、间用比和底部操作区域均可通过滚动访问。
- 键盘关闭后 IME padding 回到 0，表单不保留多余底部空白。
- 输入框切换焦点、展开/收起动态输入区、错误提示出现/消失时，当前输入框保持可见。
- 点击“开始使用”和“取消”的业务行为与 beta.5 一致。
- Overlay 继续阻止申请理由和表单内容进入截图、录屏和非安全显示。

执行前读取以下 Android 官方资料：

- <https://developer.android.com/reference/android/view/WindowManager.LayoutParams>
- <https://developer.android.com/develop/ui/views/touch-and-input/keyboard-input/visibility>
- <https://developer.android.com/develop/ui/compose/system/insets-ui>
- <https://developer.android.com/reference/kotlin/androidx/compose/foundation/relocation/package-summary>

## Task 1: 建立独立工作树并锁定 beta.5 基线

**Files:**

- Read: `AGENTS.md`
- Read: `README_DEV.md`
- Read: `docs/releasing.md`
- Read: `docs/testing/release-smoke-checklist.md`
- Read: `app/src/main/kotlin/li/songe/gkd/sdp/service/UsageGuardRequestOverlayService.kt`
- Read: `app/src/test/kotlin/li/songe/gkd/sdp/service/UsageGuardRequestLayoutContractTest.kt`
- Read: `gradle/version.properties`
- Read: `CHANGELOG.md`

**Step 1: 记录主工作区状态**

```bash
cd /Users/zeiy/Project/gkd-SDP
git status --short --branch
git worktree list
```

保留主工作区全部已修改和未跟踪文件，不清理、不暂存、不格式化。

**Step 2: 更新 main 和版本 tag 引用**

```bash
git fetch origin refs/heads/main:refs/remotes/origin/main
git fetch origin 'refs/tags/v2.0.0-beta.*:refs/tags/v2.0.0-beta.*'
git rev-parse origin/main
git log -5 --oneline origin/main
```

固定基线提交为包含 `v2.0.0-beta.5` 的 `origin/main`。

**Step 3: 创建功能分支和工作树**

```bash
git check-ignore -q .worktrees
git worktree add .worktrees/usage-request-ime-resize \
  -b codex/usage-request-ime-resize origin/main
cd .worktrees/usage-request-ime-resize
git status --short --branch
```

工作树必须干净，分支必须为 `codex/usage-request-ime-resize`。

**Step 4: 锁定版本和 schema 基线**

```bash
cat gradle/version.properties
git tag --list 'v2.0.0-beta.5'
test -f app/schemas/li.songe.gkd.sdp.db.AppDb/33.json
```

固定输出：

```text
versionName=2.0.0-beta.5
versionCode=97
upstreamBase=1.12.1
upstreamVersionCode=92
```

**Step 5: 运行基线测试**

```bash
./gradlew :app:testGkdDebugUnitTest \
  --tests '*UsageGuardRequestLayoutContractTest'
git diff --check
```

命令必须 PASS，`git diff --check` 必须无输出。

## Task 2: 用失败测试固定 Overlay 的 IME 窗口合同

**Files:**

- Modify: `app/src/main/kotlin/li/songe/gkd/sdp/service/UsageGuardRequestOverlayService.kt`
- Create: `app/src/test/kotlin/li/songe/gkd/sdp/service/UsageGuardRequestOverlayWindowContractTest.kt`

**Step 1: 创建窗口参数契约测试**

创建 `UsageGuardRequestOverlayWindowContractTest.kt`：

```kotlin
package li.songe.gkd.sdp.service

import android.view.WindowManager
import org.junit.Assert.assertEquals
import org.junit.Test

class UsageGuardRequestOverlayWindowContractTest {
    @Test
    fun requestOverlayIsSecureFocusableAndResizableForIme() {
        val requiredFlags = listOf(
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            WindowManager.LayoutParams.FLAG_SECURE,
        )
        requiredFlags.forEach { flag ->
            assertEquals(flag, USAGE_GUARD_REQUEST_OVERLAY_FLAGS and flag)
        }

        val forbiddenFlags = listOf(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
        )
        forbiddenFlags.forEach { flag ->
            assertEquals(0, USAGE_GUARD_REQUEST_OVERLAY_FLAGS and flag)
        }

        assertEquals(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE,
            USAGE_GUARD_REQUEST_OVERLAY_SOFT_INPUT_MODE,
        )
    }
}
```

**Step 2: 运行测试并记录失败**

```bash
./gradlew :app:testGkdDebugUnitTest \
  --tests '*UsageGuardRequestOverlayWindowContractTest'
```

固定失败原因：`USAGE_GUARD_REQUEST_OVERLAY_FLAGS` 和 `USAGE_GUARD_REQUEST_OVERLAY_SOFT_INPUT_MODE` 尚未定义。

**Step 3: 不提交失败状态**

保留测试文件，继续执行 Task 3。

## Task 3: 用失败测试固定 Compose IME 避让与三个输入框可见性合同

**Files:**

- Modify: `app/src/test/kotlin/li/songe/gkd/sdp/service/UsageGuardRequestLayoutContractTest.kt`

**Step 1: 扩展现有 source contract test**

在 `UsageGuardRequestLayoutContractTest` 增加第二个测试：

```kotlin
@Test
fun requestFormConsumesImeInsetsAndRelocatesEveryInputField() {
    val source = sourceFile(
        "app/src/main/kotlin/li/songe/gkd/sdp/service/UsageGuardRequestOverlayService.kt",
    ).readText()
    val form = source.substringAfter("private fun UsageGuardRequestContent(")
    val windowParams = source
        .substringAfter("val params = WindowManager.LayoutParams(")
        .substringBefore("runCatching { windowManager.addView")

    val imePadding = form.indexOf(".imePadding()")
    val verticalScroll = form.indexOf(".verticalScroll(formScrollState)")
    assertTrue(imePadding >= 0)
    assertTrue(imePadding < verticalScroll)

    assertTrue(form.contains("val newTagInputModifier = rememberImeAwareBringIntoViewModifier()"))
    assertTrue(form.contains("val reasonInputModifier = rememberImeAwareBringIntoViewModifier()"))
    assertTrue(form.contains("val customDurationInputModifier = rememberImeAwareBringIntoViewModifier()"))
    assertTrue(form.contains(".then(newTagInputModifier)"))
    assertTrue(form.contains(".then(reasonInputModifier)"))
    assertTrue(form.contains(".then(customDurationInputModifier)"))

    assertTrue(source.contains("val imeVisible = WindowInsets.isImeVisible"))
    assertTrue(source.contains("LaunchedEffect(isFocused, imeVisible)"))
    assertTrue(source.contains("requester.bringIntoView()"))

    assertTrue(windowParams.contains("USAGE_GUARD_REQUEST_OVERLAY_FLAGS"))
    assertTrue(
        windowParams.contains(
            "softInputMode = USAGE_GUARD_REQUEST_OVERLAY_SOFT_INPUT_MODE",
        ),
    )
    assertFalse(windowParams.contains("FLAG_LAYOUT_NO_LIMITS"))
}
```

保留该测试文件已有的 `assertFalse`、`assertTrue` 和 `sourceFile()`。

**Step 2: 运行两个契约测试并记录失败**

```bash
./gradlew :app:testGkdDebugUnitTest \
  --tests '*UsageGuardRequestOverlayWindowContractTest' \
  --tests '*UsageGuardRequestLayoutContractTest'
```

固定失败项为缺少显式 resize mode、`imePadding()`、三个焦点 relocation modifier 和新窗口常量。

## Task 4: 实现可调整大小且保持安全的 Overlay 窗口参数

**Files:**

- Modify: `app/src/main/kotlin/li/songe/gkd/sdp/service/UsageGuardRequestOverlayService.kt`

**Step 1: 定义唯一窗口常量**

在 imports 之后、`UsageRequestDatasetState` 之前增加：

```kotlin
internal val USAGE_GUARD_REQUEST_OVERLAY_FLAGS =
    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
        WindowManager.LayoutParams.FLAG_SECURE

internal val USAGE_GUARD_REQUEST_OVERLAY_SOFT_INPUT_MODE =
    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
```

**Step 2: 替换内联窗口 flags**

把 `showOverlay()` 中的 `WindowManager.LayoutParams` 改为：

```kotlin
val params = WindowManager.LayoutParams(
    WindowManager.LayoutParams.MATCH_PARENT,
    WindowManager.LayoutParams.MATCH_PARENT,
    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
    USAGE_GUARD_REQUEST_OVERLAY_FLAGS,
    PixelFormat.TRANSLUCENT,
).apply {
    softInputMode = USAGE_GUARD_REQUEST_OVERLAY_SOFT_INPUT_MODE
}
```

**Step 3: 保持挂载成功边界原样**

保留以下调用顺序：

```text
构建 ComposeView
构建 LayoutParams 并设置 softInputMode
WindowManager.addView
挂载失败清理 view
UsageGuardEngine.onOverlayMountFailed
stopSelf
```

**Step 4: 运行窗口契约测试**

```bash
./gradlew :app:testGkdDebugUnitTest \
  --tests '*UsageGuardRequestOverlayWindowContractTest'
```

命令必须 PASS。

**Step 5: 提交窗口合同**

```bash
git add \
  app/src/main/kotlin/li/songe/gkd/sdp/service/UsageGuardRequestOverlayService.kt \
  app/src/test/kotlin/li/songe/gkd/sdp/service/UsageGuardRequestOverlayWindowContractTest.kt
git commit -m "fix: resize usage request overlay for ime"
```

## Task 5: 让滚动容器消费 IME inset 并自动显示当前输入框

**Files:**

- Modify: `app/src/main/kotlin/li/songe/gkd/sdp/service/UsageGuardRequestOverlayService.kt`
- Modify: `app/src/test/kotlin/li/songe/gkd/sdp/service/UsageGuardRequestLayoutContractTest.kt`

**Step 1: 增加 Compose imports**

增加：

```kotlin
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.onFocusChanged
```

**Step 2: 增加统一焦点 relocation helper**

在 `UsageGuardRequestContent` 之前增加：

```kotlin
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun rememberImeAwareBringIntoViewModifier(): Modifier {
    val requester = remember { BringIntoViewRequester() }
    var isFocused by remember { mutableStateOf(false) }
    val imeVisible = WindowInsets.isImeVisible

    LaunchedEffect(isFocused, imeVisible) {
        if (isFocused) {
            requester.bringIntoView()
        }
    }

    return Modifier
        .bringIntoViewRequester(requester)
        .onFocusChanged { isFocused = it.isFocused }
}
```

该 helper 不加入延时、不读取真实时钟、不启动独立长期协程、不操作键盘显示状态。

**Step 3: 在表单顶层创建唯一滚动状态和三个独立 modifier**

在 `UsageGuardRequestContent` 的本地状态之后增加：

```kotlin
val formScrollState = rememberScrollState()
val newTagInputModifier = rememberImeAwareBringIntoViewModifier()
val reasonInputModifier = rememberImeAwareBringIntoViewModifier()
val customDurationInputModifier = rememberImeAwareBringIntoViewModifier()
```

三个 modifier 不共享 `BringIntoViewRequester`。

**Step 4: 固定滚动容器 modifier 顺序**

把表单 `Column` modifier 改为：

```kotlin
modifier = Modifier
    .fillMaxSize()
    .imePadding()
    .verticalScroll(formScrollState)
    .padding(24.dp),
```

保持一个纵向滚动容器，不在 `SelfControlElapsedCard`、标签区、时长区或按钮区增加第二个纵向滚动状态。

**Step 5: 绑定“添加标签”输入框**

把 modifier 改为：

```kotlin
modifier = Modifier
    .weight(1f)
    .then(newTagInputModifier),
```

保留单行输入、“加入”按钮、去重、trim、自动选中新标签和收起编辑器行为。

**Step 6: 绑定“申请理由”输入框**

把 modifier 改为：

```kotlin
modifier = Modifier
    .fillMaxWidth()
    .then(reasonInputModifier),
```

保留 `minLines = 3`、字数提示、错误提示、trim 和最低字数校验。

**Step 7: 绑定“自定义分钟数”输入框**

把 modifier 改为：

```kotlin
modifier = Modifier
    .fillMaxWidth()
    .then(customDurationInputModifier),
```

保留数字键盘、仅数字输入、正整数校验和间用比实时刷新。

**Step 8: 运行布局和窗口契约测试**

```bash
./gradlew :app:testGkdDebugUnitTest \
  --tests '*UsageGuardRequestOverlayWindowContractTest' \
  --tests '*UsageGuardRequestLayoutContractTest'
```

命令必须 PASS。

**Step 9: 运行 Kotlin 编译**

```bash
./gradlew \
  :app:compileGkdDebugKotlin \
  :app:compilePlayDebugKotlin
```

两个 flavor 必须 PASS。

**Step 10: 提交 Compose 避让修复**

```bash
git add \
  app/src/main/kotlin/li/songe/gkd/sdp/service/UsageGuardRequestOverlayService.kt \
  app/src/test/kotlin/li/songe/gkd/sdp/service/UsageGuardRequestLayoutContractTest.kt
git commit -m "fix: keep request inputs above keyboard"
```

## Task 6: 补充维护合同、验收矩阵和变更记录

**Files:**

- Modify: `README_DEV.md`
- Modify: `docs/testing/release-smoke-checklist.md`
- Modify: `CHANGELOG.md`

**Step 1: 更新 README_DEV Overlay 合同**

在 Digital Self-Discipline / Usage Guard Overlay 说明中加入以下固定合同：

```text
The usage-request form is a focusable TYPE_APPLICATION_OVERLAY. Its window
keeps FLAG_SECURE, uses SOFT_INPUT_ADJUST_RESIZE, excludes
FLAG_LAYOUT_NO_LIMITS and FLAG_ALT_FOCUSABLE_IM, consumes the Compose IME
inset in its single vertical scroll container, and relocates the focused
request input after IME visibility changes.
```

**Step 2: 更新 Release smoke checklist**

在“使用申请弹窗、理由、标签、时长”条目后增加：

```markdown
- [ ] 分别打开“添加标签”“申请理由”“自定义分钟数”输入框；软键盘出现后当前输入框、辅助文字和同一行操作按钮位于键盘上方，表单可继续滚动，关闭键盘后不残留底部空白。
- [ ] 在 360dp 竖屏、横屏、200% 字体下切换三个输入框焦点并展开/收起动态输入区；当前输入框保持可见，提交、取消、FLAG_SECURE 和申请数据语义不变。
```

这些条目保持未勾选，公开 prerelease 后由用户验证。

**Step 3: 更新 `[Unreleased]`**

在 `CHANGELOG.md` 的 `[Unreleased]` 下加入：

```markdown
### Fixed

- Keep the active usage-request input visible above the software keyboard and
  preserve scrolling across tag, reason, and custom-duration editors.

### Security

- Preserve `FLAG_SECURE` while making the request overlay resize for the IME.
```

Release notes 不写入真实申请理由、标签内容、应用名称或设备信息。

**Step 4: 运行文档与格式检查**

```bash
git diff --check
rg -n "SOFT_INPUT_ADJUST_RESIZE|FLAG_LAYOUT_NO_LIMITS|FLAG_ALT_FOCUSABLE_IM|imePadding|BringIntoViewRequester" \
  README_DEV.md \
  docs/testing/release-smoke-checklist.md \
  app/src/main/kotlin/li/songe/gkd/sdp/service/UsageGuardRequestOverlayService.kt
```

**Step 5: 提交文档**

```bash
git add README_DEV.md docs/testing/release-smoke-checklist.md CHANGELOG.md
git commit -m "docs: document request overlay ime behavior"
```

## Task 7: 执行功能分支完整验证和代码审查

**Files:**

- Verify: `app/src/main/kotlin/li/songe/gkd/sdp/service/UsageGuardRequestOverlayService.kt`
- Verify: `app/src/test/kotlin/li/songe/gkd/sdp/service/UsageGuardRequestOverlayWindowContractTest.kt`
- Verify: `app/src/test/kotlin/li/songe/gkd/sdp/service/UsageGuardRequestLayoutContractTest.kt`
- Verify: `README_DEV.md`
- Verify: `docs/testing/release-smoke-checklist.md`
- Verify: `CHANGELOG.md`

**Step 1: 运行针对性测试**

```bash
./gradlew :app:testGkdDebugUnitTest \
  --tests '*UsageGuardRequestOverlayWindowContractTest' \
  --tests '*UsageGuardRequestLayoutContractTest' \
  --tests '*UsageGuardRequestIntervalContractTest'
```

全部测试必须 PASS。

**Step 2: 运行完整 JVM、Lint 和双 flavor 构建**

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

全部命令必须 PASS。

**Step 3: 验证无持久化和依赖变化**

```bash
git diff --exit-code origin/main -- \
  app/schemas \
  app/src/main/kotlin/li/songe/gkd/sdp/db/AppDb.kt \
  gradle/libs.versions.toml
```

命令必须无输出。

**Step 4: 验证 Overlay 安全与输入合同**

```bash
rg -n "USAGE_GUARD_REQUEST_OVERLAY_FLAGS|USAGE_GUARD_REQUEST_OVERLAY_SOFT_INPUT_MODE|softInputMode|imePadding|rememberImeAwareBringIntoViewModifier" \
  app/src/main/kotlin/li/songe/gkd/sdp/service/UsageGuardRequestOverlayService.kt \
  app/src/test/kotlin/li/songe/gkd/sdp/service/UsageGuardRequestOverlayWindowContractTest.kt \
  app/src/test/kotlin/li/songe/gkd/sdp/service/UsageGuardRequestLayoutContractTest.kt
rg -n "reasonText|actualUrl|redirectUrl|selector|node text|Authorization" \
  app/src/test/kotlin/li/songe/gkd/sdp/service/UsageGuardRequestOverlayWindowContractTest.kt \
  app/src/test/kotlin/li/songe/gkd/sdp/service/UsageGuardRequestLayoutContractTest.kt
```

第二条命令不得命中敏感测试数据。

**Step 5: 运行最终静态检查**

```bash
git diff --check
git status --short --branch
git log --oneline origin/main..HEAD
```

工作树只包含本任务提交，无未提交生产文件和构建产物。

**Step 6: 执行代码审查**

审查逐项确认：

- Window flags 精确等于 `FLAG_LAYOUT_IN_SCREEN or FLAG_SECURE`。
- `softInputMode` 在 `addView()` 前精确设置为 `SOFT_INPUT_ADJUST_RESIZE`。
- 三个输入框分别持有独立 `BringIntoViewRequester`。
- `imePadding()` 位于唯一滚动容器的 `verticalScroll()` 之前。
- 焦点和 IME 可见状态各能触发 relocation。
- 未增加延时、轮询、额外 ticker、额外数据库读取和输入日志。
- 申请保存、取消、Overlay mount failure、ticker 和数据加载调用链无改动。
- `FLAG_SECURE`、`gkd`、`play` 和 Room 33 合同保持。

所有审查问题在同一分支修复，并重复 Task 7 的全部命令。

## Task 8: 创建、验证并合并功能 PR

**Files:**

- PR scope: Task 2 至 Task 7 的全部提交

**Step 1: 推送功能分支**

```bash
git push -u origin codex/usage-request-ime-resize
```

**Step 2: 创建固定 PR body 文件**

使用 `apply_patch` 创建 `/tmp/usage-request-ime-resize-pr.md`，内容固定为：

```markdown
## Purpose

Prevent the software keyboard from covering inputs in the usage-request overlay.

## Changes

- make the request overlay explicitly resize for the IME
- keep the secure, focusable application-overlay contract
- consume IME insets in the single scroll container
- relocate tag, reason, and custom-duration inputs on focus and IME visibility changes
- add JVM source/window contracts and release smoke coverage

## Preserved behavior

- request validation, persistence, cancel, mount-failure, countdown, rhythm, and overlay launcher flows
- `FLAG_SECURE`
- Room schema 33 and both Android flavors

## Verification

- list every command and its real PASS output from Task 7
- no physical-device/OEM validation executed before prerelease
```

把 Task 7 的真实命令和结果写入 `## Verification`，不写入申请理由、设备数据或凭据。

**Step 3: 创建功能 PR**

```bash
gh pr create \
  --repo wskeei/gkd-SDP \
  --base main \
  --head codex/usage-request-ime-resize \
  --title "fix: keep usage request inputs above keyboard" \
  --body-file /tmp/usage-request-ime-resize-pr.md
```

**Step 4: 等待 required checks**

```bash
gh pr checks codex/usage-request-ime-resize \
  --repo wskeei/gkd-SDP \
  --watch \
  --fail-fast
```

`quality`、`build`、`dependency-review` 和 CodeQL 适用检查必须全部成功。

**Step 5: 确认 PR 状态**

```bash
gh pr view codex/usage-request-ime-resize \
  --repo wskeei/gkd-SDP \
  --json state,mergeStateStatus,statusCheckRollup,url
```

`state` 必须为 `OPEN`，`mergeStateStatus` 必须允许合并，required checks 必须成功。

**Step 6: 合并功能 PR**

```bash
cd /tmp
gh pr merge codex/usage-request-ime-resize \
  --repo wskeei/gkd-SDP \
  --merge \
  --delete-branch
```

**Step 7: 同步远端 main 并记录合并提交**

```bash
git fetch origin refs/heads/main:refs/remotes/origin/main
git log -3 --oneline origin/main
git merge-base --is-ancestor HEAD origin/main
```

功能分支提交必须已包含在 `origin/main`。

## Task 9: 准备并合并 v2.0.0-beta.6 Release PR

**Files:**

- Modify: `gradle/version.properties`
- Modify: `CHANGELOG.md`

**Step 1: 创建 release 分支工作树**

```bash
cd /Users/zeiy/Project/gkd-SDP
git worktree add .worktrees/release-v2.0.0-beta.6 \
  -b codex/release-v2.0.0-beta.6 origin/main
cd .worktrees/release-v2.0.0-beta.6
git status --short --branch
```

**Step 2: 固定版本元数据**

把 `gradle/version.properties` 改为：

```properties
versionName=2.0.0-beta.6
versionCode=98
upstreamBase=1.12.1
upstreamVersionCode=92
```

**Step 3: 固定 CHANGELOG 版本段**

把功能 PR 写入 `[Unreleased]` 的内容移动到：

```markdown
## [2.0.0-beta.6] - 2026-08-09

### Fixed

- Keep the active usage-request input visible above the software keyboard and
  preserve scrolling across tag, reason, and custom-duration editors.

### Security

- Preserve `FLAG_SECURE` while making the request overlay resize for the IME.
```

保留空的 `## [Unreleased]`，并更新文末链接：

```markdown
[Unreleased]: https://github.com/wskeei/gkd-SDP/compare/v2.0.0-beta.6...HEAD
[2.0.0-beta.6]: https://github.com/wskeei/gkd-SDP/compare/v2.0.0-beta.5...v2.0.0-beta.6
```

**Step 4: 运行 release metadata 检查**

```bash
bash scripts/test-verify-release-metadata.sh
bash scripts/verify-release-metadata.sh --no-tag
git diff --check
```

全部命令必须 PASS。

**Step 5: 提交和推送 release 分支**

```bash
git add gradle/version.properties CHANGELOG.md
git commit -m "chore: prepare v2.0.0-beta.6"
git push -u origin codex/release-v2.0.0-beta.6
```

**Step 6: 创建 release PR**

```bash
gh pr create \
  --repo wskeei/gkd-SDP \
  --base main \
  --head codex/release-v2.0.0-beta.6 \
  --title "chore: prepare v2.0.0-beta.6" \
  --body "Prepare versionName 2.0.0-beta.6, versionCode 98, and the dated changelog section for the usage-request IME avoidance fix."
```

**Step 7: 等待并确认 release PR checks**

```bash
gh pr checks codex/release-v2.0.0-beta.6 \
  --repo wskeei/gkd-SDP \
  --watch \
  --fail-fast
gh pr view codex/release-v2.0.0-beta.6 \
  --repo wskeei/gkd-SDP \
  --json state,mergeStateStatus,statusCheckRollup,url
```

全部 required checks 必须成功。

**Step 8: 合并 release PR**

```bash
cd /tmp
gh pr merge codex/release-v2.0.0-beta.6 \
  --repo wskeei/gkd-SDP \
  --merge \
  --delete-branch
cd /Users/zeiy/Project/gkd-SDP/.worktrees/release-v2.0.0-beta.6
git fetch origin refs/heads/main:refs/remotes/origin/main
git log -3 --oneline origin/main
```

## Task 10: 创建 annotated tag 并完成 Release workflow

**Files:**

- Verify: `gradle/version.properties`
- Verify: `CHANGELOG.md`
- Verify: `.github/workflows/release.yml`

**Step 1: 在 release 工作树切换到合并后的 main 提交**

```bash
cd /Users/zeiy/Project/gkd-SDP/.worktrees/release-v2.0.0-beta.6
git switch --detach origin/main
git status --short --branch
```

工作树必须干净。

**Step 2: 创建 annotated tag**

```bash
git tag -a v2.0.0-beta.6 -m "GKD-SDP v2.0.0-beta.6"
git cat-file -t refs/tags/v2.0.0-beta.6
git rev-parse origin/main
git rev-parse v2.0.0-beta.6^{}
```

tag object 类型必须为 `tag`，tag peeled commit 必须与 `origin/main` 相同。

**Step 3: 运行带 tag 的 metadata 验证**

```bash
bash scripts/test-verify-release-metadata.sh
bash scripts/verify-release-metadata.sh --tag v2.0.0-beta.6
git diff --check
```

全部命令必须 PASS。

**Step 4: 推送 tag**

```bash
git push origin v2.0.0-beta.6
```

**Step 5: 获取并等待 Release workflow**

```bash
release_run_id="$(
  gh run list \
    --repo wskeei/gkd-SDP \
    --workflow Release \
    --limit 20 \
    --json databaseId,headBranch,event \
    --jq '[.[] | select(.headBranch == "v2.0.0-beta.6" and .event == "push")][0].databaseId'
)"
test -n "$release_run_id"
gh run watch "$release_run_id" \
  --repo wskeei/gkd-SDP \
  --exit-status
```

Release workflow 的 metadata、unit tests、Lint、release compilation、signing、APK verification、update manifest、checksums、attestation 和 Draft Release 步骤必须全部成功。

## Task 11: 独立核验 Draft 资产并发布 prerelease

**Files:**

- Verify asset: `gkd-sdp-v2.0.0-beta.6.apk`
- Verify asset: `update.json`
- Verify asset: `SHA256SUMS.txt`

**Step 1: 确认 Draft Release 状态和资产集合**

```bash
gh release view v2.0.0-beta.6 \
  --json isDraft,isPrerelease,name,tagName,targetCommitish,assets,url
```

固定要求：

- `isDraft=true`
- `isPrerelease=true`
- tag 为 `v2.0.0-beta.6`
- 资产仅包含 APK、`update.json` 和 `SHA256SUMS.txt`

**Step 2: 下载 Draft 资产到临时目录**

```bash
release_verify_dir="$(mktemp -d)"
gh release download v2.0.0-beta.6 --dir "$release_verify_dir"
ls -l "$release_verify_dir"
```

**Step 3: 验证 checksum**

```bash
cd "$release_verify_dir"
sha256sum --check SHA256SUMS.txt
```

APK 和 `update.json` 必须全部显示 `OK`。

**Step 4: 验证 update manifest**

```bash
python3 - "$release_verify_dir/update.json" <<'PY'
import json
import sys
from pathlib import Path

manifest = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
assert manifest["versionName"] == "2.0.0-beta.6"
assert manifest["versionCode"] == 98
assert manifest["downloadUrl"].endswith(
    "/v2.0.0-beta.6/gkd-sdp-v2.0.0-beta.6.apk"
)
assert len(manifest["sha256"]) == 64
print("update.json OK")
PY
```

**Step 5: 验证 provenance attestation**

```bash
gh attestation verify \
  "$release_verify_dir/gkd-sdp-v2.0.0-beta.6.apk" \
  --repo wskeei/gkd-SDP
```

命令必须以状态码 0 完成。

**Step 6: 核对 workflow 中的 APK 验签步骤**

```bash
release_run_id="$(
  gh run list \
    --repo wskeei/gkd-SDP \
    --workflow Release \
    --limit 20 \
    --json databaseId,headBranch,event \
    --jq '[.[] | select(.headBranch == "v2.0.0-beta.6" and .event == "push")][0].databaseId'
)"
gh run view "$release_run_id" \
  --repo wskeei/gkd-SDP \
  --json status,conclusion,jobs \
  --jq '{status, conclusion, verification: [.jobs[].steps[] | select(.name == "Verify APK signature and prepare assets") | {name, conclusion}]}'
```

固定输出为 workflow `conclusion=success`，`Verify APK signature and prepare assets` 步骤 `conclusion=success`。该步骤已经断言包名为 `li.songe.gkd.sdp`、versionName 为 `2.0.0-beta.6`、versionCode 为 `98`，并把证书 SHA-256 与 `RELEASE_CERT_SHA256` 对照。

**Step 7: 发布 prerelease**

```bash
gh release edit v2.0.0-beta.6 --draft=false --prerelease
gh release view v2.0.0-beta.6 \
  --json isDraft,isPrerelease,url,assets
```

最终状态必须为 `isDraft=false`、`isPrerelease=true`。

## Task 12: 发布后交接用户下载验证

**Files:**

- Read: `docs/testing/release-smoke-checklist.md`

**Step 1: 输出公开 Release 信息**

交接必须包含：

- 功能 PR URL
- release PR URL
- Release workflow URL
- `https://github.com/wskeei/gkd-SDP/releases/tag/v2.0.0-beta.6`
- APK 文件名
- APK SHA-256
- `versionName=2.0.0-beta.6`
- `versionCode=98`
- checksum 和 attestation 验证结果
- 未执行真机/OEM 验收

**Step 2: 给出用户下载后的固定验证步骤**

```text
1. 覆盖安装 v2.0.0-beta.6。
2. 打开纳入使用申请的目标应用。
3. 展开“添加标签”，点击输入框并输入长文本。
4. 确认输入框和“加入”按钮位于软键盘上方。
5. 点击“申请理由”，输入超过三行文本。
6. 确认当前输入区域、字数提示和错误提示位于软键盘上方。
7. 展开“自定义时长”，点击数字输入框并输入分钟数。
8. 确认数字输入框位于软键盘上方且间用比实时刷新。
9. 在三个输入框之间切换焦点并上下滚动表单。
10. 关闭软键盘，确认表单底部不残留键盘高度空白。
11. 提交一次申请并确认记录、倒计时、理由和间用比正常。
12. 再次打开申请表单并取消，确认取消不创建记录、不重置间隔锚点。
```

**Step 3: 保留工作区边界**

```bash
cd /Users/zeiy/Project/gkd-SDP
git status --short --branch
git worktree list
```

保留用户原有主工作区文件和其他 worktree，不删除、不清理、不重置。

## 完成标准

以下各项必须同时满足：

- Task 1 至 Task 12 全部完成。
- Overlay flags、soft input mode、IME inset 和三个输入框 relocation 均有自动化契约。
- 所有针对性测试、完整 JVM、Lint、双 flavor 构建、CI、CodeQL 和 dependency review 成功。
- Room schema、数据库、依赖、权限和 Manifest 无变化。
- `FLAG_SECURE`、申请提交、取消、挂载失败、倒计时和间隔统计保持原合同。
- 功能 PR 和 release PR 已合并到 `main`。
- annotated `v2.0.0-beta.6` tag 指向 release merge commit。
- Release workflow 成功，APK、update manifest、checksum、签名和 attestation 完成核验。
- `v2.0.0-beta.6` 以公开 prerelease 状态发布。
- 真机/OEM 项目保持未执行，并在交接中明确由用户下载后验证。
