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
