# `:weft-core` Library

## Role

`weft-core` owns the plugin installation contract shared by every Weft capability library. It is published as `run.thespot.weft:weft-core` and version-aligned by `:weft-bom`. Apps and capability libraries depend on this artifact to declare plugins with a Ktor-style block:

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

For modular applications, a library can expose a `WeftContribution` and the app
can pass all collected contributions to `Weft(contributions)`. Koin Annotations may collect those
library contributions, but Koin must remain outside this module and its public API.

The module contains no vendor SDK, platform adapter, or provider-specific implementation. It owns the shared installation contract and provider-neutral map contract, and must stay free of Android and Apple framework types so that capability modules on `commonMain` can depend on it.

Two pieces of the contract reach beyond installation:

- **`LocalWeft` and `WeftApp`** give composables the runtime without threading a parameter through
  every screen. The provider-neutral map contract also exposes Compose UI and foundation types,
  so `weft-core` publishes the Compose runtime, UI, and foundation dependencies and applies the
  Compose compiler through the `weft.compose` convention. It keeps Material, vendor SDK, and
  platform framework types out of `commonMain`.
- **`WeftHealthCheck` and `WeftPlugin.healthCheck`** let each capability library describe the HTTP
  probe that proves its configured credential works. `Weft.healthChecks()` only collects the
  descriptions; running them belongs to `:weft-gradle-plugin` (`verifyWeftHealth`) or an admin tool.

## Milestones

- **M0 — Build and publication skeleton:** active. KMP Android and iOS targets, shared conventions, Apache-2.0 POM metadata, and Central staging are configured.
- **M1 — Plugin installation core:** active. `WeftPlugin.type` (`KClass`), `WeftPlugin`, `WeftConfig`, `WeftContribution`, `Weft`, the `Weft { }` and `Weft(contributions)` factories, `Weft.get<T>()` / `Weft.getOrNull<T>()`, and duplicate/missing plugin errors are implemented in `commonMain`. Any capability plugin, including several implementations of one capability family, installs through this contract.
- **M1.5 — Compose entry point and health contract:** active. `LocalWeft` (`staticCompositionLocalOf`) and `WeftApp(weft) { ... }` provide the runtime to a composition and fail loudly when it is missing; `WeftHealthCheck` / `WeftPlugin.healthCheck` / `Weft.healthChecks()` describe the credential probes a capability reports. `commonTest` drives a headless composition, so the checks need no UI toolkit.
- **M2 — Consumer validation:** install a real capability plugin set (for example a map provider plus login) from a separate Gradle consumer, confirm the runtime resolves each capability, and review API stability and binary compatibility before a release candidate.

## Rules

- One plugin per plugin type. Implementations of one capability family declare the same type (`override val type: KClass<MapPlugin> = MapPlugin::class`), so a second installation must fail fast with `DuplicateWeftPluginException` instead of silently replacing the first. Do not add a replace or force-install option without a recorded decision.
- A `WeftContribution` may only register plugins through the supplied `WeftConfig`; do not create another runtime registry or put Koin types in `weft-core`.
- The plugin type is the storage slot and is compared by `KClass`. Declare the shared capability interface, never a concrete provider class, unless that provider is meant to be a separate capability; consumers then resolve it with `weft.get<MapPlugin>()` and `weft.getOrNull<MapPlugin>()`.
- Keep `Context`, `Activity`, `UIViewController`, and vendor SDK types out of `commonMain`. A plugin that needs a platform type keeps it in its own platform source set or behind an injected boundary.
- Keep `prepare` side-effect free apart from building the configured instance. Bind into the runtime in `install`, which receives the completed registry, and resolve peer plugins there rather than in a constructor.
- Do not add dependencies to `commonMain` without a public API need. Compose runtime, UI, and
  foundation are accepted because the public `LocalWeft`/map contract exposes their types; keep
  Material, vendor SDK, and platform framework types out.
- Keep the module free of networking. A plugin describes its health check as data; running it is the build tool's or the app's decision.
- A capability library declares its own health check next to the plugin that owns the credential, and returns an empty list when it has no remote credential.
- Do not release the module before its consumer milestone is complete.

## Verification

- `./gradlew :weft-core:check`
- `./gradlew :weft-core:testAndroidHostTest`
- `./gradlew :weft-core:compileAndroidMain :weft-core:bundleAndroidMainAar`
- `./gradlew :weft-core:checkPomFileForKotlinMultiplatformPublication`
- `./gradlew :weft-core:checkPomFileForIosArm64Publication :weft-core:checkPomFileForIosSimulatorArm64Publication`
