# Weft Bridge Library Modules

The bridge-library workspace is a multi-project Gradle build inside the Weft repository. Each published capability is a separate Kotlin Multiplatform leaf with its own API and dependencies. `:weft-core` owns the plugin installation contract and provider-neutral map contracts. The BOM aligns versions, and the default release set is coordinated; candidates are verified before they are admitted to that set.

## Initial modules

| Gradle path | Artifact ID | Responsibility | Current milestone |
| --- | --- | --- | --- |
| `:weft-core` | `weft-core` | Plugin container, map models, Compose `WeftMap` and content tree | M1: plugin and map contracts |
| `:weft-map` | none | Namespace parent for provider leaves | No tasks or publication |
| `:weft-map:kakaomap` | `weft-kakaomap` | Kakao Maps provider binding and Android/iOS adapters | M1: implemented; local fixture passes, CI/remote consumer pending |
| `:weft-map:navermap` | `weft-navermap` | Naver Maps provider binding and Android/iOS adapters (SDK 3.24.1) | M1: implementation and local Android/iOS consumer gates pass; remote Maven consumer pending |
| `:weft-sensor` | none | Namespace parent for sensor leaves | No tasks or publication |
| `:weft-sensor:gps` | `weft-gps` | Provider-neutral GPS stream and native Android/iOS location adapters | M1/M2: implementation added; compile, permissions, and device validation pending |
| `:weft-login` | `weft-login` | Supabase OAuth PKCE, Android/iOS session storage, and Android Restore Credentials | M1/M2: implementation compiled; provider and device validation pending |
| `:weft-push` | `weft-push` | FID-based registration, common message processing, and OS notification presentation backed by Firebase Cloud Messaging | M2: Android presenter and iOS app/extension processor bridge |
| `:weft-live` | `weft-live` | Finite live-activity lifecycle with Android notifications and an ActivityKit/WidgetKit façade | M1: candidate implemented; external Apple consumer and remote SwiftPM source pending |
| `:weft-bom` | `weft-bom` | Maven BOM aligning the default six KMP artifacts; optionally includes `weft-live` | M0: build and publication skeleton |
| `:weft-gradle-plugin` | not published | Gradle plugin with the offline wiring check and the aggregated health check | M1: plugin and health verification |

Published artifacts use group `run.thespot.weft` and shared development version `0.1.0-SNAPSHOT`. The `run.thespot` namespace must be verified in Central Portal before the first release. The GPS stream's permission flow and Android/iOS device behavior need validation before release. Login implements the Supabase path; its project and device validation remain before release, and a generic OIDC adapter for Spring-backed profiles remains future work. Push has its public API and first Firebase adapters, but is not a release candidate until CI and a separate Android/iOS consumer validate both platform paths. The Kakao provider also remains short of release readiness until CI and a separately published consumer validate its Android/iOS path. The Naver provider's Android publication and iOS dynamic-framework consumer pass locally; runtime iOS tests require Apple Silicon, and CI plus a separately published Maven consumer remain release gates. `:weft-live` is always included in root verification, but its BOM constraint and coordinated publication remain behind `weftLiveReleaseEnabled=false` until the versioned remote SwiftPM and external consumer gates pass.

## Login capability roadmap

The first `:weft-login` implementation targets Supabase Auth: OAuth 2.0 Authorization Code with PKCE, Keychain/Keystore session storage, and Android Restore Credentials through Supabase's experimental passkey endpoints. A PKCE-capable generic OIDC adapter remains future work for Spring-backed profiles. Keep provider transport inside the Weft artifact and app modules on the approved Weft/Ktor/Ktorfit allowlist. PKCE is a Weft sign-in policy and does not inherently require an application backend. See the [Supabase login guide](login.md) for setup and current release limitations.

## Plugin installation model

`:weft-core` owns the plugin registry and a container-neutral `WeftContribution` contract. Each
capability module can expose one contribution, while a multi-module app collects contributions and
builds one runtime. The app's Koin module is the single aggregation point:

~~~kotlin
@Module
@Configuration
class AppWeftModule {
    @Single
    fun weft(contributions: List<WeftContribution>): Weft = Weft(contributions)
}

@KoinApplication
class AppKoinApplication

startKoin<AppKoinApplication> {
    properties(
        mapOf(
            "weft.kakaoMap.nativeAppKey" to kakaoNativeAppKey,
            "weft.login.supabaseUrl" to supabaseUrl,
            "weft.login.publishableKey" to supabasePublishableKey,
            "weft.login.redirectUri" to loginRedirectUri,
        ),
    )
}
val weft = KoinPlatform.getKoin().get<Weft>()
~~~

Each implemented capability leaf publishes a Koin `@Configuration` module and a `@Single`
contribution. Koin's Compiler Plugin discovers configured modules from the app classpath, and the
app injects every `WeftContribution` into one factory. Kakao Maps reads its native app key from
`weft.kakaoMap.nativeAppKey`; Supabase login reads `weft.login.supabaseUrl`,
`weft.login.publishableKey`, and `weft.login.redirectUri`. These are public client settings; never
put a Supabase secret or service-role key in Koin properties. Firebase push uses
`DefaultPushMessageProcessor`, or receives an app-provided `PushMessageProcessor` definition.
`weft-live` contributes when the optional artifact is present, but the app must provide a
platform-specific `LiveActivityPlatform` definition. Each contribution performs the same
`WeftConfig.install` call that a direct consumer would write. A consumer that does not start Koin
can continue to build a runtime with `Weft { install(...) }`.

The `:weft-sensor:gps` contribution installs `Gps` under its own `GpsPlugin` type. GPS and maps can
therefore be installed in the same runtime; adding another sensor leaf gives that sensor a separate
plugin type and dependency.

The provider-side contribution stays with its implementation:

~~~kotlin
@Single
class KakaoMapWeftContribution(
    @Property("weft.kakaoMap.nativeAppKey") private val nativeAppKey: String,
) : WeftContribution {
    override fun install(config: WeftConfig) {
        config.install(KakaoMap) {
            this.nativeAppKey = this@KakaoMapWeftContribution.nativeAppKey
        }
    }
}

@Module
@ComponentScan
@Configuration
class KakaoMapKoinModule
~~~

The Koin Compiler Plugin generates module DSL code that uses `koin-core`. Each capability leaf
with annotated modules declares `koin-core` as an implementation dependency and exposes
`koin-annotations` to consumers; the Kakao leaf follows that contract. An app module that declares
`AppWeftModule` and `AppKoinApplication` applies the compiler plugin and declares `koin-core` plus
`koin-annotations` in `commonMain`:

~~~kotlin
plugins {
    id("io.insert-koin.compiler.plugin") version "1.2.1"
}

kotlin {
    sourceSets.commonMain.dependencies {
        implementation("io.insert-koin:koin-core:4.2.2")
        implementation("io.insert-koin:koin-annotations:4.2.2")
    }
}
~~~

As reviewed on 2026-11-26, Koin's official setup lists Koin 4.2.2 and Compiler Plugin 1.2.1; the
older KSP processor is deprecated. The plugin supports K2 starting at Kotlin 2.3.20, but its
documented verification matrix currently stops at Kotlin 2.4.20. Weft's Kotlin 2.4.21 compile
succeeds with a warning that the plugin is using its 2.4.20 adapter; this tuple remains unverified
by Koin. Automatic discovery from a separately published KMP artifact is also a consumer-validation
gate. See [Koin Compiler Plugin setup](https://insert-koin.io/docs/setup/compiler-plugin/),
[Koin multi-module apps](https://insert-koin.io/docs/reference/koin-android/multi-module/), and
[Koin releases](https://insert-koin.io/docs/support/releases/).

Each plugin declares the `KClass` it installs under, and a capability family publishes one interface that its implementations use. `KakaoMap` and `NaverMap` install as `MapPlugin`; an app chooses one provider per `Weft` runtime. `WeftMap` resolves that contract from `LocalWeft` and invokes its `MapContentRenderer` function value to publish immutable camera, marker, polyline, polygon, and circle snapshots to the platform adapter. Keeping the Compose renderer in a function value avoids abstract composable-member dispatch in Kotlin/Native. `Gps` installs as `GpsPlugin`, a distinct slot that can coexist with a map and other sensor capabilities. A future map provider can implement the same interface without changing map UI code.

Because the plugin type identifies the slot, only one plugin per type may be installed; a second installation throws `DuplicateWeftPluginException` naming the type and both plugin names. Resolution through `get<T>()` (or `get(KClass)`) throws `MissingWeftPluginException` when no plugin is installed, while `getOrNull<T>()` returns `null` for optional capabilities. Plugin `install` hooks run once after every registration is prepared, so a plugin may resolve peer plugins from the completed `Weft` scope.

`:weft-core` holds no Koin, vendor, or platform types. Provider modules add their plugin types, configuration, Koin contribution, and adapters on top of it and keep Android and Apple SDK types in their platform source sets.

## Compose entry point

`:weft-core` also owns the one place a composition receives the runtime: `LocalWeft` (a `staticCompositionLocalOf` whose read fails with an explanatory message when nothing provides it) and `WeftApp(weft) { ... }`, which provides it. The map DSL and `WeftMap` require Compose Runtime, UI, and Foundation, exposed as API dependencies; this does not introduce a Material dependency.

Because the composition local is public API, the Compose runtime is an `api` dependency: an app that imports `LocalWeft` gets the same runtime it already uses for its screens.

## Health checks

Each capability library knows which endpoint accepts its credential, so the endpoint description lives with the plugin, not in a generic checker. `WeftHealthCheck` carries a name, a URL, headers, and a method; `WeftPlugin.healthCheck(plugin)` reports the probes for a prepared plugin; `Weft.healthChecks()` collects them in installation order and performs no I/O.

~~~kotlin
override fun healthCheck(plugin: SupabasePlugin): List<WeftHealthCheck> = listOf(
    WeftHealthCheck(
        name = "supabase-auth",
        url = "${plugin.config.url}/auth/v1/health",
        headers = mapOf("apikey" to plugin.config.anonKey),
    ),
)
~~~

`:weft-gradle-plugin` aggregates those probes with the endpoints a build configures and runs them in one `verifyWeftHealth` pass; its offline `verifyWeftPlugins` task runs inside `check` and also rejects credentials that must never reach a client build (a Supabase `service_role` JWT, a provider secret token, or a privileged property such as `serviceRoleKey`), reporting only `file:line` and the category so the value is never printed. Both are described in [`weft-gradle-plugin/AGENTS.md`](../../weft-gradle-plugin/AGENTS.md).

## Dependency allowlist verification

The direct-dependency policy in [Application dependency policy](dependency-policy.md) is enforced for Gradle builds by the same plugin. `verifyWeftDependencies` compares every external module a module declares against `allowedDependencyGroups` and `allowedDependencies`, with defaults for the Weft, Kotlin, Compose, Kotlinx, Ktor, Koin, AndroidX, and Google groups. A build that adopts the policy sets `weft { enforceDependencyWhitelist = true }`, which also runs the check with `check`; without that switch the task exists but is not part of the build. Project and file dependencies are skipped because they have no Maven coordinate, an approved library may bring any transitive dependency its API needs, and test source sets are outside the allowlist.

## Source ownership

Each module uses the standard KMP source-set boundaries:

~~~text
  <published-leaf>/
  src/commonMain/kotlin/      # public API and platform-neutral models
  src/commonTest/kotlin/      # shared API behavior checks
  src/androidMain/kotlin/     # Android SDK adapter
  src/androidHostTest/kotlin/ # Android host-side checks when needed
  src/iosMain/kotlin/         # Kotlin/Native adapter and Apple interop
~~~

Only create a source-set implementation when a module milestone needs it. Keep Android and Apple SDK dependencies in their platform source sets. The `:weft-map` and `:weft-sensor` parents are namespaces; `:weft-map:kakaomap`, `:weft-map:navermap`, and `:weft-sensor:gps` are published leaves. The BOM is metadata-only and constrains `weft-core`, `weft-kakaomap`, `weft-navermap`, `weft-gps`, `weft-login`, and `weft-push` to the shared release version; it does not add runtime dependencies. When the explicit release gate is enabled, it also constrains `weft-live`. `:weft-core` owns the provider-neutral map contract; sensor leaf artifacts own their sensor contracts and native adapters.

`:weft-push` owns the provider-neutral registration ID, message-processing contract, notification request, presenter API, and permission status. The sending backend constructs the FCM data payload; Android and iOS adapters present common processor results. Its FCM setup and OS lifecycle boundaries are described in [Push notifications](push-notifications.md).

## Platform targets

The common convention currently declares Android, iOS device (`iosArm64`), and Apple Silicon simulator (`iosSimulatorArm64`) targets. Android uses compile SDK 37, min SDK 24, and JVM bytecode target 17; Compose 1.12 requires API 37. Kakao and Naver Maps support iOS 13.0; the provider frameworks explicitly lower Kotlin 2.4's default iOS 15 minimum to 13.0. The NMapsMap 3.24.1 vendor binary advertises iOS 9.0, while the Naver Weft framework and consumer app retain iOS 13.0. `:weft-live` raises its Apple deployment floor to iOS 16.1 because ActivityKit Live Activities are unavailable before that release. These are leaf-specific targets, not a minimum OS promise for every Weft artifact. The Intel simulator (`iosX64`) target is intentionally absent: Compose Multiplatform stopped publishing `iosX64` artifacts (the last published version is `1.11.0-alpha01`), so a library that depends on the Compose runtime, `:weft-core` included, cannot resolve that target.

The official Kotlin compatibility guide lists Kotlin 2.4.21 with Gradle 7.6.3–9.7.0 and AGP 8.5.2–9.3.1. The repository pins the upper compatible versions for this baseline. Recheck the guide before changing the tuple, and record CI results separately from official compatibility ranges.

## Distribution boundary

The Kakao iOS adapter imports KakaoMapsSDK-SPM 2.12.19 through the Kotlin SwiftPM integration and packages the dependency lock/resource handling with the leaf. The Naver iOS adapter imports the official `SPM-NMapsMap` 3.24.1 package through the same integration; its dynamic NMapsMap/NMapsGeometry frameworks and `Assets.car` resource pass a local Xcode consumer build. The integration is Alpha; local framework linking and an Xcode simulator consumer have been validated, but runtime simulator tests require Apple Silicon and a remote Maven consumer remains a release gate. Maven Central publishes the KMP Gradle artifact, not a standalone XCFramework for native Swift consumers.

The live-activity candidate imports the checked-in `weft-live/iosInterop/WeftLiveKit` package. It exposes an Objective-C-compatible ActivityKit bridge and reusable WidgetKit/SwiftUI layouts to `iosMain`; the consuming app still owns the `NSSupportsLiveActivities` Info.plist entry, WidgetBundle registration, deep-link routing, and linking the same package into its app and widget extension. The local façade is not yet a versioned remote SwiftPM distribution, so the default BOM and coordinated release intentionally exclude `weft-live`.

## References

- [Kotlin Multiplatform compatibility guide](https://kotlinlang.org/docs/multiplatform/multiplatform-compatibility-guide.html)
- [Android KMP library plugin](https://developer.android.com/kotlin/multiplatform/plugin)
- [KMP publication setup](https://kotlinlang.org/docs/multiplatform/multiplatform-publish-lib-setup.html)
