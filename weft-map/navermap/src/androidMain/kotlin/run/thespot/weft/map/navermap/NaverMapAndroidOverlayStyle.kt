package run.thespot.weft.map.navermap

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlin.math.roundToInt

/** Android-only style conversion shared by the provider-native overlay layers. */
internal data class NaverMapAndroidOverlayStyle(
    val density: Float,
) {
    init {
        require(density.isFinite() && density > 0f) { "Naver Maps display density must be positive." }
    }

    fun color(color: Color): Int = color.toArgb()

    fun strokeWidth(widthDp: Float): Int = (widthDp * density).roundToInt()

    fun zIndex(zIndex: Float): Int = zIndex.roundToInt()
}
