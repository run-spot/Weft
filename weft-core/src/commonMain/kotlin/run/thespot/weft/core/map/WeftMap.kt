package run.thespot.weft.core.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import run.thespot.weft.core.LocalWeft

/**
 * Hosts the installed provider's map surface and its declarative marker content.
 */
@Composable
public fun WeftMap(
    state: WeftMapState,
    modifier: Modifier = Modifier,
    content: @Composable MapScope.() -> Unit = {},
    onError: ((Throwable) -> Unit)? = null,
): Unit {
    Box(modifier) {
        LocalWeft.current.get<MapPlugin>().Content(
            state = state,
            modifier = Modifier.fillMaxSize(),
            content = content,
            onError = onError,
        )
    }
}
