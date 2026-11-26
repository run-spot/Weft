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
        val stableMarker = markers.nativeById.getValue("one")
        reconciler.render(second)

        assertEquals(second.markers, reconciler.snapshot.markers)
        assertEquals(second.polylines, reconciler.snapshot.shapes.filterIsInstance<NaverMapAndroidShape.Polyline>().map { it.value })
        assertSame(stableMarker, markers.nativeById.getValue("one"))
        assertEquals(listOf("two"), markers.removedIds)
        assertEquals(listOf("line"), shapes.removedIds)
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
        val markerIdentity = markers.nativeById.getValue("marker")
        val shapeIdentities = shapes.nativeById.toMap()

        reconciler.render(
            content(
                markers = listOf(marker("marker", title = "after", snippet = "details")),
                polylines = listOf(polyline("line", visible = false, zIndex = 2.6f)),
                polygons = listOf(polygon("polygon", visible = false)),
                circles = listOf(circle("circle", visible = false)),
            ),
        )

        assertSame(markerIdentity, markers.nativeById.getValue("marker"))
        assertEquals(shapeIdentities, shapes.nativeById)
        assertEquals(1, shapes.nativeById.getValue("line").updates)
        assertFalse(shapes.nativeById.getValue("line").visible)
        assertEquals(3, shapes.nativeById.getValue("line").globalZIndex)
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
        assertTrue(shapes.nativeById.getValue("line").visible)
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
        assertFalse(shapes.nativeById.getValue("line").visible)
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
    fun restoresEmptyAndRestoredSavedStateBundles() {
        val emptyView = FakeViewLifecycle()
        val emptyState = FakeSavedStateBridge()
        lifecycleBridge(emptyView, emptyState, FakeComponentCallbacksBridge()).create()
        assertEquals(0, emptyView.createdWith?.size())

        val restored = Bundle().apply { putString("camera", "restored") }
        val restoredView = FakeViewLifecycle()
        val restoredState = FakeSavedStateBridge(restored)
        lifecycleBridge(restoredView, restoredState, FakeComponentCallbacksBridge()).create()
        assertSame(restored, restoredView.createdWith)
        assertEquals(1, restoredState.consumeCalls)
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
    ) = NaverMapAndroidLifecycleBridge(
        view = view,
        savedState = state,
        componentCallbacks = callbacks,
        savedStateKey = "test-key",
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

    override fun add(marker: MapMarker): NaverMapAndroidNativeMarker {
        val native = FakeNativeMarker(marker.id)
        nativeById[marker.id] = native
        addedModels += marker
        return native
    }

    override fun update(native: NaverMapAndroidNativeMarker, marker: MapMarker) {
        (native as FakeNativeMarker).model = marker
    }

    override fun remove(native: NaverMapAndroidNativeMarker, markerId: String) {
        nativeById.remove(markerId)
        removedIds += markerId
    }

    override fun restore(markers: List<MapMarker>) {
        nativeById.clear()
        markers.forEach { nativeById[it.id] = FakeNativeMarker(it.id, it) }
    }
}

private class FakeShapeLayer : NaverMapAndroidShapeLayer {
    val nativeById = linkedMapOf<String, FakeNativeShape>()
    val removedIds = mutableListOf<String>()
    var restoreCalls = 0
    var failOnUpdateId: String? = null
    var failOnRestoreCall: Int? = null

    override fun add(shape: NaverMapAndroidShape): NaverMapAndroidNativeShape {
        val native = FakeNativeShape(shape.id)
        native.apply(shape)
        native.updates = 0
        nativeById[shape.id] = native
        return native
    }

    override fun update(native: NaverMapAndroidNativeShape, shape: NaverMapAndroidShape) {
        if (shape.id == failOnUpdateId) throw IllegalStateException("shape update failed")
        (native as FakeNativeShape).apply(shape)
    }

    override fun remove(native: NaverMapAndroidNativeShape, shapeId: String) {
        nativeById.remove(shapeId)
        removedIds += shapeId
    }

    override fun restore(shapes: List<NaverMapAndroidShape>) {
        restoreCalls++
        if (restoreCalls == failOnRestoreCall) throw IllegalStateException("shape restore failed")
        nativeById.clear()
        shapes.forEach {
            nativeById[it.id] = FakeNativeShape(it.id).also { native -> native.apply(it) }
        }
    }
}

private class FakeNativeMarker(
    val id: String,
    var model: MapMarker? = null,
) : NaverMapAndroidNativeMarker

private class FakeNativeShape(
    val id: String,
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

private class FakeViewLifecycle : NaverMapAndroidViewLifecycle {
    override val view: View = View(Application())
    val calls = mutableListOf<String>()
    var createdWith: Bundle? = null

    override fun onCreate(bundle: Bundle) { calls += "create"; createdWith = bundle }
    override fun onStart() { calls += "start" }
    override fun onResume() { calls += "resume" }
    override fun onPause() { calls += "pause" }
    override fun onStop() { calls += "stop" }
    override fun onDestroy() { calls += "destroy" }
    override fun onLowMemory() { calls += "lowMemory" }
    override fun onSaveInstanceState(bundle: Bundle) { bundle.putString("saved", "yes") }
    override fun getMapAsync(callback: (NaverMapAndroidMapBridge) -> Unit) = Unit
}

private class FakeSavedStateBridge(
    private val restored: Bundle? = null,
) : NaverMapAndroidSavedStateBridge {
    var consumeCalls = 0
    var registerCalls = 0
    var unregisterCalls = 0
    override fun consumeRestoredStateForKey(key: String): Bundle? { consumeCalls++; return restored }
    override fun registerSavedStateProvider(key: String, provider: () -> Bundle): Boolean {
        registerCalls++
        return true
    }
    override fun unregisterSavedStateProvider(key: String) { unregisterCalls++ }
}

private class FakeComponentCallbacksBridge : NaverMapAndroidComponentCallbacksBridge {
    var registerCalls = 0
    var unregisterCalls = 0
    var lowMemoryCalls = 0
    private var callback: (() -> Unit)? = null
    override fun register(callback: () -> Unit) { registerCalls++; this.callback = callback }
    override fun unregister(callback: () -> Unit) { unregisterCalls++ }
    fun lowMemory() { callback?.invoke(); lowMemoryCalls++ }
}
