# Weft contributor and agent guide

## Purpose

Weft documents and scaffolds KMP/CMP projects. It is not tied to the RunTheSpot product domain. Architecture docs are the source of truth; skills route work through those docs, and the CLI turns a selected profile into a reviewable directory skeleton.

## Workflow

1. Fetch remote refs before reviewing or changing repository work: git fetch --all --prune.
2. Read the relevant page in docs/README.md and the nearest AGENTS.md.
3. Separate verified facts, Weft recommendations, and open decisions. Add an official source and review date to toolchain claims.
4. Update profiles, scaffold behavior, docs, or skills together when their contract changes.
5. Run ./scripts/verify.sh after changing the harness or skills.

## Repository language

Write tracked repository content in English, including documentation, skills, CLI output, and release notes. Keep Korean conversation notes outside the repository.

## Architecture invariants

- Choose the sharing boundary from product needs; do not default every project to shared UI or a server.
- Keep Android and iOS entry points distinct from shared implementation where the selected profile requires it.
- Keep Android framework types out of commonMain contracts. Android API familiarity may guide semantics, but must not leak Context, Activity, or AndroidX types into common APIs.
- Treat SwiftPM import, Kotlin framework export, and Swift implementation of Kotlin contracts as separate interop directions.
- Treat backend: none as a first-class profile. Spring Boot is an optional server module, not a mandatory part of KMP.
- A scaffold is not build-ready unless its Gradle/Xcode files and CI prove that claim. The current CLI only generates a structural scaffold.

## Toolchain policy

The official Kotlin/Gradle/AGP compatibility table is authoritative for supported tuples. Keep experimental tuples separate from supported tuples and label the source and last-reviewed date. Recheck official Kotlin, Android, Compose Multiplatform, and Apple documentation before changing version recommendations.

## Validation

~~~bash
./scripts/verify.sh
~~~

This checks profile manifests, required docs, skill frontmatter, Markdown links, and generated project layouts. It does not build an Android app, an iOS framework, or a server.
