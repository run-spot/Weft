package run.thespot.weft.core.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ComposeNode
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.key
import androidx.compose.ui.graphics.Color
import run.thespot.weft.core.map.composition.MapApplier
import run.thespot.weft.core.map.composition.MapCircleNode
import run.thespot.weft.core.map.composition.MapMarkerNode
import run.thespot.weft.core.map.composition.MapPolygonNode
import run.thespot.weft.core.map.composition.MapPolylineNode

/**
 * DSL marker for declarative map content.
 */
@DslMarker
public annotation class WeftMapDsl

/**
 * Receiver for declarative map content.
 */
@WeftMapDsl
public object MapScope

// Avoid a restart group around key(marker.id), so reordered markers retain their node identity.
@Composable
@NonRestartableComposable
public fun MapScope.Marker(marker: MapMarker) {
    key(marker.id) {
        ComposeNode<MapMarkerNode, MapApplier>(
            factory = { MapMarkerNode(marker) },
        ) {
            set(marker) { updateMarker(it) }
        }
    }
}

// Avoid a restart group around key(overlay.id), so reordered overlays retain their node identity.
@Composable
@NonRestartableComposable
public fun MapScope.Polyline(polyline: MapPolyline) {
    key(polyline.id) {
        ComposeNode<MapPolylineNode, MapApplier>(factory = { MapPolylineNode(polyline) }) {
            set(polyline) { updatePolyline(it) }
        }
    }
}

@Composable
@NonRestartableComposable
public fun MapScope.Polyline(
    id: String,
    points: List<LatLng>,
    color: Color = Color(0xFF0B57D0),
    widthDp: Float = 6f,
    zIndex: Float = 0f,
    visible: Boolean = true,
) {
    Polyline(MapPolyline(id, points, color, widthDp, zIndex, visible))
}

@Composable
@NonRestartableComposable
public fun MapScope.Polygon(polygon: MapPolygon) {
    key(polygon.id) {
        ComposeNode<MapPolygonNode, MapApplier>(factory = { MapPolygonNode(polygon) }) {
            set(polygon) { updatePolygon(it) }
        }
    }
}

@Composable
@NonRestartableComposable
public fun MapScope.Polygon(
    id: String,
    points: List<LatLng>,
    fillColor: Color = Color(0x1F3366FF),
    strokeColor: Color = Color(0x803366FF),
    strokeWidthDp: Float = 2f,
    zIndex: Float = 0f,
    visible: Boolean = true,
) {
    Polygon(MapPolygon(id, points, fillColor, strokeColor, strokeWidthDp, zIndex, visible))
}

@Composable
@NonRestartableComposable
public fun MapScope.Circle(circle: MapCircle) {
    key(circle.id) {
        ComposeNode<MapCircleNode, MapApplier>(factory = { MapCircleNode(circle) }) {
            set(circle) { updateCircle(it) }
        }
    }
}

@Composable
@NonRestartableComposable
public fun MapScope.Circle(
    id: String,
    center: LatLng,
    radiusMeters: Double,
    fillColor: Color = Color(0x1F3366FF),
    strokeColor: Color = Color(0x803366FF),
    strokeWidthDp: Float = 2f,
    zIndex: Float = 0f,
    visible: Boolean = true,
) {
    Circle(MapCircle(id, center, radiusMeters, fillColor, strokeColor, strokeWidthDp, zIndex, visible))
}
