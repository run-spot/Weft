# `:map` Library

## Role

`map` will expose a Kotlin-first API for map capability integrations across Android and iOS. It is a separate Maven artifact: `map`.

## Milestones

- **M0 — Build and publication skeleton:** active. KMP Android and iOS targets, shared conventions, Apache-2.0 POM metadata, and Central staging are configured. No map API or provider adapter is included yet.
- **M1 — Public contract:** choose the first supported map use case and define common models, lifecycle, callbacks, and failure behavior.
- **M2 — Platform adapters:** select compatible Android and iOS SDKs; keep vendor types in their platform source sets or an explicit interop boundary.
- **M3 — Release candidate:** validate API compatibility, provider redistribution terms, target coverage, and a separate consumer before creating a release.

## Rules

- Keep Android SDK, UIKit, and provider SDK types out of `commonMain`.
- Add dependencies only to the source set that needs them.
- Do not add a generic provider framework before a second real integration requires one.
- Do not release the empty M0 scaffold.

## Verification

- `./gradlew :map:check`
- `./gradlew :map:checkPomFileForKotlinMultiplatformPublication`
