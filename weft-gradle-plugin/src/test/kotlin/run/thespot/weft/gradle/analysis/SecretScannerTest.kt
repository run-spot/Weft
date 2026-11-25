package run.thespot.weft.gradle.analysis

import java.nio.charset.StandardCharsets
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Secret detection: a privileged credential in client code fails the offline check, and the message
 * never repeats the value it found.
 */
class SecretScannerTest {
    private fun analyze(vararg files: Pair<String, String>): WeftAnalysis {
        val sources = files.map { (path, text) -> KotlinSourceFile(path, text) }
        return WeftPluginAnalyzer(WeftAnalysisOptions()).analyze(sources)
    }

    private fun analyzeOne(text: String): WeftAnalysis =
        analyze("src/commonMain/kotlin/App.kt" to text.trimIndent())

    @Test
    fun rejectsSupabaseServiceRoleJwtWithoutPrintingIt() {
        val token = jwt("""{"iss":"supabase","ref":"project","role":"service_role","iat":1700000000}""")
        val analysis = analyzeOne(
            """
            val weft = Weft {
                install(SupabaseClient) {
                    url = "https://project.supabase.co"
                    anonKey = "$token"
                }
            }
            """,
        )

        assertFalse(analysis.isValid)
        val issue = analysis.secretIssues.single()
        assertEquals(SecretRule.SUPABASE_SERVICE_ROLE_JWT, issue.rule)
        assertEquals(4, issue.location.line)

        val message = WeftReport.offline(analysis)
        assertTrue(message.contains("Supabase service_role JWT"), message)
        assertTrue(message.contains("src/commonMain/kotlin/App.kt:4"), message)
        assertTrue(message.contains("Supabase Edge Functions (supabase secrets set)"), message)
        assertFalse(message.contains(token), "the JWT must not be printed")
        assertFalse(message.contains(token.split('.').getOrElse(1) { "" }), "the payload must not be printed")
    }

    @Test
    fun allowsSupabaseAnonJwt() {
        val token = jwt("""{"iss":"supabase","ref":"project","role":"anon","iat":1700000000}""")
        val analysis = analyzeOne(
            """
            val weft = Weft {
                install(SupabaseClient) {
                    url = "https://project.supabase.co"
                    anonKey = "$token"
                }
            }
            """,
        )

        assertTrue(analysis.secretIssues.isEmpty(), WeftReport.offline(analysis))
        assertTrue(analysis.isValid, WeftReport.offline(analysis))
    }

    @Test
    fun allowsJwtWithoutRoleClaim() {
        val token = jwt("""{"iss":"https://issuer.example","sub":"user-1","aud":"client"}""")
        val analysis = analyzeOne(
            """
            const val ID_TOKEN = "$token"
            """,
        )

        assertTrue(analysis.secretIssues.isEmpty(), WeftReport.offline(analysis))
    }

    @Test
    fun rejectsSkTokenWithoutLeakingIt() {
        val secret = "sk-proj-abcdefghijklmnopqrstuvwxyz0123456789"
        val analysis = analyzeOne(
            """
            val weft = Weft {
                install(OpenAi) {
                    apiKey = "$secret"
                }
            }
            """,
        )

        assertFalse(analysis.isValid)
        assertEquals(SecretRule.SECRET_TOKEN_SK, analysis.secretIssues.single().rule)

        val message = WeftReport.offline(analysis)
        assertTrue(message.contains("Secret token prefix (sk-)"), message)
        assertFalse(message.contains(secret), "the key must not be printed")
        assertFalse(message.contains("abcdefghij"), "no part of the key may be printed")
    }

    @Test
    fun rejectsAwsAccessKeyWithoutLeakingIt() {
        val secret = "AKIAIOSFODNN7EXAMPLE"
        val analysis = analyzeOne(
            """
            object AwsConfig {
                val accessKeyId = "$secret"
            }
            """,
        )

        assertEquals(SecretRule.AWS_ACCESS_KEY, analysis.secretIssues.single().rule)
        val message = WeftReport.offline(analysis)
        assertTrue(message.contains("AWS access key"), message)
        assertFalse(message.contains(secret), message)
    }

    @Test
    fun rejectsGithubAndSlackTokensAndPrivateKeys() {
        val github = "ghp_" + "a".repeat(30)
        val slack = "xoxb-" + "1234567890-abcdefghij"
        val analysis = analyzeOne(
            """
            val token = "$github"
            val slackToken = "$slack"
            val key = "-----BEGIN RSA PRIVATE KEY----- MIIEowIBAAKCAQEA -----END RSA PRIVATE KEY-----"
            """,
        )

        assertEquals(
            setOf(
                SecretRule.GITHUB_PAT,
                SecretRule.SLACK_TOKEN,
                SecretRule.PRIVATE_KEY_BLOCK,
            ),
            analysis.secretIssues.map { it.rule }.toSet(),
        )
        val message = WeftReport.offline(analysis)
        assertFalse(message.contains(github), message)
        assertFalse(message.contains(slack), message)
    }

    @Test
    fun rejectsServiceRoleKeyInInstallBlockWithoutLeakingTheValue() {
        val analysis = analyzeOne(
            """
            val weft = Weft {
                install(SupabaseClient) {
                    url = "https://project.supabase.co"
                    serviceRoleKey = "super-secret-admin-key-value"
                }
            }
            """,
        )

        assertFalse(analysis.isValid)
        val issue = analysis.secretIssues.single()
        assertEquals(SecretRule.FORBIDDEN_CLIENT_PROPERTY, issue.rule)
        assertEquals("serviceRoleKey", issue.property)

        val message = WeftReport.offline(analysis)
        assertTrue(message.contains("serviceRoleKey"), message)
        assertTrue(message.contains("Supabase Edge Functions (supabase secrets set)"), message)
        assertFalse(message.contains("super-secret-admin-key-value"), message)
    }

    @Test
    fun rejectsForbiddenPropertyEvenWhenItsValueComesFromTheEnvironment() {
        val analysis = analyzeOne(
            """
            val weft = Weft {
                install(SupabaseClient) {
                    serviceRoleKey = System.getenv("SUPABASE_SERVICE_ROLE_KEY").orEmpty()
                }
            }
            """,
        )

        assertEquals(SecretRule.FORBIDDEN_CLIENT_PROPERTY, analysis.secretIssues.single().rule)
    }

    @Test
    fun acceptsOrdinaryConfiguration() {
        val analysis = analyzeOne(
            """
            val weft = Weft {
                install(SupabaseClient) {
                    url = "https://project.supabase.co"
                    anonKey = "public-anon-key"
                }
                install(NaverMap) {
                    applicationKey = "naver-app-key"
                }
            }
            """,
        )

        assertTrue(analysis.secretIssues.isEmpty(), WeftReport.offline(analysis))
    }

    @Test
    fun masksSentCredentialsAndDetectedSecretsInText() {
        val masked = SecretRules.mask(
            "key sk-proj-abcdefghijklmnopqrstuvwxyz rejected for header value ak-live-1234",
            secrets = listOf("ak-live-1234"),
        )

        assertFalse(masked.contains("sk-proj-abcdef"), masked)
        assertFalse(masked.contains("ak-live-1234"), masked)
        assertTrue(masked.contains("<redacted>"), masked)
    }

    private fun jwt(payload: String): String {
        val encoder = Base64.getUrlEncoder().withoutPadding()
        fun encode(text: String) = encoder.encodeToString(text.toByteArray(StandardCharsets.UTF_8))
        return listOf(
            encode("""{"alg":"HS256","typ":"JWT"}"""),
            encode(payload),
            encode("signature"),
        ).joinToString(".")
    }
}
