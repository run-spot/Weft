package run.thespot.weft.map.navermap

import run.thespot.weft.core.map.MapCircle
import run.thespot.weft.core.map.MapContent
import run.thespot.weft.core.map.MapMarker
import run.thespot.weft.core.map.MapPolygon
import run.thespot.weft.core.map.MapPolyline

internal interface NaverMapAndroidNativeMarker

internal interface NaverMapAndroidNativeShape

internal interface NaverMapAndroidMarkerLayer {
    fun add(marker: MapMarker): NaverMapAndroidNativeMarker

    fun update(native: NaverMapAndroidNativeMarker, marker: MapMarker)

    fun remove(native: NaverMapAndroidNativeMarker, markerId: String)

    /** Rebuild the complete committed marker snapshot after a partial native mutation. */
    fun restore(markers: List<MapMarker>)
}

internal interface NaverMapAndroidShapeLayer {
    fun add(shape: NaverMapAndroidShape): NaverMapAndroidNativeShape

    fun update(native: NaverMapAndroidNativeShape, shape: NaverMapAndroidShape)

    fun remove(native: NaverMapAndroidNativeShape, shapeId: String)

    /** Rebuild the complete committed shape snapshot after a partial native mutation. */
    fun restore(shapes: List<NaverMapAndroidShape>)
}

internal object NaverMapAndroidEmptyMarkerLayer : NaverMapAndroidMarkerLayer {
    private object Handle : NaverMapAndroidNativeMarker
    override fun add(marker: MapMarker): NaverMapAndroidNativeMarker = Handle
    override fun update(native: NaverMapAndroidNativeMarker, marker: MapMarker) = Unit
    override fun remove(native: NaverMapAndroidNativeMarker, markerId: String) = Unit
    override fun restore(markers: List<MapMarker>) = Unit
}

internal object NaverMapAndroidEmptyShapeLayer : NaverMapAndroidShapeLayer {
    private object Handle : NaverMapAndroidNativeShape
    override fun add(shape: NaverMapAndroidShape): NaverMapAndroidNativeShape = Handle
    override fun update(native: NaverMapAndroidNativeShape, shape: NaverMapAndroidShape) = Unit
    override fun remove(native: NaverMapAndroidNativeShape, shapeId: String) = Unit
    override fun restore(shapes: List<NaverMapAndroidShape>) = Unit
}

internal sealed interface NaverMapAndroidShape {
    val id: String
    val visible: Boolean
    val zIndex: Int

    data class Polyline(val value: MapPolyline, val style: NaverMapAndroidOverlayStyle) : NaverMapAndroidShape {
        override val id: String get() = value.id
        override val visible: Boolean get() = value.visible
        override val zIndex: Int get() = style.zIndex(value.zIndex)
    }

    data class Polygon(val value: MapPolygon, val style: NaverMapAndroidOverlayStyle) : NaverMapAndroidShape {
        override val id: String get() = value.id
        override val visible: Boolean get() = value.visible
        override val zIndex: Int get() = style.zIndex(value.zIndex)
    }

    data class Circle(val value: MapCircle, val style: NaverMapAndroidOverlayStyle) : NaverMapAndroidShape {
        override val id: String get() = value.id
        override val visible: Boolean get() = value.visible
        override val zIndex: Int get() = style.zIndex(value.zIndex)
    }
}

internal data class NaverMapAndroidOverlaySnapshot(
    val markers: List<MapMarker>,
    val shapes: List<NaverMapAndroidShape>,
)

internal class NaverMapAndroidOverlayReconciler(
    private val markerLayer: NaverMapAndroidMarkerLayer,
    private val shapeLayer: NaverMapAndroidShapeLayer,
    private val style: NaverMapAndroidOverlayStyle,
) {
    private var markerHandles = linkedMapOf<String, NaverMapAndroidNativeMarker>()
    private var shapeHandles = linkedMapOf<String, NaverMapAndroidNativeShape>()
    private var committed = NaverMapAndroidOverlaySnapshot(emptyList(), emptyList())
    private var layerSynchronized = true

    internal val snapshot: NaverMapAndroidOverlaySnapshot
        get() = committed

    internal fun render(content: MapContent) {
        val next = content.toNaverSnapshot(style)
        if (!layerSynchronized) {
            restoreFull(next)
            committed = next
            layerSynchronized = true
            return
        }

        val previous = committed
        try {
            reconcileMarkers(next.markers)
            reconcileShapes(next.shapes)
            committed = next
        } catch (failure: Throwable) {
            try {
                restoreFull(previous)
            } catch (rollbackFailure: Throwable) {
                layerSynchronized = false
                failure.addSuppressedSafely(rollbackFailure)
            }
            throw failure
        }
    }

    /** Applies overlays before camera and restores both resources when camera mutation fails. */
    internal fun renderWithCamera(
        content: MapContent,
        applyCamera: () -> Unit,
        restoreCamera: () -> Unit,
    ) {
        val previous = committed
        render(content)
        try {
            applyCamera()
        } catch (failure: Throwable) {
            committed = previous
            try {
                restoreFull(previous)
                layerSynchronized = true
            } catch (rollbackFailure: Throwable) {
                layerSynchronized = false
                failure.addSuppressedSafely(rollbackFailure)
            }
            try {
                restoreCamera()
            } catch (rollbackFailure: Throwable) {
                failure.addSuppressedSafely(rollbackFailure)
            }
            throw failure
        }
    }

    internal fun clear() {
        var firstFailure: Throwable? = null
        try {
            markerLayer.restore(emptyList())
        } catch (failure: Throwable) {
            firstFailure = failure
        }
        try {
            shapeLayer.restore(emptyList())
        } catch (failure: Throwable) {
            val primary = firstFailure
            if (primary == null) firstFailure = failure else primary.addSuppressedSafely(failure)
        }
        markerHandles.clear()
        shapeHandles.clear()
        committed = NaverMapAndroidOverlaySnapshot(emptyList(), emptyList())
        firstFailure?.let { throw it }
    }

    private fun reconcileMarkers(next: List<MapMarker>) {
        val nextById = next.associateByStableId("marker")
        next.forEach { marker ->
            val native = markerHandles[marker.id]
            when {
                native == null -> markerHandles[marker.id] = markerLayer.add(marker)
                committed.markers.firstOrNull { it.id == marker.id } != marker -> markerLayer.update(native, marker)
            }
        }
        committed.markers.map(MapMarker::id).filterNot(nextById::containsKey).forEach { id ->
            markerHandles.remove(id)?.let { markerLayer.remove(it, id) }
        }
    }

    private fun reconcileShapes(next: List<NaverMapAndroidShape>) {
        val nextById = next.associateByStableId("shape")
        next.forEach { shape ->
            val native = shapeHandles[shape.id]
            when {
                native == null -> shapeHandles[shape.id] = shapeLayer.add(shape)
                committed.shapes.firstOrNull { it.id == shape.id } != shape -> shapeLayer.update(native, shape)
            }
        }
        committed.shapes.map(NaverMapAndroidShape::id).filterNot(nextById::containsKey).forEach { id ->
            shapeHandles.remove(id)?.let { shapeLayer.remove(it, id) }
        }
    }

    private fun restoreFull(snapshot: NaverMapAndroidOverlaySnapshot) {
        markerLayer.restore(snapshot.markers)
        shapeLayer.restore(snapshot.shapes)
    }
}

private fun MapContent.toNaverSnapshot(style: NaverMapAndroidOverlayStyle): NaverMapAndroidOverlaySnapshot {
    val markers = markers.associateByStableId("marker").values.toList()
    val shapes = buildList {
        polylines.forEach { add(NaverMapAndroidShape.Polyline(it, style)) }
        polygons.forEach { add(NaverMapAndroidShape.Polygon(it, style)) }
        circles.forEach { add(NaverMapAndroidShape.Circle(it, style)) }
    }.associateByStableId("shape").values.toList()
    return NaverMapAndroidOverlaySnapshot(markers, shapes)
}

private fun <T> List<T>.associateByStableId(kind: String, id: (T) -> String = { item ->
    when (item) {
        is MapMarker -> item.id
        is NaverMapAndroidShape -> item.id
        else -> error("Unsupported $kind overlay")
    }
}): Map<String, T> = linkedMapOf<String, T>().also { result ->
    forEach { item ->
        require(result.put(id(item), item) == null) {
            "Map content contains duplicate $kind id '${id(item)}'."
        }
    }
}

internal fun Throwable.addSuppressedSafely(rollbackFailure: Throwable) {
    if (rollbackFailure !== this) addSuppressed(rollbackFailure)
}
