allprojects {
    group = providers.gradleProperty("GROUP").get()
    version = providers.gradleProperty("VERSION_NAME").get()
}

val weftLibraryProjects = listOf(":weft-core", ":weft-map:kakaomap", ":weft-login", ":weft-push")

tasks.register("verify") {
    group = "verification"
    description = "Checks every published Weft library leaf, plugin, BOM, and publication metadata."

    dependsOn(weftLibraryProjects.map { "$it:check" })
    dependsOn(":weft-bom:check", ":weft-gradle-plugin:check")

    val kmpPublicationChecks = listOf(
        "checkPomFileForKotlinMultiplatformPublication",
        "checkPomFileForAndroidPublication",
        "checkPomFileForIosArm64Publication",
        "checkPomFileForIosSimulatorArm64Publication",
    )
    weftLibraryProjects.forEach { libraryPath ->
        // Native cinterop targets can be disabled on hosts without the required toolchain
        // (for example, Kakao's iOS targets on Linux). Depend on the publication checks
        // registered by each leaf instead of resolving absent platform task paths.
        dependsOn(project(libraryPath).tasks.matching { it.name in kmpPublicationChecks })
    }
    dependsOn("verifyWeftBomPublication")
    dependsOn("verifyWeftMapLayout", "verifyKakaoMapPublication")
}

tasks.register("verifyWeftMapLayout") {
    group = "verification"
    description = "Checks that the map namespace contains only published provider leaves."
    doLast {
        val mapNamespace = project(":weft-map")
        check(mapNamespace.childProjects.keys == setOf("kakaomap")) {
            "Expected :weft-map:kakaomap as the only map leaf, found ${mapNamespace.childProjects.keys}"
        }
        check(mapNamespace.tasks.findByName("check") == null) {
            "The :weft-map namespace project must not own a check task."
        }
        check(mapNamespace.tasks.names.none { it.startsWith("publish") }) {
            "The :weft-map namespace project must not publish an artifact."
        }
    }
}

tasks.register("verifyKakaoMapPublication") {
    group = "verification"
    description = "Checks the Android publication POM includes the Kakao Maps SDK dependency."
    dependsOn(":weft-map:kakaomap:generatePomFileForAndroidPublication")

    doLast {
        val pomFile = file("weft-map/kakaomap/build/publications/android/pom-default.xml")
        check(pomFile.isFile) { "Kakao map Android POM was not generated: $pomFile" }

        val document = javax.xml.parsers.DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(pomFile)
        val dependencies = document.getElementsByTagNameNS("*", "dependency")
        val hasKakaoAndroidSdk = (0 until dependencies.length).any { index ->
            val dependency = dependencies.item(index) as org.w3c.dom.Element
            val group = dependency.getElementsByTagNameNS("*", "groupId").item(0)?.textContent
            val artifact = dependency.getElementsByTagNameNS("*", "artifactId").item(0)?.textContent
            group == "com.kakao.maps.open" && artifact == "android"
        }
        check(hasKakaoAndroidSdk) {
            "Kakao map Android POM must declare com.kakao.maps.open:android."
        }
    }
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
        }
        val expected = listOf("weft-core", "weft-kakaomap", "weft-login", "weft-push")
            .map { artifact -> "$expectedGroup:$artifact:$expectedVersion" }
        check(actual.sorted() == expected.sorted()) {
            "Weft BOM constraints differ. Expected $expected but found $actual"
        }
    }
}

gradle.taskGraph.whenReady {
    val taskPaths = allTasks.map { it.path }.toSet()
    if (":verify" in taskPaths) {
        check(":weft-map:check" !in taskPaths) {
            "Root verification must not request the :weft-map namespace check task."
        }
        check(":weft-map:kakaomap:check" in taskPaths) {
            "Root verification must check the :weft-map:kakaomap leaf."
        }
    }
    if (":publishWeftReleaseToMavenCentral" in taskPaths) {
        check(":weft-map:publishToMavenCentral" !in taskPaths) {
            "Release staging must not publish the :weft-map namespace project."
        }
        check(":weft-map:kakaomap:publishToMavenCentral" in taskPaths) {
            "Release staging must publish the :weft-map:kakaomap leaf."
        }
    }
}

tasks.register("publishWeftReleaseToMavenCentral") {
    group = "publishing"
    description = "Stages the Weft libraries and BOM together for a coordinated release."
    dependsOn(
        ":weft-core:publishToMavenCentral",
        ":weft-map:kakaomap:publishToMavenCentral",
        ":weft-login:publishToMavenCentral",
        ":weft-push:publishToMavenCentral",
        ":weft-bom:publishToMavenCentral",
    )
}
