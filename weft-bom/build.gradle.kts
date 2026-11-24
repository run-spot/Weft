plugins {
    `java-platform`
    id("weft.maven-publish")
}

dependencies {
    constraints {
        api(project(":weft-map"))
        api(project(":weft-login"))
        api(project(":weft-push"))
    }
}
