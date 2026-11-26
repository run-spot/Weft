package run.thespot.weft.core.map

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MapModelsTest {
    @Test
    fun latLngAcceptsFiniteCoordinatesWithinBounds() {
        assertEquals(LatLng(-90.0, -180.0), LatLng(-90.0, -180.0))
        assertEquals(LatLng(90.0, 180.0), LatLng(90.0, 180.0))
    }

    @Test
    fun latLngRejectsNonFiniteAndOutOfRangeCoordinates() {
        assertFailsWith<IllegalArgumentException> {
            LatLng(Double.NaN, 0.0)
        }.also { assertEquals("latitude must be finite and between -90 and 90", it.message) }
        assertFailsWith<IllegalArgumentException> {
            LatLng(91.0, 0.0)
        }.also { assertEquals("latitude must be finite and between -90 and 90", it.message) }
        assertFailsWith<IllegalArgumentException> {
            LatLng(0.0, Double.POSITIVE_INFINITY)
        }.also { assertEquals("longitude must be finite and between -180 and 180", it.message) }
        assertFailsWith<IllegalArgumentException> {
            LatLng(0.0, -181.0)
        }.also { assertEquals("longitude must be finite and between -180 and 180", it.message) }
    }

    @Test
    fun cameraPositionValidatesZoomBearingAndTilt() {
        val target = LatLng(37.5665, 126.9780)
        assertEquals(15f, CameraPosition(target).zoom)

        assertFailsWith<IllegalArgumentException> {
            CameraPosition(target, zoom = -0.1f)
        }.also { assertEquals("zoom must be finite and non-negative", it.message) }
        assertFailsWith<IllegalArgumentException> {
            CameraPosition(target, zoom = Float.POSITIVE_INFINITY)
        }.also { assertEquals("zoom must be finite and non-negative", it.message) }
        assertFailsWith<IllegalArgumentException> {
            CameraPosition(target, bearing = Float.NaN)
        }.also { assertEquals("bearing must be finite", it.message) }
        assertFailsWith<IllegalArgumentException> {
            CameraPosition(target, tilt = 90.1f)
        }.also { assertEquals("tilt must be finite and between 0 and 90", it.message) }
    }

    @Test
    fun markerIdMustNotBeBlank() {
        assertFailsWith<IllegalArgumentException> {
            MapMarker(id = "   ", position = LatLng(0.0, 0.0))
        }.also { assertEquals("marker id must not be blank", it.message) }
    }

    @Test
    fun mapStateExposesAnImmutableCameraPosition() {
        val camera = CameraPosition(LatLng(0.0, 0.0))
        val state = WeftMapState(cameraPosition = camera)

        assertEquals(camera, state.cameraPosition)
    }

    @Test
    fun mapContentCopiesTheMarkerSnapshot() {
        val markers = mutableListOf(MapMarker(id = "one", position = LatLng(0.0, 0.0)))
        val content = MapContent(markers)

        markers += MapMarker(id = "two", position = LatLng(1.0, 1.0))

        assertEquals(listOf(MapMarker(id = "one", position = LatLng(0.0, 0.0))), content.markers)
    }

    @Test
    fun polylineValidatesIdentityGeometryAndWidthAndCopiesPoints() {
        val input = mutableListOf(LatLng(0.0, 0.0), LatLng(1.0, 1.0))
        val polyline = MapPolyline(id = "route", points = input, widthDp = 0f)

        input += LatLng(2.0, 2.0)

        assertEquals(2, polyline.points.size)
        assertFailsWith<IllegalArgumentException> { MapPolyline(" ", input) }
        assertFailsWith<IllegalArgumentException> { MapPolyline("short", listOf(input.first())) }
        assertFailsWith<IllegalArgumentException> { MapPolyline("bad-width", input, widthDp = -1f) }
        assertFailsWith<IllegalArgumentException> { MapPolyline("nan-width", input, widthDp = Float.NaN) }
        assertFailsWith<IllegalArgumentException> {
            MapPolyline("infinite-width", input, widthDp = Float.POSITIVE_INFINITY)
        }
    }

    @Test
    fun polygonValidatesIdentityGeometryAndWidthsAndCopiesPoints() {
        val input = mutableListOf(LatLng(0.0, 0.0), LatLng(1.0, 1.0), LatLng(2.0, 0.0))
        val polygon = MapPolygon(id = "area", points = input, strokeWidthDp = 0f)

        input.clear()

        assertEquals(3, polygon.points.size)
        assertFailsWith<IllegalArgumentException> { MapPolygon(" ", emptyList()) }
        assertFailsWith<IllegalArgumentException> {
            MapPolygon("short", listOf(LatLng(0.0, 0.0), LatLng(1.0, 1.0)))
        }
        assertFailsWith<IllegalArgumentException> {
            MapPolygon("bad-stroke", polygon.points, strokeWidthDp = -1f)
        }
        assertFailsWith<IllegalArgumentException> {
            MapPolygon("nan-stroke", polygon.points, strokeWidthDp = Float.NaN)
        }
        assertFailsWith<IllegalArgumentException> {
            MapPolygon("infinite-stroke", polygon.points, strokeWidthDp = Float.POSITIVE_INFINITY)
        }
    }

    @Test
    fun circleValidatesIdentityRadiusAndStrokeWidth() {
        val center = LatLng(0.0, 0.0)
        assertEquals(center, MapCircle("circle", center, 0.1).center)
        listOf(Double.NaN, Double.POSITIVE_INFINITY, 0.0, -1.0).forEach { radius ->
            assertFailsWith<IllegalArgumentException> { MapCircle("bad-radius", center, radius) }
        }
        assertFailsWith<IllegalArgumentException> { MapCircle(" ", center, 1.0) }
        assertFailsWith<IllegalArgumentException> { MapCircle("bad-stroke", center, 1.0, strokeWidthDp = -1f) }
        assertFailsWith<IllegalArgumentException> { MapCircle("nan-stroke", center, 1.0, strokeWidthDp = Float.NaN) }
    }

    @Test
    fun mapContentCopiesAllOverlaySnapshots() {
        val polyline = MapPolyline("route", listOf(LatLng(0.0, 0.0), LatLng(1.0, 1.0)))
        val polygon = MapPolygon("area", listOf(LatLng(0.0, 0.0), LatLng(1.0, 0.0), LatLng(0.0, 1.0)))
        val circle = MapCircle("radius", LatLng(0.0, 0.0), 100.0)
        val polylines = mutableListOf(polyline)
        val polygons = mutableListOf(polygon)
        val circles = mutableListOf(circle)

        val content = MapContent(emptyList(), polylines, polygons, circles)
        polylines.clear()
        polygons.clear()
        circles.clear()

        assertEquals(listOf(polyline), content.polylines)
        assertEquals(listOf(polygon), content.polygons)
        assertEquals(listOf(circle), content.circles)
    }

    @Test
    fun mapPluginExposesTheProviderNeutralContentSignature() {
        val plugin: MapPlugin = object : MapPlugin {
            @Composable
            override fun Content(
                state: WeftMapState,
                modifier: Modifier,
                content: @Composable MapScope.() -> Unit,
                onError: ((Throwable) -> Unit)?,
            ) = Unit
        }
    }
}

@Composable
private fun renderMapPluginWithDefaults(plugin: MapPlugin) {
    plugin.Content(WeftMapState(CameraPosition(LatLng(0.0, 0.0))))
}
