# Build Logic

## Role

`build-logic` owns the shared Gradle conventions for Weft's published KMP libraries.

## Milestones

- **M0 — Convention baseline:** pin Kotlin, AGP, and publishing plugin versions; configure Android and Apple targets; centralize Maven POM metadata and signing.
- **M1 — Consumer validation:** add compile and publication checks against a real library API and a separate Gradle consumer.
- **M2 — Release hardening:** review namespace ownership, credentials, signing rotation, and release automation before the first Central deployment.

## Rules

- Keep cross-module defaults here and module-specific API or SDK choices in the owning library.
- Never put publishing credentials or signing keys in the repository.
- Keep Maven Central uploads staged for review; do not enable automatic release without an explicit maintainer decision.
- Record official sources and a review date when changing toolchain versions.

## Verification

- `./gradlew verify`
- `./gradlew :map:tasks`
