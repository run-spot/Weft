plugins {
    id("weft.kmp-library")
    id("weft.maven-publish")
    id("weft.koin")
}

kotlin {
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "WeftLive"
            isStatic = true
        }
    }

    swiftPMDependencies {
        localSwiftPackage(
            directory = project.layout.projectDirectory.dir("iosInterop/WeftLiveKit"),
            products = listOf("WeftLiveKit"),
        )
        iosMinimumDeploymentTarget.set("16.1")
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":weft-core"))
            api(libs.koin.annotations)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.koin.core)
        }
    }
}
