@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi::class)

plugins {
    id("weft.kmp-library")
    id("weft.maven-publish")
    id("weft.koin")
}

kotlin {
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "WeftPush"
            isStatic = true
        }
    }

    swiftPMDependencies {
        discoverClangModulesImplicitly = true
        swiftPackage(
            url = url("https://github.com/firebase/firebase-ios-sdk.git"),
            version = exact("13.2.0"),
            products = listOf(
                product("FirebaseCore", platforms = setOf(iOS())),
                product("FirebaseMessaging", platforms = setOf(iOS())),
            ),
        )
        iosMinimumDeploymentTarget.set("15.0")
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":weft-core"))
            api(libs.koin.annotations)
            api(libs.kotlinx.coroutines.core)
            implementation(libs.koin.core)
        }
        androidMain.dependencies {
            implementation(libs.firebase.messaging)
        }
    }
}
