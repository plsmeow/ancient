package meow.ancient.module.modules.movement

import meow.ancient.client.extensions.mc
import meow.ancient.client.util.other.color.Color4b
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos
import net.minecraft.gizmos.GizmoStyle
import net.minecraft.gizmos.Gizmos
import net.minecraft.world.phys.AABB

class MioScaffoldRenderer(
    private val enabled: () -> Boolean,
    private val lineWidth: () -> Double,
    private val fill: () -> Color4b,
    private val outline: () -> Color4b,
    private val fade: () -> Boolean,
    private val fadeTime: () -> Double,
    private val multi: () -> Boolean = { true }
) {
    private val positions = LongArray(49)
    private val boxes = arrayOfNulls<AABB>(49)
    private val holdUntil = LongArray(49)
    private var level: ClientLevel? = null

    init {
        LevelRenderEvents.BEFORE_GIZMOS.register { _ -> render() }
    }

    fun reset() {
        boxes.fill(null)
        level = null
    }

    fun beginTick() {
        if (!checkContext()) return
        prune(System.nanoTime(), fadeDuration())
    }

    fun show(pos: BlockPos) {
        if (!checkContext()) return
        val now = System.nanoTime()
        val packed = pos.asLong()
        val historical = multi()
        var slot = -1
        var oldest = 0
        var matchingBox: AABB? = null
        for (index in boxes.indices) {
            if (boxes[index] == null) {
                if (slot == -1) slot = index
            } else if (positions[index] == packed) {
                holdUntil[index] = now + HOLD_NANOS
                if (historical) return
                matchingBox = boxes[index]
            } else if (holdUntil[index] < holdUntil[oldest]) {
                oldest = index
            }
        }
        if (!historical) {
            val box = matchingBox ?: AABB(pos)
            boxes.fill(null)
            positions[0] = packed
            boxes[0] = box
            holdUntil[0] = now + HOLD_NANOS
            return
        }
        if (slot == -1) slot = oldest
        positions[slot] = packed
        boxes[slot] = AABB(pos)
        holdUntil[slot] = now + HOLD_NANOS
    }

    private fun render() {
        if (!checkContext()) return
        val now = System.nanoTime()
        val fading = fade()
        val duration = if (fading) fadeTime() * FADE_SCALE_NANOS else 0.0
        val fillColor = fill()
        val outlineColor = outline()
        val width = lineWidth().toFloat()
        var fullStyle: GizmoStyle? = null
        for (index in boxes.indices) {
            val box = boxes[index] ?: continue
            val elapsed = now - holdUntil[index]
            if (elapsed > 0L && elapsed >= duration) {
                boxes[index] = null
                continue
            }
            val alpha = if (fading && elapsed > 0L) (1.0 - elapsed / duration).toFloat() else 1.0f
            val style = if (alpha == 1.0f) {
                fullStyle ?: GizmoStyle.strokeAndFill(outlineColor.argb, width, fillColor.argb).also { fullStyle = it }
            } else {
                GizmoStyle.strokeAndFill(outlineColor.fade(alpha).argb, width, fillColor.fade(alpha).argb)
            }
            Gizmos.cuboid(box, style).setAlwaysOnTop()
        }
    }

    private fun checkContext(): Boolean {
        val currentLevel = mc.level
        if (!enabled() || mc.player == null || currentLevel == null) {
            reset()
            return false
        }
        if (level !== currentLevel) {
            reset()
            level = currentLevel
        }
        return true
    }

    private fun fadeDuration(): Double = if (fade()) fadeTime() * FADE_SCALE_NANOS else 0.0

    private fun prune(now: Long, duration: Double) {
        for (index in boxes.indices) {
            if (boxes[index] != null) {
                val elapsed = now - holdUntil[index]
                if (elapsed > 0L && elapsed >= duration) boxes[index] = null
            }
        }
    }

    private companion object {
        const val HOLD_NANOS = 150_000_000L
        const val FADE_SCALE_NANOS = 450_000_000.0
    }
}
