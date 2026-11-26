# `:weft-live` Library

## Role

`weft-live` exposes a provider-neutral, finite live-activity lifecycle for user-visible progress and
status updates. It is published as `run.thespot.weft:weft-live` only after the local ActivityKit
façade has a versioned remote SwiftPM source and a separate external consumer has proved the Apple
linkage path. Until then, the module is always verified but remains outside the default BOM and
coordinated release.

## Platform boundaries

- `commonMain` contains only validated activity models, lifecycle rules, and the injected
  `LiveActivityPlatform` contract. It must not import Android, UIKit, ActivityKit, WidgetKit,
  `Context`, `Activity`, or `PendingIntent` types.
- `androidMain` uses public Android notification APIs. API 36+ promotion is best-effort through the
  public Live Updates contract; older Android versions use the standard notification fallback.
  Samsung Now Bar/Now Brief placement is OEM policy and is not a private integration guarantee.
- `iosMain` imports the checked-in local `WeftLiveKit` SwiftPM façade. The consuming app owns
  `NSSupportsLiveActivities`, WidgetBundle registration, deep-link routing, and app/widget package
  linkage.
- Remote APNs/FCM updates, persistence, actions, images, foreground services, and private Samsung
  APIs are outside this slice.

## Validation

```bash
./gradlew :weft-live:check
./gradlew :weft-live:checkPomFileForKotlinMultiplatformPublication
./gradlew :weft-live:checkPomFileForAndroidPublication
./gradlew :weft-live:checkPomFileForIosArm64Publication
./gradlew :weft-live:checkPomFileForIosSimulatorArm64Publication
swift test --package-path weft-live/iosInterop/WeftLiveKit
```

The Kotlin SwiftPM import is an Alpha feature on the repository's pinned Kotlin toolchain. Do not
upgrade Kotlin merely to make this module compile; report incompatibilities as a release gate.
