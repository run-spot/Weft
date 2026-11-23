allprojects {
    group = providers.gradleProperty("GROUP").get()
    version = providers.gradleProperty("VERSION_NAME").get()
}

tasks.register("verify") {
    group = "verification"
    description = "Checks every library module and validates Maven publication metadata."

    dependsOn(subprojects.map { "${it.path}:check" })

    val publicationChecks = listOf(
        "checkPomFileForKotlinMultiplatformPublication",
        "checkPomFileForAndroidPublication",
        "checkPomFileForIosArm64Publication",
        "checkPomFileForIosSimulatorArm64Publication",
        "checkPomFileForIosX64Publication",
    )
    subprojects.forEach { library ->
        publicationChecks.forEach { taskName ->
            dependsOn("${library.path}:$taskName")
        }
    }
}
