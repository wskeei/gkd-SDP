# 2026-08-11 Review Fix Status

This file tracks the findings in
`docs/2026-08-11-product-quality-experience-infrastructure-release-review.md`.
It is not a release gate; it records current implementation evidence.

| Finding | Status | Current evidence |
| --- | --- | --- |
| P1-1 Image preview transport | Complete | HTTPS-only policy, shared controlled client, interceptor, redirect/origin tests, and security doc added; JVM tests pass. |
| P1-2 Performance gates | Complete | Verifier computes missing P95 from real runs; Baseline Profile generated on API 35, cold/warm macrobenchmarks ran, current and baseline release APKs compared, `baseline.prof`/`baseline.profm` checked, Compose report compared against the fixed stability baseline; `verify-performance-reports.py` returns OK. Startup-dex partitioning is disabled so the APK size gate compares like-for-like with the release baseline. CI job still needs its own green run. |
| P1-3 Managed device UI flows | Complete | Test-quality policy rejects placeholders; real Activity/Compose flows cover navigation, settings search, capability, privacy/backup, self-control and recreation; API 26 gkd, API 35 gkd, and API 35 play GMD all pass after a clean build. |
| P1-4 Screenshot matrix | Complete | 28 checked-in references render production composables or `*Content` surfaces with deterministic fixtures: overview, self-control, rules, settings, capability center, usage request, interception, review, privacy, chart and delete confirmation; update and validate pass after a clean build. Stale placeholder references removed. |
| P1-5 Localization lint | Complete | Lint registry is active, Gkd/Play lint report zero `HardcodedText`, resource key/format parity passes, and `verify-localization-sources.py` requires `// i18n-ignore` for remaining non-UI CJK literals. Screenshot tests now use current resource keys and pass in both locales/theme fixtures. |
| P1-6 Capability route | Complete | Semantic/legacy links and notification repair route to capability center; JVM tests updated. |
| P1-7 Privacy data controls | Complete | Real inventory counts/sizes, delete/reset paths, config resets, delete-all, backup entry, cleartext origin revocation, support whitelist and danger zone are wired; repository and coordinator tests cover active blocking, delete-all walking, diagnostics cleanup and backup recovery. |
| P2-1 Settings search | Complete | Recent items, route/anchor targets, highlight, recreation-friendly state and real GMD click/input coverage pass. |
| P2-2 Kover scope | Complete | Single config, 0% class check, `koverVerifyGkdDebug` and `verify-kover-report.py` pass with line 83.94% / branch 70.16%. |
| P2-3 UI architecture | Complete | Every page/overlay module has immutable UiState, sealed UiAction, and a non-identity Presenter; `verify-ui-file-boundaries.py` recursively scans page/overlay hosts and rejects UiState holding ViewModel/mutable render context. Presenter reduce contracts have JVM tests. |
| P2-4 Adaptive navigation | Complete | Vertical divider, weighted content, and destination-scoped reset implemented with policy tests. |
| P2-5 A11y Guard mode | Complete | Flavor/mode matrix implemented and tested. |
| P2-6 Release evidence | Pending | No new tag/release; release PR and full gates remain. |

## Verification run so far

- Python contract suite: 79 tests pass.
- Release tooling contracts, update-manifest generation, sensitive-output policy, Compose-lifecycle policy, test-quality policy, and security dependency audit pass locally.
- `:selector:jvmTest`, `:app:testGkdDebugUnitTest`, `:quality-lint:test` pass after a clean build.
- `:app:lintGkdDebug`, `:app:lintPlayDebug` pass with zero `HardcodedText` reports.
- `:app:compileGkdDebugAndroidTestKotlin`, `:app:compilePlayDebugAndroidTestKotlin` pass.
- `:app:updateGkdDebugScreenshotTest`, `:app:validateGkdDebugScreenshotTest` pass after a clean build.
- `:app:koverVerifyGkdDebug` and `scripts/verify-kover-report.py` pass at line 83.94% / branch 70.16%.
- GMD: `pixel2Api26GkdDebugAndroidTest`, `pixel6Api35GkdDebugAndroidTest`, and `pixel6Api35PlayDebugAndroidTest` all pass after the final ActionLog/UsageGuard refactor and localization changes.
- Baseline Profile generation, startup macrobenchmarks, clean release/baseline APK builds, `scripts/verify-performance-reports.py`, and `:app:assemblePlayRelease` pass locally after the final changes.
- `git diff --check` passes.

## Not yet executed

- Release PR, independent review, tag, draft assets, and publication.
- CI runs for the review-fix branch are not yet available because the branch has not been pushed.
