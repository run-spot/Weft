# Weft Naver Map Provider Design

**Date:** 2026-11-26

**Status:** Scope approved; written-spec review pending before implementation

## Goal

Add Naver Maps as a second provider leaf for the provider-neutral Weft map contract. The new
`:weft-map:navermap` module will expose the same `MapPlugin` and `WeftMap` API as the existing
Kakao provider, while keeping Naver Android/iOS SDK types out of `:weft-core` and
`commonMain` contracts.

Applications choose one map provider per `Weft` runtime:

```kotlin
val weft = Weft {
    install(NaverMap) {
        ncpKeyId = "naver-cloud-client-id"
    }
}
```

No core registry change or multi-provider selection API is included in this milestone.

## Current context

- `:weft-core` owns `MapPlugin`, provider-neutral camera/marker/shape models, `WeftMap`, and
  `rememberMapContent`.
- `:weft-map` is a namespace project. `:weft-map:kakaomap` is the existing provider leaf and
  establishes the Android/iOS lifecycle, reconciliation, publication, and fixture patterns.
- Every provider installs into `MapPlugin::class`; the existing `Weft` registry intentionally
  rejects a second implementation of the same capability type.
- The repository pins Kotlin `2.4.21`, AGP `9.3.1`, Compose Multiplatform `1.12.1`, Android
  compile SDK `37`, and minimum Android SDK `24`.
- The baseline provider-neutral contract includes camera state, markers, polylines, polygons,
  circles, z-order, colors, visibility, and declarative Compose child content.
- The baseline worktree passed `./gradlew verify` and `./scripts/verify.sh` before this design
  was written. Kotlin/Native iOS runtime tests remain skipped on the host's deprecated
  `macos_x64` platform, as they are for Kakao.

## Scope

### In scope

1. Add `:weft-map:navermap` as a published Kotlin Multiplatform leaf with artifact
   `run.thespot.weft:weft-navermap`.
2. Add `NaverMapConfig` and `NaverMap` in `commonMain`, implementing the existing `MapPlugin`
   contract without changing `:weft-core`.
3. Integrate NAVER Maps Android SDK `3.24.1` from NAVER's Maven repository in `androidMain`.
4. Integrate NAVER Maps iOS SDK `3.24.1` through the official `SPM-NMapsMap` Swift package in
   `iosMain`.
5. Render the existing camera, marker, polyline, polygon, and circle models on Android and iOS.
6. Implement platform lifecycle handling, SDK key initialization, camera normalization, native
   overlay reconciliation, rollback on render failure, and teardown cleanup.
7. Add common, Android host, and iOS coordinator/reconciler tests following the Kakao provider
   test boundary.
8. Update Gradle settings, version catalog, root verification, BOM constraints, architecture
   docs, module guidance, and a separate consumer/publication validation fixture.

### Out of scope

- Changing the provider-neutral `:weft-core` API or enabling two map providers in one `Weft`
  runtime.
- Place search, geocoding, routing, clustering, indoor maps, user-location tracking, gestures,
  map-type controls, info windows, or provider-specific callbacks.
- Introducing a generic provider framework or sharing platform adapter internals between Kakao and
  Naver before a third provider proves the need.
- Exposing Android, UIKit, Naver, or Swift package types through `commonMain`.
- Publishing to Maven Central or configuring credentials.

## Public API

The provider leaf owns only provider configuration and the plugin implementation. Its public
configuration uses the current NAVER Cloud Platform key terminology shared by both native SDKs:

```kotlin
/** Configuration for the NAVER Maps native SDK. */
public class NaverMapConfig {
    /** NAVER Cloud Platform key ID registered for the Android package/iOS bundle ID. */
    public var ncpKeyId: String = ""
}

/** NAVER Maps provider for the shared Weft map capability. */
public object NaverMap : WeftPlugin<NaverMapConfig, MapPlugin> {
    override val type: KClass<MapPlugin> = MapPlugin::class
    override val pluginName: String = "NaverMap"

    override fun prepare(block: NaverMapConfig.() -> Unit): MapPlugin {
        val config = NaverMapConfig().apply(block)
        require(config.ncpKeyId.isNotBlank()) {
            "NAVER Maps NCP key ID must not be blank."
        }
        return NaverMapPlugin(config.ncpKeyId)
    }

    override fun install(plugin: MapPlugin, scope: Weft): Unit = Unit
    override fun healthCheck(plugin: MapPlugin): List<WeftHealthCheck> = emptyList()
}
```

`NaverMapPlugin.Content` calls the existing `rememberMapContent(content)` and forwards the
immutable snapshot to an `expect/actual` platform surface. It does not add a second map DSL or
provider-specific model.

## Module and build layout

Create the following leaf structure, matching `:weft-map:kakaomap`:

```text
weft-map/navermap/
  build.gradle.kts
  src/commonMain/kotlin/run/thespot/weft/map/navermap/
    NaverMap.kt
    NaverMapException.kt
    NaverMapOverlayShapes.kt
    NaverMapHostTeardown.kt
  src/commonTest/kotlin/run/thespot/weft/map/navermap/
    NaverMapTest.kt
    NaverMapOverlayShapesTest.kt
  src/androidMain/kotlin/run/thespot/weft/map/navermap/
    NaverMapPlatform.android.kt
    NaverMapAndroidCoordinator.kt
    NaverMapAndroidMapViewHost.kt
    NaverMapAndroidOverlayReconciler.kt
    NaverMapAndroidSdkInitializer.kt
    NaverMapAndroidOverlayStyle.kt
  src/androidHostTest/kotlin/run/thespot/weft/map/navermap/
    NaverMapAndroidCoordinatorTest.kt
    NaverMapAndroidOverlayReconcilerTest.kt
  src/iosMain/kotlin/run/thespot/weft/map/navermap/
    NaverMapPlatform.ios.kt
    NaverMapIosCoordinator.kt
    NaverMapIosHost.kt
    NaverMapIosOverlayReconciler.kt
  src/iosTest/kotlin/run/thespot/weft/map/navermap/
    NaverMapIosCoordinatorTest.kt
    NaverMapIosOverlayReconcilerTest.kt
```

`NaverMap.kt` contains both the public `NaverMapConfig`/`NaverMap` declarations and the private
`NaverMapPlugin` implementation. The private plugin is deliberately kept beside the public
provider entry point, matching the existing Kakao leaf.

Build integration changes:

- `settings.gradle.kts`: include `:weft-map:navermap` and add
  `https://repository.map.naver.com/archive/maven` to dependency resolution repositories.
- `gradle/libs.versions.toml`: add the `com.naver.maps:map-sdk:3.24.1` alias.
- `weft-map/navermap/build.gradle.kts`: apply the existing KMP, Compose, and Maven publication
  conventions; depend on `:weft-core` from `commonMain`; use the Android SDK only from
  `androidMain`; import the exact `SPM-NMapsMap` `3.24.1` package/product only for iOS.
- `build.gradle.kts`: add the leaf to library verification and coordinated release task lists;
  extend namespace verification to `{ "kakaomap", "navermap" }`; add
  `verifyNaverMapPublication` to assert that the Android POM declares
  `com.naver.maps:map-sdk`. Update the `gradle.taskGraph.whenReady` assertions so root
  verification and coordinated release staging explicitly include
  `:weft-map:navermap:check` and `:weft-map:navermap:publishToMavenCentral`.
- `weft-bom/build.gradle.kts` and `weft-bom/AGENTS.md`: constrain and document
  `weft-navermap` alongside the existing published leaves.
- `AGENTS.md`, `weft-map/AGENTS.md`, `docs/architecture/library-modules.md`, and the root README:
  document the second provider, the one-provider-per-runtime rule, and the verified SDK versions.
- Add a consumer fixture that resolves `run.thespot.weft:weft-navermap` through the BOM from a
  separate Android project. Add an iOS SwiftPM/Xcode fixture and treat its framework/resource
  build as a hard completion gate. If the KMP SwiftPM linkage task cannot produce a runnable
  consumer on the pinned toolchain, record the exact limitation and leave the feature explicitly
  blocked rather than claiming completion.

## Android adapter

Use `com.naver.maps.map.MapView` inside `AndroidView`, not a Fragment, so the provider surface
fits the existing Compose adapter boundary. The surface owns one coordinator and a saved-state /
component-callback bridge:

- `factory` creates the `MapView`, calls `onCreate` exactly once with a restored or empty
  `Bundle`, and registers a `SavedStateRegistry.SavedStateProvider` whose callback invokes
  `onSaveInstanceState` into the registry bundle.
- A `LifecycleEventObserver` forwards `ON_START`, `ON_RESUME`, `ON_PAUSE`, `ON_STOP`, and
  `ON_DESTROY` to the corresponding `MapView` methods. `onDestroy` is terminal and unregisters
  the saved-state provider.
- A `ComponentCallbacks2` registered on the owning context forwards `onLowMemory` and is
  unregistered during disposal. This is separate from the lifecycle observer because
  `LifecycleEventObserver` cannot deliver saved-state or memory-pressure callbacks.

The coordinator must not start or render before the view is attached, resumed, and
`getMapAsync` has delivered a `NaverMap` instance.

Before the first native view is created, initialize the SDK using
`NaverMapSdk.getInstance(context).setClient(NaverMapSdk.NcpKeyClient(ncpKeyId))`. A process-local
initializer registry mirrors the Kakao adapter:

- the same key is initialized once and reused;
- a different key reports one `NaverMapException` through `onError` and does not reinitialize the
  global SDK;
- reset hooks are internal test-only helpers and never part of the public API.

The Android host translates provider-neutral data as follows:

- `LatLng` -> `com.naver.maps.geometry.LatLng`.
- `CameraPosition` -> Naver `CameraPosition`, with bearing normalized to `[0, 360)` and zoom/
  tilt clamped to the native map's current bounds before `moveCamera`.
- `MapMarker` -> `com.naver.maps.map.overlay.Marker`; `id` is retained in the reconciler,
  `position` maps to native position, `title` maps to caption text, and `snippet` maps to the
  secondary caption text. Marker visibility and z-order are updated without replacing an
  unchanged native object.
- `MapPolyline` -> `PolylineOverlay`; `MapPolygon` -> `PolygonOverlay`; `MapCircle` ->
  `CircleOverlay`. Geometry and style changes update the existing object when possible, while
  ID removal detaches it by setting its map to `null`.
- `Color` preserves alpha and RGB. Android stroke widths convert from dp to pixels with the
  host density. Provider z-order is the rounded common `zIndex` mapped to Naver's integer global
  z-order field; the adapter keeps the mapping deterministic for all overlay kinds.
- `visible = false` updates native visibility rather than removing the logical child.

The coordinator applies a complete snapshot transactionally: render markers and shapes, move the
camera, then commit the snapshot. If any native operation throws, it restores the last committed
marker/shape snapshot and camera when possible, attaches rollback failures as suppressed
exceptions, and sends the original failure through `onError` once per surface failure. It must not
advance its committed snapshot after a failed render.

## iOS adapter

Use the official NAVER Swift package:

```kotlin
swiftPackage(
    url = url("https://github.com/navermaps/SPM-NMapsMap.git"),
    version = exact("3.24.1"),
    products = listOf(product("NMapsMap", platforms = setOf(iOS()))),
)
```

The package is an Objective-C-visible wrapper around NAVER's dynamic `xcframework`, so the Kotlin
`iosMain` adapter imports its generated Clang module through the existing SwiftPM import path. The
adapter uses `NMFMapView` in `UIKitView`; `NMFNaverMapView` and optional built-in controls are
out of scope for the first leaf.

The iOS host configures `NMFAuthManager.shared().client` with `NMFNcpKeyClient(ncpKeyId)` before
creating the map view. It owns the map view, camera coordinator, overlay objects, and delegate
lifecycle. The conversion rules mirror Android:

- `LatLng` -> `NMGLatLng`.
- `CameraPosition` -> `NMFCameraPosition`/`NMFCameraUpdate`, normalizing bearing and clamping
  tilt/zoom to the native map bounds.
- `MapMarker` -> `NMFMarker` with caption/subcaption text, position, visibility, and stable
  reconciler ID.
- `MapPolyline`, `MapPolygon`, and `MapCircle` -> their corresponding Naver overlay classes,
  preserving geometry, fill/stroke colors including alpha, stroke width, visibility, and z-order.
- Every overlay is detached and released during teardown. Delegate callbacks are removed before
  the UIKit view is released.

The iOS adapter must keep SDK authentication and render failures inside `NaverMapException` (or
its cause) and call `onError` only while the surface is alive. The `UIKitView` host follows the
Kakao lifecycle bridge: `didMoveToWindow` marks attach/detach, `layoutSubviews` forwards current
width and height, and zero-sized bounds delay SDK preparation until a non-zero layout arrives.
Detach pauses a prepared map; reset removes delegates and native overlays. If SwiftPM import,
framework linking, resource delivery, or an Xcode consumer build fails on the pinned Kotlin/Xcode
host, the implementation must stop at that verified boundary and document the blocker; it must
not claim that a passing Kotlin compile alone proves iOS support.

## Shared reconciliation and lifecycle rules

Use the same invariants established by the Kakao implementation without importing Kakao code:

1. Normalize `MapContent` into provider-neutral internal overlay records with stable IDs and a
   native-kind prefix (`weft:marker:<id>`, `weft:polyline:<id>`, etc.).
2. Reconcile additions, updates, and removals by ID while preserving the last committed snapshot.
3. Treat geometry, style, visibility, and z-order changes as updates; unchanged records retain
   their native objects.
4. On failure, attempt rollback of every touched overlay class and camera. If rollback itself
   fails, mark the host unsynchronized so the next successful render performs a full restore.
5. On teardown, attempt shape cleanup, native engine/view finish, and reference clearing
   independently. Preserve the first failure and attach later failures as suppressed exceptions.
6. Ignore late Compose invalidations, lifecycle events, and native callbacks after disposal.
7. Never log or include the configured NCP key ID in error messages beyond the fact that a key was
   blank or changed.

## Error and configuration behavior

- Blank `ncpKeyId` fails synchronously from `NaverMap.prepare` with a stable message.
- Native SDK initialization failure, authentication failure surfaced by the SDK, map readiness
  failure, and reconciliation failure are wrapped in `NaverMapException` and delivered to
  `onError` while the surface is alive.
- Repeated identical SDK key initialization is idempotent; a conflicting key is one failure per
  surface and never silently replaces the process-wide client.
- `onError = null` still keeps the surface alive only when the provider can safely continue; a
  failed transactional render must preserve or restore the previous committed state regardless of
  callback configuration.
- The provider does not add a network health check because the SDK credential is a client key
  and no approved offline-safe probe exists in the current Weft health contract.

## Testing strategy

### Common tests

- `NaverMap` registers under `MapPlugin::class` and rejects blank keys.
- The plugin name and provider configuration are stable.
- Provider-neutral overlay conversion produces deterministic IDs, preserves order, and retains
  circle geometry as a native circle rather than approximating it.
- Duplicate IDs and invalid common models remain rejected by `:weft-core`; no provider-specific
  validation is duplicated.

### Android host tests

- Lifecycle events start/resume exactly once after attach and finish idempotently.
- `MapView` receives create/start/resume/pause/stop/destroy, saved-state, and low-memory callbacks
  exactly once per corresponding host event; disposal unregisters both callback bridges.
- The latest pre-ready camera/content snapshot is rendered once when `getMapAsync` completes.
- Camera bearing normalization and native zoom/tilt bounds are applied.
- Marker and shape additions, updates, visibility changes, and removals preserve native object
  identity where possible.
- Same-key SDK initialization occurs once; conflicting keys report one wrapped error.
- A failing native operation rolls back the previous snapshot and marks the next render for full
  restore if rollback fails.
- Teardown attempts cleanup, finish, and reference clearing even when one step throws.

### iOS tests and consumer validation

- The iOS coordinator maps camera and overlay snapshots with alpha, width, z-order, visibility,
  and stable IDs.
- Map view creation/auth configuration happens once per surface and teardown removes delegates
  and overlays.
- `compileKotlinIosSimulatorArm64`, framework linking, generated SwiftPM lock data, and the
  Xcode consumer fixture are hard verification gates. Runtime simulator tests may be skipped on
  `macos_x64`, but compile/link and consumer evidence is required; if the consumer cannot be
  produced, the feature remains explicitly blocked.
- The Android consumer fixture resolves the new artifact and BOM without a direct vendor SDK
  declaration. The root publication task checks the SDK dependency appears in the provider POM.

## Acceptance criteria

The feature is complete only when all of the following are true:

1. `:weft-map:navermap` builds as Android, iOS device, and Apple Silicon simulator targets using
   the repository's existing KMP convention.
2. An app can install `NaverMap` and render the existing `WeftMap` camera, marker, polyline,
   polygon, and circle content without changing `:weft-core`.
3. Android uses the official Naver Maven SDK `3.24.1`; iOS resolves the official
   `SPM-NMapsMap` `3.24.1` package and does not statically merge its dynamic framework.
4. Lifecycle, SDK-key initialization, render rollback, and teardown behavior have automated test
   coverage for both platform adapters, with runtime skips explicitly recorded when host-limited.
5. Root verification, task-graph/release assertions, BOM constraints, publication POM checks,
   module documentation, and separate Android/iOS consumer fixtures all include `weft-navermap`.
6. `./gradlew verify` and `./scripts/verify.sh` pass on the final branch, the iOS consumer gate
   is green or explicitly blocks completion, and no Maven Central publication is performed.

## Official references

- [NAVER Map Android SDK get started](https://navermaps.github.io/android-map-sdk/guide-en/1.html)
- [NAVER Map Android SDK map object and lifecycle](https://navermaps.github.io/android-map-sdk/guide-en/2-1.html)
- [NAVER Map Android SDK shapes](https://navermaps.github.io/android-map-sdk/guide-en/5-4.html)
- [NAVER Map iOS SDK get started](https://navermaps.github.io/ios-map-sdk/guide-ko/1.html)
- [NAVER Map iOS SDK map object](https://navermaps.github.io/ios-map-sdk/guide-en/2-1.html)
- [Official NAVER SPM wrapper](https://github.com/navermaps/SPM-NMapsMap)
- [NAVER SPM package manifest](https://raw.githubusercontent.com/navermaps/SPM-NMapsMap/main/Package.swift)

SDK versions and official documentation were reviewed on 2026-11-26.
