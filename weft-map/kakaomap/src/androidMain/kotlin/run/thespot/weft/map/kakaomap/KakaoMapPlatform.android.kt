package run.thespot.weft.map.kakaomap

import android.content.Context
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kakao.vectormap.MapView
import run.thespot.weft.core.map.MapContent
import run.thespot.weft.core.map.WeftMapState

@Composable
internal actual fun KakaoMapPlatformSurface(
    nativeAppKey: String,
    state: WeftMapState,
    content: MapContent,
    modifier: Modifier,
    onError: ((Throwable) -> Unit)?,
) {
    val lifecycleOwner = LocalLifecycleOwner.current

    key(nativeAppKey, lifecycleOwner) {
        val surface = remember {
            KakaoMapAndroidSurface(
                appKey = nativeAppKey,
                initialState = state,
                initialContent = content,
                initialOnError = onError,
            )
        }

        DisposableEffect(lifecycleOwner, surface) {
            val observer = LifecycleEventObserver { _, event -> surface.onLifecycleEvent(event) }
            lifecycleOwner.lifecycle.addObserver(observer)
            surface.syncLifecycle(lifecycleOwner.lifecycle.currentState)

            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                surface.finish()
            }
        }

        AndroidView(
            factory = surface::createView,
            modifier = modifier,
            update = { surface.update(state, content, onError) },
            onRelease = { surface.finish() },
        )
    }
}

private class KakaoMapAndroidSurface(
    private val appKey: String,
    initialState: WeftMapState,
    initialContent: MapContent,
    initialOnError: ((Throwable) -> Unit)?,
) {
    private var currentState = initialState
    private var currentContent = initialContent
    private var currentOnError = initialOnError
    private var lifecycleResumed = false
    private var view: MapView? = null
    private var coordinator: KakaoMapAndroidCoordinator? = null

    fun createView(context: Context): MapView {
        check(view == null) { "Kakao Maps Android view was created more than once." }
        val mapView = MapView(context).apply {
            setFinishManually(true)
        }
        val mapCoordinator = KakaoMapAndroidCoordinator(
            host = KakaoMapAndroidMapViewHost(context, mapView),
            appKey = appKey,
            initialState = currentState,
            initialContent = currentContent,
            onError = currentOnError,
        )
        view = mapView
        coordinator = mapCoordinator
        mapView.addOnAttachStateChangeListener(
            object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(view: View) {
                    mapCoordinator.onAttach()
                    if (lifecycleResumed) mapCoordinator.onResume()
                }

                override fun onViewDetachedFromWindow(view: View) {
                    mapCoordinator.onDetach()
                }
            },
        )
        if (mapView.isAttachedToWindow) {
            mapCoordinator.onAttach()
            if (lifecycleResumed) mapCoordinator.onResume()
        }
        return mapView
    }

    fun update(
        state: WeftMapState,
        content: MapContent,
        onError: ((Throwable) -> Unit)?,
    ) {
        currentState = state
        currentContent = content
        currentOnError = onError
        coordinator?.update(state, content, onError)
    }

    fun syncLifecycle(state: Lifecycle.State) {
        setLifecycleResumed(state.isAtLeast(Lifecycle.State.RESUMED))
    }

    fun onLifecycleEvent(event: Lifecycle.Event) {
        when (event) {
            Lifecycle.Event.ON_RESUME -> setLifecycleResumed(true)
            Lifecycle.Event.ON_PAUSE,
            Lifecycle.Event.ON_STOP -> setLifecycleResumed(false)

            Lifecycle.Event.ON_DESTROY -> finish()
            else -> Unit
        }
    }

    fun finish() {
        coordinator?.onFinish()
        coordinator = null
        view = null
    }

    private fun setLifecycleResumed(resumed: Boolean) {
        lifecycleResumed = resumed
        val mapView = view ?: return
        val mapCoordinator = coordinator ?: return
        if (!mapView.isAttachedToWindow) return
        if (resumed) mapCoordinator.onResume() else mapCoordinator.onPause()
    }
}
