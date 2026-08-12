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

The checked-in matrix renders production composables or production `*Content`
surfaces with deterministic fixtures: overview, self-control hub, rules hub with
production empty-state content, settings search, privacy data and delete
confirmation, capability center, interception source, usage request form and
rhythm feedback, review dashboard, and chart/state components. It includes
compact/expanded widths, dark mode, English locale, and large font previews.
The current 28 references cover the review's target pages and required states;
it does not claim to reproduce Android/OEM rendering, `FLAG_SECURE`, or
physical-device output.
