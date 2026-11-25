# Kakao Maps iOS SwiftPM consumer

This Xcode app fixture exercises the generated SwiftPM linkage package and Kakao Maps SDK
resource delivery. The Kakao SDK manifest copies `KakaoMapsSDKBundle.bundle/assets`; SwiftPM
wraps that directory in its generated product resource bundle. After a successful simulator build,
verify the app contains:

```text
WeftKakaoConsumer.app/KakaoMapsSDK-SPM_KakaoMapsSDK-SPM.bundle/assets/KakaoMapsSDKBundle.plist
```

The SDK's original `KakaoMapsSDKBundle.bundle` directory is present in the resolved package
checkout, not as a top-level directory in this fixture or in the built app. The Xcode project
excludes the unsupported `x86_64` simulator architecture because the library publishes only
`iosArm64` and `iosSimulatorArm64` Kotlin/Native targets.
