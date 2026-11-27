package run.thespot.weft.map.navermap

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryOwner
import com.naver.maps.map.MapView
import com.naver.maps.map.NaverMapSdk
import com.naver.maps.map.OnMapReadyCallback
import run.thespot.weft.core.map.MapContent
import run.thespot.weft.core.map.WeftMapState
import java.util.UUID

internal const val NAVER_MAP_SAVED_STATE_KEY = "run.thespot.weft.map.navermap.MapView"

internal fun naverMapSavedStateKey(surfaceId: String): String =
    "$NAVER_MAP_SAVED_STATE_KEY.$surfaceId"

internal fun interface NaverMapAndroidMapViewFactory {
    fun create(context: Context): NaverMapAndroidViewLifecycle
}

internal interface NaverMapAndroidViewLifecycle {
    val view: View
    fun onCreate(bundle: Bundle)
    fun onStart()
    fun onResume()
    fun onPause()
    fun onStop()
    fun onDestroy()
    fun onLowMemory()
    fun onSaveInstanceState(bundle: Bundle)
    fun getMapAsync(callback: (NaverMapAndroidMapBridge) -> Unit)
}

internal interface NaverMapAndroidSavedStateBridge {
    fun consumeRestoredStateForKey(key: String): Bundle?
    fun registerSavedStateProvider(
        key: String,
        provider: () -> Bundle,
    ): NaverMapAndroidSavedStateRegistration?
}

internal fun interface NaverMapAndroidSavedStateRegistration {
    fun unregister()
}

internal interface NaverMapAndroidComponentCallbacksBridge {
    fun register(callback: () -> Unit)
    fun unregister(callback: () -> Unit)
}

internal class NaverMapAndroidLifecycleBridge(
    private val view: NaverMapAndroidViewLifecycle,
    private val savedState: NaverMapAndroidSavedStateBridge,
    private val componentCallbacks: NaverMapAndroidComponentCallbacksBridge,
    private val savedStateKey: String,
    private val onAttachCallback: () -> Unit = {},
    private val onDetachCallback: () -> Unit = {},
    private val onResumeCallback: () -> Unit = {},
    private val onPauseCallback: () -> Unit = {},
    private val onFinishCallback: (() -> Unit)? = null,
) {
    private var created = false
    private var disposed = false
    private var savedStateRegistration: NaverMapAndroidSavedStateRegistration? = null
    private var componentRegistered = false
    private var viewCreateStarted = false
    private var viewDestroyed = false
    private var started = false
    private var resumed = false
    private val lowMemoryCallback = { view.onLowMemory() }
    private val savedStateProvider = { Bundle().also(view::onSaveInstanceState) }

    fun create() {
        if (created || disposed) return
        viewCreateStarted = true
        try {
            view.onCreate(savedState.consumeRestoredStateForKey(savedStateKey) ?: Bundle())
            componentRegistered = true
            componentCallbacks.register(lowMemoryCallback)
            savedStateRegistration = savedState.registerSavedStateProvider(savedStateKey, savedStateProvider)
            created = true
        } catch (failure: Throwable) {
            runCatching { dispose() }
                .exceptionOrNull()
                ?.let(failure::addSuppressedSafely)
            throw failure
        }
    }

    fun onAttach() {
        if (disposed) return
        try {
            if (savedStateRegistration == null) {
                savedStateRegistration = savedState.registerSavedStateProvider(savedStateKey, savedStateProvider)
            }
            onAttachCallback()
        } catch (failure: Throwable) {
            runCatching { dispose() }
                .exceptionOrNull()
                ?.let(failure::addSuppressedSafely)
            throw failure
        }
    }

    fun onDetach() { if (!disposed) onDetachCallback() }
    fun onStart() {
        if (!disposed && !started) {
            started = true
            view.onStart()
        }
    }

    fun onResume() {
        if (!disposed && !resumed) {
            resumed = true
            view.onResume()
            onResumeCallback()
        }
    }

    fun onPause() {
        if (!disposed && resumed) {
            resumed = false
            var firstFailure: Throwable? = null
            try {
                view.onPause()
            } catch (failure: Throwable) {
                firstFailure = failure
            }
            try {
                onPauseCallback()
            } catch (failure: Throwable) {
                if (firstFailure == null) firstFailure = failure else firstFailure?.addSuppressedSafely(failure)
            }
            firstFailure?.let { throw it }
        }
    }

    fun onStop() {
        if (!disposed && started) {
            started = false
            view.onStop()
        }
    }

    fun dispose() {
        if (disposed) return
        disposed = true
        var firstFailure: Throwable? = null

        fun attempt(action: () -> Unit) {
            try {
                action()
            } catch (failure: Throwable) {
                val primary = firstFailure
                if (primary == null) firstFailure = failure else primary.addSuppressedSafely(failure)
            }
        }

        savedStateRegistration?.let { registration ->
            attempt { registration.unregister() }
        }
        savedStateRegistration = null
        if (componentRegistered) attempt { componentCallbacks.unregister(lowMemoryCallback) }
        componentRegistered = false
        if (resumed) {
            resumed = false
            attempt { view.onPause() }
            attempt { onPauseCallback() }
        }
        if (started) {
            started = false
            attempt { view.onStop() }
        }
        attempt {
            if (onFinishCallback == null) {
                if (!viewDestroyed) {
                    viewDestroyed = true
                    view.onDestroy()
                }
            } else {
                onFinishCallback.invoke()
            }
        }
        firstFailure?.let { throw it }
    }
}

private class ProductionNaverMapViewFactory : NaverMapAndroidMapViewFactory {
    override fun create(context: Context): NaverMapAndroidViewLifecycle = ProductionNaverMapViewLifecycle(MapView(context))
}

private class ProductionNaverMapViewLifecycle(private val mapView: MapView) : NaverMapAndroidViewLifecycle {
    override val view: View get() = mapView
    override fun onCreate(bundle: Bundle) = mapView.onCreate(bundle)
    override fun onStart() = mapView.onStart()
    override fun onResume() = mapView.onResume()
    override fun onPause() = mapView.onPause()
    override fun onStop() = mapView.onStop()
    override fun onDestroy() = mapView.onDestroy()
    @Suppress("DEPRECATION")
    override fun onLowMemory() = mapView.onLowMemory()
    override fun onSaveInstanceState(bundle: Bundle) = mapView.onSaveInstanceState(bundle)
    override fun getMapAsync(callback: (NaverMapAndroidMapBridge) -> Unit) {
        mapView.getMapAsync(OnMapReadyCallback { callback(NaverMapAndroidNaverMapBridge(it)) })
    }
}

private class ProductionSavedStateBridge(private val view: View) : NaverMapAndroidSavedStateBridge {
    private fun registry(): SavedStateRegistry? = viewTreeOwner()?.savedStateRegistry

    private fun viewTreeOwner(): SavedStateRegistryOwner? = runCatching {
        Class.forName("androidx.savedstate.ViewTreeSavedStateRegistryOwner")
            .getMethod("get", View::class.java)
            .invoke(null, view) as? SavedStateRegistryOwner
    }.getOrNull()
    override fun consumeRestoredStateForKey(key: String): Bundle? = registry()?.consumeRestoredStateForKey(key)
    override fun registerSavedStateProvider(
        key: String,
        provider: () -> Bundle,
    ): NaverMapAndroidSavedStateRegistration? {
        val registry = registry() ?: return null
        registry.registerSavedStateProvider(key, SavedStateRegistry.SavedStateProvider { provider() })
        return NaverMapAndroidSavedStateRegistration {
            registry.unregisterSavedStateProvider(key)
        }
    }
}

@Suppress("DEPRECATION")
private class ProductionComponentCallbacksBridge(private val context: Context) : NaverMapAndroidComponentCallbacksBridge {
    private val callbacks = linkedMapOf<() -> Unit, ComponentCallbacks2>()
    override fun register(callback: () -> Unit) {
        val component = object : ComponentCallbacks2 {
            override fun onConfigurationChanged(newConfig: Configuration) = Unit
            override fun onLowMemory() = callback()
            override fun onTrimMemory(level: Int) = Unit
        }
        callbacks[callback] = component
        context.registerComponentCallbacks(component)
    }
    override fun unregister(callback: () -> Unit) { callbacks.remove(callback)?.let(context::unregisterComponentCallbacks) }
}

@Composable
internal actual fun NaverMapPlatformSurface(
    ncpKeyId: String,
    state: WeftMapState,
    content: MapContent,
    modifier: Modifier,
    onError: ((Throwable) -> Unit)?,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val surfaceId = rememberSaveable { UUID.randomUUID().toString() }
    key(ncpKeyId, lifecycleOwner) {
        val surface = remember {
            NaverMapAndroidSurface(
                ncpKeyId,
                state,
                content,
                onError,
                savedStateKey = naverMapSavedStateKey(surfaceId),
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

internal class NaverMapAndroidSurface(
    private val ncpKeyId: String,
    initialState: WeftMapState,
    initialContent: MapContent,
    private val initialOnError: ((Throwable) -> Unit)?,
    private val mapViewFactory: NaverMapAndroidMapViewFactory = ProductionNaverMapViewFactory(),
    private val initializeSdk: ((Context, String) -> Unit)? = null,
    private val savedStateBridgeFactory: (View) -> NaverMapAndroidSavedStateBridge = { view ->
        ProductionSavedStateBridge(view)
    },
    private val componentCallbacksBridgeFactory: (Context) -> NaverMapAndroidComponentCallbacksBridge = { context ->
        ProductionComponentCallbacksBridge(context)
    },
    private val savedStateKey: String = naverMapSavedStateKey(UUID.randomUUID().toString()),
) {
    private var state = initialState
    private var content = initialContent
    private var finished = false
    private var bridge: NaverMapAndroidLifecycleBridge? = null
    private var coordinator: NaverMapAndroidCoordinator? = null
    private var currentOnError: ((Throwable) -> Unit)? = initialOnError
    private var lifecycleState: Lifecycle.State = Lifecycle.State.INITIALIZED

    fun createView(context: Context): View {
        check(!finished) { "Naver Maps Android surface is finished." }
        return try {
            createViewOrThrow(context)
        } catch (_: Throwable) {
            finished = true
            initialOnError?.invoke(
                NaverMapException(
                    "Naver Maps Android surface initialization failed.",
                ),
            )
            View(context)
        }
    }

    private fun createViewOrThrow(context: Context): View {
        val host = NaverMapAndroidMapViewHost(
            context = context,
            mapViewFactory = mapViewFactory,
            initializeSdk = { key ->
                initializeSdk?.invoke(context, key)
                    ?: NaverMapSdk.getInstance(context).setClient(NaverMapSdk.NcpKeyClient(key))
            },
            density = { context.resources.displayMetrics.density },
        )
        val lifecycle = host.createLifecycle(ncpKeyId)
        val coordinator = NaverMapAndroidCoordinator(host, ncpKeyId, state, content, initialOnError)
        val lifecycleBridge = NaverMapAndroidLifecycleBridge(
            lifecycle,
            savedStateBridgeFactory(lifecycle.view),
            componentCallbacksBridgeFactory(context),
            savedStateKey,
            onAttachCallback = coordinator::onAttach,
            onDetachCallback = coordinator::onDetach,
            onResumeCallback = coordinator::onResume,
            onPauseCallback = coordinator::onPause,
            onFinishCallback = coordinator::onFinish,
        )
        return try {
            lifecycleBridge.create()
            host.bindLifecycle(lifecycle)
            lifecycle.view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(view: View) {
                    runCatching {
                        lifecycleBridge.onAttach()
                        replayLifecycle(lifecycleBridge)
                    }.onFailure(::handleLifecycleFailure)
                }

                override fun onViewDetachedFromWindow(view: View) {
                    runCatching { lifecycleBridge.onDetach() }
                        .onFailure(::handleLifecycleFailure)
                }
            })
            this.bridge = lifecycleBridge
            this.coordinator = coordinator
            replayLifecycle(lifecycleBridge)
            lifecycle.view
        } catch (failure: Throwable) {
            runCatching { lifecycleBridge.dispose() }
                .exceptionOrNull()
                ?.let(failure::addSuppressedSafely)
            throw failure
        }
    }

    fun update(state: WeftMapState, content: MapContent, onError: ((Throwable) -> Unit)?) {
        this.state = state
        this.content = content
        currentOnError = onError
        coordinator?.update(state, content, onError)
    }

    fun syncLifecycle(state: Lifecycle.State) {
        lifecycleState = state
        bridge?.let(::replayLifecycle)
    }

    fun onLifecycleEvent(event: Lifecycle.Event) {
        lifecycleState = when (event) {
            Lifecycle.Event.ON_START -> Lifecycle.State.STARTED
            Lifecycle.Event.ON_RESUME -> Lifecycle.State.RESUMED
            Lifecycle.Event.ON_PAUSE -> Lifecycle.State.STARTED
            Lifecycle.Event.ON_STOP -> Lifecycle.State.CREATED
            Lifecycle.Event.ON_DESTROY -> Lifecycle.State.DESTROYED
            else -> lifecycleState
        }
        runCatching {
            when (event) {
                Lifecycle.Event.ON_START -> bridge?.onStart()
                Lifecycle.Event.ON_RESUME -> bridge?.onResume()
                Lifecycle.Event.ON_PAUSE -> bridge?.onPause()
                Lifecycle.Event.ON_STOP -> bridge?.onStop()
                Lifecycle.Event.ON_DESTROY -> finish()
                else -> Unit
            }
        }.onFailure(::handleLifecycleFailure)
    }

    fun finish() {
        if (finished) return
        finished = true
        val bridgeToDispose = bridge
        val coordinatorToFinish = coordinator
        bridge = null
        coordinator = null
        var firstFailure: Throwable? = null

        fun attempt(action: () -> Unit) {
            try {
                action()
            } catch (failure: Throwable) {
                val primary = firstFailure
                if (primary == null) firstFailure = failure else primary.addSuppressedSafely(failure)
            }
        }

        attempt { bridgeToDispose?.dispose() }
        attempt { coordinatorToFinish?.onFinish() }
        firstFailure?.let { throw it }
    }

    private fun replayLifecycle(bridge: NaverMapAndroidLifecycleBridge) {
        if (lifecycleState.isAtLeast(Lifecycle.State.STARTED)) bridge.onStart()
        if (lifecycleState.isAtLeast(Lifecycle.State.RESUMED)) bridge.onResume() else bridge.onPause()
    }

    private fun handleLifecycleFailure(failure: Throwable) {
        val sanitized = NaverMapException("Naver Maps Android surface lifecycle operation failed.")
        runCatching { finish() }
            .exceptionOrNull()
            ?.let(sanitized::addSuppressedSafely)
        runCatching { currentOnError?.invoke(sanitized) }
    }
}
