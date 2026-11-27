# `:weft-map` Namespace

## Role

`:weft-map` is a Gradle namespace project, not a publication artifact. Provider integrations live in nested leaf modules. The current map leaves are `:weft-map:kakaomap` (`run.thespot.weft:weft-kakaomap`) and `:weft-map:navermap` (`run.thespot.weft:weft-navermap`), both version-aligned by `:weft-bom`.

## Provider leaves

- Keep Android SDK, UIKit, and provider SDK types out of `commonMain`.
- Publish the Kakao capability's Koin registration as a `WeftContribution` in `commonMain`; apply `weft.koin` here and keep Koin annotations and contribution construction in this leaf, not in `:weft-core`.
- Read the public native app key from the consuming app's Koin property `weft.kakaoMap.nativeAppKey`; do not put the key value in the library or generated annotation metadata.
- Add dependencies only to the source set that needs them.
- Keep the Kakao Maven repository and Android SDK dependency in the Kakao leaf configuration. Kakao uses Android SDK 2.15.2 and SwiftPM SDK 2.12.19.
- Keep the Naver Maven repository and Android SDK dependency in the Naver leaf configuration. Naver uses `com.naver.maps:map-sdk` 3.24.1 and SwiftPM `NMapsMap` 3.24.1.
- Both provider leaves support iOS 13.0. Naver's KMP framework is dynamic so the official dynamic Naver XCFramework can be delivered to consumers.
- Applications install exactly one `MapPlugin` provider per `Weft` runtime; a second provider installation must fail through the core duplicate-plugin check.
- Do not release a provider leaf without validating its platform publication metadata and a separate Android/iOS consumer.

## Verification

- `./gradlew :weft-map:kakaomap:check`
- `./gradlew :weft-map:kakaomap:checkPomFileForKotlinMultiplatformPublication`
- `./gradlew :weft-map:kakaomap:checkPomFileForAndroidPublication`
- `./gradlew verifyKakaoMapPublication`
- `./gradlew :weft-map:navermap:check`
- `./gradlew :weft-map:navermap:checkPomFileForKotlinMultiplatformPublication`
- `./gradlew :weft-map:navermap:checkPomFileForAndroidPublication`
- `./gradlew verifyNaverMapPublication`

Do not run `:weft-map:check` or publish the namespace project; it has no code or artifact of its own.
