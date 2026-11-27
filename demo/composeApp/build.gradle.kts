import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    android {
        namespace = "run.thespot.weft.demo.shared"
        compileSdk = 37
        minSdk = 24

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.configureEach {
            freeCompilerArgs += "-Xoverride-konan-properties=minVersion.ios=13.0"
        }
        target.binaries.framework {
            baseName = "WeftDemo"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.foundation)
            implementation(libs.compose.runtime)
            implementation(libs.compose.ui)

            implementation("run.thespot.weft:weft-core:0.1.0-SNAPSHOT")
            implementation("run.thespot.weft:weft-kakaomap:0.1.0-SNAPSHOT")
            implementation("run.thespot.weft:weft-login:0.1.0-SNAPSHOT")
            implementation("run.thespot.weft:weft-push:0.1.0-SNAPSHOT")
        }
    }
}
