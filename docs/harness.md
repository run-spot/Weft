# Project harness

The Weft harness converts a selected architecture profile into a reviewable project layout and checks for missing or inconsistent structure.

## Current commands

- profiles: list available profiles and descriptions.
- plan: print a profile's modules and intent without writing files.
- scaffold: create WEFT.md, a project README, module READMEs, and empty module/source-set directories.
- verify: validate profile JSON, required docs, skill frontmatter, Markdown links, generated module layouts, the strict application dependency policy, and generated BOM coordinates/constraints.

The scaffold records `dependency_policy: strict-direct-dependency-allowlist` and `ios_implementation: kotlin-first` in WEFT.md. When Gradle build files are present, project verification checks direct dependency coordinates, `add`/`addProvider` declarations, and aliases from the default version catalog against `harness/dependency-policy.json`. Unsupported dependency notation fails closed. Skills are also checked for their required dependency and Kotlin-first Apple-platform rules. This version does not generate Gradle build files, an Xcode project, dependency versions, or feature code. Do not describe its output as a buildable app.

## Profiles

- kmp-core-native-ui: share Kotlin core behavior, implement platform-specific UI in Kotlin, and keep Swift app shells thin.
- cmp-client: share Android/iOS screens with CMP and keep native app shells as Kotlin bootstraps.
- cmp-spring-service: add a separate Spring Boot server to a CMP client.

Profiles are JSON manifests under harness/profiles. Each profile must provide a unique ID, intended use, module list, and responsibility for each module.

## Generated project record

WEFT.md records:

- Weft profile ID and generator version.
- UI-sharing and server choices.
- Generated modules and responsibilities.
- The unresolved toolchain tuple and next decisions.
- The strict application dependency policy that generated modules must follow.
- Kotlin-first iOS ownership and the narrow permitted Swift boundaries.

Update the manifest and module READMEs when changing a generated project, before treating the structure as a new template.

## Next harness milestones

1. Add a minimal KMP-core template that builds with an officially compatible tuple.
2. Add an Android app shell, CMP module, and iOS wrapper with Android/iOS CI.
3. Validate one SwiftPM-import fixture, including clean/incremental build and a native SDK flow.
4. Mark a scaffold stable only after its template and release workflow are reproducible.
