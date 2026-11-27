package run.thespot.weft.core.map

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** A provider's composable renderer for the shared map contract. */
public typealias MapContentRenderer = @Composable (
    state: WeftMapState,
    modifier: Modifier,
    content: @Composable MapScope.() -> Unit,
    onError: ((Throwable) -> Unit)?,
) -> Unit

/**
 * Provider-neutral map capability exposed through the Weft plugin registry.
 *
 * Provider implementations declare [run.thespot.weft.core.WeftPlugin.type] as
 * [MapPlugin]::class so applications resolve the active provider by this shared contract.
 */
public interface MapPlugin {
    /** Renderer supplied by the active provider. */
    public val renderer: MapContentRenderer
}
