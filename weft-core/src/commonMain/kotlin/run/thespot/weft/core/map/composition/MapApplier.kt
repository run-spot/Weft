package run.thespot.weft.core.map.composition

import androidx.compose.runtime.AbstractApplier

internal class MapApplier(
    private val mapRoot: MapRootNode,
    onInvalidate: () -> Unit,
) : AbstractApplier<MapNode>(mapRoot) {
    init {
        mapRoot.attach(onInvalidate)
    }

    override fun insertTopDown(index: Int, instance: MapNode) = Unit

    override fun insertBottomUp(index: Int, instance: MapNode) {
        current.insertChild(index, instance)
    }

    override fun remove(index: Int, count: Int) {
        current.removeChildren(index, count)
    }

    override fun move(from: Int, to: Int, count: Int) {
        current.moveChildren(from, to, count)
    }

    override fun onClear() {
        mapRoot.clearChildren()
    }
}
