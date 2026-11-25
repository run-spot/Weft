package run.thespot.weft.map.kakaomap

import platform.Foundation.NSThread
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import run.thespot.weft.core.map.CameraPosition
import run.thespot.weft.core.map.MapContent
import run.thespot.weft.core.map.MapMarker
import run.thespot.weft.core.map.WeftMapState
import kotlin.math.PI
import kotlin.math.roundToInt

internal data class KakaoMapIosZoomBounds(
    val min: Int,
    val max: Int,
) {
    init {
        require(min <= max) { "Kakao Maps minimum zoom must not exceed maximum zoom." }
    }
}

internal interface KakaoMapIosHost {
    fun prepare(
        appKey: String,
        initialCamera: CameraPosition,
        onReady: (KakaoMapIosZoomBounds) -> Unit,
        onError: (Throwable) -> Unit,
    )

    fun activate()

    /** A null camera updates children only and must preserve gestures on the native map. */
    fun render(camera: CameraPosition?, content: MapContent)

    fun pause()

    fun reset()
}

internal enum class KakaoMapIosCoordinatorState {
    Created,
    AttachedZeroBounds,
    Prepared,
    Ready,
    Paused,
    Reset,
}

internal data class KakaoMapIosCameraUpdate(
    val longitude: Double,
    val latitude: Double,
    val zoomLevel: Int,
    val rotationRadians: Double,
    val tiltRadians: Double,
    val animated: Boolean,
)

internal fun CameraPosition.toKakaoMapIosCameraUpdate(bounds: KakaoMapIosZoomBounds): KakaoMapIosCameraUpdate =
    KakaoMapIosCameraUpdate(
        longitude = target.longitude,
        latitude = target.latitude,
        zoomLevel = zoom.roundToInt().coerceIn(bounds.min, bounds.max),
        rotationRadians = normalizeBearing(bearing) * PI / 180.0,
        tiltRadians = tilt.coerceIn(0f, 90f).toDouble() * PI / 180.0,
        animated = false,
    )

internal fun CameraPosition.toKakaoMapIosDefaultLevel(): Long = zoom.roundToInt().toLong()

internal class KakaoMapIosCoordinator(
    private val host: KakaoMapIosHost,
    private val appKey: String,
    initialState: WeftMapState,
    initialContent: MapContent,
    onError: ((Throwable) -> Unit)?,
    private val dispatchToMain: ((() -> Unit) -> Unit) = ::dispatchToMainThread,
) {
    private var attached = false
    private var width = 0.0
    private var height = 0.0
    private var prepared = false
    private var paused = false
    private var ready = false
    private var generation = 0L
    private var errorReported = false
    private var zoomBounds: KakaoMapIosZoomBounds? = null
    private var nativeStateMayBeOutOfSync = false
    private var cameraMayBeOutOfSync = false
    private var camera: CameraPosition = initialState.cameraPosition
    private var content: MapContent = initialContent
    private var lastRenderedCamera: CameraPosition? = null
    private var lastRenderedMarkers: List<MapMarker>? = null

    private var currentOnError = onError

    internal var state: KakaoMapIosCoordinatorState = KakaoMapIosCoordinatorState.Created
        private set

    fun onWindowAttached(width: Double, height: Double) {
        if (state == KakaoMapIosCoordinatorState.Reset) return
        attached = true
        updateBounds(width, height)
        startOrReactivate()
    }

    fun onWindowDetached() {
        if (state == KakaoMapIosCoordinatorState.Reset || !attached) return
        attached = false
        if (!prepared) {
            state = KakaoMapIosCoordinatorState.Created
            return
        }
        pauseIfNeeded()
    }

    fun onLayout(width: Double, height: Double) {
        if (state == KakaoMapIosCoordinatorState.Reset) return
        updateBounds(width, height)
        if (attached) startOrReactivate()
    }

    fun update(
        state: WeftMapState,
        content: MapContent,
        onError: ((Throwable) -> Unit)?,
    ) {
        if (this.state == KakaoMapIosCoordinatorState.Reset) return
        camera = state.cameraPosition
        this.content = content
        currentOnError = onError
        renderIfReady(force = false, callbackGeneration = generation)
    }

    fun reset() {
        if (state == KakaoMapIosCoordinatorState.Reset) return
        generation++
        state = KakaoMapIosCoordinatorState.Reset
        attached = false
        prepared = false
        ready = false
        try {
            host.reset()
        } catch (_: Throwable) {
            // Disposal is terminal. Native cleanup is best-effort and callbacks are invalidated.
        }
    }

    private fun updateBounds(newWidth: Double, newHeight: Double) {
        width = newWidth
        height = newHeight
        if (width <= 0.0 || height <= 0.0) {
            if (!prepared) state = KakaoMapIosCoordinatorState.AttachedZeroBounds
        }
    }

    private fun startOrReactivate() {
        if (!attached || width <= 0.0 || height <= 0.0 || state == KakaoMapIosCoordinatorState.Reset) return
        if (!prepared) {
            val callbackGeneration = generation
            prepared = true
            paused = false
            errorReported = false
            state = KakaoMapIosCoordinatorState.Prepared
            try {
                host.prepare(
                    appKey = appKey,
                    initialCamera = camera,
                    onReady = { bounds ->
                        dispatchToMain { onReady(callbackGeneration, bounds) }
                    },
                    onError = { throwable -> reportError(throwable, callbackGeneration) },
                )
                host.activate()
            } catch (throwable: Throwable) {
                reportError(throwable, callbackGeneration)
            }
            return
        }
        if (paused) {
            try {
                host.activate()
                paused = false
                state = if (ready) KakaoMapIosCoordinatorState.Ready else KakaoMapIosCoordinatorState.Prepared
                renderIfReady(force = true, callbackGeneration = generation)
            } catch (throwable: Throwable) {
                reportError(throwable, generation)
            }
        }
    }

    private fun onReady(callbackGeneration: Long, bounds: KakaoMapIosZoomBounds) {
        if (state == KakaoMapIosCoordinatorState.Reset || callbackGeneration != generation || !prepared) return
        zoomBounds = bounds
        ready = true
        state = if (attached && !paused) KakaoMapIosCoordinatorState.Ready else KakaoMapIosCoordinatorState.Paused
        renderIfReady(force = true, callbackGeneration = callbackGeneration)
    }

    private fun renderIfReady(force: Boolean, callbackGeneration: Long) {
        if (
            state == KakaoMapIosCoordinatorState.Reset || callbackGeneration != generation ||
            !ready || !attached || paused
        ) return
        val bounds = zoomBounds ?: return
        val normalizedCamera = camera.normalizeFor(bounds)
        val markers = content.markers
        if (
            !force && !nativeStateMayBeOutOfSync &&
            normalizedCamera == lastRenderedCamera && markers == lastRenderedMarkers
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
            nativeStateMayBeOutOfSync = false
        } catch (throwable: Throwable) {
            nativeStateMayBeOutOfSync = true
            if (cameraToApply != null) cameraMayBeOutOfSync = true
            reportError(throwable, callbackGeneration)
        }
    }

    private fun pauseIfNeeded() {
        if (!prepared || paused) {
            state = KakaoMapIosCoordinatorState.Paused
            return
        }
        paused = true
        state = KakaoMapIosCoordinatorState.Paused
        try {
            host.pause()
        } catch (throwable: Throwable) {
            reportError(throwable, generation)
        }
    }

    private fun reportError(throwable: Throwable, callbackGeneration: Long) {
        if (state == KakaoMapIosCoordinatorState.Reset || callbackGeneration != generation || errorReported) return
        val callback = currentOnError ?: return
        errorReported = true
        val error = throwable.asKakaoMapException()
        dispatchToMain {
            if (state != KakaoMapIosCoordinatorState.Reset && callbackGeneration == generation) {
                currentOnError?.invoke(error)
            }
        }
    }
}

internal object KakaoMapIosSdkInitializer {
    private var initializedAppKey: String? = null

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

    internal fun resetForTest() {
        initializedAppKey = null
    }
}

internal interface KakaoMapIosMarkerLayer {
    val styleId: String
    val layerId: String

    fun add(marker: MapMarker)

    fun update(marker: MapMarker)

    fun remove(markerId: String)

    fun restore(markers: List<MapMarker>)
}

internal class KakaoMapIosMarkerReconciler(
    private val layer: KakaoMapIosMarkerLayer,
) {
    private var renderedMarkers: Map<String, MapMarker> = emptyMap()
    private var layerSynchronized = true

    internal val snapshot: List<MapMarker>
        get() = renderedMarkers.values.toList()

    fun render(content: MapContent) {
        val nextMarkers = content.markers.toIosMarkerMap()
        if (!layerSynchronized) {
            layer.restore(nextMarkers.values.toList())
            renderedMarkers = nextMarkers
            layerSynchronized = true
            return
        }
        val previous = snapshot
        try {
            nextMarkers.forEach { (id, marker) ->
                when {
                    id !in renderedMarkers -> layer.add(marker)
                    renderedMarkers[id] != marker -> layer.update(marker)
                }
            }
            renderedMarkers.keys.filterNot(nextMarkers::containsKey).forEach(layer::remove)
            renderedMarkers = nextMarkers
        } catch (failure: Throwable) {
            try {
                layer.restore(previous)
            } catch (rollbackFailure: Throwable) {
                if (failure !== rollbackFailure) failure.addSuppressed(rollbackFailure)
                layerSynchronized = false
            }
            throw failure
        }
    }

    fun restore(markers: List<MapMarker>) {
        val restoredMarkers = markers.toIosMarkerMap()
        try {
            layer.restore(markers)
        } catch (rollbackFailure: Throwable) {
            layerSynchronized = false
            throw rollbackFailure
        }
        renderedMarkers = restoredMarkers
        layerSynchronized = true
    }

    internal fun clear() {
        renderedMarkers.keys.toList().forEach(layer::remove)
        renderedMarkers = emptyMap()
        layerSynchronized = true
    }
}

internal const val KAKAO_MAP_IOS_STYLE_ID = "weft_default_marker"
internal const val KAKAO_MAP_IOS_LAYER_ID = "weft_markers"

private fun CameraPosition.normalizeFor(bounds: KakaoMapIosZoomBounds): CameraPosition = copy(
    zoom = zoom.roundToInt().coerceIn(bounds.min, bounds.max).toFloat(),
    bearing = normalizeBearing(bearing),
    tilt = tilt.coerceIn(0f, 90f),
)

private fun normalizeBearing(value: Float): Float = ((value % 360f) + 360f) % 360f

private fun List<MapMarker>.toIosMarkerMap(): Map<String, MapMarker> = linkedMapOf<String, MapMarker>().also { result ->
    forEach { marker ->
        require(result.put(marker.id, marker) == null) {
            "Map content contains duplicate marker id '${marker.id}'."
        }
    }
}

private fun Throwable.asKakaoMapException(): KakaoMapException =
    this as? KakaoMapException ?: KakaoMapException("Kakao Maps iOS surface operation failed.", this)

internal fun dispatchToMainThread(block: () -> Unit) {
    if (NSThread.isMainThread) {
        block()
    } else {
        dispatch_async(dispatch_get_main_queue(), block)
    }
}
