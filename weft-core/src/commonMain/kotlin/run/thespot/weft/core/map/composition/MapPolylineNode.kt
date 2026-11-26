package run.thespot.weft.core.map.composition

import run.thespot.weft.core.map.MapPolyline

internal class MapPolylineNode(polyline: MapPolyline) : MapNode() {
    internal var polyline: MapPolyline = polyline
        private set

    internal fun updatePolyline(polyline: MapPolyline) {
        if (this.polyline == polyline) return

        this.polyline = polyline
        invalidate()
    }
}
