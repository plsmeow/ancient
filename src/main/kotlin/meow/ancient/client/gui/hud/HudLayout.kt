package meow.ancient.client.gui.hud

import kotlin.math.abs

internal enum class HudAnchor(val fraction: Float) {
    START(0f), CENTER(0.5f), END(1f)
}

internal data class HudBounds(
    val element: HudElement,
    var x: Float,
    var y: Float,
    val width: Float,
    val height: Float
)

internal data class HudSnap(
    val position: Float,
    val target: Int,
    val ownAnchor: HudAnchor,
    val targetAnchor: HudAnchor,
    val gap: Float,
    val guide: Float
)

/** Geometry in scaled GUI pixels; independent of rendering and configuration. */
internal object HudLayout {
    const val SCREEN = -2
    const val NONE = -1
    const val GAP = 2.5f
    private const val SNAP_DISTANCE = 5f
    private const val SCREEN_MARGIN = 7f
    private const val EPSILON = 0.01f

    fun snap(
        bounds: HudBounds,
        horizontal: Boolean,
        screenSize: Float,
        targets: List<HudBounds>
    ): HudSnap? {
        val position = if (horizontal) bounds.x else bounds.y
        val size = if (horizontal) bounds.width else bounds.height
        val crossPosition = if (horizontal) bounds.y else bounds.x
        val crossSize = if (horizontal) bounds.height else bounds.width
        val maximum = (screenSize - size).coerceAtLeast(0f)
        var best: HudSnap? = null
        var distance = SNAP_DISTANCE + EPSILON
        fun consider(target: Int, own: HudAnchor, anchor: HudAnchor, coordinate: Float, gap: Float = 0f) {
            val candidate = coordinate + gap - size * own.fraction
            if (candidate < -EPSILON || candidate > maximum + EPSILON) return
            val delta = abs(candidate - position)
            if (delta >= distance || delta > SNAP_DISTANCE) return
            distance = delta
            best = HudSnap(candidate.coerceIn(0f, maximum), target, own, anchor, gap,
                if (target == SCREEN) coordinate + gap else coordinate)
        }
        for (anchor in HudAnchor.entries) {
            val coordinate = screenSize * anchor.fraction
            if (anchor != HudAnchor.CENTER) {
                consider(SCREEN, anchor, anchor, coordinate,
                    if (anchor == HudAnchor.START) SCREEN_MARGIN else -SCREEN_MARGIN)
            }
            consider(SCREEN, anchor, anchor, coordinate)
        }
        for (other in targets) {
            if (other.element == bounds.element) continue
            val otherPosition = if (horizontal) other.x else other.y
            val otherSize = if (horizontal) other.width else other.height
            for (anchor in HudAnchor.entries) {
                consider(other.element.ordinal, anchor, anchor, otherPosition + otherSize * anchor.fraction)
            }
            val otherCrossPosition = if (horizontal) other.y else other.x
            val otherCrossSize = if (horizontal) other.height else other.width
            if (crossPosition <= otherCrossPosition + otherCrossSize + SNAP_DISTANCE &&
                crossPosition + crossSize >= otherCrossPosition - SNAP_DISTANCE) {
                consider(other.element.ordinal, HudAnchor.START, HudAnchor.END, otherPosition + otherSize, GAP)
                consider(other.element.ordinal, HudAnchor.END, HudAnchor.START, otherPosition, -GAP)
            }
        }
        return best
    }

    /** Keep the requested row/column when possible, trying another free slot at screen edges. */
    fun avoidOverlap(bounds: HudBounds, obstacles: List<HudBounds>, screenWidth: Float, screenHeight: Float) {
        val maxX = (screenWidth - bounds.width).coerceAtLeast(0f)
        val maxY = (screenHeight - bounds.height).coerceAtLeast(0f)
        bounds.x = bounds.x.coerceIn(0f, maxX)
        bounds.y = bounds.y.coerceIn(0f, maxY)
        val collisions = obstacles.filter { overlaps(bounds, it) }
        if (collisions.isEmpty()) return
        val originalX = bounds.x
        val originalY = bounds.y
        val first = collisions.first()
        fun alignmentDistance(position: Float, size: Float, otherPosition: Float, otherSize: Float): Float =
            HudAnchor.entries.minOf { anchor ->
                abs(position + size * anchor.fraction - otherPosition - otherSize * anchor.fraction)
            } / (size + otherSize).coerceAtLeast(1f)
        val horizontalDistance = alignmentDistance(originalX, bounds.width, first.x, first.width)
        val verticalDistance = alignmentDistance(originalY, bounds.height, first.y, first.height)
        val preferHorizontal = horizontalDistance >= verticalDistance
        var bestX = originalX
        var bestY = originalY
        var bestScore = Float.POSITIVE_INFINITY
        fun consider(x: Float, y: Float) {
            if (x < -EPSILON || y < -EPSILON || x > maxX + EPSILON || y > maxY + EPSILON) return
            bounds.x = x.coerceIn(0f, maxX)
            bounds.y = y.coerceIn(0f, maxY)
            if (obstacles.any { overlaps(bounds, it) }) return
            val dx = abs(bounds.x - originalX)
            val dy = abs(bounds.y - originalY)
            val changedRowOrColumn = if (preferHorizontal) dy > EPSILON else dx > EPSILON
            val score = dx + dy + if (changedRowOrColumn) screenWidth + screenHeight else 0f
            if (score < bestScore) {
                bestScore = score
                bestX = bounds.x
                bestY = bounds.y
            }
        }
        val xs = ArrayList<Float>(obstacles.size * 2 + 2)
        val ys = ArrayList<Float>(obstacles.size * 2 + 2)
        xs.add(0f)
        xs.add(maxX)
        ys.add(0f)
        ys.add(maxY)
        for (other in obstacles) {
            xs.add(other.x - bounds.width - GAP)
            xs.add(other.x + other.width + GAP)
            ys.add(other.y - bounds.height - GAP)
            ys.add(other.y + other.height + GAP)
        }
        for (x in xs) consider(x, originalY)
        for (y in ys) consider(originalX, y)
        if (!bestScore.isFinite()) {
            for (x in xs) for (y in ys) consider(x, y)
        }
        bounds.x = bestX
        bounds.y = bestY
    }

    private fun overlaps(a: HudBounds, b: HudBounds): Boolean =
        a.x < b.x + b.width - EPSILON && a.x + a.width > b.x + EPSILON &&
            a.y < b.y + b.height - EPSILON && a.y + a.height > b.y + EPSILON
}
