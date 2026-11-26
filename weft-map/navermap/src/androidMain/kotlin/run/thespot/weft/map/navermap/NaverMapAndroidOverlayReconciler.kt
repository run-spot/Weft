package run.thespot.weft.map.navermap

import run.thespot.weft.core.map.MapCircle
import run.thespot.weft.core.map.MapContent
import run.thespot.weft.core.map.MapMarker
import run.thespot.weft.core.map.MapPolygon
import run.thespot.weft.core.map.MapPolyline

internal object NaverMapAndroidNativeIds {
    fun marker(id: String): String = "weft:marker:$id"
    fun polyline(id: String): String = "weft:polyline:$id"
    fun polygon(id: String): String = "weft:polygon:$id"
    fun circle(id: String): String = "weft:circle:$id"
}

internal class NaverMapAndroidNativeHandleRegistry<T>(
    private val detach: (T) -> Unit,
) {
    private val handles = linkedMapOf<String, T>()

    internal val ids: Set<String>
        get() = handles.keys.toSet()

    internal fun get(nativeId: String): T? = handles[nativeId]

    internal fun put(nativeId: String, handle: T) {
        handles[nativeId] = handle
    }

    internal fun remove(nativeId: String): T? = handles.remove(nativeId)

    internal fun retain(nativeIds: Set<String>) {
        handles.entries
            .filterNot { it.key in nativeIds }
            .toList()
            .forEach { entry ->
                detach(entry.value)
                handles.remove(entry.key)
            }
    }
}

internal interface NaverMapAndroidNativeMarker {
    val nativeId: String
}

internal interface NaverMapAndroidNativeShape {
    val nativeId: String
}

internal interface NaverMapAndroidMarkerLayer {
    fun add(marker: MapMarker): NaverMapAndroidNativeMarker

    fun update(native: NaverMapAndroidNativeMarker, marker: MapMarker)

    fun remove(native: NaverMapAndroidNativeMarker, nativeId: String)

    /** Rebuild the complete committed marker snapshot after a partial native mutation. */
    fun restore(markers: List<MapMarker>)
}

internal interface NaverMapAndroidShapeLayer {
    fun add(shape: NaverMapAndroidShape): NaverMapAndroidNativeShape

    fun update(native: NaverMapAndroidNativeShape, shape: NaverMapAndroidShape)

    fun remove(native: NaverMapAndroidNativeShape, nativeId: String)

    /** Rebuild the complete committed shape snapshot after a partial native mutation. */
    fun restore(shapes: List<NaverMapAndroidShape>)
}

internal object NaverMapAndroidEmptyMarkerLayer : NaverMapAndroidMarkerLayer {
    private object Handle : NaverMapAndroidNativeMarker {
        override val nativeId: String = "weft:marker:empty"
    }
    override fun add(marker: MapMarker): NaverMapAndroidNativeMarker = Handle
    override fun update(native: NaverMapAndroidNativeMarker, marker: MapMarker) = Unit
    override fun remove(native: NaverMapAndroidNativeMarker, markerId: String) = Unit
    override fun restore(markers: List<MapMarker>) = Unit
}

internal object NaverMapAndroidEmptyShapeLayer : NaverMapAndroidShapeLayer {
    private object Handle : NaverMapAndroidNativeShape {
        override val nativeId: String = "weft:shape:empty"
    }
    override fun add(shape: NaverMapAndroidShape): NaverMapAndroidNativeShape = Handle
    override fun update(native: NaverMapAndroidNativeShape, shape: NaverMapAndroidShape) = Unit
    override fun remove(native: NaverMapAndroidNativeShape, shapeId: String) = Unit
    override fun restore(shapes: List<NaverMapAndroidShape>) = Unit
}

internal sealed interface NaverMapAndroidShape {
    val id: String
    val nativeId: String
    val visible: Boolean
    val zIndex: Int

    data class Polyline(val value: MapPolyline, val style: NaverMapAndroidOverlayStyle) : NaverMapAndroidShape {
        override val id: String get() = value.id
        override val nativeId: String get() = NaverMapAndroidNativeIds.polyline(value.id)
        override val visible: Boolean get() = value.visible
        override val zIndex: Int get() = style.zIndex(value.zIndex)
    }

    data class Polygon(val value: MapPolygon, val style: NaverMapAndroidOverlayStyle) : NaverMapAndroidShape {
        override val id: String get() = value.id
        override val nativeId: String get() = NaverMapAndroidNativeIds.polygon(value.id)
        override val visible: Boolean get() = value.visible
        override val zIndex: Int get() = style.zIndex(value.zIndex)
    }

    data class Circle(val value: MapCircle, val style: NaverMapAndroidOverlayStyle) : NaverMapAndroidShape {
        override val id: String get() = value.id
        override val nativeId: String get() = NaverMapAndroidNativeIds.circle(value.id)
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
            restoreLayersOrThrow(next)
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
            val rollbackFailures = restoreLayers(previous)
            if (rollbackFailures.isNotEmpty()) {
                layerSynchronized = false
                rollbackFailures.forEach(failure::addSuppressedSafely)
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
            val rollbackFailures = restoreLayers(previous)
            if (rollbackFailures.isNotEmpty()) {
                layerSynchronized = false
                rollbackFailures.forEach(failure::addSuppressedSafely)
            } else {
                layerSynchronized = true
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
        val failures = restoreLayers(NaverMapAndroidOverlaySnapshot(emptyList(), emptyList()))
        markerHandles.clear()
        shapeHandles.clear()
        committed = NaverMapAndroidOverlaySnapshot(emptyList(), emptyList())
        failures.firstOrNull()?.let { primary ->
            failures.drop(1).forEach(primary::addSuppressedSafely)
            throw primary
        }
    }

    private fun reconcileMarkers(next: List<MapMarker>) {
        val nextById = next.associateBy { NaverMapAndroidNativeIds.marker(it.id) }
        next.forEach { marker ->
            val nativeId = NaverMapAndroidNativeIds.marker(marker.id)
            val native = markerHandles[nativeId]
            when {
                native == null -> markerHandles[nativeId] = markerLayer.add(marker)
                committed.markers.firstOrNull { NaverMapAndroidNativeIds.marker(it.id) == nativeId } != marker ->
                    markerLayer.update(native, marker)
            }
        }
        committed.markers.map { NaverMapAndroidNativeIds.marker(it.id) }.filterNot(nextById::containsKey).forEach { nativeId ->
            markerHandles.remove(nativeId)?.let { markerLayer.remove(it, nativeId) }
        }
    }

    private fun reconcileShapes(next: List<NaverMapAndroidShape>) {
        val nextById = next.associateBy(NaverMapAndroidShape::nativeId)
        next.forEach { shape ->
            val native = shapeHandles[shape.nativeId]
            when {
                native == null -> shapeHandles[shape.nativeId] = shapeLayer.add(shape)
                committed.shapes.firstOrNull { it.nativeId == shape.nativeId } != shape -> shapeLayer.update(native, shape)
            }
        }
        committed.shapes.map(NaverMapAndroidShape::nativeId).filterNot(nextById::containsKey).forEach { nativeId ->
            shapeHandles.remove(nativeId)?.let { shapeLayer.remove(it, nativeId) }
        }
    }

    private fun restoreLayers(snapshot: NaverMapAndroidOverlaySnapshot): List<Throwable> {
        val failures = mutableListOf<Throwable>()
        try {
            markerLayer.restore(snapshot.markers)
        } catch (failure: Throwable) {
            failures += failure
        }
        try {
            shapeLayer.restore(snapshot.shapes)
        } catch (failure: Throwable) {
            failures += failure
        }
        return failures
    }

    private fun restoreLayersOrThrow(snapshot: NaverMapAndroidOverlaySnapshot) {
        val failures = restoreLayers(snapshot)
        failures.firstOrNull()?.let { primary ->
            failures.drop(1).forEach(primary::addSuppressedSafely)
            throw primary
        }
    }
}

private fun MapContent.toNaverSnapshot(style: NaverMapAndroidOverlayStyle): NaverMapAndroidOverlaySnapshot {
    val markers = markers.associateByStableId("marker") { NaverMapAndroidNativeIds.marker(it.id) }.values.toList()
    val shapes = buildList {
        polylines.forEach { add(NaverMapAndroidShape.Polyline(it, style)) }
        polygons.forEach { add(NaverMapAndroidShape.Polygon(it, style)) }
        circles.forEach { add(NaverMapAndroidShape.Circle(it, style)) }
    }.associateByStableId("shape") { it.nativeId }.values.toList()
    return NaverMapAndroidOverlaySnapshot(markers, shapes)
}

private fun <T> List<T>.associateByStableId(kind: String, id: (T) -> String): Map<String, T> = linkedMapOf<String, T>().also { result ->
    forEach { item ->
        require(result.put(id(item), item) == null) {
            "Map content contains duplicate $kind id '${id(item)}'."
        }
    }
}

internal fun Throwable.addSuppressedSafely(rollbackFailure: Throwable) {
    if (rollbackFailure !== this) addSuppressed(rollbackFailure)
}
