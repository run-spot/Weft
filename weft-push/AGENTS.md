# `:weft-push` Library

## Role

`weft-push` exposes a Kotlin-first API for push registration and notification events across Android and iOS. Its first provider is Firebase Cloud Messaging (FCM), and the shared registration identifier is Firebase's FID-based registration. It is published as `run.thespot.weft:weft-push` and version-aligned by `:weft-bom`.

## Milestones

- **M0 — Build and publication skeleton:** complete. KMP Android and iOS targets, shared conventions, Apache-2.0 POM metadata, and Central staging are configured.
- **M1 — Public contract:** implemented. Common models expose provider registration IDs, normalized remote messages, notification requests, processing decisions, a configurable processor, permission status, registration lifecycle, and Flow cancellation semantics without platform types.
- **M2 — Platform adapters:** implemented for Firebase FID registration, Android message processing and notification display, iOS payload normalization and local notification display. The app-owned iOS Notification Service Extension sample runs the same Kotlin processor before APNs displays a mutable alert.
- **M3 — Release candidate:** open. Validate permission denial, FID rotation, data-only Android and mutable-content iOS delivery, Notification Service Extension linkage in a consumer Xcode project, Firebase SDK redistribution terms, and physical-device behavior before release.

## Rules

- Keep `Context`, APNs types, notification framework objects, and provider SDK types out of `commonMain`.
- Model permission and delivery differences explicitly; do not imply equivalent platform behavior without evidence.
- Add dependencies only to the source set that needs them.
- Do not release the empty M0 scaffold.
- Use Firebase Installation IDs for registration; do not introduce deprecated FCM token APIs.
- Do not own or replace the app's `UNUserNotificationCenterDelegate`. iOS apps forward callbacks into the shared stream and decide foreground presentation.
- Do not put product-specific event names or routing policy in `weft-push`; expose a common processor for apps to implement.
- A consuming app owns its Notification Service Extension target. It may use the shared processor for `mutable-content` alerts and rich-media notification handling.

## Verification

- `./gradlew :weft-push:check`
- `./gradlew :weft-push:checkPomFileForKotlinMultiplatformPublication`

Firebase's iOS SwiftPM import is Alpha in the pinned Kotlin toolchain. Keep its lock file and consumer-linkage evidence with the module before declaring a release candidate.
