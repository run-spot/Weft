package run.thespot.weft.map.kakaomap

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.cValue
import kotlinx.cinterop.useContents
import platform.darwin.NSObject
import platform.UIKit.UIColor
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
        val reconciler = markerReconciler ?: createMarkerReconciler(map).also { markerReconciler = it }
        val previousMarkers = reconciler.snapshot
        reconciler.render(content)
        if (camera == null) return
        try {
            map.moveCamera(
                camera.toKakaoMapIosCameraUpdate(
                    KakaoMapIosZoomBounds(map.minLevel.toInt(), map.maxLevel.toInt()),
                ).toNative(map),
                callback = null,
            )
        } catch (failure: Throwable) {
            try {
                reconciler.restore(previousMarkers)
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
        controller = null
        delegate = null
        kakaoMap = null
        pendingMapInfo = null
        readyCallback = null
        errorCallback = null
        markerLayer = null
        markerReconciler = null
        activeController.delegate = null
        activeController.resetEngine()
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
                    val options = PoiOptions(styleID = styleId, poiID = marker.id)
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
                    layer.removePoiWithPoiID(poiID = markerId, callback = null)
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
