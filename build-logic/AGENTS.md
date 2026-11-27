# Build Logic

## Role

`build-logic` owns the shared Gradle conventions for Weft's published KMP libraries.

## Milestones

- **M0 — Convention baseline:** pin Kotlin, AGP, and publishing plugin versions; configure Android and Apple targets; centralize Maven POM metadata and signing.
- **M1 — Consumer validation:** add compile and publication checks against a real library API and a separate Gradle consumer.
- **M2 — Release hardening:** review namespace ownership, credentials, signing rotation, and release automation before the first Central deployment.

## Rules

- Keep cross-module defaults here and module-specific API or SDK choices in the owning library.
- Keep the Apple target list aligned with what Compose Multiplatform publishes: `iosArm64` and `iosSimulatorArm64`. `iosX64` is not published by Compose and was removed from the shared convention.
- A module that uses Compose API applies `weft.compose`; a JVM Gradle plugin module applies `weft.jvm`. Do not repeat the compiler or toolchain setup in a module build file.
- A KMP module that declares Koin definitions applies `weft.koin`. Keep the compiler plugin routed through this included build so the Kotlin Gradle Plugin and Koin plugin share their build classloader.
- Koin Compiler Plugin 1.2.1 warns on Kotlin 2.4.21 because its published verification matrix ends at 2.4.20; keep the compile result and the warning recorded until Koin updates that matrix.
- Never put publishing credentials or signing keys in the repository.
- Maven Central uploads are released automatically from a version tag by `.github/workflows/publish.yml`; keep credentials and signing keys out of tracked files, and never reuse a published version because Central coordinates are immutable.
- Record official sources and a review date when changing toolchain versions.

## Verification

- `./gradlew verify`
- `./gradlew :weft-map:tasks`
- `./gradlew :weft-gradle-plugin:check`
