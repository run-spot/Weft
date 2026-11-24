# `:weft-push` Library

## Role

`weft-push` will expose a Kotlin-first API for push registration and notification events across Android and iOS. It is published as `run.thespot.weft:weft-push` and version-aligned by `:weft-bom`.

## Milestones

- **M0 — Build and publication skeleton:** active. KMP Android and iOS targets, shared conventions, Apache-2.0 POM metadata, and Central staging are configured. No push API or provider adapter is included yet.
- **M1 — Public contract:** define token registration, refresh, notification payload, permission, and cancellation semantics without platform types.
- **M2 — Platform adapters:** choose the first Android and Apple push providers and document app-level configuration, entitlements, and lifecycle requirements.
- **M3 — Release candidate:** validate success, denial, token rotation, delivery callbacks, provider redistribution terms, and a separate consumer.

## Rules

- Keep `Context`, APNs types, notification framework objects, and provider SDK types out of `commonMain`.
- Model permission and delivery differences explicitly; do not imply equivalent platform behavior without evidence.
- Add dependencies only to the source set that needs them.
- Do not release the empty M0 scaffold.

## Verification

- `./gradlew :weft-push:check`
- `./gradlew :weft-push:checkPomFileForKotlinMultiplatformPublication`
