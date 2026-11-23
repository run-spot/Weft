---
name: weft-apple-bridge
description: "Design or implement an Apple SDK boundary for a KMP library, including SwiftPM import, Objective-C façades, and distribution. Use when Kotlin/Native needs Apple SDK APIs or Swift needs to consume a KMP library."
---

# Weft Apple Bridge

Use this skill for native SDK boundaries, not ordinary SwiftUI screens or generic KMP architecture planning. Read the selected project's WEFT.md or architecture record. If a Weft checkout is available, consult docs/architecture/apple-interop.md, bridge-library.md, and toolchain-policy.md.

Before proposing code, name the integration direction:

- Kotlin iosMain imports a SwiftPM package's Clang/Objective-C APIs.
- Swift consumes a Kotlin/Native XCFramework through a Swift package.
- Swift implements or injects an exported Kotlin contract.

Keep commonMain APIs platform-neutral. Match Android behavior when useful, but do not expose Android framework types. Map cancellation, threading, errors, and lifecycle at the platform adapter. Add a minimal façade when a Swift-only API is not visible through Objective-C/Clang.

Kotlin's reviewed SwiftPM import and Swift export docs mark these features Alpha. The import guide also warns that exporting a KMP module that uses SwiftPM import as a Swift package may not work. Check the current official docs and the project's exact Kotlin/Xcode tuple before implementation. Treat Maven Central and SwiftPM/XCFramework as separate consumer paths. Do not claim that an import experiment removes the app's Kotlin framework or improves build time without a measured comparison.
