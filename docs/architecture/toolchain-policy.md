# Toolchain version policy

Do not copy version numbers without recording the Kotlin Gradle Plugin, Gradle, AGP, Xcode, and Compose Multiplatform tuple and its validation status.

## Support states

- **officially-supported:** The official Kotlin compatibility guide includes the tuple, and Weft CI has built it.
- **observed:** A case-study project uses the version. Weft does not claim support.
- **candidate:** The official range or Weft CI validation is still being checked.
- **unsupported:** Documentation or builds show the tuple cannot be used.

Do not use these labels interchangeably. The ranges below were checked on 2026-11-24 and must be refreshed from official docs.

## AGP 9+

For AGP 9+, keep the Android application shell and KMP Android library target in separate modules. Use Google's com.android.kotlin.multiplatform.library plugin for the KMP Android target and keep the Android app module on com.android.application. Do not copy the old com.android.library plus KMP setup into a new profile.

The Kotlin compatibility guide lists Kotlin 2.4.20–2.4.21 with Gradle 7.6.3–9.7.0, AGP 8.5.2–9.3.1, and Xcode 26.4. Select an exact tuple within the published range, then require Weft Android and iOS CI before marking it officially-supported.

## RunTheSpot observed tuple

The fetched RunTheSpot develop snapshot uses Kotlin 2.4.21, AGP 9.3.2, and Compose Multiplatform 1.12.2. Record this as an observed production case, not as a Weft supported tuple: AGP 9.3.2 is outside the AGP 9.3.1 upper bound in the reviewed Kotlin compatibility table. Revisit this classification when the official table changes and the tuple has reproducible builds.

## Weft bridge-library baseline

The initial bridge-library build pins Kotlin 2.4.21, Gradle 9.7.0, and AGP 9.3.1, with Android compile SDK 36, min SDK 24, and JVM target 17. This tuple is within the reviewed Kotlin compatibility range, and API 36 is supported by this AGP line. Keep it **candidate** until repository CI builds the Android and Apple targets and the modules contain real consumer-facing APIs. The local POM and Gradle configuration checks are not a full platform compatibility claim.

## Version update procedure

1. Check the Kotlin KMP compatibility guide for KGP, Gradle, AGP, and Xcode ranges.
2. Check Android's KMP library plugin documentation.
3. Check Compose Multiplatform compatibility for the selected Kotlin version.
4. Build both CMP and KMP-core profiles on macOS CI.
5. Keep unverified combinations as candidates; do not promote them to defaults.
6. Record the review date, documentation URLs, exact version tuple, and CI run.

## Sources

- [Kotlin Multiplatform compatibility guide](https://kotlinlang.org/docs/multiplatform/multiplatform-compatibility-guide.html)
- [AGP 9 migration for multiplatform projects](https://kotlinlang.org/docs/multiplatform/multiplatform-project-agp-9-migration.html)
- [Android KMP library plugin](https://developer.android.com/kotlin/multiplatform/plugin)
- [Compose Multiplatform compatibility and releases](https://kotlinlang.org/docs/multiplatform/compose-compatibility-and-versioning.html)
- [Android API and AGP compatibility](https://developer.android.com/studio/releases)
