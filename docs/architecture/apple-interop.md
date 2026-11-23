# Apple SDK bridges and distribution

“SwiftPM import” can describe three different integration directions. Name the direction in architecture docs and code reviews.

## Three directions

| Integration | Call direction | Meaning | Distribution artifact |
| --- | --- | --- | --- |
| SwiftPM import into KMP | Kotlin iosMain to SwiftPM package APIs | Kotlin/Native imports Clang/Objective-C modules from Swift packages | KMP framework plus Xcode SwiftPM linkage |
| KMP framework through SwiftPM | Swift code to KMP framework | Xcode consumes an XCFramework as a Swift package binary target | XCFramework ZIP, Package.swift, checksum |
| Swift implements a Kotlin contract | Kotlin to Swift implementation | Swift implements or injects an exported Kotlin contract | Kotlin/Native framework and Xcode integration |

These are not interchangeable features. Use the terms import, framework export, and reverse implementation explicitly.

## Recommended bridge boundary

~~~text
commonMain Kotlin contract
          ↑ implementation
iosMain Kotlin adapter
          ↓ Clang/Objective-C API import
SwiftPM package façade
          ↓
native Swift SDK
~~~

- commonMain owns shared meaning, result models, and cancellable suspend APIs.
- androidMain owns Android SDK implementations.
- iosMain translates between the Kotlin contract and the Apple SDK façade, including callbacks, errors, threading, and lifecycle.
- The SwiftPM façade exposes the smallest Objective-C-compatible API surface needed by Kotlin/Native.
- App entry points own app-specific policy such as URL callbacks, permission consent, and UI presentation.

The API may preserve familiar Android behavior and results. Do not expose Context, Activity, Parcelable, or Android lifecycle types through the common API. Document callback semantics, cancellation, errors, and main-thread requirements across platforms.

## SwiftPM import constraints

As of 2026-11-23, Kotlin's official documentation labels SwiftPM dependency import into KMP modules as Alpha. This path imports Clang/Objective-C APIs; do not assume all pure Swift APIs become visible to Kotlin. If the SDK exposes only Swift APIs, consider a minimal Objective-C façade.

The Kotlin guide also warns that exporting a KMP module that uses SwiftPM import as a Swift package is not yet supported and may not work. An integration that works inside an app does not prove that the same module can be redistributed as a bridge library.

Before integrating an SDK, validate:

1. The required API is visible through Clang/Objective-C.
2. The package provides binaries for simulator and device targets.
3. Dependency resolution and version pinning are reproducible in CI.
4. The Kotlin adapter preserves cancellation, failure, empty-result, and main-thread behavior.
5. Framework linking and archive builds work on the minimum supported Xcode.

## Maven Central and SwiftPM

KMP Gradle publications can be released to Maven Central for Android and KMP Gradle consumers.

SwiftPM consumers need a separate Swift package distribution. A common binary flow publishes an XCFramework ZIP, a Package.swift binary target, checksum, and version tag. Therefore a future bridge library may use one source repository but still needs separate Maven Central and SwiftPM release artifacts.

Do not describe a Maven Central artifact alone as satisfying an Xcode SwiftPM dependency. Validate the actual bridge library's transitive SDK linking and package export path before promising dual distribution.

## References

- [Import Swift packages into KMP modules](https://kotlinlang.org/docs/multiplatform/multiplatform-spm-import.html)
- [Export Kotlin frameworks as Swift packages](https://kotlinlang.org/docs/multiplatform/native-spm.html)
- [Export Kotlin APIs to Swift](https://kotlinlang.org/docs/native-swift-export.html)
- [Publish KMP libraries to Maven Central](https://kotlinlang.org/docs/multiplatform/multiplatform-publish-libraries-to-maven.html)
- [Distribute XCFramework binary targets with SwiftPM](https://developer.apple.com/documentation/xcode/distributing-binary-frameworks-as-swift-packages)
