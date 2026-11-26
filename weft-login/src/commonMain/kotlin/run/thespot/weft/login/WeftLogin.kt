package run.thespot.weft.login

import io.ktor.http.Url
import io.ktor.client.request.header
import io.ktor.client.request.get
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import run.thespot.weft.core.Weft
import run.thespot.weft.core.WeftHealthCheck
import run.thespot.weft.core.WeftPlugin
import kotlin.reflect.KClass

/** Configuration for the Supabase Auth implementation of [LoginPlugin]. */
public class WeftLoginConfig {
    /** Supabase project URL, for example `https://example.supabase.co`. */
    public var supabaseUrl: String = ""

    /** Supabase publishable key (or a legacy anon key). Never use a secret/service-role key. */
    public var publishableKey: String = ""

    /** App callback URL registered in Supabase Auth, for example `myapp://auth/callback`. */
    public var redirectUri: String = ""
}

/** Installs the Supabase-backed login capability into a [Weft] runtime. */
public object WeftLogin : WeftPlugin<WeftLoginConfig, LoginPlugin> {
    override val type: KClass<LoginPlugin> = LoginPlugin::class

    override val pluginName: String = "WeftLogin"

    override fun prepare(block: WeftLoginConfig.() -> Unit): LoginPlugin {
        val config = WeftLoginConfig().apply(block)
        val projectUrl = config.supabaseUrl.trimEnd('/')
        require(projectUrl.isNotBlank()) { "WeftLogin.supabaseUrl must not be blank." }
        val parsedUrl = Url(projectUrl)
        val isLoopback = parsedUrl.host == "localhost" || parsedUrl.host == "127.0.0.1" || parsedUrl.host == "10.0.2.2"
        require(parsedUrl.protocol.name == "https" || (parsedUrl.protocol.name == "http" && isLoopback)) {
            "WeftLogin.supabaseUrl must use HTTPS, except for a local development endpoint."
        }
        require(config.publishableKey.isNotBlank()) { "WeftLogin.publishableKey must not be blank." }
        require(!config.publishableKey.startsWith("sb_secret_", ignoreCase = true)) {
            "WeftLogin only accepts a Supabase publishable key or legacy anon key; secret keys must never ship in an app."
        }
        require(!config.publishableKey.contains("service_role", ignoreCase = true)) {
            "WeftLogin cannot use a Supabase service-role key in client code."
        }
        require(config.redirectUri.isNotBlank()) { "WeftLogin.redirectUri must not be blank." }
        val redirect = Url(config.redirectUri)
        require(redirect.protocol.name.isNotBlank() && redirect.host.isNotBlank()) {
            "WeftLogin.redirectUri must be an absolute app callback URL."
        }

        return SupabaseLoginClient(
            config = LoginConfig(
                supabaseUrl = projectUrl,
                publishableKey = config.publishableKey,
                redirectUri = config.redirectUri,
            ),
        )
    }

    override fun install(plugin: LoginPlugin, scope: Weft): Unit = Unit

    override fun healthCheck(plugin: LoginPlugin): List<WeftHealthCheck> {
        val client = plugin as SupabaseLoginClient
        return listOf(
            WeftHealthCheck(
                name = "supabase-auth",
                url = "${client.config.supabaseUrl}/auth/v1/health",
                headers = mapOf("apikey" to client.config.publishableKey),
            ),
        )
    }
}

internal data class LoginConfig(
    val supabaseUrl: String,
    val publishableKey: String,
    val redirectUri: String,
) {
    val authUrl: String = "$supabaseUrl/auth/v1"
    val storageNamespace: String = "weft-login-${base64Url(sha256(supabaseUrl.encodeToByteArray()))}"
}

internal class SupabaseLoginClient(
    val config: LoginConfig,
) : LoginPlugin {
    private val http = createLoginHttpClient()
    private val mutableSession = MutableStateFlow<LoginSession?>(null)
    private var storage: LoginStorage? = null

    override val session: StateFlow<LoginSession?> = mutableSession.asStateFlow()

    override suspend fun initialize(storage: LoginStorage): LoginSession? {
        check(this.storage == null || this.storage === storage) {
            "WeftLogin is already initialized with another LoginStorage instance."
        }
        this.storage = storage
        val savedSession = storage.get(SESSION_KEY)?.let { decodeSession(parseJsonObject(it)) }
        mutableSession.value = savedSession
        return savedSession
    }

    override suspend fun beginOAuthSignIn(
        provider: String,
        scopes: List<String>,
    ): LoginAuthorizationRequest {
        requireInitialized()
        require(provider.isNotBlank() && provider.all { it.isLetterOrDigit() || it == '_' || it == '-' }) {
            "OAuth provider must be a Supabase provider slug containing letters, digits, '_' or '-'."
        }
        val verifier = secureRandomBase64Url(32)
        val challenge = base64Url(sha256(verifier.encodeToByteArray()))
        storage().put(PKCE_VERIFIER_KEY, verifier)
        val url = io.ktor.http.URLBuilder("${config.authUrl}/authorize").apply {
            parameters.append("provider", provider)
            parameters.append("redirect_to", config.redirectUri)
            parameters.append("code_challenge", challenge)
            parameters.append("code_challenge_method", "s256")
            if (scopes.isNotEmpty()) parameters.append("scopes", scopes.joinToString(" "))
        }.buildString()
        return LoginAuthorizationRequest(url, config.redirectUri)
    }

    override suspend fun completeOAuthSignIn(callbackUrl: String): LoginSession {
        requireInitialized()
        val callback = try {
            Url(callbackUrl)
        } catch (error: Throwable) {
            throw LoginException("invalid_callback", "The OAuth callback URL is invalid.", cause = error)
        }
        val expected = Url(config.redirectUri)
        if (
            callback.protocol.name != expected.protocol.name ||
            callback.host != expected.host ||
            callback.port != expected.port ||
            callback.encodedPath != expected.encodedPath
        ) {
            throw LoginException("callback_mismatch", "The OAuth callback does not match WeftLogin.redirectUri.")
        }
        callback.parameters["error"]?.let { code ->
            val message = callback.parameters["error_description"] ?: "The identity provider rejected sign-in."
            throw LoginException(code, message)
        }
        val authorizationCode = callback.parameters["code"]
            ?: throw LoginException("missing_code", "The OAuth callback did not contain an authorization code.")
        val verifier = storage().get(PKCE_VERIFIER_KEY)
            ?: throw LoginException("verifier_missing", "The PKCE verifier is unavailable. Start sign-in again.")

        val response = requestJson(
            method = "POST",
            path = "/token?grant_type=pkce",
            body = jsonObject(
                "auth_code" to authorizationCode,
                "code_verifier" to verifier,
            ),
        )
        val loginSession = decodeSession(response)
        persistSession(loginSession)
        storage().remove(PKCE_VERIFIER_KEY)
        return loginSession
    }

    override suspend fun refreshSession(): LoginSession? {
        requireInitialized()
        val existing = mutableSession.value
            ?: storage().get(SESSION_KEY)?.let { decodeSession(parseJsonObject(it)) }
            ?: return null
        val response = requestJson(
            method = "POST",
            path = "/token?grant_type=refresh_token",
            body = jsonObject("refresh_token" to existing.refreshToken),
        )
        val renewed = decodeSession(response, fallbackUser = existing.user)
        persistSession(renewed)
        return renewed
    }

    override suspend fun signOut() {
        requireInitialized()
        val current = mutableSession.value ?: storage().get(SESSION_KEY)?.let { decodeSession(parseJsonObject(it)) }
        var failure: Throwable? = null
        try {
            deleteRestorePasskeyRecord()
        } catch (error: Throwable) {
            failure = error
        }
        try {
            if (current != null) {
                requestJson(
                    method = "POST",
                    path = "/logout?scope=global",
                    accessToken = current.accessToken,
                )
            }
        } catch (error: Throwable) {
            if (failure == null) failure = error
        } finally {
            storage().remove(SESSION_KEY)
            storage().remove(PKCE_VERIFIER_KEY)
            storage().remove(RESTORE_PASSKEY_ID_KEY)
            storage().remove(RESTORE_PASSKEY_OWNER_KEY)
            mutableSession.value = null
        }
        failure?.let { throw it }
    }

    internal suspend fun createRestoreRegistrationOptions(friendlyName: String): PasskeyOptions {
        val current = mutableSession.value ?: throw LoginException(
            "session_missing",
            "Sign in before creating an Android restore credential.",
        )
        val response = requestJson(
            method = "POST",
            path = "/passkeys/registration/options",
            body = jsonObject(),
            accessToken = current.accessToken,
        )
        val rawOptions = response.requiredObject("options")
        val user = (rawOptions["user"] as? JsonObject)?.toMutableMap() ?: mutableMapOf()
        if (user["name"]?.toString().let { it == null || it == "\"\"" }) {
            user["name"] = kotlinx.serialization.json.JsonPrimitive(friendlyName)
        }
        if (user["displayName"]?.toString().let { it == null || it == "\"\"" }) {
            user["displayName"] = user["name"] ?: kotlinx.serialization.json.JsonPrimitive(friendlyName)
        }
        return PasskeyOptions(
            challengeId = response.requiredString("challenge_id"),
            optionsJson = JsonObject(rawOptions.toMutableMap().apply { put("user", JsonObject(user)) }).toString(),
        )
    }

    internal suspend fun verifyRestoreRegistration(
        options: PasskeyOptions,
        responseJson: String,
        friendlyName: String,
    ): String {
        val current = mutableSession.value ?: throw LoginException("session_missing", "The Supabase session expired.")
        val credential = parseJsonObject(responseJson)
        val registered = requestJson(
            method = "POST",
            path = "/passkeys/registration/verify",
            body = jsonObject(
                "challenge_id" to options.challengeId,
                "credential" to credential,
            ),
            accessToken = current.accessToken,
        )
        val passkeyId = registered.requiredString("id")
        try {
            requestJson(
                method = "PATCH",
                path = "/passkeys/${passkeyId.encodeURLPathPart()}",
                body = jsonObject("friendly_name" to friendlyName),
                accessToken = current.accessToken,
            )
        } catch (_: Throwable) {
            // Registration is already valid. A label update is only cosmetic.
        }
        recordRestorePasskey(passkeyId, current.user.id)
        return passkeyId
    }

    internal suspend fun createRestoreAuthenticationOptions(): PasskeyOptions {
        requireInitialized()
        val response = requestJson(
            method = "POST",
            path = "/passkeys/authentication/options",
            body = jsonObject("gotrue_meta_security" to jsonObject()),
        )
        return PasskeyOptions(
            challengeId = response.requiredString("challenge_id"),
            optionsJson = response.requiredObject("options").toString(),
        )
    }

    internal suspend fun verifyRestoreAuthentication(
        options: PasskeyOptions,
        responseJson: String,
    ): LoginSession {
        val response = requestJson(
            method = "POST",
            path = "/passkeys/authentication/verify",
            body = jsonObject(
                "challenge_id" to options.challengeId,
                "credential" to parseJsonObject(responseJson),
            ),
        )
        val restored = decodeSession(response)
        persistSession(restored)
        return restored
    }

    internal suspend fun findRestorePasskeyId(friendlyName: String, credentialId: String? = null): String? {
        val current = mutableSession.value ?: return null
        val response = http.get("${config.authUrl}/passkeys") {
            header("apikey", config.publishableKey)
            header("Accept", "application/json")
            header("Authorization", "Bearer ${current.accessToken}")
        }
        val responseText = response.bodyAsText()
        if (response.status.value !in 200..299) {
            throw LoginException("passkey_list_failed", "Could not list Supabase passkeys.", response.status.value)
        }
        val responseJson = runCatching { Json.parseToJsonElement(responseText) }.getOrNull() ?: return null
        val passkeys = when (responseJson) {
            is JsonArray -> responseJson
            is JsonObject -> responseJson["passkeys"] as? JsonArray ?: responseJson["data"] as? JsonArray
            else -> null
        } ?: return null
        val matches = passkeys.mapNotNull { entry ->
            val passkey = entry as? JsonObject ?: return@mapNotNull null
            if (passkey.stringOrNull("friendly_name") != friendlyName) return@mapNotNull null
            val id = passkey.stringOrNull("id") ?: return@mapNotNull null
            passkey to id
        }
        val id = if (credentialId != null) {
            val normalizedCredentialId = credentialId.trimEnd('=')
            matches.firstOrNull { (passkey, _) ->
                passkey.stringOrNull("credential_id")?.trimEnd('=') == normalizedCredentialId
            }?.second ?: matches.singleOrNull()?.second
        } else {
            matches.singleOrNull()?.second
        }
        if (id != null) recordRestorePasskey(id, current.user.id)
        return id
    }

    internal suspend fun clearRestoreCredentialRecord() {
        storage().remove(RESTORE_PASSKEY_ID_KEY)
        storage().remove(RESTORE_PASSKEY_OWNER_KEY)
    }

    internal suspend fun storedRestorePasskeyId(): String? = storage().get(RESTORE_PASSKEY_ID_KEY)

    internal suspend fun storedRestorePasskeyOwnerId(): String? = storage().get(RESTORE_PASSKEY_OWNER_KEY)

    internal suspend fun recordRestorePasskey(passkeyId: String, ownerId: String) {
        storage().put(RESTORE_PASSKEY_ID_KEY, passkeyId)
        storage().put(RESTORE_PASSKEY_OWNER_KEY, ownerId)
    }

    internal suspend fun deleteRestorePasskeyRecord() {
        val restorePasskeyId = storage().get(RESTORE_PASSKEY_ID_KEY) ?: return
        val ownerId = storage().get(RESTORE_PASSKEY_OWNER_KEY)
        val current = mutableSession.value ?: storage().get(SESSION_KEY)?.let { decodeSession(parseJsonObject(it)) }
        if (current != null && (ownerId == null || ownerId == current.user.id)) {
            requestJson(
                method = "DELETE",
                path = "/passkeys/${restorePasskeyId.encodeURLPathPart()}",
                accessToken = current.accessToken,
            )
        }
        storage().remove(RESTORE_PASSKEY_ID_KEY)
        storage().remove(RESTORE_PASSKEY_OWNER_KEY)
    }

    private suspend fun persistSession(session: LoginSession) {
        storage().put(SESSION_KEY, encodeSession(session))
        mutableSession.value = session
    }

    private suspend fun requestJson(
        method: String,
        path: String,
        body: kotlinx.serialization.json.JsonObject? = null,
        accessToken: String? = null,
    ): kotlinx.serialization.json.JsonObject {
        val response = http.request("${config.authUrl}$path") {
            this.method = io.ktor.http.HttpMethod.parse(method)
            header("apikey", config.publishableKey)
            header("Accept", "application/json")
            if (accessToken != null) header("Authorization", "Bearer $accessToken")
            if (body != null) {
                header("Content-Type", "application/json")
                setBody(body.toString())
            }
        }
        val responseText = response.bodyAsText()
        if (response.status.value !in 200..299) {
            val errorBody = responseText.takeIf(String::isNotBlank)?.let(::parseJsonObjectOrNull)
            val code = errorBody?.stringOrNull("error_code") ?: errorBody?.stringOrNull("error") ?: "auth_error"
            val message = errorBody?.stringOrNull("msg")
                ?: errorBody?.stringOrNull("message")
                ?: "Supabase Auth request failed with HTTP ${response.status.value}."
            throw LoginException(code, message, response.status.value)
        }
        if (responseText.isBlank()) return jsonObject()
        return parseJsonObject(responseText)
    }

    private fun decodeSession(
        json: kotlinx.serialization.json.JsonObject,
        fallbackUser: LoginUser? = null,
    ): LoginSession {
        val accessToken = json.stringOrNull("access_token")
            ?: throw LoginException("session_invalid", "Supabase Auth returned no access token.")
        val refreshToken = json.stringOrNull("refresh_token")
            ?: throw LoginException("session_invalid", "Supabase Auth returned no refresh token.")
        val expiresAt = json.longOrNull("expires_at")
            ?: (platformEpochSeconds() + (json.longOrNull("expires_in") ?: 0L))
        val userObject = json["user"]?.let { it as? kotlinx.serialization.json.JsonObject }
        val user = userObject?.let(::decodeUser) ?: fallbackUser
            ?: throw LoginException("session_invalid", "Supabase Auth returned no user.")
        return LoginSession(accessToken, refreshToken, expiresAt, user)
    }

    private fun decodeUser(json: kotlinx.serialization.json.JsonObject): LoginUser {
        val id = json.stringOrNull("id")
            ?: throw LoginException("user_invalid", "Supabase Auth returned a user without an id.")
        return LoginUser(
            id = id,
            email = json.stringOrNull("email"),
            phone = json.stringOrNull("phone"),
            metadataJson = json["user_metadata"]?.toString() ?: "{}",
        )
    }

    private fun encodeSession(session: LoginSession): String = jsonObject(
        "access_token" to session.accessToken,
        "refresh_token" to session.refreshToken,
        "expires_at" to session.expiresAtEpochSeconds,
        "user" to jsonObject(
            "id" to session.user.id,
            "email" to session.user.email,
            "phone" to session.user.phone,
            "user_metadata" to parseJsonObjectOrNull(session.user.metadataJson),
        ),
    ).toString()

    private fun requireInitialized() {
        check(storage != null) { "Call LoginPlugin.initialize(...) before using WeftLogin." }
    }

    private fun storage(): LoginStorage = storage
        ?: throw IllegalStateException("Call LoginPlugin.initialize(...) before using WeftLogin.")

    private companion object {
        const val SESSION_KEY = "session"
        const val PKCE_VERIFIER_KEY = "oauth.pkce.verifier"
        const val RESTORE_PASSKEY_ID_KEY = "android.restore.passkey.id"
        const val RESTORE_PASSKEY_OWNER_KEY = "android.restore.passkey.owner"
    }
}

internal data class PasskeyOptions(
    val challengeId: String,
    val optionsJson: String,
)
