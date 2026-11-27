# `:weft-gradle-plugin`

## Role

`:weft-gradle-plugin` is the Gradle plugin that keeps a Weft app honest about its capability
wiring. Apply it to an app or shared module:

~~~kotlin
plugins {
    id("weft.verify")
}
~~~

It registers three tasks and the `weft { }` extension:

| Task | Network | Wired into `check` | Purpose |
| --- | --- | --- | --- |
| `verifyWeftPlugins` | no | yes | Every required `.get<T>()` has an installed plugin or configured `installedTypes` entry, every credential literal in an `install(...)` block is real, and no secret or privileged credential ships in client code |
| `verifyWeftDependencies` | no | only with `weft { enforceDependencyWhitelist = true }` | Every declared dependency is on the Weft allowlist |
| `verifyWeftHealth` | yes | no | Every configured and module-declared health check answers correctly |

The split is deliberate: `check` must pass in an offline sandbox and on every pull request, while a
credential probe needs the provider. A release job or a developer runs `./gradlew verifyWeftHealth`.
The dependency allowlist stays opt-in so an existing build keeps its current dependency set until it
adopts the policy.

## `verifyWeftPlugins`

Scans Kotlin sources (default `src`, excluding build output, generated code, and test source sets)
and reports:

- **Missing capability**: a `.get<T>()` call on a Weft receiver (`weft`, `LocalWeft.current`, and
  anything ending in those) whose `T` is not provided by a scanned `install(...)` or the configured
  `installedTypes` metadata. `getOrNull<T>()` is an optional lookup and is ignored. The failure names
  the type and every `file:line` that resolves it. Contributions compiled into dependency artifacts
  are not source-scanned, so Koin consumers list those capability types in `installedTypes`.
- **Empty or placeholder credential**: an `install(...)` configuration block assigning a blank
  string, or an obvious placeholder such as `TODO`, `CHANGE_ME`, `YOUR_...`, `example`, to a
  credential-like property (`key`, `token`, `secret`, `clientId`, `url`, `uri`, `endpoint`, ...).
  Values that are computed at runtime are skipped rather than guessed.

Resolution of `install(NaverMap)` to `MapPlugin` uses, in order: an explicit
`override val type: KClass<MapPlugin> = MapPlugin::class`, the second type argument of a
`WeftPlugin<TConfig, TPlugin>` supertype, and the build's `pluginTypes` map for plugins declared
outside the scanned sources.

## Secret and privileged-credential blocking

The same offline task rejects a value that must never reach a client build. It scans each Kotlin
file as written, because the values live in string literals and a key left in a comment is still a
leaked key:

- **Supabase `service_role` JWT**: a JWT literal is decoded only far enough to read its `role` claim.
  `service_role` fails immediately; the public `anon` role is allowed, and a token without a `role`
  claim is left alone.
- **Provider secret tokens**: `sk-...` (OpenAI/Stripe style, including `sk-proj-...`), `ghp_`,
  `gho_`, `github_pat_`, `AKIA` + 16 characters, `xox[baprs]-`, and
  `-----BEGIN ... PRIVATE KEY-----` blocks.
- **Forbidden client properties** in an `install(...)` block, Koin `@Property` / `@PropertyValue`,
  or Koin `properties(mapOf(...))`: `serviceRole`, `serviceRoleKey`, `adminKey`, `secretKey`,
  `privateKey`, `clientSecret`. A property is rejected whether its value is a literal or read from
  the environment, because handing the app a privileged credential is the design error, not the
  literal.

**Strict masking**: no message, log line, or exception may contain the value. A finding reports
`file:line`, the category (for example `Supabase service_role JWT` or `Secret token prefix (sk-)`),
and the guidance `Sensitive keys must not be placed in client code or chat; move them to Supabase
Edge Functions (supabase secrets set).` The analyzer keeps no copy of the value, so the report cannot
leak one by accident.

`verifyWeftHealth` masks too: a provider that echoes the key it was sent in an error body, and a URL
that carries one as a query parameter, are redacted before the report is printed.

## `verifyWeftDependencies`

The task enforces the direct-dependency allowlist from `harness/dependency-policy.json`. It reads the
dependencies declared in this module and fails on an `group:name` that no rule approves:

~~~kotlin
weft {
    enforceDependencyWhitelist = true       // also run with `check`
    allowedDependencyGroups.add("de.mypkg") // approve a group, or the whole family with `de.mypkg.*`
    allowedDependencies.add("io.ktor:ktor-client-okhttp") // approve one exact module
}
~~~

- An `allowedDependencyGroups` entry approves its own group and any dotted child, so `io.ktor`
  covers `io.ktor.client.core`; an entry ending in `.*` approves the whole family, as `androidx.*`
  does. The default list is `run.thespot.weft`, `org.jetbrains.kotlin`, `org.jetbrains.compose`,
  `org.jetbrains.kotlinx`, `io.ktor`, `io.insert-koin`, `androidx.*`, and `com.google.*`; `allowedDependencies` is
  empty by default.
- Only declared external modules are inspected: project and file dependencies have no Maven
  coordinate, and an approved library may bring any transitive dependency its API needs. Test source
  sets are skipped because they do not ship with the app.
- The task runs on request at any time; wiring it into `check` is the explicit step that applies the
  policy to a module.

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
- Never put a detected secret, a header value, or a token from a response into a message. Report the
  category and `file:line`, and mask anything else that is printed.
- Keep `verifyWeftDependencies` opt-in for `check`; a library that is missing from the allowlist is
  a policy decision, not something a plugin should impose on a build that never asked for it.
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
