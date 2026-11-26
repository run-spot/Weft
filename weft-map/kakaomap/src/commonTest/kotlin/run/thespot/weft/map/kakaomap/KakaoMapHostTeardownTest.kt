package run.thespot.weft.map.kakaomap

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class KakaoMapHostTeardownTest {
    @Test
    fun engineFinishAndReferenceClearRunWhenShapeCleanupFails() {
        val calls = mutableListOf<String>()
        val cleanupFailure = IllegalStateException("shape cleanup failed")

        val failure = assertFailsWith<IllegalStateException> {
            runKakaoMapHostTeardown(
                cleanupShapes = {
                    calls += "shapes"
                    throw cleanupFailure
                },
                finishEngine = { calls += "finish" },
                clearReferences = { calls += "clear" },
            )
        }

        assertSame(cleanupFailure, failure)
        assertEquals(listOf("shapes", "finish", "clear"), calls)
    }

    @Test
    fun referenceClearRunsWhenBothNativeCleanupAndEngineFinishFail() {
        val calls = mutableListOf<String>()
        val cleanupFailure = IllegalStateException("shape cleanup failed")
        val finishFailure = IllegalArgumentException("engine finish failed")

        val failure = assertFailsWith<IllegalStateException> {
            runKakaoMapHostTeardown(
                cleanupShapes = {
                    calls += "shapes"
                    throw cleanupFailure
                },
                finishEngine = {
                    calls += "finish"
                    throw finishFailure
                },
                clearReferences = { calls += "clear" },
            )
        }

        assertSame(cleanupFailure, failure)
        assertEquals(listOf("shapes", "finish", "clear"), calls)
    }
}
