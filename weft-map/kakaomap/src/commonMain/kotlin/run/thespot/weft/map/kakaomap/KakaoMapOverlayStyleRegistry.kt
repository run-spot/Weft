package run.thespot.weft.map.kakaomap

import androidx.compose.ui.graphics.Color

internal data class KakaoMapOverlayStyle(
    val kind: KakaoMapOverlayKind,
    val fillColor: Color?,
    val strokeColor: Color,
    val strokeWidthDp: Float,
)

/** Caches provider style registrations by appearance, not geometry or visibility. */
internal class KakaoMapOverlayStyleRegistry<T>(
    private val idPrefix: String,
    private val create: (styleId: String, style: KakaoMapOverlayStyle) -> T,
) {
    private val styles = mutableMapOf<KakaoMapOverlayStyle, T>()
    private var nextStyleId = 0

    fun getOrCreate(shape: KakaoMapOverlayShape): T {
        val key = KakaoMapOverlayStyle(
            kind = shape.kind,
            fillColor = shape.fillColor,
            strokeColor = shape.strokeColor,
            strokeWidthDp = shape.strokeWidthDp,
        )
        return styles[key] ?: create("$idPrefix:${nextStyleId++}", key).also { styles[key] = it }
    }
}
