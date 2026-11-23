# Weft Bridge Library Modules

The bridge-library workspace is a multi-project Gradle build inside the Weft repository. Each capability is an independent Kotlin Multiplatform publication with its own API, dependencies, and release readiness.

## Initial modules

| Gradle path | Artifact ID | Responsibility | Current milestone |
| --- | --- | --- | --- |
| `:map` | `map` | Map capability contracts and platform adapters | M0: build and publication skeleton |
| `:push` | `push` | Push registration and notification capability | M0: build and publication skeleton |
| `:login` | `login` | Cross-platform sign-in capability | M0: build and publication skeleton |

The initial Maven group property is `io.github.run-spot.weft`, and the development version is `0.1.0-SNAPSHOT`. The parent `io.github.run-spot` namespace must be verified in Central Portal before the first release. The module skeletons intentionally contain no public API or vendor SDK; they are not release candidates.

## Source ownership

Each module uses the standard KMP source-set boundaries:

~~~text
<module>/
  src/commonMain/kotlin/      # public API and platform-neutral models
  src/commonTest/kotlin/      # shared API behavior checks
  src/androidMain/kotlin/     # Android SDK adapter
  src/androidHostTest/kotlin/ # Android host-side checks when needed
  src/iosMain/kotlin/         # Kotlin/Native adapter and Apple interop
~~~

Only create a source-set implementation when a module milestone needs it. Keep Android and Apple SDK dependencies in their platform source sets. Do not add a cross-module `:core` or umbrella artifact until real shared API ownership requires it.

## Platform targets

The common convention currently declares Android, iOS device (`iosArm64`), Apple Silicon simulator (`iosSimulatorArm64`), and Intel simulator (`iosX64`) targets. Android uses compile SDK 37, min SDK 24, and JVM bytecode target 17. These are initial build defaults, not yet the minimum OS support promise of any released artifact.

The official Kotlin compatibility guide lists Kotlin 2.4.21 with Gradle 7.6.3–9.7.0 and AGP 8.5.2–9.3.1. The repository pins the upper compatible versions for this baseline. Recheck the guide before changing the tuple, and record CI results separately from official compatibility ranges.

## Distribution boundary

Maven Central receives KMP metadata and target publications consumed by Gradle. This setup does not create an XCFramework or a SwiftPM package. SwiftPM distribution remains a distinct milestone with separate package metadata, binary hosting, checksum, and Apple archive validation.

## References

- [Kotlin Multiplatform compatibility guide](https://kotlinlang.org/docs/multiplatform/multiplatform-compatibility-guide.html)
- [Android KMP library plugin](https://developer.android.com/kotlin/multiplatform/plugin)
- [KMP publication setup](https://kotlinlang.org/docs/multiplatform/multiplatform-publish-lib-setup.html)
