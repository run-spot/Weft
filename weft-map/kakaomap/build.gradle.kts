plugins {
    id("weft.kmp-library")
    id("weft.compose")
    id("weft.maven-publish")
}

mavenPublishing {
    coordinates(artifactId = "weft-kakaomap")
}

kotlin {
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
