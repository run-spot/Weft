package run.thespot.weft.sensor.gps

import kotlinx.coroutines.flow.Flow
import kotlin.reflect.KClass
import run.thespot.weft.core.Weft
import run.thespot.weft.core.WeftPlugin

/** A location update emitted by the operating system's location service. */
public data class GpsLocation(
    /** Latitude in decimal degrees, between -90 and 90. */
    public val latitude: Double,
    /** Longitude in decimal degrees, between -180 and 180. */
    public val longitude: Double,
    /** Altitude above the platform's reference surface, in meters, when available. */
    public val altitudeMeters: Double? = null,
    /** Horizontal uncertainty radius reported by the platform, in meters. */
    public val horizontalAccuracyMeters: Double? = null,
    /** Vertical uncertainty reported by the platform, in meters. */
    public val verticalAccuracyMeters: Double? = null,
    /** Speed reported by the platform, in meters per second. */
    public val speedMetersPerSecond: Double? = null,
    /** Direction of travel reported by the platform, in degrees clockwise from north. */
    public val bearingDegrees: Double? = null,
    /** Platform event time as milliseconds since the Unix epoch. */
    public val timestampMillis: Long,
) {
    init {
        require(latitude.isFinite() && latitude in -90.0..90.0) {
            "latitude must be finite and between -90 and 90"
        }
        require(longitude.isFinite() && longitude in -180.0..180.0) {
            "longitude must be finite and between -180 and 180"
        }
        require(altitudeMeters == null || altitudeMeters.isFinite()) {
            "altitudeMeters must be finite when present"
        }
        require(horizontalAccuracyMeters == null || horizontalAccuracyMeters.isFinite() && horizontalAccuracyMeters >= 0.0) {
            "horizontalAccuracyMeters must be finite and non-negative when present"
        }
        require(verticalAccuracyMeters == null || verticalAccuracyMeters.isFinite() && verticalAccuracyMeters >= 0.0) {
            "verticalAccuracyMeters must be finite and non-negative when present"
        }
        require(speedMetersPerSecond == null || speedMetersPerSecond.isFinite() && speedMetersPerSecond >= 0.0) {
            "speedMetersPerSecond must be finite and non-negative when present"
        }
        require(bearingDegrees == null || bearingDegrees.isFinite() && bearingDegrees in 0.0..<360.0) {
            "bearingDegrees must be finite and between 0 (inclusive) and 360 (exclusive) when present"
        }
        require(timestampMillis >= 0L) { "timestampMillis must be non-negative" }
    }
}

/** Why the GPS location stream could not be started or continued. */
public enum class GpsFailureReason {
    /** Android setup has not supplied the application context, or the platform service is missing. */
    PLATFORM_NOT_INITIALIZED,

    /** The app has not been granted location access. Permission prompts belong to the app. */
    PERMISSION_REQUIRED,

    /** Location services are disabled or no location provider is currently available. */
    LOCATION_UNAVAILABLE,

    /** A platform API rejected the location request. */
    PLATFORM_FAILURE,
}

/** A typed failure from the GPS location stream. */
public class GpsException(
    public val reason: GpsFailureReason,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/** Configuration shared by the Android and Apple location adapters. */
public class GpsConfig {
    /** Minimum movement before another update is requested, in meters. Defaults to one meter. */
    public var distanceFilterMeters: Double = 1.0
}

/** Installed Weft capability for continuous location updates. */
public interface GpsPlugin {
    /**
     * A cold stream of OS-reported locations. Collecting starts native location updates; cancelling
     * the collection stops them. Location permission must be granted before collection begins.
     */
    public val locationUpdates: Flow<GpsLocation>
}

/** Native Android and Apple location service adapter. */
public object Gps : WeftPlugin<GpsConfig, GpsPlugin> {
    override val type: KClass<GpsPlugin> = GpsPlugin::class

    override val pluginName: String = "Gps"

    override fun prepare(block: GpsConfig.() -> Unit): GpsPlugin {
        val config = GpsConfig().apply(block)
        require(
            config.distanceFilterMeters.isFinite() &&
                config.distanceFilterMeters >= 0.0 &&
                config.distanceFilterMeters <= Float.MAX_VALUE.toDouble(),
        ) {
            "distanceFilterMeters must be finite, non-negative, and supported by Android's float range"
        }
        return createGpsPlugin(config.distanceFilterMeters)
    }

    override fun install(plugin: GpsPlugin, scope: Weft): Unit = Unit
}

internal expect fun createGpsPlugin(distanceFilterMeters: Double): GpsPlugin
