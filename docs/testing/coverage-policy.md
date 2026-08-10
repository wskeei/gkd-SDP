# Coverage Policy

Kover coverage is enforced only for `gkdDebug` JVM tests and the business-layer
include list in `config/quality/kover-includes.txt`.

- Line coverage minimum: 80%.
- Branch coverage minimum: 70%.
- New included classes must not report 0% coverage.
- Compose, Android service/activity/receiver/widget hosts, Room/KSP generated
  classes, icons, resources, and test fakes are excluded.

Reports are generated as XML and HTML and uploaded by CI with a seven-day
retention period. Coverage must not be improved by expanding excludes, deleting
tests, or marking production code generated.
