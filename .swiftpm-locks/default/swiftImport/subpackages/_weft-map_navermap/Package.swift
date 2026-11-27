// swift-tools-version: 5.9
import PackageDescription
let package = Package(
  name: "_weft-map_navermap",
  platforms: [
    .iOS("16.1")
  ],
  products: [
    .library(
      name: "_weft-map_navermap",
      type: .none,
      targets: ["_weft-map_navermap"]
    )
  ],
  dependencies: [
    .package(
      url: "https://github.com/navermaps/SPM-NMapsMap.git",
      exact: "3.24.1"
    )
  ],
  targets: [
    .target(
      name: "_weft-map_navermap",
      dependencies: [
        .product(
          name: "NMapsMap",
          package: "SPM-NMapsMap",
          condition: .when(platforms: [.iOS])
        )
      ]
    )
  ]
)
