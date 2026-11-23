---
name: weft-architect
description: "Choose a KMP/CMP project shape and module architecture from product requirements. Use before creating or migrating a multiplatform app; do not use for ordinary feature implementation."
---

# Weft Architect

Turn product constraints into a reviewable KMP/CMP architecture decision. Start with target platforms, existing apps, UI-sharing goals, native SDK requirements, and backend ownership. If a Weft checkout is available, consult docs/architecture/project-shapes.md, module-layout.md, backend.md, apple-interop.md, and toolchain-policy.md for the full rationale.

Choose among these starting shapes:

- kmp-core-native-ui: share Kotlin domain/data behavior while keeping Android UI and SwiftUI native.
- cmp-client: share most Android/iOS UI with CMP and keep native app shells separate.
- cmp-spring-service: add an independently configured Spring Boot server to a CMP client.

The server is optional. CMP UI sharing is a product choice, not a KMP prerequisite.

Produce a concise architecture decision with:

1. Selected profile.
2. Shared and platform-specific responsibilities.
3. Module map with ownership and dependency direction.
4. Server and Apple integration decisions when applicable.
5. Toolchain status and unanswered compatibility questions.
6. Alternatives considered and observable signals for revisiting the decision.

Do not treat any previous project's domain, libraries, backend, or module count as the default. Label evidence as observed, validated, or hypothetical. When a required product choice is missing, use a reversible default only if context supports it; otherwise record the decision as open.
