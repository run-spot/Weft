package run.thespot.weft.map.navermap

import android.os.Bundle
import android.app.Application
import android.view.View
import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composition
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import run.thespot.weft.core.map.CameraPosition
import run.thespot.weft.core.map.LatLng
import run.thespot.weft.core.map.MapCircle
import run.thespot.weft.core.map.MapContent
import run.thespot.weft.core.map.MapMarker
import run.thespot.weft.core.map.MapPolygon
import run.thespot.weft.core.map.MapPolyline
import run.thespot.weft.core.map.MapScope
import run.thespot.weft.core.map.Circle
import run.thespot.weft.core.map.Marker
import run.thespot.weft.core.map.Polygon
import run.thespot.weft.core.map.Polyline
import run.thespot.weft.core.map.rememberMapContent
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class NaverMapAndroidOverlayReconcilerTest {
    private lateinit var contentFactory: TestMapContentFactory

    @BeforeTest
    fun setUpContentFactory() {
        contentFactory = TestMapContentFactory()
    }

    @AfterTest
    fun tearDownContentFactory() {
        contentFactory.close()
    }

    @Test
    fun addsUpdatesAndRemovesByStableId() {
        val markers = FakeMarkerLayer()
        val shapes = FakeShapeLayer()
        val reconciler = reconciler(markers, shapes)
        val first = content(
            markers = listOf(marker("one", title = "One"), marker("two", title = "Two")),
            polylines = listOf(polyline("line", visible = true)),
        )
        val second = content(
            markers = listOf(marker("one", title = "Updated"), marker("three")),
            polylines = emptyList(),
        )

        reconciler.render(first)
        val stableMarker = markers.nativeById.getValue("weft:marker:one")
        reconciler.render(second)

        assertEquals(second.markers, reconciler.snapshot.markers)
        assertEquals(second.polylines, reconciler.snapshot.shapes.filterIsInstance<NaverMapAndroidShape.Polyline>().map { it.value })
        assertSame(stableMarker, markers.nativeById.getValue("weft:marker:one"))
        assertEquals(listOf("weft:marker:two"), markers.removedIds)
        assertEquals(listOf("weft:polyline:line"), shapes.removedIds)
        assertEquals(listOf("three"), markers.addedModels.map(MapMarker::id).takeLast(1))
    }

    @Test
    fun keepsNativeIdentityForStyleAndVisibilityUpdates() {
        val markers = FakeMarkerLayer()
        val shapes = FakeShapeLayer()
        val reconciler = reconciler(markers, shapes)
        val first = content(
            markers = listOf(marker("marker", title = "before")),
            polylines = listOf(polyline("line", visible = true, zIndex = 1.4f)),
            polygons = listOf(polygon("polygon", visible = true)),
            circles = listOf(circle("circle", visible = true)),
        )
        reconciler.render(first)
        val markerIdentity = markers.nativeById.getValue("weft:marker:marker")
        val shapeIdentities = shapes.nativeById.toMap()

        reconciler.render(
            content(
                markers = listOf(marker("marker", title = "after", snippet = "details")),
                polylines = listOf(polyline("line", visible = false, zIndex = 2.6f)),
                polygons = listOf(polygon("polygon", visible = false)),
                circles = listOf(circle("circle", visible = false)),
            ),
        )

        assertSame(markerIdentity, markers.nativeById.getValue("weft:marker:marker"))
        assertEquals(shapeIdentities, shapes.nativeById)
        assertEquals(1, shapes.nativeById.getValue("weft:polyline:line").updates)
        assertFalse(shapes.nativeById.getValue("weft:polyline:line").visible)
        assertEquals(3, shapes.nativeById.getValue("weft:polyline:line").globalZIndex)
    }

    @Test
    fun usesPrefixedNativeIdsForEveryOverlayKind() {
        val markers = FakeMarkerLayer()
        val shapes = FakeShapeLayer()
        val reconciler = reconciler(markers, shapes)

        reconciler.render(
            content(
                markers = listOf(marker("same")),
                polylines = listOf(polyline("same")),
                polygons = listOf(polygon("same", visible = true)),
                circles = listOf(circle("same", visible = true)),
            ),
        )

        assertEquals(setOf("weft:marker:same"), markers.nativeById.keys)
        assertEquals(
            setOf(
                "weft:polyline:same",
                "weft:polygon:same",
                "weft:circle:same",
            ),
            shapes.nativeById.keys,
        )
        assertEquals(4, (markers.nativeById.values.map { it.nativeId } + shapes.nativeById.values.map { it.nativeId }).toSet().size)
    }

    @Test
    fun rollsBackAfterMutationFailure() {
        val markers = FakeMarkerLayer()
        val shapes = FakeShapeLayer().apply { failOnUpdateId = "line" }
        val reconciler = reconciler(markers, shapes)
        val committed = content(polylines = listOf(polyline("line", visible = true)))
        reconciler.render(committed)

        val failure = assertFailsWith<IllegalStateException> {
            reconciler.render(content(polylines = listOf(polyline("line", visible = false))))
        }

        assertEquals("shape update failed", failure.message)
        assertEquals(committed.polylines, reconciler.snapshot.shapes.filterIsInstance<NaverMapAndroidShape.Polyline>().map { it.value })
        assertEquals(1, shapes.restoreCalls)
        assertTrue(shapes.nativeById.getValue("weft:polyline:line").visible)
    }

    @Test
    fun marksLayerUnsynchronizedWhenRollbackFails() {
        val markers = FakeMarkerLayer()
        val shapes = FakeShapeLayer().apply {
            failOnUpdateId = "line"
            failOnRestoreCall = 1
        }
        val reconciler = reconciler(markers, shapes)
        val committed = content(polylines = listOf(polyline("line", visible = true)))
        reconciler.render(committed)

        assertFailsWith<IllegalStateException> {
            reconciler.render(content(polylines = listOf(polyline("line", visible = false))))
        }
        shapes.failOnUpdateId = null
        reconciler.render(content(polylines = listOf(polyline("line", visible = false))))

        assertEquals(2, shapes.restoreCalls)
        assertEquals(listOf("line"), reconciler.snapshot.shapes.map(NaverMapAndroidShape::id))
        assertFalse(shapes.nativeById.getValue("weft:polyline:line").visible)
    }

    @Test
    fun restoresEachOverlayLayerAndAggregatesRollbackFailuresIndependently() {
        val markers = FakeMarkerLayer()
        val shapes = FakeShapeLayer().apply { failOnUpdateId = "line" }
        val reconciler = reconciler(markers, shapes)
        val committed = content(
            markers = listOf(marker("marker", title = "before")),
            polylines = listOf(polyline("line", visible = true)),
        )
        reconciler.render(committed)
        val markerRollback = IllegalArgumentException("marker rollback failed")
        val shapeRollback = IllegalStateException("shape rollback failed")
        markers.restoreFailure = markerRollback
        shapes.restoreFailure = shapeRollback

        val failure = assertFailsWith<IllegalStateException> {
            reconciler.render(
                content(
                    markers = listOf(marker("marker", title = "after")),
                    polylines = listOf(polyline("line", visible = false)),
                ),
            )
        }

        assertEquals(1, markers.restoreCalls)
        assertEquals(1, shapes.restoreCalls)
        assertEquals(listOf(markerRollback, shapeRollback), failure.suppressedExceptions)
        assertEquals(committed.markers, reconciler.snapshot.markers)
    }

    @Test
    fun cameraRollbackRestoresPreviousCameraAndSuppressesRollbackFailure() {
        val markers = FakeMarkerLayer()
        val shapes = FakeShapeLayer()
        val reconciler = reconciler(markers, shapes)
        val committed = content(polylines = listOf(polyline("line")))
        reconciler.render(committed)
        val previous = CameraPosition(LatLng(37.0, 127.0), zoom = 10f)
        var restored: CameraPosition? = null

        val failure = assertFailsWith<IllegalStateException> {
            reconciler.renderWithCamera(
                content = committed,
                applyCamera = { throw IllegalStateException("camera failed") },
                restoreCamera = { restored = previous },
            )
        }

        assertEquals(previous, restored)
        assertEquals(committed.polylines, reconciler.snapshot.shapes.filterIsInstance<NaverMapAndroidShape.Polyline>().map { it.value })
        assertTrue(failure.suppressedExceptions.isEmpty())
    }

    @Test
    fun cameraRollbackFailureIsSuppressed() {
        val reconciler = reconciler(FakeMarkerLayer(), FakeShapeLayer())
        val rollbackFailure = IllegalArgumentException("camera restore failed")
        val failure = assertFailsWith<IllegalStateException> {
            reconciler.renderWithCamera(
                content = content(),
                applyCamera = { throw IllegalStateException("camera failed") },
                restoreCamera = { throw rollbackFailure },
            )
        }
        assertSame(rollbackFailure, failure.suppressedExceptions.single())
    }

    @Test
    fun cameraRollbackAttemptsBothOverlayRestoresAndCameraRestore() {
        val markers = FakeMarkerLayer()
        val shapes = FakeShapeLayer()
        val reconciler = reconciler(markers, shapes)
        val committed = content(markers = listOf(marker("marker")), polylines = listOf(polyline("line")))
        reconciler.render(committed)
        val markerRollback = IllegalArgumentException("marker camera rollback failed")
        val shapeRollback = IllegalStateException("shape camera rollback failed")
        val cameraRollback = IllegalStateException("camera rollback failed")
        markers.restoreFailure = markerRollback
        shapes.restoreFailure = shapeRollback

        val failure = assertFailsWith<IllegalStateException> {
            reconciler.renderWithCamera(
                content = committed,
                applyCamera = { throw IllegalStateException("camera failed") },
                restoreCamera = { throw cameraRollback },
            )
        }

        assertEquals(1, markers.restoreCalls)
        assertEquals(1, shapes.restoreCalls)
        assertEquals(listOf(markerRollback, shapeRollback, cameraRollback), failure.suppressedExceptions)
    }

    @Test
    fun rollbackRefreshesMarkerAndShapeHandlesBeforeRetrying() {
        val markers = FakeMarkerLayer().apply { failOnDetachedUpdate = true }
        val shapes = FakeShapeLayer().apply { failOnDetachedUpdate = true }
        val reconciler = reconciler(markers, shapes)
        val next = content(
            markers = listOf(marker("marker")),
            polylines = listOf(polyline("line")),
        )

        assertFailsWith<IllegalStateException> {
            reconciler.renderWithCamera(
                content = next,
                applyCamera = { throw IllegalStateException("camera failed") },
                restoreCamera = {},
            )
        }

        reconciler.render(next)

        assertEquals(0, markers.detachedUpdateCalls)
        assertEquals(0, shapes.detachedUpdateCalls)
        assertEquals(2, markers.addedModels.count { it.id == "marker" })
        assertEquals(setOf("weft:marker:marker"), markers.nativeById.keys)
        assertEquals(setOf("weft:polyline:line"), shapes.nativeById.keys)
    }

    @Test
    fun fullRestoreRefreshesMarkerAndShapeHandlesAfterRollbackFailure() {
        val markers = FakeMarkerLayer().apply { failOnDetachedUpdate = true }
        val shapes = FakeShapeLayer().apply {
            failOnUpdateId = "line"
            failOnRestoreCall = 1
            failOnDetachedUpdate = true
        }
        val reconciler = reconciler(markers, shapes)
        val committed = content(
            markers = listOf(marker("marker", title = "before")),
            polylines = listOf(polyline("line", visible = true)),
        )
        reconciler.render(committed)

        assertFailsWith<IllegalStateException> {
            reconciler.render(
                content(
                    markers = listOf(marker("marker", title = "after")),
                    polylines = listOf(polyline("line", visible = false)),
                ),
            )
        }

        shapes.failOnUpdateId = null
        reconciler.render(
            content(
                markers = listOf(marker("marker", title = "after")),
                polylines = listOf(polyline("line", visible = false)),
            ),
        )
        reconciler.render(
            content(
                markers = listOf(marker("marker", title = "final")),
                polylines = listOf(polyline("line", visible = true)),
            ),
        )

        assertEquals(0, markers.detachedUpdateCalls)
        assertEquals(0, shapes.detachedUpdateCalls)
        assertEquals("final", markers.nativeById.getValue("weft:marker:marker").model?.title)
        assertTrue(shapes.nativeById.getValue("weft:polyline:line").visible)
    }

    @Test
    fun removesStaleDetachedHandlesFromNativeLayerRegistry() {
        val detached = mutableListOf<String>()
        val registry = NaverMapAndroidNativeHandleRegistry<String> { detached += it }
        registry.put("weft:marker:stale", "stale")
        registry.put("weft:marker:live", "live")

        registry.retain(setOf("weft:marker:live"))

        assertEquals(setOf("weft:marker:live"), registry.ids)
        assertEquals(listOf("stale"), detached)
    }

    @Test
    fun forwardsSavedStateAndLowMemoryCallbacks() {
        val view = FakeViewLifecycle()
        val savedState = FakeSavedStateBridge()
        val callbacks = FakeComponentCallbacksBridge()
        val surface = lifecycleBridge(view, savedState, callbacks)

        surface.create()
        surface.onStart()
        surface.onResume()
        surface.onPause()
        surface.onStop()
        callbacks.lowMemory()

        assertEquals(
            listOf("create", "start", "resume", "pause", "stop", "lowMemory"),
            view.calls,
        )
        assertEquals(1, callbacks.registerCalls)
        assertEquals(1, callbacks.lowMemoryCalls)
        assertEquals(1, savedState.registerCalls)
    }

    @Test
    fun forwardsMapViewStartAgainAfterStop() {
        val view = FakeViewLifecycle()
        val surface = lifecycleBridge(view, FakeSavedStateBridge(), FakeComponentCallbacksBridge())

        surface.create()
        surface.onStart()
        surface.onStop()
        surface.onStart()

        assertEquals(listOf("create", "start", "stop", "start"), view.calls)
    }

    @Test
    fun restoresEmptyAndRestoredSavedStateBundles() {
        val emptyView = FakeViewLifecycle()
        val emptyState = FakeSavedStateBridge()
        lifecycleBridge(emptyView, emptyState, FakeComponentCallbacksBridge()).create()
        assertEquals(0, emptyView.createdWith?.size())

        val restored = Bundle().apply { putString("camera", "restored") }
        val restoredView = FakeViewLifecycle()
        val restoredState = FakeSavedStateBridge(restored = restored)
        lifecycleBridge(restoredView, restoredState, FakeComponentCallbacksBridge()).create()
        assertSame(restored, restoredView.createdWith)
        assertEquals(1, restoredState.consumeCalls)
    }

    @Test
    fun createUnwindsWhenMapViewOnCreateFails() {
        val view = FakeViewLifecycle().apply { failOnCreate = IllegalStateException("create failed") }
        val savedState = FakeSavedStateBridge()
        val callbacks = FakeComponentCallbacksBridge()
        val bridge = lifecycleBridge(view, savedState, callbacks)

        assertFailsWith<IllegalStateException> { bridge.create() }

        assertEquals(listOf("create", "destroy"), view.calls)
        assertEquals(0, callbacks.registerCalls)
    }

    @Test
    fun createUnwindsWhenComponentRegistrationFails() {
        val view = FakeViewLifecycle()
        val savedState = FakeSavedStateBridge()
        val callbacks = FakeComponentCallbacksBridge().apply {
            registerFailure = IllegalStateException("component registration failed")
        }
        val bridge = lifecycleBridge(view, savedState, callbacks)

        assertFailsWith<IllegalStateException> { bridge.create() }

        assertEquals(listOf("create", "destroy"), view.calls)
        assertEquals(0, savedState.registerCalls)
    }

    @Test
    fun deferredSavedStateRegistrationFailureUnwindsLifecycle() {
        val view = FakeViewLifecycle()
        val savedState = FakeSavedStateBridge().apply { registerResult = false }
        val callbacks = FakeComponentCallbacksBridge()
        val bridge = lifecycleBridge(view, savedState, callbacks)
        bridge.create()
        savedState.registerFailure = IllegalStateException("deferred registration failed")

        assertFailsWith<IllegalStateException> { bridge.onAttach() }

        assertEquals(listOf("create", "destroy"), view.calls)
        assertEquals(1, callbacks.unregisterCalls)
    }

    @Test
    fun unregistersSavedStateProvider() {
        val savedState = FakeSavedStateBridge()
        val surface = lifecycleBridge(FakeViewLifecycle(), savedState, FakeComponentCallbacksBridge())
        surface.create()
        surface.dispose()
        surface.dispose()
        assertEquals(1, savedState.unregisterCalls)
    }

    @Test
    fun disposesLifecycleBridgesIdempotently() {
        val view = FakeViewLifecycle()
        val savedState = FakeSavedStateBridge()
        val callbacks = FakeComponentCallbacksBridge()
        val surface = lifecycleBridge(view, savedState, callbacks)
        surface.create()
        surface.dispose()
        surface.dispose()

        assertEquals(listOf("create", "destroy"), view.calls)
        assertEquals(1, callbacks.unregisterCalls)
        assertEquals(1, savedState.unregisterCalls)
    }

    @Test
    fun disposeAttemptsEveryCleanupStepAndAggregatesFailures() {
        val calls = mutableListOf<String>()
        val view = FakeViewLifecycle(calls).apply {
            failOnPause = IllegalArgumentException("pause failed")
            failOnStop = IllegalStateException("stop failed")
            failOnDestroy = IllegalStateException("destroy failed")
        }
        val savedState = FakeSavedStateBridge(eventLog = calls).apply {
            unregisterFailure = IllegalStateException("saved state unregister failed")
        }
        val callbacks = FakeComponentCallbacksBridge(calls).apply {
            unregisterFailure = IllegalArgumentException("component unregister failed")
        }
        val surface = lifecycleBridge(view, savedState, callbacks)
        surface.create()
        surface.onStart()
        surface.onResume()

        val failure = assertFailsWith<IllegalStateException> { surface.dispose() }

        assertEquals(
            listOf("saved-state-unregister", "component-unregister", "pause", "stop", "destroy"),
            calls,
        )
        assertEquals(
            listOf(
                savedState.unregisterFailure,
                callbacks.unregisterFailure,
                view.failOnPause,
                view.failOnStop,
                view.failOnDestroy,
            ),
            listOf(failure) + failure.suppressedExceptions,
        )
    }

    @Test
    fun preservesSubOneDensityForStrokeWidths() {
        assertEquals(2, NaverMapAndroidOverlayStyle(density = 0.5f).strokeWidth(3f))
    }

    @Test
    fun configuresPolygonGeometryBeforeNativeMapAttachment() {
        var configuredPointCount = 0
        val events = mutableListOf<String>()

        attachNaverMapOverlayAfterConfiguration(
            configure = {
                configuredPointCount = 3
                events += "coords"
            },
            attach = {
                assertTrue(configuredPointCount >= 3)
                events += "map"
            },
        )

        assertEquals(listOf("coords", "map"), events)
    }

    @Test
    fun disposeRunsPauseCallbackWhenMapViewPauseFails() {
        var pauseCallbackCalls = 0
        val view = FakeViewLifecycle().apply { failOnPause = IllegalStateException("pause failed") }
        val bridge = NaverMapAndroidLifecycleBridge(
            view = view,
            savedState = FakeSavedStateBridge(),
            componentCallbacks = FakeComponentCallbacksBridge(),
            savedStateKey = "test-key",
            onPauseCallback = { pauseCallbackCalls++ },
        )
        bridge.create()
        bridge.onStart()
        bridge.onResume()

        assertFailsWith<IllegalStateException> { bridge.dispose() }
        assertEquals(1, pauseCallbackCalls)
    }

    @Test
    fun teardownRunsAllCleanupStepsAfterFailure() {
        val calls = mutableListOf<String>()
        val failure = assertFailsWith<IllegalStateException> {
            runNaverMapHostTeardown(
                cleanupShapes = { calls += "overlays"; throw IllegalStateException("overlay cleanup") },
                finishEngine = { calls += "view"; throw IllegalArgumentException("view cleanup") },
                clearReferences = { calls += "clear" },
            )
        }
        assertEquals(listOf("overlays", "view", "clear"), calls)
        assertEquals(1, failure.suppressedExceptions.size)
    }

    private fun reconciler(
        markers: FakeMarkerLayer,
        shapes: FakeShapeLayer,
    ) = NaverMapAndroidOverlayReconciler(
        markerLayer = markers,
        shapeLayer = shapes,
        style = NaverMapAndroidOverlayStyle(density = 2f),
    )

    private fun lifecycleBridge(
        view: FakeViewLifecycle,
        state: FakeSavedStateBridge,
        callbacks: FakeComponentCallbacksBridge,
        onPauseCallback: () -> Unit = {},
    ) = NaverMapAndroidLifecycleBridge(
        view = view,
        savedState = state,
        componentCallbacks = callbacks,
        savedStateKey = "test-key",
        onPauseCallback = onPauseCallback,
    )

    private fun marker(id: String, title: String? = null, snippet: String? = null) =
        MapMarker(id, LatLng(37.5665, 126.9780), title, snippet)

    private fun polyline(id: String, visible: Boolean = true, zIndex: Float = 0f) =
        MapPolyline(id, listOf(LatLng(37.0, 127.0), LatLng(38.0, 128.0)), Color.Red, 3f, zIndex, visible)

    private fun polygon(id: String, visible: Boolean) = MapPolygon(
        id,
        listOf(LatLng(37.0, 127.0), LatLng(38.0, 128.0), LatLng(37.0, 129.0)),
        visible = visible,
    )

    private fun circle(id: String, visible: Boolean) = MapCircle(
        id,
        LatLng(37.5, 127.5),
        100.0,
        visible = visible,
    )

    private fun content(
        markers: List<MapMarker> = emptyList(),
        polylines: List<MapPolyline> = emptyList(),
        polygons: List<MapPolygon> = emptyList(),
        circles: List<MapCircle> = emptyList(),
    ): MapContent = contentFactory.content(markers, polylines, polygons, circles)
}

private class TestMapContentFactory : AutoCloseable {
    private val frameClock = BroadcastFrameClock()
    private val context = Dispatchers.Unconfined + frameClock
    private val recomposer = Recomposer(context)
    private val scope = CoroutineScope(SupervisorJob() + context)
    private val recomposerJob = scope.launch { recomposer.runRecomposeAndApplyChanges() }
    private val composition = Composition(OverlayNoOpApplier, recomposer)
    private val currentContent = mutableStateOf<@Composable MapScope.() -> Unit>(
        emptyContentLambda,
        policy = neverEqualPolicy(),
    )
    private val snapshots = mutableListOf<MapContent>()
    private var frameTimeNanos = 0L

    init {
        composition.setContent { snapshots += rememberMapContent(currentContent.value) }
        recompose()
    }

    fun content(
        markers: List<MapMarker>,
        polylines: List<MapPolyline>,
        polygons: List<MapPolygon>,
        circles: List<MapCircle>,
    ): MapContent {
        currentContent.value = {
            markers.forEach { Marker(it) }
            polylines.forEach { Polyline(it) }
            polygons.forEach { Polygon(it) }
            circles.forEach { Circle(it) }
        }
        recompose()
        return snapshots.last()
    }

    private fun recompose() {
        Snapshot.sendApplyNotifications()
        repeat(4) {
            frameClock.sendFrame(++frameTimeNanos)
            Snapshot.sendApplyNotifications()
        }
    }

    override fun close() {
        composition.dispose()
        recomposerJob.cancel()
        recomposer.cancel()
        scope.cancel()
    }
}

private val emptyContentLambda: @Composable MapScope.() -> Unit = {}

private object OverlayNoOpApplier : AbstractApplier<Unit>(Unit) {
    override fun insertTopDown(index: Int, instance: Unit) = Unit
    override fun insertBottomUp(index: Int, instance: Unit) = Unit
    override fun remove(index: Int, count: Int) = Unit
    override fun move(from: Int, to: Int, count: Int) = Unit
    override fun onClear() = Unit
}

private class FakeMarkerLayer : NaverMapAndroidMarkerLayer {
    val nativeById = linkedMapOf<String, FakeNativeMarker>()
    val addedModels = mutableListOf<MapMarker>()
    val removedIds = mutableListOf<String>()
    var failOnDetachedUpdate = false
    var detachedUpdateCalls = 0
    var restoreCalls = 0
    var restoreFailure: Throwable? = null

    override fun add(marker: MapMarker): NaverMapAndroidNativeMarker {
        val nativeId = NaverMapAndroidNativeIds.marker(marker.id)
        val native = FakeNativeMarker(nativeId)
        nativeById[nativeId] = native
        addedModels += marker
        return native
    }

    override fun update(native: NaverMapAndroidNativeMarker, marker: MapMarker) {
        if (native !in nativeById.values) {
            detachedUpdateCalls++
            if (failOnDetachedUpdate) throw IllegalStateException("marker update used detached handle")
        }
        (native as FakeNativeMarker).model = marker
    }

    override fun remove(native: NaverMapAndroidNativeMarker, nativeId: String) {
        nativeById.remove(nativeId)
        removedIds += nativeId
    }

    override fun restore(markers: List<MapMarker>): Map<String, NaverMapAndroidNativeMarker> {
        restoreCalls++
        restoreFailure?.let { failure -> restoreFailure = null; throw failure }
        nativeById.clear()
        markers.forEach {
            val nativeId = NaverMapAndroidNativeIds.marker(it.id)
            nativeById[nativeId] = FakeNativeMarker(nativeId, it)
        }
        return nativeById.toMap()
    }
}

private class FakeShapeLayer : NaverMapAndroidShapeLayer {
    val nativeById = linkedMapOf<String, FakeNativeShape>()
    val removedIds = mutableListOf<String>()
    var failOnDetachedUpdate = false
    var detachedUpdateCalls = 0
    var restoreCalls = 0
    var failOnUpdateId: String? = null
    var failOnRestoreCall: Int? = null
    var restoreFailure: Throwable? = null

    override fun add(shape: NaverMapAndroidShape): NaverMapAndroidNativeShape {
        val native = FakeNativeShape(shape.nativeId)
        native.apply(shape)
        native.updates = 0
        nativeById[shape.nativeId] = native
        return native
    }

    override fun update(native: NaverMapAndroidNativeShape, shape: NaverMapAndroidShape) {
        if (native !in nativeById.values) {
            detachedUpdateCalls++
            if (failOnDetachedUpdate) throw IllegalStateException("shape update used detached handle")
        }
        if (shape.id == failOnUpdateId) throw IllegalStateException("shape update failed")
        (native as FakeNativeShape).apply(shape)
    }

    override fun remove(native: NaverMapAndroidNativeShape, nativeId: String) {
        nativeById.remove(nativeId)
        removedIds += nativeId
    }

    override fun restore(shapes: List<NaverMapAndroidShape>): Map<String, NaverMapAndroidNativeShape> {
        restoreCalls++
        if (restoreCalls == failOnRestoreCall) throw IllegalStateException("shape restore failed")
        restoreFailure?.let { failure -> restoreFailure = null; throw failure }
        nativeById.clear()
        shapes.forEach {
            nativeById[it.nativeId] = FakeNativeShape(it.nativeId).also { native -> native.apply(it) }
        }
        return nativeById.toMap()
    }
}

private class FakeNativeMarker(
    override val nativeId: String,
    var model: MapMarker? = null,
) : NaverMapAndroidNativeMarker

private class FakeNativeShape(
    override val nativeId: String,
    var updates: Int = 0,
    var visible: Boolean = true,
    var globalZIndex: Int = 0,
) : NaverMapAndroidNativeShape {
    fun apply(shape: NaverMapAndroidShape) {
        updates++
        visible = shape.visible
        globalZIndex = shape.zIndex
    }
}

private class FakeViewLifecycle(
    private val eventLog: MutableList<String> = mutableListOf(),
) : NaverMapAndroidViewLifecycle {
    override val view: View = View(Application())
    val calls = mutableListOf<String>()
    var createdWith: Bundle? = null
    var failOnCreate: Throwable? = null
    var failOnPause: Throwable? = null
    var failOnStop: Throwable? = null
    var failOnDestroy: Throwable? = null

    override fun onCreate(bundle: Bundle) {
        calls += "create"
        createdWith = bundle
        failOnCreate?.let { throw it }
    }
    override fun onStart() { calls += "start" }
    override fun onResume() { calls += "resume" }
    override fun onPause() { calls += "pause"; eventLog += "pause"; failOnPause?.let { throw it } }
    override fun onStop() { calls += "stop"; eventLog += "stop"; failOnStop?.let { throw it } }
    override fun onDestroy() { calls += "destroy"; eventLog += "destroy"; failOnDestroy?.let { throw it } }
    override fun onLowMemory() { calls += "lowMemory" }
    override fun onSaveInstanceState(bundle: Bundle) { bundle.putString("saved", "yes") }
    override fun getMapAsync(callback: (NaverMapAndroidMapBridge) -> Unit) = Unit
}

private class FakeSavedStateBridge(
    private val restored: Bundle? = null,
    private val eventLog: MutableList<String> = mutableListOf(),
) : NaverMapAndroidSavedStateBridge {
    var consumeCalls = 0
    var registerCalls = 0
    var unregisterCalls = 0
    var unregisterFailure: Throwable? = null
    var registerResult = true
    var registerFailure: Throwable? = null
    override fun consumeRestoredStateForKey(key: String): Bundle? { consumeCalls++; return restored }
    override fun registerSavedStateProvider(key: String, provider: () -> Bundle): NaverMapAndroidSavedStateRegistration? {
        registerCalls++
        registerFailure?.let { throw it }
        if (!registerResult) return null
        return NaverMapAndroidSavedStateRegistration {
            unregisterCalls++
            eventLog += "saved-state-unregister"
            unregisterFailure?.let { throw it }
        }
    }
}

private class FakeComponentCallbacksBridge(
    private val eventLog: MutableList<String> = mutableListOf(),
) : NaverMapAndroidComponentCallbacksBridge {
    var registerCalls = 0
    var unregisterCalls = 0
    var lowMemoryCalls = 0
    var registerFailure: Throwable? = null
    var unregisterFailure: Throwable? = null
    private var callback: (() -> Unit)? = null
    override fun register(callback: () -> Unit) {
        registerCalls++
        registerFailure?.let { throw it }
        this.callback = callback
    }
    override fun unregister(callback: () -> Unit) {
        unregisterCalls++
        eventLog += "component-unregister"
        unregisterFailure?.let { throw it }
    }
    fun lowMemory() { callback?.invoke(); lowMemoryCalls++ }
}
