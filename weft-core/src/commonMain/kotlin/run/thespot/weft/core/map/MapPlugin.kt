package run.thespot.weft.core.map

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Provider-neutral map capability exposed through the Weft plugin registry.
 *
 * Provider implementations declare [run.thespot.weft.core.WeftPlugin.type] as
 * [MapPlugin]::class so applications resolve the active provider by this shared contract.
 */
public interface MapPlugin {
    /**
     * Renders a map state and its declarative children.
     */
    @Composable
    public fun Content(
        state: WeftMapState,
        modifier: Modifier = Modifier,
        content: @Composable MapScope.() -> Unit = {},
        onError: ((Throwable) -> Unit)? = null,
    ): Unit
}
