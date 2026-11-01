package meow.ancient.module.modules.render

import meow.ancient.client.extensions.RENDER
import meow.ancient.client.extensions.inGame
import meow.ancient.client.extensions.mc
import meow.ancient.client.gui.material.M3Theme
import meow.ancient.module.Module
import meow.ancient.module.modules.combat.HitAuraModule
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.minecraft.gizmos.GizmoStyle
import net.minecraft.gizmos.Gizmos
import net.minecraft.util.Mth
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.AABB
import kotlin.math.exp
object TargetEspModule : Module(
    name = "TargetESP",
    category = RENDER,
    description = "Visual indicator on current aura target"
) {
    val fill = boolean("Fill", true)
    val throughWalls = boolean("Through Walls", true)
    val lineWidth = number("Line Width", 2.0, 0.5, 5.0, 0.5, 1)
    val smooth = boolean("Smooth", true)
    val speed = number("Speed", 35.0, 10.0, 60.0, 5.0, 0, isVisible = { smooth.value })

    private var registered = false
    private var currentBox: AABB? = null
    private var lastTarget: LivingEntity? = null
    private var alpha = 0.0f
    private var lastFrameTime = 0L
    override fun onEnable() {
        if (!registered) {
            LevelRenderEvents.BEFORE_GIZMOS.register { _ ->
                if (!isEnabled() || !inGame) {
                    resetAnimation()
                    return@register
                }

                val now = System.nanoTime()
                val dt = if (lastFrameTime == 0L) 0.016f else ((now - lastFrameTime) / 1_000_000_000.0).toFloat().coerceIn(0.001f, 0.1f)
                lastFrameTime = now

                val target = HitAuraModule.currentTarget
                val activeTarget = if (target != null && target.isAlive) target else null

                if (activeTarget != null) {
                    alpha = (alpha + dt * 10f).coerceAtMost(1f)
                    val partialTicks = mc.deltaTracker.getGameTimeDeltaPartialTick(false)
                    val interpX = Mth.lerp(partialTicks.toDouble(), activeTarget.xo, activeTarget.x)
                    val interpY = Mth.lerp(partialTicks.toDouble(), activeTarget.yo, activeTarget.y)
                    val interpZ = Mth.lerp(partialTicks.toDouble(), activeTarget.zo, activeTarget.z)
                    val targetBox = activeTarget.boundingBox.move(
                        interpX - activeTarget.x,
                        interpY - activeTarget.y,
                        interpZ - activeTarget.z
                    )

                    val prev = currentBox
                    if (prev == null || !smooth.value) {
                        currentBox = targetBox
                    } else {
                        val distSq = prev.center.distanceToSqr(targetBox.center)
                        currentBox = if (distSq > 400.0) {
                            targetBox
                        } else {
                            val factor = (1.0 - exp((-dt * speed.value))).coerceIn(0.0, 1.0)
                            lerpBox(prev, targetBox, factor)
                        }
                    }
                    lastTarget = activeTarget
                } else {
                    alpha = (alpha - dt * 8f).coerceAtLeast(0f)
                    if (alpha <= 0.001f) {
                        resetAnimation()
                        return@register
                    }
                }

                val box = currentBox ?: return@register
                if (alpha <= 0.01f) return@register

                val palette = M3Theme.current
                val strokeAlpha = (180 * alpha).toInt().coerceIn(0, 255)
                val fillAlpha = (60 * alpha).toInt().coerceIn(0, 255)
                val strokeColor = palette.primary.alpha(strokeAlpha)
                val fillColor = palette.primary.alpha(fillAlpha)
                val style = if (fill.value) {
                    GizmoStyle.strokeAndFill(strokeColor.argb, lineWidth.value.toFloat(), fillColor.argb)
                } else {
                    GizmoStyle.stroke(strokeColor.argb, lineWidth.value.toFloat())
                }

                if (throughWalls.value) {
                    Gizmos.cuboid(box, style).setAlwaysOnTop()
                } else {
                    Gizmos.cuboid(box, style)
                }
            }
            registered = true
        }
    }

    override fun onDisable() {
        resetAnimation()
    }

    private fun resetAnimation() {
        currentBox = null
        lastTarget = null
        alpha = 0f
        lastFrameTime = 0L
    }

    private fun lerp(a: Double, b: Double, t: Double): Double = a + (b - a) * t

    private fun lerpBox(a: AABB, b: AABB, t: Double): AABB {
        return AABB(
            lerp(a.minX, b.minX, t),
            lerp(a.minY, b.minY, t),
            lerp(a.minZ, b.minZ, t),
            lerp(a.maxX, b.maxX, t),
            lerp(a.maxY, b.maxY, t),
            lerp(a.maxZ, b.maxZ, t)
        )
    }
}
