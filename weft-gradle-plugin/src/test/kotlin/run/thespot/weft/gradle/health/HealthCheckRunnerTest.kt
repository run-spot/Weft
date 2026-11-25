package run.thespot.weft.gradle.health

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Health check behavior against a local HTTP server, so the tests need no network access.
 */
class HealthCheckRunnerTest {
    private lateinit var server: HttpServer
    private val requestCount = AtomicInteger()

    private val runner = HealthCheckRunner(connectTimeoutMillis = 2_000, readTimeoutMillis = 2_000)

    @BeforeTest
    fun startServer() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/health") { exchange ->
            requestCount.incrementAndGet()
            val apiKey = exchange.requestHeaders.getFirst("apikey")
            val response = if (apiKey == "valid-key") 200 to """{"ok":true}""" else 401 to """{"error":"invalid api key"}"""
            exchange.sendResponseHeaders(response.first, response.second.length.toLong())
            exchange.responseBody.use { it.write(response.second.toByteArray()) }
        }
        server.createContext("/echo") { exchange ->
            requestCount.incrementAndGet()
            val apiKey = exchange.requestHeaders.getFirst("apikey").orEmpty()
            val response = """{"error":"invalid api key: $apiKey"}"""
            exchange.sendResponseHeaders(401, response.length.toLong())
            exchange.responseBody.use { it.write(response.toByteArray()) }
        }
        server.createContext("/forbidden") { exchange ->
            requestCount.incrementAndGet()
            exchange.sendResponseHeaders(403, -1)
            exchange.close()
        }
        server.start()
    }

    @AfterTest
    fun stopServer() {
        server.stop(0)
    }

    private fun url(path: String): String = "http://127.0.0.1:${server.address.port}$path"

    @Test
    fun acceptsAValidCredential() {
        val result = runner.run(
            HealthCheckRequest(
                name = "supabase-auth",
                url = url("/health"),
                headers = mapOf("apikey" to "valid-key"),
            ),
        )

        assertTrue(result.success, result.detail)
        assertEquals(200, result.status)
        assertEquals(1, requestCount.get())
    }

    @Test
    fun rejectsARejectedCredential() {
        val result = runner.run(
            HealthCheckRequest(
                name = "supabase-auth",
                url = url("/health"),
                headers = mapOf("apikey" to "revoked-key"),
            ),
        )

        assertFalse(result.success)
        assertEquals(401, result.status)
        assertTrue(result.detail.orEmpty().contains("credential was rejected"), result.detail)
        assertTrue(result.detail.orEmpty().contains("invalid api key"), result.detail)
    }

    @Test
    fun aRejectionBodyCannotLeakTheCredential() {
        val result = runner.run(
            HealthCheckRequest(
                name = "supabase-auth",
                url = url("/echo"),
                headers = mapOf("apikey" to "secret-service-key-value"),
            ),
        )

        assertFalse(result.success)
        val detail = result.detail.orEmpty()
        assertTrue(detail.contains("credential was rejected"), detail)
        assertFalse(detail.contains("secret-service-key-value"), detail)
        assertTrue(detail.contains("<redacted>"), detail)
    }

    @Test
    fun rejectsForbidden() {
        val result = runner.run(HealthCheckRequest(name = "kakao-local-api", url = url("/forbidden")))

        assertFalse(result.success)
        assertEquals(403, result.status)
        assertTrue(result.detail.orEmpty().contains("credential was rejected"), result.detail)
    }

    @Test
    fun rejectsUnreachableEndpoint() {
        val result = runner.run(HealthCheckRequest(name = "offline", url = "http://127.0.0.1:1/health"))

        assertFalse(result.success)
        assertTrue(result.detail.orEmpty().contains("could not reach"), result.detail)
    }

    @Test
    fun blankHeaderValueFailsWithoutSendingARequest() {
        val problem = runner.validate(
            HealthCheckRequest(
                name = "supabase-auth",
                url = url("/health"),
                headers = mapOf("apikey" to ""),
            ),
        )

        assertTrue(problem.orEmpty().contains("empty value"), problem.orEmpty())
        assertEquals(0, requestCount.get())
    }

    @Test
    fun blankUrlFailsValidation() {
        val problem = runner.validate(HealthCheckRequest(name = "broken", url = " "))

        assertTrue(problem.orEmpty().contains("has no URL"), problem.orEmpty())
    }

    @Test
    fun nonHttpUrlFailsValidation() {
        val problem = runner.validate(HealthCheckRequest(name = "broken", url = "file:///tmp/health"))

        assertTrue(problem.orEmpty().contains("not HTTP"), problem.orEmpty())
    }

    @Test
    fun validRequestHasNoValidationProblem() {
        assertNull(runner.validate(HealthCheckRequest(name = "ok", url = url("/health"))))
    }
}
