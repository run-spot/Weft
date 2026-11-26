# Supabase login with Weft

`:weft-login` provides a Supabase Auth implementation for native OAuth sign-in with Authorization Code and PKCE. It stores sessions and pending PKCE data in Android Keystore-backed encrypted storage or the iOS Keychain. The Supabase URL and publishable key are public client configuration; never place a Supabase secret or `service_role` key in an app.

The Android adapter also supports Restore Credentials, sometimes described as zero-tap sign-in. On a new device it asks Credential Manager for the app's transferred restore key, sends the assertion to Supabase Auth, and stores the new session. This is an Android-only capability; iOS uses the shared OAuth and session APIs.

## Configure the capability

Add `weft-login` and install its plugin through `Weft`:

~~~kotlin
val weft = Weft {
    install(WeftLogin) {
        supabaseUrl = "https://your-project.supabase.co"
        publishableKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY
        redirectUri = "com.example.app://auth/callback"
    }
}

val login = weft.get<LoginPlugin>()
~~~

Register the exact app callback URL in Supabase Auth's URL configuration. Configure the identity provider in the Supabase Dashboard and use the Supabase project's provider callback URL at the identity provider. Keep provider client secrets in Supabase's trusted configuration; the app only needs its public Supabase key.

## OAuth with PKCE

Initialize with the platform storage helper, open the returned URL in the system browser, and pass the matching app callback back to the plugin:

~~~kotlin
// Android
login.initialize(activity)
val request = login.beginOAuthSignIn(provider = "google")
request.openInSystemBrowser(activity)

// In the app's deep-link handler
val session = login.completeOAuthSignIn(callbackUri.toString())
~~~

On iOS, call `login.initialize()` and `request.openInSystemBrowser()`, then route the registered URL to `completeOAuthSignIn`. The app owns its URL scheme or universal-link configuration and the lifecycle hook that receives the callback.

The plugin creates a fresh PKCE verifier for each attempt and validates the callback URL before exchanging the one-use authorization code. Supabase Auth owns and validates the OAuth provider `state`; its native PKCE callback returns the authorization `code` to the app. `LoginPlugin.session` exposes the current in-memory session; call `refreshSession()` when the app needs to renew it.

## Android Restore Credentials

Restore Credentials require Android 9 (API 28), Credential Manager 1.5.0 or later, and compatible Google Play services. Devices without a supported provider should continue through the normal OAuth sign-in path. The adapter returns `null` when no restore credential exists; provider and Supabase errors remain `LoginException`s for the app to handle.

Call restore before showing the sign-in screen when no local session was loaded. After a successful sign-in, create the restore credential once. Use the Activity context for Credential Manager calls:

~~~kotlin
val session = login.initialize(activity)
    ?: login.restoreWithCredential(activity)

if (session != null && login.isRestoreCredentialSupported()) {
    login.createRestoreCredential(activity)
}
~~~

Clear both the Android restore key and the Supabase session when the user signs out:

~~~kotlin
login.signOut(activity)
~~~

`AndroidLoginStorage` uses Android Keystore with AES-GCM and stores its ciphertext under `noBackupFilesDir`. Restore Credentials transfer is handled by Google Play services independently of that app-private session file. `IosLoginStorage` uses the iOS Keychain.

### Supabase project setup

Supabase Passkey Auth is currently experimental. Enable it in the project's Authentication → Passkeys settings and choose a stable relying-party ID before users enroll credentials. Changing the ID makes existing credentials unusable. Add the Android signing certificate origin in the form `android:apk-key-hash:<base64url SHA-256 certificate hash>` to the allowed origins and configure the Android Digital Asset Links association for the relying-party domain.

Weft registers the Android restore key as a Supabase passkey named `Android restore key`. The current Supabase API does not provide a distinct restore-key type, so it appears in the same passkey inventory as user-created passkeys. The implementation uses Supabase's experimental passkey options, registration, authentication, and deletion endpoints; review provider changes before upgrading or releasing it.

Restore-key creation uses cloud backup when end-to-end encrypted backup is available and retries as device-to-device-only when Android reports that cloud E2EE is unavailable. The key must be cleared locally on sign-out because Credential Manager does not remove it automatically.

## Dependency versions and references

The module uses the repository's kotlinx.coroutines 1.10.2 baseline, Ktor 3.6.2, kotlinx.serialization 1.11.0, and AndroidX Credentials 1.5.0. The Android Restore Credentials guide lists 1.5.0 as the minimum supported credentials-library version. Version baseline reviewed 2026-11-26 against the [Ktor release page](https://ktor.io/docs/releases.html), [kotlinx.coroutines releases](https://github.com/Kotlin/kotlinx.coroutines/releases), [Kotlin serialization setup](https://kotlinlang.org/docs/serialization-get-started.html), and the [Android Restore Credentials guide](https://developer.android.com/identity/sign-in/restore-credentials-implementation).

- [Supabase PKCE flow](https://supabase.com/docs/guides/auth/sessions/pkce-flow)
- [Supabase passkey authentication](https://supabase.com/docs/guides/auth/passkeys)
- [Android Restore Credentials implementation](https://developer.android.com/identity/sign-in/restore-credentials-implementation)
- [Google Play quality requirements](https://support.google.com/googleplay/android-developer/answer/17492799?hl=en)

Reviewed 2026-11-26. Supabase marks passkey support experimental; verify the endpoints, Android origin rules, and Credential Manager compatibility before production rollout.
