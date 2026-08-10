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

The macro benchmark module is intentionally not part of this Draft because the
`androidx.baselineprofile` 1.4.1 producer plugin does not currently resolve the
AGP 9.3 `com.android.test` extension type. The repository keeps the threshold
contract and verification script so a compatible AGP/plugin combination can be
added without loosening the performance gate.
