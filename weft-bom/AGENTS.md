# `:weft-bom` Publication

## Role

`:weft-bom` publishes the `run.thespot.weft:weft-bom` Java Platform. By default it aligns the versions of the `weft-core`, `weft-kakaomap`, `weft-login`, and `weft-push` artifacts. The `weft-kakaomap` coordinates are configured by `:weft-map:kakaomap`; `:weft-map` is only a namespace project. The `:weft-live` artifact is a verified release candidate and is added to the BOM only when the explicit `weftLiveReleaseEnabled=true` release gate is enabled.

## Milestones

- **M0 — Build and publication skeleton:** active. The Java Platform declares constraints on the default four KMP library projects and publishes a Maven BOM with shared metadata and signing. The live-activity candidate is verified by the root build but remains outside the default BOM.
- **M1 — Consumer validation:** import the published BOM in a separate Gradle consumer and confirm that all four artifacts resolve without explicit versions.
- **M2 — Release hardening:** verify the `run.thespot` namespace, ensure all constrained artifacts are available at the BOM version, and review staged publication before the first release.

## Rules

- Keep this module metadata-only; do not add Kotlin, Java, or provider implementation sources.
- Keep the BOM and all constrained library versions aligned through the shared `VERSION_NAME`.
- Release the BOM and its constrained artifacts together.
- Do not stage or publish artifacts as part of verification.

## Verification

- `./gradlew :weft-bom:check`
- `./gradlew :weft-bom:generatePomFileForMavenPublication`
- `./gradlew verify` (also checks the generated BOM coordinates and the default constraints)
- `./gradlew -PweftLiveReleaseEnabled=true verify` (includes the live-activity constraint for release-gate validation)
