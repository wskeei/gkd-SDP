# Plan: Restore and Improve Browser Adapter Configuration

## User Objective
1.  **Restore**: The "Browser Adapter" configuration (view/add supported browsers) which was lost during the redesign. It allowed viewing ~9 built-in browsers and adding custom ones (Package Name + Node ID).
2.  **Improve**: Add a feature to "Select from Installed Apps" when adding a custom browser, reducing the friction of manually typing package names.

## Analysis
*   **Current State**: `UrlBlockVm.kt` still has `browsersFlow` and CRUD logic (`saveBrowser`, `deleteBrowser`). The data layer (`BrowserConfig`) is intact. The UI entry point and components were removed from `UrlBlockPage.kt`.
*   **Missing UI**: List of browsers, Add/Edit forms.
*   **Improvement**: The "Package Name" input in the Add/Edit form needs an "Select App" button. This requires an App Picker similar to the one in `AppBlockerPage.kt`.

## Implementation Steps

### Step 1: Shared Component - `AppPickerDialog`
*   **Refactor**: `AppPickerDialog` is currently private inside `AppBlockerPage.kt`.
*   **Action**: Extract it to `app/src/main/kotlin/li/songe/gkd/sdp/ui/component/AppPickerDialog.kt`.
*   **Update**: Modify `AppBlockerPage.kt` to use the shared component.

### Step 2: UI Components - Browser Management
*   **Modify `UrlBlockerComponents.kt`**:
    *   Add `BrowserListSheet`: Displays list of browsers (Built-in vs User).
        *   Items show Name, Package Name.
        *   Built-in ones are read-only (or just toggleable?). The VM has `deleteBrowser` check `isBuiltin`.
    *   Add `BrowserEditSheet`: Form for Name, Package Name, Node ID.
        *   **Enhancement**: Add "Select App" button next to Package Name field.
        *   **Logic**: When "Select App" is clicked, show `AppPickerDialog`. When app selected, fill Package Name and Name (if empty).

### Step 3: ViewModel Updates
*   **Modify `UrlBlockVm.kt`**:
    *   Ensure `browsersFlow` is exposed (checked: yes).
    *   Ensure UI state variables exist: `showBrowserList`, `showBrowserEditor`, `editingBrowser`.
    *   Ensure CRUD methods exist (`saveBrowser`, `deleteBrowser`, `toggleBrowserEnabled`).

### Step 4: Main Page Integration
*   **Modify `UrlBlockPage.kt`**:
    *   **TopBar**: Add "Settings" (or specific "Browsers") icon/button.
    *   **Sheet Logic**: Handle showing `BrowserListSheet` and `BrowserEditSheet`.
    *   **Interaction**: Connect VM states and events.

## Detailed Tasks

1.  **Extract `AppPickerDialog`**: Move from `AppBlockerPage.kt` to `ui/component/AppPickerDialog.kt`.
2.  **Create Components**: Add `BrowserListSheet` and `BrowserEditSheet` to `UrlBlockerComponents.kt`.
3.  **Update ViewModel**: Verify/Add state in `UrlBlockVm.kt`.
4.  **Integrate**: Update `UrlBlockPage.kt` to show the button and sheets.

