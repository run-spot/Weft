package run.thespot.weft.gradle.health

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** One HTTP request that proves a configured credential works. */
internal data class HealthCheckRequest(
    val name: String,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val method: String = "GET",
    val origin: String? = null,
)

/** Outcome of one probe, with the reason kept for the report. */
internal data class HealthCheckResult(
    val request: HealthCheckRequest,
    val success: Boolean,
    val status: Int? = null,
    val detail: String? = null,
)

/**
 * Validates and runs health checks with `HttpURLConnection`, so the plugin needs no HTTP client
 * dependency and runs offline builds unchanged (checks are only performed by `verifyWeftHealth`).
 */
internal class HealthCheckRunner(
    private val connectTimeoutMillis: Int = 5_000,
    private val readTimeoutMillis: Int = 15_000,
) {
    /**
     * Returns why [request] cannot be run, or `null` when it is runnable.
     *
     * A blank credential is a configuration error, not a network result: reporting it before any
     * request keeps the failure precise and avoids calling a remote endpoint with no key.
     */
    fun validate(request: HealthCheckRequest): String? {
        if (request.name.isBlank()) return "health check has no name"
        if (request.url.isBlank()) return "health check '${request.name}' has no URL"
        if (!request.url.startsWith("http://") && !request.url.startsWith("https://")) {
            return "health check '${request.name}' URL is not HTTP: ${request.url}"
        }
        request.headers.forEach { (name, value) ->
            if (name.isBlank()) return "health check '${request.name}' has a header with no name"
            if (value.isBlank()) {
                return "health check '${request.name}' sends header '$name' with an empty value; " +
                    "configure the credential before running health checks"
            }
        }
        return null
    }

    fun run(request: HealthCheckRequest): HealthCheckResult {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(request.url).openConnection() as HttpURLConnection).apply {
                requestMethod = request.method
                connectTimeout = connectTimeoutMillis
                readTimeout = readTimeoutMillis
                instanceFollowRedirects = true
                request.headers.forEach { (name, value) -> setRequestProperty(name, value) }
            }
            val status = connection.responseCode
            val body = readBody(connection, status)
            HealthCheckResult(
                request = request,
                success = status in 200..299,
                status = status,
                detail = when {
                    status in 200..299 -> null
                    status == 401 || status == 403 ->
                        "HTTP $status: the credential was rejected. $body"

                    else -> "HTTP $status. $body"
                },
            )
        } catch (error: IOException) {
            HealthCheckResult(
                request = request,
                success = false,
                detail = "could not reach ${request.url}: ${error.message ?: error::class.simpleName}",
            )
        } finally {
            connection?.disconnect()
        }
    }

    private fun readBody(connection: HttpURLConnection, status: Int): String {
        val stream = runCatching {
            if (status in 200..299) connection.inputStream else connection.errorStream
        }.getOrNull() ?: return ""
        return stream.bufferedReader().use { reader -> reader.readText() }
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(300)
    }
}
