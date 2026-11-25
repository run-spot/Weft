package run.thespot.weft.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import run.thespot.weft.gradle.analysis.KotlinSourceFile
import run.thespot.weft.gradle.analysis.WeftAnalysisOptions
import run.thespot.weft.gradle.analysis.WeftPluginAnalyzer
import run.thespot.weft.gradle.analysis.WeftReport
import java.io.File

/**
 * Offline check that fails a build when the project resolves a Weft capability it never installed,
 * or when an `install(...)` block configures a credential as an empty or placeholder string.
 *
 * Runs as part of `check`, so it needs no network and never touches a provider.
 */
public abstract class WeftPluginsVerifyTask : DefaultTask() {
    /** Kotlin sources to scan. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    public abstract val sourceFiles: ConfigurableFileCollection

    /** Paths in reports are relative to this directory. */
    @get:Input
    public abstract val projectRootPath: Property<String>

    /** Plugin types installed by dependencies. */
    @get:Input
    public abstract val installedTypes: SetProperty<String>

    /** Plugin symbol to plugin type, for plugins declared outside the sources. */
    @get:Input
    public abstract val pluginTypes: MapProperty<String, String>

    /** Expressions whose `.get<T>()` counts as a Weft lookup. */
    @get:Input
    public abstract val lookupReceivers: SetProperty<String>

    /** Literal credential values accepted even though they look like placeholders. */
    @get:Input
    public abstract val allowedCredentialValues: SetProperty<String>

    @TaskAction
    public fun verify() {
        val files = readSources(sourceFiles, projectRootPath.get(), logger)
        val analysis = WeftPluginAnalyzer(options()).analyze(files)
        if (!analysis.isValid) {
            throw GradleException(WeftReport.offline(analysis))
        }
        logger.lifecycle(
            "Weft plugins verified in ${files.size} file(s): " +
                "${analysis.installs.size} install(s), ${analysis.lookups.size} lookup(s).",
        )
    }

    internal fun options(): WeftAnalysisOptions = WeftAnalysisOptions(
        installedTypes = installedTypes.get(),
        pluginTypes = pluginTypes.get(),
        lookupReceivers = lookupReceivers.get(),
        allowedCredentialValues = allowedCredentialValues.get(),
    )
}

/** Reads the scanned files into source models, with paths relative to [projectRoot]. */
internal fun readSources(
    files: Iterable<File>,
    projectRoot: String,
    logger: org.gradle.api.logging.Logger,
): List<KotlinSourceFile> {
    val root = File(projectRoot).toPath()
    return files
        .filter { it.isFile }
        .sortedBy { it.absolutePath }
        .map { file ->
            val relative = runCatching { root.relativize(file.toPath()).toString() }
                .getOrDefault(file.name)
                .replace(File.separatorChar, '/')
            KotlinSourceFile(path = relative, text = file.readText())
        }
        .also { sources -> logger.info("Weft scanned ${sources.size} Kotlin source file(s).") }
}
