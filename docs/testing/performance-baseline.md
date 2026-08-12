# Performance Baseline

Performance thresholds live in `config/quality/performance-thresholds.json`:

- Cold startup median: <= 3200 ms on the managed emulator.
- Cold startup P95: <= 3800 ms on the managed emulator.
- Warm startup P95: <= 2100 ms on the managed emulator.
- Frame overrun P95: <= 1300 ms on the managed emulator.
- Release APK growth: <= 8% and <= 2 MiB versus `origin/main`.
- Compose unstable class count and core stable types are compared against
  `config/quality/compose-stability-baseline.json`.

Baseline Profile must exist at
`app/src/gkdRelease/generated/baselineProfiles/baseline-prof.txt` and be packaged
as `assets/dexopt/baseline.prof` plus `baseline.profm`.
Startup-dex partitioning is disabled so the APK size gate compares like-for-like
with the baseline release; the regular baseline profile is still generated and
packaged.

The `baselineprofile` module uses the current AndroidX Benchmark plugin
(`1.5.0-beta01`) with AGP 9.3 and generates a Pixel 6 API 35 profile through a
Gradle Managed Device. The performance CI job generates the profile, runs cold
and warm startup macrobenchmarks, builds the release APK, and runs
`scripts/verify-performance-reports.py`.

The CI macrobenchmark runs on a managed emulator. Thresholds were calibrated
from a real local cold/warm run on the same API 35 emulator configuration and
are regression evidence for the automated pipeline, not physical-device
performance claims. `verify-performance-reports.py` fails if a
threshold is exceeded, a metric is missing or non-finite, the release APK is
missing a baseline profile asset, or a required Compose core type becomes
unstable.

Release builds call `reportFullyDrawn()` through an Activity-scoped
`AppDrawReporter` once the first Compose frame is produced. Debug builds install
StrictMode thread and VM policies that route typed violations to
`DiagnosticLogger`; release builds keep the no-op factory and install no extra
detection.
