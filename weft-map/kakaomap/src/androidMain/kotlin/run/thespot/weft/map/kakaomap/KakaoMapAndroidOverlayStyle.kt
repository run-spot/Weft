package run.thespot.weft.map.kakaomap

import androidx.compose.ui.graphics.toArgb
import kotlin.math.roundToInt

internal data class KakaoMapAndroidOverlayStyle(
    val fillColorArgb: Int?,
    val strokeColorArgb: Int,
    val strokeWidthPx: Float,
)

internal fun KakaoMapOverlayStyle.toKakaoMapAndroidStyle(density: Float): KakaoMapAndroidOverlayStyle =
    KakaoMapAndroidOverlayStyle(
        fillColorArgb = fillColor?.toArgb(),
        strokeColorArgb = strokeColor.toArgb(),
        strokeWidthPx = strokeWidthDp * density,
    )

internal fun Float.toKakaoMapAndroidZOrder(): Int = roundToInt()
