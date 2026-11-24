# Weft direction

## One-line definition

Weft provides KMP/CMP architecture choices for Android and iOS apps as documentation, project profiles, agent skills, and a verifiable harness.

## The problem

Starting a KMP/CMP project requires teams to decide how much code to share, whether to share UI with Compose Multiplatform, how iOS-native SDKs connect, where module boundaries belong, whether a server is needed, and which toolchain versions work together. Tutorials usually focus on one sample. Production repositories mix product-specific decisions with experiments, making them difficult to copy safely.

## Audience

- Teams adding an iOS target to an existing Android app.
- Teams deciding how much Android and iOS UI to share.
- Library authors connecting Swift and Android SDKs behind Kotlin contracts.
- Developers using coding agents to plan and configure KMP projects.

## What We provide

1. **What to build:** compare Kotlin platform-specific UI over a shared KMP core, shared CMP UI, and an optional server.
2. **How to build it:** describe module ownership, platform boundaries, networking, state, testing, and release practices.
3. **A project harness:** select a profile, generate a directory structure and decision record, and verify that the output matches the profile.
4. **Agent skills:** guide architecture, scaffolding, Apple interop, and architecture review as separate tasks.
5. **KMP bridge libraries:** organize capability libraries as separate KMP artifacts, align them with a BOM, then validate a real Android/iOS SDK pair and release the coordinated set.
6. **A library build harness:** share Gradle conventions, publication metadata, and release gates across the independent artifacts.

## Principles

- **Record decisions first.** Decide whether to share UI and whether to run a server before generating code.
- **Offer choices instead of one universal structure.** Explain the costs and alternatives for each recommended profile.
- **Keep evidence and limits together.** Distinguish project observations, reproduced results, and unverified hypotheses.
- **Keep common APIs platform-neutral.** Android SDK concepts may inform compatibility, but Android-only types must not appear in common APIs.
- **Keep the application dependency set closed.** Use only Weft, Montage, approved networking, and platform foundations; add capabilities through Weft or Montage instead of introducing one-off libraries.
- **Date and source changing version claims.** For AGP 9+, distinguish the Kotlin Gradle Plugin compatibility table from actual build results.
- **State what the generator produces.** The current scaffold creates module directories and decision records; it does not claim to generate a buildable app.

## Scope

### First milestone

- Document project shapes, module boundaries, backend choices, Apple interop, and toolchain policy.
- Provide three Android/iOS project profiles.
- Provide the plan, scaffold, and verify CLI commands with CI checks.
- Connect four Codex skills to the docs and CLI.

### Later milestones

- Add buildable CMP and KMP-core templates using officially compatible version tuples.
- Implement the first real bridge API and platform adapters, then validate them through a separate consumer project.
- Create a minimal bridge pilot with a SwiftPM Objective-C-visible façade.
- Select an SDK pair that provides the same user-facing capability on Android and iOS, then verify its API and distribution.
- After validation, release the KMP artifacts to Maven Central and automate separate SwiftPM XCFramework distribution.

## First release acceptance criteria

- Users can choose one of three profiles and understand its architectural consequences.
- The scaffold consistently creates the selected module map and a WEFT.md decision record.
- Verification catches unknown profiles, missing modules, unfinished skills, missing required docs, and unapproved direct app dependencies.
- Documentation does not conflate SwiftPM import with Kotlin framework distribution.
- Kotlin/AGP/Gradle support claims are traceable to official compatibility data.
