# `:weft-map` Namespace

## Role

`:weft-map` is a Gradle namespace project, not a publication artifact. Provider integrations live in nested leaf modules. The current map leaf is `:weft-map:kakaomap`, configured for coordinates `run.thespot.weft:weft-kakaomap` and version-aligned by `:weft-bom`.

## Provider: Kakao Maps

- Keep Android SDK, UIKit, and provider SDK types out of `commonMain`.
- Add dependencies only to the source set that needs them.
- Keep the Kakao Maven repository and Android SDK dependency in the Kakao leaf configuration.
- The iOS adapter imports Kakao Maps SDK 2.12.19 through the Kotlin SwiftPM integration and supports iOS 13.0.
- Do not add a generic provider framework before a second real integration requires one.
- Do not release a provider leaf without validating its platform publication metadata and a separate consumer.

## Verification

- `./gradlew :weft-map:kakaomap:check`
- `./gradlew :weft-map:kakaomap:checkPomFileForKotlinMultiplatformPublication`
- `./gradlew :weft-map:kakaomap:checkPomFileForAndroidPublication`
- `./gradlew verifyKakaoMapPublication`

Do not run `:weft-map:check` or publish the namespace project; it has no code or artifact of its own.
