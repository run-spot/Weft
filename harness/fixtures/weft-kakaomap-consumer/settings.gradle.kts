pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        id("com.android.library") version "9.3.1"
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenLocal()
        maven("https://devrepo.kakao.com/nexus/repository/kakaomap-releases/")
        google()
        mavenCentral()
    }
}

rootProject.name = "weft-kakaomap-consumer"
