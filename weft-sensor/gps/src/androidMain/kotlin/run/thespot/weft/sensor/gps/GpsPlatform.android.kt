package run.thespot.weft.sensor.gps

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn

private const val MIN_LOCATION_UPDATE_INTERVAL_MILLIS = 1_000L

private object AndroidGpsRuntime {
    @Volatile
    var applicationContext: Context? = null
}

/** Supplies the Android application context used by [Gps] location streams. */
public fun initializeWeftGps(context: Context): Unit {
    AndroidGpsRuntime.applicationContext = context.applicationContext ?: context
}

internal actual fun createGpsPlugin(distanceFilterMeters: Double): GpsPlugin =
    AndroidGpsPlugin(distanceFilterMeters)

private class AndroidGpsPlugin(
    private val distanceFilterMeters: Double,
) : GpsPlugin {
    @OptIn(ExperimentalCoroutinesApi::class)
    override val locationUpdates: Flow<GpsLocation> = callbackFlow {
        val context = AndroidGpsRuntime.applicationContext
        if (context == null) {
            close(
                GpsException(
                    reason = GpsFailureReason.PLATFORM_NOT_INITIALIZED,
                    message = "Call initializeWeftGps(context) before collecting GPS location updates.",
                ),
            )
            return@callbackFlow
        }

        if (!context.hasLocationPermission()) {
            close(
                GpsException(
                    reason = GpsFailureReason.PERMISSION_REQUIRED,
                    message = "Grant Android location permission before collecting GPS location updates.",
                ),
            )
            return@callbackFlow
        }

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (locationManager == null) {
            close(
                GpsException(
                    reason = GpsFailureReason.PLATFORM_NOT_INITIALIZED,
                    message = "Android LocationManager is unavailable.",
                ),
            )
            return@callbackFlow
        }

        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { provider -> runCatching { locationManager.isProviderEnabled(provider) }.getOrDefault(false) }
        if (providers.isEmpty()) {
            close(
                GpsException(
                    reason = GpsFailureReason.LOCATION_UNAVAILABLE,
                    message = "Android location services have no enabled GPS or network provider.",
                ),
            )
            return@callbackFlow
        }

        val listener = LocationListener { location -> trySend(location.toGpsLocation()) }
        try {
            providers.forEach { provider ->
                locationManager.requestLocationUpdates(
                    provider,
                    MIN_LOCATION_UPDATE_INTERVAL_MILLIS,
                    distanceFilterMeters.toFloat(),
                    listener,
                    Looper.getMainLooper(),
                )
            }
        } catch (error: SecurityException) {
            runCatching { locationManager.removeUpdates(listener) }
            close(
                GpsException(
                    reason = GpsFailureReason.PERMISSION_REQUIRED,
                    message = "Android rejected the GPS location request because permission is unavailable.",
                    cause = error,
                ),
            )
            return@callbackFlow
        } catch (error: Exception) {
            runCatching { locationManager.removeUpdates(listener) }
            close(
                GpsException(
                    reason = GpsFailureReason.PLATFORM_FAILURE,
                    message = "Android failed to start GPS location updates.",
                    cause = error,
                ),
            )
            return@callbackFlow
        }

        awaitClose { runCatching { locationManager.removeUpdates(listener) } }
    }.flowOn(Dispatchers.Main)
}

private fun Context.hasLocationPermission(): Boolean =
    checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

private fun Location.toGpsLocation(): GpsLocation = GpsLocation(
    latitude = latitude,
    longitude = longitude,
    altitudeMeters = altitude.takeIf { hasAltitude() },
    horizontalAccuracyMeters = accuracy.toDouble().takeIf { hasAccuracy() },
    verticalAccuracyMeters = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && hasVerticalAccuracy()) {
        verticalAccuracyMeters.toDouble()
    } else {
        null
    },
    speedMetersPerSecond = speed.toDouble().takeIf { hasSpeed() },
    bearingDegrees = bearing.toDouble().takeIf { hasBearing() }?.normalizeBearing(),
    timestampMillis = time.coerceAtLeast(0L),
)

private fun Double.normalizeBearing(): Double = ((this % 360.0) + 360.0) % 360.0
