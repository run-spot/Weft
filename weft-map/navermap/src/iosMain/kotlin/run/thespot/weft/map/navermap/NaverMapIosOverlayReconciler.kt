package run.thespot.weft.map.navermap

/** Reconciles a complete immutable snapshot while retaining the last committed state on failure. */
internal class NaverMapIosOverlayReconciler(
    private val layer: NaverMapIosOverlayLayer,
) {
    private var rendered = linkedMapOf<String, NaverMapOverlayShape>()
    private var layerSynchronized = true

    internal val snapshot: List<NaverMapOverlayShape>
        get() = rendered.values.toList()

    fun render(shapes: List<NaverMapOverlayShape>) {
        val next = linkedMapOf<String, NaverMapOverlayShape>()
        shapes.forEach { shape ->
            check(next.put(shape.nativeId, shape) == null) {
                "Duplicate Naver Maps iOS native overlay ID."
            }
        }
        if (!layerSynchronized) {
            layer.restore(next.values.toList())
            rendered = next
            layerSynchronized = true
            return
        }

        val previous = snapshot
        try {
            next.forEach { (id, shape) ->
                when {
                    id !in rendered -> layer.add(shape)
                    rendered[id] != shape -> layer.update(shape)
                }
            }
            rendered.keys.filterNot(next::containsKey).forEach(layer::remove)
            rendered = next
        } catch (failure: Throwable) {
            try {
                layer.restore(previous)
            } catch (rollbackFailure: Throwable) {
                if (failure !== rollbackFailure) failure.addSuppressed(rollbackFailure)
                layerSynchronized = false
            }
            throw failure
        }
    }

    fun restore(shapes: List<NaverMapOverlayShape>) {
        val restored = linkedMapOf<String, NaverMapOverlayShape>()
        shapes.forEach { shape ->
            check(restored.put(shape.nativeId, shape) == null) {
                "Duplicate Naver Maps iOS native overlay ID."
            }
        }
        try {
            layer.restore(shapes)
        } catch (failure: Throwable) {
            layerSynchronized = false
            throw failure
        }
        rendered = restored
        layerSynchronized = true
    }

    fun clear() = restore(emptyList())
}
