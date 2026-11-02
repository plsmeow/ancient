package meow.ancient.module.modules.render

import meow.ancient.client.extensions.RENDER
import meow.ancient.client.extensions.mc
import meow.ancient.client.gui.material.M3Theme
import meow.ancient.client.util.other.color.Color4b
import meow.ancient.module.Module
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.player.Player

object ChamsModule : Module(
    name = "Chams",
    category = RENDER,
    description = "Highlights entities with outlines"
) {
    val mode by mode("Mode", "Glow Outline", listOf("Glow Outline", "Tinted Glow"))
    val players = boolean("Players", true)
    val mobs = boolean("Mobs", false)
    val self by boolean("Self", false)
    val color = color("Color", Color4b(255, 60, 60, 255))
    val theme = boolean("Theme", true)


    @JvmStatic
    fun shouldRenderChams(entity: LivingEntity): Boolean {
        if (!isEnabled()) return false
        if (entity is Player) {
            if (entity === mc.player && (!self || mc.options.cameraType.isFirstPerson)) return false
            return players.value
        }
        if (entity is Mob) {
            return mobs.value
        }
        return false
    }

    @JvmStatic
    fun shouldRenderChamsState(state: net.minecraft.client.renderer.entity.state.LivingEntityRenderState): Boolean {
        if (!isEnabled()) return false
        if (state is net.minecraft.client.renderer.entity.state.AvatarRenderState) {
            return players.value
        }
        return mobs.value
    }

    @JvmStatic
    fun shouldTint(): Boolean {
        return isEnabled() && mode == "Tinted Glow"
    }

    @JvmStatic
    fun getChamsColor(): Int {
        val raw = if (theme.value) M3Theme.current.primary.argb else color.value.argb
        return if ((raw and 0xFF000000.toInt()) == 0) (raw or 0xFF000000.toInt()) else raw
    }
}
