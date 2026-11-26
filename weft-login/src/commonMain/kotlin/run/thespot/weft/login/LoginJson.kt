package run.thespot.weft.login

import io.ktor.http.encodeURLPathPart as ktorEncodeURLPathPart
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

private val loginJson = Json { ignoreUnknownKeys = true }

internal fun jsonObject(vararg values: Pair<String, Any?>): JsonObject = buildJsonObject {
    values.forEach { (key, value) ->
        when (value) {
            null -> put(key, JsonNull)
            is String -> put(key, JsonPrimitive(value))
            is Long -> put(key, JsonPrimitive(value))
            is Int -> put(key, JsonPrimitive(value))
            is Boolean -> put(key, JsonPrimitive(value))
            is JsonElement -> put(key, value)
            is List<*> -> put(key, JsonArray(value.map { item ->
                when (item) {
                    null -> JsonNull
                    is String -> JsonPrimitive(item)
                    is JsonElement -> item
                    else -> throw IllegalArgumentException("Unsupported JSON list value: ${item::class.simpleName}")
                }
            }))
            else -> throw IllegalArgumentException("Unsupported JSON value for '$key'.")
        }
    }
}

internal fun parseJsonObject(value: String): JsonObject = try {
    loginJson.parseToJsonElement(value).jsonObject
} catch (error: Throwable) {
    throw LoginException("invalid_response", "Supabase Auth returned invalid JSON.", cause = error)
}

internal fun parseJsonObjectOrNull(value: String): JsonObject? = runCatching { parseJsonObject(value) }.getOrNull()

internal fun JsonObject.requiredString(key: String): String = stringOrNull(key)
    ?: throw LoginException("invalid_response", "Supabase Auth response is missing '$key'.")

internal fun JsonObject.requiredObject(key: String): JsonObject = this[key] as? JsonObject
    ?: throw LoginException("invalid_response", "Supabase Auth response is missing '$key'.")

internal fun JsonObject.stringOrNull(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

internal fun JsonObject.longOrNull(key: String): Long? = this[key]?.jsonPrimitive?.longOrNull

internal expect fun createLoginHttpClient(): io.ktor.client.HttpClient

internal expect fun secureRandomBytes(size: Int): ByteArray

internal expect fun sha256(input: ByteArray): ByteArray

internal expect fun platformEpochSeconds(): Long

internal fun secureRandomBase64Url(byteCount: Int): String = base64Url(secureRandomBytes(byteCount))

internal fun base64Url(bytes: ByteArray): String {
    val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
    val result = StringBuilder((bytes.size * 4 + 2) / 3)
    var index = 0
    while (index < bytes.size) {
        val first = bytes[index].toInt() and 0xff
        val second = if (index + 1 < bytes.size) bytes[index + 1].toInt() and 0xff else 0
        val third = if (index + 2 < bytes.size) bytes[index + 2].toInt() and 0xff else 0
        result.append(alphabet[first ushr 2])
        result.append(alphabet[((first and 0x03) shl 4) or (second ushr 4)])
        if (index + 1 < bytes.size) result.append(alphabet[((second and 0x0f) shl 2) or (third ushr 6)])
        if (index + 2 < bytes.size) result.append(alphabet[third and 0x3f])
        index += 3
    }
    return result.toString()
}

internal fun String.encodeURLPathPart(): String = this.ktorEncodeURLPathPart()
