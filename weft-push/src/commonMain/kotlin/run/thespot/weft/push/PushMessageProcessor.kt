package run.thespot.weft.push

/** A normalized FCM message that is independent of Android and Apple notification APIs. */
public data class PushRemoteMessage(
    public val messageId: String? = null,
    public val type: String? = null,
    public val title: String? = null,
    public val body: String? = null,
    public val route: String? = null,
    public val data: Map<String, String> = emptyMap(),
    public val sentAt: String? = null,
)

/** Presentation details produced by the shared message policy. */
public data class PushNotificationRequest(
    public val id: String,
    public val title: String? = null,
    public val body: String? = null,
    public val type: String? = null,
    public val route: String? = null,
    public val data: Map<String, String> = emptyMap(),
    public val groupKey: String? = null,
    public val channelId: String = DEFAULT_CHANNEL_ID,
    public val channelName: String = DEFAULT_CHANNEL_NAME,
    public val soundEnabled: Boolean = true,
) {
    public companion object {
        public const val DEFAULT_CHANNEL_ID: String = "weft.push.general"
        public const val DEFAULT_CHANNEL_NAME: String = "General"
    }
}

/** A decision made by the common policy before a platform presents an incoming message. */
public sealed interface PushMessageDecision {
    /** The message is handled elsewhere or should not be shown to the user. */
    public data object Ignore : PushMessageDecision

    /** The message should be presented with [notification] by the platform notification service. */
    public data class Display(
        public val notification: PushNotificationRequest,
    ) : PushMessageDecision
}

/** Converts a provider message into an OS notification decision. Implement this in common code. */
public fun interface PushMessageProcessor {
    public fun process(message: PushRemoteMessage): PushMessageDecision
}

/** Default policy: present messages that contain a non-blank title or body. */
public object DefaultPushMessageProcessor : PushMessageProcessor {
    override fun process(message: PushRemoteMessage): PushMessageDecision {
        val title = message.title?.takeIf(String::isNotBlank)
        val body = message.body?.takeIf(String::isNotBlank)
        if (title == null && body == null) return PushMessageDecision.Ignore

        val type = message.type?.takeIf(String::isNotBlank)
        val id = message.messageId?.takeIf(String::isNotBlank)
            ?: listOfNotNull(type, message.sentAt, title, body).joinToString(":")

        return PushMessageDecision.Display(
            PushNotificationRequest(
                id = id,
                title = title,
                body = body,
                type = type,
                route = message.route,
                data = message.data,
                groupKey = type,
            ),
        )
    }
}

/** Reserved FCM data keys understood by both platform adapters. */
public object PushPayloadKeys {
    public const val PREFIX: String = "weft."
    public const val MESSAGE_ID: String = "weft.messageId"
    public const val TYPE: String = "weft.type"
    public const val TITLE: String = "weft.title"
    public const val BODY: String = "weft.body"
    public const val ROUTE: String = "weft.route"
    public const val SENT_AT: String = "weft.sentAt"
    public const val DATA_PREFIX: String = "weft.data."
    public const val LOCAL_NOTIFICATION: String = "weft.local"
}
