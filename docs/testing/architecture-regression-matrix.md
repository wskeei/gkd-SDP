# Architecture regression matrix

This matrix is the minimum JVM/CI regression set for runtime architecture changes.
Device/OEM capture and permission behavior remains a post-release manual check.

| Boundary | Required cases | Automated evidence |
| --- | --- | --- |
| Runtime owner parity | Accessibility owner and Automation/Shizuku owner reach the same `SdpRuntimeFeatureCoordinator` decision path; owner replacement does not duplicate events | `SdpRuntimeFeatureCoordinatorTest`, `A11yRuleEngine` contract tests |
| Process recreation | `rememberNavBackStack` restores the complete stack and selected first-level tab; semantic external intents do not duplicate the current destination | `DeepLinkParserTest`, `NavigationRestoreTest` |
| Clock and dispatcher seams | interval windows, cooldowns, auto-reenable and review summaries use a fake clock; clock rollback and boundary timestamps remain deterministic | `SdpClockTest`, self-control policy tests |
| Overlay mount failure | accepted/start failure, duplicate start, invalid intent and `WindowManager.addView` failure do not write displayed history or cooldown; service lifecycle owner is destroyed | overlay launcher/window contract tests, `ServiceOverlayLifecycleOwnerTest` |
| Cancellation | request cancellation, countdown lease replacement, import rollback and queued mutation cancellation release no stale state or gate | usage-guard, backup and storage mutation unit suites |
| Flavor parity | `gkdDebug` and `playDebug` compile; the strict accessibility-only path remains isolated to the gkd flavor | CI compile/lint jobs |
| UI boundaries | feature directories contain the fixed Route/Screen/UiState/Presenter/Sections/Dialogs/Editor set; overlay directories contain the fixed host/window/screen/state/presenter set | `verify-ui-file-boundaries.py`, `test_ui_file_boundaries.py` |

Every runtime change also runs `git diff --check`, the complete Python contract
suite, `:selector:jvmTest`, and `:app:testGkdDebugUnitTest` before review.
