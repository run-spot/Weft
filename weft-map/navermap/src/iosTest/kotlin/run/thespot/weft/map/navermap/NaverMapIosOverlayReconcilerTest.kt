package run.thespot.weft.map.navermap

import androidx.compose.ui.graphics.Color
import run.thespot.weft.core.map.LatLng
import run.thespot.weft.core.map.MapCircle
import run.thespot.weft.core.map.MapMarker
import run.thespot.weft.core.map.MapPolygon
import run.thespot.weft.core.map.MapPolyline
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertFailsWith

class NaverMapIosOverlayReconcilerTest {
    @Test
    fun addsUpdatesAndRemovesOverlaysByStableIdWhileKeepingNativeIdentity() {
        val layer = FakeLayer()
        val reconciler = NaverMapIosOverlayReconciler(layer)
        val first = toNaverMapOverlayShapes(
            markers = listOf(MapMarker("pin", LatLng(37.5, 127.0))),
            polylines = emptyList(),
            polygons = emptyList(),
            circles = emptyList(),
        )
        reconciler.render(first)
        val nativeHandle = layer.handles.getValue("weft:marker:pin")

        val updated = toNaverMapOverlayShapes(
            markers = listOf(MapMarker("pin", LatLng(37.6, 127.1))),
            polylines = emptyList(),
            polygons = emptyList(),
            circles = emptyList(),
        )
        reconciler.render(updated)
        assertSame(nativeHandle, layer.handles.getValue("weft:marker:pin"))
        assertEquals(LatLng(37.6, 127.1), layer.shapes.getValue("weft:marker:pin").position)

        reconciler.render(emptyList())
        assertEquals(emptyMap(), layer.handles)
    }

    @Test
    fun retainsCircleGeometryAndStyleIncludingAlpha() {
        val circle = MapCircle(
            id = "radius",
            center = LatLng(37.5, 127.0),
            radiusMeters = 250.0,
            fillColor = Color(0x33112233),
            strokeColor = Color(0x80445566),
            strokeWidthDp = 3f,
            zIndex = 7f,
            visible = false,
        )
        val shape = toNaverMapOverlayShapes(emptyList(), emptyList(), emptyList(), listOf(circle)).single()
        assertEquals("weft:circle:radius", shape.nativeId)
        assertEquals(NaverMapOverlayKind.Circle, shape.kind)
        assertEquals(circle.center, shape.center)
        assertEquals(250.0, shape.radiusMeters)
        assertEquals(circle.fillColor, shape.fillColor)
        assertEquals(circle.strokeColor, shape.strokeColor)
        assertEquals(3f, shape.strokeWidthDp)
        assertEquals(7f, shape.zIndex)
        assertEquals(false, shape.visible)
    }

    @Test
    fun restoresCommittedSnapshotAfterMutationFailure() {
        val layer = FakeLayer()
        val reconciler = NaverMapIosOverlayReconciler(layer)
        val original = toNaverMapOverlayShapes(
            markers = listOf(MapMarker("old", LatLng(37.5, 127.0))),
            polylines = emptyList(),
            polygons = emptyList(),
            circles = emptyList(),
        )
        reconciler.render(original)
        layer.failNextUpdate = true
        val changed = toNaverMapOverlayShapes(
            markers = listOf(MapMarker("old", LatLng(37.6, 127.1)), MapMarker("new", LatLng(37.7, 127.2))),
            polylines = emptyList(),
            polygons = emptyList(),
            circles = emptyList(),
        )
        assertFailsWith<IllegalStateException> { reconciler.render(changed) }
        assertEquals(original, reconciler.snapshot)
        assertEquals(original.associateBy(NaverMapOverlayShape::nativeId), layer.shapes)
    }

    private class FakeLayer : NaverMapIosOverlayLayer {
        val shapes = linkedMapOf<String, NaverMapOverlayShape>()
        val handles = linkedMapOf<String, Any>()
        var failNextUpdate = false
        override fun add(shape: NaverMapOverlayShape) {
            shapes[shape.nativeId] = shape
            handles[shape.nativeId] = Any()
        }
        override fun update(shape: NaverMapOverlayShape) {
            if (failNextUpdate) {
                failNextUpdate = false
                shapes[shape.nativeId] = shape
                error("partial native mutation")
            }
            shapes[shape.nativeId] = shape
        }
        override fun remove(nativeId: String) {
            shapes.remove(nativeId)
            handles.remove(nativeId)
        }
        override fun restore(shapes: List<NaverMapOverlayShape>) {
            this.shapes.clear()
            this.handles.clear()
            shapes.forEach {
                this.shapes[it.nativeId] = it
                this.handles[it.nativeId] = Any()
            }
        }
    }
}
