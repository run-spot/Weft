package run.thespot.weft.map.kakaomap

import android.content.Context
import android.graphics.Color
import com.kakao.vectormap.KakaoMap
import com.kakao.vectormap.KakaoMapReadyCallback
import com.kakao.vectormap.KakaoMapSdk
import com.kakao.vectormap.LatLng as KakaoLatLng
import com.kakao.vectormap.MapLifeCycleCallback
import com.kakao.vectormap.MapView
import com.kakao.vectormap.camera.CameraPosition as KakaoCameraPosition
import com.kakao.vectormap.camera.CameraUpdateFactory
import com.kakao.vectormap.label.Label
import com.kakao.vectormap.label.LabelLayer
import com.kakao.vectormap.label.LabelLayerOptions
import com.kakao.vectormap.label.LabelManager
import com.kakao.vectormap.label.LabelOptions
import com.kakao.vectormap.label.LabelStyle
import com.kakao.vectormap.label.LabelStyles
import com.kakao.vectormap.label.LabelTextBuilder
import com.kakao.vectormap.label.LabelTextStyle
import com.kakao.vectormap.shape.MapPoints
import com.kakao.vectormap.shape.Polygon as KakaoPolygon
import com.kakao.vectormap.shape.PolygonOptions
import com.kakao.vectormap.shape.PolygonStyle
import com.kakao.vectormap.shape.PolygonStyles
import com.kakao.vectormap.shape.PolygonStylesSet
import com.kakao.vectormap.shape.Polyline as KakaoPolyline
import com.kakao.vectormap.shape.PolylineOptions
import com.kakao.vectormap.shape.PolylineStyle
import com.kakao.vectormap.shape.PolylineStyles
import com.kakao.vectormap.shape.PolylineStylesSet
import com.kakao.vectormap.shape.ShapeLayer
import com.kakao.vectormap.shape.ShapeLayerOptions
import com.kakao.vectormap.shape.ShapeManager
import run.thespot.weft.core.map.CameraPosition
import run.thespot.weft.core.map.MapContent
import run.thespot.weft.core.map.MapMarker
import kotlin.math.roundToInt

internal class KakaoMapAndroidMapViewHost(
    private val context: Context,
    private val mapView: MapView,
) : KakaoMapAndroidHost {
    private var kakaoMap: KakaoMap? = null
    private var markerLayer: LabelLayer? = null
    private var markerReconciler: KakaoMapAndroidMarkerReconciler? = null
    private var shapeLayer: ShapeLayer? = null
    private var shapeReconciler: KakaoMapAndroidShapeReconciler? = null

    override fun start(
        appKey: String,
        initialCamera: CameraPosition,
        onReady: (KakaoMapZoomBounds) -> Unit,
        onError: (Throwable) -> Unit,
    ) {
        KakaoMapAndroidSdkInitializer.initialize(appKey) { key -> KakaoMapSdk.init(context, key) }
        mapView.start(
            object : MapLifeCycleCallback() {
                override fun onMapDestroy() {
                    kakaoMap = null
                    markerLayer = null
                    markerReconciler = null
                    shapeLayer = null
                    shapeReconciler = null
                }

                override fun onMapError(exception: Exception) {
                    onError(exception)
                }
            },
            object : KakaoMapReadyCallback() {
                override fun getPosition(): KakaoLatLng = initialCamera.target.toKakaoLatLng()

                override fun getZoomLevel(): Int = initialCamera.zoom.roundToInt()

                override fun onMapReady(map: KakaoMap) {
                    kakaoMap = map
                    onReady(
                        KakaoMapZoomBounds(
                            min = map.minZoomLevel,
                            max = map.maxZoomLevel,
                        ),
                    )
                }
            },
        )
    }

    override fun render(
        camera: CameraPosition?,
        content: MapContent,
        onCameraUpdateAttempted: () -> Unit,
    ) {
        val readyMap = checkNotNull(kakaoMap) { "KakaoMap is not ready." }
        val markers = markerReconciler ?: createMarkerReconciler(readyMap).also { markerReconciler = it }
        val shapes = shapeReconciler ?: createShapeReconciler(readyMap).also { shapeReconciler = it }
        val previousMarkers = markers.snapshot
        val previousShapes = shapes.snapshot

        if (camera == null) {
            try {
                markers.render(content)
                shapes.render(content)
            } catch (failure: Throwable) {
                restoreContent(markers, previousMarkers, shapes, previousShapes, failure)
                throw failure
            }
            return
        }

        val previousNativeCamera = checkNotNull(readyMap.cameraPosition) {
            "Kakao Maps did not provide its current camera position."
        }
        val cameraPosition = KakaoCameraPosition.from(
            camera.target.latitude,
            camera.target.longitude,
            camera.zoom.roundToInt(),
            camera.tilt.coerceIn(0f, 90f).toDouble(),
            normalizeBearing(camera.bearing).toDouble(),
            0.0,
        )
        var cameraAttempted = false
        try {
            markers.render(content)
            shapes.render(content)
            val cameraUpdate = CameraUpdateFactory.newCameraPosition(cameraPosition)
            onCameraUpdateAttempted()
            cameraAttempted = true
            readyMap.moveCamera(cameraUpdate)
        } catch (failure: Throwable) {
            restoreContent(markers, previousMarkers, shapes, previousShapes, failure)
            if (cameraAttempted) {
                try {
                    readyMap.moveCamera(CameraUpdateFactory.newCameraPosition(previousNativeCamera))
                } catch (rollbackFailure: Throwable) {
                    failure.addSuppressedSafely(rollbackFailure)
                }
            }
            throw failure
        }
    }

    private fun restoreContent(
        markers: KakaoMapAndroidMarkerReconciler,
        previousMarkers: List<MapMarker>,
        shapes: KakaoMapAndroidShapeReconciler,
        previousShapes: List<KakaoMapOverlayShape>,
        failure: Throwable,
    ) {
        try {
            markers.restore(previousMarkers)
        } catch (rollbackFailure: Throwable) {
            failure.addSuppressedSafely(rollbackFailure)
        }
        try {
            shapes.restore(previousShapes)
        } catch (rollbackFailure: Throwable) {
            failure.addSuppressedSafely(rollbackFailure)
        }
    }

    override fun resume() {
        mapView.resume()
    }

    override fun pause() {
        mapView.pause()
    }

    override fun finish() {
        runKakaoMapHostTeardown(
            cleanupShapes = { shapeLayer?.removeAll() },
            finishEngine = { mapView.finish() },
            clearReferences = {
                kakaoMap = null
                markerLayer = null
                markerReconciler = null
                shapeLayer = null
                shapeReconciler = null
            },
        )
    }

    private fun createShapeReconciler(map: KakaoMap): KakaoMapAndroidShapeReconciler {
        val manager: ShapeManager = checkNotNull(map.shapeManager) {
            "Kakao Maps did not provide a shape manager."
        }
        val layer = checkNotNull(
            manager.addLayer(ShapeLayerOptions.from(SHAPE_LAYER_ID).setZOrder(SHAPE_LAYER_Z_ORDER)),
        ) { "Kakao Maps could not create the overlay shape layer." }
        shapeLayer = layer
        val nativeShapes = mutableMapOf<String, Any>()
        val density = context.resources.displayMetrics.density
        val polylineStyles = KakaoMapOverlayStyleRegistry("weft:polyline-style") { styleId, style ->
            val mapped = style.toKakaoMapAndroidStyle(density)
            val styles = PolylineStylesSet.from(
                styleId,
                PolylineStyles.from(PolylineStyle.from(mapped.strokeWidthPx, mapped.strokeColorArgb)),
            )
            checkNotNull(manager.addPolylineStyles(styles)) { "Kakao Maps rejected polyline style '$styleId'." }
        }
        val polygonStyles = KakaoMapOverlayStyleRegistry("weft:polygon-style") { styleId, style ->
            val mapped = style.toKakaoMapAndroidStyle(density)
            val fillArgb = mapped.fillColorArgb ?: Color.TRANSPARENT
            val styles = PolygonStylesSet.from(
                styleId,
                PolygonStyles.from(
                    PolygonStyle.from(0, fillArgb, mapped.strokeWidthPx, mapped.strokeColorArgb),
                ),
            )
            checkNotNull(manager.addPolygonStyles(styles)) { "Kakao Maps rejected polygon style '$styleId'." }
        }

        fun nextPolylineStyle(shape: KakaoMapOverlayShape): PolylineStylesSet = polylineStyles.getOrCreate(shape)

        fun nextPolygonStyle(shape: KakaoMapOverlayShape): PolygonStylesSet = polygonStyles.getOrCreate(shape)

        fun mapPoints(shape: KakaoMapOverlayShape): List<MapPoints> = listOf(
            MapPoints.fromLatLng(shape.points.map { KakaoLatLng.from(it.latitude, it.longitude) }),
        )

        return KakaoMapAndroidShapeReconciler(
            object : KakaoMapAndroidShapeLayer {
                override fun add(shape: KakaoMapOverlayShape) {
                    when (shape.kind) {
                        KakaoMapOverlayKind.Polyline -> {
                            val options = PolylineOptions.from()
                                .setMapPoints(mapPoints(shape))
                                .setStylesSet(nextPolylineStyle(shape))
                                .setZOrder(shape.zIndex.toKakaoMapAndroidZOrder())
                                .setVisible(shape.visible)
                                .apply { polylineId = shape.nativeId }
                            val native = checkNotNull(layer.addPolyline(options)) {
                                "Kakao Maps could not add polyline '${shape.nativeId}'."
                            }
                            nativeShapes[shape.nativeId] = native
                        }
                        KakaoMapOverlayKind.Polygon -> {
                            val options = PolygonOptions.from()
                                .setMapPoints(mapPoints(shape))
                                .setStylesSet(nextPolygonStyle(shape))
                                .setZOrder(shape.zIndex.toKakaoMapAndroidZOrder())
                                .setVisible(shape.visible)
                                .apply { polygonId = shape.nativeId }
                            val native = checkNotNull(layer.addPolygon(options)) {
                                "Kakao Maps could not add polygon '${shape.nativeId}'."
                            }
                            nativeShapes[shape.nativeId] = native
                        }
                    }
                }

                override fun update(shape: KakaoMapOverlayShape) {
                    when (shape.kind) {
                        KakaoMapOverlayKind.Polyline -> {
                            val native = checkNotNull(nativeShapes[shape.nativeId] as? KakaoPolyline) {
                                "Kakao Maps polyline '${shape.nativeId}' is not present."
                            }
                            native.changeStylesAndMapPoints(nextPolylineStyle(shape), mapPoints(shape))
                            native.setZOrder(shape.zIndex.toKakaoMapAndroidZOrder())
                            if (shape.visible) native.show() else native.hide()
                        }
                        KakaoMapOverlayKind.Polygon -> {
                            val native = checkNotNull(nativeShapes[shape.nativeId] as? KakaoPolygon) {
                                "Kakao Maps polygon '${shape.nativeId}' is not present."
                            }
                            native.changeStylesAndMapPoints(nextPolygonStyle(shape), mapPoints(shape))
                            native.setZOrder(shape.zIndex.toKakaoMapAndroidZOrder())
                            if (shape.visible) native.show() else native.hide()
                        }
                    }
                }

                override fun remove(nativeId: String) {
                    when (val native = nativeShapes.remove(nativeId)) {
                        is KakaoPolyline -> native.remove()
                        is KakaoPolygon -> native.remove()
                    }
                }

                override fun restore(shapes: List<KakaoMapOverlayShape>) {
                    layer.removeAll()
                    nativeShapes.clear()
                    shapes.forEach(::add)
                }
            },
        )
    }

    private fun createMarkerReconciler(map: KakaoMap): KakaoMapAndroidMarkerReconciler {
        val manager: LabelManager = checkNotNull(map.labelManager) {
            "Kakao Maps did not provide a label manager."
        }
        val styles = LabelStyles.from(
            MARKER_STYLE_ID,
            LabelStyle.from(android.R.drawable.ic_menu_mylocation)
                .setTextStyles(LabelTextStyle.from(14, Color.BLACK)),
        )
        checkNotNull(manager.addLabelStyles(styles)) {
            "Kakao Maps rejected the marker label style."
        }
        val layer = checkNotNull(manager.addLayer(LabelLayerOptions.from(MARKER_LAYER_ID))) {
            "Kakao Maps could not create the marker layer."
        }
        markerLayer = layer
        val labels = mutableMapOf<String, Label>()

        return KakaoMapAndroidMarkerReconciler(
            object : KakaoMapAndroidMarkerLayer {
                override fun add(marker: MapMarker) {
                    val label = checkNotNull(
                        layer.addLabel(marker.toLabelOptions(styles)),
                    ) { "Kakao Maps could not add marker '${marker.id}'." }
                    labels[marker.id] = label
                }

                override fun update(marker: MapMarker) {
                    val label = checkNotNull(labels[marker.id]) {
                        "Kakao Maps marker '${marker.id}' is not present."
                    }
                    label.moveTo(marker.position.toKakaoLatLng())
                    label.changeText(marker.toLabelText())
                }

                override fun remove(markerId: String) {
                    labels.remove(markerId)?.remove()
                }

                override fun restore(markers: List<MapMarker>) {
                    layer.removeAll()
                    labels.clear()
                    markers.forEach(::add)
                }
            },
        )
    }

    private fun MapMarker.toLabelOptions(styles: LabelStyles): LabelOptions =
        LabelOptions.from(kakaoMapNativeElementId(KakaoMapNativeElementKind.Marker, id), position.toKakaoLatLng())
            .setStyles(styles)
            .setTexts(toLabelText())

    private fun MapMarker.toLabelText(): LabelTextBuilder =
        LabelTextBuilder().setTexts(title.orEmpty())

    private fun run.thespot.weft.core.map.LatLng.toKakaoLatLng(): KakaoLatLng =
        KakaoLatLng.from(latitude, longitude)

    private fun normalizeBearing(bearing: Float): Float = ((bearing % 360f) + 360f) % 360f

    private companion object {
        const val MARKER_LAYER_ID = "weft_markers"
        const val MARKER_STYLE_ID = "weft_default_marker"
        const val SHAPE_LAYER_ID = "weft_overlays"
        const val SHAPE_LAYER_Z_ORDER = 1
    }
}
