plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.compose")
}

val weftVersion = providers.gradleProperty("VERSION_NAME").orElse("0.1.0-SNAPSHOT")

android {
    namespace = "consumer"
    compileSdk = 37

    defaultConfig {
        minSdk = 24
        manifestPlaceholders["NCP_KEY_ID"] = "REPLACE_WITH_NCP_KEY_ID"
    }
}

dependencies {
    implementation(platform("run.thespot.weft:weft-bom:${weftVersion.get()}"))
    implementation("run.thespot.weft:weft-navermap")
}
