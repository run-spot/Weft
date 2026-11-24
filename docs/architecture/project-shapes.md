# Choosing a project shape

KMP lets a team choose how much non-UI Kotlin code to share. CMP lets a team share Compose UI across platforms. Treat these as separate decisions.

## Three starting profiles

| Profile | Android UI | iOS UI | Shared code | Server |
| --- | --- | --- | --- | --- |
| kmp-core-native-ui | Kotlin UI, usually Jetpack Compose | Kotlin UI (Compose Multiplatform preferred; Kotlin/UIKit only when justified), hosted by a thin Swift bootstrap | Domain, data, and selected presentation logic | None |
| cmp-client | Compose Multiplatform | Compose Multiplatform with a thin Swift wrapper | UI and client logic | None |
| cmp-spring-service | Compose Multiplatform | Compose Multiplatform with a thin Swift wrapper | Client and selected contracts | Spring Boot |

The last profile adds a server only when the product chooses to operate its own API. A server is not the default for cmp-client. App screens and app lifecycle behavior stay in Kotlin in every profile.

## Decision flow

### 1. Decide the iOS product experience

- Does the product need an Xcode widget or extension that must stay native?
- Are there iOS-specific interaction, accessibility, or animation requirements?
- Can those requirements be implemented in Kotlin/CMP while keeping the Swift Xcode shell thin?

### 2. Choose the sharing boundary

- Start with kmp-core-native-ui when sharing domain/data behavior while keeping platform-specific UI implementations in Kotlin is the goal.
- Consider cmp-client when a single UI implementation across Android and iOS helps the team deliver faster.
- A mixed strategy is valid: share selected CMP screens and keep Kotlin platform-specific UI where behavior differs.

### 3. Choose who operates the backend

- If an existing server is available, do not generate a server module.
- If the product needs its own API, review deployment, authentication, and data-retention responsibilities before choosing cmp-spring-service or a separate backend.

## Record these decisions

- The UI, state, domain rules, and data access that will be shared.
- Platform-specific functionality and why it remains native.
- The iOS SDK integration approach and its maturity.
- The approved direct libraries for each application module, following the [dependency policy](dependency-policy.md).
- Server ownership, API contract source, and operational responsibility.
- Observable signals that could justify revisiting the choice, such as UI delivery bottlenecks, platform-specific code volume, or build time.

A profile is a starting structure. Do not force product features into modules that do not fit.
