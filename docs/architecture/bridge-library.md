# KMP bridge library contract

This document defines the public boundary and distribution requirements for Weft's Android/iOS native SDK bridge libraries. Kakao Maps and Naver Maps are provider leaves over the provider-neutral map API in `:weft-core`, nested at `:weft-map:kakaomap` and `:weft-map:navermap`. Applications select exactly one map provider per `Weft` runtime.

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

Use the smallest useful structure for each provider:

~~~text
<capability>/<provider>/
  src/commonMain/       # public API, neutral models, shared validation
  src/androidMain/      # Android SDK adapter
  src/iosMain/          # Kotlin/Native adapter to Apple façade
  src/commonTest/       # platform-independent API behavior
  iosInterop/<Facade>/  # SwiftPM package with narrow Objective-C-visible façade
~~~

Connect the common API with expect/actual or an injected platform provider when appropriate. Choose based on SDK lifecycle and testability. Do not start with a generic provider framework or combine multiple native SDKs in one artifact. Keep the app dependency allowlist closed: apps consume approved Weft artifacts rather than depending directly on vendor SDKs.

## Distribution units

### Maven Central

Publish each capability/provider leaf as a separate KMP Gradle artifact for Android and KMP Gradle consumers. The group is `run.thespot.weft`, with default artifact IDs `weft-core`, `weft-kakaomap`, `weft-navermap`, `weft-login`, and `weft-push`; `:weft-map` is a Gradle namespace, not a publication. The `weft-bom` artifact aligns those five artifacts, and releases are coordinated. `:weft-live` remains a verified candidate whose BOM constraint and publication are enabled only by the explicit `weftLiveReleaseEnabled` gate after its external Apple consumer path is proven. `weft-core` carries the plugin installation and provider-neutral map contracts; it contains no vendor or platform types. The Kakao Android SDK is a runtime dependency of `weft-kakaomap`, so Android consumers must configure Kakao's Maven repository. The Naver Android SDK (`com.naver.maps:map-sdk:3.24.1`) is a runtime dependency of `weft-navermap`, so Naver consumers must configure `https://repository.map.naver.com/archive/maven`. Verify both provider POMs, the `run.thespot` namespace, and separate Android/iOS consumers before the first release. The repository is licensed under Apache-2.0; confirm any SDK dependency's redistribution terms before including or bundling it.

The build conventions, module coordinates, metadata, signing hooks, and staged release workflow are described in [Bridge library modules](library-modules.md) and [Maven Central publishing](maven-central-publishing.md). Nothing has been published yet.

### SwiftPM/Xcode

Use SwiftPM inside the KMP bridge build to import Objective-C/Clang-visible SDK APIs. The Kakao pilot imports KakaoMapsSDK-SPM 2.12.19 and publishes a Kotlin KMP adapter as `run.thespot.weft:weft-kakaomap`. The Naver leaf imports `NMapsMap` 3.24.1 from `https://github.com/navermaps/SPM-NMapsMap.git` and publishes `run.thespot.weft:weft-navermap` with a dynamic `WeftNaverMap` framework for iOS 13.0. The Naver Android Maven dependency and iOS SwiftPM package are platform-scoped; neither is exposed from `commonMain`. The local framework and Xcode consumer gate must link the generated dynamic framework with the official dynamic NMapsMap XCFramework before release. These providers do not bundle their Swift packages into Maven or replace SwiftPM resolution needed to build and link the iOS target.

If native Swift consumers need the Kotlin framework directly, that is a separate XCFramework/SwiftPM binary distribution with its own public artifact URL, version tag, checksum, supported Apple architectures, and minimum deployment target.

Kotlin labels SwiftPM import into KMP modules as Alpha and warns that exporting such a module itself as a Swift package may not work. The observed local Kakao pilot on KGP 2.4.21 is not official support: runtime simulator tests on this Intel host and a remote Maven consumer remain unvalidated. The Naver dynamic framework must additionally pass the separate iOS SwiftPM/Xcode consumer gate described above. Direct Swift consumers would need a separately published XCFramework with a versioned Package.swift, checksum, supported Apple architectures, and minimum deployment target.

### Live activity capability

`:weft-live` provides a provider-neutral `LiveActivity` snapshot and suspend lifecycle through
`WeftLive`. Android renders the snapshot as a tagged notification (`id` is the tag and notification
ID `0` is used consistently), with API 36 `ProgressStyle` promotion when available and a standard
notification fallback on older releases. Samsung Now Bar/Now Brief placement remains OEM policy;
the adapter uses only public notification APIs and does not promise private Samsung behavior.

The Apple adapter uses the local `WeftLiveKit` SwiftPM façade. ActivityKit owns the native activity
identity and content state, while the façade serializes native writes and exposes only
Objective-C-compatible callbacks to Kotlin. The façade also ships generic Lock Screen and Dynamic
Island views, but the app owns WidgetBundle registration, branding, the `NSSupportsLiveActivities`
Info.plist key, and deep-link handling. The package is source-checkout-only in this milestone and
is not a release-ready transitive Maven dependency.

## Pilot acceptance criteria

- The Android and iOS SDKs support the same user-facing scenario.
- The common API exposes no Android or Apple SDK types.
- Success, cancellation, error, permission-denied, and missing-capability paths are checked.
- Android host checks and iOS device/simulator framework builds verify the adapters; Apple Silicon CI must run simulator runtime tests.
- A separate Gradle consumer resolves the local Maven publication.
- Separate Android consumers resolve both provider publications with their vendor repositories; the Naver consumer resolves `com.naver.maps:map-sdk:3.24.1` from Naver's Maven repository.
- The Naver iOS consumer links the dynamic `WeftNaverMap` framework and official dynamic `NMapsMap` XCFramework on the supported iOS 13.0 targets.
- If direct Swift consumption is promised, a separate SwiftPM consumer sample resolves the XCFramework and completes an archive build.
- Supported version tuples, iOS deployment target, Android minSdk, and API stability policy are documented.

## References

- [Import Swift package dependencies into KMP modules](https://kotlinlang.org/docs/multiplatform/multiplatform-spm-import.html)
- [Export Kotlin frameworks as Swift packages](https://kotlinlang.org/docs/multiplatform/native-spm.html)
- [Publish KMP libraries to Maven Central](https://kotlinlang.org/docs/multiplatform/multiplatform-publish-libraries-to-maven.html)
- [Apple XCFramework SwiftPM distribution](https://developer.apple.com/documentation/xcode/distributing-binary-frameworks-as-swift-packages)
