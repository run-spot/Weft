package run.thespot.weft.map.kakaomap

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import run.thespot.weft.core.map.MapContent
import run.thespot.weft.core.map.WeftMapState

@Composable
internal expect fun KakaoMapPlatformSurface(
    nativeAppKey: String,
    state: WeftMapState,
    content: MapContent,
    modifier: Modifier,
    onError: ((Throwable) -> Unit)?,
)
