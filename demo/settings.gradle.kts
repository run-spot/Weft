pluginManagement {
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

    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "weft-demo"

include(":androidApp", ":composeApp", ":iosApp")

includeBuild("..") {
    dependencySubstitution {
        substitute(module("run.thespot.weft:weft-core")).using(project(":weft-core"))
        substitute(module("run.thespot.weft:weft-kakaomap")).using(project(":weft-map:kakaomap"))
        substitute(module("run.thespot.weft:weft-login")).using(project(":weft-login"))
        substitute(module("run.thespot.weft:weft-push")).using(project(":weft-push"))
    }
}
