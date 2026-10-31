package meow.ancient.module.modules.player

import meow.ancient.client.extensions.PLAYER
import meow.ancient.client.extensions.mc
import meow.ancient.event.events.PlayerUpdateEvent
import meow.ancient.module.Module
import net.minecraft.world.item.Items
import sweetie.evaware.flora.api.Commando

object FastExpModule : Module(
    name = "FastExp",
    category = PLAYER,
    description = ""
) {
    @Commando
    fun onUpdate(event: PlayerUpdateEvent) {
        safeRun {
            val holdingExp = player.mainHandItem.`is`(Items.EXPERIENCE_BOTTLE) || player.offhandItem.`is`(Items.EXPERIENCE_BOTTLE)
            if (holdingExp && (mc.options.keyUse.isDown || mc.mouseHandler.isRightPressed)) {
                mc.rightClickDelay = 0
            }
        }
    }
}
