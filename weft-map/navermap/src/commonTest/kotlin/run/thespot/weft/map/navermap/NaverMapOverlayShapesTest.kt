package run.thespot.weft.map.navermap

import androidx.compose.ui.graphics.Color
import run.thespot.weft.core.map.LatLng
import run.thespot.weft.core.map.MapCircle
import run.thespot.weft.core.map.MapMarker
import run.thespot.weft.core.map.MapPolygon
import run.thespot.weft.core.map.MapPolyline
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class NaverMapOverlayShapesTest {
    @Test
    fun usesStableKindPrefixedIdsAndDeterministicKindOrder() {
        val point = LatLng(37.0, 127.0)
        val shapes = toNaverMapOverlayShapes(
            markers = listOf(MapMarker("marker", point)),
            polylines = listOf(MapPolyline("line", listOf(point, LatLng(37.1, 127.1)))),
            polygons = listOf(MapPolygon("area", listOf(point, LatLng(37.1, 127.1), LatLng(37.2, 127.0)))),
            circles = listOf(MapCircle("radius", point, 250.0)),
        )

        assertEquals(
            listOf(
                "weft:marker:marker",
                "weft:polyline:line",
                "weft:polygon:area",
                "weft:circle:radius",
            ),
            shapes.map { it.nativeId },
        )
        assertEquals(
            listOf(
                NaverMapOverlayKind.Marker,
                NaverMapOverlayKind.Polyline,
                NaverMapOverlayKind.Polygon,
                NaverMapOverlayKind.Circle,
            ),
            shapes.map { it.kind },
        )
    }

    @Test
    fun mapsMarkersAndNaverCircleWithoutPolygonApproximation() {
        val markerPosition = LatLng(37.5, 127.5)
        val circleCenter = LatLng(38.0, 128.0)
        val marker = toNaverMapOverlayShapes(
            markers = listOf(MapMarker("pin", markerPosition, "Title", "Snippet")),
            polylines = emptyList(),
            polygons = emptyList(),
            circles = emptyList(),
        ).single()
        val circle = toNaverMapOverlayShapes(
            markers = emptyList(),
            polylines = emptyList(),
            polygons = emptyList(),
            circles = listOf(MapCircle("circle", circleCenter, 1250.0)),
        ).single()

        assertEquals(markerPosition, marker.position)
        assertEquals("Title", marker.title)
        assertEquals("Snippet", marker.snippet)
        assertEquals(0f, marker.zIndex)
        assertEquals(true, marker.visible)
        assertEquals(circleCenter, circle.center)
        assertEquals(1250.0, circle.radiusMeters)
        assertEquals(emptyList(), circle.points)
    }

    @Test
    fun keepsStyleVisibilityAndZIndex() {
        val point = LatLng(37.0, 127.0)
        val polyline = MapPolyline(
            id = "line",
            points = listOf(point, LatLng(37.1, 127.1)),
            color = Color.Red,
            widthDp = 4f,
            zIndex = 3.5f,
            visible = false,
        )
        val polygon = MapPolygon(
            id = "area",
            points = listOf(point, LatLng(37.1, 127.1), LatLng(37.2, 127.0)),
            fillColor = Color.Blue,
            strokeColor = Color.Green,
            strokeWidthDp = 7f,
            zIndex = 4.5f,
            visible = false,
        )

        val shapes = toNaverMapOverlayShapes(
            markers = listOf(MapMarker("pin", point)),
            polylines = listOf(polyline),
            polygons = listOf(polygon),
            circles = listOf(MapCircle("circle", point, 100.0, visible = false, zIndex = 5.5f)),
        )

        assertEquals(point, shapes[0].position)
        assertEquals(3.5f, shapes[1].zIndex)
        assertEquals(Color.Red, shapes[1].strokeColor)
        assertEquals(4f, shapes[1].strokeWidthDp)
        assertFalse(shapes[1].visible)
        assertEquals(Color.Blue, shapes[2].fillColor)
        assertEquals(Color.Green, shapes[2].strokeColor)
        assertEquals(7f, shapes[2].strokeWidthDp)
        assertEquals(4.5f, shapes[2].zIndex)
        assertFalse(shapes[2].visible)
        assertEquals(5.5f, shapes[3].zIndex)
        assertFalse(shapes[3].visible)
    }
}
