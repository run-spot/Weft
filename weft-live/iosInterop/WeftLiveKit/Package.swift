// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "WeftLiveKit",
    platforms: [.iOS("16.1")],
    products: [
        .library(name: "WeftLiveKit", targets: ["WeftLiveKit"]),
    ],
    targets: [
        .target(name: "WeftLiveKit", path: "Sources/WeftLiveKit"),
        .testTarget(
            name: "WeftLiveKitTests",
            dependencies: ["WeftLiveKit"],
            path: "Tests/WeftLiveKitTests",
        ),
    ],
)
