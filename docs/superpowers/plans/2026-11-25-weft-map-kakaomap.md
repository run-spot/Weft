# Weft KakaoMap Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the provider-neutral Compose map contract in `:weft-core` and the first Kakao Maps provider in `:weft-map:kakaomap`, including Android lifecycle/rendering and the iOS SwiftPM import boundary.

**Architecture:** `:weft-core` owns immutable map models, `MapPlugin`, `WeftMap`, and a private Compose child-composition pipeline based on `MapApplier` and mutable `MapNode` children. `:weft-map:kakaomap` owns configuration, plugin installation, and an expect/actual platform surface; Android adapts `MapView`, while iOS adapts `KMController`/`KMViewContainer` through Kotlin SwiftPM import. Native coordinators consume atomic `MapContent` snapshots and expose fake host seams for deterministic tests.

**Tech Stack:** Kotlin Multiplatform 2.4.21, Compose Multiplatform 1.12.1, AGP 9.3.1, Compose Runtime/UI/Foundation, Kakao Maps Android SDK 2.15.2, KakaoMapsSDK-SPM 2.12.19, Kotlin test, Android host tests, and iOS Kotlin/Native compile/link checks.

**Spec:** `docs/superpowers/specs/2026-11-25-weft-map-kakaomap-design.md`

---

## Chunk 1: Core map contract and Compose materialization

### Task 1: Add the core map API models and plugin contract

**Files:**
- Create: `weft-core/src/commonMain/kotlin/run/thespot/weft/core/map/MapModels.kt`
- Create: `weft-core/src/commonMain/kotlin/run/thespot/weft/core/map/MapPlugin.kt`
- Create: `weft-core/src/commonMain/kotlin/run/thespot/weft/core/map/MapScope.kt`
- Modify: `weft-core/build.gradle.kts`
- Modify: `weft-core/AGENTS.md`
- Modify: `gradle/libs.versions.toml`
- Test: `weft-core/src/commonTest/kotlin/run/thespot/weft/core/map/MapModelsTest.kt`

- [ ] **Step 1: Write failing validation and API-shape tests**
  - Test finite latitude/longitude and their `-90..90`/`-180..180` ranges.
  - Test non-negative finite zoom, finite bearing, and `0..90` tilt.
  - Test non-blank marker IDs and immutable `WeftMapState` construction.
  - Test `MapPlugin.renderer` accepts the provider-neutral composable renderer signature.
  - Test `MapContent` makes a defensive copy of the marker list.

- [ ] **Step 2: Run the focused test and verify RED**
  - Run: `./gradlew :weft-core:testAndroidHostTest --tests 'run.thespot.weft.core.map.MapModelsTest'`
  - Expected: compilation/test failure because the map package and public types do not exist.

- [ ] **Step 3: Implement the minimal common API**
  - Add `LatLng`, `CameraPosition`, `MapMarker`, `WeftMapState`, and `MapContent` with explicit API visibility and deterministic validation messages.
  - Add `MapPlugin`, `WeftMapDsl`, and the `MapScope` marker receiver declaration.
  - Add `compose-ui` and `compose-foundation` catalog aliases in `gradle/libs.versions.toml` and use them as `api` dependencies in `:weft-core:commonMain`; retain Compose Runtime.
  - Update `weft-core/AGENTS.md` and the existing build comment to record that the public map contract now requires Compose UI/Foundation in addition to Runtime; keep vendor and platform types out of commonMain.

- [ ] **Step 4: Run the focused test and verify GREEN**
  - Run the same `:weft-core:testAndroidHostTest --tests ...` command.
  - Expected: all model and API-shape tests pass.

- [ ] **Step 5: Commit**
  - `git add gradle/libs.versions.toml weft-core/AGENTS.md weft-core/build.gradle.kts weft-core/src/commonMain weft-core/src/commonTest`
  - `git commit -m "feat(core): add provider-neutral map contract"`

### Task 2: Implement the internal MapNode/MapApplier tree

**Files:**
- Create: `weft-core/src/commonMain/kotlin/run/thespot/weft/core/map/composition/MapNode.kt`
- Create: `weft-core/src/commonMain/kotlin/run/thespot/weft/core/map/composition/MapApplier.kt`
- Create: `weft-core/src/commonMain/kotlin/run/thespot/weft/core/map/composition/MapMarkerNode.kt`
- Create: `weft-core/src/commonMain/kotlin/run/thespot/weft/core/map/composition/MapContentBuilder.kt`
- Modify: `weft-core/src/commonMain/kotlin/run/thespot/weft/core/map/MapScope.kt`
- Test: `weft-core/src/commonTest/kotlin/run/thespot/weft/core/map/composition/MapNodeTest.kt`
- Test: `weft-core/src/commonTest/kotlin/run/thespot/weft/core/map/composition/MapApplierTest.kt`

- [ ] **Step 1: Write failing node/applier tests**
  - Test attach/detach callback propagation, insert/remove/clear, and move preserving node identity.
  - Test detached or removed nodes no longer invoke the invalidation callback.
  - Test clearing the root detaches every descendant and prevents descendant invalidation.
  - Test `MapMarkerNode` updates in place and invalidates once only when its model changes.
  - Test the builder preserves order and rejects duplicate marker IDs before producing a snapshot.
  - Test `MapScope.Marker` uses `key(marker.id)` and `ComposeNode` so same-ID updates reuse a node.

- [ ] **Step 2: Run tests and verify RED**
  - Run: `./gradlew :weft-core:testAndroidHostTest --tests 'run.thespot.weft.core.map.composition.*'`
  - Expected: compilation failure because the internal composition classes are absent.

- [ ] **Step 3: Implement the minimal tree and marker DSL**
  - Follow the RunTheSpot `AbstractApplier`/node boundary, keeping all classes `internal`.
  - Implement `insertTopDown`, `insertBottomUp`, `remove`, `move`, and `onClear` against the root node.
  - Make `remove`, `detach`, and `onClear` recursively clear invalidation callbacks before dropping nodes.
  - Implement the immutable marker model update and snapshot builder.

- [ ] **Step 4: Run tests and verify GREEN**
  - Run the same `:weft-core:testAndroidHostTest --tests ...` command.
  - Expected: all node, applier, identity, and duplicate-ID tests pass.

- [ ] **Step 5: Commit**
  - `git add weft-core/src/commonMain/kotlin/run/thespot/weft/core/map weft-core/src/commonTest/kotlin/run/thespot/weft/core/map`
  - `git commit -m "feat(core): add Compose map node tree"`

### Task 3: Add child composition publication and WeftMap delegation

**Files:**
- Create: `weft-core/src/commonMain/kotlin/run/thespot/weft/core/map/MapContentComposition.kt`
- Create: `weft-core/src/commonMain/kotlin/run/thespot/weft/core/map/WeftMap.kt`
- Test: `weft-core/src/commonTest/kotlin/run/thespot/weft/core/map/MapContentCompositionTest.kt`
- Test: `weft-core/src/androidHostTest/kotlin/run/thespot/weft/core/map/WeftMapAndroidTest.kt` (or the repository's configured Android host-test source set)

- [ ] **Step 1: Write failing composition tests**
  - Build a small `Recomposer`/frame-clock harness that proves initial marker content publishes after the child composition runs and that the child reads `invalidationToken` before invoking content.
  - Change a marker model and prove `MapApplier` invalidation causes a new immutable snapshot.
  - Remove and reorder markers and prove the snapshot is atomic and ordered.
  - Submit duplicate IDs and prove the last valid snapshot remains published while the deterministic exception propagates.
  - Dispose the composition and prove late invalidation cannot publish.
  - In an Android host Compose test, install a fake `MapPlugin`, call `WeftMap`, and prove the plugin receives the state, full-size child modifier, content, and error callback.

- [ ] **Step 2: Run tests and verify RED**
  - Run: `./gradlew :weft-core:testAndroidHostTest --tests 'run.thespot.weft.core.map.MapContentCompositionTest'`
  - Run: `./gradlew :weft-core:testAndroidHostTest --tests '*WeftMapAndroidTest'`
  - Expected: missing composable/publisher failures before implementation.

- [ ] **Step 3: Implement composition state and public WeftMap**
  - Use the parent composition context, `Composition`, `DisposableEffect`, `SideEffect`, and `LaunchedEffect` as specified.
  - Make `published` an observable `mutableStateOf(MapContent(emptyList()), neverEqualPolicy())` and make `invalidationToken` observable so node mutations trigger child recomposition; read the token immediately before invoking `currentContent`.
  - Build and validate a local next snapshot, then assign `published.value` only after validation succeeds; dispose state before disposing the child composition.
  - Implement `WeftMap` with the caller modifier around a `Box` and delegate through `LocalWeft.current.get<MapPlugin>()` using `Modifier.fillMaxSize()` for the provider surface.

- [ ] **Step 4: Run tests and verify GREEN**
  - Run both focused `:weft-core:testAndroidHostTest --tests ...` commands.
  - Expected: publication, disposal, delegation, and modifier tests pass.

- [ ] **Step 5: Commit**
  - `git add weft-core/src/commonMain/kotlin/run/thespot/weft/core/map weft-core/src/commonTest/kotlin/run/thespot/weft/core/map weft-core/src/androidHostTest`
  - `git commit -m "feat(core): add Compose map content and WeftMap"`

## Chunk 2: Kakao common provider and Android adapter

### Task 4: Register the nested Kakao map leaf module

**Files:**
- Modify: `settings.gradle.kts`
- Modify: `settings.gradle.kts` dependency resolution repositories
- Modify: `gradle/libs.versions.toml`
- Create: `weft-map/kakaomap/build.gradle.kts`
- Delete: `weft-map/build.gradle.kts`
- Test/build check: Gradle project task graph

- [ ] **Step 1: Write the failing module-resolution check**
  - Run: `./gradlew :weft-map:kakaomap:tasks`
  - Expected: project-selection failure because `:weft-map:kakaomap` is not included yet.

- [ ] **Step 2: Register the nested module and platform repositories**
  - Replace the flat include with `include(":weft-map:kakaomap")`; Gradle creates `:weft-map` as a namespace parent without a publication build file.
  - Delete the old `weft-map/build.gradle.kts` so the removed `weft-map` artifact cannot remain in the task graph.
  - Add Kakao's exact Maven repository `https://devrepo.kakao.com/nexus/repository/kakaomap-releases/` to `dependencyResolutionManagement`, never to a project repository.
  - Add the `kakao-maps-android` alias (`com.kakao.maps.open:android:2.15.2`) and `lifecycle-runtime-compose` alias (`org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose:2.10.0-alpha05`); retain the Compose UI/Foundation aliases created in Task 1.
  - Create the leaf build file with `weft.kmp-library`, `weft.compose`, and `weft.maven-publish`, common `api(project(":weft-core"))`, Android `implementation(libs.compose.ui)`, `implementation(libs.kakao.maps.android)`, and `implementation(libs.lifecycle.runtime.compose)`, plus explicit `weft-kakaomap` publication coordinates.

- [ ] **Step 3: Run the module-resolution check and verify GREEN**
  - Run: `./gradlew :weft-map:kakaomap:tasks`
  - Expected: the nested project is listed and its KMP/host-test/publication tasks are available.

- [ ] **Step 4: Commit**
  - `git add settings.gradle.kts gradle/libs.versions.toml weft-map/build.gradle.kts weft-map/kakaomap/build.gradle.kts`
  - `git commit -m "build: register Kakao map leaf module"`

### Task 5: Add the Kakao provider configuration and common platform seam

**Files:**
- Create: `weft-map/kakaomap/src/commonMain/kotlin/run/thespot/weft/map/kakaomap/KakaoMap.kt`
- Create: `weft-map/kakaomap/src/commonMain/kotlin/run/thespot/weft/map/kakaomap/KakaoMapException.kt`
- Create: `weft-map/kakaomap/src/commonMain/kotlin/run/thespot/weft/map/kakaomap/KakaoMapPlatform.kt`
- Create: `weft-map/kakaomap/src/androidMain/kotlin/run/thespot/weft/map/kakaomap/KakaoMapPlatform.android.kt` (minimal compile-only actual until Task 6)
- Test: `weft-map/kakaomap/src/commonTest/kotlin/run/thespot/weft/map/kakaomap/KakaoMapTest.kt`

- [ ] **Step 1: Write failing provider tests**
  - Test `KakaoMapConfig.nativeAppKey` defaults to blank and blank keys are rejected by `prepare`.
  - Test the prepared plugin exposes `type = MapPlugin::class`, `pluginName = "KakaoMap"`, and no health probes.
  - Test `Weft { install(KakaoMap) { ... } }.healthChecks()` is empty.
  - Test `Weft { install(KakaoMap) { ... } }` resolves a `MapPlugin` and a second map provider is rejected by the existing duplicate type exception.
  - Test `KakaoMapException` preserves a cause.

- [ ] **Step 2: Run tests and verify RED**
  - Run: `./gradlew :weft-map:kakaomap:testAndroidHostTest --tests 'run.thespot.weft.map.kakaomap.KakaoMapTest'`
  - Expected: compilation failure because the provider common types and Android expect/actual implementation do not exist; the nested project itself is already registered by Task 4.

- [ ] **Step 3: Implement provider and expect surface**
  - Configure `api(project(":weft-core"))` and Compose in commonMain.
  - Implement side-effect-free `prepare`/`install`, key validation, and `healthCheck = emptyList()`.
  - Implement the common plugin's `Content` by passing `rememberMapContent(content)` to a private expect `KakaoMapPlatformSurface`.
  - Add a minimal Android actual that compiles the common provider tests and reports an unsupported-surface error; Task 6 replaces this body with the real MapView host.

- [ ] **Step 4: Run tests and verify GREEN**
  - Run: `./gradlew :weft-map:kakaomap:testAndroidHostTest --tests 'run.thespot.weft.map.kakaomap.KakaoMapTest'`
  - Expected: all configuration, registration, and duplicate-plugin tests pass.

- [ ] **Step 5: Commit**
  - `git add weft-map/kakaomap`
  - `git commit -m "feat(kakaomap): add common provider binding"`

### Task 6: Implement Android Kakao host and lifecycle coordinator

**Files:**
- Modify: `weft-map/kakaomap/src/androidMain/kotlin/run/thespot/weft/map/kakaomap/KakaoMapPlatform.android.kt`
- Create: `weft-map/kakaomap/src/androidMain/kotlin/run/thespot/weft/map/kakaomap/KakaoMapAndroidCoordinator.kt`
- Create: `weft-map/kakaomap/src/androidMain/kotlin/run/thespot/weft/map/kakaomap/KakaoMapAndroidHost.kt`
- Test: `weft-map/kakaomap/src/androidHostTest/kotlin/run/thespot/weft/map/kakaomap/KakaoMapAndroidCoordinatorTest.kt`

- [ ] **Step 1: Write failing fake-host lifecycle and mapping tests**
  - Test `Created -> Attached -> Started -> Ready -> Paused -> Finished` call order and idempotent pause/finish.
  - Test pause/detach before start is recorded without calling native pause, then resume starts once.
  - Test same-key SDK initialization is idempotent, different-key initialization reports one error, and late ready/error callbacks after a generation change are ignored.
  - Test pre-ready camera/content are retained and rendered once after ready bounds arrive.
  - Test bearing normalization, zoom rounding/clamping to fake min/max, tilt clamping, and marker add/update/remove by ID.
  - Test native render failures are wrapped as `KakaoMapException` and callback replacement is honored.
  - Test null `onError` keeps the last valid snapshot, post-Finished events are no-ops, and all error callbacks are delivered on the platform main thread.

- [ ] **Step 2: Run tests and verify RED**
  - Run: `./gradlew :weft-map:kakaomap:testAndroidHostTest --tests '*KakaoMapAndroidCoordinatorTest'`
  - Expected: compilation failure because the Android coordinator/host do not exist.

- [ ] **Step 3: Implement the fakeable coordinator and real MapView host**
  - Keep Kakao Android types in `androidMain`; use `AndroidView`, `LocalLifecycleOwner`, and `LifecycleEventObserver`.
  - Initialize `KakaoMapSdk` once per key, adapt `MapView.start/resume/pause/finish`, and pass native zoom bounds through the ready callback.
  - Render camera and `LabelLayer` markers in one coordinator pass, retaining snippet but not rendering it.
  - Invalidate generation before disposal and route native errors once per generation to the current `onError`.
  - Define `KakaoMapAndroidHost.start(appKey, initialCamera, onReady: (KakaoMapZoomBounds) -> Unit, onError)`, define `KakaoMapZoomBounds(min, max)`, and make the real host read native min/max levels while the fake host records and asserts them.

- [ ] **Step 4: Run tests and verify GREEN**
  - Run: `./gradlew :weft-map:kakaomap:testAndroidHostTest --tests '*KakaoMapAndroidCoordinatorTest'`
  - Expected: all fake-host lifecycle, camera, marker, callback, and disposal tests pass.

- [ ] **Step 5: Commit**
  - `git add weft-map/kakaomap/src/androidMain weft-map/kakaomap/src/androidHostTest`
  - `git commit -m "feat(kakaomap): add Android map adapter"`

## Chunk 3: iOS SwiftPM adapter, build integration, and release checks

### Task 7: Add iOS SwiftPM import and fakeable coordinator

**Files:**
- Modify: `weft-map/kakaomap/build.gradle.kts`
- Create: `weft-map/kakaomap/src/iosMain/kotlin/run/thespot/weft/map/kakaomap/KakaoMapPlatform.ios.kt`
- Create: `weft-map/kakaomap/src/iosMain/kotlin/run/thespot/weft/map/kakaomap/KakaoMapIosCoordinator.kt`
- Create: `weft-map/kakaomap/src/iosMain/kotlin/run/thespot/weft/map/kakaomap/KakaoMapIosHost.kt`
- Test: `weft-map/kakaomap/src/iosTest/kotlin/run/thespot/weft/map/kakaomap/KakaoMapIosCoordinatorTest.kt`
- Create: `harness/fixtures/weft-kakaomap-ios-consumer/WeftKakaoConsumer.xcodeproj/project.pbxproj`
- Create: `harness/fixtures/weft-kakaomap-ios-consumer/WeftKakaoConsumer.xcodeproj/xcshareddata/xcschemes/WeftKakaoConsumer.xcscheme`
- Create: `harness/fixtures/weft-kakaomap-ios-consumer/WeftKakaoConsumer/Info.plist`
- Create: `harness/fixtures/weft-kakaomap-ios-consumer/WeftKakaoConsumer/AppDelegate.swift`
- Create/modify: `.swiftpm-locks/default/swiftImport/Package.resolved` and the generated linkage package under `harness/fixtures/weft-kakaomap-ios-consumer/SwiftPM/`

- [x] **Step 1: Write failing fake-host iOS coordinator tests**
  - Test zero-bounds attach defers preparation, non-zero layout prepares once, and window detach/reattach pauses/reactivates.
  - Test `MapPoint` coordinate mapping, `roundToInt` plus zoom min/max clamping, normalized bearing and clamped tilt conversion with `PI / 180`, and non-animated camera updates.
  - Test marker same-ID position/title updates, removal, ID-change remove/add behavior, and deterministic style/layer identity.
  - Test same process-global key is a no-op, a different key calls `onError`, `authenticationFailed`/`addViewFailed` route `KakaoMapException`, and late callbacks after reset are ignored.
  - Test render errors, callback replacement, idempotent reset, and once-per-generation main-thread error delivery.

- [x] **Step 2: Add the SwiftPM configuration and run the compatibility gate**
  - Configuration-only change (before production iOS source): add `swiftPMDependencies` with `discoverClangModulesImplicitly = true`, `swiftPackage(url = url("https://github.com/kakao-mapsSDK/KakaoMapsSDK-SPM.git"), version = exact("2.12.19"), products = listOf(product("KakaoMapsSDK-SPM", platforms = setOf(iOS()))))`, and `iosMinimumDeploymentTarget.set("13.0")`.
  - Run: `./gradlew :weft-map:kakaomap:fetchSyntheticImportProjectPackages`
  - Run: `./gradlew :weft-map:kakaomap:compileKotlinIosArm64`
  - Expected: either the documented SwiftPM/Kotlin 2.4.21 incompatibility is reported, or the compile proceeds to the expected missing-iOS-source/actual failure. If the DSL/import namespace is unavailable, stop and report the exact error; do not change the root Kotlin version silently.

- [x] **Step 3: Implement the iOS SwiftPM boundary**
  - Keep the exact SwiftPM configuration from Step 2; the generated imports must resolve under `swiftPMImport` for the provider's group/project namespace.
  - Import generated `swiftPMImport` symbols only in `iosMain`; keep `KMController`, `KMViewContainer`, `KakaoMap`, `MapPoint`, `MapviewInfo`, `SDKInitializer`, and UIKit types out of commonMain.
  - Implement the fakeable coordinator state machine, one default Poi style/layer per map view, camera conversion, resource/error handling, and generation invalidation.
  - Kotlin 2.4 raises its default Apple minimum to iOS 15; set `-Xoverride-konan-properties=minVersion.ios=13.0` on the iOS binaries and verify each linked framework's `Info.plist` reports `MinimumOSVersion=13.0`.
  - Run `XCODEPROJ_PATH="$PWD/harness/fixtures/weft-kakaomap-ios-consumer/WeftKakaoConsumer.xcodeproj" ./gradlew :weft-map:kakaomap:integrateLinkagePackage`, commit the updated Xcode project, the generated `harness/fixtures/weft-kakaomap-ios-consumer/KotlinMultiplatformLinkedPackage/` package, and `.swiftpm-locks/default/swiftImport/Package.resolved`. Kakao's vendor manifest copies `KakaoMapsSDKBundle.bundle/assets`; Xcode wraps these in `KakaoMapsSDK-SPM_KakaoMapsSDK-SPM.bundle`, so verify that generated resource bundle and its `assets/KakaoMapsSDKBundle.plist` inside the built app rather than searching for the original bundle directory.

- [x] **Step 4: Run iOS validation and verify GREEN where the environment supports it**
  - Run: `./gradlew :weft-map:kakaomap:iosSimulatorArm64Test` on Apple Silicon; Kotlin disables simulator runtime tests on `x86_64` hosts. The `iosArm64Test` shorthand is ambiguous in Kotlin 2.4.21, so do not use it.
  - Run: `./gradlew :weft-map:kakaomap:compileKotlinIosArm64 :weft-map:kakaomap:compileKotlinIosSimulatorArm64`
  - Run: `xcodebuild -project harness/fixtures/weft-kakaomap-ios-consumer/WeftKakaoConsumer.xcodeproj -scheme WeftKakaoConsumer -sdk iphonesimulator build`
  - Verify the built app contains `KakaoMapsSDK-SPM_KakaoMapsSDK-SPM.bundle/assets/KakaoMapsSDKBundle.plist` and its `Info.plist` reports iOS 13.0.
  - Expected: fake-host tests, both generated imports, Xcode consumer build, and resource assertion pass where the host supports runtime execution; on `x86_64`, record simulator tests as skipped while still checking source compilation, framework link, and consumer build.

- [x] **Step 5: Commit**
  - `git add weft-map/kakaomap/build.gradle.kts weft-map/kakaomap/src/iosMain weft-map/kakaomap/src/iosTest harness/fixtures/weft-kakaomap-ios-consumer .swiftpm-locks`
  - `git commit -m "feat(kakaomap): add iOS SwiftPM adapter"`

### Task 8: Rewire BOM, verification, and documentation

**Files:**
- Modify: `build.gradle.kts`
- Modify: `gradle.properties`
- Modify: `.github/workflows/verify.yml`
- Modify: `.github/workflows/publish.yml`
- Modify: `scripts/weft.py`
- Modify: `scripts/verify.sh`
- Create: `scripts/test_weft.py`
- Modify: `weft-bom/build.gradle.kts`
- Modify: `weft-map/AGENTS.md`
- Modify: `weft-bom/AGENTS.md`
- Modify: `README.md`
- Modify: `docs/architecture/library-modules.md`
- Modify: `docs/architecture/apple-interop.md`
- Modify: `docs/architecture/module-layout.md`
- Modify: `docs/architecture/toolchain-policy.md`
- Modify: `docs/architecture/maven-central-publishing.md`
- Modify: `docs/architecture/bridge-library.md`
- Modify: `weft-core/src/commonMain/kotlin/run/thespot/weft/core/WeftApp.kt`
- Create: `harness/fixtures/weft-kakaomap-consumer/settings.gradle.kts`
- Create: `harness/fixtures/weft-kakaomap-consumer/build.gradle.kts`
- Create: `harness/fixtures/weft-kakaomap-consumer/src/main/AndroidManifest.xml`
- Create: `harness/fixtures/weft-kakaomap-consumer/src/main/java/consumer/Consumer.java`

- [ ] **Step 1: Write failing build/publication checks**
  - Update/add checks that assert `:weft-map:kakaomap` is the only map leaf, the old `weft-map` artifact is absent, and BOM constraints are exactly core/kakaomap/login/push.
  - Add a publication POM assertion for `com.kakao.maps.open:android` and the concrete Gradle consumer fixture that explicitly configures Kakao's Maven repository.
  - Add a task-graph check proving root `verify` and release staging never request `:weft-map:check` or `:weft-map:publishToMavenCentral`.

- [ ] **Step 2: Run checks and verify RED**
  - Run: `./gradlew verifyWeftBomPublication`
  - Run: `./scripts/verify.sh`
  - Expected: the BOM/root graph checks fail on stale flat `:weft-map` wiring; repository verification also exposes generated vendor SwiftPM checkout files that must be excluded from source-text checks.

- [ ] **Step 3: Implement module and publication wiring**
  - Verify the nested `:weft-map:kakaomap` registration from Task 4 and preserve the Kakao repository/catalog wiring there; this task must not reintroduce a flat `:weft-map` publication.
  - Enumerate only leaf libraries in root verification/release tasks; keep the parent `:weft-map` as a namespace project with no publication task.
  - Replace `dependsOn(subprojects.map { "${it.path}:check" })` with explicit leaf paths (`:weft-core`, `:weft-map:kakaomap`, `:weft-login`, `:weft-push`, plus the BOM/plugin checks) so root verification never requests `:weft-map:check`.
  - Point the BOM at `project(":weft-map:kakaomap")` and explicit `weft-kakaomap` coordinates.
  - Update repository/module docs with the current provider milestone, vendor repository consumer setup, SwiftPM alpha/toolchain gate, iOS 13 requirement, lock/resource handling, and exact verification commands.
  - Update the concrete Android Gradle library consumer fixture to use `mavenLocal()` for the locally staged Weft publication and the exact Kakao Maven repository, then compile a Java source referencing `KakaoMap.INSTANCE` from `run.thespot.weft:weft-kakaomap:${VERSION_NAME}`.
  - Compose 1.12 requires Android compile SDK 37; align the shared `android.compileSdk` property and CI SDK installation with API 37.

- [ ] **Step 4: Run full verification and verify GREEN or report environment blockers**
  - Run: `./scripts/verify.sh`
  - Run: `./gradlew :weft-core:check :weft-map:kakaomap:check`
  - Run: `./gradlew :weft-core:checkPomFileForKotlinMultiplatformPublication :weft-map:kakaomap:checkPomFileForKotlinMultiplatformPublication`
  - Run: `./gradlew verifyWeftBomPublication`
  - Run: `./gradlew :weft-core:publishToMavenLocal :weft-map:kakaomap:publishToMavenLocal :weft-bom:publishToMavenLocal`
  - Run: `test -f weft-map/kakaomap/build/publications/android/pom-default.xml && rg -q 'com\.kakao\.maps\.open' weft-map/kakaomap/build/publications/android/pom-default.xml`
  - Run: `./gradlew -p harness/fixtures/weft-kakaomap-consumer -PVERSION_NAME=0.1.0-SNAPSHOT compileDebugJavaWithJavac`
  - Run: `./gradlew -p harness/fixtures/weft-kakaomap-consumer -PVERSION_NAME=0.1.0-SNAPSHOT dependencies --configuration debugRuntimeClasspath`
  - Run: `! ./gradlew verify --dry-run | rg ':weft-map(:check|:publishToMavenCentral)'` and expect no matching lines.
  - Run: `! ./gradlew publishWeftReleaseToMavenCentral --dry-run | rg ':weft-map(:check|:publishToMavenCentral)'` and expect no matching lines.
  - Run: `./gradlew verify`
  - Expected: repository/script checks and all available JVM/host tasks pass; Android SDK or macOS/Xcode absence is reported with the exact failing command and not hidden.

- [ ] **Step 5: Commit**
  - `git add .github/workflows AGENTS.md README.md build.gradle.kts docs/architecture docs/superpowers/plans/2026-11-25-weft-map-kakaomap.md gradle.properties harness/fixtures/weft-kakaomap-consumer scripts/test_weft.py scripts/verify.sh scripts/weft.py weft-bom weft-core/src/commonMain/kotlin/run/thespot/weft/core/WeftApp.kt weft-map/AGENTS.md`
  - `git commit -m "build: publish Kakao map leaf module"`

## Final review and delivery

- [ ] Re-read the spec and this plan against the complete diff; confirm no provider type leaked into `commonMain`, no credentials were added, and no Maven Central publication task was executed (`publishToMavenLocal` was used only for consumer validation).
- [ ] Run the full verification commands fresh and record exit codes/output before any completion claim.
- [ ] Dispatch a spec-compliance reviewer, then a code-quality reviewer, and fix/re-review every issue before integration.
- [ ] Push `feature/map`, open a PR targeting `main`, wait for CI, and merge only after required checks pass; if CI or environment is blocked, report the exact blocker and leave the branch reproducible.
