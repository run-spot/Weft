package run.thespot.weft.live

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.service.notification.StatusBarNotification
import java.net.URI
import java.text.DateFormat
import java.util.Date

/**
 * Android notification adapter for [LiveActivity].
 *
 * The adapter uses the public notification APIs only. API 36 promotion is requested on eligible
 * progress notifications, but system and OEM policy remain authoritative.
 */
public class AndroidLiveActivityPlatform(
    context: Context,
    public val channelId: String,
    public val channelName: CharSequence,
    public val smallIconResId: Int,
    public val requestPromotedOngoing: Boolean = true,
) : LiveActivityPlatform {
    private val applicationContext: Context = context.applicationContext ?: context
    private val notificationManager: NotificationManager = requireNotNull(
        applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager,
    ) {
        "NotificationManager is unavailable"
    }

    init {
        require(channelId.isNotBlank()) { "channelId must not be blank" }
        require(channelName.isNotBlank()) { "channelName must not be blank" }
        require(smallIconResId > 0) { "smallIconResId must be a valid drawable resource" }
    }

    override fun availability(): LiveActivityAvailability = androidLiveActivityAvailability(
        sdkInt = Build.VERSION.SDK_INT,
        runtimePermissionGranted = hasNotificationPermission(),
        notificationsEnabled = notificationManager.areNotificationsEnabled(),
        channelDisabled = isChannelDisabled(),
    )

    override suspend fun start(activity: LiveActivity): Unit {
        val existing = existingNotificationMatch(activity.id)
        when (existing) {
            AndroidLiveActivityNotificationMatch.None,
            AndroidLiveActivityNotificationMatch.Active,
            -> Unit
            AndroidLiveActivityNotificationMatch.Multiple -> throw platformFailure(
                "Multiple active notifications exist for live activity '${activity.id}'.",
            )
            AndroidLiveActivityNotificationMatch.Terminal,
            AndroidLiveActivityNotificationMatch.Invalid,
            -> throw invalidState(
                "Cannot start live activity '${activity.id}' because its existing notification is not active.",
            )
        }

        requireWritableAvailability(allowTeardown = false)
        createChannelIfNeeded()
        post(activity)
    }

    override suspend fun update(activity: LiveActivity): Unit {
        requireSingleActiveNotification(activity.id)
        requireWritableAvailability(allowTeardown = false)
        createChannelIfNeeded()
        post(activity)
    }

    override suspend fun end(activity: LiveActivity): Unit {
        requireSingleActiveNotification(activity.id)
        val allowTeardown = activity.status == LiveActivityStatus.Cancelled
        requireWritableAvailability(allowTeardown = allowTeardown)

        if (activity.status == LiveActivityStatus.Cancelled) {
            cancel(activity.id)
        } else {
            createChannelIfNeeded()
            post(activity)
        }
    }

    private fun hasNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            applicationContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun isChannelDisabled(): Boolean =
        Build.VERSION.SDK_INT >= 26 &&
            notificationManager.getNotificationChannel(channelId)?.importance ==
            NotificationManager.IMPORTANCE_NONE

    private fun createChannelIfNeeded(): Unit {
        try {
            if (Build.VERSION.SDK_INT >= 26 && notificationManager.getNotificationChannel(channelId) == null) {
                notificationManager.createNotificationChannel(
                    NotificationChannel(
                        channelId,
                        channelName,
                        NotificationManager.IMPORTANCE_LOW,
                    ),
                )
            }
        } catch (error: SecurityException) {
            throw LiveActivityException(
                reason = LiveActivityFailureReason.PermissionRequired,
                message = "Android rejected the live activity notification channel permission.",
                cause = error,
            )
        } catch (error: Exception) {
            throw platformFailure("Android failed to create live activity notification channel.", error)
        }
    }

    private fun existingNotificationMatch(id: String): AndroidLiveActivityNotificationMatch =
        resolveAndroidNotificationMatch(
            notificationManager.activeNotifications
                .filter { it.matchesLiveActivity(id) }
                .map { it.notification.extras.getString(LIVE_ACTIVITY_STATUS_EXTRA) },
        )

    private fun requireSingleActiveNotification(id: String): Unit {
        when (existingNotificationMatch(id)) {
            AndroidLiveActivityNotificationMatch.Active -> Unit
            AndroidLiveActivityNotificationMatch.None,
            AndroidLiveActivityNotificationMatch.Terminal,
            AndroidLiveActivityNotificationMatch.Invalid,
            AndroidLiveActivityNotificationMatch.Multiple,
            -> throw invalidState("No single active live activity notification exists for '$id'.")
        }
    }

    private fun requireWritableAvailability(allowTeardown: Boolean): Unit {
        when (val state = availability()) {
            LiveActivityAvailability.Available -> Unit
            LiveActivityAvailability.PermissionRequired -> {
                if (!allowTeardown) throw LiveActivityException(
                    LiveActivityFailureReason.PermissionRequired,
                    "POST_NOTIFICATIONS permission is required before posting a live activity.",
                )
            }
            LiveActivityAvailability.Disabled -> {
                if (!allowTeardown) throw LiveActivityException(
                    LiveActivityFailureReason.Disabled,
                    "Notifications are disabled for this application or channel.",
                )
            }
            LiveActivityAvailability.Unsupported -> throw LiveActivityException(
                LiveActivityFailureReason.Unsupported,
                "Live activities are unsupported on this Android API level.",
            )
        }
    }

    private fun post(activity: LiveActivity): Unit {
        try {
            val notification = buildNotification(activity)
            notificationManager.notify(activity.id, LIVE_ACTIVITY_NOTIFICATION_ID, notification)
        } catch (error: SecurityException) {
            throw LiveActivityException(
                reason = LiveActivityFailureReason.PermissionRequired,
                message = "Android rejected the live activity notification permission.",
                cause = error,
            )
        } catch (error: Exception) {
            throw platformFailure(
                message = "Android failed to post live activity '${activity.id}'.",
                cause = error,
            )
        }
    }

    private fun cancel(id: String): Unit {
        try {
            notificationManager.cancel(id, LIVE_ACTIVITY_NOTIFICATION_ID)
        } catch (error: SecurityException) {
            throw LiveActivityException(
                reason = LiveActivityFailureReason.PermissionRequired,
                message = "Android rejected the live activity cancellation permission.",
                cause = error,
            )
        } catch (error: Exception) {
            throw platformFailure("Android failed to cancel live activity '$id'.", error)
        }
    }

    @Suppress("NewApi")
    private fun buildNotification(activity: LiveActivity): Notification {
        val builder = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(applicationContext, channelId)
        } else {
            Notification.Builder(applicationContext)
        }
            .setSmallIcon(smallIconResId)
            .setContentTitle(activity.title)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .addExtras(Bundle().apply {
                putString(LIVE_ACTIVITY_STATUS_EXTRA, activity.status.name)
            })

        activity.subtitle?.let(builder::setContentText)
        activity.deepLink?.let { deepLink ->
            builder.setContentIntent(createDeepLinkPendingIntent(activity.id, deepLink))
            builder.setAutoCancel(true)
        }

        when (activity.status) {
            LiveActivityStatus.Active -> {
                builder.setOngoing(true)
                activity.startedAtEpochMillis?.let { startedAt ->
                    builder.setWhen(startedAt)
                    builder.setUsesChronometer(true)
                    builder.setShowWhen(true)
                }
                activity.endsAtEpochMillis?.let { endsAt ->
                    builder.setContentInfo("ETA ${formatTime(endsAt)}")
                }
            }
            LiveActivityStatus.Completed,
            LiveActivityStatus.Failed,
            -> {
                builder
                    .setOngoing(false)
                    .setAutoCancel(true)
                    .setShowWhen(false)
                    .setSubText(
                        if (activity.status == LiveActivityStatus.Completed) "Completed" else "Failed",
                    )
            }
            LiveActivityStatus.Cancelled -> error("Cancelled activities are removed instead of rendered")
        }

        activity.progress?.let { progress ->
            builder
                .setProgress(100, progress.percentage, false)
                .setContentInfo("${progress.current}/${progress.total} (${progress.percentage}%)")

            if (androidLiveActivityRenderMode(Build.VERSION.SDK_INT, activity.status, hasProgress = true) ==
                AndroidLiveActivityRenderMode.ProgressStyle
            ) {
                builder.setStyle(
                    Notification.ProgressStyle()
                        .setStyledByProgress(true)
                        .addProgressSegment(Notification.ProgressStyle.Segment(100))
                        .setProgress(progress.percentage),
                )
                if (activity.status == LiveActivityStatus.Active && requestPromotedOngoing) {
                    builder.setRequestPromotedOngoing(true)
                }
            }
        }

        return builder.build()
    }

    private fun createDeepLinkPendingIntent(id: String, deepLink: String): PendingIntent {
        val parsed = runCatching { URI(deepLink) }.getOrNull()
        if (parsed?.isAbsolute != true || parsed.rawAuthority.isNullOrEmpty()) {
            throw invalidState("deepLink must be an absolute URI with an authority")
        }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(deepLink)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        return PendingIntent.getActivity(
            applicationContext,
            id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

internal const val LIVE_ACTIVITY_NOTIFICATION_ID: Int = 0
internal const val LIVE_ACTIVITY_STATUS_EXTRA: String = "run.thespot.weft.live.status"

internal enum class AndroidLiveActivityRenderMode {
    Standard,
    ProgressStyle,
}

internal enum class AndroidLiveActivityNotificationMatch {
    None,
    Active,
    Terminal,
    Invalid,
    Multiple,
}

internal fun androidLiveActivityAvailability(
    sdkInt: Int,
    runtimePermissionGranted: Boolean,
    notificationsEnabled: Boolean,
    channelDisabled: Boolean,
): LiveActivityAvailability = when {
    sdkInt < 24 -> LiveActivityAvailability.Unsupported
    sdkInt >= 33 && !runtimePermissionGranted -> LiveActivityAvailability.PermissionRequired
    !notificationsEnabled || channelDisabled -> LiveActivityAvailability.Disabled
    else -> LiveActivityAvailability.Available
}

internal fun androidLiveActivityRenderMode(
    sdkInt: Int,
    status: LiveActivityStatus,
    hasProgress: Boolean,
): AndroidLiveActivityRenderMode =
    if (sdkInt >= 36 && status == LiveActivityStatus.Active && hasProgress) {
        AndroidLiveActivityRenderMode.ProgressStyle
    } else {
        AndroidLiveActivityRenderMode.Standard
    }

internal fun resolveAndroidNotificationMatch(
    markers: List<String?>,
): AndroidLiveActivityNotificationMatch {
    if (markers.isEmpty()) return AndroidLiveActivityNotificationMatch.None
    if (markers.size != 1) return AndroidLiveActivityNotificationMatch.Multiple
    return when (markers.single()) {
        LiveActivityStatus.Active.name -> AndroidLiveActivityNotificationMatch.Active
        LiveActivityStatus.Completed.name,
        LiveActivityStatus.Failed.name,
        LiveActivityStatus.Cancelled.name,
        -> AndroidLiveActivityNotificationMatch.Terminal
        else -> AndroidLiveActivityNotificationMatch.Invalid
    }
}

private fun StatusBarNotification.matchesLiveActivity(id: String): Boolean = tag == id && this.id == LIVE_ACTIVITY_NOTIFICATION_ID

private fun formatTime(epochMillis: Long): String =
    DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(epochMillis))

private fun invalidState(message: String): LiveActivityException = LiveActivityException(
    reason = LiveActivityFailureReason.InvalidState,
    message = message,
)

private fun platformFailure(message: String, cause: Throwable? = null): LiveActivityException = LiveActivityException(
    reason = LiveActivityFailureReason.PlatformFailure,
    message = message,
    cause = cause,
)
