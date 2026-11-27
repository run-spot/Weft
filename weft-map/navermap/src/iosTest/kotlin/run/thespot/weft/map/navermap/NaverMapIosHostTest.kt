package run.thespot.weft.map.navermap

import run.thespot.weft.core.map.CameraPosition
import run.thespot.weft.core.map.LatLng
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class NaverMapIosHostTest {
    @AfterTest fun resetInitializer() { NaverMapIosSdkInitializer.resetForTest() }

    @Test
    fun configuresAuthBeforeMapViewCreationAndCreatesOneViewPerSurface() {
        val events = mutableListOf<String>()
        var created = 0
        val host = host(events) {
            created++
            FakeLayer(events)
        }
        val readyBounds = mutableListOf<NaverMapIosZoomBounds>()
        val ready: (NaverMapIosZoomBounds) -> Unit = readyBounds::add
        host.prepare("client", camera(), ready, {})
        host.prepare("client", camera(), ready, {})

        assertEquals(listOf("auth:client", "create"), events)
        assertEquals(1, created)
        assertEquals(listOf(NaverMapIosZoomBounds(2f, 20f, 60f)), readyBounds)
    }

    @Test
    fun teardownRemovesOverlaysAndDelegatesBeforeReleasingTheView() {
        val events = mutableListOf<String>()
        val host = host(events) { FakeLayer(events) }
        host.prepare("client", camera(), {}, {})
        host.reset()

        assertEquals(listOf("auth:client", "create", "restore:0", "removeDelegates", "release"), events)
    }

    @Test
    fun rejectsConflictingClientIdWithoutCreatingAnotherMapView() {
        val events = mutableListOf<String>()
        var creates = 0
        val host = host(events) {
            creates++
            FakeLayer(events)
        }
        host.prepare("client-a", camera(), {}, {})
        val failure = assertFailsWith<NaverMapException> {
            host.prepare("client-b", camera(), {}, {})
        }
        assertEquals("Naver Maps iOS SDK is already initialized with a different NCP key ID.", failure.message)
        assertEquals(1, creates)
        assertEquals(listOf("auth:client-a", "create"), events)
    }

    private fun host(events: MutableList<String>, factory: () -> NaverMapIosNativeLayer) =
        NaverMapIosHostAdapter(
            configureAuth = { key -> events += "auth:$key" },
            createLayer = { events += "create"; factory() },
        )

    private fun camera() = CameraPosition(LatLng(37.5665, 126.978), zoom = 15f)

    private class FakeLayer(private val events: MutableList<String>) : NaverMapIosNativeLayer {
        override val zoomBounds = NaverMapIosZoomBounds(2f, 20f, 60f)
        override var currentCamera: CameraPosition = CameraPosition(LatLng(37.5665, 126.978), zoom = 15f)
        private val shapes = linkedMapOf<String, NaverMapOverlayShape>()
        override fun moveCamera(camera: CameraPosition) {
            currentCamera = camera
            events += "camera"
        }
        override fun add(shape: NaverMapOverlayShape) { shapes[shape.nativeId] = shape }
        override fun update(shape: NaverMapOverlayShape) { shapes[shape.nativeId] = shape }
        override fun remove(nativeId: String) { shapes.remove(nativeId) }
        override fun restore(shapes: List<NaverMapOverlayShape>) {
            this.shapes.clear()
            shapes.forEach { this.shapes[it.nativeId] = it }
            events += "restore:${shapes.size}"
        }
        override fun removeDelegates() { events += "removeDelegates" }
        override fun release() { events += "release" }
    }
}
