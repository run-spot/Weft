package run.thespot.weft.map.kakaomap

import androidx.compose.ui.graphics.Color
import run.thespot.weft.core.map.LatLng
import run.thespot.weft.core.map.MapCircle
import run.thespot.weft.core.map.MapPolygon
import run.thespot.weft.core.map.MapPolyline
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KakaoMapOverlayShapesTest {
    @Test
    fun nativeIdsAreKindPrefixedAndHiddenStateAndStylesArePreserved() {
        val point = LatLng(37.0, 127.0)
        val shapes = toKakaoMapOverlayShapes(
            polylines = listOf(MapPolyline("same", listOf(point, LatLng(38.0, 128.0)), color = Color.Red, widthDp = 4f, visible = false)),
            polygons = listOf(MapPolygon("same", listOf(point, LatLng(38.0, 128.0), LatLng(37.5, 128.0)))),
            circles = listOf(MapCircle("same", point, 500.0)),
        )

        assertEquals(listOf("weft:polyline:same", "weft:polygon:same", "weft:circle:same"), shapes.map { it.nativeId })
        assertEquals("weft:marker:same", kakaoMapNativeElementId(KakaoMapNativeElementKind.Marker, "same"))
        assertEquals(KakaoMapOverlayKind.Polyline, shapes[0].kind)
        assertEquals(Color.Red, shapes[0].strokeColor)
        assertEquals(4f, shapes[0].strokeWidthDp)
        assertFalse(shapes[0].visible)
        assertEquals(KakaoMapOverlayKind.Polygon, shapes[1].kind)
        assertEquals(KakaoMapOverlayKind.Polygon, shapes[2].kind)
        assertTrue(shapes[2].isCircleApproximation)
    }

    @Test
    fun circleProjectionHasDeterministicSixtyFourPointRingAtRequestedDistance() {
        val center = LatLng(0.0, 0.0)
        val circle = MapCircle("radius", center, 1_000.0)
        val projected = projectKakaoMapCircle(circle)

        assertEquals(65, projected.size)
        assertEquals(projected.first(), projected.last())
        val firstDistance = distanceMeters(center, projected[0])
        assertTrue(firstDistance in 999.0..1001.0, "Expected ~1000m, got $firstDistance")
    }
}
