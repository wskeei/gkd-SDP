# Focus Quick Start Widget Implementation Plan

> **For Gemini:** REQUIRED SUB-SKILL: Use `subagent-driven-development` to implement this plan task-by-task.

**Goal:** Implement an Android App Widget that allows users to quickly start "Quick Start" focus rules directly from their home screen.

**Architecture:**
-   **Widget Provider (`FocusQuickStartWidget`):** Manages the widget lifecycle and updates.
-   **RemoteViewsService (`FocusWidgetService`):** Adapts the data (list of focus rules) to the ListView in the widget.
-   **Configuration Activity (`FocusWidgetConfigActivity`):** Allows users to select which "Quick Start" rules to display on the widget.
-   **Data Storage:** Use `SharedPreferences` to store the mapping between `appWidgetId` and the selected rule IDs.
-   **Action Handling:** Use `PendingIntent` to trigger the focus mode start action in the main app.

**Tech Stack:** Kotlin, Android App Widgets (RemoteViews), Room (for reading rules), Jetpack Compose (for Config Activity UI).

---

### Task 1: Create Widget Layouts and Metadata

**Goal:** Define the visual structure of the widget and its configuration in `AndroidManifest.xml`.

**Files:**
-   Create: `app/src/main/res/layout/widget_focus_quick_start.xml` (Main widget layout)
-   Create: `app/src/main/res/layout/widget_focus_item.xml` (List item layout)
-   Create: `app/src/main/res/xml/focus_quick_start_widget_info.xml` (Widget metadata)
-   Modify: `app/src/main/AndroidManifest.xml` (Register receiver and service)
-   Create: `app/src/main/kotlin/li/songe/gkd/sdp/widget/FocusQuickStartWidget.kt` (Stub)

**Step 1: Create Widget Layout (Main)**
Create `app/src/main/res/layout/widget_focus_quick_start.xml`. It should contain a title ("快速专注") and a `ListView`.
*   Use `LinearLayout` or `RelativeLayout`.
*   Include a `TextView` for the title.
*   Include a `ListView` with id `@+id/widget_list`.
*   Set `android:background` to a rounded drawable (need to create `app/src/main/res/drawable/widget_background.xml` or use existing if suitable).

**Step 2: Create Widget List Item Layout**
Create `app/src/main/res/layout/widget_focus_item.xml`.
*   Use `LinearLayout` (horizontal).
*   `TextView` for Rule Name (weight 1).
*   `TextView` for Duration (e.g., "25m").
*   Optional: `ImageView` for a "Play" icon.

**Step 3: Create Widget Metadata**
Create `app/src/main/res/xml/focus_quick_start_widget_info.xml`.
*   `minWidth`, `minHeight`.
*   `updatePeriodMillis` (e.g., 86400000 - daily, we update manually mostly).
*   `initialLayout`: `@layout/widget_focus_quick_start`.
*   `configure`: `li.songe.gkd.sdp.widget.FocusWidgetConfigActivity` (will be created later).

**Step 4: Create Widget Provider Stub**
Create `app/src/main/kotlin/li/songe/gkd/sdp/widget/FocusQuickStartWidget.kt`.
*   Extend `AppWidgetProvider`.
*   Override `onUpdate` (empty for now).

**Step 5: Update AndroidManifest**
*   Register `FocusQuickStartWidget` as a receiver.
    *   Intent filter: `android.appwidget.action.APPWIDGET_UPDATE`.
    *   Meta-data: `android.appwidget.provider` -> `@xml/focus_quick_start_widget_info`.

**Step 6: Build and Verify**
*   Run `./gradlew assembleDebug` to ensure resources are correct.

---

### Task 2: Implement Widget Configuration Activity

**Goal:** Allow users to select which "Quick Start" rules to show on the widget when they add it.

**Files:**
-   Create: `app/src/main/kotlin/li/songe/gkd/sdp/widget/FocusWidgetConfigActivity.kt`
-   Modify: `app/src/main/AndroidManifest.xml`

**Step 1: Create Config Activity**
Create `app/src/main/kotlin/li/songe/gkd/sdp/widget/FocusWidgetConfigActivity.kt`.
*   Extend `ComponentActivity`.
*   In `onCreate`:
    *   Get `appWidgetId` from Intent extras. If invalid, `finish()`.
    *   Set result to `RESULT_CANCELED` initially.
    *   Use `setContent` to render Compose UI.
*   **UI Logic:**
    *   Query `FocusRuleDao.queryAll()` filtering for `ruleType == RULE_TYPE_QUICK_START` and `enabled == true`.
    *   Display a list with Checkboxes.
    *   "Save" button.

**Step 2: Implement Save Logic**
*   On Save:
    *   Collect selected rule IDs.
    *   Save to `SharedPreferences` (name: `widget_prefs`). Key: `"widget_$appWidgetId"`, Value: Set of String IDs or JSON string.
    *   Trigger widget update: `AppWidgetManager.getInstance(context).updateAppWidget(appWidgetId, remoteViews)`.
    *   Set result `RESULT_OK`, return intent with `EXTRA_APPWIDGET_ID`.
    *   `finish()`.

**Step 3: Register in Manifest**
*   Add `<activity>` for `FocusWidgetConfigActivity`.
    *   Intent filter: `android.appwidget.action.APPWIDGET_CONFIGURE`.

---

### Task 3: Implement Widget Service and Data Loading

**Goal:** Populate the widget's ListView with the selected rules.

**Files:**
-   Create: `app/src/main/kotlin/li/songe/gkd/sdp/widget/FocusWidgetService.kt`
-   Modify: `app/src/main/kotlin/li/songe/gkd/sdp/widget/FocusQuickStartWidget.kt`
-   Modify: `app/src/main/AndroidManifest.xml`

**Step 1: Create RemoteViewsService and Factory**
Create `app/src/main/kotlin/li/songe/gkd/sdp/widget/FocusWidgetService.kt`.
*   Extend `RemoteViewsService`.
*   Implement `RemoteViewsFactory`.
    *   `onDataSetChanged()`: Read `appWidgetId` (passed via intent), read saved rule IDs from Prefs, query DB for these rules. **Note:** Cannot inject Dao easily in RemoteViewsFactory, might need to use `AppDb.getDatabase(context)` directly.

**Step 2: Implement getViewAt**
*   In `getViewAt(position)`:
    *   Get `FocusRule` object.
    *   Construct `RemoteViews` for `@layout/widget_focus_item`.
    *   Set text for name and duration (`rule.formatDuration()`).
    *   **Fill-in Intent:** Create a fill-in intent with `EXTRA_RULE_ID`. `views.setOnClickFillInIntent(R.id.item_root, fillInIntent)`.

**Step 3: Update Widget Provider**
*   In `FocusQuickStartWidget.onUpdate`:
    *   Loop through all `appWidgetIds`.
    *   Construct `RemoteViews` for `@layout/widget_focus_quick_start`.
    *   Set up adapter: `views.setRemoteAdapter(R.id.widget_list, intentPointingToService)`.
    *   Set pending intent template: `views.setPendingIntentTemplate(R.id.widget_list, pendingIntentForBroadcast)`.
    *   `appWidgetManager.updateAppWidget(id, views)`.

**Step 4: Register Service**
*   In `AndroidManifest.xml`, add `<service>` for `FocusWidgetService` with permission `android.permission.BIND_REMOTEVIEWS`.

---

### Task 4: Handle Click Actions (Start Focus)

**Goal:** Start the focus mode when a user clicks an item.

**Files:**
-   Modify: `app/src/main/kotlin/li/songe/gkd/sdp/widget/FocusQuickStartWidget.kt`
-   Create: `app/src/main/kotlin/li/songe/gkd/sdp/service/FocusStartReceiver.kt` (Or handle in WidgetProvider)

**Step 1: Define Action**
*   Const `ACTION_START_FOCUS = "li.songe.gkd.sdp.action.START_FOCUS"`.

**Step 2: Implement onReceive in WidgetProvider**
*   In `FocusQuickStartWidget`:
    *   Check for `ACTION_START_FOCUS`.
    *   Extract `ruleId`.
    *   Launch a coroutine (goAsync).
    *   Get `FocusRule` from DB.
    *   **Start Focus Logic:**
        *   Calculate `endTime`.
        *   Insert/Update `FocusSession`.
        *   Start `FocusOverlayService` / `FocusModeEngine`. (Check `FocusModeVm` for how it's done usually).
    *   Open Main Activity or Focus Page to show feedback.

**Step 3: Update PendingIntent Template**
*   Ensure `onUpdate` sets the correct `PendingIntent` template targeting `FocusQuickStartWidget` class with `ACTION_START_FOCUS`.

**Step 4: Final Polish**
*   Ensure `onDeleted` cleans up SharedPreferences.

---

### Task 5: Manual Testing & Verification

**Goal:** Verify the widget works on a device/emulator.

**Step 1: Build and Install**
*   `./gradlew installDebug`

**Step 2: Verify Flow**
*   Add Widget -> Config Screen appears -> Select Rules -> Widget appears with items.
*   Click Item -> App opens/Focus starts.
