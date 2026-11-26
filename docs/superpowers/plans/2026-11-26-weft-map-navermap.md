# Naver Maps Provider Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents are available) or superpowers:executing-plans to implement this plan. Each production-code change must follow @superpowers:test-driven-development, and completion claims require @superpowers:verification-before-completion.

**Goal:** Add a published `:weft-map:navermap` Kotlin Multiplatform provider that implements the existing `MapPlugin` contract for Naver Maps on Android and iOS without changing `:weft-core`.

**Architecture:** Create a provider leaf parallel to `:weft-map:kakaomap`. `commonMain` owns only Naver configuration, provider-neutral snapshot conversion, stable IDs, and lifecycle helpers; Android and iOS source sets own native SDK initialization, Compose/UIKit surfaces, lifecycle bridges, camera conversion, and overlay reconciliation. The Weft registry remains one-provider-per-`MapPlugin` slot.

**Tech Stack:** Kotlin Multiplatform 2.4.21, Compose Multiplatform 1.12.1, Android SDK `com.naver.maps:map-sdk:3.24.1`, Naver iOS SwiftPM package `SPM-NMapsMap` `3.24.1`, Android host tests, Kotlin/Native iOS compile/tests, Gradle publication checks, and separate Android/iOS consumer fixtures.

**Spec:** `docs/superpowers/specs/2026-11-26-weft-map-navermap-design.md`

---

## Chunk 1: Module, common provider contract, and build wiring

### Task 1: Add the provider leaf and publication metadata

**Files:**
- Modify: `settings.gradle.kts`
- Modify: `gradle/libs.versions.toml`
- Create: `weft-map/navermap/build.gradle.kts`
- Modify: `weft-bom/build.gradle.kts`
- Modify: `weft-bom/AGENTS.md`
- Modify: `build.gradle.kts`
- Modify: `weft-map/AGENTS.md`
- Modify: `AGENTS.md`
- Modify: `README.md`
- Modify: `docs/architecture/library-modules.md`

- [ ] **Step 1: Add the Naver repository, version alias, project include, and BOM constraint**

  Add `maven("https://repository.map.naver.com/archive/maven")` to
  `dependencyResolutionManagement.repositories`, add
  `naver-maps-android = { module = "com.naver.maps:map-sdk", version = "3.24.1" }`, include
  `:weft-map:navermap`, and add `api(project(":weft-map:navermap"))` to the BOM constraints.
  Keep the Kakao repository and leaf unchanged.

- [ ] **Step 2: Create the KMP leaf build file**

  Create `weft-map/navermap/build.gradle.kts` using the same three convention plugins as the
  Kakao leaf. Set `artifactId = "weft-navermap"`. Declare `iosArm64()` and
  `iosSimulatorArm64()` framework binaries with base name `WeftNaverMap` and static KMP
  framework output, matching the existing publication shape. Configure SwiftPM import with:

  ```kotlin
  swiftPackage(
      url = url("https://github.com/navermaps/SPM-NMapsMap.git"),
      version = exact("3.24.1"),
      products = listOf(product("NMapsMap", platforms = setOf(iOS()))),
  )
  ```

  Set `discoverClangModulesImplicitly = true` like the Kakao leaf. Keep `commonMain` dependent
  only on `:weft-core`; put Compose UI, lifecycle compose, and `libs.naver.maps.android` in
  `androidMain`. Do not add vendor SDK dependencies to `commonMain`.

- [ ] **Step 3: Add root verification and release task wiring before provider source exists**

  Extend `weftLibraryProjects` with `:weft-map:navermap`. Extend `verifyWeftMapLayout` to
  require exactly `kakaomap` and `navermap`. Add `verifyNaverMapPublication` that depends on
  `:weft-map:navermap:generatePomFileForAndroidPublication`, parses
  `weft-map/navermap/build/publications/android/pom-default.xml`, and requires a dependency with
  group `com.naver.maps` and artifact `map-sdk`. Add `verifyNaverMapPublication` to `verify`.
  Update `gradle.taskGraph.whenReady` assertions and `publishWeftReleaseToMavenCentral` so
  `:weft-map:navermap:check` and `:weft-map:navermap:publishToMavenCentral` are explicit members
  of verification/release staging and the namespace is never published. Update
  `verifyWeftBomPublication`'s expected artifact list to include `weft-navermap`.

- [ ] **Step 4: Update repository guidance for the second leaf**

  Update `AGENTS.md`, `weft-map/AGENTS.md`, and `weft-bom/AGENTS.md` to list
  `weft-navermap`, the Naver SDK versions, and the same provider-leaf verification rules as
  Kakao. Update `README.md` with the Naver installation example and the one-provider-per-runtime
  rule. Update `docs/architecture/library-modules.md` with the new leaf, artifact, SDK versions,
  and iOS consumer gate. Keep the repository content in English.

- [ ] **Step 5: Run the build configuration baseline**

  Run:

  ```sh
  ANDROID_HOME="$HOME/Library/Android/sdk" \
  ANDROID_SDK_ROOT="$HOME/Library/Android/sdk" \
  ./gradlew :weft-map:navermap:tasks :verifyWeftMapLayout
  ```

  Expected: Gradle configures `:weft-map:navermap`, lists its KMP/publication tasks, and
  `verifyWeftMapLayout` fails only if the module include or namespace set is wrong. Do not add
  provider source code in this step.

- [ ] **Step 6: Commit the build skeleton**

  ```sh
  git add settings.gradle.kts gradle/libs.versions.toml weft-map/navermap/build.gradle.kts \
    weft-bom/build.gradle.kts build.gradle.kts AGENTS.md weft-map/AGENTS.md weft-bom/AGENTS.md \
    README.md docs/architecture/library-modules.md
  git commit -m "build: scaffold Naver map provider leaf"
  ```

### Task 2: Define common provider API and snapshot conversion with tests first

**Files:**
- Create: `weft-map/navermap/src/commonTest/kotlin/run/thespot/weft/map/navermap/NaverMapTest.kt`
- Create: `weft-map/navermap/src/commonTest/kotlin/run/thespot/weft/map/navermap/NaverMapOverlayShapesTest.kt`
- Create: `weft-map/navermap/src/commonTest/kotlin/run/thespot/weft/map/navermap/NaverMapHostTeardownTest.kt`
- Create: `weft-map/navermap/src/commonMain/kotlin/run/thespot/weft/map/navermap/NaverMap.kt`
- Create: `weft-map/navermap/src/commonMain/kotlin/run/thespot/weft/map/navermap/NaverMapException.kt`
- Create: `weft-map/navermap/src/commonMain/kotlin/run/thespot/weft/map/navermap/NaverMapPlatform.kt`
- Create: `weft-map/navermap/src/commonMain/kotlin/run/thespot/weft/map/navermap/NaverMapOverlayShapes.kt`
- Create: `weft-map/navermap/src/commonMain/kotlin/run/thespot/weft/map/navermap/NaverMapHostTeardown.kt`
- Create: `weft-map/navermap/src/androidMain/kotlin/run/thespot/weft/map/navermap/NaverMapPlatform.android.kt`
- Create: `weft-map/navermap/src/iosMain/kotlin/run/thespot/weft/map/navermap/NaverMapPlatform.ios.kt`

- [ ] **Step 1: Write failing common tests for configuration and registration**

  Add tests named `registersUnderMapPluginType`, `rejectsBlankNcpKeyId`, and
  `keepsPluginNameStable`. Assert that `NaverMap.prepare` produces a `MapPlugin`, uses
  `MapPlugin::class`, rejects `""` and whitespace-only keys with the stable message
  `NAVER Maps NCP key ID must not be blank.`, and reports `NaverMap` as the plugin name.

- [ ] **Step 2: Run the common tests and verify the expected missing-symbol failure**

  Run:

  ```sh
  ANDROID_HOME="$HOME/Library/Android/sdk" \
  ANDROID_SDK_ROOT="$HOME/Library/Android/sdk" \
  ./gradlew :weft-map:navermap:testAndroidHostTest
  ```

  Expected: compilation fails because `NaverMap` and `NaverMapConfig` do not exist yet. Fix only
  test/setup errors; do not implement production code before this failure is observed.

- [ ] **Step 3: Implement the minimal public provider entry point**

  In `NaverMap.kt`, add public `NaverMapConfig.ncpKeyId`, public `NaverMap`, and private
  `NaverMapPlugin`. `NaverMapPlugin.Content` must call `rememberMapContent(content)` and
  `NaverMapPlatformSurface(ncpKeyId, state, snapshot, modifier, onError)`. Add the `expect`
  declaration in `NaverMapPlatform.kt`; it must accept only common types plus the NCP key string.
  Add `NaverMapException` as an `IllegalStateException` wrapper and keep SDK types out of
  common code.

- [ ] **Step 4: Run the focused tests and verify green**

  ```sh
  ANDROID_HOME="$HOME/Library/Android/sdk" \
  ANDROID_SDK_ROOT="$HOME/Library/Android/sdk" \
  ./gradlew :weft-map:navermap:testAndroidHostTest --tests '*NaverMapTest'
  ```

  Expected: all configuration tests pass.

- [ ] **Step 5: Write failing shape conversion and teardown tests**

  Add tests named `usesStableKindPrefixedIdsAndDeterministicKindOrder`,
  `mapsMarkersAndNaverCircleWithoutPolygonApproximation`, `keepsStyleVisibilityAndZIndex`,
  `runsAllTeardownActionsAfterFailure`, and `suppressesLaterTeardownFailuresOnTheFirstFailure`.
  Supply separate `List<MapMarker>`, `List<MapPolyline>`, `List<MapPolygon>`, and
  `List<MapCircle>` arguments to the converter seam because `MapContent`'s constructor is internal
  to `:weft-core`. Assert the documented deterministic order is all markers, then polylines,
  then polygons, then circles; preserve each list's order; use
  `weft:marker:<id>`, `weft:polyline:<id>`, `weft:polygon:<id>`, and `weft:circle:<id>`; and
  keep circle center/radius as circle fields. Assert marker position, title, snippet, visibility,
  and z-index fields in `NaverMapOverlayShape`. Test that teardown attempts cleanup, finish, and
  reference clearing even when earlier actions throw.

- [ ] **Step 6: Run the new tests and verify the expected failure**

  ```sh
  ANDROID_HOME="$HOME/Library/Android/sdk" \
  ANDROID_SDK_ROOT="$HOME/Library/Android/sdk" \
  ./gradlew :weft-map:navermap:testAndroidHostTest --tests '*NaverMapOverlayShapesTest' --tests '*NaverMapHostTeardownTest'
  ```

  Expected: compilation fails for the missing Naver shape records/converter and teardown helper.

- [ ] **Step 7: Implement common shape records and teardown**

  In `NaverMapOverlayShapes.kt`, define internal overlay kinds for marker, polyline, polygon,
  and circle; define an immutable `NaverMapOverlayShape` carrying native ID, marker position,
  marker title/snippet, marker visibility/z-index, points or circle center/radius, fill/stroke
  colors, width, z-index, and visibility.
  Implement a testable converter that accepts the four separate model lists, emits them in the
  documented marker/polyline/polygon/circle order, and a production overload that reads those
  lists from `MapContent`. Convert markers as well as shapes; do not approximate circles. In
  `NaverMapHostTeardown.kt`, use the Kakao pattern: attempt cleanup, engine/view finish, and
  reference clearing independently; preserve the first throwable and add subsequent throwables
  as suppressed.

- [ ] **Step 8: Add temporary platform actual scaffolding required by the KMP compiler**

  Add minimal `NaverMapPlatform.android.kt` and `NaverMapPlatform.ios.kt` actual declarations
  with the exact common signature. They may throw a clearly named internal
  `NaverMapException("Naver Maps platform surface is not implemented.")` when called, but must
  not import vendor SDK types; Task 4 and Task 6 replace them with the tested platform surfaces.
  The common configuration tests must not call `Content`, so this scaffold is only a compilation
  boundary and is removed/replaced before the provider leaf is considered implemented.

- [ ] **Step 9: Run common tests and commit the common slice**

  ```sh
  ANDROID_HOME="$HOME/Library/Android/sdk" \
  ANDROID_SDK_ROOT="$HOME/Library/Android/sdk" \
  ./gradlew :weft-map:navermap:testAndroidHostTest && \
  git add weft-map/navermap/src/commonMain weft-map/navermap/src/commonTest \
    weft-map/navermap/src/androidMain/kotlin/run/thespot/weft/map/navermap/NaverMapPlatform.android.kt \
    weft-map/navermap/src/iosMain/kotlin/run/thespot/weft/map/navermap/NaverMapPlatform.ios.kt && \
  git commit -m "feat: add Naver map provider contract"
  ```

  Expected: the common provider tests pass and no common source imports Android, UIKit, or Naver
  SDK types.

## Chunk 2: Android lifecycle, native adapter, and reconciliation

### Task 3: Build Android lifecycle/key initialization with failing host tests first

**Files:**
- Create: `weft-map/navermap/src/androidHostTest/kotlin/run/thespot/weft/map/navermap/NaverMapAndroidCoordinatorTest.kt`
- Create: `weft-map/navermap/src/androidHostTest/kotlin/run/thespot/weft/map/navermap/NaverMapAndroidSdkInitializerTest.kt`
- Create: `weft-map/navermap/src/androidMain/kotlin/run/thespot/weft/map/navermap/NaverMapAndroidCoordinator.kt`
- Create: `weft-map/navermap/src/androidMain/kotlin/run/thespot/weft/map/navermap/NaverMapAndroidSdkInitializer.kt`

`NaverMapAndroidCoordinator.kt` owns the `NaverMapAndroidHost` interface used by the fake
coordinator tests; the concrete `MapView` implementation is created in Task 4.

- [ ] **Step 1: Write failing coordinator tests around a fake host**

  Add tests named `startsAfterAttachAndResumeOnce`, `pauseBeforeStartDoesNotTouchHost`,
  `rendersLatestStateWhenMapBecomesReady`, `normalizesCameraToNativeBounds`,
  `lateCallbacksAreIgnoredAfterFinish`, `reportsNativeFailureOnce`,
  `cameraRollbackRestoresPreviousCamera`, and `cameraRollbackFailureIsSuppressed`. Define a fake
  `NaverMapAndroidHost` in the test source with
  explicit lifecycle/render call logs, configurable zoom and tilt bounds, and controllable
  ready/error callbacks. Assert exact target latitude/longitude, rounded/clamped zoom, clamped
  tilt, and normalized bearing passed to the fake host. Assert user pan is preserved when only
  content changes by passing a null camera to the host render call. In
  `NaverMapAndroidSdkInitializerTest.kt`, add
  `sameKeyInitializesOnce` and `conflictingKeyDoesNotReinitialize` using an injectable
  `initializeSdk: (String) -> Unit` seam and `resetForTest`.

- [ ] **Step 2: Run the host tests to observe the missing implementation failure**

  ```sh
  ANDROID_HOME="$HOME/Library/Android/sdk" \
  ANDROID_SDK_ROOT="$HOME/Library/Android/sdk" \
  ./gradlew :weft-map:navermap:testAndroidHostTest \
    --tests '*NaverMapAndroidCoordinatorTest' \
    --tests '*NaverMapAndroidSdkInitializerTest'
  ```

  Expected: compilation fails because the host interface, coordinator, and SDK initializer are
  absent.

- [ ] **Step 3: Implement the coordinator state machine**

  Model the Kakao states as `Created`, `Attached`, `Started`, `Ready`, `Paused`, and `Finished`.
  Track generation, attachment, resumed state, native readiness, current camera/content,
  last-rendered snapshots, native-out-of-sync flags, and one error callback per failure. Start
  only when attached and resumed; dispatch native ready/error callbacks to the main thread;
  normalize bearing to `[0, 360)`, round/clamp zoom to host bounds, clamp tilt to the native
  range, and skip camera updates when only content changed. On a failed render, mark the native
  state out of sync so the next render forces a full restore. Keep the coordinator's committed
  camera/content snapshot separate from the overlay reconciler's committed native-object snapshot;
  the host owns native camera capture/restore and the coordinator owns generation/out-of-sync
  recovery.

- [ ] **Step 4: Implement the process-local SDK initializer**

  In `NaverMapAndroidSdkInitializer.kt`, expose an internal `initialize(ncpKeyId,
  initializeSdk: (String) -> Unit)` function and `resetForTest`. Initialize the native
  `NaverMapSdk` from the Android host by passing a lambda that constructs its
  `NcpKeyClient(context, ncpKeyId)`; the registry itself remains context-free and testable.
  Initialize only once for the first key, accept repeated identical keys, and throw
  `NaverMapException` for a conflicting key without invoking the native initializer. Never
  include the key value in the exception message.

- [ ] **Step 5: Run focused Android tests and commit the coordinator slice**

  ```sh
  ANDROID_HOME="$HOME/Library/Android/sdk" \
  ANDROID_SDK_ROOT="$HOME/Library/Android/sdk" \
  ./gradlew :weft-map:navermap:testAndroidHostTest \
    --tests '*NaverMapAndroidCoordinatorTest' \
    --tests '*NaverMapAndroidSdkInitializerTest' && \
  git add weft-map/navermap/src/androidMain/kotlin/run/thespot/weft/map/navermap/NaverMapAndroidCoordinator.kt \
    weft-map/navermap/src/androidMain/kotlin/run/thespot/weft/map/navermap/NaverMapAndroidSdkInitializer.kt \
    weft-map/navermap/src/androidHostTest/kotlin/run/thespot/weft/map/navermap/NaverMapAndroidCoordinatorTest.kt \
    weft-map/navermap/src/androidHostTest/kotlin/run/thespot/weft/map/navermap/NaverMapAndroidSdkInitializerTest.kt && \
  git commit -m "feat: add Naver Android map lifecycle coordinator"
  ```

### Task 4: Implement Android MapView lifecycle bridge and native overlays with TDD

**Files:**
- Create: `weft-map/navermap/src/androidHostTest/kotlin/run/thespot/weft/map/navermap/NaverMapAndroidOverlayReconcilerTest.kt`
- Modify: `weft-map/navermap/src/androidHostTest/kotlin/run/thespot/weft/map/navermap/NaverMapAndroidCoordinatorTest.kt`
- Create: `weft-map/navermap/src/androidMain/kotlin/run/thespot/weft/map/navermap/NaverMapAndroidOverlayReconciler.kt`
- Create: `weft-map/navermap/src/androidMain/kotlin/run/thespot/weft/map/navermap/NaverMapAndroidOverlayStyle.kt`
- Create: `weft-map/navermap/src/androidMain/kotlin/run/thespot/weft/map/navermap/NaverMapAndroidMapViewHost.kt`
- Create: `weft-map/navermap/src/androidMain/kotlin/run/thespot/weft/map/navermap/NaverMapPlatform.android.kt`

- [ ] **Step 1: Write failing reconciler and lifecycle-bridge tests**

  Add fake native marker/shape layers and injectable seams for `MapView` construction/method calls,
  `SavedStateRegistryOwner` state consumption/provider registration/unregistration, and
  `ComponentCallbacks2` registration/unregistration. Use a fake
  `NaverMapAndroidViewLifecycle` seam; tests named `addsUpdatesAndRemovesByStableId`,
  `keepsNativeIdentityForStyleAndVisibilityUpdates`, `rollsBackAfterMutationFailure`,
  `marksLayerUnsynchronizedWhenRollbackFails`, `forwardsSavedStateAndLowMemoryCallbacks`,
  `restoresEmptyAndRestoredSavedStateBundles`, `unregistersSavedStateProvider`,
  `initializesSdkBeforeMapViewCreation`, `mapsLatLngAndCameraToNativeValues`,
  `disposesLifecycleBridgesIdempotently`, and `teardownRunsAllCleanupStepsAfterFailure`. Assert
  the reconciler stores committed snapshots, restores on failure, and makes the next render a
  full restore after rollback failure. Assert the fake lifecycle seam receives
  `MapView.onCreate`, `onStart`, `onResume`, `onPause`, `onStop`, `onDestroy`, saved-state
  registration/unregistration, and `onLowMemory` exactly once in the correct order without
  constructing a real SDK view.

- [ ] **Step 2: Run the tests and verify the expected missing-symbol failure**

  ```sh
  ANDROID_HOME="$HOME/Library/Android/sdk" \
  ANDROID_SDK_ROOT="$HOME/Library/Android/sdk" \
  ./gradlew :weft-map:navermap:testAndroidHostTest \
    --tests '*NaverMapAndroidOverlayReconcilerTest' \
    --tests '*NaverMapAndroidCoordinatorTest'
  ```

  Expected: compilation fails because the reconciler, style converter, and Android surface are
  absent.

- [ ] **Step 3: Implement the transactional Android reconciler**

  Define a small native-layer interface so tests do not construct SDK objects. Reconcile marker
  IDs and shape IDs separately but in one host render transaction. For Naver SDK objects map:

  - `MapMarker` to `Marker.position`, `captionText`, `subCaptionText`, `isVisible`, `zIndex`,
    and `map`.
  - `MapPolyline` to `PolylineOverlay.coords`, `color`, `width`, `isVisible`, `globalZIndex`,
    and `map`.
  - `MapPolygon` to `PolygonOverlay.coords`, `color`, `outlineColor`, `outlineWidth`,
    `isVisible`, `globalZIndex`, and `map`.
  - `MapCircle` to `CircleOverlay.center`, `radius`, `color`, `outlineColor`, `outlineWidth`,
    `isVisible`, `globalZIndex`, and `map`.

  Use `roundToInt()` for common z-index values, preserve alpha in `Color.toArgb()`, and multiply
  Android stroke widths by display density. Detach removed overlays by setting their map to null.
  Keep native object identity for updates where the SDK allows property mutation. Reconciliation
  rollback restores the previous marker/shape snapshot; the host render transaction also captures
  the native `NaverMap.cameraPosition` before `moveCamera` and restores it through a second
  `moveCamera` on failure, attaching marker/shape/camera rollback failures as suppressed
  exceptions. Add fake-host assertions for both successful camera rollback and camera rollback
  failure.

- [ ] **Step 4: Implement `MapView` host and Compose surface**

  In `NaverMapAndroidMapViewHost.kt`, initialize the SDK through
  `NaverMapAndroidSdkInitializer` before constructing the first `MapView`; test that ordering
  through the injectable factory. Then create `MapView`, call `onCreate` once with the restored
  or empty `Bundle`, register `getMapAsync`, and implement the host interface. Map `LatLng` to
  `com.naver.maps.geometry.LatLng`, construct the native `CameraPosition`, and call
  `moveCamera(CameraUpdate.toCameraPosition(...))`; assert the exact native target latitude/
  longitude, zoom, tilt, and normalized bearing through the fake map-view bridge. Derive current
  zoom/tilt bounds from the map before normalization. Keep the `NaverMap` native object only after
  `onMapReady`. Capture the prior native camera for rollback and use
  `runNaverMapHostTeardown` during finish.

  In `NaverMapPlatform.android.kt`, use `LocalLifecycleOwner`, `AndroidView`, and `DisposableEffect`.
  Define injectable `NaverMapAndroidMapViewFactory`, `NaverMapAndroidSavedStateBridge`, and
  `NaverMapAndroidComponentCallbacksBridge` interfaces in this platform file, with production
  adapters around `MapView`, `ViewTreeSavedStateRegistryOwner.get(mapView)`, and the owning
  `Context`. Tests inject fakes for all three, so no real SDK view or saved-state owner is needed.
  Consume a restored bundle before `onCreate`, register a stable key with
  `registerSavedStateProvider`, and remove it with `unregisterSavedStateProvider` during
  disposal. If the owner is not available until attach, defer registration while using an empty
  bundle for initial creation. Register a `ComponentCallbacks2` for `onLowMemory`; unregister
  both bridges on disposal. Forward attach/detach and all five lifecycle callbacks to the host/
  coordinator. Do not use a Fragment or expose Android classes through the common expect
  declaration.

- [ ] **Step 5: Run Android provider tests and commit**

  ```sh
  ANDROID_HOME="$HOME/Library/Android/sdk" \
  ANDROID_SDK_ROOT="$HOME/Library/Android/sdk" \
  ./gradlew :weft-map:navermap:testAndroidHostTest :weft-map:navermap:check && \
  git add weft-map/navermap/src/androidMain weft-map/navermap/src/androidHostTest && \
  git commit -m "feat: implement Naver Android map adapter"
  ```

  Expected: all common and Android host tests pass. Record any Naver SDK API mismatch as a code
  correction, not as a relaxed test.

## Chunk 3: iOS adapter, SwiftPM linkage, and consumers

### Task 5: Add iOS coordinator and overlay tests before native implementation

**Files:**
- Create: `weft-map/navermap/src/iosTest/kotlin/run/thespot/weft/map/navermap/NaverMapIosCoordinatorTest.kt`
- Create: `weft-map/navermap/src/iosTest/kotlin/run/thespot/weft/map/navermap/NaverMapIosOverlayReconcilerTest.kt`
- Create: `weft-map/navermap/src/iosMain/kotlin/run/thespot/weft/map/navermap/NaverMapIosCoordinator.kt`
- Create: `weft-map/navermap/src/iosMain/kotlin/run/thespot/weft/map/navermap/NaverMapIosOverlayReconciler.kt`

- [ ] **Step 1: Write failing iOS coordinator tests using a fake host**

  Add tests named `waitsForWindowAndNonZeroBounds`, `preventsDuplicatePreparation`,
  `pausesOnWindowDetachAndReactivatesOnAttach`, `rendersLatestContentWhenReady`,
  `normalizesCameraAndPreservesPanOnContentOnlyUpdate`, `ignoresCallbacksAfterReset`, and
  `reportsOnlyOneErrorPerGeneration`. The fake host must record `prepare`, `activate`, `render`,
  `pause`, and `reset`, deliver ready callbacks with explicit zoom bounds, and allow render
  failures.

- [ ] **Step 2: Run the iOS tests to verify the expected missing implementation failure**

  ```sh
  ./gradlew :weft-map:navermap:compileTestKotlinIosSimulatorArm64
  ```

  Expected: compilation fails because `NaverMapIosCoordinator` and its host contract do not
  exist. Do not add the native SDK imports yet.

- [ ] **Step 3: Implement the iOS coordinator state machine**

  Mirror the Kakao coordinator's attach/layout/readiness states: `Created`, `AttachedZeroBounds`,
  `Prepared`, `Ready`, `Paused`, and `Reset`. Gate preparation on a non-zero window size, use a
  generation counter to ignore stale callbacks, keep latest camera/content snapshots before
  readiness, normalize bearing, clamp zoom/tilt, and render camera only when changed. Wrap host
  failures in `NaverMapException` and invoke `onError` only while the generation is live.

- [ ] **Step 4: Write failing iOS reconciliation tests**

  Add fake marker/overlay layers and tests named `addsUpdatesAndRemovesNativeOverlaysById`,
  `keepsCircleAsGeographicCircle`, `preservesColorAlphaAndPointWidth`, `restoresAfterRenderFailure`,
  and `clearsDelegatesAndOverlaysOnReset`. Assert Naver overlay classes receive the exact
  provider-neutral geometry/style snapshot and rollback is attempted after partial mutation.

- [ ] **Step 5: Run the focused iOS tests and commit the pure coordinator slice**

  ```sh
  ./gradlew :weft-map:navermap:compileTestKotlinIosSimulatorArm64
  git add weft-map/navermap/src/iosMain/kotlin/run/thespot/weft/map/navermap/NaverMapIosCoordinator.kt \
    weft-map/navermap/src/iosMain/kotlin/run/thespot/weft/map/navermap/NaverMapIosOverlayReconciler.kt \
    weft-map/navermap/src/iosTest
  git commit -m "feat: add Naver iOS map coordinator"
  ```

  Expected: the iOS test sources compile; runtime test execution may be skipped on `macos_x64`.

### Task 6: Implement the Naver iOS SwiftPM host and verify native linkage

**Files:**
- Create: `weft-map/navermap/src/iosMain/kotlin/run/thespot/weft/map/navermap/NaverMapIosHost.kt`
- Create: `weft-map/navermap/src/iosMain/kotlin/run/thespot/weft/map/navermap/NaverMapPlatform.ios.kt`
- Modify: `.swiftpm-locks/default/swiftImport/Package.resolved`
- Create/modify: generated SwiftPM linkage package under `weft-map/navermap/build/` only through the Gradle task

- [ ] **Step 1: Compile the SwiftPM import boundary before writing the host**

  ```sh
  ./gradlew :weft-map:navermap:generateSyntheticLinkageSwiftPMImportProjectForCinteropsAndLdDump \
    :weft-map:navermap:cinteropSwiftPMImportIosSimulatorArm64
  ```

  Expected: the exact `NMapsMap`/`NMapsGeometry` generated import namespace is available. Inspect
  generated cinterop declarations only to confirm names; keep generated output out of source
  control except the repository's persisted lock file.

- [ ] **Step 2: Implement the iOS host with official Naver APIs**

  Create `NMFMapView`, configure `NMFAuthManager.shared().client` with
  `NMFNcpKeyClient(ncpKeyId)`, and implement the host contract. Use `UIKitView` through a custom
  `UIView` subclass whose `didMoveToWindow` calls coordinator attach/detach and whose
  `layoutSubviews` forwards non-zero bounds. Use `NMFCameraPosition`/`NMFCameraUpdate` for camera
  changes; use `NMFMarker`, `NMFPolylineOverlay`, `NMFPolygonOverlay`, and `NMFCircleOverlay`
  for native content. Set `mapView` only after geometry is assigned, detach all objects by
  setting `mapView = null`, remove delegates before release, and use
  `runNaverMapHostTeardown` for terminal cleanup.

- [ ] **Step 3: Implement the UIKit Compose surface**

  In `NaverMapPlatform.ios.kt`, key the surface by `ncpKeyId`, remember the custom host/surface,
  dispose it from both `DisposableEffect` and `UIKitView.onRelease`, and forward state/content/
  `onError` updates to the coordinator. Keep all `NMF*`, `NMGLatLng`, `UIView`, and UIKit imports
  in `iosMain`.

- [ ] **Step 4: Compile and link both Apple targets**

  ```sh
  ./gradlew :weft-map:navermap:compileKotlinIosSimulatorArm64 \
    :weft-map:navermap:compileKotlinIosArm64 \
    :weft-map:navermap:linkDebugFrameworkIosSimulatorArm64
  ```

  Expected: both Kotlin/Native targets compile and the framework links against the dynamic Naver
  framework. If the Naver package cannot be imported or linked by the pinned Kotlin toolchain,
  stop and record the exact compiler/linker error as a completion blocker; do not ship an Android-
  only PR under the approved scope.

- [ ] **Step 5: Commit the iOS native adapter and persisted package lock**

  ```sh
  git add weft-map/navermap/src/iosMain .swiftpm-locks/default/swiftImport/Package.resolved
  git commit -m "feat: implement Naver iOS map adapter"
  ```

### Task 7: Add separate Android/iOS consumer validation

**Files:**
- Create: `harness/fixtures/weft-navermap-consumer/settings.gradle.kts`
- Create: `harness/fixtures/weft-navermap-consumer/build.gradle.kts`
- Create: `harness/fixtures/weft-navermap-consumer/src/main/AndroidManifest.xml`
- Create/modify: `harness/fixtures/weft-navermap-ios-consumer/README.md`
- Create: `harness/fixtures/weft-navermap-ios-consumer/WeftNaverConsumer.xcodeproj/...` as generated by the existing linkage task
- Modify: `scripts/test_weft.py` or fixture verification code only if the new fixture needs explicit coverage

- [ ] **Step 1: Add the Android consumer fixture before relying on publication metadata**

  Use the existing Kakao consumer fixture shape, but depend on
  `implementation(platform("run.thespot.weft:weft-bom:${weftVersion.get()}"))` and
  `implementation("run.thespot.weft:weft-navermap")`. Add `maven("https://repository.map.naver.com/archive/maven")`
  and `mavenLocal()` to the fixture repositories. Add a minimal Android manifest/application
  declaration with the Naver `NCP_KEY_ID` metadata placeholder, without committing a real key.

- [ ] **Step 2: Resolve the Android fixture against local publication outputs**

  ```sh
  ./gradlew :weft-map:navermap:publishAllPublicationsToMavenLocal \
    :weft-bom:publishAllPublicationsToMavenLocal
  ./gradlew -p harness/fixtures/weft-navermap-consumer assemble
  ```

  Expected: the fixture resolves the BOM and `weft-navermap` without declaring a direct Naver SDK
  dependency. If a task name differs on this KMP version, use the leaf's generated publication
  task shown by `./gradlew :weft-map:navermap:tasks` and record the exact command in the fixture
  README.

- [ ] **Step 3: Generate and build the iOS consumer fixture**

  Configure the fixture's Xcode project to link `WeftNaverMap.framework` and the generated
  SwiftPM linkage package. Run:

  ```sh
  XCODEPROJ_PATH="$PWD/harness/fixtures/weft-navermap-ios-consumer/WeftNaverConsumer.xcodeproj" \
  ./gradlew :weft-map:navermap:integrateLinkagePackage
  xcodebuild -project harness/fixtures/weft-navermap-ios-consumer/WeftNaverConsumer.xcodeproj \
    -scheme WeftNaverConsumer -sdk iphonesimulator build
  ```

  Expected: the app builds for the Apple Silicon simulator and includes the dynamic Naver
  framework/resource bundle. On the Intel host, record compile/link results and the runtime test
  skip; if the consumer cannot link, leave the branch blocked as required by the spec.

- [ ] **Step 4: Commit consumer fixtures and documentation**

  ```sh
  git add harness/fixtures/weft-navermap-consumer harness/fixtures/weft-navermap-ios-consumer
  git commit -m "test: validate Naver map consumers"
  ```

## Chunk 4: Documentation, full verification, review, and delivery

### Task 8: Finish fixture documentation and repository verification

**Files:**
- Modify: `harness/fixtures/weft-navermap-consumer/README.md`
- Modify: `harness/fixtures/weft-navermap-ios-consumer/README.md`

- [ ] **Step 1: Document consumer fixture commands and platform gates**

  Add English README files for the Android and iOS fixtures with exact local publication,
  `assemble`, `integrateLinkagePackage`, and `xcodebuild` commands. State that placeholders are
  used for client IDs, no runtime credentials are committed, and an iOS linkage/consumer failure
  blocks feature completion rather than being silently downgraded to Android-only support.

- [ ] **Step 2: Run repository checks after documentation and build changes**

  ```sh
  ANDROID_HOME="$HOME/Library/Android/sdk" \
  ANDROID_SDK_ROOT="$HOME/Library/Android/sdk" \
  ./gradlew verify
  ./scripts/verify.sh
  git diff --check
  ```

  Expected: Gradle verification includes both provider leaves, BOM constraints, task-graph checks,
  POM checks, and all available common/Android/iOS compile tests; the script reports `Weft
  verification passed`. iOS runtime tests may report `SKIPPED` on `macos_x64`, but no iOS compile,
  link, or consumer gate may be skipped without an explicit blocker.

- [ ] **Step 3: Commit fixture documentation**

  ```sh
  git add harness/fixtures/weft-navermap-consumer/README.md \
    harness/fixtures/weft-navermap-ios-consumer/README.md
  git commit -m "docs: record Naver map consumer validation"
  ```

### Task 9: Independent code review and final branch checks

**Files:**
- Review all changes in `feature/navermap` against `origin/main`
- Create: `docs/superpowers/reviews/2026-11-26-weft-map-navermap-review.md`

- [ ] **Step 1: Inspect the complete diff and generated artifacts**

  ```sh
  git diff --stat origin/main...HEAD
  git diff --check origin/main...HEAD
  git status --short
  ```

  Confirm no real SDK keys, local build outputs, or unrelated worktree changes are tracked.

- [ ] **Step 2: Dispatch an independent code reviewer**

  Ask a read-only reviewer to inspect the exact `origin/main...HEAD` diff, focusing on common API
  boundaries, Android/iOS lifecycle teardown, native overlay identity/rollback, publication POMs,
  SwiftPM dynamic framework delivery, and test evidence. The reviewer must report Critical,
  Important, and Minor findings with file/line references and must not edit the worktree.

- [ ] **Step 3: Fix review findings with regression tests first**

  For every Critical or Important issue, add or update a failing focused test, observe the failure,
  implement the smallest fix, rerun the focused test, then rerun the affected leaf check. Do not
  suppress a reviewer finding without recording the technical reason in the review report.

- [ ] **Step 4: Run final verification before any completion claim**

  ```sh
  ANDROID_HOME="$HOME/Library/Android/sdk" \
  ANDROID_SDK_ROOT="$HOME/Library/Android/sdk" \
  ./gradlew :weft-core:check :weft-map:kakaomap:check :weft-map:navermap:check \
    verifyNaverMapPublication verifyWeftBomPublication verify
  ./scripts/verify.sh
  ```

  Expected: every requested task exits successfully; iOS runtime tests may be skipped only because
  of the known `macos_x64` host limitation, while iOS compile/link and consumer evidence remain
  recorded.

- [ ] **Step 5: Write the review report and commit it**

  ```sh
  git add docs/superpowers/reviews/2026-11-26-weft-map-navermap-review.md
  git commit -m "docs: record Naver map provider review"
  ```

- [ ] **Step 6: Stop before publication/merge unless explicitly requested**

  Report the branch, commits, verification commands/results, PR readiness, and any iOS platform
  blocker. Do not publish to Maven Central. Do not merge the GitHub PR unless the user explicitly
  asks for merge after reviewing the result.

---

## Execution notes

- Follow @superpowers:subagent-driven-development for independent implementation/review tasks,
  but keep all Gradle integration and cross-platform wiring with one owner to avoid conflicting
  edits.
- Follow @superpowers:test-driven-development literally: every production Kotlin behavior starts
  with a focused failing test and the failure is observed before implementation.
- Use the existing `feature/navermap` worktree at
  `/Users/easternkite/.openclaw/workspace/main/weft/.worktrees/feature-navermap`; do not create a
  second clone or reset the user's local `main` branch.
- No Maven Central publication, credentials, or external release actions are part of this plan.
