plugins {
    id("com.vanniktech.maven.publish")
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()

    pom {
        name.set("Weft ${project.name.replaceFirstChar { it.uppercase() }}")
        description.set("Kotlin Multiplatform library scaffolding for the Weft ${project.name} capability.")
        inceptionYear.set("2026")
        url.set("https://github.com/run-spot/Weft")

        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                distribution.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }

        developers {
            developer {
                id.set("weft-maintainers")
                name.set("Weft Maintainers")
                email.set(providers.gradleProperty("POM_DEVELOPER_EMAIL").get())
                url.set("https://github.com/run-spot/Weft")
                organization.set("Weft")
                organizationUrl.set("https://github.com/run-spot/Weft")
            }
        }

        scm {
            url.set("https://github.com/run-spot/Weft")
            connection.set("scm:git:https://github.com/run-spot/Weft.git")
            developerConnection.set("scm:git:ssh://git@github.com/run-spot/Weft.git")
        }
    }
}
