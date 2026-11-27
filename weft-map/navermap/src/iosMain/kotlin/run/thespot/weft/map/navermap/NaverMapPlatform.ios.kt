package run.thespot.weft.map.navermap

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UIColor
import platform.UIKit.UIView
import run.thespot.weft.core.map.CameraPosition
import run.thespot.weft.core.map.LatLng
import run.thespot.weft.core.map.MapContent
import run.thespot.weft.core.map.WeftMapState
import kotlin.math.roundToInt
import swiftPMImport.run.thespot.weft.weft.map.navermap.NMFAuthManager
import swiftPMImport.run.thespot.weft.weft.map.navermap.NMFCameraPosition
import swiftPMImport.run.thespot.weft.weft.map.navermap.NMFCameraUpdate
import swiftPMImport.run.thespot.weft.weft.map.navermap.NMFCircleOverlay
import swiftPMImport.run.thespot.weft.weft.map.navermap.NMFMapView
import swiftPMImport.run.thespot.weft.weft.map.navermap.NMFMarker
import swiftPMImport.run.thespot.weft.weft.map.navermap.NMFNcpKeyClient
import swiftPMImport.run.thespot.weft.weft.map.navermap.NMFOverlay
import swiftPMImport.run.thespot.weft.weft.map.navermap.NMFPolygonOverlay
import swiftPMImport.run.thespot.weft.weft.map.navermap.NMFPolylineOverlay
import swiftPMImport.run.thespot.weft.weft.map.navermap.NMGLineString
import swiftPMImport.run.thespot.weft.weft.map.navermap.NMGPolygon
import swiftPMImport.run.thespot.weft.weft.map.navermap.NMGLatLng

@Composable
internal actual fun NaverMapPlatformSurface(
    ncpKeyId: String,
    state: WeftMapState,
    content: MapContent,
    modifier: Modifier,
    onError: ((Throwable) -> Unit)?,
) {
    key(ncpKeyId) {
        val surface = remember {
            NaverMapIosSurface(
                ncpKeyId = ncpKeyId,
                initialState = state,
                initialContent = content,
                initialOnError = onError,
            )
        }
        DisposableEffect(surface) {
            onDispose(surface::dispose)
        }
        UIKitView(
            factory = { surface },
            modifier = modifier,
            update = { view -> view.update(state, content, onError) },
            onRelease = { view -> view.dispose() },
        )
    }
}

@OptIn(ExperimentalForeignApi::class)
private class NaverMapIosSurface(
    ncpKeyId: String,
    initialState: WeftMapState,
    initialContent: MapContent,
    initialOnError: ((Throwable) -> Unit)?,
) : UIView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0)) {
    private var mapView: NMFMapView? = null
    private val host = NaverMapIosHostAdapter(
        configureAuth = { key ->
            NMFAuthManager.shared().client = NMFNcpKeyClient(ncpKeyId = key)
        },
        createLayer = {
            val nativeMap = NMFMapView(frame = bounds)
            mapView = nativeMap
            addSubview(nativeMap)
            NaverMapIosNmfNativeLayer(nativeMap) {
                mapView = null
            }
        },
    )
    private val coordinator = NaverMapIosCoordinator(
        host = host,
        ncpKeyId = ncpKeyId,
        initialState = initialState,
        initialContent = initialContent,
        onError = initialOnError,
    )

    override fun didMoveToWindow() {
        super.didMoveToWindow()
        val size = bounds.useContents { size }
        if (window != null) {
            coordinator.onWindowAttached(size.width, size.height)
        } else {
            coordinator.onWindowDetached()
        }
    }

    override fun layoutSubviews() {
        super.layoutSubviews()
        val size = bounds.useContents { size }
        coordinator.onLayout(size.width, size.height)
        mapView?.setFrame(bounds)
    }

    fun update(state: WeftMapState, content: MapContent, onError: ((Throwable) -> Unit)?) {
        coordinator.update(state, content, onError)
    }

    fun dispose() {
        coordinator.reset()
    }
}

@OptIn(ExperimentalForeignApi::class)
private class NaverMapIosNmfNativeLayer(
    private var mapView: NMFMapView?,
    private val onRelease: () -> Unit,
) : NaverMapIosNativeLayer {
    private val nativeOverlays = linkedMapOf<String, NMFOverlay>()

    override val zoomBounds: NaverMapIosZoomBounds
        get() {
            val map = requireMapView()
            return NaverMapIosZoomBounds(
                minZoom = map.minZoomLevel.toFloat(),
                maxZoom = map.maxZoomLevel.toFloat(),
                maxTilt = map.maxTilt.toFloat(),
            )
        }

    override val currentCamera: CameraPosition
        get() {
            val camera = requireMapView().cameraPosition
            return CameraPosition(
                target = LatLng(camera.target.lat, camera.target.lng),
                zoom = camera.zoom.toFloat(),
                bearing = camera.heading.toFloat(),
                tilt = camera.tilt.toFloat(),
            )
        }

    override fun moveCamera(camera: CameraPosition) {
        val target = NMGLatLng.latLngWithLat(lat = camera.target.latitude, lng = camera.target.longitude)
        val nativePosition = NMFCameraPosition.cameraPosition(
            target = target,
            zoom = camera.zoom.toDouble(),
            tilt = camera.tilt.toDouble(),
            heading = camera.bearing.toDouble(),
        )
        requireMapView().moveCamera(NMFCameraUpdate.cameraUpdateWithPosition(position = nativePosition))
    }

    override fun add(shape: NaverMapOverlayShape) {
        check(shape.nativeId !in nativeOverlays) { "Naver Maps iOS overlay already exists." }
        val overlay = createOverlay(shape)
        apply(shape, overlay)
        overlay.mapView = requireMapView()
        nativeOverlays[shape.nativeId] = overlay
    }

    override fun update(shape: NaverMapOverlayShape) {
        val overlay = checkNotNull(nativeOverlays[shape.nativeId]) {
            "Naver Maps iOS overlay is missing."
        }
        apply(shape, overlay)
    }

    override fun remove(nativeId: String) {
        nativeOverlays.remove(nativeId)?.mapView = null
    }

    override fun restore(shapes: List<NaverMapOverlayShape>) {
        nativeOverlays.values.forEach { it.mapView = null }
        nativeOverlays.clear()
        shapes.forEach(::add)
    }

    override fun removeDelegates() {
        val map = mapView ?: return
        @Suppress("DEPRECATION")
        run {
            map.delegate = null
        }
        map.touchDelegate = null
    }

    override fun release() {
        nativeOverlays.values.forEach { it.mapView = null }
        nativeOverlays.clear()
        mapView?.removeFromSuperview()
        mapView = null
        onRelease()
    }

    private fun createOverlay(shape: NaverMapOverlayShape): NMFOverlay = when (shape.kind) {
        NaverMapOverlayKind.Marker -> NMFMarker()
        NaverMapOverlayKind.Polyline -> NMFPolylineOverlay()
        NaverMapOverlayKind.Polygon -> NMFPolygonOverlay()
        NaverMapOverlayKind.Circle -> NMFCircleOverlay()
    }

    private fun apply(shape: NaverMapOverlayShape, overlay: NMFOverlay) {
        overlay.hidden = !shape.visible
        overlay.globalZIndex = shape.zIndex.roundToInt().toLong()
        when (shape.kind) {
            NaverMapOverlayKind.Marker -> {
                val marker = checkNotNull(overlay as? NMFMarker)
                marker.position = checkNotNull(shape.position).toNativeCoordinate()
                marker.captionText = shape.title ?: ""
                marker.subCaptionText = shape.snippet ?: ""
            }
            NaverMapOverlayKind.Polyline -> {
                val line = checkNotNull(overlay as? NMFPolylineOverlay)
                line.line = NMGLineString.lineStringWithPoints(points = shape.points.map { point -> point.toNativeCoordinate() })
                line.color = (shape.strokeColor ?: Color.Black).toNativeColor()
                line.width = (shape.strokeWidthDp ?: 0f).toDouble()
            }
            NaverMapOverlayKind.Polygon -> {
                val polygon = checkNotNull(overlay as? NMFPolygonOverlay)
                val ring = NMGLineString.lineStringWithPoints(points = shape.points.map { point -> point.toNativeCoordinate() })
                polygon.polygon = NMGPolygon.polygonWithRing(exteriorRing = ring)
                polygon.fillColor = (shape.fillColor ?: Color.Transparent).toNativeColor()
                polygon.outlineColor = (shape.strokeColor ?: Color.Transparent).toNativeColor()
                polygon.outlineWidth = (shape.strokeWidthDp ?: 0f).roundToInt().toULong()
            }
            NaverMapOverlayKind.Circle -> {
                val circle = checkNotNull(overlay as? NMFCircleOverlay)
                circle.center = checkNotNull(shape.center).toNativeCoordinate()
                circle.radius = checkNotNull(shape.radiusMeters)
                circle.fillColor = (shape.fillColor ?: Color.Transparent).toNativeColor()
                circle.outlineColor = (shape.strokeColor ?: Color.Transparent).toNativeColor()
                circle.outlineWidth = (shape.strokeWidthDp ?: 0f).toDouble()
            }
        }
    }

    private fun LatLng.toNativeCoordinate(): NMGLatLng =
        NMGLatLng.latLngWithLat(lat = latitude, lng = longitude)

    private fun Color.toNativeColor(): UIColor {
        val argb = toArgb()
        val alpha = ((argb ushr 24) and 0xff) / 255.0
        val red = ((argb ushr 16) and 0xff) / 255.0
        val green = ((argb ushr 8) and 0xff) / 255.0
        val blue = (argb and 0xff) / 255.0
        return UIColor.colorWithRed(red = red, green = green, blue = blue, alpha = alpha)
    }

    private fun requireMapView(): NMFMapView =
        checkNotNull(mapView) { "Naver Maps iOS map view has been released." }
}
