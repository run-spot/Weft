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
        val reconciler = markerReconciler ?: createMarkerReconciler(readyMap).also {
            markerReconciler = it
        }
        if (camera == null) {
            reconciler.render(content)
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
        reconciler.renderWithCamera(
            content = content,
            applyCamera = {
                val cameraUpdate = CameraUpdateFactory.newCameraPosition(cameraPosition)
                onCameraUpdateAttempted()
                readyMap.moveCamera(cameraUpdate)
            },
            restoreCamera = {
                readyMap.moveCamera(CameraUpdateFactory.newCameraPosition(previousNativeCamera))
            },
        )
    }

    override fun resume() {
        mapView.resume()
    }

    override fun pause() {
        mapView.pause()
    }

    override fun finish() {
        mapView.finish()
        kakaoMap = null
        markerLayer = null
        markerReconciler = null
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
        LabelOptions.from(id, position.toKakaoLatLng())
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
    }
}
