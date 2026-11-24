---
name: weft-architect
description: "Choose a KMP/CMP project shape, required backend, and module architecture from product requirements. Use before creating or migrating a multiplatform app; do not use for ordinary feature implementation."
---

# Weft Architect

Turn product constraints into a reviewable KMP/CMP architecture decision. Start with target platforms, existing apps, UI-sharing goals, native SDK requirements, and backend ownership. If a Weft checkout is available, consult docs/architecture/project-shapes.md, module-layout.md, backend.md, apple-interop.md, dependency-policy.md, and toolchain-policy.md for the full rationale.

## Choose the backend first

A backend choice is mandatory: Spring Boot or Supabase. Ask the user: “Will the team operate a separate server for this product?” If the team will operate a separate server, choose Spring Boot; otherwise choose Supabase. Record which service owns identity, token issuance, application APIs, data, secrets, and operations. Do not offer a `none` backend profile.

Use OAuth 2.0 Authorization Code with PKCE for native app sign-in. PKCE itself does not require an application backend; this mandatory backend choice is Weft policy. For Spring Boot, identify the PKCE-capable authorization issuer and distinguish authorization-code exchange from the API's resource-server token validation. For Supabase, use Supabase Auth and record its project, callback URLs, RLS policies, Edge Functions, and secret provisioning.

## Choose the UI-sharing shape

Choose a profile matching the backend:

- kmp-core-supabase: share Kotlin domain/data behavior, keep platform-specific app UI in Kotlin, and use Supabase.
- kmp-core-spring-service: share Kotlin domain/data behavior, keep platform-specific app UI in Kotlin, and operate a Spring Boot service.
- cmp-supabase: share most Android/iOS UI with CMP and keep native app shells separate, using Supabase.
- cmp-spring-service: share most Android/iOS UI with CMP and keep native app shells separate, operating a Spring Boot service.

The UI-sharing choice is independent from the backend choice. App screens and lifecycle behavior stay in Kotlin in every profile.

Apply the dependency policy as a hard allowlist. App modules may directly use Weft artifacts, Montage KMP UI, Ktor/Ktorfit, and the listed Kotlin/Compose/platform foundations only. Do not recommend an unlisted library as a convenience or waive the policy for a project. In particular, do not add the Supabase Kotlin SDK directly to an app module; use the Weft login abstraction and approved networking. If a capability is missing, record a policy blocker and propose adding it to Weft or Montage through an explicit policy change.

Kotlin owns app startup and `UIApplicationDelegate`-level product behavior. Implement app UI, state, and business behavior in Kotlin. Keep Swift app code to Xcode bootstrap, except for Xcode widgets/extensions and the Objective-C-visible SwiftPM SDK façade defined by the Apple bridge policy.

Produce a concise architecture decision with:

1. Selected profile and why the team chose Spring Boot or Supabase.
2. Authentication issuer, PKCE callback, verifier/token storage boundary, and API token validation owner.
3. Shared and platform-specific responsibilities.
4. Module map with ownership and dependency direction.
5. Server, Supabase, and Apple integration decisions when applicable.
6. Toolchain status and unanswered compatibility questions.
7. Alternatives considered and observable signals for revisiting the decision.
8. The approved direct libraries for each application module and any capability blocked by the allowlist.
9. Kotlin/Swift ownership boundaries and the permitted Swift target files or exceptions.

Do not treat any previous project's domain, libraries, backend, or module count as the default. Label evidence as observed, validated, or hypothetical. Ask the required backend question if the choice is missing; do not silently select `none`.
