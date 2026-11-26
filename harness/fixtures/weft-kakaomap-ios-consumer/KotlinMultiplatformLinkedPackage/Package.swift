// swift-tools-version: 5.9
import PackageDescription
let package = Package(
  name: "KotlinMultiplatformLinkedPackage",
  platforms: [
    .iOS("13.0")
  ],
  products: [
    .library(
      name: "KotlinMultiplatformLinkedPackage",
      type: .none,
      targets: ["KotlinMultiplatformLinkedPackage"]
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
      name: "KotlinMultiplatformLinkedPackage",
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
