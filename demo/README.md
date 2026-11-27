# Weft Demo

The demo contains Android and iOS Kotlin consumer apps. Both build from this repository with a
Gradle composite build, so they exercise the library projects directly without publishing
snapshots or changing their Maven coordinates.

## What it exercises

- `weft-core`: creates the app runtime, provides it with `WeftApp`, resolves `MapPlugin` through
  `LocalWeft`, and displays plugin health-check descriptions.
- `weft-kakaomap`: lets you enter a Kakao native app key, installs `KakaoMap`, and renders a marker,
  polyline, polygon, and circle through `WeftMap` in the Android app. The iOS app compiles and runs
  the same consumer code and links the transitive SwiftPM import.
- `weft-login` and `weft-push`: included as consumer dependencies. Both modules are still M0 build
  and publication scaffolds with no public capability API, so their pages say that there is no
  runtime feature to exercise yet.
- `weft-bom` and `weft-gradle-plugin`: build-time artifacts rather than app capabilities; the demo
  does not present them as runtime features.

The native app key is held in memory only. No key is committed or written to disk. Add the demo's
Android package ID (`run.thespot.weft.demo.android`) to the Kakao app configuration before opening
the map page on an Android device.

## Android

Open `demo/` as a Gradle project in Android Studio, or run from the repository root:

```sh
./gradlew -p demo :androidApp:installDebug
```

This uses the Android SDK installed for the checkout. Select a connected device or emulator before
installing the app.

## iOS app

The iOS app, app delegate, and shared UI are Kotlin. There is no Swift source or Xcode project. The
Kotlin Gradle Plugin generates SwiftPM manifests and Objective-C module wrappers as build inputs for
the Kakao SDK import; those generated files are not maintained by the demo.

From the repository root, package the app for the iOS simulator:

```sh
./gradlew -p demo :iosApp:packageIosSimulatorApp
```

The app bundle is written to `demo/iosApp/build/app/iosSimulatorArm64/WeftDemo.app`. Install it with
`xcrun simctl install <device-udid> <app-path>` and launch it with
`xcrun simctl launch <device-udid> run.thespot.weft.demo.ios`.

## Current limits

This demo validates a local composite consumer, not a remotely published Maven consumer. Kotlin's
SwiftPM import integration is Alpha. The official Kotlin guide currently documents KGP 2.5.0-RC as
the trial version; this repository pins KGP 2.4.21 and has a local Kakao SDK pilot on that tuple.
Treat the iOS simulator build here as evidence for this exact local setup, not as general Kotlin
support or a release guarantee. Device packaging and distribution are outside this demo's current
scope.

The demo inherits Kotlin 2.4.21, AGP 9.3.1, Gradle 9.7.0, and Compose Multiplatform 1.12.1 from the
repository. Its Android entry uses `androidx.activity:activity:1.13.0`, the stable version listed
in the AndroidX release notes. Toolchain and dependency references were reviewed on 2026-11-26:

- [Kotlin SwiftPM import guide](https://kotlinlang.org/docs/multiplatform/multiplatform-spm-import.html)
- [Android built-in Kotlin migration guide](https://developer.android.com/build/migrate-to-built-in-kotlin)
- [AndroidX Activity release notes](https://developer.android.com/jetpack/androidx/releases/activity)
