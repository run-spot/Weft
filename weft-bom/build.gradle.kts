plugins {
    `java-platform`
    id("weft.maven-publish")
}

val weftLiveReleaseEnabled = providers.gradleProperty("weftLiveReleaseEnabled")
    .map(String::toBoolean)
    .orElse(false)

dependencies {
    constraints {
        api(project(":weft-core"))
        api(project(":weft-map:kakaomap"))
        api(project(":weft-map:navermap"))
        api(project(":weft-sensor:gps"))
        api(project(":weft-login"))
        api(project(":weft-push"))
        if (weftLiveReleaseEnabled.get()) {
            api(project(":weft-live"))
        }
    }
}
