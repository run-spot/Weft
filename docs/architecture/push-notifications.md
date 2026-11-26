# Push notifications

This page describes the first `:weft-push` provider: Firebase Cloud Messaging (FCM). The module normalizes FCM payloads into a common message, runs a shared processing policy, and presents the resulting notification through Android or Apple APIs. Platform permissions, Firebase project setup, the Xcode extension target, and notification tap navigation stay at the app boundary.

## Common contract

Install the provider through the Weft runtime:

~~~kotlin
val weft = Weft {
    install(FirebasePush)
}

val push = weft.get<PushPlugin>()
~~~

For a multi-module app using Koin, `weft-push` publishes a discovered contribution so the app can
aggregate it with the other capabilities. It uses `DefaultPushMessageProcessor` unless the app
provides a `PushMessageProcessor` definition, which keeps app-specific message policy outside the
library. See the [shared contribution assembly](library-modules.md#plugin-installation-model).

`PushPlugin.registrationId` is a `StateFlow<PushRegistrationId?>`. Collect it and upload changed identifiers to the app's backend. Firebase's recommended identifier is a Firebase Installation ID (FID); do not log or treat it as a user credential. `register()` requests provider registration and the ID arrives through the state flow. `unregister()` disables the current FCM registration, clears the local value, and does not delete the FID or an app-server record.

`PushPlugin.notifications` is a hot `Flow<PushNotification>` without replay. A collector sees messages delivered while it is collecting. Notifications are not persisted across process death, and tapping a system notification is not replayed as a received event. The model contains the provider message ID, type, alert title/body, route, timestamp, and string-valued custom data when supplied.

`PushRemoteMessage` is the normalized input to `PushMessageProcessor`. `DefaultPushMessageProcessor` displays a message when it has a non-blank title or body and ignores payloads without visible text. Configure an app-specific common policy through `FirebasePush { messageProcessor = ... }`. A processor can route message types, choose a channel/group, change displayed text, or ignore the message. The result is a `PushNotificationRequest`; `PushPlugin.showNotification`, `cancelNotification`, and `cancelAllNotifications` use the platform notification service.

The reserved FCM data keys are `weft.messageId`, `weft.type`, `weft.title`, `weft.body`, `weft.route`, and `weft.sentAt`. Pass custom string fields as `weft.data.<key>`; adapters remove that prefix before passing the custom map to common code. Other non-reserved string data fields are also preserved. The sending backend owns constructing this payload; this client library does not send FCM messages.

`permissionStatus()` reads OS authorization without prompting. The common statuses preserve iOS provisional and App Clip ephemeral authorization. The app owns when and how it asks permission: Android uses its Activity Result launcher for `POST_NOTIFICATIONS` on Android 13+, while iOS may call `FirebasePush.requestNotificationPermission()` from app-owned consent UI. A denied iOS permission must be changed in Settings.

Cancelling a Flow collector stops delivery to that collector. Cancelling `register()` or `unregister()` stops waiting for the native callback, but does not cancel work already accepted by Firebase. FCM may auto-register again on a later launch unless the app disables auto-initialization.

## Android

The library adds `WeftFirebaseMessagingService`, the FID registration metadata, and the `POST_NOTIFICATIONS` permission declaration to the Android merged manifest. It forwards registration changes to the common API, normalizes messages, runs the common processor, and displays `Display` results through `NotificationManager`. The notification service creates the request's channel, uses the app icon, and attaches route/type/custom data to the launch intent. It skips visual presentation if the user has disabled notifications or denied Android 13+ permission. `firebase-messaging` is an Android-only runtime dependency. Android apps should have one `com.google.firebase.MESSAGING_EVENT` service. If the app needs its own subclass, remove the library's service declaration and register only the subclass:

~~~xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">
    <application>
        <service
            android:name="run.thespot.weft.push.WeftFirebaseMessagingService"
            tools:node="remove" />
        <service
            android:name="com.example.app.AppFirebaseMessagingService"
            android:exported="false">
            <intent-filter>
                <action android:name="com.google.firebase.MESSAGING_EVENT" />
            </intent-filter>
        </service>
    </application>
</manifest>
~~~

`AppFirebaseMessagingService` extends `WeftFirebaseMessagingService` and calls `super` from any overridden callback.

The app still owns Firebase setup: add its `google-services.json`, apply Google's Services Gradle plugin, and let Firebase initialize before using the provider. If customizing the service, use the manifest removal and app subclass shown above, then call `super` from overridden callbacks.

Android 13 and newer require a runtime `POST_NOTIFICATIONS` grant for visible notifications. Use the platform's Activity Result API from the app UI; `permissionStatus()` only reports the current state. Before API 33, notifications can still be disabled by the user or by individual notification channels.

When an FCM `notification` payload arrives in the background, Firebase displays it directly and bypasses `onMessageReceived`, so the common processor cannot change or suppress it. Data messages and foreground messages reach the service and use the common processor and local notification service. To apply the same policy before display in the background, send Android data-only messages and keep the callback work short. A system-tray tap opens the configured activity; the app reads the `weft.push.*` intent extras to navigate.

## iOS

Apple delivery uses APNs underneath FCM. Bundle the Firebase `GoogleService-Info.plist`, call `FirebasePush.configure()` on the main thread before constructing the Weft runtime, enable the Push Notifications capability, configure an APNs authentication key in Firebase, and call `registerForRemoteNotifications()` from the app lifecycle. Set `FirebaseMessagingInstallationIdEnabled` to `YES` in the app's `Info.plist` so `register()` and the messaging delegate use FIDs. `FirebasePush` owns `FIRMessaging.delegate` for registration updates; collect `registrationId` instead of assigning a competing messaging delegate. The app keeps its `UNUserNotificationCenterDelegate`; pass its payload to the common processor:

~~~kotlin
val decision = FirebasePush.onNotificationReceived(notification.request.content.userInfo)
if (decision is PushMessageDecision.Display) {
    pushPlugin.showNotification(decision.notification)
}
~~~

The helper emits the normalized message and returns the common display/ignore decision. For a transformed foreground notification, enqueue the returned request with `showNotification` and suppress the original presentation to avoid showing both. The app handles tap navigation from the normalized route/data fields. `FirebasePushNotificationProcessor` exposes the same processor to an iOS extension.

If the app disables Firebase delegate swizzling, forward the APNs device token to `FirebasePush.onApnsTokenRegistered(deviceToken)` from its app-delegate callback.

A `UNNotificationServiceExtension` is an app-owned Xcode target, not a target a KMP library can add to a consuming app. The sample at [`weft-push/src/iosExtension/NotificationServiceExtension.swift`](../../weft-push/src/iosExtension/NotificationServiceExtension.swift) calls the common Kotlin processor and applies its result to APNs content. Add the file to an extension target and link the generated `WeftPush` framework to that target. The extension runs only for alert payloads with `mutable-content: 1`; the sending backend must set that APNs flag for background content processing. FCM also requires this extension for image notifications. A normal FCM alert without content modification or images does not need an extension.

The extension process is separate from the app process. It starts with the default processor; if an app supplies custom policy, configure the same common `PushMessageProcessor` implementation on its `FirebasePushNotificationProcessor` instance in the extension. If the extension ignores a message, it clears the mutable alert content. Silent notifications do not invoke this extension.

The first iOS adapter uses Firebase Apple SDK 13.2.0 and therefore targets iOS 15 or later. It imports Firebase Messaging through Kotlin's SwiftPM integration. Kotlin documents this SwiftPM import feature as Alpha; linking `WeftPush` into a Notification Service Extension, an Xcode consumer build, and physical-device push checks remain release gates.

## Firebase setup and reviewed versions

The module pins Firebase Android Messaging 26.0.2 (the version mapped by Firebase Android BoM 35.2.0) and Firebase Apple SDK 13.2.0. Versions and platform requirements were reviewed against official Firebase and Kotlin documentation on 2026-11-26. Firebase recommends the FID registration APIs for new targeting flows; the old FCM registration-token APIs are deprecated.

References:

- [Firebase Android setup and BoM](https://firebase.google.com/docs/android/setup)
- [FCM Android registration and receiving](https://firebase.google.com/docs/cloud-messaging/android/get-started), [Android receive callbacks](https://firebase.google.com/docs/cloud-messaging/android/receive-messages)
- [FCM Apple setup and FID registration](https://firebase.google.com/docs/cloud-messaging/ios/get-started), [Apple receive callbacks](https://firebase.google.com/docs/cloud-messaging/ios/receive-messages)
- [Firebase Apple platform requirements](https://firebase.google.com/docs/ios/setup)
- [Kotlin SwiftPM import](https://kotlinlang.org/docs/multiplatform/multiplatform-spm-import.html)
- [Apple notification service extension requirements](https://developer.apple.com/documentation/usernotifications/unnotificationserviceextension)
