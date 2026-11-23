# Maven Central Publishing

This repository prepares independent Maven Central publications for `:map`, `:push`, and `:login`. The Gradle build is configured for publication, but no artifact has been published.

## Build configuration

- `build-logic` applies one KMP convention and one Maven publication convention to each library.
- `GROUP` is initially `io.github.run-spot.weft`; `VERSION_NAME` defaults to `0.1.0-SNAPSHOT`.
- Each module publishes an artifact ID matching its Gradle project name (`map`, `push`, or `login`) and the KMP Android and iOS target publications.
- POM metadata includes the Apache-2.0 license, RunTheSpot developer identity, and GitHub SCM coordinates.
- `publishToMavenCentral()` stages the deployment for validation. Automatic release is not enabled.

The build uses the Vanniktech Maven Publish Gradle plugin, which is a community plugin and is also used by Kotlin's current Multiplatform Maven Central tutorial. Sonatype does not provide an official Gradle publishing plugin.

The `io.github.run-spot` namespace is a proposed coordinate based on the GitHub organization. A maintainer must verify that namespace and confirm that RunTheSpot can publish under it in Maven Central before creating a release. Do not work around an unavailable namespace by publishing under a personal account.

## Local checks

~~~bash
./gradlew verify
./gradlew :map:checkPomFileForKotlinMultiplatformPublication
./gradlew :map:publishToMavenLocal
~~~

Repeat the module-specific POM and Maven Local checks for `:push` and `:login`. `publishToMavenLocal` is a local validation path and does not upload anything to Maven Central.

## Release workflow

Each library is released independently. Publish a GitHub release with a module-prefixed tag such as `map-v1.0.0`, `push-v1.0.0`, or `login-v1.0.0`. The workflow selects only that Gradle module, removes the module prefix, overrides `VERSION_NAME`, and uploads its publications to a Central Portal deployment. A maintainer reviews the validated deployment and publishes it from Central Portal. The workflow does not enable automatic publication.

The release is blocked until the selected library has completed its module milestones, the namespace is verified, all coordinates and POMs are reviewed, and a consumer build passes. Maven Central components are immutable after publication.

## Required GitHub Actions secrets

Configure these repository or environment secrets before using the release workflow:

- `MAVEN_CENTRAL_USERNAME`: Central Portal user token username.
- `MAVEN_CENTRAL_PASSWORD`: Central Portal user token password.
- `SIGNING_IN_MEMORY_KEY`: ASCII-armored GPG private key used to sign all publications.
- `SIGNING_IN_MEMORY_KEY_PASSWORD`: passphrase for the private key.

The workflow maps them to Gradle project properties using `ORG_GRADLE_PROJECT_...`. Upload the public signing key to a supported key server as required by Central Portal. Never store the private key or Central credentials in the repository.

## References

- [Kotlin Multiplatform Maven Central tutorial](https://kotlinlang.org/docs/multiplatform/multiplatform-publish-libraries-to-maven.html)
- [Central Portal namespace registration](https://central.sonatype.org/register/namespace/)
- [Central Portal Gradle publishing options](https://central.sonatype.org/publish/publish-portal-gradle/)
- [Vanniktech Gradle Maven Publish Plugin](https://github.com/vanniktech/gradle-maven-publish-plugin)
