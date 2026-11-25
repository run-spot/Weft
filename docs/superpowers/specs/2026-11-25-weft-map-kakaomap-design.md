# Weft Kakao Map Engine Design

**Date:** 2026-11-25  
**Status:** Scope approved; implementation follows this design

## Goal

Add the first real Weft map capability without putting a provider SDK into `commonMain`:
`:weft-core` owns the provider-neutral map contract and Compose entry point, while
`:weft-map:kakaomap` owns the Kakao Maps Android and iOS adapters.

This slice implements only Kakao Maps. Naver Maps and Google Maps remain future provider modules.

## Current context

- `:weft-core` already owns `Weft`, `WeftPlugin`, `LocalWeft`, `WeftApp`, and health-check
  collection.
- `:weft-map` is currently an empty KMP publication skeleton.
- The repository uses Kotlin `2.4.21`, Android Gradle Plugin `9.3.1`, and Compose Multiplatform
  `1.12.1`.
- The RunTheSpot map implementation uses a Compose child composition backed by an
  `AbstractApplier`, a mutable node tree, a content builder, and a platform surface/coordinator.
  Weft will adopt that boundary without copying RunTheSpot product-specific marker styles,
  analytics, or application state.

## Scope

### In scope

1. Add provider-neutral map models and the `MapPlugin` contract to `:weft-core`.
2. Add `WeftMap` and a small declarative `MapScope.Marker` API backed by `ComposeNode`.
3. Implement the shared map `Node`/`Applier`/content-materialization pipeline in `:weft-core`.
4. Replace the empty `:weft-map` publication with `:weft-map:kakaomap`, artifact
   `run.thespot.weft:weft-kakaomap`.
5. Integrate Kakao Maps Android SDK `2.15.2` through `androidMain`.
6. Integrate KakaoMapsSDK iOS `2.12.19` through Kotlin SwiftPM import and `iosMain`.
7. Update settings, root verification, BOM constraints, publication coordinates, module guides,
   and tests.

### Out of scope

- Naver Maps or Google Maps modules.
- Place search, geocoding, routing, clustering, user-location tracking, or polyline/polygon APIs.
- Provider-specific types in `commonMain`.
- Publishing or releasing artifacts.
- Reproducing RunTheSpot's application-specific marker visuals or telemetry.

## Public API

The map contract lives under `run.thespot.weft.core.map` so that the core artifact is the stable
dependency shared by every future provider artifact.

```kotlin
public interface MapPlugin {
    @Composable
    public fun Content(
        state: WeftMapState,
        modifier: Modifier = Modifier,
        content: @Composable MapScope.() -> Unit = {},
    )
}

public data class LatLng(
    public val latitude: Double,
    public val longitude: Double,
)

public data class CameraPosition(
    public val target: LatLng,
    public val zoom: Float = 15f,
    public val bearing: Float = 0f,
    public val tilt: Float = 0f,
)

public data class MapMarker(
    public val id: String,
    public val position: LatLng,
    public val title: String? = null,
    public val snippet: String? = null,
)

public data class WeftMapState(
    public val cameraPosition: CameraPosition,
)

@Composable
public fun WeftMap(
    state: WeftMapState,
    modifier: Modifier = Modifier,
    content: @Composable MapScope.() -> Unit = {},
) {
    LocalWeft.current.get<MapPlugin>().Content(state, modifier, content)
}

@Composable
public fun MapScope.Marker(marker: MapMarker)
```

The first contract intentionally keeps the state small. Camera position and marker identity are
provider-neutral; callbacks, mutable camera state, overlays, and location tracking will be added
only when a second consumer proves their shape.

`MapPlugin` declares the same `type` for every provider:

```kotlin
override val type: KClass<MapPlugin> = MapPlugin::class
```

Therefore `Weft { install(KakaoMap) { ... } }` resolves through `weft.get<MapPlugin>()`, and a
second map provider fails with the existing `DuplicateWeftPluginException`.

## Compose tree architecture

The declarative child content follows the proven RunTheSpot boundary:

1. `rememberMapContent` creates a child `Composition` using the parent composition context.
2. `MapApplier : AbstractApplier<MapNode>` receives insert, move, remove, and clear operations.
3. `MapNode` owns children, propagates an invalidation callback, and detaches removed nodes.
4. `MapMarker` uses `ComposeNode<MapMarkerNode, MapApplier>` and updates an existing node when
   its immutable model changes.
5. A content builder flattens the node tree into an immutable `MapContent` snapshot.
6. The provider surface consumes that snapshot. The platform coordinator owns SDK objects and
   performs minimal diffs between the previous and next marker/camera state.

This keeps Compose reconciliation and native SDK lifecycle separate. The common tree is testable
with a no-op applier and does not import Android, UIKit, or Kakao types.

Reference shape:

- [RunTheSpotMapApplier](https://github.com/run-spot/RunTheSpot/blob/f2c87adb8965495e72256f9214bfde5113ec8f99/client/core/maps/src/commonMain/kotlin/run/thespot/ui/maps/composition/RunTheSpotMapApplier.kt)
- [RunTheSpotMapNode](https://github.com/run-spot/RunTheSpot/blob/f2c87adb8965495e72256f9214bfde5113ec8f99/client/core/maps/src/commonMain/kotlin/run/thespot/ui/maps/composition/RunTheSpotMapNode.kt)
- [RunTheSpotMapContent](https://github.com/run-spot/RunTheSpot/blob/f2c87adb8965495e72256f9214bfde5113ec8f99/client/core/maps/src/commonMain/kotlin/run/thespot/ui/maps/composition/RunTheSpotMapContent.kt)
- [RunTheSpot Marker](https://github.com/run-spot/RunTheSpot/blob/f2c87adb8965495e72256f9214bfde5113ec8f99/client/core/maps/src/commonMain/kotlin/run/thespot/ui/maps/composition/Marker.kt)

## Kakao provider

### Configuration and installation

`KakaoMapConfig` exposes the native app key and validates that it is non-blank during `prepare`.
The value is never logged or embedded in the common API. `KakaoMap` implements
`WeftPlugin<KakaoMapConfig, MapPlugin>` and installs under `MapPlugin::class`.

The native map SDK has no stable credential-only HTTP probe. `KakaoMap.healthCheck` therefore
returns no `WeftHealthCheck`; the Android `MapLifeCycleCallback.onMapError` and iOS SDK error
callback are the provider-native health signal. A future REST capability can add a separate
REST-key configuration without misusing the native app key.

### Android

- Add the Kakao Maven repository in dependency resolution management.
- Add `com.kakao.maps.open:android:2.15.2` only to `androidMain`.
- Use `AndroidView` to host `MapView`.
- Initialize the SDK with `KakaoMapSdk.init(context, nativeAppKey)` at the platform boundary.
- Coordinate `MapView.start`, `resume`, `pause`, and `finish` with the Compose lifecycle owner.
- On `KakaoMapReadyCallback`, render the initial camera and materialized markers through a
  `LabelLayer`.
- Keep all `com.kakao.vectormap.*` types inside `androidMain`.

The lifecycle/coordinator shape follows RunTheSpot's
[PlatformMapSurface.android.kt](https://github.com/run-spot/RunTheSpot/blob/f2c87adb8965495e72256f9214bfde5113ec8f99/client/core/maps/src/androidMain/kotlin/run/thespot/ui/maps/PlatformMapSurface.android.kt),
reduced to camera and marker rendering.

### iOS

- Configure `swiftPMDependencies` for
  `https://github.com/kakao-mapsSDK/KakaoMapsSDK-SPM.git`, exact version `2.12.19`, product
  `KakaoMapsSDK-SPM`, restricted to iOS.
- Keep imports in `iosMain` through the generated `swiftPMImport` namespace.
- Host the Kakao map view behind an `UIKitView`/platform surface and keep its coordinator outside
  `commonMain`.
- Initialize the SDK with the native app key at the iOS platform boundary and translate camera and
  marker models to the SDK's native objects.

The exact SwiftPM import and generated symbol names must be verified by the iOS compile task; no
manual Swift source is added to the Weft library in this slice.

## Gradle and publication changes

- Remove `include(":weft-map")` and add `include(":weft-map:kakaomap")`.
- Remove the empty `weft-map/build.gradle.kts` publication.
- Create `weft-map/kakaomap/build.gradle.kts` with the KMP, Compose, and Maven conventions.
- Set the publication artifact ID explicitly to `weft-kakaomap` because the nested Gradle project
  name is `kakaomap`.
- Add Compose UI and foundation catalog entries to `:weft-core` as `api` dependencies.
- Add Kakao Android and iOS version entries to the version catalog.
- Replace the root verify and BOM `weft-map` references with `weft-map:kakaomap` and the artifact
  `weft-kakaomap`; keep login and push unchanged.
- Keep the BOM metadata-only and aligned with the four currently published artifacts:
  `weft-core`, `weft-kakaomap`, `weft-login`, and `weft-push`.

## Testing and acceptance

### Common tests

- `MapNode` insertion, move, removal, clear, attach, and detach behavior.
- `MapApplier` routes Compose operations to the node tree and invalidates materialized content.
- `MapMarker` updates an existing node rather than replacing it for equal identity.
- `rememberMapContent` publishes marker snapshots after composition changes.
- `WeftMap` delegates to the installed `MapPlugin` through `LocalWeft`.
- `KakaoMap` uses `MapPlugin::class`, forwards `nativeAppKey`, and rejects a blank key.
- Installing Kakao Maps twice fails with the existing duplicate plugin exception.

### Platform/build checks

- `./scripts/verify.sh`
- `./gradlew :weft-core:check :weft-map:kakaomap:check`
- `./gradlew :weft-core:checkPomFileForKotlinMultiplatformPublication :weft-map:kakaomap:checkPomFileForKotlinMultiplatformPublication`
- Android compilation and host tests with a configured Android SDK.
- iOS compilation/linking on the configured macOS/Xcode environment, including SwiftPM resolution.

No Maven Central publication task is run.

## Risks and decisions

- Kakao Android and iOS SDK versions are intentionally pinned independently because their release
  trains differ. The Android quickstart documents `com.kakao.maps.open:android:2.15.2`; the iOS
  map release feed documents `2.12.19`, both reviewed on 2026-11-25.
- SwiftPM import is a toolchain-sensitive KMP feature. If the iOS compile task exposes a Kotlin or
  generated-symbol incompatibility, stop at the platform boundary and report the exact toolchain
  blocker rather than weakening the common contract or adding vendor types to `commonMain`.
- The first renderer does not promise full feature parity with RunTheSpot. It deliberately starts
  with camera and marker primitives so the contract can stabilize before adding routes, tracking,
  or rich marker styles.

## References

- [Kakao Maps Android quickstart](https://apis.map.kakao.com/android_v2/docs/getting-started/quickstart/)
- [Kakao Maps iOS quickstart](https://apis.map.kakao.com/ios_v2/docs/getting-started/gettingstarted/)
- [Kakao Maps iOS release feed](https://apis.map.kakao.com/ios_v2/news/)
- [Kotlin SwiftPM import](https://kotlinlang.org/docs/multiplatform/multiplatform-spm-import.html)
