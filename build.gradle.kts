allprojects {
    group = providers.gradleProperty("GROUP").get()
    version = providers.gradleProperty("VERSION_NAME").get()
}

val weftLibraryProjects = listOf(
    ":weft-core",
    ":weft-map:kakaomap",
    ":weft-map:navermap",
    ":weft-sensor:gps",
    ":weft-login",
    ":weft-push",
)
val weftLiveReleaseEnabled = providers.gradleProperty("weftLiveReleaseEnabled")
    .map(String::toBoolean)
    .orElse(false)
val weftVerificationProjects = weftLibraryProjects + ":weft-live"
val weftReleasePublishTasks = buildList {
    addAll(
        listOf(
            ":weft-core:publishToMavenCentral",
            ":weft-map:kakaomap:publishToMavenCentral",
            ":weft-map:navermap:publishToMavenCentral",
            ":weft-sensor:gps:publishToMavenCentral",
            ":weft-login:publishToMavenCentral",
            ":weft-push:publishToMavenCentral",
            ":weft-bom:publishToMavenCentral",
        ),
    )
    if (weftLiveReleaseEnabled.get()) add(":weft-live:publishToMavenCentral")
}

tasks.register("verify") {
    group = "verification"
    description = "Checks every published Weft library leaf, plugin, BOM, and publication metadata."

    dependsOn(weftVerificationProjects.map { "$it:check" })
    dependsOn(":weft-bom:check", ":weft-gradle-plugin:check")

    val kmpPublicationChecks = listOf(
        "checkPomFileForKotlinMultiplatformPublication",
        "checkPomFileForAndroidPublication",
        "checkPomFileForIosArm64Publication",
        "checkPomFileForIosSimulatorArm64Publication",
    )
    weftVerificationProjects.forEach { libraryPath ->
        // Native cinterop targets can be disabled on hosts without the required toolchain
        // (for example, Kakao's iOS targets on Linux). Depend on the publication checks
        // registered by each leaf instead of resolving absent platform task paths.
        dependsOn(project(libraryPath).tasks.matching { it.name in kmpPublicationChecks })
    }
    dependsOn("verifyWeftBomPublication")
    dependsOn(
        "verifyWeftMapLayout",
        "verifyWeftSensorLayout",
        "verifyKakaoMapPublication",
        "verifyNaverMapPublication",
    )
}

tasks.register("verifyWeftMapLayout") {
    group = "verification"
    description = "Checks that the map namespace contains only published provider leaves."
    doLast {
        val mapNamespace = project(":weft-map")
        check(mapNamespace.childProjects.keys == setOf("kakaomap", "navermap")) {
            "Expected :weft-map:kakaomap and :weft-map:navermap as map leaves, found ${mapNamespace.childProjects.keys}"
        }
        check(mapNamespace.tasks.findByName("check") == null) {
            "The :weft-map namespace project must not own a check task."
        }
        check(mapNamespace.tasks.names.none { it.startsWith("publish") }) {
            "The :weft-map namespace project must not publish an artifact."
        }
    }
}

tasks.register("verifyWeftSensorLayout") {
    group = "verification"
    description = "Checks that the sensor namespace contains only published sensor leaves."
    doLast {
        val sensorNamespace = project(":weft-sensor")
        check(sensorNamespace.childProjects.keys == setOf("gps")) {
            "Expected :weft-sensor:gps as the first sensor leaf, found ${sensorNamespace.childProjects.keys}"
        }
        check(sensorNamespace.tasks.findByName("check") == null) {
            "The :weft-sensor namespace project must not own a check task."
        }
        check(sensorNamespace.tasks.names.none { it.startsWith("publish") }) {
            "The :weft-sensor namespace project must not publish an artifact."
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

tasks.register("verifyNaverMapPublication") {
    group = "verification"
    description = "Checks the Android publication POM includes the Naver Maps SDK dependency."
    dependsOn(":weft-map:navermap:generatePomFileForAndroidPublication")

    doLast {
        val pomFile = file("weft-map/navermap/build/publications/android/pom-default.xml")
        check(pomFile.isFile) { "Naver map Android POM was not generated: $pomFile" }

        val document = javax.xml.parsers.DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(pomFile)
        val dependencies = document.getElementsByTagNameNS("*", "dependency")
        val hasNaverAndroidSdk = (0 until dependencies.length).any { index ->
            val dependency = dependencies.item(index) as org.w3c.dom.Element
            val group = dependency.getElementsByTagNameNS("*", "groupId").item(0)?.textContent
            val artifact = dependency.getElementsByTagNameNS("*", "artifactId").item(0)?.textContent
            group == "com.naver.maps" && artifact == "map-sdk"
        }
        check(hasNaverAndroidSdk) {
            "Naver map Android POM must declare com.naver.maps:map-sdk."
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
        val expectedArtifacts = buildList {
            addAll(listOf("weft-core", "weft-kakaomap", "weft-navermap", "weft-gps", "weft-login", "weft-push"))
            if (weftLiveReleaseEnabled.get()) add("weft-live")
        }
        val expected = expectedArtifacts
            .map { artifact -> "$expectedGroup:$artifact:$expectedVersion" }
        check(actual.sorted() == expected.sorted()) {
            "Weft BOM constraints differ. Expected $expected but found $actual"
        }
    }
}

gradle.taskGraph.whenReady {
    val taskPaths = allTasks.map { it.path }.toSet()
    val livePublishRequested = taskPaths.any { it.startsWith(":weft-live:publish") }
    check(!livePublishRequested || weftLiveReleaseEnabled.get()) {
        "Direct :weft-live publication is disabled until weftLiveReleaseEnabled=true."
    }
    if (":verify" in taskPaths) {
        check(":weft-map:check" !in taskPaths) {
            "Root verification must not request the :weft-map namespace check task."
        }
        check(":weft-map:kakaomap:check" in taskPaths) {
            "Root verification must check the :weft-map:kakaomap leaf."
        }
        check(":weft-map:navermap:check" in taskPaths) {
            "Root verification must check the :weft-map:navermap leaf."
        }
        check(":weft-sensor:gps:check" in taskPaths) {
            "Root verification must check the :weft-sensor:gps leaf."
        }
    }
    if (":publishWeftReleaseToMavenCentral" in taskPaths) {
        check(":weft-map:publishToMavenCentral" !in taskPaths) {
            "Release staging must not publish the :weft-map namespace project."
        }
        check(":weft-map:kakaomap:publishToMavenCentral" in taskPaths) {
            "Release staging must publish the :weft-map:kakaomap leaf."
        }
        check(":weft-map:navermap:publishToMavenCentral" in taskPaths) {
            "Release staging must publish the :weft-map:navermap leaf."
        }
        check(":weft-sensor:gps:publishToMavenCentral" in taskPaths) {
            "Release staging must publish the :weft-sensor:gps leaf."
        }
        check((":weft-live:publishToMavenCentral" in taskPaths) == weftLiveReleaseEnabled.get()) {
            "Coordinated live publication must match weftLiveReleaseEnabled."
        }
    }
}

tasks.register("publishWeftReleaseToMavenCentral") {
    group = "publishing"
    description = "Stages the Weft libraries and BOM together for a coordinated release."
    dependsOn(weftReleasePublishTasks)
}
