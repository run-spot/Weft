package run.thespot.weft.map.kakaomap

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.cValue
import kotlinx.cinterop.useContents
import platform.darwin.NSObject
import platform.UIKit.UIColor
import androidx.compose.ui.graphics.Color as ComposeColor
import swiftPMImport.run.thespot.weft.weft.map.kakaomap.MapPolygon
import swiftPMImport.run.thespot.weft.weft.map.kakaomap.MapPolygonShape
import swiftPMImport.run.thespot.weft.weft.map.kakaomap.MapPolygonShapeOptions
import swiftPMImport.run.thespot.weft.weft.map.kakaomap.MapPolyline
import swiftPMImport.run.thespot.weft.weft.map.kakaomap.MapPolylineShape
import swiftPMImport.run.thespot.weft.weft.map.kakaomap.MapPolylineShapeOptions
import swiftPMImport.run.thespot.weft.weft.map.kakaomap.PerLevelPolygonStyle
import swiftPMImport.run.thespot.weft.weft.map.kakaomap.PerLevelPolylineStyle
import swiftPMImport.run.thespot.weft.weft.map.kakaomap.PolygonStyle
import swiftPMImport.run.thespot.weft.weft.map.kakaomap.PolygonStyleSet
import swiftPMImport.run.thespot.weft.weft.map.kakaomap.PolylineStyle
import swiftPMImport.run.thespot.weft.weft.map.kakaomap.PolylineStyleSet
import swiftPMImport.run.thespot.weft.weft.map.kakaomap.ShapeLayer
import swiftPMImport.run.thespot.weft.weft.map.kakaomap.ShapeLayerPassTypeOverlay
import swiftPMImport.run.thespot.weft.weft.map.kakaomap.ShapeManager
import run.thespot.weft.core.map.CameraPosition
import run.thespot.weft.core.map.MapContent
import run.thespot.weft.core.map.MapMarker
import swiftPMImport.run.thespot.weft.weft.map.kakaomap.CameraUpdate
import swiftPMImport.run.thespot.weft.weft.map.kakaomap.CompetitionTypeAll
import swiftPMImport.run.thespot.weft.weft.map.kakaomap.CompetitionUnitPoi
import swiftPMImport.run.thespot.weft.weft.map.kakaomap.KAPhaseReal
import swiftPMImport.run.thespot.weft.weft.map.kakaomap.KMController
import swiftPMImport.run.thespot.weft.weft.map.kakaomap.KMViewContainer
import swiftPMImport.run.thespot.weft.weft.map.kakaomap.KakaoMap
import swiftPMImport.run.thespot.weft.weft.map.kakaomap.LabelLayer
import swiftPMImport.run.thespot.weft.weft.map.kakaomap.LabelLayerOptions
import swiftPMImport.run.thespot.weft.weft.map.kakaomap.LabelManager
import swiftPMImport.run.thespot.weft.weft.map.kakaomap.MapControllerDelegateProtocol
import swiftPMImport.run.thespot.weft.weft.map.kakaomap.MapPoint
import swiftPMImport.run.thespot.weft.weft.map.kakaomap.MapviewInfo
import swiftPMImport.run.thespot.weft.weft.map.kakaomap.OrderingTypeRank
import swiftPMImport.run.thespot.weft.weft.map.kakaomap.PerLevelPoiStyle
import swiftPMImport.run.thespot.weft.weft.map.kakaomap.Poi
import swiftPMImport.run.thespot.weft.weft.map.kakaomap.PoiOptions
import swiftPMImport.run.thespot.weft.weft.map.kakaomap.PoiStyle
import swiftPMImport.run.thespot.weft.weft.map.kakaomap.PoiText
import swiftPMImport.run.thespot.weft.weft.map.kakaomap.PoiTextLineStyle
import swiftPMImport.run.thespot.weft.weft.map.kakaomap.PoiTextStyle
import swiftPMImport.run.thespot.weft.weft.map.kakaomap.PoiTransition
import swiftPMImport.run.thespot.weft.weft.map.kakaomap.SDKInitializer
import swiftPMImport.run.thespot.weft.weft.map.kakaomap.TextStyle
import swiftPMImport.run.thespot.weft.weft.map.kakaomap.TransitionTypeNone
import swiftPMImport.run.thespot.weft.weft.map.kakaomap.create

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal class KakaoMapIosMapViewHost(
    private val container: KMViewContainer,
) : KakaoMapIosHost {
    private var controller: KMController? = null
    private var delegate: MapControllerDelegateProtocol? = null
    private var kakaoMap: KakaoMap? = null
    private var pendingMapInfo: MapviewInfo? = null
    private var didRequestMapView = false
    private var readyCallback: ((KakaoMapIosZoomBounds) -> Unit)? = null
    private var errorCallback: ((Throwable) -> Unit)? = null
    private var initialCamera: CameraPosition? = null
    private var markerLayer: LabelLayer? = null
    private var markerReconciler: KakaoMapIosMarkerReconciler? = null
    private var shapeLayer: ShapeLayer? = null
    private var shapeReconciler: KakaoMapIosShapeReconciler? = null

    override fun prepare(
        appKey: String,
        initialCamera: CameraPosition,
        onReady: (KakaoMapIosZoomBounds) -> Unit,
        onError: (Throwable) -> Unit,
    ) {
        check(controller == null) { "Kakao Maps iOS engine was prepared more than once." }
        readyCallback = onReady
        errorCallback = onError
        this.initialCamera = initialCamera
        try {
            KakaoMapIosSdkInitializer.initialize(appKey) { key ->
                SDKInitializer.InitSDKWithAppKey(key, KAPhaseReal)
            }
            val newController = KMController(viewContainer = container)
            val newDelegate = ControllerDelegate()
            controller = newController
            delegate = newDelegate
            newController.delegate = newDelegate
            if (!newController.prepareEngine()) {
                throw KakaoMapException("Kakao Maps iOS engine rejected prepareEngine().")
            }
        } catch (throwable: Throwable) {
            onError(throwable)
            throw throwable
        }
    }

    override fun activate() {
        checkNotNull(controller) { "Kakao Maps iOS engine is not prepared." }.activateEngine()
    }

    override fun render(camera: CameraPosition?, content: MapContent) {
        val map = checkNotNull(kakaoMap) { "Kakao Maps iOS view is not ready." }
        val markers = markerReconciler ?: createMarkerReconciler(map).also { markerReconciler = it }
        val shapes = shapeReconciler ?: createShapeReconciler(map).also { shapeReconciler = it }
        val previousMarkers = markers.snapshot
        val previousShapes = shapes.snapshot
        try {
            markers.render(content)
            shapes.render(content)
            if (camera != null) {
                map.moveCamera(
                    camera.toKakaoMapIosCameraUpdate(
                        KakaoMapIosZoomBounds(map.minLevel.toInt(), map.maxLevel.toInt()),
                    ).toNative(map),
                    callback = null,
                )
            }
        } catch (failure: Throwable) {
            try {
                markers.restore(previousMarkers)
            } catch (rollbackFailure: Throwable) {
                if (failure !== rollbackFailure) failure.addSuppressed(rollbackFailure)
            }
            try {
                shapes.restore(previousShapes)
            } catch (rollbackFailure: Throwable) {
                if (failure !== rollbackFailure) failure.addSuppressed(rollbackFailure)
            }
            throw failure
        }
    }

    override fun pause() {
        controller?.pauseEngine()
    }

    override fun reset() {
        val activeController = controller ?: return
        runKakaoMapHostTeardown(
            cleanupShapes = { shapeLayer?.clearAllShapes() },
            finishEngine = {
                activeController.delegate = null
                activeController.resetEngine()
            },
            clearReferences = {
                controller = null
                delegate = null
                kakaoMap = null
                pendingMapInfo = null
                readyCallback = null
                errorCallback = null
                markerLayer = null
                markerReconciler = null
                shapeLayer = null
                shapeReconciler = null
            },
        )
    }

    fun onLayout() {
        addPendingViewIfReady()
    }

    private fun createMapInfo(): MapviewInfo {
        val camera = checkNotNull(initialCamera)
        return MapviewInfo.create(
            viewName = VIEW_NAME,
            appName = "openmap",
            viewInfoName = "map",
            defaultPosition = MapPoint(
                longitude = camera.target.longitude,
                latitude = camera.target.latitude,
            ),
            defaultLevel = camera.toKakaoMapIosDefaultLevel(),
            enabled = true,
        )
    }

    private fun addPendingViewIfReady() {
        if (didRequestMapView || container.frame.useContents { size.width <= 0.0 || size.height <= 0.0 }) return
        val mapInfo = pendingMapInfo ?: return
        val activeController = controller ?: return
        didRequestMapView = true
        activeController.addView(mapInfo)
    }

    private fun createShapeReconciler(map: KakaoMap): KakaoMapIosShapeReconciler {
        val manager: ShapeManager = checkNotNull(map.getShapeManager()) {
            "Kakao Maps iOS did not provide a shape manager."
        }
        val layer = checkNotNull(
            manager.addShapeLayerWithLayerID(
                layerID = KAKAO_MAP_IOS_SHAPE_LAYER_ID,
                zOrder = KAKAO_MAP_IOS_SHAPE_LAYER_Z_ORDER,
                passType = ShapeLayerPassTypeOverlay,
            ),
        ) { "Kakao Maps iOS could not create the overlay shape layer." }
        shapeLayer = layer
        val nativeShapes = mutableMapOf<String, Any>()
        val nativeZOrders = mutableMapOf<String, Int>()
        val polylineStyles = KakaoMapOverlayStyleRegistry("weft:polyline-style") { styleId, style ->
            val width = style.strokeWidthDp.toKakaoMapIosStrokeWidth()
            val perLevel = PerLevelPolylineStyle(
                bodyColor = style.strokeColor.toKakaoUIColor(),
                bodyWidth = width,
                strokeColor = UIColor.clearColor,
                strokeWidth = 0u,
                level = 0L,
            )
            val nativeStyle = PolylineStyle(styles = listOf(perLevel))
            val set = PolylineStyleSet(styleSetID = styleId).apply { addStyle(nativeStyle) }
            manager.addPolylineStyleSet(set)
            styleId to set
        }
        val polygonStyles = KakaoMapOverlayStyleRegistry("weft:polygon-style") { styleId, style ->
            val perLevel = PerLevelPolygonStyle(
                color = (style.fillColor ?: ComposeColor.Transparent).toKakaoUIColor(),
                strokeWidth = style.strokeWidthDp.toKakaoMapIosStrokeWidth(),
                strokeColor = style.strokeColor.toKakaoUIColor(),
                level = 0L,
            )
            val nativeStyle = PolygonStyle(styles = listOf(perLevel))
            val set = PolygonStyleSet(styleSetID = styleId).apply { addStyle(nativeStyle) }
            manager.addPolygonStyleSet(set)
            styleId to set
        }

        fun nextPolylineStyle(shape: KakaoMapOverlayShape): Pair<String, PolylineStyleSet> =
            polylineStyles.getOrCreate(shape)

        fun nextPolygonStyle(shape: KakaoMapOverlayShape): Pair<String, PolygonStyleSet> =
            polygonStyles.getOrCreate(shape)

        fun lines(shape: KakaoMapOverlayShape): List<MapPolyline> = listOf(
            MapPolyline(
                line = shape.points.map { MapPoint(longitude = it.longitude, latitude = it.latitude) },
                styleIndex = 0u,
            ),
        )

        fun polygons(shape: KakaoMapOverlayShape): List<MapPolygon> = listOf(
            MapPolygon(
                exteriorRing = shape.points.map { MapPoint(longitude = it.longitude, latitude = it.latitude) },
                holes = null,
                styleIndex = 0u,
            ),
        )

        fun updateVisibility(shape: Any, visible: Boolean) {
            when (shape) {
                is MapPolylineShape -> if (visible) shape.show() else shape.hide()
                is MapPolygonShape -> if (visible) shape.show() else shape.hide()
            }
        }

        return KakaoMapIosShapeReconciler(
            object : KakaoMapIosShapeLayer {
                override fun add(shape: KakaoMapOverlayShape) {
                    when (shape.kind) {
                        KakaoMapOverlayKind.Polyline -> {
                            val (styleId, _) = nextPolylineStyle(shape)
                            val options = MapPolylineShapeOptions(
                                shapeID = shape.nativeId,
                                styleID = styleId,
                                zOrder = shape.zIndex.toKakaoMapIosZOrder().toLong(),
                            ).also { it.polylines = lines(shape) }
                            val native = checkNotNull(
                                layer.addMapPolylineShape(shapeOptions = options, callback = null),
                            ) { "Kakao Maps iOS could not add polyline '${shape.nativeId}'." }
                            nativeShapes[shape.nativeId] = native
                            nativeZOrders[shape.nativeId] = shape.zIndex.toKakaoMapIosZOrder()
                            updateVisibility(native, shape.visible)
                        }
                        KakaoMapOverlayKind.Polygon -> {
                            val (styleId, _) = nextPolygonStyle(shape)
                            val options = MapPolygonShapeOptions(
                                shapeID = shape.nativeId,
                                styleID = styleId,
                                zOrder = shape.zIndex.toKakaoMapIosZOrder().toLong(),
                            ).also { it.polygons = polygons(shape) }
                            val native = checkNotNull(
                                layer.addMapPolygonShape(shapeOptions = options, callback = null),
                            ) { "Kakao Maps iOS could not add polygon '${shape.nativeId}'." }
                            nativeShapes[shape.nativeId] = native
                            nativeZOrders[shape.nativeId] = shape.zIndex.toKakaoMapIosZOrder()
                            updateVisibility(native, shape.visible)
                        }
                    }
                }

                override fun update(shape: KakaoMapOverlayShape) {
                    val nextZOrder = shape.zIndex.toKakaoMapIosZOrder()
                    if (nativeZOrders[shape.nativeId] != nextZOrder) {
                        remove(shape.nativeId)
                        add(shape)
                        return
                    }
                    when (shape.kind) {
                        KakaoMapOverlayKind.Polyline -> {
                            val native = checkNotNull(nativeShapes[shape.nativeId] as? MapPolylineShape) {
                                "Kakao Maps iOS polyline '${shape.nativeId}' is not present."
                            }
                            val (styleId, _) = nextPolylineStyle(shape)
                            native.changeStyleAndDataWithStyleID(styleID = styleId, lines = lines(shape))
                            updateVisibility(native, shape.visible)
                        }
                        KakaoMapOverlayKind.Polygon -> {
                            val native = checkNotNull(nativeShapes[shape.nativeId] as? MapPolygonShape) {
                                "Kakao Maps iOS polygon '${shape.nativeId}' is not present."
                            }
                            val (styleId, _) = nextPolygonStyle(shape)
                            native.changeStyleAndDataWithStyleID(styleID = styleId, polygons = polygons(shape))
                            updateVisibility(native, shape.visible)
                        }
                    }
                }

                override fun remove(nativeId: String) {
                    nativeZOrders.remove(nativeId)
                    when (nativeShapes.remove(nativeId)) {
                        is MapPolylineShape -> layer.removeMapPolylineShapeWithShapeID(
                            shapeID = nativeId,
                            callback = null,
                        )
                        is MapPolygonShape -> layer.removeMapPolygonShapeWithShapeID(
                            shapeID = nativeId,
                            callback = null,
                        )
                    }
                }

                override fun restore(shapes: List<KakaoMapOverlayShape>) {
                    layer.clearAllShapes()
                    nativeShapes.clear()
                    nativeZOrders.clear()
                    shapes.forEach(::add)
                }
            },
        )
    }

    private fun ComposeColor.toKakaoUIColor(): UIColor = toKakaoMapIosOverlayColor().let { rgba ->
        UIColor(red = rgba.red, green = rgba.green, blue = rgba.blue, alpha = rgba.alpha)
    }

    private fun createMarkerReconciler(map: KakaoMap): KakaoMapIosMarkerReconciler {
        val manager: LabelManager = checkNotNull(map.getLabelManager()) {
            "Kakao Maps iOS did not provide a label manager."
        }
        val textLineStyle = PoiTextLineStyle().apply {
            textStyle = TextStyle(fontSize = 14u, fontColor = UIColor.blackColor)
        }
        val poiTextStyle = PoiTextStyle(
            transition = cValue<PoiTransition> {
                entrance = TransitionTypeNone
                exit = TransitionTypeNone
            },
            enableEntranceTransition = false,
            enableExitTransition = false,
            textLineStyles = listOf(textLineStyle),
        )
        val poiStyle = PoiStyle(
            styleID = KAKAO_MAP_IOS_STYLE_ID,
            styles = listOf(
                PerLevelPoiStyle(
                    textStyle = poiTextStyle,
                    padding = 0f,
                    level = 0L,
                ),
            ),
        )
        manager.addPoiStyle(poiStyle)
        val layer = checkNotNull(
            manager.addLabelLayerWithOption(
                LabelLayerOptions(
                    layerID = KAKAO_MAP_IOS_LAYER_ID,
                    competitionType = CompetitionTypeAll,
                    competitionUnit = CompetitionUnitPoi,
                    orderType = OrderingTypeRank,
                    zOrder = 0,
                ),
            ),
        ) { "Kakao Maps iOS could not create its marker layer." }
        markerLayer = layer
        return KakaoMapIosMarkerReconciler(
            object : KakaoMapIosMarkerLayer {
                private val pois = mutableMapOf<String, Poi>()
                override val styleId: String = KAKAO_MAP_IOS_STYLE_ID
                override val layerId: String = KAKAO_MAP_IOS_LAYER_ID

                override fun add(marker: MapMarker) {
                    val options = PoiOptions(styleID = styleId, poiID = kakaoMapNativeElementId(KakaoMapNativeElementKind.Marker, marker.id))
                    marker.title?.let { options.addText(PoiText(text = it, styleIndex = 0u)) }
                    val poi = checkNotNull(
                        layer.addPoiWithOption(
                            option = options,
                            at = marker.position.toKakaoMapPoint(),
                            callback = null,
                        ),
                    ) {
                        "Kakao Maps iOS could not add marker '${marker.id}'."
                    }
                    pois[marker.id] = poi
                }

                override fun update(marker: MapMarker) {
                    val poi = checkNotNull(pois[marker.id]) {
                        "Kakao Maps iOS marker '${marker.id}' is not present."
                    }
                    poi.position = marker.position.toKakaoMapPoint()
                    poi.changeTextAndStyleWithTexts(
                        texts = marker.title?.let { listOf(PoiText(text = it, styleIndex = 0u)) }.orEmpty(),
                        styleID = styleId,
                        enableTransition = false,
                    )
                }

                override fun remove(markerId: String) {
                    layer.removePoiWithPoiID(poiID = kakaoMapNativeElementId(KakaoMapNativeElementKind.Marker, markerId), callback = null)
                    pois.remove(markerId)
                }

                override fun restore(markers: List<MapMarker>) {
                    layer.clearAllItems()
                    pois.clear()
                    markers.forEach(::add)
                }
            },
        )
    }

    private inner class ControllerDelegate : NSObject(), MapControllerDelegateProtocol {
        override fun addViews() {
            pendingMapInfo = createMapInfo()
            addPendingViewIfReady()
        }

        override fun addViewSucceeded(viewName: String, viewInfoName: String) {
            if (viewName != VIEW_NAME || viewInfoName != "map") return
            val map = controller?.getView(viewName) as? KakaoMap ?: run {
                errorCallback?.invoke(KakaoMapException("Kakao Maps iOS addView succeeded without a map instance."))
                return
            }
            kakaoMap = map
            pendingMapInfo = null
            dispatchToMainThread {
                readyCallback?.invoke(
                    KakaoMapIosZoomBounds(min = map.minLevel.toInt(), max = map.maxLevel.toInt()),
                )
            }
        }

        override fun addViewFailed(viewName: String, viewInfoName: String) {
            if (viewName != VIEW_NAME || viewInfoName != "map") return
            errorCallback?.invoke(KakaoMapException("Kakao Maps iOS addViewFailed for '$viewName/$viewInfoName'."))
        }

        override fun authenticationFailed(errorCode: Long, desc: String) {
            errorCallback?.invoke(KakaoMapException("Kakao Maps iOS authenticationFailed ($errorCode): $desc"))
        }
    }

    private companion object {
        const val VIEW_NAME = "weft_kakao_map"
        const val KAKAO_MAP_IOS_SHAPE_LAYER_ID = "weft_overlays"
        const val KAKAO_MAP_IOS_SHAPE_LAYER_Z_ORDER = 1L
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun run.thespot.weft.core.map.LatLng.toKakaoMapPoint(): MapPoint =
    MapPoint(longitude = longitude, latitude = latitude)

@OptIn(ExperimentalForeignApi::class)
private fun KakaoMapIosCameraUpdate.toNative(map: KakaoMap): CameraUpdate = CameraUpdate.makeWithTarget(
    target = MapPoint(longitude = longitude, latitude = latitude),
    zoomLevel = zoomLevel.toLong(),
    rotation = rotationRadians,
    tilt = tiltRadians,
    mapView = map,
)
