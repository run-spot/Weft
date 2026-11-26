package run.thespot.weft.map.navermap

import android.os.Handler
import android.os.Looper
import run.thespot.weft.core.map.CameraPosition
import run.thespot.weft.core.map.MapContent
import run.thespot.weft.core.map.MapMarker
import run.thespot.weft.core.map.WeftMapState
import kotlin.math.roundToInt

internal data class NaverMapCameraBounds(
    val minZoom: Int,
    val maxZoom: Int,
    val minTilt: Float,
    val maxTilt: Float,
) {
    init {
        require(minZoom <= maxZoom) {
            "Naver Maps minimum zoom must not exceed maximum zoom."
        }
        require(minTilt <= maxTilt) {
            "Naver Maps minimum tilt must not exceed maximum tilt."
        }
    }
}

internal interface NaverMapAndroidHost {
    fun start(
        ncpKeyId: String,
        initialCamera: CameraPosition,
        onReady: (NaverMapCameraBounds) -> Unit,
        onError: (Throwable) -> Unit,
    )

    /** A null camera updates content without resetting the native camera. */
    fun render(
        camera: CameraPosition?,
        content: MapContent,
        onCameraUpdateAttempted: () -> Unit,
    )

    fun resume()

    fun pause()

    fun finish()
}

internal enum class NaverMapAndroidCoordinatorState {
    Created,
    Attached,
    Started,
    Ready,
    Paused,
    Finished,
}

internal class NaverMapAndroidCoordinator(
    private val host: NaverMapAndroidHost,
    private val ncpKeyId: String,
    initialState: WeftMapState,
    initialContent: MapContent,
    onError: ((Throwable) -> Unit)?,
    private val dispatchToMain: ((() -> Unit) -> Unit) = ::dispatchToMainThread,
) {
    @Volatile
    private var generation = 0L
    private var attached = false
    private var lifecycleResumed = false
    private var started = false
    private var hostPaused = false
    @Volatile
    private var finished = false
    private var ready = false
    private var cameraBounds: NaverMapCameraBounds? = null
    private var nativeStateMayBeOutOfSync = false
    private var cameraMayBeOutOfSync = false
    private var camera = initialState.cameraPosition
    private var content = initialContent
    @Volatile
    private var currentOnError: ((Throwable) -> Unit)? = onError
    private val errorLock = Any()
    private var errorReported = false
    private var lastRenderedCamera: CameraPosition? = null
    private var lastRenderedMarkers: List<MapMarker>? = null
    private var lastRenderedPolylines = emptyList<run.thespot.weft.core.map.MapPolyline>()
    private var lastRenderedPolygons = emptyList<run.thespot.weft.core.map.MapPolygon>()
    private var lastRenderedCircles = emptyList<run.thespot.weft.core.map.MapCircle>()

    internal var state: NaverMapAndroidCoordinatorState = NaverMapAndroidCoordinatorState.Created
        private set

    fun onAttach() {
        if (finished) return
        attached = true
        if (!started) state = NaverMapAndroidCoordinatorState.Attached
        maybeStartOrResume()
    }

    fun onDetach() {
        if (finished) return
        attached = false
        onPause()
    }

    fun onResume() {
        if (finished) return
        lifecycleResumed = true
        maybeStartOrResume()
    }

    fun onPause() {
        if (finished) return
        lifecycleResumed = false
        if (!started) {
            state = NaverMapAndroidCoordinatorState.Paused
            return
        }
        if (hostPaused) return

        hostPaused = true
        state = NaverMapAndroidCoordinatorState.Paused
        try {
            host.pause()
        } catch (failure: Throwable) {
            reportError(failure, generation)
        }
    }

    fun update(
        state: WeftMapState,
        content: MapContent,
        onError: ((Throwable) -> Unit)?,
    ) {
        if (finished) return
        camera = state.cameraPosition
        this.content = content
        currentOnError = onError
        renderIfReady(force = false, callbackGeneration = generation)
    }

    fun onFinish() {
        if (finished) return
        finished = true
        generation++
        state = NaverMapAndroidCoordinatorState.Finished
        try {
            host.finish()
        } catch (_: Throwable) {
            // Finishing is best-effort and terminal; no callback from this surface is valid now.
        }
    }

    private fun maybeStartOrResume() {
        if (finished || !attached || !lifecycleResumed) return

        if (!started) {
            val callbackGeneration = generation
            started = true
            state = NaverMapAndroidCoordinatorState.Started
            try {
                host.start(
                    ncpKeyId = ncpKeyId,
                    initialCamera = camera,
                    onReady = { bounds ->
                        dispatchToMain { onReady(callbackGeneration, bounds) }
                    },
                    onError = { failure ->
                        reportError(failure, callbackGeneration)
                    },
                )
            } catch (failure: Throwable) {
                dispatchToMain { reportError(failure, callbackGeneration) }
            }
            return
        }

        if (hostPaused) {
            try {
                host.resume()
                hostPaused = false
                state = if (ready) {
                    NaverMapAndroidCoordinatorState.Ready
                } else {
                    NaverMapAndroidCoordinatorState.Started
                }
                renderIfReady(
                    force = nativeStateMayBeOutOfSync,
                    callbackGeneration = generation,
                )
            } catch (failure: Throwable) {
                reportError(failure, generation)
            }
        }
    }

    private fun onReady(callbackGeneration: Long, bounds: NaverMapCameraBounds) {
        if (finished || callbackGeneration != generation || !started) return
        cameraBounds = bounds
        ready = true
        state = if (hostPaused) {
            NaverMapAndroidCoordinatorState.Paused
        } else {
            NaverMapAndroidCoordinatorState.Ready
        }
        renderIfReady(force = true, callbackGeneration = callbackGeneration)
    }

    private fun renderIfReady(force: Boolean, callbackGeneration: Long) {
        if (finished || !ready || callbackGeneration != generation || hostPaused) return
        val bounds = cameraBounds ?: return
        val normalizedCamera = camera.normalizeFor(bounds)
        val markers = content.markers
        val polylines = content.polylines
        val polygons = content.polygons
        val circles = content.circles
        if (
            !force &&
            !nativeStateMayBeOutOfSync &&
            normalizedCamera == lastRenderedCamera &&
            markers == lastRenderedMarkers &&
            polylines == lastRenderedPolylines &&
            polygons == lastRenderedPolygons &&
            circles == lastRenderedCircles
        ) {
            return
        }

        val cameraToApply = normalizedCamera.takeIf {
            force || nativeStateMayBeOutOfSync || cameraMayBeOutOfSync || it != lastRenderedCamera
        }
        var cameraUpdateAttempted = false
        try {
            host.render(cameraToApply, content) {
                cameraUpdateAttempted = true
            }
            if (cameraToApply != null) {
                lastRenderedCamera = cameraToApply
                cameraMayBeOutOfSync = false
            }
            lastRenderedMarkers = markers
            lastRenderedPolylines = polylines
            lastRenderedPolygons = polygons
            lastRenderedCircles = circles
            nativeStateMayBeOutOfSync = false
        } catch (failure: Throwable) {
            nativeStateMayBeOutOfSync = true
            if (cameraUpdateAttempted) cameraMayBeOutOfSync = true
            reportError(failure, callbackGeneration)
        }
    }

    private fun reportError(failure: Throwable, callbackGeneration: Long) {
        val shouldDeliver = synchronized(errorLock) {
            if (finished || callbackGeneration != generation || errorReported || currentOnError == null) {
                false
            } else {
                errorReported = true
                true
            }
        }
        if (!shouldDeliver) return
        val error = failure.asNaverMapException()
        dispatchToMain {
            if (!finished && callbackGeneration == generation) currentOnError?.invoke(error)
        }
    }
}

private fun CameraPosition.normalizeFor(bounds: NaverMapCameraBounds): CameraPosition = copy(
    zoom = zoom.roundToInt().coerceIn(bounds.minZoom, bounds.maxZoom).toFloat(),
    bearing = ((bearing % 360f) + 360f) % 360f,
    tilt = tilt.coerceIn(bounds.minTilt, bounds.maxTilt),
)

private fun Throwable.asNaverMapException(): NaverMapException =
    this as? NaverMapException
        ?: NaverMapException("Naver Maps Android surface operation failed.", this)

private fun dispatchToMainThread(block: () -> Unit) {
    val mainLooper = Looper.getMainLooper()
    if (Looper.myLooper() == mainLooper) {
        block()
    } else {
        Handler(mainLooper).post(block)
    }
}
