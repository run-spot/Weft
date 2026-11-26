package run.thespot.weft.map.kakaomap

import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Composition
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import run.thespot.weft.core.map.CameraPosition
import run.thespot.weft.core.map.LatLng
import run.thespot.weft.core.map.MapContent
import run.thespot.weft.core.map.MapCircle
import run.thespot.weft.core.map.MapPolygon
import run.thespot.weft.core.map.MapPolyline
import run.thespot.weft.core.map.MapMarker
import run.thespot.weft.core.map.Marker
import run.thespot.weft.core.map.Polyline
import run.thespot.weft.core.map.Polygon
import run.thespot.weft.core.map.Circle
import run.thespot.weft.core.map.WeftMapState
import run.thespot.weft.core.map.rememberMapContent
import kotlin.math.PI
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class KakaoMapIosCoordinatorTest {
    private lateinit var contentFactory: MapContentFactory

    @BeforeTest
    fun setUp() {
        KakaoMapIosSdkInitializer.resetForTest()
        contentFactory = MapContentFactory()
    }

    @AfterTest
    fun tearDown() {
        contentFactory.close()
        KakaoMapIosSdkInitializer.resetForTest()
    }

    @Test
    fun zeroBoundsDefersPrepareAndNonZeroLayoutPreparesOnce() {
        val host = FakeKakaoMapIosHost()
        val coordinator = coordinator(host)

        coordinator.onWindowAttached(width = 0.0, height = 0.0)

        assertEquals(KakaoMapIosCoordinatorState.AttachedZeroBounds, coordinator.state)
        assertTrue(host.calls.isEmpty())

        coordinator.onLayout(width = 320.0, height = 640.0)
        coordinator.onLayout(width = 320.0, height = 640.0)

        assertEquals(KakaoMapIosCoordinatorState.Prepared, coordinator.state)
        assertEquals(listOf("prepare", "activate"), host.calls)
        assertEquals(1, host.startedAppKeys.size)
    }

    @Test
    fun windowDetachPausesAndReattachReactivatesWithoutPreparingAgain() {
        val host = FakeKakaoMapIosHost()
        val coordinator = coordinator(host)
        coordinator.onWindowAttached(width = 320.0, height = 640.0)
        host.ready(KakaoMapIosZoomBounds(min = 3, max = 18))

        coordinator.onWindowDetached()

        assertEquals(KakaoMapIosCoordinatorState.Paused, coordinator.state)
        assertEquals(1, host.calls.count { it == "pause" })

        coordinator.onWindowAttached(width = 320.0, height = 640.0)

        assertEquals(KakaoMapIosCoordinatorState.Ready, coordinator.state)
        assertEquals(1, host.calls.count { it == "prepare" })
        assertEquals(2, host.calls.count { it == "activate" })
        assertEquals(listOf("prepare", "activate", "pause", "activate"), host.calls)
    }

    @Test
    fun latestCameraAndContentAreRenderedOnlyAfterNativeViewIsReady() {
        val host = FakeKakaoMapIosHost()
        val coordinator = coordinator(host, initialState = state(zoom = 5f))
        coordinator.onWindowAttached(width = 320.0, height = 640.0)
        coordinator.update(
            state = state(zoom = 25f, bearing = 450f),
            content = content(marker("latest")),
            onError = null,
        )

        assertTrue(host.renders.isEmpty())

        host.ready(KakaoMapIosZoomBounds(min = 2, max = 18))

        assertEquals(KakaoMapIosCoordinatorState.Ready, coordinator.state)
        assertEquals(1, host.renders.size)
        assertEquals(18f, checkNotNull(host.renders.single().camera).zoom)
        assertEquals(90f, checkNotNull(host.renders.single().camera).bearing)
        assertEquals(listOf("latest"), host.renders.single().content.markers.map(MapMarker::id))
    }

    @Test
    fun markerOnlyUpdateAndFailedSnapshotRetryDoNotResetUserCamera() {
        val host = FakeKakaoMapIosHost()
        val coordinator = coordinator(host)
        coordinator.onWindowAttached(width = 320.0, height = 640.0)
        host.ready(KakaoMapIosZoomBounds(min = 2, max = 18))
        val cameraApplicationsAfterReady = host.cameraApplications

        // Model a pan performed by the native map after the last common state was applied.
        host.userPanCount++
        host.failNextRender = IllegalStateException("marker pass failed")
        val markerOnlyContent = content(marker("marker-only"))
        coordinator.update(state(), markerOnlyContent, onError = null)

        assertEquals(cameraApplicationsAfterReady, host.cameraApplications)
        assertEquals(null, host.renderAttempts.last().camera)

        val renderCountAfterFailure = host.renders.size
        coordinator.update(state(), markerOnlyContent, onError = null)

        assertEquals(renderCountAfterFailure + 1, host.renders.size)
        assertEquals(null, host.renders.last().camera)
        assertEquals(cameraApplicationsAfterReady, host.cameraApplications)
        assertEquals(1, host.userPanCount)
    }

    @Test
    fun overlayOnlyUpdateRendersWithoutResettingNativeUserCamera() {
        val host = FakeKakaoMapIosHost()
        val coordinator = coordinator(host)
        coordinator.onWindowAttached(width = 320.0, height = 640.0)
        host.ready(KakaoMapIosZoomBounds(min = 2, max = 18))
        val rendersBeforeUpdate = host.renders.size
        val cameraApplications = host.cameraApplications
        val contentWithOverlay = contentFactory.contentWithPolyline(
            MapPolyline("route", listOf(LatLng(37.0, 126.0), LatLng(38.0, 127.0))),
        )

        coordinator.update(state(), contentWithOverlay, onError = null)

        assertEquals(rendersBeforeUpdate + 1, host.renders.size)
        assertEquals(null, host.renders.last().camera)
        assertEquals(cameraApplications, host.cameraApplications)
        assertEquals(listOf("route"), host.renders.last().content.polylines.map(MapPolyline::id))
    }

    @Test
    fun cameraMapperConvertsCoordinatesZoomAnglesAndNonAnimatedMode() {
        val fractionalZoom = state(zoom = 8.6f).cameraPosition
        assertEquals(9L, fractionalZoom.toKakaoMapIosDefaultLevel())
        assertEquals(
            9,
            fractionalZoom.toKakaoMapIosCameraUpdate(KakaoMapIosZoomBounds(min = 4, max = 18)).zoomLevel,
        )

        val converted = state(
            latitude = -33.8688,
            longitude = 151.2093,
            zoom = 20.6f,
            bearing = -90f,
            tilt = 60f,
        ).cameraPosition.toKakaoMapIosCameraUpdate(KakaoMapIosZoomBounds(min = 4, max = 18))

        assertEquals(151.2093, converted.longitude)
        assertEquals(-33.8688, converted.latitude)
        assertEquals(18, converted.zoomLevel)
        assertEquals(3.0 * PI / 2.0, converted.rotationRadians, 0.00001)
        assertEquals(PI / 3.0, converted.tiltRadians, 0.00001)
        assertEquals(false, converted.animated)

        val lowerBound = state(zoom = 0.4f, bearing = 360f, tilt = 90f)
            .cameraPosition.toKakaoMapIosCameraUpdate(KakaoMapIosZoomBounds(min = 4, max = 18))

        assertEquals(4, lowerBound.zoomLevel)
        assertEquals(0.0, lowerBound.rotationRadians, 0.00001)
        assertEquals(PI / 2.0, lowerBound.tiltRadians, 0.00001)
    }

    @Test
    fun cameraFailureAfterShapeMutationRestoresSnapshotAndRetriesWithOneWrappedError() {
        val initial = MapPolyline("route", listOf(LatLng(37.0, 126.0), LatLng(38.0, 127.0)))
        val next = initial.copy(widthDp = 9f)
        val initialContent = contentFactory.contentWithOverlays(polylines = listOf(initial))
        val nextContent = contentFactory.contentWithOverlays(polylines = listOf(next))
        val host = FakeKakaoMapIosHost()
        val errors = mutableListOf<Throwable>()
        val coordinator = coordinator(host, initialContent = initialContent, onError = { errors += it })
        coordinator.onWindowAttached(width = 320.0, height = 640.0)
        host.ready(KakaoMapIosZoomBounds(min = 2, max = 18))
        val committedShapes = host.shapeLayer.shapes.toMap()
        assertEquals(listOf("weft:polyline:route"), committedShapes.keys.toList())
        host.failNextCameraMutationAfterApply = true

        coordinator.update(state(zoom = 8f), nextContent, onError = { errors += it })

        assertEquals(committedShapes, host.shapeLayer.shapes)
        assertEquals(1, errors.size)
        assertIs<KakaoMapException>(errors.single())

        coordinator.update(state(zoom = 8f), nextContent, onError = { errors += it })

        assertEquals(listOf("weft:polyline:route"), host.shapeLayer.shapes.keys.toList())
        assertEquals(9f, host.shapeLayer.shapes.values.single().strokeWidthDp)
        assertEquals(1, errors.size)
    }

    @Test
    fun shapeReconciliationUsesKindPrefixedIdsAndRetainsHiddenShapes() {
        val layer = FakeKakaoMapIosShapeLayer()
        val reconciler = KakaoMapIosShapeReconciler(layer)
        val pointA = LatLng(37.0, 126.0)
        val line = MapPolyline("shared", listOf(pointA, LatLng(38.0, 127.0)), visible = false)
        val polygon = MapPolygon("shared", listOf(pointA, LatLng(38.0, 127.0), LatLng(37.5, 128.0)))
        val circle = MapCircle("shared", pointA, 500.0)

        reconciler.render(contentFactory.contentWithOverlays(listOf(line), listOf(polygon), listOf(circle)))

        assertEquals(
            listOf("weft:polyline:shared", "weft:polygon:shared", "weft:circle:shared"),
            layer.shapes.keys.toList(),
        )
        assertEquals(false, layer.shapes.getValue("weft:polyline:shared").visible)
        assertTrue(layer.shapes.getValue("weft:circle:shared").isCircleApproximation)
        reconciler.render(contentFactory.contentWithOverlays(polylines = listOf(line.copy(visible = true))))
        assertEquals(listOf("weft:polyline:shared"), layer.shapes.keys.toList())
        assertTrue(reconciler.snapshot.single().visible)
    }

    @Test
    fun iosOverlayStyleMappingPreservesColorAlphaAndRoundsWidthAndZOrder() {
        val translucent = androidx.compose.ui.graphics.Color(
            red = 1f,
            green = 0.5f,
            blue = 0.25f,
            alpha = 0.4f,
        )

        val rgba = translucent.toKakaoMapIosOverlayColor()

        assertEquals(1.0, rgba.red)
        assertEquals(0.5, rgba.green)
        assertEquals(0.25, rgba.blue)
        assertTrue(kotlin.math.abs(rgba.alpha - 0.4) < 0.000001)
        assertEquals(3uL, 2.6f.toKakaoMapIosStrokeWidth())
        assertEquals(3, 2.6f.toKakaoMapIosZOrder())
    }

    @Test
    fun partialShapeFailureRestoresPriorSnapshotAndNextPassRetries() {
        val layer = FakeKakaoMapIosShapeLayer()
        val reconciler = KakaoMapIosShapeReconciler(layer)
        val pointA = LatLng(37.0, 126.0)
        val committed = MapPolyline("committed", listOf(pointA, LatLng(38.0, 127.0)))
        reconciler.render(contentFactory.contentWithOverlays(polylines = listOf(committed)))
        val previous = layer.shapes.toMap()
        val requested = contentFactory.contentWithOverlays(
            polylines = listOf(committed.copy(widthDp = 8f)),
            polygons = listOf(MapPolygon("partial", listOf(pointA, LatLng(38.0, 127.0), LatLng(37.5, 128.0)))),
        )
        layer.failAfterMutatingId = "weft:polygon:partial"

        assertFailsWith<IllegalStateException> { reconciler.render(requested) }
        assertEquals(previous, layer.shapes)
        assertEquals(previous.values.toList(), reconciler.snapshot)

        reconciler.render(requested)

        assertEquals(requested.toExpectedKakaoShapes(), layer.shapes.values.toList())
        assertEquals(layer.shapes.values.toList(), reconciler.snapshot)
    }

    @Test
    fun markerReconcilerUpdatesAndRemovesByIdWithDeterministicStyleAndLayer() {
        val layer = FakeKakaoMapIosMarkerLayer()
        val reconciler = KakaoMapIosMarkerReconciler(layer)

        reconciler.render(content(marker("a", title = "before")))
        reconciler.render(content(marker("a", title = "after", latitude = 1.0)))
        reconciler.render(content(marker("b", title = "new")))
        reconciler.render(emptyContent())

        assertEquals(
            listOf("add:a:before", "update:a:after", "add:b:new", "remove:a", "remove:b"),
            layer.operations,
        )
        assertEquals(KAKAO_MAP_IOS_STYLE_ID, layer.styleId)
        assertEquals(KAKAO_MAP_IOS_LAYER_ID, layer.layerId)
        assertEquals("details", marker("a", snippet = "details").snippet)
    }

    @Test
    fun failedMarkerPassRestoresTheLastCommittedSnapshot() {
        val layer = FakeKakaoMapIosMarkerLayer()
        val reconciler = KakaoMapIosMarkerReconciler(layer)
        val committed = marker("committed", title = "valid")
        reconciler.render(content(committed))
        layer.failAfterMutatingId = "partial"
        val failure = IllegalStateException("native add failed after mutation")
        layer.nextFailure = failure

        val thrown = assertFailsWith<IllegalStateException> {
            reconciler.render(content(marker("committed", title = "changed"), marker("partial")))
        }

        assertSame(failure, thrown)
        assertEquals(listOf(committed), layer.markers.values.toList())
        assertEquals(listOf(committed), reconciler.snapshot)
    }

    @Test
    fun sameSdkKeyInitializesOnceAndDifferentKeyReportsOneError() {
        val firstHost = FakeKakaoMapIosHost()
        val secondHost = FakeKakaoMapIosHost()
        val thirdHost = FakeKakaoMapIosHost()
        val errors = mutableListOf<Throwable>()
        val first = coordinator(firstHost, appKey = "same-key")
        val second = coordinator(secondHost, appKey = "same-key")
        val third = coordinator(thirdHost, appKey = "different-key", onError = { errors += it })

        first.onWindowAttached(width = 320.0, height = 640.0)
        second.onWindowAttached(width = 320.0, height = 640.0)
        third.onWindowAttached(width = 320.0, height = 640.0)
        thirdHost.fail(IllegalStateException("different SDK key"))
        thirdHost.fail(IllegalStateException("duplicate SDK failure"))

        assertEquals(1, firstHost.sdkInitializations)
        assertEquals(0, secondHost.sdkInitializations)
        assertEquals(0, thirdHost.sdkInitializations)
        assertEquals(listOf("same-key"), firstHost.startedAppKeys)
        assertEquals(listOf("same-key"), secondHost.startedAppKeys)
        assertEquals(listOf("different-key"), thirdHost.startedAppKeys)
        assertEquals(1, errors.size)
        assertIs<KakaoMapException>(errors.single())
    }

    @Test
    fun authenticationAndAddViewFailuresAreWrappedAndDeliveredOnce() {
        val host = FakeKakaoMapIosHost()
        val errors = mutableListOf<Throwable>()
        val coordinator = coordinator(host, onError = { errors += it })
        coordinator.onWindowAttached(width = 320.0, height = 640.0)

        host.fail(IllegalArgumentException("authenticationFailed"))
        host.fail(IllegalStateException("addViewFailed"))

        assertEquals(1, errors.size)
        assertIs<KakaoMapException>(errors.single())
        assertNotNull(errors.single().cause)
    }

    @Test
    fun renderFailureUsesReplacementCallbackAndDispatchesThroughMainQueue() {
        val pendingMainCallbacks = mutableListOf<() -> Unit>()
        val originalErrors = mutableListOf<Throwable>()
        val replacementErrors = mutableListOf<Throwable>()
        val host = FakeKakaoMapIosHost()
        val coordinator = coordinator(
            host = host,
            onError = { originalErrors += it },
            dispatchToMain = { pendingMainCallbacks += it },
        )
        coordinator.onWindowAttached(width = 320.0, height = 640.0)
        host.ready(KakaoMapIosZoomBounds(min = 2, max = 18))
        assertEquals(1, pendingMainCallbacks.size)
        pendingMainCallbacks.removeAt(0).invoke()
        assertEquals(1, host.renders.size)

        host.failNextRender = IllegalArgumentException("render failure")

        coordinator.update(state(zoom = 12f), content(marker("changed")), onError = { replacementErrors += it })
        host.fail(IllegalStateException("duplicate error"))

        assertTrue(originalErrors.isEmpty())
        assertTrue(replacementErrors.isEmpty())
        assertEquals(1, pendingMainCallbacks.size)

        coordinator.update(state(zoom = 12f), content(marker("changed")), onError = { replacementErrors += it })
        pendingMainCallbacks.single().invoke()

        assertTrue(originalErrors.isEmpty())
        assertEquals(1, replacementErrors.size)
        assertIs<KakaoMapException>(replacementErrors.single())
    }

    @Test
    fun lateCallbacksAfterResetAreIgnoredAndResetIsIdempotent() {
        val host = FakeKakaoMapIosHost()
        val errors = mutableListOf<Throwable>()
        val coordinator = coordinator(host, onError = { errors += it })
        coordinator.onWindowAttached(width = 320.0, height = 640.0)
        coordinator.reset()
        coordinator.reset()

        host.ready(KakaoMapIosZoomBounds(min = 2, max = 18))
        host.fail(IllegalStateException("late"))

        assertEquals(KakaoMapIosCoordinatorState.Reset, coordinator.state)
        assertTrue(host.renders.isEmpty())
        assertTrue(errors.isEmpty())
        assertEquals(1, host.calls.count { it == "reset" })
    }

    private fun coordinator(
        host: FakeKakaoMapIosHost,
        appKey: String = "test-ios-key",
        initialState: WeftMapState = state(),
        initialContent: MapContent = emptyContent(),
        onError: ((Throwable) -> Unit)? = null,
        dispatchToMain: ((() -> Unit) -> Unit) = { it() },
    ): KakaoMapIosCoordinator = KakaoMapIosCoordinator(
        host = host,
        appKey = appKey,
        initialState = initialState,
        initialContent = initialContent,
        onError = onError,
        dispatchToMain = dispatchToMain,
    )

    private fun state(
        latitude: Double = 37.5665,
        longitude: Double = 126.9780,
        zoom: Float = 15f,
        bearing: Float = 0f,
        tilt: Float = 0f,
    ): WeftMapState = WeftMapState(
        CameraPosition(
            target = LatLng(latitude = latitude, longitude = longitude),
            zoom = zoom,
            bearing = bearing,
            tilt = tilt,
        ),
    )

    private fun marker(
        id: String,
        title: String? = null,
        snippet: String? = null,
        latitude: Double = 37.5665,
    ): MapMarker = MapMarker(
        id = id,
        position = LatLng(latitude = latitude, longitude = 126.9780),
        title = title,
        snippet = snippet,
    )

    private fun content(vararg markers: MapMarker): MapContent = contentFactory.content(markers.toList())

    private fun emptyContent(): MapContent = contentFactory.content(emptyList())
}

private data class KakaoMapIosRender(
    val camera: CameraPosition?,
    val content: MapContent,
)

private class FakeKakaoMapIosHost : KakaoMapIosHost {
    val calls = mutableListOf<String>()
    val startedAppKeys = mutableListOf<String>()
    val renders = mutableListOf<KakaoMapIosRender>()
    val renderAttempts = mutableListOf<KakaoMapIosRender>()
    var cameraApplications = 0
    var userPanCount = 0
    var sdkInitializations = 0
    val shapeLayer = FakeKakaoMapIosShapeLayer()
    private val shapeReconciler = KakaoMapIosShapeReconciler(shapeLayer)
    var failNextCameraMutationAfterApply: Boolean = false
    var failNextRender: Throwable? = null
    private var onReady: ((KakaoMapIosZoomBounds) -> Unit)? = null
    private var onError: ((Throwable) -> Unit)? = null

    override fun prepare(
        appKey: String,
        initialCamera: CameraPosition,
        onReady: (KakaoMapIosZoomBounds) -> Unit,
        onError: (Throwable) -> Unit,
    ) {
        calls += "prepare"
        startedAppKeys += appKey
        this.onReady = onReady
        this.onError = onError
        try {
            KakaoMapIosSdkInitializer.initialize(appKey) { sdkInitializations++ }
        } catch (throwable: Throwable) {
            onError(throwable)
        }
    }

    override fun activate() {
        calls += "activate"
    }

    override fun render(camera: CameraPosition?, content: MapContent) {
        val render = KakaoMapIosRender(camera, content)
        renderAttempts += render
        val previousShapes = shapeReconciler.snapshot
        try {
            shapeReconciler.render(content)
            failNextRender?.let { failure ->
                failNextRender = null
                throw failure
            }
            if (camera != null) {
                cameraApplications++
                if (failNextCameraMutationAfterApply) {
                    failNextCameraMutationAfterApply = false
                    throw IllegalStateException("native camera mutation failed")
                }
            }
        } catch (failure: Throwable) {
            try {
                shapeReconciler.restore(previousShapes)
            } catch (rollbackFailure: Throwable) {
                if (failure !== rollbackFailure) failure.addSuppressed(rollbackFailure)
            }
            throw failure
        }
        renders += render
    }

    override fun pause() {
        calls += "pause"
    }

    override fun reset() {
        calls += "reset"
    }

    fun ready(bounds: KakaoMapIosZoomBounds) {
        onReady?.invoke(bounds)
    }

    fun fail(throwable: Throwable) {
        onError?.invoke(throwable)
    }
}

private class FakeKakaoMapIosMarkerLayer : KakaoMapIosMarkerLayer {
    val operations = mutableListOf<String>()
    val markers = linkedMapOf<String, MapMarker>()
    override val styleId: String = KAKAO_MAP_IOS_STYLE_ID
    override val layerId: String = KAKAO_MAP_IOS_LAYER_ID
    var failAfterMutatingId: String? = null
    var nextFailure: Throwable? = null

    override fun add(marker: MapMarker) {
        operations += "add:${marker.id}:${marker.title}"
        markers[marker.id] = marker
        if (marker.id == failAfterMutatingId) {
            failAfterMutatingId = null
            throw checkNotNull(nextFailure)
        }
    }

    override fun update(marker: MapMarker) {
        operations += "update:${marker.id}:${marker.title}"
        markers[marker.id] = marker
    }

    override fun remove(markerId: String) {
        operations += "remove:$markerId"
        markers.remove(markerId)
    }

    override fun restore(markers: List<MapMarker>) {
        this.markers.clear()
        markers.forEach { this.markers[it.id] = it }
    }
}

private class MapContentFactory : AutoCloseable {
    private val frameClock = BroadcastFrameClock()
    private val context = Dispatchers.Unconfined + frameClock
    private val recomposer = Recomposer(context)
    private val scope = CoroutineScope(SupervisorJob() + context)
    private val recomposerJob = scope.launch { recomposer.runRecomposeAndApplyChanges() }
    private val composition = Composition(NoOpApplier, recomposer)
    private val markers = mutableStateOf(emptyList<MapMarker>())
    private val polylines = mutableStateOf(emptyList<MapPolyline>())
    private val polygons = mutableStateOf(emptyList<MapPolygon>())
    private val circles = mutableStateOf(emptyList<MapCircle>())
    private val content = mutableStateOf<MapContent?>(null)
    private var frameTimeNanos = 0L

    init {
        composition.setContent {
            val snapshot = rememberMapContent {
                markers.value.forEach { Marker(it) }
                polylines.value.forEach { Polyline(it) }
                polygons.value.forEach { Polygon(it) }
                circles.value.forEach { Circle(it) }
            }
            SideEffect { content.value = snapshot }
        }
        recompose()
    }

    fun content(markers: List<MapMarker>): MapContent {
        this.markers.value = markers
        recompose()
        return checkNotNull(content.value) { "Map content was not published by composition." }
    }

    fun contentWithPolyline(polyline: MapPolyline): MapContent =
        contentWithOverlays(polylines = listOf(polyline))

    fun contentWithOverlays(
        polylines: List<MapPolyline> = emptyList(),
        polygons: List<MapPolygon> = emptyList(),
        circles: List<MapCircle> = emptyList(),
    ): MapContent {
        markers.value = emptyList()
        this.polylines.value = polylines
        this.polygons.value = polygons
        this.circles.value = circles
        recompose()
        return checkNotNull(content.value) { "Map content was not published by composition." }
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

private object NoOpApplier : AbstractApplier<Unit>(Unit) {
    override fun insertTopDown(index: Int, instance: Unit) = Unit
    override fun insertBottomUp(index: Int, instance: Unit) = Unit
    override fun remove(index: Int, count: Int) = Unit
    override fun move(from: Int, to: Int, count: Int) = Unit
    override fun onClear() = Unit
}

private class FakeKakaoMapIosShapeLayer : KakaoMapIosShapeLayer {
    val shapes = linkedMapOf<String, KakaoMapOverlayShape>()
    var failAfterMutatingId: String? = null
    private val failure: Throwable = IllegalStateException("fake shape mutation failure")

    override fun add(shape: KakaoMapOverlayShape) {
        shapes[shape.nativeId] = shape
        if (shape.nativeId == failAfterMutatingId) {
            failAfterMutatingId = null
            throw failure
        }
    }

    override fun update(shape: KakaoMapOverlayShape) {
        shapes[shape.nativeId] = shape
    }

    override fun remove(nativeId: String) {
        shapes.remove(nativeId)
    }

    override fun restore(shapes: List<KakaoMapOverlayShape>) {
        this.shapes.clear()
        shapes.forEach { this.shapes[it.nativeId] = it }
    }
}

private fun MapContent.toExpectedKakaoShapes(): List<KakaoMapOverlayShape> =
    toKakaoMapOverlayShapes(polylines, polygons, circles)
