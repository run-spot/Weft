plugins {
    id("weft.kmp-library")
    id("weft.maven-publish")
    id("weft.koin")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":weft-core"))
            api(libs.koin.annotations)
            api(libs.kotlinx.coroutines.core)
            implementation(libs.koin.core)
            implementation(libs.ktor.client.core)
            implementation(libs.kotlinx.serialization.json)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
            implementation(libs.androidx.credentials)
            implementation(libs.androidx.credentials.play.services.auth)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
    }
}

mavenPublishing {
    coordinates(artifactId = "weft-login")
}
