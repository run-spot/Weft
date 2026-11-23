# Weft

**Weft is an architecture and project harness for Kotlin Multiplatform (KMP) and Compose Multiplatform (CMP).**

Weft helps a team answer three questions before generating project code:

1. What should this product share across Android and iOS?
2. How should shared and native code communicate?
3. Which modules should own each responsibility?

Weft combines architecture guidance, focused Codex skills, project profiles, and a structural scaffold CLI. It uses RunTheSpot as a case study while keeping its running-service domain and server choices out of the default template.

## Current status

Weft is an early, local prototype. `weft scaffold` creates a decision-recorded module layout, not a ready-to-build Android or iOS app. Gradle templates and a publishable bridge library are later milestones.

## Start here

- [Direction and delivery plan](docs/direction.md)
- [Project shapes](docs/architecture/project-shapes.md)
- [Module layout](docs/architecture/module-layout.md)
- [Apple interop and distribution](docs/architecture/apple-interop.md)
- [Bridge library contract](docs/architecture/bridge-library.md)
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

Run `./scripts/verify.sh` before proposing changes. Read [AGENTS.md](AGENTS.md) for the repository workflow. The open-source license has not been selected; choose one before the first source push.
