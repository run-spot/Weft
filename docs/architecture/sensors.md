# Sensor capabilities

Weft exposes each sensor capability as an independent Kotlin Multiplatform leaf. The namespace
parent `:weft-sensor` has no artifact; the first leaf, `:weft-sensor:gps`, publishes
`run.thespot.weft:weft-gps`. Its `GpsPlugin` contract and native adapters live in that leaf, while
registration uses the existing `WeftConfig` registry from `:weft-core`.

Each sensor leaf declares its own plugin type. An application can install GPS, a map provider, and
other sensors in one `Weft` runtime. Installing two implementations of the same sensor type still
fails with the core duplicate-plugin check.

## Add the GPS leaf

Use the BOM to align versions, then add only the sensor capability the app needs:

~~~kotlin
dependencies {
    implementation(platform("run.thespot.weft:weft-bom:1.0.0"))
    implementation("run.thespot.weft:weft-gps")
}
~~~

Register GPS directly with Weft:

~~~kotlin
val weft = Weft {
    install(Gps) {
        distanceFilterMeters = 1.0
    }
}

val locations = weft.get<GpsPlugin>().locationUpdates
~~~

`locationUpdates` is a cold `Flow`. Collection starts the platform location service; cancelling
the collection removes its native listener. Each `GpsLocation` includes latitude, longitude,
available altitude, accuracy, speed, travel bearing, and the platform event timestamp. Updates are
not uploaded or retained by this library.

For an app that aggregates `WeftContribution` instances through Koin, the GPS artifact exposes
`GpsWeftContribution`; its `GpsKoinModule` registers `Gps` automatically with the discovered
contributions.

## Platform setup and permissions

The app owns permission disclosure, manifest and property-list entries, and runtime permission
prompts. Weft does not request permission implicitly.

On Android, declare the location permission required by the product and request it through the
app's permission flow. Before collecting the stream, initialize the Android adapter once with an
application context:

~~~kotlin
initializeWeftGps(applicationContext)
~~~

The adapter uses `LocationManager`'s enabled GPS and network providers. Collection fails with a
typed `GpsException` when location permission has not been granted, location providers are
unavailable, or Android setup has not run. The app remains responsible for foreground-service and
background-location policy when it needs a run to continue while backgrounded.

On iOS, add `NSLocationWhenInUseUsageDescription` to the app's `Info.plist`, request authorization
from the app, and collect after the user grants access. The adapter uses `CLLocationManager` and
stops updates when the flow is cancelled. This first adapter does not enable continuous background
updates; apps that need route tracking while backgrounded need an app-owned background mode and a
follow-up adapter/configuration path. Core Location can use GPS, Wi-Fi, cellular, and other
available sources; this API reports OS location updates rather than raw GNSS measurements.

The `distanceFilterMeters` configuration is a platform request hint. Location services may deliver
updates less often or with lower precision than requested. Consumers should inspect accuracy and
apply their product's filtering policy before recording a route.

## References

Reviewed 2026-11-27.

- [Android `LocationManager`](https://developer.android.com/reference/android/location/LocationManager)
- [Android location permissions](https://developer.android.com/develop/sensors-and-location/location/permissions)
- [Apple: Getting the current location of a device](https://developer.apple.com/documentation/CoreLocation/getting-the-current-location-of-a-device)
- [Apple: Requesting authorization to use location services](https://developer.apple.com/documentation/CoreLocation/requesting-authorization-to-use-location-services)
- [Apple: `allowsBackgroundLocationUpdates`](https://developer.apple.com/documentation/corelocation/cllocationmanager/allowsbackgroundlocationupdates)
