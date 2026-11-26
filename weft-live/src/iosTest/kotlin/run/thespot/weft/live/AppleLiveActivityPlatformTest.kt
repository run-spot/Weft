package run.thespot.weft.live

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield

class AppleLiveActivityPlatformTest {
    @Test
    fun reportsUnsupportedBelowActivityKitMinimum() {
        val bridge = FakeAppleLiveActivityBridge(availabilityCode = "available")
        val platform = AppleLiveActivityPlatform(
            bridge = bridge,
            osVersion = AppleOperatingSystemVersion(16, 0, 0),
        )

        assertEquals(LiveActivityAvailability.Unsupported, platform.availability())
    }

    @Test
    fun mapsBridgeAvailability() {
        val bridge = FakeAppleLiveActivityBridge(availabilityCode = "disabled")
        val platform = AppleLiveActivityPlatform(
            bridge = bridge,
            osVersion = AppleOperatingSystemVersion(16, 1, 0),
        )

        assertEquals(LiveActivityAvailability.Disabled, platform.availability())
        bridge.availabilityCode = "available"
        assertEquals(LiveActivityAvailability.Available, platform.availability())
        bridge.availabilityCode = "unsupported"
        assertEquals(LiveActivityAvailability.Unsupported, platform.availability())
    }

    @Test
    fun translatesSnapshotsAndWaitsForNativeCompletion() = runBlocking {
        val bridge = FakeAppleLiveActivityBridge()
        val platform = AppleLiveActivityPlatform(
            bridge = bridge,
            osVersion = AppleOperatingSystemVersion(17, 0, 0),
        )
        val activity = LiveActivity(
            id = "order-1",
            title = "Preparing",
            subtitle = "Almost ready",
            progress = LiveActivityProgress(2, 4),
            startedAtEpochMillis = 100,
            endsAtEpochMillis = 200,
            deepLink = "weft://orders/order-1",
        )

        val operation = async { platform.start(activity) }
        yield()
        assertEquals(activity, bridge.lastActivity)
        bridge.completeNext(AppleLiveActivityResult(accepted = true))
        operation.await()

        assertEquals(activity, bridge.lastActivity)
    }

    @Test
    fun cancellingFirstWaiterDoesNotLetSecondNativeOperationOvertakeIt() = runBlocking {
        val bridge = FakeAppleLiveActivityBridge(serializeOperations = true)
        val platform = AppleLiveActivityPlatform(
            bridge = bridge,
            osVersion = AppleOperatingSystemVersion(17, 0, 0),
        )

        val first = async { platform.start(LiveActivity(id = "order-1", title = "First")) }
        yield()
        assertEquals(1, bridge.nativeStartCount)

        first.cancelAndJoin()
        val second = async { platform.update(LiveActivity(id = "order-1", title = "Second")) }
        yield()
        assertEquals(1, bridge.nativeStartCount)

        bridge.completeActive(AppleLiveActivityResult(accepted = true))
        assertEquals(2, bridge.nativeStartCount)
        bridge.completeActive(AppleLiveActivityResult(accepted = true))
        second.await()
    }

    @Test
    fun mapsNativeFailuresToTheCommonException() = runBlocking {
        val bridge = FakeAppleLiveActivityBridge(
            nextResult = AppleLiveActivityResult(
                accepted = false,
                failureCode = "payload_too_large",
                failureMessage = "too large",
            ),
        )
        val platform = AppleLiveActivityPlatform(
            bridge = bridge,
            osVersion = AppleOperatingSystemVersion(17, 0, 0),
        )

        val error = assertFailsWith<LiveActivityException> {
            platform.update(LiveActivity(id = "order-1", title = "Update"))
        }
        assertEquals(LiveActivityFailureReason.PlatformFailure, error.reason)
        assertEquals("too large", error.message)
    }

    @Test
    fun mapsUnsupportedAndMalformedNativeResultsToPlatformFailureCategories() = runBlocking {
        val bridge = FakeAppleLiveActivityBridge(
            nextResult = AppleLiveActivityResult(false, "unsupported", "unsupported"),
        )
        val platform = AppleLiveActivityPlatform(
            bridge = bridge,
            osVersion = AppleOperatingSystemVersion(17, 0, 0),
        )

        val unsupported = assertFailsWith<LiveActivityException> {
            platform.start(LiveActivity(id = "order-1", title = "Start"))
        }
        assertEquals(LiveActivityFailureReason.Unsupported, unsupported.reason)

        bridge.nextResult = AppleLiveActivityResult(accepted = false)
        val malformedRejected = assertFailsWith<LiveActivityException> {
            platform.start(LiveActivity(id = "order-1", title = "Start"))
        }
        assertEquals(LiveActivityFailureReason.PlatformFailure, malformedRejected.reason)

        bridge.nextResult = AppleLiveActivityResult(
            accepted = true,
            failureCode = "disabled",
            failureMessage = "inconsistent",
        )
        val malformedAccepted = assertFailsWith<LiveActivityException> {
            platform.start(LiveActivity(id = "order-1", title = "Start"))
        }
        assertEquals(LiveActivityFailureReason.PlatformFailure, malformedAccepted.reason)
    }

    @Test
    fun mapsKnownAvailabilityAndStateFailures() = runBlocking {
        val bridge = FakeAppleLiveActivityBridge(
            nextResult = AppleLiveActivityResult(false, "disabled", "disabled"),
        )
        val platform = AppleLiveActivityPlatform(
            bridge = bridge,
            osVersion = AppleOperatingSystemVersion(17, 0, 0),
        )

        val disabled = assertFailsWith<LiveActivityException> {
            platform.start(LiveActivity(id = "order-1", title = "Start"))
        }
        assertEquals(LiveActivityFailureReason.Disabled, disabled.reason)

        bridge.nextResult = AppleLiveActivityResult(false, "invalid_state", "bad state")
        val invalid = assertFailsWith<LiveActivityException> {
            platform.end(
                LiveActivity(
                    id = "order-1",
                    title = "Done",
                    status = LiveActivityStatus.Cancelled,
                ),
            )
        }
        assertEquals(LiveActivityFailureReason.InvalidState, invalid.reason)
    }

    private class FakeAppleLiveActivityBridge(
        var availabilityCode: String = "available",
        var nextResult: AppleLiveActivityResult? = null,
        private val serializeOperations: Boolean = false,
    ) : AppleLiveActivityBridge {
        var lastActivity: LiveActivity? = null
        private val completions = ArrayDeque<(AppleLiveActivityResult) -> Unit>()
        private val queuedOperations = ArrayDeque<Pair<LiveActivity, (AppleLiveActivityResult) -> Unit>>()
        private var activeCompletion: ((AppleLiveActivityResult) -> Unit)? = null
        var nativeStartCount = 0
            private set

        override fun availability(): String = availabilityCode

        override fun start(activity: LiveActivity, completion: (AppleLiveActivityResult) -> Unit) {
            enqueue(activity, completion)
        }

        override fun update(activity: LiveActivity, completion: (AppleLiveActivityResult) -> Unit) {
            enqueue(activity, completion)
        }

        override fun end(activity: LiveActivity, completion: (AppleLiveActivityResult) -> Unit) {
            enqueue(activity, completion)
        }

        fun completeNext(result: AppleLiveActivityResult) {
            completions.removeFirst().invoke(result)
        }

        fun completeActive(result: AppleLiveActivityResult) {
            val completion = checkNotNull(activeCompletion)
            activeCompletion = null
            completion(result)
            startNextOperation()
        }

        private fun enqueue(activity: LiveActivity, completion: (AppleLiveActivityResult) -> Unit) {
            if (serializeOperations) {
                queuedOperations += activity to completion
                startNextOperation()
            } else {
                lastActivity = activity
                completions += completion
                completeIfConfigured()
            }
        }

        private fun startNextOperation() {
            if (activeCompletion != null || queuedOperations.isEmpty()) return
            val (activity, completion) = queuedOperations.removeFirst()
            lastActivity = activity
            activeCompletion = completion
            nativeStartCount += 1
        }

        private fun completeIfConfigured() {
            nextResult?.let { completeNext(it) }
            nextResult = null
        }
    }
}
