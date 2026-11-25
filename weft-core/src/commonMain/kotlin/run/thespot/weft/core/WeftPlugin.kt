package run.thespot.weft.core

import kotlin.reflect.KClass

/**
 * A Weft capability that can be installed into a [Weft] instance.
 *
 * The contract has two phases, in the same spirit as Ktor plugins:
 *
 * 1. [prepare] turns the caller's configuration block into a plugin instance. The plugin owns its
 *    configuration type, creates its default configuration, and validates it here.
 * 2. [install] binds the prepared instance into the running [scope]. The hook runs after every
 *    registered plugin has been prepared, so it may resolve other installed plugins through
 *    [Weft.get] or [Weft.getOrNull]. This is where a plugin publishes itself as the active
 *    implementation of its capability.
 *
 * A plugin declares the [type] it installs under. Implementations in the same capability family
 * declare the same type, which makes them mutually exclusive: installing a second plugin for the
 * same type fails with [DuplicateWeftPluginException].
 *
 * @param TConfig type of the plugin configuration.
 * @param TPlugin type of the prepared plugin instance stored in the [Weft] registry.
 */
public interface WeftPlugin<TConfig : Any, TPlugin : Any> {
    /**
     * Type this plugin installs under, normally the capability interface shared by every
     * implementation of that capability.
     *
     * Declare the shared interface rather than the provider class, for example
     * `override val type: KClass<MapPlugin> = MapPlugin::class`, so that consumers resolve the
     * capability with `weft.get<MapPlugin>()` whichever provider was installed.
     */
    public val type: KClass<TPlugin>

    /**
     * Human-readable plugin name used in diagnostics and duplicate-installation errors.
     */
    public val pluginName: String

    /**
     * Probes that verify the credential this plugin was configured with, reported to build and
     * admin tools through [Weft.healthChecks].
     *
     * A capability library owns the endpoint its provider answers, so the check lives here rather
     * than in a generic verifier that would have to know every vendor URL. Return an empty list when
     * the plugin needs no remote credential, which is the default.
     *
     * The hook runs while [plugin] is registered, after [prepare] produced it, so it may read the
     * same configuration the plugin received. It must not perform network I/O: it describes the
     * probe, and the caller runs it.
     */
    public fun healthCheck(plugin: TPlugin): List<WeftHealthCheck> = emptyList()

    /**
     * Creates and configures the plugin instance from [block].
     *
     * The implementation creates its default configuration, applies [block], validates the result,
     * and returns the instance that [install] receives.
     */
    public fun prepare(block: TConfig.() -> Unit): TPlugin

    /**
     * Binds [plugin] into the created [scope].
     *
     * Called once per installation, after all plugins have been prepared and the registry is
     * complete.
     */
    public fun install(plugin: TPlugin, scope: Weft)
}
