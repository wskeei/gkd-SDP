# Design Specification: 微信联系人自动跳转 (Auto-Jump to WeChat Contact)

Based on your feedback, here is the detailed design for the automation logic.

## 1. State Machine (状态机)
We will introduce a `JumpState` enum in `FocusModeEngine` to track the automation progress.

```kotlin
enum class JumpState {
    IDLE,               // 空闲
    WAIT_FOR_MAIN,      // 已启动微信，等待主界面
    WAIT_FOR_SEARCH,    // 已点击搜索，等待搜索页
    WAIT_FOR_RESULT,    // 已输入内容，等待搜索结果
    COMPLETED           // 检测到聊天页，完成
}
```

## 2. Execution Flow (执行流程)

### Step 1: Initiation (in `FocusOverlayService`)
*   User clicks a contact.
*   Call `FocusModeEngine.startWechatJump(wechatId)`.
*   **Action**:
    *   Copy `wechatId` to Clipboard.
    *   Set `jumpState = WAIT_FOR_MAIN`.
    *   Start 15s timeout timer.
    *   Launch WeChat (`com.tencent.mm`).
    *   **Crucial**: Temporarily disable "Focus Blocking" for 15s (or until jump ends) to allow the automation to run without being immediately blocked by the overlay.

### Step 2: Automation (in `FocusModeEngine.onAccessibilityEvent`)
*   **State: WAIT_FOR_MAIN**
    *   Target: `com.tencent.mm.ui.LauncherUI`
    *   Action: Find node with contentDescription="搜索" or text="搜索". Click it.
    *   Transition -> `WAIT_FOR_SEARCH`.
*   **State: WAIT_FOR_SEARCH**
    *   Target: Search Activity (often `FTSMainUI`).
    *   Action: Find `EditText`. Perform `ACTION_PASTE` (more reliable than `SET_TEXT` for some apps) or `SET_TEXT(wechatId)`.
    *   Transition -> `WAIT_FOR_RESULT`.
*   **State: WAIT_FOR_RESULT**
    *   Target: Search results.
    *   Action: Find node containing text `微信号: $targetWechatId` (Strict Match). Click the parent/container.
    *   Transition -> `COMPLETED`.
*   **State: COMPLETED**
    *   Target: `ChattingUI` detected.
    *   Action: Reset state to `IDLE`. Cancel timeout. Re-enable Focus Blocking (strict check logic will now pass because we are in the correct chat).

## 3. Error Handling & Timeout
*   **Timeout (15s)**: If the flow gets stuck (e.g., bad network, UI changed), reset `jumpState` to `IDLE`.
*   **Result**: The temporary "Focus Blocking" disable expires. The `FocusModeEngine` standard check runs.
*   **Outcome**: If the user is NOT in the correct chat, the standard blocking overlay reappears immediately ("Continue Blocking"). A Toast "Jump failed, contact not found" is shown.

## 4. Implementation Details
*   Modify `FocusModeEngine`: Add `jumpState`, `jumpJob` (timeout), and the event handling logic.
*   Modify `FocusOverlayService`: Update click listener to call `startWechatJump`.

Does this detailed design look correct to you? specifically the "Temporary Disable" logic to allow the automation to work?
