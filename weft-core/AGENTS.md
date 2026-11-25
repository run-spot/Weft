# `:weft-core` Library

## Role

`weft-core` owns the plugin installation contract shared by every Weft capability library. It is published as `run.thespot.weft:weft-core` and version-aligned by `:weft-bom`. Apps and capability libraries depend on this artifact to declare plugins in one place with a Ktor-style block:

~~~kotlin
val weft = Weft {
    install(NaverMap) {
        appKey = "naver-app-key"
    }
    install(WeftLogin) {
        redirectUri = "app://callback"
    }
}
~~~

The module contains no vendor SDK, platform adapter, or capability API of its own. It must stay free of Android and Apple framework types so that capability modules on `commonMain` can depend on it.

## Milestones

- **M0 — Build and publication skeleton:** active. KMP Android and iOS targets, shared conventions, Apache-2.0 POM metadata, and Central staging are configured.
- **M1 — Plugin installation core:** active. `WeftPluginKey`, `WeftPlugin`, `WeftConfig`, `Weft`, the `Weft { }` factory, and duplicate/missing plugin errors are implemented in `commonMain` with `commonTest` coverage. Any capability plugin, including several implementations of one capability family, installs through this contract.
- **M2 — Consumer validation:** install a real capability plugin set (for example a map provider plus login) from a separate Gradle consumer, confirm the runtime resolves each capability, and review API stability and binary compatibility before a release candidate.

## Rules

- One plugin per `WeftPluginKey`. Plugins in one capability family share the key, so a second installation must fail fast with `DuplicateWeftPluginException` instead of silently replacing the first. Do not add a replace or force-install option without a recorded decision.
- A key is compared by name. Declare the key of a capability family once as a public constant and use a stable, namespaced name such as `weft.map`; never reuse a name for a different plugin or type.
- Keep `Context`, `Activity`, `UIViewController`, and vendor SDK types out of `commonMain`. A plugin that needs a platform type keeps it in its own platform source set or behind an injected boundary.
- Keep `prepare` side-effect free apart from building the configured instance. Bind into the runtime in `install`, which receives the completed registry, and resolve peer plugins there rather than in a constructor.
- Do not add dependencies to `commonMain` without a public API need; the core must stay dependency-free for consumers.
- Do not release the module before its consumer milestone is complete.

## Verification

- `./gradlew :weft-core:check`
- `./gradlew :weft-core:testAndroidHostTest`
- `./gradlew :weft-core:compileAndroidMain :weft-core:bundleAndroidMainAar`
- `./gradlew :weft-core:checkPomFileForKotlinMultiplatformPublication`
