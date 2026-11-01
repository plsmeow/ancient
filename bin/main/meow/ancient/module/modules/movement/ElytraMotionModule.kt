package meow.ancient.module.modules.movement

import meow.ancient.client.extensions.MOVEMENT
import meow.ancient.client.extensions.inGame
import meow.ancient.client.extensions.mc
import meow.ancient.event.events.PlayerUpdateEvent
import meow.ancient.module.Module
import meow.ancient.module.modules.combat.HitAuraModule
import net.minecraft.world.phys.Vec3
import sweetie.evaware.flora.api.Commando

object ElytraMotionModule : Module(
    name = "Elytra Motion",
    category = MOVEMENT,
    description = "Stops in front of target during elytra flight"
) {
    private var waitTarget = false

    @Commando
    fun onUpdate(event: PlayerUpdateEvent) {
        if (!inGame) return
        val player = mc.player ?: return
        val target = HitAuraModule.currentTarget

        if (target == null) {
            if (!waitTarget) {
                player.isNoGravity = false
                waitTarget = true
            }
            return
        } else {
            waitTarget = false
        }

        val dist = player.eyePosition.distanceTo(target.boundingBox.center)
        val shouldChase = target.isFallFlying && (target.deltaMovement.length() * 20.0 >= 13.0)

        val threshold = if (target.isFallFlying) 1.5 else 3.0
        if (player.isFallFlying && dist < threshold && !shouldChase) {
            player.deltaMovement = Vec3.ZERO
            player.isNoGravity = true
        } else {
            player.isNoGravity = false
        }
    }

    override fun onDisable() {
        val player = mc.player ?: return
        waitTarget = false
        player.isNoGravity = false
    }
}
