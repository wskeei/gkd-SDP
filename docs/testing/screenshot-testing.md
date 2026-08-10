# Compose Screenshot Testing

The app uses `com.android.compose.screenshot:0.0.1-alpha15` with the
`screenshotTest` source set. Preview functions annotated with `@PreviewTest` are
rendered as reference images and validated on CI.

References live under `app/src/screenshotTestGkdDebug/reference`. Regenerate
them only in a deliberate visual-change commit:

```bash
./gradlew :app:updateGkdDebugScreenshotTest
```

CI only runs validation:

```bash
./gradlew :app:validateGkdDebugScreenshotTest
```

The current matrix covers compact/expanded widths, light/dark variants, font
scale 2.0, core overview states, self-control hub, settings, interception
source-missing state, usage request, and dense chart data. It does not claim
to reproduce Android/OEM rendering, `FLAG_SECURE`, or physical-device output.
