package run.thespot.weft.core.map

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
) {
    /** Defensive copy of the marker snapshot produced by the child composition. */
    public val markers: List<MapMarker> = markers.toList()
}
