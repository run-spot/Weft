package run.thespot.weft.map.navermap

import android.content.Context
import android.os.Bundle
import android.view.View
import com.naver.maps.geometry.LatLng as NaverLatLng
import com.naver.maps.map.CameraPosition as NaverCameraPosition
import com.naver.maps.map.CameraUpdate
import com.naver.maps.map.NaverMap
import run.thespot.weft.core.map.CameraPosition
import run.thespot.weft.core.map.MapContent
import run.thespot.weft.core.map.LatLng
import kotlin.math.roundToInt

internal data class NaverMapAndroidNativeLatLng(
    val latitude: Double,
    val longitude: Double,
)

internal data class NaverMapAndroidNativeCameraPosition(
    val target: NaverMapAndroidNativeLatLng,
    val zoom: Double,
    val tilt: Double,
    val bearing: Double,
)

internal interface NaverMapAndroidMapBridge {
    var cameraPosition: NaverMapAndroidNativeCameraPosition
    val minZoom: Double
    val maxZoom: Double
    val minTilt: Double
    val maxTilt: Double

    fun moveCamera(camera: NaverMapAndroidNativeCameraPosition)

    fun markerLayer(style: NaverMapAndroidOverlayStyle): NaverMapAndroidMarkerLayer =
        NaverMapAndroidEmptyMarkerLayer

    fun shapeLayer(style: NaverMapAndroidOverlayStyle): NaverMapAndroidShapeLayer =
        NaverMapAndroidEmptyShapeLayer
}

internal class NaverMapAndroidMapViewHost(
    private val context: Context,
    private val mapViewFactory: NaverMapAndroidMapViewFactory,
    private val initializeSdk: (String) -> Unit,
    private val density: Float = 1f,
) : NaverMapAndroidHost {
    private var viewLifecycle: NaverMapAndroidViewLifecycle? = null
    private var nativeMap: NaverMapAndroidMapBridge? = null
    private var reconciler: NaverMapAndroidOverlayReconciler? = null
    private var finished = false

    /** Creates the native view only after the process-wide SDK client has been initialized. */
    internal fun createLifecycle(ncpKeyId: String): NaverMapAndroidViewLifecycle {
        check(!finished) { "Naver Maps Android host is finished." }
        return viewLifecycle ?: run {
            NaverMapAndroidSdkInitializer.initialize(ncpKeyId, initializeSdk)
            mapViewFactory.create(context).also { viewLifecycle = it }
        }
    }

    internal fun bindLifecycle(lifecycle: NaverMapAndroidViewLifecycle) {
        check(viewLifecycle === lifecycle) { "Naver Maps lifecycle is not owned by this host." }
    }

    /** Testable convenience used by the Android surface when no saved-state bridge is present. */
    internal fun createView(ncpKeyId: String, restoredState: Bundle): View {
        val lifecycle = createLifecycle(ncpKeyId)
        lifecycle.onCreate(restoredState)
        return lifecycle.view
    }

    override fun start(
        ncpKeyId: String,
        initialCamera: CameraPosition,
        onReady: (NaverMapCameraBounds) -> Unit,
        onError: (Throwable) -> Unit,
    ) {
        if (finished) return
        val lifecycle = viewLifecycle ?: createLifecycle(ncpKeyId).also { it.onCreate(Bundle()) }
        lifecycle.getMapAsync { map ->
            if (finished) return@getMapAsync
            nativeMap = map
            val style = NaverMapAndroidOverlayStyle(density)
            reconciler = NaverMapAndroidOverlayReconciler(
                markerLayer = map.markerLayer(style),
                shapeLayer = map.shapeLayer(style),
                style = style,
            )
            onReady(
                NaverMapCameraBounds(
                    minZoom = map.minZoom.roundToInt(),
                    maxZoom = map.maxZoom.roundToInt(),
                    minTilt = map.minTilt.toFloat(),
                    maxTilt = map.maxTilt.toFloat(),
                ),
            )
        }
    }

    override fun render(
        camera: CameraPosition?,
        content: MapContent,
        onCameraUpdateAttempted: () -> Unit,
    ) {
        val map = checkNotNull(nativeMap) { "Naver Maps native map is not ready." }
        val overlayReconciler = checkNotNull(reconciler) { "Naver Maps overlays are not ready." }
        val previousCamera = map.cameraPosition
        overlayReconciler.renderWithCamera(
            content = content,
            applyCamera = {
                if (camera != null) {
                    onCameraUpdateAttempted()
                    map.moveCamera(camera.toNativeCamera(map))
                }
            },
            restoreCamera = { map.moveCamera(previousCamera) },
        )
    }

    override fun resume() {
        // MapView lifecycle callbacks are owned by NaverMapAndroidLifecycleBridge.
    }

    override fun pause() {
        // MapView lifecycle callbacks are owned by NaverMapAndroidLifecycleBridge.
    }

    override fun finish() {
        if (finished) return
        finished = true
        runNaverMapHostTeardown(
            cleanupShapes = { reconciler?.clear() },
            finishEngine = {
                viewLifecycle?.onDestroy()
            },
            clearReferences = {
                reconciler = null
                nativeMap = null
                viewLifecycle = null
            },
        )
    }
}

private fun CameraPosition.toNativeCamera(map: NaverMapAndroidMapBridge): NaverMapAndroidNativeCameraPosition {
    val minZoom = map.minZoom.roundToInt()
    val maxZoom = map.maxZoom.roundToInt()
    val minTilt = map.minTilt.toFloat()
    val maxTilt = map.maxTilt.toFloat()
    return NaverMapAndroidNativeCameraPosition(
        target = NaverMapAndroidNativeLatLng(target.latitude, target.longitude),
        zoom = zoom.roundToInt().coerceIn(minZoom, maxZoom).toDouble(),
        tilt = tilt.coerceIn(minTilt, maxTilt).toDouble(),
        bearing = ((bearing % 360f) + 360f) % 360f.toDouble(),
    )
}

internal class NaverMapAndroidNaverMapBridge(
    private val map: NaverMap,
) : NaverMapAndroidMapBridge {
    override var cameraPosition: NaverMapAndroidNativeCameraPosition
        get() = map.cameraPosition.toNativeCamera()
        set(value) { moveCamera(value) }

    override val minZoom: Double get() = map.minZoom
    override val maxZoom: Double get() = map.maxZoom
    override val minTilt: Double get() = NaverMap.MINIMUM_TILT.toDouble()
    override val maxTilt: Double get() = map.maxTilt

    override fun moveCamera(camera: NaverMapAndroidNativeCameraPosition) {
        map.moveCamera(
            CameraUpdate.toCameraPosition(
                NaverCameraPosition(
                    NaverLatLng(camera.target.latitude, camera.target.longitude),
                    camera.zoom,
                    camera.tilt,
                    camera.bearing,
                ),
            ),
        )
    }

    override fun markerLayer(style: NaverMapAndroidOverlayStyle): NaverMapAndroidMarkerLayer =
        NaverMapAndroidNaverMarkerLayer(map, style)

    override fun shapeLayer(style: NaverMapAndroidOverlayStyle): NaverMapAndroidShapeLayer =
        NaverMapAndroidNaverShapeLayer(map, style)

    private fun NaverCameraPosition.toNativeCamera() = NaverMapAndroidNativeCameraPosition(
        target = NaverMapAndroidNativeLatLng(target.latitude, target.longitude),
        zoom = zoom,
        tilt = tilt,
        bearing = bearing,
    )
}

private class NaverMapAndroidNaverMarkerLayer(
    private val map: NaverMap,
    private val style: NaverMapAndroidOverlayStyle,
) : NaverMapAndroidMarkerLayer {
    private val handles = NaverMapAndroidNativeHandleRegistry<NaverMarkerHandle> { it.detach() }

    override fun add(marker: run.thespot.weft.core.map.MapMarker): NaverMapAndroidNativeMarker =
        NaverMarkerHandle(NaverMapAndroidNativeIds.marker(marker.id)).also {
            handles.put(it.nativeId, it)
            it.apply(marker)
        }

    override fun update(native: NaverMapAndroidNativeMarker, marker: run.thespot.weft.core.map.MapMarker) {
        (native as NaverMarkerHandle).apply(marker)
    }

    override fun remove(native: NaverMapAndroidNativeMarker, nativeId: String) {
        handles.remove(nativeId)?.detach()
    }

    override fun restore(markers: List<run.thespot.weft.core.map.MapMarker>) {
        val ids = markers.map { NaverMapAndroidNativeIds.marker(it.id) }.toSet()
        handles.retain(ids)
        markers.forEach { marker ->
            val nativeId = NaverMapAndroidNativeIds.marker(marker.id)
            val handle = handles.get(nativeId) ?: NaverMarkerHandle(nativeId).also { handles.put(nativeId, it) }
            handle.apply(marker)
        }
    }

    private inner class NaverMarkerHandle(
        override val nativeId: String,
    ) : NaverMapAndroidNativeMarker {
        val marker = com.naver.maps.map.overlay.Marker()

        fun apply(value: run.thespot.weft.core.map.MapMarker) {
            marker.position = NaverLatLng(value.position.latitude, value.position.longitude)
            marker.captionText = value.title.orEmpty()
            marker.subCaptionText = value.snippet.orEmpty()
            marker.isVisible = true
            marker.globalZIndex = style.zIndex(0f)
            marker.map = map
        }

        fun detach() {
            marker.map = null
        }
    }
}

private class NaverMapAndroidNaverShapeLayer(
    private val map: NaverMap,
    private val style: NaverMapAndroidOverlayStyle,
) : NaverMapAndroidShapeLayer {
    private val handles = NaverMapAndroidNativeHandleRegistry<NaverShapeHandle> { it.detach() }

    override fun add(shape: NaverMapAndroidShape): NaverMapAndroidNativeShape =
        NaverShapeHandle(shape).also { handles.put(it.nativeId, it) }

    override fun update(native: NaverMapAndroidNativeShape, shape: NaverMapAndroidShape) {
        (native as NaverShapeHandle).apply(shape)
    }

    override fun remove(native: NaverMapAndroidNativeShape, nativeId: String) {
        handles.remove(nativeId)?.detach()
    }

    override fun restore(shapes: List<NaverMapAndroidShape>) {
        val ids = shapes.map(NaverMapAndroidShape::nativeId).toSet()
        handles.retain(ids)
        shapes.forEach { shape ->
            val handle = handles.get(shape.nativeId) ?: NaverShapeHandle(shape).also { handles.put(shape.nativeId, it) }
            handle.apply(shape)
        }
    }

    private inner class NaverShapeHandle(
        shape: NaverMapAndroidShape,
    ) : NaverMapAndroidNativeShape {
        override val nativeId: String = shape.nativeId
        private var overlay: com.naver.maps.map.overlay.Overlay = create(shape)

        init { apply(shape) }

        fun apply(shape: NaverMapAndroidShape) {
            overlay.map = map
            when (shape) {
                is NaverMapAndroidShape.Polyline -> {
                    val native = overlay as com.naver.maps.map.overlay.PolylineOverlay
                    native.coords = shape.value.points.map { NaverLatLng(it.latitude, it.longitude) }
                    native.color = style.color(shape.value.color)
                    native.width = style.strokeWidth(shape.value.widthDp)
                    native.isVisible = shape.value.visible
                    native.globalZIndex = style.zIndex(shape.value.zIndex)
                }
                is NaverMapAndroidShape.Polygon -> {
                    val native = overlay as com.naver.maps.map.overlay.PolygonOverlay
                    native.coords = shape.value.points.map { NaverLatLng(it.latitude, it.longitude) }
                    native.color = style.color(shape.value.fillColor)
                    native.outlineColor = style.color(shape.value.strokeColor)
                    native.outlineWidth = style.strokeWidth(shape.value.strokeWidthDp)
                    native.isVisible = shape.value.visible
                    native.globalZIndex = style.zIndex(shape.value.zIndex)
                }
                is NaverMapAndroidShape.Circle -> {
                    val native = overlay as com.naver.maps.map.overlay.CircleOverlay
                    native.center = NaverLatLng(shape.value.center.latitude, shape.value.center.longitude)
                    native.radius = shape.value.radiusMeters
                    native.color = style.color(shape.value.fillColor)
                    native.outlineColor = style.color(shape.value.strokeColor)
                    native.outlineWidth = style.strokeWidth(shape.value.strokeWidthDp)
                    native.isVisible = shape.value.visible
                    native.globalZIndex = style.zIndex(shape.value.zIndex)
                }
            }
        }

        fun detach() { overlay.map = null }

        private fun create(shape: NaverMapAndroidShape): com.naver.maps.map.overlay.Overlay = when (shape) {
            is NaverMapAndroidShape.Polyline -> com.naver.maps.map.overlay.PolylineOverlay()
            is NaverMapAndroidShape.Polygon -> com.naver.maps.map.overlay.PolygonOverlay()
            is NaverMapAndroidShape.Circle -> com.naver.maps.map.overlay.CircleOverlay()
        }
    }
}
