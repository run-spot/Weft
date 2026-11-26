# Weft Live Activity Design

**Date:** 2026-11-26

**Status:** Revised for written-spec review

## Goal

Add `:weft-live` as a Kotlin Multiplatform capability candidate with Maven coordinates `run.thespot.weft:weft-live` for finite, user-visible live activities. The coordinate is configured, but coordinated publication is gated until the Apple SwiftPM consumer path is ready:

- Android posts an ongoing notification using the Android Live Updates contract when the platform supports it. Samsung Galaxy devices may surface an eligible notification through One UI surfaces such as Now Bar/Now Brief, but Weft does not claim a private or guaranteed Now Brief integration.
- Apple starts, updates, and ends an ActivityKit Live Activity and ships a reusable WidgetKit/SwiftUI view through a local SwiftPM façade.
- The common API describes the activity and its lifecycle without Android, UIKit, ActivityKit, WidgetKit, Context, Activity, or PendingIntent types.

The first slice is an in-process lifecycle adapter. FCM/APNs remote start/update/end, push-token registration, actions, images, persistence across process death, and Samsung-private APIs remain future work.

## Current context and verified constraints

- :weft-core owns the WeftPlugin installation contract and provider-neutral capability slots.
- Published capability leaves apply weft.kmp-library and weft.maven-publish, depend on :weft-core from commonMain, and keep native SDKs in platform source sets.
- The current coordinated release set and default BOM are weft-core, weft-kakaomap, weft-login, and weft-push. This feature adds `:weft-live` to root verification, but leaves it out of the default BOM/release until its façade has a versioned remote SwiftPM source and a proven external consumer path.
- Android builds use compile SDK 37, min SDK 24, and JVM 17 on origin/main.
- ActivityKit Live Activities require an app/widget-extension integration and the NSSupportsLiveActivities Info.plist key. ActivityKit dynamic state is limited to 4 KB (4,096 bytes maximum).
- Android Live Updates are promoted ongoing notifications. The official contract requires an eligible notification style and the POST_PROMOTED_NOTIFICATIONS manifest permission; OEMs may apply additional eligibility rules.
- Samsung documents Now Brief as a personalized system experience that aggregates selected app information. No public app-facing Now Brief SDK contract was found during this design review.
- Kotlin SwiftPM import is Alpha and imports Objective-C-compatible APIs. The repository pins KGP 2.4.21; Kotlin's current guide documents 2.5.0-RC as the trial version. The existing Kakao remote-package pilot on 2.4.21 does not prove local-package support, so the implementation must verify `localSwiftPackage` against the pinned tuple before treating iOS integration as built. Do not silently upgrade the repository toolchain to make this feature compile.

References reviewed 2026-11-26:

- Android Live Updates: <https://developer.android.com/develop/ui/views/notifications/live-update>
- Android Notification.ProgressStyle: <https://developer.android.com/reference/android/app/Notification.ProgressStyle>
- Apple ActivityKit: <https://developer.apple.com/documentation/activitykit>
- Apple Live Activity setup: <https://developer.apple.com/documentation/activitykit/displaying-live-data-with-live-activities>
- Apple ActivityKit push updates: <https://developer.apple.com/documentation/activitykit/starting-and-updating-live-activities-with-activitykit-push-notifications>
- Apple ActivityState: <https://developer.apple.com/documentation/activitykit/activitystate>
- Apple ActivityUIDismissalPolicy.default: <https://developer.apple.com/documentation/activitykit/activityuidismissalpolicy/default>
- Samsung Now Brief overview: <https://news.samsung.com/sg/the-future-of-mobile-ai-is-where-personalisation-meets-innovation>
- Kotlin SwiftPM import: <https://kotlinlang.org/docs/multiplatform/multiplatform-spm-import.html>

## Scope

### In scope

1. Add and register the :weft-live KMP publication leaf.
2. Add a common live-activity model with validation for identifiers, text, progress, timestamps, and terminal states.
3. Add WeftLive, installed through Weft { install(WeftLive) { ... } }, with a platform-provider injection boundary.
4. Add Android notification publishing:
   - notification channel creation;
   - ongoing notification start/update;
   - API 36+ Notification.ProgressStyle and promoted-ongoing request;
   - pre-36 fallback to a standard progress notification;
   - completion/failure final notification and cancellation;
   - notification-enabled and permission-aware availability reporting.
5. Add the Apple local SwiftPM façade:
   - ActivityAttributes and ContentState for one generic Weft activity shape;
   - WeftLiveActivityBridge with Objective-C-compatible lifecycle methods;
   - a reusable WeftLiveActivityWidget that renders Lock Screen and Dynamic Island presentations;
   - ActivityKit availability/error translation.
6. Add iosMain adapter code that translates common models to the façade and maps façade failures to platform-neutral LiveActivityException values.
7. Update root settings, verification, BOM constraints, module guides, and architecture docs.
8. Add common model/plugin tests, Android rendering/permission tests, and SwiftPM façade/build checks. Coverage includes validation boundaries, lifecycle states, unknown/duplicate IDs, availability precedence, every façade error code, and payload limits.

### Out of scope

- Calling an undocumented Samsung Now Brief/Now Bar API.
- Claiming every Galaxy model or region will show a Weft notification in Now Brief.
- APNs/FCM server code, remote push tokens, broadcast channels, or background-start policy.
- Interactive buttons/toggles, rich images, custom per-product widget layouts, or app-specific deep-link routing beyond passing a URL through the generic surface.
- Foreground-service orchestration, notification listener access, or application lifecycle ownership.
- Direct Swift consumers of a published XCFramework. The SwiftPM façade is an integration dependency of the KMP iOS target and widget extension, not a separate release artifact in this slice.

## Public common API

The API lives under run.thespot.weft.live and keeps all platform framework types out of commonMain:

~~~kotlin
public enum class LiveActivityStatus {
    Active,
    Completed,
    Failed,
    Cancelled,
}

public enum class LiveActivityAvailability {
    Available,
    PermissionRequired,
    Disabled,
    Unsupported,
}

public enum class LiveActivityFailureReason {
    Unsupported,
    PermissionRequired,
    Disabled,
    NotConfigured,
    InvalidState,
    PlatformFailure,
}

public data class LiveActivityProgress(
    public val current: Long,
    public val total: Long,
) {
    init {
        require(total > 0) { "progress total must be greater than zero" }
        require(current in 0..total) { "progress current must be between zero and total" }
    }

    public val percentage: Int
        get() = floorPercentageWithoutOverflow(current, total)
}

public data class LiveActivity(
    public val id: String,
    public val title: String,
    public val subtitle: String? = null,
    public val status: LiveActivityStatus = LiveActivityStatus.Active,
    public val progress: LiveActivityProgress? = null,
    public val startedAtEpochMillis: Long? = null,
    public val endsAtEpochMillis: Long? = null,
    public val deepLink: String? = null,
) {
    init {
        require(id.matches(Regex("[A-Za-z0-9._:-]{1,128}"))) {
            "live activity id must be 1-128 ASCII letters, digits, '.', '_', ':', or '-'"
        }
        require(title.isNotBlank() && title.utf8Length() <= 128) {
            "live activity title must be non-blank and at most 128 UTF-8 bytes"
        }
        require(subtitle == null || subtitle.utf8Length() <= 512) {
            "live activity subtitle must be at most 512 UTF-8 bytes"
        }
        require(deepLink == null || isValidDeepLink(deepLink)) {
            "deepLink must be an absolute hierarchical URI of at most 2048 UTF-8 bytes"
        }
        require(startedAtEpochMillis == null || startedAtEpochMillis >= 0) {
            "live activity start time must be a non-negative epoch timestamp"
        }
        require(endsAtEpochMillis == null || endsAtEpochMillis >= 0) {
            "live activity end time must be a non-negative epoch timestamp"
        }
        if (startedAtEpochMillis != null && endsAtEpochMillis != null) {
            require(endsAtEpochMillis >= startedAtEpochMillis) {
                "live activity end time must not precede its start time"
            }
        }
        if (status == LiveActivityStatus.Completed && progress != null) {
            require(progress.current == progress.total) {
                "completed live activity progress must equal its total"
            }
        }
    }
}

public class LiveActivityException(
    public val reason: LiveActivityFailureReason,
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)

public interface LiveActivityPlatform {
    public fun availability(): LiveActivityAvailability

    public suspend fun start(activity: LiveActivity)

    public suspend fun update(activity: LiveActivity)

    public suspend fun end(activity: LiveActivity)
}

public interface LiveActivityPlugin {
    public fun availability(): LiveActivityAvailability

    public suspend fun start(activity: LiveActivity)

    public suspend fun update(activity: LiveActivity)

    public suspend fun end(activity: LiveActivity)
}

public class WeftLiveConfig {
    public var platform: LiveActivityPlatform? = null
}

public object WeftLive : WeftPlugin<WeftLiveConfig, LiveActivityPlugin> {
    override val type: KClass<LiveActivityPlugin> = LiveActivityPlugin::class
    override val pluginName: String = "WeftLive"
}
~~~

The implementation fills in `prepare` and delegates to the injected platform. A missing platform configuration fails `prepare` with `LiveActivityException(NotConfigured, ...)`. The plugin serializes lifecycle calls with `kotlinx.coroutines.sync.Mutex` so concurrent start/update/end calls cannot race; `kotlinx-coroutines-core:1.10.2` is an implementation dependency, not part of the public model API. It validates the operation's required status before checking platform availability, so InvalidState wins over an availability error when both apply. `end(Cancelled)` skips Disabled/PermissionRequired gating for teardown, but still fails on Unsupported or NotConfigured.

- start accepts only Active.
- update accepts only Active.
- end accepts only Completed, Failed, or Cancelled.
- An invalid state throws LiveActivityException(InvalidState, ...).
- Start/update and final-state end on an unavailable platform throw before native work: Unsupported maps to `Unsupported`, PermissionRequired maps to `PermissionRequired`, and Disabled maps to `Disabled`. `end(Cancelled)` is teardown and may still attempt native dismissal when Disabled or PermissionRequired; Unsupported still maps to Unsupported.
- The plugin does not keep an in-memory active-ID registry. Native platforms remain authoritative for activity identity after process restart.

Lifecycle operations are suspend so an Apple implementation can await ActivityKit update/end calls without exposing Swift concurrency types. Android completes after NotificationManager accepts the request. Apple completes after the native operation returns without error; this does not promise display or promotion.

Lifecycle contract (normative):

| Operation | Input state | Existing ID behavior | Unknown ID | Completion |
| --- | --- | --- | --- | --- |
| `start` | Active only | Idempotently updates the existing native activity; never creates a second instance | Creates one activity | Completes after native start/update is accepted |
| `update` | Active only | Replaces all mutable content | Throws InvalidState | Completes after native update is accepted |
| `end` | Completed, Failed, or Cancelled only | Completed/Failed render final state; Cancelled dismisses | Throws InvalidState | Completes after native end/dismiss/cancel is accepted |

`id` is the sole immutable identity field. Title, subtitle, deepLink, progress, and timestamps are mutable snapshots. `startedAtEpochMillis` is the actual start time when supplied; `endsAtEpochMillis` is an ETA, not a lifecycle command. Both may be omitted, must be non-negative when present, and no comparison to wall-clock time is performed. When Active, startedAt renders an elapsed timer and endsAt renders a localized ETA; terminal state hides both. Completed progress, when present, must be full; Failed/Cancelled may retain partial progress. `percentage` is exactly `floor(current * 100 / total)` and must avoid overflow and floating-point rounding for every valid Long input. Android uses `(tag = id, notificationId = 0)` to avoid hash collisions, and stores the activity status in a namespaced `Notification.extras` key. A start with no matching notification creates one; an Active match is replaced; a terminal or unrecognized status marker fails InvalidState. Update/end require exactly one Active notification marker; unknown or terminal IDs fail InvalidState. Apple stores `id` in ActivityAttributes and all other fields in ContentState. A start with no match creates an activity; one Active or Stale match is updated; one Pending, Ended, or Dismissed match fails InvalidState; multiple matches fail PlatformFailure. Update/end require exactly one Active or Stale match, otherwise they fail InvalidState. The plugin serializes its own lifecycle calls; external writers are outside that guarantee.

`utf8Length()` means UTF-8 byte count, not Kotlin character count. `isValidDeepLink` accepts only `scheme://authority` hierarchical URIs (ASCII scheme grammar `[A-Za-z][A-Za-z0-9+.-]*`, non-empty authority, optional path/query/fragment), rejects whitespace, controls, backslashes, and malformed `%HH` escapes. Android additionally parses with `java.net.URI`; Apple validates with `URLComponents` and `URL` before native use. The common model enforces size and shared syntax on every target. Before an Apple write, the façade JSON-encodes ContentState and rejects payloads over 4,096 bytes. Exactly 4,096 bytes is permitted; 4,097 is rejected. Text and URLs are never silently truncated.

LiveActivity deliberately carries a small text/progress payload. Apple ContentState must be at or below the 4 KB ActivityKit limit, and Android system surfaces are designed for brief, glanceable status rather than arbitrary UI.

## Weft installation and platform boundaries

The capability is installed through the existing core registry:

~~~kotlin
val weft = Weft {
    install(WeftLive) {
        platform = createPlatformForThisTarget()
    }
}

val live = weft.get<LiveActivityPlugin>()
~~~

createPlatformForThisTarget() is an application/platform-source-set concern. It may be an expect/actual factory in a KMP app, or the platform shell may construct the adapter before creating the shared Weft configuration. The common configuration never accepts Context, Activity, ActivityKit, WidgetKit, or PendingIntent.

All implementations are mutually exclusive under LiveActivityPlugin::class, so installing a second live provider fails with the existing DuplicateWeftPluginException.

## Android adapter

androidMain publishes:

~~~kotlin
public class AndroidLiveActivityPlatform(
    context: Context,
    channelId: String,
    channelName: CharSequence,
    smallIconResId: Int,
    requestPromotedOngoing: Boolean = true,
)
~~~

The adapter stores the application context and validates the channel and icon inputs. It creates the notification channel lazily on API 26+; API 24–25 have no channel API and post the same notification without a channel. Android API 24–35 reports Available when notifications are permitted, because the standard notification fallback is supported even though Live Updates promotion is unavailable. API 36+ also reports Available under the same permission/settings rules. It uses `NotificationManager.notify(tag = LiveActivity.id, id = 0, ...)`, so repeated starts/updates replace one notification without hash collisions or a local database. Update/end check the app's active notification list; an unknown ID is InvalidState.

Rendering rules:

- title becomes notification content title and subtitle becomes content text.
- Active with `startedAtEpochMillis` displays an elapsed chronometer; Active with `endsAtEpochMillis` displays a localized ETA. Terminal notifications hide the elapsed/ETA fields.
- deepLink, when present, becomes an ACTION_VIEW content PendingIntent; the app owns the deep-link route.
- A progress snapshot is shown as a percentage and as current/total text.
- On API 36+, an Active activity with progress uses Notification.ProgressStyle with one segment and a normalized 0–100 tracker; it requests promoted ongoing treatment only when configured. Completed/Failed never request promotion.
- Without progress, even on API 36+, the adapter posts a standard ongoing notification and does not request Live Update promotion because no progress-style update is available.
- On older APIs, the adapter uses the standard progress notification fallback. The activity remains useful but is not promised as an Android Live Update.
- Active posts an ongoing, only-alert-once notification.
- Completed and Failed post a non-ongoing final notification with visible status labels distinguishing “Completed” from “Failed” (for example notification subtext/accessibility text).
- Cancelled removes the notification.
- API 33+ notification runtime permission is app-owned. `availability()` reports PermissionRequired when absent and never launches a permission dialog.
- App-level or channel-level notification disablement reports Disabled. Availability precedence is support, runtime permission, then app/channel enablement.
- If a permission race produces `SecurityException` during a post, it maps to PermissionRequired; other native exceptions map to PlatformFailure. Pure policy helpers for permission/settings/API-level selection are tested on host JVM for API 24/25/32/33/35/36, permission denied, app notifications disabled, and channel disabled.
- API/OEM promotion eligibility is best-effort; the adapter requests promotion but does not claim system acceptance.

The Android manifest contributed by the library declares the non-runtime android.permission.POST_PROMOTED_NOTIFICATIONS permission. The consuming app still owns POST_NOTIFICATIONS runtime permission handling and notification icon resources. Channels are created with IMPORTANCE_LOW (never IMPORTANCE_MIN) to avoid sound while remaining eligible for promotion. Consumers should call start only for user-initiated, ongoing, time-sensitive activities; this generic module does not decide whether a product event meets that bar.

## Apple adapter and SwiftPM façade

iosMain uses a Swift package at `iosInterop/WeftLiveKit`. The package exposes a narrow Objective-C-compatible façade around Swift-only ActivityKit types:

~~~swift
@objc(WeftLiveActivityBridgeResult)
public final class WeftLiveActivityBridgeResult: NSObject {
    @objc public let accepted: Bool
    @objc public let failureCode: String? // unsupported, disabled, invalid_state, payload_too_large, platform_failure
    @objc public let failureMessage: String?
}

@objc(WeftLiveActivityBridge)
public final class WeftLiveActivityBridge: NSObject {
    @objc public func availability() -> String // available, disabled, unsupported
    @objc(startWithId:title:status:subtitle:progressCurrent:progressTotal:startedAtMillis:endsAtMillis:deepLink:completion:)
    public func start(id: String, title: String, status: String, subtitle: String?, progressCurrent: NSNumber?,
                      progressTotal: NSNumber?, startedAtMillis: NSNumber?, endsAtMillis: NSNumber?,
                      deepLink: String?, completion: @escaping (WeftLiveActivityBridgeResult) -> Void)
    @objc(updateWithId:title:status:subtitle:progressCurrent:progressTotal:startedAtMillis:endsAtMillis:deepLink:completion:)
    public func update(id: String, title: String, status: String, subtitle: String?, progressCurrent: NSNumber?,
                       progressTotal: NSNumber?, startedAtMillis: NSNumber?, endsAtMillis: NSNumber?,
                       deepLink: String?, completion: @escaping (WeftLiveActivityBridgeResult) -> Void)
    @objc(endWithId:title:status:subtitle:progressCurrent:progressTotal:startedAtMillis:endsAtMillis:deepLink:completion:)
    public func end(id: String, title: String, status: String, subtitle: String?, progressCurrent: NSNumber?,
                    progressTotal: NSNumber?, startedAtMillis: NSNumber?, endsAtMillis: NSNumber?,
                    deepLink: String?, completion: @escaping (WeftLiveActivityBridgeResult) -> Void)
}
~~~

The result is a complete operation contract: `accepted` is true only after ActivityKit request/update/end finishes without error, never merely because work was scheduled. Completion runs exactly once, including validation failures, and on the main queue. ActivityKit access is serialized on `MainActor`; Objective-C entry points do not expose Swift concurrency types. Each entry point synchronously appends to a bridge-owned FIFO task chain before returning; the chain tail is protected from concurrent callers, and normal call order follows acquisition of the Kotlin Mutex. Kotlin bridges the callback to `suspend` with `suspendCancellableCoroutine`; cancellation stops waiting but does not roll back a native operation already submitted. Because cancellation releases the Kotlin Mutex while the Swift operation may still be running, the bridge owns an unstructured serial task chain: each accepted operation awaits the previous task before calling ActivityKit, and caller cancellation never cancels or removes that task. A subsequent operation therefore cannot overtake an earlier native write even if its Kotlin waiter was cancelled.

Result invariants: accepted results have no failure code/message; rejected results have a non-empty failure code and diagnostic message. Kotlin treats malformed/inconsistent results as PlatformFailure. The façade accepts `active` for start/update and one of `completed`, `failed`, `cancelled` for end. Failure messages are diagnostic only and are never parsed for behavior.

The façade owns:

- WeftLiveActivityAttributes with stable ID; all other snapshot properties are mutable ContentState;
- lookup of existing activities by stable Weft ID; multiple matches fail rather than guessing;
- idempotent Activity.request / Activity.update for starts that find an Active or Stale match; a matching Pending, Ended, or Dismissed activity causes InvalidState, and no match creates one;
- awaited Activity.update and Activity.end;
- authorization, payload-size, invalid-state, and native-error mapping to the result codes above;
- encoded ContentState byte-budget validation before every ActivityKit write;
- WeftLiveActivityWidget, which renders all required Lock Screen and Dynamic Island presentations with a compact generic layout.

The widget's minimum presentation contract is: Lock Screen shows title, optional subtitle, status, optional progress text/bar, optional elapsed time/ETA while Active, and an optional `widgetURL` deep link. Dynamic Island supplies expanded leading (title), trailing (status/progress), bottom (subtitle, optional progress, and optional elapsed time/ETA), compact leading/trailing (title/status), and minimal (status-only) views. If progress or subtitle is absent, its row/gauge is omitted without placeholders. Completed/Failed activities end with the system's default dismissal policy so the terminal snapshot can be seen; Cancelled ends immediately. All text must have an accessibility label and the widget must not promise a particular visual treatment on devices without Dynamic Island.

`AppleLiveActivityPlatform` translates LiveActivity to façade arguments, awaits its completion callback, and maps errors exactly: `unsupported` -> Unsupported, `disabled` -> Disabled, `invalid_state` -> InvalidState, `payload_too_large` / `platform_failure` / unknown -> PlatformFailure. The adapter targets iOS 16.1+, the first iOS release containing ActivityKit Live Activities. Availability is Unsupported below iOS 16.1 or when ActivityKit is unavailable, Disabled when ActivityKit authorization is off, and Available otherwise. iOS has no runtime permission dialog owned by this library.

The consuming iOS app must:

1. Obtain and link the exact same WeftLiveKit package source into both the app and its Widget Extension.
2. Add NSSupportsLiveActivities = YES to the app target.
3. Include WeftLiveActivityWidget() in its WidgetBundle (or add equivalent custom views).
4. Provide a deep-link route if activities pass deepLink.
5. Test on supported iOS hardware/simulator; the library does not claim ActivityKit behavior on unsupported platforms such as visionOS.

This is explicitly SwiftPM import plus a reusable Swift WidgetKit façade, not an XCFramework/SwiftPM distribution of the KMP library itself. The initial package at `weft-live/iosInterop/WeftLiveKit` is source-checkout-only; it is not embedded in Maven metadata or the KMP publication. Therefore the module is not release-ready for external Maven consumers until WeftLiveKit is available from a stable, versioned remote SwiftPM source and a separate consumer proves Gradle transitive package resolution plus app/widget-extension linkage. No public package URL or version is invented here. Remote ActivityKit push notifications are reserved for a later milestone.

## Files and responsibilities

### Create

- weft-live/AGENTS.md — module role, platform setup, limitations, and validation.
- weft-live/build.gradle.kts — KMP publication, core dependency, local SwiftPM import, iOS target.
- weft-live/src/commonMain/kotlin/run/thespot/weft/live/LiveActivity.kt — models, validation, enums, and failure type.
- weft-live/src/commonMain/kotlin/run/thespot/weft/live/WeftLive.kt — plugin/config/platform contract and delegation.
- weft-live/src/commonTest/kotlin/run/thespot/weft/live/LiveActivityTest.kt — validation, lifecycle-state, delegation, availability/error mapping, and duplicate-install behavior.
- weft-live/src/androidMain/AndroidManifest.xml — promoted notification permission.
- weft-live/src/androidMain/kotlin/run/thespot/weft/live/AndroidLiveActivityPlatform.kt — notification/channel/intent rendering and availability.
- weft-live/src/androidHostTest/kotlin/run/thespot/weft/live/AndroidLiveActivityPlatformTest.kt — pure helper tests for API 24–36 availability/render selection, permission/settings states, status-marker lifecycle, and ID/percentage checks.
- weft-live/src/iosMain/kotlin/run/thespot/weft/live/AppleLiveActivityPlatform.kt — Swift façade translation and failure mapping.
- weft-live/iosInterop/WeftLiveKit/Package.swift — local SwiftPM product manifest.
- weft-live/iosInterop/WeftLiveKit/Sources/WeftLiveKit/WeftLiveActivityKit.swift — ActivityKit bridge, data types, and WidgetKit UI.
- weft-live/iosInterop/WeftLiveKit/Tests/WeftLiveKitTests/WeftLiveActivityBridgeTests.swift — deterministic façade tests using an injected ActivityKit driver.
- weft-live/src/iosTest/kotlin/run/thespot/weft/live/AppleLiveActivityPlatformTest.kt — façade translation, result mapping, and cancellation-order behavior with a fake bridge.

### Modify

- settings.gradle.kts — include :weft-live.
- build.gradle.kts — always verify the candidate; conditionally include it in the BOM/release only behind `weftLiveReleaseEnabled`.
- weft-bom/build.gradle.kts and weft-bom/AGENTS.md — document the candidate and conditionally constrain it only when release is enabled.
- gradle.properties — default `weftLiveReleaseEnabled=false`.
- gradle/libs.versions.toml — pin `kotlinx-coroutines-core:1.10.2` for the common lifecycle mutex and cancellable callback bridge.
- docs/architecture/library-modules.md — document the new capability and artifact set.
- docs/architecture/bridge-library.md — document live activity as a real bridge leaf and its Apple widget boundary.
- docs/architecture/apple-interop.md — record the ActivityKit Swift façade and iOS 16.1 floor.
- README.md or the relevant module index — expose the new capability if the current index lists published artifacts.
- .github/workflows/verify.yml — retain the Android/Linux gate and add a macOS Apple Silicon job that builds iOS device/simulator frameworks and runs the SwiftPM façade tests.
- .github/workflows/publish.yml — pass the release gate explicitly (default false); release must not include :weft-live unless the checked-in release gate has been enabled after consumer proof.

The release registry is explicit: `settings.gradle.kts` module inclusion; root `weftLibraryProjects`; root `verify` leaf and POM task enumeration; `verifyWeftBomPublication` expected artifacts; the `verify` task-graph invariant; `publishWeftReleaseToMavenCentral` dependencies and task-graph invariant; `weft-bom/build.gradle.kts` constraints; `weft-bom/AGENTS.md`; `docs/architecture/library-modules.md`; `gradle.properties`; and `.github/workflows/verify.yml` / `.github/workflows/publish.yml`. `:weft-live` is always verified, but omitted from the BOM and coordinated publication by default. `weftLiveReleaseEnabled` defaults to false; when false, Gradle rejects any direct `:weft-live:publish...` task. It may be set true only after a versioned remote WeftLiveKit source is configured and the release-consumer acceptance tests pass. The workflows must not bypass this task-graph gate. The generic harness has no separate published-artifact registry.

### Tests and verification

- Required local gates: `./gradlew :weft-live:check`, all four `:weft-live` publication-POM checks (KMP, Android, iOS arm64, iOS simulator arm64), `./gradlew verify`, and `./scripts/verify.sh`.
- Release-gate acceptance: with the default `weftLiveReleaseEnabled=false`, the BOM POM has no `weft-live` constraint, the coordinated publish dry run omits `:weft-live`, and a direct `:weft-live:publish... --dry-run` fails before task execution. With the property true, the BOM and coordinated release both include `:weft-live`.
- CI acceptance requires Android SDK compilation and API 35/36 branch coverage (through a pure notification-style selector), plus Apple Silicon/Xcode resolution of the façade and iOS device/simulator-arm64 framework builds.
- Swift façade tests must cover availability/authorization states, invalid operation/status, exactly-4,096 and 4,097-byte state payloads, native request/update/end failures, and callback completion exactly once.
- Cancellation ordering must be tested by holding the first fake native operation, cancelling its Kotlin waiter, queuing a second operation, and proving the second native call starts only after the first completes.
- A release candidate additionally requires a separate Gradle consumer resolving the published KMP artifact and its versioned remote WeftLiveKit SwiftPM dependency, an Xcode app + Widget Extension link/archive check, and supported Galaxy/iOS device smoke tests. Local checkout tests alone do not satisfy release acceptance.

## Risks and follow-ups

1. Samsung surface variability: Now Brief/Now Bar placement is OS/OEM policy. Keep the Android implementation on public Live Updates APIs and validate on a supported Galaxy device before publishing any Samsung-specific claim.
2. Kotlin SwiftPM Alpha and toolchain/source boundary: verify local-package import on pinned KGP 2.4.21; do not silently upgrade. The local façade is not available to Maven consumers yet. The default release gate excludes `:weft-live`; only enable coordinated publication after its versioned remote package source and transitive-consumer proof are resolved and CI passes on Apple Silicon.
3. ActivityKit UI ownership: a reusable generic widget is included, but app targets still own WidgetBundle registration, Info.plist, deep links, branding, and permissions.
4. Remote updates: APNs/FCM payloads, push tokens, 4 KB encoding, and server authentication require a separate design and must not be smuggled into this first module.

