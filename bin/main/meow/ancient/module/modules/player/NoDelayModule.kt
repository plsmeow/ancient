package meow.ancient.module.modules.player

import meow.ancient.client.extensions.PLAYER
import meow.ancient.client.extensions.mc
import meow.ancient.event.events.PlayerUpdateEvent
import meow.ancient.module.Module
import sweetie.evaware.flora.api.Commando

object NoDelayModule : Module(
    name = "NoDelay",
    category = PLAYER,
    description = ""
) {
    val jump by boolean("Jump", true, "")
    val place by boolean("Place", true, "")

    @Commando
    fun onUpdate(event: PlayerUpdateEvent) {
        safeRun {
            if (jump) {
                player.noJumpDelay = 0
            }

            if (place) {
                mc.rightClickDelay = 0
            }
        }
    }
}
