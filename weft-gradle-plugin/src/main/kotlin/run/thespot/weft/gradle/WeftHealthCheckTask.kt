package run.thespot.weft.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import run.thespot.weft.gradle.analysis.HealthCheckReport
import run.thespot.weft.gradle.analysis.WeftAnalysisOptions
import run.thespot.weft.gradle.analysis.WeftPluginAnalyzer
import run.thespot.weft.gradle.health.HealthCheckRequest
import run.thespot.weft.gradle.health.HealthCheckRunner
import java.io.File

/**
 * Runs every configured and detected health check in one pass and fails when a credential is blank,
 * rejected, or the endpoint is unreachable.
 *
 * The task needs the network, so it is deliberately not part of `check`; call it explicitly, for
 * example from a release job: `./gradlew verifyWeftHealth`.
 */
public abstract class WeftHealthCheckTask : DefaultTask() {
    /** Kotlin sources scanned for literal `WeftHealthCheck(...)` declarations and install blocks. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    public abstract val sourceFiles: ConfigurableFileCollection

    /** Paths in reports are relative to this directory. */
    @get:Input
    public abstract val projectRootPath: Property<String>

    /** Endpoints added through `weft { healthCheck("name") { ... } }`. */
    @get:Input
    public abstract val configuredChecks: ListProperty<HealthCheckInput>

    /** Whether probes derived from `install(...)` credentials are included. */
    @get:Input
    public abstract val detectCredentialProbes: Property<Boolean>

    /** Timeout applied to each request, in seconds. */
    @get:Input
    public abstract val timeoutSeconds: Property<Int>

    @get:Input
    public abstract val installedTypes: SetProperty<String>

    @get:Input
    public abstract val pluginTypes: MapProperty<String, String>

    @get:Input
    public abstract val lookupReceivers: SetProperty<String>

    @get:Input
    public abstract val allowedCredentialValues: SetProperty<String>

    @TaskAction
    public fun verifyHealth() {
        val configured = configuredChecks.get().map { input ->
            HealthCheckRequest(
                name = input.name,
                url = input.url,
                headers = input.headers,
                method = input.method,
                origin = "weft { healthCheck(\"${input.name}\") }",
            )
        }

        val detected = if (detectCredentialProbes.get()) {
            val analysis = WeftPluginAnalyzer(
                WeftAnalysisOptions(
                    installedTypes = installedTypes.get(),
                    pluginTypes = pluginTypes.get(),
                    lookupReceivers = lookupReceivers.get(),
                    allowedCredentialValues = allowedCredentialValues.get(),
                ),
            ).analyze(readSources(sourceFiles, projectRootPath.get(), logger))
            analysis.healthChecks.map { check ->
                HealthCheckRequest(
                    name = check.name,
                    url = check.url,
                    headers = check.headers,
                    method = check.method,
                    origin = check.origin + (check.location?.let { " at $it" } ?: ""),
                )
            }
        } else {
            emptyList()
        }

        val requests = (configured + detected).distinctBy { it.name to it.url }
        if (requests.isEmpty()) {
            logger.lifecycle(
                "Weft health checks: nothing to verify. Add one with weft { healthCheck(\"name\") { url = ... } } " +
                    "or configure a credential in an install(...) block.",
            )
            return
        }

        val runner = HealthCheckRunner(readTimeoutMillis = timeoutSeconds.get() * 1_000)
        val configurationProblems = requests.mapNotNull { request ->
            runner.validate(request)?.let { reason -> request to reason }
        }
        if (configurationProblems.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("Weft health checks are misconfigured:")
                    configurationProblems.forEach { (request, reason) ->
                        appendLine("- $reason${request.origin?.let { " ($it)" } ?: ""}")
                    }
                }.trim(),
            )
        }

        val results = requests.map { runner.run(it) }
        logger.lifecycle(
            HealthCheckReport.summary(
                results.map { result ->
                    result.request.name to if (result.success) {
                        "ok (HTTP ${result.status})"
                    } else {
                        "FAILED: ${result.detail}"
                    }
                },
            ).trimEnd(),
        )

        val failures = results.filter { !it.success }
        if (failures.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine(
                        "Weft health checks failed: ${failures.size} of ${results.size} " +
                            "credential${if (results.size == 1) "" else "s"} did not answer correctly.",
                    )
                    failures.forEach { failure ->
                        appendLine(
                            "- ${failure.request.name} (${failure.request.url})" +
                                (failure.request.origin?.let { " from $it" } ?: "") +
                                ": ${failure.detail}",
                        )
                    }
                }.trim(),
            )
        }
    }
}

/** Convenience for tests and error messages. */
internal fun File.weftRelativePath(root: File): String =
    runCatching { root.toPath().relativize(toPath()).toString() }.getOrDefault(name)
