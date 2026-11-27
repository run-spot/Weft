package run.thespot.weft.map.navermap

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class NaverMapHostTeardownTest {
    @Test
    fun runsAllTeardownActionsAfterFailure() {
        val calls = mutableListOf<String>()
        val cleanupFailure = IllegalStateException("shape cleanup failed")

        val failure = assertFailsWith<IllegalStateException> {
            runNaverMapHostTeardown(
                cleanupShapes = {
                    calls += "cleanup"
                    throw cleanupFailure
                },
                finishEngine = { calls += "finish" },
                clearReferences = { calls += "clear" },
            )
        }

        assertSame(cleanupFailure, failure)
        assertEquals(listOf("cleanup", "finish", "clear"), calls)
    }

    @Test
    fun suppressesLaterTeardownFailuresOnTheFirstFailure() {
        val cleanupFailure = IllegalStateException("shape cleanup failed")
        val finishFailure = IllegalArgumentException("engine finish failed")
        val clearFailure = IllegalStateException("reference clear failed")

        val failure = assertFailsWith<IllegalStateException> {
            runNaverMapHostTeardown(
                cleanupShapes = { throw cleanupFailure },
                finishEngine = { throw finishFailure },
                clearReferences = { throw clearFailure },
            )
        }

        assertSame(cleanupFailure, failure)
        assertEquals(listOf(finishFailure, clearFailure), failure.suppressedExceptions)
    }
}
