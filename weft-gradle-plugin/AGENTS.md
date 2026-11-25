# `:weft-gradle-plugin`

## Role

`:weft-gradle-plugin` is the Gradle plugin that keeps a Weft app honest about its capability
wiring. Apply it to an app or shared module:

~~~kotlin
plugins {
    id("weft.verify")
}
~~~

It registers two tasks and the `weft { }` extension:

| Task | Network | Wired into `check` | Purpose |
| --- | --- | --- | --- |
| `verifyWeftPlugins` | no | yes | Every required `.get<T>()` has an installed plugin, and every credential literal in an `install(...)` block is real |
| `verifyWeftHealth` | yes | no | Every configured and module-declared health check answers correctly |

The split is deliberate: `check` must pass in an offline sandbox and on every pull request, while a
credential probe needs the provider. A release job or a developer runs `./gradlew verifyWeftHealth`.

## `verifyWeftPlugins`

Scans Kotlin sources (default `src`, excluding build output, generated code, and test source sets)
and reports:

- **Missing capability**: a `.get<T>()` call on a Weft receiver (`weft`, `LocalWeft.current`, and
  anything ending in those) whose `T` is not provided by any `install(...)`. `getOrNull<T>()` is an
  optional lookup and is ignored. The failure names the type and every `file:line` that resolves it.
- **Empty or placeholder credential**: an `install(...)` configuration block assigning a blank
  string, or an obvious placeholder such as `TODO`, `CHANGE_ME`, `YOUR_...`, `example`, to a
  credential-like property (`key`, `token`, `secret`, `clientId`, `url`, `uri`, `endpoint`, ...).
  Values that are computed at runtime are skipped rather than guessed.

Resolution of `install(NaverMap)` to `MapPlugin` uses, in order: an explicit
`override val type: KClass<MapPlugin> = MapPlugin::class`, the second type argument of a
`WeftPlugin<TConfig, TPlugin>` supertype, and the build's `pluginTypes` map for plugins declared
outside the scanned sources.

## `verifyWeftHealth`

Aggregates every probe in one run:

1. `weft { healthCheck("name") { url = ...; header("apikey", ...) } }` entries from the build.
2. Literal `WeftHealthCheck(...)` declarations in the scanned sources, which is how a capability
   library reports the endpoint it owns.
3. Probes derived from literal credentials in `install(...)` blocks: Supabase
   (`url` + `anonKey` → `<url>/auth/v1/health` with `apikey`) and Kakao (`restApiKey`/`apiKey`/
   `appKey` → the Local API address search with `Authorization: KakaoAK <key>`).

A blank credential fails before any request is sent; a 401/403 is reported as a rejected credential;
another non-2xx status or an unreachable host fails with the reason. Every check runs, so one run
shows all broken credentials.

The endpoint contract lives in `:weft-core`: `WeftHealthCheck` and `WeftPlugin.healthCheck`. Each
capability module knows its provider's URL and headers, so it declares the probe and the Gradle
plugin only collects and runs them.

## Rules

- Keep `verifyWeftPlugins` offline and deterministic: no network, no provider SDK, no Gradle worker
  that needs a device. `check` depends on it.
- Keep `verifyWeftHealth` out of `check`; it reaches the network and needs real credentials.
- Do not hard-code vendor knowledge in a second place. A new provider belongs in its capability
  module as a `WeftHealthCheck`, with a derived fallback only when the credential is written
  literally in an `install(...)` block.
- Treat unreadable values as unknown, not as failures: interpolation, variables, and calls are
  skipped so the checks never invent a false positive.
- Keep the analyzer independent of Gradle types (`run.thespot.weft.gradle.analysis`) so it stays
  unit-testable without a Gradle build.

## Verification

- `./gradlew :weft-gradle-plugin:test`
- `./gradlew :weft-gradle-plugin:validatePlugins`
- `./gradlew :weft-gradle-plugin:check`
- `./scripts/verify.sh` (repository language, docs, and links)
