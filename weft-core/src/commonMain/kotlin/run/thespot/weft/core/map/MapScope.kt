package run.thespot.weft.core.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ComposeNode
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.key
import run.thespot.weft.core.map.composition.MapApplier
import run.thespot.weft.core.map.composition.MapMarkerNode

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

// Avoid a restart group around key(marker.id), so reordered markers retain their node identity.
@Composable
@NonRestartableComposable
public fun MapScope.Marker(marker: MapMarker) {
    key(marker.id) {
        ComposeNode<MapMarkerNode, MapApplier>(
            factory = { MapMarkerNode(marker) },
        ) {
            set(marker) { updateMarker(it) }
        }
    }
}
