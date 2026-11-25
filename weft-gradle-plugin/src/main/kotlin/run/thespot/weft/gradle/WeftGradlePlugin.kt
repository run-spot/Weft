package run.thespot.weft.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Registers Weft's verification tasks and its `weft { }` extension.
 *
 * ```kotlin
 * plugins {
 *     id("weft.verify")
 * }
 * ```
 *
 * - `verifyWeftPlugins` is offline and runs with `check`: it fails when a required `get<T>()` has no
 *   installed plugin, when an `install(...)` block configures an empty or placeholder credential, or
 *   when client code contains a credential that must not ship (a Supabase `service_role` JWT, a
 *   provider secret token, or a privileged configuration property).
 * - `verifyWeftHealth` is a separate, networked task: it runs every health check the modules and the
 *   build declare, and fails when a credential is blank, rejected, or the endpoint is unreachable.
 * - `verifyWeftDependencies` checks the declared library allowlist and joins `check` once a build
 *   sets `weft { enforceDependencyWhitelist = true }`.
 */
public class WeftGradlePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create("weft", WeftExtension::class.java)

        val scannedSources = project.files(extension.sourceDirectories).asFileTree
            .matching { tree ->
                tree.include(extension.sourceIncludes.get())
                tree.exclude(extension.sourceExcludes.get())
            }

        val verifyPlugins = project.tasks.register("verifyWeftPlugins", WeftPluginsVerifyTask::class.java) { task ->
            task.group = "verification"
            task.description =
                "Checks that every required Weft plugin type is installed and that configured " +
                    "credentials are not empty or placeholders."
            task.sourceFiles.from(scannedSources)
            task.projectRootPath.set(project.projectDir.absolutePath)
            task.installedTypes.set(extension.installedTypes)
            task.pluginTypes.set(extension.pluginTypes)
            task.lookupReceivers.set(extension.lookupReceivers)
            task.allowedCredentialValues.set(extension.allowedCredentialValues)
        }

        project.tasks.matching { it.name == "check" }.configureEach { checkTask ->
            checkTask.dependsOn(verifyPlugins)
        }

        project.tasks.register("verifyWeftDependencies", WeftDependenciesVerifyTask::class.java) { task ->
            task.group = "verification"
            task.description =
                "Fails when a dependency declared in this module is not on the Weft allowlist."
            task.declaredDependencies.set(project.provider { collectDeclaredDependencies(project) })
            task.allowedDependencyGroups.set(extension.allowedDependencyGroups)
            task.allowedDependencies.set(extension.allowedDependencies)
        }

        project.tasks.register("verifyWeftHealth", WeftHealthCheckTask::class.java) { task ->
            task.group = "verification"
            task.description =
                "Runs every configured and module-declared Weft health check and fails when a " +
                    "credential is blank, rejected, or the endpoint is unreachable."
            task.sourceFiles.from(scannedSources)
            task.projectRootPath.set(project.projectDir.absolutePath)
            task.installedTypes.set(extension.installedTypes)
            task.pluginTypes.set(extension.pluginTypes)
            task.lookupReceivers.set(extension.lookupReceivers)
            task.allowedCredentialValues.set(extension.allowedCredentialValues)
            task.detectCredentialProbes.set(extension.detectHealthChecks)
            task.timeoutSeconds.set(extension.healthCheckTimeoutSeconds)
            task.configuredChecks.set(project.provider { extension.healthCheckSpecs().map { it.toInput() } })
        }

        // The allowlist is opt-in: a build that has not adopted it keeps `check` as it was.
        project.afterEvaluate {
            val verifyDependencies = project.tasks.named("verifyWeftDependencies")
            if (extension.enforceDependencyWhitelist.getOrElse(false)) {
                project.tasks.matching { it.name == "check" }.configureEach { checkTask ->
                    checkTask.dependsOn(verifyDependencies)
                }
            }
        }
    }
}
