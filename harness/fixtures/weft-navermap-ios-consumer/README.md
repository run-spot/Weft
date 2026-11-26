# Naver Maps iOS consumer fixture

This Xcode fixture validates the dynamic `WeftNaverMap.framework`, the official Naver Maps Swift
Package Manager dependency, and Naver's framework resources. It uses only the
`REPLACE_WITH_NCP_KEY_ID` placeholder in `Info.plist`; never commit a real NCP key. The app and
framework target iOS 13.0. The Xcode target excludes `x86_64` simulator builds because this
provider publishes Apple Silicon device/simulator Kotlin targets only.

From the repository root, publish the provider/BOM locally for consumer metadata checks, integrate
the generated SwiftPM linkage package into this Xcode project, then build:

```sh
./gradlew :weft-map:navermap:publishToMavenLocal \
  :weft-bom:publishToMavenLocal
XCODEPROJ_PATH="$PWD/harness/fixtures/weft-navermap-ios-consumer/WeftNaverConsumer.xcodeproj" \
  ./gradlew :weft-map:navermap:integrateLinkagePackage
xcodebuild -project harness/fixtures/weft-navermap-ios-consumer/WeftNaverConsumer.xcodeproj \
  -scheme WeftNaverConsumer -sdk iphonesimulator \
  -derivedDataPath harness/fixtures/weft-navermap-ios-consumer/build build
```

After the build, verify dynamic framework/resource delivery:

```sh
APP=harness/fixtures/weft-navermap-ios-consumer/build/Build/Products/Debug-iphonesimulator/WeftNaverConsumer.app
test -x "$APP/Frameworks/NMapsMap.framework/NMapsMap"
test -f "$APP/Frameworks/NMapsMap.framework/Assets.car"
test "$(/usr/libexec/PlistBuddy -c 'Print :MinimumOSVersion' "$APP/Info.plist")" = 13.0
test "$(/usr/libexec/PlistBuddy -c 'Print :MinimumOSVersion' \
  "$APP/Frameworks/WeftNaverMap.framework/Info.plist")" = 13.0
# The official NMapsMap 3.24.1 vendor binary advertises iOS 9.0; preserve its upstream plist.
test "$(/usr/libexec/PlistBuddy -c 'Print :MinimumOSVersion' \
  "$APP/Frameworks/NMapsMap.framework/Info.plist")" = 9.0
test -x "$APP/Frameworks/WeftNaverMap.framework/WeftNaverMap"
file "$APP/Frameworks/NMapsMap.framework/NMapsMap" | grep "dynamically linked"
```

The Naver SDK must remain a separate dynamically linked framework with its resource bundle
delivered to the app; it must not be statically merged into the KMP framework. An iOS framework
compile/link or consumer-build failure blocks provider completion rather than silently downgrading
the provider to Android-only.
