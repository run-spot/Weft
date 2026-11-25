package run.thespot.weft.core

import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Behavior of the plugin installation core, exercised with fake capability plugins that follow the
 * shape real Weft libraries use: one shared capability interface and several mutually exclusive
 * implementations of it.
 */
class WeftCoreTest {
    @Test
    fun createsRuntimeWithoutPlugins() {
        val weft = Weft { }

        assertNull(weft.getOrNull<MapPlugin>())
        assertNull(weft.getOrNull(MapPlugin::class))
    }

    @Test
    fun installsPluginWithConfigurationAndResolvesItByCapabilityType() {
        val weft = Weft {
            install(NaverMap) {
                applicationKey = "naver-app-key"
            }
        }

        val map = weft.get<MapPlugin>()
        assertEquals("naver", map.providerName)
        assertEquals("naver-app-key", map.applicationKey)
        assertSame(map, weft.get(MapPlugin::class))
        assertSame(map, weft.getOrNull<MapPlugin>())
        assertSame(map, weft.getOrNull(MapPlugin::class))
    }

    @Test
    fun usesConfigurationDefaultsWhenTheBlockIsOmitted() {
        val weft = Weft {
            install(KakaoMetaMap)
        }

        assertEquals("", weft.get<MapPlugin>().applicationKey)
    }

    @Test
    fun resolvesCapabilityToTheInstalledMapImplementation() {
        val naver = Weft { install(NaverMap) }
        val kakao = Weft { install(KakaoMetaMap) }
        val google = Weft { install(GoogleMap) }

        assertEquals("naver", naver.get<MapPlugin>().providerName)
        assertEquals("kakao", kakao.get<MapPlugin>().providerName)
        assertEquals("google", google.get<MapPlugin>().providerName)
    }

    @Test
    fun rejectsSecondPluginWithTheSamePluginType() {
        val error = assertFailsWith<DuplicateWeftPluginException> {
            Weft {
                install(KakaoMetaMap)
                install(NaverMap)
            }
        }

        assertEquals(MapPlugin::class, error.type)
        assertEquals("KakaoMetaMap", error.existingPluginName)
        assertEquals("NaverMap", error.newPluginName)
        assertIs<IllegalStateException>(error)

        val message = error.message.orEmpty()
        assertTrue(message.contains("MapPlugin"), message)
        assertTrue(message.contains("KakaoMetaMap"), message)
        assertTrue(message.contains("NaverMap"), message)
    }

    @Test
    fun keepsDifferentPluginTypesInSeparateSlots() {
        val weft = Weft {
            install(NaverMap)
            install(GreetingPlugin) {
                greeting = "hi"
            }
        }

        assertEquals("naver", weft.get<MapPlugin>().providerName)
        assertEquals("hi", weft.get<Greeting>().text)
    }

    @Test
    fun missingPluginFailsOnRequiredLookupAndReturnsNullOnOptionalLookup() {
        val weft = Weft {
            install(NaverMap)
        }

        assertNull(weft.getOrNull<Greeting>())
        assertNull(weft.getOrNull(Greeting::class))

        val error = assertFailsWith<MissingWeftPluginException> {
            weft.get<Greeting>()
        }
        assertEquals(Greeting::class, error.type)
        assertIs<IllegalStateException>(error)
        assertTrue(error.message.orEmpty().contains("Greeting"))
    }

    @Test
    fun installHookResolvesPluginsInstalledLaterInTheConfiguration() {
        val weft = Weft {
            install(GreetingPlugin) {
                greeting = "hi"
            }
            install(NaverMap)
        }

        val greeting = weft.get<Greeting>()
        assertEquals("hi", greeting.text)
        assertEquals("naver", greeting.mapProviderName)
    }

    @Test
    fun collectsHealthChecksFromPluginsThatReportThem() {
        val weft = Weft {
            install(NaverMap) {
                applicationKey = "naver-app-key"
            }
            install(GreetingPlugin) {
                greeting = "hi"
            }
        }

        val checks = weft.healthChecks()
        assertEquals(1, checks.size)
        assertEquals("greeting", checks.single().name)
        assertEquals("https://example.invalid/greeting/health", checks.single().url)
        assertEquals("GET", checks.single().method)
        assertEquals("hi", checks.single().headers["X-Greeting"])
    }

    @Test
    fun runtimeWithoutHealthChecksReportsAnEmptyList() {
        val weft = Weft {
            install(NaverMap)
        }

        assertTrue(weft.healthChecks().isEmpty())
    }
}

// MARK: - Fake capability plugins

/**
 * One map capability type shared by every map implementation. A real library would publish this
 * interface, and each provider artifact would declare it as the type it installs under.
 */
private interface MapPlugin {
    val providerName: String
    val applicationKey: String
}

private class MapConfig {
    var applicationKey: String = ""
}

/** Shape of a map provider plugin: every provider declares the shared capability type. */
private abstract class MapProviderPlugin(
    override val pluginName: String,
    private val mapProvider: String,
) : WeftPlugin<MapConfig, MapPlugin> {
    override val type: KClass<MapPlugin> = MapPlugin::class

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
    override val type: KClass<Greeting> = Greeting::class

    override val pluginName: String = "Greeting"

    override fun prepare(block: GreetingConfig.() -> Unit): Greeting {
        val config = GreetingConfig().apply(block)
        return Greeting(config.greeting)
    }

    override fun install(plugin: Greeting, scope: Weft) {
        plugin.mapProviderName = scope.getOrNull<MapPlugin>()?.providerName
    }

    override fun healthCheck(plugin: Greeting): List<WeftHealthCheck> = listOf(
        WeftHealthCheck(
            name = "greeting",
            url = "https://example.invalid/greeting/health",
            headers = mapOf("X-Greeting" to plugin.text),
        ),
    )
}
