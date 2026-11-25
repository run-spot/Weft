plugins {
    id("org.jetbrains.kotlin.jvm")
    id("java-gradle-plugin")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation(gradleTestKit())
}
