# Usage Guard UX Refinement Design

Date: 2026-04-09
Topic: 数字自律 / 使用申请页面与申请弹窗体验重构
Status: Proposed

## Goal

Refine the `使用申请` feature so that it feels denser, clearer, and more intentional while staying within Android conventions.

This refinement should:

- reduce the current long-form, stacked layout on the `使用申请` settings page
- make selected-app management visually lighter and easier to scan
- improve application-form ergonomics inside the request overlay
- make records browsable by date instead of endlessly listing recent items
- add missing feedback for saved settings
- include `使用申请总开关` in the existing `自动重开保护` recovery loop

## Scope

This is one spec and later one implementation plan, but the plan should be decomposed into multiple tasks.

The design covers six connected areas:

1. `UsageGuardPage` information architecture
2. selected-app / whitelist presentation
3. request overlay interaction design
4. record browsing by day
5. persistent duration-option configuration
6. auto re-enable protection linkage for `usageGuardEnabled`

## Confirmed Requirements

From the discussion:

- In `仅选中应用`, apps should no longer be shown as a long vertical list.
- `仅选中应用` should be grouped into two sections: `严格模式` and `普通模式`.
- Apps in those sections should be represented mainly by app icons.
- Mode changes should support both:
  - tapping an app and choosing the mode in a secondary UI
  - dragging an app into the other section
- `白名单列表` should become a horizontal icon-based list and still support add/remove/edit operations.
- `最近申请记录` should not remain a flat recent-history list.
- Opening the records view should show today’s records by default.
- The user should be able to switch to another date and see all records from that date.
- Saving `最少理由字数` should show a success message.
- `自动重开保护` should restore only the `使用申请总开关`, not other `UsageGuard` settings.
- In the request overlay, `添加标签` should be collapsed by default and only expand when needed.
- In the request overlay, the reason field should show the current character count in the lower-right area.
- The request overlay’s four quick duration choices should come from long-term settings stored on the `使用申请` page.
- Those duration options only need minute values, not custom labels.
- The overall design direction should stay inside Android norms, but feel more custom, layered, and intentional.
- Emotional tone should lean toward `约束感 / 提醒感 / 仪式感`, not a plain utility form.

## Non-Goals

This refinement does not add:

- recovery of `UsageGuard` sub-settings beyond the main enable switch
- record export, search, or analytics
- persistent custom positions for the countdown overlay
- a new multi-page navigation system for `UsageGuard`
- editable custom labels for duration options

## Recommended Approach

Use a `single page, restructured sections` approach for `UsageGuardPage`, and keep the request flow as an overlay service with denser, staged interactions.

Recommendation reasons:

- It keeps the feature inside the current navigation model.
- It solves the current clutter problem without introducing several new pages.
- It allows the plan to stay in one coordinated spec while still splitting implementation into multiple tasks.

Rejected alternatives:

- Split everything into multiple `UsageGuard` sub-pages: clearer IA, but too heavy for the current feature size.
- Apply only local UI tweaks without restructuring: lower implementation risk, but the page would still feel like a patched long form.

## Architecture

### Runtime ownership

The existing ownership model remains:

- `UsageGuardEngine` owns runtime protection decisions and request/timeout/countdown overlay transitions.
- `UsageGuardVm` owns settings-page state and persistence actions.
- `UsageGuardRequestOverlayService` owns the application-form overlay shown before entering a protected app.

### Data ownership

Current data sources remain in use:

- `SettingsStore` for global `UsageGuard` preferences
- `UsageGuardAppProfile` for per-app inclusion / mode state
- `UsageGuardTag` for tag library
- `UsageGuardRecord` for application history

This refinement likely requires extending `SettingsStore` with a new persistent field for the four fixed duration options.

Recommended shape:

- `usageGuardDurationOptionsMinutes: List<Int>`

Requirements for this setting:

- exactly 4 values
- each value is a positive integer minute count
- values are displayed in the request form as `${minutes}分钟`

No new Room table is required for duration options.

## Information Architecture

`UsageGuardPage` should be reorganized into four top-level sections in this order.

### 1. Protection Status

Purpose:

- show whether `使用申请` is enabled
- communicate that `自动重开保护` will restore the main switch if it gets disabled

Contents:

- main enable switch
- short explanatory text for auto-recovery behavior

This section should feel like a status card, not a generic form row.

### 2. Rules And Preferences

Purpose:

- centralize global behavior choices

Contents:

- scope mode
- default grant mode
- minimum reason length
- four fixed duration-option settings

Design intent:

- more card grouping and better hierarchy
- less repeated full-width form scaffolding
- a tighter, higher-signal layout than the current stacked cards

Behavior:

- tapping save for minimum reason length should show a success toast/snackbar
- editing duration options should update the request overlay’s quick choices after persistence

### 3. App Management

Purpose:

- manage which apps are controlled and how they are categorized

Behavior depends on scope mode.

#### Selected-only mode

Show two visually distinct sections:

- `严格模式`
- `普通模式`

Each section should display app icons in a compact grid-like or wrapped flow presentation, with app name available through accompanying text, tooltip, or dialog context.

Supported interactions:

- tap app icon -> open a small secondary UI to switch mode or remove app from the selected set
- drag app icon from one section into the other -> update grant mode directly
- add apps through the existing picker entry point

#### Global-with-whitelist mode

Show:

- a horizontal icon-based whitelist strip or wrapped icon row
- existing add/remove/edit affordances
- grant-mode overrides still available, but visually less dominant than today

The whitelist should feel lightweight and scannable, not like a vertical settings list.

### 4. Records

Purpose:

- make the record area useful over time instead of degrading into an ever-growing feed

Behavior:

- opening the record section should show records for today by default
- provide date filtering / date switching
- after selecting a date, show all records for that day

Recommended interaction:

- a date header or selector at the top of the record section
- default value = today
- record list below, limited to the chosen date

This can be implemented inline on the page or via a date-specific detail surface, but the primary entry state must still be “today”.

## Request Overlay Design

The request overlay should become more staged and less noisy.

### Tag selection

Current problem:

- the add-tag field is always visible, even when the preset/global tags are enough

Refined behavior:

- keep preset/global tag chips visible
- keep `添加标签` collapsed by default
- show an explicit affordance such as `没有合适的标签？添加标签`
- only after tapping that control should the input row expand

### Reason field

Refined behavior:

- show current character count in the lower-right area of the input field
- retain minimum-length guidance
- preserve validation errors

The count should help the user judge progress instead of guessing against the minimum.

### Duration selection

Current problem:

- `10 / 15 / 30 / 60` is hardcoded and too generic

Refined behavior:

- load the four quick options from `SettingsStore`
- render them as four selectable minute chips/cards
- keep the request interaction simple and ritualized

Recommendation:

- remove or de-emphasize free-form custom duration entry if the product goal is deliberate, constrained use
- if current custom entry is kept, it should be a secondary path, not the dominant path

Given the user’s stated goal, the preferred product direction is to emphasize the configured four options as the primary choice set.

## Auto Re-enable Protection Integration

The existing `自动重开保护` feature should also monitor `usageGuardEnabled`.

Required behavior:

- on each enforcement interval, if `usageGuardEnabled == false`, set it back to `true`
- do not overwrite:
  - scope mode
  - default grant mode
  - minimum reason length
  - fixed duration options
  - selected apps
  - whitelist apps

This is strictly a main-switch recovery behavior.

## Error Handling

### Settings validation

Duration options must reject invalid persisted or edited values.

Recommended normalization:

- enforce exactly 4 options
- clamp or reject non-positive values
- if persisted values are missing or invalid, fall back to a default safe set

### Drag-and-drop fallback

If drag interaction is unreliable on some devices or Compose containers, tap-to-edit mode switching must still fully support the feature.

Drag support is additive, not the sole path.

### Date browsing fallback

If there are no records for the selected date:

- show an empty-state message specific to that date
- do not collapse back to recent global history automatically

## Testing Scope

### Unit / contract tests

Add or extend tests for:

- duration-option normalization / defaults
- date grouping or date filtering for `UsageGuardRecord`
- app-mode grouping logic for selected apps
- auto re-enable restoring `usageGuardEnabled` without touching other `UsageGuard` settings

### Manual verification

Verify:

1. `仅选中应用` shows two sections: strict and resumable.
2. Apps can switch sections through tap flow.
3. Apps can switch sections through drag flow.
4. `白名单列表` is icon-based and lighter than the old vertical list.
5. Saving minimum reason length shows success feedback.
6. Today’s records show by default.
7. Switching the record date shows that day’s records only.
8. Add-tag UI is collapsed by default in the request overlay.
9. Reason field shows live character count.
10. Request duration choices match the four configured values from settings.
11. Auto re-enable restores the `使用申请` main switch after it is turned off.

## Design Summary

This refinement should make `使用申请` feel less like a debug/settings screen and more like a deliberate control surface:

- lighter scanning through icon-based grouping
- clearer structure through stronger sectioning
- more ritualized request flow
- better long-term record browsing
- stronger protection through auto-reenable linkage
