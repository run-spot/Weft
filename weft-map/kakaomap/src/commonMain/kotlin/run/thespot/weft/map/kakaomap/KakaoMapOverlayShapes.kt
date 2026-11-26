package run.thespot.weft.map.kakaomap

import androidx.compose.ui.graphics.Color
import run.thespot.weft.core.map.LatLng
import run.thespot.weft.core.map.MapCircle
import run.thespot.weft.core.map.MapPolygon
import run.thespot.weft.core.map.MapPolyline
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

internal enum class KakaoMapOverlayKind {
    Polyline,
    Polygon,
}

internal enum class KakaoMapNativeElementKind(val idPrefix: String) {
    Marker("marker"),
    Polyline("polyline"),
    Polygon("polygon"),
    Circle("circle"),
}

internal fun kakaoMapNativeElementId(kind: KakaoMapNativeElementKind, id: String): String =
    "weft:${kind.idPrefix}:$id"

/** Native-neutral shape data; the platform adapters translate this to the Kakao SDK surface. */
internal data class KakaoMapOverlayShape(
    val nativeId: String,
    val kind: KakaoMapOverlayKind,
    val points: List<LatLng>,
    val fillColor: Color?,
    val strokeColor: Color,
    val strokeWidthDp: Float,
    val zIndex: Float,
    val visible: Boolean,
    val isCircleApproximation: Boolean = false,
)

internal fun toKakaoMapOverlayShapes(
    polylines: List<MapPolyline>,
    polygons: List<MapPolygon>,
    circles: List<MapCircle>,
): List<KakaoMapOverlayShape> = buildList {
    polylines.forEach { polyline ->
        add(
            KakaoMapOverlayShape(
                nativeId = kakaoMapNativeElementId(KakaoMapNativeElementKind.Polyline, polyline.id),
                kind = KakaoMapOverlayKind.Polyline,
                points = polyline.points,
                fillColor = null,
                strokeColor = polyline.color,
                strokeWidthDp = polyline.widthDp,
                zIndex = polyline.zIndex,
                visible = polyline.visible,
            ),
        )
    }
    polygons.forEach { polygon ->
        add(
            KakaoMapOverlayShape(
                nativeId = kakaoMapNativeElementId(KakaoMapNativeElementKind.Polygon, polygon.id),
                kind = KakaoMapOverlayKind.Polygon,
                points = polygon.points,
                fillColor = polygon.fillColor,
                strokeColor = polygon.strokeColor,
                strokeWidthDp = polygon.strokeWidthDp,
                zIndex = polygon.zIndex,
                visible = polygon.visible,
            ),
        )
    }
    circles.forEach { circle ->
        add(
            KakaoMapOverlayShape(
                nativeId = kakaoMapNativeElementId(KakaoMapNativeElementKind.Circle, circle.id),
                kind = KakaoMapOverlayKind.Polygon,
                points = projectKakaoMapCircle(circle),
                fillColor = circle.fillColor,
                strokeColor = circle.strokeColor,
                strokeWidthDp = circle.strokeWidthDp,
                zIndex = circle.zIndex,
                visible = circle.visible,
                isCircleApproximation = true,
            ),
        )
    }
}

/** Projects a geodesic circle to a 64-segment polygon ring on a spherical Earth. */
internal fun projectKakaoMapCircle(circle: MapCircle): List<LatLng> {
    val center = circle.center
    val angularDistance = circle.radiusMeters / EARTH_RADIUS_METERS
    val latitude1 = center.latitude * DEGREES_TO_RADIANS
    val longitude1 = center.longitude * DEGREES_TO_RADIANS
    val ring = (0..CIRCLE_SEGMENTS).map { index ->
        if (index == CIRCLE_SEGMENTS) {
            projectCirclePoint(center, angularDistance, latitude1, longitude1, 0.0)
        } else {
            val bearing = 2.0 * PI * index / CIRCLE_SEGMENTS
            projectCirclePoint(center, angularDistance, latitude1, longitude1, bearing)
        }
    }
    return ring
}

private fun projectCirclePoint(
    center: LatLng,
    angularDistance: Double,
    latitude1: Double,
    longitude1: Double,
    bearing: Double,
): LatLng {
    val sinLatitude1 = sin(latitude1)
    val cosLatitude1 = cos(latitude1)
    val latitude2 = asin(
        sinLatitude1 * cos(angularDistance) +
            cosLatitude1 * sin(angularDistance) * cos(bearing),
    )
    val longitude2 = longitude1 + atan2(
        sin(bearing) * sin(angularDistance) * cosLatitude1,
        cos(angularDistance) - sinLatitude1 * sin(latitude2),
    )
    val longitudeDegrees = (longitude2 / DEGREES_TO_RADIANS + 540.0) % 360.0 - 180.0
    return LatLng(latitude2 / DEGREES_TO_RADIANS, longitudeDegrees)
}

internal fun distanceMeters(first: LatLng, second: LatLng): Double {
    val lat1 = first.latitude * DEGREES_TO_RADIANS
    val lat2 = second.latitude * DEGREES_TO_RADIANS
    val deltaLat = lat2 - lat1
    val deltaLon = (second.longitude - first.longitude) * DEGREES_TO_RADIANS
    val haversine = sin(deltaLat / 2.0) * sin(deltaLat / 2.0) +
        cos(lat1) * cos(lat2) * sin(deltaLon / 2.0) * sin(deltaLon / 2.0)
    return 2.0 * EARTH_RADIUS_METERS * asin(kotlin.math.sqrt(haversine))
}

private const val CIRCLE_SEGMENTS = 64
private const val EARTH_RADIUS_METERS = 6_371_008.8
private const val DEGREES_TO_RADIANS = PI / 180.0
