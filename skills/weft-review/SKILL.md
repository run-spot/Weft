---
name: weft-review
description: "Review a KMP/CMP project's module boundaries, platform interop, toolchain claims, and generated Weft profile. Use for architecture reviews or before promoting a scaffold to a supported template."
---

# Weft Review

Review a project against its recorded Weft profile and current architecture guidance. This is a focused architecture review, not a general code-quality audit. If a Weft checkout is available, consult docs/architecture for profile rationale and current status.

When the Weft CLI is available, run python3 scripts/weft.py verify --project <path> for a generated project. Then inspect relevant build files and platform entry points. If the CLI is unavailable, perform the manual checks below and say that structural CLI validation was not run.

Check:

- Shared and native responsibilities match the selected profile.
- Android application setup and KMP Android library setup are separate where required by AGP 9+.
- commonMain APIs do not expose Android or Apple SDK types.
- SwiftPM import, framework export, and reverse implementation are described as separate paths.
- Backend modules exist only when selected, and Spring Boot remains in a separate JVM application module.
- Claimed supported versions have official compatibility sources and successful Android/iOS CI evidence.
- The project is not called build-ready unless Android and iOS builds have been verified.

Report findings with file paths, impact, and whether each issue is a mismatch, missing evidence, or open decision. Keep observations separate from recommendations.
