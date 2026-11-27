# `:weft-sensor` Namespace

## Role

`:weft-sensor` is a Gradle namespace project with no publication artifact. Each sensor capability
lives in its own nested KMP leaf so consumers can add and register only the sensors they use.

## Rules

- Keep the namespace parent free of build conventions, source files, and publication metadata.
- Put provider-neutral sensor contracts and native implementations in the owning leaf.
- Keep Android and Apple framework types in `androidMain` and `iosMain`; common APIs use Kotlin
  models and standard types.
- Give every distinct sensor capability its own `WeftPlugin` contract type so different sensors
  can be installed in one `Weft` runtime.
- Keep runtime permission requests and product disclosure at the consuming app boundary.
- Add each leaf to root verification, the coordinated release set, and the BOM only after its API
  and publication metadata are ready for those paths.

## Verification

- Do not run `:weft-sensor:check` or publish the namespace project.
- Verify a leaf such as GPS with `./gradlew :weft-sensor:gps:check`.
