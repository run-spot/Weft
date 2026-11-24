# Choosing a project shape

KMP lets a team choose how much non-UI Kotlin code to share. CMP lets a team share Compose UI across platforms. Treat these as separate decisions.

## Four starting profiles

| Profile | Android UI | iOS UI | Shared code | Backend |
| --- | --- | --- | --- | --- |
| kmp-core-supabase | Kotlin UI, usually Compose Multiplatform | Kotlin UI hosted by a thin Swift bootstrap | Domain, data, and selected presentation logic | Supabase |
| kmp-core-spring-service | Kotlin UI, usually Compose Multiplatform | Kotlin UI hosted by a thin Swift bootstrap | Domain, data, and selected presentation logic | Spring Boot |
| cmp-supabase | Compose Multiplatform | Compose Multiplatform with a thin Swift wrapper | UI and client logic | Supabase |
| cmp-spring-service | Compose Multiplatform | Compose Multiplatform with a thin Swift wrapper | Client and selected contracts | Spring Boot |

App screens and app lifecycle behavior stay in Kotlin in every profile. The UI-sharing choice and backend choice are independent: each UI shape has a Spring Boot and a Supabase profile. Weft requires one of those backend paths and OAuth 2.0 Authorization Code with PKCE for native sign-in; PKCE itself does not require an application backend.

## Decision flow

### 1. Decide the iOS product experience

- Does the product need an Xcode widget or extension that must stay native?
- Are there iOS-specific interaction, accessibility, or animation requirements?
- Can those requirements be implemented in Kotlin/CMP while keeping the Swift Xcode shell thin?

### 2. Choose the sharing boundary

- Start with a `kmp-core-*` profile when sharing domain/data behavior while keeping platform-specific UI implementations in Kotlin is the goal.
- Consider a `cmp-*` profile when a single UI implementation across Android and iOS helps the team deliver faster.
- A mixed strategy is valid: share selected CMP screens and keep Kotlin platform-specific UI where behavior differs.

### 3. Choose the backend path

- Ask: “Will this team operate a separate server for this product?”
- Yes: choose the Spring Boot profile matching the selected UI shape. Record server ownership, deployment, observability, incident response, data retention, and the PKCE-capable issuer.
- No: choose the Supabase profile matching the selected UI shape. Record the project, Auth configuration, RLS policies, Edge Functions, and secret provisioning.
- Do not choose `none`; selecting a backend is a Weft app policy. This policy must not be described as a requirement of the PKCE protocol.

## Record these decisions

- The UI, state, domain rules, and data access that will be shared.
- Platform-specific functionality and why it remains native.
- The iOS SDK integration approach and its maturity.
- The approved direct libraries for each application module, following the [dependency policy](dependency-policy.md).
- Backend path, authentication issuer, API contract source, secret provisioning, and operational responsibility.
- Observable signals that could justify revisiting the choice, such as UI delivery bottlenecks, platform-specific code volume, or build time.

A profile is a starting structure. Do not force product features into modules that do not fit.
