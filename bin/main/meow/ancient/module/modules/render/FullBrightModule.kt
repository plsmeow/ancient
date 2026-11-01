package meow.ancient.module.modules.render

import meow.ancient.client.extensions.RENDER
import meow.ancient.client.extensions.mc
import meow.ancient.event.events.PlayerUpdateEvent
import meow.ancient.module.Module
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import sweetie.evaware.flora.api.Commando

object FullBrightModule : Module(
    name = "FullBright",
    category = RENDER,
    description = "Applies full bright gamma via night vision effect"
) {
    @Commando
    fun onUpdate(event: PlayerUpdateEvent) {
        val player = mc.player ?: return
        if (!player.hasEffect(MobEffects.NIGHT_VISION)) {
            player.addEffect(MobEffectInstance(MobEffects.NIGHT_VISION, MobEffectInstance.INFINITE_DURATION, 0, false, false, false))
        }
    }

    override fun onDisable() {
        val player = mc.player ?: return
        player.removeEffect(MobEffects.NIGHT_VISION)
    }
}
