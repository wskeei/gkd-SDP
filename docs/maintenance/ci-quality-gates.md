# CI Quality Gates

The `quality` job runs the Python contract suite, sensitive-output policy,
Compose lifecycle policy, UI boundary policy, test-quality policy, release
metadata contracts, security dependency audit, selector tests, app JVM tests,
and both debug lint tasks.

The `build` job assembles all four variants: `gkdDebug`, `playDebug`,
`gkdRelease`, and `playRelease`. Reports are uploaded with a seven-day
retention period.

The `coverage` job enforces the Kover business-policy thresholds, the
`visual-regression` job validates Compose screenshot references, and the
`managed-device-api26` / `managed-device-api35` jobs run the instrumentation
matrix on KVM-enabled emulators.

The `performance` job generates the release Baseline Profile, runs cold and
warm startup macrobenchmarks, assembles the release APK, and verifies the
performance report contract.
