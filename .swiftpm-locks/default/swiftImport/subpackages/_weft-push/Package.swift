// swift-tools-version: 5.9
import PackageDescription
let package = Package(
  name: "_weft-push",
  platforms: [
    .iOS("16.1")
  ],
  products: [
    .library(
      name: "_weft-push",
      type: .none,
      targets: ["_weft-push"]
    )
  ],
  dependencies: [
    .package(
      url: "https://github.com/firebase/firebase-ios-sdk.git",
      exact: "13.2.0"
    )
  ],
  targets: [
    .target(
      name: "_weft-push",
      dependencies: [
        .product(
          name: "FirebaseCore",
          package: "firebase-ios-sdk",
          condition: .when(platforms: [.iOS])
        ),
        .product(
          name: "FirebaseMessaging",
          package: "firebase-ios-sdk",
          condition: .when(platforms: [.iOS])
        )
      ]
    )
  ]
)
