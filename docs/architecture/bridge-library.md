# KMP bridge library contract

This document defines the public boundary and distribution requirements for Weft's second deliverable: an Android/iOS native SDK bridge library. Choose a concrete SDK and stable API after a pilot.

## Goals

- Provide familiar Android feature semantics through a consistent Kotlin API.
- Hide Android/iOS SDK differences behind platform adapters.
- Let KMP consumers depend on common Kotlin contracts rather than raw native SDK types.
- Publish clear installation paths and support ranges for Android/KMP and SwiftPM/Xcode consumers.

## API compatibility promise

“Align with the Android API” means:

- Existing Android behavior, result meaning, failure cases, defaults, and naming vocabulary may be the compatibility reference.
- The public common API uses KMP-friendly Kotlin types such as suspend functions, Flow, and data models.
- Do not expose Android-only Context, Activity, Parcelable, lifecycle, or Uri types in common APIs.
- Convert callbacks and listeners into Kotlin contracts with documented cancellation, repeated-event, and threading semantics.
- Keep iOS-only permission, presentation, and URL handling in an injected app boundary or platform adapter.
- Do not hide platform behavior differences behind identical names; expose capabilities or document unsupported behavior.

This library does not copy an Android interface unchanged into common Kotlin. It preserves familiar behavior and result semantics while keeping Android types behind the platform boundary.

## Initial module shape

After selecting a real SDK, validate the smallest useful structure:

~~~text
<capability>/
  src/commonMain/       # public API, neutral models, shared validation
  src/androidMain/      # Android SDK adapter
  src/iosMain/          # Kotlin/Native adapter to Apple façade
  src/commonTest/       # platform-independent API behavior
  iosInterop/<Facade>/  # SwiftPM package with narrow Objective-C-visible façade
~~~

Connect the common API with expect/actual or an injected platform provider when appropriate. Choose based on SDK lifecycle and testability. Do not start with a generic provider framework or combine multiple native SDKs in one artifact.

## Distribution units

### Maven Central

Publish each capability as an independent KMP Gradle artifact for Android and KMP Gradle consumers. The initial `GROUP` is `io.github.run-spot.weft`; verify ownership of the parent `io.github.run-spot` namespace in Central Portal before the first release. The repository is licensed under Apache-2.0; confirm any SDK dependency's redistribution terms before including or bundling it.

The build conventions, module coordinates, metadata, signing hooks, and staged release workflow are described in [Bridge library modules](library-modules.md) and [Maven Central publishing](maven-central-publishing.md). Nothing has been published yet.

### SwiftPM/Xcode

SwiftPM consumers need a separate package and XCFramework binary distribution. Maven Central KMP publications are for Gradle consumers and do not provide a SwiftPM package or XCFramework. Manage the public artifact URL, version tag, checksum, supported Apple architectures, and minimum deployment target in that separate release path.

Kotlin currently labels SwiftPM import into KMP modules as Alpha and warns that exporting such a module itself as a Swift package may not work. Dual distribution is a design goal, not a completed implementation. Validate Swift package export, transitive SDK linking, archive creation, and checksum updates in an isolated prototype before promising a stable artifact contract.

## Pilot acceptance criteria

- The Android and iOS SDKs support the same user-facing scenario.
- The common API exposes no Android or Apple SDK types.
- Success, cancellation, error, permission-denied, and missing-capability paths are checked.
- Android unit/instrumentation checks and iOS simulator/device builds verify the adapters.
- A separate Gradle consumer resolves the local Maven publication.
- A SwiftPM consumer sample resolves the XCFramework and completes an archive build.
- Supported version tuples, iOS deployment target, Android minSdk, and API stability policy are documented.

## References

- [Import Swift package dependencies into KMP modules](https://kotlinlang.org/docs/multiplatform/multiplatform-spm-import.html)
- [Export Kotlin frameworks as Swift packages](https://kotlinlang.org/docs/multiplatform/native-spm.html)
- [Publish KMP libraries to Maven Central](https://kotlinlang.org/docs/multiplatform/multiplatform-publish-libraries-to-maven.html)
- [Apple XCFramework SwiftPM distribution](https://developer.apple.com/documentation/xcode/distributing-binary-frameworks-as-swift-packages)
