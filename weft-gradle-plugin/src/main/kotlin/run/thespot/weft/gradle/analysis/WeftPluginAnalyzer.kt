package run.thespot.weft.gradle.analysis

/**
 * Configuration that lets a build describe what the scanner cannot see in source.
 *
 * @param installedTypes plugin types provided by dependencies, so a `get<T>()` for a capability
 *   installed in another module still resolves.
 * @param pluginTypes plugin symbol to plugin type, for plugins declared outside the scanned sources.
 * @param lookupReceivers expressions whose `.get<T>()` counts as a Weft lookup. Any other
 *   `.get<T>()`, such as a map read, is ignored.
 * @param allowedCredentialValues literal values accepted in credential properties even though they
 *   look like placeholders, for example a documented demo key used by a sample app.
 */
internal data class WeftAnalysisOptions(
    val installedTypes: Set<String> = emptySet(),
    val pluginTypes: Map<String, String> = emptyMap(),
    val lookupReceivers: Set<String> = setOf("weft", "LocalWeft.current"),
    val allowedCredentialValues: Set<String> = emptySet(),
)

/** Everything the offline checks need from a project's sources. */
internal class WeftAnalysis(
    val installedTypes: Map<String, SourceLocation?>,
    val installs: List<InstallSite>,
    val lookups: List<LookupSite>,
    val missingLookups: List<LookupSite>,
    val credentialIssues: List<CredentialIssue>,
    val secretIssues: List<SecretIssue>,
    val healthChecks: List<DeclaredHealthCheck>,
) {
    val isValid: Boolean
        get() = missingLookups.isEmpty() && credentialIssues.isEmpty() && secretIssues.isEmpty()
}

/**
 * Combines every scanned file into one picture of what the project installs and what it resolves.
 *
 * The checks are intentionally conservative. A plugin symbol that cannot be tied to a declaration is
 * reported as unresolved rather than assumed missing, because the declaration may live in a
 * dependency; a build fixes that by listing the symbol in `weft { pluginTypes { ... } }`.
 */
internal class WeftPluginAnalyzer(private val options: WeftAnalysisOptions) {
    fun analyze(files: List<KotlinSourceFile>): WeftAnalysis {
        val parsed = files.map { WeftSourceParser(it).parse() }
        val declarations = parsed.flatMap { it.declarations }.associateBy { it.name }

        val installs = parsed.flatMap { it.installs }.map { site ->
            site.copy(
                capabilityType = site.capabilityType
                    ?: declarations[site.symbol]?.capabilityType
                    ?: options.pluginTypes[site.symbol],
            )
        }

        val installedTypes = linkedMapOf<String, SourceLocation?>()
        options.installedTypes.forEach { installedTypes[it] = null }
        installs.forEach { site -> site.capabilityType?.let { installedTypes.putIfAbsent(it, site.location) } }

        val lookups = parsed.flatMap { it.lookups }.filter { lookup ->
            options.lookupReceivers.any { receiver ->
                lookup.receiver == receiver || lookup.receiver.endsWith(".$receiver")
            }
        }
        val missingLookups = lookups.filter { it.type !in installedTypes }

        val credentialIssues = installs.flatMap { site ->
            site.literals.mapNotNull { (property, literal) ->
                if (literal.hasInterpolation || !CredentialRule.isCredentialProperty(property)) return@mapNotNull null
                if (literal.value in options.allowedCredentialValues) return@mapNotNull null
                val kind = when {
                    literal.value.isBlank() -> CredentialIssueKind.EMPTY
                    CredentialRule.isPlaceholder(literal.value) -> CredentialIssueKind.PLACEHOLDER
                    else -> null
                } ?: return@mapNotNull null
                CredentialIssue(
                    plugin = site.symbol,
                    property = property,
                    value = literal.value,
                    kind = kind,
                    location = site.literalLocations[property] ?: site.location,
                )
            }
        }

        val declaredChecks = parsed.flatMap { it.healthChecks }
        val detectedChecks = detectedHealthChecks(installs)
        val secretIssues = parsed.flatMap { it.secretIssues }
            .distinctBy { it.rule to it.location.path to it.location.line to it.property }

        return WeftAnalysis(
            installedTypes = installedTypes,
            installs = installs,
            lookups = lookups,
            missingLookups = missingLookups,
            credentialIssues = credentialIssues,
            secretIssues = secretIssues,
            healthChecks = (declaredChecks + detectedChecks).distinctBy { it.name to it.url },
        )
    }

    /**
     * Derives probes from credentials written in an `install(...)` block.
     *
     * A capability library reports its own probes through `WeftPlugin.healthCheck`, but a build also
     * writes the provider's URL or key directly in the install block, so the same endpoint can be
     * derived from those literals without running application code.
     */
    private fun detectedHealthChecks(installs: List<InstallSite>): List<DeclaredHealthCheck> {
        val checks = mutableListOf<DeclaredHealthCheck>()
        for (install in installs) {
            val literals = install.literals.filterValues { !it.hasInterpolation }
            fun value(vararg names: String): String? = names.firstNotNullOfOrNull { literals[it]?.value }
            fun location(vararg names: String): SourceLocation =
                names.firstNotNullOfOrNull { install.literalLocations[it] } ?: install.location

            val hint = "${install.symbol} ${install.capabilityType.orEmpty()}".lowercase()

            value("healthCheckUrl", "healthUrl")?.let { url ->
                checks += DeclaredHealthCheck(
                    name = "${install.symbol}-health",
                    url = url,
                    headers = emptyMap(),
                    method = "GET",
                    origin = "install(${install.symbol})",
                    location = location("healthCheckUrl", "healthUrl"),
                )
            }

            val projectUrl = value("url", "baseUrl", "projectUrl", "supabaseUrl")
            val anonKey = value("anonKey", "anon_key", "anonkey")
            if (projectUrl != null && anonKey != null) {
                checks += DeclaredHealthCheck(
                    name = "supabase-auth",
                    url = projectUrl.trimEnd('/') + "/auth/v1/health",
                    headers = mapOf("apikey" to anonKey),
                    method = "GET",
                    origin = "install(${install.symbol})",
                    location = location("anonKey", "anon_key"),
                )
            }

            if ("kakao" in hint) {
                value("restApiKey", "apiKey", "appKey")?.let { key ->
                    checks += DeclaredHealthCheck(
                        name = "kakao-local-api",
                        url = KAKAO_LOCAL_API_PROBE,
                        headers = mapOf("Authorization" to "KakaoAK $key"),
                        method = "GET",
                        origin = "install(${install.symbol})",
                        location = location("restApiKey", "apiKey", "appKey"),
                    )
                }
            }
        }
        return checks
    }

    private companion object {
        /** A Kakao Local API search that answers 200 only for a valid REST API key. */
        const val KAKAO_LOCAL_API_PROBE =
            "https://dapi.kakao.com/v2/local/search/address.json?query=%EC%84%9C%EC%9A%B8"
    }
}

/** Which properties hold a credential, and which literal values are clearly not one. */
internal object CredentialRule {
    private val credentialSuffixes = listOf(
        "apikey",
        "anonkey",
        "appkey",
        "restapikey",
        "restkey",
        "nativekey",
        "jskey",
        "clientsecret",
        "clientid",
        "secret",
        "token",
        "password",
        "passwd",
        "key",
        "url",
        "uri",
        "endpoint",
        "baseurl",
        "healthurl",
        "healthcheckurl",
    )

    private val placeholderMarkers = listOf(
        "todo",
        "tbd",
        "fixme",
        "changeme",
        "change-me",
        "change_me",
        "placeholder",
        "your_",
        "your-",
        "<your",
        "example",
        "sample",
        "dummy",
        "insert_",
        "replace_",
        "xxx",
    )

    fun isCredentialProperty(property: String): Boolean {
        val normalized = property.lowercase().replace("_", "").replace("-", "")
        return credentialSuffixes.any { normalized == it || normalized.endsWith(it) }
    }

    fun isPlaceholder(value: String): Boolean {
        val lower = value.trim().lowercase()
        if (lower.isEmpty()) return true
        return placeholderMarkers.any { lower.contains(it) }
    }
}
