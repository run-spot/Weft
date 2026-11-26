package run.thespot.weft.map.navermap

import run.thespot.weft.core.map.CameraPosition
import run.thespot.weft.core.map.LatLng
import kotlin.math.roundToInt

/** Native-neutral camera limits read from the Naver map view. */
internal data class NaverMapIosZoomBounds(
    val minZoom: Float,
    val maxZoom: Float,
    val maxTilt: Float = 60f,
) {
    init {
        require(minZoom.isFinite() && maxZoom.isFinite() && minZoom <= maxZoom) {
            "Naver Maps iOS zoom bounds must be finite and ordered."
        }
        require(maxTilt.isFinite() && maxTilt >= 0f) {
            "Naver Maps iOS maximum tilt must be finite and non-negative."
        }
    }
}

internal fun CameraPosition.normalizeForNaverIos(bounds: NaverMapIosZoomBounds): CameraPosition =
    copy(
        zoom = zoom.roundToInt().toFloat().coerceIn(bounds.minZoom, bounds.maxZoom),
        bearing = ((bearing % 360f) + 360f) % 360f,
        tilt = tilt.coerceIn(0f, bounds.maxTilt),
    )

/** Vendor-free operations needed by transactional overlay reconciliation. */
internal interface NaverMapIosOverlayLayer {
    fun add(shape: NaverMapOverlayShape)
    fun update(shape: NaverMapOverlayShape)
    fun remove(nativeId: String)
    fun restore(shapes: List<NaverMapOverlayShape>)
}

/** Vendor-neutral camera and overlay transaction seam for the Naver iOS adapter. */
internal interface NaverMapIosNativeLayer : NaverMapIosOverlayLayer {
    val zoomBounds: NaverMapIosZoomBounds
    val currentCamera: CameraPosition
    fun moveCamera(camera: CameraPosition)
    fun removeDelegates()
    fun release()
}

internal fun LatLng.toNaverIosCoordinates(): Pair<Double, Double> = latitude to longitude
