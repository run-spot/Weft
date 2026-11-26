// swift-tools-version: 5.9
import PackageDescription
let package = Package(
  name: "_weft-live",
  platforms: [
    .iOS("16.1")
  ],
  products: [
    .library(
      name: "_weft-live",
      type: .none,
      targets: ["_weft-live"]
    )
  ],
  dependencies: [
    .package(
      path: "../../../../../weft-live/iosInterop/WeftLiveKit"
    )
  ],
  targets: [
    .target(
      name: "_weft-live",
      dependencies: [
        .product(
          name: "WeftLiveKit",
          package: "WeftLiveKit"
        )
      ]
    )
  ]
)
