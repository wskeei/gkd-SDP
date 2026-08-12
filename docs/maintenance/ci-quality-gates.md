# CI Quality Gates

The `quality` job runs the Python contract suite, sensitive-output policy,
Compose lifecycle policy, localization resource parity, UI boundary policy,
test-quality policy, release metadata contracts, security dependency audit,
selector tests, app JVM tests, the custom copy lint tests, and both debug lint
tasks.

The `build` job assembles all four variants: `gkdDebug`, `playDebug`,
`gkdRelease`, and `playRelease`. Reports are uploaded with a seven-day
retention period.

The `coverage` job reads `config/quality/kover-includes.txt` and
`config/quality/kover-excludes.txt`, enforces the Kover thresholds, and runs
`verify-kover-report.py` for per-class 0% coverage checks. The
`visual-regression` job validates Compose screenshot references, and the
`managed-device-api26` / `managed-device-api35` jobs run the instrumentation
matrix on KVM-enabled emulators.

The `performance` job generates the release Baseline Profile, runs cold and
warm startup macrobenchmarks, assembles the release APK, and verifies the
startup/frame thresholds, release APK growth, baseline profile assets, and
Compose stability against the checked-in baseline.
