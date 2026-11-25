package run.thespot.weft.gradle

import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import run.thespot.weft.gradle.analysis.DependencyWhitelist
import javax.inject.Inject

/**
 * Configuration for the three Weft verification tasks.
 *
 * ```kotlin
 * plugins {
 *     id("weft.verify")
 * }
 *
 * weft {
 *     // Module code is scanned from src/ by default; add a generated or shared source tree here.
 *     sourceDirectories.from("build/generated/weft")
 *
 *     // Plugins published by a dependency, which source scanning cannot see.
 *     installedTypes.add("PushPlugin")
 *     pluginTypes.put("NaverMap", "MapPlugin")
 *
 *     // Apply the direct-dependency allowlist in `check` as well.
 *     enforceDependencyWhitelist = true
 *     allowedDependencyGroups.add("com.example")
 *
 *     // Endpoints the plugin modules report; the task runs them all in one pass.
 *     healthCheck("supabase") {
 *         url = "https://project.supabase.co/auth/v1/health"
 *         header("apikey", providers.environmentVariable("SUPABASE_ANON_KEY").orNull.orEmpty())
 *     }
 * }
 * ```
 */
public abstract class WeftExtension @Inject constructor(project: Project) {
    private val objects: ObjectFactory = project.objects
    private val healthChecks: MutableList<HealthCheckSpec> = mutableListOf()

    /** Source roots scanned by both tasks. Defaults to the project's `src` directory. */
    public val sourceDirectories: ConfigurableFileCollection = objects.fileCollection()

    /** Glob patterns of files to scan. */
    public val sourceIncludes: SetProperty<String> =
        objects.setProperty(String::class.java).convention(listOf("**/*.kt"))

    /** Glob patterns to skip: build output, generated code, and test source sets by default. */
    public val sourceExcludes: SetProperty<String> = objects.setProperty(String::class.java).convention(
        listOf("**/build/**", "**/generated/**", "**/*Test/**", "**/test/**", "**/androidTest/**"),
    )

    /** Plugin types installed by dependencies, which source scanning cannot resolve. */
    public val installedTypes: SetProperty<String> = objects.setProperty(String::class.java).convention(emptyList())

    /** Plugin symbol to plugin type, for plugin objects declared outside the scanned sources. */
    public val pluginTypes: MapProperty<String, String> =
        objects.mapProperty(String::class.java, String::class.java)

    /** Expressions whose `.get<T>()` counts as a Weft lookup; other `.get<T>()` calls are ignored. */
    public val lookupReceivers: SetProperty<String> =
        objects.setProperty(String::class.java).convention(listOf("weft", "LocalWeft.current"))

    /** Literal values accepted even though they look like placeholders, such as a demo key. */
    public val allowedCredentialValues: SetProperty<String> =
        objects.setProperty(String::class.java).convention(emptyList())

    /** Whether `verifyWeftHealth` also derives probes from credentials written in `install(...)`. */
    public val detectHealthChecks: Property<Boolean> =
        objects.property(Boolean::class.java).convention(true)

    /**
     * Whether `check` also runs `verifyWeftDependencies`.
     *
     * Off by default so an existing build keeps its current dependency set; turning it on is the
     * deliberate step that applies the Weft allowlist to this module.
     */
    public val enforceDependencyWhitelist: Property<Boolean> =
        objects.property(Boolean::class.java).convention(false)

    /**
     * Dependency groups `verifyWeftDependencies` approves.
     *
     * An entry approves its own group and any dotted child (`io.ktor` covers
     * `io.ktor.client.core`), and an entry ending in `.*` approves the whole family
     * (`androidx.*`). The defaults are the standard Weft/KMP/Compose/AndroidX/Google groups; add to
     * them for one more library family, or `set(listOf(...))` to replace them.
     */
    public val allowedDependencyGroups: SetProperty<String> =
        objects.setProperty(String::class.java).convention(DependencyWhitelist.defaultGroups)

    /** Exact `group:name` modules `verifyWeftDependencies` approves beyond the allowed groups. */
    public val allowedDependencies: SetProperty<String> =
        objects.setProperty(String::class.java).convention(emptyList())

    /** Timeout applied to each health check request. */
    public val healthCheckTimeoutSeconds: Property<Int> =
        objects.property(Int::class.java).convention(15)

    init {
        sourceDirectories.from(project.layout.projectDirectory.dir("src"))
    }

    /**
     * Adds a health check that `verifyWeftHealth` runs with every other check.
     *
     * A capability library reports its own probes through `WeftPlugin.healthCheck`; this DSL covers
     * an endpoint the build knows about even when the module's probe cannot be read statically, such
     * as a token injected from the environment.
     */
    public fun healthCheck(name: String, configure: Action<HealthCheckSpec>): HealthCheckSpec {
        val spec = HealthCheckSpec(name)
        configure.execute(spec)
        healthChecks += spec
        return spec
    }

    internal fun healthCheckSpecs(): List<HealthCheckSpec> = healthChecks.toList()
}

/** One endpoint added through `weft { healthCheck("name") { ... } }`. */
public class HealthCheckSpec internal constructor(
    /** Name shown in the health report. */
    public val name: String,
) {
    /** Absolute HTTP(S) URL of the endpoint, typically the provider's health route. */
    public var url: String? = null

    /** HTTP method, `GET` unless the endpoint answers another verb. */
    public var method: String = "GET"

    private val headers: MutableMap<String, String> = linkedMapOf()

    /** Adds a request header, usually the credential being verified. */
    public fun header(name: String, value: String) {
        headers[name] = value
    }

    /** Adds several request headers at once. */
    public fun headers(values: Map<String, String>) {
        headers.putAll(values)
    }

    internal fun toInput(): HealthCheckInput = HealthCheckInput(
        name = name,
        url = url.orEmpty(),
        headers = headers.toMap(),
        method = method,
    )
}

/** Immutable health check handed to the task as an input. */
public class HealthCheckInput(
    public val name: String,
    public val url: String,
    public val headers: Map<String, String>,
    public val method: String,
) : java.io.Serializable
