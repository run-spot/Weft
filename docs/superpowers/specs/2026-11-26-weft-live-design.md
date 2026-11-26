# Weft Live Activity Design

**Date:** 2026-11-26

**Status:** Draft for written-spec review

## Goal

Add :weft-live, published as run.thespot.weft:weft-live, as a Kotlin Multiplatform capability for finite, user-visible live activities:

- Android posts an ongoing notification using the Android Live Updates contract when the platform supports it. Samsung Galaxy devices may surface an eligible notification through One UI surfaces such as Now Bar/Now Brief, but Weft does not claim a private or guaranteed Now Brief integration.
- Apple starts, updates, and ends an ActivityKit Live Activity and ships a reusable WidgetKit/SwiftUI view through a local SwiftPM façade.
- The common API describes the activity and its lifecycle without Android, UIKit, ActivityKit, WidgetKit, Context, Activity, or PendingIntent types.

The first slice is an in-process lifecycle adapter. FCM/APNs remote start/update/end, push-token registration, actions, images, persistence across process death, and Samsung-private APIs remain future work.

## Current context and verified constraints

- :weft-core owns the WeftPlugin installation contract and provider-neutral capability slots.
- Published capability leaves apply weft.kmp-library and weft.maven-publish, depend on :weft-core from commonMain, and keep native SDKs in platform source sets.
- The current release set is weft-core, weft-kakaomap, weft-login, and weft-push; the BOM and root verification enumerate that set explicitly.
- Android builds use compile SDK 37, min SDK 24, and JVM 17 on origin/main.
- ActivityKit Live Activities require an app/widget-extension integration and the NSSupportsLiveActivities Info.plist key. ActivityKit dynamic state is limited to 4 KB.
- Android Live Updates are promoted ongoing notifications. The official contract requires an eligible notification style and the POST_PROMOTED_NOTIFICATIONS manifest permission; OEMs may apply additional eligibility rules.
- Samsung documents Now Brief as a personalized system experience that aggregates selected app information. No public app-facing Now Brief SDK contract was found during this design review.
- Kotlin SwiftPM import is Alpha and imports Objective-C-compatible APIs. The Apple bridge therefore exposes a narrow @objc façade around the Swift-only ActivityKit API and is consumed by iosMain through localSwiftPackage.

References reviewed 2026-11-26:

- Android Live Updates: <https://developer.android.com/develop/ui/views/notifications/live-update>
- Android Notification.ProgressStyle: <https://developer.android.com/reference/android/app/Notification.ProgressStyle>
- Apple ActivityKit: <https://developer.apple.com/documentation/activitykit>
- Apple Live Activity setup: <https://developer.apple.com/documentation/activitykit/displaying-live-data-with-live-activities>
- Apple ActivityKit push updates: <https://developer.apple.com/documentation/activitykit/starting-and-updating-live-activities-with-activitykit-push-notifications>
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
8. Add common model/plugin tests, Android rendering/permission tests where the host harness permits, and SwiftPM façade/build checks.

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
    PermissionDenied,
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
        get() = ((current.toDouble() / total.toDouble()) * 100.0).toInt().coerceIn(0, 100)
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
        require(id.isNotBlank()) { "live activity id must not be blank" }
        require(title.isNotBlank()) { "live activity title must not be blank" }
        if (startedAtEpochMillis != null && endsAtEpochMillis != null) {
            require(endsAtEpochMillis >= startedAtEpochMillis) {
                "live activity end time must not precede its start time"
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

The actual implementation fills in prepare and delegates to the injected platform. The plugin validates operation state before delegating:

- start accepts only Active.
- update accepts only Active.
- end accepts only Completed, Failed, or Cancelled.
- An invalid state throws LiveActivityException(InvalidState, ...).
- An unavailable platform throws a platform-neutral exception before attempting native work.
- The plugin does not keep an in-memory active-ID registry. This avoids rejecting valid updates after an app process restart; native platforms remain authoritative for activity identity.

Lifecycle operations are suspend so an Apple implementation can bridge ActivityKit asynchronous update/end calls without exposing Swift concurrency types. Android completes the operation after the notification manager accepts the notification. Apple completes after the façade schedules the native operation; native failures after scheduling are reported by the façade result/error boundary and must not be represented as a successful synchronous start.

LiveActivity deliberately carries a small text/progress payload. Apple static and dynamic state must remain below the 4 KB ActivityKit limit, and Android system surfaces are designed for brief, glanceable status rather than arbitrary UI.

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

The adapter stores the application context, validates the channel and icon inputs, and creates the notification channel lazily before the first post. It uses a stable positive notification ID derived from LiveActivity.id, so repeated starts/updates replace one notification without a local database.

Rendering rules:

- title becomes notification content title and subtitle becomes content text.
- deepLink, when present, becomes an ACTION_VIEW content PendingIntent; the app owns the deep-link route.
- A progress snapshot is shown as a percentage and as current/total text.
- On API 36+, a progress snapshot uses Notification.ProgressStyle with one segment and a normalized 0–100 tracker; the builder requests promoted ongoing treatment when configured.
- On older APIs, the adapter uses the standard progress notification fallback. The activity remains useful but is not promised as an Android Live Update.
- Active posts an ongoing, only-alert-once notification.
- Completed and Failed post a non-ongoing final notification.
- Cancelled removes the notification.
- API 33+ notification runtime permission is app-owned. availability() reports PermissionRequired when it is absent and never attempts to launch a permission dialog.
- Disabled notification channels/settings report Disabled.
- API/OEM promotion eligibility is best-effort; the adapter requests promotion but does not claim system acceptance.

The Android manifest contributed by the library declares the non-runtime android.permission.POST_PROMOTED_NOTIFICATIONS permission. The consuming app still owns POST_NOTIFICATIONS runtime permission handling and notification icon resources.

## Apple adapter and SwiftPM façade

iosMain uses a local Swift package at iosInterop/WeftLiveKit. The package exposes a narrow Objective-C-compatible façade around Swift-only ActivityKit types:

~~~swift
public final class WeftLiveActivityBridge: NSObject {
    public func isEnabled() -> Bool
    public func start(...) -> WeftLiveActivityBridgeResult
    public func update(...) -> WeftLiveActivityBridgeResult
    public func end(...) -> WeftLiveActivityBridgeResult
}
~~~

The façade owns:

- WeftLiveActivityAttributes, with static identity/title/deep-link fields;
- ContentState, with subtitle/status/progress/time fields;
- lookup of existing activities by the stable Weft ID;
- Activity.request for foreground starts;
- asynchronous Activity.update and Activity.end;
- mapping of authorization and native errors to an Objective-C-compatible result object;
- WeftLiveActivityWidget, which renders all required Lock Screen and Dynamic Island presentations with a compact generic layout.

AppleLiveActivityPlatform translates LiveActivity to façade arguments and maps a rejected result to LiveActivityException. The adapter targets iOS 16.1+, the first iOS release containing ActivityKit Live Activities. The Gradle module imports the local Swift package using localSwiftPackage and pins its iOS minimum deployment target to 16.1.

The consuming iOS app must:

1. Link the generated/local Swift package into both the app and its Widget Extension.
2. Add NSSupportsLiveActivities = YES to the app target.
3. Include WeftLiveActivityWidget() in its WidgetBundle (or add equivalent custom views).
4. Provide a deep-link route if activities pass deepLink.
5. Test on supported iOS hardware/simulator; the library does not claim ActivityKit behavior on unsupported platforms such as visionOS.

This is explicitly SwiftPM import plus a reusable Swift WidgetKit façade, not an XCFramework/SwiftPM distribution of the KMP library itself. Remote ActivityKit push notifications are intentionally reserved for a later milestone.

## Files and responsibilities

### Create

- weft-live/AGENTS.md — module role, platform setup, limitations, and validation.
- weft-live/build.gradle.kts — KMP publication, core dependency, local SwiftPM import, iOS target.
- weft-live/src/commonMain/kotlin/run/thespot/weft/live/LiveActivity.kt — models, validation, enums, and failure type.
- weft-live/src/commonMain/kotlin/run/thespot/weft/live/WeftLive.kt — plugin/config/platform contract and delegation.
- weft-live/src/commonTest/kotlin/run/thespot/weft/live/LiveActivityTest.kt — validation, lifecycle-state, delegation, and duplicate-install behavior.
- weft-live/src/androidMain/AndroidManifest.xml — promoted notification permission.
- weft-live/src/androidMain/kotlin/run/thespot/weft/live/AndroidLiveActivityPlatform.kt — notification/channel/intent rendering and availability.
- weft-live/src/androidHostTest/kotlin/run/thespot/weft/live/AndroidLiveActivityPlatformTest.kt — API-independent rendering and ID/percentage checks where the host test harness supports them.
- weft-live/src/iosMain/kotlin/run/thespot/weft/live/AppleLiveActivityPlatform.kt — Swift façade translation and failure mapping.
- weft-live/iosInterop/WeftLiveKit/Package.swift — local SwiftPM product manifest.
- weft-live/iosInterop/WeftLiveKit/Sources/WeftLiveKit/WeftLiveActivityKit.swift — ActivityKit bridge, data types, and WidgetKit UI.
- weft-live/src/iosTest/kotlin/run/thespot/weft/live/AppleLiveActivityPlatformTest.kt — façade translation and result mapping with a fake bridge where possible.

### Modify

- settings.gradle.kts — include :weft-live.
- build.gradle.kts — include the new leaf in verification, BOM checks, and coordinated release.
- weft-bom/build.gradle.kts and weft-bom/AGENTS.md — constrain/document weft-live.
- docs/architecture/library-modules.md — document the new capability and artifact set.
- docs/architecture/bridge-library.md — document live activity as a real bridge leaf and its Apple widget boundary.
- docs/architecture/apple-interop.md — record the ActivityKit Swift façade and iOS 16.1 floor.
- README.md or the relevant module index — expose the new capability if the current index lists published artifacts.

### Tests and verification

- ./gradlew :weft-live:check
- ./gradlew :weft-live:checkPomFileForKotlinMultiplatformPublication
- ./gradlew :weft-live:checkPomFileForAndroidPublication
- ./gradlew :weft-live:checkPomFileForIosArm64Publication
- ./gradlew :weft-live:checkPomFileForIosSimulatorArm64Publication
- ./gradlew verify
- ./scripts/verify.sh
- On a host with Android SDK and Xcode/iOS toolchains, compile the iOS targets and resolve the local SwiftPM façade. The current Intel host's missing Android SDK is an environment blocker already observed during the clean baseline run; it must be reported separately from code failures.

## Risks and follow-ups

1. Samsung surface variability: Now Brief/Now Bar placement is OS/OEM policy. Keep the Android implementation on public Live Updates APIs and validate on a supported Galaxy device before publishing any Samsung-specific claim.
2. Kotlin SwiftPM Alpha: local package import and transitive linking need CI on Apple Silicon. Do not call the module release-ready solely because common tests pass.
3. ActivityKit UI ownership: a reusable generic widget is included, but app targets still own WidgetBundle registration, Info.plist, deep links, branding, and permissions.
4. Remote updates: APNs/FCM payloads, push tokens, 4 KB encoding, and server authentication require a separate design and must not be smuggled into this first module.

