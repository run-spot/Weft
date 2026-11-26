# Naver Maps Android consumer fixture

This fixture verifies that an Android consumer resolves `weft-navermap` through the Weft BOM
and compiles against the public provider API. It intentionally does not declare the Naver SDK
directly; the provider's published metadata supplies that dependency.

No real client ID is committed. Replace the `NCP_KEY_ID` manifest placeholder with an NCP key
registered for the consuming application's Android package before running the SDK.

From the repository root, publish the provider and BOM to the local Maven repository, then build:

```sh
./gradlew :weft-map:navermap:publishToMavenLocal \
  :weft-bom:publishToMavenLocal
./gradlew -p harness/fixtures/weft-navermap-consumer assemble
```

The fixture resolves from `mavenLocal()` first and also declares Naver's official Maven repository
(`https://repository.map.naver.com/archive/maven`) for transitive metadata resolution. It uses AGP 9's
built-in Kotlin support and applies `org.jetbrains.kotlin.plugin.compose` at 2.4.21 to align the
built-in compiler with the provider's Kotlin metadata; it intentionally does not apply the legacy
`org.jetbrains.kotlin.android` plugin.
