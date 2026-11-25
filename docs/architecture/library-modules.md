# Weft Bridge Library Modules

The bridge-library workspace is a multi-project Gradle build inside the Weft repository. Each capability is a separate Kotlin Multiplatform artifact with its own API and dependencies. `:weft-core` owns the plugin installation contract that capability libraries and apps share. The BOM aligns versions, and artifacts are released as a coordinated set.

## Initial modules

| Gradle path | Artifact ID | Responsibility | Current milestone |
| --- | --- | --- | --- |
| `:weft-core` | `weft-core` | Plugin installation container (`Weft`, `WeftConfig`, `WeftPlugin`, `Weft.get`) | M1: plugin installation core |
| `:weft-map` | `weft-map` | Map capability contracts and platform adapters | M0: build and publication skeleton |
| `:weft-login` | `weft-login` | Cross-platform sign-in capability | M0: build and publication skeleton |
| `:weft-push` | `weft-push` | Push registration and notification capability | M0: build and publication skeleton |
| `:weft-bom` | `weft-bom` | Maven BOM aligning the four KMP artifacts | M0: build and publication skeleton |
| `:weft-gradle-plugin` | not published | Gradle plugin with the offline wiring check and the aggregated health check | M1: plugin and health verification |

All publications use the group `run.thespot.weft` and the shared development version `0.1.0-SNAPSHOT`. The `run.thespot` namespace must be verified in Central Portal before the first release. Apart from `:weft-core`, the KMP module skeletons intentionally contain no public API or vendor SDK; none of them are release candidates.

## Login capability roadmap

The `:weft-login` contract will use OAuth 2.0 Authorization Code with PKCE for native sign-in. It must support the Supabase Auth exchange and a PKCE-capable OIDC issuer for Spring-backed profiles, while keeping app modules on the approved Weft/Ktor/Ktorfit allowlist. M1 defines provider-neutral results and configuration; M2 implements system-browser callbacks, verifier/session storage, and the provider adapters; M3 verifies both provider paths in a separate consumer. PKCE is a Weft sign-in policy and does not inherently require an application backend.

## Plugin installation model

`:weft-core` lets an app assemble its capabilities in one Ktor-style block instead of wiring each library separately:

~~~kotlin
val weft = Weft {
    install(NaverMap) {
        appKey = "naver-app-key"
    }
    install(WeftLogin) {
        redirectUri = "app://callback"
    }
}

val map = weft.get<MapPlugin>()
~~~

Each plugin declares the `KClass` it installs under, and a capability family publishes one interface that all of its implementations use (for example `MapPlugin` for Naver, Kakao, and Google map plugins, declared as `override val type: KClass<MapPlugin> = MapPlugin::class`). Consumers and views resolve the active implementation through `weft.get<MapPlugin>()`, so switching providers is an installation change rather than a UI change.

Because the plugin type identifies the slot, only one plugin per type may be installed; a second installation throws `DuplicateWeftPluginException` naming the type and both plugin names. Resolution through `get<T>()` (or `get(KClass)`) throws `MissingWeftPluginException` when no plugin is installed, while `getOrNull<T>()` returns `null` for optional capabilities. Plugin `install` hooks run once after every registration is prepared, so a plugin may resolve peer plugins from the completed `Weft` scope.

`:weft-core` holds no vendor or platform types. Capability modules add their plugin types, configuration, and adapters on top of it and keep Android and Apple SDK types in their platform source sets.

## Compose entry point

`:weft-core` also owns the one place a composition receives the runtime: `LocalWeft` (a `staticCompositionLocalOf` whose read fails with an explanatory message when nothing provides it) and `WeftApp(weft) { ... }`, which provides it. The module therefore depends on the Compose Multiplatform runtime and applies the Compose compiler through the `weft.compose` convention; Material, UI, and foundation stay out, so the dependency cannot grow into a UI toolkit on consumers.

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

`:weft-gradle-plugin` aggregates those probes with the endpoints a build configures and runs them in one `verifyWeftHealth` pass; its offline `verifyWeftPlugins` task runs inside `check`. Both are described in [`weft-gradle-plugin/AGENTS.md`](../../weft-gradle-plugin/AGENTS.md).

## Source ownership

Each module uses the standard KMP source-set boundaries:

~~~text
<module>/
  src/commonMain/kotlin/      # public API and platform-neutral models
  src/commonTest/kotlin/      # shared API behavior checks
  src/androidMain/kotlin/     # Android SDK adapter
  src/androidHostTest/kotlin/ # Android host-side checks when needed
  src/iosMain/kotlin/         # Kotlin/Native adapter and Apple interop
~~~

Only create a source-set implementation when a module milestone needs it. Keep Android and Apple SDK dependencies in their platform source sets. The BOM is metadata-only and constrains all four library artifacts to the shared release version; it does not add runtime dependencies. `:weft-core` owns the only cross-module contract; store shared API in it rather than adding a second shared module.

## Platform targets

The common convention currently declares Android, iOS device (`iosArm64`), and Apple Silicon simulator (`iosSimulatorArm64`) targets. Android uses compile SDK 36, min SDK 24, and JVM bytecode target 17. These are initial build defaults, not yet the minimum OS support promise of any released artifact. The Intel simulator (`iosX64`) target is intentionally absent: Compose Multiplatform stopped publishing `iosX64` artifacts (the last published version is `1.11.0-alpha01`), so a library that depends on the Compose runtime, `:weft-core` included, cannot resolve that target.

The official Kotlin compatibility guide lists Kotlin 2.4.21 with Gradle 7.6.3–9.7.0 and AGP 8.5.2–9.3.1. The repository pins the upper compatible versions for this baseline. Recheck the guide before changing the tuple, and record CI results separately from official compatibility ranges.

## Distribution boundary

Maven Central receives KMP metadata and target publications consumed by Gradle. This setup does not create an XCFramework or a SwiftPM package. SwiftPM distribution remains a distinct milestone with separate package metadata, binary hosting, checksum, and Apple archive validation.

## References

- [Kotlin Multiplatform compatibility guide](https://kotlinlang.org/docs/multiplatform/multiplatform-compatibility-guide.html)
- [Android KMP library plugin](https://developer.android.com/kotlin/multiplatform/plugin)
- [KMP publication setup](https://kotlinlang.org/docs/multiplatform/multiplatform-publish-lib-setup.html)
