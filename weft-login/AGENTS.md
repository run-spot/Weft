# `:weft-login` Library

## Role

`weft-login` publishes a Kotlin-first Supabase Auth capability at `run.thespot.weft:weft-login`, version-aligned by `:weft-bom`. It implements native OAuth Authorization Code with PKCE, protected session storage on Android and iOS, and Android Restore Credentials using Supabase's passkey endpoints.

## Milestones

- **M0 — Build and publication skeleton:** complete.
- **M1 — Public contract:** complete for the Supabase path. Defines user/session models, PKCE OAuth requests and callbacks, storage, refresh, sign-out, and typed Auth errors.
- **M2 — Platform adapters:** implementation present. Android uses Credential Manager and Android Keystore-backed encrypted storage; iOS uses the Keychain. Supabase Auth REST requests use Ktor inside this artifact.
- **M3 — Release candidate:** pending Supabase-project validation, device restore/sign-out checks, callback integration in separate Android/iOS consumers, and provider compatibility review.

## Rules

- Keep `Context`, `Activity`, `UIViewController`, provider SDK types, and platform URLs out of `commonMain`.
- Keep app integrations on the approved Ktor/Ktorfit allowlist. Do not require a Supabase SDK in consumer apps.
- Never require a confidential OAuth client secret in the mobile API. Supabase publishable keys are allowed; secret and `service_role` keys are not.
- Expose a Koin `WeftContribution` for `WeftLogin`; read only the Supabase URL, publishable key, and callback URI from named Koin properties.
- Keep PKCE verifiers and session tokens in platform-protected storage; Supabase Auth owns OAuth state, which the client must not override. Never log auth material or commit credentials.
- Android Restore Credentials require API 28 or newer and a compatible credential provider. Standard OAuth must remain available as the fallback.
- Create the Android restore credential after sign-in, attempt restore on first launch when no local session exists, and clear the OS credential on sign-out.
- Supabase Passkey Auth is experimental. Keep that maturity visible in user documentation and recheck its REST contract before a release.
- The generic PKCE OIDC adapter for Spring-backed profiles remains future work.
- Do not release the module until M3 and the root Gradle verification requirements are complete.

## Verification

- Compile common, Android, and iOS implementations with `./gradlew :weft-login:compileCommonMainKotlinMetadata :weft-login:compileAndroidMain :weft-login:compileKotlinIosArm64`.
- `./gradlew :weft-login:check`
- `./gradlew :weft-login:checkPomFileForKotlinMultiplatformPublication`

Do not publish artifacts without an explicit release request. See [`docs/architecture/login.md`](../docs/architecture/login.md) for provider configuration and consumer integration.
