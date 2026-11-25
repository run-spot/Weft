package run.thespot.weft.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import run.thespot.weft.gradle.analysis.DependencyWhitelist

/**
 * Fails when a dependency declared directly in this module is not on the Weft allowlist.
 *
 * Only declared external modules are inspected: a project or file dependency has no Maven
 * coordinate, and approved libraries may bring any transitive dependency their API needs. Test
 * source sets are skipped because they do not ship with the app.
 */
public abstract class WeftDependenciesVerifyTask : DefaultTask() {
    /** Declared dependencies as `configuration|group:name`, collected while the project is configured. */
    @get:Input
    public abstract val declaredDependencies: ListProperty<String>

    /** Groups (and dotted children, or `*` patterns) that are approved. */
    @get:Input
    public abstract val allowedDependencyGroups: SetProperty<String>

    /** Exact `group:name` modules approved in addition to the allowed groups. */
    @get:Input
    public abstract val allowedDependencies: SetProperty<String>

    @TaskAction
    public fun verify() {
        val groups = allowedDependencyGroups.get()
        val modules = allowedDependencies.get()
        val usages = declaredDependencies.get().mapNotNull { entry ->
            val separator = entry.indexOf('|')
            if (separator <= 0) return@mapNotNull null
            val configuration = entry.substring(0, separator)
            val coordinate = entry.substring(separator + 1)
            val colon = coordinate.indexOf(':')
            if (colon <= 0) return@mapNotNull null
            DependencyWhitelist.Usage(
                configuration = configuration,
                group = coordinate.substring(0, colon),
                name = coordinate.substring(colon + 1),
            )
        }

        val violations = usages
            .filterNot { DependencyWhitelist.isApproved(it, modules, groups) }
            .distinctBy { it.coordinate to it.configuration }

        if (violations.isNotEmpty()) {
            throw GradleException(DependencyWhitelist.report(violations))
        }
        logger.lifecycle(
            "Weft dependencies verified: ${usages.size} declared dependenc" +
                "${if (usages.size == 1) "y is" else "ies are"} on the allowlist.",
        )
    }
}

/**
 * Collects the external modules this module declares, tagged with the configuration that holds
 * them.
 *
 * Reading the declaration itself, instead of a resolved graph, keeps the check offline and stable:
 * the answer depends on the build script, not on a repository or a lock file.
 */
internal fun collectDeclaredDependencies(project: Project): List<String> {
    val declared = linkedSetOf<String>()
    for (configuration in project.configurations) {
        if (!isDependencyDeclarationConfiguration(configuration.name)) continue
        for (dependency in configuration.dependencies) {
            if (dependency !is ExternalModuleDependency) continue
            val group = dependency.group.takeIf { it.isNotBlank() } ?: continue
            declared += "${configuration.name}|$group:${dependency.name}"
        }
    }
    return declared.toList()
}

/** True for a configuration an app writes a library coordinate into, such as `commonMainApi`. */
internal fun isDependencyDeclarationConfiguration(name: String): Boolean {
    // Test source sets do not ship with the app, so their libraries are outside the allowlist.
    if (name.startsWith("test") || name.startsWith("androidTest") || name.contains("Test")) return false
    return declarationConfigurationNames.any { name == it } ||
        declarationConfigurationSuffixes.any { name.endsWith(it) }
}

private val declarationConfigurationNames = setOf(
    "implementation",
    "api",
    "compileOnly",
    "runtimeOnly",
    "compileOnlyApi",
)

private val declarationConfigurationSuffixes = listOf(
    "Implementation",
    "Api",
    "CompileOnly",
    "RuntimeOnly",
    "CompileOnlyApi",
)
