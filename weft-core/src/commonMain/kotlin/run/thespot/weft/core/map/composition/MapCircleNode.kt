package run.thespot.weft.core.map.composition

import run.thespot.weft.core.map.MapCircle

internal class MapCircleNode(circle: MapCircle) : MapNode() {
    internal var circle: MapCircle = circle
        private set

    internal fun updateCircle(circle: MapCircle) {
        if (this.circle == circle) return

        this.circle = circle
        invalidate()
    }
}
