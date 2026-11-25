package run.thespot.weft.gradle.analysis

/** Renders analysis results as the message a failing task shows. */
internal object WeftReport {
    fun offline(analysis: WeftAnalysis): String {
        val sections = mutableListOf<String>()

        if (analysis.secretIssues.isNotEmpty()) {
            sections += secretSection(analysis)
        }

        if (analysis.missingLookups.isNotEmpty()) {
            val byType = analysis.missingLookups.groupBy { it.type }
            sections += buildString {
                appendLine(
                    "Weft plugin verification failed: ${byType.size} required plugin " +
                        "type${if (byType.size == 1) " is" else "s are"} not installed.",
                )
                byType.forEach { (type, lookups) ->
                    val places = lookups.joinToString(", ") { it.location.toString() }
                    appendLine()
                    appendLine("- `$type` is resolved at $places, but no installed plugin provides it.")
                }
                appendLine()
                appendLine(
                    "Install a plugin whose `type` is that capability, for example " +
                        "`Weft { install(NaverMap) }`, or read it optionally with `getOrNull<${
                            byType.keys.first()
                        }>()` when the capability is optional.",
                )
            }
        }

        if (analysis.credentialIssues.isNotEmpty()) {
            sections += buildString {
                appendLine(
                    "Weft credential verification failed: ${analysis.credentialIssues.size} " +
                        "configured value${if (analysis.credentialIssues.size == 1) " is" else "s are"} " +
                        "empty or a placeholder.",
                )
                analysis.credentialIssues.forEach { issue ->
                    appendLine()
                    append(
                        "- `install(${issue.plugin})` at ${issue.location} sets " +
                            "`${issue.property}` to ",
                    )
                    append(
                        when (issue.kind) {
                            CredentialIssueKind.EMPTY -> "an empty string"
                            // Usually a harmless placeholder, but a value shaped like a real token is
                            // masked before it can reach the log.
                            CredentialIssueKind.PLACEHOLDER -> "\"${SecretRules.mask(issue.value)}\""
                        },
                    )
                    appendLine(".")
                }
                appendLine()
                append(
                    "Provide the real value, or read it from the environment or a secret store, " +
                        "before shipping this configuration.",
                )
            }
        }

        sections += installedSummary(analysis)
        return sections.joinToString("\n\n")
    }

    /**
     * Reports each finding by category and `file:line` only.
     *
     * The message is the plugin's most public output, so it deliberately repeats no value: not the
     * token, not a prefix, and not the decoded JWT payload beyond the role it is rejected for.
     */
    private fun secretSection(analysis: WeftAnalysis): String = buildString {
        appendLine(
            "Weft secret verification failed: ${analysis.secretIssues.size} sensitive " +
                "value${if (analysis.secretIssues.size == 1) " was" else "s were"} found in " +
                "client code.",
        )
        analysis.secretIssues.forEach { issue ->
            appendLine()
            when {
                issue.property != null -> appendLine(
                    "- ${issue.location} sets `${issue.property}` in an `install(...)` block " +
                        "(${issue.rule.label}).",
                )

                else -> appendLine("- ${issue.location}: ${issue.rule.label}.")
            }
        }
        appendLine()
        append(SecretRules.GUIDANCE)
    }

    private fun installedSummary(analysis: WeftAnalysis): String {
        if (analysis.installedTypes.isEmpty()) {
            return "No Weft plugin types are installed in the scanned sources."
        }
        val installed = analysis.installedTypes.entries.joinToString(", ") { (type, location) ->
            if (location == null) type else "$type (install at $location)"
        }
        return "Installed plugin types: $installed."
    }
}

/** Renders the health-check report of one `verifyWeftHealth` run. */
internal object HealthCheckReport {
    fun summary(results: List<Pair<String, String>>): String = buildString {
        appendLine("Weft health checks:")
        results.forEach { (name, status) -> appendLine("  $name → $status") }
    }
}
