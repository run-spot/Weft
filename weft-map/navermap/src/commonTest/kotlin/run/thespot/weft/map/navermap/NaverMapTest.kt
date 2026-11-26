package run.thespot.weft.map.navermap

import run.thespot.weft.core.map.MapPlugin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class NaverMapTest {
    @Test
    fun registersUnderMapPluginType() {
        val plugin = NaverMap.prepare { ncpKeyId = "test-ncp-key-id" }

        assertIs<MapPlugin>(plugin)
        assertEquals(MapPlugin::class, NaverMap.type)
    }

    @Test
    fun rejectsBlankNcpKeyId() {
        val error = assertFailsWith<IllegalArgumentException> {
            NaverMap.prepare { ncpKeyId = " \t\n" }
        }

        assertEquals("NAVER Maps NCP key ID must not be blank.", error.message)
    }

    @Test
    fun keepsPluginNameStable() {
        assertEquals("NaverMap", NaverMap.pluginName)
    }
}
