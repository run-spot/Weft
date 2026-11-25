package run.thespot.weft.core.map

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composition
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Recomposer
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import run.thespot.weft.core.Weft
import run.thespot.weft.core.WeftApp
import run.thespot.weft.core.WeftPlugin
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class WeftMapAndroidTest {
    @Test
    fun delegatesStateFullSizeModifierContentAndErrorCallbackToInstalledPlugin() {
        val fakePlugin = RecordingMapPlugin()
        val weft = Weft { install(FakeMapProvider(fakePlugin)) }
        val state = WeftMapState(CameraPosition(target = LatLng(1.0, 2.0), zoom = 8f))
        val callerModifier = Modifier.padding(12.dp)
        val content: @Composable MapScope.() -> Unit = { Marker(MapMarker("id", LatLng(3.0, 4.0))) }
        val expectedError = IllegalStateException("expected")
        val onError: (Throwable) -> Unit = { assertSame(expectedError, it) }

        composeOnHost {
            WeftApp(weft) {
                WeftMap(
                    state = state,
                    modifier = callerModifier,
                    content = content,
                    onError = onError,
                )
            }
        }

        assertSame(state, fakePlugin.state)
        assertEquals(Modifier.fillMaxSize(), fakePlugin.modifier)
        assertSame(content, fakePlugin.content)
        assertSame(onError, fakePlugin.onError)
    }
}

private class RecordingMapPlugin : MapPlugin {
    var state: WeftMapState? = null
    var modifier: Modifier? = null
    var content: (@Composable MapScope.() -> Unit)? = null
    var onError: ((Throwable) -> Unit)? = null

    @Composable
    override fun Content(
        state: WeftMapState,
        modifier: Modifier,
        content: @Composable MapScope.() -> Unit,
        onError: ((Throwable) -> Unit)?,
    ) {
        this.state = state
        this.modifier = modifier
        this.content = content
        this.onError = onError
    }
}

private class FakeMapConfig

private class FakeMapProvider(
    private val plugin: RecordingMapPlugin,
) : WeftPlugin<FakeMapConfig, MapPlugin> {
    override val type: KClass<MapPlugin> = MapPlugin::class
    override val pluginName: String = "FakeMapProvider"
    override fun prepare(block: FakeMapConfig.() -> Unit): MapPlugin = plugin
    override fun install(plugin: MapPlugin, scope: Weft) = Unit
}

private fun composeOnHost(content: @Composable () -> Unit) {
    val recomposer = Recomposer(Dispatchers.Unconfined)
    val scope = CoroutineScope(Dispatchers.Unconfined)
    val recomposerJob = scope.launch { recomposer.runRecomposeAndApplyChanges() }
    val composition = Composition(NoOpUiApplier, recomposer)
    try {
        composition.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(1f),
                LocalLayoutDirection provides LayoutDirection.Ltr,
                LocalViewConfiguration provides TestViewConfiguration,
            ) {
                content()
            }
        }
    } finally {
        composition.dispose()
        recomposerJob.cancel()
        recomposer.cancel()
        scope.cancel()
    }
}

/** Composes Box's layout nodes without an Android owner or a rendered UI scene. */
private object NoOpUiApplier : AbstractApplier<Any>(Any()) {
    override fun insertTopDown(index: Int, instance: Any) = Unit
    override fun insertBottomUp(index: Int, instance: Any) = Unit
    override fun remove(index: Int, count: Int) = Unit
    override fun move(from: Int, to: Int, count: Int) = Unit
    override fun onClear() = Unit
}

private object TestViewConfiguration : ViewConfiguration {
    override val longPressTimeoutMillis: Long = 500L
    override val doubleTapMinTimeMillis: Long = 40L
    override val doubleTapTimeoutMillis: Long = 300L
    override val touchSlop: Float = 18f
}
