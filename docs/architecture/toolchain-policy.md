# Toolchain version policy

Do not copy version numbers without recording the Kotlin Gradle Plugin, Gradle, AGP, Xcode, and Compose Multiplatform tuple and its validation status.

## Support states

- **officially-supported:** The official Kotlin compatibility guide includes the tuple, and Weft CI has built it.
- **observed:** An external project or Weft example uses the version. Weft does not claim support.
- **candidate:** The official range or Weft CI validation is still being checked.
- **unsupported:** Documentation or builds show the tuple cannot be used.

Do not use these labels interchangeably. The ranges below were checked on 2026-11-26 and must be refreshed from official docs.

## AGP 9+

For AGP 9+, keep the Android application shell and KMP Android library target in separate modules. Use Google's com.android.kotlin.multiplatform.library plugin for the KMP Android target and keep the Android app module on com.android.application. Do not copy the old com.android.library plus KMP setup into a new profile.

The Kotlin compatibility guide lists Kotlin 2.4.20–2.4.21 with Gradle 7.6.3–9.7.0, AGP 8.5.2–9.3.1, and Xcode 26.4. Select an exact tuple within the published range, then require Weft Android and iOS CI before marking it officially-supported.

## Weft bridge-library baseline

The initial bridge-library build pins Kotlin 2.4.21, Gradle 9.7.0, and AGP 9.3.1, with Android compile SDK 37, min SDK 24, and JVM target 17. Compose 1.12 requires compileSdk 37, and AGP 9.3 supports API 37. Keep the tuple **candidate** until repository CI builds the Android and Apple targets and separate consumers validate the publications. The Kakao leaf is the first implemented provider; local checks are not a full platform compatibility claim.

## SwiftPM import compatibility gate

Kotlin's SwiftPM import guide labels the feature Alpha and currently documents KGP 2.5.0-RC. Weft's Kakao pilot nevertheless demonstrated a local import on the baseline tuple (KGP 2.4.21, Gradle 9.7.0, AGP 9.3.1, Xcode 26.3, KakaoMapsSDK-SPM 2.12.19): device and simulator framework compilation/linking and an Xcode simulator consumer build passed, and the built frameworks/consumer target report iOS 13.0. The Xcode fixture also verified the SDK resource bundle. This is **observed**, not officially-supported: the implementation overrides Kotlin 2.4's default iOS 15 minimum for this leaf, test sources compile but simulator runtime tests are unavailable on the Intel build host, and remote Maven-consumer plus Apple Silicon CI validation remain release gates. Keep SwiftPM-backed libraries as candidates until those gates pass.

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
- [Jetpack Compose compiler and dependency setup](https://developer.android.com/develop/ui/compose/setup-compose-dependencies-and-compiler)
- [Android Gradle Plugin 9.3 release notes](https://developer.android.com/build/releases/agp-9-3-0-release-notes)
- [SwiftPM dependencies in KMP modules](https://kotlinlang.org/docs/multiplatform/multiplatform-spm-import.html)
- [Android API and AGP compatibility](https://developer.android.com/studio/releases)
