package run.thespot.weft.map.kakaomap

import run.thespot.weft.core.map.MapContent

internal interface KakaoMapAndroidShapeLayer {
    fun add(shape: KakaoMapOverlayShape)
    fun update(shape: KakaoMapOverlayShape)
    fun remove(nativeId: String)
    fun restore(shapes: List<KakaoMapOverlayShape>)
}

/** Applies full provider-neutral snapshots transactionally, retaining the last committed state. */
internal class KakaoMapAndroidShapeReconciler(
    private val layer: KakaoMapAndroidShapeLayer,
) {
    private var rendered: Map<String, KakaoMapOverlayShape> = emptyMap()
    private var layerSynchronized = true

    internal val snapshot: List<KakaoMapOverlayShape>
        get() = rendered.values.toList()

    fun render(content: MapContent) {
        val next = toKakaoMapOverlayShapes(content.polylines, content.polygons, content.circles)
            .associateByTo(linkedMapOf(), KakaoMapOverlayShape::nativeId)
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
                failure.addSuppressedSafely(rollbackFailure)
                layerSynchronized = false
            }
            throw failure
        }
    }

    fun restore(shapes: List<KakaoMapOverlayShape>) {
        val restored = shapes.associateByTo(linkedMapOf(), KakaoMapOverlayShape::nativeId)
        try {
            layer.restore(shapes)
        } catch (failure: Throwable) {
            layerSynchronized = false
            throw failure
        }
        rendered = restored
        layerSynchronized = true
    }
}
