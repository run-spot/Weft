# Weft contributor and agent guide

## Purpose

Weft documents and scaffolds KMP/CMP projects and hosts independent KMP bridge libraries. Architecture docs are the source of truth; skills route work through those docs, and the CLI turns a selected profile into a reviewable directory skeleton.

## Workflow

1. Fetch remote refs before reviewing or changing repository work: `git fetch --all --prune`.
2. Read the relevant page in `docs/README.md` and the nearest `AGENTS.md`.
3. Separate verified facts, Weft recommendations, and open decisions. Add an official source and review date to toolchain claims.
4. Update profiles, scaffold behavior, docs, skills, Gradle conventions, and module milestones together when their contract changes.
5. Run `./scripts/verify.sh` after changing the harness or skills, and `./gradlew verify` after changing Gradle builds, modules, or publication metadata.

## Repository language

Write tracked repository content in English, including documentation, skills, CLI output, and release notes. Keep Korean conversation notes outside the repository.

## Architecture invariants

- Choose the UI-sharing boundary from product needs; every Weft app profile must select either a Spring Boot backend or Supabase.
- Use OAuth 2.0 Authorization Code with PKCE for native app sign-in. This is a Weft architecture policy; PKCE itself does not require an application backend.
- Application modules use the strict direct-dependency allowlist in `harness/dependency-policy.json`; do not add one-off third-party libraries.
- Kotlin owns app startup, `UIApplicationDelegate`-level behavior, app UI, and business logic. Swift app code is limited to a thin Xcode bootstrap, widgets/extensions, or the approved Objective-C-visible SDK façade.
- Keep Android and iOS entry points distinct from shared implementation where the selected profile requires it.
- Keep Android framework types out of commonMain contracts. Android API familiarity may guide semantics, but must not leak Context, Activity, or AndroidX types into common APIs.
- Install every Weft capability plugin through `:weft-core` so an app configures its capabilities in one `Weft { install(...) }` block. Plugins of one capability family declare the same plugin type (`KClass`), and a second installation for the same type must fail fast rather than replace the first.
- Treat SwiftPM import, Kotlin framework export, and Swift implementation of Kotlin contracts as separate interop directions.
- Treat `backend: spring-boot` and `backend: supabase` as the only supported app profile values. Ask whether the team will operate a separate server: choose Spring Boot for that path and Supabase when it will not.
- A scaffold is not build-ready unless its Gradle/Xcode files and CI prove that claim. The current CLI only generates a structural scaffold.

## Toolchain policy

The official Kotlin/Gradle/AGP compatibility table is authoritative for supported tuples. Keep experimental tuples separate from supported tuples and label the source and last-reviewed date. Recheck official Kotlin, Android, Compose Multiplatform, and Apple documentation before changing version recommendations.

## Validation

~~~bash
./scripts/verify.sh
./gradlew verify
~~~

The script checks profile manifests, required docs, skill frontmatter, Markdown links, and generated project layouts. The Gradle task checks library modules and Maven publication metadata. Neither task publishes artifacts. Build and publish Apple XCFrameworks only from a macOS release job.

## Gradle libraries and release safety

- `build-logic` owns shared KMP and Maven Central conventions; read its nearest `AGENTS.md` before changing conventions.
- `weft-core`, `weft-map`, `weft-login`, and `weft-push` are KMP publication units with independent API milestones; `weft-bom` aligns their released versions.
- Keep credentials and signing keys out of tracked files. Configure them as Gradle properties or GitHub Actions secrets.
- Do not run a Maven Central publication task unless the maintainer explicitly requests a release. Published Central coordinates are immutable.

## Gradle library map

- `build-logic`: shared KMP target, toolchain, POM, and signing conventions.
- `:weft-core`: plugin installation contract (`Weft`, `WeftConfig`, `WeftPlugin`, `Weft.get`) shared by every capability library; no vendor or platform types.
- `:weft-map`, `:weft-login`, and `:weft-push`: capability libraries with independent API milestone checklists.
- `:weft-bom`: metadata-only Java Platform that publishes version constraints for the four libraries.
- The KMP modules share build conventions. Keep the BOM as version metadata; do not add library-to-library dependencies without a real public API need.
