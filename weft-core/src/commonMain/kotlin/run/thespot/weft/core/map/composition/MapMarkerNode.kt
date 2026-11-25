package run.thespot.weft.core.map.composition

import run.thespot.weft.core.map.MapMarker

internal class MapMarkerNode(marker: MapMarker) : MapNode() {
    internal var marker: MapMarker = marker
        private set

    internal fun updateMarker(marker: MapMarker) {
        if (this.marker == marker) return

        this.marker = marker
        invalidate()
    }
}
