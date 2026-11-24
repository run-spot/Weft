---
name: weft-architect
description: "Choose a KMP/CMP project shape and module architecture from product requirements. Use before creating or migrating a multiplatform app; do not use for ordinary feature implementation."
---

# Weft Architect

Turn product constraints into a reviewable KMP/CMP architecture decision. Start with target platforms, existing apps, UI-sharing goals, native SDK requirements, and backend ownership. If a Weft checkout is available, consult docs/architecture/project-shapes.md, module-layout.md, backend.md, apple-interop.md, dependency-policy.md, and toolchain-policy.md for the full rationale.

Choose among these starting shapes:

- kmp-core-native-ui: share Kotlin domain/data behavior while keeping platform-specific app UI in Kotlin and native shells thin.
- cmp-client: share most Android/iOS UI with CMP and keep native app shells separate.
- cmp-spring-service: add an independently configured Spring Boot server to a CMP client.

The server is optional. CMP UI sharing is a product choice, not a KMP prerequisite.

Apply the dependency policy as a hard allowlist. App modules may directly use Weft artifacts, Montage KMP UI, Ktor/Ktorfit, and the listed Kotlin/Compose/platform foundations only. Do not recommend an unlisted library as a convenience or waive the policy for a project. If a capability is missing, record a policy blocker and propose adding it to Weft or Montage through an explicit policy change.

Kotlin owns app startup and `UIApplicationDelegate`-level product behavior. Implement app UI, state, and business behavior in Kotlin. Keep Swift app code to Xcode bootstrap, except for Xcode widgets/extensions and the Objective-C-visible SwiftPM SDK façade defined by the Apple bridge policy.

Produce a concise architecture decision with:

1. Selected profile.
2. Shared and platform-specific responsibilities.
3. Module map with ownership and dependency direction.
4. Server and Apple integration decisions when applicable.
5. Toolchain status and unanswered compatibility questions.
6. Alternatives considered and observable signals for revisiting the decision.
7. The approved direct libraries for each application module and any capability blocked by the allowlist.
8. Kotlin/Swift ownership boundaries and the permitted Swift target files or exceptions.

Do not treat any previous project's domain, libraries, backend, or module count as the default. Label evidence as observed, validated, or hypothetical. When a required product choice is missing, use a reversible default only if context supports it; otherwise record the decision as open.
