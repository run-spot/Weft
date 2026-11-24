# `:weft-login` Library

## Role

`weft-login` will expose a Kotlin-first sign-in API across Android and iOS while preserving familiar Android call semantics where they are safe to share. It is published as `run.thespot.weft:weft-login` and version-aligned by `:weft-bom`.

## Milestones

- **M0 — Build and publication skeleton:** active. KMP Android and iOS targets, shared conventions, Apache-2.0 POM metadata, and Central staging are configured. No sign-in API or provider adapter is included yet.
- **M1 — Public contract:** select the first sign-in flow and define result, cancellation, account, and error semantics.
- **M2 — Platform adapters:** choose Android and Apple SDK paths; document callback URLs, app lifecycle, permissions, and app-owned presentation boundaries.
- **M3 — Release candidate:** verify success, cancellation, provider errors, minimum OS support, redistribution terms, and a separate consumer.

## Rules

- Keep `Context`, `Activity`, `UIViewController`, provider SDK types, and platform URLs out of `commonMain`.
- Preserve Android behavior where useful, but expose platform capability differences instead of hiding them.
- Add dependencies only to the source set that needs them.
- Do not release the empty M0 scaffold.

## Verification

- `./gradlew :weft-login:check`
- `./gradlew :weft-login:checkPomFileForKotlinMultiplatformPublication`
