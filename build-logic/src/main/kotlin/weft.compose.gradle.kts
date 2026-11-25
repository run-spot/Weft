plugins {
    // Applies the Compose compiler to the module that uses Compose API. Kept in build-logic so the
    // Kotlin version and the compiler plugin version cannot drift apart between modules.
    id("org.jetbrains.kotlin.plugin.compose")
}
