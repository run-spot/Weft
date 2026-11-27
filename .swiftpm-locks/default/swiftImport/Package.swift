// swift-tools-version: 5.9
import PackageDescription
let package = Package(
  name: "KotlinMultiplatformLinkedPackage",
  platforms: [
    .iOS("16.1")
  ],
  products: [
    .library(
      name: "KotlinMultiplatformLinkedPackage",
      type: .none,
      targets: ["KotlinMultiplatformLinkedPackage"]
    )
  ],
  dependencies: [
    .package(path: "subpackages/_weft-live"),
    .package(path: "subpackages/_weft-map_kakaomap"),
    .package(path: "subpackages/_weft-map_navermap"),
    .package(path: "subpackages/_weft-push")
  ],
  targets: [
    .target(
      name: "KotlinMultiplatformLinkedPackage",
      dependencies: [
        .product(name: "_weft-live", package: "_weft-live"),
        .product(name: "_weft-map_kakaomap", package: "_weft-map_kakaomap"),
        .product(name: "_weft-map_navermap", package: "_weft-map_navermap"),
        .product(name: "_weft-push", package: "_weft-push")
      ]
    )
  ]
)
