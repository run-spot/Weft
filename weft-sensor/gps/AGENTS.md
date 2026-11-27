# `:weft-sensor:gps` Library

## Role

`weft-gps` provides a provider-neutral Kotlin `Flow` of OS location updates through `GpsPlugin`.
Android uses `LocationManager`; Apple uses Core Location. The artifact has no vendor SDK dependency.

## Milestones

- **M0 — Build and publication skeleton:** added to the KMP conventions and Maven coordinates; compile and POM validation pending.
- **M1 — Public contract:** location model, configuration, typed failures, and Weft plugin registration are present.
- **M2 — Platform adapters:** Android and iOS streaming implementations are present; permission, lifecycle, and device validation pending.
- **M3 — Release candidate:** pending Android/iOS consumer builds, device testing, background execution review, and published-consumer validation.

## Rules

- Keep `Context`, `Location`, `CLLocationManager`, and other platform types out of `commonMain`.
- The Android app initializes the adapter with `initializeWeftGps(applicationContext)` before collecting.
- The app declares location permissions and requests them; the library only checks authorization and reports typed failures.
- Collection starts native updates and cancellation removes the listener.
- Do not claim raw GNSS-only data, exact sampling intervals, or background tracking guarantees.
- Do not upload or persist location data in this capability library.
- Expose `Gps` through a Koin `WeftContribution`; keep direct `Weft { install(Gps) }` available.
- Do not stage a Maven Central release before M3 and consumer validation.

## Verification

- `./gradlew :weft-sensor:gps:check`
- `./gradlew :weft-sensor:gps:checkPomFileForKotlinMultiplatformPublication`

See [`docs/architecture/sensors.md`](../../docs/architecture/sensors.md) for integration and
permission setup.
