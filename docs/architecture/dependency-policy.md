# Application Dependency Policy

Weft projects use a strict allowlist for direct application dependencies. The goal is to ship app features from a small, shared library set instead of selecting a new third-party library for each feature.

## Approved product libraries

Application modules may directly depend on:

- Weft artifacts in `run.thespot.weft` whose artifact IDs start with `weft-`.
- `run.thespot.montage:montage-kmp` for shared UI components and design-system behavior.
- Ktor client artifacts (`io.ktor:ktor-client-*`) and the Kotlin serialization JSON adapter (`io.ktor:ktor-serialization-kotlinx-json`) for HTTP.
- Ktorfit artifacts (`de.jensklingenberg.ktorfit:ktorfit-*`) for declarative HTTP APIs.

Kotlin, coroutines, serialization runtime, Compose Multiplatform, and the AndroidX Compose, Activity, Lifecycle, SavedState, and Core foundation artifacts required by the selected profile are platform foundations. They are listed in [the machine-readable allowlist](../../harness/dependency-policy.json). Kotlin/Gradle plugins, Android SDKs, and Apple SDKs are toolchain or platform APIs, not application libraries.

Do not add another direct library for dependency injection, navigation, storage, image loading, analytics, logging, date/time, or UI components. Use the approved libraries and platform APIs. If a capability is missing, record the gap and add it to Weft or Montage through an explicit policy change. Do not install a one-off dependency in an app to bypass the allowlist.

The allowlist applies to direct dependencies. Approved libraries may have transitive dependencies required to implement their APIs; do not redeclare those transitive components directly unless they are also allowlisted.

## iOS implementation policy

- Keep app startup and `UIApplicationDelegate`-level product behavior in Kotlin. Any Swift app shell needed by Xcode only boots or forwards into Kotlin.
- Implement app screens, state, and business behavior in Kotlin. Do not add SwiftUI app screens.
- Swift app code is permitted for Xcode widgets and extensions, or for the narrow compatibility façade described below.
- Keep `Package.swift` dependencies out of app targets. SDK packages belong behind an approved Weft KMP bridge.

### Swift-only SDK bridge

If a required SDK exposes only Swift APIs that Kotlin/Native cannot import through SwiftPM, create the smallest Swift package façade with an Objective-C-visible API. Keep the façade limited to bridging that SDK; do not put app behavior in it. Declare the package through the KMP module's SwiftPM import configuration and call the Objective-C-compatible API from `iosMain`. Publish the Kotlin bridge library as the corresponding `run.thespot.weft:weft-*` artifact.

Maven Central distributes the Kotlin bridge artifact; it does not embed the Swift package source. Commit the SwiftPM dependency declaration and resolved version lock with the bridge source. Gradle/KMP consumers rely on Kotlin Gradle Plugin support for transitive SwiftPM dependencies, which must be verified with the selected plugin version. Native SwiftPM consumers need a separately published XCFramework package. Verify the complete iOS link and archive path before release.

## Verification

`python3 scripts/weft.py verify --project <path>` checks direct Maven dependencies when Gradle build files are present. The verifier supports direct coordinates, `add`/`addProvider` configuration declarations, and aliases from the default `gradle/libs.versions.toml` catalog. Unsupported or unresolved dependency notation fails closed and requires conversion to a checked form before approval. Do not hide application dependencies in convention plugins or indirect build logic; declare them in the application module so the policy check can inspect them.

The server is a separate optional profile. Server-only dependencies are outside the app allowlist and must follow the selected backend architecture.

## References

- [Ktor Multiplatform client](https://ktor.io/docs/client-create-multiplatform-application.html)
- [Ktorfit](https://foso.github.io/Ktorfit/)
- [Gradle Java Platform and Maven BOMs](https://docs.gradle.org/current/userguide/java_platform_plugin.html)
- [Kotlin SwiftPM import into KMP](https://kotlinlang.org/docs/multiplatform/multiplatform-spm-import.html)
