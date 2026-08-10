# CI Quality Gates

The `quality` job runs the Python contract suite, sensitive-output policy,
Compose lifecycle policy, UI boundary policy, test-quality policy, release
metadata contracts, security dependency audit, selector tests, app JVM tests,
and both debug lint tasks.

The `build` job assembles all four variants: `gkdDebug`, `playDebug`,
`gkdRelease`, and `playRelease`. Reports are uploaded with a seven-day
retention period.
