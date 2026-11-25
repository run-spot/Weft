# Weft Gradle Build Logic

This included build provides the convention plugins used by the Weft modules:

- `weft.kmp-library` — Kotlin Multiplatform Android and Apple targets, explicit API mode, and shared host-test defaults for the published libraries.
- `weft.maven-publish` — Maven Central coordinates, POM metadata, and signing for the published libraries and BOM.
- `weft.compose` — the Compose compiler for a module that uses Compose API (`:weft-core`).
- `weft.jvm` — a JVM Gradle-plugin module (Kotlin JVM plus `java-gradle-plugin`, JDK 17 toolchain, `kotlin("test")` and Gradle TestKit on the test classpath).

Version pins live in `../gradle/libs.versions.toml`; shared Android defaults and the publication group live in the repository `gradle.properties`. Maven artifact IDs use the `weft-` prefix.

`weft.kmp-library` declares `iosArm64` and `iosSimulatorArm64` only. Compose Multiplatform stopped publishing `iosX64` artifacts, so a library that depends on the Compose runtime cannot resolve that target; keep the shared target list free of targets Compose does not publish.

See [AGENTS.md](AGENTS.md) for the current milestone and change rules.
