package run.thespot.weft.login

import kotlinx.coroutines.flow.StateFlow

/** A signed-in Supabase user returned by Auth. */
public data class LoginUser(
    public val id: String,
    public val email: String?,
    public val phone: String?,
    /** JSON object containing Supabase user metadata, retained without lossy field mapping. */
    public val metadataJson: String,
)

/** Supabase access and refresh tokens with the account that owns them. */
public data class LoginSession(
    public val accessToken: String,
    public val refreshToken: String,
    public val expiresAtEpochSeconds: Long,
    public val user: LoginUser,
)

/** OAuth URL that the host app opens in the system browser. */
public data class LoginAuthorizationRequest(
    public val authorizationUrl: String,
    public val redirectUri: String,
)

/**
 * The active Weft login capability.
 *
 * Call [initialize] once with platform-protected storage before starting an OAuth flow. The
 * Android and iOS source sets provide convenience overloads backed by Android Keystore and
 * Keychain respectively.
 */
public interface LoginPlugin {
    /** Session state, updated after sign-in, restore, refresh, and sign-out. */
    public val session: StateFlow<LoginSession?>

    /** Loads the persisted session while retaining any pending PKCE verifier in platform storage. */
    public suspend fun initialize(storage: LoginStorage): LoginSession?

    /** Builds a Supabase OAuth Authorization Code with PKCE URL for [provider]. */
    public suspend fun beginOAuthSignIn(
        provider: String,
        scopes: List<String> = emptyList(),
    ): LoginAuthorizationRequest

    /** Exchanges the `code` in a registered app callback URL for a Supabase session. */
    public suspend fun completeOAuthSignIn(callbackUrl: String): LoginSession

    /** Refreshes the current session, or returns `null` when no session is stored. */
    public suspend fun refreshSession(): LoginSession?

    /**
     * Signs out the current Supabase session and removes any registered restore-key record.
     * On Android, call the platform `signOut(context)` overload to clear Credential Manager too.
     */
    public suspend fun signOut()
}

/** Platform-protected key-value storage used for tokens and pending PKCE verifiers. */
public interface LoginStorage {
    public suspend fun get(key: String): String?

    public suspend fun put(key: String, value: String)

    public suspend fun remove(key: String)
}

/** Failure returned by Supabase Auth or by the local OAuth callback validation. */
public class LoginException(
    public val errorCode: String,
    override val message: String,
    public val statusCode: Int? = null,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)
