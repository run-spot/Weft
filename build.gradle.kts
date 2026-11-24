allprojects {
    group = providers.gradleProperty("GROUP").get()
    version = providers.gradleProperty("VERSION_NAME").get()
}

tasks.register("verify") {
    group = "verification"
    description = "Checks every library module and validates Maven publication metadata."

    dependsOn(subprojects.map { "${it.path}:check" })

    val kmpPublicationChecks = listOf(
        "checkPomFileForKotlinMultiplatformPublication",
        "checkPomFileForAndroidPublication",
        "checkPomFileForIosArm64Publication",
        "checkPomFileForIosSimulatorArm64Publication",
        "checkPomFileForIosX64Publication",
    )
    listOf(":weft-map", ":weft-login", ":weft-push").forEach { libraryPath ->
        kmpPublicationChecks.forEach { taskName ->
            dependsOn("$libraryPath:$taskName")
        }
    }
    dependsOn("verifyWeftBomPublication")
}

tasks.register("verifyWeftBomPublication") {
    group = "verification"
    description = "Checks that the generated Maven BOM aligns all published Weft libraries."
    dependsOn(":weft-bom:generatePomFileForMavenPublication")

    doLast {
        val pomFile = file("weft-bom/build/publications/maven/pom-default.xml")
        check(pomFile.isFile) { "Weft BOM POM was not generated: $pomFile" }

        val document = javax.xml.parsers.DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(pomFile)
        val pom = document.documentElement

        fun directChildText(element: org.w3c.dom.Element, name: String): String? {
            for (index in 0 until element.childNodes.length) {
                val child = element.childNodes.item(index)
                if (child is org.w3c.dom.Element && child.localName == name) return child.textContent
            }
            return null
        }

        val expectedGroup = providers.gradleProperty("GROUP").get()
        val expectedVersion = providers.gradleProperty("VERSION_NAME").get()
        check(directChildText(pom, "groupId") == expectedGroup) { "Weft BOM groupId must be $expectedGroup" }
        check(directChildText(pom, "artifactId") == "weft-bom") { "Weft BOM artifactId must be weft-bom" }
        check(directChildText(pom, "version") == expectedVersion) { "Weft BOM version must be $expectedVersion" }

        val dependencyManagement = pom.getElementsByTagNameNS("*", "dependencyManagement").item(0) as org.w3c.dom.Element
        val dependencies = dependencyManagement.getElementsByTagNameNS("*", "dependency")
        val actual = (0 until dependencies.length).map { index ->
            val dependency = dependencies.item(index) as org.w3c.dom.Element
            listOf(
                directChildText(dependency, "groupId"),
                directChildText(dependency, "artifactId"),
                directChildText(dependency, "version"),
            ).joinToString(":")
        }.toSet()
        val expected = setOf("weft-map", "weft-login", "weft-push")
            .map { artifact -> "$expectedGroup:$artifact:$expectedVersion" }
            .toSet()
        check(actual == expected) {
            "Weft BOM constraints differ. Expected $expected but found $actual"
        }
    }
}

tasks.register("publishWeftReleaseToMavenCentral") {
    group = "publishing"
    description = "Stages the Weft libraries and BOM together for a coordinated release."
    dependsOn(
        ":weft-map:publishToMavenCentral",
        ":weft-login:publishToMavenCentral",
        ":weft-push:publishToMavenCentral",
        ":weft-bom:publishToMavenCentral",
    )
}
