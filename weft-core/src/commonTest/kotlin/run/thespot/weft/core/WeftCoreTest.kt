package run.thespot.weft.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Behavior of the plugin installation core, exercised with fake capability plugins that follow the
 * shape real Weft libraries use: one shared capability key and several mutually exclusive
 * implementations of it.
 */
class WeftCoreTest {
    @Test
    fun createsRuntimeWithoutPlugins() {
        val weft = Weft { }

        assertNull(weft.pluginOrNull(MapCapabilityKey))
    }

    @Test
    fun installsPluginWithConfigurationAndResolvesItByCapabilityKey() {
        val weft = Weft {
            install(NaverMap) {
                applicationKey = "naver-app-key"
            }
        }

        val map = weft.plugin(MapCapabilityKey)
        assertEquals("naver", map.providerName)
        assertEquals("naver-app-key", map.applicationKey)
        assertSame(map, weft.plugin(NaverMap))
        assertSame(map, weft.pluginOrNull(NaverMap))
        assertSame(map, weft.pluginOrNull(MapCapabilityKey))
    }

    @Test
    fun usesConfigurationDefaultsWhenTheBlockIsOmitted() {
        val weft = Weft {
            install(KakaoMetaMap)
        }

        assertEquals("", weft.plugin(MapCapabilityKey).applicationKey)
    }

    @Test
    fun resolvesCapabilityToTheInstalledMapImplementation() {
        val naver = Weft { install(NaverMap) }
        val kakao = Weft { install(KakaoMetaMap) }
        val google = Weft { install(GoogleMap) }

        assertEquals("naver", naver.plugin(MapCapabilityKey).providerName)
        assertEquals("kakao", kakao.plugin(MapCapabilityKey).providerName)
        assertEquals("google", google.plugin(MapCapabilityKey).providerName)
        assertSame(naver.plugin(NaverMap), naver.plugin(MapCapabilityKey))
    }

    @Test
    fun rejectsSecondPluginInTheSameCapabilityFamily() {
        val error = assertFailsWith<DuplicateWeftPluginException> {
            Weft {
                install(KakaoMetaMap)
                install(NaverMap)
            }
        }

        assertEquals(MapCapabilityKey, error.key)
        assertEquals("weft.map", error.key.name)
        assertEquals("KakaoMetaMap", error.existingPluginName)
        assertEquals("NaverMap", error.newPluginName)
        assertIs<IllegalStateException>(error)

        val message = error.message.orEmpty()
        assertTrue(message.contains("weft.map"), message)
        assertTrue(message.contains("KakaoMetaMap"), message)
        assertTrue(message.contains("NaverMap"), message)
    }

    @Test
    fun rejectsSecondPluginEvenWhenTheCapabilityKeyInstanceDiffers() {
        // Each artifact declares its own key instance for the shared capability, so the duplicate
        // check must compare key names rather than object identity.
        val error = assertFailsWith<DuplicateWeftPluginException> {
            Weft {
                install(GoogleMap)
                install(KakaoMetaMap)
            }
        }

        assertEquals("GoogleMap", error.existingPluginName)
    }

    @Test
    fun identifiesKeysByCapabilityName() {
        assertEquals(WeftPluginKey<MapPlugin>("weft.map"), WeftPluginKey<MapPlugin>("weft.map"))
        assertEquals(
            WeftPluginKey<MapPlugin>("weft.map").hashCode(),
            WeftPluginKey<MapPlugin>("weft.map").hashCode(),
        )
        assertTrue(WeftPluginKey<MapPlugin>("weft.map") != WeftPluginKey<MapPlugin>("weft.login"))
    }

    @Test
    fun missingPluginFailsOnRequiredLookupAndReturnsNullOnOptionalLookup() {
        val weft = Weft {
            install(NaverMap)
        }

        assertNull(weft.pluginOrNull(GreetingPlugin))
        assertNull(weft.pluginOrNull(GreetingKey))

        val error = assertFailsWith<MissingWeftPluginException> {
            weft.plugin(GreetingPlugin)
        }
        assertEquals(GreetingKey, error.key)
        assertIs<IllegalStateException>(error)
        assertTrue(error.message.orEmpty().contains("weft.greeting"))
    }

    @Test
    fun installHookResolvesPluginsInstalledLaterInTheConfiguration() {
        val weft = Weft {
            install(GreetingPlugin) {
                greeting = "hi"
            }
            install(NaverMap)
        }

        val greeting = weft.plugin(GreetingPlugin)
        assertEquals("hi", greeting.text)
        assertEquals("naver", greeting.mapProviderName)
    }
}

// MARK: - Fake capability plugins

/**
 * One map capability key shared by every map implementation. A real library would publish this
 * constant, and each provider artifact would declare an equal key of its own.
 */
private val MapCapabilityKey: WeftPluginKey<MapPlugin> = WeftPluginKey("weft.map")

private val GreetingKey: WeftPluginKey<Greeting> = WeftPluginKey("weft.greeting")

private interface MapPlugin {
    val providerName: String
    val applicationKey: String
}

private class MapConfig {
    var applicationKey: String = ""
}

/** Shape of a map provider plugin: its own copy of the shared capability key. */
private abstract class MapProviderPlugin(
    override val pluginName: String,
    private val mapProvider: String,
) : WeftPlugin<MapConfig, MapPlugin> {
    override val key: WeftPluginKey<MapPlugin> = WeftPluginKey("weft.map")

    override fun prepare(block: MapConfig.() -> Unit): MapPlugin {
        val config = MapConfig().apply(block)
        val provider = mapProvider
        return object : MapPlugin {
            override val providerName: String = provider
            override val applicationKey: String = config.applicationKey
        }
    }

    override fun install(plugin: MapPlugin, scope: Weft) = Unit
}

private object NaverMap : MapProviderPlugin(pluginName = "NaverMap", mapProvider = "naver")

private object KakaoMetaMap : MapProviderPlugin(pluginName = "KakaoMetaMap", mapProvider = "kakao")

private object GoogleMap : MapProviderPlugin(pluginName = "GoogleMap", mapProvider = "google")

private class Greeting(val text: String) {
    var mapProviderName: String? = null
}

private class GreetingConfig {
    var greeting: String = "hello"
}

/** Fails unless the map capability was already resolvable when its install hook ran. */
private object GreetingPlugin : WeftPlugin<GreetingConfig, Greeting> {
    override val key: WeftPluginKey<Greeting> = GreetingKey

    override val pluginName: String = "Greeting"

    override fun prepare(block: GreetingConfig.() -> Unit): Greeting {
        val config = GreetingConfig().apply(block)
        return Greeting(config.greeting)
    }

    override fun install(plugin: Greeting, scope: Weft) {
        plugin.mapProviderName = scope.pluginOrNull(MapCapabilityKey)?.providerName
    }
}
