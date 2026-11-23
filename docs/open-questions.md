# Open questions

Resolve these before public project operations or a real library release.

## Project operations

- Should Weft skills target Codex only, or should we validate compatibility with other Agent Skills hosts?

## Templates

- Should the first buildable template share UI with CMP or keep native UI over a KMP core?
- Which minimum iOS, Android SDK, and Xcode versions match the intended users?
- If the Spring example is included, should client/server contracts come from shared KMP source, generated OpenAPI, or a separate contract module?

## Bridge library

- Can the `io.github.run-spot` namespace be verified and managed by the RunTheSpot organization in Central Portal?
- Which iOS Swift SDK and Android SDK should the first pilot connect?
- Should compatibility with an Android API preserve behavior only, or also naming and call shape?
- Which Android minSdk and iOS deployment target should the first released artifacts support?
- How should the SwiftPM package repository and XCFramework hosting be managed?
- Until Kotlin SwiftPM import stabilizes, should the bridge remain experimental or also support an Objective-C façade with CocoaPods/local package integration?

## Version policy

- Should only officially compatible tuples be marked stable while versions observed in RunTheSpot remain candidates?
- Who owns the compatibility matrix and how often should it be reviewed?
