# Product Experience Test Matrix

The following matrix is the fixed PR 4 verification baseline for the product
information architecture and core UX.

## Layouts

- Compact: width < 600 dp, bottom `NavigationBar`, four top-level destinations.
- Medium: 600-839 dp, left `NavigationRail`.
- Expanded: >= 840 dp, left `NavigationRail` with bounded content.

## Content and Language

- Chinese and English resources for the four top-level destinations, capability
  center, settings search, privacy data page, usage-request form, and review.
- Light and dark themes.
- Font scales 1.0, 1.3, and 2.0 with no truncated controls or overlapping text.

## Data and States

- Privacy data page: empty, populated, active-session block, and deletion
  confirmation states.
- Review dashboard: empty, sparse, and dense rolling-window data.
- Usage request: no history, sparse history, dense 24-hour data, IME open, and
  duplicate-submit disabled state.
- Process recreation preserves tab, search, scroll, and form state where the
  feature explicitly persists it.

## Flavor Boundary

- `gkdDebug`: full capability center and accessibility-guard behavior.
- `playDebug`: shared pages compile and launch; gkd-only strong permission
  actions are not shown.
