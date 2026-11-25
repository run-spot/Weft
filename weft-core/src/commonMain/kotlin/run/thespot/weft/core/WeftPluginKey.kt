package run.thespot.weft.core

/**
 * Identity of a Weft plugin or of a shared capability family.
 *
 * A key is the address a plugin is stored under, and consumers use the same key to resolve the
 * active implementation of a capability. Two keys with the same [name] are equal, so separate
 * artifacts can declare the key of one family independently and still address the same slot. For
 * example, a Naver map artifact and a Kakao map artifact may each declare
 * `WeftPluginKey<MapPlugin>("weft.map")`; installing either one makes the `weft.map` capability
 * resolve to that implementation.
 *
 * Because equality is based on [name] alone, the name must be owned by exactly one capability
 * family or plugin. Use a stable, namespaced name such as `weft.map` or `weft.login`, and declare it
 * once per family as a public constant so that every implementation shares it. The runtime cannot
 * check the generic type; install or resolve a key with a value of the type the key was declared
 * with.
 *
 * @param TPlugin type of the plugin instance stored under this key.
 * @property name stable identifier of the plugin or capability family.
 */
public class WeftPluginKey<TPlugin : Any>(public val name: String) {
    override fun equals(other: Any?): Boolean =
        this === other || (other is WeftPluginKey<*> && other.name == name)

    override fun hashCode(): Int = name.hashCode()

    override fun toString(): String = "WeftPluginKey($name)"
}
