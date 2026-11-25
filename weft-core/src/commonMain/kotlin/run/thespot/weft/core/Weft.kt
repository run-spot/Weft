package run.thespot.weft.core

import kotlin.reflect.KClass

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
 *
 * val map = weft.get<MapPlugin>()
 * ```
 *
 * Registration is declarative: [install] applies the configuration block immediately and records
 * the prepared instance. Every plugin's [WeftPlugin.install] hook runs when the [Weft] instance is
 * built, after the registry is complete.
 */
public class WeftConfig internal constructor() {
    private val pendingInstalls: MutableList<PendingInstall> = mutableListOf()

    /**
     * Registers [plugin] under its [type][WeftPlugin.type] and applies [configure] to its
     * configuration.
     *
     * @throws DuplicateWeftPluginException when an earlier plugin in this configuration already
     * occupies the same plugin type. Plugins that belong to one capability family declare the same
     * plugin type, so only one implementation of a capability can be installed.
     */
    public fun <TConfig : Any, TPlugin : Any> install(
        plugin: WeftPlugin<TConfig, TPlugin>,
        configure: TConfig.() -> Unit = {},
    ) {
        val existing = pendingInstalls.firstOrNull { it.type == plugin.type }
        if (existing != null) {
            throw DuplicateWeftPluginException(
                type = plugin.type,
                existingPluginName = existing.pluginName,
                newPluginName = plugin.pluginName,
            )
        }

        val instance = plugin.prepare(configure)
        pendingInstalls += PendingInstall(
            type = plugin.type,
            pluginName = plugin.pluginName,
            instance = instance,
            install = { weft -> plugin.install(instance, weft) },
        )
    }

    internal fun createWeft(): Weft {
        val weft = Weft(
            installedPlugins = pendingInstalls.associate { pending ->
                pending.type to InstalledPlugin(pending.instance)
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
 * Plugins are stored under their [plugin type][WeftPlugin.type]. Consumers resolve the active
 * implementation of a capability with that type, which keeps a view independent of the concrete
 * provider that was installed.
 */
public class Weft internal constructor(
    private val installedPlugins: Map<KClass<*>, InstalledPlugin>,
) {
    /**
     * Returns the plugin installed for [TPlugin], normally a capability interface such as
     * `MapPlugin`.
     *
     * @throws MissingWeftPluginException when nothing is installed for [TPlugin].
     */
    public inline fun <reified TPlugin : Any> get(): TPlugin = get(TPlugin::class)

    /**
     * Returns the plugin installed for [type].
     *
     * @throws MissingWeftPluginException when nothing is installed for [type].
     */
    public fun <TPlugin : Any> get(type: KClass<TPlugin>): TPlugin =
        getOrNull(type) ?: throw MissingWeftPluginException(type)

    /**
     * Returns the plugin installed for [TPlugin], or `null` when nothing is installed for it.
     */
    public inline fun <reified TPlugin : Any> getOrNull(): TPlugin? = getOrNull(TPlugin::class)

    /**
     * Returns the plugin installed for [type], or `null` when nothing is installed for it.
     */
    @Suppress("UNCHECKED_CAST")
    public fun <TPlugin : Any> getOrNull(type: KClass<TPlugin>): TPlugin? =
        installedPlugins[type]?.instance as TPlugin?
}

internal class InstalledPlugin(
    val instance: Any,
)

private class PendingInstall(
    val type: KClass<*>,
    val pluginName: String,
    val instance: Any,
    val install: (Weft) -> Unit,
)
