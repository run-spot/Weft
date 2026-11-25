package run.thespot.weft.gradle.analysis

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Offline analysis behavior: installed plugin types, required lookups, credential literals, and
 * health check discovery.
 */
class WeftPluginAnalyzerTest {
    @Test
    fun passesWhenInstalledPluginProvidesTheResolvedCapability() {
        val analysis = analyze(
            "src/commonMain/kotlin/App.kt" to """
                val weft = Weft {
                    install(NaverMap) {
                        applicationKey = "naver-app-key"
                    }
                }

                @Composable
                fun Screen() {
                    val map: MapPlugin = LocalWeft.current.get<MapPlugin>()
                }
            """.trimIndent(),
            "src/commonMain/kotlin/NaverMap.kt" to """
                object NaverMap : WeftPlugin<MapConfig, MapPlugin> {
                    override val type: KClass<MapPlugin> = MapPlugin::class
                }
            """.trimIndent(),
        )

        assertTrue(analysis.isValid, "expected no issues")
        assertTrue(analysis.installedTypes.containsKey("MapPlugin"))
        assertEquals(1, analysis.lookups.size)
    }

    @Test
    fun ignoresOptionalLookups() {
        val analysis = analyze(
            "src/commonMain/kotlin/Analytics.kt" to """
                fun track(): AnalyticsPlugin? = weft.getOrNull<AnalyticsPlugin>()
            """.trimIndent(),
        )

        assertTrue(analysis.isValid)
        assertTrue(analysis.lookups.isEmpty())
    }

    @Test
    fun failsWhenRequiredCapabilityIsNotInstalled() {
        val analysis = analyze(
            "src/commonMain/kotlin/Map.kt" to """
                @Composable
                fun WeftMap() {
                    val plugin = LocalWeft.current.get<MapPlugin>()
                }
            """.trimIndent(),
        )

        assertFalse(analysis.isValid)
        val missing = analysis.missingLookups.single()
        assertEquals("MapPlugin", missing.type)
        assertEquals(3, missing.location.line)

        val message = WeftReport.offline(analysis)
        assertTrue(message.contains("MapPlugin"), message)
        assertTrue(message.contains("src/commonMain/kotlin/Map.kt:3"), message)
        assertTrue(message.contains("getOrNull<MapPlugin>"), message)
    }

    @Test
    fun resolvesPluginThroughExplicitTypeDeclaration() {
        val analysis = analyze(
            "src/commonMain/kotlin/Login.kt" to """
                object WeftLogin : WeftPlugin<LoginConfig, LoginPlugin> {
                    override val type: KClass<LoginPlugin> = LoginPlugin::class
                }
            """.trimIndent(),
            "src/commonMain/kotlin/Screen.kt" to """
                val weft = Weft { install(WeftLogin) }

                fun signIn() = weft.get<LoginPlugin>()
            """.trimIndent(),
        )

        assertTrue(analysis.isValid, WeftReport.offline(analysis))
    }

    @Test
    fun resolvesMultiLineSupertypeDeclaration() {
        val analysis = analyze(
            "src/commonMain/kotlin/Push.kt" to """
                object WeftPush :
                    WeftPlugin<PushConfig, PushPlugin> {
                    override val pluginName: String = "push"
                }
            """.trimIndent(),
            "src/commonMain/kotlin/Screen.kt" to """
                val weft = Weft { install(WeftPush) }

                fun register() = weft.get<PushPlugin>()
            """.trimIndent(),
        )

        assertTrue(analysis.isValid, WeftReport.offline(analysis))
    }

    @Test
    fun reportsEmptyCredentialLiteral() {
        val analysis = analyze(
            "src/commonMain/kotlin/App.kt" to """
                val weft = Weft {
                    install(NaverMap) {
                        applicationKey = ""
                    }
                }
            """.trimIndent(),
        )

        assertFalse(analysis.isValid)
        val issue = analysis.credentialIssues.single()
        assertEquals("applicationKey", issue.property)
        assertEquals(CredentialIssueKind.EMPTY, issue.kind)
        assertEquals(3, issue.location.line)
        val message = WeftReport.offline(analysis)
        assertTrue(message.contains("`applicationKey`"), message)
        assertTrue(message.contains("empty string"), message)
    }

    @Test
    fun reportsPlaceholderCredentialLiteral() {
        val analysis = analyze(
            "src/commonMain/kotlin/App.kt" to """
                val weft = Weft {
                    install(SupabaseClient) {
                        anonKey = "YOUR_SUPABASE_ANON_KEY"
                        url = "https://project.supabase.co"
                    }
                }
            """.trimIndent(),
        )

        assertFalse(analysis.isValid)
        val issue = analysis.credentialIssues.single()
        assertEquals("anonKey", issue.property)
        assertEquals(CredentialIssueKind.PLACEHOLDER, issue.kind)
        assertTrue(WeftReport.offline(analysis).contains("YOUR_SUPABASE_ANON_KEY"))
    }

    @Test
    fun ignoresNonCredentialLiteralsAndInterpolatedValues() {
        val analysis = analyze(
            "src/commonMain/kotlin/App.kt" to """
                val weft = Weft {
                    install(WeftLogin) {
                        title = ""
                        redirectUri = buildRedirectUri()
                        appKey = resolveKey()
                    }
                }
            """.trimIndent(),
        )

        assertTrue(analysis.credentialIssues.isEmpty())
    }

    @Test
    fun ignoresLookupsOnOtherReceivers() {
        val analysis = analyze(
            "src/commonMain/kotlin/Cache.kt" to """
                fun read(map: Map<String, String>) = map.get<String>("key")
            """.trimIndent(),
        )

        assertTrue(analysis.lookups.isEmpty())
        assertTrue(analysis.isValid)
    }

    @Test
    fun collectsLiteralHealthCheckDeclarations() {
        val analysis = analyze(
            "src/commonMain/kotlin/SupabasePlugin.kt" to """
                object SupabaseClient : WeftPlugin<SupabaseConfig, SupabasePlugin> {
                    override val type: KClass<SupabasePlugin> = SupabasePlugin::class

                    override fun healthCheck(plugin: SupabasePlugin): List<WeftHealthCheck> = listOf(
                        WeftHealthCheck(
                            name = "supabase-auth",
                            url = "https://project.supabase.co/auth/v1/health",
                            headers = mapOf("apikey" to "anon-key"),
                        ),
                    )
                }
            """.trimIndent(),
        )

        val check = analysis.healthChecks.single()
        assertEquals("supabase-auth", check.name)
        assertEquals("https://project.supabase.co/auth/v1/health", check.url)
        assertEquals("anon-key", check.headers["apikey"])
        assertEquals("WeftHealthCheck declaration", check.origin)
    }

    @Test
    fun derivesSupabaseProbeFromInstallBlock() {
        val analysis = analyze(
            "src/commonMain/kotlin/App.kt" to """
                val weft = Weft {
                    install(SupabaseClient) {
                        url = "https://project.supabase.co/"
                        anonKey = "anon-key"
                    }
                }
            """.trimIndent(),
        )

        val check = analysis.healthChecks.single()
        assertEquals("supabase-auth", check.name)
        assertEquals("https://project.supabase.co/auth/v1/health", check.url)
        assertEquals("anon-key", check.headers["apikey"])
        assertTrue(check.origin.contains("install(SupabaseClient)"))
    }

    @Test
    fun derivesKakaoProbeFromInstallBlock() {
        val analysis = analyze(
            "src/commonMain/kotlin/App.kt" to """
                val weft = Weft {
                    install(KakaoMap) {
                        restApiKey = "kakao-rest-key"
                    }
                }
            """.trimIndent(),
        )

        val check = analysis.healthChecks.single()
        assertEquals("kakao-local-api", check.name)
        assertEquals("KakaoAK kakao-rest-key", check.headers["Authorization"])
        assertTrue(check.url.contains("dapi.kakao.com"))
    }

    @Test
    fun readsLookupInsideWeftAppContentAndNestedExpressions() {
        val analysis = analyze(
            "src/commonMain/kotlin/App.kt" to """
                fun App(weft: Weft) {
                    WeftApp(weft) {
                        val push = LocalWeft.current.get<PushPlugin>()
                        val login = this.weft.get<LoginPlugin>()
                    }
                }
            """.trimIndent(),
        )

        assertEquals(setOf("PushPlugin", "LoginPlugin"), analysis.missingLookups.map { it.type }.toSet())
    }

    private fun analyze(vararg files: Pair<String, String>): WeftAnalysis {
        val sources = files.map { (path, text) -> KotlinSourceFile(path, text) }
        return WeftPluginAnalyzer(WeftAnalysisOptions()).analyze(sources)
    }
}
