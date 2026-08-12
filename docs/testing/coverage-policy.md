# Coverage Policy

Kover coverage is enforced only for `gkdDebug` JVM tests. The include and
exclude lists are read from `config/quality/kover-includes.txt` and
`config/quality/kover-excludes.txt`; `app/build.gradle.kts` does not carry a
second hardcoded list.

- Line coverage minimum: 80%.
- Branch coverage minimum: 70%.
- New included top-level classes must not report 0% coverage.
  `verify-kover-report.py` checks this from the XML report and fails when an
  include pattern matches no class or a broad UI exclusion would hide required
  classes. Kotlin-generated nested lambdas and file facades are not counted as
  separate top-level classes.
- Compose host implementations, generated classes, icons, resources, and
  explicit preview facades are excluded only when listed in
  `kover-excludes.txt`.
- `DatabaseDataInventorySource` is excluded as a narrow Android Room adapter
  boundary; the inventory/deletion policy it serves is covered through the
  injectable `DataInventorySource` fake in unit tests. Do not use this entry
  as a template for excluding business repositories or policies.

Reports are generated as XML and HTML and uploaded by CI with a seven-day
retention period. Coverage must not be improved by expanding excludes, deleting
tests, or marking production code generated.
