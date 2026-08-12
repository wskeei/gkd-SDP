# Managed Device Matrix

The app uses Gradle Managed Devices for instrumentation coverage on API 26 and
API 35. Both devices use Google APIs system images and the `x86_64` ABI.

- `pixel2Api26`: Pixel 2, API 26, `gkdDebug` Activity/Compose smoke and
  migration tests.
- `pixel6Api35`: Pixel 6, API 35, `gkdDebug` Activity/Compose smoke and
  migration tests.
- `pixel6Api35PlayDebug`: Pixel 6, API 35, `playDebug` startup/navigation/settings
  Activity/Compose smoke tests without gkd-only strong permission actions.

Devices run with a single concurrent emulator, English system locale, and a
clean app data before each test. These tests currently cover Activity launch,
top-level navigation, settings search, capability center entry, privacy entry,
and self-control/review entry points; they do not yet exercise every page
form, persistence mutation, recreation, IME, or SAF flow. CI enables KVM
permissions before running GMD
and passes `swiftshader_indirect` GPU emulation. The suite does not claim real
Accessibility, Shizuku, `FLAG_SECURE`, or OEM behavior; those remain
physical-device checks.
