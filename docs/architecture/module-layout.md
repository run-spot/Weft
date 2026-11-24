# Module layout

Split modules by ownership and change boundary, not by platform name alone. Do not create a Gradle module just to move one file.

## KMP core with Kotlin platform UI

~~~text
androidApp/                 # Android application, entry Activity, manifest, variants, signing
iosApp/                     # Xcode project and thin Swift bootstrap into Kotlin
shared/
  core/<capability>/        # network, persistence, settings, logging adapters
  feature/<capability>/     # common contracts/use cases, Kotlin UI, and platform implementations
~~~

commonMain uses Kotlin standard APIs and allowlisted multiplatform libraries. Android SDK implementations belong in androidMain; Apple SDK integrations belong in iosMain or an explicitly declared SwiftPM bridge. Product UI and app lifecycle behavior stay in Kotlin; a profile without shared UI still keeps its platform-specific UI in Kotlin.

## CMP shared UI

~~~text
androidApp/                 # Android app shell
iosApp/                     # Xcode host and minimal Swift bootstrap
composeApp/                 # KMP Android library, shared Compose root, iOS framework target
shared/
  core/<capability>/
  feature/<capability>/
~~~

- androidApp owns the com.android.application configuration, entry Activity, manifest, variants, and signing.
- composeApp owns the Android KMP library target, shared Compose entry point, client composition, and iOS framework target.
- shared/core owns shared client contracts and adapters for networking, storage, and platform services.
- shared/feature owns contracts and behavior organized by capability.
- iosApp owns Xcode settings and a minimal Swift bootstrap/forwarder. Product UI, startup policy, and app lifecycle behavior belong in Kotlin.

For AGP 9+, do not apply the Android application plugin and KMP Android library configuration to the same module. Use com.android.kotlin.multiplatform.library for a KMP Android target such as composeApp, and keep androidApp as a separate com.android.application module. The default Weft profile places the app entry Activity and manifest in the Android shell. If an existing project places them in the KMP library's Android source set, validate manifest merging and install/build behavior in CI and record the decision.

## CMP with a Spring server

~~~text
androidApp/
iosApp/
composeApp/
shared/
  core/<capability>/
  feature/<capability>/     # common client models/contracts when useful
serverApp/                  # Spring Boot application, configuration, and wiring
server/
  feature/<capability>/     # server use cases, persistence, and HTTP adapters
~~~

- Add a JVM target to shared contracts only when the server benefits from consuming them.
- Apply Spring Boot entry-point and plugin configuration in the non-KMP serverApp module.
- Keep API models separate from database entities. API, persistence, and UI models need not be one-to-one.
- Keep Spring annotations, R2DBC, and server configuration within the JVM boundary.

## Dependency direction

~~~text
androidApp ─┐
iosApp ─────┼─> composeApp (CMP profile only)
            └─> shared feature/core contracts
serverApp ─────> shared JVM-compatible contracts (when selected)
~~~

UI modules consume feature and client contracts instead of calling platform SDKs directly. Platform implementations sit behind public common contracts. App targets use only the [approved dependency allowlist](dependency-policy.md).

## When to create a module

Create a module when at least one of these is true:

- It requires a different platform target or Gradle plugin.
- Its public API and dependency direction need an enforceable boundary.
- It can be tested, released, or reused independently.
- It has a measurable build-parallelism or team-ownership benefit.

Start with app, core, feature, and optional server boundaries. Do not optimize for module count.
