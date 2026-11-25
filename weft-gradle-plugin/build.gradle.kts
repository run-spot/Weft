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
                "Checks that required Weft plugins are installed, that no client secret ships in " +
                    "code, that declared dependencies are on the Weft allowlist, and that " +
                    "module-declared health checks answer."
        }
    }
}
