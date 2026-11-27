plugins {
    id("weft.kmp-library")
    id("weft.compose")
    id("weft.maven-publish")
}

mavenPublishing {
    coordinates(artifactId = "weft-navermap")
}

kotlin {
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.configureEach {
            freeCompilerArgs += "-Xoverride-konan-properties=minVersion.ios=13.0"
        }
        target.binaries.framework {
            baseName = "WeftNaverMap"
            isStatic = false
        }
    }

    swiftPMDependencies {
        discoverClangModulesImplicitly = true
        swiftPackage(
            url = url("https://github.com/navermaps/SPM-NMapsMap.git"),
            version = exact("3.24.1"),
            products = listOf(product("NMapsMap", platforms = setOf(iOS()))),
        )
        iosMinimumDeploymentTarget.set("13.0")
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":weft-core"))
        }
        androidMain.dependencies {
            implementation(libs.compose.ui)
            implementation(libs.lifecycle.runtime.compose)
            implementation("androidx.savedstate:savedstate:1.4.0")
            implementation(libs.naver.maps.android)
        }
    }
}
