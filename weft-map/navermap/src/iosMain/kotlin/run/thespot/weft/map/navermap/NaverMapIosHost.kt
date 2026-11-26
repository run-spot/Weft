package run.thespot.weft.map.navermap

import run.thespot.weft.core.map.CameraPosition
import run.thespot.weft.core.map.MapContent

internal fun interface NaverMapIosNativeLayerFactory {
    fun create(): NaverMapIosNativeLayer
}

internal class NaverMapIosHostAdapter(
    private val configureAuth: (String) -> Unit,
    private val createLayer: NaverMapIosNativeLayerFactory,
) : NaverMapIosHost {
    private var initializedKey: String? = null
    private var layer: NaverMapIosNativeLayer? = null
    private var overlays: NaverMapIosOverlayReconciler? = null
    private var ready = false
    private var finished = false

    override fun prepare(
        ncpKeyId: String,
        initialCamera: CameraPosition,
        onReady: (NaverMapIosZoomBounds) -> Unit,
        onError: (Throwable) -> Unit,
    ) {
        check(!finished) { "Naver Maps iOS host has been reset." }
        val existingKey = initializedKey
        if (existingKey != null && existingKey != ncpKeyId) {
            val failure = runCatching {
                NaverMapIosSdkInitializer.initialize(ncpKeyId, configureAuth)
            }.exceptionOrNull() ?: NaverMapException("Naver Maps iOS SDK key configuration failed.")
            onError(failure)
            throw failure
        }
        if (ready) return

        try {
            NaverMapIosSdkInitializer.initialize(ncpKeyId, configureAuth)
            initializedKey = ncpKeyId
            val createdLayer = checkNotNull(layer ?: createLayer.create().also { layer = it })
            overlays = NaverMapIosOverlayReconciler(createdLayer)
            ready = true
            onReady(createdLayer.zoomBounds)
        } catch (failure: Throwable) {
            onError(failure)
            throw failure
        }
    }

    override fun activate() {
        checkNotNull(layer) { "Naver Maps iOS host is not prepared." }
    }

    override fun render(camera: CameraPosition?, content: MapContent) {
        val activeLayer = checkNotNull(layer) { "Naver Maps iOS host is not prepared." }
        val activeOverlays = checkNotNull(overlays) { "Naver Maps iOS overlays are not prepared." }
        val previousShapes = activeOverlays.snapshot
        val previousCamera = activeLayer.currentCamera
        try {
            activeOverlays.render(toNaverMapOverlayShapes(content))
            if (camera != null) activeLayer.moveCamera(camera)
        } catch (failure: Throwable) {
            try {
                activeOverlays.restore(previousShapes)
            } catch (rollbackFailure: Throwable) {
                if (failure !== rollbackFailure) failure.addSuppressed(rollbackFailure)
            }
            if (camera != null) {
                try {
                    activeLayer.moveCamera(previousCamera)
                } catch (rollbackFailure: Throwable) {
                    if (failure !== rollbackFailure) failure.addSuppressed(rollbackFailure)
                }
            }
            throw failure
        }
    }

    override fun pause() {
        check(!finished) { "Naver Maps iOS host has been reset." }
    }

    override fun reset() {
        if (finished) return
        finished = true
        val activeLayer = layer
        val activeOverlays = overlays
        runNaverMapHostTeardown(
            cleanupShapes = { activeOverlays?.clear() },
            finishEngine = { activeLayer?.removeDelegates() },
            clearReferences = {
                try {
                    activeLayer?.release()
                } finally {
                    layer = null
                    overlays = null
                    initializedKey = null
                    ready = false
                }
            },
        )
    }
}

internal object NaverMapIosSdkInitializer {
    private var initializedKey: String? = null

    fun initialize(ncpKeyId: String, initializeSdk: (String) -> Unit) {
        when (val existingKey = initializedKey) {
            null -> {
                initializeSdk(ncpKeyId)
                initializedKey = ncpKeyId
            }
            ncpKeyId -> Unit
            else -> throw NaverMapException(
                "Naver Maps iOS SDK is already initialized with a different NCP key ID.",
            )
        }
    }

    internal fun resetForTest() {
        initializedKey = null
    }
}
