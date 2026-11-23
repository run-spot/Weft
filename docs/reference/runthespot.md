# Lessons from RunTheSpot

## Snapshot and source

This summary reflects the RunTheSpot develop snapshot fetched on 2026-11-23, commit b4c7c1e90b73e973ac9cfb31742b8763077e2ee5. [Browse the snapshot](https://github.com/run-spot/RunTheSpot/tree/b4c7c1e90b73e973ac9cfb31742b8763077e2ee5).

This case informs Weft architecture choices. One project's success does not make its structure the default for every KMP/CMP app.

## Observed structure

- Android, iOS, Spring Boot, KMP feature contracts, and CMP UI live in one monorepo.
- AndroidApp owns variants and signing; ComposeApp owns the shared Compose root and iOS framework export.
- The current ComposeApp guide also places Android Activity, Application, manifest, and resources in ComposeApp androidMain. Weft's first AGP 9 profile instead places the app entry Activity and manifest in the Android shell. Treat the RunTheSpot arrangement as a valid variant and verify plugin, manifest-merging, and build behavior for the chosen combination.
- client/core provides networking, storage, and platform adapters/wrappers; client/feature owns screens and routes.
- feature modules group common contracts and platform-specific implementations by capability.
- Apple SDK integration combines Kotlin adapters, SwiftPM packages, and an Objective-C-visible façade when needed.
- The server uses Spring Boot with a separate entry module.

Public references: [technical stack](https://github.com/run-spot/RunTheSpot/blob/b4c7c1e90b73e973ac9cfb31742b8763077e2ee5/docs/architecture/stack.md), [ComposeApp guide](https://github.com/run-spot/RunTheSpot/blob/b4c7c1e90b73e973ac9cfb31742b8763077e2ee5/ComposeApp/AGENTS.md), and [client module guide](https://github.com/run-spot/RunTheSpot/blob/b4c7c1e90b73e973ac9cfb31742b8763077e2ee5/docs/ui/module-structure.md).

## Reusable experience

- Make ownership explicit for app shells, shared UI, feature contracts, client adapters, and server entry points.
- Put stable SDK wrappers between product screens and native SDKs.
- Split common contracts from platform adapters when a capability needs Android and iOS implementations.
- Give the server its own responsibilities apart from app modules.
- Turn build and documentation checks into repeatable commands and CI.

## Do not generalize

- Running/spot domain logic, Kakao SDK, Wanted Design System, Koin, Ktorfit, and Spring Boot are product choices.
- A feature module does not need both client and server source sets in one Gradle project.
- This case alone does not show that sharing all iOS UI with CMP fits every product.
- Do not promote observed AGP/Kotlin versions to Weft defaults without compatibility-table review and CI evidence.

## Claims that need measurement

If a bridge or module change is claimed to reduce build time, binary size, or Swift source, compare before and after using the same machine, Xcode version, and Gradle cache conditions. Record an unmeasured claim as a hypothesis, not a result.
