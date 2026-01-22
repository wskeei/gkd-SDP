# Focus Quick Start Widget UI Improvement Plan

> **For Gemini:** REQUIRED SUB-SKILL: Use `subagent-driven-development` to implement this plan task-by-task.

**Goal:** Improve the visual appearance of the "Focus Quick Start Widget" by adding a dedicated play button and refining the layout for better usability.

**Architecture:**
-   **Vector Drawable:** Create a new `ic_play_arrow.xml` for a clear "Start" action.
-   **Layout Update:** Redesign `widget_focus_item.xml` to include the play button and improve spacing/typography.
-   **Widget Provider Update:** Ensure the click action is bound specifically to the play button (or the whole row, but visually centered on the button).

**Tech Stack:** Android XML Layouts, Vector Drawables.

---

### Task 1: Create Play Icon

**Goal:** Add a standard Material Design play arrow icon.

**Files:**
-   Create: `app/src/main/res/drawable/ic_play_arrow.xml`

**Step 1: Create Vector Drawable**
Create `app/src/main/res/drawable/ic_play_arrow.xml`.
*   Standard 24dp play icon path (triangle).
*   Tintable (use `android:tint` or `android:fillColor` with white/black that can be overridden).

---

### Task 2: Redesign Widget Item Layout

**Goal:** Make the list item look better and include the play button.

**Files:**
-   Modify: `app/src/main/res/layout/widget_focus_item.xml`

**Step 1: Update Layout**
*   Use `RelativeLayout` or `ConstraintLayout` (or nested `LinearLayout`) for better control.
*   **Left:** Rule Name (Bold, Larger) + Duration (Smaller, lighter) stacked vertically.
*   **Right:** `ImageView` with `@drawable/ic_play_arrow`.
    *   Background: Circular shape (optional, or just the icon).
    *   Size: 32dp or 48dp touch target.
    *   Color: Accent color (or primary).

**Step 2: Refine Typography**
*   Increase font size of rule name (e.g., 16sp).
*   Add padding/margin to separate items.

---

### Task 3: Update Widget Service to Bind Data

**Goal:** Ensure the new layout IDs are correctly bound in the `RemoteViewsFactory`.

**Files:**
-   Modify: `app/src/main/kotlin/li/songe/gkd/sdp/widget/FocusWidgetService.kt`

**Step 1: Update `getViewAt`**
*   Update `setTextViewText` calls if IDs changed (likely same IDs, but verify).
*   **Critical:** Ensure `setOnClickFillInIntent` is attached to the root view (`R.id.item_root`) OR specifically the play button (`R.id.item_play_button`). Attaching to root is usually better for widgets (easier to click). Let's stick to root but make the button visual only.

---

### Task 4: Verify Build

**Goal:** Ensure resources compile.

**Step 1: Build**
*   `./gradlew :app:assembleDebug`
