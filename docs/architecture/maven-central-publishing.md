# Maven Central Publishing

This repository prepares four Kotlin Multiplatform Maven Central publications and one Java Platform BOM. The Gradle build is configured for publication, but no artifact has been published.

## Build configuration

- `build-logic` applies one KMP convention to each library and shared Maven publication metadata to all artifacts.
- `GROUP` is `run.thespot.weft`; `VERSION_NAME` defaults to `0.1.0-SNAPSHOT`.
- The KMP modules publish `weft-core`, `weft-map`, `weft-login`, and `weft-push`, with Android and iOS target publications.
- `:weft-bom` publishes `weft-bom`, a Maven BOM that constrains the four KMP artifacts to the same release version.
- POM metadata includes the Apache-2.0 license, Weft maintainer identity, and GitHub SCM coordinates.
- `publishToMavenCentral()` stages the deployment for validation. Automatic release is not enabled.

The build uses the Vanniktech Maven Publish Gradle plugin, which is a community plugin and is also used by Kotlin's current Multiplatform Maven Central tutorial. Sonatype does not provide an official Gradle publishing plugin.

The `run.thespot` namespace must be verified in Central Portal before creating a release. Do not work around an unavailable namespace by publishing under a personal account.

The checked-in developer email is a reserved `.invalid` address for local POM validation. Set the repository Actions variable `WEFT_DEVELOPER_EMAIL` to a public maintainer contact before publishing. The release workflow fails when this value is missing and writes it into the publication POM.

## Local checks

~~~bash
./gradlew verify
./gradlew :weft-core:checkPomFileForKotlinMultiplatformPublication
./gradlew :weft-map:checkPomFileForKotlinMultiplatformPublication
./gradlew :weft-map:publishToMavenLocal
./gradlew :weft-bom:generatePomFileForMavenPublication
./gradlew :weft-bom:publishToMavenLocal
~~~

Repeat the module-specific KMP POM and Maven Local checks for `:weft-map`, `:weft-login`, and `:weft-push`. `publishToMavenLocal` is a local validation path and does not upload anything to Maven Central.

Gradle consumers import the BOM and omit the versions on selected artifacts:

~~~kotlin
dependencies {
    implementation(platform("run.thespot.weft:weft-bom:1.0.0"))
    implementation("run.thespot.weft:weft-core")
    implementation("run.thespot.weft:weft-map")
    implementation("run.thespot.weft:weft-login")
}
~~~

## Release workflow

The libraries and BOM share a version and are staged together. Publish a GitHub release with a tag such as `weft-v1.0.0`. The workflow removes the `weft-v` prefix, overrides `VERSION_NAME`, and stages `:weft-core`, `:weft-map`, `:weft-login`, `:weft-push`, and `:weft-bom` publications for Central Portal review. A maintainer reviews the validated deployments and publishes them from Central Portal. The workflow does not enable automatic publication.

The release is blocked until all selected libraries have completed their module milestones, the namespace is verified, all coordinates and POMs are reviewed, and a consumer build passes. Maven Central components are immutable after publication.

## Required GitHub Actions configuration

Configure these repository or environment values before using the release workflow:

- `MAVEN_CENTRAL_USERNAME`: Central Portal user token username.
- `MAVEN_CENTRAL_PASSWORD`: Central Portal user token password.
- `SIGNING_IN_MEMORY_KEY`: ASCII-armored GPG private key used to sign all publications.
- `SIGNING_IN_MEMORY_KEY_PASSWORD`: passphrase for the private key.
- `WEFT_DEVELOPER_EMAIL`: repository Actions variable containing the public maintainer contact included in the publication POM.

The workflow maps them to Gradle project properties using `ORG_GRADLE_PROJECT_...`. Upload the public signing key to a supported key server as required by Central Portal. Never store the private key or Central credentials in the repository.

## References

- [Kotlin Multiplatform Maven Central tutorial](https://kotlinlang.org/docs/multiplatform/multiplatform-publish-libraries-to-maven.html)
- [Central Portal namespace registration](https://central.sonatype.org/register/namespace/)
- [Central Portal Gradle publishing options](https://central.sonatype.org/publish/publish-portal-gradle/)
- [Gradle Java Platform plugin](https://docs.gradle.org/current/userguide/java_platform_plugin.html)
- [Vanniktech Gradle Maven Publish Plugin](https://github.com/vanniktech/gradle-maven-publish-plugin)
