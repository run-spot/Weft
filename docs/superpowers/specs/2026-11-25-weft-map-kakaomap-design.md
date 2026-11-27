# Weft Kakao Map Engine Design

**Date:** 2026-11-25

**Status:** Scope approved; implementation follows this design after the final contract review

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
public typealias MapContentRenderer = @Composable (
    state: WeftMapState,
    modifier: Modifier,
    content: @Composable MapScope.() -> Unit,
    onError: ((Throwable) -> Unit)?,
) -> Unit

public interface MapPlugin {
    public val renderer: MapContentRenderer
}

@DslMarker
public annotation class WeftMapDsl

@WeftMapDsl
public object MapScope

public data class LatLng(
    public val latitude: Double,
    public val longitude: Double,
) {
    init {
        require(latitude.isFinite() && latitude in -90.0..90.0) {
            "latitude must be finite and between -90 and 90"
        }
        require(longitude.isFinite() && longitude in -180.0..180.0) {
            "longitude must be finite and between -180 and 180"
        }
    }
}

public data class CameraPosition(
    public val target: LatLng,
    public val zoom: Float = 15f,
    public val bearing: Float = 0f,
    public val tilt: Float = 0f,
) {
    init {
        require(zoom.isFinite() && zoom >= 0f) { "zoom must be finite and non-negative" }
        require(bearing.isFinite()) { "bearing must be finite" }
        require(tilt.isFinite() && tilt in 0f..90f) { "tilt must be finite and between 0 and 90" }
    }
}

public data class MapMarker(
    public val id: String,
    public val position: LatLng,
    public val title: String? = null,
    public val snippet: String? = null,
) {
    init {
        require(id.isNotBlank()) { "marker id must not be blank" }
    }
}

public data class WeftMapState(
    public val cameraPosition: CameraPosition,
)

public class MapContent internal constructor(
    markers: List<MapMarker>,
) {
    /** Defensive copy of the marker snapshot produced by the child composition. */
    public val markers: List<MapMarker> = markers.toList()
}

@Composable
public fun WeftMap(
    state: WeftMapState,
    modifier: Modifier = Modifier,
    content: @Composable MapScope.() -> Unit = {},
    onError: ((Throwable) -> Unit)? = null,
) {
    Box(modifier) {
        LocalWeft.current.get<MapPlugin>().renderer(
            state = state,
            modifier = Modifier.fillMaxSize(),
            content = content,
            onError = onError,
        )
    }
}

@Composable
public fun MapScope.Marker(marker: MapMarker)

/**
 * Materializes the declarative map children into a provider-neutral snapshot. Provider modules use
 * this public bridge; the node and applier implementation remains private to `:weft-core`.
 */
@Composable
public fun rememberMapContent(
    content: @Composable MapScope.() -> Unit,
): MapContent
```

The first contract intentionally keeps the state small. Camera position and marker identity are
provider-neutral; callbacks, mutable camera state, overlays, and location tracking will be added
only when a second consumer proves their shape. `onError` is the one lifecycle escape hatch: a
provider reports native initialization or rendering failures through it and ignores callbacks after
its surface is disposed.

The `Box`/`fillMaxSize` host is why `:weft-core` exposes Compose Foundation in addition to the
Compose UI types used by `Modifier`; provider surfaces receive a normalized full-size modifier while
the caller's outer modifier controls placement, size, and clipping.

`MapContent` preserves Compose child order. Marker IDs must be unique within one content snapshot;
the builder throws `IllegalArgumentException` with the duplicate IDs before the snapshot is
published. Native adapters diff by marker ID, while Compose slot movement remains responsible for
node ordering.

`MapPlugin` declares the same `type` for every provider:

```kotlin
override val type: KClass<MapPlugin> = MapPlugin::class
```

Therefore `Weft { install(KakaoMap) { ... } }` resolves through `weft.get<MapPlugin>()`, and a
second map provider fails with the existing `DuplicateWeftPluginException`.

## Compose tree architecture

The declarative child content follows the proven RunTheSpot boundary:

1. `rememberMapContent` creates a child `Composition` using the parent composition context and
   returns the public `MapContent` snapshot consumed by provider modules.
2. `MapApplier : AbstractApplier<MapNode>` receives insert, move, remove, and clear operations.
3. `MapNode` owns children, propagates an invalidation callback, and detaches removed nodes.
4. `MapMarker` uses `ComposeNode<MapMarkerNode, MapApplier>` and updates an existing node when
   its immutable model changes.
5. A content builder flattens the node tree into an immutable `MapContent` snapshot. It preserves
   insertion order and rejects duplicate marker IDs.
6. The provider surface consumes that snapshot. The platform coordinator owns SDK objects and
   performs minimal diffs between the previous and next marker/camera state.

The node classes, applier, builder, and composition state are `internal` implementation details of
`:weft-core`; only `MapContent` and `rememberMapContent` cross the module boundary. The child
composition is disposed by `DisposableEffect` when the provider content leaves the composition, and
the snapshot state refuses to publish after disposal. This keeps Compose reconciliation and native
SDK lifecycle separate. The common tree is testable with a no-op applier and does not import
Android, UIKit, or Kakao types.

Node lifecycle rules:

- `attach` propagates one invalidation callback to the entire subtree.
- `detach`, `remove`, and `clear` recursively clear callbacks before dropping nodes.
- `move` preserves node instances and only changes list order.
- `MapMarkerNode.updateMarker` replaces the immutable model in place and invalidates once when the
  model changes.
- A disposed child composition ignores late invalidations and does not publish another snapshot.

Snapshot implementation contract:

```kotlin
@Composable
public fun rememberMapContent(
    content: @Composable MapScope.() -> Unit,
): MapContent {
    val parentComposition = rememberCompositionContext()
    val currentContent = remember {
        mutableStateOf(content, policy = neverEqualPolicy())
    }
    val root = remember { MapRootNode() }
    val state = remember(root) { MapContentState(root) }
    val applier = remember(root) { MapApplier(root) { state.invalidate() } }
    val composition = remember(parentComposition, applier) {
        Composition(applier, parentComposition)
    }
    DisposableEffect(composition) {
        onDispose {
            state.dispose()
            composition.dispose()
        }
    }
    SideEffect { currentContent.value = content }
    LaunchedEffect(composition) {
        composition.setContent {
            currentContent.value.invoke(MapScope)
            SideEffect { state.publishIfDirty() }
        }
    }
    return state.content
}
```

The abbreviated implementation above has two required state details. `MapContentState` owns an
observable `published` value initialized to `MapContent(emptyList())` using
`mutableStateOf(..., neverEqualPolicy())`, and `content` reads `published.value`. It also owns an
observable integer `invalidationToken`. `invalidate()` increments that token unless the state is
disposed, and the child composition reads the token before invoking `currentContent`; this is the
explicit bridge that turns `MapApplier` mutations into another child-composition pass. The outer
provider composition reads `published.value`, so assigning a new immutable `MapContent` schedules
the platform surface with the latest snapshot.

`MapContentState.publishIfDirty` builds a complete next snapshot into a local value, validates
marker IDs, copies the marker list, and assigns `published.value` only after the build succeeds.
A builder failure leaves the last valid snapshot unchanged and propagates the deterministic
validation exception through composition. `dispose` marks the state closed before disposing the
child composition; subsequent invalidation or publication is a no-op. The child composition's
`SideEffect` is the only publication point, which makes one snapshot atomic from the platform
surface's perspective.

Marker identity behavior is fixed:

| Event | Compose node | Native marker |
| --- | --- | --- |
| Same `id`, changed position/title/snippet | Update existing node; invalidate once | Move/update the existing marker |
| Same IDs in a different order | `MapApplier.move` preserves node instances | Reorder the snapshot; do not recreate markers |
| ID removed | Detach/remove node | Remove that native marker |
| New ID | Insert node | Add one native marker |
| ID changed at the same call site | Old keyed group is removed and a new group inserted | Remove old marker, add new marker |
| Duplicate IDs in one snapshot | Snapshot build fails before publication | No partial native update |

`MapScope.Marker` wraps its `ComposeNode` in `key(marker.id)` so declarative reordering uses the
same identity as the native diff. The snapshot's list order is still retained for deterministic
rendering and hit-test lookup.

## Common-to-platform seam

The provider module has one common implementation and one expect/actual surface boundary:

```kotlin
// :weft-map:kakaomap commonMain
private class KakaoMapPlugin(
    private val nativeAppKey: String,
) : MapPlugin {
    override val renderer: MapContentRenderer = { state, modifier, content, onError ->
        KakaoMapPlatformSurface(
            nativeAppKey = nativeAppKey,
            state = state,
            content = rememberMapContent(content),
            modifier = modifier,
            onError = onError,
        )
    }
}

@Composable
private expect fun KakaoMapPlatformSurface(
    nativeAppKey: String,
    state: WeftMapState,
    content: MapContent,
    modifier: Modifier,
    onError: ((Throwable) -> Unit)?,
)
```

The expect signature contains only core models and Compose types. Android and iOS actual functions
own all native view creation, SDK callbacks, lifecycle observers, camera conversion, marker
diffing, disposal, and error forwarding. No native coordinator or SDK type crosses into
`commonMain`.

Error and threading behavior is fixed:

- Core model/config/duplicate-ID validation throws synchronously and is not routed to `onError`.
- Native startup/render errors are wrapped in `KakaoMapException` and delivered at most once per
  surface generation through `onError` on the platform main thread.
- A null `onError` means the surface keeps its last valid content and performs no callback.
- Exceptions thrown by a caller's `onError` callback propagate on the platform main thread; the
  library does not swallow application callback failures.
- A callback arriving after disposal or after a newer surface generation is ignored.

Reference shape:

- [RunTheSpotMapApplier](https://github.com/run-spot/RunTheSpot/blob/f2c87adb8965495e72256f9214bfde5113ec8f99/client/core/maps/src/commonMain/kotlin/run/thespot/ui/maps/composition/RunTheSpotMapApplier.kt)
- [RunTheSpotMapNode](https://github.com/run-spot/RunTheSpot/blob/f2c87adb8965495e72256f9214bfde5113ec8f99/client/core/maps/src/commonMain/kotlin/run/thespot/ui/maps/composition/RunTheSpotMapNode.kt)
- [RunTheSpotMapContent](https://github.com/run-spot/RunTheSpot/blob/f2c87adb8965495e72256f9214bfde5113ec8f99/client/core/maps/src/commonMain/kotlin/run/thespot/ui/maps/composition/RunTheSpotMapContent.kt)
- [RunTheSpot Marker](https://github.com/run-spot/RunTheSpot/blob/f2c87adb8965495e72256f9214bfde5113ec8f99/client/core/maps/src/commonMain/kotlin/run/thespot/ui/maps/composition/Marker.kt)

## Kakao provider

### Configuration and installation

The provider API lives under `run.thespot.weft.map.kakaomap`. `KakaoMapConfig` and the plugin
registration are fixed as follows:

```kotlin
public class KakaoMapConfig {
    public var nativeAppKey: String = ""
}

public object KakaoMap : WeftPlugin<KakaoMapConfig, MapPlugin> {
    override val type: KClass<MapPlugin> = MapPlugin::class
    override val pluginName: String = "KakaoMap"

    override fun prepare(block: KakaoMapConfig.() -> Unit): MapPlugin
    override fun install(plugin: MapPlugin, scope: Weft)
    override fun healthCheck(plugin: MapPlugin): List<WeftHealthCheck> = emptyList()
}

public class KakaoMapException(
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)
```

`prepare` applies the block, rejects a blank `nativeAppKey`, and returns a private common
`KakaoMapPlugin` implementation that retains the key for the platform surface. The value is never
logged or embedded in generated source. `install` is deliberately side-effect free; platform SDK
initialization happens when the Compose surface owns a native view.

The native map SDK has no stable credential-only HTTP probe. `KakaoMap.healthCheck` therefore
returns no `WeftHealthCheck`; the Android `MapLifeCycleCallback.onMapError` and iOS SDK error
callback are the provider-native health signal. A future REST capability can add a separate
REST-key configuration without misusing the native app key.

Common validation rejects non-finite coordinates, out-of-range latitude/longitude, negative or
non-finite zoom, and tilt outside `0..90`. The Kakao adapters normalize bearing to `0..360`, round
zoom to the native integer level, and clamp it to the SDK's advertised min/max level once the native
map is ready. A native callback error is delivered to `onError` as a `KakaoMapException` carrying
the provider error message; after disposal the callback is ignored.

### Android

- Add the Kakao Maven repository in dependency resolution management.
- Add `com.kakao.maps.open:android:2.15.2` only to `androidMain`.
- Use `AndroidView` to host `MapView`.
- Initialize the SDK with `KakaoMapSdk.init(context, nativeAppKey)` at the platform boundary.
- Coordinate `MapView.start`, `resume`, `pause`, and `finish` with the Compose lifecycle owner.
- On `KakaoMapReadyCallback`, render the initial camera and materialized markers through a
  `LabelLayer`.
- Keep all `com.kakao.vectormap.*` types inside `androidMain`.

The Android coordinator owns the `MapView` created by `AndroidView`. Its state machine is
`Created -> Attached -> Started -> Ready -> Paused -> Finished`; `resume` before `Started` is
remembered and applied after start, `pause` is idempotent, and `finish` is called exactly once from
`AndroidView.onRelease`/composition disposal. A pause or detach received before `start` only marks
the coordinator paused; it does not call the native host's `pause`, and a later resume starts the
map once. A monotonically increasing surface generation invalidates late `onMapReady` and
`onMapError` callbacks. SDK initialization is guarded by the native key: reusing the same key is
idempotent, while a different key reports an error through `onError`.

Android lifecycle transitions are exact:

| Event | Action |
| --- | --- |
| `AndroidView.factory` | Create `MapView`; state `Created` |
| View attached, lifecycle below `RESUMED` | State `Attached`; defer `start` |
| View attached and `ON_RESUME` | Call `start` once; state `Started` then `Ready` from callback |
| `ON_PAUSE` or view detach | Call `pause` once; state `Paused` |
| Reattach/`ON_RESUME` after pause | Call `resume`; do not recreate the SDK map |
| `ON_DESTROY` or `onRelease` | Invalidate generation, call `finish` once, state `Finished` |
| Any event after `Finished` | No-op |

The coordinator uses `LocalLifecycleOwner` and `LifecycleEventObserver` from
`lifecycle-runtime-compose`; it is the sole owner of `MapView` lifecycle calls. Android platform
tests inject a fake host with the same transitions and assert call order. The internal Android seam
is:

```kotlin
internal interface KakaoMapAndroidHost {
    fun start(
        appKey: String,
        initialCamera: CameraPosition,
        onReady: (KakaoMapZoomBounds) -> Unit,
        onError: (Throwable) -> Unit,
    )
    fun render(camera: CameraPosition, content: MapContent)
    fun resume()
    fun pause()
    fun finish()
}

internal data class KakaoMapZoomBounds(
    val min: Int,
    val max: Int,
)
```

`AndroidMapViewHost` adapts the real `MapView` and passes the ready map's actual minimum and
maximum native zoom levels to `onReady`. `FakeKakaoMapAndroidHost` records those bounds and all
calls for unit tests. The coordinator, not the host, owns the generation token, transition
validation, and pre-ready camera normalization.

Camera and marker mapping is exact:

- Before `Ready`, retain the latest `WeftMapState` and `MapContent`; apply both after the ready
  callback.
- After `Ready`, a camera value change issues one non-animated native camera update. The initial
  implementation does not report camera gestures back to common state.
- Bearing is normalized with `((bearing % 360) + 360) % 360`, so `360` becomes `0`.
- Zoom uses `kotlin.math.roundToInt()` and is clamped to the ready map's native min/max level;
  pre-ready values are clamped after the native range is known.
- Tilt is clamped to `0..90` before conversion.
- Marker `title` becomes the native label text; `snippet` remains in the common model but is not
  rendered until a future info-window/click contract exists.
- Native marker add/update/remove uses the identity table above and is applied as one coordinator
  pass after each `MapContent` snapshot.

The lifecycle/coordinator shape follows RunTheSpot's
[PlatformMapSurface.android.kt](https://github.com/run-spot/RunTheSpot/blob/f2c87adb8965495e72256f9214bfde5113ec8f99/client/core/maps/src/androidMain/kotlin/run/thespot/ui/maps/PlatformMapSurface.android.kt),
reduced to camera and marker rendering.

### iOS

- Configure `swiftPMDependencies` for
  `https://github.com/kakao-mapsSDK/KakaoMapsSDK-SPM.git`, exact version `2.12.19`, product
  `KakaoMapsSDK-SPM`, restricted to iOS, and set `iosMinimumDeploymentTarget` to `13.0`.
- Keep imports in `iosMain` through the generated `swiftPMImport` namespace.
- Host the Kakao map view behind an `UIKitView`/platform surface and keep its coordinator outside
  `commonMain`.
- Initialize the SDK with the native app key at the iOS platform boundary and translate camera and
  marker models to the SDK's native objects.

The iOS coordinator owns `KMController` and `KMViewContainer`, prepares and activates the engine
when the view is attached and has a non-zero bounds, adds one map view, and pauses/resets the engine
on disposal. All UIKit and Kakao callbacks run on the main thread. The expected generated symbols
follow the existing RunTheSpot bridge (`KMController`, `KMViewContainer`, `KakaoMap`,
`MapControllerDelegateProtocol`, `MapPoint`, `MapviewInfo`, `SDKInitializer`); the compile task
must confirm the generated namespace and fail loudly if the package exposes a different API.

The iOS mapping contract is explicit:

- `SDKInitializer.InitSDKWithAppKey` is process-global. A main-thread registry accepts the first
  non-blank key, treats repeated initialization with the same key as a no-op, and reports a
  different key as `KakaoMapException` through the current surface's `onError`.
- `LatLng` becomes `MapPoint(longitude = longitude, latitude = latitude)`.
- `zoom` is rounded with `roundToInt()` and clamped to the SDK map's integer level range before it
  becomes `MapviewInfo.defaultLevel` or `CameraUpdate.zoomLevel`.
- `bearing` is normalized to `0..360` degrees and converted to clockwise radians; `tilt` is
  clamped to `0..90` degrees and converted to radians. The conversion is `degrees * PI / 180`.
  Camera updates use the SDK's `CameraUpdate.make(target:zoomLevel:rotation:tilt:mapView:)`
  contract and are non-animated.
- The adapter creates one deterministic `PoiStyle`/`LabelLayer` pair per map view. A marker uses
  `PoiOptions(poiID = marker.id, styleID = "weft_default_marker")`; a non-null title is supplied
  as the first Poi text, while `snippet` is retained but not rendered. The style is registered
  before any Poi is added. Position/title changes update the existing Poi; ID removal calls
  `removePoi`, and an ID change removes the old Poi and adds a new one. If the SDK cannot create
  the style, layer, or Poi, the whole render pass fails without partially publishing the snapshot.

The iOS host seam is internal and mirrors the Android fake boundary:

```kotlin
internal interface KakaoMapIosHost {
    fun prepare(
        appKey: String,
        initialCamera: CameraPosition,
        onReady: (KakaoMapIosZoomBounds) -> Unit,
        onError: (Throwable) -> Unit,
    )
    fun render(camera: CameraPosition, content: MapContent)
    fun pause()
    fun reset()
}

internal data class KakaoMapIosZoomBounds(
    val min: Int,
    val max: Int,
)
```

`KakaoMapIosNativeHost` adapts `KMController`; `FakeKakaoMapIosHost` records prepare/render/pause/
reset, supplies explicit zoom bounds, and manually triggers delegate callbacks. The coordinator
owns generation tokens, non-zero-bounds retries, window attach/detach transitions, and idempotent
disposal; it does not require an app key or live SDK in its state-machine tests.

iOS transitions mirror the Android contract: `Created -> AttachedZeroBounds -> Prepared -> Active
-> ViewAdded -> Ready -> Paused -> Reset`. `layoutSubviews` retries `addViews` only when bounds
become non-zero; `didMoveToWindow` pauses when the window becomes null and reactivates when it
returns; `dispose` invalidates the generation, clears adapters, pauses the engine, resets it, and
is idempotent. `authenticationFailed` and `addViewFailed` route a `KakaoMapException` to `onError`
on the main thread. Late delegate calls after `Reset` are ignored. The fake host tests cover each
transition, zero-bounds retry, callback replacement, render failure, and disposal.

SwiftPM integration is part of the repository/consumer contract. The first resolution must run
the module's `integrateLinkagePackage` task against the consuming Xcode project, and the generated
synthetic package plus `.swiftpm-locks/default/swiftImport/Package.resolved` are committed. The
generated package must retain the Kakao package's `KakaoMapsSDKBundle.bundle` resource; an iOS
smoke build fails if the bundle is absent. The provider's published KMP metadata does not make a
consumer's Xcode project automatically adopt SwiftPM: the module guide documents the integration
task and iOS 13 deployment target for every consumer.

The exact SwiftPM import and generated symbol names must be verified by the iOS compile task; no
manual Swift source is added to the Weft library in this slice.

## Gradle and publication changes

- Remove `include(":weft-map")` and add `include(":weft-map:kakaomap")`.
- Remove the empty `weft-map/build.gradle.kts` publication.
- Create `weft-map/kakaomap/build.gradle.kts` with the KMP, Compose, and Maven conventions.
- Set the publication artifact ID explicitly with
  `mavenPublishing { coordinates(artifactId = "weft-kakaomap") }` because the nested Gradle
  project name is `kakaomap`.
- Add these version-catalog keys: `compose-ui` (`org.jetbrains.compose.ui:ui`),
  `compose-foundation` (`org.jetbrains.compose.foundation:foundation`), and
  `kakao-maps-android` (`com.kakao.maps.open:android`, version `2.15.2`), plus
  `lifecycle-runtime-compose` (`org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose`,
  version `2.10.0-alpha05`). Keep the iOS SwiftPM version as `2.12.19` in the
  `swiftPackage(... version = exact("2.12.19"))` declaration.
- Add the Kakao Maps Maven repository
  `https://devrepo.kakao.com/nexus/repository/kakaomap-releases/` to
  `dependencyResolutionManagement`; do not add a project repository.
- Add `api(libs.compose.ui)` and `api(libs.compose.foundation)` to `:weft-core:commonMain`.
- Add `api(project(":weft-core"))` to `:weft-map:kakaomap:commonMain`, the Compose convention to
  the provider module, `implementation(libs.compose.ui)` to its `androidMain`, and
  `implementation(libs.kakao.maps.android)` plus `implementation(libs.lifecycle.runtime.compose)`
  to its `androidMain`.
- Configure the provider's Kotlin block exactly as:

  ```kotlin
  swiftPMDependencies {
      discoverClangModulesImplicitly = true
      swiftPackage(
          url = url("https://github.com/kakao-mapsSDK/KakaoMapsSDK-SPM.git"),
          version = exact("2.12.19"),
          products = listOf(product("KakaoMapsSDK-SPM", platforms = setOf(iOS()))),
      )
      iosMinimumDeploymentTarget.set("13.0")
  }
  ```

  The `iosMain` source set has no direct Maven dependency; its generated `swiftPMImport` symbols
  come from this declaration.
- Replace the root verify and BOM `weft-map` references with `weft-map:kakaomap` and the artifact
  `weft-kakaomap`; keep login and push unchanged. The BOM's generated dependency set must be
  exactly `run.thespot.weft:weft-core`, `run.thespot.weft:weft-kakaomap`,
  `run.thespot.weft:weft-login`, and `run.thespot.weft:weft-push` at `VERSION_NAME`.
- Keep the BOM metadata-only and aligned with the four currently published artifacts:
  `weft-core`, `weft-kakaomap`, `weft-login`, and `weft-push`.
- Treat `:weft-map` as a Gradle namespace/parent only. Root verification and release tasks enumerate
  the four leaf libraries explicitly (`:weft-core`, `:weft-map:kakaomap`, `:weft-login`,
  `:weft-push`) and never request `:weft-map:check` or `:weft-map:publishToMavenCentral`.
- Update the `:weft-map` and `:weft-bom` module guides and the repository architecture guide in the
  same change. A consumer resolving `weft-kakaomap` must add Kakao's Maven repository because a
  Maven POM cannot guarantee that every consuming Gradle settings file accepts the vendor repo;
  the module guide contains the exact `dependencyResolutionManagement` snippet and the publication
  fixture verifies that the transitive Kakao dependency is resolvable with that repository.

### SwiftPM toolchain gate

The repository remains on the currently pinned stable Kotlin `2.4.21`; the user's threshold is met
and no unrelated root-toolchain upgrade is made. SwiftPM import is an alpha/toolchain-sensitive
feature whose current Kotlin documentation uses a `2.5.0-RC` validation example, so the first
Gradle configuration/build step is an explicit compatibility gate. If `swiftPMDependencies` or the
generated `swiftPMImport` namespace is unavailable on `2.4.21`, stop with the exact Gradle error
and request the smallest toolchain decision (upgrade to the documented RC, or choose another iOS
bridge). Do not silently downgrade to CocoaPods, add manual Swift source, or silently change the
root Kotlin version.

## Testing and acceptance

### Common tests

- `MapNode` insertion, move, removal, clear, attach, and detach behavior.
- `MapApplier` routes Compose operations to the node tree and invalidates materialized content.
- `MapMarker` updates an existing node rather than replacing it for equal identity.
- `rememberMapContent` publishes marker snapshots after composition changes.
- `WeftMap` delegates to the installed `MapPlugin` through `LocalWeft`.
- `KakaoMap` uses `MapPlugin::class`, forwards `nativeAppKey`, and rejects a blank key.
- Installing Kakao Maps twice fails with the existing duplicate plugin exception.
- Invalid `LatLng`, camera, blank marker IDs, and duplicate marker IDs fail with deterministic
  messages before native rendering.

### Platform/build checks

- `./scripts/verify.sh`
- `./gradlew :weft-core:check :weft-map:kakaomap:check`
- `./gradlew :weft-core:checkPomFileForKotlinMultiplatformPublication :weft-map:kakaomap:checkPomFileForKotlinMultiplatformPublication`
- `./gradlew verifyWeftBomPublication` must find exactly the four expected artifact coordinates,
  including `weft-kakaomap`, and must not find the removed `weft-map` artifact.
- Android coordinator tests use a fake platform host to cover `Created/Attached/Started/Ready/
  Paused/Finished`, duplicate initialization keys, late callbacks, marker add/update/remove, and
  camera normalization without requiring a live Kakao service.
- iOS coordinator tests use `FakeKakaoMapIosHost` for startup, zero-bounds retry, camera/marker
  mapping, late callbacks, render failures, callback replacement, and disposal/reset behavior.
- iOS compile/link validation confirms SwiftPM product resolution, generated symbol imports,
  `iosMinimumDeploymentTarget = 13.0`, the generated package/lock files, resource-bundle delivery,
  non-zero-bounds engine startup, and disposal/reset behavior on the macOS/Xcode environment.
- A Compose runtime harness using a `Recomposer`/frame clock tests `rememberMapContent` and its
  invalidation-to-publication handoff. `WeftMap`'s `Box` delegation test runs in an Android host
  Compose test; it is not falsely claimed as a headless common test using a no-op applier.
- Publication checks inspect the generated KMP POM for the Kakao SDK dependency and run a small
  consumer-resolution fixture with Kakao's repository explicitly configured. They also inspect
  merged Android manifest/consumer rules and record any vendor-required entries in the module
  guide.
- Android compilation and host tests with a configured Android SDK.
- iOS compilation/linking on the configured macOS/Xcode environment, including SwiftPM resolution.

No Maven Central publication task is run.

## Risks and decisions

- Kakao Android and iOS SDK versions are intentionally pinned independently because their release
  trains differ. The Android quickstart documents `com.kakao.maps.open:android:2.15.2`; the iOS
  map release feed documents `2.12.19`, both reviewed on 2026-11-25.
- SwiftPM import is a toolchain-sensitive KMP feature. The stable `2.4.21` pin is preserved until
  the compatibility gate proves it works; if it does not, stop at the platform boundary and report
  the exact toolchain blocker rather than weakening the common contract or adding vendor types to
  `commonMain`.
- The first renderer does not promise full feature parity with RunTheSpot. It deliberately starts
  with camera and marker primitives so the contract can stabilize before adding routes, tracking,
  or rich marker styles.

## References

- [Kakao Maps Android quickstart](https://apis.map.kakao.com/android_v2/docs/getting-started/quickstart/)
- [Kakao Maps iOS quickstart](https://apis.map.kakao.com/ios_v2/docs/getting-started/gettingstarted/)
- [Kakao Maps iOS release feed](https://apis.map.kakao.com/ios_v2/news/)
- [Kotlin SwiftPM import](https://kotlinlang.org/docs/multiplatform/multiplatform-spm-import.html)
