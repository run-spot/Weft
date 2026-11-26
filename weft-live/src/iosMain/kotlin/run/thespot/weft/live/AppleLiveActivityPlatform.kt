@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package run.thespot.weft.live

import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.cinterop.useContents
import platform.Foundation.NSNumber
import platform.Foundation.NSProcessInfo
import swiftPMImport.run.thespot.weft.weft.live.WeftLiveActivityBridge
import swiftPMImport.run.thespot.weft.weft.live.WeftLiveActivityBridgeResult

/** ActivityKit-backed live-activity provider for iOS 16.1 and newer. */
public class AppleLiveActivityPlatform internal constructor(
    private val bridge: AppleLiveActivityBridge,
    private val osVersion: AppleOperatingSystemVersion,
) : LiveActivityPlatform {
    /** Creates a provider backed by the local WeftLiveKit ActivityKit façade. */
    public constructor() : this(
        bridge = NativeAppleLiveActivityBridge(WeftLiveActivityBridge()),
        osVersion = currentAppleOperatingSystemVersion(),
    )

    override fun availability(): LiveActivityAvailability {
        if (!osVersion.supportsActivityKit) return LiveActivityAvailability.Unsupported
        return when (bridge.availability()) {
            "available" -> LiveActivityAvailability.Available
            "disabled" -> LiveActivityAvailability.Disabled
            "permission_required" -> LiveActivityAvailability.PermissionRequired
            else -> LiveActivityAvailability.Unsupported
        }
    }

    override suspend fun start(activity: LiveActivity): Unit = perform { completion ->
        bridge.start(activity, completion)
    }

    override suspend fun update(activity: LiveActivity): Unit = perform { completion ->
        bridge.update(activity, completion)
    }

    override suspend fun end(activity: LiveActivity): Unit = perform { completion ->
        bridge.end(activity, completion)
    }

    private suspend fun perform(
        operation: ((AppleLiveActivityResult) -> Unit) -> Unit,
    ) {
        if (!osVersion.supportsActivityKit) {
            throw LiveActivityException(
                reason = LiveActivityFailureReason.Unsupported,
                message = "ActivityKit Live Activities require iOS 16.1 or newer.",
            )
        }
        suspendCancellableCoroutine { continuation ->
            try {
                operation { result ->
                    if (!continuation.isActive) return@operation
                    val failure = result.toException()
                    if (failure == null) {
                        continuation.resume(Unit)
                    } else {
                        continuation.resumeWithException(failure)
                    }
                }
            } catch (cause: Throwable) {
                if (continuation.isActive) {
                    continuation.resumeWithException(
                        LiveActivityException(
                            reason = LiveActivityFailureReason.PlatformFailure,
                            message = "ActivityKit bridge invocation failed.",
                            cause = cause,
                        ),
                    )
                }
            }
        }
    }
}
internal data class AppleOperatingSystemVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
) {
    val supportsActivityKit: Boolean
        get() = major > 16 || (major == 16 && minor >= 1)
}

private fun currentAppleOperatingSystemVersion(): AppleOperatingSystemVersion {
    return NSProcessInfo.processInfo.operatingSystemVersion.useContents {
        AppleOperatingSystemVersion(
            major = majorVersion.toInt(),
            minor = minorVersion.toInt(),
            patch = patchVersion.toInt(),
        )
    }
}

internal data class AppleLiveActivityResult(
    val accepted: Boolean,
    val failureCode: String? = null,
    val failureMessage: String? = null,
)

internal interface AppleLiveActivityBridge {
    fun availability(): String

    fun start(activity: LiveActivity, completion: (AppleLiveActivityResult) -> Unit)

    fun update(activity: LiveActivity, completion: (AppleLiveActivityResult) -> Unit)

    fun end(activity: LiveActivity, completion: (AppleLiveActivityResult) -> Unit)
}

private class NativeAppleLiveActivityBridge(
    private val bridge: WeftLiveActivityBridge,
) : AppleLiveActivityBridge {
    override fun availability(): String = bridge.availability()

    override fun start(activity: LiveActivity, completion: (AppleLiveActivityResult) -> Unit) {
        bridge.startWithId(
            id = activity.id,
            title = activity.title,
            status = activity.status.toWireValue(),
            subtitle = activity.subtitle,
            progressCurrent = activity.progress?.current.toNSNumber(),
            progressTotal = activity.progress?.total.toNSNumber(),
            startedAtMillis = activity.startedAtEpochMillis.toNSNumber(),
            endsAtMillis = activity.endsAtEpochMillis.toNSNumber(),
            deepLink = activity.deepLink,
            completion = { result -> completion(result.toCommonResult()) },
        )
    }

    override fun update(activity: LiveActivity, completion: (AppleLiveActivityResult) -> Unit) {
        bridge.updateWithId(
            id = activity.id,
            title = activity.title,
            status = activity.status.toWireValue(),
            subtitle = activity.subtitle,
            progressCurrent = activity.progress?.current.toNSNumber(),
            progressTotal = activity.progress?.total.toNSNumber(),
            startedAtMillis = activity.startedAtEpochMillis.toNSNumber(),
            endsAtMillis = activity.endsAtEpochMillis.toNSNumber(),
            deepLink = activity.deepLink,
            completion = { result -> completion(result.toCommonResult()) },
        )
    }

    override fun end(activity: LiveActivity, completion: (AppleLiveActivityResult) -> Unit) {
        bridge.endWithId(
            id = activity.id,
            title = activity.title,
            status = activity.status.toWireValue(),
            subtitle = activity.subtitle,
            progressCurrent = activity.progress?.current.toNSNumber(),
            progressTotal = activity.progress?.total.toNSNumber(),
            startedAtMillis = activity.startedAtEpochMillis.toNSNumber(),
            endsAtMillis = activity.endsAtEpochMillis.toNSNumber(),
            deepLink = activity.deepLink,
            completion = { result -> completion(result.toCommonResult()) },
        )
    }
}

private fun LiveActivityStatus.toWireValue(): String = when (this) {
    LiveActivityStatus.Active -> "active"
    LiveActivityStatus.Completed -> "completed"
    LiveActivityStatus.Failed -> "failed"
    LiveActivityStatus.Cancelled -> "cancelled"
}

private fun Long?.toNSNumber(): NSNumber? = this?.let { NSNumber(longLong = it) }

private fun WeftLiveActivityBridgeResult?.toCommonResult(): AppleLiveActivityResult = AppleLiveActivityResult(
    accepted = this?.accepted == true,
    failureCode = this?.failureCode,
    failureMessage = this?.failureMessage,
)

private fun AppleLiveActivityResult.toException(): LiveActivityException? {
    if (accepted) {
        if (failureCode == null && failureMessage == null) return null
        return LiveActivityException(
            reason = LiveActivityFailureReason.PlatformFailure,
            message = "ActivityKit returned an inconsistent success result.",
        )
    }
    if (failureCode.isNullOrBlank() || failureMessage.isNullOrBlank()) {
        return LiveActivityException(
            reason = LiveActivityFailureReason.PlatformFailure,
            message = "ActivityKit returned an incomplete failure result.",
        )
    }
    val reason = when (failureCode) {
        "unsupported" -> LiveActivityFailureReason.Unsupported
        "disabled" -> LiveActivityFailureReason.Disabled
        "permission_required" -> LiveActivityFailureReason.PermissionRequired
        "invalid_state" -> LiveActivityFailureReason.InvalidState
        else -> LiveActivityFailureReason.PlatformFailure
    }
    return LiveActivityException(
        reason = reason,
        message = failureMessage,
    )
}
