plugins {
    id("weft.kmp-library")
    id("weft.maven-publish")
    id("weft.compose")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // `LocalWeft` and `WeftApp` are public API that exposes a Compose composition local, so
            // consumers receive the Compose runtime through this module. Only the runtime is added:
            // no Material, UI, or foundation dependency belongs in the capability contract module.
            api(libs.compose.runtime)
        }
    }
}
