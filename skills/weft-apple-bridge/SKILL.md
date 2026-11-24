---
name: weft-apple-bridge
description: "Design or implement an Apple SDK boundary for a KMP library, including SwiftPM import, Objective-C façades, and distribution. Use when Kotlin/Native needs Apple SDK APIs or Swift needs to consume a KMP library."
---

# Weft Apple Bridge

Use this skill for native SDK boundaries, not ordinary SwiftUI screens or generic KMP architecture planning. Read the selected project's WEFT.md or architecture record. If a Weft checkout is available, consult docs/architecture/dependency-policy.md, apple-interop.md, bridge-library.md, and toolchain-policy.md.

Before proposing code, name the integration direction:

- Kotlin iosMain imports a SwiftPM package's Clang/Objective-C APIs.
- Swift consumes a Kotlin/Native XCFramework through a Swift package.
- Swift implements or injects an exported Kotlin contract.

Keep commonMain APIs platform-neutral. Match Android behavior when useful, but do not expose Android framework types. Map cancellation, threading, errors, and lifecycle at the platform adapter. Add a minimal façade when a Swift-only API is not visible through Objective-C/Clang.

Follow the strict application dependency and iOS implementation rules in `docs/architecture/dependency-policy.md`:

- App startup and `UIApplicationDelegate`-level product behavior belong in Kotlin. Keep any Xcode-required Swift app entry as a bootstrap/forwarder only.
- Do not implement ordinary app screens or business behavior in Swift. Swift app code is reserved for Xcode widgets/extensions and the narrow SDK façade below.
- When an SDK is Objective-C/Clang-visible, declare it through KMP SwiftPM import and adapt it in `iosMain`.
- When a required SDK is Swift-only and cannot be imported by KMP, create a minimal SwiftPM façade with an Objective-C-visible API exposing only Objective-C-compatible declarations. Import it into the KMP bridge module, implement the Kotlin adapter, then publish that KMP bridge as a `run.thespot.weft:weft-*` artifact.
- Do not add the raw SDK as a direct application dependency. Commit the Swift package lock data with the KMP bridge source. A Maven KMP artifact does not contain the Swift package source; verify Kotlin Gradle Plugin transitive SwiftPM resolution for Gradle/KMP consumers. Native SwiftPM consumers need a separate XCFramework package distribution.

Use only dependencies allowed by `docs/architecture/dependency-policy.md`. If the SDK or tool requires another direct library, stop and record a policy blocker instead of adding it silently.

Kotlin's reviewed SwiftPM import and Swift export docs mark these features Alpha. As of the reviewed import guide, SwiftPM import requires KGP 2.5.0-RC; the current Weft KMP/AGP 9+ baseline pins KGP 2.4.21 and has not validated this integration. Keep the bridge blocked until the exact KGP/Xcode tuple is selected and a published Maven consumer links and archives successfully. The import guide also warns that exporting a KMP module that uses SwiftPM import as a Swift package may not work. Treat Maven Central and native SwiftPM/XCFramework as separate consumer paths. Do not claim that an import experiment removes the app's Kotlin framework or improves build time without a measured comparison.
