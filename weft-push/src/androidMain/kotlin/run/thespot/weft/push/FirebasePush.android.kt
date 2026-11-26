package run.thespot.weft.push

import android.app.NotificationManager
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import run.thespot.weft.core.Weft

internal actual fun createFirebasePushPlugin(processor: PushMessageProcessor): PushPlugin =
    AndroidFirebasePushRuntime(processor)

@Suppress("UNUSED_PARAMETER")
internal actual fun installFirebasePush(plugin: PushPlugin, scope: Weft): Unit = Unit

private class AndroidFirebasePushRuntime(
    override val messageProcessor: PushMessageProcessor,
) : PushPlugin {
    override val registrationId = PushEventHub.registrationId

    override val notifications = PushEventHub.notifications

    override suspend fun register() {
        FirebaseMessaging.getInstance().register().awaitCompletion()
    }

    override suspend fun unregister() {
        FirebaseMessaging.getInstance().unregister().awaitCompletion()
        PushEventHub.onUnregistered()
    }

    override suspend fun permissionStatus(): PushPermissionStatus {
        val context = FirebaseApp.getInstance().applicationContext
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return PushPermissionStatus.DENIED
        }

        val manager = context.getSystemService(NotificationManager::class.java)
            ?: return PushPermissionStatus.UNAVAILABLE
        return if (manager.areNotificationsEnabled()) {
            PushPermissionStatus.GRANTED
        } else {
            PushPermissionStatus.DENIED
        }
    }
}

private suspend fun Task<*>.awaitCompletion(): Unit = suspendCancellableCoroutine { continuation ->
    addOnCompleteListener { task ->
        if (!continuation.isActive) return@addOnCompleteListener

        when {
            task.isCanceled -> continuation.cancel(
                java.util.concurrent.CancellationException("Firebase push operation was cancelled."),
            )
            task.isSuccessful -> continuation.resume(Unit)
            else -> continuation.resumeWithException(
                task.exception
                    ?: IllegalStateException("Firebase push operation failed without an exception."),
            )
        }
    }
}
