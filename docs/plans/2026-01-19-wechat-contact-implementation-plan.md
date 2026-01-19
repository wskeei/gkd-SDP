# WeChat Contact Auto-Jump Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task.

**Goal:** Implement automated jumping to a specific WeChat contact's chat page from the Focus Mode blocking overlay using a state machine and accessibility events.

**Architecture:**
- `FocusModeEngine`: Holds the state machine (`JumpState`) and processes accessibility events to drive the automation (Launch -> Click Search -> Paste ID -> Click Result -> Confirm Chat).
- `FocusOverlayService`: Triggers the jump sequence when the user clicks a contact.
- `A11yFeat`: Hooks `FocusModeEngine` to accessibility events.

**Tech Stack:** Android AccessibilityService, Kotlin Coroutines, State Machine pattern.

---

### Task 1: Implement State Machine in FocusModeEngine

**Files:**
- Modify: `app/src/main/kotlin/li/songe/gkd/sdp/a11y/FocusModeEngine.kt`

**Step 1: Write the failing test**
*Since this is a logic-heavy object depending on Android APIs, we will rely on manual verification after implementation, or write a unit test if we can mock `AccessibilityNodeInfo`. For now, we'll implement the logic directly as TDD with mocks is complex for `AccessibilityNodeInfo`.*
*(Skip formal failing test for this step due to heavy Android dependency, but will verify with logging)*

**Step 2: Add JumpState enum and properties**
Add `JumpState` enum, `jumpState` (Atomic/Mutex or main thread confined), `jumpJob`, `targetWechatId`.

**Step 3: Implement `startWechatJump`**
- Copy ID to clipboard.
- Set state to `WAIT_FOR_MAIN`.
- Launch WeChat.
- Start timeout job (15s).

**Step 4: Implement `onA11yEvent`**
- Check `jumpState`.
- If `IDLE`, return.
- `WAIT_FOR_MAIN`: Look for "搜索" (Search) button/text. Click. -> `WAIT_FOR_SEARCH`
- `WAIT_FOR_SEARCH`: Look for EditText. Paste ID. -> `WAIT_FOR_RESULT`
- `WAIT_FOR_RESULT`: Look for text containing "微信号: $id". Click parent. -> `COMPLETED`
- `COMPLETED`: Check if top activity is `ChattingUI` (optional, or just done). Reset state.

**Step 5: Implement `isJumpInProgress`**
- Return true if `jumpState != IDLE`.
- Update `onAppChanged` to respect this flag.

**Step 6: Commit**
`git add app/src/main/kotlin/li/songe/gkd/sdp/a11y/FocusModeEngine.kt`
`git commit -m "feat: implement WeChat jump state machine in FocusModeEngine"`

---

### Task 2: Hook Accessibility Events in A11yFeat

**Files:**
- Modify: `app/src/main/kotlin/li/songe/gkd/sdp/a11y/A11yFeat.kt`

**Step 1: Update `useFocusMode`**
Add `onA11yEvent { FocusModeEngine.onA11yEvent(it) }` inside `useFocusMode` extension function.

**Step 2: Commit**
`git add app/src/main/kotlin/li/songe/gkd/sdp/a11y/A11yFeat.kt`
`git commit -m "feat: hook FocusModeEngine to accessibility events"`

---

### Task 3: Update FocusOverlayService to Trigger Jump

**Files:**
- Modify: `app/src/main/kotlin/li/songe/gkd/sdp/service/FocusOverlayService.kt`

**Step 1: Update `onOpenWechatChat` callback**
Replace the existing `Intent` logic with `FocusModeEngine.startWechatJump(cleanId)`.
Keep the clipboard copy (redundant but safe) or let Engine handle it. Engine handles it.
Remove the `startActivity` with `weixin://` scheme.

**Step 2: Commit**
`git add app/src/main/kotlin/li/songe/gkd/sdp/service/FocusOverlayService.kt`
`git commit -m "feat: use FocusModeEngine.startWechatJump in overlay"`

---

### Task 4: Verification

**Step 1: Build and Install**
`./gradlew assembleDebug`

**Step 2: Manual Test**
1. Enable Focus Mode with WeChat Contact Whitelist.
2. Trigger blocking.
3. Click a contact in the whitelist.
4. Observe:
    - Focus blocking temporarily disabled.
    - WeChat launches.
    - "Search" clicked.
    - ID pasted.
    - Result clicked.
    - Chat opens.
    - Blocking resumes (but allows chat because of whitelist logic).

**Step 3: Commit Verification**
(No code change, just confirmation)