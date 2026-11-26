package run.thespot.weft.map.kakaomap

import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composition
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.snapshots.Snapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import run.thespot.weft.core.map.CameraPosition
import run.thespot.weft.core.map.LatLng
import run.thespot.weft.core.map.Marker
import run.thespot.weft.core.map.Polyline
import run.thespot.weft.core.map.Polygon
import run.thespot.weft.core.map.Circle
import run.thespot.weft.core.map.MapContent
import run.thespot.weft.core.map.MapCircle
import run.thespot.weft.core.map.MapPolygon
import run.thespot.weft.core.map.MapPolyline
import run.thespot.weft.core.map.MapMarker
import run.thespot.weft.core.map.MapScope
import run.thespot.weft.core.map.WeftMapState
import run.thespot.weft.core.map.rememberMapContent
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

class KakaoMapAndroidCoordinatorTest {
    private lateinit var mapContentFactory: MapContentFactory

    @BeforeTest
    fun resetSdkKeyRegistry() {
        KakaoMapAndroidSdkInitializer.resetForTest()
        mapContentFactory = MapContentFactory()
    }

    @AfterTest
    fun clearSdkKeyRegistry() {
        mapContentFactory.close()
        KakaoMapAndroidSdkInitializer.resetForTest()
    }

    @Test
    fun startsOnlyWhenAttachedAndResumedAndFinishesOnce() {
        val host = FakeKakaoMapAndroidHost()
        val coordinator = coordinator(host)

        assertEquals(KakaoMapAndroidCoordinatorState.Created, coordinator.state)
        coordinator.onAttach()
        assertEquals(KakaoMapAndroidCoordinatorState.Attached, coordinator.state)
        assertTrue(host.lifecycleCalls.isEmpty())

        coordinator.onResume()
        assertEquals(KakaoMapAndroidCoordinatorState.Started, coordinator.state)
        assertEquals(listOf("start"), host.lifecycleCalls)
        host.ready(KakaoMapZoomBounds(min = 3, max = 20))
        assertEquals(KakaoMapAndroidCoordinatorState.Ready, coordinator.state)
        coordinator.onPause()
        assertEquals(KakaoMapAndroidCoordinatorState.Paused, coordinator.state)
        coordinator.onPause()
        coordinator.onDetach()
        coordinator.onAttach()
        coordinator.onResume()
        assertEquals(KakaoMapAndroidCoordinatorState.Ready, coordinator.state)
        coordinator.onFinish()
        assertEquals(KakaoMapAndroidCoordinatorState.Finished, coordinator.state)
        coordinator.onFinish()
        coordinator.onResume()
        coordinator.onAttach()

        assertEquals(listOf("start", "pause", "resume", "finish"), host.lifecycleCalls)
    }

    @Test
    fun pauseBeforeStartDoesNotPauseHostAndResumeStartsExactlyOnce() {
        val host = FakeKakaoMapAndroidHost()
        val coordinator = coordinator(host)

        coordinator.onAttach()
        coordinator.onPause()
        coordinator.onDetach()
        assertTrue(host.lifecycleCalls.isEmpty())

        coordinator.onAttach()
        coordinator.onResume()
        coordinator.onResume()

        assertEquals(listOf("start"), host.lifecycleCalls)
    }

    @Test
    fun sameSdkKeyInitializesOnceAndDifferentKeyReportsOneError() {
        val firstHost = FakeKakaoMapAndroidHost()
        val secondHost = FakeKakaoMapAndroidHost()
        val thirdHost = FakeKakaoMapAndroidHost()
        var firstInitializations = 0
        var secondInitializations = 0
        var thirdInitializations = 0
        firstHost.initializeSdk = { firstInitializations++ }
        secondHost.initializeSdk = { secondInitializations++ }
        thirdHost.initializeSdk = { thirdInitializations++ }
        val errors = mutableListOf<Throwable>()
        val first = coordinator(firstHost, appKey = "same-key")
        val second = coordinator(secondHost, appKey = "same-key")
        val third = coordinator(thirdHost, appKey = "different-key", onError = { errors += it })

        first.onAttach()
        first.onResume()
        second.onAttach()
        second.onResume()
        third.onAttach()
        third.onResume()
        thirdHost.fail(IllegalStateException("second SDK key"))
        thirdHost.fail(IllegalStateException("duplicate failure"))

        assertEquals(1, firstInitializations)
        assertEquals(0, secondInitializations)
        assertEquals(0, thirdInitializations)
        assertEquals(1, errors.size)
        assertIs<KakaoMapException>(errors.single())
        assertEquals("same-key", firstHost.startedAppKeys.single())
        assertEquals("same-key", secondHost.startedAppKeys.single())
        assertEquals("different-key", thirdHost.startedAppKeys.single())
    }

    @Test
    fun latestPreReadyCameraAndContentAreRenderedOnceUsingNativeZoomBounds() {
        val host = FakeKakaoMapAndroidHost()
        val coordinator = coordinator(
            host = host,
            initialState = state(zoom = 9f, bearing = 0f),
            initialContent = content(marker("initial")),
        )
        coordinator.onAttach()
        coordinator.onResume()

        coordinator.update(
            state = state(zoom = 30.5f, bearing = 450f),
            content = content(marker("latest")),
            onError = null,
        )
        assertTrue(host.renders.isEmpty())

        host.ready(KakaoMapZoomBounds(min = 4, max = 18))

        val rendered = host.renders.single()
        assertEquals(18f, rendered.camera.zoom)
        assertEquals(90f, rendered.camera.bearing)
        assertEquals(listOf("latest"), rendered.content.markers.map(MapMarker::id))
        assertEquals(KakaoMapZoomBounds(min = 4, max = 18), host.readyBounds.single())
    }

    @Test
    fun cameraChangesAreRoundedClampedAndBearingIsNormalized() {
        val host = FakeKakaoMapAndroidHost()
        val coordinator = coordinator(host)
        coordinator.onAttach()
        coordinator.onResume()
        host.ready(KakaoMapZoomBounds(min = 5, max = 15))

        coordinator.update(state(zoom = 4.6f, bearing = -10f, tilt = 90f), emptyContent(), null)
        coordinator.update(state(zoom = 9.5f, bearing = 360f), emptyContent(), null)

        assertEquals(3, host.renders.size)
        assertEquals(5f, host.renders[1].camera.zoom)
        assertEquals(350f, host.renders[1].camera.bearing)
        assertEquals(90f, host.renders[1].camera.tilt)
        assertEquals(10f, host.renders[2].camera.zoom)
        assertEquals(0f, host.renders[2].camera.bearing)
        assertEquals(0f, host.renders[2].camera.tilt)
    }

    @Test
    fun markerOnlyUpdateDoesNotResetNativeUserPan() {
        val host = FakeKakaoMapAndroidHost()
        val commonState = state(zoom = 12f, bearing = 20f)
        val coordinator = coordinator(
            host = host,
            initialState = commonState,
            initialContent = content(marker("first")),
        )
        coordinator.onAttach()
        coordinator.onResume()
        host.ready(KakaoMapZoomBounds(min = 2, max = 18))
        assertEquals(1, host.cameraUpdates.size)

        val nativeUserPan = state(zoom = 7f, bearing = 75f).cameraPosition
        host.simulateNativePan(nativeUserPan)

        coordinator.update(
            state = commonState,
            content = content(marker("first"), marker("second")),
            onError = null,
        )

        assertEquals(1, host.cameraUpdates.size)
        assertEquals(nativeUserPan, host.nativeCamera)
        assertEquals(nativeUserPan, host.renders.last().camera)
    }

    @Test
    fun retriesLastSuccessfulCameraAfterCameraMutationAndRollbackBothFail() {
        val host = FakeKakaoMapAndroidHost()
        val cameraA = state(zoom = 12f, bearing = 20f)
        val cameraB = state(zoom = 8f, bearing = 80f)
        val markers = content(marker("one"))
        val coordinator = coordinator(host, initialState = cameraA, initialContent = markers)
        coordinator.onAttach()
        coordinator.onResume()
        host.ready(KakaoMapZoomBounds(min = 2, max = 18))
        host.failNextCameraMutationAfterApply = true
        host.failNextCameraRestore = true

        coordinator.update(cameraB, markers, onError = null)

        assertEquals(cameraB.cameraPosition, host.nativeCamera)

        coordinator.update(cameraA, markers, onError = null)

        assertEquals(cameraA.cameraPosition, host.nativeCamera)
        assertEquals(
            listOf(cameraA.cameraPosition, cameraB.cameraPosition, cameraA.cameraPosition),
            host.cameraUpdates,
        )
    }

    @Test
    fun overlayOnlyUpdateRendersWithoutResettingNativeUserPan() {
        val host = FakeKakaoMapAndroidHost()
        val coordinator = coordinator(host)
        coordinator.onAttach()
        coordinator.onResume()
        host.ready(KakaoMapZoomBounds(min = 2, max = 18))
        val rendersBeforeUpdate = host.renders.size
        val userPan = state(zoom = 7f, bearing = 35f).cameraPosition
        host.simulateNativePan(userPan)
        val cameraUpdatesBefore = host.cameraUpdates.size
        val contentWithOverlay = mapContentFactory.contentWithPolyline(
            MapPolyline("route", listOf(LatLng(37.0, 126.0), LatLng(38.0, 127.0))),
        )

        coordinator.update(state(), contentWithOverlay, onError = null)

        assertEquals(rendersBeforeUpdate + 1, host.renders.size)
        assertEquals(userPan, host.renders.last().camera)
        assertEquals(listOf("route"), host.renders.last().content.polylines.map(MapPolyline::id))
        assertEquals(cameraUpdatesBefore, host.cameraUpdates.size)
    }

    @Test
    fun markerReconciliationAddsUpdatesAndRemovesByStableId() {
        val layer = FakeTransactionalMarkerLayer()
        val reconciler = KakaoMapAndroidMarkerReconciler(layer)

        reconciler.render(content(marker("a", title = "A"), marker("b", title = "B")))
        reconciler.render(content(marker("a", title = "A2", latitude = 1.0), marker("c", title = "C")))

        assertEquals(
            listOf("add:a:A", "add:b:B", "update:a:A2", "add:c:C", "remove:b"),
            layer.operations,
        )
        assertEquals("detail", marker("a", snippet = "detail").snippet)
    }

    @Test
    fun markerReconciliationRestoresCommittedLayerWhenLaterMutationThrows() {
        val layer = FakeTransactionalMarkerLayer()
        val reconciler = KakaoMapAndroidMarkerReconciler(layer)
        val committed = marker("a", title = "before")
        reconciler.render(content(committed))
        val originalFailure = IllegalStateException("native add partially failed")
        layer.failAfterMutatingAddId = "b"
        layer.failure = originalFailure

        val failure = assertFailsWith<IllegalStateException> {
            reconciler.render(content(marker("a", title = "updated"), marker("b", title = "new")))
        }

        assertSame(originalFailure, failure)
        assertEquals(listOf(committed), layer.markers.values.toList())
        assertEquals(listOf(committed), reconciler.snapshot)
    }

    @Test
    fun failedInitialMarkerRenderRestoresTheEmptyLayer() {
        val layer = FakeTransactionalMarkerLayer()
        val reconciler = KakaoMapAndroidMarkerReconciler(layer)
        layer.failAfterMutatingAddId = "first"

        assertFailsWith<IllegalStateException> {
            reconciler.render(content(marker("first"), marker("second")))
        }

        assertTrue(layer.markers.isEmpty())
        assertTrue(reconciler.snapshot.isEmpty())
    }

    @Test
    fun cameraFailureRestoresThePriorMarkerSnapshotAndCamera() {
        val layer = FakeTransactionalMarkerLayer()
        val reconciler = KakaoMapAndroidMarkerReconciler(layer)
        val previousMarker = marker("a", title = "before")
        val nextMarker = marker("a", title = "after")
        val previousCamera = "camera-before"
        var camera = previousCamera
        reconciler.render(content(previousMarker))
        val cameraFailure = IllegalArgumentException("camera update failed")

        val failure = assertFailsWith<IllegalArgumentException> {
            reconciler.renderWithCamera(
                content = content(nextMarker),
                applyCamera = {
                    camera = "camera-after"
                    throw cameraFailure
                },
                restoreCamera = { camera = previousCamera },
            )
        }

        assertSame(cameraFailure, failure)
        assertEquals(listOf(previousMarker), layer.markers.values.toList())
        assertEquals(previousCamera, camera)
        assertEquals(listOf(previousMarker), reconciler.snapshot)
    }

    @Test
    fun cameraFailureKeepsTheOriginalErrorAndSuppressesBothRollbackFailures() {
        val layer = FakeTransactionalMarkerLayer()
        val reconciler = KakaoMapAndroidMarkerReconciler(layer)
        reconciler.render(content(marker("a", title = "before")))
        val cameraFailure = IllegalStateException("camera update failed")
        val markerRollbackFailure = IllegalArgumentException("marker restore failed")
        val cameraRollbackFailure = UnsupportedOperationException("camera restore failed")
        layer.failRestoreWith = markerRollbackFailure

        val failure = assertFailsWith<IllegalStateException> {
            reconciler.renderWithCamera(
                content = content(marker("a", title = "after")),
                applyCamera = { throw cameraFailure },
                restoreCamera = { throw cameraRollbackFailure },
            )
        }

        assertSame(cameraFailure, failure)
        assertEquals(listOf(markerRollbackFailure, cameraRollbackFailure), failure.suppressed.toList())
    }

    @Test
    fun rollbackFailureIsSuppressedOnTheOriginalRenderFailure() {
        val layer = FakeTransactionalMarkerLayer()
        val reconciler = KakaoMapAndroidMarkerReconciler(layer)
        val renderFailure = IllegalStateException("partial marker add failed")
        val rollbackFailure = IllegalArgumentException("marker restore failed")
        layer.failAfterMutatingAddId = "first"
        layer.failure = renderFailure
        layer.failRestoreWith = rollbackFailure

        val failure = assertFailsWith<IllegalStateException> {
            reconciler.render(content(marker("first")))
        }

        assertSame(renderFailure, failure)
        assertEquals(listOf(rollbackFailure), failure.suppressed.toList())
    }

    @Test
    fun nextPassRebuildsTheLayerAfterRollbackCouldNotRestoreIt() {
        val layer = FakeTransactionalMarkerLayer()
        val reconciler = KakaoMapAndroidMarkerReconciler(layer)
        val rollbackFailure = IllegalArgumentException("restore failed")
        layer.failAfterMutatingAddId = "first"
        layer.failRestoreWith = rollbackFailure

        assertFailsWith<IllegalStateException> {
            reconciler.render(content(marker("first"), marker("second")))
        }
        assertTrue(layer.markers.isEmpty())

        reconciler.render(content(marker("first"), marker("second")))

        assertEquals(listOf("first", "second"), layer.markers.keys.toList())
        assertEquals(2, layer.operations.count { it == "restore" })
    }

    @Test
    fun cameraFailureAfterShapeMutationRestoresSnapshotAndRetriesWithOneWrappedError() {
        val initial = MapPolyline("route", listOf(LatLng(37.0, 126.0), LatLng(38.0, 127.0)))
        val next = initial.copy(widthDp = 9f)
        val initialContent = mapContentFactory.contentWithOverlays(polylines = listOf(initial))
        val nextContent = mapContentFactory.contentWithOverlays(polylines = listOf(next))
        val host = FakeKakaoMapAndroidHost()
        val errors = mutableListOf<Throwable>()
        val coordinator = coordinator(host, initialContent = initialContent, onError = { errors += it })
        coordinator.onAttach()
        coordinator.onResume()
        host.ready(KakaoMapZoomBounds(min = 2, max = 18))
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
    fun shapeReconciliationUsesKindPrefixedIdsAndRemovesMissingShapes() {
        val layer = FakeKakaoMapAndroidShapeLayer()
        val reconciler = KakaoMapAndroidShapeReconciler(layer)
        val pointA = LatLng(37.0, 126.0)
        val line = MapPolyline("shared", listOf(pointA, LatLng(38.0, 127.0)))
        val polygon = MapPolygon("shared", listOf(pointA, LatLng(38.0, 127.0), LatLng(37.5, 128.0)))
        val circle = MapCircle("shared", pointA, 500.0)

        reconciler.render(mapContentFactory.contentWithOverlays(listOf(line), listOf(polygon), listOf(circle)))

        assertEquals(
            listOf("weft:polyline:shared", "weft:polygon:shared", "weft:circle:shared"),
            layer.shapes.keys.toList(),
        )
        assertTrue(layer.shapes.getValue("weft:circle:shared").isCircleApproximation)
        reconciler.render(mapContentFactory.contentWithOverlays(polylines = listOf(line.copy(widthDp = 8f))))
        assertEquals(listOf("weft:polyline:shared"), layer.shapes.keys.toList())
        assertEquals(8f, reconciler.snapshot.single().strokeWidthDp)
    }

    @Test
    fun partialShapeMutationRestoresCommittedSnapshotAndRetriesRequestedContent() {
        val layer = FakeKakaoMapAndroidShapeLayer()
        val reconciler = KakaoMapAndroidShapeReconciler(layer)
        val pointA = LatLng(37.0, 126.0)
        val committed = MapPolyline("committed", listOf(pointA, LatLng(38.0, 127.0)))
        reconciler.render(mapContentFactory.contentWithOverlays(polylines = listOf(committed)))
        val previous = layer.shapes.toMap()
        val requested = mapContentFactory.contentWithOverlays(
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
    fun lateReadyAndErrorCallbacksAfterFinishAreIgnored() {
        val host = FakeKakaoMapAndroidHost()
        val errors = mutableListOf<Throwable>()
        val coordinator = coordinator(host, onError = { errors += it })
        coordinator.onAttach()
        coordinator.onResume()
        coordinator.onFinish()

        host.ready(KakaoMapZoomBounds(min = 2, max = 18))
        host.fail(IllegalStateException("late"))

        assertTrue(host.renders.isEmpty())
        assertTrue(errors.isEmpty())
        assertEquals(1, host.lifecycleCalls.count { it == "finish" })
    }

    @Test
    fun renderErrorUsesCurrentCallbackAndIsDispatchedToMainThreadOnce() {
        val pendingMainCallbacks = mutableListOf<() -> Unit>()
        val initialErrors = mutableListOf<Throwable>()
        val replacementErrors = mutableListOf<Throwable>()
        val expectedMainThread = Thread.currentThread()
        var callbackThread: Thread? = null
        val host = FakeKakaoMapAndroidHost()
        val coordinator = coordinator(
            host = host,
            onError = {
                initialErrors += it
                callbackThread = Thread.currentThread()
            },
            dispatchToMain = { pendingMainCallbacks += it },
        )
        coordinator.onAttach()
        coordinator.onResume()
        host.ready(KakaoMapZoomBounds(min = 2, max = 18))
        pendingMainCallbacks.removeAt(0).invoke()
        host.failNextRender = true
        coordinator.update(
            state = state(zoom = 16f, tilt = 45f),
            content = emptyContent(),
            onError = { error ->
                initialErrors += error
                callbackThread = Thread.currentThread()
            },
        )
        coordinator.update(
            state = state(zoom = 16f, tilt = 45f),
            content = emptyContent(),
            onError = { error ->
                replacementErrors += error
                callbackThread = Thread.currentThread()
            },
        )
        host.fail(IllegalArgumentException("duplicate native failure"))

        assertTrue(initialErrors.isEmpty())
        assertTrue(replacementErrors.isEmpty())
        assertEquals(1, pendingMainCallbacks.size)
        pendingMainCallbacks.single().invoke()

        assertTrue(initialErrors.isEmpty())
        assertEquals(1, replacementErrors.size)
        val error = assertIs<KakaoMapException>(replacementErrors.single())
        assertNotNull(error.cause)
        assertEquals(expectedMainThread, callbackThread)
    }

    @Test
    fun nativeErrorFromWorkerThreadIsDeliveredThroughTheMainDispatcher() {
        val pendingMainCallbacks = mutableListOf<() -> Unit>()
        val errors = mutableListOf<Throwable>()
        var callbackThread: Thread? = null
        val host = FakeKakaoMapAndroidHost()
        val coordinator = coordinator(
            host = host,
            onError = {
                errors += it
                callbackThread = Thread.currentThread()
            },
            dispatchToMain = { pendingMainCallbacks += it },
        )
        coordinator.onAttach()
        coordinator.onResume()
        val nativeCallbackThread = Thread { host.fail(IllegalStateException("native failure")) }
        nativeCallbackThread.start()
        nativeCallbackThread.join()

        assertTrue(errors.isEmpty())
        assertEquals(1, pendingMainCallbacks.size)
        pendingMainCallbacks.single().invoke()

        assertEquals(1, errors.size)
        assertIs<KakaoMapException>(errors.single())
        assertEquals(Thread.currentThread(), callbackThread)
    }

    @Test
    fun missingErrorCallbackLeavesLastSuccessfulRenderIntact() {
        val host = FakeKakaoMapAndroidHost()
        val validMarker = marker("valid", title = "committed")
        val coordinator = coordinator(
            host = host,
            initialContent = content(validMarker),
        )
        coordinator.onAttach()
        coordinator.onResume()
        host.ready(KakaoMapZoomBounds(min = 2, max = 18))
        host.failAfterMutatingMarkerAddId = "failed"

        coordinator.update(
            state(zoom = 10f),
            content(validMarker.copy(title = "partially-updated"), marker("failed")),
            onError = null,
        )

        assertEquals(1, host.renders.size)
        assertEquals(listOf("valid"), host.renders.single().content.markers.map(MapMarker::id))
        assertEquals(listOf(validMarker), host.markerLayer.markers.values.toList())
        assertNull(host.lastReportedError)
    }

    @Test
    fun identicalLastValidUpdateRetriesWhenFailedRenderLeftNativeStateUncertain() {
        val host = FakeKakaoMapAndroidHost()
        val committedMarker = marker("valid", title = "committed")
        val lastValidContent = content(committedMarker)
        val coordinator = coordinator(
            host = host,
            initialState = state(),
            initialContent = lastValidContent,
        )
        coordinator.onAttach()
        coordinator.onResume()
        host.ready(KakaoMapZoomBounds(min = 2, max = 18))
        val rollbackFailure = IllegalArgumentException("partial native restore failed")
        host.markerLayer.failRestoreWith = rollbackFailure
        host.failAfterMutatingMarkerAddId = "failed"

        coordinator.update(
            state = state(zoom = 10f),
            content = content(committedMarker.copy(title = "partial"), marker("failed")),
            onError = null,
        )
        assertEquals(1, host.renders.size)
        assertTrue(host.markerLayer.markers.isEmpty())

        coordinator.update(state(), lastValidContent, onError = null)

        assertEquals(2, host.renders.size)
        assertEquals(listOf(committedMarker), host.markerLayer.markers.values.toList())
        assertEquals(listOf("valid"), host.renders.last().content.markers.map(MapMarker::id))
    }

    @Test
    fun lifecycleResumeRetriesLastValidSnapshotWhenNativeStateIsUncertain() {
        val host = FakeKakaoMapAndroidHost()
        val committedMarker = marker("valid", title = "committed")
        val lastValidContent = content(committedMarker)
        val coordinator = coordinator(
            host = host,
            initialState = state(),
            initialContent = lastValidContent,
        )
        coordinator.onAttach()
        coordinator.onResume()
        host.ready(KakaoMapZoomBounds(min = 2, max = 18))
        host.markerLayer.failRestoreWith = IllegalArgumentException("partial native restore failed")
        host.failAfterMutatingMarkerAddId = "failed"

        coordinator.update(
            state = state(zoom = 10f),
            content = content(committedMarker.copy(title = "partial"), marker("failed")),
            onError = null,
        )
        coordinator.onPause()
        coordinator.update(state(), lastValidContent, onError = null)
        assertEquals(1, host.renders.size)
        assertTrue(host.markerLayer.markers.isEmpty())

        coordinator.onResume()

        assertEquals(2, host.renders.size)
        assertEquals(listOf(committedMarker), host.markerLayer.markers.values.toList())
    }

    private fun coordinator(
        host: FakeKakaoMapAndroidHost,
        appKey: String = "test-app-key",
        initialState: WeftMapState = state(),
        initialContent: MapContent = emptyContent(),
        onError: ((Throwable) -> Unit)? = null,
        dispatchToMain: ((() -> Unit) -> Unit) = { it() },
    ): KakaoMapAndroidCoordinator = KakaoMapAndroidCoordinator(
        host = host,
        appKey = appKey,
        initialState = initialState,
        initialContent = initialContent,
        onError = onError,
        dispatchToMain = dispatchToMain,
    )

    private fun content(vararg markers: MapMarker): MapContent =
        mapContentFactory.content(markers.toList())

    private fun emptyContent(): MapContent = mapContentFactory.content(emptyList())
}

private class FakeKakaoMapAndroidHost : KakaoMapAndroidHost {
    val lifecycleCalls = mutableListOf<String>()
    val startedAppKeys = mutableListOf<String>()
    val readyBounds = mutableListOf<KakaoMapZoomBounds>()
    val renders = mutableListOf<RenderedMap>()
    val cameraUpdates = mutableListOf<CameraPosition>()
    var nativeCamera: CameraPosition? = null
        private set
    val markerLayer = FakeTransactionalMarkerLayer()
    private val markerReconciler = KakaoMapAndroidMarkerReconciler(markerLayer)
    val shapeLayer = FakeKakaoMapAndroidShapeLayer()
    private val shapeReconciler = KakaoMapAndroidShapeReconciler(shapeLayer)
    var initializeSdk: (String) -> Unit = {}
    var failNextRender: Boolean = false
    var failNextCameraMutationAfterApply: Boolean = false
    var failNextCameraRestore: Boolean = false
    var failAfterMutatingMarkerAddId: String? = null
    var lastReportedError: Throwable? = null
        private set
    private var onReady: ((KakaoMapZoomBounds) -> Unit)? = null
    private var onError: ((Throwable) -> Unit)? = null

    override fun start(
        appKey: String,
        initialCamera: CameraPosition,
        onReady: (KakaoMapZoomBounds) -> Unit,
        onError: (Throwable) -> Unit,
    ) {
        lifecycleCalls += "start"
        startedAppKeys += appKey
        this.onReady = onReady
        this.onError = onError
        try {
            KakaoMapAndroidSdkInitializer.initialize(appKey, initializeSdk)
        } catch (throwable: Throwable) {
            lastReportedError = throwable
            onError(throwable)
        }
    }

    override fun render(
        camera: CameraPosition?,
        content: MapContent,
        onCameraUpdateAttempted: () -> Unit,
    ) {
        markerLayer.failAfterMutatingAddId = failAfterMutatingMarkerAddId
        failAfterMutatingMarkerAddId = null
        val previousMarkers = markerReconciler.snapshot
        val previousShapes = shapeReconciler.snapshot
        val previousCamera = nativeCamera
        var markersRendered = false
        var shapesRendered = false
        try {
            markerReconciler.render(content)
            markersRendered = true
            shapeReconciler.render(content)
            shapesRendered = true
            if (camera != null) {
                if (failNextRender) {
                    failNextRender = false
                    throw IllegalArgumentException("camera render failed")
                }
                onCameraUpdateAttempted()
                nativeCamera = camera
                cameraUpdates += camera
                if (failNextCameraMutationAfterApply) {
                    failNextCameraMutationAfterApply = false
                    throw IllegalStateException("native camera mutation failed")
                }
            }
        } catch (failure: Throwable) {
            if (markersRendered) {
                try {
                    markerReconciler.restore(previousMarkers)
                } catch (rollbackFailure: Throwable) {
                    failure.addSuppressedSafely(rollbackFailure)
                }
            }
            if (shapesRendered) {
                try {
                    shapeReconciler.restore(previousShapes)
                } catch (rollbackFailure: Throwable) {
                    failure.addSuppressedSafely(rollbackFailure)
                }
            }
            if (camera != null && cameraUpdates.lastOrNull() == camera) {
                if (failNextCameraRestore) {
                    failNextCameraRestore = false
                    failure.addSuppressedSafely(IllegalArgumentException("native camera restore failed"))
                } else {
                    nativeCamera = previousCamera
                }
            }
            throw failure
        }
        renders += RenderedMap(checkNotNull(nativeCamera), content)
    }

    override fun resume() {
        lifecycleCalls += "resume"
    }

    override fun pause() {
        lifecycleCalls += "pause"
    }

    override fun finish() {
        lifecycleCalls += "finish"
    }

    fun ready(bounds: KakaoMapZoomBounds) {
        readyBounds += bounds
        onReady?.invoke(bounds)
    }

    fun fail(throwable: Throwable) {
        onError?.invoke(throwable)
    }

    fun simulateNativePan(camera: CameraPosition) {
        nativeCamera = camera
    }
}

private data class RenderedMap(
    val camera: CameraPosition,
    val content: MapContent,
)

private class FakeTransactionalMarkerLayer : KakaoMapAndroidMarkerLayer {
    val operations = mutableListOf<String>()
    val markers = linkedMapOf<String, MapMarker>()
    var failAfterMutatingAddId: String? = null
    var failure: Throwable = IllegalStateException("fake marker mutation failure")
    var failRestoreWith: Throwable? = null

    override fun add(marker: MapMarker) {
        operations += "add:${marker.id}:${marker.title}"
        markers[marker.id] = marker
        if (marker.id == failAfterMutatingAddId) {
            failAfterMutatingAddId = null
            throw failure
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
        operations += "restore"
        this.markers.clear()
        failRestoreWith?.let { failure ->
            failRestoreWith = null
            throw failure
        }
        markers.forEach { marker -> this.markers[marker.id] = marker }
    }
}

private fun state(
    zoom: Float = 15f,
    bearing: Float = 0f,
    tilt: Float = 0f,
): WeftMapState = WeftMapState(
    CameraPosition(
        target = LatLng(latitude = 37.5665, longitude = 126.9780),
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

private class MapContentFactory : AutoCloseable {
    private val frameClock = BroadcastFrameClock()
    private val context = Dispatchers.Unconfined + frameClock
    private val recomposer = Recomposer(context)
    private val scope = CoroutineScope(SupervisorJob() + context)
    private val recomposerJob = scope.launch { recomposer.runRecomposeAndApplyChanges() }
    private val composition = Composition(CoordinatorTestNoOpApplier, recomposer)
    private val currentContent = mutableStateOf<@Composable MapScope.() -> Unit>(
        emptyContentLambda,
        policy = neverEqualPolicy(),
    )
    private val snapshots = mutableListOf<MapContent>()
    private var frameTimeNanos = 0L

    init {
        composition.setContent {
            snapshots += rememberMapContent(currentContent.value)
        }
        recompose()
    }

    fun content(markers: List<MapMarker>): MapContent {
        currentContent.value = { markers.forEach { Marker(it) } }
        recompose()
        return snapshots.last()
    }

    fun contentWithPolyline(polyline: MapPolyline): MapContent {
        currentContent.value = { Polyline(polyline) }
        recompose()
        return snapshots.last()
    }

    fun contentWithOverlays(
        polylines: List<MapPolyline> = emptyList(),
        polygons: List<MapPolygon> = emptyList(),
        circles: List<MapCircle> = emptyList(),
    ): MapContent {
        currentContent.value = {
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

private object CoordinatorTestNoOpApplier : AbstractApplier<Unit>(Unit) {
    override fun insertTopDown(index: Int, instance: Unit) = Unit
    override fun insertBottomUp(index: Int, instance: Unit) = Unit
    override fun remove(index: Int, count: Int) = Unit
    override fun move(from: Int, to: Int, count: Int) = Unit
    override fun onClear() = Unit
}

private class FakeKakaoMapAndroidShapeLayer : KakaoMapAndroidShapeLayer {
    val shapes = linkedMapOf<String, KakaoMapOverlayShape>()
    var failAfterMutatingId: String? = null
    private val failure: Throwable = IllegalStateException("fake shape mutation failure")
    val operations = mutableListOf<String>()

    override fun add(shape: KakaoMapOverlayShape) {
        operations += "add:${shape.nativeId}"
        shapes[shape.nativeId] = shape
        if (shape.nativeId == failAfterMutatingId) {
            failAfterMutatingId = null
            throw failure
        }
    }

    override fun update(shape: KakaoMapOverlayShape) {
        operations += "update:${shape.nativeId}"
        shapes[shape.nativeId] = shape
    }

    override fun remove(nativeId: String) {
        operations += "remove:$nativeId"
        shapes.remove(nativeId)
    }

    override fun restore(shapes: List<KakaoMapOverlayShape>) {
        operations += "restore"
        this.shapes.clear()
        shapes.forEach { this.shapes[it.nativeId] = it }
    }
}

private fun MapContent.toExpectedKakaoShapes(): List<KakaoMapOverlayShape> =
    toKakaoMapOverlayShapes(polylines, polygons, circles)
