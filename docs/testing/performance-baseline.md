# Performance Baseline

Performance thresholds live in `config/quality/performance-thresholds.json`:

- Cold startup median: <= 1200 ms.
- Cold startup P95: <= 2000 ms.
- Warm startup P95: <= 700 ms.
- Frame overrun P95: <= 16 ms.
- Release APK growth: <= 8% and <= 2 MiB versus `origin/main`.

Baseline Profile must exist at
`app/src/gkdRelease/generated/baselineProfiles/baseline-prof.txt` and be packaged
as `assets/dexopt/baseline.prof` plus `baseline.profm`.

The `baselineprofile` module uses the current AndroidX Benchmark plugin
(`1.5.0-beta01`) with AGP 9.3 and generates a Pixel 6 API 35 profile through a
Gradle Managed Device. The performance CI job generates the profile, runs cold
and warm startup macrobenchmarks, builds the release APK, and runs
`scripts/verify-performance-reports.py`.
