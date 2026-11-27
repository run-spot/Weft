package run.thespot.weft.map.navermap

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSThread
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import run.thespot.weft.core.map.CameraPosition
import run.thespot.weft.core.map.MapContent
import run.thespot.weft.core.map.WeftMapState

internal interface NaverMapIosHost {
    fun prepare(
        ncpKeyId: String,
        initialCamera: CameraPosition,
        onReady: (NaverMapIosZoomBounds) -> Unit,
        onError: (Throwable) -> Unit,
    )
    fun activate()
    /** A null camera updates content only and preserves user gestures. */
    fun render(camera: CameraPosition?, content: MapContent)
    fun pause()
    fun reset()
}

internal enum class NaverMapIosCoordinatorState {
    Created,
    AttachedZeroBounds,
    Prepared,
    Ready,
    Paused,
    Reset,
}

internal class NaverMapIosCoordinator(
    private val host: NaverMapIosHost,
    private val ncpKeyId: String,
    initialState: WeftMapState,
    initialContent: MapContent,
    onError: ((Throwable) -> Unit)?,
    private val dispatchToMain: ((() -> Unit) -> Unit) = ::dispatchNaverIosToMainThread,
) {
    private var attached = false
    private var width = 0.0
    private var height = 0.0
    private var prepared = false
    private var paused = false
    private var ready = false
    private var generation = 0L
    private var errorReported = false
    private var zoomBounds: NaverMapIosZoomBounds? = null
    private var nativeStateMayBeOutOfSync = false
    private var cameraMayBeOutOfSync = false
    private var camera: CameraPosition = initialState.cameraPosition
    private var content: MapContent = initialContent
    private var lastRenderedCamera: CameraPosition? = null
    private var lastRenderedMarkers = emptyList<run.thespot.weft.core.map.MapMarker>()
    private var lastRenderedPolylines = emptyList<run.thespot.weft.core.map.MapPolyline>()
    private var lastRenderedPolygons = emptyList<run.thespot.weft.core.map.MapPolygon>()
    private var lastRenderedCircles = emptyList<run.thespot.weft.core.map.MapCircle>()
    private var currentOnError = onError

    internal var state: NaverMapIosCoordinatorState = NaverMapIosCoordinatorState.Created
        private set

    fun onWindowAttached(width: Double, height: Double) {
        if (state == NaverMapIosCoordinatorState.Reset) return
        attached = true
        updateBounds(width, height)
        startOrReactivate()
    }

    fun onWindowDetached() {
        if (state == NaverMapIosCoordinatorState.Reset || !attached) return
        attached = false
        if (!prepared) {
            state = NaverMapIosCoordinatorState.Created
            return
        }
        pauseIfNeeded()
    }

    fun onLayout(width: Double, height: Double) {
        if (state == NaverMapIosCoordinatorState.Reset) return
        updateBounds(width, height)
        if (attached) startOrReactivate()
    }

    fun update(state: WeftMapState, content: MapContent, onError: ((Throwable) -> Unit)?) {
        if (this.state == NaverMapIosCoordinatorState.Reset) return
        camera = state.cameraPosition
        this.content = content
        currentOnError = onError
        renderIfReady(force = false, callbackGeneration = generation)
    }

    fun reset() {
        if (state == NaverMapIosCoordinatorState.Reset) return
        generation++
        state = NaverMapIosCoordinatorState.Reset
        attached = false
        prepared = false
        ready = false
        runCatching(host::reset)
    }

    private fun updateBounds(newWidth: Double, newHeight: Double) {
        width = newWidth
        height = newHeight
        if (width <= 0.0 || height <= 0.0) {
            if (!prepared) state = NaverMapIosCoordinatorState.AttachedZeroBounds
        }
    }

    private fun startOrReactivate() {
        if (!attached || width <= 0.0 || height <= 0.0 || state == NaverMapIosCoordinatorState.Reset) return
        if (!prepared) {
            val callbackGeneration = generation
            prepared = true
            paused = false
            errorReported = false
            state = NaverMapIosCoordinatorState.Prepared
            try {
                host.prepare(
                    ncpKeyId = ncpKeyId,
                    initialCamera = camera,
                    onReady = { bounds -> dispatchToMain { onReady(callbackGeneration, bounds) } },
                    onError = { failure -> reportError(failure, callbackGeneration) },
                )
                host.activate()
            } catch (failure: Throwable) {
                reportError(failure, callbackGeneration)
            }
            return
        }
        if (paused) {
            try {
                host.activate()
                paused = false
                state = if (ready) NaverMapIosCoordinatorState.Ready else NaverMapIosCoordinatorState.Prepared
                renderIfReady(force = true, callbackGeneration = generation)
            } catch (failure: Throwable) {
                reportError(failure, generation)
            }
        }
    }

    private fun onReady(callbackGeneration: Long, bounds: NaverMapIosZoomBounds) {
        if (state == NaverMapIosCoordinatorState.Reset || callbackGeneration != generation || !prepared) return
        zoomBounds = bounds
        ready = true
        state = if (attached && !paused) NaverMapIosCoordinatorState.Ready else NaverMapIosCoordinatorState.Paused
        renderIfReady(force = true, callbackGeneration = callbackGeneration)
    }

    private fun renderIfReady(force: Boolean, callbackGeneration: Long) {
        if (
            state == NaverMapIosCoordinatorState.Reset || callbackGeneration != generation ||
            !ready || !attached || paused
        ) return
        val bounds = zoomBounds ?: return
        val normalizedCamera = camera.normalizeForNaverIos(bounds)
        val markers = content.markers
        val polylines = content.polylines
        val polygons = content.polygons
        val circles = content.circles
        if (
            !force && !nativeStateMayBeOutOfSync &&
            normalizedCamera == lastRenderedCamera && markers == lastRenderedMarkers &&
            polylines == lastRenderedPolylines && polygons == lastRenderedPolygons &&
            circles == lastRenderedCircles
        ) return
        val cameraToApply = normalizedCamera.takeIf {
            it != lastRenderedCamera || cameraMayBeOutOfSync
        }
        try {
            host.render(cameraToApply, content)
            if (cameraToApply != null) {
                lastRenderedCamera = normalizedCamera
                cameraMayBeOutOfSync = false
            }
            lastRenderedMarkers = markers
            lastRenderedPolylines = polylines
            lastRenderedPolygons = polygons
            lastRenderedCircles = circles
            nativeStateMayBeOutOfSync = false
            errorReported = false
        } catch (failure: Throwable) {
            nativeStateMayBeOutOfSync = true
            if (cameraToApply != null) cameraMayBeOutOfSync = true
            reportError(failure, callbackGeneration)
        }
    }

    private fun pauseIfNeeded() {
        if (!prepared || paused) {
            state = NaverMapIosCoordinatorState.Paused
            return
        }
        paused = true
        state = NaverMapIosCoordinatorState.Paused
        try {
            host.pause()
        } catch (failure: Throwable) {
            reportError(failure, generation)
        }
    }

    private fun reportError(failure: Throwable, callbackGeneration: Long) {
        if (state == NaverMapIosCoordinatorState.Reset || callbackGeneration != generation || errorReported) return
        errorReported = true
        val callback = currentOnError ?: return
        val error = failure.asNaverMapIosException()
        dispatchToMain {
            if (state != NaverMapIosCoordinatorState.Reset && callbackGeneration == generation) {
                currentOnError?.invoke(error)
            }
        }
    }
}

private fun Throwable.asNaverMapIosException(): NaverMapException =
    this as? NaverMapException ?: NaverMapException("Naver Maps iOS surface operation failed.", this)

@OptIn(ExperimentalForeignApi::class)
private fun dispatchNaverIosToMainThread(block: () -> Unit) {
    if (NSThread.isMainThread) block() else dispatch_async(dispatch_get_main_queue(), block)
}
