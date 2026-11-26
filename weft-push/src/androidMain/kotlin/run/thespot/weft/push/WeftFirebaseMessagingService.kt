package run.thespot.weft.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Android FCM entry point declared by the library manifest.
 *
 * An application that needs a custom service should remove this library's manifest declaration,
 * declare its subclass instead, and call `super` from overridden callbacks. Android apps should
 * have one FCM messaging service.
 */
public open class WeftFirebaseMessagingService : FirebaseMessagingService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    public open override fun onRegistered(installationId: String) {
        PushEventHub.onRegistered(installationId)
        super.onRegistered(installationId)
    }

    public open override fun onUnregistered(installationId: String) {
        PushEventHub.onUnregistered(installationId)
        super.onUnregistered(installationId)
    }

    public open override fun onMessageReceived(message: RemoteMessage) {
        val remoteMessage = message.toPushRemoteMessage()
        val decision = PushEventHub.onMessageReceived(remoteMessage)
        if (decision is PushMessageDecision.Display) {
            serviceScope.launch { showPlatformPushNotification(decision.notification) }
        }
        super.onMessageReceived(message)
    }

    public override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}

private fun RemoteMessage.toPushRemoteMessage(): PushRemoteMessage {
    val values = data
    val customData = buildMap {
        values.forEach { (key, value) ->
            when {
                key.startsWith(PushPayloadKeys.DATA_PREFIX) ->
                    put(key.removePrefix(PushPayloadKeys.DATA_PREFIX), value)
                !key.startsWith(PushPayloadKeys.PREFIX) -> put(key, value)
            }
        }
    }
    return PushRemoteMessage(
        messageId = values[PushPayloadKeys.MESSAGE_ID] ?: messageId,
        type = values[PushPayloadKeys.TYPE],
        title = values[PushPayloadKeys.TITLE] ?: notification?.title,
        body = values[PushPayloadKeys.BODY] ?: notification?.body,
        route = values[PushPayloadKeys.ROUTE],
        data = customData,
        sentAt = values[PushPayloadKeys.SENT_AT],
    )
}
