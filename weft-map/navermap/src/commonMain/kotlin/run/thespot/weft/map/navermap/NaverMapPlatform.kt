package run.thespot.weft.map.navermap

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import run.thespot.weft.core.map.MapContent
import run.thespot.weft.core.map.WeftMapState

@Composable
internal expect fun NaverMapPlatformSurface(
    ncpKeyId: String,
    state: WeftMapState,
    content: MapContent,
    modifier: Modifier,
    onError: ((Throwable) -> Unit)?,
)
