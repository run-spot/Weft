plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "run.thespot.weft.demo.android"
    compileSdk = 37

    defaultConfig {
        applicationId = "run.thespot.weft.demo.android"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
    }
}

dependencies {
    implementation(project(":composeApp"))
    implementation(libs.androidx.activity)
    implementation(libs.androidx.activity.compose)
}
