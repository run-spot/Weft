package run.thespot.weft.core

/**
 * One HTTP probe that shows a configured capability is reachable with the credential the app
 * installed.
 *
 * Capability libraries own the endpoints their provider needs: a map plugin knows which host accepts
 * its REST key, and a Supabase plugin knows its project URL and `apikey`. Each plugin reports its
 * probes from [WeftPlugin.healthCheck], the runtime collects them with [Weft.healthChecks], and a
 * build or admin tool runs them together instead of hard-coding vendor URLs in a generic checker.
 *
 * ```kotlin
 * override fun healthCheck(plugin: SupabasePlugin): List<WeftHealthCheck> = listOf(
 *     WeftHealthCheck(
 *         name = "supabase-auth",
 *         url = "${plugin.config.url}/auth/v1/health",
 *         headers = mapOf("apikey" to plugin.config.anonKey),
 *     ),
 * )
 * ```
 *
 * A probe is a plain description: [Weft] never performs the request, so a library stays free of
 * networking, and the caller decides timeout, retries, and how to report a failure.
 *
 * @param name stable label used in reports, normally `<capability>-<purpose>`.
 * @param url absolute HTTP(S) URL that answers without a request body.
 * @param headers request headers the endpoint requires, typically the credential being verified.
 * @param method HTTP method; `GET` unless an endpoint only answers another verb.
 */
public data class WeftHealthCheck(
    public val name: String,
    public val url: String,
    public val headers: Map<String, String> = emptyMap(),
    public val method: String = "GET",
)
