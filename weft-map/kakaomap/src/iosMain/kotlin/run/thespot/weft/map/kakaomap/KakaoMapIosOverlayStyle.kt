package run.thespot.weft.map.kakaomap

import androidx.compose.ui.graphics.Color
import kotlin.math.roundToInt

internal data class KakaoMapIosOverlayColor(
    val red: Double,
    val green: Double,
    val blue: Double,
    val alpha: Double,
)

internal fun Color.toKakaoMapIosOverlayColor(): KakaoMapIosOverlayColor =
    KakaoMapIosOverlayColor(
        red = red.toDouble(),
        green = green.toDouble(),
        blue = blue.toDouble(),
        alpha = alpha.toDouble(),
    )

internal fun Float.toKakaoMapIosStrokeWidth(): ULong = roundToInt().coerceAtLeast(0).toULong()

internal fun Float.toKakaoMapIosZOrder(): Int = roundToInt()
