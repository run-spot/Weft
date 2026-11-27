package run.thespot.weft.map.kakaomap

import run.thespot.weft.core.DuplicateWeftPluginException
import run.thespot.weft.core.Weft
import run.thespot.weft.core.WeftPlugin
import run.thespot.weft.core.map.MapContentRenderer
import run.thespot.weft.core.map.MapPlugin
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class KakaoMapTest {
    @Test
    fun nativeAppKeyDefaultsToBlankAndBlankKeysAreRejected() {
        assertEquals("", KakaoMapConfig().nativeAppKey)

        assertFailsWith<IllegalArgumentException> {
            KakaoMap.prepare { nativeAppKey = " \t\n" }
        }
    }

    @Test
    fun preparedProviderUsesTheSharedMapTypeAndHasNoHealthProbes() {
        val plugin = KakaoMap.prepare { nativeAppKey = "test-app-key" }

        assertEquals(MapPlugin::class, KakaoMap.type)
        assertEquals("KakaoMap", KakaoMap.pluginName)
        assertTrue(KakaoMap.healthCheck(plugin).isEmpty())
    }

    @Test
    fun installedProviderResolvesAsMapPluginWithoutHealthChecks() {
        val weft = Weft {
            install(KakaoMap) { nativeAppKey = "test-app-key" }
        }

        assertIs<MapPlugin>(weft.get<MapPlugin>())
        assertTrue(weft.healthChecks().isEmpty())
    }

    @Test
    fun rejectsAnotherProviderForTheMapCapability() {
        val error = assertFailsWith<DuplicateWeftPluginException> {
            Weft {
                install(KakaoMap) { nativeAppKey = "test-app-key" }
                install(OtherMapProvider)
            }
        }

        assertEquals(MapPlugin::class, error.type)
        assertEquals("KakaoMap", error.existingPluginName)
        assertEquals("OtherMapProvider", error.newPluginName)
    }

    @Test
    fun exceptionPreservesTheProviderCause() {
        val cause = IllegalStateException("native failure")

        val error = KakaoMapException("Map initialization failed", cause)

        assertSame(cause, error.cause)
        assertIs<IllegalStateException>(error)
    }
}

private class OtherMapConfig

private object OtherMapProvider : WeftPlugin<OtherMapConfig, MapPlugin> {
    override val type: KClass<MapPlugin> = MapPlugin::class
    override val pluginName: String = "OtherMapProvider"

    override fun prepare(block: OtherMapConfig.() -> Unit): MapPlugin = object : MapPlugin {
        override val renderer: MapContentRenderer = { _, _, _, _ -> }
    }

    override fun install(plugin: MapPlugin, scope: Weft) = Unit
}
