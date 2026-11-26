package run.thespot.weft.core.map

import androidx.compose.ui.graphics.Color

/**
 * A geographic coordinate in decimal degrees.
 */
public data class LatLng(
    public val latitude: Double,
    public val longitude: Double,
) {
    init {
        require(latitude.isFinite() && latitude in -90.0..90.0) {
            "latitude must be finite and between -90 and 90"
        }
        require(longitude.isFinite() && longitude in -180.0..180.0) {
            "longitude must be finite and between -180 and 180"
        }
    }
}

/**
 * The camera position rendered by a map provider.
 */
public data class CameraPosition(
    public val target: LatLng,
    public val zoom: Float = 15f,
    public val bearing: Float = 0f,
    public val tilt: Float = 0f,
) {
    init {
        require(zoom.isFinite() && zoom >= 0f) {
            "zoom must be finite and non-negative"
        }
        require(bearing.isFinite()) {
            "bearing must be finite"
        }
        require(tilt.isFinite() && tilt in 0f..90f) {
            "tilt must be finite and between 0 and 90"
        }
    }
}

/**
 * A marker in a provider-neutral map content snapshot.
 */
public data class MapMarker(
    public val id: String,
    public val position: LatLng,
    public val title: String? = null,
    public val snippet: String? = null,
) {
    init {
        require(id.isNotBlank()) {
            "marker id must not be blank"
        }
    }
}

/** A provider-neutral geographic line in a map content snapshot. */
public class MapPolyline(
    public val id: String,
    points: List<LatLng>,
    public val color: Color = Color(0xFF0B57D0),
    public val widthDp: Float = 6f,
    public val zIndex: Float = 0f,
    public val visible: Boolean = true,
) {
    /** Defensive copy so a caller cannot mutate geometry after constructing a snapshot. */
    public val points: List<LatLng> = points.toList()

    init {
        require(id.isNotBlank()) { "polyline id must not be blank" }
        require(this.points.size >= 2) { "polyline must have at least two points" }
        require(widthDp.isFinite() && widthDp >= 0f) { "polyline width must be finite and non-negative" }
        require(zIndex.isFinite()) { "polyline zIndex must be finite" }
    }

    public fun copy(
        id: String = this.id,
        points: List<LatLng> = this.points,
        color: Color = this.color,
        widthDp: Float = this.widthDp,
        zIndex: Float = this.zIndex,
        visible: Boolean = this.visible,
    ): MapPolyline = MapPolyline(id, points, color, widthDp, zIndex, visible)

    override fun equals(other: Any?): Boolean = other is MapPolyline &&
        id == other.id && points == other.points && color == other.color &&
        widthDp == other.widthDp && zIndex == other.zIndex && visible == other.visible

    override fun hashCode(): Int =
        (((((id.hashCode() * 31 + points.hashCode()) * 31 + color.hashCode()) * 31 + widthDp.hashCode()) * 31 +
            zIndex.hashCode()) * 31 + visible.hashCode())

    override fun toString(): String =
        "MapPolyline(id=$id, points=$points, color=$color, widthDp=$widthDp, zIndex=$zIndex, visible=$visible)"
}

/** A provider-neutral filled polygon in a map content snapshot. */
public class MapPolygon(
    public val id: String,
    points: List<LatLng>,
    public val fillColor: Color = Color(0x1F3366FF),
    public val strokeColor: Color = Color(0x803366FF),
    public val strokeWidthDp: Float = 2f,
    public val zIndex: Float = 0f,
    public val visible: Boolean = true,
) {
    /** Defensive copy so a caller cannot mutate geometry after constructing a snapshot. */
    public val points: List<LatLng> = points.toList()

    init {
        require(id.isNotBlank()) { "polygon id must not be blank" }
        require(this.points.size >= 3) { "polygon must have at least three points" }
        require(strokeWidthDp.isFinite() && strokeWidthDp >= 0f) {
            "polygon strokeWidthDp must be finite and non-negative"
        }
        require(zIndex.isFinite()) { "polygon zIndex must be finite" }
    }

    public fun copy(
        id: String = this.id,
        points: List<LatLng> = this.points,
        fillColor: Color = this.fillColor,
        strokeColor: Color = this.strokeColor,
        strokeWidthDp: Float = this.strokeWidthDp,
        zIndex: Float = this.zIndex,
        visible: Boolean = this.visible,
    ): MapPolygon = MapPolygon(id, points, fillColor, strokeColor, strokeWidthDp, zIndex, visible)

    override fun equals(other: Any?): Boolean = other is MapPolygon &&
        id == other.id && points == other.points && fillColor == other.fillColor &&
        strokeColor == other.strokeColor && strokeWidthDp == other.strokeWidthDp &&
        zIndex == other.zIndex && visible == other.visible

    override fun hashCode(): Int =
        ((((((id.hashCode() * 31 + points.hashCode()) * 31 + fillColor.hashCode()) * 31 +
            strokeColor.hashCode()) * 31 + strokeWidthDp.hashCode()) * 31 + zIndex.hashCode()) * 31 + visible.hashCode())

    override fun toString(): String =
        "MapPolygon(id=$id, points=$points, fillColor=$fillColor, strokeColor=$strokeColor, " +
            "strokeWidthDp=$strokeWidthDp, zIndex=$zIndex, visible=$visible)"
}

/** A provider-neutral geographic circle in a map content snapshot. */
public class MapCircle(
    public val id: String,
    public val center: LatLng,
    public val radiusMeters: Double,
    public val fillColor: Color = Color(0x1F3366FF),
    public val strokeColor: Color = Color(0x803366FF),
    public val strokeWidthDp: Float = 2f,
    public val zIndex: Float = 0f,
    public val visible: Boolean = true,
) {
    init {
        require(id.isNotBlank()) { "circle id must not be blank" }
        require(radiusMeters.isFinite() && radiusMeters > 0.0) {
            "circle radiusMeters must be finite and positive"
        }
        require(strokeWidthDp.isFinite() && strokeWidthDp >= 0f) {
            "circle strokeWidthDp must be finite and non-negative"
        }
        require(zIndex.isFinite()) { "circle zIndex must be finite" }
    }

    public fun copy(
        id: String = this.id,
        center: LatLng = this.center,
        radiusMeters: Double = this.radiusMeters,
        fillColor: Color = this.fillColor,
        strokeColor: Color = this.strokeColor,
        strokeWidthDp: Float = this.strokeWidthDp,
        zIndex: Float = this.zIndex,
        visible: Boolean = this.visible,
    ): MapCircle = MapCircle(id, center, radiusMeters, fillColor, strokeColor, strokeWidthDp, zIndex, visible)

    override fun equals(other: Any?): Boolean = other is MapCircle &&
        id == other.id && center == other.center && radiusMeters == other.radiusMeters &&
        fillColor == other.fillColor && strokeColor == other.strokeColor &&
        strokeWidthDp == other.strokeWidthDp && zIndex == other.zIndex && visible == other.visible

    override fun hashCode(): Int =
        (((((((id.hashCode() * 31 + center.hashCode()) * 31 + radiusMeters.hashCode()) * 31 +
            fillColor.hashCode()) * 31 + strokeColor.hashCode()) * 31 + strokeWidthDp.hashCode()) * 31 +
            zIndex.hashCode()) * 31 + visible.hashCode())

    override fun toString(): String =
        "MapCircle(id=$id, center=$center, radiusMeters=$radiusMeters, fillColor=$fillColor, " +
            "strokeColor=$strokeColor, strokeWidthDp=$strokeWidthDp, zIndex=$zIndex, visible=$visible)"
}

/**
 * Immutable map state supplied to a map provider.
 */
public data class WeftMapState(
    public val cameraPosition: CameraPosition,
)

/**
 * Immutable marker content materialized from a map child composition.
 *
 * The constructor is internal because provider modules receive instances from
 * [run.thespot.weft.core.map.rememberMapContent], while the snapshot itself is public API.
 */
public class MapContent internal constructor(
    markers: List<MapMarker>,
    polylines: List<MapPolyline> = emptyList(),
    polygons: List<MapPolygon> = emptyList(),
    circles: List<MapCircle> = emptyList(),
) {
    /** Defensive copy of the marker snapshot produced by the child composition. */
    public val markers: List<MapMarker> = markers.toList()

    /** Defensive ordered snapshot of polyline overlays. */
    public val polylines: List<MapPolyline> = polylines.toList()

    /** Defensive ordered snapshot of polygon overlays. */
    public val polygons: List<MapPolygon> = polygons.toList()

    /** Defensive ordered snapshot of circle overlays. */
    public val circles: List<MapCircle> = circles.toList()
}
