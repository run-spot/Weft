plugins {
    id("weft.kmp-library")
    id("weft.compose")
    id("weft.maven-publish")
}

mavenPublishing {
    coordinates(artifactId = "weft-kakaomap")
}

kotlin {
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
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
        }
        androidMain.dependencies {
            implementation(libs.compose.ui)
            implementation(libs.kakao.maps.android)
            implementation(libs.lifecycle.runtime.compose)
        }
    }
}
