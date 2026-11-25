package run.thespot.weft.core

/**
 * Collects plugin installations and produces a configured [Weft] instance.
 *
 * Callers configure a runtime the same way a Ktor application installs plugins:
 *
 * ```kotlin
 * val weft = Weft {
 *     install(NaverMap) {
 *         appKey = "naver-app-key"
 *     }
 *     install(WeftLogin) {
 *         redirectUri = "app://callback"
 *     }
 * }
 * ```
 *
 * Registration is declarative: [install] applies the configuration block immediately and records
 * the prepared instance. Every plugin's [WeftPlugin.install] hook runs when the [Weft] instance is
 * built, after the registry is complete.
 */
public class WeftConfig internal constructor() {
    private val pendingInstalls: MutableList<PendingInstall> = mutableListOf()

    /**
     * Registers [plugin] under its [key][WeftPlugin.key] and applies [configure] to its
     * configuration.
     *
     * @throws DuplicateWeftPluginException when an earlier plugin in this configuration already
     * occupies the same key. Plugins that belong to one capability family share a key, so only one
     * implementation of a capability can be installed.
     */
    public fun <TConfig : Any, TPlugin : Any> install(
        plugin: WeftPlugin<TConfig, TPlugin>,
        configure: TConfig.() -> Unit = {},
    ) {
        val existing = pendingInstalls.firstOrNull { it.key == plugin.key }
        if (existing != null) {
            throw DuplicateWeftPluginException(
                key = plugin.key,
                existingPluginName = existing.pluginName,
                newPluginName = plugin.pluginName,
            )
        }

        val instance = plugin.prepare(configure)
        pendingInstalls += PendingInstall(
            key = plugin.key,
            pluginName = plugin.pluginName,
            instance = instance,
            install = { weft -> plugin.install(instance, weft) },
        )
    }

    internal fun createWeft(): Weft {
        val weft = Weft(
            installedPlugins = pendingInstalls.associate { pending ->
                pending.key to InstalledPlugin(pending.instance)
            },
        )
        pendingInstalls.forEach { it.install(weft) }
        return weft
    }
}

/**
 * Creates and configures a [Weft] runtime.
 *
 * Configuration is validated while the instance is built, so a duplicate plugin in one capability
 * family fails here instead of at first use.
 */
public fun Weft(block: WeftConfig.() -> Unit): Weft = WeftConfig().apply(block).createWeft()

/**
 * Runtime registry of installed Weft plugins.
 *
 * Plugins are stored under their [WeftPluginKey]. Consumers resolve the active implementation of a
 * capability with the capability key, which keeps a view independent of the concrete provider that
 * was installed.
 */
public class Weft internal constructor(
    private val installedPlugins: Map<WeftPluginKey<*>, InstalledPlugin>,
) {
    /**
     * Returns the plugin installed for [key].
     *
     * @throws MissingWeftPluginException when nothing is installed for [key].
     */
    public fun <TPlugin : Any> plugin(key: WeftPluginKey<TPlugin>): TPlugin =
        pluginOrNull(key) ?: throw MissingWeftPluginException(key)

    /**
     * Returns the plugin installed for [key], or `null` when nothing is installed for it.
     */
    @Suppress("UNCHECKED_CAST")
    public fun <TPlugin : Any> pluginOrNull(key: WeftPluginKey<TPlugin>): TPlugin? =
        installedPlugins[key]?.instance as TPlugin?

    /**
     * Returns the instance installed for [plugin]'s key.
     *
     * @throws MissingWeftPluginException when [plugin] is not installed.
     */
    public fun <TConfig : Any, TPlugin : Any> plugin(plugin: WeftPlugin<TConfig, TPlugin>): TPlugin =
        plugin(plugin.key)

    /**
     * Returns the instance installed for [plugin]'s key, or `null` when [plugin] is not installed.
     */
    public fun <TConfig : Any, TPlugin : Any> pluginOrNull(
        plugin: WeftPlugin<TConfig, TPlugin>,
    ): TPlugin? = pluginOrNull(plugin.key)
}

internal class InstalledPlugin(
    val instance: Any,
)

private class PendingInstall(
    val key: WeftPluginKey<*>,
    val pluginName: String,
    val instance: Any,
    val install: (Weft) -> Unit,
)
