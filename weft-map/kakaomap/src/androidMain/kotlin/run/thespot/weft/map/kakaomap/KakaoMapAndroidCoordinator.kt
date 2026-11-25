package run.thespot.weft.map.kakaomap

import android.os.Handler
import android.os.Looper
import run.thespot.weft.core.map.CameraPosition
import run.thespot.weft.core.map.MapContent
import run.thespot.weft.core.map.MapMarker
import run.thespot.weft.core.map.WeftMapState
import kotlin.math.roundToInt

internal data class KakaoMapZoomBounds(
    val min: Int,
    val max: Int,
) {
    init {
        require(min <= max) { "Kakao Maps minimum zoom must not exceed maximum zoom." }
    }
}

internal interface KakaoMapAndroidHost {
    fun start(
        appKey: String,
        initialCamera: CameraPosition,
        onReady: (KakaoMapZoomBounds) -> Unit,
        onError: (Throwable) -> Unit,
    )

    fun render(camera: CameraPosition, content: MapContent)

    fun resume()

    fun pause()

    fun finish()
}

internal enum class KakaoMapAndroidCoordinatorState {
    Created,
    Attached,
    Started,
    Ready,
    Paused,
    Finished,
}

internal class KakaoMapAndroidCoordinator(
    private val host: KakaoMapAndroidHost,
    private val appKey: String,
    initialState: WeftMapState,
    initialContent: MapContent,
    onError: ((Throwable) -> Unit)?,
    private val dispatchToMain: ((() -> Unit) -> Unit) = ::dispatchToMainThread,
) {
    @Volatile
    private var generation: Long = 0L
    private var attached = false
    private var lifecycleResumed = false
    private var started = false
    private var hostPaused = false
    @Volatile
    private var finished = false
    private var ready = false
    private val errorLock = Any()
    private var errorReported = false
    private var zoomBounds: KakaoMapZoomBounds? = null
    private var camera: CameraPosition = initialState.cameraPosition
    private var content: MapContent = initialContent
    @Volatile
    private var currentOnError: ((Throwable) -> Unit)? = onError
    private var lastRenderedCamera: CameraPosition? = null
    private var lastRenderedMarkers: List<MapMarker>? = null

    internal var state: KakaoMapAndroidCoordinatorState = KakaoMapAndroidCoordinatorState.Created
        private set

    fun onAttach() {
        if (finished) return
        attached = true
        if (!started) state = KakaoMapAndroidCoordinatorState.Attached
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
            state = KakaoMapAndroidCoordinatorState.Paused
            return
        }
        if (hostPaused) return

        hostPaused = true
        state = KakaoMapAndroidCoordinatorState.Paused
        try {
            host.pause()
        } catch (throwable: Throwable) {
            reportError(throwable, generation)
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
        state = KakaoMapAndroidCoordinatorState.Finished
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
            state = KakaoMapAndroidCoordinatorState.Started
            try {
                host.start(
                    appKey = appKey,
                    initialCamera = camera,
                    onReady = { bounds ->
                        dispatchToMain { onReady(callbackGeneration, bounds) }
                    },
                    onError = { throwable -> reportError(throwable, callbackGeneration) },
                )
            } catch (throwable: Throwable) {
                reportError(throwable, callbackGeneration)
            }
            return
        }

        if (hostPaused) {
            try {
                host.resume()
                hostPaused = false
                state = if (ready) {
                    KakaoMapAndroidCoordinatorState.Ready
                } else {
                    KakaoMapAndroidCoordinatorState.Started
                }
                renderIfReady(force = false, callbackGeneration = generation)
            } catch (throwable: Throwable) {
                reportError(throwable, generation)
            }
        }
    }

    private fun onReady(callbackGeneration: Long, bounds: KakaoMapZoomBounds) {
        if (finished || callbackGeneration != generation || !started) return
        zoomBounds = bounds
        ready = true
        state = if (hostPaused) {
            KakaoMapAndroidCoordinatorState.Paused
        } else {
            KakaoMapAndroidCoordinatorState.Ready
        }
        renderIfReady(force = true, callbackGeneration = callbackGeneration)
    }

    private fun renderIfReady(force: Boolean, callbackGeneration: Long) {
        if (finished || !ready || callbackGeneration != generation) return
        if (hostPaused) return
        val bounds = zoomBounds ?: return
        val normalizedCamera = camera.normalizeFor(bounds)
        val markers = content.markers
        if (!force && normalizedCamera == lastRenderedCamera && markers == lastRenderedMarkers) return

        try {
            host.render(normalizedCamera, content)
            lastRenderedCamera = normalizedCamera
            lastRenderedMarkers = markers
        } catch (throwable: Throwable) {
            reportError(throwable, callbackGeneration)
        }
    }

    private fun reportError(throwable: Throwable, callbackGeneration: Long) {
        val shouldDeliver = synchronized(errorLock) {
            if (finished || callbackGeneration != generation || errorReported) {
                false
            } else {
                errorReported = true
                currentOnError != null
            }
        }
        if (!shouldDeliver) return
        val error = throwable.asKakaoMapException()

        dispatchToMain {
            if (!finished && callbackGeneration == generation) {
                currentOnError?.invoke(error)
            }
        }
    }
}

internal object KakaoMapAndroidSdkInitializer {
    private var initializedAppKey: String? = null

    @Synchronized
    fun initialize(appKey: String, initializeSdk: (String) -> Unit) {
        val existingKey = initializedAppKey
        when {
            existingKey == null -> {
                initializeSdk(appKey)
                initializedAppKey = appKey
            }

            existingKey == appKey -> Unit

            else -> throw KakaoMapException(
                "Kakao Maps SDK is already initialized with a different native app key.",
            )
        }
    }

    @Synchronized
    internal fun resetForTest() {
        initializedAppKey = null
    }
}

internal interface KakaoMapAndroidMarkerLayer {
    fun add(marker: MapMarker)

    fun update(marker: MapMarker)

    fun remove(markerId: String)

    /** Restore the complete last committed native marker snapshot after a partial failed pass. */
    fun restore(markers: List<MapMarker>)
}

internal class KakaoMapAndroidMarkerReconciler(
    private val layer: KakaoMapAndroidMarkerLayer,
) {
    private var renderedMarkers: Map<String, MapMarker> = emptyMap()
    private var layerSynchronized = true

    internal val snapshot: List<MapMarker>
        get() = renderedMarkers.values.toList()

    fun render(content: MapContent) {
        val nextMarkers = content.markers.toMarkerMap()
        if (!layerSynchronized) {
            layer.restore(nextMarkers.values.toList())
            renderedMarkers = nextMarkers
            layerSynchronized = true
            return
        }

        val previousSnapshot = snapshot
        try {
            nextMarkers.forEach { (id, marker) ->
                when {
                    id !in renderedMarkers -> layer.add(marker)
                    renderedMarkers[id] != marker -> layer.update(marker)
                }
            }
            renderedMarkers.keys
                .filterNot(nextMarkers::containsKey)
                .forEach(layer::remove)

            renderedMarkers = nextMarkers
        } catch (renderFailure: Throwable) {
            try {
                restore(previousSnapshot)
            } catch (rollbackFailure: Throwable) {
                renderFailure.addSuppressedSafely(rollbackFailure)
            }
            throw renderFailure
        }
    }

    /** Restore a known committed snapshot after a later camera operation fails. */
    fun restore(snapshot: List<MapMarker>) {
        val restoredMarkers = snapshot.toMarkerMap()
        try {
            layer.restore(snapshot)
        } catch (rollbackFailure: Throwable) {
            layerSynchronized = false
            throw rollbackFailure
        }
        renderedMarkers = restoredMarkers
        layerSynchronized = true
    }

    /** Apply markers before camera and restore both resources if the camera mutation fails. */
    fun renderWithCamera(
        content: MapContent,
        applyCamera: () -> Unit,
        restoreCamera: () -> Unit,
    ) {
        val previousMarkers = snapshot
        render(content)
        try {
            applyCamera()
        } catch (renderFailure: Throwable) {
            try {
                restore(previousMarkers)
            } catch (rollbackFailure: Throwable) {
                renderFailure.addSuppressedSafely(rollbackFailure)
            }
            try {
                restoreCamera()
            } catch (rollbackFailure: Throwable) {
                renderFailure.addSuppressedSafely(rollbackFailure)
            }
            throw renderFailure
        }
    }
}

private fun List<MapMarker>.toMarkerMap(): Map<String, MapMarker> = linkedMapOf<String, MapMarker>().also { map ->
    forEach { marker ->
        require(map.put(marker.id, marker) == null) {
            "Map content contains duplicate marker id '${marker.id}'."
        }
    }
}

internal fun Throwable.addSuppressedSafely(rollbackFailure: Throwable) {
    if (rollbackFailure !== this) addSuppressed(rollbackFailure)
}

private fun CameraPosition.normalizeFor(bounds: KakaoMapZoomBounds): CameraPosition = copy(
    zoom = zoom.roundToInt().coerceIn(bounds.min, bounds.max).toFloat(),
    bearing = ((bearing % 360f) + 360f) % 360f,
    tilt = tilt.coerceIn(0f, 90f),
)

private fun Throwable.asKakaoMapException(): KakaoMapException =
    this as? KakaoMapException
        ?: KakaoMapException("Kakao Maps Android surface operation failed.", this)

private fun dispatchToMainThread(block: () -> Unit) {
    val mainLooper = Looper.getMainLooper()
    if (Looper.myLooper() == mainLooper) {
        block()
    } else {
        Handler(mainLooper).post(block)
    }
}
