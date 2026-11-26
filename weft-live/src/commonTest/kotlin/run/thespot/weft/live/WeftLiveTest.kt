package run.thespot.weft.live

import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import run.thespot.weft.core.DuplicateWeftPluginException
import run.thespot.weft.core.Weft

class WeftLiveTest {
    @Test
    fun installsAndDelegatesValidLifecycleOperations() {
        val platform = RecordingPlatform()
        val plugin = Weft {
            install(WeftLive) { this.platform = platform }
        }.get<LiveActivityPlugin>()
        val active = LiveActivity("order", "Preparing")
        val completed = LiveActivity("order", "Done", status = LiveActivityStatus.Completed)

        runSuspend {
            plugin.start(active)
            plugin.update(active.copy(title = "Almost ready"))
            plugin.end(completed)
        }

        assertEquals(listOf("start:order", "update:order", "end:order"), platform.operations)
        assertEquals(LiveActivityAvailability.Available, plugin.availability())
    }

    @Test
    fun rejectsInvalidOperationStateBeforeCheckingAvailability() {
        val platform = RecordingPlatform(availability = LiveActivityAvailability.Disabled)
        val plugin = install(platform)

        val error = assertFailsWith<LiveActivityException> {
            runSuspend { plugin.start(LiveActivity("order", "Done", status = LiveActivityStatus.Completed)) }
        }

        assertEquals(LiveActivityFailureReason.InvalidState, error.reason)
        assertEquals(0, platform.availabilityCalls)
        assertTrue(platform.operations.isEmpty())
    }

    @Test
    fun mapsUnavailablePlatformForStartUpdateAndTerminalEnd() {
        for ((availability, reason) in listOf(
            LiveActivityAvailability.Unsupported to LiveActivityFailureReason.Unsupported,
            LiveActivityAvailability.PermissionRequired to LiveActivityFailureReason.PermissionRequired,
            LiveActivityAvailability.Disabled to LiveActivityFailureReason.Disabled,
        )) {
            assertFailure(reason) {
                install(RecordingPlatform(availability)).start(LiveActivity("order", "Preparing"))
            }
            assertFailure(reason) {
                install(RecordingPlatform(availability)).update(LiveActivity("order", "Preparing"))
            }
            assertFailure(reason) {
                install(RecordingPlatform(availability)).end(
                    LiveActivity("order", "Done", status = LiveActivityStatus.Completed),
                )
            }
        }
    }

    @Test
    fun cancelledEndAttemptsTeardownWhenPermissionOrSettingsAreUnavailable() {
        for (availability in listOf(
            LiveActivityAvailability.PermissionRequired,
            LiveActivityAvailability.Disabled,
        )) {
            val platform = RecordingPlatform(availability)
            val plugin = install(platform)

            runSuspend {
                plugin.end(LiveActivity("order", "Cancelled", status = LiveActivityStatus.Cancelled))
            }

            assertEquals(listOf("end:order"), platform.operations)
        }
    }

    @Test
    fun cancelledEndStillRejectsUnsupportedPlatforms() {
        val plugin = install(RecordingPlatform(LiveActivityAvailability.Unsupported))

        val error = assertFailsWith<LiveActivityException> {
            runSuspend {
                plugin.end(LiveActivity("order", "Cancelled", status = LiveActivityStatus.Cancelled))
            }
        }

        assertEquals(LiveActivityFailureReason.Unsupported, error.reason)
    }

    @Test
    fun missingPlatformIsNotConfiguredAndDuplicateInstallationFails() {
        val missing = assertFailsWith<LiveActivityException> {
            Weft { install(WeftLive) }
        }
        assertEquals(LiveActivityFailureReason.NotConfigured, missing.reason)

        val platform = RecordingPlatform()
        val duplicate = assertFailsWith<DuplicateWeftPluginException> {
            Weft {
                install(WeftLive) { this.platform = platform }
                install(WeftLive) { this.platform = platform }
            }
        }
        assertEquals(LiveActivityPlugin::class, duplicate.type)
    }

    @Test
    fun platformOwnsUnknownActivityIdentityFailures() {
        val platform = RecordingPlatform().apply {
            failure = LiveActivityException(LiveActivityFailureReason.InvalidState, "unknown activity")
        }
        val plugin = install(platform)

        val error = assertFailsWith<LiveActivityException> {
            runSuspend { plugin.update(LiveActivity("unknown", "Update")) }
        }

        assertEquals(LiveActivityFailureReason.InvalidState, error.reason)
        assertEquals("unknown activity", error.message)
    }

    @Test
    fun serializesOverlappingLifecycleCallsWithMutex() {
        val platform = GatePlatform()
        val plugin = install(platform)
        val firstCompletion = Completion<Unit>()
        val secondCompletion = Completion<Unit>()

        suspend { plugin.start(LiveActivity("first", "First")) }.startCoroutine(firstCompletion)
        assertTrue(platform.firstEntered)

        suspend { plugin.start(LiveActivity("second", "Second")) }.startCoroutine(secondCompletion)
        assertFalse(platform.secondEntered)

        platform.releaseFirst()

        assertTrue(platform.secondEntered)
        assertTrue(firstCompletion.result?.isSuccess == true)
        assertTrue(secondCompletion.result?.isSuccess == true)
    }

    private fun install(platform: LiveActivityPlatform): LiveActivityPlugin = Weft {
        install(WeftLive) { this.platform = platform }
    }.get()

    private fun assertFailure(reason: LiveActivityFailureReason, operation: suspend () -> Unit) {
        val error = assertFailsWith<LiveActivityException> { runSuspend(operation) }
        assertEquals(reason, error.reason)
    }

    private class RecordingPlatform(
        private val availability: LiveActivityAvailability = LiveActivityAvailability.Available,
    ) : LiveActivityPlatform {
        val operations = mutableListOf<String>()
        var availabilityCalls: Int = 0
        var failure: Throwable? = null

        override fun availability(): LiveActivityAvailability {
            availabilityCalls += 1
            return availability
        }

        override suspend fun start(activity: LiveActivity) {
            failure?.let { throw it }
            operations += "start:${activity.id}"
        }

        override suspend fun update(activity: LiveActivity) {
            failure?.let { throw it }
            operations += "update:${activity.id}"
        }

        override suspend fun end(activity: LiveActivity) {
            failure?.let { throw it }
            operations += "end:${activity.id}"
        }
    }

    private class GatePlatform : LiveActivityPlatform {
        var firstEntered: Boolean = false
        var secondEntered: Boolean = false
        private var firstRelease: Continuation<Unit>? = null

        override fun availability(): LiveActivityAvailability = LiveActivityAvailability.Available

        override suspend fun start(activity: LiveActivity) {
            if (activity.id == "first") {
                firstEntered = true
                suspendCoroutine { continuation -> firstRelease = continuation }
            } else {
                secondEntered = true
            }
        }

        override suspend fun update(activity: LiveActivity): Unit = Unit

        override suspend fun end(activity: LiveActivity): Unit = Unit

        fun releaseFirst(): Unit = firstRelease!!.resume(Unit)
    }
}

private class Completion<T> : Continuation<T> {
    override val context = EmptyCoroutineContext
    var result: Result<T>? = null

    override fun resumeWith(result: Result<T>) {
        this.result = result
    }
}

private fun runSuspend(block: suspend () -> Unit) {
    var outcome: Result<Unit>? = null
    block.startCoroutine(object : Continuation<Unit> {
        override val context = EmptyCoroutineContext

        override fun resumeWith(result: Result<Unit>) {
            outcome = result
        }
    })
    checkNotNull(outcome).getOrThrow()
}
