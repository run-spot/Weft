---
name: weft-scaffold
description: "Generate a reviewable KMP/CMP project layout from a chosen Weft profile. Use after architecture decisions; the current scaffold is structural and does not create build-ready Gradle or Xcode projects."
---

# Weft Scaffold

Use the Weft CLI after the UI-sharing shape, required backend, and output location are known. If backend ownership is not decided, ask whether the team will operate a separate server: yes selects Spring Boot; no selects Supabase. Do not offer a no-backend profile. OAuth 2.0 Authorization Code with PKCE is required by Weft for native sign-in; do not treat PKCE itself as requiring a backend. Locate the Weft checkout containing scripts/weft.py: prefer the current workspace root; otherwise use a path explicitly available to the task. If the CLI cannot be found, produce the selected plan and request the Weft checkout path before generating files.

If the checkout is available, consult docs/harness.md, docs/architecture/module-layout.md, and docs/architecture/dependency-policy.md, then:

1. List profiles with python3 scripts/weft.py profiles.
2. Print the selected plan with python3 scripts/weft.py plan --profile <profile-id>.
3. Generate with python3 scripts/weft.py scaffold --profile <profile-id> --name <kebab-case-name> --output <empty-or-new-path>.
4. Validate with python3 scripts/weft.py verify --project <generated-path>, then report generated modules and unresolved choices.

The generator refuses non-empty output directories. Do not delete or overwrite existing project content to make generation succeed. The generated WEFT.md records the selected backend, PKCE auth flow, strict direct-dependency allowlist, and `ios_implementation: kotlin-first`. Keep app startup, `UIApplicationDelegate`-level behavior, screens, state, and business logic in Kotlin. Swift app code is limited to an Xcode bootstrap/forwarder, widgets/extensions, or the narrow Objective-C-visible SwiftPM SDK façade. Use only Weft, Montage, the approved networking stack, and listed platform foundations; do not add one-off libraries or the Supabase SDK directly to app modules. Use `supabase/` for Supabase project configuration, migrations, RLS policies, and Edge Functions; keep credentials in ignored local files or deployment secret storage. The current version creates module directories, source-set placeholders, module READMEs, and WEFT.md. It does not pin tool versions or claim the project builds. Do not publish artifacts, create remote repositories, or add credentials as part of scaffolding.
