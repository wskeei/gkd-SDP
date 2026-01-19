# WeChat Contact Fetcher Improvement Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task.

**Goal:** Optimize WeChat contact fetching with precise node identification, robust scrolling, and a visual status overlay.

**Architecture:**
- **`WechatContactFetcher`**: Core logic engine. Manages state, scrolling, and node processing.
- **`FetchOverlayController`**: UI component. Manages a floating window showing real-time status.
- **`FetchState`**: Data class representing the current fetching state.

**Tech Stack:** Kotlin, Android AccessibilityService, WindowManager, Jetpack Compose (for Overlay UI if applicable, otherwise standard Views).

---

### Task 1: Create `FetchOverlayController`

**Files:**
- Create: `app/src/main/kotlin/li/songe/gkd/sdp/a11y/FetchOverlayController.kt`
- Modify: `app/src/main/AndroidManifest.xml` (Ensure `SYSTEM_ALERT_WINDOW` permission is declared if not already - likely handled by base service but good to check)

**Step 1: Check Permissions**
Read `app/src/main/AndroidManifest.xml` to ensure `android.permission.SYSTEM_ALERT_WINDOW` is present.

**Step 2: Create `FetchOverlayController` class**
Create the file with a singleton `object`. It needs:
- `windowManager`: To add/remove views.
- `view`: The root view of the overlay.
- `show(context: Context)`: Initialize WM and View, add to screen.
- `hide()`: Remove from screen.
- `update(state: FetchState)`: Update text views based on state.

Use a simple `FrameLayout` containing a rounded background and `TextView`s for status/progress, and a `Button` for "Stop".

**Step 3: Define `FetchState`**
Define `data class FetchState` inside `WechatContactFetcher` (or separate file if preferred, but `WechatContactFetcher` is fine for now as it's the producer).

```kotlin
data class FetchState(
    val isFetching: Boolean = false,
    val fetchedCount: Int = 0,
    val statusText: String = "准备中...",
    val currentTarget: String? = null
)
```

**Step 4: Implement View Layout (Programmatic)**
Since we might not want to add XML resources, build the View hierarchy programmatically using `LinearLayout`, `CardView` (or custom drawable background), `TextView`.
Style: Semi-transparent black background, white text.

**Step 5: Verify Compilation**
Run `./gradlew :app:assembleDebug` to ensure no syntax errors.

---

### Task 2: Integrate Overlay into `WechatContactFetcher`

**Files:**
- Modify: `app/src/main/kotlin/li/songe/gkd/sdp/a11y/WechatContactFetcher.kt`

**Step 1: Add State Flow**
Add `val fetchStateFlow = MutableStateFlow(FetchState())` to `WechatContactFetcher`.

**Step 2: Connect Overlay to Lifecycle**
- In `startFetch`: Call `FetchOverlayController.show(service)`.
- In `stopFetch` / `finishFetch`: Call `FetchOverlayController.hide()`.
- Launch a coroutine in `startFetch` to collect `fetchStateFlow` and call `FetchOverlayController.update(it)`.

**Step 3: Update State Helpers**
Add helper method `private fun updateStatus(text: String, target: String? = null)` to easily update the flow.
Replace existing `fetchProgressFlow.value = ...` calls with `updateStatus(...)`.

---

### Task 3: Implement Node Identification Strategy

**Files:**
- Modify: `app/src/main/kotlin/li/songe/gkd/sdp/a11y/WechatContactFetcher.kt`

**Step 1: Add Blacklist Constant**
```kotlin
private val BLACK_LIST = setOf(
    "新的朋友", "仅聊天的朋友", "群聊", "标签", "公众号", "服务号",
    "企业微信联系人", "企业微信通知", "我的企业及企业联系人",
    "微信团队", "文件传输助手"
)
```

**Step 2: Implement `findValidContactNodes`**
Refactor `findContactNodes`.
- Input: `listNode` (The scrolling container).
- Logic:
    - Iterate children of `listNode`.
    - Filter out if text is in `BLACK_LIST`.
    - Filter out if text matches Index pattern (single letter A-Z, #, etc. - check height/width to be safe, indexes are usually short).
    - Return list of valid `AccessibilityNodeInfo`.

**Step 3: Implement `findScrollableList`**
Create function to find the best scrolling container.
- Traverse from root.
- Look for `ListView`, `RecyclerView`.
- If multiple, pick the one with largest area or most children.

---

### Task 4: Implement Robust Scrolling Strategy

**Files:**
- Modify: `app/src/main/kotlin/li/songe/gkd/sdp/a11y/WechatContactFetcher.kt`

**Step 1: Implement `performScroll`**
- Try `listNode.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)`.
- If return false, try `dispatchGesture` (Swipe up: center bottom -> center top).
- Return boolean indicating if *action* was dispatched (not necessarily if content changed).

**Step 2: Implement Content Hashing**
- `private fun calculateScreenHash(root: AccessibilityNodeInfo): Int`
- Traverse visible nodes, concatenate their text + bounds, calculate hash.

**Step 3: Implement `isAtBottom`**
- Check for presence of "X位联系人" text at the bottom.
- Check if `calculateScreenHash` remains unchanged after scroll attempt.

---

### Task 5: Refactor Main Loop & Detail Extraction

**Files:**
- Modify: `app/src/main/kotlin/li/songe/gkd/sdp/a11y/WechatContactFetcher.kt`

**Step 1: Refactor `fetchContactsFromCurrentScreen`**
- Implement the loop:
    1. Find List.
    2. Find Nodes.
    3. `processSingleContact` loop.
    4. `performScroll`.
    5. Check termination.

**Step 2: Implement `processSingleContact`**
- Click node.
- Wait for detail page (check specific element presence).
- Call `extractContactInfo`.
- Back.
- Handle failures/timeouts gracefully (don't crash the whole loop, just skip).

**Step 3: Optimization**
- Ensure `extractContactInfo` is fast.
- Verify `wechatId` extraction logic (look for "微信号:" text specifically).

**Step 4: Final Verification**
- Run build.
