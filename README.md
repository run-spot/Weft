# Weft

**Weft is an architecture and project harness for Kotlin Multiplatform (KMP) and Compose Multiplatform (CMP).**

Weft helps a team answer four questions before generating project code:

1. What should this product share across Android and iOS?
2. How should shared and native code communicate?
3. Will the team operate a separate server (Spring Boot), or use Supabase?
4. Which modules should own each responsibility?

Weft combines architecture guidance, focused Codex skills, project profiles, and a structural scaffold CLI. Its recommendations are organized around explicit product decisions and reusable platform boundaries.

## Current status

Weft is an early open-source project. Its CLI creates a decision-recorded app layout, while the repository also hosts a KMP bridge-library workspace. `:weft-core` provides the plugin installation contract, health-check contract, provider-neutral map models, and Compose APIs (`LocalWeft`, `WeftApp`, and `WeftMap`). The Kakao map provider is the first map implementation in `:weft-map:kakaomap`, configured with artifact ID `weft-kakaomap`; `:weft-map` itself is only a namespace project. The provider has Android and iOS adapters, with Kakao Maps SDK 2.15.2 for Android and SwiftPM KakaoMapsSDK-SPM 2.12.19 for iOS. Login and push remain build/publication scaffolding. These libraries are not yet published.

## Start here

- [Direction and delivery plan](docs/direction.md)
- [Project shapes](docs/architecture/project-shapes.md)
- [Module layout](docs/architecture/module-layout.md)
- [Apple interop and distribution](docs/architecture/apple-interop.md)
- [Bridge library contract](docs/architecture/bridge-library.md)
- [Bridge library modules](docs/architecture/library-modules.md)
- [Maven Central publishing](docs/architecture/maven-central-publishing.md)
- [Backend choices](docs/architecture/backend.md)
- [Application dependency policy](docs/architecture/dependency-policy.md)
- [Toolchain policy](docs/architecture/toolchain-policy.md)
- [Harness design and profiles](docs/harness.md)
- [Open questions](docs/open-questions.md)

## Try the harness

```bash
python3 scripts/weft.py profiles
python3 scripts/weft.py plan --profile cmp-supabase
python3 scripts/weft.py scaffold --profile cmp-supabase --name sample-app --output /tmp/sample-app
python3 scripts/weft.py verify --project /tmp/sample-app
python3 scripts/weft.py verify
```

The scaffold records architecture choices and creates the planned module tree. Every profile selects Spring Boot or Supabase and records OAuth 2.0 Authorization Code with PKCE for native sign-in. This is a Weft policy, not a protocol requirement for PKCE. The scaffold deliberately does not invent Kotlin, AGP, Compose, or Xcode versions; choose a version tuple from the reviewed compatibility policy before adding build scripts.

## Build the bridge libraries

The Gradle workspace includes Kotlin Multiplatform libraries for the plugin core, map, login, and push notifications, plus a Java Platform BOM that aligns their versions. Their initial artifact coordinates are:

~~~text
run.thespot.weft:weft-core:0.1.0-SNAPSHOT
run.thespot.weft:weft-kakaomap:0.1.0-SNAPSHOT
run.thespot.weft:weft-login:0.1.0-SNAPSHOT
run.thespot.weft:weft-push:0.1.0-SNAPSHOT
run.thespot.weft:weft-bom:0.1.0-SNAPSHOT
~~~

Capabilities are installed in one place through `:weft-core`, and a capability family such as maps exposes one plugin type so that its implementations are interchangeable:

~~~kotlin
val weft = Weft {
    install(KakaoMap) {
        nativeAppKey = "kakao-native-app-key"
    }
}

val mapPlugin = weft.get<MapPlugin>()
~~~

Every map provider declares `type = MapPlugin::class`, so `weft.get<MapPlugin>()` returns whichever implementation was installed. Installing a second plugin for the same plugin type fails with `DuplicateWeftPluginException` instead of silently replacing the first.

Composables read the runtime from the composition instead of receiving it as a parameter. `WeftApp` provides it once at the root, so screens depend only on the capability they use:

~~~kotlin
setContent {
    WeftApp(weft) {
        val map = LocalWeft.current.get<MapPlugin>()
        MapScreen(map)
    }
}
~~~

Reading `LocalWeft` outside `WeftApp` fails during composition with a message that names the wrapper, and a preview or UI test provides a fake runtime the same way.

`WeftMap` delegates to the installed provider. Its child content is provider-neutral and keyed by marker ID:

~~~kotlin
WeftApp(weft) {
    WeftMap(WeftMapState(CameraPosition(LatLng(37.5665, 126.9780)))) {
        Marker(MapMarker(id = "seoul-city-hall", position = LatLng(37.5665, 126.9780)))
    }
}
~~~

## Verify plugin wiring

`:weft-gradle-plugin` keeps an app honest about what it installed. Add it to the app module:

~~~kotlin
plugins {
    id("weft.verify")
}
~~~

`verifyWeftPlugins` is offline and part of `check`: it fails when a required `.get<T>()` has no installed plugin (naming the type and every `file:line`), when an `install(...)` block configures a credential as an empty or placeholder string, or when client code contains a credential that must not ship — a Supabase `service_role` JWT, a provider secret token (`sk-`, `ghp_`, `AKIA...`, `xox[baprs]-`, a private key block), or a privileged property such as `serviceRoleKey`. A finding reports only the category and `file:line`; the token never appears in the build output. `verifyWeftHealth` runs the health checks instead, and fails when a credential is blank, rejected (401/403), or the endpoint is unreachable.

`verifyWeftDependencies` enforces the direct-dependency allowlist. It is opt-in, because adopting it is a policy decision:

~~~kotlin
weft {
    enforceDependencyWhitelist = true
    allowedDependencyGroups.add("com.example")  // or the whole family with "com.example.*"
}
~~~

Capability libraries own their probes, because each provider knows its own endpoint. A plugin reports them through `WeftPlugin.healthCheck`, and a build can add one more:

~~~kotlin
weft {
    healthCheck("supabase-auth") {
        url = "https://project.supabase.co/auth/v1/health"
        header("apikey", providers.environmentVariable("SUPABASE_ANON_KEY").get())
    }
}
~~~

~~~bash
./gradlew verifyWeftPlugins       # offline, also runs with `check`; blocks secrets too
./gradlew verifyWeftDependencies  # offline; joins `check` when the whitelist is enforced
./gradlew verifyWeftHealth        # network; run before a release or when credentials change
~~~

Consumers can import the BOM to use the coordinated version without repeating it:

~~~kotlin
dependencies {
    implementation(platform("run.thespot.weft:weft-bom:1.0.0"))
    implementation("run.thespot.weft:weft-kakaomap")
}
~~~

The Kakao Android SDK is resolved from Kakao's Maven repository. Add it to the consumer's dependency repositories alongside Google and Maven Central:

~~~kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://devrepo.kakao.com/nexus/repository/kakaomap-releases/")
    }
}
~~~

The `run.thespot` Central namespace must be verified before the first release. The map API and platform adapters are implemented, but the bridge baseline remains a candidate until CI and a published consumer validate the full target set. See the module instructions and [toolchain policy](docs/architecture/toolchain-policy.md).

~~~bash
./gradlew projects
./gradlew verify
./gradlew :weft-core:publishToMavenLocal
./gradlew :weft-map:kakaomap:publishToMavenLocal
./gradlew :weft-bom:publishToMavenLocal
~~~

See [Maven Central publishing](docs/architecture/maven-central-publishing.md) for the local consumer fixture, credentials, signing, and the release workflow. The iOS provider currently imports the Kakao SDK through Kotlin's SwiftPM integration; publishing a native SwiftPM XCFramework for direct Swift consumers is a separate distribution path.

## Skills

The repository contains four installable Codex skills under `skills/`:

- `weft-architect` chooses a project shape and records architecture decisions.
- `weft-scaffold` generates a project layout from a Weft profile.
- `weft-apple-bridge` designs Kotlin-to-Apple SDK boundaries and distribution.
- `weft-review` checks a project against its recorded profile and Weft guidance.

To try a skill in Codex, link it into `~/.codex/skills/` and keep the Weft checkout available in the workspace. For example:

```bash
mkdir -p ~/.codex/skills
ln -s /absolute/path/to/Weft/skills/weft-architect ~/.codex/skills/weft-architect
```

A symlink keeps the skill and its repository guidance discoverable.

## Contributing

Run `./scripts/verify.sh` before proposing changes. Read [AGENTS.md](AGENTS.md) for the repository workflow. Weft is licensed under the [Apache License 2.0](LICENSE).
