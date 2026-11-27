plugins {
    id("weft.kmp-library")
    id("weft.maven-publish")
    id("weft.koin")
}

mavenPublishing {
    coordinates(artifactId = "weft-gps")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":weft-core"))
            api(libs.koin.annotations)
            api(libs.kotlinx.coroutines.core)
            implementation(libs.koin.core)
        }
        androidMain.dependencies {
            implementation(libs.kotlinx.coroutines.android)
        }
    }
}
