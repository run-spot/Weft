package run.thespot.weft.core

/**
 * A Weft capability that can be installed into a [Weft] instance.
 *
 * The contract has two phases, in the same spirit as Ktor plugins:
 *
 * 1. [prepare] turns the caller's configuration block into a plugin instance. The plugin owns its
 *    configuration type, creates its default configuration, and validates it here.
 * 2. [install] binds the prepared instance into the running [scope]. The hook runs after every
 *    registered plugin has been prepared, so it may resolve other installed plugins through
 *    [Weft.plugin] or [Weft.pluginOrNull]. This is where a plugin publishes itself as the active
 *    implementation of its capability.
 *
 * A plugin declares the [key] it installs under. Implementations in the same capability family
 * share one key, which makes them mutually exclusive: installing a second plugin for the same key
 * fails with [DuplicateWeftPluginException].
 *
 * @param TConfig type of the plugin configuration.
 * @param TPlugin type of the prepared plugin instance stored in the [Weft] registry.
 */
public interface WeftPlugin<TConfig : Any, TPlugin : Any> {
    /**
     * Key this plugin installs under, normally the key of its capability family.
     */
    public val key: WeftPluginKey<TPlugin>

    /**
     * Human-readable plugin name used in diagnostics and duplicate-installation errors.
     */
    public val pluginName: String

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
