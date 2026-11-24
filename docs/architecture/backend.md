# Backend choices and PKCE

Every Weft app profile selects one backend path: Spring Boot or Supabase. The architecture skill asks whether the team will operate a separate server. Choose Spring Boot when the team will own and operate that server; choose Supabase when it will not.

This is a Weft product policy, not a PKCE protocol requirement. Native apps are public OAuth clients and cannot keep a client secret confidential. Authorization Code with PKCE lets the client prove possession of a per-login verifier when exchanging the authorization code. The backend choice determines who owns authentication services, application APIs, data, and operations.

## Shared native-app authentication rules

- Use OAuth 2.0 Authorization Code with PKCE for native sign-in.
- Open the authorization page in the system browser and return through a registered app callback.
- Create a fresh verifier for each sign-in attempt, keep it in platform-protected storage while the flow is pending, and send it only to the token endpoint when exchanging the matching authorization code.
- Treat the mobile app as a public client. Never embed an OAuth client secret, database password, Supabase secret key, or service-role key in Android or iOS binaries.
- Send access tokens to APIs as bearer tokens. APIs validate token signature or introspection result, issuer, audience, expiration, and required scopes/claims; never trust decoded-but-unverified JWT payloads.
- Keep callback handling, verifier storage, and token storage behind the `weft-login` API so feature code does not implement provider-specific auth flows.

## Spring Boot path

Choose a Spring profile when the team will operate a separate JVM service. Keep the Spring entry point, API, authorization integration, configuration, persistence, and deployment in non-KMP server modules. Do not apply the Spring Boot plugin and KMP plugin to the same Gradle project; keep Spring in its own JVM module.

For authentication, either operate Spring Authorization Server with a public native client that has client authentication set to `none` and requires proof keys, or configure Spring as the resource server for an existing PKCE-capable OIDC issuer. A Spring API that validates bearer tokens does not itself perform the app's authorization-code exchange. Record the issuer and which service owns login, consent, and token issuance.

The app contains a public client ID and issuer/endpoint URLs only. The server keeps confidential upstream OAuth credentials, signing material, database passwords, and third-party API secrets. Supply them at deployment through the runtime's secret manager, environment variables, or mounted configuration files; keep placeholders in tracked `application.yaml`. Spring Boot supports external configuration including environment variables and `configtree:` mounted secrets. Bind settings through typed configuration properties and fail startup when a required secret is missing.

```yaml
spring:
  config:
    import: optional:configtree:/run/secrets/
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: ${WEFT_AUTH_ISSUER_URI}
```

If operating Spring Authorization Server, persist signing keys and registered-client configuration outside source control. Public native clients do not receive a client secret. Use Spring Security Resource Server at protected APIs to validate issuer, audience, and authorization rules.

## Supabase path

Choose a Supabase profile when the team does not want to operate a separate application server. Supabase Auth handles the PKCE authorization-code exchange; the native client retains the verifier locally and exchanges the callback code with the same Supabase Auth project. The `supabase/` directory is the deployment boundary for CLI configuration, database migrations, row-level security policies, and Edge Functions.

The app may contain the Supabase project URL and a publishable key. Protect user data with RLS and pass the user's access token when calling protected resources. Supabase secret keys and legacy `service_role` keys bypass RLS and belong only in Edge Function or other trusted server environments. Provision Edge Function secrets with the Supabase CLI or dashboard; use Supabase Vault only when a database-side function, trigger, or job needs a secret. Keep local `.env` files ignored and out of Git.

The application allowlist remains closed: app modules must not add the Supabase Kotlin SDK as a one-off dependency. App code uses `weft-login` and approved Ktor/Ktorfit networking. Provider-specific implementation belongs inside a published Weft artifact and must follow its own library policy.

## Profile mapping

| Team choice | KMP core with Kotlin platform UI | Shared Compose UI |
| --- | --- | --- |
| No separately operated server | `kmp-core-supabase` | `cmp-supabase` |
| Operate a separate server | `kmp-core-spring-service` | `cmp-spring-service` |

Do not silently combine Spring and Supabase as two authoritative backends. A product that needs both must document why responsibilities cannot live in one selected path and define the authentication/token trust boundary explicitly.

## References

- [RFC 8252: OAuth 2.0 for Native Apps](https://www.rfc-editor.org/rfc/rfc8252.html)
- [Spring Authorization Server: PKCE for a public client](https://docs.spring.io/spring-authorization-server/reference/guides/how-to-pkce.html)
- [Spring Security: OAuth 2.0 Resource Server JWT](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html)
- [Spring Boot: Externalized Configuration](https://docs.spring.io/spring-boot/reference/features/external-config.html)
- [Supabase: PKCE flow](https://supabase.com/docs/guides/auth/sessions/pkce-flow)
- [Supabase: Edge Function secrets](https://supabase.com/docs/guides/functions/secrets)
- [Supabase: API keys](https://supabase.com/docs/guides/getting-started/api-keys)
- [Supabase: Vault](https://supabase.com/docs/guides/database/vault)

Reviewed 2026-11-25. Recheck provider behavior and deployment instructions before adopting a production configuration.
