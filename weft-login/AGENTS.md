# `:weft-login` Library

## Role

`weft-login` will expose a Kotlin-first sign-in API across Android and iOS while preserving familiar Android call semantics where they are safe to share. It is published as `run.thespot.weft:weft-login` and version-aligned by `:weft-bom`.

## Milestones

- **M0 — Build and publication skeleton:** active. KMP Android and iOS targets, shared conventions, Apache-2.0 POM metadata, and Central staging are configured. No sign-in API or provider adapter is included yet.
- **M1 — Public contract:** define OAuth 2.0 Authorization Code with PKCE, provider configuration for Supabase Auth and PKCE-capable OIDC issuers (including Spring Authorization Server), and result, cancellation, account, and error semantics. Never require a confidential client secret in the mobile API.
- **M2 — Platform adapters:** implement system-browser launch, app callback routing, verifier/session storage, and token exchange; document callback URLs, app lifecycle, permissions, and app-owned presentation boundaries. Keep provider-specific transport within the Weft artifact and use approved Ktor/Ktorfit dependencies rather than requiring the Supabase SDK in consuming apps.
- **M3 — Release candidate:** verify PKCE success, cancellation, callback mismatch, verifier loss, token expiry/refresh, provider errors, minimum OS support, redistribution terms, and a separate consumer for Supabase and Spring-compatible OIDC configurations.

## Rules

- Keep `Context`, `Activity`, `UIViewController`, provider SDK types, and platform URLs out of `commonMain`.
- Preserve Android behavior where useful, but expose platform capability differences instead of hiding them.
- Add dependencies only to the source set that needs them.
- Keep PKCE verifiers and user tokens in platform-protected storage; never log them or commit provider credentials.
- Do not release the empty M0 scaffold.

## Verification

- `./gradlew :weft-login:check`
- `./gradlew :weft-login:checkPomFileForKotlinMultiplatformPublication`
