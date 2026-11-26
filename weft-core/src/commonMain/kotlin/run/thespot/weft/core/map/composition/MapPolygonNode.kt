package run.thespot.weft.core.map.composition

import run.thespot.weft.core.map.MapPolygon

internal class MapPolygonNode(polygon: MapPolygon) : MapNode() {
    internal var polygon: MapPolygon = polygon
        private set

    internal fun updatePolygon(polygon: MapPolygon) {
        if (this.polygon == polygon) return

        this.polygon = polygon
        invalidate()
    }
}
