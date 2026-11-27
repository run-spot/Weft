# `:weft-bom` Publication

## Role

`:weft-bom` publishes the `run.thespot.weft:weft-bom` Java Platform. By default it aligns the versions of `weft-core`, `weft-kakaomap`, `weft-navermap`, `weft-gps`, `weft-login`, and `weft-push`. Map coordinates are configured by `:weft-map:kakaomap` and `:weft-map:navermap`; `:weft-map` and `:weft-sensor` are namespace projects. The `:weft-live` artifact remains outside the BOM unless the explicit `weftLiveReleaseEnabled=true` release gate is enabled.

## Milestones

- **M0 — Build and publication skeleton:** active. The Java Platform declares constraints on the default six KMP library projects and publishes a Maven BOM with shared metadata and signing. The live-activity candidate is verified by the root build but remains outside the default BOM.
- **M1 — Consumer validation:** import the published BOM in a separate Gradle consumer and confirm that all six artifacts resolve without explicit versions.
- **M2 — Release hardening:** verify the `run.thespot` namespace, ensure all constrained artifacts are available at the BOM version, and review staged publication before the first release.

## Rules

- Keep this module metadata-only; do not add Kotlin, Java, or provider implementation sources.
- Keep the BOM and all constrained library versions aligned through the shared `VERSION_NAME`.
- Release the BOM and its constrained artifacts together.
- Do not stage or publish artifacts as part of verification.

## Verification

- `./gradlew :weft-bom:check`
- `./gradlew :weft-bom:generatePomFileForMavenPublication`
- `./gradlew verify` (also checks generated BOM coordinates and the six default constraints)
- `./gradlew -PweftLiveReleaseEnabled=true verify` (includes the optional live-activity constraint for release-gate validation)
