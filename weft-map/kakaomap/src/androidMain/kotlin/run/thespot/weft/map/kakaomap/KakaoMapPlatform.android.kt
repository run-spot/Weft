package run.thespot.weft.map.kakaomap

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import run.thespot.weft.core.map.MapContent
import run.thespot.weft.core.map.WeftMapState

@Suppress("UNUSED_PARAMETER")
@Composable
internal actual fun KakaoMapPlatformSurface(
    nativeAppKey: String,
    state: WeftMapState,
    content: MapContent,
    modifier: Modifier,
    onError: ((Throwable) -> Unit)?,
) {
    LaunchedEffect(onError) {
        onError?.invoke(KakaoMapException("The Kakao Maps Android surface is not available yet."))
    }
}
