# Open questions

Resolve these before public project operations or a real library release.

## Project operations

- Should Weft skills target Codex only, or should we validate compatibility with other Agent Skills hosts?

## Templates

- Should the first buildable template share all UI with CMP or use Kotlin platform-specific UI over a shared KMP core?
- Which minimum iOS, Android SDK, and Xcode versions match the intended users?
- For the first Spring auth template, should Weft host Spring Authorization Server or document an external PKCE-capable OIDC issuer as the default?
- Should client/server contracts come from shared KMP source, generated OpenAPI, or a separate contract module?
- Which Supabase authentication and data-access capabilities should `weft-login` cover first without adding a direct Supabase SDK dependency to apps?

## Bridge library

- Can the `run.thespot` namespace be verified and managed in Central Portal before the first release?
- Which iOS Swift SDK and Android SDK should the first pilot connect?
- Should compatibility with an Android API preserve behavior only, or also naming and call shape?
- Which Android minSdk and iOS deployment target should the first released artifacts support?
- How should the SwiftPM package repository and XCFramework hosting be managed?
- Which SDK pair should the first SwiftPM import pilot support, and what Objective-C-visible façade does its Swift API require?
- Which KGP version should a SwiftPM-backed bridge use? The reviewed SwiftPM guide is Alpha and specifies KGP 2.5.0-RC, while the current KMP/AGP 9+ bridge baseline pins KGP 2.4.21.

## Version policy

- Should only officially compatible tuples be marked stable while unverified versions remain candidates?
- Who owns the compatibility matrix and how often should it be reviewed?
