# Weft

**Weft is an architecture and project harness for Kotlin Multiplatform (KMP) and Compose Multiplatform (CMP).**

Weft helps a team answer three questions before generating project code:

1. What should this product share across Android and iOS?
2. How should shared and native code communicate?
3. Which modules should own each responsibility?

Weft combines architecture guidance, focused Codex skills, project profiles, and a structural scaffold CLI. It uses RunTheSpot as a case study while keeping its running-service domain and server choices out of the default template.

## Current status

Weft is an early open-source project. Its CLI creates a decision-recorded app layout, while the repository also hosts a separate KMP bridge-library workspace. The `:map`, `:push`, and `:login` modules currently provide build and publication scaffolding; their provider APIs and adapters are later milestones.

## Start here

- [Direction and delivery plan](docs/direction.md)
- [Project shapes](docs/architecture/project-shapes.md)
- [Module layout](docs/architecture/module-layout.md)
- [Apple interop and distribution](docs/architecture/apple-interop.md)
- [Bridge library contract](docs/architecture/bridge-library.md)
- [Bridge library modules](docs/architecture/library-modules.md)
- [Maven Central publishing](docs/architecture/maven-central-publishing.md)
- [Backend choices](docs/architecture/backend.md)
- [Toolchain policy](docs/architecture/toolchain-policy.md)
- [RunTheSpot case study](docs/reference/runthespot.md)
- [Harness design and profiles](docs/harness.md)
- [Open questions](docs/open-questions.md)

## Try the harness

```bash
python3 scripts/weft.py profiles
python3 scripts/weft.py plan --profile cmp-client
python3 scripts/weft.py scaffold --profile cmp-client --name sample-app --output /tmp/sample-app
python3 scripts/weft.py verify --project /tmp/sample-app
python3 scripts/weft.py verify
```

The scaffold records architecture choices and creates the planned module tree. It deliberately does not invent Kotlin, AGP, Compose, or Xcode versions; choose a version tuple from the reviewed compatibility policy before adding build scripts.

## Build the bridge libraries

The Gradle workspace includes independent Kotlin Multiplatform libraries for map, push notifications, and login. Their initial artifact coordinates are:

~~~text
io.github.run-spot.weft:map:0.1.0-SNAPSHOT
io.github.run-spot.weft:push:0.1.0-SNAPSHOT
io.github.run-spot.weft:login:0.1.0-SNAPSHOT
~~~

The Central namespace must be verified before the first release. The current modules contain publication and target configuration only; do not release them until the module milestones in their `AGENTS.md` files are complete.

~~~bash
./gradlew projects
./gradlew verify
./gradlew :map:publishToMavenLocal
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
