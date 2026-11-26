// swift-tools-version: 5.9
import PackageDescription
let package = Package(
  name: "_weft-map_kakaomap",
  platforms: [
    .iOS("16.1")
  ],
  products: [
    .library(
      name: "_weft-map_kakaomap",
      type: .none,
      targets: ["_weft-map_kakaomap"]
    )
  ],
  dependencies: [
    .package(
      url: "https://github.com/kakao-mapsSDK/KakaoMapsSDK-SPM.git",
      exact: "2.12.19"
    )
  ],
  targets: [
    .target(
      name: "_weft-map_kakaomap",
      dependencies: [
        .product(
          name: "KakaoMapsSDK-SPM",
          package: "KakaoMapsSDK-SPM",
          condition: .when(platforms: [.iOS])
        )
      ]
    )
  ]
)
