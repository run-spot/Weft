@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package run.thespot.weft.sensor.gps

import kotlinx.cinterop.useContents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import platform.CoreLocation.CLAuthorizationStatus
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLAuthorizationStatusNotDetermined
import platform.CoreLocation.kCLLocationAccuracyBest
import platform.darwin.NSObject

internal actual fun createGpsPlugin(distanceFilterMeters: Double): GpsPlugin =
    IosGpsPlugin(distanceFilterMeters)

private class IosGpsPlugin(
    private val distanceFilterMeters: Double,
) : GpsPlugin {
    override val locationUpdates: Flow<GpsLocation> = callbackFlow {
        if (!CLLocationManager.locationServicesEnabled()) {
            close(
                GpsException(
                    reason = GpsFailureReason.LOCATION_UNAVAILABLE,
                    message = "iOS location services are disabled.",
                ),
            )
            return@callbackFlow
        }

        val status = CLLocationManager.authorizationStatus()
        if (!status.isGpsAuthorized()) {
            close(
                GpsException(
                    reason = GpsFailureReason.PERMISSION_REQUIRED,
                    message = if (status == kCLAuthorizationStatusNotDetermined) {
                        "Request iOS location permission before collecting GPS location updates."
                    } else {
                        "Grant iOS location permission before collecting GPS location updates."
                    },
                ),
            )
            return@callbackFlow
        }

        val manager = CLLocationManager()
        val delegate = GpsLocationManagerDelegate(
            onLocationsUpdated = { locations ->
                val location = locations.lastOrNull() as? CLLocation
                location?.let { trySend(it.toGpsLocation()) }
            },
            onAuthorizationChanged = { authorizationStatus ->
                if (!authorizationStatus.isGpsAuthorized()) {
                    close(
                        GpsException(
                            reason = GpsFailureReason.PERMISSION_REQUIRED,
                            message = "iOS location permission is no longer available.",
                        ),
                    )
                }
            },
        )
        manager.delegate = delegate
        manager.desiredAccuracy = kCLLocationAccuracyBest
        manager.distanceFilter = distanceFilterMeters
        manager.startUpdatingLocation()

        awaitClose {
            manager.stopUpdatingLocation()
            manager.delegate = null
        }
    }.flowOn(Dispatchers.Main)
}

private class GpsLocationManagerDelegate(
    private val onLocationsUpdated: (List<*>) -> Unit,
    private val onAuthorizationChanged: (CLAuthorizationStatus) -> Unit,
) : NSObject(), CLLocationManagerDelegateProtocol {
    override fun locationManagerDidChangeAuthorization(manager: CLLocationManager) {
        onAuthorizationChanged(manager.authorizationStatus)
    }

    override fun locationManager(
        manager: CLLocationManager,
        didChangeAuthorizationStatus: CLAuthorizationStatus,
    ) {
        onAuthorizationChanged(didChangeAuthorizationStatus)
    }

    override fun locationManager(
        manager: CLLocationManager,
        didUpdateLocations: List<*>,
    ) {
        onLocationsUpdated(didUpdateLocations)
    }
}

private fun CLAuthorizationStatus.isGpsAuthorized(): Boolean =
    this == kCLAuthorizationStatusAuthorizedAlways || this == kCLAuthorizationStatusAuthorizedWhenInUse

private fun CLLocation.toGpsLocation(): GpsLocation {
    val coordinate = coordinate.useContents { latitude to longitude }
    return GpsLocation(
        latitude = coordinate.first,
        longitude = coordinate.second,
        altitudeMeters = altitude,
        horizontalAccuracyMeters = horizontalAccuracy.takeIf { it >= 0.0 },
        verticalAccuracyMeters = verticalAccuracy.takeIf { it >= 0.0 },
        speedMetersPerSecond = speed.takeIf { it >= 0.0 },
        bearingDegrees = course.takeIf { it >= 0.0 }?.let { ((it % 360.0) + 360.0) % 360.0 },
        timestampMillis = (timestamp.timeIntervalSince1970 * 1_000.0).toLong().coerceAtLeast(0L),
    )
}
