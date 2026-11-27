import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import javax.inject.Inject

abstract class BuildSwiftPackageDependenciesTask @Inject constructor(
    private val execOperations: ExecOperations,
) : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val packageDirectory: DirectoryProperty

    @get:Input
    abstract val sdkPath: Property<String>

    @get:OutputDirectory
    abstract val buildDirectory: DirectoryProperty

    @TaskAction
    fun buildSwiftPackage() {
        execOperations.exec {
            commandLine(
                "swift",
                "build",
                "--quiet",
                "--package-path",
                packageDirectory.get().asFile.absolutePath,
                "--triple",
                "arm64-apple-ios-simulator",
                "--sdk",
                sdkPath.get(),
                "--build-path",
                buildDirectory.get().asFile.absolutePath,
            )
        }
    }
}

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.executable {
            baseName = "WeftDemo"
            entryPoint = "run.thespot.weft.demo.ios.main"
        }
    }

    sourceSets {
        iosMain.dependencies {
            implementation(project(":composeApp"))
            implementation(libs.compose.ui)
        }
    }
}

val simulatorApp = layout.buildDirectory.dir("app/iosSimulatorArm64/WeftDemo.app")
val simulatorExecutable = layout.buildDirectory.file("bin/iosSimulatorArm64/debugExecutable/WeftDemo.kexe")
val swiftPackageDirectory = rootProject.layout.dir(
    providers.provider {
        val packagesRoot = rootProject.layout.buildDirectory.dir("kotlin/swiftSyntheticPackages").get().asFile
        packagesRoot.listFiles()
            ?.asSequence()
            ?.filter { it.isDirectory && it.resolve("Package.swift").isFile }
            ?.firstOrNull { packageRoot ->
                packageRoot.walkTopDown()
                    .filter { it.name == "Package.swift" }
                    .any { it.readText().contains("KakaoMapsSDK-SPM") }
            }
            ?: error("Could not find the generated SwiftPM package containing KakaoMapsSDK-SPM.")
    },
)
val swiftPackageBuildDirectory = layout.buildDirectory.dir("swiftpm/iosSimulatorArm64")
val swiftPackageProducts = swiftPackageBuildDirectory.map { it.dir("out/Products/Debug-iphonesimulator") }
val simulatorSdkPath = providers.exec {
    commandLine("xcrun", "--sdk", "iphonesimulator", "--show-sdk-path")
}.standardOutput.asText.map(String::trim)

val deleteIosSimulatorApp = tasks.register<Delete>("deleteIosSimulatorApp") {
    delete(simulatorApp)
}

val buildSwiftPackageDependencies = tasks.register<BuildSwiftPackageDependenciesTask>("buildSwiftPackageDependencies") {
    group = "distribution"
    description = "Builds SwiftPM products imported by the Kotlin/Native iOS app."
    dependsOn("linkDebugExecutableIosSimulatorArm64")
    packageDirectory.set(swiftPackageDirectory)
    sdkPath.set(simulatorSdkPath)
    buildDirectory.set(swiftPackageBuildDirectory)
}

val syncIosSimulatorApp = tasks.register<Sync>("syncIosSimulatorApp") {
    group = "distribution"
    description = "Assembles the Kotlin/Native iOS simulator app bundle."
    dependsOn(deleteIosSimulatorApp)
    dependsOn("linkDebugExecutableIosSimulatorArm64")
    dependsOn(buildSwiftPackageDependencies)

    from(simulatorExecutable) {
        rename { "WeftDemo" }
        filePermissions {
            unix("rwxr-xr-x")
        }
    }
    from("src/Info.plist")
    from(swiftPackageProducts) {
        include("*.bundle/**", "Assets.car")
    }
    from(swiftPackageProducts) {
        include("*.framework/**")
        exclude("**/_CodeSignature/**")
        into("Frameworks")
    }
    into(simulatorApp)
}

val signIosSimulatorApp = tasks.register<Exec>("signIosSimulatorApp") {
    group = "distribution"
    description = "Ad hoc signs the simulator app bundle for installation."
    dependsOn(syncIosSimulatorApp)
    commandLine(
        "codesign",
        "--force",
        "--deep",
        "--sign",
        "-",
        "--timestamp=none",
        simulatorApp.get().asFile.absolutePath,
    )
}

tasks.register("packageIosSimulatorApp") {
    group = "distribution"
    description = "Packages the Kotlin/Native iOS simulator app without an Xcode project."
    dependsOn(signIosSimulatorApp)
}
