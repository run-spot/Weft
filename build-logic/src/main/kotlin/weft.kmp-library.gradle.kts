import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
}

kotlin {
    explicitApi()

    android {
        namespace = "run.thespot.weft.${project.name.removePrefix("weft-")}"
        compileSdk = providers.gradleProperty("android.compileSdk").get().toInt()
        minSdk = providers.gradleProperty("android.minSdk").get().toInt()

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }

        withHostTest {
            // Host tests run on the JVM without Android framework stubs. Returning defaults keeps
            // framework calls (for example the Compose runtime reporting a composition failure
            // through android.util.Log) from masking the original assertion failure.
            isReturnDefaultValues = true
        }
    }

    // Apple targets Compose Multiplatform publishes. Compose dropped the Intel simulator
    // (iosX64) publication, so weft-core could not resolve its Compose runtime dependency for
    // that target; the shared convention keeps only the device and Apple Silicon simulator.
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
