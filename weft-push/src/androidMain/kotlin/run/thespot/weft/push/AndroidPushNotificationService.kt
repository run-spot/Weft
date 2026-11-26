package run.thespot.weft.push

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import com.google.firebase.FirebaseApp

internal actual suspend fun showPlatformPushNotification(notification: PushNotificationRequest) {
    AndroidPushNotificationService.show(FirebaseApp.getInstance().applicationContext, notification)
}

internal actual suspend fun cancelPlatformPushNotification(id: String) {
    AndroidPushNotificationService.cancel(FirebaseApp.getInstance().applicationContext, id)
}

internal actual suspend fun cancelAllPlatformPushNotifications() {
    AndroidPushNotificationService.cancelAll(FirebaseApp.getInstance().applicationContext)
}

private object AndroidPushNotificationService {
    private const val TAG = "weft.push"
    private const val PREFERENCES = "weft_push_notifications"
    private const val OWNED_IDS_KEY = "owned_ids"
    private const val EXTRA_PREFIX = "weft.push."
    private const val EXTRA_ID = "${EXTRA_PREFIX}id"
    private const val EXTRA_TYPE = "${EXTRA_PREFIX}type"
    private const val EXTRA_ROUTE = "${EXTRA_PREFIX}route"
    private const val EXTRA_DATA = "${EXTRA_PREFIX}data"

    fun show(context: Context, notification: PushNotificationRequest) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (!manager.areNotificationsEnabled()) return
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val channelId = if (notification.soundEnabled) {
            notification.channelId
        } else {
            "${notification.channelId}.silent"
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    channelId,
                    if (notification.soundEnabled) notification.channelName else "${notification.channelName} (silent)",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    if (!notification.soundEnabled) setSound(null, null)
                },
            )
        }

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, channelId)
        } else {
            Notification.Builder(context)
        }
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_ID, notification.id)
            notification.type?.let { putExtra(EXTRA_TYPE, it) }
            notification.route?.let { putExtra(EXTRA_ROUTE, it) }
            if (notification.data.isNotEmpty()) {
                putExtra(EXTRA_DATA, Bundle().apply { notification.data.forEach(::putString) })
            }
        }
        val pendingIntent = intent?.let {
            PendingIntent.getActivity(
                context,
                notification.id.hashCode(),
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
        val smallIcon = context.applicationInfo.icon.takeIf { it != 0 } ?: android.R.drawable.ic_dialog_info
        builder.setSmallIcon(smallIcon)
            .setContentTitle(notification.title.orEmpty())
            .setContentText(notification.body.orEmpty())
            .setStyle(Notification.BigTextStyle().bigText(notification.body.orEmpty()))
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_MESSAGE)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O && !notification.soundEnabled) {
            builder.setSound(null, null)
        }
        notification.groupKey?.let(builder::setGroup)
        pendingIntent?.let(builder::setContentIntent)

        manager.notify("$TAG:${notification.id}", 0, builder.build())
        updateOwnedIds(context) { it + notification.id }
    }

    fun cancel(context: Context, id: String) {
        context.getSystemService(NotificationManager::class.java)?.cancel("$TAG:$id", 0)
        updateOwnedIds(context) { it - id }
    }

    fun cancelAll(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        ownedIds(context).forEach { id -> manager.cancel("$TAG:$id", 0) }
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit().remove(OWNED_IDS_KEY).apply()
    }

    private fun ownedIds(context: Context): Set<String> =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getStringSet(OWNED_IDS_KEY, emptySet())
            .orEmpty()

    private fun updateOwnedIds(context: Context, update: (Set<String>) -> Set<String>) {
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        synchronized(this) {
            preferences.edit().putStringSet(OWNED_IDS_KEY, update(ownedIds(context))).apply()
        }
    }
}
