package meow.ancient.module.modules.render

import meow.ancient.client.extensions.RENDER
import meow.ancient.client.extensions.inGame
import meow.ancient.client.extensions.mc
import meow.ancient.client.util.other.color.Color4b
import meow.ancient.module.Module
import meow.ancient.module.modules.combat.HitAuraModule
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.minecraft.gizmos.GizmoStyle
import net.minecraft.gizmos.Gizmos

object TargetEspModule : Module(
    name = "TargetESP",
    category = RENDER,
    description = "Visual indicator on current aura target"
) {
    val fill = boolean("Fill", true)
    val color = color("Color", Color4b(255, 60, 60, 180))
    val fillColor = color("Fill Color", Color4b(255, 60, 60, 60))
    val lineWidth = number("Line Width", 2.0, 0.5, 5.0, 0.5, 1)

    private var registered = false

    override fun onEnable() {
        if (!registered) {
            LevelRenderEvents.BEFORE_GIZMOS.register { _ ->
                if (!isEnabled() || !inGame) return@register
                val target = HitAuraModule.currentTarget ?: return@register
                if (!target.isAlive) return@register

                val style = if (fill.value) {
                    GizmoStyle.strokeAndFill(color.value.argb, lineWidth.value.toFloat(), fillColor.value.argb)
                } else {
                    GizmoStyle.stroke(color.value.argb, lineWidth.value.toFloat())
                }

                Gizmos.cuboid(target.boundingBox, style)
            }
            registered = true
        }
    }
}
