package run.thespot.weft.core.map

import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composition
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.snapshots.Snapshot
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import run.thespot.weft.core.map.composition.MapApplier
import run.thespot.weft.core.map.composition.MapMarkerNode
import run.thespot.weft.core.map.composition.MapRootNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MapContentCompositionTest {
    @Test
    fun nodeInvalidationPublishesANewSnapshotWithoutParentContentChanges() {
        val root = MapRootNode()
        val state = MapContentState(root)
        val applier = MapApplier(root, state::invalidate)
        val original = marker("one", title = "before")
        val node = MapMarkerNode(original)
        applier.insertBottomUp(0, node)
        state.publishIfDirty()
        val previous = state.content

        val frameClock = BroadcastFrameClock()
        val context = Dispatchers.Unconfined + frameClock
        val recomposer = Recomposer(context)
        val scope = CoroutineScope(SupervisorJob() + context)
        val recomposerJob = scope.launch { recomposer.runRecomposeAndApplyChanges() }
        val composition = Composition(applier, recomposer)
        var frameTimeNanos = 0L

        try {
            composition.setContent {
                state.invalidationToken
                state.markDirty()
                SideEffect { state.publishIfDirty() }
            }
            repeat(3) {
                Snapshot.sendApplyNotifications()
                frameClock.sendFrame(++frameTimeNanos)
                Snapshot.sendApplyNotifications()
            }
            assertSame(previous, state.content)

            val updated = original.copy(title = "after")
            node.updateMarker(updated)
            repeat(3) {
                Snapshot.sendApplyNotifications()
                frameClock.sendFrame(++frameTimeNanos)
                Snapshot.sendApplyNotifications()
            }

            assertNotSame(previous, state.content)
            assertEquals(updated, state.content.markers.single())
        } finally {
            state.dispose()
            composition.dispose()
            recomposerJob.cancel()
            recomposer.cancel()
            scope.cancel()
        }
    }

    @Test
    fun publishesAfterChildCompositionAndPublishesANewSnapshotAfterNodeInvalidation() {
        val markers = listOf(marker("one"))
        val harness = MapContentHarness(markerContent(markers))

        harness.use {
            assertEquals(emptyList(), harness.snapshots.first().markers)
            assertEquals(listOf("one"), harness.latest.markers.map { it.id })

            val previous = harness.latest
            harness.updateContent(
                markerContent(listOf(marker("one", title = "updated"))),
            )

            assertEquals("updated", harness.latest.markers.single().title)
            assertNotSame(previous, harness.latest)
        }
    }

    @Test
    fun changedMarkersPublishAnOrderedAtomicSnapshotWithoutMutatingThePreviousOne() {
        val first = marker("first", title = "before")
        val second = marker("second", title = "unchanged")
        val harness = MapContentHarness(markerContent(listOf(first, second)))

        harness.use {
            val previous = harness.latest
            val snapshotCount = harness.snapshots.size
            val updated = second.copy(title = "after")
            harness.updateContent(markerContent(listOf(first, updated)))

            val expected = listOf("first", "second")
            assertEquals(expected, harness.latest.markers.map { it.id })
            assertEquals("after", harness.latest.markers.last().title)
            assertEquals("unchanged", previous.markers.last().title)
            assertNotSame(previous, harness.latest)
            val previousIds = listOf("first", "second")
            assertTrue(
                harness.snapshots.drop(snapshotCount).all { snapshot ->
                    val ids = snapshot.markers.map { it.id }
                    ids == previousIds || ids == expected
                },
                "provider must only observe complete old or new snapshots",
            )
        }
    }

    @Test
    fun publishesOrderedOverlaySnapshotsAndAllowsIdsAcrossKinds() {
        val sameId = "shared-id"
        val line = polyline(sameId)
        val area = polygon(sameId)
        val radius = MapCircle(sameId, LatLng(0.0, 0.0), 250.0)
        val harness = MapContentHarness {
            Marker(marker(sameId))
            Polyline(line)
            Polygon(area)
            Circle(radius)
        }

        harness.use {
            assertEquals(listOf(sameId), harness.latest.markers.map { it.id })
            assertEquals(listOf(line), harness.latest.polylines)
            assertEquals(listOf(area), harness.latest.polygons)
            assertEquals(listOf(radius), harness.latest.circles)
        }
    }

    @Test
    fun overlayUpdatesRemovalReorderingAndVisibilityPublishCompleteOrderedSnapshots() {
        val first = polyline("first")
        val second = polyline("second")
        val harness = MapContentHarness(overlayContent(polylines = listOf(first, second)))

        harness.use {
            val previous = harness.latest
            harness.updateContent(
                overlayContent(
                    polylines = listOf(
                        polyline("second", visible = false),
                        polyline("third", visible = true),
                    ),
                ),
            )

            assertEquals(listOf("second", "third"), harness.latest.polylines.map { it.id })
            assertEquals(listOf(false, true), harness.latest.polylines.map { it.visible })
            assertEquals(listOf("first", "second"), previous.polylines.map { it.id })
        }
    }

    @Test
    fun duplicateOverlayIdsKeepTheLastPublishedSnapshot() {
        val valid = polyline("route")
        val harness = MapContentHarness(overlayContent(polylines = listOf(valid)))

        harness.use {
            val previous = harness.latest
            val failure = assertFailsWith<IllegalArgumentException> {
                harness.updateContent(overlayContent(polylines = listOf(valid, valid)))
            }

            assertEquals("duplicate polyline id: route", failure.message)
            assertSame(previous, harness.latest)
            assertEquals(listOf(valid), harness.latest.polylines)
        }
    }

    @Test
    fun duplicatePolygonIdsKeepTheLastPublishedSnapshot() {
        val valid = polygon("service-area")
        val harness = MapContentHarness(overlayContent(polygons = listOf(valid)))

        harness.use {
            val previous = harness.latest
            val failure = assertFailsWith<IllegalArgumentException> {
                harness.updateContent(overlayContent(polygons = listOf(valid, valid)))
            }

            assertEquals("duplicate polygon id: service-area", failure.message)
            assertSame(previous, harness.latest)
            assertEquals(listOf(valid), harness.latest.polygons)
        }
    }

    @Test
    fun duplicateCircleIdsKeepTheLastPublishedSnapshot() {
        val valid = MapCircle("pickup-radius", LatLng(0.0, 0.0), 250.0)
        val harness = MapContentHarness(overlayContent(circles = listOf(valid)))

        harness.use {
            val previous = harness.latest
            val failure = assertFailsWith<IllegalArgumentException> {
                harness.updateContent(overlayContent(circles = listOf(valid, valid)))
            }

            assertEquals("duplicate circle id: pickup-radius", failure.message)
            assertSame(previous, harness.latest)
            assertEquals(listOf(valid), harness.latest.circles)
        }
    }

    @Test
    fun removalAndReorderingPublishOneCompleteOrderedSnapshot() {
        var contentRuns = 0
        val harness = MapContentHarness(
            markerContent(listOf(marker("first"), marker("second"), marker("third"))) {
                contentRuns++
            },
        )

        harness.use {
            val previous = harness.latest
            val snapshotCount = harness.snapshots.size
            harness.updateContent(markerContent(listOf(marker("third"), marker("first"))) {
                contentRuns++
            })

            val oldIds = listOf("first", "second", "third")
            val nextIds = listOf("third", "first")
            assertEquals(
                nextIds,
                harness.latest.markers.map { it.id },
                "changed marker list should recompose child content; runs=$contentRuns",
            )
            assertEquals(listOf("first", "second", "third"), previous.markers.map { it.id })
            assertTrue(contentRuns > 1, "the changed marker list must recompose child content")
            assertTrue(
                harness.snapshots.drop(snapshotCount).all { snapshot ->
                    val ids = snapshot.markers.map { it.id }
                    ids == oldIds || ids == nextIds
                },
                "provider must not observe a partially removed or reordered tree",
            )

            val afterReorderCount = harness.snapshots.size
            harness.updateContent(
                markerContent(listOf(marker("third"), marker("first"), marker("fourth"))) {
                    contentRuns++
                },
            )
            assertEquals(
                listOf("third", "first", "fourth"),
                harness.latest.markers.map { it.id },
            )
            assertTrue(harness.snapshots.size > afterReorderCount)
        }
    }

    @Test
    fun duplicateIdsKeepTheLastValidSnapshotAndPropagateTheDeterministicFailure() {
        val root = MapRootNode()
        val state = MapContentState(root)
        val applier = MapApplier(root, state::invalidate)
        val original = marker("same", title = "valid")
        applier.insertBottomUp(0, MapMarkerNode(original))
        state.publishIfDirty()
        val previous = state.content

        applier.insertBottomUp(1, MapMarkerNode(marker("same", title = "duplicate")))
        state.invalidate()
        val recomposer = Recomposer(kotlin.coroutines.EmptyCoroutineContext)
        val composition = Composition(applier, recomposer)
        val failure = try {
            assertFailsWith<IllegalArgumentException> {
                composition.setContent {
                    state.invalidationToken
                    SideEffect { state.publishIfDirty() }
                }
            }
        } finally {
            state.dispose()
            composition.dispose()
            recomposer.cancel()
        }

        assertEquals("duplicate marker id: same", failure.message)
        assertSame(previous, state.content)
        assertEquals(listOf(original), state.content.markers)
    }

    @Test
    fun disposalSuppressesLateInvalidationsAndPublications() {
        val root = MapRootNode()
        val state = MapContentState(root)
        val applier = MapApplier(root, state::invalidate)
        val node = MapMarkerNode(marker("one"))
        applier.insertBottomUp(0, node)
        state.publishIfDirty()
        val previous = state.content

        state.dispose()
        node.invalidate()
        state.publishIfDirty()

        assertSame(previous, state.content)
        assertEquals(listOf("one"), state.content.markers.map { it.id })
    }

    @Test
    fun disposedRememberedCompositionDoesNotPublishLateContentChanges() {
        val markers = mutableStateOf(listOf(marker("one")))
        val content: @Composable MapScope.() -> Unit = {
            markers.value.forEach { Marker(it) }
        }
        val harness = MapContentHarness(content)
        val publicationCount = harness.snapshots.size
        harness.close()

        markers.value = listOf(marker("two"))
        harness.recompose()

        assertEquals(publicationCount, harness.snapshots.size)
    }
}

private class MapContentHarness(
    content: @Composable MapScope.() -> Unit,
) : AutoCloseable {
    private val frameClock = BroadcastFrameClock()
    private val errors = mutableListOf<Throwable>()
    private val context = Dispatchers.Unconfined + frameClock + CoroutineExceptionHandler { _, error ->
        errors += error
    }
    private val recomposer = Recomposer(context)
    private val scope = CoroutineScope(SupervisorJob() + context)
    private val recomposerJob = scope.launch { recomposer.runRecomposeAndApplyChanges() }
    private val composition = Composition(NoOpApplier, recomposer)
    private val currentContent = mutableStateOf(content, policy = neverEqualPolicy())
    private var frameTimeNanos = 0L
    val snapshots = mutableListOf<MapContent>()
    val latest: MapContent
        get() = snapshots.last()

    init {
        composition.setContent {
            snapshots += rememberMapContent(currentContent.value)
        }
        recompose()
    }

    fun updateContent(content: @Composable MapScope.() -> Unit) {
        currentContent.value = content
        recompose()
    }

    fun recompose() {
        Snapshot.sendApplyNotifications()
        repeat(3) {
            frameClock.sendFrame(++frameTimeNanos)
            Snapshot.sendApplyNotifications()
        }
        if (errors.isNotEmpty()) throw errors.removeAt(0)
    }

    override fun close() {
        composition.dispose()
        recomposerJob.cancel()
        recomposer.cancel()
        scope.cancel()
    }
}

private fun markerContent(
    markers: List<MapMarker>,
    onRun: () -> Unit = {},
): @Composable MapScope.() -> Unit = {
    onRun()
    markers.forEach { Marker(it) }
}

private fun overlayContent(
    polylines: List<MapPolyline> = emptyList(),
    polygons: List<MapPolygon> = emptyList(),
    circles: List<MapCircle> = emptyList(),
): @Composable MapScope.() -> Unit = {
    polylines.forEach { Polyline(it) }
    polygons.forEach { Polygon(it) }
    circles.forEach { Circle(it) }
}

private fun marker(id: String, title: String? = null) =
    MapMarker(id = id, position = LatLng(0.0, 0.0), title = title)

private fun polyline(id: String, visible: Boolean = true) =
    MapPolyline(id, listOf(LatLng(0.0, 0.0), LatLng(1.0, 1.0)), visible = visible)

private fun polygon(id: String) =
    MapPolygon(id, listOf(LatLng(0.0, 0.0), LatLng(1.0, 0.0), LatLng(0.0, 1.0)))

private object NoOpApplier : AbstractApplier<Unit>(Unit) {
    override fun insertTopDown(index: Int, instance: Unit) = Unit
    override fun insertBottomUp(index: Int, instance: Unit) = Unit
    override fun remove(index: Int, count: Int) = Unit
    override fun move(from: Int, to: Int, count: Int) = Unit
    override fun onClear() = Unit
}
