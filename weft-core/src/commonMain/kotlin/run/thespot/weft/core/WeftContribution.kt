package run.thespot.weft.core

/**
 * Adds the plugin registrations owned by one capability module to a [Weft] runtime.
 *
 * A contribution should configure only the capabilities that its module provides. Applications
 * can collect contributions from a dependency-injection container, then pass the collection to
 * [Weft]. The core contract does not depend on a particular container.
 */
public fun interface WeftContribution {
    /** Registers this module's plugin or plugins in [config]. */
    public fun install(config: WeftConfig)
}
