package run.thespot.weft.gradle.analysis

/**
 * Direct-dependency allowlist used by `verifyWeftDependencies`.
 *
 * The policy is the one in `harness/dependency-policy.json`: an app ships its features from a small
 * set of Weft and platform libraries, and a new third-party library is a policy change rather than a
 * one-off build edit. The check looks only at dependencies the build declares directly; anything
 * approved may still bring its own transitive dependencies.
 */
internal object DependencyWhitelist {
    /**
     * Groups a Weft app may depend on without further configuration.
     *
     * An entry matches its own group and any dotted child, so `org.jetbrains.compose` covers
     * `org.jetbrains.compose.runtime`, and a `.*` entry such as `androidx.*` covers the whole
     * AndroidX family. Callers restrict or extend this list with `weft { allowedDependencyGroups }`.
     */
    val defaultGroups: List<String> = listOf(
        "run.thespot.weft",
        "org.jetbrains.kotlin",
        "org.jetbrains.compose",
        "org.jetbrains.kotlinx",
        "io.ktor",
        "io.insert-koin",
        "androidx.*",
        "com.google.*",
    )

    /** A declared external module dependency, kept as `configuration` plus `group:name`. */
    data class Usage(
        val configuration: String,
        val group: String,
        val name: String,
    ) {
        val coordinate: String get() = "$group:$name"
    }

    fun isApproved(
        usage: Usage,
        allowedDependencies: Set<String>,
        allowedGroups: Set<String>,
    ): Boolean {
        if (usage.coordinate in allowedDependencies) return true
        return allowedGroups.any { matchesGroup(it, usage.group) }
    }

    /** True when [pattern] approves [group] exactly, as a dotted child, or through a wildcard. */
    fun matchesGroup(pattern: String, group: String): Boolean {
        val trimmed = pattern.trim()
        if (trimmed.isEmpty()) return false
        return when {
            trimmed == "*" -> true
            trimmed.endsWith(".*") -> {
                val prefix = trimmed.removeSuffix(".*")
                group == prefix || group.startsWith("$prefix.")
            }

            trimmed.endsWith("*") -> group.startsWith(trimmed.removeSuffix("*"))
            else -> group == trimmed || group.startsWith("$trimmed.")
        }
    }

    /** The message a failing `verifyWeftDependencies` run shows. Contains no credential material. */
    fun report(violations: List<Usage>): String = buildString {
        appendLine(
            "Weft dependency verification failed: ${violations.size} declared " +
                "dependenc${if (violations.size == 1) "y is" else "ies are"} not on the allowlist.",
        )
        violations.forEach { usage ->
            appendLine()
            appendLine("- `${usage.coordinate}` is declared in `${usage.configuration}`.")
        }
        appendLine()
        appendLine(
            "Approve a group in `weft { allowedDependencyGroups.add(\"<group>\") }`, approve the " +
                "exact module in `weft { allowedDependencies.add(\"<group>:<name>\") }`, or remove " +
                "the dependency.",
        )
        appendLine()
        append(
            "Set `weft { enforceDependencyWhitelist = true }` to run this check with `check`; a " +
                "release job can also run `./gradlew verifyWeftDependencies` directly.",
        )
    }
}
