package run.thespot.weft.core

import kotlin.reflect.KClass

/**
 * Thrown when a plugin is installed for a [type][WeftPlugin.type] that already holds a plugin.
 *
 * Plugins of one capability family share the same plugin type, so this error marks a configuration
 * that would have two active implementations of one capability, such as two map providers.
 */
public class DuplicateWeftPluginException(
    /** Plugin type, and therefore capability family, whose slot is already occupied. */
    public val type: KClass<*>,
    /** Name of the plugin that was installed first. */
    public val existingPluginName: String,
    /** Name of the plugin that was rejected. */
    public val newPluginName: String,
) : IllegalStateException(
    "Weft plugin '$newPluginName' cannot be installed for plugin type '${type.weftTypeName()}': " +
        "'$existingPluginName' is already installed for this plugin type. " +
        "Install only one plugin per plugin type.",
)

/**
 * Thrown when [Weft.get] is asked for a plugin type that has no installed plugin.
 *
 * Use [Weft.getOrNull] when an optional capability may be absent.
 */
public class MissingWeftPluginException(
    /** Plugin type that has no installed plugin. */
    public val type: KClass<*>,
) : IllegalStateException(
    "No Weft plugin is installed for plugin type '${type.weftTypeName()}'. " +
        "Install the plugin with Weft { install(...) } before resolving it.",
)

/**
 * Name of [type] in diagnostic messages, falling back to the qualified name when the platform does
 * not expose a simple name.
 */
internal fun KClass<*>.weftTypeName(): String = simpleName ?: toString()
