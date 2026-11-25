package run.thespot.weft.gradle

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.tasks.TaskProvider
import org.gradle.testfixtures.ProjectBuilder
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** `verifyWeftDependencies`: approved libraries pass, an unapproved one fails by coordinate. */
class WeftDependenciesVerifyTaskTest {
    private lateinit var projectDir: File

    @BeforeTest
    fun setUp() {
        projectDir = Files.createTempDirectory("weft-dependencies").toFile()
    }

    @AfterTest
    fun tearDown() {
        projectDir.deleteRecursively()
    }

    private fun project(): Project {
        val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
        project.plugins.apply("java")
        project.plugins.apply(WeftGradlePlugin::class.java)
        return project
    }

    private fun Project.dependenciesVerification() =
        tasks.getByName("verifyWeftDependencies") as WeftDependenciesVerifyTask

    private fun Project.extension() = extensions.getByType(WeftExtension::class.java)

    @Test
    fun approvingTheStandardGroupsPasses() {
        val project = project()
        listOf(
            "run.thespot.weft:weft-core:0.1.0-SNAPSHOT",
            "run.thespot.weft:weft-map:0.1.0-SNAPSHOT",
            "org.jetbrains.kotlin:kotlin-stdlib:2.4.21",
            "org.jetbrains.compose.runtime:runtime:1.9.0",
            "org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0",
            "io.ktor:ktor-client-core:3.0.0",
            "androidx.core:core-ktx:1.13.1",
            "androidx.compose.ui:ui:1.7.0",
            "com.google.firebase:firebase-messaging:24.0.0",
        ).forEach { coordinate -> project.dependencies.add("implementation", coordinate) }

        project.dependenciesVerification().verify()
    }

    @Test
    fun unapprovedDependencyFailsWithTheCoordinateAndTheFix() {
        val project = project()
        project.dependencies.add("implementation", "com.squareup.moshi:moshi:1.15.0")

        val error = assertFailsWith<GradleException> {
            project.dependenciesVerification().verify()
        }
        val message = error.message.orEmpty()
        assertTrue(message.contains("com.squareup.moshi:moshi"), message)
        assertTrue(message.contains("`implementation`"), message)
        assertTrue(message.contains("allowedDependencyGroups"), message)
        assertTrue(message.contains("allowedDependencies"), message)
    }

    @Test
    fun exactModuleApprovalPasses() {
        val project = project()
        project.extension().allowedDependencies.add("com.squareup.moshi:moshi")
        project.dependencies.add("implementation", "com.squareup.moshi:moshi:1.15.0")

        project.dependenciesVerification().verify()
    }

    @Test
    fun groupApprovalPasses() {
        val project = project()
        project.extension().allowedDependencyGroups.add("com.squareup")
        project.dependencies.add("implementation", "com.squareup.moshi:moshi:1.15.0")

        project.dependenciesVerification().verify()
    }

    @Test
    fun wildcardGroupApprovalPasses() {
        val project = project()
        project.configurations.create("commonMainImplementation")
        project.extension().allowedDependencyGroups.add("com.squareup.*")
        project.dependencies.add("commonMainImplementation", "com.squareup.moshi:moshi:1.15.0")

        project.dependenciesVerification().verify()
    }

    @Test
    fun testSourceSetsAndFileDependenciesAreNotChecked() {
        val project = project()
        project.dependencies.add("testImplementation", "junit:junit:4.13.2")
        project.dependencies.add("testImplementation", "org.mockito:mockito-core:5.11.0")
        project.dependencies.add("implementation", project.files("libs/local.jar"))

        project.dependenciesVerification().verify()
    }

    @Test
    fun reportsEveryConfigurationThatDeclaresTheSameModule() {
        val project = project()
        project.configurations.create("commonMainImplementation")
        project.dependencies.add("implementation", "com.squareup.moshi:moshi:1.15.0")
        project.dependencies.add("commonMainImplementation", "com.squareup.moshi:moshi:1.15.0")

        val error = assertFailsWith<GradleException> {
            project.dependenciesVerification().verify()
        }
        assertTrue(error.message.orEmpty().contains("2 declared dependencies"), error.message.orEmpty())
    }

    @Test
    fun checkIncludesTheWhitelistOnlyWhenEnforced() {
        val lenient = project()
        (lenient as ProjectInternal).evaluate()
        assertFalse(lenient.checkDependencies().contains("verifyWeftDependencies"), lenient.checkDependencies().toString())

        val strictDir = Files.createTempDirectory("weft-dependencies-strict").toFile()
        try {
            val strict = ProjectBuilder.builder().withProjectDir(strictDir).build()
            strict.plugins.apply("java")
            strict.plugins.apply(WeftGradlePlugin::class.java)
            strict.extension().enforceDependencyWhitelist.set(true)
            (strict as ProjectInternal).evaluate()

            assertTrue(
                strict.checkDependencies().contains("verifyWeftDependencies"),
                strict.checkDependencies().toString(),
            )
            assertEquals(true, strict.extension().enforceDependencyWhitelist.get())
        } finally {
            strictDir.deleteRecursively()
        }
    }

    private fun Project.checkDependencies(): List<String> =
        tasks.getByName("check").dependsOn.map { dependency ->
            if (dependency is TaskProvider<*>) dependency.name else dependency.toString()
        }
}
