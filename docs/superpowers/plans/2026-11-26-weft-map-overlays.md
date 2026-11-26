# Weft Map Overlays Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add provider-neutral polyline, polygon, and circle overlays to the shared Weft map composition and render them through Kakao Android and iOS adapters.

**Architecture:** Extend `:weft-core`'s existing `MapNode`/`MapApplier`/`MapContentBuilder` pipeline with one immutable model and node per overlay kind. Keep provider SDK types in `:weft-map:kakaomap` and reconcile each platform's shape layer from immutable snapshots with rollback on native failures.

**Tech Stack:** Kotlin Multiplatform, Compose Runtime/Compose UI, Kakao Maps Android and iOS SDKs, Kotlin test, Gradle.

---

## Chunk 1: Common overlay contract and composition

### Task 1: Add failing model and snapshot tests

**Files:**
- Modify: `weft-core/src/commonTest/kotlin/run/thespot/weft/core/map/MapModelsTest.kt`
- Modify: `weft-core/src/commonTest/kotlin/run/thespot/weft/core/map/MapContentCompositionTest.kt`
- Modify: `weft-core/src/commonMain/kotlin/run/thespot/weft/core/map/composition/MapContentComposition.kt`

- [ ] Add tests for non-blank IDs, point-count validation, positive finite circle radius, non-negative finite widths, and defensive point-list copies.
- [ ] Add tests that publish ordered polylines, polygons, and circles alongside markers.
- [ ] Add tests for update, remove, move, and visibility changes through the child composition.
- [ ] Add tests that reject duplicate IDs within one overlay kind while allowing IDs across different kinds, and prove a failed duplicate-ID build leaves the previously published full `MapContent` unchanged.
- [ ] Add node tests that preserve identity across moves and verify each changed overlay node invalidates the content exactly once.
- [ ] Run `./gradlew :weft-core:compileTestKotlinIosSimulatorArm64` and confirm compilation fails on the expected missing overlay models/DSL/snapshot fields (not on a task lookup or unrelated toolchain failure).

### Task 2: Implement common overlay models and `MapContent`

**Files:**
- Modify: `weft-core/src/commonMain/kotlin/run/thespot/weft/core/map/MapModels.kt`

- [ ] Add immutable `MapPolyline`, `MapPolygon`, and `MapCircle` models with validated constructor arguments and defensive point-list copies.
- [ ] Add `polylines`, `polygons`, and `circles` to `MapContent` with defensive copies while preserving the existing marker API.
- [ ] Run `./gradlew :weft-core:compileCommonMainKotlinMetadata` and confirm the common model sources compile; composition tests are expected to remain red until Task 3.

### Task 3: Add overlay nodes, DSL, and content builder

**Files:**
- Modify: `weft-core/src/commonMain/kotlin/run/thespot/weft/core/map/composition/MapContentComposition.kt`
- Create: `weft-core/src/commonMain/kotlin/run/thespot/weft/core/map/composition/MapPolylineNode.kt`
- Create: `weft-core/src/commonMain/kotlin/run/thespot/weft/core/map/composition/MapPolygonNode.kt`
- Create: `weft-core/src/commonMain/kotlin/run/thespot/weft/core/map/composition/MapCircleNode.kt`
- Modify: `weft-core/src/commonMain/kotlin/run/thespot/weft/core/map/MapScope.kt`
- Modify: `weft-core/src/commonMain/kotlin/run/thespot/weft/core/map/composition/MapContentBuilder.kt`

- [ ] Implement one `ComposeNode` model overload and one convenience overload for each overlay kind, keyed by ID.
- [ ] Implement node update/invalidation behavior matching `MapMarkerNode`.
- [ ] Collect each overlay list in source order; validate all kinds before atomically publishing, and leave the prior `MapContent` intact on any validation error.
- [ ] Ensure `MapContentState.publishIfDirty()` compares every overlay collection, not only markers.
- [ ] Run `./gradlew :weft-core:iosSimulatorArm64Test` and confirm common composition/model tests pass.

### Task 4: Document and commit the common contract

**Files:**
- Modify: `README.md`
- Modify: `docs/architecture/library-modules.md`

- [ ] Add a concise overlay usage example to `README.md`, link the design spec, and update marker-only architecture wording in `docs/architecture/library-modules.md`.
- [ ] Run `./gradlew :weft-core:iosSimulatorArm64Test` and commit `feat(core): add provider-neutral map overlays`.
- [ ] Push the verified common-contract commit and open/update the authorized Draft PR now, so the requester can follow the provider work in progress.

## Chunk 2: Android Kakao shape reconciliation

### Task 5: Add failing Android reconciler tests

**Files:**
- Modify: `weft-map/kakaomap/src/androidHostTest/kotlin/run/thespot/weft/map/kakaomap/KakaoMapAndroidCoordinatorTest.kt`
- Create: `weft-map/kakaomap/src/androidHostTest/kotlin/run/thespot/weft/map/kakaomap/KakaoMapAndroidShapeReconcilerTest.kt`

- [ ] Add fake shape-layer tests for add/update/remove of all three overlay kinds; inject a partial native-shape mutation failure, assert restoration of the prior committed shape snapshot, then verify the next render retries the complete intended snapshot.
- [ ] Add tests for hidden-to-visible transitions, retained hidden reconciler entries, style/geometry changes, and IDs shared across different kinds; assert provider-native IDs are kind-prefixed.
- [ ] Add tests for overlay-only updates preserving the user camera and for a camera failure after shape mutation restoring/retrying the complete committed shape snapshot while reporting one `KakaoMapException`.
- [ ] Verify deterministic dp/color-alpha/z-order/circle-projection conversion and shape cleanup in `onMapDestroy()` and `finish()`.
- [ ] Run `ANDROID_HOME="${HOME}/Library/Android/sdk" ./gradlew :weft-map:kakaomap:testAndroidHostTest --tests '*KakaoMapAndroid*Test'` and confirm the new tests fail for missing reconciliation behavior (or record the exact SDK/toolchain failure before implementation).

### Task 6: Implement the Android reconciler and SDK layer

**Files:**
- Create: `weft-map/kakaomap/src/androidMain/kotlin/run/thespot/weft/map/kakaomap/KakaoMapAndroidShapeReconciler.kt`
- Modify: `weft-map/kakaomap/src/androidMain/kotlin/run/thespot/weft/map/kakaomap/KakaoMapAndroidCoordinator.kt`
- Modify: `weft-map/kakaomap/src/androidMain/kotlin/run/thespot/weft/map/kakaomap/KakaoMapAndroidHost.kt`

- [ ] Add a testable transactional reconciler for polylines, polygons, and circles keyed by type and ID, with provider-native IDs prefixed by kind (same public ID may exist in different kinds).
- [ ] Create a Kakao `ShapeLayer`, styles, and native shapes without leaking SDK types into common code.
- [ ] Convert colors, widths, coordinates, z-order, and visibility; approximate circles with a documented polygon projection.
- [ ] Include overlays in the coordinator's rendered-state equality and retry/rollback path.
- [ ] Run `ANDROID_HOME="${HOME}/Library/Android/sdk" ./gradlew :weft-map:kakaomap:testAndroidHostTest --tests '*KakaoMapAndroid*Test'` and `ANDROID_HOME="${HOME}/Library/Android/sdk" ./gradlew :weft-map:kakaomap:check`; require both to pass, or document exact toolchain error output while keeping toolchain-independent checks green.

## Chunk 3: iOS Kakao shape reconciliation

### Task 7: Add failing iOS reconciler tests

**Files:**
- Modify: `weft-map/kakaomap/src/iosTest/kotlin/run/thespot/weft/map/kakaomap/KakaoMapIosCoordinatorTest.kt`
- Create: `weft-map/kakaomap/src/iosTest/kotlin/run/thespot/weft/map/kakaomap/KakaoMapIosShapeReconcilerTest.kt`

- [ ] Add fake shape-layer tests matching Android add/update/remove, hidden toggles, kind-prefixed IDs, and cleanup on reset; inject a partial native-shape mutation failure, assert restoration of the prior committed snapshot, then verify the next render retries the complete intended snapshot.
- [ ] Add coordinator tests proving content-only overlay updates do not apply the camera and that camera failure after shape mutation rolls back/retries the complete committed snapshot and reports one `KakaoMapException`.
- [ ] Verify deterministic dp/color-alpha/z-order/circle-projection conversion.
- [ ] Run `./gradlew :weft-map:kakaomap:compileTestKotlinIosSimulatorArm64` and confirm new tests fail on the missing overlay behavior before implementation.

### Task 8: Implement the iOS reconciler and SDK layer

**Files:**
- Create: `weft-map/kakaomap/src/iosMain/kotlin/run/thespot/weft/map/kakaomap/KakaoMapIosShapeReconciler.kt`
- Modify: `weft-map/kakaomap/src/iosMain/kotlin/run/thespot/weft/map/kakaomap/KakaoMapIosCoordinator.kt`
- Modify: `weft-map/kakaomap/src/iosMain/kotlin/run/thespot/weft/map/kakaomap/KakaoMapIosHost.kt`

- [ ] Add a testable transactional reconciler for all three overlay kinds with kind-prefixed provider-native IDs (same public ID across kinds is allowed).
- [ ] Create and own one Kakao iOS shape layer, style sets, and native shapes; project circles to polygon points where required.
- [ ] Convert common colors, widths, coordinates, z-order, and visibility using the imported SDK bindings.
- [ ] Include overlays in coordinator equality, rollback, reset, and retry behavior; route failures exactly once through the coordinator error path.
- [ ] Run `./gradlew :weft-map:kakaomap:iosSimulatorArm64Test` and `./gradlew :weft-map:kakaomap:compileKotlinIosSimulatorArm64`; require both to pass or document the exact Apple-toolchain failure.

## Chunk 4: Integration verification and delivery

### Task 9: Run full verification and review the diff

**Files:**
- No new files.

The requester explicitly authorized pushing `feature/map-overlays` and creating/updating an open Draft PR so they can follow the work. Keep this publication in scope, preserve Draft state, and do not mark ready, merge, or publish artifacts.

- [ ] Run `ANDROID_HOME="${HOME}/Library/Android/sdk" ./gradlew :weft-core:check :weft-map:kakaomap:check`, `./gradlew verify`, and `./scripts/verify.sh`; require exit 0 for all available gates and record the exact failing task/output for unavailable toolchains. Do not run any Maven Central publication task.
- [ ] Run `git diff --check`; inspect `git diff -- weft-core/src/commonMain/kotlin/run/thespot/weft/core/map` for intended public API changes; assert no Kakao SDK references enter common code with `if rg -n 'Kakao|com\.kakao|KakaoMap' weft-core/src/commonMain; then exit 1; else echo 'PASS: no Kakao SDK references in commonMain'; fi`.
- [ ] Request an independent code review identifying the exact base and head SHAs; save the reviewer report, resolve every blocking/high finding, and re-review the final head SHA before delivery.
- [ ] Push the remaining verified commits to `feature/map-overlays`, update the existing Draft PR, and confirm the observed PR URL/state/head SHA and checks; keep it Draft and report changed files, validation evidence, and any environment blocker.
