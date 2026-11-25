package run.thespot.weft.map.kakaomap

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import run.thespot.weft.core.map.MapContent
import run.thespot.weft.core.map.WeftMapState
import swiftPMImport.run.thespot.weft.weft.map.kakaomap.KMViewContainer

@Composable
internal actual fun KakaoMapPlatformSurface(
    nativeAppKey: String,
    state: WeftMapState,
    content: MapContent,
    modifier: Modifier,
    onError: ((Throwable) -> Unit)?,
) {
    key(nativeAppKey) {
        val surface = remember {
            KakaoMapIosSurface(
                appKey = nativeAppKey,
                initialState = state,
                initialContent = content,
                initialOnError = onError,
            )
        }
        DisposableEffect(surface) {
            onDispose(surface::dispose)
        }

        UIKitView(
            factory = { surface },
            modifier = modifier,
            update = { view -> view.update(state, content, onError) },
            onRelease = { view -> view.dispose() },
        )
    }
}

@OptIn(ExperimentalForeignApi::class)
private class KakaoMapIosSurface(
    private val appKey: String,
    initialState: WeftMapState,
    initialContent: MapContent,
    initialOnError: ((Throwable) -> Unit)?,
) : KMViewContainer(frame = CGRectMake(0.0, 0.0, 0.0, 0.0)) {
    private val host = KakaoMapIosMapViewHost(this)
    private val coordinator = KakaoMapIosCoordinator(
        host = host,
        appKey = appKey,
        initialState = initialState,
        initialContent = initialContent,
        onError = initialOnError,
    )

    override fun didMoveToWindow() {
        super.didMoveToWindow()
        if (window != null) {
            val size = frame.useContents { size }
            coordinator.onWindowAttached(width = size.width, height = size.height)
            host.onLayout()
        } else {
            coordinator.onWindowDetached()
        }
    }

    override fun layoutSubviews() {
        super.layoutSubviews()
        val size = frame.useContents { size }
        coordinator.onLayout(width = size.width, height = size.height)
        host.onLayout()
    }

    fun update(state: WeftMapState, content: MapContent, onError: ((Throwable) -> Unit)?) {
        coordinator.update(state, content, onError)
    }

    fun dispose() {
        coordinator.reset()
    }
}
