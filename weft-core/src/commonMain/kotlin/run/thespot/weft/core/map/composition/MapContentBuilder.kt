package run.thespot.weft.core.map.composition

import run.thespot.weft.core.map.MapContent
import run.thespot.weft.core.map.MapCircle
import run.thespot.weft.core.map.MapMarker
import run.thespot.weft.core.map.MapPolygon
import run.thespot.weft.core.map.MapPolyline

internal object MapContentBuilder {
    internal fun build(root: MapRootNode): MapContent {
        val markers = mutableListOf<MapMarker>()
        val markerIds = mutableSetOf<String>()
        val polylines = mutableListOf<MapPolyline>()
        val polylineIds = mutableSetOf<String>()
        val polygons = mutableListOf<MapPolygon>()
        val polygonIds = mutableSetOf<String>()
        val circles = mutableListOf<MapCircle>()
        val circleIds = mutableSetOf<String>()

        fun collect(node: MapNode) {
            if (node is MapMarkerNode) {
                val marker = node.marker
                require(markerIds.add(marker.id)) {
                    "duplicate marker id: ${marker.id}"
                }
                markers += marker
            }
            if (node is MapPolylineNode) {
                val polyline = node.polyline
                require(polylineIds.add(polyline.id)) {
                    "duplicate polyline id: ${polyline.id}"
                }
                polylines += polyline
            }
            if (node is MapPolygonNode) {
                val polygon = node.polygon
                require(polygonIds.add(polygon.id)) {
                    "duplicate polygon id: ${polygon.id}"
                }
                polygons += polygon
            }
            if (node is MapCircleNode) {
                val circle = node.circle
                require(circleIds.add(circle.id)) {
                    "duplicate circle id: ${circle.id}"
                }
                circles += circle
            }
            node.children.forEach(::collect)
        }

        root.children.forEach(::collect)
        return MapContent(markers, polylines, polygons, circles)
    }
}
