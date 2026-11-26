package run.thespot.weft.live

import kotlin.test.Test
import kotlin.test.assertEquals

class AndroidLiveActivityPlatformTest {
    @Test
    fun availabilityUsesSupportPermissionAndSettingsPrecedence() {
        assertEquals(
            LiveActivityAvailability.Unsupported,
            androidLiveActivityAvailability(
                sdkInt = 23,
                runtimePermissionGranted = false,
                notificationsEnabled = false,
                channelDisabled = true,
            ),
        )
        assertEquals(
            LiveActivityAvailability.PermissionRequired,
            androidLiveActivityAvailability(
                sdkInt = 33,
                runtimePermissionGranted = false,
                notificationsEnabled = false,
                channelDisabled = true,
            ),
        )
        assertEquals(
            LiveActivityAvailability.Disabled,
            androidLiveActivityAvailability(
                sdkInt = 33,
                runtimePermissionGranted = true,
                notificationsEnabled = false,
                channelDisabled = true,
            ),
        )
        assertEquals(
            LiveActivityAvailability.Disabled,
            androidLiveActivityAvailability(
                sdkInt = 33,
                runtimePermissionGranted = true,
                notificationsEnabled = true,
                channelDisabled = true,
            ),
        )
        assertEquals(
            LiveActivityAvailability.Available,
            androidLiveActivityAvailability(
                sdkInt = 24,
                runtimePermissionGranted = false,
                notificationsEnabled = true,
                channelDisabled = false,
            ),
        )
        assertEquals(
            LiveActivityAvailability.Available,
            androidLiveActivityAvailability(
                sdkInt = 25,
                runtimePermissionGranted = false,
                notificationsEnabled = true,
                channelDisabled = false,
            ),
        )
        assertEquals(
            LiveActivityAvailability.Available,
            androidLiveActivityAvailability(
                sdkInt = 32,
                runtimePermissionGranted = false,
                notificationsEnabled = true,
                channelDisabled = false,
            ),
        )
        assertEquals(
            LiveActivityAvailability.PermissionRequired,
            androidLiveActivityAvailability(
                sdkInt = 35,
                runtimePermissionGranted = false,
                notificationsEnabled = true,
                channelDisabled = false,
            ),
        )
        assertEquals(
            LiveActivityAvailability.Disabled,
            androidLiveActivityAvailability(
                sdkInt = 36,
                runtimePermissionGranted = true,
                notificationsEnabled = false,
                channelDisabled = false,
            ),
        )
    }

    @Test
    fun renderModeUsesProgressStyleOnlyForActiveApi36ActivitiesWithProgress() {
        assertEquals(
            AndroidLiveActivityRenderMode.ProgressStyle,
            androidLiveActivityRenderMode(36, LiveActivityStatus.Active, hasProgress = true),
        )
        assertEquals(
            AndroidLiveActivityRenderMode.Standard,
            androidLiveActivityRenderMode(36, LiveActivityStatus.Active, hasProgress = false),
        )
        assertEquals(
            AndroidLiveActivityRenderMode.Standard,
            androidLiveActivityRenderMode(36, LiveActivityStatus.Completed, hasProgress = true),
        )
        assertEquals(
            AndroidLiveActivityRenderMode.Standard,
            androidLiveActivityRenderMode(35, LiveActivityStatus.Active, hasProgress = true),
        )
    }

    @Test
    fun notificationMarkerResolutionDistinguishesLifecycleIdentityStates() {
        assertEquals(AndroidLiveActivityNotificationMatch.None, resolveAndroidNotificationMatch(emptyList()))
        assertEquals(
            AndroidLiveActivityNotificationMatch.Active,
            resolveAndroidNotificationMatch(listOf(LiveActivityStatus.Active.name)),
        )
        assertEquals(
            AndroidLiveActivityNotificationMatch.Terminal,
            resolveAndroidNotificationMatch(listOf(LiveActivityStatus.Completed.name)),
        )
        assertEquals(
            AndroidLiveActivityNotificationMatch.Invalid,
            resolveAndroidNotificationMatch(listOf("unexpected")),
        )
        assertEquals(
            AndroidLiveActivityNotificationMatch.Multiple,
            resolveAndroidNotificationMatch(listOf(LiveActivityStatus.Active.name, LiveActivityStatus.Active.name)),
        )
    }
}
