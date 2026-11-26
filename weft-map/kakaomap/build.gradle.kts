plugins {
    id("weft.kmp-library")
    id("weft.compose")
    id("weft.maven-publish")
    id("weft.koin")
}

mavenPublishing {
    coordinates(artifactId = "weft-kakaomap")
}

kotlin {
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        // Kotlin 2.4 raises its default Apple minimum to iOS 15; the public Kakao adapter
        // contract and Kakao Maps SDK support iOS 13, so preserve that deployment floor.
        target.binaries.configureEach {
            freeCompilerArgs += "-Xoverride-konan-properties=minVersion.ios=13.0"
        }
        target.binaries.framework {
            baseName = "WeftKakaoMap"
            isStatic = true
        }
    }

    swiftPMDependencies {
        discoverClangModulesImplicitly = true
        swiftPackage(
            url = url("https://github.com/kakao-mapsSDK/KakaoMapsSDK-SPM.git"),
            version = exact("2.12.19"),
            products = listOf(product("KakaoMapsSDK-SPM", platforms = setOf(iOS()))),
        )
        iosMinimumDeploymentTarget.set("13.0")
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":weft-core"))
            api(libs.koin.annotations)
            implementation(libs.koin.core)
        }
        androidMain.dependencies {
            implementation(libs.compose.ui)
            implementation(libs.kakao.maps.android)
            implementation(libs.lifecycle.runtime.compose)
        }
    }
}
