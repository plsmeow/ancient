package meow.ancient.module.modules.movement

import meow.ancient.client.extensions.MOVEMENT
import meow.ancient.client.extensions.mc
import meow.ancient.event.events.PlayerUpdateEvent
import meow.ancient.module.Module
import net.minecraft.world.entity.ai.attributes.Attributes
import sweetie.evaware.flora.api.Commando

object StepModule : Module(
    name = "Step",
    category = MOVEMENT,
    description = ""
) {
    val height = number("Height", 1.25, 1.0, 10.0, 0.25, 2)

    private var prevStepHeight = 0.6

    override fun onEnable() {
        val player = mc.player ?: return
        val attribute = player.getAttribute(Attributes.STEP_HEIGHT) ?: return
        prevStepHeight = attribute.baseValue
        attribute.baseValue = height.value
    }

    override fun onDisable() {
        val player = mc.player ?: return
        player.getAttribute(Attributes.STEP_HEIGHT)?.baseValue = prevStepHeight
    }

    @Commando
    fun onUpdate(event: PlayerUpdateEvent) {
        val player = mc.player ?: return
        player.getAttribute(Attributes.STEP_HEIGHT)?.baseValue = height.value
    }
}
