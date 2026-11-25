package run.thespot.weft.gradle.analysis

/** A `WeftPlugin` implementation found in the scanned sources. */
internal data class PluginDeclaration(
    val name: String,
    val capabilityType: String?,
    val location: SourceLocation,
)

/**
 * An `install(...)` call, with the string literals written in its configuration block.
 *
 * [capabilityType] is the plugin type the installed symbol provides, resolved from the declaration
 * in the same source set or from the build's configured plugin types.
 */
internal data class InstallSite(
    val symbol: String,
    val location: SourceLocation,
    val capabilityType: String?,
    val literals: Map<String, StringLiteral>,
    val literalLocations: Map<String, SourceLocation>,
)

/** A `.get<T>()` call that resolves a required capability. */
internal data class LookupSite(
    val type: String,
    val receiver: String,
    val location: SourceLocation,
)

/** A credential configured as an empty or placeholder value. */
internal data class CredentialIssue(
    val plugin: String,
    val property: String,
    val value: String,
    val kind: CredentialIssueKind,
    val location: SourceLocation,
)

internal enum class CredentialIssueKind { EMPTY, PLACEHOLDER }

/** A health check collected from module code or from the build's `weft { healthCheck { ... } }` DSL. */
internal data class DeclaredHealthCheck(
    val name: String,
    val url: String,
    val headers: Map<String, String>,
    val method: String,
    val origin: String,
    val location: SourceLocation?,
)

/** Everything one file contributes to the analysis. */
internal data class ParsedFile(
    val declarations: List<PluginDeclaration> = emptyList(),
    val installs: List<InstallSite> = emptyList(),
    val lookups: List<LookupSite> = emptyList(),
    val healthChecks: List<DeclaredHealthCheck> = emptyList(),
)
