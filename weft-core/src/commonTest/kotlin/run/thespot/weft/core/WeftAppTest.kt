package run.thespot.weft.core

import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composition
import androidx.compose.runtime.Recomposer
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Behavior of [LocalWeft] and [WeftApp]: the runtime is reachable from composables below
 * [WeftApp], and reading it without a provider fails while the composition is created.
 *
 * The tests drive a headless composition (a [Composition] with a no-op applier) instead of a UI
 * toolkit, so they run in `commonTest` on every target without Material, UI, or a display.
 */
class WeftAppTest {
    @Test
    fun providesRuntimeToContent() {
        val weft = Weft {
            install(FakeMapProvider) {
                applicationKey = "fake-app-key"
            }
        }

        var observedWeft: Weft? = null
        var observedMap: FakeMapPlugin? = null
        compose {
            WeftApp(weft) {
                observedWeft = LocalWeft.current
                observedMap = LocalWeft.current.get<FakeMapPlugin>()
            }
        }

        assertSame(weft, observedWeft)
        assertEquals("fake", observedMap?.providerName)
        assertEquals("fake-app-key", observedMap?.applicationKey)
    }

    @Test
    fun readingLocalWeftWithoutWeftAppFails() {
        val thrown = assertFailsWith<IllegalStateException> {
            compose {
                LocalWeft.current
            }
        }

        val message = thrown.message.orEmpty()
        assertTrue(message.contains("Weft is not provided"), message)
        assertTrue(message.contains("WeftApp(weft)"), message)
    }

    @Test
    fun nestedWeftAppProvidesTheInnerRuntime() {
        val outer = Weft {
            install(FakeMapProvider) {
                applicationKey = "outer"
            }
        }
        val inner = Weft {
            install(FakeMapProvider) {
                applicationKey = "inner"
            }
        }

        var outerKey: String? = null
        var innerKey: String? = null
        compose {
            WeftApp(outer) {
                outerKey = LocalWeft.current.get<FakeMapPlugin>().applicationKey
                WeftApp(inner) {
                    innerKey = LocalWeft.current.get<FakeMapPlugin>().applicationKey
                }
            }
        }

        assertEquals("outer", outerKey)
        assertEquals("inner", innerKey)
    }
}

/**
 * Runs [content] in a headless composition and disposes it immediately: the assertions only need
 * the initial composition, so no recomposer loop or test frame clock is required.
 */
private fun compose(content: @Composable () -> Unit) {
    val recomposer = Recomposer(EmptyCoroutineContext)
    try {
        val composition = Composition(NoOpApplier, recomposer)
        composition.setContent(content)
        composition.dispose()
    } finally {
        recomposer.cancel()
    }
}

/** Discards every change: the tests assert on captured values, not on a rendered tree. */
private object NoOpApplier : AbstractApplier<Unit>(Unit) {
    override fun insertTopDown(index: Int, instance: Unit) = Unit

    override fun insertBottomUp(index: Int, instance: Unit) = Unit

    override fun remove(index: Int, count: Int) = Unit

    override fun move(from: Int, to: Int, count: Int) = Unit

    override fun onClear() = Unit
}

private interface FakeMapPlugin {
    val providerName: String
    val applicationKey: String
}

private class FakeMapConfig {
    var applicationKey: String = ""
}

private object FakeMapProvider : WeftPlugin<FakeMapConfig, FakeMapPlugin> {
    override val type: KClass<FakeMapPlugin> = FakeMapPlugin::class

    override val pluginName: String = "FakeMapProvider"

    override fun prepare(block: FakeMapConfig.() -> Unit): FakeMapPlugin {
        val config = FakeMapConfig().apply(block)
        return object : FakeMapPlugin {
            override val providerName: String = "fake"
            override val applicationKey: String = config.applicationKey
        }
    }

    override fun install(plugin: FakeMapPlugin, scope: Weft) = Unit
}
