import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
}

kotlin {
    explicitApi()

    android {
        namespace = "io.github.runspot.weft.${project.name}"
        compileSdk = providers.gradleProperty("android.compileSdk").get().toInt()
        minSdk = providers.gradleProperty("android.minSdk").get().toInt()

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }

        withHostTest {}
    }

    iosArm64()
    iosSimulatorArm64()
    iosX64()

    sourceSets {
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
