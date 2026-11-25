package run.thespot.weft.core.map.composition

import run.thespot.weft.core.map.MapContent
import run.thespot.weft.core.map.MapMarker

internal object MapContentBuilder {
    internal fun build(root: MapRootNode): MapContent {
        val markers = mutableListOf<MapMarker>()
        val markerIds = mutableSetOf<String>()

        fun collect(node: MapNode) {
            if (node is MapMarkerNode) {
                val marker = node.marker
                require(markerIds.add(marker.id)) {
                    "duplicate marker id: ${marker.id}"
                }
                markers += marker
            }
            node.children.forEach(::collect)
        }

        root.children.forEach(::collect)
        return MapContent(markers)
    }
}
