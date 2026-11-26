package run.thespot.weft.login

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.AtomicFile
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CreateRestoreCredentialRequest
import androidx.credentials.CreateRestoreCredentialResponse
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetRestoreCredentialOption
import androidx.credentials.RestoreCredential
import androidx.credentials.exceptions.CreateCredentialProviderConfigurationException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.NoCredentialException
import androidx.credentials.exceptions.restorecredential.E2eeUnavailableException
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.intOrNull
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import org.json.JSONObject

/** Encrypted app-private storage backed by Android Keystore and excluded from Android backups. */
public class AndroidLoginStorage(
    context: Context,
    namespace: String = "default",
) : LoginStorage {
    private val appContext = context.applicationContext
    private val namespaceId = base64Url(sha256(namespace.encodeToByteArray()))
    private val file = File(appContext.noBackupFilesDir, "weft-login-$namespaceId.bin")
    private val atomicFile = AtomicFile(file)
    private val alias = "run.thespot.weft.login.$namespaceId"
    private val lock = Any()

    override public suspend fun get(key: String): String? = synchronized(lock) {
        readAll().optString(key).takeIf { it.isNotEmpty() }
    }

    override public suspend fun put(key: String, value: String) {
        synchronized(lock) {
            val values = readAll()
            values.put(key, value)
            writeAll(values)
        }
    }

    override public suspend fun remove(key: String) {
        synchronized(lock) {
            val values = readAll()
            values.remove(key)
            writeAll(values)
        }
    }

    private fun readAll(): JSONObject {
        return runCatching {
            val encoded = atomicFile.openRead().use { it.readBytes() }
            require(encoded.size > IV_LENGTH)
            val iv = encoded.copyOfRange(1, IV_LENGTH + 1)
            val ciphertext = encoded.copyOfRange(IV_LENGTH + 1, encoded.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_LENGTH_BITS, iv))
            JSONObject(cipher.doFinal(ciphertext).decodeToString())
        }.getOrElse {
            atomicFile.delete()
            JSONObject()
        }
    }

    private fun writeAll(values: JSONObject) {
        if (values.length() == 0) {
            atomicFile.delete()
            return
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(values.toString().encodeToByteArray())
        file.parentFile?.mkdirs()
        val output = atomicFile.startWrite()
        try {
            output.write(iv.size)
            output.write(iv)
            output.write(ciphertext)
            output.fd.sync()
            atomicFile.finishWrite(output)
        } catch (error: Throwable) {
            atomicFile.failWrite(output)
            throw error
        }
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER).run {
            init(
                KeyGenParameterSpec.Builder(
                    alias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build(),
            )
            generateKey()
        }
    }

    private companion object {
        const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_LENGTH_BITS = 128
        const val IV_LENGTH = 12
    }
}

/** Opens an OAuth request in the device's external browser. */
public fun LoginAuthorizationRequest.openInSystemBrowser(context: Context) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(authorizationUrl))
        .addCategory(Intent.CATEGORY_BROWSABLE)
    if (context !is android.app.Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}

/** Initializes the login plugin with Android Keystore-backed storage. */
public suspend fun LoginPlugin.initialize(context: Context): LoginSession? {
    val client = requireSupabaseLoginClient()
    return initialize(AndroidLoginStorage(context, client.config.storageNamespace))
}

/** True when Android is at least API 28; a compatible Credential Manager provider is also required. */
public fun LoginPlugin.isRestoreCredentialSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

/**
 * Creates a Supabase-backed Android restore key after a user has signed in.
 *
 * The key is backed up to the cloud when possible. Android's documented local-only fallback is
 * used when cloud end-to-end encryption is unavailable.
 */
public suspend fun LoginPlugin.createRestoreCredential(
    context: Context,
): String {
    if (!isRestoreCredentialSupported()) {
        throw LoginException("restore_credential_unsupported", "Restore Credentials require Android 9 or newer.")
    }
    val client = requireSupabaseLoginClient()
    val currentUserId = client.session.value?.user?.id
        ?: throw LoginException("session_missing", "Sign in before creating an Android restore credential.")
    val existingPasskeyId = client.storedRestorePasskeyId()
    if (existingPasskeyId != null && client.storedRestorePasskeyOwnerId() == currentUserId) return existingPasskeyId
    if (existingPasskeyId != null) {
        val credentialManager = CredentialManager.create(context)
        runCatching {
            credentialManager.clearCredentialState(
                ClearCredentialStateRequest(ClearCredentialStateRequest.TYPE_CLEAR_RESTORE_CREDENTIAL),
            )
        }
        client.clearRestoreCredentialRecord()
    }

    val options = client.createRestoreRegistrationOptions(DEFAULT_RESTORE_KEY_NAME)
    val requestJson = normalizeRegistrationOptions(options.optionsJson, DEFAULT_RESTORE_KEY_NAME)
    val credentialManager = CredentialManager.create(context)
    val response = try {
        credentialManager.createRestoreResponse(context, requestJson, cloudBackupEnabled = true)
    } catch (error: E2eeUnavailableException) {
        credentialManager.createRestoreResponse(context, requestJson, cloudBackupEnabled = false)
    }

    return try {
        client.verifyRestoreRegistration(options, response.responseJson, DEFAULT_RESTORE_KEY_NAME)
    } catch (error: Throwable) {
        runCatching { clearRestoreCredential(context) }
        throw error
    }
}

/** Restores a Supabase session silently, returning `null` when this device has no restore key. */
public suspend fun LoginPlugin.restoreWithCredential(context: Context): LoginSession? {
    if (!isRestoreCredentialSupported()) return null
    val client = requireSupabaseLoginClient()
    val options = client.createRestoreAuthenticationOptions()
    val credentialManager = CredentialManager.create(context)
    val response = try {
        credentialManager.getCredential(
            context,
            GetCredentialRequest(listOf(GetRestoreCredentialOption(normalizeAuthenticationOptions(options.optionsJson)))),
        )
    } catch (_: NoCredentialException) {
        return null
    } catch (_: GetCredentialProviderConfigurationException) {
        return null
    }
    val restoreCredential = response.credential as? RestoreCredential ?: return null
    val session = client.verifyRestoreAuthentication(options, restoreCredential.authenticationResponseJson)
    try {
        client.findRestorePasskeyId(
            friendlyName = DEFAULT_RESTORE_KEY_NAME,
            credentialId = parseJsonObject(restoreCredential.authenticationResponseJson).stringOrNull("id"),
        )
    } catch (error: CancellationException) {
        throw error
    } catch (_: Throwable) {
        // Session restoration succeeded. This lookup is only used for server-side cleanup on sign-out.
    }
    return session
}

/** Clears the OS restore key and signs out from Supabase. */
public suspend fun LoginPlugin.signOut(context: Context) {
    try {
        clearRestoreCredential(context)
    } finally {
        signOut()
    }
}

/** Explicitly removes both Credential Manager session state and the app's restore key. */
public suspend fun LoginPlugin.clearRestoreCredential(context: Context) {
    val credentialManager = CredentialManager.create(context)
    runCatching { credentialManager.clearCredentialState(ClearCredentialStateRequest()) }
    runCatching {
        credentialManager.clearCredentialState(
            ClearCredentialStateRequest(ClearCredentialStateRequest.TYPE_CLEAR_RESTORE_CREDENTIAL),
        )
    }
    val client = requireSupabaseLoginClient()
    client.deleteRestorePasskeyRecord()
    client.clearRestoreCredentialRecord()
}

private fun normalizeRegistrationOptions(raw: String, friendlyName: String): String {
    val root = parseJsonObject(raw).toMutableMap()
    root["challenge"]?.jsonPrimitive?.contentOrNull
        ?.let(::normalizeBase64Url)?.let { root["challenge"] = JsonPrimitive(it) }
    val user = (root["user"] as? JsonObject)?.toMutableMap() ?: mutableMapOf()
    if (user["name"]?.jsonPrimitive?.contentOrNull.isNullOrBlank()) user["name"] = JsonPrimitive(friendlyName)
    if (user["displayName"]?.jsonPrimitive?.contentOrNull.isNullOrBlank()) {
        user["displayName"] = user["name"] ?: JsonPrimitive(friendlyName)
    }
    user["id"]?.jsonPrimitive?.contentOrNull
        ?.let(::normalizeBase64Url)?.let { user["id"] = JsonPrimitive(it) }
    root["user"] = JsonObject(user)
    val authenticatorSelection = (root["authenticatorSelection"] as? JsonObject)?.toMutableMap() ?: mutableMapOf()
    authenticatorSelection["requireResidentKey"] = JsonPrimitive(true)
    authenticatorSelection["residentKey"] = JsonPrimitive("required")
    authenticatorSelection["userVerification"] = JsonPrimitive("required")
    root["authenticatorSelection"] = JsonObject(authenticatorSelection)
    if (root["attestation"] == null) root["attestation"] = JsonPrimitive("none")
    val algorithms = (root["pubKeyCredParams"] as? JsonArray)?.toMutableList() ?: mutableListOf()
    listOf(-7, -257).forEach { algorithm ->
        if (algorithms.none { parameter ->
                (parameter as? JsonObject)?.get("alg")?.jsonPrimitive?.intOrNull == algorithm
            }
        ) {
            algorithms += kotlinx.serialization.json.buildJsonObject {
                put("type", JsonPrimitive("public-key"))
                put("alg", JsonPrimitive(algorithm))
            }
        }
    }
    root["pubKeyCredParams"] = JsonArray(algorithms)
    val excluded = root["excludeCredentials"] as? JsonArray
    if (excluded != null) {
        root["excludeCredentials"] = JsonArray(excluded.map { item -> normalizeCredentialDescriptor(item) })
    }
    return JsonObject(root).toString()
}

private fun normalizeAuthenticationOptions(raw: String): String {
    val root = parseJsonObject(raw).toMutableMap()
    root["challenge"]?.jsonPrimitive?.contentOrNull
        ?.let(::normalizeBase64Url)?.let { root["challenge"] = JsonPrimitive(it) }
    val allowed = root["allowCredentials"] as? JsonArray
    if (allowed != null) {
        root["allowCredentials"] = JsonArray(allowed.map { item -> normalizeCredentialDescriptor(item) })
    }
    return JsonObject(root).toString()
}

private fun normalizeCredentialDescriptor(item: kotlinx.serialization.json.JsonElement): kotlinx.serialization.json.JsonElement {
    val descriptor = item as? JsonObject ?: return item
    val values = descriptor.toMutableMap()
    descriptor["id"]?.jsonPrimitive?.contentOrNull
        ?.let(::normalizeBase64Url)?.let { values["id"] = JsonPrimitive(it) }
    if (values["transports"] !is JsonArray) values["transports"] = JsonArray(emptyList())
    return JsonObject(values)
}

private fun normalizeBase64Url(value: String): String = value
    .replace('+', '-')
    .replace('/', '_')
    .trimEnd('=')

private suspend fun CredentialManager.createRestoreResponse(
    context: Context,
    requestJson: String,
    cloudBackupEnabled: Boolean,
): CreateRestoreCredentialResponse = try {
    createCredential(
        context,
        CreateRestoreCredentialRequest(requestJson, isCloudBackupEnabled = cloudBackupEnabled),
    ) as? CreateRestoreCredentialResponse
        ?: throw LoginException("restore_credential_invalid", "Credential Manager returned an unexpected credential type.")
} catch (error: CreateCredentialProviderConfigurationException) {
    throw LoginException(
        "restore_credential_unsupported",
        "This device does not have a compatible restore credential provider. Use standard sign-in.",
        cause = error,
    )
}

private fun LoginPlugin.requireSupabaseLoginClient(): SupabaseLoginClient = this as? SupabaseLoginClient
    ?: throw IllegalArgumentException("Android Supabase login extensions require the WeftLogin implementation.")

private const val DEFAULT_RESTORE_KEY_NAME = "Android restore key"

internal actual fun createLoginHttpClient(): HttpClient = HttpClient(OkHttp)

internal actual fun secureRandomBytes(size: Int): ByteArray = ByteArray(size).also { SecureRandom().nextBytes(it) }

internal actual fun sha256(input: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(input)

internal actual fun platformEpochSeconds(): Long = System.currentTimeMillis() / 1_000L
