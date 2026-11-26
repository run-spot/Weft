plugins {
    id("weft.kmp-library")
    id("weft.maven-publish")
    id("weft.compose")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // `LocalWeft`, `WeftApp`, and the provider-neutral map contract expose Compose
            // runtime, UI, and foundation types, so consumers receive those public dependencies
            // through this module. Vendor SDKs and platform framework types stay out of commonMain.
            api(libs.compose.runtime)
            api(libs.compose.ui)
            api(libs.compose.foundation)
        }
    }
}
