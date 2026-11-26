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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import run.thespot.weft.core.map.CameraPosition
import run.thespot.weft.core.map.LatLng
import run.thespot.weft.core.map.MapContent
import run.thespot.weft.core.map.MapMarker
import run.thespot.weft.core.map.MapScope
import run.thespot.weft.core.map.Marker
import run.thespot.weft.core.map.WeftMapState
import run.thespot.weft.core.map.rememberMapContent
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class NaverMapAndroidCoordinatorTest {
    private lateinit var contentFactory: MapContentFactory

    @BeforeTest
    fun resetSdkRegistry() {
        NaverMapAndroidSdkInitializer.resetForTest()
        contentFactory = MapContentFactory()
    }

    @AfterTest
    fun resetSdkRegistryAfterTest() {
        contentFactory.close()
        NaverMapAndroidSdkInitializer.resetForTest()
    }

    @Test
    fun startsAfterAttachAndResumeOnce() {
        val host = FakeNaverMapAndroidHost()
        val coordinator = coordinator(host)

        coordinator.onAttach()
        assertTrue(host.lifecycleCalls.isEmpty())
        coordinator.onResume()
        coordinator.onResume()

        assertEquals(NaverMapAndroidCoordinatorState.Started, coordinator.state)
        assertEquals(listOf("start"), host.lifecycleCalls)

        host.ready()
        coordinator.onPause()
        coordinator.onResume()

        assertEquals(listOf("start", "pause", "resume"), host.lifecycleCalls)
    }

    @Test
    fun pauseBeforeStartDoesNotTouchHost() {
        val host = FakeNaverMapAndroidHost()
        val coordinator = coordinator(host)

        coordinator.onAttach()
        coordinator.onPause()

        assertEquals(NaverMapAndroidCoordinatorState.Paused, coordinator.state)
        assertTrue(host.lifecycleCalls.isEmpty())

        coordinator.onResume()

        assertEquals(listOf("start"), host.lifecycleCalls)
    }

    @Test
    fun rendersLatestStateWhenMapBecomesReady() {
        val host = FakeNaverMapAndroidHost()
        val initial = state(target = LatLng(35.0, 129.0), zoom = 7f)
        val latest = state(target = LatLng(37.5665, 126.9780), zoom = 12f)
        val coordinator = coordinator(host, initialState = initial)

        coordinator.onAttach()
        coordinator.onResume()
        coordinator.update(latest, content("latest"), null)

        assertTrue(host.renders.isEmpty())

        host.ready()

        val render = host.renders.single()
        assertEquals(NaverMapAndroidCoordinatorState.Ready, coordinator.state)
        assertEquals(LatLng(37.5665, 126.9780), render.camera?.target)
        assertEquals(listOf("latest"), render.content.markers.map(MapMarker::id))
    }

    @Test
    fun normalizesCameraToNativeBounds() {
        val host = FakeNaverMapAndroidHost(
            bounds = NaverMapCameraBounds(
                minZoom = 4,
                maxZoom = 18,
                minTilt = 10f,
                maxTilt = 45f,
            ),
        )
        val coordinator = coordinator(host)
        coordinator.onAttach()
        coordinator.onResume()
        host.ready()

        coordinator.update(
            state(
                target = LatLng(33.4484, -112.0740),
                zoom = 18.6f,
                bearing = -450f,
                tilt = 90f,
            ),
            emptyContent(),
            null,
        )

        val camera = host.renders.last().camera
        assertEquals(33.4484, camera?.target?.latitude)
        assertEquals(-112.0740, camera?.target?.longitude)
        assertEquals(18f, camera?.zoom)
        assertEquals(270f, camera?.bearing)
        assertEquals(45f, camera?.tilt)
    }

    @Test
    fun contentOnlyUpdatesPassNullCameraAndPreserveUserPan() {
        val host = FakeNaverMapAndroidHost()
        val currentState = state(zoom = 12f, bearing = 20f)
        val coordinator = coordinator(host, initialState = currentState)
        coordinator.onAttach()
        coordinator.onResume()
        host.ready()

        val userCamera = state(target = LatLng(36.0, 128.0), zoom = 8f, bearing = 75f).cameraPosition
        host.nativeCamera = userCamera
        coordinator.update(currentState, content("first", "second"), null)

        assertNull(host.renders.last().camera)
        assertEquals(userCamera, host.nativeCamera)
    }

    @Test
    fun lateCallbacksAreIgnoredAfterFinish() {
        val host = FakeNaverMapAndroidHost()
        val errors = mutableListOf<Throwable>()
        val coordinator = coordinator(host, onError = { errors += it })
        coordinator.onAttach()
        coordinator.onResume()

        coordinator.onFinish()
        host.ready()
        host.fail(IllegalStateException("late native failure"))

        assertEquals(NaverMapAndroidCoordinatorState.Finished, coordinator.state)
        assertEquals(listOf("start", "finish"), host.lifecycleCalls)
        assertTrue(host.renders.isEmpty())
        assertTrue(errors.isEmpty())
    }

    @Test
    fun reportsNativeFailureOnce() {
        val host = FakeNaverMapAndroidHost()
        val errors = mutableListOf<Throwable>()
        val coordinator = coordinator(host, onError = { errors += it })
        coordinator.onAttach()
        coordinator.onResume()
        val failure = IllegalStateException("native failure")

        host.fail(failure)
        host.fail(failure)

        assertEquals(1, errors.size)
        assertIs<NaverMapException>(errors.single())
        assertSame(failure, errors.single().cause)
    }

    @Test
    fun reportsDistinctFailuresAfterSuccessfulRecovery() {
        val host = FakeNaverMapAndroidHost()
        val errors = mutableListOf<Throwable>()
        val coordinator = coordinator(host)
        coordinator.onAttach()
        coordinator.onResume()
        host.ready()

        host.failNextCameraMutation = true
        coordinator.update(state(zoom = 8f), emptyContent(), { errors += it })
        assertEquals(1, errors.size)

        coordinator.update(state(zoom = 8f), emptyContent(), { errors += it })
        assertEquals(2, host.renders.size)

        host.failNextCameraMutation = true
        coordinator.update(state(zoom = 7f), emptyContent(), { errors += it })

        assertEquals(2, errors.size)
        assertIs<NaverMapException>(errors[1])
    }

    @Test
    fun queuesNativeCallbackAndSuppressesItAfterFinish() {
        val pendingMainCallbacks = mutableListOf<() -> Unit>()
        val expectedThread = Thread.currentThread()
        val staleErrors = mutableListOf<Throwable>()
        val staleHost = FakeNaverMapAndroidHost()
        val staleCoordinator = coordinator(
            host = staleHost,
            onError = { staleErrors += it },
            dispatchToMain = { pendingMainCallbacks += it },
        )
        staleCoordinator.onAttach()
        staleCoordinator.onResume()

        val staleWorker = Thread { staleHost.fail(IllegalStateException("stale native failure")) }
        staleWorker.start()
        staleWorker.join()

        assertNotEquals(expectedThread, staleHost.lastErrorInvocationThread)
        assertTrue(staleErrors.isEmpty())
        assertEquals(1, pendingMainCallbacks.size)
        staleCoordinator.onFinish()
        pendingMainCallbacks.removeAt(0).invoke()
        assertTrue(staleErrors.isEmpty())

        val liveErrors = mutableListOf<Throwable>()
        var callbackThread: Thread? = null
        val liveHost = FakeNaverMapAndroidHost()
        val liveCoordinator = coordinator(
            host = liveHost,
            onError = {
                liveErrors += it
                callbackThread = Thread.currentThread()
            },
            dispatchToMain = { pendingMainCallbacks += it },
        )
        liveCoordinator.onAttach()
        liveCoordinator.onResume()
        val liveWorker = Thread { liveHost.fail(IllegalStateException("live native failure")) }
        liveWorker.start()
        liveWorker.join()

        assertNotEquals(expectedThread, liveHost.lastErrorInvocationThread)
        assertTrue(liveErrors.isEmpty())
        assertEquals(1, pendingMainCallbacks.size)
        pendingMainCallbacks.removeAt(0).invoke()

        assertEquals(1, liveErrors.size)
        assertEquals(expectedThread, callbackThread)
    }

    @Test
    fun queuesReadyCallbackFromWorkerUntilMainDispatcherDrains() {
        val pendingMainCallbacks = mutableListOf<() -> Unit>()
        val expectedThread = Thread.currentThread()
        val host = FakeNaverMapAndroidHost()
        val coordinator = coordinator(
            host = host,
            dispatchToMain = { pendingMainCallbacks += it },
        )
        coordinator.onAttach()
        coordinator.onResume()

        val worker = Thread { host.ready() }
        worker.start()
        worker.join()

        assertNotEquals(expectedThread, host.lastReadyInvocationThread)
        assertEquals(NaverMapAndroidCoordinatorState.Started, coordinator.state)
        assertTrue(host.renders.isEmpty())
        assertEquals(1, pendingMainCallbacks.size)

        pendingMainCallbacks.removeAt(0).invoke()

        assertEquals(NaverMapAndroidCoordinatorState.Ready, coordinator.state)
        assertEquals(1, host.renders.size)
    }

    @Test
    fun cameraRollbackRestoresPreviousCamera() {
        val host = FakeNaverMapAndroidHost()
        val coordinator = coordinator(host, initialState = state(zoom = 10f))
        coordinator.onAttach()
        coordinator.onResume()
        host.ready()
        val previousCamera = checkNotNull(host.nativeCamera)
        host.failNextCameraMutation = true

        coordinator.update(state(zoom = 8f), emptyContent(), null)

        assertEquals(previousCamera, host.nativeCamera)
    }

    @Test
    fun cameraRollbackFailureIsSuppressed() {
        val host = FakeNaverMapAndroidHost()
        val errors = mutableListOf<Throwable>()
        val coordinator = coordinator(host, onError = { errors += it })
        coordinator.onAttach()
        coordinator.onResume()
        host.ready()
        val rollbackFailure = IllegalArgumentException("camera restore failed")
        host.failNextCameraMutation = true
        host.cameraRollbackFailure = rollbackFailure

        coordinator.update(state(zoom = 8f), emptyContent(), { errors += it })

        val delivered = errors.single()
        assertIs<NaverMapException>(delivered)
        assertSame(rollbackFailure, delivered.cause?.suppressedExceptions?.single())
    }

    @Test
    fun initializesSdkBeforeMapViewCreation() {
        val events = mutableListOf<String>()
        val lifecycle = FakeHostViewLifecycle(events)
        val host = NaverMapAndroidMapViewHost(
            context = Application(),
            mapViewFactory = NaverMapAndroidMapViewFactory { events += "view"; lifecycle },
            initializeSdk = { events += "sdk:$it" },
        )

        host.createView("test-key", Bundle())

        assertEquals(listOf("sdk:test-key", "view", "create"), events)
    }

    @Test
    fun mapsLatLngAndCameraToNativeValues() {
        val lifecycle = FakeHostViewLifecycle(mutableListOf())
        val nativeMap = FakeHostMapBridge()
        val host = NaverMapAndroidMapViewHost(
            context = Application(),
            mapViewFactory = NaverMapAndroidMapViewFactory { lifecycle },
            initializeSdk = {},
        )
        host.createView("test-key", Bundle())
        host.start("test-key", state(target = LatLng(35.0, 129.0)).cameraPosition, { }, {})
        lifecycle.ready(nativeMap)

        val camera = CameraPosition(
            target = LatLng(37.5665, 126.9780),
            zoom = 12f,
            bearing = -45f,
            tilt = 30f,
        )
        host.render(camera, emptyContent()) {}

        assertEquals(NaverMapAndroidNativeLatLng(37.5665, 126.9780), nativeMap.lastCamera?.target)
        assertEquals(12.0, nativeMap.lastCamera?.zoom)
        assertEquals(30.0, nativeMap.lastCamera?.tilt)
        assertEquals(315.0, nativeMap.lastCamera?.bearing)
    }

    @Test
    fun reportsSdkFactoryFailureThroughOnErrorAndReturnsPlaceholderView() {
        val secretKey = "secret-ncp-key"
        val errors = mutableListOf<Throwable>()
        var factoryCalled = false
        val surface = NaverMapAndroidSurface(
            ncpKeyId = secretKey,
            initialState = state(),
            initialContent = emptyContent(),
            initialOnError = { errors += it },
            mapViewFactory = NaverMapAndroidMapViewFactory {
                factoryCalled = true
                FakeHostViewLifecycle(mutableListOf())
            },
            initializeSdk = { _, key -> throw IllegalStateException("SDK failed for $key") },
        )

        val placeholder = surface.createView(Application())

        assertIs<View>(placeholder)
        assertFalse(factoryCalled)
        assertEquals(1, errors.size)
        assertFalse(errors.single().toString().contains(secretKey))
    }

    private fun coordinator(
        host: FakeNaverMapAndroidHost,
        initialState: WeftMapState = state(),
        onError: ((Throwable) -> Unit)? = null,
        dispatchToMain: ((() -> Unit) -> Unit) = { it() },
    ): NaverMapAndroidCoordinator = NaverMapAndroidCoordinator(
        host = host,
        ncpKeyId = "test-ncp-key-id",
        initialState = initialState,
        initialContent = emptyContent(),
        onError = onError,
        dispatchToMain = dispatchToMain,
    )

    private fun content(vararg ids: String): MapContent =
        contentFactory.content(ids.map { id -> MapMarker(id, LatLng(37.5665, 126.9780)) })

    private fun emptyContent(): MapContent = contentFactory.content(emptyList())
}

private class FakeNaverMapAndroidHost(
    private val bounds: NaverMapCameraBounds = NaverMapCameraBounds(2, 20, 0f, 60f),
) : NaverMapAndroidHost {
    val lifecycleCalls = mutableListOf<String>()
    val renders = mutableListOf<RenderedNaverMap>()
    var nativeCamera: CameraPosition? = null
    var failNextCameraMutation = false
    var cameraRollbackFailure: Throwable? = null

    private var onReady: ((NaverMapCameraBounds) -> Unit)? = null
    private var onError: ((Throwable) -> Unit)? = null
    var lastReadyInvocationThread: Thread? = null
        private set
    var lastErrorInvocationThread: Thread? = null
        private set

    override fun start(
        ncpKeyId: String,
        initialCamera: CameraPosition,
        onReady: (NaverMapCameraBounds) -> Unit,
        onError: (Throwable) -> Unit,
    ) {
        lifecycleCalls += "start"
        this.onReady = onReady
        this.onError = onError
    }

    override fun render(
        camera: CameraPosition?,
        content: MapContent,
        onCameraUpdateAttempted: () -> Unit,
    ) {
        val previousCamera = nativeCamera
        try {
            if (camera != null) {
                onCameraUpdateAttempted()
                nativeCamera = camera
                if (failNextCameraMutation) {
                    failNextCameraMutation = false
                    throw IllegalStateException("camera mutation failed")
                }
            }
            renders += RenderedNaverMap(camera, content)
        } catch (failure: Throwable) {
            cameraRollbackFailure?.let {
                cameraRollbackFailure = null
                failure.addSuppressed(it)
            } ?: run { nativeCamera = previousCamera }
            throw failure
        }
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

    fun ready() {
        lastReadyInvocationThread = Thread.currentThread()
        onReady?.invoke(bounds)
    }

    fun fail(failure: Throwable) {
        lastErrorInvocationThread = Thread.currentThread()
        onError?.invoke(failure)
    }
}

private data class RenderedNaverMap(
    val camera: CameraPosition?,
    val content: MapContent,
)

private fun state(
    target: LatLng = LatLng(37.5665, 126.9780),
    zoom: Float = 15f,
    bearing: Float = 0f,
    tilt: Float = 0f,
): WeftMapState = WeftMapState(CameraPosition(target, zoom, bearing, tilt))

private class MapContentFactory : AutoCloseable {
    private val frameClock = BroadcastFrameClock()
    private val context = Dispatchers.Unconfined + frameClock
    private val recomposer = Recomposer(context)
    private val scope = CoroutineScope(SupervisorJob() + context)
    private val recomposerJob = scope.launch { recomposer.runRecomposeAndApplyChanges() }
    private val composition = Composition(NoOpApplier, recomposer)
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

private object NoOpApplier : AbstractApplier<Unit>(Unit) {
    override fun insertTopDown(index: Int, instance: Unit) = Unit
    override fun insertBottomUp(index: Int, instance: Unit) = Unit
    override fun remove(index: Int, count: Int) = Unit
    override fun move(from: Int, to: Int, count: Int) = Unit
    override fun onClear() = Unit
}

private class FakeHostViewLifecycle(
    private val events: MutableList<String>,
) : NaverMapAndroidViewLifecycle {
    override val view: View = View(Application())
    private var callback: ((NaverMapAndroidMapBridge) -> Unit)? = null

    override fun onCreate(bundle: Bundle) { events += "create" }
    override fun onStart() = Unit
    override fun onResume() = Unit
    override fun onPause() = Unit
    override fun onStop() = Unit
    override fun onDestroy() = Unit
    override fun onLowMemory() = Unit
    override fun onSaveInstanceState(bundle: Bundle) = Unit
    override fun getMapAsync(callback: (NaverMapAndroidMapBridge) -> Unit) { this.callback = callback }

    fun ready(map: NaverMapAndroidMapBridge) {
        callback?.invoke(map)
    }
}

private class FakeHostMapBridge : NaverMapAndroidMapBridge {
    override var cameraPosition = NaverMapAndroidNativeCameraPosition(
        target = NaverMapAndroidNativeLatLng(0.0, 0.0),
        zoom = 10.0,
        tilt = 0.0,
        bearing = 0.0,
    )
    override val minZoom = 0.0
    override val maxZoom = 20.0
    override val minTilt = 0.0
    override val maxTilt = 60.0
    var lastCamera: NaverMapAndroidNativeCameraPosition? = null

    override fun moveCamera(camera: NaverMapAndroidNativeCameraPosition) {
        lastCamera = camera
        cameraPosition = camera
    }
}
