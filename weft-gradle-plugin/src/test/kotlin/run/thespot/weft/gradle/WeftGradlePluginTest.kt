package run.thespot.weft.gradle

import com.sun.net.httpserver.HttpServer
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import java.io.File
import java.net.InetSocketAddress
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Wires the plugin into a real Gradle project model and runs both tasks, including the aggregated
 * health check against a local server.
 */
class WeftGradlePluginTest {
    private lateinit var projectDir: File
    private lateinit var server: HttpServer
    private val requestCount = AtomicInteger()
    private var healthStatus = 200

    @BeforeTest
    fun setUp() {
        projectDir = Files.createTempDirectory("weft-verify").toFile()
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            requestCount.incrementAndGet()
            exchange.sendResponseHeaders(healthStatus, -1)
            exchange.close()
        }
        server.start()
    }

    @AfterTest
    fun tearDown() {
        server.stop(0)
        projectDir.deleteRecursively()
    }

    private val serverUrl: String
        get() = "http://127.0.0.1:${server.address.port}"

    private fun writeSource(relativePath: String, text: String) {
        File(projectDir, relativePath).apply {
            parentFile.mkdirs()
            writeText(text.trimIndent())
        }
    }

    private fun projectWithPlugin(): Project {
        val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
        project.plugins.apply("base")
        project.plugins.apply(WeftGradlePlugin::class.java)
        return project
    }

    private fun Project.pluginsVerification() = tasks.getByName("verifyWeftPlugins") as WeftPluginsVerifyTask

    private fun Project.healthVerification() = tasks.getByName("verifyWeftHealth") as WeftHealthCheckTask

    @Test
    fun registersBothTasksAndAddsOfflineCheckToCheck() {
        val project = projectWithPlugin()

        assertNotNull(project.tasks.findByName("verifyWeftPlugins"))
        assertNotNull(project.tasks.findByName("verifyWeftHealth"))
        val checkDependsOn = project.tasks.getByName("check").dependsOn.map { dependency ->
            if (dependency is org.gradle.api.tasks.TaskProvider<*>) dependency.name else dependency.toString()
        }
        assertTrue(checkDependsOn.contains("verifyWeftPlugins"), checkDependsOn.toString())
    }

    @Test
    fun offlineCheckFailsWhenCapabilityIsNeverInstalled() {
        writeSource(
            "src/commonMain/kotlin/Map.kt",
            """
            fun WeftMap(weft: Weft) = weft.get<MapPlugin>()
            """,
        )
        val task = projectWithPlugin().pluginsVerification()

        val error = assertFailsWith<GradleException> { task.verify() }
        val message = error.message.orEmpty()
        assertTrue(message.contains("MapPlugin"), message)
        assertTrue(message.contains("src/commonMain/kotlin/Map.kt:1"), message)
    }

    @Test
    fun offlineCheckPassesWithInstallAndLookup() {
        writeSource(
            "src/commonMain/kotlin/Map.kt",
            """
            object NaverMap : WeftPlugin<MapConfig, MapPlugin> {
                override val type: KClass<MapPlugin> = MapPlugin::class
            }

            val weft = Weft {
                install(NaverMap) {
                    applicationKey = "naver-app-key"
                }
            }

            fun WeftMap() = LocalWeft.current.get<MapPlugin>()
            """,
        )
        projectWithPlugin().pluginsVerification().verify()
    }

    @Test
    fun offlineCheckFailsOnEmptyCredential() {
        writeSource(
            "src/commonMain/kotlin/Map.kt",
            """
            val weft = Weft {
                install(NaverMap) {
                    applicationKey = ""
                }
            }
            """,
        )
        val error = assertFailsWith<GradleException> {
            projectWithPlugin().pluginsVerification().verify()
        }
        assertTrue(error.message.orEmpty().contains("applicationKey"), error.message.orEmpty())
    }

    @Test
    fun healthCheckRunsTheProbeDerivedFromAnInstallBlock() {
        healthStatus = 200
        writeSource(
            "src/commonMain/kotlin/Supabase.kt",
            """
            val weft = Weft {
                install(SupabaseClient) {
                    url = "$serverUrl"
                    anonKey = "valid-anon-key"
                }
            }
            """,
        )
        projectWithPlugin().healthVerification().verifyHealth()

        assertTrue(requestCount.get() >= 1, "expected the derived Supabase probe to be called")
    }

    @Test
    fun healthCheckFailsWhenTheEndpointRejectsTheCredential() {
        healthStatus = 401
        writeSource(
            "src/commonMain/kotlin/Supabase.kt",
            """
            val weft = Weft {
                install(SupabaseClient) {
                    url = "$serverUrl"
                    anonKey = "revoked-anon-key"
                }
            }
            """,
        )

        val error = assertFailsWith<GradleException> {
            projectWithPlugin().healthVerification().verifyHealth()
        }
        val message = error.message.orEmpty()
        assertTrue(message.contains("supabase-auth"), message)
        assertTrue(message.contains("401"), message)
    }

    @Test
    fun healthCheckFailsBeforeRequestingWhenTheTokenIsBlank() {
        writeSource(
            "src/commonMain/kotlin/Supabase.kt",
            """
            val weft = Weft {
                install(SupabaseClient) {
                    url = "$serverUrl"
                    anonKey = ""
                }
            }
            """,
        )

        val error = assertFailsWith<GradleException> {
            projectWithPlugin().healthVerification().verifyHealth()
        }
        assertTrue(error.message.orEmpty().contains("empty value"), error.message.orEmpty())
        assertTrue(requestCount.get() == 0, "no request should be sent with a blank credential")
    }

    @Test
    fun healthCheckRunsConfiguredAndDerivedChecksTogether() {
        healthStatus = 200
        writeSource(
            "src/commonMain/kotlin/Supabase.kt",
            """
            val weft = Weft {
                install(SupabaseClient) {
                    url = "$serverUrl"
                    anonKey = "valid-anon-key"
                }
            }
            """,
        )
        val project = projectWithPlugin()
        val extension = project.extensions.getByType(WeftExtension::class.java)
        extension.healthCheck("custom-endpoint") {
            it.url = "$serverUrl/custom"
            it.header("Authorization", "Bearer custom-token")
        }

        project.healthVerification().verifyHealth()

        assertTrue(requestCount.get() >= 2, "expected both the configured and derived checks to run")
    }
}
