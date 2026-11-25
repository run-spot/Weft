package run.thespot.weft.core

/**
 * Thrown when a plugin is installed for a [key][WeftPluginKey] that already holds a plugin.
 *
 * Plugins of one capability family share the same key, so this error marks a configuration that
 * would have two active implementations of one capability, such as two map providers.
 */
public class DuplicateWeftPluginException(
    /** Key, and therefore capability family, whose slot is already occupied. */
    public val key: WeftPluginKey<*>,
    /** Name of the plugin that was installed first. */
    public val existingPluginName: String,
    /** Name of the plugin that was rejected. */
    public val newPluginName: String,
) : IllegalStateException(
    "Weft plugin '$newPluginName' cannot be installed for key '${key.name}': " +
        "'$existingPluginName' is already installed for this plugin family. " +
        "Install only one plugin per key.",
)

/**
 * Thrown when [Weft.plugin] is asked for a key that has no installed plugin.
 *
 * Use [Weft.pluginOrNull] when an optional capability may be absent.
 */
public class MissingWeftPluginException(
    /** Key that has no installed plugin. */
    public val key: WeftPluginKey<*>,
) : IllegalStateException(
    "No Weft plugin is installed for key '${key.name}'. " +
        "Install the plugin with Weft { install(...) } before resolving it.",
)
