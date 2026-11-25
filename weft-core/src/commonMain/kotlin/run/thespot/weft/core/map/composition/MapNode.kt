package run.thespot.weft.core.map.composition

internal open class MapNode {
    internal val children: MutableList<MapNode> = mutableListOf()

    private var invalidateCallback: (() -> Unit)? = null

    internal fun attach(onInvalidate: () -> Unit) {
        invalidateCallback = onInvalidate
        children.forEach { it.attach(onInvalidate) }
    }

    internal fun detach() {
        invalidateCallback = null
        children.forEach(MapNode::detach)
    }

    internal fun invalidate() {
        invalidateCallback?.invoke()
    }

    internal fun insertChild(index: Int, child: MapNode) {
        children.add(index, child)
        invalidateCallback?.let(child::attach)
    }

    internal fun removeChildren(index: Int, count: Int) {
        if (count == 0) return
        val removed = children.subList(index, index + count).toList()
        removed.forEach(MapNode::detach)
        children.subList(index, index + count).clear()
    }

    internal fun clearChildren() {
        if (children.isEmpty()) return
        children.forEach(MapNode::detach)
        children.clear()
    }

    internal fun moveChildren(from: Int, to: Int, count: Int) {
        if (count == 0) return

        val targetIndex = if (from > to) to else to - count
        if (count == 1 && (from == to + 1 || from == to - 1)) {
            val node = children[from]
            children[from] = children[to]
            children[to] = node
        } else {
            val moved = children.subList(from, from + count).toList()
            children.subList(from, from + count).clear()
            children.addAll(targetIndex, moved)
        }
    }
}

internal class MapRootNode : MapNode()
