package run.thespot.weft.gradle.analysis

import java.nio.charset.StandardCharsets
import java.util.Base64

/** A sensitive value found in client source, described by category instead of by value. */
internal data class SecretIssue(
    val rule: SecretRule,
    val location: SourceLocation,
    val property: String? = null,
)

/**
 * Why a value may not appear in client code.
 *
 * The labels are safe to print: they name the category, never the value. A message built from these
 * issues can therefore be shown in Gradle output, CI logs, and chat without leaking what it found.
 */
internal enum class SecretRule(val label: String) {
    SUPABASE_SERVICE_ROLE_JWT("Supabase service_role JWT"),
    SECRET_TOKEN_SK("Secret token prefix (sk-)"),
    GITHUB_PAT("GitHub token prefix (ghp_)"),
    GITHUB_OAUTH_TOKEN("GitHub token prefix (gho_)"),
    GITHUB_FINE_GRAINED_TOKEN("GitHub token prefix (github_pat_)"),
    AWS_ACCESS_KEY("AWS access key (AKIA...)"),
    SLACK_TOKEN("Slack token prefix (xox[baprs]-)"),
    PRIVATE_KEY_BLOCK("Private key block (-----BEGIN ... PRIVATE KEY-----)"),
    FORBIDDEN_CLIENT_PROPERTY("Forbidden client-side credential property"),
}

/**
 * Finds credentials that must never ship in an app, and masks them wherever the plugin prints text.
 *
 * Detection works on the file as written rather than on the sanitized structure, because the values
 * live inside string literals. A key that is commented out is still a leaked key, so comments are
 * scanned as well. The JWT rule is the one place a value is interpreted: the payload is decoded only
 * to read its `role` claim, and a Supabase `service_role` token is rejected while the public `anon`
 * role is allowed.
 */
internal object SecretRules {
    /** What to do about whatever was found. Safe to print verbatim, and quoted by the report. */
    const val GUIDANCE: String =
        "Sensitive keys must not be placed in client code or chat; move them to Supabase Edge " +
            "Functions (supabase secrets set)."

    /** Property names that hand an app a privileged credential, so writing one is a design error. */
    val forbiddenClientProperties: Set<String> = setOf(
        "serviceRole",
        "serviceRoleKey",
        "adminKey",
        "secretKey",
        "privateKey",
        "clientSecret",
    )

    private const val SERVICE_ROLE = "service_role"
    private const val ANON_ROLE = "anon"

    private val tokenPatterns: List<Pair<SecretRule, Regex>> = listOf(
        SecretRule.SECRET_TOKEN_SK to Regex("""\bsk-[A-Za-z0-9_-]{16,}"""),
        SecretRule.GITHUB_PAT to Regex("""\bghp_[A-Za-z0-9_]{8,}"""),
        SecretRule.GITHUB_OAUTH_TOKEN to Regex("""\bgho_[A-Za-z0-9_]{8,}"""),
        SecretRule.GITHUB_FINE_GRAINED_TOKEN to Regex("""\bgithub_pat_[A-Za-z0-9_]{8,}"""),
        SecretRule.AWS_ACCESS_KEY to Regex("""\bAKIA[0-9A-Z]{16}\b"""),
        SecretRule.SLACK_TOKEN to Regex("""\bxox[baprs]-[A-Za-z0-9-]{8,}"""),
        SecretRule.PRIVATE_KEY_BLOCK to Regex("""-----BEGIN (?:[A-Z0-9 ]+ )?PRIVATE KEY-----"""),
    )

    private val jwtPattern = Regex("""\beyJ[A-Za-z0-9_-]*\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+""")
    private val rolePattern = Regex(""""role"\s*:\s*"([^"]*)"""")

    /** True when a value looks like a comma-free credential property such as `service_role_key`. */
    fun isForbiddenClientProperty(property: String): Boolean {
        val normalized = property.lowercase().replace("_", "").replace("-", "")
        return forbiddenClientProperties.any { it.lowercase() == normalized }
    }

    /** Every sensitive value in [text], reported by category and position only. */
    fun scan(path: String, text: String): List<SecretIssue> {
        val issues = mutableListOf<Pair<Int, SecretIssue>>()
        fun location(offset: Int) = SourceLocation(
            path = path,
            line = KotlinSourceSanitizer.lineAt(text, offset),
            column = KotlinSourceSanitizer.columnAt(text, offset),
        )

        for ((rule, pattern) in tokenPatterns) {
            for (match in pattern.findAll(text)) {
                issues += match.range.first to SecretIssue(rule = rule, location = location(match.range.first))
            }
        }

        for (match in jwtPattern.findAll(text)) {
            val role = jwtRole(match.value) ?: continue
            when (role) {
                SERVICE_ROLE -> issues += match.range.first to SecretIssue(
                    rule = SecretRule.SUPABASE_SERVICE_ROLE_JWT,
                    location = location(match.range.first),
                )

                ANON_ROLE -> Unit
                else -> Unit
            }
        }

        return issues
            .sortedBy { it.first }
            .map { it.second }
            .distinctBy { it.rule to it.location.path to it.location.line }
    }

    /**
     * Replaces known credentials in [text], including the given [secrets] the plugin itself sent, so
     * a provider that echoes a key in an error body cannot put it in a Gradle log.
     */
    fun mask(text: String, secrets: Iterable<String> = emptyList()): String {
        if (text.isEmpty()) return text
        var output = text
        for (secret in secrets) {
            if (secret.length < 4) continue
            if (output.contains(secret)) output = output.replace(secret, "<redacted>")
        }
        for ((rule, pattern) in tokenPatterns) {
            output = pattern.replace(output) { "<redacted:${rule.name.lowercase()}>" }
        }
        return output
    }

    /** The `role` claim of a JWT literal, or `null` when the payload cannot be read. */
    private fun jwtRole(token: String): String? {
        val payload = token.split('.').getOrNull(1) ?: return null
        val json = runCatching {
            String(Base64.getUrlDecoder().decode(payload), StandardCharsets.UTF_8)
        }.getOrNull() ?: return null
        return rolePattern.find(json)?.groupValues?.get(1)
    }
}
