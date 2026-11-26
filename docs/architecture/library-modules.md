# Weft Bridge Library Modules

The bridge-library workspace is a multi-project Gradle build inside the Weft repository. Each published capability is a separate Kotlin Multiplatform leaf with its own API and dependencies. `:weft-core` owns the plugin installation and provider-neutral map contracts. The BOM aligns versions, and artifacts are released as a coordinated set.

## Initial modules

| Gradle path | Artifact ID | Responsibility | Current milestone |
| --- | --- | --- | --- |
| `:weft-core` | `weft-core` | Plugin container, map models, Compose `WeftMap` and content tree | M1: plugin and map contracts |
| `:weft-map` | none | Namespace parent for provider leaves | No tasks or publication |
| `:weft-map:kakaomap` | `weft-kakaomap` | Kakao Maps provider binding and Android/iOS adapters | M1: implemented; local fixture passes, CI/remote consumer pending |
| `:weft-login` | `weft-login` | Cross-platform sign-in capability | M0: build and publication skeleton |
| `:weft-push` | `weft-push` | Push registration and notification capability | M0: build and publication skeleton |
| `:weft-bom` | `weft-bom` | Maven BOM aligning the four KMP artifacts | M0: build and publication skeleton |
| `:weft-gradle-plugin` | not published | Gradle plugin with the offline wiring check and the aggregated health check | M1: plugin and health verification |

Published artifacts use group `run.thespot.weft` and shared development version `0.1.0-SNAPSHOT`. The `run.thespot` namespace must be verified in Central Portal before the first release. Login and push remain scaffolding; the Kakao provider is implemented but is not a release candidate until CI and a separately published consumer validate its Android/iOS path.

## Login capability roadmap

The `:weft-login` contract will use OAuth 2.0 Authorization Code with PKCE for native sign-in. It must support the Supabase Auth exchange and a PKCE-capable OIDC issuer for Spring-backed profiles, while keeping app modules on the approved Weft/Ktor/Ktorfit allowlist. M1 defines provider-neutral results and configuration; M2 implements system-browser callbacks, verifier/session storage, and the provider adapters; M3 verifies both provider paths in a separate consumer. PKCE is a Weft sign-in policy and does not inherently require an application backend.

## Plugin installation model

`:weft-core` lets an app assemble its capabilities in one Ktor-style block instead of wiring each library separately:

~~~kotlin
val weft = Weft {
    install(KakaoMap) {
        nativeAppKey = "kakao-native-app-key"
    }
    install(WeftLogin) {
        redirectUri = "app://callback"
    }
}

val map = weft.get<MapPlugin>()
~~~

Each plugin declares the `KClass` it installs under, and a capability family publishes one interface that its implementations use. `KakaoMap` installs as `MapPlugin`; `WeftMap` resolves that contract from `LocalWeft` and publishes immutable camera, marker, polyline, polygon, and circle snapshots to the platform adapter. A future provider can implement the same interface without changing map UI code.

Because the plugin type identifies the slot, only one plugin per type may be installed; a second installation throws `DuplicateWeftPluginException` naming the type and both plugin names. Resolution through `get<T>()` (or `get(KClass)`) throws `MissingWeftPluginException` when no plugin is installed, while `getOrNull<T>()` returns `null` for optional capabilities. Plugin `install` hooks run once after every registration is prepared, so a plugin may resolve peer plugins from the completed `Weft` scope.

`:weft-core` holds no vendor or platform types. Provider modules add their plugin types, configuration, and adapters on top of it and keep Android and Apple SDK types in their platform source sets.

## Compose entry point

`:weft-core` also owns the one place a composition receives the runtime: `LocalWeft` (a `staticCompositionLocalOf` whose read fails with an explanatory message when nothing provides it) and `WeftApp(weft) { ... }`, which provides it. The map DSL and `WeftMap` require Compose Runtime, UI, and Foundation, exposed as API dependencies; this does not introduce a Material dependency.

Because the composition local is public API, the Compose runtime is an `api` dependency: an app that imports `LocalWeft` gets the same runtime it already uses for its screens.

## Health checks

Each capability library knows which endpoint accepts its credential, so the endpoint description lives with the plugin, not in a generic checker. `WeftHealthCheck` carries a name, a URL, headers, and a method; `WeftPlugin.healthCheck(plugin)` reports the probes for a prepared plugin; `Weft.healthChecks()` collects them in installation order and performs no I/O.

~~~kotlin
override fun healthCheck(plugin: SupabasePlugin): List<WeftHealthCheck> = listOf(
    WeftHealthCheck(
        name = "supabase-auth",
        url = "${plugin.config.url}/auth/v1/health",
        headers = mapOf("apikey" to plugin.config.anonKey),
    ),
)
~~~

`:weft-gradle-plugin` aggregates those probes with the endpoints a build configures and runs them in one `verifyWeftHealth` pass; its offline `verifyWeftPlugins` task runs inside `check` and also rejects credentials that must never reach a client build (a Supabase `service_role` JWT, a provider secret token, or a privileged property such as `serviceRoleKey`), reporting only `file:line` and the category so the value is never printed. Both are described in [`weft-gradle-plugin/AGENTS.md`](../../weft-gradle-plugin/AGENTS.md).

## Dependency allowlist verification

The direct-dependency policy in [Application dependency policy](dependency-policy.md) is enforced for Gradle builds by the same plugin. `verifyWeftDependencies` compares every external module a module declares against `allowedDependencyGroups` and `allowedDependencies`, with defaults for the Weft, Kotlin, Compose, Kotlinx, Ktor, AndroidX, and Google groups. A build that adopts the policy sets `weft { enforceDependencyWhitelist = true }`, which also runs the check with `check`; without that switch the task exists but is not part of the build. Project and file dependencies are skipped because they have no Maven coordinate, an approved library may bring any transitive dependency its API needs, and test source sets are outside the allowlist.

## Source ownership

Each module uses the standard KMP source-set boundaries:

~~~text
  <published-leaf>/
  src/commonMain/kotlin/      # public API and platform-neutral models
  src/commonTest/kotlin/      # shared API behavior checks
  src/androidMain/kotlin/     # Android SDK adapter
  src/androidHostTest/kotlin/ # Android host-side checks when needed
  src/iosMain/kotlin/         # Kotlin/Native adapter and Apple interop
~~~

Only create a source-set implementation when a module milestone needs it. Keep Android and Apple SDK dependencies in their platform source sets. The `:weft-map` parent is only a namespace; `:weft-map:kakaomap` is the published leaf. The BOM is metadata-only and constrains `weft-core`, `weft-kakaomap`, `weft-login`, and `weft-push` to the shared release version; it does not add runtime dependencies. `:weft-core` owns the provider-neutral map contract.

## Platform targets

The common convention currently declares Android, iOS device (`iosArm64`), and Apple Silicon simulator (`iosSimulatorArm64`) targets. Android uses compile SDK 37, min SDK 24, and JVM bytecode target 17; Compose 1.12 requires API 37. Kakao Maps supports iOS 13.0; its framework binaries explicitly lower Kotlin 2.4's default iOS 15 minimum to 13.0. This is the Kakao leaf's target, not a minimum OS promise for every Weft artifact. The Intel simulator (`iosX64`) target is intentionally absent: Compose Multiplatform stopped publishing `iosX64` artifacts (the last published version is `1.11.0-alpha01`), so a library that depends on the Compose runtime, `:weft-core` included, cannot resolve that target.

The official Kotlin compatibility guide lists Kotlin 2.4.21 with Gradle 7.6.3–9.7.0 and AGP 8.5.2–9.3.1. The repository pins the upper compatible versions for this baseline. Recheck the guide before changing the tuple, and record CI results separately from official compatibility ranges.

## Distribution boundary

The Kakao iOS adapter imports KakaoMapsSDK-SPM 2.12.19 through the Kotlin SwiftPM integration and packages the dependency lock/resource handling with the leaf. The integration is Alpha; local framework linking and an Xcode simulator consumer have been validated, but runtime simulator tests require Apple Silicon and a remote Maven consumer remains a release gate. Maven Central publishes the KMP Gradle artifact, not a standalone XCFramework for native Swift consumers.

## References

- [Kotlin Multiplatform compatibility guide](https://kotlinlang.org/docs/multiplatform/multiplatform-compatibility-guide.html)
- [Android KMP library plugin](https://developer.android.com/kotlin/multiplatform/plugin)
- [KMP publication setup](https://kotlinlang.org/docs/multiplatform/multiplatform-publish-lib-setup.html)
