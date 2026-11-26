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
      url: "https://github.com/navermaps/SPM-NMapsMap.git",
      exact: "3.24.1"
    ),
    .package(path: "subpackages/KotlinMultiplatformLinkedPackageDylib")
  ],
  targets: [
    .target(
      name: "KotlinMultiplatformLinkedPackage",
      dependencies: [
        .product(name: "KotlinMultiplatformLinkedPackageDylib", package: "KotlinMultiplatformLinkedPackageDylib")
      ]
    )
  ]
)
