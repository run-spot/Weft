package run.thespot.weft.core.map.composition

import run.thespot.weft.core.map.LatLng
import run.thespot.weft.core.map.MapMarker
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class MapNodeTest {
    @Test
    fun attachPropagatesInvalidationAndDetachClearsItFromTheWholeSubtree() {
        val root = MapRootNode()
        val group = MapNode()
        val marker = MapMarkerNode(marker("one"))
        group.children += marker
        root.children += group
        var invalidations = 0

        root.attach { invalidations++ }
        marker.invalidate()
        assertEquals(1, invalidations)

        root.detach()
        marker.invalidate()
        group.invalidate()
        root.invalidate()

        assertEquals(1, invalidations)
    }

    @Test
    fun markerUpdateReplacesTheModelInPlaceAndInvalidatesOnlyForChanges() {
        val original = marker("one", title = "before")
        val node = MapMarkerNode(original)
        val root = MapRootNode().apply { children += node }
        var invalidations = 0
        root.attach { invalidations++ }

        val changed = original.copy(position = LatLng(1.0, 2.0), title = "after")
        node.updateMarker(changed)

        assertSame(node, root.children.single())
        assertEquals(changed, node.marker)
        assertEquals(1, invalidations)

        node.updateMarker(changed)
        assertEquals(1, invalidations)
    }
}

private fun marker(id: String, title: String? = null) =
    MapMarker(id = id, position = LatLng(0.0, 0.0), title = title)
