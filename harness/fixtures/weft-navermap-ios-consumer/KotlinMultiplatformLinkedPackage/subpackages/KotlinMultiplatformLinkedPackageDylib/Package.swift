// swift-tools-version: 5.9
import PackageDescription
let package = Package(
  name: "KotlinMultiplatformLinkedPackageDylib",
  platforms: [
    .iOS("13.0")
  ],
  products: [
    .library(
      name: "KotlinMultiplatformLinkedPackageDylib",
      type: .dynamic,
      targets: ["KotlinMultiplatformLinkedPackageDylib"]
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
      name: "KotlinMultiplatformLinkedPackageDylib",
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
