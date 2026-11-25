pluginManagement {
    includeBuild("build-logic")

    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)

    repositories {
        google()
        mavenCentral()
        maven("https://devrepo.kakao.com/nexus/repository/kakaomap-releases/")
    }
}

rootProject.name = "Weft"

include(":weft-core", ":weft-login", ":weft-push", ":weft-bom", ":weft-gradle-plugin")
include(":weft-map:kakaomap")
