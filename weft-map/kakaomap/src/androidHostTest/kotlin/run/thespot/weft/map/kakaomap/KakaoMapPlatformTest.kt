package run.thespot.weft.map.kakaomap

import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composition
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import run.thespot.weft.core.map.CameraPosition
import run.thespot.weft.core.map.LatLng
import run.thespot.weft.core.map.MapScope
import run.thespot.weft.core.map.WeftMapState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class KakaoMapPlatformTest {
    @Test
    fun replacingErrorCallbackDoesNotReplayTheUnsupportedSurfaceFailure() {
        val initialErrors = mutableListOf<Throwable>()
        val replacementErrors = mutableListOf<Throwable>()
        val harness = KakaoMapContentHarness(
            onError = { initialErrors += it },
        )

        harness.use {
            assertEquals(1, initialErrors.size)
            assertIs<KakaoMapException>(initialErrors.single())

            harness.updateOnError { replacementErrors += it }

            assertEquals(1, initialErrors.size)
            assertTrue(replacementErrors.isEmpty())
        }
    }
}

private class KakaoMapContentHarness(
    onError: (Throwable) -> Unit,
) : AutoCloseable {
    private val frameClock = BroadcastFrameClock()
    private val context = Dispatchers.Unconfined + frameClock
    private val recomposer = Recomposer(context)
    private val scope = CoroutineScope(SupervisorJob() + context)
    private val recomposerJob = scope.launch { recomposer.runRecomposeAndApplyChanges() }
    private val composition = Composition(NoOpApplier, recomposer)
    private val currentOnError = mutableStateOf(onError)
    private var frameTimeNanos = 0L

    init {
        val plugin = KakaoMap.prepare { nativeAppKey = "test-app-key" }
        composition.setContent {
            plugin.Content(
                state = WeftMapState(CameraPosition(target = LatLng(0.0, 0.0))),
                modifier = Modifier,
                content = emptyMapContent,
                onError = currentOnError.value,
            )
        }
        recompose()
    }

    fun updateOnError(onError: (Throwable) -> Unit) {
        currentOnError.value = onError
        recompose()
    }

    private fun recompose() {
        Snapshot.sendApplyNotifications()
        repeat(3) {
            frameClock.sendFrame(++frameTimeNanos)
            Snapshot.sendApplyNotifications()
        }
    }

    override fun close() {
        composition.dispose()
        recomposerJob.cancel()
        recomposer.cancel()
        scope.cancel()
    }
}

private val emptyMapContent: @Composable MapScope.() -> Unit = {}

private object NoOpApplier : AbstractApplier<Unit>(Unit) {
    override fun insertTopDown(index: Int, instance: Unit) = Unit
    override fun insertBottomUp(index: Int, instance: Unit) = Unit
    override fun remove(index: Int, count: Int) = Unit
    override fun move(from: Int, to: Int, count: Int) = Unit
    override fun onClear() = Unit
}
