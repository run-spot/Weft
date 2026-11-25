package run.thespot.weft.core.map

/**
 * DSL marker for declarative map content.
 */
@DslMarker
public annotation class WeftMapDsl

/**
 * Receiver for declarative map content.
 */
@WeftMapDsl
public object MapScope
