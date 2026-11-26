package run.thespot.weft.live

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LiveActivityTest {
    @Test
    fun progressValidatesBoundsAndCalculatesOverflowSafePercentage() {
        assertEquals(0, LiveActivityProgress(0, 10).percentage)
        assertEquals(33, LiveActivityProgress(1, 3).percentage)
        assertEquals(66, LiveActivityProgress(2, 3).percentage)
        assertEquals(100, LiveActivityProgress(10, 10).percentage)
        assertEquals(99, LiveActivityProgress(Long.MAX_VALUE - 1, Long.MAX_VALUE).percentage)

        assertFailsWith<IllegalArgumentException> { LiveActivityProgress(0, 0) }
        assertFailsWith<IllegalArgumentException> { LiveActivityProgress(-1, 10) }
        assertFailsWith<IllegalArgumentException> { LiveActivityProgress(11, 10) }
    }

    @Test
    fun activityValidatesIdentityTextLinksAndTimes() {
        val activity = LiveActivity(
            id = "order:2026-11-26.1",
            title = "Preparing order",
            subtitle = "Almost ready",
            deepLink = "weft://orders/2026-11-26.1?source=live#status",
            startedAtEpochMillis = 10,
            endsAtEpochMillis = 20,
        )

        assertEquals(LiveActivityStatus.Active, activity.status)

        assertFailsWith<IllegalArgumentException> { LiveActivity("", "title") }
        assertFailsWith<IllegalArgumentException> { LiveActivity("contains space", "title") }
        assertFailsWith<IllegalArgumentException> { LiveActivity("id", " ") }
        assertFailsWith<IllegalArgumentException> { LiveActivity("id", "가".repeat(43)) }
        assertFailsWith<IllegalArgumentException> {
            LiveActivity("id", "title", deepLink = "weft://orders/%2")
        }
        assertFailsWith<IllegalArgumentException> {
            LiveActivity("id", "title", deepLink = "weft:/missing-authority")
        }
        assertFailsWith<IllegalArgumentException> {
            LiveActivity("id", "title", deepLink = "weft://orders/has\\backslash")
        }
        assertFailsWith<IllegalArgumentException> {
            LiveActivity("id", "title", startedAtEpochMillis = 20, endsAtEpochMillis = 19)
        }
        assertFailsWith<IllegalArgumentException> {
            LiveActivity("id", "title", startedAtEpochMillis = -1)
        }
        assertFailsWith<IllegalArgumentException> {
            LiveActivity("id", "title", status = LiveActivityStatus.Completed, progress = LiveActivityProgress(1, 2))
        }
    }

    @Test
    fun terminalStatesAcceptOnlyTheirDefinedProgressSemantics() {
        val completed = LiveActivity(
            id = "completed",
            title = "Done",
            status = LiveActivityStatus.Completed,
            progress = LiveActivityProgress(5, 5),
        )
        val failed = LiveActivity(
            id = "failed",
            title = "Failed",
            status = LiveActivityStatus.Failed,
            progress = LiveActivityProgress(2, 5),
        )
        val cancelled = LiveActivity("cancelled", "Cancelled", status = LiveActivityStatus.Cancelled)

        assertEquals(100, completed.progress?.percentage)
        assertEquals(40, failed.progress?.percentage)
        assertEquals(LiveActivityStatus.Cancelled, cancelled.status)
    }
}
