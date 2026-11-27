# Maven Central Publishing

This repository prepares six default Kotlin Multiplatform Maven Central publications, one verified live-activity candidate, and one Java Platform BOM. The Gradle build is configured for publication, but no artifact has been published.

## Build configuration

- `build-logic` applies one KMP convention to each library and shared Maven publication metadata to all artifacts.
- `GROUP` is `run.thespot.weft`; `VERSION_NAME` defaults to `0.1.0-SNAPSHOT`.
- The default KMP leaves publish `weft-core`, `weft-kakaomap`, `weft-navermap`, `weft-gps`, `weft-login`, and `weft-push`, with Android and iOS target publications. `:weft-map` and `:weft-sensor` are namespace parents; `:weft-map:kakaomap`, `:weft-map:navermap`, and `:weft-sensor:gps` own their capability artifacts. `:weft-live` is always verified but remains a candidate.
- `:weft-bom` publishes `weft-bom`, a Maven BOM that constrains the default six KMP artifacts to the same release version. It includes `weft-live` only when `weftLiveReleaseEnabled=true` is explicitly enabled.
- The Naver leaf resolves `com.naver.maps:map-sdk:3.24.1` from `https://repository.map.naver.com/archive/maven`; its iOS SwiftPM dependency is `NMapsMap` 3.24.1 and its KMP framework is dynamic.
- POM metadata includes the Apache-2.0 license, Weft maintainer identity, and GitHub SCM coordinates.
- `publishToMavenCentral()` stages the deployment for validation. Automatic release is not enabled.

The build uses the Vanniktech Maven Publish Gradle plugin, which is a community plugin and is also used by Kotlin's current Multiplatform Maven Central tutorial. Sonatype does not provide an official Gradle publishing plugin.

The `run.thespot` namespace must be verified in Central Portal before creating a release. Do not work around an unavailable namespace by publishing under a personal account.

The checked-in developer email is a reserved `.invalid` address for local POM validation. Set the repository Actions variable `WEFT_DEVELOPER_EMAIL` to a public maintainer contact before publishing. The release workflow fails when this value is missing and writes it into the publication POM.

## Local checks

~~~bash
./gradlew verify
./gradlew :weft-core:checkPomFileForKotlinMultiplatformPublication
./gradlew :weft-map:kakaomap:checkPomFileForKotlinMultiplatformPublication
./gradlew :weft-map:kakaomap:checkPomFileForAndroidPublication
./gradlew :weft-map:navermap:check
./gradlew :weft-map:navermap:checkPomFileForKotlinMultiplatformPublication
./gradlew :weft-map:navermap:checkPomFileForAndroidPublication
./gradlew :weft-sensor:gps:check
./gradlew :weft-sensor:gps:checkPomFileForKotlinMultiplatformPublication
./gradlew verifyKakaoMapPublication
./gradlew verifyNaverMapPublication
./gradlew :weft-map:kakaomap:publishToMavenLocal
./gradlew :weft-map:navermap:publishToMavenLocal
./gradlew :weft-bom:generatePomFileForMavenPublication
./gradlew :weft-bom:publishToMavenLocal
~~~

Repeat the module-specific KMP POM and Maven Local checks for `:weft-login` and `:weft-push`. The GPS API uses platform location frameworks and has no vendor repository requirement. To run the separate Kakao consumer fixture, publish the required artifacts locally first:

~~~bash
./gradlew :weft-core:publishToMavenLocal :weft-map:kakaomap:publishToMavenLocal :weft-bom:publishToMavenLocal
./gradlew -p harness/fixtures/weft-kakaomap-consumer -PVERSION_NAME=0.1.0-SNAPSHOT compileDebugJavaWithJavac
./gradlew -p harness/fixtures/weft-kakaomap-consumer -PVERSION_NAME=0.1.0-SNAPSHOT dependencies --configuration debugRuntimeClasspath
~~~

The fixture's `settings.gradle.kts` configures `mavenLocal()`, Google's Maven repository, Maven Central, and Kakao's Maven repository (`https://devrepo.kakao.com/nexus/repository/kakaomap-releases/`). That vendor repository is required to resolve the Kakao Android SDK dependency from the `weft-kakaomap` publication. A separate Naver Android consumer must likewise configure `https://repository.map.naver.com/archive/maven`, resolve `weft-navermap` through the BOM, and prove that `com.naver.maps:map-sdk:3.24.1` is present. The Naver iOS consumer gate must link the generated dynamic `WeftNaverMap` framework together with the official dynamic `NMapsMap` XCFramework. `publishToMavenLocal` is local-only and does not upload anything to Maven Central.

Gradle consumers import the BOM and omit the versions on selected artifacts:

~~~kotlin
dependencies {
    implementation(platform("run.thespot.weft:weft-bom:1.0.0"))
    implementation("run.thespot.weft:weft-core")
    implementation("run.thespot.weft:weft-kakaomap")
    implementation("run.thespot.weft:weft-gps")
    implementation("run.thespot.weft:weft-login")
}
~~~

Applications choose one map provider per runtime. A Naver Android consumer selects the Naver leaf explicitly:

~~~kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://repository.map.naver.com/archive/maven")
    }
}

dependencies {
    implementation(platform("run.thespot.weft:weft-bom:1.0.0"))
    implementation("run.thespot.weft:weft-navermap")
}
~~~

## Release workflow

The libraries and BOM share a version and are staged together. Publish a GitHub release with a tag such as `weft-v1.0.0`. The workflow removes the `weft-v` prefix, overrides `VERSION_NAME`, and stages `:weft-core`, `:weft-map:kakaomap`, `:weft-map:navermap`, `:weft-sensor:gps`, `:weft-login`, `:weft-push`, and `:weft-bom` publications for Central Portal review. A maintainer reviews the validated deployments and publishes them from Central Portal. The workflow does not enable automatic publication.

The release is blocked until all selected libraries have completed their module milestones, the namespace is verified, all coordinates and POMs are reviewed, and a consumer build passes. Maven Central components are immutable after publication.

The root `verify` task always checks `:weft-live` and its four target POMs. With the default
`weftLiveReleaseEnabled=false`, the BOM and coordinated publish task omit it, and any direct live
publication task fails during task-graph validation. Set the property to `true` only after the
versioned remote SwiftPM source, external app/widget consumer, and Apple CI gates pass; the publish
workflow forwards the checked-in property explicitly.

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
