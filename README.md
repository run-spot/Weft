# Weft

**Weft is an architecture and project harness for Kotlin Multiplatform (KMP) and Compose Multiplatform (CMP).**

Weft helps a team answer four questions before generating project code:

1. What should this product share across Android and iOS?
2. How should shared and native code communicate?
3. Will the team operate a separate server (Spring Boot), or use Supabase?
4. Which modules should own each responsibility?

Weft combines architecture guidance, focused Codex skills, project profiles, and a structural scaffold CLI. Its recommendations are organized around explicit product decisions and reusable platform boundaries.

## Current status

Weft is an early open-source project. Its CLI creates a decision-recorded app layout, while the repository also hosts a separate KMP bridge-library workspace. The `:weft-core` module provides the Ktor-style plugin installation contract that capability libraries and apps share. The `:weft-map`, `:weft-login`, and `:weft-push` modules currently provide build and publication scaffolding; their provider APIs and adapters are later milestones.

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
run.thespot.weft:weft-map:0.1.0-SNAPSHOT
run.thespot.weft:weft-login:0.1.0-SNAPSHOT
run.thespot.weft:weft-push:0.1.0-SNAPSHOT
run.thespot.weft:weft-bom:0.1.0-SNAPSHOT
~~~

Capabilities are installed in one place through `:weft-core`, and a capability family such as maps exposes one plugin type so that its implementations are interchangeable:

~~~kotlin
val weft = Weft {
    install(NaverMap) {
        appKey = "naver-app-key"
    }
}

val map = weft.get<MapPlugin>()
~~~

Every map provider declares `type = MapPlugin::class`, so `weft.get<MapPlugin>()` returns whichever implementation was installed. Installing a second plugin for the same plugin type fails with `DuplicateWeftPluginException` instead of silently replacing the first.

Consumers can import the BOM to use the coordinated version without repeating it:

~~~kotlin
dependencies {
    implementation(platform("run.thespot.weft:weft-bom:1.0.0"))
    implementation("run.thespot.weft:weft-map")
}
~~~

The `run.thespot` Central namespace must be verified before the first release. The current modules contain publication and target configuration only; do not release them until the module milestones in their `AGENTS.md` files are complete.

~~~bash
./gradlew projects
./gradlew verify
./gradlew :weft-map:publishToMavenLocal
./gradlew :weft-bom:publishToMavenLocal
~~~

See [Maven Central publishing](docs/architecture/maven-central-publishing.md) for credentials, signing, and the release workflow. Maven Central KMP publications include target artifacts for Gradle consumers; SwiftPM XCFramework distribution is a separate milestone.

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
