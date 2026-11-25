plugins {
    id("com.android.library")
}

val weftVersion = providers.gradleProperty("VERSION_NAME").orElse("0.1.0-SNAPSHOT")

android {
    namespace = "consumer"
    compileSdk = 37

    defaultConfig {
        minSdk = 24
    }
}

dependencies {
    implementation(platform("run.thespot.weft:weft-bom:${weftVersion.get()}"))
    implementation("run.thespot.weft:weft-kakaomap")
}
