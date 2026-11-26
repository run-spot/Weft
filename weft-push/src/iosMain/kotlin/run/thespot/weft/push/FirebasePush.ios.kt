package run.thespot.weft.push

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSData
import platform.Foundation.NSDictionary
import platform.Foundation.NSUserDefaults
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNAuthorizationStatusDenied
import platform.UserNotifications.UNAuthorizationStatusEphemeral
import platform.UserNotifications.UNAuthorizationStatusNotDetermined
import platform.UserNotifications.UNAuthorizationStatusProvisional
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNUserNotificationCenter
import platform.darwin.NSObject
import run.thespot.weft.core.Weft
import swiftPMImport.run.thespot.weft.weft.push.FIRApp
import swiftPMImport.run.thespot.weft.weft.push.FIRMessaging
import swiftPMImport.run.thespot.weft.weft.push.FIRMessagingDelegateProtocol
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private var registrationDelegate: FirebaseMessagingRegistrationDelegate? = null

internal actual fun createFirebasePushPlugin(processor: PushMessageProcessor): PushPlugin =
    IosFirebasePushRuntime(processor)

@OptIn(ExperimentalForeignApi::class)
internal actual fun installFirebasePush(plugin: PushPlugin, scope: Weft): Unit {
    val delegate = FirebaseMessagingRegistrationDelegate()
    registrationDelegate = delegate
    FIRMessaging.messaging().delegate = delegate
}

/**
 * Configures Firebase from the consuming app's bundled `GoogleService-Info.plist` when needed.
 *
 * Call this once on the main thread before constructing the Weft runtime. Apps that already
 * configured a default Firebase app can skip this call.
 */
@OptIn(ExperimentalForeignApi::class)
public fun FirebasePush.configure(): Unit {
    if (FIRApp.defaultApp() == null) {
        FIRApp.configure()
    }
}

/**
 * Requests iOS notification authorization.
 *
 * Call this from app-owned permission UI. iOS permission prompts are one-time system UI; a
 * later denied state must be changed in Settings. The app must separately register with APNs
 * from its application lifecycle.
 */
@OptIn(ExperimentalForeignApi::class)
public suspend fun FirebasePush.requestNotificationPermission(): PushPermissionStatus {
    val center = UNUserNotificationCenter.currentNotificationCenter()
    suspendCancellableCoroutine<Unit> { continuation ->
        val options = UNAuthorizationOptionAlert or
            UNAuthorizationOptionBadge or
            UNAuthorizationOptionSound
        center.requestAuthorizationWithOptions(options) { _, error ->
            if (!continuation.isActive) return@requestAuthorizationWithOptions
            if (error == null) {
                continuation.resume(Unit)
            } else {
                continuation.resumeWithException(PushException(error.localizedDescription))
            }
        }
    }

    return IosFirebasePushRuntime(DefaultPushMessageProcessor).permissionStatus()
}

/**
 * Forwards a payload received by the app's `UNUserNotificationCenterDelegate` into the shared
 * notification stream. The host app retains control of foreground presentation and tap behavior.
 */
@OptIn(ExperimentalForeignApi::class)
public fun FirebasePush.onNotificationReceived(userInfo: NSDictionary): PushMessageDecision {
    if (userInfo.objectForKey(PushPayloadKeys.LOCAL_NOTIFICATION) as? String == "true") {
        return PushMessageDecision.Ignore
    }
    val message = userInfo.toPushRemoteMessage()
    return PushEventHub.onMessageReceived(message)
}

/** Forwards an APNs device token to Firebase when the app disables delegate swizzling. */
@OptIn(ExperimentalForeignApi::class)
public fun FirebasePush.onApnsTokenRegistered(token: NSData): Unit {
    FIRMessaging.messaging().APNSToken = token
}

/** Forwards an already normalized payload into the shared notification stream. */
public fun FirebasePush.onNotificationReceived(notification: PushNotification) {
    PushEventHub.onNotificationReceived(notification)
}

private class IosFirebasePushRuntime(
    override val messageProcessor: PushMessageProcessor,
) : PushPlugin {
    override val registrationId = PushEventHub.registrationId

    override val notifications = PushEventHub.notifications

    @OptIn(ExperimentalForeignApi::class)
    override suspend fun register() {
        suspendCancellableCoroutine<Unit> { continuation ->
            FIRMessaging.messaging().registerWithCompletion { error ->
                if (!continuation.isActive) return@registerWithCompletion
                if (error == null) {
                    continuation.resume(Unit)
                } else {
                    continuation.resumeWithException(PushException(error.localizedDescription))
                }
            }
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    override suspend fun unregister() {
        suspendCancellableCoroutine<Unit> { continuation ->
            FIRMessaging.messaging().unregisterWithCompletion { error ->
                if (!continuation.isActive) return@unregisterWithCompletion
                if (error == null) {
                    PushEventHub.onUnregistered()
                    continuation.resume(Unit)
                } else {
                    continuation.resumeWithException(PushException(error.localizedDescription))
                }
            }
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    override suspend fun permissionStatus(): PushPermissionStatus =
        suspendCancellableCoroutine { continuation ->
            UNUserNotificationCenter.currentNotificationCenter()
                .getNotificationSettingsWithCompletionHandler { settings ->
                    val status = when (settings?.authorizationStatus) {
                        UNAuthorizationStatusAuthorized -> PushPermissionStatus.GRANTED
                        UNAuthorizationStatusProvisional -> PushPermissionStatus.PROVISIONAL
                        UNAuthorizationStatusEphemeral -> PushPermissionStatus.EPHEMERAL
                        UNAuthorizationStatusDenied -> PushPermissionStatus.DENIED
                        UNAuthorizationStatusNotDetermined -> PushPermissionStatus.NOT_DETERMINED
                        else -> PushPermissionStatus.UNAVAILABLE
                    }
                    if (continuation.isActive) continuation.resume(status)
                }
        }
}

/**
 * Common payload processor that an app-owned Notification Service Extension can call before it
 * returns modified content to iOS. The extension process uses the default policy unless it
 * explicitly configures the same app-specific [PushMessageProcessor].
 */
@OptIn(ExperimentalForeignApi::class)
public class FirebasePushNotificationProcessor {
    private var processor: PushMessageProcessor = PushEventHub.messageProcessor

    /** Installs the same app-specific common policy that the app process uses. */
    public fun configure(processor: PushMessageProcessor): Unit {
        this.processor = processor
    }

    public fun process(userInfo: NSDictionary): PushNotificationRequest? {
        if (userInfo.objectForKey(PushPayloadKeys.LOCAL_NOTIFICATION) as? String == "true") return null
        val message = userInfo.toPushRemoteMessage()
        return when (val result = processor.process(message)) {
            PushMessageDecision.Ignore -> null
            is PushMessageDecision.Display -> result.notification
        }
    }

    /** Processes the payload and applies the common result to APNs content before display. */
    public fun process(userInfo: NSDictionary, content: UNMutableNotificationContent): Boolean {
        val notification = process(userInfo) ?: run {
            content.setTitle("")
            content.setSubtitle("")
            content.setBody("")
            content.setSound(null)
            content.setBadge(null)
            return false
        }
        content.setTitle(notification.title.orEmpty())
        content.setBody(notification.body.orEmpty())
        content.setThreadIdentifier(notification.groupKey.orEmpty())
        content.setCategoryIdentifier(notification.channelId)
        content.setSound(if (notification.soundEnabled) UNNotificationSound.defaultSound() else null)
        content.setUserInfo(notification.toUserInfo(isLocal = false))
        return true
    }
}

@OptIn(ExperimentalForeignApi::class)
internal actual suspend fun showPlatformPushNotification(notification: PushNotificationRequest) {
    val content = UNMutableNotificationContent()
    content.setTitle(notification.title.orEmpty())
    content.setBody(notification.body.orEmpty())
    content.setThreadIdentifier(notification.groupKey.orEmpty())
    content.setCategoryIdentifier(notification.channelId)
    content.setSound(if (notification.soundEnabled) UNNotificationSound.defaultSound() else null)
    content.setUserInfo(notification.toUserInfo(isLocal = true))

    val request = UNNotificationRequest.requestWithIdentifier(
        identifier = notification.id,
        content = content,
        trigger = null,
    )
    suspendCancellableCoroutine<Unit> { continuation ->
        UNUserNotificationCenter.currentNotificationCenter()
            .addNotificationRequest(request) { error ->
                if (!continuation.isActive) return@addNotificationRequest
                if (error != null) {
                    continuation.resumeWithException(PushException(error.localizedDescription))
                } else {
                    saveOwnedNotificationId(notification.id)
                    continuation.resume(Unit)
                }
            }
    }
}

@OptIn(ExperimentalForeignApi::class)
internal actual suspend fun cancelPlatformPushNotification(id: String) {
    val center = UNUserNotificationCenter.currentNotificationCenter()
    center.removePendingNotificationRequestsWithIdentifiers(listOf(id))
    center.removeDeliveredNotificationsWithIdentifiers(listOf(id))
    saveOwnedNotificationIds(ownedNotificationIds() - id)
}

@OptIn(ExperimentalForeignApi::class)
internal actual suspend fun cancelAllPlatformPushNotifications() {
    val center = UNUserNotificationCenter.currentNotificationCenter()
    val ids = ownedNotificationIds()
    center.removePendingNotificationRequestsWithIdentifiers(ids)
    center.removeDeliveredNotificationsWithIdentifiers(ids)
    NSUserDefaults.standardUserDefaults.removeObjectForKey(OWNED_NOTIFICATION_IDS_KEY)
}

@OptIn(ExperimentalForeignApi::class)
private fun NSDictionary.toPushRemoteMessage(): PushRemoteMessage {
    val values = buildMap {
        val keys = keyEnumerator()
        while (true) {
            val key = keys.nextObject() ?: break
            val stringKey = key as? String ?: continue
            val stringValue = objectForKey(key) as? String ?: continue
            put(stringKey, stringValue)
        }
    }
    val aps = objectForKey("aps") as? NSDictionary
    val alert = aps?.objectForKey("alert")
    val alertDictionary = alert as? NSDictionary
    val customData = buildMap {
        values.forEach { (key, value) ->
            when {
                key.startsWith(PushPayloadKeys.DATA_PREFIX) ->
                    put(key.removePrefix(PushPayloadKeys.DATA_PREFIX), value)
                !key.startsWith(PushPayloadKeys.PREFIX) &&
                    key != "gcm.message_id" && key != "google.message_id" -> put(key, value)
            }
        }
    }
    return PushRemoteMessage(
        messageId = values[PushPayloadKeys.MESSAGE_ID]
            ?: values["gcm.message_id"]
            ?: values["google.message_id"],
        type = values[PushPayloadKeys.TYPE],
        title = values[PushPayloadKeys.TITLE] ?: alertDictionary?.objectForKey("title") as? String,
        body = values[PushPayloadKeys.BODY] ?: when (alert) {
            is String -> alert
            is NSDictionary -> alert.objectForKey("body") as? String
            else -> null
        },
        route = values[PushPayloadKeys.ROUTE],
        data = customData,
        sentAt = values[PushPayloadKeys.SENT_AT],
    )
}

private fun PushNotificationRequest.toUserInfo(isLocal: Boolean): Map<Any?, Any> = buildMap {
    put(PushPayloadKeys.LOCAL_NOTIFICATION, isLocal.toString())
    put(PushPayloadKeys.MESSAGE_ID, id)
    type?.let { put(PushPayloadKeys.TYPE, it) }
    title?.let { put(PushPayloadKeys.TITLE, it) }
    body?.let { put(PushPayloadKeys.BODY, it) }
    route?.let { put(PushPayloadKeys.ROUTE, it) }
    data.forEach { (key, value) -> put(PushPayloadKeys.DATA_PREFIX + key, value) }
}

@OptIn(ExperimentalForeignApi::class)
private fun ownedNotificationIds(): List<String> =
    NSUserDefaults.standardUserDefaults.stringArrayForKey(OWNED_NOTIFICATION_IDS_KEY)?.mapNotNull { it as? String }
        .orEmpty()

@OptIn(ExperimentalForeignApi::class)
private fun saveOwnedNotificationId(id: String) {
    saveOwnedNotificationIds(ownedNotificationIds() + id)
}

@OptIn(ExperimentalForeignApi::class)
private fun saveOwnedNotificationIds(ids: List<String>) {
    NSUserDefaults.standardUserDefaults.setObject(ids.distinct(), forKey = OWNED_NOTIFICATION_IDS_KEY)
}

private const val OWNED_NOTIFICATION_IDS_KEY = "weft.push.notification_ids"

@OptIn(ExperimentalForeignApi::class)
private class FirebaseMessagingRegistrationDelegate : NSObject(), FIRMessagingDelegateProtocol {
    override fun messaging(messaging: FIRMessaging, didReceiveRegistration: String?) {
        didReceiveRegistration?.let(PushEventHub::onRegistered)
    }

    override fun messaging(messaging: FIRMessaging, didUnregister: String) {
        PushEventHub.onUnregistered(didUnregister)
    }
}
