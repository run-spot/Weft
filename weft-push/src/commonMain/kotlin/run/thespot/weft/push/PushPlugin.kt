package run.thespot.weft.push

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.reflect.KClass
import run.thespot.weft.core.Weft
import run.thespot.weft.core.WeftPlugin

/**
 * An opaque identifier that the configured push provider accepts when targeting this app
 * installation.
 *
 * The Firebase adapter currently exposes Firebase Installation IDs (FIDs), not legacy FCM
 * registration tokens. Treat this value as a server-side device identifier and do not log it.
 */
public data class PushRegistrationId(
    public val value: String,
)

/** A provider-neutral notification delivered to the running app. */
public data class PushNotification(
    /** Provider message identifier, when one was included in the payload. */
    public val messageId: String?,
    /** Notification title, when the provider supplied one. */
    public val title: String?,
    /** Notification body, when the provider supplied one. */
    public val body: String?,
    /** String-valued custom data included with the notification. */
    public val data: Map<String, String>,
    /** Application-defined message type, when supplied. */
    public val type: String? = null,
    /** App route to open when the user selects the notification. */
    public val route: String? = null,
    /** Provider message timestamp, when supplied. */
    public val sentAt: String? = null,
)

/** The platform's current ability to show user-visible notifications. */
public enum class PushPermissionStatus {
    /** The platform currently allows notifications. */
    GRANTED,

    /** iOS allows quiet delivery to Notification Center without a standard prompt. */
    PROVISIONAL,

    /** The user or system has denied notification presentation. */
    DENIED,

    /** iOS has not asked the user for notification authorization yet. */
    NOT_DETERMINED,

    /** iOS grants temporary authorization to an App Clip. */
    EPHEMERAL,

    /** The platform or device cannot report or support notification permission. */
    UNAVAILABLE,
}

/**
 * Push registration and incoming notification events for the active provider.
 *
 * The notification stream is a hot stream with no replay. Collect it while the app is ready to
 * handle in-process notifications. It does not persist messages across process death or replay
 * system-tray notifications when a user opens the app.
 */
public interface PushPlugin {
    /** Latest provider registration identifier, or `null` until a registration is reported. */
    public val registrationId: StateFlow<PushRegistrationId?>

    /** Notifications delivered while this process can receive them. */
    public val notifications: Flow<PushNotification>

    /** Policy used by platform callbacks to decide how an incoming message should be handled. */
    public val messageProcessor: PushMessageProcessor

    /** Runs the configured common policy without presenting a notification. */
    public fun process(message: PushRemoteMessage): PushMessageDecision = messageProcessor.process(message)

    /** Presents a local notification through the current platform notification service. */
    public suspend fun showNotification(notification: PushNotificationRequest): Unit =
        showPlatformPushNotification(notification)

    /** Removes a local notification previously presented through this service. */
    public suspend fun cancelNotification(id: String): Unit = cancelPlatformPushNotification(id)

    /** Removes local notifications previously presented through this service. */
    public suspend fun cancelAllNotifications(): Unit = cancelAllPlatformPushNotifications()

    /**
     * Ensures this app installation is registered with the provider.
     *
     * The resulting identifier arrives through [registrationId]. Platform callback delivery may
     * happen before or after this call completes, so observe [registrationId] independently.
     */
    public suspend fun register()

    /**
     * Unregisters this app installation from the provider and clears the local identifier.
     *
     * This does not delete a Firebase Installation ID or remove an app-server record. Firebase
     * auto-initialization may register the app again on a later launch.
     */
    public suspend fun unregister()

    /** Reads the current OS notification authorization state without prompting the user. */
    public suspend fun permissionStatus(): PushPermissionStatus
}

/** Configuration for the default Firebase Cloud Messaging adapter. */
public class FirebasePushConfig internal constructor() {
    /** Shared processing policy for messages delivered while the app process is running. */
    public var messageProcessor: PushMessageProcessor = DefaultPushMessageProcessor
}

/**
 * Default Firebase Cloud Messaging provider for Android and iOS.
 *
 * Install it through `Weft { install(FirebasePush) }`. Firebase project setup, APNs credentials,
 * and user-facing permission prompts remain owned by the consuming application.
 */
public object FirebasePush : WeftPlugin<FirebasePushConfig, PushPlugin> {
    public override val type: KClass<PushPlugin> = PushPlugin::class

    public override val pluginName: String = "FirebasePush"

    public override fun prepare(block: FirebasePushConfig.() -> Unit): PushPlugin {
        val config = FirebasePushConfig().apply(block)
        return createFirebasePushPlugin(config.messageProcessor)
    }

    public override fun install(plugin: PushPlugin, scope: Weft) {
        PushEventHub.messageProcessor = plugin.messageProcessor
        installFirebasePush(plugin, scope)
    }
}

internal expect fun createFirebasePushPlugin(processor: PushMessageProcessor): PushPlugin

internal expect fun installFirebasePush(plugin: PushPlugin, scope: Weft)

internal expect suspend fun showPlatformPushNotification(notification: PushNotificationRequest)

internal expect suspend fun cancelPlatformPushNotification(id: String)

internal expect suspend fun cancelAllPlatformPushNotifications()

internal object PushEventHub {
    private val mutableRegistrationId = MutableStateFlow<PushRegistrationId?>(null)
    private val mutableNotifications = MutableSharedFlow<PushNotification>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    var messageProcessor: PushMessageProcessor = DefaultPushMessageProcessor

    val registrationId: StateFlow<PushRegistrationId?> = mutableRegistrationId
    val notifications: Flow<PushNotification> = mutableNotifications

    fun onRegistered(value: String) {
        mutableRegistrationId.value = PushRegistrationId(value)
    }

    fun onUnregistered(value: String? = null) {
        if (value == null || mutableRegistrationId.value?.value == value) {
            mutableRegistrationId.value = null
        }
    }

    fun onNotificationReceived(notification: PushNotification) {
        mutableNotifications.tryEmit(notification)
    }

    fun onMessageReceived(message: PushRemoteMessage): PushMessageDecision {
        onNotificationReceived(
            PushNotification(
                messageId = message.messageId,
                title = message.title,
                body = message.body,
                data = message.data,
                type = message.type,
                route = message.route,
                sentAt = message.sentAt,
            ),
        )
        return messageProcessor.process(message)
    }
}
