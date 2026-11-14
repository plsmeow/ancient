package meow.ancient.client.util.render

import meow.ancient.client.extensions.mc
import meow.ancient.client.gui.material.M3Theme
import meow.ancient.client.util.other.color.Color4b
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.gizmos.GizmoStyle
import net.minecraft.gizmos.Gizmos
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import kotlin.math.exp
import kotlin.math.sin

/** One moving marker per aura, with smooth position, dimensions, colour and visibility. */
class AuraTargetRenderer(private val enabled: () -> Boolean, private val slideSpeed: () -> Double) {
    enum class Kind { PLACE, BASE, CHARGE, BREAK, MINE }
    private data class Target(val box: AABB, val kind: Kind, val progress: Float)
    private var target: Target? = null
    private var lastTarget: Target? = null
    private var level: ClientLevel? = null
    private var center: Vec3? = null
    private var trail: Vec3? = null
    private var halfSize = Vec3.ZERO
    private var color: Color4b? = null
    private var opacity = 0.0
    private var flash = 0.0
    private var lastFrame = 0L
    private var time = 0.0
    private var miningProgress = 0.0

    init { LevelRenderEvents.BEFORE_GIZMOS.register { _ -> draw() } }

    fun beginTick() { target = null }

    fun show(box: AABB, kind: Kind, progress: Float = 0f) {
        if (level !== mc.level) {
            reset()
            level = mc.level
        }
        if (kind == Kind.MINE && (lastTarget?.kind != Kind.MINE || lastTarget?.box != box
                || progress < (lastTarget?.progress ?: 0f))) miningProgress = 0.0
        target = Target(box, kind, progress.coerceIn(0f, 1f))
        lastTarget = target
    }

    fun pulse() { flash = 1.0 }

    fun reset() {
        target = null
        lastTarget = null
        level = null
        center = null
        trail = null
        color = null
        halfSize = Vec3.ZERO
        opacity = 0.0
        flash = 0.0
        lastFrame = 0L
        time = 0.0
        miningProgress = 0.0
    }

    private fun draw() {
        if (!enabled() || mc.level == null || level !== mc.level) { reset(); return }
        val desired = target ?: lastTarget ?: return
        val now = System.nanoTime()
        val dt = if (lastFrame == 0L) 1.0 / 60.0 else ((now - lastFrame) / 1.0E9).coerceIn(0.0, 0.05)
        lastFrame = now
        time += dt
        val movement = 1.0 - exp(-slideSpeed().coerceIn(5.0, 30.0) * dt)
        val fade = 1.0 - exp(-12.0 * dt)
        opacity += ((if (target == null) 0.0 else 1.0) - opacity) * fade
        flash *= exp(-7.0 * dt)
        if (opacity < 0.005 && target == null) {
            lastTarget = null
            center = null
            trail = null
            return
        }
        val desiredCenter = desired.box.center
        val desiredSize = Vec3(desired.box.xsize, desired.box.ysize, desired.box.zsize).scale(0.5)
        val previousCenter = center
        if (previousCenter == null || previousCenter.distanceToSqr(desiredCenter) > 32.0 * 32.0) {
            center = desiredCenter
            trail = desiredCenter
            halfSize = desiredSize
        } else {
            center = previousCenter.lerp(desiredCenter, movement)
            halfSize = halfSize.lerp(desiredSize, movement)
        }
        val currentCenter = center!!
        val lag = trail ?: currentCenter
        trail = lag.lerp(currentCenter, 1.0 - exp(-6.0 * dt))
        val palette = M3Theme.current
        val desiredColor = when (desired.kind) {
            Kind.PLACE -> palette.primary
            Kind.BASE -> palette.secondary
            Kind.CHARGE -> palette.tertiary
            Kind.BREAK -> palette.error
            Kind.MINE -> palette.tertiary
        }
        color = (color ?: desiredColor).interpolateTo(desiredColor, fade)
        val tint = color!!
        val breathe = sin(time * 3.2) * 0.5 + 0.5
        val scale = 0.92 + opacity * 0.08 + flash * 0.035
        val size = halfSize.scale(scale)
        val box = box(currentCenter, size).inflate(0.006)
        val outlineAlpha = ((175.0 + breathe * 35.0 + flash * 45.0) * opacity).toInt().coerceIn(0, 255)

        // Low-opacity fill and halo leave the entity and terrain readable.
        Gizmos.cuboid(box, GizmoStyle.strokeAndFill(tint.alpha(outlineAlpha).argb, 1f,
            tint.alpha(((18 + breathe * 7 + flash * 22) * opacity).toInt()).argb)).setAlwaysOnTop()
        Gizmos.cuboid(box.inflate(0.018 + breathe * 0.008),
            GizmoStyle.stroke(tint.alpha((20 * opacity).toInt()).argb, 2f)).setAlwaysOnTop()

        if (desired.kind == Kind.MINE) {
            miningProgress += (desired.progress - miningProgress) * (1.0 - exp(-20.0 * dt))
            val progressBox = miningBox(currentCenter, size, miningProgress)
            Gizmos.cuboid(progressBox, GizmoStyle.strokeAndFill(tint.alpha((210 * opacity).toInt()).argb, 1f,
                tint.alpha((100 * opacity).toInt()).argb)).setAlwaysOnTop()
        }

        val tail = trail!!
        if (tail.distanceToSqr(currentCenter) > 0.0025) {
            Gizmos.cuboid(box(tail, size), GizmoStyle.stroke(tint.alpha((28 * opacity).toInt()).argb, 0.75f)).setAlwaysOnTop()
        }
    }

    private fun box(center: Vec3, half: Vec3) = AABB(center.x - half.x, center.y - half.y, center.z - half.z,
        center.x + half.x, center.y + half.y, center.z + half.z)

    private fun miningBox(center: Vec3, half: Vec3, progress: Double) =
        box(center, half.scale(0.08 + progress.coerceIn(0.0, 1.0) * 0.92))

}
