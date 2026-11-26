package run.thespot.weft.live

import kotlin.reflect.KClass
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import run.thespot.weft.core.Weft
import run.thespot.weft.core.WeftPlugin

/** Platform boundary implemented by the Android or Apple adapter. */
public interface LiveActivityPlatform {
    public fun availability(): LiveActivityAvailability

    public suspend fun start(activity: LiveActivity)

    public suspend fun update(activity: LiveActivity)

    public suspend fun end(activity: LiveActivity)
}

/** Installed Weft capability for finite, user-visible live activities. */
public interface LiveActivityPlugin {
    public fun availability(): LiveActivityAvailability

    public suspend fun start(activity: LiveActivity)

    public suspend fun update(activity: LiveActivity)

    public suspend fun end(activity: LiveActivity)
}

/** Configuration for [WeftLive]. */
public class WeftLiveConfig {
    public var platform: LiveActivityPlatform? = null
}

/** Installs the configured live-activity provider into the shared Weft registry. */
public object WeftLive : WeftPlugin<WeftLiveConfig, LiveActivityPlugin> {
    override val type: KClass<LiveActivityPlugin> = LiveActivityPlugin::class

    override val pluginName: String = "WeftLive"

    override fun prepare(block: WeftLiveConfig.() -> Unit): LiveActivityPlugin {
        val config = WeftLiveConfig().apply(block)
        val platform = config.platform
            ?: throw LiveActivityException(
                reason = LiveActivityFailureReason.NotConfigured,
                message = "WeftLive requires a LiveActivityPlatform in its configuration.",
            )
        return DelegatingLiveActivityPlugin(platform)
    }

    override fun install(plugin: LiveActivityPlugin, scope: Weft): Unit = Unit
}

private class DelegatingLiveActivityPlugin(
    private val platform: LiveActivityPlatform,
) : LiveActivityPlugin {
    private val lifecycleMutex = Mutex()

    override fun availability(): LiveActivityAvailability = platform.availability()

    override suspend fun start(activity: LiveActivity) {
        lifecycleMutex.withLock {
            requireStatus(activity, LiveActivityStatus.Active, operation = "start")
            requireAvailable(platform.availability(), allowTeardown = false)
            platform.start(activity)
        }
    }

    override suspend fun update(activity: LiveActivity) {
        lifecycleMutex.withLock {
            requireStatus(activity, LiveActivityStatus.Active, operation = "update")
            requireAvailable(platform.availability(), allowTeardown = false)
            platform.update(activity)
        }
    }

    override suspend fun end(activity: LiveActivity) {
        lifecycleMutex.withLock {
            requireTerminalStatus(activity, operation = "end")
            requireAvailable(
                availability = platform.availability(),
                allowTeardown = activity.status == LiveActivityStatus.Cancelled,
            )
            platform.end(activity)
        }
    }
}

private fun requireStatus(activity: LiveActivity, expected: LiveActivityStatus, operation: String) {
    if (activity.status != expected) {
        throw LiveActivityException(
            reason = LiveActivityFailureReason.InvalidState,
            message = "$operation requires status $expected, but received ${activity.status}.",
        )
    }
}

private fun requireTerminalStatus(activity: LiveActivity, operation: String) {
    if (activity.status !in setOf(
            LiveActivityStatus.Completed,
            LiveActivityStatus.Failed,
            LiveActivityStatus.Cancelled,
        )
    ) {
        throw LiveActivityException(
            reason = LiveActivityFailureReason.InvalidState,
            message = "$operation requires a terminal status, but received ${activity.status}.",
        )
    }
}

private fun requireAvailable(
    availability: LiveActivityAvailability,
    allowTeardown: Boolean,
) {
    when (availability) {
        LiveActivityAvailability.Available -> Unit
        LiveActivityAvailability.PermissionRequired -> {
            if (!allowTeardown) {
                throw LiveActivityException(
                    reason = LiveActivityFailureReason.PermissionRequired,
                    message = "Live activity notification permission is required.",
                )
            }
        }
        LiveActivityAvailability.Disabled -> {
            if (!allowTeardown) {
                throw LiveActivityException(
                    reason = LiveActivityFailureReason.Disabled,
                    message = "Live activities are disabled for this application.",
                )
            }
        }
        LiveActivityAvailability.Unsupported -> throw LiveActivityException(
            reason = LiveActivityFailureReason.Unsupported,
            message = "Live activities are unsupported on this platform.",
        )
    }
}
