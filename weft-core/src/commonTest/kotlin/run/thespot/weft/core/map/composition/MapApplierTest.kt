package run.thespot.weft.core.map.composition

import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composition
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import run.thespot.weft.core.map.LatLng
import run.thespot.weft.core.map.MapContent
import run.thespot.weft.core.map.MapMarker
import run.thespot.weft.core.map.MapScope
import run.thespot.weft.core.map.Marker
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MapApplierTest {
    @Test
    fun insertRemoveAndClearManageChildrenAndDetachRemovedSubtrees() {
        val root = MapRootNode()
        var invalidations = 0
        val applier = MapApplier(root) { invalidations++ }
        val first = MapMarkerNode(marker("first"))
        val branch = MapNode().apply {
            children += MapMarkerNode(marker("nested"))
        }

        applier.insertTopDown(0, first)
        applier.insertBottomUp(0, first)
        applier.insertTopDown(1, branch)
        applier.insertBottomUp(1, branch)

        assertEquals(listOf(first, branch), root.children)
        val nested = branch.children.single() as MapMarkerNode
        val beforeRemove = invalidations
        applier.remove(0, 1)
        assertEquals(listOf(branch), root.children)
        first.invalidate()
        assertEquals(beforeRemove, invalidations)

        applier.clear()
        assertTrue(root.children.isEmpty())
        val afterClear = invalidations
        nested.invalidate()
        branch.invalidate()
        assertEquals(afterClear, invalidations)
    }

    @Test
    fun moveReordersWithoutReplacingNodeInstances() {
        val root = MapRootNode()
        val applier = MapApplier(root) {}
        val first = MapMarkerNode(marker("first"))
        val second = MapMarkerNode(marker("second"))
        val third = MapMarkerNode(marker("third"))
        listOf(first, second, third).forEachIndexed { index, node ->
            applier.insertTopDown(index, node)
            applier.insertBottomUp(index, node)
        }

        applier.move(from = 0, to = 3, count = 1)

        assertSame(second, root.children[0])
        assertSame(third, root.children[1])
        assertSame(first, root.children[2])
    }

    @Test
    fun builderPreservesTreeOrderAndRejectsDuplicateIdsBeforeReturningContent() {
        val root = MapRootNode()
        val group = MapNode().apply {
            children += MapMarkerNode(marker("first"))
            children += MapMarkerNode(marker("second"))
        }
        root.children += group
        root.children += MapMarkerNode(marker("third"))

        val content = MapContentBuilder.build(root)
        assertEquals(listOf("first", "second", "third"), content.markers.map { it.id })

        val duplicateRoot = MapRootNode().apply {
            children += MapMarkerNode(marker("duplicate"))
            children += MapMarkerNode(marker("duplicate"))
        }
        var snapshot: MapContent? = null
        val failure = assertFailsWith<IllegalArgumentException> {
            snapshot = MapContentBuilder.build(duplicateRoot)
        }

        assertTrue(failure.message.orEmpty().contains("duplicate"))
        assertEquals(null, snapshot)
    }

    @Test
    fun markerDslReusesKeyedNodesWhenSameIdsMoveAndUpdate() {
        val root = MapRootNode()
        val applier = MapApplier(root) {}
        val frameClock = BroadcastFrameClock()
        val testContext = Dispatchers.Unconfined + frameClock
        val recomposer = Recomposer(testContext)
        val composition = Composition(applier, recomposer)
        val recomposerScope = CoroutineScope(testContext)
        val recomposerJob = recomposerScope.launch {
            recomposer.runRecomposeAndApplyChanges()
        }
        val markers = mutableStateOf(
            listOf(marker("first", title = "first"), marker("second", title = "second")),
        )
        val content: @Composable MapScope.() -> Unit = {
            markers.value.forEach { marker -> Marker(marker) }
        }

        try {
            composition.setContent {
                content.invoke(MapScope)
            }
            val originalNodes = root.children.toList()

            markers.value = listOf(
                marker("second", title = "updated second"),
                marker("first", title = "updated first"),
            )
            Snapshot.sendApplyNotifications()
            frameClock.sendFrame(1L)

            assertEquals(
                listOf("second", "first"),
                root.children.map { (it as MapMarkerNode).marker.id },
            )
            assertSame(originalNodes[1], root.children[0])
            assertSame(originalNodes[0], root.children[1])
            assertEquals("updated second", (root.children[0] as MapMarkerNode).marker.title)
            assertEquals("updated first", (root.children[1] as MapMarkerNode).marker.title)
        } finally {
            composition.dispose()
            recomposerJob.cancel()
            recomposer.cancel()
            recomposerScope.cancel()
        }
    }
}

private fun marker(id: String, title: String? = null) =
    MapMarker(id = id, position = LatLng(0.0, 0.0), title = title)
