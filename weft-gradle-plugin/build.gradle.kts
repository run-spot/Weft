plugins {
    id("weft.jvm")
}

gradlePlugin {
    plugins {
        create("weftVerify") {
            id = "weft.verify"
            implementationClass = "run.thespot.weft.gradle.WeftGradlePlugin"
            displayName = "Weft verification"
            description =
                "Checks that required Weft plugins are installed, that configured credentials are " +
                    "not empty or placeholders, and that module-declared health checks answer."
        }
    }
}
