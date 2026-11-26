package run.thespot.weft.map.kakaomap

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals

class KakaoMapAndroidOverlayStyleTest {
    @Test
    fun styleMappingConvertsDpAlphaAndZOrderDeterministically() {
        val translucent = Color(red = 1f, green = 0.5f, blue = 0.25f, alpha = 0.4f)
        val style = KakaoMapOverlayStyle(
            kind = KakaoMapOverlayKind.Polygon,
            fillColor = translucent,
            strokeColor = translucent,
            strokeWidthDp = 2.5f,
        )

        val mapped = style.toKakaoMapAndroidStyle(density = 2f)

        assertEquals(5f, mapped.strokeWidthPx)
        assertEquals(0x66ff8040, mapped.strokeColorArgb)
        assertEquals(0x66ff8040, mapped.fillColorArgb)
        assertEquals(3, 2.6f.toKakaoMapAndroidZOrder())
    }
}
