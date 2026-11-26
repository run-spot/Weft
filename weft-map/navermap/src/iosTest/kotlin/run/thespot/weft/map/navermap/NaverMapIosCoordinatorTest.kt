package run.thespot.weft.map.navermap

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
import run.thespot.weft.core.map.MapMarker
import run.thespot.weft.core.map.Marker
import run.thespot.weft.core.map.WeftMapState
import run.thespot.weft.core.map.rememberMapContent
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NaverMapIosCoordinatorTest {
    private lateinit var contents: ContentFactory

    @BeforeTest fun setUp() { contents = ContentFactory() }
    @AfterTest fun tearDown() { contents.close() }

    @Test
    fun waitsForWindowAndNonZeroBoundsAndPreparesOncePerGeneration() {
        val host = FakeHost()
        val coordinator = coordinator(host)
        coordinator.onWindowAttached(0.0, 0.0)
        assertTrue(host.calls.isEmpty())
        coordinator.onLayout(320.0, 640.0)
        coordinator.onLayout(320.0, 640.0)
        assertEquals(listOf("prepare", "activate"), host.calls)
        assertEquals(1, host.preparations)
        coordinator.reset()
        coordinator.onWindowAttached(320.0, 640.0)
        assertEquals(1, host.preparations)
    }

    @Test
    fun pausesOnDetachAndRendersLatestCameraAndContentWhenReady() {
        val host = FakeHost()
        val coordinator = coordinator(host)
        coordinator.onWindowAttached(320.0, 640.0)
        coordinator.update(
            WeftMapState(CameraPosition(LatLng(37.5, 127.0), zoom = 25f, bearing = 450f)),
            contents.content("latest"),
            null,
        )
        assertTrue(host.renders.isEmpty())
        host.ready()
        assertEquals(1, host.renders.size)
        assertEquals(20f, host.renders.single().camera?.zoom)
        assertEquals(90f, host.renders.single().camera?.bearing)
        assertEquals(listOf("latest"), host.renders.single().content.markers.map(MapMarker::id))
        coordinator.update(
            WeftMapState(CameraPosition(LatLng(37.5, 127.0), zoom = 25f, bearing = 450f)),
            contents.content("latest", "second"),
            null,
        )
        assertEquals(null, host.renders.last().camera)
        coordinator.onWindowDetached()
        assertEquals(1, host.calls.count { it == "pause" })
        coordinator.onWindowAttached(320.0, 640.0)
        assertEquals(1, host.preparations)
        assertEquals(2, host.calls.count { it == "activate" })
    }

    @Test
    fun reportsOnlyOneErrorForAFailedGeneration() {
        val host = FakeHost()
        val errors = mutableListOf<Throwable>()
        val coordinator = coordinator(host, onError = errors::add)
        coordinator.onWindowAttached(320.0, 640.0)
        host.failNextRender = IllegalStateException("first render failed")
        host.ready()
        coordinator.update(
            WeftMapState(CameraPosition(LatLng(37.5, 127.0), zoom = 16f)),
            contents.content("retry"),
            errors::add,
        )
        assertEquals(1, errors.size)
        assertTrue(errors.single() is NaverMapException)
    }

    @Test
    fun staleReadyCallbackAndFailuresAreIgnoredAfterReset() {
        val host = FakeHost()
        val errors = mutableListOf<Throwable>()
        val coordinator = coordinator(host, onError = errors::add)
        coordinator.onWindowAttached(320.0, 640.0)
        coordinator.reset()
        host.ready()
        host.fail(IllegalStateException("late"))
        assertTrue(host.renders.isEmpty())
        assertTrue(errors.isEmpty())
    }

    private fun coordinator(host: FakeHost, onError: ((Throwable) -> Unit)? = null) =
        NaverMapIosCoordinator(
            host = host,
            ncpKeyId = "fake-key",
            initialState = WeftMapState(CameraPosition(LatLng(37.5665, 126.978))),
            initialContent = contents.content(),
            onError = onError,
            dispatchToMain = { it() },
        )

    private class FakeHost : NaverMapIosHost {
        val calls = mutableListOf<String>()
        val renders = mutableListOf<Render>()
        var preparations = 0
        private var ready: ((NaverMapIosZoomBounds) -> Unit)? = null
        private var error: ((Throwable) -> Unit)? = null
        var failNextRender: Throwable? = null
        override fun prepare(
            ncpKeyId: String,
            initialCamera: CameraPosition,
            onReady: (NaverMapIosZoomBounds) -> Unit,
            onError: (Throwable) -> Unit,
        ) {
            calls += "prepare"
            preparations++
            ready = onReady
            error = onError
        }
        override fun activate() { calls += "activate" }
        override fun render(camera: CameraPosition?, content: MapContent) {
            failNextRender?.let { failure ->
                failNextRender = null
                throw failure
            }
            renders += Render(camera, content)
        }
        override fun pause() { calls += "pause" }
        override fun reset() { calls += "reset" }
        fun ready() { ready?.invoke(NaverMapIosZoomBounds(2f, 20f)) }
        fun fail(failure: Throwable) { error?.invoke(failure) }
    }

    private data class Render(val camera: CameraPosition?, val content: MapContent)

    private class ContentFactory : AutoCloseable {
        private val clock = BroadcastFrameClock()
        private val context = Dispatchers.Unconfined + clock
        private val recomposer = Recomposer(context)
        private val scope = CoroutineScope(SupervisorJob() + context)
        private val job = scope.launch { recomposer.runRecomposeAndApplyChanges() }
        private val composition = Composition(NoOpApplier, recomposer)
        private val markerIds = mutableStateOf(emptyList<String>())
        private val snapshot = mutableStateOf<MapContent?>(null)
        private var frame = 0L
        init {
            composition.setContent {
                val content = rememberMapContent { markerIds.value.forEach { Marker(MapMarker(it, LatLng(37.5, 127.0))) } }
                SideEffect { snapshot.value = content }
            }
            recompose()
        }
        fun content(vararg ids: String): MapContent {
            markerIds.value = ids.toList()
            recompose()
            return checkNotNull(snapshot.value)
        }
        private fun recompose() {
            Snapshot.sendApplyNotifications()
            repeat(4) { clock.sendFrame(++frame); Snapshot.sendApplyNotifications() }
        }
        override fun close() { composition.dispose(); job.cancel(); recomposer.cancel(); scope.cancel() }
    }

    private object NoOpApplier : AbstractApplier<Unit>(Unit) {
        override fun insertTopDown(index: Int, instance: Unit) = Unit
        override fun insertBottomUp(index: Int, instance: Unit) = Unit
        override fun remove(index: Int, count: Int) = Unit
        override fun move(from: Int, to: Int, count: Int) = Unit
        override fun onClear() = Unit
    }
}
