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
        val emptyError = assertFailsWith<IllegalArgumentException> {
            NaverMap.prepare { ncpKeyId = "" }
        }
        assertEquals("NAVER Maps NCP key ID must not be blank.", emptyError.message)

        val whitespaceError = assertFailsWith<IllegalArgumentException> {
            NaverMap.prepare { ncpKeyId = " \t\n" }
        }

        assertEquals("NAVER Maps NCP key ID must not be blank.", whitespaceError.message)
    }

    @Test
    fun keepsPluginNameStable() {
        assertEquals("NaverMap", NaverMap.pluginName)
    }
}
