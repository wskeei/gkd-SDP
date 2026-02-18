# Auto Re-enable Interval UX Refine Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Make auto re-enable interval editable only once every 72 hours in UI (disabled input during cooldown), and show the next auto re-enable execution time after interval is configured.

**Architecture:** Keep policy logic in pure functions under `FocusLockVm` companion for deterministic unit tests. `FocusLockPage` consumes derived UI state (`canEdit`, `nextEditableAt`, `nextEnforceAt`) to control input enabled state and time labels.

**Tech Stack:** Kotlin, Jetpack Compose Material3, existing `AutoReenablePolicy`, JUnit4 unit tests.

---

### Task 1: Add/extend failing unit tests first

**Files:**
- Modify: `app/src/test/kotlin/li/songe/gkd/sdp/ui/FocusLockVmAutoReenableTest.kt`

**Steps:**
1. Add tests for cooldown UI editability and next enforce timestamp derivation.
2. Run focused test command and confirm failure first.

### Task 2: Implement minimal VM UI-state helpers

**Files:**
- Modify: `app/src/main/kotlin/li/songe/gkd/sdp/ui/FocusLockVm.kt`

**Steps:**
1. Add pure UI-state data class and evaluation function in companion.
2. Reuse `AutoReenablePolicy` to compute `canEdit`, `nextEditableAt`, `nextEnforceAt`.
3. Keep existing update behavior unchanged except consuming shared computations when useful.

### Task 3: Wire UI behavior in FocusLockPage

**Files:**
- Modify: `app/src/main/kotlin/li/songe/gkd/sdp/ui/FocusLockPage.kt`

**Steps:**
1. Disable interval `OutlinedTextField` during cooldown (`enabled = canEdit`).
2. Disable confirm button during cooldown.
3. Show “下一次自动重开时间” in dialog and guard card.

### Task 4: Verify

**Steps:**
1. Run focused tests: `./gradlew :app:testGkdDebugUnitTest --tests "*FocusLockVmAutoReenableTest"`.
2. Run broader safety test: `./gradlew :app:testGkdDebugUnitTest --tests "*AutoReenable*"`.
3. Report exact command results before claiming completion.
