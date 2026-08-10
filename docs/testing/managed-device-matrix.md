# Managed Device Matrix

The app uses Gradle Managed Devices for instrumentation coverage on API 26 and
API 35. Both devices use AOSP system images and the `x86_64` ABI.

- `pixel2Api26`: Pixel 2, API 26, `gkdDebug` full UI and migration tests.
- `pixel6Api35`: Pixel 6, API 35, `gkdDebug` full UI and migration tests.
- `pixel6Api35PlayDebug`: Pixel 6, API 35, `playDebug` startup/navigation/settings
  smoke tests without gkd-only strong permission actions.

Devices run with a single concurrent emulator, English system locale, and a
clean app data before each test. The suite does not claim real Accessibility,
Shizuku, `FLAG_SECURE`, or OEM behavior; those remain physical-device checks.
