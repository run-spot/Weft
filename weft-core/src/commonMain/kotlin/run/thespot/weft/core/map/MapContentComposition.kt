package run.thespot.weft.core.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composition
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import run.thespot.weft.core.map.composition.MapApplier
import run.thespot.weft.core.map.composition.MapContentBuilder
import run.thespot.weft.core.map.composition.MapRootNode

/**
 * Materializes the declarative map children into a provider-neutral snapshot. Provider modules use
 * this bridge while the marker tree and applier remain private to `:weft-core`.
 */
@Composable
public fun rememberMapContent(
    content: @Composable MapScope.() -> Unit,
): MapContent {
    val parentComposition = rememberCompositionContext()
    val currentContent = remember {
        mutableStateOf(content, policy = neverEqualPolicy())
    }
    val root = remember { MapRootNode() }
    val state = remember(root) { MapContentState(root) }
    val applier = remember(root) { MapApplier(root) { state.invalidate() } }
    val composition = remember(parentComposition, applier) {
        Composition(applier, parentComposition)
    }

    DisposableEffect(composition) {
        onDispose {
            state.dispose()
            composition.dispose()
        }
    }
    SideEffect { currentContent.value = content }
    LaunchedEffect(composition) {
        composition.setContent {
            state.markDirty()
            state.invalidationToken
            currentContent.value.invoke(MapScope)
            SideEffect { state.publishIfDirty() }
        }
    }

    return state.content
}

internal class MapContentState(
    private val root: MapRootNode,
) {
    private val published = mutableStateOf(
        MapContent(emptyList()),
        policy = neverEqualPolicy(),
    )
    private val observedInvalidationToken = mutableStateOf(0)
    private var dirty = true
    private var disposed = false

    internal val content: MapContent
        get() = published.value

    internal val invalidationToken: Int
        get() = observedInvalidationToken.value

    internal fun markDirty() {
        if (!disposed) dirty = true
    }

    internal fun invalidate() {
        if (disposed) return
        dirty = true
        observedInvalidationToken.value++
    }

    internal fun publishIfDirty() {
        if (disposed || !dirty) return

        val next = MapContentBuilder.build(root)
        dirty = false
        if (
            next.markers != published.value.markers ||
            next.polylines != published.value.polylines ||
            next.polygons != published.value.polygons ||
            next.circles != published.value.circles
        ) {
            published.value = next
        }
    }

    internal fun dispose() {
        disposed = true
    }
}
