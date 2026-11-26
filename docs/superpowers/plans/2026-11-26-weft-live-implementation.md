# Weft Live Activity Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the first in-process `:weft-live` Kotlin Multiplatform capability for validated common lifecycle models, Android Live Update-compatible notifications, and an ActivityKit/WidgetKit SwiftPM façade while keeping release publication gated until the remote Apple consumer path is proven.

**Architecture:** Add `:weft-live` as an independent KMP publication leaf. The common plugin owns validation, lifecycle-state rules, serialized delegation, and platform-neutral failures; Android and iOS adapters own native framework types behind `LiveActivityPlatform`. The Apple side imports a checked-in local SwiftPM package through Kotlin's existing pinned toolchain, and root release verification always checks the candidate while default BOM/coordinated publication excludes it behind `weftLiveReleaseEnabled=false`.

**Tech Stack:** Kotlin Multiplatform 2.4.21, Gradle 9.7/AGP 9.3.1, Kotlin coroutines 1.10.2, Android notification APIs (API 24+ with API 36 `ProgressStyle` branch), Swift ActivityKit/WidgetKit, SwiftPM, Kotlin test, XCTest, existing Weft build conventions.

---

## Chunk 1: Module and common contract

### Task 1: Register the candidate module and build conventions

**Files:**
- Create: `weft-live/build.gradle.kts`
- Create: `weft-live/AGENTS.md`
- Modify: `settings.gradle.kts`
- Modify: `gradle/libs.versions.toml`
- Modify: `gradle.properties`

- [x] Add `:weft-live` to settings and configure KMP/Maven publication, `api(project(":weft-core"))`, coroutines implementation, Android host tests, iOS arm64/simulator targets, and local SwiftPM import with the documented iOS 16.1 floor.
- [x] Add the minimal `iosInterop/WeftLiveKit` SwiftPM manifest, source target, and test-target skeleton before invoking Gradle's local-package validation; Task 5 fills in the façade implementation.
- [x] Add the coroutines version to the catalog and `weftLiveReleaseEnabled=false` to Gradle properties without changing the pinned Kotlin toolchain.
- [x] Document Android/iOS setup, ActivityKit limitations, release-gate status, and module-specific validation.
- [x] Run `./gradlew :weft-live:tasks` and `./gradlew :weft-live:validateLocalSwiftPMDependencies` with the Android SDK environment and confirm the new leaf/configuration tasks and package path are registered.
- [x] Commit `feat: scaffold weft live capability module`.

### Task 2: Add the validated common model and failing tests first

**Files:**
- Create: `weft-live/src/commonMain/kotlin/run/thespot/weft/live/LiveActivity.kt`
- Create: `weft-live/src/commonTest/kotlin/run/thespot/weft/live/LiveActivityTest.kt`

- [x] Write tests for ID/text/UTF-8/deep-link/timestamp/progress validation, exact floor percentage without overflow, and terminal-state constraints. The ActivityKit JSON 4,096-byte boundary belongs to the Swift façade tests in Task 5.
- [x] Run the focused common test and observe the expected failure before production implementation.
- [x] Implement enums, immutable models, UTF-8 sizing, URI validation, overflow-safe percentage, and public exception/failure types with explicit API mode.
- [x] Run `./gradlew :weft-live:allTests` (or the focused common test task) and verify the model tests pass.
- [x] Commit `feat: add live activity common models`.

### Task 3: Add the common plugin lifecycle with TDD

**Files:**
- Create/modify: `weft-live/src/commonMain/kotlin/run/thespot/weft/live/WeftLive.kt`
- Modify: `weft-live/src/commonTest/kotlin/run/thespot/weft/live/LiveActivityTest.kt`

- [x] Add failing tests for missing platform, duplicate installation through `Weft`, valid start/update/end states, invalid-state precedence over availability, availability mapping, unknown IDs delegated to the platform, `end(Cancelled)` teardown behavior under Disabled/PermissionRequired versus Unsupported/NotConfigured, and serialized cancellation/operation ordering.
- [x] Run the focused tests and capture the red failure.
- [x] Implement `LiveActivityPlatform`, `LiveActivityPlugin`, `WeftLiveConfig`, `WeftLive`, `prepare`, and a mutex-serialized delegating plugin. Keep native types out of commonMain and keep no in-memory ID registry.
- [x] Run common tests and `:weft-live:check` to verify the green state.
- [x] Commit `feat: add weft live lifecycle plugin`.

## Chunk 2: Android adapter

### Task 4: Implement Android policy helpers and notification adapter with TDD

**Files:**
- Create: `weft-live/src/androidMain/AndroidManifest.xml`
- Create: `weft-live/src/androidMain/kotlin/run/thespot/weft/live/AndroidLiveActivityPlatform.kt`
- Create: `weft-live/src/androidHostTest/kotlin/run/thespot/weft/live/AndroidLiveActivityPlatformTest.kt`

- [x] Write host tests first for API 24/25/32/33/35/36 rendering selection, permission/app/channel availability precedence, stable `(tag=id, notificationId=0)` identity, status marker handling, progress normalization, and terminal rendering/cancellation.
- [x] Run host tests red before implementing helpers.
- [x] Implement validated constructor inputs, lazy channel creation, standard fallback, API 36 `ProgressStyle`/promoted-ongoing request, deep-link PendingIntent, elapsed/ETA rendering, final status labels, cancellation, and exception mapping.
- [x] Add only the non-runtime `POST_PROMOTED_NOTIFICATIONS` manifest permission; do not request `POST_NOTIFICATIONS` at runtime.
- [x] Run `./gradlew :weft-live:testAndroidHostTest :weft-live:check` with the Android SDK environment.
- [x] Commit `feat: add android live activity adapter`.

## Chunk 3: Apple façade and adapter

### Task 5: Build the local SwiftPM ActivityKit/WidgetKit façade with TDD

**Files:**
- Create: `weft-live/iosInterop/WeftLiveKit/Package.swift`
- Create: `weft-live/iosInterop/WeftLiveKit/Sources/WeftLiveKit/WeftLiveActivityKit.swift`
- Create: `weft-live/iosInterop/WeftLiveKit/Tests/WeftLiveKitTests/WeftLiveActivityBridgeTests.swift`

- [x] Define a package targeting iOS 16.1 with an Objective-C-compatible bridge result/bridge and an injectable ActivityKit driver so tests do not require live system activities.
- [x] Write XCTest coverage first for availability/authorization, operation/status validation, callback exactly once, native request/update/end failures, malformed result invariants, and exactly 4,096 accepted vs 4,097 rejected JSON payloads.
- [x] Implement stable ID attributes, mutable content state, FIFO serial task chain, awaited ActivityKit writes, error-code mapping, payload-size checks, and generic Lock Screen/Dynamic Island widget layouts with accessibility labels and optional rows omitted.
- [x] Run `swift test` from `weft-live/iosInterop/WeftLiveKit` on macOS and record any host/toolchain limitations explicitly.
- [x] Commit `feat: add activitykit swiftpm facade`.

### Task 6: Add the Kotlin iOS adapter and cancellation-order tests

**Files:**
- Create: `weft-live/src/iosMain/kotlin/run/thespot/weft/live/AppleLiveActivityPlatform.kt`
- Create: `weft-live/src/iosTest/kotlin/run/thespot/weft/live/AppleLiveActivityPlatformTest.kt`

- [x] Define the generated façade interop surface used by Kotlin and write failing tests for model translation, every façade failure code, availability mapping, malformed result handling, and cancellation that cannot overtake an earlier native operation.
- [x] Implement iOS 16.1 availability checks, argument translation, cancellable callback bridging, and exact failure mapping (`unsupported`, `disabled`, `invalid_state`, `payload_too_large`, `platform_failure`).
- [x] Run the focused iOS tests and iOS device/simulator compilation tasks; keep the implementation compatible with the pinned KGP 2.4.21 local SwiftPM behavior.
- [x] Commit `feat: connect kotlin live activity ios adapter`.

## Chunk 4: Release registry, docs, CI, and verification

### Task 7: Integrate candidate verification and guarded publication

**Files:**
- Modify: `build.gradle.kts`
- Modify: `weft-bom/build.gradle.kts`
- Modify: `weft-bom/AGENTS.md`
- Modify: `docs/architecture/library-modules.md`
- Modify: `docs/architecture/bridge-library.md`
- Modify: `docs/architecture/apple-interop.md`
- Modify: `README.md` or current module index
- Modify: `.github/workflows/verify.yml`
- Modify: `.github/workflows/publish.yml`

- [x] Add `:weft-live` to candidate verification, four POM checks, and publication task enumeration without adding it to the default `weftLibraryProjects` release set.
- [x] Add a guarded `weftLiveReleaseEnabled` task-graph check: false rejects direct live publication and omits its BOM constraint/coordinated release; true includes both only after the remote SwiftPM consumer gate is explicitly enabled.
- [x] Add the candidate module to architecture/module documentation, including Samsung public-API limitations and iOS façade ownership.
- [x] Add a macOS Apple Silicon verification job for SwiftPM façade tests and iOS device/simulator framework builds; pass the release gate explicitly in publish workflow.
- [x] Run failing/passing Gradle task-graph checks for both gate values.
- [x] Commit `feat: gate weft live publication and verification`.

### Task 8: Full verification and review

**Files:**
- Modify any implementation/docs files required by verification feedback.

- [x] Run `ANDROID_HOME="$HOME/Library/Android/sdk" ANDROID_SDK_ROOT="$HOME/Library/Android/sdk" ./gradlew :weft-live:check`.
- [x] Run all four `:weft-live` publication POM checks.
- [x] Run `ANDROID_HOME="$HOME/Library/Android/sdk" ANDROID_SDK_ROOT="$HOME/Library/Android/sdk" ./gradlew verify`.
- [x] Run `./scripts/verify.sh`.
- [x] Run SwiftPM tests and available Apple framework checks.
- [x] Inspect `git diff`, generated metadata, and worktree status; fix all critical/important review findings.
- [x] Request code review before any PR/merge and report evidence-based status.

---

**Definition of done:** `:weft-live` common and Android tests pass, the available Apple façade/interop checks pass or are explicitly blocked by host capability, root verification and harness verification pass, default release metadata excludes the candidate, and the branch is ready for review with every change committed.

## Verification evidence

- `swift test --package-path weft-live/iosInterop/WeftLiveKit --disable-sandbox` passed all 8 façade tests.
- `xcrun swift build --package-path weft-live/iosInterop/WeftLiveKit --sdk "$(xcrun --sdk iphoneos --show-sdk-path)" --triple arm64-apple-ios16.1` passed.
- `ANDROID_HOME="$HOME/Library/Android/sdk" ANDROID_SDK_ROOT="$HOME/Library/Android/sdk" ./gradlew :weft-live:testAndroidHostTest :weft-live:compileKotlinIosSimulatorArm64 :weft-live:compileTestKotlinIosSimulatorArm64` passed.
- `ANDROID_HOME="$HOME/Library/Android/sdk" ANDROID_SDK_ROOT="$HOME/Library/Android/sdk" ./gradlew verify` passed (160 actionable tasks).
- `./scripts/verify.sh` passed.
- Default and explicitly enabled BOM/release task graphs were checked with Gradle `--dry-run`; no publication was performed. The default property remains `weftLiveReleaseEnabled=false`.
