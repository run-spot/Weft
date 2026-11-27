package run.thespot.weft.map.navermap

import androidx.compose.ui.graphics.Color
import run.thespot.weft.core.map.LatLng
import run.thespot.weft.core.map.MapCircle
import run.thespot.weft.core.map.MapContent
import run.thespot.weft.core.map.MapMarker
import run.thespot.weft.core.map.MapPolygon
import run.thespot.weft.core.map.MapPolyline

internal enum class NaverMapOverlayKind {
    Marker,
    Polyline,
    Polygon,
    Circle,
}

internal enum class NaverMapNativeElementKind(val idPrefix: String) {
    Marker("marker"),
    Polyline("polyline"),
    Polygon("polygon"),
    Circle("circle"),
}

internal fun naverMapNativeElementId(kind: NaverMapNativeElementKind, id: String): String =
    "weft:${kind.idPrefix}:$id"

/** Native-neutral overlay data; platform adapters translate this to the NAVER Maps SDK. */
internal data class NaverMapOverlayShape(
    val nativeId: String,
    val kind: NaverMapOverlayKind,
    val position: LatLng? = null,
    val title: String? = null,
    val snippet: String? = null,
    val points: List<LatLng> = emptyList(),
    val center: LatLng? = null,
    val radiusMeters: Double? = null,
    val fillColor: Color? = null,
    val strokeColor: Color? = null,
    val strokeWidthDp: Float? = null,
    val zIndex: Float = 0f,
    val visible: Boolean = true,
)

internal fun toNaverMapOverlayShapes(content: MapContent): List<NaverMapOverlayShape> =
    toNaverMapOverlayShapes(content.markers, content.polylines, content.polygons, content.circles)

internal fun toNaverMapOverlayShapes(
    markers: List<MapMarker>,
    polylines: List<MapPolyline>,
    polygons: List<MapPolygon>,
    circles: List<MapCircle>,
): List<NaverMapOverlayShape> = buildList {
    markers.forEach { marker ->
        add(
            NaverMapOverlayShape(
                nativeId = naverMapNativeElementId(NaverMapNativeElementKind.Marker, marker.id),
                kind = NaverMapOverlayKind.Marker,
                position = marker.position,
                title = marker.title,
                snippet = marker.snippet,
            ),
        )
    }
    polylines.forEach { polyline ->
        add(
            NaverMapOverlayShape(
                nativeId = naverMapNativeElementId(NaverMapNativeElementKind.Polyline, polyline.id),
                kind = NaverMapOverlayKind.Polyline,
                points = polyline.points,
                strokeColor = polyline.color,
                strokeWidthDp = polyline.widthDp,
                zIndex = polyline.zIndex,
                visible = polyline.visible,
            ),
        )
    }
    polygons.forEach { polygon ->
        add(
            NaverMapOverlayShape(
                nativeId = naverMapNativeElementId(NaverMapNativeElementKind.Polygon, polygon.id),
                kind = NaverMapOverlayKind.Polygon,
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
            NaverMapOverlayShape(
                nativeId = naverMapNativeElementId(NaverMapNativeElementKind.Circle, circle.id),
                kind = NaverMapOverlayKind.Circle,
                center = circle.center,
                radiusMeters = circle.radiusMeters,
                fillColor = circle.fillColor,
                strokeColor = circle.strokeColor,
                strokeWidthDp = circle.strokeWidthDp,
                zIndex = circle.zIndex,
                visible = circle.visible,
            ),
        )
    }
}
