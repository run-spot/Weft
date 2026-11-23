---
name: weft-scaffold
description: "Generate a reviewable KMP/CMP project layout from a chosen Weft profile. Use after architecture decisions; the current scaffold is structural and does not create build-ready Gradle or Xcode projects."
---

# Weft Scaffold

Use the Weft CLI after the profile and output location are known. Locate the Weft checkout containing scripts/weft.py: prefer the current workspace root; otherwise use a path explicitly available to the task. If the CLI cannot be found, produce the selected plan and request the Weft checkout path before generating files.

If the checkout is available, consult docs/harness.md and docs/architecture/module-layout.md, then:

1. List profiles with python3 scripts/weft.py profiles.
2. Print the selected plan with python3 scripts/weft.py plan --profile <profile-id>.
3. Generate with python3 scripts/weft.py scaffold --profile <profile-id> --name <kebab-case-name> --output <empty-or-new-path>.
4. Validate with python3 scripts/weft.py verify --project <generated-path>, then report generated modules and unresolved choices.

The generator refuses non-empty output directories. Do not delete or overwrite existing project content to make generation succeed. The current version creates module directories, source-set placeholders, module READMEs, and WEFT.md. It does not pin tool versions or claim the project builds. Do not publish artifacts, create remote repositories, or add credentials as part of scaffolding.
